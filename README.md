# DevFlow

## 企业级 AI 实时协作研发平台

> **Everything about software development happens here.**
>
> 一个融合 **飞书 + Jira + Notion + GitHub + AI Agent** 的企业研发平台。

---

## 项目简介

DevFlow 是一套面向企业研发团队的 **AI Native 协同研发平台**，覆盖研发全生命周期：

```
企业组织 → 项目管理 → 需求管理 → Sprint → 任务 → Bug → 文档 → 实时聊天 → AI助手 → 代码协作 → 版本发布
```

---

## 技术架构

```
                Browser
                    │
          Next.js 16 (React 19)
                    │
      ┌─────────────┴──────────────┐
 Spring Boot 3.4              Python FastAPI
      │                              │
  MySQL 8  Redis 7            ChromaDB (向量库)
      │                              │
            MinIO (文件存储)
```

### 前端

| 技术 | 用途 |
|------|------|
| Next.js 16 (App Router) | 全栈框架 |
| React 19 | UI 框架 |
| TypeScript | 类型安全 |
| TailwindCSS 4 | 样式系统 |
| Shadcn UI | 组件库 |
| TanStack Query | 数据请求 |
| Zustand | 状态管理 |
| Framer Motion | 动画 |
| Lucide Icons | 图标 |
| next-themes | 主题切换 |

### Java 后端

| 技术 | 用途 |
|------|------|
| Spring Boot 3.4 | 应用框架 |
| Spring Security + JWT | 认证授权 |
| MyBatis Plus 3.5 | ORM 框架 |
| MySQL 8.0 | 关系数据库 |
| Redis 7 | 缓存/会话 |
| MinIO | 对象存储 |
| WebSocket | 实时通信 |
| Springdoc OpenAPI | API 文档 |

### Python AI 服务

| 技术 | 用途 |
|------|------|
| FastAPI | Web 框架 |
| LangChain | LLM 编排 |
| OpenAI SDK | 大模型调用 |
| ChromaDB | 向量数据库 |
| Sentence Transformers | 文本嵌入 |
| Celery | 异步任务 |

---

## 快速开始

### 1. 启动基础设施

```bash
# 启动 MySQL、Redis、MinIO、ChromaDB
docker-compose up -d
```

### 2. 初始化数据库

```bash
# MySQL 会自动执行 schema.sql（docker-compose 中配置）
# 手动执行：
mysql -u root -pdevflow123 devflow < backend/src/main/resources/schema.sql
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

### 4. 启动 Java 后端

```bash
cd backend
mvn spring-boot:run
# API: http://localhost:8080/api/v1
# Swagger: http://localhost:8080/api/v1/swagger-ui.html
```

### 5. 启动 AI 服务

```bash
cd ai-service
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000
# API: http://localhost:8000/api/v1
# 文档: http://localhost:8000/docs
```

### 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| zhangsan | admin123 | 成员 |

---

## 产品模块

| 模块 | 说明 | 状态 |
|------|------|------|
| 企业组织 | 企业/部门/成员/角色管理 | Phase 2 ✅ |
| 工作台 | 首页仪表盘 + 数据联动 | Phase 2 ✅ |
| 项目中心 | 项目 CRUD | Phase 2 ✅ |
| 企业管理 | 成员管理/部门树/邀请码 | Phase 2 ✅ |
| Dashboard | 项目统计/燃尽图/甘特图 | Phase 3 ✅ |
| 需求管理 | 需求 CRUD/审批流/关联任务 | Phase 3 ✅ |
| Sprint | 迭代管理/Kanban | Phase 3 ✅ |
| 任务管理 | 拖拽/筛选/批量编辑 | Phase 3 ✅ |
| Bug 中心 | Bug 生命周期 | Phase 3 ✅ |
| 聊天室 | WebSocket 实时聊天 | Phase 4 ✅ |
| 通知中心 | 实时推送 | Phase 4 ✅ |
| 文档中心 | Markdown/Notion 风格 | Phase 5 ✅ |
| Wiki | 企业知识库 | Phase 5 ✅ |
| 文件中心 | MinIO 存储/在线预览 | Phase 5 ✅ |
| AI 平台 | RAG/Agent/Code Review | Phase 6 ✅ |
| 统计中心 | 报表/项目健康度 | Phase 7 |

---

## 项目结构

```
DevFlow/
├── frontend/           # Next.js 前端
│   ├── src/
│   │   ├── app/        # 页面路由 (App Router)
│   │   ├── components/ # 组件
│   │   ├── lib/        # 工具函数
│   │   ├── stores/     # Zustand 状态管理
│   │   └── types/      # TypeScript 类型
│   └── package.json
│
├── backend/            # Spring Boot 后端
│   ├── src/main/java/com/devflow/
│   │   ├── config/     # 配置类
│   │   ├── controller/ # 控制器
│   │   ├── service/    # 服务层
│   │   ├── mapper/     # 数据访问
│   │   ├── entity/     # 实体类
│   │   ├── dto/        # 数据传输
│   │   ├── security/   # 安全配置
│   │   └── common/     # 通用组件
│   ├── pom.xml
│   └── src/main/resources/
│       ├── application.yml
│       └── schema.sql
│
├── ai-service/         # Python AI 服务
│   ├── app/
│   │   ├── api/v1/     # 接口路由
│   │   ├── core/       # 核心配置
│   │   └── services/   # 业务服务
│   ├── pyproject.toml
│   └── .env
│
├── docker-compose.yml  # 基础设施
└── scripts/            # 运维脚本
```

---

## 开发阶段

| 阶段 | 模块 | 目标 |
|------|------|------|
| Phase 1 | 基础架构 | ✅ Next.js、Spring Boot、FastAPI、登录、权限、响应式布局 |
| Phase 2 | 企业与工作台 | ✅ 企业管理、成员/部门管理、邀请码、Workspace 数据联动 |
| Phase 3 | 项目管理 | ✅ Dashboard、需求管理（审批流）、Sprint、任务看板、Bug 中心 |
| Phase 4 | 实时协作 | ✅ WebSocket、聊天室、通知中心 |
| Phase 5 | 文档与文件 | ✅ 文档中心（Markdown）、Wiki 知识库、文件管理（MinIO） |
| Phase 6 | AI 平台 | ✅ RAG 知识库、Agent 智能体、Code Review、AI 助手 |
| Phase 7 | 数据分析 | 统计报表、项目健康度 |
| Phase 8 | DevOps | Git 集成、CI/CD |
| Phase 9 | 性能与部署 | Docker、Nginx、生产部署 |

---

## 设计理念

- **极简现代** - 参考 Linear / Notion / Vercel 设计风格
- **响应式** - 桌面 / 笔记本 / 平板 / 手机全适配
- **深色主题** - 原生支持 Light / Dark 模式
- **动效克制** - 150-250ms 微妙过渡
- **AI Native** - AI 不是插件，而是平台核心

---

© 2026 DevFlow. All rights reserved.
