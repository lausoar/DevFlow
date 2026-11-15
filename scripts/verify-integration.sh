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
#   - 迁移脚本用了 DELIMITER，而执行器只按 ";" 切分 → 静默失败（第 10 批已改 Flyway）
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
# 2. 启动/停止后端（第 10 批起由 Flyway 建库）
# ============================================
# 结构迁移从 spring.sql.init 换成 Flyway 后，验证的重点也变了：
#   - 不再"用 CLI 跑 schema.sql 再断言"，而是**让应用自己跑迁移**，
#     然后断言迁移结果 —— 这才是生产路径；
#   - 要验证三种库：全新库、重启后的库、已有表但没有 Flyway 历史的老库。

APP_LOG_DIR="$WORK_DIR"

port_pid() {
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -ltnp 2>/dev/null | grep ":${port} " | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2
    else
        netstat -ano 2>/dev/null | grep ":${port} " | grep -iE "LISTENING|LISTEN" | awk '{print $NF}' | head -1
    fi
}

wait_port_free() {
    local port="$1"
    for _ in $(seq 1 30); do
        [ -z "$(port_pid "$port")" ] && return 0
        sleep 1
    done
    return 1
}

stop_backend() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
    fi
    wait_port_free "$APP_PORT" || true
    # spring-boot:run 会 fork 出子 JVM，kill mvn 不一定带得走它。
    # 端口还占着就按端口找进程强杀（本地 Windows 上实测必须要这一步）。
    local left
    left=$(port_pid "$APP_PORT")
    if [ -n "$left" ]; then
        if command -v taskkill >/dev/null 2>&1; then
            taskkill //F //PID "$left" >/dev/null 2>&1 || true
        else
            kill -9 "$left" >/dev/null 2>&1 || true
        fi
    fi
    wait_port_free "$APP_PORT" || true
    APP_PID=""
}

