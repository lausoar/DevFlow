# 第 2 批：地基改造

> 对应 [AUDIT_REPORT_2026-11.md](./AUDIT_REPORT_2026-11.md) 的第 2 批"地基改造"。
> 这一批不加业务功能，而是把后面所有功能都要踩在上面的六块地基补齐：
> 可配置工作流、版本/发布、测试计划、设计稿协作、结构化查询、构建部署。

## 一、可配置工作流（最重要的一块）

### 为什么先做这个

状态原本硬编码在三处同源定义里：`Task` 实体注释、`schema.sql` 的列默认值、前端 `types/index.ts` 的
字面量联合类型。客户流程差异只有一个解法——改代码发版。更麻烦的是**报表口径**：
燃尽、速度、健康度、看板进度全部按 `"DONE"` 这样的状态码字符串过滤，
一旦新增一个状态，它既不算完成也不算进行中，完成率会被静默稀释。

### 数据模型

```
workflow（project_id 为 NULL = 全局模板）
  └── workflow_status（code / name / category / color / sort / isInitial / isTerminal）
  └── workflow_transition（from → to）
```

**关键约束：每个状态必须归入 `TODO / IN_PROGRESS / DONE` 三类。**
这是"团队可自定义状态"与"报表口径统一"之间的调和器——所有聚合都按 `category` 走，
而不是按状态码。

### 生效规则

- `WorkflowService.resolve(projectId, issueType)`：项目自定义优先，否则全局模板，都没有则按内置默认自动初始化；
- 内置默认与改造前**完全一致**（任务四态、缺陷六态、需求三态），所以存量行为不变；
- 首次改写会先把全局模板整体复制成本项目快照（`ensureProjectWorkflow`），之后的改动只影响本项目；
- 进程内按 `projectId:issueType` 缓存，配置改动后 `reload()` 清缓存。

### 已接入的判定点

| 位置 | 改造后 |
|------|--------|
| `TaskController.updateStatus` | 状态白名单换成 `assertTransitionAllowed`（流转表校验） |
| `TaskController` 的 TASK_DONE 触发 | 按 category 判定"跨入 DONE 类"，DONE→DONE 不再重复触发 |
| `BugController.changeStatus` | 同上，走缺陷工作流 |
| `RequirementServiceImpl` | 创建用 `initialStatus`，更新走流转校验 |
| `StatisticsServiceImpl` 企业统计 / 健康度 / 成员负载 / 燃尽 / 甘特 / 趋势 | 全部改为按 `statusCodesInCategory(...)` 聚合 |
| `DashboardController` 项目仪表盘 | 同上，分布按实际状态码统计 |
| `SprintController.complete` | "未完成任务"按 category != DONE 判定 |
| `TaskExtraController` 依赖徽章 / 依赖视图 | done 判定按 category；SQL 的 `<> 'DONE'` 改为传入状态码集合 |
| `TaskServiceImpl` 通知文案 | 状态名从工作流配置取 |

### 前端

- 看板列不再来自常量 `COLUMNS`，而是 `useWorkflow(projectId, 'TASK')`；
  加载失败或未配置时回落到内置默认列，不会白屏；
- 卡片上的"推进/回退"按钮由工作流的 `next` 驱动，不再写死 `TODO→DOING→TESTING→DONE`；
- 历史数据里出现的未知状态码会单独成一列显示，而不是被静默丢弃。

## 二、版本 / 发布

新增 `project_release`（版本号项目内唯一），并在 `task / bug / requirement` 上加 `fix_version_id`。

- **版本是聚合键**：需求、任务、缺陷按版本聚起来，才能回答"这个版本还差什么才能发"；
- **发布说明自动生成**：按版本汇总需求（新功能/改进）、已完成任务、未完成任务（单列提示）、缺陷修复，
  输出 Markdown 写回 `release_notes`，可人工再编辑；
- **发布是留痕动作**：`markReleased` 写入发布时间与操作人，已发布不可改状态、不可删除；
- 前端新增「版本」标签页：发布前检查（未完成项计数）+ 一键生成发布说明 + 标记发布。

## 三、测试计划与轮次

原本只有项目级用例平铺列表 + 散落的 `test_execution`，回答不了"这一轮测了什么、进度多少"。

新增 `test_plan → test_cycle → test_cycle_case`，执行记录增加 `cycle_id`：

- 用例可以按模块圈进某个轮次；
- 轮次进度 = 已执行用例数 / 轮次用例总数，通过率按**每个用例在本轮的最新一次结果**计算（重复执行不会刷高通过率）；
- 用例列表支持 `cycleId` 过滤，只看本轮范围的用例与执行情况。

