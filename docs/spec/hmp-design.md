# HMP Design System —— 一套 Compose UI 与一套设计 token 的长相契约

> **约束**：三端只有一套 UI（`shared-ui` 的 `commonMain` → `AppRoot`）与一套 token（颜色 / 字体 / 毛玻璃 / 动效 / 尺寸）；改外观一律改 `shared-ui`，平台壳不得自绘第二套视觉。
> **适用范围**：`shared-ui` 的 `commonMain` 与三端 actual、Android / iOS / Desktop 壳层的视觉改动、新增组件与页面 ｜ **最后核对**：2026-09-28（对 `shared-ui` 源码逐节 grep 复核：色值 / 字号 / 毛玻璃默认值 / 断点 / 动效 token；未跑 gradle）
> **判据**：① 新增代码不得引入新的硬编码色或字号（白名单 = §2 的 `ColorTokens` 色值与 §3 的 `TypographyTokens` 层级；其余 token 的现状见「已知偏差」R49）② `grep -c "BottomTabItem(" shared-ui/.../agent/shell/BottomFusionBar.kt` = **3**，且 `tabIndexForPage(page) = page - 1`（门面页无 Tab）③ 布局分支只经 `AppWindowSizeInfo`（`useFusionSidebar = isLandscape && heightSizeClass == Compact`），不得另设断点 ④ 毛玻璃只经 `hazeEffect(state, hazeStyleForIntensity())`，强度 / 模糊 / 噪点取自 `LocalHazeRenderSettings`

## 1. 设计原则

HMP 是纯本地、跨平台音乐播放器：不做云同步、不设账号、不追踪。

- **纯粹**：UI 是音乐的容器，克制装饰，让内容与封面说话。
- **私密**：暗色主色由激进的红换为柔和浅蓝——夜间听歌不该被红刺到。
- **用心但安静**：毛玻璃是空间层次的表达（玻璃漂浮在动态背景之上），不是炫技。
- **明暗同等对待**：两套色板各自设计，禁止简单翻转颜色。

## 2. 颜色系统

### 2.1 品牌色

标识是**红蓝冷暖对比**：红 = 情感温度，蓝 = 理性克制（`ColorTokens.kt:7-8`）。

| 令牌 | 色值 |
|------|------|
| `HDRed` | `#C92C2C` |
| `HDBlue` | `#002FA7` |

### 2.2 亮色主题

| 令牌 | 色值 | 用途 |
|------|------|------|
| `primary` / `onPrimary` / `secondary` | `#C92C2C` / `#FFFFFF` / `#002FA7` | 按钮、选中态、链接；标签与辅助强调 |
| `primaryContainer` / `onPrimaryContainer` | `#1976D2` / `#B00020` | 主色容器（蓝，与红形成冷暖对比） |
| `background` / `surface` | `#FFFFFF` | 页面底色 / 卡片表面 |
| `surfaceVariant`、`onSurface`、`onSurfaceVariant`、分割线 | **未定义** | 走 M3 默认；原文 `#E1E2EC` / `#1E1E1E` / `#616161` / `#E0E0E0` 无实现（D-6） |

### 2.3 暗色主题

**关键决策**：暗色主色从品牌红切换为 `#90CAF9`（柔和浅蓝），红退到 `primaryContainer`（`#CF6679` 粉红）——大面积红在暗底刺眼。

| 令牌 | 色值 | 用途 |
|------|------|------|
| `primary` / `onPrimary` / `secondary` | `#90CAF9` / `#000000` / `#F48FB1` | 按钮、选中态、链接（暗色下主色上为黑字）；标签与辅助强调 |
| `primaryContainer` / `onPrimaryContainer` | `#CF6679` / `#1976D2` | 主色容器 |
| `background` / `surface` | `#121212` | 页面底色 / 卡片表面 |
| `surfaceVariant`、`onSurface`、`onSurfaceVariant`、分割线 | **未定义** | 走 M3 默认；原文 `#44464F` / `#F2F2F2` / `#ADADAD` / `#333333` 无实现（D-6） |

### 2.4 主题切换

三模式：跟随系统（默认）/ 强制亮色 / 强制暗色，持久化在共享 `SettingsRepository.themeMode`（`SettingsRepository.kt:18`，UI 见 `CustomScreen.kt:425-455`）。**播放中整体切动态主题，暂停回落预置主题**（`AppRoot.kt:191-195`）。

