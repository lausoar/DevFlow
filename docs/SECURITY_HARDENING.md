# 安全加固说明（第 0 批）

> 对应审计报告 [AUDIT_REPORT_2026-11.md](./AUDIT_REPORT_2026-11.md) 的「第 0 批：安全止血」。
> 本批改动只做"堵住口子 + 建立统一入口"，不引入新业务概念。

## 一、必须先配的环境变量

后端与 AI 服务共享同一把 JWT 密钥，**两边值必须完全一致**，否则用户 token 无法通过 AI 侧校验。

| 变量 | 作用 | 生成方式 |
|------|------|----------|
| `JWT_SECRET` | 后端签发 / AI 服务验签（Base64，≥32 字节） | `openssl rand -base64 48` |
| `AI_SERVICE_TOKEN` | 后端 → AI 服务的服务间调用令牌 | 任意足够长的随机串 |
| `DB_PASSWORD` | MySQL 口令（docker compose 与后端共用） | 自行设定 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 凭据 | 自行设定 |
| `CORS_ALLOWED_ORIGINS` / `WS_ALLOWED_ORIGINS` | 前端来源白名单（逗号分隔） | 如 `https://devflow.example.com` |
| `SWAGGER_ENABLED` | 生产建议 `false` | - |
| `LOG_LEVEL` / `MYBATIS_LOG_IMPL` | 生产建议 `info` / `NoLoggingImpl`（避免 SQL 全量打 stdout） | - |
| `DEVFLOW_TRUST_PROXY` | 仅在可信反向代理之后设为 `true`（否则取 XFF 会被绕过限流） | - |

根目录 `.env.example`、`ai-service/.env.example` 已列出全部模板。

**未配置 `JWT_SECRET` 时**：后端启动会生成一次性随机密钥（重启即让所有登录态失效），AI 服务则直接拒绝所有请求（fail-closed）。这是有意的：宁可服务不可用，也不让认证被静默关闭。

## 二、代码层面的加固点

### 1. 统一资源级鉴权入口 `security/AccessGuard`

新增 `com.devflow.security.AccessGuard`，把所有"能不能访问这条数据"的判定收敛到一处：

- `requireView` — 项目存在且可见，否则 404（不泄露存在性）
- `requireProjectPermission` — 可见 + 项目级权限点，否则 403
- `requireMemberOfProject` — 校验 `project_member` 行属于路径中的项目（堵住"用 B 项目的 memberId 改 A 项目成员"）
- `requireSameEnterpriseUser` — 目标用户必须与项目同企业
- `requireEnterpriseMember` / `requireEnterprisePermission` — 企业归属 + 企业级权限点

新增写接口时**必须**调用它；只判断"登录"不算鉴权。

### 2. 全量替换请求体实体 → 白名单 DTO

禁止 `@RequestBody 实体 + updateById`（mass assignment）。新增：

- `CreateProjectRequest` / `UpdateProjectRequest`
- `CreateTaskRequest` / `UpdateTaskRequest`
- `CreateBugRequest` / `UpdateBugRequest`
- `CreateDocumentRequest` / `UpdateDocumentRequest`

归属字段（`enterpriseId` / `ownerId` / `projectId` / `reporterId` / `reporterType` / `pending` / `version` / `deleted`）一律由服务端决定，客户端改写无效。

### 3. 补上缺失的归属校验（审计 §A1 的 11 条）

| 接口 | 现在的口径 |
|------|-----------|
| `PUT/DELETE /projects/{id}` | `project:edit` / `project:manage`，字段白名单 |
| `PUT /tasks/{id}`、`PUT /tasks/{id}/status` | 项目可见 + `task:manage`；状态白名单 `TODO/DOING/TESTING/DONE` |
| `PUT /bugs/{id}` | 项目可见 + `bug:edit`，字段白名单（改不了 status/projectId/reporterId） |
| `DELETE /documents/{id}` | 项目可见 + `document:delete` |
| 文件上传/列表/删除/下载 | 项目可见 + `file:manage`；无项目文件默认只看自己上传的 |
| `DELETE /comments/{id}`（任务评论） | 评论作者，或同企业 ADMIN 以上 |
| `GET /channels/{id}/messages` | 频道所属项目可见 |
| `GET /enterprises/{id}`、`/members`、`/departments/tree` | 必须是本企业成员 |
| `GET /enterprises/{id}/audit-logs` | 本企业 + `audit:read` |
| 统计导出两个端点 | 项目可见 |
| `PUT /permissions/matrix` | 本企业 + 超管（见下） |
| `POST /enterprises/{id}/wecom/test` | 本企业 + `enterprise:manage` |
| 自动化规则 list/CRUD | 项目可见；写需 `testcase:manage` |

