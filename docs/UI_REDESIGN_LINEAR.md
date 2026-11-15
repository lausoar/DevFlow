# UI 重构：采用 Linear 设计语言

> 用 `awesome-design-md` 里的 `linear.app` 那份分析重构前端视觉层。
> 只改样式与 token，**没有改任何业务逻辑、组件 API 或数据流**。

## 一、选了哪份设计，为什么

`INDEX.md` 里 84 个候选，筛到 Linear 的理由：

- 它面向**开发者工具**，信息密度高，与本项目（研发管理平台）同构；
- 它有完整的产品内界面分析（surface 阶梯、hairline 边框、状态徽章），
  不只是营销页；
- 它的体系极简：**一个强调色 + 四级表面 + 四级文字**，落地时不需要重写每个页面。

对比排除了 Airbnb / Stripe（消费级，暖色，密度低）与 Apple（摄影优先，
不适用于数据密集的后台）。

## 二、token 层

`globals.css` 重写为两层：

```
@theme inline  → 把 CSS 变量映射成 Tailwind 工具类（bg-surface-1 / text-ink-subtle …）
:root, .dark   → Linear 实测值
.light         → 推导值（见下）
```

### 实测值（直接来自 designs/linear.app.md）

```css
--canvas: #010102;        --surface-1: #0f1011;   --surface-2: #141516;
--surface-3: #18191a;     --surface-4: #191a1b;
--hairline: #23252a;      --hairline-strong: #34343a;
--ink: #f7f8f8;           --ink-muted: #d0d6e0;
--ink-subtle: #8a8f98;    --ink-tertiary: #62666d;
--brand: #5e6ad2;         --brand-hover: #828fff;
--success: #27a644;
圆角：4 / 6 / 8 / 12 / 16 / 24
```

### 推导值 —— **这些不是 Linear 原值，说明如下**

Linear 那份分析是**深色营销画布**的分析，浅色主题只给了四个值
（`inverse-canvas` / `inverse-surface-1/2` / `inverse-ink`）。所以：

