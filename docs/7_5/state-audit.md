# 7.5 期 · 页面状态盘点（UDF 限度调研）

> **日期**：2026-11-06。**目的**：把「UDF 做到哪个限度」从抽象原则变成可逐条对照的清单 —— 页面上每一处 `remember` 状态，哪些该搬进 ViewModel、哪些留在 composable。
> **扫描范围**：`shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/**/*.kt`（177 个 `.kt`）。匹配 `by/= remember{ mutable*StateOf / List / Map }` 与 `rememberLazyListState` / `rememberPagerState` / `derivedStateOf` / `rememberCoroutineScope`。
> **方法**：机械扫描 + 变量名语义初判 + 抽样读码核实（`SearchScreen`、`PlaylistManageScreen`、`GalleryScreen`、`AppRoot`）。**局限**：非 AST 分析，靠变量名与上下文判断；C 档是人工倾向，施工前须逐条复核。

---

## 一、汇总

| 分类 | 处数 | 含义 | 处置 |
|---|---:|---|---|
| **A 明确进 VM** | **45** | 表单值 / 配置 / 加载与错误态 / 待操作对象 / 持久化开关 | 收进单一 `UiState` |
| **B 明确留 UI** | **91** | 滚动、拖拽、动画、焦点、展开收起、测量尺寸、协程作用域 | 白名单，不动 |
| **C 灰区（人工判）** | **38** | 弹窗可见性、待操作对象配对、Tab 选择、卡片异步缓存 | 倾向 A 29 / 倾向 B 6 / 需判 3 |
| 合计 | **174 处 / 51 文件** | | |

**按建议限度（业务状态全收、局部交互留 UI），实际要搬 ≈ 74 处，留 UI ≈ 97 处，真正需逐条判的只剩 3 处。** 超过一半的状态**不该**搬 —— 这是「全搬」不可取的实证。

### 按域分布

| 域 | A | B | C | 合计 |
|---|---:|---:|---:|---:|
| `agent/` | 28 | 31 | 9 | 68 |
| `library/` | 2 | 23 | 6 | 31 |
| `player/` | 0 | 20 | 2 | 22 |
| `settings/` | 7 | 5 | 8 | 20 |
| `common/` | 2 | 4 | 6 | 12 |
| `playlist/` | 5 | 3 | 4 | 12 |
| `AppRoot.kt` | 1 | 4 | 3 | 8 |
| `MainShell.kt` | 0 | 1 | 0 | 1 |

---

## 二、A 档：明确进 VM（逐条）

> 冒号后为行号。**集中度**：`AgentConfigScreen` 19 + `AIScreen` 8 = 27 处，占 A 档六成，正是批 5 的主体。

| 文件 | 处数 | 状态（变量:行） |
|---|---:|---|
| `agent/config/AgentConfigScreen.kt` | 19 | `policyConfig`:177、`endpointLoaded`:182、`useCustomEndpoint`:183、`customEndpoint`:184、`customApiKey`:185、`customModel`:186、`globalEndpoint`:188、`temperature`:191、`stepBudget`:192、`dailyRecommendCount`:193、`recommendListSize`:194、`targetCoverage`:195、`targetCount`:196、`autoRenew`:197、`preferredLang`:198、`agentEnabled`:200、`trustLevel`:202、`promptDirty`:233、`multiPromptDirty`:245 |
| `agent/config/AIScreen.kt` | 8 | `endpointValue`:625、`apiKeyValue`:630、`modelValue`:631、`enabled`:847、`saved`:858、`replyLanguage`:989、`portraits`:1067、`memoryEnabled`:1069 |
| `playlist/pages/PlaylistManageScreen.kt` | 4 | `playlistToDelete`:103、`playlistToRename`:104、`renameValue`:105、`renameError`:143 |
| `settings/pages/LyricsSettingsPage.kt` | 3 | `playerConfig`:100、`fullscreenConfig`:101、`floatingConfig`:102 |
| `settings/pages/AudioEffectsScreen.kt` | 2 | `sliderValue`:278、`sliderValue`:515 |
| `settings/pages/ProfileSettingsScreen.kt` | 2 | `uriImg`:147、`name`:228 |
| `agent/monitor/AgentMonitorScreen.kt` | 1 | `ledgerSnap`:126 |
| `AppRoot.kt` | 1 | `messageToShowState`:213 |
| `common/dialogs/base/InputDialog.kt` | 1 | `inputValue`:43 |
| `common/pages/IntroScreen.kt` | 1 | `currentStep`:109 |
| `library/pages/SearchScreen.kt` | 1 | `searchQuery`:82 |
| `library/pages/SongDetailScreen.kt` | 1 | `selectedSection`:625 |
| `playlist/pages/PlaylistScreen.kt` | 1 | `renameValue`:218 |

