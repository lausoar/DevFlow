# DevFlow 全面审计报告

> 审计日期：2026-11-15  
> 方法：11 个代码审查代理（6 个领域功能盘点 + 5 个专项深挖：安全 / 正确性 / 生产化 / 数据模型 / 工程质量）+ 6 个市场竞品研究代理（Atlassian 生态、GitLab/Azure DevOps、PingCode/ONES/TAPD/飞书项目/云效、Linear/ClickUp/Monday、Figma/蓝湖设计环节、TestRail/MeterSphere/发布管理），所有问题经独立核实代理逐条回读代码对抗验证。
> 结果：132 条问题（72 条经对抗核实为确凿），90 条功能差距。

---

## 一、总体结论

DevFlow 的**业务宽度**已经相当接近国产一线研发管理产品（PingCode / ONES / 云效）的骨架：需求、迭代、任务、缺陷、测试用例、文档、聊天、AI、报表、双域 RBAC、自动化规则、企业微信、Git webhook 都有真实落地，且有几处设计（ProjectAccessService 统一可见性收口、权限矩阵零查表判定、RAG 多级降级）体现了不错的工程判断。

但当前存在三类结构性问题：

1. **安全边界是“点状”的，不是“面状”的。** `canView` 在 27 处被调用，但 10+ 个写接口和 3 个资源域（文件、文档删除、任务更新）完全没有它。任一处在新增功能时漏写，就是一次跨租户数据泄露。这是最紧急的问题。
2. **定位上缺两整段：UI 设计协作、部署与发布。** README 宣称覆盖“需求文档 → UI → 开发 → 测试 → 部署上线”，但代码里既没有设计稿，也没有环境、部署记录、版本、发布。这是产品叙事与实现之间的硬断档。
3. **“看起来有、实际没有”的功能较多。** 任务自动指派、BUG_FAIL 触发器、Refresh Token 静默续期、迭代与任务的关联、看板徽章、文档 Markdown 预览、流式 AI 输出——都有代码、都有 UI 入口，但链路在某处断开，用户侧完全无感知。这类问题比“没做”更伤信任。

---

## 二、现有系统的不足与问题

### A. 安全：越权与鉴权缺口（最高优先级）

#### A1. 核心写接口几乎无鉴权 —— 跨租户 IDOR 成链

| # | 接口 | 现状 | 后果 |
|---|------|------|------|
| 1 | `PUT /projects/{id}`、`DELETE /projects/{id}` | `ProjectController:163-178` 无 Authentication 参数，直接 `updateById(project)` / `deleteById(id)` | 任意登录用户可改任意项目名、把 `enterpriseId` 改成自己企业（逆向吞并）、夺取 `ownerId`、重置 `inviteCode` 与 `gitWebhookSecret`，或直接删项目 |
| 2 | `PUT /tasks/{id}`、`PUT /tasks/{id}/status` | `TaskController:94-108` 无 Authentication，`TaskServiceImpl:88` 直接 `updateById(task)` | 改任意任务全部字段，可把 `projectId` 改到自己项目（搬运）；状态变更还会触发自动化规则、给别人发通知 |
| 3 | `PUT /bugs/{id}` | `BugController:86-97` 用 `@RequestBody Bug` 实体接参后整对象落库 | 拥有 bug:edit 的 TESTER 可在 body 里塞 `status` 绕过“仅负责人/报告人可改状态”，或改 `projectId` 跨项目投毒、伪造 `reporterType`/`pending` |
| 4 | `DELETE /documents/{id}` | `DocumentController:261-267` 无任何校验 | 任意登录用户可删任意企业任意文档（同文件 update/getById/comments 都做了 `docVisible`，唯独 delete 遗漏） |
| 5 | 文件全部端点 | `FileController` list/delete/download/upload 全无归属校验 | 不传 `projectId` 时 list 返回**全库所有企业**的文件；拿到 objectName 即可下载任意文件；可删他人文件；可往任意项目上传 |
| 6 | 任务评论删除 | `TaskCommentController:60-64` 无 Authentication | 任意登录用户可删任意评论（Bug 评论有“本人或管理员”判定，任务评论没有） |
| 7 | `GET /channels/{id}/messages` | `ChatController:93-102` 无 Authentication、无 canView | 遍历 channelId 即可读任意企业群聊历史 |
| 8 | `GET /enterprises/{id}`、`/members`、`/departments/tree` | 全部只按路径参数查询 | 跨企业枚举全员 email/phone/部门，为钓鱼与撞库提供弹药；详情还返回 `inviteCode` 与 `wecomWebhook` |
| 9 | 审计日志 `GET /enterprises/{id}/audit-logs` | 只校验调用者在**自己企业**有 audit:read，路径 id 直接透传 | A 企业管理员可读 B 企业完整审计日志 |
| 10 | 统计导出两个接口 | `StatisticsController:129-190` 只判项目存在，无 canView | 跨企业导出健康分、完成率、成员工作量（同文件其他 4 个统计接口都有 canView） |
| 11 | `POST /permissions/matrix` | `sys_role_permission` 表无租户列，写的是全局映射 | **任一企业超管可收回/放开全平台所有企业的权限** |

**根治建议**：不要继续“在每个接口补一行 canView”。改为在 MyBatis 层加租户/项目拦截器自动注入条件 + `@PreAuthorize` 声明式授权 + 资源级 ACL 三层兜底，并把 `@RequestBody 实体` 全部换成白名单 DTO（当前 52 处 `@RequestBody` 只有 10 处带 `@Valid`，10 处直接收实体）。

#### A2. WebSocket 鉴权可完全绕过

`WebSocketAuthInterceptor:36-56` 只在 CONNECT 且 token 有效时 `setUser`，**失败路径直接放行连接**；SimpleBroker 对 `/topic` 无订阅 ACL；`ChatController:168-191` 拿不到 userId 时降级为 `senderId="anonymous"` 仍然入库并广播。

结果：**未登录的匿名连接可以订阅任意 `/topic/channel.{id}` 实时窃听所有项目群消息，并可以向任意频道注入伪造消息。**

#### A3. AI 服务全站裸奔

`ai-service` 没有任何认证依赖（全目录 grep `Depends|HTTPBearer|Security(` 零命中），CORS `allow_origins=["*"]`。`/rag/index` 可被匿名写入伪造文档、`/rag/search` 可匿名检索全平台文档、`/chat/completions` 与 `/agent/run` 可匿名消耗付费 LLM 额度。任意网页在浏览器上下文都能跨域调用 `127.0.0.1:8000`。

#### A4. RAG 无项目/企业隔离 —— A 项目文档会答给 B 项目成员

`rag_service.py:32-40` 索引时把 Java 侧传来的 `project_id` **直接丢弃**，metadata 只存 `doc_id/title/doc_type/chunk_index`；`chroma_client.query()` 签名根本没有 `where` 参数。所有企业共用一个 `devflow_docs` collection。

最现实的泄露路径：项目群里 @AI 提问时，`AiChatAssistantService:113-118` 只发 `{question, top_k}`，不带任何项目上下文 → 检索到其他项目的文档 → 生成成答案广播回该频道。

#### A5. 密钥硬编码入库 + Token 伪造链

- `application.yml:61` 的 JWT secret 是仓库里可直接 Base64 解出的固定值（`devflow-jwt-secret-key-2026-enterprise-ai-platform`），**拿到源码即可为任意 userId 签发合法 Token**
- 同文件 `:14-15` MySQL `root/devflow123`、`:68-69` MinIO `minioadmin/minioadmin`
- `docker-compose.yml` 把 3306 / 6380 / 9002 / 9003 / 8001 全部映射到宿主机 0.0.0.0
- `JwtAuthFilter:50-61` 只验签名与黑名单，**不回查用户是否存在/是否被移除/是否 ACTIVE** → 被移出企业的成员在 24h 内仍可用
- `schema.sql:393-399` 每次启动以 `sql.init.mode=always` 插入 `admin/admin123`

#### A6. 文件上传构成同源存储型 XSS 与完整 IDOR 链

上传无扩展名/MIME 白名单，MinIO 直接存客户端声明的 `getContentType()`；下载时 `Content-Disposition: inline` + `setContentType(item.getMimeType())` 原样回显。上传一个 `text/html` 或 `image/svg+xml` 文件后访问下载链接，脚本在 8080 同源执行，而 `df_token` cookie 是 JS 可读的（前端 `client.ts:24` 用 `document.cookie` 写入），**脚本可直接读 token 外传，实现完整冒充**。

