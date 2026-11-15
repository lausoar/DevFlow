# 第 11 批：删除审计与并发闭环

> 前两批修的是"底层到底对不对"。这一批修的是**用户能直接感觉到的那一层**：
> 回收站显示的时间是假的、并发编辑仍然静默覆盖、列表超过 100 条悄悄消失。

## 一、回收站的"删除于"一直是假的

### 现象

回收站每条记录显示"删除于 <时间>"，但那个时间取的是 `updated_at` —— **最后一次修改时间**。

一条任务 10 月建、11 月删，中间改过三次标题，回收站显示的是最后一次改标题的时间。
一条任务建完立刻删，显示的是创建时间。

更实际的后果：出了"数据怎么没了"的事故，没有人能回答**是谁删的**。

### 修复

给三类工作项（task / bug / requirement）补 `deleted_at` 和 `deleted_by`：

```sql
UPDATE task SET deleted = 1, deleted_at = NOW(), deleted_by = #{operatorId}
 WHERE id = #{id} AND deleted = 0
```

关键点是**不能用 MyBatis-Plus 的 `deleteById`**：它只写 `deleted=1`，不写时间和人。
所以三个 mapper 各加了一个 `softDelete(id, operatorId)`，删除路径改成调它。

`WHERE deleted = 0` 这半个条件顺带解决了一个老问题：重复删除同一条记录时，
第二次会影响 0 行 —— 调用方据此能知道"已经不在了"，而不是假装成功。

### 连带修正

| 位置 | 之前 | 现在 |
|---|---|---|
| 回收站列表排序 | `ORDER BY updated_at DESC` | `ORDER BY deleted_at DESC` |
| 恢复记录 | 只置 `deleted = 0` | 同时清空 `deleted_at` / `deleted_by`（否则恢复后还带着"删除于"的痕迹） |
| 回收站索引 | 无（按 `updated_at` 排序走不上索引） | `(project_id, deleted, deleted_at)` 三列复合索引 |

历史数据用一次性迁移回填了 `deleted_at = updated_at` —— 改不了过去，但至少不是 NULL。

## 二、排查中发现验证脚本自己的两个假绿

**第一个：断言"回收站首条是 bin-new"。**

我为了验证排序，往库里插了两条删除时间相差 6 年的记录（`bin-old` / `bin-new`），
然后断言回收站第一条是 `bin-new`。跑出来是 ❌ —— 但**代码是对的**，
只是库里还有别的已删任务（前面测删除审计时删的那几条），它们的删除时间是"现在"，
自然排在 `bin-new` 前面。

改成比较 `bin-new` 与 `bin-old` 的**相对顺序**，而不是绝对位置。

**第二个：迁移版本断言写死 `1,2,3,4`。**

加了 V5 之后这条必然失败。这类"断言里写死会增长的清单"是维护负担：
每次加迁移都要记得改断言。已更新为 `1,2,3,4,5`，但值得记住这个模式的风险。

## 三、并发：最后一个缺口

第 9 批给 Task/Bug 加了 `version` 列和 `@Version`，但只做到了后端"支持"。
这一批补完剩下三段：

### 1. 前端从来不传 expectedVersion

后端校验了，前端不传 —— 等于没校验。现在任务/缺陷的编辑弹窗保存时都带上
打开弹窗那一刻的版本号：

```ts
expectedVersion: task.version,
```

### 2. 状态变更忽略影响行数

乐观锁插件会把 `version` 拼进 `WHERE`。并发时 `updateById` 返回 0 行，
而原实现**不看返回值** —— 表现是"点了没反应"，比报错难查得多。

```java
int statusRows = taskMapper.updateById(task);
if (statusRows == 0) {
    throw new BusinessException(409, "任务状态已被他人修改，请刷新后重试");
}
```

### 3. DuplicateKeyException 变成 500

文档并发保存时，两个请求都基于版本 N 插同号快照，第二个撞 `uk_doc_ver`。
`GlobalExceptionHandler` 没有对应 handler，落进兜底分支 → **500**。
状态码错直接导致前端提示错：用户看到"服务器错误"，其实该看到"已被他人修改"。

```java
@ExceptionHandler(DuplicateKeyException.class)
public ResponseEntity<R<Void>> handleDuplicateKey(DuplicateKeyException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(R.fail(409, "数据已被他人修改（唯一约束冲突），请刷新后重试"));
}
```

### 4. 前端把 409 说明白

保存失败时原来直接抛出（弹窗关不掉、也没提示）。现在捕获并显示服务端返回的
message —— "任务已被他人修改（当前版本 v3），请刷新后再保存"。

## 四、分页截断：从"静默"改成"明示"

后端列表默认 `pageSize=100`，前端从不翻页。超过 100 条的部分**不会显示，
也没有任何提示** —— 用户以为看板就是全部任务。

完整改造（虚拟滚动 / 加载更多）不在本批范围，但至少要让用户知道：

```
⚠ 当前只显示前 100 条任务，另有 47 条未加载（共 147 条）。
   请用筛选/视图缩小范围后再操作，避免误判任务总数。
```

`total` 后端一直在返回，前端一直没用。现在用上了。

## 五、验证

集成验证从 85 项扩到 **94 项，全绿**。新增 9 项：

```
✅ 任务软删除写入 deleted_at 与 deleted_by
✅ 删除的任务出现在回收站
✅ 恢复后清除删除审计字段（0|NULL）
✅ Bug 软删除写入删除审计
✅ 字段编辑的过期版本被拒绝（409）
✅ 状态变更成功 / 过期版本 409
✅ 唯一键冲突映射为 409（不是 500）
✅ 分页 total 与实际条数一致
✅ 回收站按删除时间倒序（bin-new 排在 bin-old 前）
```

单测 89 项全绿（`TaskServiceImplTest` 的 update/delete 断言随签名变化做了更新）。
前端 `tsc` 通过，lint 保持 16 个存量错误。

## 六、本批未做

- **列表仍是 100 条截断**：现在只是提示，不是解决。真正的方案是虚拟滚动或
  加载更多，涉及看板/列表两套 UI，工作量不小。
- **Requirement/Project/Sprint 仍无 version 列**：乐观锁只覆盖了 Task 和 Bug。
- **`deleted_by` 目前只写不读**：回收站没有展示"谁删的"。数据已经在了，
  加一列展示是几行的事，但没做。
- **删除审计只覆盖三类工作项**：文档、评论、文件、迭代仍是只写 `deleted=1`。
  它们目前没有回收站入口，补了也没消费方。
- **两个分页断言的教训没形成机制**：验证脚本里仍有写死的清单（迁移版本号、
  索引名单），加东西时容易忘。理想做法是从代码/目录动态推导。