### 2.5 动态主题（专辑取色）

播放时从当前封面取色生成动态色板（`ThemeViewModel.kt:110-241`，3D 直方图峰值检测 + WCAG 对比度）：① 主色 = 饱和度最高的峰，且与背景对比度 ≥ 3.0:1（不足则 `ensureContrast` 推进到达标）② 背景色 = `明度 + 饱和度×0.3` 最小的峰 ③ 强调色 = 色相距主色最远的峰，对比度 < 2.0 时退回主色。

**何时使用**：播放器页、迷你播放条、动态氛围背景；预设与动态主题共存，用户可切回预设。

## 3. 字体系统

### 3.1 平台字体

**三端同一字体：HarmonyOS Sans**（`TypographyTokens.kt:21-28`），6 个字重 Thin / Light / Regular / Medium / Bold / Black 随 `shared-ui/src/commonMain/composeResources/font/` 打包。**iOS 不是 SF Pro、Desktop 不是系统默认**（原文三行平台表作废，见 D-1）。

### 3.2 排版层级

15 级 Material3 缩放表，三端统一（字号 / 字重实测自 `TypographyTokens.kt:33-137`，行高同处）：

| 层级 | 字号 / 字重 | 用途 |
|------|-----------|------|
| `displayLarge` / `displayMedium` / `displaySmall` / `headlineLarge` | 40 / 24 / 20 / 24，Bold | 播放器歌曲标题 / 页面大标题 |
| `headlineMedium` / `headlineSmall` | 18 / 14，Bold | 区块标题（`headlineMedium`） |
| `titleLarge` / `titleMedium` / `titleSmall` | 18 / 16 / 14，Bold / Medium / Medium | 卡片标题与播放条歌名（`titleMedium`）、列表项标题与导航标签（`titleSmall`） |
| `bodyLarge` / `bodyMedium` / `bodySmall` | 16 / 14 / 12，Normal | 正文 / 辅助说明 / 次要信息与时间戳 |
| `labelLarge` / `labelMedium` / `labelSmall` | 14 / 12 / 11，Medium | 标签与按钮文字 / 徽标 |

字重规则：`display*` / `headline*` / `titleLarge` 为 Bold；`titleMedium` / `titleSmall` 与 `label*` 为 Medium；正文族 Normal。原文「标题用 Bold」对 `titleMedium` / `titleSmall` 不成立（D-5）。

## 4. 毛玻璃效果

### 4.1 使用原则

**底部悬浮层（导航胶囊、播放胶囊）必用 · 对话框必用 · 内容区卡片（列表、网格）不用 · 只在动态背景之上才有意义**（纯色底上玻璃无意义）；例外实况：`AddSongToPlaylistDialog` 无毛玻璃（D-8）。

### 4.2 三平台实现

**三端同一实现：Haze**（`dev.chrisbanes.haze` 1.7.2，声明在 `shared-ui` 的 `commonMain`，iOS 构件齐备）——**iOS 不再有 `.ultraThinMaterial` 原生毛玻璃**（原文 §4.2 / §9 的 iOS 行作废）。
默认参数（`HazeIntensity.kt:13-20`）：模式 `custom`、强度 **0.6f**、模糊 20dp、噪点 0.15、着色 alpha 0.22（`preset` 模式着色按下式推算：`0.08 + 强度×0.24`）。

### 4.3 强度分级

`preset` 档位：Ultra Thin `0.08` · Thin `0.25` · Regular `0.50` · Thick `0.75` · Ultra Thick `1.00`（几乎不透明，极少使用）。**默认档是 `custom` 的 0.6f，不是 Regular 0.50**（`0.50` 只是 preset 档位值，D-4）。用户可调强度（0–1）/ 模糊半径 / 噪点，均持久化在共享 `SettingsRepository`，三端同步。

## 5. 动效

### 5.1 持续时间

微交互 200ms（按钮、开关、选中切换）· 过渡 400ms（页面导航、组件出现 / 消失）· 复杂编排 650ms（多元素协同）· 氛围背景 3000ms（极光旋转、光斑漂移）（`AnimationTokens.kt:9-12`）。实际引用只有 `TRANSITION`（14 处，均在 `AppRoot`），其余三个无调用点、氛围背景实为 20000 / 30000ms 字面量（D-6）。

