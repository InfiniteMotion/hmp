# HMP Design System —— 一套 Compose UI 与一套设计 token 的长相契约

> **约束**：三端只有一套 UI（`shared-ui` 的 `commonMain` → `AppRoot`）与一套 token（颜色 / 字体 / 尺寸 / 动效 / 毛玻璃）；改外观一律改 `shared-ui`，平台壳不得自绘第二套视觉。
> **适用范围**：`shared-ui` 的 `commonMain` 与三端 actual、Android / iOS / Desktop 壳层的视觉改动、新增组件与页面 ｜ **最后核对**：2026-11-04（五类 token 全部落地后整篇重排）
> **判据**：① 业务代码零硬编码——裸 `.dp/.sp`、`Color(0x…)`、`MaterialTheme.typography`、动效裸时长 / 裸弹簧一律红，机械守门 = `./gradlew-smart checkDesignTokens`；确属例外的行写 `// design-token-exception: <理由>` ② `grep -c "BottomTabItem(" shared-ui/.../agent/shell/BottomFusionBar.kt` = **3**，且 `tabIndexForPage(page) = page - 1`（门面页无 Tab）③ 布局分支只经 `AppWindowSizeInfo`（`useFusionSidebar = isLandscape && heightSizeClass == Compact`），不得另设断点 ④ 毛玻璃只经 `hazeEffect(state, hazeStyleForIntensity())`，强度 / 模糊 / 噪点取自 `LocalHazeRenderSettings`

## 0. 文档地图

| 章 | 主题 | 状态 | 机械守门 |
|---|---|---|---|
| §1 | 设计原则 | — | — |
| §2 | 体系总纲 | — | — |
| §3 | 颜色 | 已收口（色值 38 → 0） | 硬编码 `Color(0x…)` 全量禁止 |
| §4 | 字体（9 角色 × 3 窗口档） | 已落地 | 裸 `.sp` / `MaterialTheme.typography` 禁止 |
| §5 | 尺寸（12 档 × 3 窗口档） | 已落地 | 裸 `.dp/.sp` 全量禁止 |
| §6 | 动效（4 档 + 3 缓动 + 2 弹簧） | 已落地 | 裸时长 / 裸弹簧 / 无显式 spec 禁止 |
| §7 | 毛玻璃 | 已收口 | 无机械守门（结构约束 + 判据④） |
| §8 | 应用层（布局 / 背景 / 组件） | — | 断点只经 `AppWindowSizeInfo` |
| §9 | 落地与维护 | — | — |
| §10 | 已知偏差（历史证据） | — | — |
| §11 | 修订记录 | — | — |

读法：改外观 → §2 找对应 token 的真源与入口；新增组件 → §5 档位 + §4 角色；判断「能不能写死数字」→ 看对应章的「业务禁令与守门」。

## 1. 设计原则

HMP 是纯本地、跨平台音乐播放器：不做云同步、不设账号、不追踪。

- **纯粹**：UI 是音乐的容器，克制装饰，让内容与封面说话。
- **私密**：暗色主色由激进的红换为柔和浅蓝——夜间听歌不该被红刺到。
- **用心但安静**：毛玻璃是空间层次的表达（玻璃漂浮在动态背景之上），不是炫技。
- **明暗同等对待**：两套色板各自设计，禁止简单翻转颜色。

## 2. 体系总纲

### 2.1 五类 token 与四项守门

完整设计体系 = 五类 token + 一条共用档位判据 + 四项机械守门。各类有独立真源与唯一入口，**互不整合**，只在契约上并列：

| 类别 | 真源 | 业务入口 | 业务禁令 | 机械守门 | 规范 |
|---|---|---|---|---|---|
| 颜色 | `ColorTokens` / `AgentStatusColors` / `HelloCardPalette` | `MaterialTheme.colorScheme` | 裸 `Color(0x…)` | 色值全量禁止 | §3 |
| 字体 | `TypeScale` / `HMPTypoTokens` | `LocalHmpTypo`（9 角色） | 裸 `.sp`、`MaterialTheme.typography.*` | 排版 API 全量禁止 | §4 |
| 尺寸 | `MetricScale` / `HMPMetricTokens` / `LayoutConstraints` | `LocalHmpMetrics`（12 档 + 组件预设） | 裸 `.dp` | 尺寸全量禁止 | §5 |
| 动效 | `HmpMotion`（4 档 + 3 缓动 + 2 弹簧） | 场景工厂 `page` / `emphasis` / `enter` / `exit` / `micro` / `standard` | 裸毫秒、裸弹簧、无显式 spec | 动效全量禁止 | §6 |
| 毛玻璃 | `HazeIntensity` | `LocalHazeRenderSettings` + `hazeEffect` | 自绘玻璃、硬编码强度 | 结构约束（判据④） | §7 |

四项守门全部并入 `checkDesignTokens`，`preflight` 自动带；**无基线、无存量容忍**，例外逐行申报（§9.2）。

### 2.2 唯一跨类共用：档位判据

字体与尺寸刻度共用 `scaleWidthClassFor(width, height, isPhoneLandscape)`（§5.4）：手机横屏强制 Compact、矮横窗（Expanded 宽 × Compact 高）降一档取 Medium、其余按宽度档直取。

**跳变时机同步是硬要求**——字号与尺寸若在不同断点跳档，跨档瞬间布局不协调，且只在拖拽桌面窗口时可见、难复现。颜色 / 动效 / 毛玻璃不共用这条判据：它们不是随窗口档变化的离散量。

## 3. 颜色

> **真源**：`ColorTokens` / `AgentStatusColors` / `HelloCardPalette` ｜ **入口**：`MaterialTheme.colorScheme` ｜ **守门**：色值全量禁止（`java.awt.Color` 自动排除）

### 3.1 品牌色

标识是**红蓝冷暖对比**：红 = 情感温度，蓝 = 理性克制（`ColorTokens.kt:7-8`）。

