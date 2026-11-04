# 设计 token 存量审计（2026-11-03）

> 口径：`shared-ui/src/commonMain/kotlin`；排除 4 个 token 定义文件与带 `design-token-exception` 的行；
> `.dp/.sp` 中 `0.dp` 不计。数据与 `design-token-baseline.json` 同源。

## 1. 总量

| 类型 | 数量 | 涉及文件 | 备注 |
|---|---:|---:|---|
| 硬编码色值 `Color(0x…)` | **0** | 0 | 审计时 38/12；P0 收 15 处（含 2 处误报）、P1 收 23 处，余下全在 token 文件内 |
| 字号 `.sp` | **58** | 10 | 仅 20 处能对上 `TypeSizes`（10/12/16/22/32）。曾试收编为 `FixedTypeScale` 裸阶梯，2026-11-03 回退——排版体系另行设计 |
| 尺寸 `.dp` | **1380** | 88 | 值高度集中在 4/8/12/16/24 |
| 尺寸合计（dp+sp，即基线 `dimension`） | **1438** | 88 | 与 R49 的复测量级一致 |

## 2. 硬编码色值（P0+P1 后 0）

| 分组 | 文件 | 数量 | 内容 |
|---|---|---:|---|
| 语义状态色 | `agent/AgentVisuals.kt` | 7 | Idle / Building / Running / Paused / Completed / Error / Warn |
| 卡片主题色 | `agent/cards/HelloCard*.kt` | 16 | Anchor 2、Anniversary 4、Discover 2、Greeting 2、Narrative 2、RadioStatus 2、SingleTrack 2 |

**P0 已收口（2026-11-03）**
- `ThemeViewModel` 2 处 ARGB 位掩码加 `design-token-exception`（不是设计色）。
- `ListScreen` 4 个分类色进 `ColorTokens`：`HDBlue` 复用原值，新增 `HDGreen` / `HDPurple` / `HDOrange`。
- `DynamicBackground` 6 处改 `ColorTokens.DarkBackground` / `LightBackground`，新增 `LightBackgroundAlt`（`#F5F5F5`）。
- `PaletteColors` 3 个兜底色改 `ColorTokens.PaletteFallback`。
- 色值总数 38 → **23**，视觉值逐一对齐、无变化；基线已同步。

**P1 已收口（2026-11-03）**
- `AgentVisuals` 的 7 个状态色 → 整体迁出为 `common/design/colors/AgentStatusColors.kt`（`object` 从页面目录搬到 token 目录，7 个色值逐一对齐、无变化）。
- 7 张 `HelloCard` 的 16 个渐变/主题色 → 新建 `common/design/colors/HelloCardPalette.kt`，按卡片家族分组命名（`Anniversary.FirstPlay`、`Discover.Start/End`、`CoverFallback.Primary` 等），色值与原字面量逐一相同。
- 色值总数 23 → **0**；两个新文件已进闸门 `TOKEN_FILES` 白名单，基线文件数随之 91 → 88（3 个文件因不再含硬编码而退出基线）。
- 上表那 23 处即本批收口对象，保留作审计留痕。

## 3. 硬编码字号（58）

| 文件 | 数量 | 典型值 |
|---|---:|---|
| `HelloCardAnniversary.kt` | 12 | 11/12/14/16/18/20/22/26sp，含 lineHeight |
| `HelloCardRadioStatus.kt` | 11 | 11/12/13/14/15/18/20sp |
| `HelloCardSingleTrack.kt` | 7 | 12/13/17/22sp |
| `HelloCardDiscover.kt` | 6 | 11/12/14/16sp |
| `HelloCardAnchor.kt` | 5 | 8/10/11/13sp |
| `HelloCardNarrative.kt` | 5 | 10/11/12/15/25sp |
| `LyricsSettingsPage.kt` | 4 | 13sp 标签、20sp 加减号 |
| `HelloCardGreeting.kt` | 3 | 15/16/27sp |
| `AgentMonitorScreen.kt` | 3 | `letterSpacing = 0.4/0.6sp`（不是字号） |
| `MusicListIndexStrip.kt` | 2 | 9/10sp |

**判断**
- 49/58 集中在 `HelloCard*`，是卡片自有排版；直接用 `MaterialTheme.typography` 会改变视觉，应先定 `TypographyTokens` 的卡片档位，或明确标例外。
- `LyricsSettingsPage` 的 4 处最容易收：13sp → `dimens.type.sm`，20sp → `dimens.type.lg`（或 M3 `titleLarge`）。
- `AgentMonitorScreen` 的 3 处是字距，不是字号，建议标例外而不是硬套 token。

## 4. 硬编码尺寸（1380 dp）

值频次 Top 15：

| 值 | 次数 | 可对应 token |
|---:|---:|---|
| 8dp | 215 | `spacing.sm` |
| 16dp | 211 | `spacing.md` / `icon.sm` |
| 12dp | 128 | `corner.sm` |
| 24dp | 121 | `spacing.lg` / `icon.md` |
| 4dp | 119 | `spacing.xs` / `corner.xs` |
| 20dp | 56 | `corner.md` |
| 32dp | 54 | `spacing.xl` |
| 6dp | 51 | 无 |
| 10dp | 48 | 无 |
| 2dp | 45 | 无 |
| 48dp | 35 | `component.xs` |
| 25dp | 24 | 无 |
| 18dp | 21 | 无 |
| 1dp | 20 | 无（细线） |
| 28dp | 19 | `corner.lg` / `icon.lg` |

