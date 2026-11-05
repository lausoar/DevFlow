# DevFlow 权限体系设计（RBAC + 资源关系混合模型）

> 状态：第 1 期（权限地基）已实施（2026-10-20）；四个决策点已全部拍板（见 §8）
> 实施：sys_permission/sys_role_permission 表+种子、PermissionService、Bug/项目成员控制器检查收拢、OWNER/MANAGER 豁免 bug 操作（@PreAuthorize 注解化留待第 2 期）
> 结论：**RBAC 行，作为骨架保留；但单一 RBAC 不够，推荐 "RBAC + 资源关系(ReBAC-lite) + 少量规则" 的混合模型**，自建轻量 `PermissionService`，不引入外部授权服务。

---

## 1. 先回答问题：RBAC 行不行？有没有更好的？

**RBAC 是正确底座，但不是完整答案。** 对比主流模型：

| 模型 | 核心思想 | 适合 DevFlow 的部分 | 不适合的部分 |
|------|---------|--------------------|--------------|
| **RBAC** | 用户→角色→权限，角色是权限集合 | 企业角色、项目角色（现有体系） | 资源级授权表达不了：bug 负责人/创建人可改状态、"这个项目的游客" |
| **ABAC** | 按属性规则动态判定（部门=X、时间∈Y） | 少量上下文规则（同企业隔离、等级压制） | 全量 ABAC 规则难维护、难审计，规则爆炸 |
| **ReBAC** | 按资源关系图判定（Zanzibar/OpenFGA） | 负责人/报告人/游客协作者，天然的"关系"授权 | 引入 OpenFGA/SpiceDB 需独立服务+数据同步，单体应用阶段过重 |
| **ACL** | 每个资源挂一张人清单 | 单资源偶发共享 | 几千人项目时清单失控 |

**业界参照**：GitHub / GitLab / Jira 没有一个是纯 RBAC——都是"组织级 RBAC + 仓库/项目级角色 + 资源关系（assignee、collaborator、guest）"的混合体。DevFlow 的形态（企业→项目→任务/Bug 的两层资源树 + 分享需求）和 GitHub 几乎同构，照这个混合路线走最稳。

**推荐：RBAC 骨架 + ReBAC-lite（自研资源关系表）+ 少量硬规则。** 不上 OpenFGA/SpiceDB——等出现"任意跨企业共享、任意深度嵌套授权"的真实需求再评估。

---

## 2. 现状盘点（为什么要动）

现有代码（2026-10-20 盘点）：

1. **两套并行角色系统，无统一判定入口**
   - 企业角色：`sys_user.role`，7 级（`EnterpriseRoles`，等级 100~10）
   - 项目角色：`project_member.role`，OWNER/MANAGER/TESTER/MEMBER
   - 判定逻辑散落在 Controller/Service 里手写 `equals` 比较
2. **已经因此出过 4 个洞**（本轮系列修复）：项目加成员接口裸奔、部门权限硬编码漏改、Bug 指派可越权、项目改角色无校验。模式性问题：**每加一个功能就要手写一遍鉴权，必漏**。
3. **无权限点概念**：`@PreAuthorize` 全项目零使用，权限无法审计、前端无法动态渲染按钮可见性（目前是各页面手工判 `isTester` 之类）。
4. **企业 7 级角色过细**：DIRECTOR 与 MANAGER 权限完全相同、LEADER 无任何差异化权限——角色多但权限没多，只增加理解成本。

---

## 3. 目标模型：三层判定

```
请求: user U 想对资源 R 执行操作 P（如 bug:edit bug#123）

PermissionService.check(U, P, R)
  │
  ├─ 第 1 层 RBAC：U 的角色（企业级 + 项目级）→ 权限点集合是否含 P？
  │     企业角色 → 企业级权限点（member:invite, enterprise:manage ...）
  │     项目角色 → 项目级权限点（bug:create, sprint:manage ...）
  │
  ├─ 第 2 层 资源关系：U 与 R 是否存在授权关系？
  │     bug.assigneeId / bug.reporterId → bug:status:change
  │     project_guest 记录            → guest 预设权限集（只读 + 提交）
  │
  └─ 第 3 层 硬规则（不可被角色绕过）：
        同企业数据隔离 / 等级压制 canOperate / 游客权限白名单
```

**核心原则：业务代码只问 `check(user, "bug:create", projectId)`，永远不再手写角色名比较。**

---

## 4. 数据模型

### 4.1 权限点注册 + 角色-权限映射（方案 A，推荐）