### 5.2 缓动

Ease In Out `(0.4, 0, 0.2, 1)` 通用过渡（默认）· Ease Out `(0.2, 0, 0.1, 1)` 元素进入 · Ease In `(0.6, 0, 0.8, 1)` 元素退出（`AnimationTokens.kt:15-17`）。

### 5.3 弹簧

Medium 胶囊展开 / 折叠与导航切换（弹而不跳）· Bouncy 封面弹出、列表项滑出（活泼）· Gentle 滚动、拖拽回弹（柔和不抢戏）。三个 token 目前无调用点，代码多就地写 `spring(dampingRatio, stiffness)`（底栏胶囊切换为 `0.7 / 400`）。

### 5.4 导航过渡

页面间统一：缩放 (0.95→1) + 淡入，400ms，Ease In Out；退出反向（`scaleOut → 1.05`）（`AppRoot.kt:406-481`）。

### 5.5 封面旋转

播放中封面持续旋转，每圈 8000ms、线性匀速；暂停立即停止（不减速）（`BottomFusionBar.kt:472-483`、`FusionSidebar.kt:131-142`）。

## 6. 三平台布局

### 6.1 Android & iOS（移动端）

移动端布局一致——**门面页（第 0 页）+ 3 个 Tab（画廊 / 列表 / 我的）**：

- **底栏 = 三颗药丸形毛玻璃胶囊**：伙伴（常驻，点按回门面）+ 导航（3 Tab）+ 播放（仅有歌时出现）；间距 8dp、左右外边距 12dp、距底 20dp，内容区恒定 48dp、圆角 36dp、0.5dp 描边（`onBackground` 14%）、无阴影（`BottomFusionBar.kt:118-125,200-207,231-245`）
- **导航行为**：默认展开导航、折叠播放；播放开始后自动切换为展开播放、折叠导航；5 秒无操作恢复默认（`BottomFusionBar.kt:161-186`）
- **播放胶囊**：折叠态是圆形封面，点按进全屏播放器；展开态可左右滑切歌。底栏最大宽度 Compact 480dp / Medium 640dp / Expanded 不限（`AppRoot.kt:610-614`）

### 6.2 Desktop（桌面端）

核心差异：窗口可自由缩放，布局响应式。断点三端共用（`WindowSizeClass.kt:72-87`）：

| 宽度 | 分类 | 布局策略 |
|------|------|----------|
| < 600dp | Compact | 底部胶囊（同移动端），底栏限宽 480dp |
| 600–840dp | Medium | 底部胶囊，底栏限宽 640dp |
| ≥ 840dp | Expanded | **仍是底部胶囊**（全宽）；**没有 Expanded → NavigationRail 这条分支** |

**唯一的侧栏分支**：`useFusionSidebar = isLandscape && heightSizeClass == Compact`（仅手机横屏），渲染 80dp 融合侧栏（含首页在内的 4 项 + 迷你封面），Tab 页由侧栏接管、底栏只在子页面出现（`WindowSizeClass.kt:49`、`AppRoot.kt:521-555`）；桌面窗口纵向够高，永远走底部胶囊。桌面端独有：

- **桌面壳**：自定义标题栏 40dp（无边框），播放时转透明沉浸（`CustomTitleBar.kt:39,49-62`）+ 系统托盘播放控制（`SystemTrayManager.kt`）
- **键盘快捷键**：Space 播放 / 暂停、← / → 切歌、L 心动模式（`PlayerScreen.kt:183-190`）；C 对话页、Esc 返回 / 关面板（`AppRoot.kt:337-350`）。**没有 Ctrl+F 搜索，也没有方向键调音量**
- **响应式内容区**：封面网格列数（`config.list.columns`）、播放页宽屏左右分栏（`PlayContent.kt:200-237`）
- **文件选择器**：`JFileChooser` 原生目录选择，用于添加音乐库目录（`DesktopPlatformServices.kt:73-97`）

## 7. 动态背景

播放时从封面取色生成氛围背景，三风格（`DynamicBackground.kt:48-102`），默认 `FLUID`：`FLUID` 流体极光（双层封面相位漂移 + 40 / 25dp 模糊 + 饱和度增强 1.6×，性能中等）· `SPOTS` 沉浸光斑（3–7 个调色板光斑漂移呼吸，18dp 模糊，较低）· `BLUR` 复古模糊（单封面放大 50dp 模糊，最低）。三风格之上叠垂直渐变遮罩——暗色 `Black(0.3) → 透明 → 透明 → Black(0.6)`，亮色 `White(0.1)` 通体——确保前景文字可读（`DynamicBackground.kt:105-128`）；用户可在设置中切换风格。

