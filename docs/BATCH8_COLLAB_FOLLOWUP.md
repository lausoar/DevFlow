# 第 8 批：协作跟进（订阅真正生效）

> 第 7 批把订阅表、`shouldNotify`、关注 API 都做完了，但**订阅没有接进通知分发**——
> 用户点了"关注"，任务再变也没人收到通知。这一批把这根线接上，并补了按钮。

## 一、问题：写完了但没有生效

第 7 批的实现是"接口齐全、界面能点、数据库有行"，唯独缺最后一环：

```
用户关注任务 → subscription 表有行 → ??? → 没有任何通知
```

三个具体断点：

1. `TaskServiceImpl.updateStatus` 的收件人只取"报告人 + 被指派人"，从不查订阅；
2. `BugServiceImpl.changeStatus` 同理；
3. 文档（第 7 批新增的越来越多全局文档）完全没有通知路径。

而且更微妙的是：`updateStatus` 调用时**根本不知道操作者是谁**，所以连"别给自己发通知"
都做不到——admin 自己改状态，admin 也会收到一条"任务状态已更新"。

## 二、收件人收敛到一处

新增 `NotificationRecipients` / `NotificationRecipientsImpl`：

```java
List<String> resolve(String targetType, String targetId, String operatorId, List<String> explicit)
```

规则是**并集 → 去重 → 排除操作者**：

1. 显式传入的直接关系人（报告人、被指派人）——业务上必须收到，不受订阅影响；
2. 关注该工作项的人（含自动关注）；
3. 去掉操作者自己。

### 为什么"订阅查询失败"要 fail-closed

同一个系统里，通知偏好查询失败走的是 **fail-open**（查不到偏好就当用户想收），
这里订阅查询失败走 **fail-closed**（只用 explicit 列表）。方向相反是有意的：

| 失败场景 | fail-open 的后果 | fail-closed 的后果 | 选哪个 |
|---|---|---|---|
| 偏好查询失败 | 多发几条通知 | 用户漏掉通知 | fail-open（漏更糟） |
| 订阅查询失败 | 发给不该看到内容的人 | 少发给关注者 | fail-closed（泄露更糟） |

这也顺带说明了为什么不能把两者合成一个"容错策略"配置项——它们的风险方向不一样。

## 三、三处接线

### 任务

`TaskService.updateStatus` 签名增加 `operatorId`。这个改动值得说明：

之前 `updateStatus(id, status)` 两参数，Controller 里已经知道操作者是谁，
但服务层拿不到，所以没法排除操作者。加参数而不是"从 SecurityContext 里取"——
服务层依赖 `SecurityContextHolder` 会让单测必须伪造线程上下文，且把不可见的耦合藏起来。
显式传参更直白。

```java
List<String> notifyUsers = notificationRecipients.resolve(
        "TASK", task.getId(), operatorId, explicit);
```

### 缺陷

`BugServiceImpl.changeStatus` 的 `notifyUserId` 原本是单个字符串，
现在过一遍 `resolveOne`，把该缺陷的关注者合并进来。

### 文档

`DocumentController` 的评论路径现在通知 **文档作者 ∪ 关注者**，跳转 `/docs`。
文档的关注支持有特殊之处，见下节。

## 四、全局文档的关注判定

文档与其他工作项不同：**它可以不属于任何项目**（全局文档，`projectId` 为空）。
项目可见性校验对它不成立，所以 `SubscriptionController` 用 `"GLOBAL"` 作为哨兵：

```java
case "DOCUMENT" -> documentProject(targetId);  // 空 projectId 时返回 "GLOBAL"
```

判定分两条路：

- 项目文档 → 走 `projectAccessService.canView`（与工作项一致）；
- 全局文档 → 走**企业归属一致**（与文档本身的可见性口径一致）。

用哨兵而不是 `null` 是刻意的：`null` 同时表示"对象不存在"和"全局文档"，
会让"文档被删了"和"全局文档"混淆，最终表现为删除的文档还能关注。

## 五、前端关注按钮

新增 `SubscribeButton`（`components/common/subscribe-button.tsx`），
配 `useSubscriptionStatus` / `useToggleSubscription` 两个 hook。

放置位置：任务编辑弹窗标题栏、缺陷详情弹窗标题栏。

交互上做了一个小决定：**按钮不显示"关注"这个动作处于 pending 状态**。
乐观更新就够了——关注是低风险、可逆、立即生效的操作，转圈反而让每次点击都显得卡。

## 六、验证

集成验证从 59 项扩到 **62 项**，新增：

```
✅ 关注者收到任务状态变更通知（0 → 1）
✅ 操作者不会收到自己触发的通知
✅ 关注全局文档（同企业）
```

第二条是这一批真正的核心断言：它不仅检查"通知发了"，还检查"不该发的没发"。
第一条的构造方式是往 `subscription` 表直接写一行 `zhangsan` 对某任务的关注，
再由 admin 改状态——这样验证的是**分发链路**，而不是关注接口（关注接口第 7 批已经覆盖）。

后端单测 81 项全绿；前端 `tsc` 通过；lint 错误保持 16 个存量，新文件 0 错误。

## 七、本批未做

- **关注列表页面**：`GET /subscriptions` 能拿到列表，但没有独立页面展示"我关注的所有工作项"。
  目前只有单条工作项上的按钮。这是"能点但看不到全貌"的缺口。
- **订阅接入需求流转**：`REQUIREMENT` 类型能关注，但需求状态变更路径还没有接线。
- **批量关注**：不能一次关注整个迭代或某个筛选条件。
- **通知聚合**：一条任务连续改 5 次状态会产生 5 条通知。
  真实产品应该做时间窗口聚合（"3 个任务有更新"）。