#### A7. 其他安全缺口

- **Refresh Token 实际失效**：前端从不保存 refreshToken（`auth-store.ts:30-40` 只存 accessToken），401 也不做静默续期 → 24h 后硬跳登录
- **两道令牌同密钥、无 type claim**：refresh token 可当 access token 用，access token 也能换新令牌；`refreshToken()` 不查黑名单 → **登出后被窃取的 refresh token 仍可续期 7 天**
- **Git webhook**：密钥对任意项目成员可见（`GET /projects/{id}` 返回整实体），明文拼进 URL query，仅做 `equals` 非常量时间比较，不校验 `X-Hub-Signature-256`，且无轮换接口
- **游客限流可绕过**：`GuestController:192-197` 无条件信任 `X-Forwarded-For` 首个值，换假 IP 即绕过；`GuestServiceImpl:425-427` 在 map 超 10000 条时 `clear()` 清空所有人限流窗口
- **项目成员改角色/移除存在 IDOR**：`ProjectController:236-259` 只校验操作者在路径 projectId 上有权限，之后用 body 里的 `memberId` 直接 update/delete，不校验该 member 属于该项目 → A 项目 MANAGER 可改/删 B 项目成员
- **加成员可直接授予 OWNER**：`addMember` 对 role 无白名单（只有 `updateMemberRole` 有），传入 `role=OWNER` 即可绕过“不可授予 OWNER”
- **创建项目可注入他人 enterpriseId**：仅在请求体未带时回填调用者企业
- **密码哈希回传**：`UserController:29-33` 的 `/users/me` 直接返回 User 实体，`User.password` 无 `@JsonIgnore`（已核实），BCrypt 哈希明文出网
- **业务异常全部以 HTTP 200 返回**：`GlobalExceptionHandler:23-27` 无 `@ResponseStatus`，前端被迫手工判断 `envelope.code !== 200`
- **企微 webhook 明文下发**：`GET /enterprises/{id}` 返回实体自带 `wecomWebhook`（内含群机器人 key）；`POST /enterprises/{id}/wecom/test` 除登录外无任何校验
- **无 2FA、无 SSO、无密码复杂度/过期策略**，改密后不失效在途 Token
- **全站 `csrf().disable()`** + cookie 回退鉴权 + 存在 GET 写接口（`getInviteCode` 内 `updateById`）→ 顶层跨站导航可触发

---

### B. 功能失效：有代码、有入口，但链路断开

这类问题最伤用户信任，因为表现为“功能有，但用了没反应”。

| # | 功能 | 根因 | 证据 |
|---|------|------|------|
| 1 | **Bug 自动指派 100% 失效** | `autoAssignBug` 只改内存对象，留了一行 `BugMapper bugMapper = null;` 占位并注释“由调用方更新”，但调用方 `BugServiceImpl:87` 丢弃返回值、从不回写 | 响应里 Bug 显示 ASSIGNED + 有负责人，数据库里仍是 NEW + assignee_id=NULL |
| 2 | **BUG_FAIL 触发器永不触发** | 全仓 `fire()` 只有 3 处（TASK_DONE / CASE_FAIL / BUG_CREATED），Bug 状态变更为任何值都不 fire | 用户配置的规则永久静默且无日志可查 |
| 3 | **TASK_DONE 语义错位** | `TaskController:107` 只判 `body.containsKey("status")` 就无条件 fire("TASK_DONE")，DONE→TODO 同样触发 | 自动化产生大量错误通知 |
| 4 | **同时配“指派+通知”的规则两者都不发生** | `fire` 里遇 `BUG_CREATED + assignToUserId` 直接 `handled++; continue`，跳过通知分支；再叠加 #1 的未落库 | 用户完全无感知 |
| 5 | **迭代与任务彻底脱节** | 前端新建/编辑任务表单**没有任何写入 `sprintId` 的入口**，也无拖入迭代的交互 | 迭代进度条恒为 0、燃尽图 `dataPoints` 为空、“迭代管理”只是能建个空壳 |
| 6 | **看板徽章永不渲染** | `tasks-tab.tsx:446` 把 `extras` 传给 `KanbanColumn`，但 `:673` 渲染 `TaskCard` 时**漏传** | 清单进度 / ⛔前置未完成 / ⏱工时 三个 `if (!extras) return null` 恒短路 |
| 7 | **任务无法在界面指派负责人** | `TaskEditDialog` 的 form 含 `assigneeId` 并回填，但没有任何指派控件，`handleSave` 的 payload 也不含它 | 看板卡片不展示负责人；指派通知链在 UI 上几乎不会发生 |
| 8 | **AI 日报是编造的** | `assistant_service.py:19-34` 提示词只插入 user_id 与 project_id 字符串，**不查询任何任务/Bug/提交/工时**；前端还硬编码 `'current-user'` | 模型凭空编“今日完成的工作”，并持久化为工作记录 |
| 9 | **AI 流式输出未落地** | `/chat/stream` SSE 已实现，但前端 `chatStream` 零调用点，且用 `responseType:'text'` 一次性接收 | 所有交互是 90s 长超时阻塞，首字延迟 = 全量生成时间 |
| 10 | **文档“预览”不渲染 Markdown** | 未引入 react-markdown / marked / @tailwindcss/typography（已核实 package.json） | `<div className="prose">` 里直接放原始字符串，Markdown 语法原样显示；版本预览只是 `<pre>` 全文，无 diff |
| 11 | **需求详情接口路径错误** | 前端 `requirement.ts:26-27` 请求 `/requirements/{id}`，后端只有 `/projects/{projectId}/requirements/{id}` | `useRequirement` 一旦启用就 404；当前详情弹窗靠“最多 20 条列表里 find”凑 |
| 12 | **任务评论作者名显示为 UUID 前 6 位** | `TaskCommentServiceImpl` 未联查 `sys_user`，实体无 username 字段 | 前端 `comment.username \|\| comment.userId.slice(0,6)` |
| 13 | **批量“取消指派”写入非法负责人** | `bugs-tab.tsx:372` `<SelectItem value="_none">` 未被 `:363-366` 的过滤拦截，后端只把空串当取消 | `assignee_id='_none'` 落库，指向不存在用户 |
| 14 | **管理员删 Bug 评论的分支恒为 false** | `BugCommentServiceImpl:97-101` 拿 `comment.getProjectId()` 去比 `operator.getEnterpriseId()`（两个 ID 空间） | “企业管理员可删他人评论”实际失效（已核实） |
| 15 | **用例转缺陷绕过 BugService** | `TestCaseController:269-287` 直接 `new Bug()` + `bugMapper.insert` | 跳过自动化规则、企微推送、自动指派、被指派人通知；且无幂等校验，同一用例可反复转出重复 Bug |
| 16 | **非项目频道无法访问** | `getMyChannels` 用 `.in(project_id, visibleIds)`，`project_id IS NULL` 的 DEPARTMENT/ENTERPRISE/PRIVATE 频道永不返回；`createChannel` 无鉴权无校验 | 部门群/企业群/私聊在数据模型上存在但产品上未落地 |
| 17 | **迭代可同时存在多个 ACTIVE** | `activate` 不检查项目内是否已有 ACTIVE，也不自动完成旧的；前端 `find` 取第一个 | 看板进度与燃尽图随机指向其中一个迭代 |

---

### C. 正确性与并发