| 令牌 | 色值 |
|------|------|
| `HDRed` | `#C92C2C` |
| `HDBlue` | `#002FA7` |

壳层另有 `ColorTokens.Shell` 三个固定色：悬浮歌词窗底（`#E61A1A1A`）、桌面关闭按钮 hover（`#C42B1C`）/ press（`#B22419`）——它们叠加在共享 Theme 之外，不随主题切换。

### 3.2 亮色主题

| 令牌 | 色值 | 用途 |
|------|------|------|
| `primary` / `onPrimary` / `secondary` | `#C92C2C` / `#FFFFFF` / `#002FA7` | 按钮、选中态、链接；标签与辅助强调 |
| `primaryContainer` / `onPrimaryContainer` | `#1976D2` / `#B00020` | 主色容器（蓝，与红形成冷暖对比） |
| `background` / `surface` | `#FFFFFF` | 页面底色 / 卡片表面 |
| `surfaceVariant`、`onSurface`、`onSurfaceVariant`、分割线 | **未定义** | 走 M3 默认（D-6） |

### 3.3 暗色主题

**关键决策**：暗色主色从品牌红切换为 `#90CAF9`（柔和浅蓝），红退到 `primaryContainer`（`#CF6679` 粉红）——大面积红在暗底刺眼。

| 令牌 | 色值 | 用途 |
|------|------|------|
| `primary` / `onPrimary` / `secondary` | `#90CAF9` / `#000000` / `#F48FB1` | 按钮、选中态、链接（暗色下主色上为黑字）；标签与辅助强调 |
| `primaryContainer` / `onPrimaryContainer` | `#CF6679` / `#1976D2` | 主色容器 |
| `background` / `surface` | `#121212` | 页面底色 / 卡片表面 |
| `surfaceVariant`、`onSurface`、`onSurfaceVariant`、分割线 | **未定义** | 走 M3 默认（D-6） |

### 3.4 主题切换

三模式：跟随系统（默认）/ 强制亮色 / 强制暗色，持久化在共享 `SettingsRepository.themeMode`（`SettingsRepository.kt:18`，UI 见 `CustomScreen.kt:425-455`）。**播放中整体切动态主题，暂停回落预置主题**（`AppRoot.kt:191-195`）。

### 3.5 动态主题（专辑取色）

播放时从当前封面取色生成动态色板（`ThemeViewModel.kt:110-241`，3D 直方图峰值检测 + WCAG 对比度）：① 主色 = 饱和度最高的峰，且与背景对比度 ≥ 3.0:1（不足则 `ensureContrast` 推进到达标）② 背景色 = `明度 + 饱和度×0.3` 最小的峰 ③ 强调色 = 色相距主色最远的峰，对比度 < 2.0 时退回主色。

**何时使用**：播放器页、迷你播放条、动态氛围背景；预设与动态主题共存，用户可切回预设。

### 3.6 业务禁令与守门

- 业务禁裸 `Color(0x…)`；色值一律走 `ColorTokens` / `AgentStatusColors` / `HelloCardPalette`。
- 守门为**色值全量禁止**，扫 `shared-ui/src/**` + `desktop/app/src/**`；`java.awt.Color` 是 AWT 绘制色，自动排除。
- 需要「按内容取色」的场景（专辑封面）走 `ThemeViewModel` 的动态取色，不得就地算色值。

## 4. 字体

> **真源**：`TypeScale`（纯数据）/ `HMPTypoTokens`（Composable） ｜ **入口**：`LocalHmpTypo`（9 角色） ｜ **守门**：裸 `.sp` 与 `MaterialTheme.typography` 全量禁止

### 4.1 平台字体与字重

**三端同一字体：HarmonyOS Sans**，6 个字重 Thin / Light / Regular / Medium / Bold / Black 随 `shared-ui/src/commonMain/composeResources/font/` 打包。**iOS 不是 SF Pro、Desktop 不是系统默认**（D-1）。

字阶字重只有 3 档：**Regular / Medium / Bold**。HarmonyOS Sans 官方字重止于 6 档（无 600）——2026-11-04 以官方字体包实测确认，原「补 `semibold.ttf`」计划作废，SemiBold 档从字阶移除。Thin / Light / Black 只保留在 `FontFamily` 定义里供特殊场景显式引用，禁止进字阶角色——杜绝字重档位膨胀。

### 4.2 架构：两件套

```
TypeScale.kt      纯数据，零 Compose 依赖，可直接单测（FontWeight 是纯值对象，不破坏此边界）
                  ├── 9 角色 × 3 窗口档整数数列 + 档位查表（含手机横屏 / 矮横窗规则）
                  ├── 行高比率（每角色常数，跨档不变）
                  ├── 字距档（tight 0 / title 0.15sp / regular 0.25sp）
                  ├── 角色默认字重（§4.3 表；跨档不变，覆盖是组合层的显式动作）
                  ├── 等宽数字特性档（tabular = "tnum"）
                  └── 行高裁剪（trim，角色级，见 §4.4）

HMPTypoTokens.kt  Composable 世界（CMP 资源 Font() 是 @Composable API）
                  ├── FontFamily（6 字重，官方字重全集）——常量不进 TypeScale
                  ├── LocalHmpTypo —— 业务唯一排版入口（三装配点 provides）
                  ├── 场景角色 TextStyle 组合（尺寸 × 字重 × 字距，字重默认取角色档、
                  │   同尺寸异字重场景用显式变体覆盖，如 body.medium()）
                  └── m3ComponentTypography（internal）—— 仅喂 M3 组件内部文字，非业务 API
```

**设计决策：原生整数数列，不用缩放因子。** ① 等比缩放在排版上是错的（tracking 应随字号收紧而非等比放大，行高比跨档也非恒定）；② 整数 sp 可单测精确断言、可与设计稿直接对数；③ 与 §5 尺寸刻度共用 `scaleWidthClassFor()` 判据，跳变时机一致。