```sql
-- 权限点注册表（全局固定，代码升级时 INSERT IGNORE 增量补种）
CREATE TABLE sys_permission (
    code        VARCHAR(64) PRIMARY KEY COMMENT '如 bug:create',
    name        VARCHAR(100) NOT NULL COMMENT '中文名',
    scope       VARCHAR(20) NOT NULL COMMENT 'ENTERPRISE / PROJECT',
    description VARCHAR(255) DEFAULT NULL,
    sort        INT DEFAULT 0
) ENGINE=InnoDB COMMENT='权限点注册表';

-- 角色-权限映射（scope 决定角色枚举取值于哪套角色）
CREATE TABLE sys_role_permission (
    id            VARCHAR(36) PRIMARY KEY,
    scope         VARCHAR(20) NOT NULL COMMENT 'ENTERPRISE / PROJECT',
    role          VARCHAR(20) NOT NULL COMMENT '企业角色或项目角色枚举',
    permission_code VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_role_perm (scope, role, permission_code)
) ENGINE=InnoDB COMMENT='角色-权限点映射';
```

- **角色仍是现有枚举字段**（`user.role`、`project_member.role`），不迁移。
- 种子数据 = 把"现在每个角色实际拥有的权限"固化成映射行 → **上线即兼容，行为零变化**。
- 想调整某角色权限 = 改映射表行，代码不动。
- 结构向后兼容方案 B（教科书五表 RBAC）：将来若要"企业自定义角色"，加 `sys_role` 表 + `user_role` 关联即可，权限点层完全复用。

### 4.2 资源关系（第 2 层）

不建新表，**复用资源上的关系字段**，由 PermissionService 统一解释：

| 资源 | 关系 | 获得的权限点 |
|------|------|-------------|
| bug | assignee / reporter | `bug:status:change` |
| project | project_member 行 | 该项目角色映射的权限点（仅限本项目） |
| project | project_guest 行 | 游客预设权限集（见 §6） |

### 4.3 游客协作者（§6 详述）

```sql
CREATE TABLE project_guest (
    id           VARCHAR(36) PRIMARY KEY,
    project_id   VARCHAR(36) NOT NULL,
    token_hash   VARCHAR(128) NOT NULL COMMENT '游客链接 token 哈希',
    nickname     VARCHAR(50) DEFAULT NULL COMMENT '游客自称',
    email        VARCHAR(100) DEFAULT NULL COMMENT '可选，用于通知',
    expires_at   DATETIME DEFAULT NULL COMMENT '过期时间，空=长期',
    revoked      TINYINT DEFAULT 0 COMMENT '撤销',
    created_by   VARCHAR(36) NOT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_token (token_hash),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='项目游客协作者';
```

---

## 5. 权限点清单（初版种子）

**企业级（scope=ENTERPRISE，挂在 7 级角色上）**

| 权限点 | 说明 | 建议映射 |
|--------|------|---------|
| enterprise:manage | 企业设置/邀请码/解散 | SUPER_ADMIN |
| member:invite | 邀请成员 | MANAGER 及以上 |
| member:remove / member:role | 移除/改角色 | MANAGER 及以上（受等级压制约束） |
| member:department | 调整部门 | SUPERVISOR 及以上 |
| audit:read | 查看操作日志 | ADMIN 及以上 |

**项目级（scope=PROJECT，挂在项目角色上）**

| 权限点 | OWNER | MANAGER | TESTER | MEMBER | GUEST |
|--------|:---:|:---:|:---:|:---:|:---:|
| project:manage（设置/归档/删） | ✅ | — | — | — | — |
| member:manage（加人/角色/移除） | ✅ | ✅ | — | — | — |
| bug:create / bug:edit / bug:delete | — | — | ✅ | — | — |
| bug:submit（游客提交） | — | — | — | — | ✅ |
| bug:comment | ✅ | ✅ | ✅ | ✅ | ✅(仅自己条目) |
| task:sprint:manage | ✅ | ✅ | — | — | — |
| requirement:submit | ✅ | ✅ | ✅ | ✅ | — |
| project:view（基础信息/需求只读） | ✅ | ✅ | ✅ | ✅ | ✅(脱敏) |

> 说明：OWNER/MANAGER 是否豁免"仅测试人员可建 Bug"的硬限制，是产品决策——见 §8 决策点 3。上表按当前严格实现画。

---

## 6. 游客分享项目（需求：不在企业内也能提 Bug/建议）

### 6.1 定位