1. **无乐观锁，全部“最后写入覆盖”**：Task / Bug / Requirement / Project / Sprint 均无 `version` 列，`MyBatisPlusConfig` 只注册了分页拦截器，未注册 `OptimisticLockerInnerInterceptor`。两人同时拖拽或编辑同一任务，后写完全覆盖前写。
2. **文档保存的版本号是读-改-写竞态**：`DocumentController:108-132` 读 `prev` → 插 `document_version(prev)` 快照 → `setVersion(prev+1)` → `updateById`，**全程不校验客户端版本号**。并发保存时第二个 insert 撞 `uk_doc_ver` 唯一键 → 500；即使不撞也是后写覆盖，前一个人的正文永久丢失。
3. **快照与更新不在同一事务**：`update` 失败后 `(document_id, version)` 留下脏快照，下次保存会重插同号触发重复键 → 接口持续 500 而非返回 409 冲突。
4. **完成/删除迭代无事务**：`SprintController.complete` 先改迭代状态再批量迁任务，`delete` 先清 `sprint_id` 再删迭代，类上无 `@Transactional`。任一步失败留下“已完成迭代 + 任务仍挂着”。
5. **创建项目无事务**：连续 insert project / project_member(OWNER) / chat_channel，失败会留下“没有负责人的项目”或“没有群的项目”。
6. **任务状态无白名单**：`TaskServiceImpl:117-122` 直接把请求字符串写进 status，可写 `'FOO'`，任务随即从四列看板消失（`tasks-tab.tsx:138-148` 丢弃未知状态）。需求有白名单、Bug 有白名单，**任务漏了**。
7. **任务状态取值在代码内不一致**：`TaskServiceImpl:129-134` 的 switch 映射 `IN_PROGRESS`/`IN_REVIEW`，而 schema 注释、前端类型、统计口径全是 `TODO/DOING/TESTING/DONE` → 通知文案永远显示原始英文键值。
8. **项目逻辑删除后子资源仍可读写**：`ProjectAccessServiceImpl.visibleProjectIds` 只查 `project_member`，**从不 JOIN project.deleted**。项目删除后原成员仍能访问任务/Bug/需求/Wiki 树/看板统计。且删除不级联，成员/任务/Bug/文档/评论全部留成孤儿。
9. **全库零外键**：`grep 'FOREIGN KEY|REFERENCES'` 结果为 0，所有关联靠应用层字符串 ID 维护，无法阻止 `assignee_id` 指向已删用户。
10. **企业移除成员不清理项目成员**：`EnterpriseServiceImpl.removeMember` 只把 `sys_user.enterprise_id` 置空，**没有处理 `project_member`**；而自助退出 `leaveEnterprise` 会清。结果“管理员移除”比“成员自己退出”清理得更不彻底，被移除者仍是原企业各项目成员，仍可读全部项目数据。
11. **schema.sql 是唯一迁移手段且 `continue-on-error: true`**：无 Flyway/Liquibase，15 条裸 `ALTER TABLE` 每次启动重跑。首次因历史数据重复而失败时，唯一约束将永久缺失且无人知晓；多实例部署时 N 个实例并发执行 DDL。
12. **启动即改写业务数据**：`schema.sql:539` 把所有非 APPROVED 的需求改成 APPROVED、`:710` 把所有非三态的需求改成 OPEN、`:722` 为所有空 webhook secret 重写 UUID、`:542-549` 用 JOIN 全量重写 notification.action_url。**这些是每次 Spring 启动都执行的 UPDATE**，后续新增状态或用户手工维护的密钥会在重启时被静默覆盖。
13. **`task_worklog` 表定义了两次**（已核实 `schema.sql:574` 与 `:607`）：先建者生效，所以实际是 `hours DECIMAL(5,1)`——后端允许 0.1~24 的小数工时（0.25h、1.75h），落库被四舍五入成 0.3、1.8，累计工时统计失真；`note` 也只有 200 字。
14. **两组重复表 + 两组死表**：`document_versions`/`document_comments`（复数）与 `document_version`/`document_comment`（单数）并存，实体只映射单数版，复数版永远为空；`task_tag`、`checklist_item` 在 Java 与前端均无任何引用。
15. **燃尽图把 `updated_at` 当完成时间**：任务完成后改标题/改负责人都会刷新 `updated_at`，历史曲线当场被重画；`dataPoints` 为空时（迭代开始日期在未来）`:545` 直接 `get(size-1)` 抛 `IndexOutOfBoundsException` → 500。
16. **`getInviteCode` 是 check-then-write 竞态**：两请求同时判断为空，各生成不同码，后写覆盖前写，先返回给 A 的邀请码随即失效。
17. **删除任务不清理依赖/工时/清单/评论/需求关联**；依赖徽章聚合 SQL 没有过滤 `deleted`，**已删除的前置任务永久阻塞看板徽章**。

---

### D. 数据模型与 schema

1. **软删除列严重不一致**：15 张表有 `deleted`，`chat_message` 却用 `is_deleted`（MyBatis-Plus 全局 `logic-delete-field=deleted` 对它不生效）；`notification`/`activity_log`/`project_member`/`department_member`/`task_dependency`/`task_worklog`/`git_commit`/`ai_chat_message`/`automation_rule`/`project_guest` **都没有删除列**，删除即物理删除（`NotificationController.delete` 直接 `deleteById`，不可审计）。全部软删除表都没有 `deleted_at`/`deleted_by`，**无法回答“谁在什么时候删的”**。
2. **回收站只覆盖 3 类实体**：只有 task/bug/requirement，文档、文件、评论、项目、迭代删了就没有恢复入口。且列表用 `updated_at` 当“删除于”展示（无 `deleted_at` 列），**显示的是最后一次修改时间**。
3. **关键查询列缺索引**：`chat_channel` 除主键外没有任何 KEY（而 `getMyChannels` 正按 `project_id` 过滤）；`bug` 无 `assignee_id` 索引（而 `listMyBugs` 正按它查）；`department_member` 无 `enterprise_id`；`notification` 无 `(user_id, is_read)` 复合索引且 `created_at` 完全没索引；`activity_log` 无 `(enterprise_id, created_at)`；`file_item.url` 无索引（下载每次都全表扫）；`chat_message` 无 `(channel_id, created_at)` 复合索引；`document` 无 `parent_id` 索引（Wiki 树全量构建）。
4. **状态/角色全是自由 VARCHAR**：无枚举表、无 CHECK。`project_member.role` 由请求体直传未白名单；`requirement.status` 的列注释仍是 `DRAFT/PENDING/APPROVED/...`，真实值是 `OPEN/COMPLETED/CLOSED`。
5. **JSON 用 TEXT 存**：`bug.screenshots`、`test_case.steps`、`requirement_template.content` 注释都明说 JSON，全部用 TEXT——MySQL 8 的 JSON 类型、生成列 + 虚拟索引一概不用。
6. **全库无 FULLTEXT**：搜索全靠 `LIKE '%kw%'` 前导通配（索引完全失效），文档正文 `LONGTEXT` 完全不参与搜索。
7. **审计日志表不足以支撑合规**：无 `ip`/`user_agent`/`request_id`/`success`/`before+after` 结构化快照；`detail` 是自由 TEXT；写入包在 `try/catch` 里 fail-open（只 warn）；`permission.denied` 每次判定失败都落一行、无去重/限流，普通成员反复点受限按钮可把审计表刷爆。
8. **无归档与保留策略**：`notification`/`chat_message`/`activity_log` 三张高增长表都没有 TTL、分区或归档任务。pom 引入了 `spring-boot-starter-quartz` 但零使用（全仓无 `@Scheduled`）。
9. **工时/数值类型不一致**：`Requirement.estimatedHours` 是 Java `Double` 对应 `DECIMAL(6,1)`，浮点→定点在 0.1/0.5 工时上产生偏差。
10. **索引/约束缺失**：`task_tag` 无 `(task_id, tag_name)` 唯一键；`sys_role_permission` 无租户列（见 A1 #11）。

---

### E. 工程质量

1. **六大核心域零测试**：23 个 service 中只有 Permission / Guest / ProjectAccess / Search 四个有单测。TaskService（状态流转 + 通知）、BugService（自动指派 + 企微推送 + 自动化）、StatisticsService（728 行）、RequirementService、EnterpriseService 全部裸奔。**任务状态映射错误（C7）正是这种裸奔的直接后果。**
2. **唯一“集成测试”依赖本机 8080 与硬编码数据**：`PermissionMatrixHttpTest:32` 写死 `http://localhost:8080`，`:34-36` 写死项目 UUID，`:45` 写死账号密码 `member1_5c14 / DevFlow@2026`，三个断言在 `@BeforeAll`。换机器必红，**无法作为 CI 门禁**。且全仓 0 处 `@SpringBootTest`/`MockMvc`/`Testcontainers`/H2。
3. **前端零测试**：`package.json` 只有 dev/build/start/lint，无 jest/vitest/playwright，无 `typecheck` 脚本，无 husky/lint-staged，`next.config.ts` 是空配置。
4. **ai-service `tests/` 是空壳**：只有一个 0 字节的 `__init__.py`，无任何 `test_*.py`；`pyproject.toml` 声明了 ruff 但无脚本/CI 调用。
5. **无 CI/CD**：仓库无 `.github`/`.gitlab-ci.yml`/`Jenkinsfile`，pom 无 JaCoCo/Checkstyle/SpotBugs，无 mvnw。
6. **错误码体系未成体系**：181 处 `BusinessException` 中 178 处是裸数字 + 中文魔法字符串，`grep "enum.*Error|ErrorCode"` 零命中。“项目不存在或已被删除”重复 18 处、“文档不存在或已被删除” 8 处，改文案要改十几处。
7. **14 处 `catch (Exception ignored) {}`**：BugServiceImpl 的自动指派 / 自动化规则 fire / 企微推送三处、TestCaseController 的通知 / 规则 / 推送三处、TaskController 的规则与前置通知、ChatController 的 WS 广播、RecycleBin 的审计、Requirement 的变更日志——**全在主链路副作用上被静默吞掉**，失败后既不报错也不打日志。
8. **分层破坏**：28 个 controller 中 18 个直接 import mapper（200+ 处引用，ProjectController 37、TaskExtraController 36、DocumentController 33），事务外的多表写入、字符串解析、通知与规则触发全堆在 controller 里。service 只覆盖部分域（**无 SprintService、无 ChatService、无 DocumentService**）。
9. **巨型类**：`enterprise-content.tsx` 1514 行、`tasks-tab.tsx` 1126 行、`dashboard-tab.tsx` 1025 行、`bugs-tab.tsx` 1005 行、`EnterpriseServiceImpl` 747 行、`StatisticsServiceImpl` 728 行。
10. **DTO 校验覆盖极低**：52 处 `@RequestBody` 只有 10 处 `@Valid`；16 处直接收 `Map`，10 处直接收实体。

