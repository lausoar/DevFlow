# 第 9 批：数据完整性（schema 真实生效 + 并发安全）

> 前八批一直在"加功能"。这一批做的是相反的事：**把已有功能底下那些
> "看起来对、实际不对"的地方修掉**，并且修完以后证明它真的对。

## 一、最重要的一条：schema.sql 里有一半语句在应用里从未执行

### 现象

审计报告 §D3 点名了 7 个缺失索引。第 2 批加了一段"幂等建索引"逻辑：

```sql
DROP PROCEDURE IF EXISTS devflow_add_index_if_absent;
DELIMITER $$
CREATE PROCEDURE devflow_add_index_if_absent(...)
BEGIN ... END$$
DELIMITER ;
CALL devflow_add_index_if_absent('task', 'idx_task_fix_version', 'fix_version_id');
...
```

集成验证每次都是绿的（"7 个高频索引全部存在"）。但实际启动服务后查库：

```
SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema='devflow' AND index_name LIKE '%fix_version%';
→ 0
```

### 根因

`spring.sql.init` 用的是 Spring 的 `ScriptUtils`，它**只按 `;` 切分语句，不认识
`DELIMITER`**。于是：

1. `DELIMITER $$` 本身是一条非法 SQL → 失败；
2. `CREATE PROCEDURE ... BEGIN ... END$$` 被 `;` 切成碎片，每片都是语法错误；
3. `DELIMITER ;` 同样失败；
4. 后面 7 条 `CALL` 全部报 `PROCEDURE does not exist`。

而 `spring.sql.init.continue-on-error: true` 把这些错误全部吞掉，服务照常启动，
日志里连一行 WARN 都没有。

**验证脚本为什么是绿的**：它用 `mysql` 命令行客户端执行同一个文件，而 CLI 认识
`DELIMITER`。同一个文件，两种执行方式，结果完全不同 —— 而只有 CLI 那条路被验证过。

这是这一批里唯一一个"验证本身在骗人"的缺陷，比缺陷本身严重。

### 修复

改成不依赖 `DELIMITER` 的写法。`SET @ddl / PREPARE / EXECUTE / DEALLOCATE`
是四条独立语句，按 `;` 切分正好每句一条，两种执行方式结果一致：

```sql
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `task` ADD KEY `idx_task_fix_version` (fix_version_id)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_fix_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;
```

顺带把审计 §D3 点名的另外 8 处索引缺口也一起补了（`chat_channel.project_id`、
`bug.assignee_id`、`notification(user_id,is_read)`、`activity_log(enterprise_id,created_at)`、
`file_item.url`、`chat_message(channel_id,created_at)`、`document.parent_id`、
`department_member.enterprise_id`），共 14 个。

### 防回归：两道

**静态检查**（脚本里）：schema.sql 里只要还出现 `DELIMITER` 或 `CREATE PROCEDURE` 就失败。

**启动自检**（新增 `SchemaSelfCheck`）：服务启动后主动核对这 14 个索引和 5 个全文索引，
缺了就打 ERROR 日志明确列出来。原则是"宁可启动后立刻报警，也不要带着缺失的索引跑几个月"。

**动态验证**（脚本里，最关键的一条）：

```
1. 用 mysql CLI 建好索引 → 断言 14 个都在（证明 CLI 路径）
2. 把这 14 个索引全部 DROP 掉 → 断言 0 个（清场）
3. 启动后端，由 spring.sql.init 执行 schema.sql
4. 断言 14 个索引全部回来了（证明**应用路径**）
```

第 4 步是历史上从未被验证过的那条路径。

## 二、重启即改写业务数据

### 现象

`schema.sql` 里有几条裸 `UPDATE`，每次启动都执行。最严重的一条：

```sql
UPDATE requirement SET status = 'OPEN'
 WHERE status IS NULL OR status NOT IN ('OPEN', 'COMPLETED', 'CLOSED');
```

在干净库上复现（两次执行 = 两次重启）：

```
BEFORE               AFTER-ONE-RESTART
r1  OPEN             r1  OPEN
r2  COMPLETED   →    r2  OPEN     ← 已完成的需求被重开
r3  CLOSED      →    r3  OPEN     ← 已关闭的需求被重开
```

用户把需求做完、关闭，重启一次服务全部回到"待处理"。而这条 UPDATE 本意是
2026-10-26 的一次性历史数据归一。

