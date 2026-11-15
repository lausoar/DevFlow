# 第 10 批：Flyway 接管迁移 + 统计聚合下推

> 第 9 批把 schema.sql 里"在应用里从未执行"的语句修好了，但**根本问题没动**：
> schema.sql 仍然是"每次启动重跑、失败靠 continue-on-error 吞掉、没有版本号"。
> 这一批把结构迁移交给 Flyway，并把企业统计从"全量加载进内存再过滤"改成 SQL 聚合。

## 一、Flyway 真正接入（审计 §C11）

### 之前的状况

`db/migration/` 目录里躺着 V1..V7 七个文件，`V1__baseline.sql` 的内容是 `SELECT 1;`，
pom 里没有 Flyway 依赖。也就是说：**这些迁移文件一秒都没被执行过**，
真正建表的一直是 `spring.sql.init` 执行的 `schema.sql`。

### 新的结构

| 文件 | 内容 | 对新库 | 对老库 |
|---|---|---|---|
| `V1__baseline.sql` | 63 张表的完整定义（`mysqldump --no-data` 导出，全部 `CREATE TABLE IF NOT EXISTS`） | 一次建全 | 空操作 |
| `V2__seed_data.sql` | 种子账号 + 权限矩阵（幂等语句） | 建 2 个账号 | 空操作（`INSERT IGNORE`） |
| `V3__legacy_structure_repair.sql` | 历史 schema.sql 里所有 `ADD COLUMN` / `ADD KEY` 的条件重放 | 空操作 | **缺什么补什么** |
| `V4__data_migrations.sql` | 6 条一次性历史数据迁移 | 跑一次 | 靠 `schema_migration` 标记跳过已跑过的 |

V1 的基线是**从"执行完第 9 批 schema.sql 的库"导出**的，所以它与历史结构逐列等价——
不是重写，是固化。这样"新库"和"老库"最终状态一致，不会因为路径不同产生两套 schema。

### 三个必须解释的配置

```yaml
flyway:
  baseline-on-migrate: true
  baseline-version: 0
  validate-on-migrate: true
```

- **`baseline-on-migrate`**：老客户的库里表都在、但没有 `flyway_schema_history`。
  不加这个，Flyway 会直接拒绝启动（"found non-empty schema without schema history table"）。
- **`baseline-version: 0`**：默认值是 1，那样 V1 在老库上会被跳过。设成 0 让 V1
  在所有库上都执行 —— V1 全是 `IF NOT EXISTS`，对老库是空操作，但**路径统一**了。
- **`validate-on-migrate`**：迁移文件被改动过就拒绝启动。防的是"本机改过一半、
  线上跑的是另一版"。

### `spring.sql.init` 关掉了

```yaml
sql:
  init:
    mode: never
```

不关的话两条路径会同时改库。`schema.sql` 保留，但文件头写清楚它是**人类可读的结构总览**，
不再是执行入口 —— 900 行 mysqldump 输出不适合查阅，1490 行手写 DDL 适合。

### 踩到的两个坑

**坑一：`DELIMITER` 的第二次教训。** 第 9 批已经知道 Spring 的 `ScriptUtils` 不认 `DELIMITER`，
但**Flyway 的解析器也不认**（同样的原因：按 `;` 切分）。所以 V3/V4 里全部用
`SET @ddl / PREPARE / EXECUTE / DEALLOCATE`。验证脚本里加了一条静态检查：
迁移目录下出现 `DELIMITER` 或 `CREATE PROCEDURE` 直接判失败。

**坑二：把带 `COMMENT '...'` 的 DDL 塞进单引号字符串。** 生成 V3 时直接拼的：

```sql
'SET @ddl := ... ''ALTER TABLE project ADD COLUMN invite_code VARCHAR(16) DEFAULT NULL COMMENT '项目邀请码''' ...'
```

字符串被第一个内部单引号截断了。必须把内部单引号双写成 `''`：

```sql
'ALTER TABLE `project` ADD COLUMN invite_code ... COMMENT ''项目邀请码'' AFTER owner_id'
```

**坑三：迁移顺序。** 最初 V3 是数据迁移、V4 是结构补齐，结果老库上一启动就报
`Unknown column 't.completed_at' in 'where clause'` —— 数据迁移引用了结构迁移才补出来的列。
**结构永远先于数据**，两者对调。

## 二、统计聚合下推 SQL（审计 §F1/§F2）

### 之前

`StatisticsServiceImpl.getEnterpriseStats` 把企业下**全部**任务/Bug/需求 `selectList` 进内存：

```java
List<Task> allTasks = taskMapper.selectList(...);
List<Bug> allBugs = bugMapper.selectList(...);
List<Requirement> allRequirements = requirementMapper.selectList(...);
// 然后用几十个 allTasks.stream().filter(...) 逐条计数
```

全文件无一条 `GROUP BY`。万级任务是数十 MB 堆内存 + 秒级 GC，而前端 `staleTime`
只有 5 分钟，反复触发全量重算。另外"每个项目查一次 `project_member`"和
"每个项目查一次成员列表"是两处典型 N+1。

### 现在

新增 `StatisticsAggregateMapper`，所有计数下推：

```sql
SELECT project_id, status, COUNT(*) AS n,
       SUM(CASE WHEN due_date < NOW() THEN 1 ELSE 0 END) AS overdue
FROM task WHERE deleted = 0 AND project_id IN (...) GROUP BY project_id, status
```