**设计决策：M3 typography 退场，不做业务级兼容投影**（2026-11-04 用户定）。原「15 级派生映射」方案被否——15 槽位挤 9 角色必然交叉（`titleLarge`(18) < `displaySmall`(22)、`displayMedium` == `headlineLarge`），且映射处字重覆盖 9/15 处，等于自证「覆盖泛滥即角色切分错误」。保留的唯一 M3 投影是 `internal` 的组件内部投影：Button / TextField / Dialog 等 M3 组件内部读 `MaterialTheme.typography`，不喂会退回 Roboto 默认字族；该投影零字重覆盖、就近映射，仅实现细节。

### 4.3 9 角色 × 3 窗口档

| 角色 | Compact | Medium | Expanded | 默认字重 | 吸收的现有值 |
|------|--------:|-------:|---------:|---|---|
| `micro` | 9 | 10 | 11 | Regular | 8 / 9 / 10sp（索引条、角标） |
| `caption` | 11 | 12 | 13 | Medium | 11sp、`type.xs`、`labelSmall` |
| `footnote` | 12 | 13 | 14 | Regular | 12 / 13sp、`bodySmall`、`type.sm` |
| `body` | 14 | 15 | 17 | Regular | 14 / 15sp、`bodyMedium`、`titleSmall`、`labelLarge` |
| `subtitle` | 16 | 18 | 19 | Medium | 16 / 17sp、`bodyLarge`、`titleMedium`、`type.md` |
| `headline` | 18 | 20 | 22 | Medium | 18sp、`headlineMedium`、`titleLarge` |
| `title` | 22 | 24 | 26 | Bold | 20 / 22sp、`headlineLarge`、`type.lg` |
| `display` | 28 | 31 | 34 | Bold | 24–27sp、`displayMedium`、`displaySmall` |
| `displayL` | 34 | 37 | 41 | Bold | 32 / 40sp、`displayLarge`、`type.xl` |

- **缩放幅度刻意比尺寸温和**（≈ +1 / +2 档，对比 §5 尺寸的 1.25×–1.5×）：文字放大直接引发换行与截断，大屏排版气质由尺寸承担、字号微调即可。
- **手机横屏强制 Compact · 矮横窗降档**：复用 §5.4 同一判据。
- **奇数字号归最近偶数档**：存量 13→12 / 14、15→14 / 16、25–27→22 / 28；字阶里不出现半档——档位膨胀等于退回自由字号。

### 4.4 行高 / 字距 / 字重 / 裁剪 / 等宽数字

- **行高比率**：每角色一个常数，跨档不变（`lineHeight = size × ratio`）——`micro` 1.35 / `caption` 1.45 / `footnote` 1.4 / `body` 1.4 / `subtitle` 1.35 / `headline` 1.35 / `title` 1.35 / `display` 1.25 / `displayL` 1.2。
- **行高裁剪（trim）**：角色默认带 `LineHeightStyle` 首尾行距裁剪（`Trim.Both`）——单行文本是 UI 主流，不裁则首行上方 / 末行下方各多出约 `(ratio − 1) / 2` 倍字号的空隙，卡片密集文本垂直偏松。多行正文在组合层用 `.multiline` 变体显式关闭裁剪（与字重同款「默认 + 显式覆盖」模式）。三端渲染差异（skiko vs Android）实施时走查。
- **等宽数字（tabular）**：时间 / 时长 / 进度 / 统计数值用场景角色的 `.tabular` 变体（`fontFeatureSettings = "tnum"`）——比例数字每个数字宽度不同，`03:24` 这类高频刷新文本会左右抖动。官方字体包 Regular / Medium 均含 `tnum` + `pnum`，已验证。
- **字距只有 3 档且跨档不缩放**：`tight` 0 / `title` 0.15sp / `regular` 0.25sp；存量 0.4 / 0.5 / 0.6sp 散值一律归档。
- **字重以「角色默认档」进 TypeScale，跨档不变**：默认值见 §4.3 表；同尺寸异字重的真实场景在组合层用显式变体覆盖（`.medium()` = Medium）。覆盖应当少见——覆盖泛滥说明角色切分错了，这本身是体系健康度的信号。

### 4.5 M3 槽位退场映射

`MaterialTheme.typography.X` 全量替换为 `typo.X`（D8-03 阶段 4A，477 处 / 64 文件，**语义优先、不保视觉**）：

| M3 槽位（处数） | → 字阶角色 | | M3 槽位（处数） | → 字阶角色 |
|---|---|---|---|---|
| `bodySmall`（102） | `footnote` | | `titleLarge`（9） | `headline` |
| `bodyMedium`（89） | `body` | | `headlineSmall`（13） | `subtitle` |
| `labelSmall`（81） | `caption` | | `headlineMedium`（9） | `headline` |
| `titleMedium`（62） | `subtitle` | | `headlineLarge`（3） | `title` |
| `labelMedium`（36） | `footnote.medium()` | | `displayMedium`（2） | `display` |
| `bodyLarge`（33） | `body` | | `displaySmall`（1） | `title` |
| `titleSmall`（20） | `body.medium()` | | `displayLarge`（2） | `displayL` |
| `labelLarge`（15） | `body.medium()` | | | |

替换规则：`Text(..., style = MaterialTheme.typography.X)` → 先 `val typo = LocalHmpTypo.current` 再 `style = typo.<角色>`；`copy(...)` 链保留原样跟随。`.medium()` 是同尺寸 Medium 字重变体（label / 按钮文字语义）。

### 4.6 场景预设

场景角色 = 尺寸 × 字重 × 字距的组合预设（`HMPTypoTokens`）：

