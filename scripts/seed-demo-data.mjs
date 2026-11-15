#!/usr/bin/env node
/**
 * DevFlow 全场景演示数据生成器
 * ================================================
 * 目标：造出一条**覆盖全部业务流程**的演示数据，让每个页面、每个标签页、
 * 每种状态都有真实内容，可直接用于演示 / 截图 / 手工回归。
 *
 * 覆盖范围（对照 README 的功能总览）：
 *   企业四级角色 · 部门树 · 审计链
 *   项目集 · 两个项目（一个进行中、一个归档）
 *   需求（含父子、字段变更记录）· 迭代（已激活）· 任务（四列看板 + 依赖 + 清单 + 工时 + 评论）
 *   Bug（六态 + 批量字段 + 评论）· 测试用例（评审 + 执行记录 + 失败转缺陷）
 *   测试计划 / 测试轮次 · 文档（版本历史 + 评论）· Wiki 树
 *   文件上传 · 聊天频道与消息 · 通知 · 订阅 · 自定义字段 · 保存的筛选器
 *   自动化规则 · 版本发布（含发布说明）· 环境与部署（审批链路）· 设计稿走查
 *   游客链接与外部提交 · Git Webhook 提交 · 通知偏好 · 双因素
 *
 * 用法（后端必须已启动）：
 *   node scripts/seed-demo-data.mjs                     # 默认 http://localhost:8080/api/v1
 *   BASE=http://localhost:8080/api/v1 node scripts/seed-demo-data.mjs
 *
 * 幂等策略：企业/用户按"已存在则复用"处理（邮箱 + 用户名唯一），
 * 业务对象每次运行都会新建，并最终打印一份清单（含登录账号与关键 ID）。
 */

const BASE = process.env.BASE || 'http://localhost:8080/api/v1';
const RUN = Date.now().toString(36).slice(-4).toUpperCase();

// ============================================
// HTTP 基建
// ============================================
let token = '';
const log = [];
const failed = [];

async function api(method, path, { body, auth = true, raw = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 二进制或纯文本 */ }
  if (res.status >= 400 || (json && json.code !== undefined && json.code !== 200)) {
    const msg = json ? `${json.code} ${json.message}` : `${res.status} ${text.slice(0, 120)}`;
    throw new Error(`${method} ${path} → ${msg}`);
  }
  return raw ? text : (json ? json.data : text);
}

/** 记一步：成功入 log，失败入 failed（不中断后续步骤，尽量把能造的造全） */
async function step(label, fn) {
  try {
    const value = await fn();
    log.push(label);
    process.stdout.write(`  ✓ ${label}\n`);
    return value;
  } catch (e) {
    failed.push(`${label} — ${e.message}`);
    process.stdout.write(`  ✗ ${label} — ${e.message}\n`);
    return null;
  }
}

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const daysAgo = (n) => new Date(Date.now() - n * 86400000).toISOString().slice(0, 10);
const daysAhead = (n) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);
const hoursFromNow = (n) => new Date(Date.now() + n * 3600000).toISOString().slice(0, 19);