同样的问题还有 5 处：角色迁移（7 级→4 级）、任务状态归一（`IN_PROGRESS`→`DOING`）、
通知地址修复、webhook 密钥回填、文档企业归属回填。

任务状态那条更危险：**项目可以自定义工作流状态码**。如果某个项目真的建了一个叫
`IN_PROGRESS` 的状态，每次重启都把它改写成 `DOING` 就是在破坏用户配置。

### 修复

新增 `schema_migration` 表，需要"只跑一次"的迁移一律写成：

```sql
UPDATE ... WHERE ... AND NOT EXISTS (SELECT 1 FROM schema_migration WHERE name = '<id>');
INSERT IGNORE INTO schema_migration (name) VALUES ('<id>');
```

顺序不能反——先查后用，否则标记先落库，迁移永远不执行。

验证脚本里加了对应的回归：插入一条 `COMPLETED`、一条 `CLOSED` 需求和一条
`IN_PROGRESS` 任务，再执行一遍 schema.sql（模拟重启），断言三条状态都没变。

## 三、task_worklog 被定义了两次

`schema.sql` 里 `task_worklog` 出现两次（第 577 行和第 610 行），
`CREATE TABLE IF NOT EXISTS` 是"先建者生效"，所以实际生效的是 `DECIMAL(5,1)`，
而实体和接口约定的都是 `DECIMAL(5,2)`。

后果：后端允许 0.1~24 小时的小数工时，`0.25` 落库变成 `0.3`、`1.75` 变成 `1.8`。
累计工时统计全部有偏差。

已验证（新建表 + 插入 0.25）：

```
hours 列类型：decimal(5,1)
插入 0.25 → 查出来 0.3
```

修复：删掉第一处定义，保留 `DECIMAL(5,2)` 那份，并加 `ALTER ... MODIFY`
把老库纠正过来（MODIFY 幂等）。`note` 也从 200 字扩到 500。

验证方式断言列类型而不是"看起来对"：

```
HOURS_TYPE = "decimal(5,2)"  → ✅
```

## 四、并发编辑：文档与任务

### 文档保存的读-改-写竞态

原来是：读 `prev` → 插快照 → `setVersion(prev+1)` → `updateById`，**全程不看客户端版本号**。

两个后果：

- 两人同时保存，后写覆盖前写，前一个人的正文永久丢失；
- 如果两次插入撞上 `uk_doc_ver` 唯一键，接口直接 500 —— 用户看到"保存失败"，
  却不知道自己的内容已经被别人覆盖了。

修复分两层：

```java
// 第一层：显式校验
if (request.getExpectedVersion() != null && request.getExpectedVersion() != prevVersion) {
    throw new BusinessException(409, "文档已被他人修改（当前版本 v" + prevVersion + "）…");
}
// 第二层：条件更新（两次请求可能交错，上面查过也可能过期）
int updated = documentMapper.update(patch, new LambdaUpdateWrapper<Document>()
        .eq(Document::getId, id).eq(Document::getVersion, prevVersion));
if (updated == 0) throw new BusinessException(409, "文档已被他人修改，请刷新后再保存");
```

### 任务与缺陷的乐观锁

`Task` / `Bug` 加了 `version` 列和 `@Version` 注解，
`MyBatisPlusConfig` 注册了 `OptimisticLockerInnerInterceptor`。

一个设计细节：**`expectedVersion` 是可选参数**。不传时保持旧行为（不加版本条件），
传了才校验。这样前端可以逐个页面接入，不用一次性改完所有调用点；
而且 `updateById` 拿到的 patch 对象上 `version` 为 null 时插件会自动跳过，
不会把所有现有写入路径一次性打崩。

## 五、其他修复