## 8. 组件设计模式

以下不是逐组件像素规范（细节在代码），而是跨平台设计规则。

### 8.1 毛玻璃卡片

- **形状**：药丸形（底栏 / 播放条，圆角 36dp）或圆角矩形（对话框 28dp）
- **描边代替阴影**：玻璃胶囊 `onBackground` 14% / 0.5dp，`HMPCard` 为 `outlineVariant` 50% / 1dp，玻璃层 `elevation = 0.dp`——**不要阴影 + 毛玻璃同时用**（玻璃本身就是空间层次，再加阴影 = 过设计）

### 8.2 按钮

- 填充按钮 = `primary` 底 + `onPrimary` 文字（亮色下白字、暗色下黑字）；轮廓按钮 = `primary` 描边 + 透明底；文字按钮不加底、只变颜色
- 三端统一走 M3 组件与 ripple；桌面标题栏按钮另用 hover / press 透明度（`CustomTitleBar.kt:177-185`）

### 8.3 列表项

- 序号 / 复选框槽 + 封面 + 标题（Bold）+ 副标题（Regular）+ 右侧辅助信息；当前播放与选中态用 `primary` 高亮
- 编辑模式用环形复选框（`RingCheckbox` 14dp / 1.5dp）并出现多选工具栏

### 8.4 对话框

- 毛玻璃底（`hazeEffect` + surface 半透明 + 圆角 28dp + 无描边 + `elevation = 0.dp`）
- 居中显示 + 全屏 `Black(0.5)` 遮罩，点遮罩关闭（扫描中可禁用），桌面按 Esc 关闭；内容过长在弹窗内滚动（列表区 `heightIn(max = 400.dp)` 一类）

## 9. 平台差异速查

| | Android | iOS | Desktop |
|------|---------|-----|---------|
| UI 框架 | Compose（`shared-ui` 的 `AppRoot`） | 同左（SwiftUI 壳仅把 `AppRoot` 包成 `UIViewController`） | Compose（同左） |
| 字体 | HarmonyOS Sans | 同左 | 同左 |
| 毛玻璃 | Haze | 同左 | 同左 |
| 导航 | 底部胶囊 | 底部胶囊 | 底部胶囊（手机横屏例外：80dp 侧栏） |
| 播放引擎 | Media3 ExoPlayer | AVFoundation | FFmpeg |
| 平台壳补充 | 状态栏 / 通知 | AVAudioSession、锁屏 MediaSession、Live Activity | 自定义标题栏 + 系统托盘 + 快捷键 |

**所有平台共享**：颜色 token、字体层级、动效时长与缓动、毛玻璃实现与设置、动态背景逻辑、专辑取色算法。

## 已知偏差

