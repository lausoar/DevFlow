# 全功能测试与修复清单（2026-11-15）

> 验证范围：前端 12 个顶层页面 + 项目详情 8 个标签页的全部按钮/操作，后端 237 个 REST 端点，
> AI 服务 9 个端点，以及 3 套自动化测试（HTTP 冒烟 191 + 组织/回收站/文件 45 + 部署/权限 30）、
> 页面前端巡检 28 个路由、后端单测 90 个、`scripts/verify-integration.sh` 94 项。
> 环境：本机 MySQL 8 / Redis / MinIO / ChromaDB，后端 :8080，AI :8000，前端 :3000。

---

## 一、修复的问题（11 项）

### 1. 【阻断】AI 服务所有接口 401 —— 前端「AI 中心」全功能不可用

**现象**：`/chat/completions`、`/agent/*`、`/rag/*`、`/assistant/*` 全部 401，
页面上 AI 对话、Agent、知识库、Code Review 四个标签页一律失败。

**根因**：Java 侧 JJWT 的 `signWith(SecretKey)` 按密钥长度选算法——
本机 53 字节密钥签出来的是 **HS384**；而 AI 服务只允许 `algorithms=["HS256"]`。
两侧 HMAC 密钥相同也无法验签。

**修复**：`ai-service/app/core/auth.py` 放开 HMAC 家族三种算法（HS256/384/512），
具体用哪种仍由 Token 头部声明，非 HMAC 一律拒绝。

**复验**：`alg=HS384` 的 Token 现在返回 200，AI 对话/Agent/知识库/Code Review 全部可用。

---

### 2. 【阻断】后端从不读取 `.env` —— 按 README 配置的 JWT_SECRET 根本不生效

**现象**：即使 `ai-service/.env` 里填了与后端一致的密钥，AI 侧仍然 401。

**根因**：README 让用户 `cp .env.example .env` 填 `JWT_SECRET`，
但 Spring 本身不认 `.env` 文件。后端拿不到密钥 → 启动时生成一次性随机密钥，
与 AI 服务的 `.env` 密钥不匹配。用户完全无从判断。

**修复**：`application.yml` 增加
`spring.config.import: optional:file:../.env[.properties]`（并覆盖 `./.env`），
并在仓库根补了本机开发用 `.env`（已被 `.gitignore` 忽略）。`optional:` 保证
CI/生产环境没有该文件时照常启动。

**复验**：不设任何环境变量直接 `mvn spring-boot:run`，签出的 Token 即可被 AI 服务验签。

---

### 3. 【高】AI Agent 运行时直接抛 500

**现象**：`POST /agent/run` 返回 `500 Agent 运行失败: Error code: 401 - api key ****lder is invalid`。

**根因**：`chat_with_tools()` 没有走 `chat()` 那套"未配置 Key → 离线降级"的判断，
拿占位 Key `sk-placeholder` 真去请求厂商，把 401 冒到用户面前。

**修复**：`ai-service/app/services/chat_service.py` 中 `chat_with_tools()` 补上
`_configured()` 判断与异常降级，与 `chat()` 口径一致。

**复验**：未配置 Key 时返回 `model=offline-fallback` 的降级答复，HTTP 200。

---

### 4. 【高】框架层 4xx 全部被映射成 500「系统内部错误」

**现象**（实测）：

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| GET 一个只支持 POST 的地址 | 500 系统内部错误 | 405 该地址不支持 GET 方法 |
| 缺必填 query 参数（`/departments/tree`） | 500 | 400 缺少必填参数: enterpriseId |
| body JSON 类型写错（`enabled: true`） | 500 | 400 请求体格式不正确 |
| 访问不存在的路径 | 500 | 404 接口不存在: /no-such-endpoint |
| 路径参数类型错误（`page=abc`） | 500 | 400 参数 page 取值格式不正确 |

**根因**：`GlobalExceptionHandler` 只处理了业务异常和校验异常，
`HttpRequestMethodNotSupportedException` / `MissingServletRequestParameterException` /
`HttpMessageNotReadableException` / `NoResourceFoundException` 等全落进兜底分支。

**修复**：`GlobalExceptionHandler` 新增 6 个 handler（405/400/400/400/415/404），
提示文案可直接指导用户改正。

---

### 5. 【中】测试用例评审缺字段时 NPE → 500

`PUT /test-cases/{id}/review` 传空 body，`List.of(...).contains(null)` 抛 NPE。
修复：先判空再判合法值 → 400「无效的评审状态」。同类问题在
`GuestServiceImpl.updateFeedbackStatus`、`submitGuestFeedback`（`type` 可为 null）
一并修掉。

---

### 6. 【高】审计哈希链校验把正常数据误报成"被篡改/被删除"

**现象**：企业页「校验链」对绝大多数企业弹出
`⚠️ 审计链异常：找不到链头（prev_hash = GENESIS 的记录），历史记录可能被删除`，
但数据其实一条没动。

**根因**（两个叠加）：

1. 写入侧链头是**全局单例**，所有企业共用一条链——第二个企业的第一条记录
   `prev_hash` 指向第一个企业的记录，校验"某企业的链"时永远找不到本企业链头；
