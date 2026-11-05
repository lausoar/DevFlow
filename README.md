# DevFlow

> **企业级 AI 协同研发平台** —— Everything about software development happens here.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-16-black?logo=next.js)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-Python-009688?logo=fastapi&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-010101?logo=socket.io)

DevFlow 是一套面向企业研发团队的 **AI Native 协同研发平台**，对标飞书项目 / PingCode / ONES，覆盖研发全生命周期：

```
企业组织 → 项目管理 → 需求 → 迭代 → 任务 → Bug → 测试用例 → 文档 Wiki → 实时聊天 → AI 助手 → 统计报表
```

三个独立服务协同工作：**Next.js 前端**（16 个路由模块）、**Spring Boot 主服务**（28 个 Controller / 158 个 REST 端点 / 41 张表）、**FastAPI AI 服务**（RAG 知识库 + 多模型路由）。已有真实团队落地日常使用。

<!-- 📸 截图区：把界面截图放到 docs/screenshots/ 目录后，取消下面的注释
## 📸 界面预览

| 工作台 | 任务看板 |
|--------|----------|
| ![工作台](docs/screenshots/workspace.png) | ![任务看板](docs/screenshots/kanban.png) |

| 实时聊天 + @AI | 权限矩阵 |
|----------------|----------|
| ![聊天](docs/screenshots/chat.png) | ![权限矩阵](docs/screenshots/permission-matrix.png) |
-->

---

## ✨ 功能总览

### 🏢 企业组织

- **四级角色体系**：SUPER_ADMIN / ADMIN / MANAGER / MEMBER，职级严格降序管理——只能操作和授予严格低于自己职级的角色
- **部门树**：递归树形结构，支持子部门、成员调岗、环检测（防止把部门挂到自己子树下）、同级重名校验
- **成员管理**：邀请码加入、一次性初始密码、超管重置密码、自助退出企业（最后一位超管保护）
- **审计日志**：企业/项目敏感操作（创建、解散、邀请、改职级、权限矩阵修改、**权限拒绝**）全量落库可查

### 📋 项目管理

- **需求管理**：OPEN / COMPLETED / CLOSED 三态生命周期、优先级、预估工时（支持 0.5h 小数）、需求↔任务双向关联、字段级变更记录（旧值→新值、操作人、时间）
- **迭代（Sprint）管理**：激活、完成时未完成任务自动迁出（返回迁移数量）、燃尽图数据源
- **任务管理**：四列看板 + **dnd-kit 拖拽流转**、子任务清单（勾选进度）、**任务依赖**（BFS 环检测、前置完成自动通知、看板徽章提醒）、工时登记（0.1~24h 校验、与预估对比）、评论 + @提及
- **Bug 中心**：NEW / ASSIGNED / DOING / RESOLVED / REOPENED / CLOSED 六态流转（仅负责人/报告人可改）、**描述内嵌截图**（Ctrl+V 粘贴直接上传、Markdown 语法渲染）、游客提交的 Bug 需站内确认、批量指派/批量改优先级
- **测试用例管理**：用例库（前置条件/步骤/预期结果/优先级）、执行记录四态（PASS/FAIL/BLOCKED/SKIP）、**失败一键转缺陷**（自动拼装复现步骤并回填 bug_id）、统计看板

### 💬 实时协作

- **项目聊天室**：STOMP over SockJS 实时收发、频道与项目动态派生成员、消息未读角标（页外 2 秒内提醒）、@提及通知（支持用户名与真实姓名）
- **通知中心**：站内通知实时推送 + 点击直达上下文（`/projects/{id}?tab=bugs` 深链）、顶栏悬浮面板、全部已读
- **在线状态**：WebSocket 会话级 Presence 追踪（支持多端在线）、成员列表实时绿点
- **@AI 助手**：聊天中 @AI 提问，RAG 优先、纯 LLM 兜底，以"AI 助手"身份回复频道

### 📚 文档与文件

- **文档中心**：Markdown 编辑、**版本历史**（快照、预览、一键恢复，恢复本身也产生新版本）、评论
- **Wiki 知识库**：企业级知识树
- **文件中心**：MinIO 对象存储、在线预览、下载鉴权（JWT 支持 Header 与 Cookie 双通道，兼容 `<img>` 标签）

### 🤖 AI 平台（独立 FastAPI 服务）