---

### F. 性能与可扩展性

1. **统计报表全量内存聚合**：`StatisticsServiceImpl:62-70` 一次性 `selectList` 出该企业**所有**项目的**全部** Task/Bug/Requirement，再用十余次 `stream filter` 逐条计数，**全文件无一条 GROUP BY**。前端 `staleTime` 5 分钟意味着反复触发全量重算。万级任务即数十 MB 堆内存 + 秒级 GC。
2. **N+1 查询**：企业统计对每个项目单独查 `project_member`（P 次），`projectProgressList` 的 map 里再查一次（又 P 次）；需求分页列表对每条记录查 project/requester/approver/关联任务/assignee（20 条一页可产生上百次查询）。
3. **测试用例列表 O(n×m)**：全量 `selectList` 用例，再拉项目**全部**执行记录（无 LIMIT），然后在用例循环里对 `allExecs` 再 `stream().filter()`。前端每次 `onChange` 就重发全量请求（无 debounce）。
4. **分页缺失或方向错误**：
   - **聊天历史 `ORDER BY created_at ASC` + `page=1`** → 每次取的是频道**最早**的 50 条。前端不传 page、无 loadMore、无上拉。**消息量超过 50 条后，新消息永远不可见**——这是生产环境几天内就会暴露的致命缺陷。
   - `/sprints/{id}/tasks` 直接 `selectList` 无分页
   - 任务/Bug 列表默认 `pageSize=100`，前端不传分页参数、无虚拟滚动、无“还有更多”提示 → **超过 100 条静默截断**
   - 报表项目下拉固定 20、项目总览固定 50、工作台新建任务的项目下拉只取 4 个（**第 5 个及以后的项目根本选不到**）
5. **全局搜索五类实体串行 5 次 `LIKE '%q%'`**，每类固定 `LIMIT 10`，无分页、无相关性排序、无高亮；文档只匹配 title，正文/聊天/评论/文件名/文件名均不在范围；且 `documents` 查询用 `.in(projectId, projectIds)` **无条件排除 `project_id IS NULL` 的全局文档**（而文档列表接口允许全局文档按企业可见，两处口径冲突）。
6. **无缓存层**：全 backend `grep @Cacheable|@EnableCaching` 零命中。
7. **文件删除只删数据库记录**：全仓 `grep removeObject` 零结果，MinIO 对象永久残留；无按用户/项目/企业的容量配额。

---

### G. 生产化与运维（交付阻断项）

1. **三个服务均无 Dockerfile**（已核实 Glob `**/Dockerfile*` 零结果），只有基础设施容器化。无 `docker-compose.prod`、无镜像制品、无 Nginx/HTTPS。**只能靠开发者本机 `mvn`/`npm`/`uvicorn` 手工启动**，无法滚动发布、无法回滚。
2. **无环境分离**：只有单一 `application.yml`，`grep '\${'` 零命中，无 `application-dev/prod.yml`。CORS 白名单、MinIO endpoint、AI 服务地址全写死 `localhost`。
3. **假的可观测性**：`SecurityConfig:58` 放行了 `/actuator/health`，但 **pom 里根本没有 `spring-boot-starter-actuator`**，健康探针实际 404。无 Micrometer/Prometheus/OTel，无慢 SQL 监控。前端无 `error.tsx`/`global-error.tsx`/`not-found.tsx`，无 Sentry 类上报。
4. **日志方案缺失**：`application.yml:57` 把 MyBatis 日志设为 `StdOutImpl`，`:87` `com.devflow: debug` → **每条 SQL 连同参数打到 stdout**，高并发下日志风暴，且会把手机号、消息内容等敏感数据写进日志。无 `logback-spring.xml`，无法按环境切级别、无法滚动、无 request-id 串联。
5. **单实例假设遍布全局**：
   - **WebSocket SimpleBroker 进程内广播**，无 StompBrokerRelay / Redis pub/sub → 扩容到 2 实例后，用户连 A、消息由 B 产生，B 用户收不到
   - `PresenceService` 两个 `ConcurrentHashMap`（注释自认“单实例部署”）
   - 权限矩阵 `reload()` 只刷本 JVM，**多实例下其他节点永远读到旧权限直到重启**
   - 登录防爆破与游客限流都是进程内 Map
6. **限流 Map 的 `clear()` 是安全漏洞**：`AuthServiceImpl:214-215` 与 `GuestServiceImpl:425-426` 都在超过 10000 条时 `clear()`。攻击者用一万个随机用户名各失败一次，**就能把所有正在被锁定的账号解锁**、把所有配额清零。且防爆破 key 用 `toLowerCase()` 不 trim，`" admin"` 与 `"admin"` 是两个 key，**加个空格即绕过**；邮箱登录与用户名登录是两条独立计数。
7. **无优雅停机、无 liveness/readiness、无备份恢复方案**：未配 `server.shutdown=graceful`，WS 与 `aiExecutor` 异步任务无排空机制；无 mysqldump/xtrabackup 备份与恢复演练。
8. **无幂等键与全局限流**：POST 创建任务/发消息/建缺陷都是裸 insert，双击、网关重试、React Query 重试都会产生重复数据与重复通知/企微推送。
9. **无多租户隔离机制**：企业隔离靠逐个控制器手写 `canView` 与 `enterpriseId` 判断，一处漏写就是跨租户越权（A1 的 11 条即后果）。

---

### H. 前端体验与一致性

1. **鉴权只有客户端守卫**：无 `middleware.ts`，`(main)/layout.tsx` 是 `'use client'` + `useEffect` 跳转 → 未登录用户仍能拿到受保护页的 RSC/HTML 外壳（数据不泄露，但结构与元信息裸露）。
2. **token 存 localStorage 并复用写入非 HttpOnly cookie**：`client.ts:20-24` 每次请求都把 token 写进 `document.cookie`，任一处 XSS 即可窃取。
3. **全局错误处理缺位**：响应拦截器对 403/404/500 **只 `console.error`**，不弹提示、不上报 → 用户看到空白或旧数据；业务侧用 30 处 `alert` + 15 处 `confirm` 兜底。
4. **报表页首屏恒为空**：四个 tab 都是 `useState(projects[0]?.id || '')`，首次渲染时 `projects` 还是空数组 → 得到空串；数据到达后**没有 `useEffect` 补选**，`enabled: !!projectId` 使 query 永不发起。用户必须手动在下拉里重新点一次项目。（已核实）
5. **甘特图自研像素布局有缺陷**：日期轴按 `floor(totalDays/15)` 取整与任务条 `dayWidth` 比例不一致，长周期时最多错开 step 天；周末背景块**在每个任务行内重复渲染**（N 任务 × M 周末 个绝对定位 div）；后端 `dependencies` 恒为 `Collections.emptyList()`，依赖连线形同虚设。
6. **CSV 导出无公式注入防护**：`csv.ts:9-13` 的 `escapeCell` 只处理逗号/引号/换行，未对 `= + - @ \t` 开头的单元格前置单引号。任务标题/Bug 标题都是用户可填内容，导出后在 Excel 打开会被当公式执行。
7. **Excel 导出绕过 axios 客户端**：自己读 localStorage、自己 `fetch`，401 无处理；中文文件名放 `filename=` 而非 `filename*=`，含空格的项目名会变成 `A+项目_统计报表.xlsx`。
8. **原地修改 React Query 缓存**：`member-workload-tab.tsx:181` 直接 `workload.members.sort(...)` —— 改写缓存数组引用，其他消费同一 queryKey 的组件顺序被副作用改变，且无 `useMemo`，每次渲染都 O(n log n)。
9. **多处 N+1 与无界加载**（见 F2/F3）。
10. **文档/需求/任务的“详情”能力薄弱**：需求详情靠列表派生、文档无 diff、任务无变更历史时间线。