| 场景 | 组合 |
|---|---|
| 页面 / 播放器大标题 | `title` / `display` / `displayL` × Bold × `title` 字距 |
| 卡片标题 | `subtitle` / `headline` × Medium × `regular` |
| 列表项标题 / 播放条歌名 | `subtitle` × Medium × `regular` |
| 正文 | `body` × Regular × `regular` |
| 强调正文 / 数值 | `body` × Medium × `regular` |
| 次要信息 / 时间戳 | `footnote` × Regular × `regular`（时间 / 时长 / 进度叠 `.tabular`） |
| 标签 / 徽标 / 按钮 | `caption` / `footnote` × Medium × `regular` |
| 索引条 / 极小标注 | `micro` × Regular × `tight` |
| 大数字自适应降档 | `display` → `title` → `subtitle` 逐档降级（步距均匀 −6sp，原 26→20→16 实测等价） |

### 4.7 业务禁令与守门

- 业务代码禁止 `.sp` 字面量、`fontWeight` 字面量、`MaterialTheme.typography.*` 与 `m3ComponentTypography`；排版一律走 `LocalHmpTypo`。
- 守门为**排版 API 全量禁止**：出现 `MaterialTheme.typography` 或 `m3ComponentTypography` 即红，仅三个装配点豁免投影调用。
- 收编口径：**允许借机修正、不要求零视觉变化**——以「档位合理、整体协调」为准，九档覆盖大部分场景即可。

## 5. 尺寸

> **真源**：`MetricScale`（纯数据）/ `HMPMetricTokens`（Composable）/ `LayoutConstraints`（对象外） ｜ **入口**：`LocalHmpMetrics`（12 档 + 组件预设） ｜ **守门**：裸 `.dp` 全量禁止

### 5.1 为什么是一把尺

旧体系把 spacing / corner / icon / component 拆成四套独立数列、各自 1.25× / 1.5× 缩放：同一个 16dp 在 `spacing.md` 是 16/20/24、在 `icon.sm` 是 16/18/20——物理量在不同族里取值不同，视觉节奏无法对齐，改一处要同时权衡四张表。

**新体系只有一条离散刻度**：间距、圆角、图标、控件尺寸、描边、模糊半径全部从同一组 12 档取值，档值随窗口档查表。值唯一，语义放在组合层的组件预设上（§5.7）——这是「多尺寸 / 多组件 / 多层级」三个诉求的落点，而不是靠增加数列数量。

### 5.2 架构：两件套 + 对象外

```
MetricScale.kt        纯数据，零 Compose 依赖：12 档 × 3 窗口档整数数列
                      + scaleWidthClassFor() 查表（字体 typeSizesFor 共用此判据）
HMPMetricTokens.kt    Composable 组合层：LocalHmpMetrics（业务唯一尺寸入口）
                      + 组件预设 + Compact 静态基准
LayoutConstraints.kt  对象外常量（§5.5）：断点 / 窗口限宽 / 几何参数 / 壳层字号
```

三个装配点（`AppRoot` / `HearableMusicPlayerTheme` / `IosComposePilot`）provides `LocalHmpMetrics`，与 `LocalHmpTypo` 并列；业务写 `val metrics = LocalHmpMetrics.current` 后取档位。文件分界只取「是否依赖 Compose 运行时」，与 §4.2 同规则。

### 5.3 12 档 × 三窗口档

| 档名 | Compact | Medium | Expanded | 来源 |
|---|---:|---:|---:|---|
| `hair` | 2 | 2 | 2 | 结构性贴合值，跨档恒定 |
| `micro` | 4 | 5 | 6 | 旧 `spacing.xs`，完全一致 |
| `compact` | 8 | 10 | 12 | 旧 `spacing.sm`，完全一致 |
| `base` | 12 | 14 | 16 | 旧 `corner.sm`，完全一致 |
| `cozy` | 16 | 20 | 24 | 旧 `spacing.md`，完全一致 |
| `section` | 24 | 30 | 36 | 旧 `spacing.lg`，完全一致 |
| `block` | 32 | 40 | 48 | 旧 `spacing.xl`，完全一致 |
| `bar` | 48 | 60 | 72 | 旧 `component.xs`(48/56/64) 微调 |
| `row` | 80 | 96 | 112 | 新补：列表行 / 底部留白 |
| `panel` | 120 | 144 | 168 | 新补：小面板 / 中等控件 |
| `card` | 200 | 240 | 288 | 新补：封面卡 / 均衡器条 |
| `hero` | 320 | 384 | 448 | 新补：主视觉 / 大面板 |

**设计决策**：① 原生整数、不用缩放因子——整数可单测精确断言、可与设计稿直接对数、各档可单独微调；② 前 6 档继承旧 spacing / corner 的整数值，让存量迁移零视觉变化；③ 后 4 档为原本无 token 的大尺寸新补，按 1.4–1.5× 派生；④ `hair` 跨档恒定——结构性贴合尺寸放大会把紧凑控件撑散（同 §4.4 字距跨档不缩放）；⑤ 全表严格递增，除 `hair` 外 Compact < Medium < Expanded。

`1dp` 细线与 `0.5dp` 描边不进刻度：小于最小整数档，归 `StrokeMetrics`（`hairline` 0.5 / `thin` 1）结构性常量。

### 5.4 共用档位判据

`scaleWidthClassFor(width, height, isPhoneLandscape)` 由字体与尺寸共用（§2.2）：手机横屏强制 Compact、矮横窗（Expanded 宽 × Compact 高）降一档取 Medium、其余按宽度档直取。**跳变时机同步是硬要求**——只在拖拽桌面窗口时可见、难复现。

### 5.5 对象外（按用途划，不按数值划）

**治理对象** = 表达视觉节奏的 `.dp`：padding / spacedBy / Spacer / 圆角 / 图标 / 控件尺寸 / 模糊半径。

**对象外** = 三类，**117 处**（66 处布局约束进 `LayoutConstraints` + 47 处节奏值回刻度静态基准 + 8 处壳层字号）：

