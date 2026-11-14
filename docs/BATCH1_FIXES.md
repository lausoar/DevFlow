# 第 1 批：修复断链功能

> 对应 [AUDIT_REPORT_2026-11.md](./AUDIT_REPORT_2026-11.md) §B「有代码、有入口，但链路断开」的 12 项。
> 这一批的共同特点是：功能看似存在、UI 也点了，但数据没有真正流转，用户侧完全无感知。

## 1. Bug 自动指派从不落库

**症状**：配置了"新建 Bug 自动指派"，接口响应显示 `status=ASSIGNED` 且有负责人，但刷新后仍是 `NEW`、负责人为空。

**原因**：`AutomationRuleServiceImpl.autoAssignBug` 只改内存对象，留了一行 `BugMapper bugMapper = null;` 占位并注释"由调用方更新"，但调用方 `BugServiceImpl` 丢弃返回值、从不回写。

**修复**：改为条件更新落库（`WHERE id = ? AND assignee_id IS NULL`），返回受影响行数为 0 时说明已被别人抢先指派，放弃并返回 null；成功后同步通知被指派人。

## 2. BUG_FAIL 触发器永不触发

**症状**：自动化规则下拉里有"Bug 标记失败时"，选了保存也没用。

**原因**：全仓只有 3 处 `fire()`（TASK_DONE / CASE_FAIL / BUG_CREATED），Bug 状态变更为任何值都不触发。

**修复**：Bug 状态流转到 `REOPENED`（修复失败 / 回归）时触发 `BUG_FAIL`，语义与"标记失败"一致。

## 3. 同时配"指派 + 通知"的规则两者都不发生

**原因**：`fire` 里遇到 `BUG_CREATED + assignToUserId` 直接 `handled++; continue`，把通知分支跳过了。叠加第 1 项，最终是"不指派、不通知、只返回 handled=1"。

**修复**：去掉 `continue`，指派与通知各自独立计数；指派由 `autoAssignBug` 负责落库。

## 4. 迭代与任务彻底脱节

**症状**：迭代进度条恒为 0、燃尽图 `dataPoints` 为空——因为前端**没有任何地方能写 `sprintId`**。

**修复**：
- 新建任务弹窗新增「所属迭代」「负责人」两个下拉，打开时默认选中活跃迭代（连续录入同批次任务时保留上次选择）。
- 编辑任务弹窗新增「负责人」「所属迭代」下拉。
- 后端 `TaskServiceImpl.update` 支持空串表示"清除"（`updateById` 默认跳过 null 字段，无法表达置空）。

## 5. 看板徽章永不渲染

**原因**：`TasksTab` 把 `extras` 传给 `KanbanColumn` 了，但 `KanbanColumn` 渲染 `TaskCard` 时漏传，`if (!extras) return null` 恒成立。

**修复**：补上传参；清单进度、⛔前置未完成、⏱工时三个徽章恢复显示。

## 6. 任务无法在界面指派负责人

**症状**：表单里有 `assigneeId` 字段也能回填，但没有控件，保存时也不提交。

**修复**：新建与编辑弹窗都加负责人下拉，看板卡片右下角显示负责人姓名；`TaskCard` 通过 `memberNames` 映射解析 `userId → 展示名`。

## 7. 聊天历史方向错误

**症状**：频道消息超过 50 条后，新消息永远看不见。

**原因**：`ORDER BY created_at ASC` + 前端固定 `page=1`，每次取的是频道**最早**的 50 条。

**修复**：
- SQL 改为 `ORDER BY created_at DESC, id DESC`，`page=1` 即最新一页，返回前在 Controller 翻回正序。
- 前端新增 `useMessageHistory(channelId, loadedPages)`：并行拉取 1..N 页、按 id 去重、按时间升序合并。
- 消息列表顶部加「加载更早的消息」；切换频道时重置翻页状态；只在新增消息时自动滚到底，翻历史不打断。

## 8. 报表页首屏恒为空

**原因**：`useState(projects[0]?.id || '')` 在首次渲染时 `projects` 还是空数组，得到空串；数据到达后没有补选，`enabled: !!projectId` 使查询永不发起。

