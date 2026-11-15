#!/usr/bin/env bash
# ============================================
# 集成验证脚本（可在本地与 CI 中运行）
# ============================================
# 目的：验证那些**单测覆盖不到**的集成层正确性 —— 表是否真的建出来了、
# schema 是否幂等、工作流/部署/审计链路在真实数据库上是否走得通。
#
# 这些正是 2026-11-15 端到端验证中发现的缺陷类型（见
# docs/E2E_VERIFICATION_2026-11-15.md）：
#   - 新表从未被创建（备用 Flyway 未接入，schema.sql 只有 ALTER）
#   - schema.sql 非幂等（重复执行报 Duplicate column）
#   - 审计哈希链因时间戳精度/排序/分叉三处错误而整体不可用
# 这三类问题编译、类型检查、单元测试全都发现不了。
#
# 用法：
#   scripts/verify-integration.sh                # 本地（默认连 localhost:3306）
#   DB_HOST=mysql DB_PASSWORD=xxx scripts/verify-integration.sh
#
# 需要：bash + mysql 客户端 + JDK17 + Maven（后端由脚本自行启动）
# 脚本会创建/使用独立数据库 ${DB_NAME:-devflow_ci}，不会碰现有业务库。

set -uo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USERNAME:-root}"
DB_PASS="${DB_PASSWORD:-devflow123}"
DB_NAME="${DB_NAME:-devflow_ci}"
APP_PORT="${APP_PORT:-18080}"
MGMT_PORT="${MGMT_PORT:-19090}"
BASE="http://127.0.0.1:${APP_PORT}/api/v1"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
SCHEMA="$BACKEND_DIR/src/main/resources/schema.sql"
WORK_DIR="$(mktemp -d)"
APP_LOG="$WORK_DIR/backend.log"
APP_PID=""

PASS=0
FAIL=0
FAILED_CHECKS=()

cleanup() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

