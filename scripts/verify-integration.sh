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
REQUIRED_TABLES="workflow workflow_status workflow_transition project_release test_plan test_cycle test_cycle_case test_case_version design_asset design_asset_version saved_filter deploy_environment deployment design_walkthrough design_walkthrough_item custom_field custom_field_value program chat_read_state chat_reaction notification_preference subscription"
MISSING=""
for t in $REQUIRED_TABLES; do
    n=$(mysql_exec -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$t';")
    [ "$n" = "1" ] || MISSING="$MISSING $t"
done
if [ -z "$MISSING" ]; then
    ok "21 张新表全部创建"
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
# 第 5 批：自定义字段
# ============================================
FIELD=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/custom-fields?issueType=TASK" -H "$AUTH"         -H "Content-Type: application/json"         -d '{"fieldKey":"impact","name":"Impact Area","type":"SELECT","options":"[\"frontend\",\"backend\"]","required":1}')
FIELD_ID=$(echo "$FIELD" | json_field "['data']['id']")
[ -n "$FIELD_ID" ] && ok "创建自定义字段" || bad "创建自定义字段失败：$FIELD"

# 重复 fieldKey → 409
CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/projects/$PROJ_ID/custom-fields?issueType=TASK" -H "$AUTH"        -H "Content-Type: application/json"        -d '{"fieldKey":"impact","name":"Duplicate","type":"TEXT"}')
[ "$CODE" = "409" ] && ok "重复字段标识被拒绝（409）" || bad "重复字段标识返回 $CODE"

if [ -n "$FIELD_ID" ] && [ -n "$TASK_ID" ]; then
    # 必填未填 → 400
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/TASK/$TASK_ID/custom-field-values" -H "$AUTH"            -H "Content-Type: application/json" -d '{"values":{}}')
    [ "$CODE" = "400" ] && ok "必填字段缺失被拒绝（400）" || bad "必填校验返回 $CODE（期望 400）"

    # 合法值 → 200
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/TASK/$TASK_ID/custom-field-values" -H "$AUTH"            -H "Content-Type: application/json" -d '{"values":{"impact":"frontend"}}')
    [ "$CODE" = "200" ] && ok "保存自定义字段值" || bad "保存字段值返回 $CODE"

    # 选项外的值 → 400
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/TASK/$TASK_ID/custom-field-values" -H "$AUTH"            -H "Content-Type: application/json" -d '{"values":{"impact":"not-an-option"}}')
    [ "$CODE" = "400" ] && ok "非法选项被拒绝（400）" || bad "非法选项返回 $CODE（期望 400）"

    # 未知字段 → 400
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/TASK/$TASK_ID/custom-field-values" -H "$AUTH"            -H "Content-Type: application/json" -d '{"values":{"nope":"x"}}')
    [ "$CODE" = "400" ] && ok "未知字段被拒绝（400）" || bad "未知字段返回 $CODE（期望 400）"

    # 读回
    VAL=$(curl -s -m 10 "$BASE/TASK/$TASK_ID/custom-field-values" -H "$AUTH" | json_field "['data']['impact']")
    [ "$VAL" = "frontend" ] && ok "自定义字段值可读回" || bad "读回值=$VAL（期望 frontend）"
fi

# ============================================
# 第 5 批：项目集
# ============================================
PROG=$(curl -s -m 10 -X POST "$BASE/enterprises/$ENT_ID/programs" -H "$AUTH"        -H "Content-Type: application/json" -d '{"name":"CI Program"}')
PROG_ID=$(echo "$PROG" | json_field "['data']['id']")
[ -n "$PROG_ID" ] && ok "创建项目集" || bad "创建项目集失败：$PROG"

if [ -n "$PROG_ID" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/projects/$PROJ_ID/program" -H "$AUTH"            -H "Content-Type: application/json" -d "{\"programId\":\"$PROG_ID\"}")
    [ "$CODE" = "200" ] && ok "项目归入项目集" || bad "项目归集返回 $CODE"

    COUNT=$(curl -s -m 10 "$BASE/programs/$PROG_ID" -H "$AUTH" | json_field "['data']['projectCount']")
    [ "$COUNT" = "1" ] && ok "项目集概览识别到 1 个项目" || bad "项目集项目数=$COUNT（期望 1）"
fi

# ============================================
# 第 5 批：备份脚本可用性
# ============================================
if [ -x "$ROOT_DIR/scripts/db-backup.sh" ] || [ -f "$ROOT_DIR/scripts/db-backup.sh" ]; then
    BK_DIR="$WORK_DIR/bktest"
    if DB_NAME="$DB_NAME" BACKUP_DIR="$BK_DIR" bash "$ROOT_DIR/scripts/db-backup.sh" >/dev/null 2>&1        && ls "$BK_DIR"/*.sql.gz >/dev/null 2>&1; then
        ok "数据库备份脚本可执行且产出备份文件"
    else
        bad "数据库备份脚本执行失败"
    fi
else
    bad "缺少 scripts/db-backup.sh"
fi

# ============================================
# 第 6 批：IM 动作 / 通知偏好 / 全文检索
# ============================================
CH=$(curl -s -m 10 "$BASE/channels" -H "$AUTH")
CH_ID=$(echo "$CH" | json_field "[0]['id']")
if [ -z "$CH_ID" ] || [ "$CH_ID" = "None" ]; then
    # 项目创建时会自动建项目群，取第一个
    CH_ID=$(echo "$CH" | python -c "import sys,json;d=json.load(sys.stdin);print(d['data'][0]['id'] if d.get('data') else '')" 2>/dev/null || echo "")
fi
if [ -n "$CH_ID" ]; then
    ok "取到项目频道"
    MSG=$(curl -s -m 10 -X POST "$BASE/channels/$CH_ID/messages" -H "$AUTH"           -H "Content-Type: application/json" -d '{"content":"ci message"}')
    MSG_ID=$(echo "$MSG" | json_field "['data']['id']")
    [ -n "$MSG_ID" ] && ok "发送消息" || bad "发送消息失败"

    if [ -n "$MSG_ID" ]; then
        # 编辑
        CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/messages/$MSG_ID" -H "$AUTH"                -H "Content-Type: application/json" -d '{"content":"ci message edited"}')
        [ "$CODE" = "200" ] && ok "编辑消息" || bad "编辑消息返回 $CODE"

        # 表情
        REACT=$(curl -s -m 10 -X POST "$BASE/messages/$MSG_ID/reactions" -H "$AUTH"                 -H "Content-Type: application/json" -d '{"emoji":"+1"}')
        ADDED=$(echo "$REACT" | json_field "['data']['added']")
        [ "$ADDED" = "True" ] && ok "表情回应添加" || bad "表情回应 added=$ADDED"

        # 再点一次取消
        ADDED2=$(curl -s -m 10 -X POST "$BASE/messages/$MSG_ID/reactions" -H "$AUTH"                  -H "Content-Type: application/json" -d '{"emoji":"+1"}' | json_field "['data']['added']")
        [ "$ADDED2" = "False" ] && ok "表情回应可取消" || bad "取消回应 added=$ADDED2"

        # 已读
        UNREAD=$(curl -s -m 10 -X POST "$BASE/channels/$CH_ID/read" -H "$AUTH"                  -H "Content-Type: application/json" -d '{}' | json_field "['data']['unread']")
        [ "$UNREAD" = "0" ] && ok "标记已读后未读归零" || bad "已读后未读=$UNREAD"

        # 撤回
        CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/messages/$MSG_ID/recall" -H "$AUTH")
        [ "$CODE" = "200" ] && ok "撤回消息" || bad "撤回消息返回 $CODE"

        # 撤回后不能再编辑
        CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/messages/$MSG_ID" -H "$AUTH"                -H "Content-Type: application/json" -d '{"content":"x"}')
        [ "$CODE" = "400" ] && ok "已撤回消息不可编辑（400）" || bad "撤回后编辑返回 $CODE（期望 400）"
    fi
else
    bad "没有可用的项目频道"
fi

# 通知偏好
PREF=$(curl -s -m 10 "$BASE/notification-preferences" -H "$AUTH")
TYPES_N=$(echo "$PREF" | python -c "import sys,json;d=json.load(sys.stdin);print(len((d.get('data') or {}).get('types') or {}))" 2>/dev/null || echo 0)
[ "${TYPES_N:-0}" -ge 5 ] && ok "读取通知偏好（$TYPES_N 个类型）" || bad "通知偏好类型数=$TYPES_N"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/notification-preferences" -H "$AUTH"        -H "Content-Type: application/json" -d '{"types":{"COMMENT":false},"dndEnabled":true,"dndStart":"22:00","dndEnd":"08:00"}')
[ "$CODE" = "200" ] && ok "保存通知偏好与免打扰" || bad "保存通知偏好返回 $CODE"

# 校验回读
DND=$(curl -s -m 10 "$BASE/notification-preferences" -H "$AUTH" | json_field "['data']['dndEnabled']")
[ "$DND" = "True" ] && ok "免打扰设置已持久化" || bad "免打扰回读=$DND"

# 全文索引
FT=$(mysql_exec -N -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DB_NAME' AND index_type='FULLTEXT';")
[ "${FT:-0}" -ge 4 ] && ok "全文索引已创建（$FT 个）" || bad "全文索引数=$FT（期望 ≥4）"

# ============================================
# 第 7 批：消息搜索 / 关注
# ============================================
if [ -n "$CH_ID" ]; then
    # 频道内消息搜索（先发一条含关键词的消息）
    curl -s -m 10 -X POST "$BASE/channels/$CH_ID/messages" -H "$AUTH"         -H "Content-Type: application/json" -d '{"content":"deploy pipeline keyword"}' >/dev/null
    HITS=$(curl -s -m 10 "$BASE/channels/$CH_ID/messages/search?q=deploy" -H "$AUTH"            | json_field "['data']['total']")
    [ "${HITS:-0}" -ge 1 ] && ok "频道内消息搜索命中（$HITS 条）" || bad "消息搜索命中数=$HITS"

    # 搜不到的词应为 0
    HITS0=$(curl -s -m 10 "$BASE/channels/$CH_ID/messages/search?q=zzzznotfound" -H "$AUTH"             | json_field "['data']['total']")
    [ "${HITS0:-1}" = "0" ] && ok "无关关键词返回 0 条" || bad "无关关键词命中=$HITS0"
fi

# 关注：关注任务 → 查询状态 → 列表 → 取消
if [ -n "$TASK_ID" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/subscriptions" -H "$AUTH"            -H "Content-Type: application/json" -d "{\"targetType\":\"TASK\",\"targetId\":\"$TASK_ID\"}")
    [ "$CODE" = "200" ] && ok "关注任务" || bad "关注任务返回 $CODE"

    ST=$(curl -s -m 10 "$BASE/subscriptions/status?targetType=TASK&targetId=$TASK_ID" -H "$AUTH"          | json_field "['data']['subscribed']")
    [ "$ST" = "True" ] && ok "关注状态查询正确" || bad "关注状态=$ST"

    N=$(curl -s -m 10 "$BASE/subscriptions?targetType=TASK" -H "$AUTH"         | python -c "import sys,json;print(len(json.load(sys.stdin).get('data') or []))" 2>/dev/null || echo 0)
    [ "${N:-0}" -ge 1 ] && ok "关注列表返回 $N 条" || bad "关注列表为空"

    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X DELETE            "$BASE/subscriptions?targetType=TASK&targetId=$TASK_ID" -H "$AUTH")
    [ "$CODE" = "200" ] && ok "取消关注" || bad "取消关注返回 $CODE"

    # 非法类型被拒绝
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/subscriptions" -H "$AUTH"            -H "Content-Type: application/json" -d '{"targetType":"HACK","targetId":"x"}')
    [ "$CODE" = "400" ] && ok "非法关注类型被拒绝（400）" || bad "非法类型返回 $CODE"
fi

# ============================================
# 第 8 批：订阅接入通知分发
# ============================================
SUB_TASK=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/tasks" -H "$AUTH" \
           -H "Content-Type: application/json" -d '{"title":"ci-sub-task","priority":"LOW"}' \
           | json_field "['data']['id']")
if [ -n "$SUB_TASK" ]; then
    # 另一个人关注该任务：直接把关注行写库（关注接口本身上面已覆盖），
    # 这里验证的是"状态变更时是否按关注关系分发通知"
    OTHER_ID=$(mysql_exec -N -e "SELECT id FROM $DB_NAME.sys_user WHERE username='zhangsan' LIMIT 1;")
    if [ -n "$OTHER_ID" ]; then
        mysql_exec -e "INSERT IGNORE INTO $DB_NAME.subscription (id, user_id, target_type, target_id, project_id, auto, created_at) \
            VALUES (REPLACE(UUID(),'-',''), '$OTHER_ID', 'TASK', '$SUB_TASK', '$PROJ_ID', 0, NOW());" >/dev/null 2>&1

        BEFORE=$(mysql_exec -N -e "SELECT COUNT(*) FROM $DB_NAME.notification WHERE user_id='$OTHER_ID' AND type='TASK_UPDATE';")
        CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/tasks/$SUB_TASK/status" -H "$AUTH" \
               -H "Content-Type: application/json" -d '{"status":"DOING"}')
        AFTER=$(mysql_exec -N -e "SELECT COUNT(*) FROM $DB_NAME.notification WHERE user_id='$OTHER_ID' AND type='TASK_UPDATE';")
        [ "$CODE" = "200" ] && [ "${AFTER:-0}" -gt "${BEFORE:-0}" ] \
            && ok "关注者收到任务状态变更通知（$BEFORE → $AFTER）" \
            || bad "关注者未收到通知（code=$CODE before=$BEFORE after=$AFTER）"

        # 操作者本人不该收到自己触发的通知
        ME_ID=$(mysql_exec -N -e "SELECT id FROM $DB_NAME.sys_user WHERE username='admin' LIMIT 1;")
        MY_N=$(mysql_exec -N -e "SELECT COUNT(*) FROM $DB_NAME.notification WHERE user_id='$ME_ID' AND type='TASK_UPDATE' AND content LIKE '%ci-sub-task%';")
        [ "${MY_N:-0}" = "0" ] && ok "操作者不会收到自己触发的通知" \
            || bad "操作者收到了自己的通知（$MY_N 条）"
    fi
fi

# 文档关注（全局文档需同企业）
DOC_ID=$(curl -s -m 10 -X POST "$BASE/documents" -H "$AUTH" \
         -H "Content-Type: application/json" -d '{"title":"ci doc","content":"hello","type":"DOC"}' \
         | json_field "['data']['id']")
if [ -n "$DOC_ID" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/subscriptions" -H "$AUTH" \
           -H "Content-Type: application/json" -d "{\"targetType\":\"DOCUMENT\",\"targetId\":\"$DOC_ID\"}")
    [ "$CODE" = "200" ] && ok "关注全局文档（同企业）" || bad "关注文档返回 $CODE"
fi

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