**Top 文件**

| 文件 | dp | sp | color | 现有 token 引用行 |
|---|---:|---:|---:|---:|
| `agent/config/AgentConfigScreen.kt` | 100 | 0 | 0 | 8 |
| `player/pages/PlayContent.kt` | 78 | 0 | 0 | 1 |
| `library/pages/SongDetailScreen.kt` | 56 | 0 | 0 | 10 |
| `settings/pages/AudioEffectsScreen.kt` | 55 | 0 | 0 | 0 |
| `library/.../MusicListItem.kt` | 48 | 0 | 0 | 0 |
| `settings/pages/LibrarySettingsScreen.kt` | 46 | 0 | 0 | 0 |
| `agent/monitor/AgentMonitorScreen.kt` | 38 | 3 | 0 | 4 |
| `common/pages/IntroScreen.kt` | 40 | 0 | 0 | 0 |
| `settings/pages/UserUsageDataScreen.kt` | 38 | 0 | 0 | 61 |
| `agent/config/AIScreen.kt` | 36 | 0 | 0 | 7 |
| `agent/chat/CompanionBubble.kt` | 35 | 0 | 0 | 1 |
| `agent/shell/RadioConsole.kt` | 33 | 0 | 0 | 0 |
| `library/pages/EditMusicTagsScreen.kt` | 32 | 0 | 0 | 0 |

**关键约束：不能按数值机械替换。**
`HMPDimens` 的三档值是随窗口宽度变化的（Compact 16dp / Medium 20dp / Expanded 24dp）。
把 `16.dp` 换成 `dimens.spacing.md`，在平板/桌面上会变成 20/24dp——这是**视觉与布局变更**，不是等价重构。
所以 `.dp` 只能按页面、带截图/实机走查地收，不能批量脚本替换。

## 5. 建议顺序

**P0（✅ 已完成 2026-11-03）**
- 误报、分类色、动态背景、调色板兜底已全部收口；色值 38 → 23。

**P1 色值（✅ 已完成 2026-11-03）**
- `AgentStatusColors`（7 个状态色）与 `HelloCardPalette`（16 个渐变色）已建并被引用；色值 23 → **0**，非 token 文件已无硬编码色。

**P1 字号（待排版体系定档，未做）**
- 卡片字号档位（49 处）：需先定排版体系里"角色 / 固定域"的划分，再决定收进语义名还是标例外。
- `LyricsSettingsPage` 4 处：13sp 标签可换 `dimens.type.sm`，但 20sp 是 ± 字形——`dimens.type` 随窗口档缩放，换过去等于让按钮字形在平板/桌面变大，需先确认是否接受。

**P2（逐页收，不批量）**
- 先做“0 token 引用 + dp 多”的页面：`AudioEffectsScreen`(55)、`MusicListItem`(48)、`LibrarySettingsScreen`(46)、`IntroScreen`(40)、`RadioConsole`(33)、`EditMusicTagsScreen`(32)。
- 每页只收 `padding/spacedBy` 与 `RoundedCornerShape` 中能精确对上 spacing/corner 的值；组件尺寸（200/300/56/64/80…）先补 `component` 档位再换。
- 一页一提交，带截图或实机走查；不追求一次性把 1380 降到 0。

## 6. 备注

- `checkDesignTokens` 已上线，保证“不再新增”；本审计是存量收口的输入。
- 本文件只登记事实与顺序，不改口径；`D8-03` 的“存量不自愿强制”仍然成立，是否升级为强制项由后续决定。

## 7. 施工结果（2026-11-04 追加）

> 本节只记录结果，**不改写 §1–§6 的结论与口径**——那部分是 2026-11-03 的存量审计证据：`HMPDimens` 三档值、1380 处 dp、P2 逐页收的顺序，都是当时的真实描述。

- **P1 字号**：由 D8-03 排版重设计完成（`TypeScale` 9 角色 × 3 窗口档取代 M3 15 级与 `dimens.type`，业务 `.sp` 清零）。
- **P2 尺寸**：由「D8-03 续 · 统一尺寸刻度」五阶段完成。12 档 × 三窗口档 + `LocalHmpMetrics` 唯一入口；裸 `.dp` **1274 处** + `dimens.*` **168 处**收编，`androidMain` 10 处补收；对象外 117 处分流到 `LayoutConstraints.kt`（66 处布局约束）与 `HMPMetricTokens.Compact`（47 处节奏值）+ 8 处壳层字号常量。旧 `HMPDimens.kt` 及其 API 已删除。
- **守门**：从「基线只拦新增」升级为**尺寸 / 色值 / M3 typography 三项全量禁止**；`design-token-baseline.json` 与基线机制一并移除，无存量容忍。
- **§4 的警告仍然成立**：`16.dp → 档位` 在 Medium / Expanded 是 20 / 24dp，属视觉变更。本次按「允许借机修正、不要求零视觉变化」执行，分 5 批收编、逐批编译，末批由用户实机走查确认可接受。这条经验不因守门升级而失效。
- 施工结果与判据见 `docs/7_3/domain/D8.md` D8-03 续；规范见 `docs/spec/hmp-design.md` §10。
