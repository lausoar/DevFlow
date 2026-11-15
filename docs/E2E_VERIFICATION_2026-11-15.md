# 端到端验证报告（第 0-4 批）

> 验证时间：2026-11-15 · 环境：Windows + Docker Desktop（MySQL 8.0.46 / Redis 7 / MinIO / ChromaDB 均已在运行）
> 方式：在 8081/9091 端口启动本轮构建的后端，对一个真实存量库（40 张表、有历史数据）执行验证。
> 结论：**发现 5 个缺陷，全部已修复并复验通过**。其中 1 个是阻断性的（新功能整体不可用）。

## 一、阻断级缺陷：新表从未被创建

**现象**：`GET /projects/{id}/workflow`、`/releases`、`/environments` 全部返回 500，
日志为 `Table 'devflow.workflow' doesn't exist`。

**根因**：第 2、3、4 批把新表定义写进了 `db/migration/V2__*.sql` / `V3__*.sql`，
但 **Flyway 并未接入**（pom 里没有依赖）。实际执行建表的只有 `schema.sql`，
而 `schema.sql` 里只有指向 `project_release` / `design_asset` / `deployment` 的
`ALTER TABLE`——表本身从来没被创建过。

这正是我在第 2 批文档里写的"过渡策略"造成的：当时把建表放进 migration 目录、
却把列变更留在 `schema.sql`，结果是**没有任何执行路径会创建新表**。
静态检查（编译、类型检查、单测）完全发现不了这个问题。

**修复**：把 V2/V3 的 `CREATE TABLE` 段落并入 `schema.sql`（migration 文件保留，作为未来 Flyway 的权威定义）。
复验：15 张新表全部创建成功，工作流/版本/环境接口恢复正常。

## 二、`schema.sql` 幂等性缺陷

**现象**：在干净库上执行 `schema.sql`，首次即报 12 条 `Duplicate column` / `Duplicate key`；
`CREATE INDEX` 语句第二次执行必然失败。

**根因**：`ALTER TABLE ... ADD COLUMN` 与 `CREATE INDEX` 都不是幂等的，
项目靠 `continue-on-error: true` 吞掉错误——但这也意味着**真正的失败同样被吞掉**。
此外有 3 条 `CREATE INDEX` 完全裸奔（连 `continue-on-error` 之外的错误处理都没有）。

**修复**：把 7 条索引改为幂等存储过程（检查 `information_schema.statistics` 后再建）。
`ALTER ADD COLUMN` 保持原样（项目既有的 `continue-on-error` 约定），但在验证报告里记为已知风险。

## 三、历史数据状态值未归一

**现象**：`devflow.task` 里存在 `IN_PROGRESS`（旧代码用的状态值），
它与 schema/前端约定的 `TODO/DOING/TESTING/DONE` 不一致。
工作流的 dashboard 分布里出现 `IN_PROGRESS: 1` 这样一个"工作流之外"的桶。

**根因**：审计报告 §C7 记录过这个问题，但前几批只改了"新数据写入"的路径，没清理存量。

**修复**：`schema.sql` 增加幂等归一 `UPDATE task SET status='DOING' WHERE status='IN_PROGRESS'`（`IN_REVIEW` → `TESTING`），并在当前库执行。

## 四、审计哈希链有三处实现错误（叠加后功能完全不可用）

**现象**：链校验对**每条新记录**都报"内容与哈希不符"。

三个独立的根因，修复一个才暴露下一个：

1. **时间戳精度**：`activity_log.created_at` 是 `DATETIME`（秒精度），
   但插入时用的是带纳秒的 `LocalDateTime.now()`。MySQL 对小数秒**四舍五入**
   （37.9 → 38），哈希用内存值 37 算、校验读回 38 → 每条都不匹配。
   → 修复：落库前统一 `truncatedTo(SECONDS)`。
2. **校验按时间排序**：同一秒内的多条记录，`ORDER BY created_at` 顺序不稳定，
   导致合法的链被判为断链。→ 修复：改为**顺着 prev_hash 指针遍历**，不依赖时间排序。
3. **链分叉**：「取上一条记录」用 `ORDER BY created_at DESC LIMIT 1`，
   同秒内两条记录会取到同一个前驱，链分叉。分叉后**删除中间记录无法被检测**
   （实测：删掉一条后校验仍报"通过"）。
   → 修复：进程内维护 `chainHead`，写入串行推进；冷启动时从库中按"未被引用为 prev_hash"恢复。

**复验**：
- 正向：5 条同秒写入 → `valid=true, checkedRecords=5`
- 篡改中间记录 → `valid=false`，定位到具体 id
- 删除中间记录 → `valid=false`，"有 1 条记录不在这条链上"