- **RAG 知识库**：文档自动入库（Java 侧异步同步）→ 500-token 分片 → 批量 Embedding → ChromaDB 向量检索；重复索引幂等（先清旧分片）；嵌入服务不可用时自动降级纯 LLM
- **多模型路由**：OpenAI / DeepSeek / 通义千问 / Kimi / 火山方舟 / 智谱 GLM 六家一处切换
- **本地嵌入零成本模式**：`EMBEDDING_PROVIDER=local` 时用 sentence-transformers + `BAAI/bge-small-zh-v1.5` 本地中文向量，不花一分钱
- **AI Agent**：接入业务数据（透传用户 JWT 调后端），支持"查我的任务 / 总结我的 Bug / 项目进度"等工具调用
- **对话历史持久化**：按账号落库、切页恢复
- **Code Review**：AI 代码审查入口

### 📊 统计报表

- 燃尽图、甘特图（含迭代泳道）、项目健康度（进度/需求完成率/缺陷密度）、成员工作量、企业级统计
- **CSV 导出**（UTF-8 BOM，Excel 中文不乱码）、Excel 导出（EasyExcel）

### 🔔 集成与自动化

- **自动化规则引擎**：`BUG_CREATED / BUG_FAIL / CASE_FAIL / TASK_DONE` 四类触发器，支持自动指派 + 自动通知，fail-open 设计（规则异常不影响主流程）
- **企业微信机器人**：Webhook 出站推送（新缺陷、用例失败、Git push 摘要），Markdown 格式，异步失败静默
- **Git Webhook**：兼容 GitHub / GitLab / Gitee push 事件，commit 落库去重、项目概览展示最近提交
- **游客反馈通道**：免登录链接（长期/7天/30天有效期，可撤销），外部用户提交 Bug 与建议，站内确认/转缺陷

---

## 🏗️ 系统架构

```
                        ┌─────────────────────┐
                        │       Browser        │
                        │  Next.js 16 + React 19 │
                        └──┬───────────────┬──┘
              HTTP (REST)  │               │  WebSocket (STOMP/SockJS)
                           ▼               ▼
        ┌──────────────────────────────────────────┐
        │         Spring Boot 3.4 主服务 :8080       │
        │  Spring Security + JWT + MyBatis Plus     │
        │  28 Controller / 158 REST 端点 / 41 张表   │
        └───────┬──────────┬──────────┬────────────┘
                │          │          │ RestClient（异步知识同步）
        ┌───────▼──┐  ┌────▼───┐  ┌───▼──────────────┐
        │ MySQL 8  │  │ Redis 7│  │ FastAPI AI :8000  │
        │  41 张表  │  │ 黑名单  │  │ LangChain 多模型   │
        └──────────┘  └────────┘  │ RAG / Agent       │
                ┌──────────────┐  └───────┬───────────┘
                │ MinIO :9002  │          │
                │  文件对象存储  │    ┌─────▼──────────┐
                └──────────────┘    │ ChromaDB :8001  │
                                    │  向量知识库      │
                                    └────────────────┘
```

| 服务 | 技术栈 | 职责 |
|------|--------|------|
| **frontend** | Next.js 16 (App Router) / React 19 / TypeScript 5 | 全部 UI，16 个路由模块，深浅色主题 |
| **backend** | Spring Boot 3.4 / Spring Security + JWT / MyBatis Plus 3.5 / Quartz / EasyExcel | 业务主服务：认证授权、全部业务域、WebSocket、审计 |
| **ai-service** | FastAPI / LangChain / OpenAI SDK / sentence-transformers | LLM 对话、RAG 检索、Agent 工具调用、多模型路由 |

### 基础设施（docker-compose 一键启动）

| 组件 | 版本 | 端口 | 用途 |
|------|------|------|------|
| MySQL | 8.0 | 3306 | 业务数据，41 张表，启动自动建表+种子数据 |
| Redis | 7 | 6380→6379 | JWT 黑名单、缓存 |
| MinIO | latest | 9002 / 9003 | 对象存储（API / 控制台） |
| ChromaDB | latest | 8001→8000 | RAG 向量数据库 |

---

## 🧠 核心设计与实现

### 1. 双域 RBAC 权限体系

```
sys_permission（权限点，如 bug:create / member:manage）
sys_role_permission（角色 × 权限点映射，企业域 4 角色 + 项目域 4 角色）
```

- **企业域 + 项目域双 scope**：一个用户同时受"企业职级"和"项目内角色"（OWNER/MANAGER/TESTER/MEMBER）两套权限约束，互不越界
- **零查表判定**：角色-权限映射在启动时全量预载进 `ConcurrentHashMap`，运行期权限判定不访问数据库；后台改矩阵后 `reload()` 热生效
- **可视化矩阵**：管理后台 48 格勾选矩阵（企业 6 权限点 × 4 角色 + 项目 6 × 4），改完全局即时生效
- **拒绝即审计**：每次权限判定失败自动写审计日志（谁、在哪个项目、想干什么、被拒）