1. **几何 / 布局参数**：参与 `toPx()`、手势阈值、动画插值基准、`minOf(containerWidth × k, N.dp)`、`widthIn/heightIn(max = …)` 等——归并不改视觉节奏，改的是交互手感与布局约束；
2. **布局断点 / 窗口限宽**：600 / 840 / 480 / 900 / 1200dp——它们定义了「档」，不能自己也从档里取值（循环依赖）；
3. **顶层 `val` 常量与 data class 默认参数**：拿不到 CompositionLocal，且原本就不随窗口变化。

> **对象外不等于散着留**：布局约束与几何参数集中到 `LayoutConstraints.kt`，业务引用具名常量，守门只豁免这一个文件；**能落回 12 档的节奏值**直接引用 `HMPMetricTokens.Compact.<档>` 静态基准（不为每个历史值再造常量，避免常量表膨胀成第二套数列）。这样「业务代码零裸 `.dp`」成立，也不会出现「写 `200.dp` 自动合法」的后门。
> ⚠️ 曾有一版方案按「>48dp」切对象外，实测站不住：`Spacer(height(64.dp))`、`padding(bottom = 88.dp)`、`size(72.dp)`、`RoundedCornerShape(80.dp)` 都是节奏值，只是数值大。48 是统计拐点（91% 的量落在 ≤48dp），不是语义边界。

### 5.6 归并口径

沿用 D8-03 收编口径：**允许借机修正、不要求零视觉变化**，以「档位合理、整体协调」为准，散值就近归档（6→8、10→8、14→16、18→16、20→16/24、25→24、28→32、36→32、40→32/48、44/46→48…）。按 12 档 Compact 列实测：节奏值约 **1335 处**，平均偏差 **1.77dp**，偏差 >16dp 的 **29 处**（12 档是边际收益拐点，10 档 3.78dp / 14 档 1.84dp / 16 档 1.57dp）。

**不做布局级归并**：`200 → 160`（40dp）、`300 → 320` 这类会改变面板宽度与折行，须作为对象外或精确档保留。

### 5.7 组件预设

`HmpMetrics` 另提供 `capsulePreset` / `cardPreset` / `listItemPreset` / `dialogPreset` / `screenPreset`，每个把某组件的 `padding / gap / corner / icon` 绑成一组，值全部来自 12 档。**预设是语义别名，不是第二套数列**。

现状：5 个预设仅有单测引用、业务零消费——属预留能力；若下一笔仍无消费，评估删除而非长期空转。

### 5.8 业务禁令与守门

- 业务代码禁止裸 `.dp/.sp`；尺寸一律走 `LocalHmpMetrics` 的 12 档或组件预设，对象外走 `LayoutConstraints` 具名常量。
- 守门为**尺寸全量禁止**，扫 `shared-ui/src/**` + `desktop/app/src/**`，白名单 = 五类 token 文件 + `LayoutConstraints.kt`；`storybook/` 不在扫描范围（不在构建图，去留归 `docs/archive/7_x/7_3/domain/X.md` X-08）。
- **禁令与存量同阶段闭环**：不收完不开禁令，不留「禁令已开、存量未清」的提交。

## 6. 动效

> **真源**：`HmpMotion` ｜ **入口**：场景工厂 ｜ **守门**：裸时长 / 裸弹簧 / 无显式 spec 全量禁止

### 6.1 范围：通用纳体系，特化不收纳

- **纳入**：页面转场、组件进出场、展开 / 折叠、微反馈、状态切换——由 UI 结构决定、跨页面可复用的动效。
- **不收纳**（对象外）：`DynamicBackground`（氛围循环 20–120s、光斑呼吸）、`AdvancedLyrics` / `KaraokeLyricText`（走 `LyricsPresentationParams`，已有独立参数对象）、`FloatingLyricsOverlay`（悬浮窗吸附）、封面旋转（8000ms/圈，内容呈现）。
- **判定标准**：参数由**内容 / 视觉**决定的，不进体系；由 **UI 结构**决定的，必须进体系。对象外文件允许写数字，但须在文件头注释说明用途，并在守门豁免清单里逐文件列名。

### 6.2 时长四档

| 档 | 值 | 语义 | 吸收现有值（处数） |
|---|---:|---|---|
| `micro` | 200ms | 即时反馈：按压 / 高亮 / 开关 | 140(2)、150(3)、180(1)、200(7) |
| `standard` | 300ms | 通用默认：组件进出场 / 展开折叠 / 标签切换 / Toast | 220(4)、250(3)、300(25) |
| `emphasis` | 400ms | 强调过渡：页面导航 / 较大组件出现消失 | 400(1)、旧 `TRANSITION`(14) |
| `page` | 800ms | 页面级转场 | 800(2) |

- **归一化口径**：沿用「允许借机修正」。归并代价：13 处微反馈（140 / 150 / 180 → 200，最大 +60ms）、7 处（220 / 250 → 300，最大 +80ms）；主力 300 与 400 / 800 不动。
- **动效不随窗口档变化**：保持 `object` 静态常量，**不引入 CompositionLocal**——动效是感知节奏，不是响应式尺寸，与 §5 的架构刻意不同（同字重「跨档不变」的理由）。
- 四档为 2026-11-04 用户定的终局；弹簧后续可扩充，**时长档不再扩**（扩档等于退回自由值）。

### 6.3 缓动三档

`standard` Ease In Out `(0.4, 0, 0.2, 1)` 通用默认 · `enter` Ease Out `(0.2, 0, 0.1, 1)` 元素进入 · `exit` Ease In `(0.6, 0, 0.8, 1)` 元素退出。

`FastOutSlowInEasing` 与 `standard` **曲线完全相同**，已直接替换、零视觉变化。通用代码里**无自定义曲线**（`CubicBezierEasing` 只出现在 `DynamicBackground` 与 token 定义处）。

### 6.4 弹簧两档