**修复**：四个报表 tab（燃尽/甘特/工作量/健康度）改为派生值 `pickedProjectId || projects[0]?.id || ''`。用派生而不是 `useEffect` 里 `setState`，避免首屏空态与级联渲染（lint 的 `set-state-in-effect` 规则也不允许）。

## 9. 需求详情接口路径错误

**原因**：前端请求 `/requirements/{id}`，后端只有 `/projects/{projectId}/requirements/{id}`，一旦启用就 404。

**修复**：`requirementApi.getById(projectId, id)` 修正路径；详情弹窗在"列表当前页找不到该需求"时（翻页后打开、从别处跳转）回落到详情接口拉取，不再空白。

## 10. 任务评论作者显示为 UUID 片段

**原因**：后端 `listByTask` 未联查 `sys_user`，实体无 `username` 字段，前端 `comment.username || comment.userId.slice(0, 6)` 永远走 fallback。

**修复**：Controller 层填充 `username`（真实姓名优先）与 `avatar`；删除评论改为"评论作者本人，或同企业 ADMIN 以上"。

## 11. 文档「预览」不渲染 Markdown

**原因**：未引入任何 Markdown 渲染库，`<div className="prose">` 里直接放原始字符串（Tailwind v4 也没装 typography 插件，`prose` 本身不生效）。

**修复**：
- 新增 `components/common/markdown-view.tsx`：基于 `react-markdown` + `remark-gfm`，覆盖标题/列表/引用/代码块/表格/任务列表等排版。
- **不渲染原始 HTML**（react-markdown 默认行为），链接统一 `noopener/noreferrer`，图片限宽。
- 文档预览面板与版本预览都改用该组件（版本对比仍是全文预览，diff 属第 2 批）。

## 12. AI 日报是编造的

**原因**：`generate_daily_report` 的提示词只插入 `user_id` 与 `project_id` 字符串，不查询任何任务/Bug/提交/工时；前端还硬编码传 `'current-user'`。

**修复**：
- AI 服务用当前用户 JWT 调后端 `/tasks/my`、`/bugs/my` 取真实数据，拼进提示词，并明确要求"只依据数据、列表为空就说没有，不要虚构"。
- 项目范围过滤在拼提示词之前完成（否则日报会混入其它项目的任务）。
- 接口要求真实用户令牌（`require_user_token`），因为要透传用户身份查数据。
- 前端传真实 `user.id`，新增「统计范围」下拉（全部项目 / 指定项目），输出末尾附数据来源条数；后端查询失败时明确标注原因而不是编造。

## 附带修复

- **批量「取消指派」写入 `_none`**：UI 的 `_none` 占位符被当成 userId 落库，产生指向不存在用户的脏负责人。现在映射为空串（后端语义为取消指派）。
- **吞异常改为记日志**：`BugServiceImpl` 的自动指派 / 规则触发两处 `catch (Exception ignored)` 改为 `log.warn`，失败可排查。

## 新增测试

`AutomationRuleServiceImplTest`（6 例）覆盖：
- 自动指派必须调用 `bugMapper.update`（回归"只改内存"的 bug）
- 并发抢占（更新 0 行）时不覆盖、不通知
- 已指派 / 规则指向报告人时跳过
- 同时配指派与通知时通知照常发出（回归 `continue` 吞通知的 bug）
- 通知对象是事件创建人时不发

> 纯单测里 `LambdaUpdateWrapper` 需要 MyBatis-Plus 的实体内省缓存，测试用 `TableInfoHelper.initTableInfo` 初始化（Spring 环境下由 mapper 扫描自动完成）。

## 验证

- 后端：`mvn compile` 通过；单测 48 项全绿（含新增 6 项）。
- 前端：`tsc --noEmit` 通过；`npm run lint` 错误数从 19 降到 16（剩余均为本批之前的既有问题，集中在未改动文件）。
- **未做运行时验证**：本机没有可用的 MySQL/Redis 容器，未实际启动三个服务点通链路。