### 2. JWT 双通道认证 + Redis 主动失效

```
HTTP:     JwtAuthFilter → Authorization Header（df_token Cookie 兜底，供 <img> 下载用）
WebSocket: WebSocketAuthInterceptor → CONNECT 帧解析 JWT → 绑定会话身份
登出:     TokenBlacklistService → Redis SETNX token:blacklist:<jwt> TTL=剩余有效期
```

无状态 JWT 的"登出即失效"难题用 Redis 黑名单解决：TTL 设为 Token 剩余有效期，过期自动清理；Redis 不可用时降级放行，不阻断业务。

### 3. STOMP 实时通信设计

| 目的地 | 方向 | 内容设计 |
|--------|------|----------|
| `/topic/channel.{id}` | 频道广播 | 完整聊天消息 |
| `/topic/chat.events` | 全局轻量事件 | 只含 `{channelId, senderId}` **不含消息体**——未读角标够用，且不向非频道成员泄露内容 |
| `/topic/presence` | 在线状态 | 上下线事件，多端会话聚合 |
| `/user/queue/notifications` | 个人通知 | 点对点通知实时推送 |

配套：断线 Disconnect 兜底监听（浏览器直接关闭不发 DISCONNECT 帧的场景）、15s 轮询兜底。

### 4. RAG 知识库管线

```
文档保存（Java）→ ai-async 异步线程 → FastAPI /rag/index
  → 500-token 分片 → 批量 Embedding → 清旧分片（幂等）→ ChromaDB 写入
提问 → query Embedding → ChromaDB top-K 检索（cosine distance → similarity）
     → 命中上下文 + 问题 → LLM 生成（引用知识库回答）
故障降级：嵌入 404 / 向量库未启动 / LLM 超时 → 逐级降级至纯 LLM，永不 500
```

### 5. 项目可见性统一口径

所有"用户能看到哪些项目/频道/文档/搜索结果"的判定收敛到 `ProjectAccessService` 单一入口（成员本人 + 企业超管管理视野），杜绝各处自门口径导致的越权泄漏。未知资源与无权资源返回同构响应，不泄露资源存在性。

### 6. 登录防爆破

账号维度滑动窗口：15 分钟内 5 次失败即锁定（429，正确密码也拒），成功登录清零，Map 超限自保清理，其他账号不受影响。

---

## 🗄️ 数据模型（41 张表）

| 域 | 表 |
|----|-----|
| 组织 | `enterprise` `sys_user` `department` `department_member` |
| 权限 | `sys_permission` `sys_role_permission` `activity_log` |
| 项目 | `project` `project_member` `project_guest` `project_feedback` `sprint` |
| 需求 | `requirement` `requirement_task` `requirement_change_log` |
| 任务 | `task` `task_checklist` `task_dependency` `task_worklog` `task_comment` |
| 缺陷 | `bug` `bug_comment` |
| 测试 | `test_case` `test_execution` |
| 文档 | `document` `document_version` `document_comment` |
| 协作 | `chat_channel` `chat_message` `notification` |
| AI | `ai_chat_message` |
| 文件 | `file_item` |
| 集成 | `automation_rule` `git_commit` |