| 档 | dampingRatio | stiffness | 吸收 |
|---|---:|---:|---|
| `snappy` | 0.7 | 400 | 0.7 / 400、0.6 / 400、`DampingRatioMediumBouncy` + `StiffnessLow`、coverflow 落定 0.8 / 420 |
| `crisp` | 0.6 | 600 | 0.6 / 600 |

两档是 2026-11-04 用户定的下限，**后续需要可扩充**；扩充时必须先说清语义场景，不能只为容纳某个历史值。

### 6.5 场景工厂与默认 spec

`HmpMotion` 提供泛型 spec 工厂，业务按场景取，不再手拼「时长 + 缓动」：

`page()` = `page` + `standard` · `emphasis()` = `emphasis` + `standard` · `standard()` = `standard` + `standard` · `enter()` = `standard` + `enter` · `exit()` = `micro` + `exit` · `micro()` = `micro` + `standard` · `snappySpring()` / `crispSpring()`。

**硬规则**：`AnimatedVisibility` / `AnimatedContent` / `Crossfade` **必须显式传 `animationSpec`**。收编前有 6 处走框架默认 spring，参数不可见、不可审计，2026-11-04 已全部补齐。

### 6.6 业务禁令与守门

- 通用代码禁止：`tween(数字)`、`durationMillis = 数字`、`spring(dampingRatio = …)`、`infiniteRepeatable(tween(数字…))`；动效一律走 `HmpMotion` 的四档 / 两档 / 场景预设。
- 结构性动画必须显式声明 spec——守门按「调用点起 10 行内出现 `HmpMotion` / `animationSpec` / `transitionSpec`」判定。
- 特化文件（§6.1 清单）加入守门豁免，**逐文件列名 + 理由**，不是整目录放行。

## 7. 毛玻璃

> **真源**：`HazeIntensity` ｜ **入口**：`LocalHazeRenderSettings` + `hazeEffect` ｜ **守门**：结构约束 + 判据④

### 7.1 使用原则

**底部悬浮层（导航胶囊、播放胶囊）必用 · 对话框必用 · 内容区卡片（列表、网格）不用 · 只在动态背景之上才有意义**（纯色底上玻璃无意义）；例外实况：`AddSongToPlaylistDialog` 无毛玻璃（D-8）。

### 7.2 实现与默认参数

**三端同一实现：Haze**（`dev.chrisbanes.haze` 1.7.2，声明在 `shared-ui` 的 `commonMain`）——**iOS 没有 `.ultraThinMaterial` 原生毛玻璃**（D-1 同源结论）。

默认参数（`HazeIntensity.kt:13-20`）：模式 `custom`、强度 **0.6f**、模糊 20dp、噪点 0.15、着色 alpha 0.22（`preset` 模式着色按下式推算：`0.08 + 强度×0.24`）。

### 7.3 强度分级

`preset` 档位：Ultra Thin `0.08` · Thin `0.25` · Regular `0.50` · Thick `0.75` · Ultra Thick `1.00`（几乎不透明，极少使用）。**默认档是 `custom` 的 0.6f，不是 Regular 0.50**（D-4）。用户可调强度（0–1）/ 模糊半径 / 噪点，均持久化在共享 `SettingsRepository`，三端同步。

### 7.4 守门

无机械守门（强度是 Float、不是字面量尺寸），靠两条结构约束：① 只经 `hazeEffect(state, hazeStyleForIntensity())`，不得自绘玻璃；② 强度 / 模糊 / 噪点取自 `LocalHazeRenderSettings`，不得就地写常量。

## 8. 应用层

### 8.1 移动端布局（Android / iOS）

**门面页（第 0 页）+ 3 个 Tab（画廊 / 列表 / 我的）**：

- **底栏 = 三颗药丸形毛玻璃胶囊**：伙伴（常驻，点按回门面）+ 导航（3 Tab）+ 播放（仅有歌时出现）；间距 `compact`(8)、左右外边距 `base`(12)、距底 `cozy`(16)、内容区 `bar`(48)、圆角 `block`(32)、`stroke.hairline`(0.5) 描边（`onBackground` 14%）、无阴影。
- **导航行为**：默认展开导航、折叠播放；播放开始后自动切换为展开播放、折叠导航；5 秒无操作恢复默认。
- **播放胶囊**：折叠态是圆形封面，点按进全屏播放器；展开态可左右滑切歌。底栏最大宽度 Compact 480 / Medium 640 / Expanded 不限（`LayoutConstraints`）。

### 8.2 桌面端布局

核心差异：窗口可自由缩放，布局响应式。断点三端共用（`WindowSizeClass.kt`，值来自 `LayoutConstraints`）：

| 宽度 | 分类 | 布局策略 |
|------|------|----------|
| < 600dp | Compact | 底部胶囊（同移动端），底栏限宽 480 |
| 600–840dp | Medium | 底部胶囊，底栏限宽 640 |
| ≥ 840dp | Expanded | **仍是底部胶囊**（全宽）；**没有 Expanded → NavigationRail 这条分支** |

**唯一的侧栏分支**：`useFusionSidebar = isLandscape && heightSizeClass == Compact`（仅手机横屏），渲染 80dp 融合侧栏（含首页在内的 4 项 + 迷你封面），Tab 页由侧栏接管、底栏只在子页面出现；桌面窗口纵向够高，永远走底部胶囊。桌面独有：

- **桌面壳**：自定义标题栏（无边框）、播放时转透明沉浸 + 系统托盘播放控制。
- **键盘快捷键**：Space 播放 / 暂停、← / → 切歌、L 心动模式、C 对话页、Esc 返回 / 关面板。**没有 Ctrl+F 搜索，也没有方向键调音量**。
- **响应式内容区**：封面网格列数（`config.list.columns`）、播放页宽屏左右分栏。
- **文件选择器**：`JFileChooser` 原生目录选择。

### 8.3 动态背景（特化动效）

播放时从封面取色生成氛围背景，三风格（`DynamicBackground.kt`），默认 `FLUID`：