2. 校验侧用 `GENESIS.equals(prevHash)` 找链首，而按企业切分后本企业链首的
   `prev_hash` 是别的企业的哈希，同样判不出来。

**修复**：`AuditServiceImpl`

- 链头改为按企业维护（`Map<enterpriseId, head>`），`loadChainHead` 加上企业过滤；
- 校验改为「`prev_hash` 不是本企业任何一条记录的 `record_hash` 即为链首」，
  并对"多条链首（分叉）"单独报错；
- 链首前驱不是 GENESIS 时（历史数据产生于分链之前）如实说明"更早的记录未纳入校验"，
  而不是把正常历史当作删改。

**复验**：新建企业 → 建项目 → 校验 `valid=true, checkedRecords=2`；
99 条历史记录的企业 `valid=true, checkedRecords=49`。

---

### 7. 【高】Bug / 任务创建后返回的 `version` 为 null —— 乐观锁第一次编辑就失效

**现象**：

```
POST /projects/{id}/bugs  → data.version = null
PUT  /bugs/{id} {expectedVersion: null}  → 200（本该 409）
```

前端拿 `bug.version` 当 `expectedVersion` 回传，null 时服务端跳过校验，
两个用户同时编辑后一个会静默覆盖前一个。

**根因**：`@Version` 字段为 null 时 MyBatis-Plus 插入语句跳过该列，
库里落默认值 1，但**返回给客户端的实体仍是 null**。

**修复**：`BugServiceImpl.create` / `TaskServiceImpl.create` 显式 `setVersion(1)`。

**复验**：创建后 `version=1`；用旧版本再改 → `409 Bug 已被他人修改（当前版本 v2）`。

---

### 8. 【中】「版本」「部署」两个标签页的深链被静默丢弃

**现象**：`/projects/{id}?tab=releases` 和 `?tab=deployments` 打开后显示的是「概览」页，
点标签能进，直接访问 URL 或从通知深链跳转就进不去。

**根因**：`page.tsx` 里 `TABS` 白名单只列了 6 个 tab，
而 `project-detail-content.tsx` 实际有 8 个 `TabsTrigger`。

**修复**：白名单补齐 `releases`/`deployments`，并加注释要求两处保持一致。

---

### 9. 【低】`mvn test` 在没起后端时必然失败

`PermissionMatrixHttpTest` 是直连 `localhost:8080` 的联调测试，
`@BeforeAll` 用 `assertFalse(token.isBlank())`，环境没准备好就整包变红。
改为 `Assumptions.assumeFalse(...)`：缺后端/缺种子账号时跳过并打印原因，
CI 里仍然按 `-Dtest=` 白名单排除它。

---

### 10. 权限矩阵保存接口的调用方式（测试方法修正，非产品缺陷）

`PUT /permissions/matrix` 是**单格开关**（`scope + role + permissionCode + enabled`），
不是整表提交。已在验证清单里按真实契约覆盖。

### 11. 部署审批的自审保护（产品设计，测试方法修正）

`POST /deployments/{id}/approve` 对发起人返回 403「不能审批自己发起的部署」；
受保护环境的回滚必须重新走审批（400）。这两条是**有意的安全约束**，
已改为用第二个账号 + 非保护环境分别验证。

---

## 二、功能按钮测试清单

### A. 顶部导航与全局

| 功能 | 操作 | 结果 |
|------|------|------|
| 侧边栏 8 项（工作台/项目/消息/通知/文档/文件/AI/报表/企业/设置） | 逐个点击 | ✅ 全部正确跳转，无 4xx/5xx |
| 全局搜索（⌘K / 点击搜索框） | 输入「UI任务」 | ✅ 弹出结果面板，命中任务 3 条，Esc 关闭 |
| 通知铃铛 | 点击 | ✅ 展开通知面板 |
| 用户头像菜单 | 点击 | ✅ 个人设置 / 企业管理 / 退出登录 |
| 退出登录 | 点击 | ✅ 清 Token 并跳 `/login?redirect=…` |
| 主题切换 | 点击 | ✅ `<html>` class 切换，深浅色即时生效 |
| 登录 / 注册 | 提交表单 | ✅ 注册后自动登录并跳工作台；错误密码提示「用户名或密码错误」 |
| 注册表单校验 | 两次密码不一致 / 邮箱非法 | ✅ 前端 zod 拦截并定位到字段 |

### B. 工作台 `/workspace`

| 按钮 | 结果 |
|------|------|
| 新建任务（弹窗：所属项目/标题/描述/优先级/指派） | ✅ 创建成功，弹窗关闭，列表刷新 |
| 全部项目 | ✅ 跳 `/projects` |
| 生成今日日报 | ✅ 跳 `/ai` |
| 分析项目健康度 | ✅ 跳 `/reports` |
| 我的任务条目 | ✅ 跳对应项目任务页 |
| 快速入口 消息/通知/文档/项目 | ✅ 分别跳 `/chat` `/notifications` `/docs` `/projects` |

### C. 项目中心 `/projects`