| 缺陷 | 问题 | 修复 |
|---|---|---|
| 燃尽图 500 | `dataPoints` 为空时 `get(size-1)` 抛 `IndexOutOfBoundsException`。迭代开始日期在未来时必然为空——"刚建好还没开始的迭代，一打开燃尽图就崩" | 空数据处理成正常状态，`velocity=0` |
| 燃尽图用错时间列 | 拿 `updated_at` 当完成时间，任务完成后改标题/换负责人就重画历史曲线 | 新增 `task.completed_at`，只在进出 DONE 分类时写入 |
| 多个 ACTIVE 迭代并存 | `activate` 不检查同项目是否已有 ACTIVE，而看板/燃尽图都只取第一个 | 激活自动完成旧的；`sprint` 的 complete/delete 补 `@Transactional` |
| 管理员删不了他人 Bug 评论 | `comment.getProjectId().equals(operator.getEnterpriseId())` —— 项目 ID 和企业 ID 是两个 ID 空间，这个分支恒为 false | 先由项目查企业再比 |
| 企业移除成员不清项目成员 | 只清 `sys_user.enterprise_id`，而自助退出会清 `project_member`。被移除的人仍是各项目成员，仍可读全部项目数据 | 级联删除本企业下所有项目的成员关系 |
| 删除任务留孤儿依赖 | 只删任务行，`task_dependency` 不动。已删的任务仍挂在别人的"前置"上，看板徽章永久显示"前置未完成" | 事务内清理双向依赖 |
| 邀请码竞态 | `getInviteCode` 是 check-then-write，两个并发请求各生成一个码，后写覆盖前写，先返回的那个立即失效 | 条件更新（`WHERE invite_code IS NULL`），没抢到就以库里已有为准 |
| `task_tag` 无唯一键 | 同一任务重复打同一标签会堆重复行（这张表目前无引用，顺手补上） | `UNIQUE (task_id, tag_name)` |
| `requirement.status` 注释错误 | 列注释写着早已废弃的 `DRAFT/PENDING/APPROVED`，真实取值是工作流定义的 `OPEN/COMPLETED/CLOSED` | `MODIFY COLUMN` 修正（幂等） |

## 六、验证

集成验证从 62 项扩到 **79 项**，新增 17 项：

```
✅ 14 个热点索引全部创建（mysql 客户端路径）
✅ schema.sql 不含 DELIMITER/CREATE PROCEDURE
✅ 重启不重置需求状态（COMPLETED/CLOSED 保持）
✅ 重启不重置自定义任务状态
✅ 一次性迁移已登记（6 条）
✅ 工时列精度 DECIMAL(5,2)
✅ 索引已清空（准备验证应用重建）
✅ 后端启动后 14 个索引全部重建（应用路径生效）
✅ 启动自检通过（未报告缺失对象）
✅ 文档按正确版本保存成功
✅ 并发保存被拒绝（409，不覆盖他人修改）
✅ 任务按正确版本保存成功
✅ 任务并发编辑被拒绝（409）
✅ 未来迭代的燃尽图返回 200（空数据不崩）
✅ 同项目内只有一个 ACTIVE 迭代
✅ 移除企业成员同时清理项目成员关系（1 → 0）
✅ 邀请码幂等（两次请求同一个码）
✅ 删除任务同时清理依赖关系（1 → 0）
```

后端单测 81 项全绿；前端 `tsc` 通过；lint 保持 16 个存量错误。

### 一个验证脚本自身的坑

第一版"删除索引"的检查写成按 `index_name` 去重计数。`idx_project_id` 这个名字
在 7 张表上都有（task/bug/document/sprint/requirement/file_item/chat_channel），
于是删掉 `chat_channel.idx_project_id` 之后计数仍然"看得见"它 —— 检查报
"仍有 2 个索引未删除"。改成按 `(表, 索引)` 成对计数才正确。

这类"通用索引名"在多表场景下会反复踩，值得记住。

## 七、本批未做

按审计报告的结论，以下问题仍然存在，但**本批没有动**，因为它们都不是
"改几行就能收口"的：

- **C1 全量乐观锁**：只接了 Task 和 Bug。Requirement/Project/Sprint 仍无版本列。
- **C11 Flyway 仍未接入**：`db/migration/` 依然只是摆设。第 9 批通过
  `schema_migration` 解决了"迁移重复执行"，但没有解决
  "DDL 没有版本号、无法回滚、多实例并发执行 DDL"。真正的解法是引入 Flyway。
- **C9 全库零外键**：关联完整性仍完全靠应用层。
- **F1/F2 统计全量内存聚合**：`StatisticsServiceImpl` 仍把企业所有任务加载进内存
  逐条过滤，全服务无一条 `GROUP BY`。万级任务即数十 MB 堆内存。
- **F4 分页缺失**：任务/Bug 列表默认 100 条静默截断；Sprint 任务列表无分页。
- **E1 核心域零单测**：TaskService / BugService / StatisticsService 仍裸奔。
  本批改的 `updateStatus` 完成时间逻辑**没有单测覆盖**，只有集成验证。