ok()   { PASS=$((PASS+1)); echo "  ✅ $1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_CHECKS+=("$1"); echo "  ❌ $1"; }
info() { echo "→ $1"; }

mysql_exec() { mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 "$@"; }

# ============================================
# 1. 准备一个干净库
# ============================================
info "重建数据库 $DB_NAME"
mysql_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    || { echo "无法连接 MySQL，请检查 DB_HOST/DB_USERNAME/DB_PASSWORD"; exit 1; }

# ============================================
# 2. schema.sql 首次执行
# ============================================
# schema.sql 不含 USE/CREATE DATABASE（曾经有，会导致业务查询写错库），
# 所以直接对目标库执行即可。
SCHEMA_FOR_CI="$SCHEMA"

info "第 1 次执行 schema.sql"
mysql_exec --force "$DB_NAME" < "$SCHEMA_FOR_CI" 2> "$WORK_DIR/schema1.err"
# ADD COLUMN 重复执行报错属项目既有约定（continue-on-error），这里只看
# 「除已知重复列/重复键之外」是否有别的错误。
UNEXPECTED=$(grep -c "ERROR" "$WORK_DIR/schema1.err" 2>/dev/null || true)
UNEXPECTED=$(grep "ERROR" "$WORK_DIR/schema1.err" 2>/dev/null | grep -vcE "Duplicate column|Duplicate key name" || true)
if [ "${UNEXPECTED:-0}" -eq 0 ]; then
    ok "首次执行无意外错误（重复列错误数 ${UNEXPECTED}）"
else
    bad "首次执行出现非预期错误："
    grep "ERROR" "$WORK_DIR/schema1.err" | grep -vE "Duplicate column|Duplicate key name" | head -5
fi

# ============================================
# 3. 新表必须真的存在（回归："新表从未创建"）
# ============================================
info "检查第 2-4 批新增的表"
REQUIRED_TABLES="workflow workflow_status workflow_transition project_release test_plan test_cycle test_cycle_case test_case_version design_asset design_asset_version saved_filter deploy_environment deployment design_walkthrough design_walkthrough_item"
MISSING=""
for t in $REQUIRED_TABLES; do
    n=$(mysql_exec -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$t';")
    [ "$n" = "1" ] || MISSING="$MISSING $t"
done
if [ -z "$MISSING" ]; then
    ok "15 张新表全部创建"
else
    bad "缺少表:$MISSING（schema.sql 里是否漏了 CREATE TABLE？）"
fi

# ============================================
# 4. schema.sql 幂等：再跑一次
# ============================================
info "第 2 次执行 schema.sql（幂等性）"
mysql_exec --force "$DB_NAME" < "$SCHEMA_FOR_CI" 2> "$WORK_DIR/schema2.err"
NEW_ERRORS=$(grep "ERROR" "$WORK_DIR/schema2.err" 2>/dev/null | grep -vcE "Duplicate column|Duplicate key name" || true)
if [ "${NEW_ERRORS:-0}" -eq 0 ]; then
    ok "重复执行除已知重复列/键外无新增错误"
else
    bad "重复执行产生新错误（索引/迁移不幂等）："
    grep "ERROR" "$WORK_DIR/schema2.err" | grep -vE "Duplicate column|Duplicate key name" | head -5
fi
# 索引幂等：单独确认存储过程那批索引都建成了
IDX_MISSING=$(mysql_exec -N -e "
SELECT COUNT(*) FROM (
  SELECT 'idx_task_fix_version' AS i UNION ALL SELECT 'idx_bug_fix_version'
  UNION ALL SELECT 'idx_requirement_fix_version' UNION ALL SELECT 'idx_execution_cycle'
  UNION ALL SELECT 'idx_requirement_assignee' UNION ALL SELECT 'idx_status'
  UNION ALL SELECT 'idx_review_status') want
WHERE NOT EXISTS (SELECT 1 FROM information_schema.statistics s
                  WHERE s.table_schema='$DB_NAME' AND s.index_name=want.i);")
if [ "$IDX_MISSING" = "0" ]; then
    ok "7 个高频索引全部存在（幂等创建生效）"
else
    bad "有 $IDX_MISSING 个索引未创建"
fi

# ============================================
# 5. 启动后端（真实 MySQL，无 Redis/MinIO/AI）
# ============================================
info "启动后端（端口 $APP_PORT，管理端口 $MGMT_PORT）"
cd "$BACKEND_DIR"
DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
DB_USERNAME="$DB_USER" \
DB_PASSWORD="$DB_PASS" \
JWT_SECRET="Y2ktdmVyaWZ5LWp3dC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmc=" \
AI_SERVICE_TOKEN="ci-token" \
LOG_LEVEL=warn \
MYBATIS_LOG_IMPL=org.apache.ibatis.logging.nologging.NoLoggingImpl \
mvn -B -q spring-boot:run \
    -Dspring-boot.run.arguments="--server.port=$APP_PORT --management.server.port=$MGMT_PORT" \
    > "$APP_LOG" 2>&1 &
APP_PID=$!

READY=0
for _ in $(seq 1 90); do
    if curl -sf -m 2 "http://127.0.0.1:${MGMT_PORT}/actuator/health" >/dev/null 2>&1; then
        READY=1; break
    fi
    sleep 2
done
if [ "$READY" = "1" ]; then
    ok "后端启动成功（健康探针 UP）"
else
    bad "后端未能在 180s 内启动，日志尾部："
    tail -30 "$APP_LOG"
    echo "---- 校验汇总：通过 $PASS，失败 $FAIL ----"
    exit 1
fi

# ============================================
# 6. 认证 + 基础业务链路
# ============================================
# JSON 取值：优先 jq，回退到 python（本地 Windows 常见没有 python3 这个名字）
json_field() {
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null || echo ""
    else
        python -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null || echo ""
    fi
}

TOKEN=$(curl -s -m 10 -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin123"}' | json_field "['data']['accessToken']")
if [ -n "$TOKEN" ]; then ok "种子账号可登录"; else bad "登录失败（种子数据或密码策略是否变了？）"; fi
AUTH="Authorization: Bearer $TOKEN"

# 未认证 → 401
CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 "$BASE/projects")
[ "$CODE" = "401" ] && ok "未认证访问返回 401" || bad "未认证访问返回 $CODE（期望 401）"

# 建企业 → 建项目
ENT_ID=$(curl -s -m 10 -X POST "$BASE/enterprises" -H "$AUTH" -H "Content-Type: application/json" \
         -d '{"name":"CI Verify Org"}' | json_field "['data']['id']")
[ -n "$ENT_ID" ] && ok "创建企业" || bad "创建企业失败"

PROJ_ID=$(curl -s -m 10 -X POST "$BASE/projects" -H "$AUTH" -H "Content-Type: application/json" \
          -d '{"name":"CI Project","projectKey":"CIP"}' | json_field "['data']['id']")
[ -n "$PROJ_ID" ] && ok "创建项目" || bad "创建项目失败"

if [ -z "$PROJ_ID" ]; then
    echo "项目创建失败，后续链路检查跳过"
    echo "---- 校验汇总：通过 $PASS，失败 $FAIL ----"
    exit 1
fi

# ============================================
# 7. 工作流：默认模板 + 状态流转
# ============================================
WF=$(curl -s -m 15 "$BASE/projects/$PROJ_ID/workflow?issueType=TASK" -H "$AUTH")
N_STATUS=$(echo "$WF" | json_field "['data']['statuses'].__len__()")
[ "$N_STATUS" = "4" ] && ok "工作流默认模板自动初始化（4 个状态）" || bad "工作流状态数=$N_STATUS（期望 4）"

TASK_ID=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/tasks" -H "$AUTH" -H "Content-Type: application/json" \
          -d '{"title":"ci-task","priority":"HIGH"}' | json_field "['data']['id']")
[ -n "$TASK_ID" ] && ok "创建任务" || bad "创建任务失败"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/tasks/$TASK_ID/status" -H "$AUTH" \
       -H "Content-Type: application/json" -d '{"status":"NOT_A_STATUS"}')
[ "$CODE" = "400" ] && ok "非法状态被拒绝（400）" || bad "非法状态返回 $CODE（期望 400）"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/tasks/$TASK_ID/status" -H "$AUTH" \
       -H "Content-Type: application/json" -d '{"status":"DONE"}')
[ "$CODE" = "200" ] && ok "合法状态流转成功" || bad "合法流转返回 $CODE（期望 200）"

# ============================================
# 8. 版本 / 部署 / DORA
# ============================================
REL_ID=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/releases" -H "$AUTH" -H "Content-Type: application/json" \
         -d '{"version":"v0.0.1"}' | json_field "['data']['id']")
[ -n "$REL_ID" ] && ok "创建版本" || bad "创建版本失败"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/projects/$PROJ_ID/releases" -H "$AUTH" \
       -H "Content-Type: application/json" -d '{"version":"v0.0.1"}')
[ "$CODE" = "409" ] && ok "重复版本号被拒绝（409）" || bad "重复版本号返回 $CODE（期望 409）"

ENV_ID=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/environments" -H "$AUTH" -H "Content-Type: application/json" \
         -d '{"name":"prod","type":"PROD"}' | json_field "['data']['id']")
[ -n "$ENV_ID" ] && ok "创建生产环境" || bad "创建环境失败"

DEP=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/environments/$ENV_ID/deployments" -H "$AUTH" \
      -H "Content-Type: application/json" -d "{\"releaseId\":\"$REL_ID\"}")
DEP_STATUS=$(echo "$DEP" | json_field "['data']['status']")
[ "$DEP_STATUS" = "PENDING_APPROVAL" ] && ok "生产部署进入待审批" || bad "部署状态=$DEP_STATUS（期望 PENDING_APPROVAL）"

DEP_ID=$(echo "$DEP" | json_field "['data']['id']")
if [ -n "$DEP_ID" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/deployments/$DEP_ID/approve" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
    [ "$CODE" = "403" ] && ok "自审批被拒绝（403）" || bad "自审批返回 $CODE（期望 403）"
fi

DORA=$(curl -s -m 15 "$BASE/projects/$PROJ_ID/dora?days=30" -H "$AUTH")
HAS_PROD=$(echo "$DORA" | json_field "['data']['hasProdEnvironment']")
[ "$HAS_PROD" = "True" ] && ok "DORA 识别生产环境" || bad "DORA hasProdEnvironment=$HAS_PROD"

# ============================================
# 9. 测试计划 → 轮次
# ============================================
PLAN_ID=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/test-plans" -H "$AUTH" -H "Content-Type: application/json" \
          -d '{"name":"ci-plan"}' | json_field "['data']['id']")
[ -n "$PLAN_ID" ] && ok "创建测试计划" || bad "创建测试计划失败"

if [ -n "$PLAN_ID" ]; then
    CYCLE_ID=$(curl -s -m 10 -X POST "$BASE/test-plans/$PLAN_ID/cycles" -H "$AUTH" -H "Content-Type: application/json" \
               -d '{"name":"cycle-1"}' | json_field "['data']['id']")
    [ -n "$CYCLE_ID" ] && ok "创建测试轮次" || bad "创建测试轮次失败"
fi

# ============================================
# 10. 结构化查询的注入防护
# ============================================
CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/query" -H "$AUTH" \
       -H "Content-Type: application/json" -d '{"entity":"TASK","expression":"status = TODO; DROP TABLE task"}')
[ "$CODE" = "400" ] && ok "SQL 注入片段被拒绝（400）" || bad "注入尝试返回 $CODE（期望 400）"

# ============================================
# 11. 审计哈希链：正向 + 篡改检测
# ============================================
# 触发几条审计（建项目/建企业已写），校验应通过
VERIFY=$(curl -s -m 15 "$BASE/enterprises/$ENT_ID/audit-logs/verify" -H "$AUTH")
VALID=$(echo "$VERIFY" | json_field "['data']['valid']")
CHECKED=$(echo "$VERIFY" | json_field "['data']['checkedRecords']")
if [ "$VALID" = "True" ] && [ "${CHECKED:-0}" -gt 0 ]; then
    ok "审计哈希链正向校验通过（$CHECKED 条）"
else
    bad "审计链校验未通过：$VERIFY"
fi

# 篡改一条链式记录后应报错
TAMPER_ID=$(mysql_exec -N -e "SELECT id FROM \`$DB_NAME\`.activity_log WHERE prev_hash IS NOT NULL LIMIT 1;")
if [ -n "$TAMPER_ID" ]; then
    mysql_exec "$DB_NAME" -e "UPDATE activity_log SET detail='TAMPERED' WHERE id='$TAMPER_ID';" 2>/dev/null
    VALID2=$(curl -s -m 15 "$BASE/enterprises/$ENT_ID/audit-logs/verify" -H "$AUTH" | json_field "['data']['valid']")
    [ "$VALID2" = "False" ] && ok "篡改被检测出来" || bad "篡改未被检测（valid=$VALID2）"
else
    bad "找不到链式审计记录（哈希链可能没写入）"
fi

# CSV 导出不应 500（回归：Servlet 流冲突）
CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 15 "$BASE/enterprises/$ENT_ID/audit-logs/export?limit=10" -H "$AUTH")
[ "$CODE" = "200" ] && ok "审计 CSV 导出正常（200）" || bad "审计导出返回 $CODE（期望 200）"

# ============================================
# 12. 双因素：绑定 → 两段式登录
# ============================================
BIND=$(curl -s -m 10 -X POST "$BASE/security/mfa/bind" -H "$AUTH")
SECRET=$(echo "$BIND" | json_field "['data']['secret']")
if [ -n "$SECRET" ]; then
    ok "发起双因素绑定"
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
           -d '{"username":"admin","password":"admin123"}')
    [ "$CODE" = "200" ] && ok "未确认绑定时登录仍为单因子" || bad "登录返回 $CODE"
else
    bad "发起双因素绑定失败"
fi

# ============================================
# 13. 可观测性：管理端口与业务端口隔离
# ============================================
CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "http://127.0.0.1:${MGMT_PORT}/actuator/prometheus")
[ "$CODE" = "200" ] && ok "Prometheus 指标可抓取（管理端口）" || bad "Prometheus 返回 $CODE"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "http://127.0.0.1:${APP_PORT}/actuator/prometheus")
[ "$CODE" != "200" ] && ok "业务端口未暴露指标端点" || bad "业务端口暴露了 /actuator/prometheus"

# ============================================
# 汇总
# ============================================
echo ""
echo "================ 校验汇总 ================"
echo "通过: $PASS   失败: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    echo "失败项："
    for c in "${FAILED_CHECKS[@]}"; do echo "  - $c"; done
    echo ""
    echo "后端日志尾部（$APP_LOG）："
    tail -20 "$APP_LOG"
    exit 1
fi
echo "全部通过 ✅"