---

## 三、可以增加的功能（对标市面产品）

### 先说定位断档：两整段缺失

你描述的范围是“需求文档 → UI → 开发 → 测试 → 部署上线”。逐段对照：

| 阶段 | DevFlow 现状 | 判断 |
|------|-------------|------|
| 需求文档 | 需求三态 + 字段 + 变更记录 + 文档中心 + Wiki | ✅ 有骨架，但缺结构化（见 1.1） |
| **UI 设计** | **完全没有** | ❌ 整段缺失 |
| 开发 | Git commit 入站 + 任务/缺陷 | ⚠️ 只有 commit 落库，无 MR/PR、无评审、无追溯 |
| 测试 | 用例库 + 执行四态 + 失败转缺陷 | ⚠️ 缺“组织层”（计划/轮次）与自动化回填 |
| **部署上线** | **完全没有** | ❌ 整段缺失 |

### 1. 需求管理进阶

1.1 **需求多层分解**：`requirement.parentId` 列已存在但 DTO/接口/前端入口全无。→ Jira Epic→Story→Subtask，子项完成率自动向上汇总。**P0**
1.2 **需求评审审批流**：`requirement_approval` 表、实体、`ApprovalRequest` DTO 全是死代码（`RequirementServiceImpl:51` 明写“审批功能已移除”），但 `docs/DEVELOPMENT_PLAN.md:18` 仍宣称已实现。→ 参考 TAPD 需求评审（会前异步评论 / 会中逐条结论 / 会后纪要）、飞书审批会签。**P0**
1.3 **验收标准 AC/Gherkin 结构化**：现在正文是一段 Markdown 字符串，AC 不可计数、不可覆盖、不可导出。AC 是“需求→用例→UI 走查”三者的共同锚点，缺它测试覆盖率只能按用例数自我安慰。→ 新建 `ac_item`（Given/When/Then），`test_case` 补 `ac_id`。**P0**
1.4 **需求自定义字段与字段配置方案**：现在字段写死在实体与前端表单里，客户加一个“影响模块/客户名称/复现环境”就要改表改接口改前端发版。→ Jira custom fields + Field Configuration（必填/只读/隐藏）+ Screen Scheme（创建/编辑/流转三套界面）。**这是平台化与做项目的分水岭。**
1.5 **需求池与统一收集入口**：现在需求只能直接建在项目里，客户/销售/内部员工的想法没有收口。→ PingCode/ONES/TAPD 需求池：表单、IM 机器人、客服工单多入口投递，池内去重合并、投票、初评，评审通过后下发并回执提交人。**P1**
1.6 **需求负责人字段与按人筛选**：现在只有 `requesterId`（提出人），需求无法分派给产品/研发，也无法做人均在办量。**P1**
1.7 **需求追踪矩阵 RTM**：需求→设计→任务→用例→缺陷→版本全链路。现有 `test_case.requirement_id` 从未参与覆盖统计。→ Polarion/Jama 的 Traceability Matrix、Xray Requirement Coverage。**P0（政企/金融审计硬要求）**
1.8 **RICE/WSJF 评分模型**：`requirement.sort` 字段无任何接口或界面使用。→ Jira Product Discovery 的公式字段 + 价值/成本四象限。**P2**

### 2. UI 设计协作（整段新建）

**战略建议：不要自研设计画布。** 画布、标注、原型交互是重资产业务，交给 Figma / 蓝湖 / MasterGo / 即时设计；DevFlow 做**编排层**——存指针（provider + fileKey + nodeId）、存版本与快照、存评审状态与走查结论。

2.1 **设计稿托管与嵌入预览**：`design_asset`（需求 ID + provider + 外部 fileKey + 缩略图 + 嵌入链接）。Figma `/embed`、蓝湖/MasterGo 分享链接；iframe 被禁时降级为缩略图卡片 + 新窗口打开。**P0**
2.2 **设计稿版本快照与开发基线锁定**：复用 `document_version` 的“保存前快照”模式做 `design_asset_version`；开发领取任务时绑定**具体版本号**而非“最新”，否则“开发照哪一版做”这个契约不存在，上线后视觉偏差无法回溯。**P0**
2.3 **需求↔设计稿关联（主设计稿唯一）**：一个需求挂多张稿但 PRIMARY 唯一。现在需求与设计之间零数据关联，无法回答“这条需求有没有设计稿”，也做不了设计覆盖率。**P0**
2.4 **设计评审状态机**：`design_status`（NONE → DESIGNING → IN_REVIEW → CHANGES_REQUESTED → APPROVED），打回必须附具体意见并可复审。→ Figma “Ready for dev”、蓝湖设计评审、PingCode 评审流。现有 `requirement_approval` 表可泛化为通用评审记录。**P0**
2.5 **DoR 开发就绪门禁**：有 PRIMARY 设计稿且已通过、AC 非空、有负责人与估算，才能进“开发中”。项目级可开关，默认先告警后拦截。**P1**
2.6 **UI 走查单与一键转 Bug**：走查项 PASS/FAIL/NA 四态，失败一键生成带设计版本与截图的缺陷并回填关联。→ 蓝湖走查单、Zeplin 截图对比、Percy/Chromatic 视觉回归。**P1**
2.7 **设计标注、设计 Token 与切图交付清单**：直接嵌入 Figma Dev Mode 拿真实变量值（尺寸/间距/颜色/代码片段），比自己造标注引擎可靠；切图复用现有 MinIO 通道。**P1**
2.8 **原型链接与结构化交互说明**：触发/前置/行为/动效/兜底文案字段化——这类细节只存在于设计师脑中或群聊截图里，是最容易漏实现的需求。**P1**
2.9 **设计变更通知**：新增 `DESIGN_CHANGED` / `DESIGN_APPROVED` 自动化触发器，通知体带受影响需求、关联任务、负责人与版本对比入口。现有 notification + WebSocket + 企微机器人 + automation_rule 已够用，别另建消息系统。**P2**
2.10 **权限点先补**：`design:manage` / `design:review` / `design:approve` / `walkthrough:manage`，前端按钮可见性与后端判定共用一套（别再重演手写角色比较）。

### 3. 开发与代码

3.1 **MR/PR 全流程与行内代码评审**：行内 diff 评论、suggestion 一键采纳、草稿态、再评审、squash 合并。现在只有 commit 入站，代码评审仍在线下，评审时长与意见数无法度量，DORA 变更前置时间也没有数据源。**建议不自研代码托管**——做“Git 网关”，对接客户已有的 GitHub/GitLab/Gitee，在现有 webhook 端点上扩展 `merge_request` / `note` / `pipeline` / `deployment` 事件类型。**P0**
3.2 **commit/分支↔工作项自动关联与开发面板**：按 commit message 与分支名正则解析回写（`Closes #123`）。**这是成本极低、立刻打通“需求-代码”追溯链的一步。** → Azure DevOps Development 面板（commits/PR/builds/releases 聚合）。**P0**
3.3 **状态机与代码事件绑定**：首个 commit → 开发中，MR 合并 → 待测试，部署到 staging → 待验收，部署到 prod → 已发布，回滚 → 重新打开。这是自研平台相对 GitLab 的**最大差异化**。**P0**
3.4 **分支保护、推送规则与合并策略**：强制 commit message 带需求号后，需求↔代码关联从“靠人补录”变成天然产生。→ GitLab Protected Branches + Push Rules。**P1**
3.5 **安全左移**：SAST/SCA/镜像扫描**引入而非自研**——用 Semgrep/Trivy/Gitleaks 的 SARIF 输出统一入库，DevFlow 只做“漏洞对象 + 门禁策略 + 自动建工单 + 豁免到期”。密钥扫描务必配推送期阻断。

### 4. CI/CD 与发布（整段新建）

**这是你定位里第二个整段缺失，且是 DevFlow 最大的产品叙事故障。**