| token | 来源 | 说明 |
|---|---|---|
| `.light` 的 surface-1/3/4 | 推导 | 按深色那套的相对明度关系换算 |
| `.light` 的 hairline | 推导 | 取 #e4e6e9，保证在 #fff 上可见但不抢眼 |
| `.light` 的 ink-muted | 推导 | #3d4149，对比度约 11:1 |
| `--warning` / `--danger` / `--info` | 推导 | Linear 只给了 success(#27a644)。三者按同档饱和度取，保证并列时不争主次 |
| `.light` 的 success/warning/danger | 推导 | 深色下的值在浅底上对比度不足（#27a644 在白底约 3.1:1），压暗到 AA |
| `--focus-ring` | 推导 | Linear 文字描述为"1px 描边 + 淡外扩"，具体数值是估的 |

字号档位按 Linear 的比例下推：它 80px 用 -3px 字距（约 4%），
所以 24px 用 -0.024em、正文 14px 用 -0.006em。

## 三、组件层

重写了 5 个基础组件，全部按 DESIGN.md 的 `components` 段取值：

| 组件 | 关键改动 | 依据 |
|---|---|---|
| `button` | 圆角 12px → **8px**；hover 换色而不是降透明度；焦点环 1px 描边 + 淡外扩；高度统一 32px | `button-primary` 的 `rounded.md` + `padding 8px 14px` |
| `card` | 去掉 `ring-foreground/10`（跟着 foreground 变色，浅色下变黑边），改 `border-hairline`；圆角 16px → **12px** | `feature-card` 的 `rounded.lg` + hairline |
| `input` / `textarea` | 从 `bg-transparent` + `dark:bg-input/30` 改为**始终有 surface 底色**，靠边框表达可编辑 | `text-input` 的 `surface-1` + `rounded.md` |
| `badge` | 补 `success` / `warning` / `info` 语义变体，用「淡底 + 同色文字」而非实心块 | `status-badge` + "深色画布上从不用大块饱和色" |
| `tabs` | 底从 muted 改 surface-1 + hairline；选中态 surface-2 + ink；下划线用品牌色 | `pricing-tab-default/selected` |

## 四、把 452 处硬编码颜色 token 化

改造前全仓有 **456 处**硬编码调色板类名（`text-green-600`、`bg-amber-100`…），
外加 104 处 `dark:` 变体手工补偿。

现在映射到语义 token：44 个文件、452 处替换。

```
text-green-600 → text-success      bg-red-100  → bg-danger-muted
text-amber-600 → text-warning      bg-blue-50  → bg-info-muted
text-slate-600 → text-ink-subtle   border-gray-200 → border-hairline
```

**刻意没有映射的**：`from-indigo-500 to-purple-600` 这类装饰性渐变。
它没有语义，强套 token 会让设计语言失去唯一的彩色强调。

### 这一步我犯了一个值得记的错误

写了个批量替换脚本，清理空白时用了 `\s{2,}`。`\s` **匹配换行**，
把 151 个文件各压成了一行，`tsc` 报 100+ 个语法错误。

第二版改用 `[ ]{2,}`，仍然错——缩进本身就是两个以上空格，
JSX 的 `Select,` 被压成 1 个空格缩进，语法又崩。

第三版才想清楚：**只处理引号内部的字符串字面量**，
字符串外的代码（缩进、换行、标识符间距）一个字节都不碰。
代价是留下一些 `text-ink  ` 的多余空格，Tailwind 忽略它们。

教训：批量改写源码时，"清理格式"比"替换内容"危险得多。
先限制作用域（只动字符串内部），再考虑整洁。

## 五、布局层

- **侧边栏** 240px → 224px（Linear 的导航更紧凑），项高 32px、13px 字；
  `Separator` 组件换成 `border-t border-hairline`（少一个 DOM 节点）；
- **顶栏** 用 `bg-canvas/80 + backdrop-blur-xl`，搜索入口改 surface-1 + hairline；
- **内容区** 背景显式 `bg-canvas`，避免继承到 card 的颜色；
- **标题层级**：`text-2xl font-bold` → `.text-headline`（24px/600/-0.024em）。
  Linear 靠字号+字距造层级，不靠堆到 700。

## 六、验证

- `tsc --noEmit`：**0 错误**
- `next build`：通过
- `lint`：16 errors / 104 warnings —— **与改造前完全一致**（存量问题，未新增）

浏览器实测（dev server + 真实登录）：

```
侧边栏宽度 224px        ✓
卡片底色 rgb(15,16,17)  = #0f1011  ✓
卡片边框 rgb(35,37,42)  = #23252a  ✓
侧栏底色 rgb(8,9,10)              ✓
body 字号 14px                    ✓
```

页面走查：登录页、工作台、项目中心、项目详情（概览/任务看板）、统计报表，
深浅两套主题各看一遍。

**一个需要说明的现象**：项目详情的「任务」标签页会调
`GET /projects/{id}/workflow` 并返回 500。这是**后端**的响应
（`localhost:8080`），与本次样式改造无关——该接口在本地后端未启动工作流配置时
就会失败，改造前也存在。样式层面该页面正常渲染。

## 七、本批未做

- **不改变信息架构**：没有重新组织页面结构、没有合并/拆分页面。
  这是视觉重构，不是产品重构。
- **图表未重绘**：报表里的图表仍是旧配色（走 `--chart-1..5` 会随主题变化，
  但图形本身没按 Linear 的极简风格重画）。
- **字体**：Linear 用的是自有字体（Linear Display / Linear Text / Linear Mono），
  这里降级到 `Geist`（next/font 已加载）+ 系统字体栈。
  正文的负字距与字重按 Linear 的比例还原，但字形本身不同。
- **营销页**：本项目是后台系统，没有营销页，所以 Linear 分析里
  「产品截图作为页面节奏」那条完全不适用。