// ============================================
// 主流程
// ============================================
(async () => {
  console.log(`\nDevFlow 全场景数据生成 — ${BASE}\n${'='.repeat(60)}`);

  // ---------------------------------------------------------------
  // 0. 账号：一个企业超管 + 四个职级
  // ---------------------------------------------------------------
  console.log('\n[0] 账号与企业');
  const owner = await loginOrRegister('demo_owner', '林知远', 'SUPER_ADMIN');
  await ensureEnterprise(owner, '星野科技');

  const admin = await inviteAndLogin(owner, 'demo_admin', '周慕青', 'ADMIN');
  const manager = await inviteAndLogin(owner, 'demo_manager', '陈砚', 'MANAGER');
  const member = await inviteAndLogin(owner, 'demo_member', '苏遥', 'MEMBER');
  const tester = await inviteAndLogin(owner, 'demo_tester', '何以宁', 'MEMBER');

  // 企业/部门
  token = owner.token;
  const enterprise = await api('GET', '/enterprises/current');
  const enterName = await api('GET', '/enterprises/current');
  const EID = enterprise.id;

  // 部门：同名已存在则复用（脚本可重复运行）
  const existingTree = await api('GET', `/departments/tree?enterpriseId=${EID}`).catch(() => []);
  const findDept = (name, parentId = null) => {
    const walk = (nodes) => {
      for (const n of nodes || []) {
        if (n.name === name && (n.parentId || null) === parentId) return n;
        const hit = walk(n.children);
        if (hit) return hit;
      }
      return null;
    };
    return walk(existingTree);
  };
  const ensureDept = (name, parentId = null) => async () =>
    findDept(name, parentId) || api('POST', `/departments?enterpriseId=${EID}`, { body: { name, parentId } });

  const deptRoot = await step('一级部门 产品研发中心', ensureDept('产品研发中心'));
  const deptBackend = await step('子部门 后端组', ensureDept('后端组', deptRoot && deptRoot.id));
  const deptFrontend = await step('子部门 前端组', ensureDept('前端组', deptRoot && deptRoot.id));

  if (deptBackend) {
    await step('把成员调入后端组', () =>
      api('PUT', `/enterprises/${EID}/members/${member.user.id}/department`, { body: { departmentId: deptBackend.id } }));
    await step('把测试调入前端组', () =>
      api('PUT', `/enterprises/${EID}/members/${tester.user.id}/department`, { body: { departmentId: deptFrontend.id } }));
  }

  // ---------------------------------------------------------------
  // 1. 项目集 + 两个项目
  // ---------------------------------------------------------------
  console.log('\n[1] 项目集与项目');
  const program = await step('创建项目集 云原生平台', () =>
    api('POST', `/enterprises/${EID}/programs`, { body: { name: '云原生平台', description: '公司级产品线：统一研发底座' } }));

  token = owner.token;
  const project = await step('创建主项目 星野研发平台', () =>
    api('POST', '/projects', {
      body: {
        name: '星野研发平台',
        projectKey: `XY${RUN}`,
        description: '面向研发团队的一体化协作平台，覆盖需求到上线全流程。',
        gitRepoUrl: 'https://github.com/lausoar/DevFlow',
      },
    }));
  if (!project) { console.error('\n无法创建项目，终止。'); process.exit(1); }
  const PID = project.id;

  const archivedProject = await step('创建演示项目 归档示例', () =>
    api('POST', '/projects', {
      body: { name: '归档示例项目', projectKey: `AR${RUN}`, description: '用于展示归档态的项目' },
    }));

  if (program) {
    await step('把主项目挂到项目集', () =>
      api('PUT', `/projects/${PID}/program`, { body: { programId: program.id } }));
  }

  // 项目成员：四个角色
  await step('项目加成员 管理员', () =>
    api('POST', `/projects/${PID}/members`, { body: { userId: admin.user.id, role: 'MANAGER' } }));
  await step('项目加成员 开发', () =>
    api('POST', `/projects/${PID}/members`, { body: { userId: member.user.id, role: 'MEMBER' } }));
  await step('项目加成员 测试', () =>
    api('POST', `/projects/${PID}/members`, { body: { userId: tester.user.id, role: 'TESTER' } }));

  // ---------------------------------------------------------------
  // 2. 自定义字段
  // ---------------------------------------------------------------
  console.log('\n[2] 自定义字段');
  const cfOwner = await step('自定义字段：负责人（USER）', () =>
    api('POST', `/projects/${PID}/custom-fields?issueType=TASK`, {
      body: { name: '协同负责人', fieldKey: 'co_owner', type: 'USER', required: 0, sort: 10 },
    }));
  await step('自定义字段：风险等级（SELECT）', () =>
    api('POST', `/projects/${PID}/custom-fields?issueType=TASK`, {
      body: {
        name: '风险等级', fieldKey: 'risk_level', type: 'SELECT',
        options: JSON.stringify(['低', '中', '高']), required: 1, defaultValue: '中', sort: 20,
      },
    }));
  await step('自定义字段：是否为外部依赖（CHECKBOX）', () =>
    api('POST', `/projects/${PID}/custom-fields?issueType=BUG`, {
      body: { name: '外部依赖', fieldKey: 'external_dep', type: 'CHECKBOX', required: 0, sort: 10 },
    }));

  // ---------------------------------------------------------------
  // 3. 需求（含父子、变更记录）
  // ---------------------------------------------------------------
  console.log('\n[3] 需求');
  const reqEpic = await step('创建父需求（史诗）多租户隔离', () =>
    api('POST', `/projects/${PID}/requirements`, {
      body: {
        title: '【史诗】多租户数据隔离改造', description: '把单租户的数据模型改造为多租户。',
        priority: 'CRITICAL', source: 'INTERNAL', category: 'FEATURE', estimatedHours: 120,
      },
    }));
  const req1 = await step('创建需求 需求池与优先级排序', () =>
    api('POST', `/projects/${PID}/requirements`, {
      body: {
        title: '需求池与优先级排序', description: '产品经理可拖拽排序需求，支持按价值/成本打分。',
        priority: 'HIGH', source: 'CUSTOMER', category: 'FEATURE', estimatedHours: 24,
        parentId: reqEpic && reqEpic.id,
      },
    }));
  const req2 = await step('创建需求 需求变更留痕', () =>
    api('POST', `/projects/${PID}/requirements`, {
      body: {
        title: '需求字段变更留痕', description: '任意字段改动都记录旧值→新值、操作人与时间。',
        priority: 'HIGH', source: 'INTERNAL', category: 'IMPROVEMENT', estimatedHours: 8,
      },
    }));
  const req3 = await step('创建需求 大文件断点续传', () =>
    api('POST', `/projects/${PID}/requirements`, {
      body: {
        title: '文件中心支持断点续传', description: '超过 100MB 的文件分片上传。',
        priority: 'MEDIUM', source: 'MARKET', category: 'FEATURE', estimatedHours: 32,
      },
    }));

  if (req1) {
    await step('更新需求字段（产生变更记录）', () =>
      api('PUT', `/projects/${PID}/requirements/${req1.id}`, {
        body: { priority: 'CRITICAL', estimatedHours: 40 },
      }));
    await step('需求状态推进到已完成', () =>
      api('PUT', `/projects/${PID}/requirements/${req1.id}`, { body: { status: 'COMPLETED' } }));
  }
  if (req3) {
    await step('需求状态关闭（不做）', () =>
      api('PUT', `/projects/${PID}/requirements/${req3.id}`, { body: { status: 'CLOSED' } }));
  }
  if (req2) {
    await step('读取需求变更记录', () => api('GET', `/projects/${PID}/requirements/${req2.id}/changes`));
  }

  // ---------------------------------------------------------------
  // 4. 迭代
  // ---------------------------------------------------------------
  console.log('\n[4] 迭代');
  const sprintActive = await step('创建当前迭代 Sprint 24', () =>
    api('POST', `/projects/${PID}/sprints`, {
      body: { name: 'Sprint 24 — 需求池上线', goal: '需求池可用，多租户改造完成 60%', startDate: daysAgo(8), endDate: daysAhead(6) },
    }));
  if (sprintActive) {
    await step('激活当前迭代', () => api('POST', `/sprints/${sprintActive.id}/activate`));
  }
  const sprintNext = await step('创建下一迭代 Sprint 25', () =>
    api('POST', `/projects/${PID}/sprints`, {
      body: { name: 'Sprint 25 — 多租户收尾', goal: '完成剩余租户隔离与迁移演练', startDate: daysAhead(7), endDate: daysAhead(21) },
    }));
  const sprintOld = await step('创建上一迭代 Sprint 23', () =>
    api('POST', `/projects/${PID}/sprints`, {
      body: { name: 'Sprint 23 — 基础能力', goal: '登录、组织、项目三大基础模块', startDate: daysAgo(22), endDate: daysAgo(9) },
    }));

  // ---------------------------------------------------------------
  // 5. 任务（四列看板 + 依赖 + 清单 + 工时 + 评论 + 自定义字段）
  // ---------------------------------------------------------------
  console.log('\n[5] 任务');
  const taskDefs = [
    { title: '需求池页面骨架与拖拽排序', priority: 'HIGH', status: 'DONE', sp: 5, assignee: member },
    { title: '需求优先级打分算法', priority: 'CRITICAL', status: 'DONE', sp: 3, assignee: member },
    { title: '需求列表接口支持排序字段', priority: 'HIGH', status: 'DOING', sp: 5, assignee: member },
    { title: '需求详情页字段变更时间线', priority: 'MEDIUM', status: 'DOING', sp: 3, assignee: admin },
    { title: '多租户：租户上下文注入', priority: 'CRITICAL', status: 'DOING', sp: 8, assignee: member },
    { title: '多租户：41 张表加 tenant_id', priority: 'CRITICAL', status: 'TODO', sp: 13, assignee: null },
    { title: '多租户：历史数据迁移脚本', priority: 'HIGH', status: 'TODO', sp: 8, assignee: null },
    { title: '需求池空状态与引导文案', priority: 'LOW', status: 'TODO', sp: 2, assignee: tester },
    { title: '需求列表性能压测（1w 条）', priority: 'MEDIUM', status: 'TESTING', sp: 5, assignee: tester },
    { title: '需求变更记录导出 CSV', priority: 'LOW', status: 'TESTING', sp: 3, assignee: admin },
  ];
  const tasks = [];
  for (const d of taskDefs) {
    const t = await step(`任务「${d.title}」`, () =>
      api('POST', `/projects/${PID}/tasks`, {
        body: {
          title: d.title,
          description: `${d.title}。验收标准见需求「需求池与优先级排序」。`,
          priority: d.priority,
          sprintId: sprintActive && sprintActive.id,
          assigneeId: d.assignee ? d.assignee.user.id : undefined,
          storyPoints: d.sp,
          dueDate: hoursFromNow(24 * (3 + tasks.length)),
        },
      }));
    if (t) tasks.push(t);
  }

  // 状态逐级推进：TODO → DOING → TESTING → DONE
  for (let i = 0; i < tasks.length; i++) {
    const target = taskDefs[i].status;
    if (!target || target === 'TODO') continue;
    const chain = { DOING: ['DOING'], TESTING: ['DOING', 'TESTING'], DONE: ['DOING', 'TESTING', 'DONE'] }[target] || [];
    for (const s of chain) {
      await step(`任务「${taskDefs[i].title}」→ ${s}`, () =>
        api('PUT', `/tasks/${tasks[i].id}/status`, { body: { status: s } }));
    }
  }

  // 依赖：迁移脚本依赖建表，性能压测依赖列表接口
  if (tasks[6] && tasks[5]) {
    await step('任务依赖：迁移脚本 ← 加 tenant_id', () =>
      api('POST', `/tasks/${tasks[6].id}/dependencies`, { body: { dependsOnTaskId: tasks[5].id } }));
  }
  if (tasks[8] && tasks[2]) {
    await step('任务依赖：压测 ← 列表接口', () =>
      api('POST', `/tasks/${tasks[8].id}/dependencies`, { body: { dependsOnTaskId: tasks[2].id } }));
  }

  // 清单 + 工时 + 评论
  if (tasks[2]) {
    const items = ['接口支持 sort_by 参数', '支持多字段组合排序', '补充分页边界测试'];
    for (const title of items) {
      const it = await step(`清单项「${title}」`, () =>
        api('POST', `/tasks/${tasks[2].id}/checklist`, { body: { title } }));
      if (it && title !== '补充分页边界测试') {
        await step(`勾选清单项「${title}」`, () =>
          api('PUT', `/tasks/${tasks[2].id}/checklist/${it.id}`, { body: { done: true } }));
      }
    }
    for (const [h, note] of [[2.5, '接口改造'], [1.5, '补测试'], [3, '联调']]) {
      await step(`登记工时 ${h}h`, () =>
        api('POST', `/tasks/${tasks[2].id}/worklogs`, { body: { hours: h, note } }));
    }
    await step('任务评论 + @提及', () =>
      api('POST', `/tasks/${tasks[2].id}/comments`, {
        body: { content: '@周慕青 排序参数的默认值我按 created_at 走了，你看下是否符合预期？' },
      }));
    await step('任务自定义字段值', () =>
      api('PUT', `/TASK/${tasks[2].id}/custom-field-values`, {
        body: { values: { risk_level: '中', co_owner: admin.user.id } },
      }));
  }
  if (tasks[4]) {
    await step('评论（关键决策留痕）', () =>
      api('POST', `/tasks/${tasks[4].id}/comments`, {
        body: { content: '租户上下文用 ThreadLocal + 拦截器实现，异步线程需要显式传递。' },
      }));
  }

  // 需求 ↔ 任务关联
  if (req1 && tasks[0]) {
    await step('需求关联任务', () =>
      api('POST', `/projects/${PID}/requirements/${req1.id}/tasks/${tasks[0].id}`));
  }
  if (reqEpic && tasks[4]) {
    await step('史诗关联任务', () =>
      api('POST', `/projects/${PID}/requirements/${reqEpic.id}/tasks/${tasks[4].id}`));
  }

  // ---------------------------------------------------------------
  // 6. Bug（六态 + 评论 + 订阅）
  // ---------------------------------------------------------------
  console.log('\n[6] Bug');
  const bugDefs = [
    { title: '需求池拖拽后排序未持久化', severity: 'MAJOR', priority: 'HIGH', assignee: member, to: 'REOPENED' },
    { title: '需求详情时间线在 Safari 下错位', severity: 'MINOR', priority: 'LOW', assignee: admin, to: 'RESOLVED' },
    { title: '多租户上下文在异步任务中丢失', severity: 'BLOCKER', priority: 'CRITICAL', assignee: member, to: 'DOING' },
    { title: '大量需求时列表首屏 3s 以上', severity: 'MAJOR', priority: 'HIGH', assignee: tester, to: 'ASSIGNED' },
    { title: '导出 CSV 中文在 Excel 乱码', severity: 'MINOR', priority: 'MEDIUM', assignee: admin, to: 'CLOSED' },
    { title: '变更记录里操作人显示为 ID', severity: 'TRIVIAL', priority: 'LOW', assignee: null, to: 'NEW' },
  ];
  const bugs = [];
  for (const d of bugDefs) {
    const b = await step(`Bug「${d.title}」`, () =>
      api('POST', `/projects/${PID}/bugs`, {
        body: {
          title: d.title,
          description: `**复现步骤**\n1. 打开需求池\n2. 执行操作\n3. 观察结果\n\n**期望**：正常\n**实际**：${d.title}`,
          priority: d.priority,
          severity: d.severity,
          assigneeId: d.assignee ? d.assignee.user.id : undefined,
          environment: pick(['Chrome 130 / Windows 11', 'Safari 18 / macOS 15', 'Edge 130 / Windows 10']),
        },
      }));
    if (b) bugs.push(b);
  }
  // 状态流转：NEW→ASSIGNED→DOING→RESOLVED→(REOPENED|CLOSED)
  const bugFlow = {
    NEW: [],
    ASSIGNED: ['ASSIGNED'],
    DOING: ['ASSIGNED', 'DOING'],
    RESOLVED: ['ASSIGNED', 'DOING', 'RESOLVED'],
    REOPENED: ['ASSIGNED', 'DOING', 'RESOLVED', 'REOPENED'],
    CLOSED: ['ASSIGNED', 'DOING', 'RESOLVED', 'CLOSED'],
  };
  for (let i = 0; i < bugs.length; i++) {
    for (const s of bugFlow[bugDefs[i].to] || []) {
      await step(`Bug「${bugDefs[i].title}」→ ${s}`, () =>
        api('PUT', `/bugs/${bugs[i].id}/status`, { body: { status: s } }));
    }
  }
  if (bugs[0]) {
    await step('Bug 评论（定位过程）', () =>
      api('POST', `/bugs/${bugs[0].id}/comments`, {
        body: { content: '定位到是 dragEnd 里没有调用保存接口，样式看着像生效了但只是本地 state。' },
      }));
    await step('关注 Bug（订阅）', () =>
      api('POST', '/subscriptions', { body: { targetType: 'BUG', targetId: bugs[0].id } }));
  }
  if (bugs[2]) {
    await step('关注核心 Bug', () =>
      api('POST', '/subscriptions', { body: { targetType: 'BUG', targetId: bugs[2].id } }));
  }

  // ---------------------------------------------------------------
  // 7. 测试用例 / 测试计划 / 轮次 / 执行 / 转缺陷
  // ---------------------------------------------------------------
  console.log('\n[7] 测试');
  const caseDefs = [
    { title: '需求池：拖拽排序后刷新仍保持顺序', priority: 'HIGH' },
    { title: '需求池：空状态显示引导文案', priority: 'LOW' },
    { title: '需求详情：字段变更时间线完整', priority: 'HIGH' },
    { title: '多租户：异步任务能拿到租户上下文', priority: 'CRITICAL' },
    { title: '导出：CSV 中文不乱码', priority: 'MEDIUM' },
  ];
  const cases = [];
  for (const c of caseDefs) {
    const tc = await step(`用例「${c.title}」`, () =>
      api('POST', `/projects/${PID}/test-cases`, {
        body: {
          title: c.title,
          precondition: '已登录且为项目成员，需求池中至少有 3 条需求',
          steps: JSON.stringify([
            { action: '进入需求池页面', expected: '列表正常渲染' },
            { action: '拖动第一条需求到第二位', expected: '列表顺序变化' },
            { action: '刷新页面', expected: '顺序与拖动后一致' },
          ]),
          expectedResult: '刷新后顺序保持不变',
          priority: c.priority,
          requirementId: req1 && req1.id,
        },
      }));
    if (tc) cases.push(tc);
  }
  // 评审：通过 / 待评审 / 驳回各一
  const reviewTargets = ['APPROVED', 'PENDING', 'REJECTED', 'APPROVED', 'APPROVED'];
  for (let i = 0; i < cases.length; i++) {
    const target = reviewTargets[i];
    await step(`用例评审 → ${target}`, () =>
      api('PUT', `/test-cases/${cases[i].id}/review`, { body: { reviewStatus: target } }));
  }

  const plan = await step('创建测试计划 1.4 回归', () =>
    api('POST', `/projects/${PID}/test-plans`, {
      body: {
        name: '1.4 版本回归测试', description: '覆盖需求池与多租户核心链路',
        status: 'ACTIVE', ownerId: tester.user.id, startDate: daysAgo(3), endDate: daysAhead(4),
      },
    }));
  let cycle = null;
  if (plan) {
    cycle = await step('创建测试轮次 第一轮', () =>
      api('POST', `/test-plans/${plan.id}/cycles`, {
        body: { name: '第一轮：冒烟', status: 'IN_PROGRESS', startDate: daysAgo(2), endDate: daysAhead(1) },
      }));
    if (cycle) {
      await step('轮次挂入全部用例', () =>
        api('POST', `/test-cycles/${cycle.id}/cases`, { body: { caseIds: cases.map((c) => c.id) } }));
    }
  }
  // 执行记录：通过 / 失败 / 阻塞 / 跳过
  const execPlan = [['PASS', ''], ['PASS', ''], ['FAIL', '时间线缺少「原值」列'], ['BLOCKED', '依赖的租户改造未合并'], ['SKIP', '本版本不做导出改动']];
  for (let i = 0; i < cases.length; i++) {
    const [result, note] = execPlan[i];
    await step(`执行用例「${caseDefs[i].title}」→ ${result}`, () =>
      api('POST', `/test-cases/${cases[i].id}/executions`, {
        body: { result, note, cycleId: cycle && cycle.id },
      }));
  }
  if (cases[2]) {
    await step('失败用例一键转缺陷', () =>
      api('POST', `/test-cases/${cases[2].id}/to-bug`, { body: { note: '时间线缺少「原值」列' } }));
  }

  // ---------------------------------------------------------------
  // 8. 文档 / Wiki
  // ---------------------------------------------------------------
  console.log('\n[8] 文档');
  const docSpec = await step('创建文档 需求池技术方案', () =>
    api('POST', '/documents', {
      body: {
        title: '需求池技术方案', projectId: PID, type: 'DOC',
        content: '# 需求池技术方案\n\n## 背景\n需求池需要支持拖拽排序与优先级打分。\n\n## 设计\n- 排序字段 `sort`（浮点，插入取中值）\n- 打分公式 `value × 2 - cost`\n\n## 风险\n并发拖拽可能写出相同 sort。',
      },
    }));
  if (docSpec) {
    await step('文档改稿（产生版本快照）', () =>
      api('PUT', `/documents/${docSpec.id}`, {
        body: { content: '# 需求池技术方案\n\n## 背景\n需求池需要支持拖拽排序与优先级打分。\n\n## 设计\n- 排序字段 `sort`（浮点，插入取中值）\n- 打分公式 `value × 2 - cost`\n\n## 风险\n并发拖拽可能写出相同 sort —— 后端用乐观锁兜底，冲突返回 409。' },
      }));
    const versions = await step('读取文档版本列表', () => api('GET', `/documents/${docSpec.id}/versions`));
    if (versions && versions.length > 1) {
      await step('恢复历史版本', () =>
        api('POST', `/documents/${docSpec.id}/versions/${versions[versions.length - 1].id}/restore`));
    }
    await step('文档评论', () =>
      api('POST', `/documents/${docSpec.id}/comments`, { body: { content: 'sort 用浮点的话，多次插入会不会精度耗尽？' } }));
  }

  const wikiRoot = await step('创建 Wiki 根节点', () =>
    api('POST', '/documents', {
      body: { title: '研发规范', projectId: PID, type: 'WIKI', content: '# 研发规范\n\n本空间收录团队约定。' },
    }));
  if (wikiRoot) {
    await step('创建 Wiki 子节点：分支策略', () =>
      api('POST', '/documents', {
        body: { title: '分支策略', projectId: PID, type: 'WIKI', parentId: wikiRoot.id, content: '# 分支策略\n\nmain 只接受 PR 合入。' },
      }));
    await step('创建 Wiki 子节点：Code Review', () =>
      api('POST', '/documents', {
        body: { title: 'Code Review 清单', projectId: PID, type: 'WIKI', parentId: wikiRoot.id, content: '# Review 清单\n\n- 有没有漏掉边界\n- 错误处理是否吞异常' },
      }));
  }
  await step('创建会议记录', () =>
    api('POST', '/documents', {
      body: {
        title: 'Sprint 24 评审会纪要', projectId: PID, type: 'MEETING',
        content: '# Sprint 24 评审会\n\n**结论**：需求池按期上线，多租户顺延一个迭代。\n\n**待办**：林知远跟进迁移脚本。',
      },
    }));

  // ---------------------------------------------------------------
  // 9. 文件
  // ---------------------------------------------------------------
  console.log('\n[9] 文件');
  await step('上传文件（演示附件）', async () => {
    const form = new FormData();
    const content = 'DevFlow 演示附件\n需求池技术方案 v1\n';
    form.append('file', new Blob([content], { type: 'text/plain' }), '需求池技术方案.txt');
    const res = await fetch(`${BASE}/files/upload?projectId=${PID}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${owner.token}` },
      body: form,
    });
    const json = await res.json();
    if (!res.ok || json.code !== 200) throw new Error(`${res.status} ${json.message}`);
    return json.data;
  });

  // ---------------------------------------------------------------
  // 10. 聊天
  // ---------------------------------------------------------------
  console.log('\n[10] 聊天');
  token = owner.token;
  const channels = await step('读取我的频道', () => api('GET', '/channels'));
  const channel = (channels || []).find((c) => c.projectId === PID);
  if (channel) {
    const msgs = [
      '大家好，Sprint 24 的需求池今天就上线了，测试同学帮忙过一下主流程。',
      '@何以宁 用例已经挂到「第一轮：冒烟」里了，麻烦今天跑完。',
      '收到，阻塞的那条我标了 BLOCKED，等租户改造合并后再跑。',
      '多租户的建表任务我今天开始，预计两天。',
      '@林知远 迁移脚本要不要先做一次试跑？',
    ];
    for (const content of msgs) {
      await step(`发送消息「${content.slice(0, 14)}…」`, () =>
        api('POST', `/channels/${channel.id}/messages`, { body: { content } }));
    }
    const page = await step('读取频道消息', () => api('GET', `/channels/${channel.id}/messages?page=1&pageSize=20`));
    const last = page && page.records && page.records[0];
    if (last) {
      await step('编辑自己的消息', () => api('PUT', `/messages/${last.id}`, { body: { content: '多租户的建表任务我今天开始，预计三天（含迁移脚本）。' } }));
      await step('表情回应', () => api('POST', `/messages/${last.id}/reactions`, { body: { emoji: '👍' } }));
    }
    await step('标记频道已读', () => api('POST', `/channels/${channel.id}/read`, { body: {} }));
  }

  // ---------------------------------------------------------------
  // 11. 版本 / 环境 / 部署（完整审批链路）
  // ---------------------------------------------------------------
  console.log('\n[11] 版本与部署');
  token = owner.token;
  const release = await step('创建版本 v1.4.0', () =>
    api('POST', `/projects/${PID}/releases`, {
      body: { name: '需求池', version: '1.4.0', description: '本版本上线需求池与优先级排序', releaseDate: daysAhead(3) },
    }));
  if (release) {
    if (tasks[0]) {
      await step('任务挂到修复版本', () =>
        api('PUT', `/tasks/${tasks[0].id}`, { body: { fixVersionId: release.id } }));
    }
    if (bugs[0]) {
      // 带上最新版本号：该 Bug 刚经历过多次状态流转，version 已被乐观锁推进
      const fresh = await api('GET', `/bugs/${bugs[0].id}`);
      await step('Bug 挂到修复版本', () =>
        api('PUT', `/bugs/${bugs[0].id}`, { body: { fixVersionId: release.id, expectedVersion: fresh.version } }));
    }
    await step('按版本自动生成发布说明', () => api('POST', `/releases/${release.id}/notes`));
    await step('读取版本统计', () => api('GET', `/releases/${release.id}/stats`));
  }

  const envStaging = await step('创建环境 staging', () =>
    api('POST', `/projects/${PID}/environments`, {
      body: { name: '预发环境', type: 'STAGING', requiresApproval: 0, url: 'https://staging.example.com' },
    }));
  const envProd = await step('创建环境 production（需审批）', () =>
    api('POST', `/projects/${PID}/environments`, {
      body: { name: '生产环境', type: 'PROD', requiresApproval: 1, url: 'https://example.com' },
    }));

  if (envStaging) {
    // 预发：直接部署成功一次 + 一次失败
    const d1 = await step('预发部署（成功链路）', () =>
      api('POST', `/projects/${PID}/environments/${envStaging.id}/deployments`, {
        body: { releaseId: release && release.id, version: '1.4.0', description: '合入需求池' },
      }));
    if (d1) {
      await step('开始部署', () => api('POST', `/deployments/${d1.id}/start`));
      await step('部署成功', () => api('POST', `/deployments/${d1.id}/finish?success=true`, { body: {} }));
    }
    const d2 = await step('预发部署（失败链路）', () =>
      api('POST', `/projects/${PID}/environments/${envStaging.id}/deployments`, {
        body: { releaseId: release && release.id, version: '1.4.1', description: '修复时间线错位' },
      }));
    if (d2) {
      await step('开始部署', () => api('POST', `/deployments/${d2.id}/start`));
      await step('部署失败', () => api('POST', `/deployments/${d2.id}/finish?success=false`, { body: { note: '健康检查未通过' } }));
    }
  }
  if (envProd) {
    const prod = await step('生产部署（等待审批）', () =>
      api('POST', `/projects/${PID}/environments/${envProd.id}/deployments`, {
        body: { releaseId: release && release.id, version: '1.4.0', description: '需求池正式上线' },
      }));
    if (prod) {
      token = admin.token;
      await step('他人审批通过（不能自审）', () =>
        api('POST', `/deployments/${prod.id}/approve`, { body: { note: '预发验证通过，同意上线' } }));
      token = owner.token;
      await step('开始生产部署', () => api('POST', `/deployments/${prod.id}/start`));
      await step('生产部署完成', () => api('POST', `/deployments/${prod.id}/finish?success=true`, { body: {} }));
    }
  }
  await step('读取 DORA 指标', () => api('GET', `/projects/${PID}/dora?days=30`));

  // ---------------------------------------------------------------
  // 12. 设计稿与评审走查
  // ---------------------------------------------------------------
  console.log('\n[12] 设计资产与走查');
  token = owner.token;
  if (req2) {
    const design = await step('为需求关联设计稿', () =>
      api('POST', `/requirements/${req2.id}/designs`, {
        body: {
          title: '需求变更时间线 · 视觉稿', embedUrl: 'https://www.figma.com/file/demo/requirement-timeline',
          isPrimary: 1, note: 'v1 初稿',
        },
      }));
    if (design) {
      await step('设计稿状态 → 评审中', () =>
        api('PUT', `/designs/${design.id}/status`, { body: { status: 'IN_REVIEW' } }));
      await step('设计稿状态 → 评审通过', () =>
        api('PUT', `/designs/${design.id}/status`, { body: { status: 'APPROVED' } }));
      await step('提交设计稿新版本', () =>
        api('POST', `/designs/${design.id}/versions`, {
          body: { snapshotUrl: 'https://www.figma.com/file/demo/requirement-timeline-v2', note: '按评审意见调整了时间线密度' },
        }));

      const wt = await step('发起设计走查', () =>
        api('POST', `/requirements/${req2.id}/walkthroughs`, {
          body: { title: '需求时间线走查 #1', designAssetId: design.id, designVersion: 1 },
        }));
      if (wt) {
        await step('批量添加走查项', () =>
          api('POST', `/walkthroughs/${wt.id}/items`, {
            body: { titles: ['时间线节点间距是否一致', 'hover 态信息是否完整', '空状态文案是否出现'] },
          }));
        const detail = await step('读取走查详情', () => api('GET', `/walkthroughs/${wt.id}`));
        const items = (detail && detail.items) || [];
        if (items[0]) {
          await step('走查项判定：通过', () =>
            api('PUT', `/walkthrough-items/${items[0].id}`, { body: { result: 'PASS' } }));
        }
        if (items[1]) {
          await step('走查项判定：不通过', () =>
            api('PUT', `/walkthrough-items/${items[1].id}`, { body: { result: 'FAIL', note: '缺少操作人头像' } }));
          await step('不通过项转缺陷', () =>
            api('POST', `/walkthrough-items/${items[1].id}/to-bug`, { body: {} }));
        }
        if (items[2]) {
          await step('走查项判定：不适用', () =>
            api('PUT', `/walkthrough-items/${items[2].id}`, { body: { result: 'NA' } }));
        }
        await step('结束走查', () => api('POST', `/walkthroughs/${wt.id}/close`));
      }
    }
  }

  // ---------------------------------------------------------------
  // 13. 自动化规则 / 筛选器 / 订阅 / 通知偏好
  // ---------------------------------------------------------------
  console.log('\n[13] 自动化与偏好');
  token = owner.token;
  await step('自动化规则：新 Bug 自动指派给开发', () =>
    api('POST', `/projects/${PID}/automations`, {
      body: { name: '新 Bug 自动指派', triggerType: 'BUG_CREATED', assignToUserId: member.user.id, notifyUserId: tester.user.id, enabled: 1 },
    }));
  await step('自动化规则：用例失败通知测试组', () =>
    api('POST', `/projects/${PID}/automations`, {
      body: { name: '用例失败通知', triggerType: 'CASE_FAIL', notifyUserId: tester.user.id, enabled: 1 },
    }));
  await step('自动化规则：任务完成通知', () =>
    api('POST', `/projects/${PID}/automations`, {
      body: { name: '任务完成播报', triggerType: 'TASK_DONE', notifyUserId: manager.user.id, enabled: 1 },
    }));

  await step('保存筛选器：我的高优任务', () =>
    api('POST', '/filters', {
      body: { projectId: PID, entity: 'TASK', name: '我的高优任务', expression: 'priority = CRITICAL and status != DONE', isShared: 1 },
    }));
  await step('保存筛选器：未解决 Bug', () =>
    api('POST', '/filters', {
      body: { projectId: PID, entity: 'BUG', name: '未解决的高严重度 Bug', expression: 'severity = BLOCKER or severity = CRITICAL', isShared: 1 },
    }));

  await step('订阅需求', () => api('POST', '/subscriptions', { body: { targetType: 'REQUIREMENT', targetId: reqEpic && reqEpic.id } }));
  await step('订阅文档', () => api('POST', '/subscriptions', { body: { targetType: 'DOCUMENT', targetId: docSpec && docSpec.id } }));

  await step('通知偏好：批量开启', () => api('POST', '/notification-preferences/bulk', { body: { enabled: true } }));
  await step('通知偏好：关闭系统消息', () => api('PUT', '/notification-preferences', { body: { type: 'SYSTEM', enabled: false } }));

  // ---------------------------------------------------------------
  // 14. 游客通道
  // ---------------------------------------------------------------
  console.log('\n[14] 游客通道');
  token = owner.token;
  const guest = await step('生成游客反馈链接（7 天）', () =>
    api('POST', `/projects/${PID}/guest-links`, { body: { expiresInDays: 7 } }));
  if (guest && guest.token) {
    const gToken = guest.token;
    const saved = token;
    token = '';
    await step('（游客）查看项目信息', () => api('GET', `/guest/${gToken}/project`));
    await step('（游客）提交 Bug', () =>
      api('POST', `/guest/${gToken}/bugs`, {
        body: { title: '在手机上打开需求池会横向溢出', description: 'iPhone 15 Safari，需求卡片右侧被截断。', environment: 'iOS 18 / Safari', nickname: '合作方-王工', email: 'wang@partner.example' },
      }));
    await step('（游客）提交建议', () =>
      api('POST', `/guest/${gToken}/feedback`, {
        body: { type: 'SUGGESTION', title: '希望支持批量导入需求', content: '我们有 200 条存量需求需要一次性导入。', nickname: '合作方-王工' },
      }));
    await step('（游客）查看自己的提交', () => api('GET', `/guest/${gToken}/submissions`));
    token = saved;

    // 站内确认游客 Bug + 处理建议
    const feedbacks = await step('读取反馈列表', () => api('GET', `/projects/${PID}/feedback`));
    if (feedbacks && feedbacks.length) {
      const guestBug = feedbacks.find((f) => f.type === 'BUG' || f.submitterType === 'GUEST');
      if (guestBug) {
        await step('反馈 → 接受', () => api('PUT', `/feedback/${guestBug.id}/status`, { body: { status: 'ACCEPTED' } }));
        await step('反馈 → 转缺陷', () => api('POST', `/feedback/${guestBug.id}/convert`));
      }
      const sug = feedbacks.find((f) => f.type === 'SUGGESTION');
      if (sug) {
        await step('建议 → 拒绝（不在本版本）', () => api('PUT', `/feedback/${sug.id}/status`, { body: { status: 'REJECTED' } }));
      }
    }
  }

  // ---------------------------------------------------------------
  // 15. Git Webhook
  // ---------------------------------------------------------------
  console.log('\n[15] Git Webhook');
  token = owner.token;
  const projectFull = await step('读取项目详情（取 webhook 密钥）', () => api('GET', `/projects/${PID}`));
  const webhookSecret = projectFull && projectFull.gitWebhookSecret;
  if (webhookSecret) {
    const commits = [
      { id: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678', message: 'feat(requirement): 需求池拖拽排序落库', author: { name: '苏遥' }, url: 'https://github.com/lausoar/DevFlow/commit/a1b2c3d' },
      { id: 'b2c3d4e5f60718293a4b5c6d7e8f90123456789a', message: 'fix(requirement): 修复刷新后顺序回退', author: { name: '苏遥' }, url: 'https://github.com/lausoar/DevFlow/commit/b2c3d4e' },
      { id: 'c3d4e5f60718293a4b5c6d7e8f90123456789abc', message: 'test: 补充需求池边界用例', author: { name: '何以宁' }, url: 'https://github.com/lausoar/DevFlow/commit/c3d4e5f' },
      { id: 'd4e5f60718293a4b5c6d7e8f90123456789abcde', message: 'chore(ci): 缓存 maven 依赖', author: { name: '周慕青' }, url: 'https://github.com/lausoar/DevFlow/commit/d4e5f60' },
    ];
    await step('模拟 GitHub push 事件（4 个提交）', async () => {
      const res = await fetch(`${BASE}/projects/${PID}/git/webhook?secret=${encodeURIComponent(webhookSecret)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pusher: { name: '苏遥' }, ref: 'refs/heads/main', commits }),
      });
      const json = await res.json();
      if (!res.ok || json.code !== 200) throw new Error(`${res.status} ${json.message}`);
      return json.data;
    });
    await step('重复推送同 SHA（验证去重）', async () => {
      const res = await fetch(`${BASE}/projects/${PID}/git/webhook?secret=${encodeURIComponent(webhookSecret)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pusher: { name: '苏遥' }, commits: [commits[0]] }),
      });
      const json = await res.json();
      if (json.data && json.data.stored !== 0) throw new Error(`去重失效，重复落库 ${json.data.stored} 条`);
      return json.data;
    });
    await step('读取项目最近提交', () => api('GET', `/projects/${PID}/git/commits`));
  } else {
    failed.push('Git Webhook — 项目未返回 webhook 密钥，跳过推送模拟');
    process.stdout.write('  ✗ Git Webhook — 项目未返回 webhook 密钥，跳过推送模拟\n');
  }

  // ---------------------------------------------------------------
  // 16. 审计链自检
  // ---------------------------------------------------------------
  console.log('\n[16] 审计链');
  token = owner.token;
  await step('校验审计哈希链', () => api('GET', `/enterprises/${EID}/audit-logs/verify`));

  // ---------------------------------------------------------------
  // 17. 归档演示项目
  // ---------------------------------------------------------------
  if (archivedProject) {
    await step('归档演示项目', () =>
      api('PUT', `/projects/${archivedProject.id}`, { body: { status: 'ARCHIVED' } }));
  }

  // ---------------------------------------------------------------
  // 汇总
  // ---------------------------------------------------------------
  console.log(`\n${'='.repeat(60)}`);
  console.log(`完成：成功 ${log.length} 步，失败 ${failed.length} 步`);
  if (failed.length) {
    console.log('\n失败明细：');
    failed.forEach((f) => console.log('  • ' + f));
  }
  console.log('\n演示账号（密码统一 Demo@2026）：');
  for (const u of [owner, admin, manager, member, tester]) {
    console.log(`  ${u.user.username.padEnd(14)} ${u.role.padEnd(12)} ${u.user.realName}`);
  }
  console.log('\n关键 ID：');
  console.log(`  企业      ${EID}`);
  console.log(`  项目      ${PID}   （星野研发平台）`);
  console.log(`  项目集    ${program ? program.id : '—'}`);
  console.log(`  迭代      ${sprintActive ? sprintActive.id : '—'}`);
  console.log(`  文档      ${docSpec ? docSpec.id : '—'}`);
  console.log(`  版本      ${release ? release.id : '—'}`);
  console.log(`\n前端直达： http://localhost:3000/projects/${PID}`);
  console.log(`          http://localhost:3000/projects/${PID}?tab=testing`);
  console.log(`          http://localhost:3000/projects/${PID}?tab=releases`);
  console.log(`          http://localhost:3000/projects/${PID}?tab=deployments\n`);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => {
  console.error('\n生成中断：', e);
  process.exit(1);
});