4.1 **流水线引擎与流水线即代码**：→ GitLab `.gitlab-ci.yml` + CI/CD Components、Azure Pipelines YAML、GitHub Actions Reusable Workflows，支持 DAG `needs` 与 matrix 并行。**P0**
4.2 **质量门禁**：把“单测通过、新增代码覆盖率 ≥ X%、无 Critical 漏洞”定义为可配置门禁，**以 commit status 回写阻断合并**。门禁的价值在“阻断”不在“展示”——这一步不做，门禁永远只是报表。→ GitLab 覆盖率检查审批、GitHub Required status checks。**P0**
4.3 **环境清单与部署记录**：`environment` 应是一等实体而非 CI 变量。每次部署落一条**不可变记录**（制品版本 + 环境 + commit + 触发人 + 审批链 + 结果 + 回滚点）。没人能回答“线上现在跑的是哪一版”是当前的最大运维黑洞。**P0**
4.4 **一键回滚与回滚台账**：回滚是独立动作（关联被回滚部署、触发人、原因、关联事故），生成新记录而不篡改历史，并统计回滚率与回滚耗时。→ GitLab Deployments Rollback、Argo Rollouts。**P0**
4.5 **Release 实体与版本号管理**：现在**完全没有“版本”这个实体**，需求/任务/Bug/commit 无法按版本聚合，也就回答不了“这个版本还差什么才能发”。`versions` 表 + `fix_version` 字段 + Release 视图约 2-3 人日。→ Jira Versions + Release Hub。**P0**
4.6 **Release Notes 自动生成**：平台已有需求状态、Bug 六态、`git_commit` 落库、企业微信推送，**只差按版本聚合，几乎零新增数据**即可自动产出分类变更日志。用最短路径证明价值。**P0**
4.7 **部署审批与受保护环境**：谁能部署 prod、部署前需 N 人批准、同环境互斥。现在只要有配置权限就能发生产。→ GitLab Deployment Approvals + Protected Environments、Azure Environment Checks、GitHub protection rules。**P0**
4.8 **Feature Flag 与渐进式放量**：“部署”与“发布”未解耦，合入即对所有客户可见。不要一步到位对标 LaunchDarkly——先做“环境 + 布尔/JSON 配置 + 定向规则 + 变更审批与审计”的轻量配置中心版，复用现有 RBAC 与 activity_log。**P1**
4.9 **发布准入检查清单 Release Gate**：P0/P1 缺陷清零、测试轮次通过率、需求覆盖率、审批完成、Release Notes 已生成——变成发布前自动闸门。**P1**
4.10 **制品库与镜像仓库**：“构建一次、多处部署”的基础缺失，部署只能重跑构建。→ GitLab Artifact Registry、Azure Artifacts、GitHub Packages。

### 5. 测试管理进阶

DevFlow 测试域的缺口不在“用例 CRUD”，在**组织层**。

5.1 **测试计划与测试轮次（Test Plan / Test Cycle）**：现在只有项目级用例平铺 + 散落的 `test_execution`，无法按版本/迭代组织回归范围，也回答不了“这一轮测了什么、进度多少”。业界共识是**测试计划+轮次 > 需求覆盖率矩阵 > 用例版本快照**。→ TestRail Test Run、Zephyr Scale Test Cycle、MeterSphere 测试计划。**P0**
5.2 **缺陷与需求/任务/迭代/版本双向关联**：`bug` 表完全没有 `requirement_id`/`task_id`/`sprint_id`/`fixVersion`，无法回答“这个需求引入了多少缺陷”“这个版本还有哪些未关闭缺陷”。**P0**
5.3 **用例版本快照与 diff**：现在 `PUT /test-cases/{id}` 直接覆盖全部字段，**历史执行记录无法解释“当时按哪版步骤执行失败”**，失败盘点会系统性失真。→ `test_case_version` + `execution.case_version_id`。**P1**
5.4 **CI 自动化结果回填**：平台已有带密钥校验的 webhook 入口与异步处理范式，**加一个带 token 的 `/test-reports/junit` 上报端点 + JUnit XML 解析**，就能让 CI 结果自动回填四态，把测试模块从“手工台账”变成“自动化看板”。这是投入产出最高的单点。**P1**
5.5 **用例评审流**：建完即生效，无评审状态/评审人/意见。可直接复用现有状态机模式。→ MeterSphere 用例评审（待评审-通过-驳回 + 逐条批注）。**P1**
5.6 **用例目录树/测试套件与标签体系**：只有平铺列表 + 关键字模糊搜索，上百条后无法按模块圈选回归。→ TestRail suites/sections、MeterSphere 模块树 + 标签（冒烟/回归/接口）。**P1**
5.7 **需求覆盖率矩阵与孤儿用例报告**：覆盖率数字只有配上“哪些需求无用例、哪些用例长期未执行”的缺口清单才可执行。**P1**
5.8 **缺陷↔用例执行结果双向状态同步**：现在只有单向（失败转缺陷时回填 `bug_id`），Bug 修复后测试侧完全不知道，复测与回归存在盲区。应做到 RESOLVED → 待复测 → 复测通过 → 关闭或重开。**P1**
5.9 **缺陷 SLA**：无 `due_date`、无时限、无升级，线上问题只能靠群里催。→ Jira Service Management SLA（工作日历、暂停条件、临近违约/违约升级）。**P1**
5.10 **Flaky 用例识别与通过率趋势**：现在取每个用例最新一次结果算通过率，偶发失败被掩盖。保留最近 N 次结果并计算翻转次数。**P1**
5.11 **缺陷去重与重复标记**：同一问题被五个客户分别报一次的常见场景下，无相似检索与 duplicate-of 标记。→ Linear AI 重复检测、Jira duplicates 链接类型。**P1**
5.12 **缺陷逃逸率**：只需给 `bug` 加 `found_stage`（测试/预发/生产）一个枚举，就能算这个管理层最关心的质量指标。

### 6. 项目规划与敏捷

6.1 **自定义工作流与状态机配置**：**这是投入产出比最高的地基改造。** 现在 Task 实体注释写死 `TODO/DOING/TESTING/DONE`、前端 `COLUMNS` 常量写死 4 列，Bug 另有一套 6 态，需求又一套 3 态。→ 建 `workflow`/`status`/`transition` 三张表（Jira 三段式 Condition/Validator/Post Function），**status 必须归入 To Do / In Progress / Done 三类**（Status Category，保证报表口径不破），前端改为按项目拉取列配置。做完这一步，WIP 限制、自定义列、按问题类型差异化流程才能顺次落地。**P0**
6.2 **Backlog 待办池与全项目唯一 rank 排序**：迭代只在看板上呈现，没有独立待办池与拖拽排序，冲刺内容只能凭感觉挑。→ Jira Backlog（拖拽 rank、按 Epic 分组折叠、Sprint Goal、未完成项自动回填）。**P0**
6.3 **层级模型统一**：三张物理表（requirement/task/bug）代表三种“问题类型”，扩展新类型（技术债、线上故障、发布单）就要建表 + Controller + 前端页。长期应统一为 `work_item` + `issue_type` + 层级；**短期可先加 Epic 实体与 `task.epic_id`**，用最小改动拿到“史诗进度条 / 按史诗统计”这个高频诉求。**P1**
6.4 **看板列映射、泳道与 WIP 限制**：四列的列与状态强耦合，想加一列必须改代码；WIP 限制是 Kanban 真正落地的前提，实现成本极低（列配置 + 超限样式 + 流转校验）。**P1**
6.5 **三值工时模型（预估/剩余/已用）**：只有 worklog 累计 `totalHours`，没有 original/remaining estimate，燃尽图只能画理想线，估点偏差与工时超支无法被发现。→ Jira original/remaining/time spent，worklog 自动扣减 remaining。**P1**
6.6 **Cycles 自动化**：已有“完成迭代时迁出未完成任务”说明数据层具备，缺自动生成、scope 快照与周期健康指标。**P1**
6.7 **迭代范围变更与结转统计**：中途插需求、移出任务完全不可见，完成率可以用扩范围美化。应冻结承诺范围快照，同时给出“按原范围”与“按当前范围”两个口径。→ Linear scope change、Jira Sprint Report。**P1**
6.8 **容量规划与历史 velocity 趋势**：只有单迭代燃尽，velocity 也只算当前迭代，无法回答“下个迭代能承载多少”。**P1**
6.9 **任务关注者/订阅与变更历史时间线**：只有需求有字段级 `change_log`，任务改动无审计、无订阅。→ Jira watchers + History 标签页。**P1**
6.10 **项目集（Program）与项目组合视图**：只有“企业→项目”两层，中大型客户多产品线时无法做项目集级里程碑与跨项目资源再平衡。→ ONES/PingCode/云效项目集与 Portfolio（组合气泡图、人力占比堆叠、what-if 推演）。**P1（拿大客户订单的结构性前提）**
6.11 **Triage 分诊收件箱**：当前需求/Bug 直接进列表，而 DevFlow 已有游客反馈通道、企业微信、Git Webhook 三个外部入口——正是最需要闸门的场景。→ Linear Triage（Accept/Decline/合并 + 首响 SLA + AI 语义去重）。**P2**
6.12 **OKR/目标对齐链**：只需在 requirement/project 上加 `objective_id`，并在目标详情页做人力投入聚合，就能讲出“战略穿透”的完整故事。→ 飞书 OKR、ClickUp Goals。**P2**
6.13 **工时审批与饱和度**：让工时数据可信，才有成本核算。