返回行数上限是「项目数 × 状态数」（几十行），与任务总量无关。

### 为什么按 (project, status) 分组，而不是 SUM(CASE WHEN status IN (doneCodes))

这是这一节里最值得记的取舍。第一版写的是后者：把所有项目的 DONE 状态码取并集，
塞进一条 SQL 的 `IN (...)`。但**每个项目可以有自己的工作流**：

- A 项目的完成态叫 `FINISHED`（属于 DONE 分类）；
- B 项目的 `FINISHED` 属于 **IN_PROGRESS** 分类（"已提交测试"）。

并集塞进一条 SQL 后，B 项目那 5 条 `FINISHED` 会被算成"已完成"。
错账只在多项目自定义工作流时出现，而且报表看起来一切正常。

所以改成按 `(project_id, status)` 返回原始计数，由 Java 侧用**各项目自己的**
`status → category` 映射归类。语义与逐条过滤完全一致，而不是"看起来差不多"。

单测 `StatisticsServiceImplTest.sameStatusNameDifferentCategoryIsNotConfused` 就是这个场景：
A 项目 `FINISHED`×3 属于 DONE、B 项目 `FINISHED`×5 属于 IN_PROGRESS，
断言 `completedTasks == 3` 且 `inProgressTasks == 5`。

### 顺带对齐的两处口径

- **任务趋势图的"完成"** 原来也用 `updated_at`，现已改为 `completed_at`；
- **缺陷"已解决"** 统一为 `DONE 分类 ∪ {RESOLVED, CLOSED}`。默认工作流里 `RESOLVED`
  属于 IN_PROGRESS（还要回归），只看分类会漏掉。

## 三、补单元测试

审计 §E1 说"六大核心域零单测"。这一批补了两个域（**89 项**，原 81 项）：

`TaskServiceImplTest`（8 项）：
```
✅ 进入 DONE 分类时写入 completed_at
✅ 离开 DONE 分类时清空 completed_at
✅ 重复进入 DONE 不刷新已有完成时间
✅ 非 DONE 状态不写 completed_at
✅ 工作流查不到分类时不抛异常（回落为字面 DONE 判断）
✅ 乐观锁：过期版本号被拒绝，不写库
✅ 乐观锁：不带版本号时保持旧行为
✅ 删除任务时清理双向依赖
```

`StatisticsServiceImplTest`（4 项）：
```
✅ 没有项目时返回空结构，不抛异常
✅ 多项目自定义工作流：同名状态按各自分类归类，不串味
✅ 燃尽图：迭代尚未开始时返回 200 而不是抛异常
✅ 燃尽图：按 completed_at 归日，而不是 updated_at
```

这些正是"编译通过、类型正确、但行为错误"的那类缺陷 —— 集成验证能发现，
但不该等到集成阶段才发现。

## 四、验证

集成验证从 79 项扩到 **85 项，全绿**。验证方式本身也改写了：从"用 mysql CLI 跑
schema.sql 再断言"改成"**让应用自己跑迁移**再断言迁移结果"——后者才是生产路径。

新增/改写的关键项：

```
✅ 全新库启动成功（Flyway 迁移完成）
✅ Flyway 迁移历史完整（版本 1,2,3,4）
✅ 无失败的迁移
✅ 全部业务表已创建（含 23 张关键表）
✅ 14 个热点索引全部创建（应用路径生效）
✅ 启动自检通过（未报告缺失对象）
✅ spring.sql.init 已关闭（结构只由 Flyway 管）
✅ 迁移文件不含 DELIMITER/CREATE PROCEDURE
✅ 重启未重复执行迁移（历史记录数不变）
✅ 重启不重置需求状态（COMPLETED/CLOSED 保持）
✅ 重启不重置自定义任务状态
✅ 老库（无迁移历史）能被 Flyway 接管并启动
✅ 老库迁移历史正确（含 baseline 记录：0,1,2,3,4）
✅ 老库缺失的列被 V3 补齐（task.completed_at）
✅ 老库缺失的索引被 V3 补齐（14 个）
```

**"老库"是怎么造的**：用 `V1__baseline.sql` 建库（等价于历史 schema.sql 的结果），
再 `DROP` 掉后加的列与索引（`task.completed_at` / `task.version` / `idx_task_fix_version`），
然后启动应用。这是升级路径的真实形态，也是最容易出问题的地方 ——
我第一次跑就撞上了"数据迁移引用结构迁移才补的列"。

后端单测 89 项全绿；前端 `tsc` 通过；lint 保持 16 个存量错误。

## 五、本批未做

- **V1 用 IF NOT EXISTS 而不是纯 CREATE**：好处是同一文件对新旧库都安全，
  代价是"结构漂移"不会报错（如果老库某张表少一列，V1 不会管，得靠 V3 兜）。
  更彻底的做法是把老库结构差异逐条写进 V3 —— 现在 V3 已覆盖 schema.sql 里
  所有 ADD COLUMN/ADD KEY，但**改表定义的可能性**（例如某列类型被改过）检测不到。
- **统计只下推了计数，没有下推时间窗口的复杂聚合**：DORA、燃尽图仍在内存算。
- **`getProjectHealth` 仍全量加载**：它需要做"每 Sprint 的速率"这种分组，
  下推需要新的 SQL 形状，本批没做。
- **E1 剩余部分**：BugService / RequirementService / EnterpriseService 仍无单测。