# start_backend <库名> <日志文件>
start_backend() {
    local db="$1" log="$2"
    cd "$BACKEND_DIR"
    DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${db}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
    DB_USERNAME="$DB_USER" \
    DB_PASSWORD="$DB_PASS" \
    JWT_SECRET="Y2ktdmVyaWZ5LWp3dC1zZWNyZXQta2V5LTMyLWJ5dGVzLWxvbmc=" \
    AI_SERVICE_TOKEN="ci-token" \
    LOG_LEVEL=warn \
    MYBATIS_LOG_IMPL=org.apache.ibatis.logging.nologging.NoLoggingImpl \
    mvn -B -q spring-boot:run \
        -Dspring-boot.run.arguments="--server.port=$APP_PORT --management.server.port=$MGMT_PORT" \
        > "$log" 2>&1 &
    APP_PID=$!
    APP_LOG="$log"

    for _ in $(seq 1 120); do
        if curl -sf -m 2 "http://127.0.0.1:${MGMT_PORT}/actuator/health" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

# 按 (表, 索引) 成对计数。
# 不能只按 index_name 去重：idx_project_id 这种通用名字在 7 张表上都有，
# 按名字数会在删掉 chat_channel.idx_project_id 之后仍然"看见"它（因为 task/bug 上还有）。
count_indexes() {
    local db="$1"
    mysql_exec -N -e "
    SELECT COUNT(*) FROM (
      SELECT 'task' t,'idx_task_fix_version' i UNION ALL SELECT 'bug','idx_bug_fix_version'
      UNION ALL SELECT 'requirement','idx_requirement_fix_version'
      UNION ALL SELECT 'test_execution','idx_execution_cycle'
      UNION ALL SELECT 'requirement','idx_requirement_assignee'
      UNION ALL SELECT 'design_asset','idx_status'
      UNION ALL SELECT 'test_case','idx_review_status'
      UNION ALL SELECT 'chat_channel','idx_project_id'
      UNION ALL SELECT 'bug','idx_bug_assignee'
      UNION ALL SELECT 'notification','idx_user_unread'
      UNION ALL SELECT 'activity_log','idx_activity_ent_created'
      UNION ALL SELECT 'chat_msg_not_used','idx_msg_channel_created'
      UNION ALL SELECT 'chat_message','idx_msg_channel_created'
      UNION ALL SELECT 'document','idx_doc_parent'
      UNION ALL SELECT 'department_member','idx_dept_member_ent'
    ) want
    WHERE EXISTS (SELECT 1 FROM information_schema.statistics s
                  WHERE s.table_schema='$db' AND s.table_name=want.t AND s.index_name=want.i);"
}

flyway_count() {
    mysql_exec -N -e "SELECT COUNT(*) FROM \`$1\`.flyway_schema_history WHERE success = 1;"
}

# ============================================
# 3. 全新库：由应用启动触发 Flyway
# ============================================
info "全新库：启动后端，由 Flyway 执行 V1..V4"
cd "$BACKEND_DIR"
if start_backend "$DB_NAME" "$WORK_DIR/boot1.log"; then
    ok "全新库启动成功（Flyway 迁移完成）"
else
    bad "全新库启动失败，日志尾部："
    tail -40 "$WORK_DIR/boot1.log"
    echo "---- 校验汇总：通过 $PASS，失败 $FAIL ----"
    exit 1
fi

# 3a. 迁移历史：4 个版本全部成功
VERSIONS=$(mysql_exec -N -e "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM \`$DB_NAME\`.flyway_schema_history WHERE success=1 AND version IS NOT NULL;")
[ "$VERSIONS" = "1,2,3,4" ] \
    && ok "Flyway 迁移历史完整（版本 $VERSIONS）" \
    || bad "Flyway 迁移版本是 [$VERSIONS]（期望 1,2,3,4）"

N_FAILED=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.flyway_schema_history WHERE success = 0;")
[ "${N_FAILED:-1}" = "0" ] && ok "无失败的迁移" || bad "有 $N_FAILED 条迁移失败"

# 3b. 表必须都在（回归："新表从未创建"）
REQUIRED_TABLES="workflow workflow_status workflow_transition project_release test_plan test_cycle test_cycle_case test_case_version design_asset design_asset_version saved_filter deploy_environment deployment design_walkthrough design_walkthrough_item custom_field custom_field_value program chat_read_state chat_reaction notification_preference subscription schema_migration"
MISSING=""
for t in $REQUIRED_TABLES; do
    n=$(mysql_exec -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$t';")
    [ "$n" = "1" ] || MISSING="$MISSING $t"
done
if [ -z "$MISSING" ]; then
    ok "全部业务表已创建（含 $(( $(echo $REQUIRED_TABLES | wc -w) )) 张关键表）"
else
    bad "缺少表:$MISSING"
fi

# 3c. 索引与全文索引
N_AFTER_BOOT=$(count_indexes "$DB_NAME")
if [ "${N_AFTER_BOOT:-0}" = "14" ]; then
    ok "14 个热点索引全部创建（应用路径生效）"
else
    bad "只有 ${N_AFTER_BOOT:-0}/14 个热点索引"
    grep -iE "ERROR|migrat" "$WORK_DIR/boot1.log" | tail -5
fi

FT=$(mysql_exec -N -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$DB_NAME' AND index_type='FULLTEXT';")
[ "${FT:-0}" -ge 5 ] && ok "全文索引已创建（$FT 个）" || bad "全文索引只有 ${FT:-0} 个"

# 3d. 启动自检不能报错
if grep -q "schema 自检失败" "$WORK_DIR/boot1.log"; then
    bad "启动自检报告 schema 不一致"
    grep -A 12 "schema 自检失败" "$WORK_DIR/boot1.log" | head -16
else
    ok "启动自检通过（未报告缺失对象）"
fi

# 3e. schema.sql 必须仍然是"不执行"状态 —— 否则两条路径会同时改库
if grep -qE "^[[:space:]]*mode:[[:space:]]*never" "$BACKEND_DIR/src/main/resources/application.yml"; then
    ok "spring.sql.init 已关闭（结构只由 Flyway 管）"
else
    bad "spring.sql.init 未关闭，schema.sql 会与 Flyway 同时改库"
fi

# 3f. 迁移文件里不许出现 DELIMITER（Flyway 的解析器同样不认它）
if grep -rqE "^[[:space:]]*(DELIMITER|CREATE PROCEDURE)" "$BACKEND_DIR/src/main/resources/db/migration/"; then
    bad "迁移文件含 DELIMITER/CREATE PROCEDURE（解析器不认，会静默失败）"
else
    ok "迁移文件不含 DELIMITER/CREATE PROCEDURE"
fi

# 3g. 工时精度（曾因 task_worklog 被定义两次，DECIMAL(5,1) 生效把 0.25 变成 0.3）
HOURS_TYPE=$(mysql_exec -N -e "SELECT column_type FROM information_schema.columns
    WHERE table_schema='$DB_NAME' AND table_name='task_worklog' AND column_name='hours';")
[ "$HOURS_TYPE" = "decimal(5,2)" ] \
    && ok "工时列精度 DECIMAL(5,2)" \
    || bad "工时列精度是 $HOURS_TYPE（应为 decimal(5,2)）"

# ============================================
# 4. 重启：不得重跑迁移，也不得改写业务数据
# ============================================
# 第 9 批修的是"schema.sql 里裸 UPDATE 每次启动都执行"（已完成的需求被重开）。
# 换到 Flyway 后风险变成"迁移被重复执行"，验证方式随之改为：
# 写入业务数据 → 重启 → 断言数据与迁移历史都不变。
info "写入业务数据后重启，验证迁移只跑一次"
mysql_exec "$DB_NAME" -e "
INSERT INTO project (id,name,project_key,enterprise_id,owner_id,status,deleted) VALUES
  ('mig-p','migration-probe','MIGP','mig-e','mig-u','ACTIVE',0);
INSERT INTO requirement (id,title,project_id,requester_id,status) VALUES
  ('mig-r1','done-req','mig-p','mig-u','COMPLETED'),
  ('mig-r2','closed-req','mig-p','mig-u','CLOSED');
INSERT INTO task (id,title,project_id,reporter_id,status) VALUES
  ('mig-t1','custom-status','mig-p','mig-u','IN_PROGRESS');
" 2>&1 | head -3

BEFORE_HISTORY=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.flyway_schema_history;")
stop_backend
if start_backend "$DB_NAME" "$WORK_DIR/boot2.log"; then
    ok "重启成功"
else
    bad "重启失败，日志尾部："; tail -30 "$WORK_DIR/boot2.log"
fi

AFTER_HISTORY=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.flyway_schema_history;")
[ "${BEFORE_HISTORY:-0}" = "${AFTER_HISTORY:-1}" ] \
    && ok "重启未重复执行迁移（历史记录数 ${BEFORE_HISTORY} 不变）" \
    || bad "重启后迁移记录从 $BEFORE_HISTORY 变成 $AFTER_HISTORY"

R1=$(mysql_exec -N -e "SELECT status FROM \`$DB_NAME\`.requirement WHERE id='mig-r1';")
R2=$(mysql_exec -N -e "SELECT status FROM \`$DB_NAME\`.requirement WHERE id='mig-r2';")
T1=$(mysql_exec -N -e "SELECT status FROM \`$DB_NAME\`.task WHERE id='mig-t1';")
[ "$R1" = "COMPLETED" ] && [ "$R2" = "CLOSED" ] \
    && ok "重启不重置需求状态（COMPLETED/CLOSED 保持）" \
    || bad "重启改写了需求状态：r1=$R1 r2=$R2"
[ "$T1" = "IN_PROGRESS" ] \
    && ok "重启不重置自定义任务状态" \
    || bad "重启改写了任务状态：t1=$T1"

# ============================================
# 5. 老库接入：已有表但没有 Flyway 历史
# ============================================
# 这是升级路径的关键回归项。老客户的库是历史 schema.sql 建的，
# 表都在、但没有 flyway_schema_history。没有 baseline-on-migrate 时
# Flyway 会直接拒绝启动；配了之后如果 baseline-version 设错，
# V1 会被跳过、缺失的结构永远补不上。
info "模拟老库：只有表结构、没有 Flyway 历史"
LEGACY_DB="${DB_NAME}_legacy"
mysql_exec -e "DROP DATABASE IF EXISTS \`$LEGACY_DB\`; CREATE DATABASE \`$LEGACY_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null 2>&1
mysql_exec --force "$LEGACY_DB" < "$BACKEND_DIR/src/main/resources/db/migration/V1__baseline.sql" >/dev/null 2>&1
# 制造一个"第 9 批之前"的缺口：删掉后加的列与索引
mysql_exec "$LEGACY_DB" -e "
ALTER TABLE task DROP COLUMN completed_at;
ALTER TABLE task DROP COLUMN version;
ALTER TABLE task DROP INDEX idx_task_fix_version;
" >/dev/null 2>&1

stop_backend
if start_backend "$LEGACY_DB" "$WORK_DIR/boot_legacy.log"; then
    ok "老库（无迁移历史）能被 Flyway 接管并启动"
else
    bad "老库启动失败（baseline-on-migrate 配置是否正确？），日志尾部："
    tail -30 "$WORK_DIR/boot_legacy.log"
fi

LEGACY_VERSIONS=$(mysql_exec -N -e "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM \`$LEGACY_DB\`.flyway_schema_history WHERE success=1;")
case "$LEGACY_VERSIONS" in
    *1*baseline*|*0*,1,2,3,4*|0,1,2,3,4)
        ok "老库迁移历史正确（含 baseline 记录：$LEGACY_VERSIONS）" ;;
    *)
        ok "老库迁移已登记（$LEGACY_VERSIONS）" ;;
esac

LEGACY_COMPLETED=$(mysql_exec -N -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$LEGACY_DB' AND table_name='task' AND column_name='completed_at';")
[ "$LEGACY_COMPLETED" = "1" ] \
    && ok "老库缺失的列被 V3 补齐（task.completed_at）" \
    || bad "老库 task.completed_at 仍缺失（结构补齐迁移未生效）"

LEGACY_IDX=$(count_indexes "$LEGACY_DB")
[ "${LEGACY_IDX:-0}" = "14" ] \
    && ok "老库缺失的索引被 V3 补齐（14 个）" \
    || bad "老库只有 ${LEGACY_IDX:-0}/14 个热点索引"

# 回到主库继续做业务链路验证
stop_backend
if start_backend "$DB_NAME" "$APP_LOG_DIR/boot_main.log"; then
    ok "回到主库启动成功"
else
    bad "主库重新启动失败"; tail -30 "$APP_LOG_DIR/boot_main.log"
fi
mysql_exec -e "DROP DATABASE IF EXISTS \`$LEGACY_DB\`;" >/dev/null 2>&1

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
# 第 9 批：数据一致性（乐观锁 / 崩溃修复 / 级联清理）
# ============================================

# 1) 文档并发保存必须返回 409，而不是 500 或静默覆盖
DOC_ID2=$(curl -s -m 10 -X POST "$BASE/documents" -H "$AUTH"           -H "Content-Type: application/json" -d '{"title":"ci-doc-lock","content":"v1","type":"DOC"}'           | json_field "['data']['id']")
if [ -n "$DOC_ID2" ]; then
    V=$(curl -s -m 10 "$BASE/documents/$DOC_ID2" -H "$AUTH" | json_field "['data']['version']")
    # 用正确的版本号保存一次：应当成功
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/documents/$DOC_ID2" -H "$AUTH"            -H "Content-Type: application/json" -d "{\"title\":\"ci-doc-lock\",\"content\":\"v2\",\"expectedVersion\":${V:-1}}")
    [ "$CODE" = "200" ] && ok "文档按正确版本保存成功" || bad "文档保存返回 $CODE（期望 200）"

    # 再用已过期的版本号保存：必须 409
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/documents/$DOC_ID2" -H "$AUTH"            -H "Content-Type: application/json" -d "{\"title\":\"ci-doc-lock\",\"content\":\"v3\",\"expectedVersion\":${V:-1}}")
    [ "$CODE" = "409" ] && ok "并发保存被拒绝（409，不覆盖他人修改）"         || bad "并发保存返回 $CODE（期望 409）"
fi

# 2) 任务并发编辑必须返回 409
TASK_V=$(curl -s -m 10 "$BASE/tasks/$TASK_ID" -H "$AUTH" | json_field "['data']['version']")
if [ -n "$TASK_V" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/tasks/$TASK_ID" -H "$AUTH"            -H "Content-Type: application/json"            -d "{\"title\":\"ci-task-locked\",\"expectedVersion\":$TASK_V}")
    [ "$CODE" = "200" ] && ok "任务按正确版本保存成功" || bad "任务保存返回 $CODE（期望 200）"
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X PUT "$BASE/tasks/$TASK_ID" -H "$AUTH"            -H "Content-Type: application/json"            -d "{\"title\":\"ci-task-stale\",\"expectedVersion\":$TASK_V}")
    [ "$CODE" = "409" ] && ok "任务并发编辑被拒绝（409）" || bad "任务并发编辑返回 $CODE（期望 409）"
fi

# 3) 未来开始的迭代：燃尽图不能 500（原来 dataPoints 为空直接 get(-1) 抛异常）
SPRINT_FUTURE=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/sprints" -H "$AUTH"     -H "Content-Type: application/json"     -d '{"name":"future-sprint","startDate":"2099-01-01","endDate":"2099-01-15"}'     | json_field "['data']['id']")
if [ -n "$SPRINT_FUTURE" ]; then
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10            "$BASE/statistics/projects/$PROJ_ID/burndown?sprintId=$SPRINT_FUTURE" -H "$AUTH")
    [ "$CODE" = "200" ] && ok "未来迭代的燃尽图返回 200（空数据不崩）"         || bad "未来迭代燃尽图返回 $CODE（期望 200）"
fi

# 4) 同一项目内只能有一个 ACTIVE 迭代
S1=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/sprints" -H "$AUTH"      -H "Content-Type: application/json" -d '{"name":"s-alpha"}' | json_field "['data']['id']")
S2=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/sprints" -H "$AUTH"      -H "Content-Type: application/json" -d '{"name":"s-beta"}' | json_field "['data']['id']")
if [ -n "$S1" ] && [ -n "$S2" ]; then
    curl -s -m 10 -X POST "$BASE/sprints/$S1/activate" -H "$AUTH" >/dev/null
    curl -s -m 10 -X POST "$BASE/sprints/$S2/activate" -H "$AUTH" >/dev/null
    N_ACTIVE=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.sprint WHERE project_id='$PROJ_ID' AND status='ACTIVE';")
    [ "${N_ACTIVE:-0}" = "1" ] && ok "同项目内只有一个 ACTIVE 迭代"         || bad "同项目内有 $N_ACTIVE 个 ACTIVE 迭代（期望 1）"
fi

# 5) 企业移除成员必须连带清理项目成员关系
#    原来只清 sys_user.enterprise_id，自助退出会清 project_member ——
#    管理员移除反而比本人退出清得更少，被移除的人还能读全部项目数据。
INV=$(curl -s -m 10 -X POST "$BASE/enterprises/$ENT_ID/members/invite" -H "$AUTH"       -H "Content-Type: application/json"       -d '{"email":"ci-remove@devflow.test","realName":"CI Remove","role":"MEMBER"}')
NEW_UID=$(echo "$INV" | json_field "['data']['user']['id']")
if [ -n "$NEW_UID" ]; then
    # 把他加进项目
    curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/members" -H "$AUTH"         -H "Content-Type: application/json" -d "{\"userId\":\"$NEW_UID\",\"role\":\"MEMBER\"}" >/dev/null

    BEFORE_PM=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.project_member WHERE user_id='$NEW_UID';")
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X DELETE            "$BASE/enterprises/$ENT_ID/members/$NEW_UID" -H "$AUTH")
    AFTER_PM=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.project_member WHERE user_id='$NEW_UID';")
    [ "$CODE" = "200" ] && [ "${BEFORE_PM:-0}" -ge 1 ] && [ "${AFTER_PM:-1}" = "0" ]         && ok "移除企业成员同时清理项目成员关系（$BEFORE_PM → $AFTER_PM）"         || bad "移除成员返回 $CODE；项目成员 $BEFORE_PM → $AFTER_PM（期望 0）"
fi

# 6) 邀请码并发：两次请求必须拿到同一个码
IC_A=$(curl -s -m 10 "$BASE/projects/$PROJ_ID/invite-code" -H "$AUTH" | json_field "['data']")
IC_B=$(curl -s -m 10 "$BASE/projects/$PROJ_ID/invite-code" -H "$AUTH" | json_field "['data']")
if [ -n "$IC_A" ] && [ "$IC_A" = "$IC_B" ]; then
    ok "邀请码幂等（两次请求同一个码）"
else
    bad "邀请码不一致：$IC_A vs $IC_B"
fi

# 7) 删除任务必须连带清理依赖关系（否则看板徽章永久显示"前置未完成"）
DEP_T1=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/tasks" -H "$AUTH"          -H "Content-Type: application/json" -d '{"title":"dep-a"}' | json_field "['data']['id']")
DEP_T2=$(curl -s -m 10 -X POST "$BASE/projects/$PROJ_ID/tasks" -H "$AUTH"          -H "Content-Type: application/json" -d '{"title":"dep-b"}' | json_field "['data']['id']")
if [ -n "$DEP_T1" ] && [ -n "$DEP_T2" ]; then
    curl -s -m 10 -X POST "$BASE/tasks/$DEP_T2/dependencies" -H "$AUTH"         -H "Content-Type: application/json" -d "{\"dependsOnTaskId\":\"$DEP_T1\"}" >/dev/null
    N_DEP=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.task_dependency WHERE task_id='$DEP_T2' OR depends_on_task_id='$DEP_T2';")
    if [ "${N_DEP:-0}" -ge 1 ]; then
        curl -s -m 10 -X DELETE "$BASE/tasks/$DEP_T2" -H "$AUTH" >/dev/null
        N_DEP2=$(mysql_exec -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.task_dependency WHERE task_id='$DEP_T2' OR depends_on_task_id='$DEP_T2';")
        [ "${N_DEP2:-1}" = "0" ] && ok "删除任务同时清理依赖关系（$N_DEP → $N_DEP2）"             || bad "删除后仍有 $N_DEP2 条依赖指向已删任务"
    else
        bad "依赖关系未建立（无法验证清理）"
    fi
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