// ============================================
// 账号辅助
// ============================================
async function login(username, password) {
  const data = await api('POST', '/auth/login', { body: { username, password }, auth: false });
  return { token: data.accessToken, user: data.user };
}

/** 注册（已存在则直接登录）；返回 { token, user, role } */
async function loginOrRegister(username, realName, role) {
  const email = `${username}@demo.devflow.local`;
  const password = 'Demo@2026';
  try {
    return { ...(await login(username, password)), role };
  } catch {
    // 不存在 → 注册
  }
  try {
    const data = await api('POST', '/auth/register', {
      body: { username, realName, email, password }, auth: false,
    });
    return { token: data.accessToken, user: data.user, role };
  } catch (e) {
    // 注册失败（多半是已存在但密码不同）→ 用重置后的固定口令再登一次
    throw new Error(`账号 ${username} 无法登录也无法注册：${e.message}`);
  }
}

/** 超管创建企业（已有企业则复用） */
async function ensureEnterprise(owner, name) {
  token = owner.token;
  try {
    const cur = await api('GET', '/enterprises/current');
    if (cur && cur.id) { owner.user.enterpriseId = cur.id; return cur; }
  } catch { /* 无企业 */ }
  const ent = await api('POST', '/enterprises', { body: { name } });
  owner.user.enterpriseId = ent.id;
  return ent;
}

/** 由超管邀请一个成员（角色按需），然后以该成员登录 */
async function inviteAndLogin(owner, username, realName, role) {
  const email = `${username}@demo.devflow.local`;
  const password = 'Demo@2026';
  token = owner.token;
  // 先注册成独立账号（无企业），再由超管邀请拉进企业——避免"已加入其他企业"
  try {
    await api('POST', '/auth/register', {
      body: { username, realName, email, password }, auth: false,
    });
  } catch { /* 已注册过 */ }
  try {
    await api('POST', `/enterprises/${owner.user.enterpriseId}/members/invite`, {
      body: { email, realName, role },
    });
  } catch (e) {
    if (!/已加入|已存在/.test(e.message)) {
      process.stdout.write(`  · 邀请 ${username} 跳过：${e.message}\n`);
    }
  }
  token = '';
  try {
    const data = await login(username, password);
    return { ...data, role };
  } catch (e) {
    throw new Error(`成员 ${username} 登录失败：${e.message}`);
  }
}