## 五、审计 CSV 导出 500

**现象**：`GET /enterprises/{id}/audit-logs/export` 抛
`IllegalStateException: getOutputStream() has already been called for this response`。

**根因**：先写 BOM 到 `getOutputStream()`，再调 `getWriter()` —— Servlet 规范不允许两者混用。

**修复**：统一走 writer 并写入 `﻿`。复验：HTTP 200，返回带 BOM 的 CSV，含记录哈希列。

## 六、验证通过的项（无缺陷）

| 项目 | 结果 |
|------|------|
| 未认证访问受保护接口 | 401 ✓ |
| 越权改项目（不存在的 id） | 404 ✓ |
| 工作流默认模板自动初始化 | 任务四态 + 分类正确 ✓ |
| 任务创建初始状态来自工作流 | `TODO` ✓ |
| 非法状态写入 | 400 ✓ |
| 合法流转 TODO→DONE | 200 ✓ |
| 看板统计按工作流状态聚合 | `{TODO, DOING, TESTING, DONE}` ✓ |
| 版本创建 / 重复版本号 | 200 / 409 ✓ |
| 发布说明生成 / 发布 / 重复发布 | 200 / 200 / 409 ✓ |
| 生产环境部署进入待审批 | `PENDING_APPROVAL` ✓ |
| 自审批拦截 | 403 ✓ |
| 环境互斥（并发部署） | 409 ✓ |
| DORA（无样本） | 返回 `hasProdEnvironment=true` + 小样本提示（不返回 0）✓ |
| 测试计划 → 轮次 → 用例 → 执行 | 全链路 200，进度 1/1、通过率 100% ✓ |
| 执行记录带 case_version / cycle_id | 均正确写入 ✓ |
| 设计稿关联 / 非法流转 / 合法链路 | 200 / 400 / 200 ✓ |
| 设计通过回写需求 design_status | `APPROVED` ✓ |
| 结构化查询（JQL 式） | 200 ✓ |
| 注入尝试 `; DROP TABLE` | 400（拒绝，未执行）✓ |
| 未知字段 `password` | 400 ✓ |
| TOTP 绑定流程（错误码 400 / 正确码 200） | ✓ |
| 两段式登录 + 凭证单次使用 | 401 → 401 → 200 ✓ |
| TOTP 实现 vs RFC 6238 向量 | `t=59 → 287082` ✓ |
| 密码策略（弱密码返回全部问题） | ✓ |
| 成员邮箱脱敏（有/无管理权限对比） | `m***@devflow.com` vs `member1@devflow.com` ✓ |
| Actuator 独立端口 | 9091 health UP + prometheus 可访问 ✓ |
| Prometheus 端点受认证保护 | 401（业务端口未暴露）✓ |
| AI 服务匿名访问 | 401 ✓ |
| AI 服务令牌 + 无检索范围 | 400 ✓ |
| 前端生产构建（standalone） | 成功，`.next/standalone/server.js` 存在 ✓ |
| 后端单测 | 81 项全绿 ✓ |

## 七、未验证 / 遗留

- **Flyway 仍未接入**：本次把建表并入 `schema.sql` 解决了"表不存在"，
  但迁移仍靠 `continue-on-error` 吞错。正式接入需要一次"导出基线 + 关掉 sql.init"的独立变更。
- **`ALTER TABLE ADD COLUMN` 不幂等**：重复执行会产生被静默吞掉的错误。
  当前靠 `continue-on-error` 保证启动不失败，但"静默失败"本身是隐患。
- **SSO / SCIM**：需要真实 IdP，未验证。
- **禁用成员后 token 立即失效**：未实现。
- **多实例场景**：登录防爆破计数、TOTP 挑战凭证、审计链头均为进程内状态，未验证多实例。
- **前端未做浏览器端交互验证**：只验证到 `npm run build` 成功与 `tsc` 通过。

## 八、这次验证说明了什么

修复的 5 个缺陷里，**没有任何一个能被编译、类型检查或单元测试发现**：

- 表不存在 → 只有连真库才暴露
- 幂等性 → 只有重复执行才暴露
- 存量数据状态值 → 只有真实数据才暴露
- 审计链的三个错误 → 只有真实写入再读回才暴露
- Servlet 流冲突 → 只有真发一次请求才暴露

这不是"测试写得不够"，而是**集成层面的正确性无法靠单测覆盖**。
后续每次改动涉及 SQL、外部 IO、Servlet 层时，都值得跑一遍这里的验证清单。