同时补了两个此前最伤追溯的缺口：

- **用例版本快照**：`test_case.version` + `test_case_version` 表，编辑即快照上一版；
  执行记录带 `case_version`，失败盘点才能回答"当时按哪一版步骤执行的"；
- **用例评审**：`review_status`（DRAFT / PENDING / APPROVED / REJECTED）+ `/review` 端点。

## 四、设计稿协作

定位明确：**平台不自研画布**，只做编排层——存指针、存版本、存评审状态。

`design_asset`（provider + fileKey + nodeId + embedUrl + isPrimary + status）
+ `design_asset_version`（版本快照链接）。

- 一个需求可挂多张稿，但**主设计稿唯一**（新增主稿时自动降级旧主稿）；
- 评审状态机：`DESIGNING → IN_REVIEW → APPROVED / CHANGES_REQUESTED`，打回可复审，APPROVED 后改稿回到 DESIGNING；
- 设计通过会回写需求的 `design_status`，作为"开发就绪"判定的依据；
- 项目维度返回设计覆盖率（有设计稿的需求数 / 需求总数）与待评审数量。

## 五、结构化查询与保存筛选器

`POST /query` 接受受限表达式，例如：

```
status IN (TODO, DOING) AND priority = HIGH AND dueDate <= endOfWeek() AND assignee = currentUser()
```

**安全设计（这是重点）**：

- 不解析 SQL，只解析"字段 运算符 值"三元组；
- 字段、运算符、函数全部白名单（未知字段直接 400）；
- 值走 MyBatis 参数绑定，并额外拒绝含 `; -- /* ' \`` 等 SQL 片段特征的值，让误用显式报错；
- 可见项目由 `ProjectAccessService.visibleProjectIds` **强制**注入，表达式无论如何都突破不了权限边界。

支持的函数：`currentUser()`、`now()` / `today()`、`startOfWeek()` / `endOfWeek()`、`startOfMonth()` / `endOfMonth()`。

保存筛选器（`saved_filter`）：保存前先解析一次（表达式非法就不让存），支持项目共享。

## 六、构建与部署

### Dockerfile（三端）

- **backend**：多阶段（maven 构建 → JRE 运行），非 root，依赖层缓存，`HEALTHCHECK` 走 actuator；
- **frontend**：`next.config.ts` 开启 `output: 'standalone'`，运行层只拷 standalone + static + public；
- **ai-service**：非 root，模型缓存挂 `/data`（否则每次重建都要重下 bge 模型），容器内监听 `0.0.0.0`。

### docker-compose.prod.yml

与开发用的 `docker-compose.yml` 分开：三端应用 + 基础设施 + Nginx 入口；
必需凭据用 `${VAR:?必须设置}` 强制校验，缺一个就起不来，避免用默认口令上生产。

### Nginx（`deploy/nginx/nginx.conf`）

- `/api/` → backend，`/ai-api/` → ai（剥前缀），`/ws` → frontend（WebSocket upgrade + 3600s 超时）；
- 全局安全响应头（nosniff / DENY / Referrer-Policy）；
- 443 段已写好模板，提供证书后取消注释即可。

### CI（`.github/workflows/ci.yml`）

后端编译 + 单测（排除依赖真实服务的那一个）、前端 tsc + build、AI 服务 ruff + import 检查、
三端镜像可构建性验证。Lint 暂时不阻断（存量 16 个错误，后续批次清）。

### Flyway

`db/migration/V1__baseline.sql`（空基线）+ `V2__batch2_foundation.sql`（幂等建表）。

**这里做了一次刻意取舍**：列级变更（`ALTER TABLE ADD COLUMN`）仍留在 `schema.sql` 末尾，
因为项目现有的迁移方式就是 `spring.sql.init` + `continue-on-error`，
在没有"导出基线 + 清空历史表"的一次切换之前，两边同时存在才有过渡空间。
切换步骤写在 `V1__baseline.sql` 顶部的注释里。**本批没有引入 flyway 依赖**——
引入它需要同时改 `application.yml` 关闭 `sql.init`，那是一次不可回滚的部署变更，应单独评估。

## 七、验证

- 后端：`mvn compile` 通过；单测 **63 项**全绿（新增 `QueryExpressionParserTest` 12 项、`WorkflowDefaultsTest` 3 项）；
- 前端：`tsc --noEmit` 通过；lint 错误数保持 16（均为存量，新文件 0 错误）；
- AI 服务：`ast.parse` 语法检查通过、模块可 import；
- **未做运行时验证**：本机无 MySQL/Redis 容器，三端未一起启动；Flyway 未实际执行（见上）。