### 7. 文档与知识库

7.1 **多人协同编辑（OT/CRDT）**：现在整篇覆盖保存，多人同时编辑只会互相覆盖或触发唯一键冲突。企业知识库的基本预期是在线协同而不是“谁最后保存谁赢”。→ Yjs/CRDT + WebSocket 文档同步、多人光标。**P1**
7.2 **富文本/块编辑器与 Markdown 渲染**：`content` 只是 Markdown 字符串，前端 textarea 编辑且**预览不渲染 Markdown**。→ Notion 块编辑器、飞书文档、Confluence 宏；块级拖拽、slash 命令、内嵌视图。**P1**
7.3 **文档权限分级与分享链接**：只有“项目成员可见 / 全局按企业可见”两档。→ Confluence 页面级限制、Google Docs 分享链接（期限/密码/访客只读）、Notion 页面共享。**P1**
7.4 **版本差异对比与并发冲突处理**：只有全文快照，无 diff 视图、无 409 冲突提示。→ Confluence 逐行 diff、Notion version history。**P1**
7.5 **全文检索**：未建 MySQL FULLTEXT 或 ES，全局搜索只按标题 LIKE 且前置通配，正文/评论/聊天/文件名都搜不到。**P0**
7.6 **PRD 模板库**：可复用现有 `requirement_template` 表（当前是 JSON 死字段）。
7.7 **白板与故事地图**：优先嵌入 FigJam/Miro 而非自研。

### 8. 协作沟通

8.1 **IM 基础能力补齐**：已有 STOMP、附件、@提及、@AI，但缺**已读回执、编辑撤回、消息线程、表情回应、置顶、消息内搜索**——达不到企业 IM 的基本可用线。→ 飞书/钉钉/Slack。**P1**
8.2 **通知做减法**：现在是“事件即通知”，任务每次字段变更推一条，用户只能全局静音。→ Linear 订阅制通知（Assigned/Mentioned/Subscribed 分组 + 聚合 + Snooze）、免打扰时段、短时间多次变更合并为一条、邮件摘要、Urgent 穿透。**这是噪音问题里最容易见效的一处。** **P1**
8.3 **WebSocket 集群化与可靠投递**：STOMP Broker Relay（RabbitMQ）+ Redis pub/sub；离线队列、序号/游标续传、心跳存活检测。**P1**
8.4 **未读状态持久化**：Zustand 内存态（注释自认“刷新后清零”），无服务端 `last_read_at` 表，换设备即归零。**P1**
8.5 **移动端**：不要做全功能，只做“捕捉 + 收件箱 + 审批”三条路径（Widget/分享菜单/语音录入、离线 Inbox、评审确认）。**P2**

### 9. AI 能力

9.1 **AI 产出一键落库**：`generate_tasks` 只返回原始 JSON 字符串，前端仅 `whitespace-pre-wrap` 展示，**没有解析、校验与创建 Task/TestCase 的回写链路**——AI 停在“看起来有用”而不能真正干活。→ Linear/Asana AI 生成草稿→人工确认入库、Jira AI 工作项生成。**P0**
9.2 **Agent 作为一等参与者**：可被 @、可为 assignee、有明确权限边界、所有写操作走现有权限矩阵并落审计、有执行 session 与进度回写、最小权限作用域。→ Linear for Agents、Asana AI teammates、Jira Rovo Agents。**这是 2025-2026 的分水岭，越晚改代价越大。**
9.3 **Agent 写操作审批与人机协同**：执行前逐次授权、执行中可中止、参数可编辑、执行可回放。一旦开放写操作就存在误改数据风险。**P1**
9.4 **多轮 Agent 会话**：`AgentRequest.conversation_id` 字段被完全忽略，每轮都是无状态单次调用，用户无法追问“那第二个任务呢”。**P1**
9.5 **语义搜索与跨对象 RAG**：全局搜索仍是 SQL 字段匹配，RAG 只存在于 AI 中心内部，**没有嵌到搜索框、文档页、任务详情的“问这个项目”场景**。→ ClickUp Brain Universal Search、Atlassian Rovo Search（Teamwork Graph）、Notion Q&A。**P1**
9.6 **AI 用量计量、配额与模型成本路由**：无按用户/项目/模型的 token 与费用统计、无配额、无超额拦截，也没有“简单任务走便宜模型”的成本路由。AI 接口按次计费，缺少治理会直接变成成本黑洞。**P1**
9.7 **AI 周期报告、迭代总结与交付风险预测**：只有日报入口且无数据来源。周报是研发经理每周固定耗时的动作。→ PingCode/ONES/云效 AI 周报、Linear Project Updates、Asana Smart Status。**P1**
9.8 **可点击的引用溯源**：`sources` 只返回 title/doc_id/score，前端不渲染引用角标也不能跳原文高亮。没有可核对的出处，企业用户不敢把 AI 答案用于决策。**P2**
9.9 **AI 嵌入点密度**：技术储备已超过多数国产竞品的宣传口径；真正该补的是把 AI 塞进需求表单、评审页、用例页、缺陷页、周报页这些高频动作点，并让 Agent 能读度量数据（“帮我解释本周交付周期为什么涨了”）。
9.10 **语义去重与相似工单**：新建 Bug 时实时提示相似项。

### 10. 报表与效能度量

10.1 **DORA 四指标**：现有报表全是过程指标（燃尽/甘特/健康度/工作量），缺部署频率、变更前置时间、变更失败率、MTTR 这套**行业唯一广泛认可的交付效能口径**。数据不需要新埋点，只需补 `deployment` 与 `incident` 两张表。建议先做“部署频率 + 前置时间”两个高可信指标。→ GitLab DORA Metrics、dora.dev、Sleuth/LinearB。**P1**
10.2 **价值流分析与累积流图 CFD**：只有燃尽图回答“还剩多少”，看不到“卡在哪”。按状态累计的 CFD、阶段停留时间与 WIP 趋势是定位瓶颈的直接工具——多数团队 80% 时间花在排队。→ GitLab Value Stream Analytics、Azure CFD。**P1**
10.3 **周期时间/前置时间分布**：当前没有从创建到上线的阶段时间戳，交付周期算不出来。平均值会掩盖长尾，应给 P50/P85/P95 分布与长尾卡点。→ Jira Control Chart、Linear cycle time。**P1**
10.4 **指标卡引擎 + 自定义仪表盘**：报表固定 5 个 Tab，每来一个报表需求就要写一个 Controller（`StatisticsController` 已在膨胀）。应抽象为「指标(metric) × 维度(dimension) × 过滤器(filter) × 图表类型」四元组，前端拖拽拼装。→ Jira Dashboards/Gadget、云效效能洞察。**P1**
10.5 **报表时间范围与对比周期筛选**：企业统计固定近 14 天、燃尽绑定当前 Sprint，无法选区间、做同比/环比。**P1**
10.6 **度量引导“改进而非考核”**：国内厂商踩过的坑——指标直接进 KPI 就会引发流程做手脚（提前关缺陷、拆小需求刷吞吐）。产品上应默认团队粒度、隐藏个人排名。这是**产品设计问题**，不是功能问题。
10.7 **订阅式报表邮件/企微推送**：定时把指标摘要推到群。

### 11. 权限与企业管理