**构成**：表单 / 配置值约 27 处、业务数据 / 加载态约 9 处、开关与阶段标志约 6 处、待操作对象 2 处、校验错误 1 处。

---

## 三、B 档：留 UI 的白名单词条

以下类别**不进 VM**，作为 `// udf-exception:` 的默认放行集合：

| 白名单词条 | 代表变量 |
|---|---|
| 滚动 / 分页 / 列表状态 | `listState`、`pagerState`、`scrollState`、`scenarioListState`、`genreListState`、`musicListState` |
| 拖拽 / 手势 / 测量 | `dragOffset`、`dragAnchorIndex`、`dragLetter`、`trackWidth`、`capsuleWidth`、`stripSize`、`headerCollapseOffsetPx`、`containerHeightPx`、`isDragging`、`isProgrammaticScroll` |
| 动画 / 时间戳 / 相位 | `glowPhase`、`lastAutoRotateAt`、`userLockUntil`、`lastInteractionTime`、`visible`、`isExiting`、`timerKey`、`lastScrollJob` |
| 展开收起 / 菜单 / 视觉可见性 | `expanded`、`menuExpanded`、`presetExpanded`、`modelExpanded`、`playlistExpanded`、`showMoreMenu`、`showDetail`、`isSettingsPanelVisible`、`isControlsVisible`、`showPassword` |
| 协程作用域（非状态） | `scope`、`coroutineScope`、`cardScope`、`bfbScope`、`radioConsoleScope` 等 |
| 卡片渲染缓存 / 上一帧值 | `anchorCard`、`lastSongKey`、`lastPlaying`、`radioStatusCard`、`narrativeCard`、`prevHeight`、`previousPage` |
| 拖动中间态（播放进度条） | `sliderPosition`、`isSeeking`、`showTimerDialog` |

> `rememberCoroutineScope` 本身不是状态，但它是「composable 内起副作用」的信号；改造后这些作用域应随副作用一起消失，或仅剩动画用途。

---

## 四、C 档：灰区逐条（含倾向）

> **A** = 收进 VM / 走 `UiEffect`；**B** = 留 UI；**判定** = 需看它是否决定加载哪份数据。