| 按钮 | 结果 |
|------|------|
| 创建项目（名称/标识/描述） | ✅ 创建成功；非法标识有前端提示 |
| 编辑项目 | ✅ 回填并保存 |
| 删除项目（确认弹窗） | ✅ 删除后列表刷新，级联清理 |
| 归档 / 恢复（菜单） | ✅ 状态切换 |
| 搜索 / 筛选 | ✅ 结果随输入变化 |
| 网格 / 列表视图切换 | ✅ 布局切换 |
| 进入项目卡片 | ✅ 跳详情页 |

### D. 项目详情 8 个标签页

| 标签页 | 覆盖的按钮/操作 | 结果 |
|--------|----------------|------|
| 概览 dashboard | 设置、成员、邀请链接、游客链接、更多菜单（自动化规则/回收站/归档/删除）、统计卡片、最近动态 | ✅ 全部可用 |
| 需求 requirements | 新建需求、打开详情、筛选（状态/优先级）、编辑、删除、关联任务 | ✅ |
| 任务 tasks | 新建任务、拖拽看板流转、新建迭代、激活迭代、完成任务、清单项勾选/删除、工时登记、依赖（含成环校验）、评论、导出看板、视图切换 | ✅ 成环被拒 400、工时 99h 被拒 400 |
| Bug bugs | 新建 Bug、详情、状态流转、批量指派、批量优先级、确认游客 Bug、评论、导出 CSV | ✅ |
| 反馈 feedback | 列表、接受/拒绝、转 Bug | ✅ |
| 测试 testing | 新建用例、评审（提交/通过/驳回）、执行（PASS/FAIL/BLOCKED/SKIP）、失败转缺陷、导出 CSV | ✅ 缺字段返回 400 而非 500 |
| 版本 releases | 新建版本、按版本自动生成发布说明、标记为已发布（二次确认）、删除 | ✅ 深链修复后可直达 |
| 部署 deployments | 新建环境、发起部署、审批/驳回、开始、完成、回滚、DORA 指标 | ✅ 自审 403、受保护环境回滚需重新审批 |

### E. 其它页面

| 页面 | 按钮 | 结果 |
|------|------|------|
| 企业 `/enterprise` | 创建或加入企业、邀请成员、成员管理、部门架构、企业设置、保存修改、发送测试消息、复制邀请码、重新生成邀请码、校验链、导出审计 CSV、刷新、退出企业、解散企业 | ✅ 全部可用 |
| 文档 `/docs` | 新建文档、类型筛选（全部/文档/Wiki/会议记录/报告）、编辑保存、版本历史、恢复版本、评论、删除评论 | ✅ |
| 文件 `/files` | 上传文件（拖拽/选择）、下载（鉴权双通道）、删除 | ✅ 上传 13 B 文本后列表即时刷新，下载文件名正确 |
| 聊天 `/chat` | 频道切换、搜索频道、搜索消息、发送消息、编辑、撤回、表情回应、群成员、未读角标 | ✅ 撤回后编辑被拒 400 |
| AI `/ai` | AI 对话、Agent、知识库检索、Code Review 四个标签页 | ✅ 修复 401 后全部可用（未配 Key 走离线降级） |
| 报表 `/reports` | 5 个标签页（企业统计/项目健康度/成员工作量/燃尽图/甘特图）、导出 CSV | ✅ CSV 带 BOM 下载成功 |
| 通知 `/notifications` | 全部已读、8 个类型筛选、仅未读、点击直达上下文 | ✅ |
| 设置 `/settings` | 保存修改、修改密码（错误旧密码提示）、启用双因素认证（完整绑定→确认→关闭 TOTP 闭环）、通知偏好逐项开关、全部开启/全部关闭、免打扰时段 | ✅ |
| 游客页 `/guest/{token}` | 查看项目、提交 Bug、提交建议、查看自己的提交 | ✅（昵称/标题必填校验生效） |

---

## 三、回归结论

| 测试 | 结果 |
|------|------|
| 后端 API 冒烟（191 项） | ✅ 191/191 |
| 组织/部门/回收站/文件/负例（45 项） | ✅ 45/45 |
| 部署全流程 + 权限 + 乐观锁 + 依赖环（30 项） | ✅ 30/30 |
| 前端 28 个路由巡检（控制台错误 / 页面异常 / 失败请求） | ✅ 28/28，0 失败请求 |
| 后端单元测试 | ✅ 90 个全过（1 个联调测试改为按环境跳过） |
| `scripts/verify-integration.sh`（真实 MySQL 起服务） | ✅ 94 项全过 |
| 前端 `tsc --noEmit` | ✅ 0 错误 |
| 前端 `next build` | ✅ 构建成功 |

**仍存在的已知情况（非缺陷）**：

- 未配置任何 LLM API Key 时，AI 服务走离线降级模式，返回模板化回复——这是设计行为，控制台/文档均有说明。
- 前端 `eslint` 仍有 16 个 `react-hooks/set-state-in-effect` 告警级错误（Lint 规则门禁问题，不影响运行），未在本轮改动范围内处理。
- 部署审批"不能审批自己发起的"、受保护环境回滚需重新审批，都是有意的安全约束。