| 编号 | 与代码不符处 | 证据（file:line） | 归属 |
|---|---|---|---|
| D-1 | **iOS 不是 SwiftUI**：`ContentView` 只把共享 Compose `AppRoot` 包成 `UIViewController`，无 SwiftUI 页面 / SF Pro / `.ultraThinMaterial`；iOS 只剩平台壳与桥接 | `ios/HMP/HMP/ContentView.swift:10,15-20`；`shared-ui/.../TypographyTokens.kt:21-28`；`shared-ui/.../common/util/HazeIntensity.kt`（haze 在 commonMain） | R49 |
| D-2 | **底部 Tab 是 3 个不是 4 个**（另有伙伴胶囊与播放胶囊；门面为第 0 页，`tabIndexForPage = page - 1`） | `shared-ui/.../agent/shell/BottomFusionBar.kt:118-125`；`.../MainShell.kt:42-58` | R49 |
| D-3 | **布局判据是 `isLandscape && heightSizeClass == Compact`**，不是「Expanded → NavigationRail」；Expanded 仍是全宽底部胶囊，80dp 侧栏只在手机横屏且含首页 4 项。另：旧稿写的 `ui/platform/WindowSizeClass.kt` 实际在 `ui/common/layout/` | `shared-ui/.../ui/common/layout/WindowSizeClass.kt:49`；`.../agent/shell/FusionSidebar.kt:64-69,90`；`AppRoot.kt:521-555` | R49 |
| D-4 | **毛玻璃默认强度 0.6f**（原写 Regular 0.50 为默认）；0.50 只是 preset 档位值，默认模式是 `custom`。文件实际在 `ui/common/util/`，非 `ui/platform/` | `shared-ui/.../ui/common/util/HazeIntensity.kt:13,20,26` | R49 |
| D-5 | **字体与字重**：三端同 HarmonyOS Sans（非 iOS SF Pro / Desktop 系统默认）；`titleMedium` / `titleSmall` 是 Medium 而非 Bold | `shared-ui/.../TypographyTokens.kt:21-28,82-95`；`shared-ui/src/commonMain/composeResources/font/*.ttf`（6 个字重） | R49 |
| D-6 | 亮 / 暗的 `surfaceVariant` / `onSurface` / `onSurfaceVariant` / 分割线在 app token 中**未定义**（走 M3 默认），原表 `#E1E2EC` / `#44464F` / `#1E1E1E` / `#616161` / `#E0E0E0` / `#F2F2F2` / `#ADADAD` / `#333333` 只存在于 storybook 独立色板；动效 `MICRO_INTERACTION` / `COMPLEX` / `BACKGROUND` 与三个弹簧 token 无调用点（按钮反馈实为 M3 ripple） | `shared-ui/.../design/colors/ColorTokens.kt:5-29`、`.../design/theme/ThemeManager.kt:13-39` 对照 `storybook/.../theme/StorybookTheme.kt:141-200`；`.../design/animation/AnimationTokens.kt:9-33` | R49 |
| D-7 | 桌面快捷键与原文不符：无 Ctrl+F 搜索、无方向键调音量；实为 Space / ← / → / L / C / Esc | `shared-ui/.../player/pages/PlayerScreen.kt:183-190`；`shared-ui/.../AppRoot.kt:337-350`；全仓 `grep -rn "isCtrlPressed\|Key.DirectionUp"` 0 命中 | 文档订正（无 R 编号） |
| D-8 | §4.1「对话框必用毛玻璃」有例外：`AddSongToPlaylistDialog` 无 `hazeEffect`（7/8 个对话框有） | `shared-ui/.../common/dialogs/` 下 `hazeEffect` 命中 7 个文件，`AddSongToPlaylistDialog.kt` 不在其中 | R49 |
| **R49** | **设计 token 是"自愿制"**（本文只登记、不发明强制规则；除判据①外无机械门禁）：硬编码 hex 色 **53 处 / 13 文件**（扣 token 文件 37/12）、`fontSize` 字面量 **43 处 / 9 文件**、`RoundedCornerShape(N.dp)` **148 处**、动效毫秒 **56 处**、`.dp` 字面量 **1444 处 / 91 文件** vs `dimens.*` 仅 **209 处 / 24 文件**、336 个 `@Composable` 只有 81 个带 `modifier: Modifier`（24%）。本人复测 `shared-ui`（2026-09-28 复核复测）：hex 50/14、`fontSize` 63/11、`RoundedCornerShape` 149、`.dp` 1454/89、`modifier: Modifier` **81** —— 量级一致，差额来自正则口径 | 审查口径 `docs/7_3/review-7.3-architecture.md:95`、`TODO.md:115`；复测见 `shared-ui/src/**` | R49 |

## 修订记录

- 2026-09-28：按三份规范统一格式**整篇重写**（279 → 199 行）。删去散文式背景介绍、无判据的说明段与 §6 / §9 的重复叙述；§1–§9 编号与标题主体未动，新增文末「已知偏差」「修订记录」。原文所有可执行事实（品牌色、明暗色值、15 级字号、时长 / 缓动 / 弹簧、三风格背景、组件模式、平台速查）逐条保留，色值与字号改为代码实测值。
- 2026-09-28：**就地订正 8 处与代码不符**（D-1…D-8：iOS=Compose、3 Tab、布局判据、毛玻璃默认 0.6f、字体与字重、未实现色 token、桌面快捷键、对话框毛玻璃例外）。沿革：v5.10（2026-05-21）为 SwiftUI + 各平台原生实现年代的稿件，v7.1 起三端统一 Compose UI，本稿按实际代码校准。