游客 = **项目级关系，不进企业、不建 sys_user 账号**。和现有"项目邀请码（同企业）"并列，新增"游客链接"。

### 6.2 流程

1. 项目 OWNER/MANAGER 在成员管理弹窗生成**游客链接**（复用现有邀请链接 UI 模式）：`/projects/guest/{token}`，可设过期时间、可撤销。
2. 游客打开链接 → 落地页：只读项目概况 + 需求列表（脱敏）+ 两个提交入口（提 Bug / 提建议），填昵称即可提交（可选留邮箱）。
3. 游客提交的 Bug：`reporter_type='GUEST'`，`reporter_id` 存 project_guest.id，另存 `guest_nickname` 冗余展示；站内流程照常指派 TESTER 修复。
4. **建议（feedback）单独建表** `project_feedback`（type: SUGGESTION/IMPROVEMENT/COMPLIMENT...，status: OPEN/ACCEPTED/REJECTED/CONVERTED）：站内人可把建议**转化**为 Bug 或任务（转化后 status=CONVERTED 并记目标 id），避免把非缺陷内容混进 Bug 队列。

### 6.3 安全约束（硬规则层）

- 游客权限=白名单预设（project:view 脱敏 + bug:submit + feedback:submit），**永不**获得成员列表/任务看板/文档。
- token 服务端存哈希、可撤销、可过期；提交接口按 token+IP 限流（如 10 次/小时）。
- 游客可见字段白名单：项目名称/描述/需求标题与状态；不看任务、预算、成员。
- 游客提交的内容默认进"待确认"队列，TESTER 可一键确认转为正式 Bug（防垃圾直达看板）。

### 6.4 Bug 表配套改动（第 3 期实施）

```sql
ALTER TABLE bug ADD COLUMN reporter_type VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT 'USER/GUEST';
ALTER TABLE bug ADD COLUMN guest_name VARCHAR(50) DEFAULT NULL COMMENT '游客昵称（reporter_type=GUEST 时）';
ALTER TABLE bug ADD COLUMN pending TINYINT DEFAULT 0 COMMENT '游客提交待确认';
```

---

## 7. 落地路线（三期，每期独立可上线）

**第 1 期：权限地基（纯重构，行为零变化）**
1. 建 `sys_permission` / `sys_role_permission` + 种子数据（按现状固化）。
2. 新建 `PermissionService.check(userId, code, resourceCtx)`；内部先实现角色映射 + assignee/reporter 关系 + 同企业/等级压制规则。
3. 把现有散落检查全部替换为 check() 调用；在 Controller 上加 `@PreAuthorize("@pms.has('bug:create')")` 风格注解（对接 PermissionService）。
4. 验收标准：现有全部权限测试（本轮的 10 项矩阵）行为不变。

**第 2 期：可视化 + 审计**
1. 企业设置页新增"角色权限"面板（读写 sys_role_permission，超管可调）。
2. 前端按钮可见性统一改为"问权限"：`/me/permissions?projectId=x` 返回权限点集合，替换手写 isTester 之类。
3. 顺手把 activity_log（表已建代码为零的遗留项）接上：权限判定拒绝事件也记审计。

**第 3 期：游客分享**
1. project_guest 表 + 游客链接生成/撤销 UI（复用邀请链接交互）。
2. 游客落地页（无需登录的路由组）+ 提交接口（限流）。
3. bug 加 reporter_type/guest 字段 + project_feedback 表 + "建议转 Bug"操作。

---

## 8. 需要拍板的决策点

1. **角色-权限映射的调整权限**：全局固定一套（超管改全局）还是每个企业自定义？→ 建议**先全局固定**，企业自定义放到有真实需求再做（多租户 SaaS 化才需要）。
2. **游客身份强度**：纯链接（昵称即提）还是必须留邮箱验证？→ 建议**轻量**：昵称必填+邮箱可选+限流；垃圾问题出现后再加验证。
3. **OWNER/MANAGER 豁免**：当前"仅测试人员可建 Bug"是严格实现，项目负责人自己也被拦。RBAC 映射表落地后这个问题自然消解（把 bug:create 同时映射给 OWNER/MANAGER 即可）——**建议放开给 OWNER/MANAGER**，符合直觉。
4. **企业 7 级角色是否收敛**：DIRECTOR/MANAGER 权限相同、LEADER 无差异权限。→ 建议**收敛为 4 级**（超管/管理员/经理/成员）+ 项目级角色承载差异；7 级映射表会有大量重复行。收敛可后置，不阻塞 1 期。