建表脚本幂等（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`），升级已有库自动补列/补数据。

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | 后端编译运行 |
| Node.js | 20+ | 前端 |
| Python | 3.11+ | AI 服务（推荐 [uv](https://github.com/astral-sh/uv) 管理依赖） |
| Docker | - | 一键启动基础设施 |

### 1️⃣ 启动基础设施

```bash
docker-compose up -d        # MySQL + Redis + MinIO + ChromaDB
```

MySQL 容器首次启动自动执行 `schema.sql`（建库建表 + 种子数据 + 幂等迁移）。

### 2️⃣ 启动 Java 后端（:8080）

```bash
cd backend
mvn spring-boot:run
# API 前缀: http://localhost:8080/api/v1
# Swagger:  http://localhost:8080/api/v1/swagger-ui.html
```

### 3️⃣ 启动 AI 服务（:8000）

```bash
cd ai-service
cp .env.example .env        # 填入任一家 LLM 的 API Key（六选一）
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000
# 文档: http://localhost:8000/docs
```

> 💡 不配 API Key 也能跑：AI 功能走降级路径，不影响主平台使用。本地嵌入模式（`EMBEDDING_PROVIDER=local`）RAG 完全免费。

### 4️⃣ 启动前端（:3000）

```bash
cd frontend
npm install
npm run dev
# 访问: http://localhost:3000
```

### 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 企业超管 |
| zhangsan | admin123 | 普通成员 |

支持**用户名或邮箱**登录。

---

## 📡 API 与实时接口

- **REST**：158 个端点，Swagger UI 自动生成（`/api/v1/swagger-ui.html`）
- **WebSocket**：`/api/v1/ws`（SockJS 握手，JWT 随 CONNECT 帧认证）
- **Git Webhook**：`POST /api/v1/projects/{id}/git/webhook?secret=xxx`（免 JWT，密钥校验，兼容三家平台）

---

## 🧪 测试

```bash
cd backend
mvn test        # JDK 17，约 30s
```

分层设计：

| 层 | 测试类 | 覆盖 |
|----|--------|------|
| 纯逻辑 | `EnterpriseRolesTest` | 四级角色防越权规则边界 |
| 单元（Mockito） | `PermissionServiceImplTest` | 权限映射加载、双域判定、拒绝审计、reload 热更新 |
| 单元（Mockito） | `GuestServiceImplTest` | 游客 token 三态、限流、待确认队列、转缺陷回填 |
| 单元（Mockito） | `ProjectAccessServiceImplTest` | 可见性口径：成员/非成员/超管并集/无企业 |
| 单元（Mockito） | `SearchServiceImplTest` | 搜索空态与可见性边界 |
| 集成（HTTP 直连） | `PermissionMatrixHttpTest` | 真实服务 401/403/200 语义、越权响应不泄露数据 |

---

## 📁 项目结构

```
DevFlow/
├── frontend/                  # Next.js 16 前端
│   └── src/
│       ├── app/               # App Router（(auth)/(main)/guest 三组路由）
│       ├── components/        # layout + 16 个 shadcn 风格 UI 组件
│       ├── lib/api/           # 23 个领域的 API 封装
│       ├── lib/hooks/         # 21 个 TanStack Query hooks
│       ├── lib/ws/            # STOMP 连接管理
│       ├── stores/            # Zustand（auth/chat-unread/presence/app）
│       └── types/             # 全局 TypeScript 类型
│
├── backend/                   # Spring Boot 3.4 主服务
│   └── src/main/java/com/devflow/
│       ├── config/            # Security / WebSocket / MyBatis / MinIO / Async
│       ├── controller/        # 28 个 Controller
│       ├── service/           # 23 个领域服务（接口+实现）
│       ├── mapper/            # MyBatis Plus 数据访问
│       ├── entity/            # 41 张表实体
│       ├── dto/               # request / response
│       ├── security/          # JwtAuthFilter
│       └── common/            # 角色常量 / 权限点 / 统一返回 / 全局异常
│
├── ai-service/                # FastAPI AI 服务
│   └── app/
│       ├── api/v1/            # chat / rag / agent / assistant
│       └── services/          # 多模型路由 / RAG / Embedding / Chroma 客户端
│
├── docs/                      # 开发计划 / 权限体系设计文档
├── docker-compose.yml         # MySQL + Redis + MinIO + ChromaDB
└── scripts/                   # 开发启动脚本
```

---

## 🗺️ Roadmap

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1-2 | 基础架构、登录认证、企业组织、工作台 | ✅ |
| Phase 3 | 需求/迭代/任务/Bug/测试用例全流程 | ✅ |
| Phase 4 | WebSocket 实时聊天、通知、在线状态 | ✅ |
| Phase 5 | 文档中心、版本历史、Wiki、文件中心 | ✅ |
| Phase 6 | RAG 知识库、AI 助手、Agent、Code Review | ✅ |
| Phase 7 | 权限矩阵可视化、审计日志、自动化规则、报表导出 | ✅ |
| Phase 8 | SSO / 自定义字段 / Git 深度集成 | 🔜 |
| Phase 9 | 全文检索引擎（ES）、生产化部署（Nginx/CI-CD） | 🔜 |

## ⚖️ 设计取舍（已知限制）

坦诚记录当前的权衡，也是后续迭代方向：

- 全局搜索基于 MySQL `LIKE`，未引入 ES——中小团队规模下够用，数据量上来后是第一个要换的件
- 统计报表为内存聚合（无物化/无 SQL 聚合），适合当前量级
- 防爆破计数在单实例内存（重启清零），多实例部署需迁移 Redis
- 邮件通知未接 SMTP（当前通知通道：站内 + 企业微信机器人）

---

© 2026 DevFlow