11.1 **SSO（OIDC/SAML/LDAP）与企微/钉钉扫码登录**：当前只有账号密码，**进不了中大型客户的采购清单**。SSO/SCIM 往往比功能更能决定成单。→ Atlassian Guard、Azure AD/Okta。**P0**
11.2 **双因素认证与敏感操作二次确认**：解散企业、重置密码、改权限矩阵这类高危操作只有单因子。2FA 是等保与 SOC2 的基础项。**P0**
11.3 **业务权限点与声明式资源授权**：只有 13 个权限点对 158 个端点，且 `PROJECT_MANAGE`/`MEMBER_INVITE`/`MEMBER_REMOVE`/`MEMBER_ROLE`/`MEMBER_DEPARTMENT`/`BUG_STATUS_CHANGE` **引用次数为 0**；任务、迭代、文档、需求、附件、聊天模块**没有任何项目级权限点**，只靠“是不是项目成员”控制，MEMBER 与 TESTER 在任务/迭代/文档上权限完全一致。→ Jira Permission Scheme（数十个权限点 + 项目角色）、`@PreAuthorize` + 资源级 ACL + 租户拦截器。**P0**
11.4 **多租户隔离硬化**：在 MyBatis 层自动注入 `tenant_id` 条件并强校验租户上下文，而不是逐个控制器手写。**P0**
11.5 **企业自定义角色与数据范围**：固定四级角色无法适配不同企业的岗位体系。客户一句“我们要一个只读的审计角色”就要改代码。→ Jira project roles、ONES 自定义角色 + 数据范围（本人/部门/项目/全企业）。**P1**
11.6 **字段级脱敏与水印**：手机号、邮箱、工时、客户联系人等敏感字段对所有有权限的人明文可见，导出文件无管控。**等保测评与客户安全评审会直接卡住。** **P1**
11.7 **审计导出、防篡改与 SIEM 外发**：已有 `activity_log`，但不可导出、不可按保留期归档、无哈希链防篡改，无法作为 SOC2/ISO27001/等保的取证材料。→ GitLab Audit Events 只追加 + SIEM 投递。**P1**
11.8 **记录级安全（Issue Security）**：漏洞、薪酬类需求仅特定角色可见。→ Jira Issue Security Level。**P1**
11.9 **密码策略**：现在只校验长度 ≥6，无复杂度/过期/历史复用/首次登录强制改密；超管重置出来的临时密码无 TTL、无 `must_change_password` 标记。**P1**
11.10 **组织架构同步（SCIM/企微通讯录）**：手工维护部门与成员不可持续。

### 12. 开放平台与集成

12.1 **JQL 式结构化查询语言与保存筛选器**：`SearchController` 只有单个 `q` 关键词参数，无法表达“我参与的项目里本周到期、P1、且 7 天没更新的 Bug”。若把“一个查询”抽象成可保存、可共享的 filter 对象，**看板、仪表盘、自动化条件、订阅邮件全部复用它**，报表口径自然一致。建议自研受限表达式解析器（递归下降 → 参数化 SQL 白名单），**不要暴露原始 SQL**。**P0**
12.2 **视图体系抽象为“同一查询 + 多渲染器”**：现在每页一套 UI。先落地查询中间表示（filter AST + 序列化到 URL），再挂 List/Board/Timeline/Calendar/Workload。新增视图的成本从“重写一个页面”降到“写一个渲染器”。**P1**
12.3 **OpenAPI 对外开放与 OAuth 应用授权**：158 个端点只服务自家前端，无版本化、限流、应用注册与 scope 授权，客户 IT 与 ISV 无法集成。→ Jira REST API v3 + Forge、Linear GraphQL + OAuth scopes。**P1**
12.4 **Webhook 事件目录与标准签名**：现有 Git webhook 用静态 secret 拼进 URL，无 HMAC 签名、时间戳与重放窗口。→ GitLab/Jira Webhooks（HMAC-SHA256 + 时间戳 + 常量时间比较 + 重放窗口 + 失败重试）。**P1**
12.5 **MCP server / 插件体系**：让外部 agent 与工具能接入。
12.6 **项目模板市场**：把已配置好的项目整体序列化（工作流 + 字段 + 看板 + 规则 + 示例数据）再实例化，**显著降低新客户实施成本**。
12.7 **自动化引擎升级**：现有 `AutomationRule` 是 4 个硬编码触发 + 2 个动作。建议升级为 `trigger / condition / action / branch` 四元组 + Smart Values 模板变量 + JSON 存规则 + 执行历史审计 + 每月配额，配 8-10 个开箱模板（超时未更新提醒、Bug 自动分诊、迭代结束清理、周报推送、SLA 升级）。**这是销售演示时最容易被客户当场点名对比的能力。**

### 13. 运维与 SaaS 化

13.1 **三端 Dockerfile 与生产 compose**（多阶段构建、镜像仓库、语义化版本标签、容器资源限制）。**P0**
13.2 **CI 流水线与测试/扫描卡点**（构建、单测、lint、SAST、依赖扫描、镜像构建推送）。**P0**
13.3 **Flyway/Liquibase 版本化数据库迁移**。**P0**
13.4 **可观测性**（Actuator + Micrometer + Prometheus + Grafana、OpenTelemetry 链路、慢 SQL 阈值告警、前端错误上报）。**P1**
13.5 **网关/Nginx/HTTPS 终结与安全响应头**（HSTS/CSP/X-Content-Type-Options/Referrer-Policy、WS upgrade 代理参数）。**P1**
13.6 **优雅停机、健康探针与备份恢复**（`server.shutdown=graceful`、K8s liveness/readiness、定时备份 + 恢复演练与 RPO/RTO 定义）。**P1**
13.7 **乐观锁、事务性事件 Outbox 与幂等/限流**（`@Version` + `Transactional Outbox` + 幂等键/去重表 + Redis 集中限流）。**P1**
13.8 **信创适配**：国产数据库（人大金仓/达梦/OceanBase）、麒麟 OS、K8s 部署包。国内中大型研发团队采购习惯是**私有化 + 年度授权**，补齐这些比建复杂 SaaS 计费系统更快产生收入。

---

## 四、建议的落地顺序

### 第 0 批：安全止血（1-2 周，不做完不宜对外演示或交付）

1. 11 条越权接口全部补鉴权 + `@RequestBody 实体`换白名单 DTO
2. WebSocket CONNECT 失败即拒绝 + SUBSCRIBE 订阅鉴权 + 移除 `anonymous` 降级
3. ai-service 加认证中间件（共享 JWT 或 service token）+ 收窄 CORS
4. RAG metadata 写入 `project_id`/`enterprise_id` + query 加 `where` 过滤
5. 密钥全部迁到环境变量，`application.yml` 只留占位符；轮换已泄露的 JWT secret
6. 文件上传加类型白名单 + 下载改 `attachment` + 加 `nosniff`/CSP
7. `User.password` 加 `@JsonIgnore` + `/users/me` 置空
8. `sys_role_permission` 加 tenant 维度
9. 业务异常加 `@ResponseStatus`，HTTP 状态与 body code 对齐

### 第 1 批：修“假功能”与致命体验（1-2 周）

自动指派落库、BUG_FAIL 触发器、TASK_DONE 条件、Sprint↔Task 写入入口、看板 extras 传参、任务指派控件、聊天分页方向（DESC + 加载更多）、报表首屏项目补选、需求详情路径、任务评论联查用户、Markdown 渲染、AI 日报接真实数据

### 第 2 批：地基改造（1-2 月，决定后面所有功能的速度）

工作流/状态表（含 Status Category）→ 版本/Release 实体 → 测试计划/轮次 → 设计稿关联 + 设计状态机 → JQL 式查询 + 保存视图 → Flyway 迁移 → 三端 Dockerfile + CI

### 第 3 批：补齐两段断档（2-3 月）

UI 设计协作（设计稿托管 → 关联 → 评审 → 走查）
CI/CD 与发布（流水线 → 环境/部署记录 → 审批 → 回滚 → Release Notes → DORA）

### 第 4 批：企业级门槛（并行，按客户需求驱动）

SSO + 2FA + 字段级脱敏 + 审计导出 → 自定义角色与数据范围 → 可观测性 + 备份恢复 → 自定义字段 → 项目集

---

## 五、一句话总结

**DevFlow 的功能密度已接近国产一线产品，但它现在更像一个“演示很完整、交付不够硬”的系统**：安全边界是点状的、两段定位是空的、若干功能是断链的。先把安全止血（第 0 批）与断链修复（第 1 批）做完，再做工作流/版本/设计/发布这四块地基——这四块做完，DevFlow 才真正覆盖你描述的“需求文档 → UI → 开发 → 测试 → 部署上线”全流程。