- `FLUID` 流体极光：双层封面相位漂移 + 模糊 + 饱和度增强 1.6×，性能中等；
- `SPOTS` 沉浸光斑：3–7 个调色板光斑漂移呼吸，较低；
- `BLUR` 复古模糊：单封面放大模糊，最低。

三风格之上叠垂直渐变遮罩——暗色 `Black(0.3) → 透明 → 透明 → Black(0.6)`，亮色 `White(0.1)` 通体——确保前景文字可读；用户可在设置中切换风格。

> **与尺寸体系的关系**：本章是**特化动效**（不纳 §6 四档），但模糊半径是**尺寸**，2026-11-04 收编后走 §5 档位（原 40/25/18/50dp → `block`/`section`/`cozy`/`bar`）。氛围循环周期（20–120s）仍是特化字面量，由守门豁免。

### 8.4 组件设计模式

不是逐组件像素规范（细节在代码），而是跨平台设计规则。**尺寸以 §5 档位为准**，下列数字是视觉意图。

- **毛玻璃卡片**：药丸形（底栏 / 播放条，圆角 `block`）或圆角矩形（对话框）；**描边代替阴影**——玻璃胶囊 `onBackground` 14% / `stroke.hairline`，`HMPCard` 为 `outlineVariant` 50% / `stroke.thin`，玻璃层 `elevation = 0`；**不要阴影 + 毛玻璃同时用**（玻璃本身就是空间层次，再加阴影 = 过设计）。
- **按钮**：填充 = `primary` 底 + `onPrimary` 文字；轮廓 = `primary` 描边 + 透明底；文字按钮不加底、只变颜色。三端统一走 M3 组件与 ripple；桌面标题栏按钮另用 hover / press 透明度。
- **列表项**：序号 / 复选框槽 + 封面 + 标题（Bold）+ 副标题（Regular）+ 右侧辅助信息；当前播放与选中态用 `primary` 高亮；编辑模式用环形复选框（`base` 尺寸 / `LayoutConstraints.RingCheckboxStroke` 描边）并出现多选工具栏。
- **对话框**：毛玻璃底（`hazeEffect` + surface 半透明 + 圆角档位 + 无描边 + `elevation = 0`）；居中 + 全屏 `Black(0.5)` 遮罩，点遮罩关闭（扫描中可禁用），桌面按 Esc 关闭；内容过长在弹窗内滚动（列表区 `heightIn(max = LayoutConstraints.DialogMaxHeight)` 一类）。

### 8.5 平台差异速查

| | Android | iOS | Desktop |
|------|---------|-----|---------|
| UI 框架 | Compose（`shared-ui` 的 `AppRoot`） | 同左（SwiftUI 壳仅把 `AppRoot` 包成 `UIViewController`） | Compose（同左） |
| 字体 | HarmonyOS Sans | 同左 | 同左 |
| 毛玻璃 | Haze | 同左 | 同左 |
| 尺寸 / 字体 / 动效 token | 同一套 12 档 / 9 角色 / 4 档 | 同左 | 同左 |
| 导航 | 底部胶囊 | 底部胶囊 | 底部胶囊（手机横屏例外：80dp 侧栏） |
| 播放引擎 | Media3 ExoPlayer | AVFoundation | FFmpeg |
| 平台壳补充 | 状态栏 / 通知 | AVAudioSession、锁屏 MediaSession、Live Activity | 自定义标题栏 + 系统托盘 + 快捷键 |

**所有平台共享**：颜色 token、字体层级、尺寸刻度、动效档位、毛玻璃实现与设置、动态背景逻辑、专辑取色算法。

## 9. 落地与维护

### 9.1 守门脚本

`./gradlew-smart checkDesignTokens`（`scripts/check-design-tokens.py`），四项全量禁令：

| 门 | 判定 | 扫描范围 |
|---|---|---|
| 尺寸 | 非 0 裸 `.dp/.sp` | `shared-ui/src/**` + `desktop/app/src/**` |
| 色值 | `Color(0x…)`（`java.awt.Color` 排除） | 同上 |
| 排版 | `MaterialTheme.typography` / `m3ComponentTypography` | 同上（三装配点豁免） |
| 动效 | 裸时长 / 裸弹簧 / `Animated*` 无显式 spec | 同上（特化文件逐文件豁免） |

白名单文件：`ColorTokens` / `AgentStatusColors` / `HelloCardPalette` / `TypeScale` / `HMPTypoTokens` / `MetricScale` / `HMPMetricTokens` / `LayoutConstraints` / `HmpMotion`。**无基线**——任何新增与存量都必须为 0。

脚本实现：**一次遍历 + 7 条规则表 + 结构化 finding**，按文件分组报告。CLI：`--json`（CI / 编辑器）、`--quiet`（只输出统计）、`--path DIR`（限定范围调试）、`--self-test`（20 个内置用例，不读工作树）。**改规则前后先跑 `--self-test`**。

### 9.2 例外申报

- 逐行写 `// design-token-exception: <理由>`，理由必须说清「为什么不是设计值」；
- 当前存量例外 7 行：3 处字距、2 处 ARGB 位掩码、2 处加减号字形；
- 例外是审计对象，不是豁免权——同一理由反复出现，说明该补 token 或该改结构。

### 9.3 落地记录

- 字体：D8-03 五阶段（9 角色 × 3 窗口档、M3 typography 退场、`TypographyTokens.kt` 删除）；
- 尺寸：「D8-03 续」五阶段（12 档、对象外集中、守门升级、`HMPDimens.kt` 删除）；
- 色值：三-4 P0/P1（38 → 0）+ 壳层色收编；
- 动效：D8-07 五阶段（4 档 + 3 缓动 + 2 弹簧、`AnimationTokens.kt` 删除）。

逐阶段验证数字、修过的缺陷、如实记，全部在 `docs/archive/7_x/7_3/domain/D8.md`。