### 4. 权限矩阵按企业隔离

`sys_role_permission` 新增 `enterprise_id`：

- `'*'` 表示全局默认模板；
- 某企业**首次**修改矩阵时，把模板整体复制成本企业快照，之后只影响本企业；
- 企业已有快照时**不再回落到模板**——否则"收回某角色全部权限"会被模板静默还原。
- 权限判定缓存按企业分组，`reload()` 全量刷新。

> 迁移：`schema.sql` 末尾的 ALTER 会把历史行补成 `enterprise_id='*'`（即全局模板），行为与之前一致。

### 5. 新增项目级权限点

`project:edit`、`task:manage`、`requirement:manage`、`document:manage`、`document:delete`、`file:manage`、`sprint:manage`。
种子映射保证既有角色的可用范围不变：任务/需求/文档/文件对全部项目角色开放，删除文档与项目设置仅 OWNER/MANAGER。

### 6. WebSocket

- CONNECT 阶段 token 缺失/无效 **直接拒绝**，不再放行匿名连接；
- SUBSCRIBE / SEND 校验目标频道所属项目可见；
- 移除 `senderId="anonymous"` 降级；
- 跨域来源从 `*` 收窄到配置白名单；
- 在线名单按企业分 topic 广播（`/topic/presence.{enterpriseId}`），不再全平台广播。

### 7. AI 服务

- 全部 `/api/v1/**` 要求认证：用户 JWT（与后端同一密钥验签）或 `X-Service-Token`；
- CORS 白名单化；
- Agent 接口要求**真实用户令牌**（服务令牌不可用），因为要以该用户身份访问后端；
- RAG 索引写入 `enterprise_id`/`project_id`，检索必须带范围（缺失直接 400），Chroma query 支持 `where`；
- Agent 的知识库工具按项目过滤，无项目上下文时明确返回错误而非全局检索；
- 请求参数加上下限（`top_k` ≤ 50、`max_tokens` ≤ 16000、消息条数/长度上限）；
- 默认监听 `127.0.0.1`。

### 8. 密钥与凭据

- `application.yml` 全部改为 `${ENV:默认值}`；真实密钥不再入库；
- JWT 密钥留空时生成一次性随机密钥（并告警）；
- `docker-compose` 凭据来自环境变量，所有端口只绑定 `127.0.0.1`；
- `.gitignore` 收紧为 `.env.*` 全忽略（只保留 `.env.example`）。

### 9. 文件与响应头

- 上传扩展名白名单（图片/文档/压缩包/音视频等），显式拒绝 html/js/exe/jsp 等；
- MIME 由服务端按扩展名推导，不信任客户端声明；
- 下载一律 `Content-Disposition: attachment` + `nosniff` + 沙箱 CSP；
- `JwtAuthFilter` 的 cookie 回退只对 `GET /files/download/**` 生效；
- SecurityConfig 全局下发 `X-Content-Type-Options`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`。

### 10. 其他

- `User.password` 加 `@JsonIgnore`，任何接口都不会再回传 BCrypt 哈希；
- 业务异常按 code 映射真实 HTTP 状态（400/401/403/404/409/429），前端不再需要从 HTTP 200 的 body 里二次判断；
- 企业微信 Webhook / 邀请码只对有管理权限的成员下发；
- 登录防爆破与游客限流：`clear()` 改为"仅淘汰过期条目"，key 先 trim 再小写（加空格不能绕过锁定）；
- Git webhook 密钥改为常量时间比较；
- 项目逻辑删除后，成员不再可见（`visibleProjectIds` 会过滤已删除项目）；
- 前端：项目设置/归档/删除按钮改为按权限点显示；RAG 与 Agent 面板必须先选项目；批量"取消指派"不再写入 `_none`。

## 三、已知的后续项（不在本批范围）

- 加成员时若目标用户属于其他企业，当前返回 403；如后续支持跨企业外部协作者，需要单独的"外部成员"模型。
- 权限矩阵的企业快照在"新建企业"时不预生成，首次改配置时才复制（懒初始化，行为正确但首次保存多一次写）。
- WebSocket 仍是单机 SimpleBroker，集群化属于第 1/2 批。
- AI 侧未做用量计量与配额，属于第 1 批。