| 文件:行 | 变量 | 倾向 | 理由 |
|---|---|---|---|
| `agent/cards/HelloCardDiscover.kt`:80-82 | `albumUris` / `liveTitles` / `liveArtists` | B | 卡片异步渲染副本 |
| `agent/cards/HelloCardSingleTrack.kt`:88-89 | `asyncCoverUri` / `asyncArtist` | B | 单曲卡异步缓存 |
| `agent/config/AgentConfigScreen.kt`:247 | `expandedPromptKey` | B | 仅决定哪张卡展开 |
| `agent/config/AIScreen.kt`:1068 | `showConfirmClear` | A | 确认清空记忆，业务动作 |
| `agent/monitor/AgentMonitorScreen.kt`:223 | `showClearConfirm` | A | 确认清空审计，业务动作 |
| `agent/shell/BottomFusionBar.kt`:162 | `fusionState` | A | 外部事件驱动，需确认是否 UI 缓存 |
| `AppRoot.kt`:263 | `savedTabIndex` | A | `rememberSaveable` 导航态，跨配置恢复 |
| `AppRoot.kt`:290-291 | `currentNotice` / `noticeIdCounter` | A | PresenceBus 事件投影，宜进组合根 VM |
| `common/dialogs/MusicPickerDialog.kt`:63 | `currentSelectedIds` | A | 选择器已选集合，表单值 |
| `common/dialogs/TimerDialog.kt`:67-68 | `selectedOption` / `customMinutesInput` | A | 定时器表单值 |
| `common/pages/IntroScreen.kt`:110-112 | `isPermissionGiven` / `showScanDialog` / `isScanCompleted` | A | 引导流程阶段态 |
| `library/pages/GalleryScreen.kt`:147-151 | `showRemoveConfirmDialog` / `pendingRemoveMusicInfo` / `showBatchRemoveConfirmDialog` / `pendingBatchIds` / `deleteCounter` | A | 成对确认链 + 刷新触发器 |
| `library/pages/SearchScreen.kt`:142 | `showIntentStrip` | A | 会话级「不再提示」偏好，影响 `isIntent` 判定 |
| `player/pages/PlayContent.kt`:223、330 | `selectedTab` ×2 | 判定 | 只切渲染块则留 UI；决定加载数据则进 VM |
| `playlist/pages/PlaylistManageScreen.kt`:102 | `isEditMode` | A | 编辑模式，隐式状态机入口 |
| `playlist/pages/PlaylistScreen.kt`:217、219、225 | `showRenameDialog` / `showDescriptionDialog` / `isListEditMode` | A | 弹窗由业务动作触发，宜走 `UiEffect` |
| `settings/pages/BackupSettingsScreen.kt`:209-210、272-273 | `showRestoreDialog` ×2 / `selectedBackupFilePath` ×2 | A | 成对确认链，待恢复路径是业务数据 |
| `settings/pages/LibrarySettingsScreen.kt`:198、430、634 | `folderToHide` / `showFullRescanDialog` / `showKnownFolderPicker` | A | 待操作对象、确认动作带落库后果 |
| `settings/pages/LyricsSettingsPage.kt`:99 | `selectedTab` | 判定 | 决定编辑哪份组件配置，倾向进 VM |

### 已核实的两处典型「隐式状态机」

- `PlaylistManageScreen.kt:102-143`：`isEditMode` / `playlistToDelete` / `playlistToRename` / `renameValue` / `renameError` 五态联动，改名弹窗 dismiss 要同时清三个。单一 `UiState` 最直接的收益点。
- `GalleryScreen.kt:147-151`：`showRemoveConfirmDialog + pendingRemoveMusicInfo`、`showBatchRemoveConfirmDialog + pendingBatchIds` 成对，`deleteCounter` 作刷新触发器。弹窗可见性看似 UI，实为业务确认链。

---

## 五、对规划的直接影响

> **2026-11-06 注**：施工方式已简化为「模板 → 逐页改 → 最后收口」，门禁在收口阶段一次性全量开。本表保留为**逐屏判定参考**，不再是前置门禁或批次设计的依据。

1. **限度定量**：按「业务状态全收、局部交互留 UI」，实际搬运 ≈ **74 处 / 约 20 个文件**；若按「全搬」上限，要多动 **97 处**纯 UI 状态，且会把 `LazyListState` 这类 Compose 对象塞进生命周期更长的 VM，是净负担。
2. **白名单可写死**：§三 的 7 类词条即共通模板里「哪些 `remember` 不用搬」的默认答案，收口门禁也照它放行。
3. **逐页施工时按本表判定**：agent 域 A+C 合计约 37 处，全阶段最重；library 虽有 10 屏，A+C 仅 8 处，多数是滚动/展开类 B 档 —— **屏数不等于工作量**，重的屏单独多花时间。
4. **`show*Dialog` 不能一律留 UI**：与 `pending*` / `selected*` / `*Value` 配对出现时是业务状态机，是 C 档倾向 A 的主体；门禁应把「弹窗可见性 + 独立待操作对象」列为需 review 模式。
5. **组合根**：`AppRoot` 的 `savedTabIndex` / `currentNotice` / `noticeIdCounter` / `messageToShowState` 属全局 UI 状态，宜进组合根 VM 或并入既有 `DialogEvent` 通道，不要分散在 772 行的 `LaunchedEffect` 里。

**参考**：[7.5 期正式规划](plan.md) §三 共通模板、§4.2 逐页改、§4.3 最后收口。