### 9.4 维护规则

- **新增 token 的门槛**：必须是「现在无法用现有档位表达，且能说清语义场景」；只为容纳某个历史值而加档，等于把体系退回自由值。
- **扩档而不是改档**：档位值可在跨档时微调，但不要为单个页面调档；页面适配走布局，不走 token。
- **改档要跑全套**：`compileAll` + `testUi` + iOS 两目标 + `checkDesignTokens`，桌面三档走查（拖拽跨 600 / 840dp）。
- **文档与代码冲突时以代码为准**，并回写本文。

## 10. 已知偏差

> **时效**：本表是 2026-09-28 的审计记录，保留作历史证据、不重写结论与行号。其中：R49 的 `.dp` / 字号 / 色值计数已被 D8-03（排版）与「尺寸五阶段」、D8-07（动效）全量收编；**D-6 引用的 `AnimationTokens.kt` 已随该文件删除失效**（动效改由 `HmpMotion` 承担），D-1 / D-5 引用的 `TypographyTokens.kt` 亦已删除。现状以 §2.1 与 §9.1 为准。

| 编号 | 与代码不符处 | 证据（file:line） | 归属 |
|---|---|---|---|
| D-1 | **iOS 不是 SwiftUI**：`ContentView` 只把共享 Compose `AppRoot` 包成 `UIViewController`，无 SwiftUI 页面 / SF Pro / `.ultraThinMaterial` | `ios/HMP/HMP/ContentView.swift:10,15-20`；`shared-ui/.../TypographyTokens.kt:21-28`；`shared-ui/.../common/util/HazeIntensity.kt` | R49 |
| D-2 | **底部 Tab 是 3 个不是 4 个**（另有伙伴胶囊与播放胶囊；门面为第 0 页） | `shared-ui/.../agent/shell/BottomFusionBar.kt:118-125`；`.../MainShell.kt:42-58` | R49 |
| D-3 | **布局判据是 `isLandscape && heightSizeClass == Compact`**，不是「Expanded → NavigationRail」；80dp 侧栏只在手机横屏 | `shared-ui/.../ui/common/layout/WindowSizeClass.kt:49`；`.../agent/shell/FusionSidebar.kt:64-69,90`；`AppRoot.kt:521-555` | R49 |
| D-4 | **毛玻璃默认强度 0.6f**（不是 Regular 0.50）；0.50 只是 preset 档位值，默认模式是 `custom` | `shared-ui/.../ui/common/util/HazeIntensity.kt:13,20,26` | R49 |
| D-5 | **字体与字重**：三端同 HarmonyOS Sans（非 iOS SF Pro / Desktop 系统默认）；`titleMedium` / `titleSmall` 是 Medium 而非 Bold | `shared-ui/.../TypographyTokens.kt:21-28,82-95`；`composeResources/font/*.ttf`（6 个字重） | R49 |
| D-6 | 亮 / 暗的 `surfaceVariant` / `onSurface` / `onSurfaceVariant` / 分割线在 app token 中**未定义**（走 M3 默认）；动效 token 无调用点（按钮反馈实为 M3 ripple） | `.../design/colors/ColorTokens.kt:5-29`、`.../design/theme/ThemeManager.kt:13-39` 对照 `storybook/.../StorybookTheme.kt:141-200` | R49 |
| D-7 | 桌面快捷键与原文不符：无 Ctrl+F 搜索、无方向键调音量；实为 Space / ← / → / L / C / Esc | `shared-ui/.../player/pages/PlayerScreen.kt:183-190`；`AppRoot.kt:337-350` | 文档订正 |
| D-8 | 「对话框必用毛玻璃」有例外：`AddSongToPlaylistDialog` 无 `hazeEffect`（7/8 个有） | `shared-ui/.../common/dialogs/` 下 `hazeEffect` 命中 7 个文件 | R49 |
| **R49** | **设计 token 曾长期「自愿制」**：硬编码 hex 53 处 / 13 文件、`fontSize` 43 处 / 9 文件、`RoundedCornerShape(N.dp)` 148 处、动效毫秒 56 处、`.dp` 1444 处 / 91 文件 vs `dimens.*` 仅 209 处 / 24 文件、336 个 `@Composable` 只有 81 个带 `modifier: Modifier`。2026-09-28 复测：hex 50/14、`fontSize` 63/11、`RoundedCornerShape` 149、`.dp` 1454/89 | 审查口径 `docs/archive/7_x/7_3/review-7.3-architecture.md:95`、`TODO.md:115` | R49 |

## 11. 修订记录

- 2026-09-28：整篇重写（279 → 199 行），删散文、补「已知偏差」「修订记录」。
- 2026-09-28：就地订正 8 处与代码不符（D-1…D-8）。
- 2026-11-04：§字体重写为 9 角色 × 3 窗口档（D8-03），M3 typography 退场、`TypographyTokens.kt` 删除。
- 2026-11-04：新增尺寸体系（D8-03 续），12 档 × 三窗口档、对象外集中 `LayoutConstraints`、守门升级为全量禁止。
- 2026-11-04：守门扩到色值与动效，新增 `ColorTokens.Shell`，移除基线机制与 `design-token-baseline.json`；清理零消费语义层（`EffectMetrics` / `StrokeMetrics.bold`）。
- 2026-11-04：新增动效体系（D8-07），4 档 + 3 缓动 + 2 弹簧 + 场景工厂，`AnimationTokens.kt` 与 `AnimationDirection` 删除。
- 2026-11-04：**整篇重排**——章节按「总纲 → 五类 token → 应用层 → 落地 → 附录」重组，每类 token 统一为「真源 / 架构 / 档位 / 规则 / 禁令」五段式；修订记录压成摘要，施工细节见 `docs/archive/7_x/7_3/domain/D8.md`。**章节编号已变更**，旧引用（如 `§6.1` 布局、`§10` 尺寸）按新结构重定位。
