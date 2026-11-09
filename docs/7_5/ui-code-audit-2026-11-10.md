# UI 层现状清点（2026-11-10）

> 本文是**一次独立的代码清点记录**，不是整改台账，也不作为后续判断的依据。
> 结论全部来自逐个 `.kt` 文件的静态阅读；清点期间未修改任何代码。

---

## 0. 这份文档是怎么来的

- **触发**：在「UI 侧 UDF 是否基本完成」的判断之后，要求不参考既有文档/台账/脚本结论，回到真实代码重新盘点。
- **方法**：5 个只读审查者分域逐文件通读 `shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/**/*.kt`。明确禁止打开 `docs/`、任何 `*.md`、`scripts/` 下的文件，也不引用任何既有台账、基线或审计脚本的结论。跨文件结论一律用全项目 `.kt` 引用检索验证。
- **覆盖范围**：该目录共 186 个 `.kt`，约 160 个被逐行读完 —— `player` 19、`library + playlist` 37、`agent` 36、`settings + common/pages + common/dialogs` 38、`common` 其余 48。少量跨目录文件只做了定点引用检索，见第 8 节。
- **交叉核对**：审查者之间对同一根因做了去重、撤回、降权与归属更正。本文按收敛后的最终版本记录，并标出被撤回的条目。
- **未做**：未编译运行、未看编译器告警、未读 `domain`/`data` 层实现、未读 Koin / Navigation3 的 `ViewModelStoreOwner` 实现、未读 `strings.xml`。凡属静态推断的结论，本文均按「推断」表述。

---

## 1. 机械核验基线（仅作背景，不构成判断依据）

清点开始前复核过一次：

| 项 | 结果 |
|---|---|
| `check-udf.py` | 通过，注入基线 0 |
| `audit-udf-ui.py`（R1~R5） | 0 违规、0 线索 |
| `ktlint check` | 0 |
| `testAll` | 全绿 |
| 提交 | `8bfcbcf`，`ahead 1 / behind 1`，工作区干净 |

**但逐文件读码后，第 2~4 节的现存问题依然成立**。三条机械门禁与五判据巡检只覆盖代码形态，覆盖不到运行时行为、ViewModel 作用域、生命周期与消费链路。

---

## 2. 重要更正：上一轮汇报的一项整改并未落地

清点中发现，此前汇报「台账四项全部完成」时，有一项**实际没有改到代码**：

| 声称完成 | 代码实际状态 |
|---|---|
| `AgentConfigScreen` 双栏 prompt 编辑复用 VM 三个具名方法 | `AgentConfigScreen.kt:318,320,1144,1146,1261-1262,1275-1283` 仍是 `onMultiPromptTextsChange` / `onMultiPromptDirtyChange`，UI 里仍拼 `multiPromptTexts + (key to value)` 与 `multiPromptDirty ± key` |
| 删除 VM 两个整包 Map/Set 方法 | `AgentConfigViewModel.kt:201,203` 两个方法都还在 |

**原因**：一次性批量脚本在第 6 步（LibrarySettingsScreen 的 import 锚点）断言失败后退出，排在其后的第 7/8 步从未执行；随后只单独补跑了第 6 步，漏了 7/8。文件未变所以编译与测试照常通过，被误判为「已完成」。

同批其余 6 项经本次核实**确已落地**：`UserUsageDataViewModel.onScreenEnter`（:217）、两屏改 `activityViewModel()`（`UserScreen.kt:179`、`UserUsageDataScreen.kt:94`）、`ProfileSettingsViewModel` 头像 `avatarUri.collect`（:55）、`LibraryViewModel.hasMusicReadAccess` + `onPermissionChecked`（:68/:328）、`LibrarySettingsScreen` 的 ON_RESUME 上报与 banner 读取（:122/:126/:161）。

另有两处是**上一轮改动本身引入/遗留**的：

- `HelloCardStack.kt:24` 的 `HelloCardsState` 仍是普通 `class` 而非 `data class`，作为 `StateFlow` 值类型没有 `equals`，`combine` 每次产生新实例都会触发重组。
- `GalleryScreen.kt:117-124` 的批量加歌完成提示改成了收 `PlaylistEffect.SongsAdded`，但 `PlaylistViewModel.addItemsToPlaylist`（:419-434）只调用 `onComplete?.invoke()`，而 UI 已删掉 `onComplete`；`SongsAdded` 的唯一发射点是 `onSongsPicked`（:466）。**这条 toast 现在是死分支，永不出现**。

---

## 3. 现存问题

### A. 真 bug（行为错误，优先）

| # | 位置 | 现象 | 建议 |
|---|---|---|---|
| A1 | `library/pages/GalleryScreen.kt:117-124,155` + `playlist/viewmodel/PlaylistViewModel.kt:419-434,466` | 批量加歌完成提示死分支，toast 永不出现（见第 2 节） | `addItemsToPlaylist` 完成后 emit `SongsAdded(count)`，或恢复一次性回调但统一口径 |
| A2 | `playlist/pages/PlaylistScreen.kt:319-334` | 同屏两套加歌反馈：路径 A 走 `PlaylistEffect`，路径 B 仍 `onComplete` 回调 toast；两屏文案口径还不一致（`song_added` vs `added_n_songs_to_playlist`） | 统一由 VM 发 `SongsAdded(count)`，UI 只留一个收集器 |
| A3 | `library/viewmodel/SongDetailViewModel.kt:54-92` | `loadSongDetail` 每次 `viewModelScope.launch` 且不取消旧 `historyFlow.collectLatest`，累积多个常驻订阅，各自把**自己那首歌**的历史写进同一 `_uiState.detail` → 串台；`:56` 每次重发 `Loading`，与 `:64` 注释「不重发 Loading」矛盾 | VM 内用单个 `Job` 字段 `cancel` 旧订阅，或改 `flatMapLatest` / 单一订阅 |
| A3 触发点 | `library/pages/SongDetailScreen.kt:113-115`（`musicId, navController.size` + `onRetry`）、`player/pages/PlayerScreen.kt:354-358`（切歌） | 两处触发同一根因；`koinViewModel(key="player-info")` 只隔离实例，不修根因 | 修复点落在 VM 一处即可覆盖两屏 |
| A4 | `agent/cards/HelloCardStack.kt:51-60` + `HelloCardCoverflow.kt:100,358` + `library/pages/HomeScreen.kt:261-267` | `resolveTracks` 默认 `{ emptyList() }`；只有横屏 `HelloCardCoverflow`（`HomeScreen.kt:153-154`）注入了 `HomeViewModel::loadTracksByIds`，竖屏 `HelloSlideCardStack` 签名里**根本没有该参数**、内部也不转发 → 竖屏单曲卡/发现卡的封面、艺术家、标题补全全部失效 | 给 `HelloSlideCardStack` 增 `resolveTracks` 并转发；更彻底的是由 `HomeUiState` 直接给卡片数据，取消外部注入的查库函数 |
| A5 | `AppRoot.kt:199-217,244-248,698-705` | `dialogManager.dialogEvent`（`MutableSharedFlow<DialogEvent?>`）被 `collectAsState(null)` 状态化 + `remember { mutableStateOf<Message?>(null) }` 第二份副本 + `onDismiss` 手动置 null + 两个 `LaunchedEffect(dialogEvent)` 分头消费同一事件 | `LaunchedEffect(Unit) { dialogEvent.collect { ... } }` 单点订阅；toast/导航以 Effect 形态直达消费端 |
| A6 | `common/dialogs/DialogEvent.kt:9-13,20,34-49` + `controller/DialogManager.kt:29-34,139-160` | `Message`/`NavRequest`/`ConfirmChain` 是 data class 且 `id = nowEpochMillis()` 参与 `equals`；同毫秒两条相同 Message 令 `LaunchedEffect` key 不变 → 第二条被吞；`DismissTimerDialog` 是 object 恒等；`replay=0 / 容量 1 / DROP_OLDEST` 并发时丢事件。确认链已改单调计数器，其余未改 | 全部 `id` 走 `allocateConfirmId()` 单调计数器 |
| A7 | `common/viewmodel/ThemeViewModel.kt:32,38-46,48-76,83-101` | 稳态同时存在 **2 个** `ThemeViewModel` 实例（1 应用级 `AppRoot.kt:142`/`PlayerScreen.kt:182` + 1 `Routes.Main.Tabs` entry 级，由 `agent/cards/HelloCardAnchor.kt:116` 与 `HelloCardRadioStatus.kt:223` 共享）；运行时上界 = 1 + 活跃且含 `koinViewModel()` 的 entry 数。每个实例都无条件订阅播放流跑 3D 直方图取色，`paletteCache` 是无锁 `mutableMapOf` 且被不同协程并发读写 | 统一到应用级实例；`paletteCache` 改并发容器或加 `Mutex`；纯取色抽成无状态 UseCase |
| A7 触发点 | `player/pages/LyricsScreen.kt:118`、`library/pages/RecommendListScreen.kt:96`、`agent/cards/HelloCardAnchor.kt:116-118`、`HelloCardRadioStatus.kt:223-225` | 4 处 entry 级 `koinViewModel()` 均会新建实例；`MainShell.kt:43 beyondViewportPageCount=3` 让 4 页常驻组合，但 HorizontalPager 不提供独立 owner，**不增加** VM 实例数 | 同上 |
| A8 | `library/pages/CustomScreen.kt:254-255,268-269,282-283` | 三个 haze Slider 的 `onValueChange` 每帧调 `SettingsViewModel.saveHaze*`（`viewModelScope.launch { useCase.saveXxx(v) }`），无防抖/无 `onValueChangeFinished` | 拖动只更新草稿态，结束时或 debounce 后落库 |
| A9 | `settings/pages/AudioEffectsScreen.kt:264-269,521-531` + `settings/viewmodel/AudioEffectViewModel.kt:56,59` | 均衡器拖动每帧构造/透传整份 `FloatArray` 给 `setBassBoost` / `setCustomEqualizer`，无节流（与 A8 是**不同页面的同型缺陷**，不合并） | 同 A8 |
| A10 | `library/pages/GalleryScreen.kt:84-89` + `library/viewmodel/LibraryViewModel.kt:136-140,436` | `MainShell` 4 页常驻 → Gallery 的 `LaunchedEffect(Unit){ getAllMusic() }` 在应用启动即触发，与 VM `init { getAllMusic() }` 并发；`getAllMusic()` 无 job 取消，快速连点排序会并发写 `_allMusic.value`、后写者可能是旧排序。`DisposableEffect` 的 `onGalleryScreenLeft()` 永不执行 → `galleryUiState` 的确认链状态不清 | 删 UI 侧重复加载；VM 内用可取消 `Job` 串行化；清理挂实际离屏信号或改自清 |
| A11 | `library/pages/HomeScreen.kt:105-111` + `library/viewmodel/HomeViewModel.kt:62-77` | 常驻 tab 使 `WhileSubscribed(5000)` 的 5 条上游流（含 masterAgent / quotaGate）从启动起永不释放，`effect` 收集器也常驻 | 按页可见性门控订阅，或收窄 effect 收集器到当前可见页 |
| A12 | `settings/viewmodel/IntroViewModel.kt:48-53` + `settings/pages/IntroScreen.kt:112` | `onStart` 用 `_uiState.value = IntroUiState(...)` 整体覆盖，会丢掉 init 里 `aiAccessMode.collect` 已写入的值 → 静默重置为 `FREE`，直接决定扫描完成是否触发 AI 补全 | 改 `update { copy(...) }`，或把 `skipPermissionStep` 传进构造、init 内一次定初态 |
| A13 | `agent/chat/ChatViewModel.kt:247-256,357-361` | `launchDirect` 不置 `running=true`、不赋给 `job` → 可重复派发且 `cancel()` 取消不到；`launchRun` 的 `finally` 只复位 `running`，未清 `pendingConfirm`/`submittedConfirms`，异常时确认卡残留且 `gatewayBridge=null` → 点「照做」空操作并卡死 | Direct 纳入运行态/job；`finally` 统一复位确认态 |
| A14 | `agent/chat/ChatAgentGateway.kt:80,77` | `ConfirmBridge.pending` 是 `LinkedHashMap`，`awaitApprovals`（生产协程）与 `submit`（VM）并发读写无同步，注释却声称「跨协程线程安全」 | 改 `ConcurrentHashMap` 或加同步 |
| A15 | `settings/viewmodel/UserUsageDataViewModel.kt:151-163` | TASTE 分支三条 `attempt(dim){...}.orEmpty()` 没有 `failed = dim`，`failedDimension` 恒为 null；注释称「任何一条抛异常就记失败」，UI 会把 DAO 异常显示成「暂无使用数据」 | TASTE 分支同样记账 |
| A16 | `library/pages/components/musiclist/MusicListConfig.kt:121` + `library/pages/ListScreen.kt:179,208,237,264` | 默认 key `"${m.music.id}_$i"` 含 index，排序/置顶/增删一变所有 key 全变；`ListScreen` 4 处 `items` 完全没给 key（`PlaylistManageScreen.kt:213` 已正确 `key={it.id}`） | 默认 key 改 `{ _, m -> m.music.id }`；各 `items` 补 key |
| A17 | `settings/pages/AudioEffectsScreen.kt:243,259,480` | `remember(currentLevel)` 的 key 选择不当：拖动中 `onLevelChanged` 回流更新 `currentLevel` → key 变 → 本地 `sliderValue` 被重置，打断拖动；文案读 VM 值，与本地 float 不同步 | 拖动中间态用无 key remember，非拖动时才同步 VM 值；文案读 `sliderValue` |
| A18 | `player/pages/AdvancedLyrics.kt:116-118` | `remember(parsedLyrics, currentPosition) { derivedStateOf { ... } }`：易变值当 key，每次进度采样都重建 `derivedStateOf`，去重机制失效 | key 只留 `parsedLyrics`，`currentPosition` 以 State 参与，或直接普通计算 |
| A19 | `player/pages/PlayerScreen.kt:197-201` + `player/pages/LyricsScreen.kt:133-140` | 两屏进入都 `startProgressTracking()`，`onDispose` 空、`stopProgressTracking()` 在 UI 层从未被调用 | 配对停止，或由 VM/控制器按订阅计数管理 |
| A20 | 功能缺失/失效交互 | `LyricsScreen.kt:101,216-231` 的「更多设置」按钮：导航 lambda 从未透传给 `LyricsSettingsPanel`，落到默认 `{}`；`common/components/base/MyButton.kt:141-154` 灯泡按钮只有触觉反馈；`agent/chat/ChatScreen.kt:113-114` 三个菜单入口空实现；`common/components/SegmentedControl.kt:133-166` 竖排不渲染 `option.label`；`library/pages/ListScreen.kt:401-408` `MoodCard` 是空 `Box` 且不渲染标题 | 补齐实现或移除不可用的可点区域 |
| A21 | `library/viewmodel/LibraryViewModel.kt:68` + `settings/pages/LibrarySettingsScreen.kt:145-147` | `hasMusicReadAccess` 乐观默认 `true`，`onScreenLeft()` 把整个 `LibrarySettingsUiState()` 重置回默认 → 离屏再进、ON_RESUME 上报前 banner 误渲染「有权限」 | 默认值改未知态（三态），或重置时保留权限位 |
| A22 | `agent/chat/ChatEntryBroker.kt:10` + `agent/chat/ChatScreen.kt:98-103` | `pendingInput.value = null` 在 `sendPreloaded` **之前**执行，发送路径异常则这次输入彻底丢失 | 改为消费成功后再清，或改 SharedFlow Effect |

### B. 形态债（UDF / 架构）

| # | 位置 | 现象 | 建议 |
|---|---|---|---|
| B1 | `agent/config/AIScreen.kt:188,563,762,879,954` | 同一 `uiState` 被 **5 处** collect；父层解包传参 + 4 个子区块各自签名收整个 `vm` 再订阅，两套读取路径，子块可绕过父层读状态/调动作 | 父层只 collect 一次，下传 `form` 与动作 lambda，子块签名不接 VM |
| B2 | `agent/cards/HelloCardRadioStatus.kt:215-225`、`HelloCardAnchor.kt:116-133` | 卡片 composable 内 `koinInject<MasterAgent>()` + 订阅 `radioMessageFlow`/`radioCardState`、`koinViewModel<ThemeViewModel>()`、`koinInject<PlaybackController>()` 订阅歌词/进度，绕过 `HelloCardsViewModel.uiState` | 这些字段并入 `HelloCardsState` 快照，卡片只收渲染数据 |
| B3 | `agent/monitor/AgentMonitorViewModel.kt:40-42`、`AgentMonitorScreen.kt:116,392,531-536,568,595,622` | UiState 持 `Capability?` **活实例**而非数据快照；整屏约 11 个订阅点（6 条 cap 流 + 4 条全局日志流）；卡片直接订阅 `cap.stateFlow`；日志业务数据在 UI 订阅全局单例；`:25` 还在 Elvis 分支里条件调用 composable | 抽 `status/statusText/detail/logCount` 只读快照由 VM 投影 |
| B4 | `AppRoot.kt:204-211` | composable 直连 `LyricsSettingsUseCase`（`koinInject`），在 `LaunchedEffect` 里读配置 + 判权限 + 启动悬浮歌词，异常静默吞 | 编排移进 `AppRootViewModel` |
| B5 | `AppRoot.kt:199` vs `:587` | 两套 Dialog 状态出口并存；`DialogManager.showTimerDialog` / `DialogEvent.ShowTimerDialog` 全项目无调用方（实际走 `DialogViewModel.showTimerDialog`），两个系统还互相 dismiss | 统一到一套出口，删未接线事件 |
| B6 | `player/pages/PlayerScreen.kt:181-186,204-205,354-355` | 一屏 4 个状态出口（`radioUiState` / `uiState` / `paletteColors` / `songDetailState`）加一个命令 VM；`SongDetailViewModel` 在 UI 里 `koinViewModel(key=...)` 创建并触发加载 | 电台态/详情态并入屏级 uiState，或明确登记为例外；加载入口移进 VM |
| B7 | `library/pages/RecommendListScreen.kt:131-145` | `playFrom(index)` 在 composable 里 `scope.launch` 完成「取 VM 结果 → 逐个入队 → 定位 → 导航」；`HomeViewModel` 已把完全相同编排收进 VM + Effect，此屏漏改 | `RecommendListViewModel.onPlayFrom(index)` + SharedFlow 导航 Effect |
| B8 | `settings/pages/IntroScreen.kt:95-97,111-118,190-201` | 一屏 4 个 VM、6 条散装流；`onStartScan` 一次点击两次 VM 调用；`onScanComplete` 里 UI 读业务态做分支再调另一个 VM | 收敛为 `IntroViewModel` 的单一具名动作 |
| B9 | `settings/pages/UserScreen.kt:175-185,196-198,210-216` | 3 个 VM、4 条散装流；组合期 `getAvatarUri()` 与 VM init 重复；`duration/60_000` 业务换算写在 UI `remember` | 单一 uiState；加载进 VM；换算下沉 |
| B10 | `settings/pages/AudioEffectsScreen.kt:139-148,169-177` | UI 里写 `FloatArray.copyOf()` 状态转换，横竖屏两处重复 | VM 提供 `onBandLevelChanged(index, level)` / `onResetEqualizer()` |
| B11 | `settings/pages/LibrarySettingsScreen.kt:106-112,131-142,145-147,199,232` | 6 条散装流 + `settingsUiState` 双出口；`loadHiddenFolders()` 组合期手动加载；`onScreenLeft()` 由 UI 编排；`scannedFolders.map{it.path}` 在 UI 转换且横竖屏两遍（注：`settingsEffect.collect` 是合规 `SharedFlow<Effect>`，**不算问题**） | VM 暴露单一 uiState；加载/清理/投影下沉 |
| B12 | `common/dialogs/viewmodel/DialogViewModel.kt:77-100,95-100,572-583` | 7 个状态出口（`activeDialog` + 4 条派生散装流 + `showTimerDialog` + `isLiked`）；Timer 与 isLiked 各两份副本；`playMusic(onPlayComplete)`、`showCreatePlaylistDialog(onCreated)` 等大量回调式 API；`updateCreatePlaylistName` 每输入一个字符 launch 一次 suspend 查重校验 | 只留 `activeDialog` + `SharedFlow<Effect>`；回调改具名动作；输入校验 debounce |
| B13 | `common/dialogs/MusicDetailDialog.kt:79,162-165,188-191,199-202,223-226` | 订阅派生流；回调里连调 `navigate` + `dismiss` + `onDismiss` 三步；`RouteNavigator` 直连导航与 `DialogEvent.NavRequest` 两套导航出口；`getMenuOptions` 把 Compose 资源与 lambda 塞进 VM 且每次重组重建 | 菜单按 state 渲染，动作统一上送，导航统一走 Effect |
| B14 | `agent/config/AgentConfigScreen.kt:248-341 vs 342-838,1136-1386` | 单栏/双栏两套完整实现（约 200 行逐行重复），已产生行为差异：温度/targetCoverage 横屏在 UI 再取整 → 双重取整；注释声称「行为一致」与事实不符。`AIScreen.kt:225-334` 横竖屏分区也各写一遍 | 共用同一组子区块，只换外层容器；取整统一进 VM |
| B15 | `agent/config/AgentConfigScreen.kt:201-208,651,774,813,819,1210,1333` + `AgentConfigViewModel.kt:67,81,89,119,134,235-249` | **层级倒挂**：`resolveEffectivePrompt`/`extractPlaceholders`/`helloPromptGroups` 等定义在 UI 文件却被 VM 调用；`globalReplyLang` 塞进 UiState 专供 UI 算 prompt；UI 把算好的 `factoryPrompt` 回灌 VM；占位符校验 UI 与 VM 各实现一遍；`:201` 的 `remember` 缺 `globalReplyLang` key | prompt 解析/校验移到 domain/VM，UiState 直接给 `factoryPrompt` 快照 |
| B16 | 一次性效果 state 化 / 回调化（一组） | `agent/shell/AgentNoticeBar.kt:48-60,107-110`（`notice: AgentNotice?` + `onDismiss` 清除 + 4s 定时退场协程在 UI + 载荷 String）；`agent/config/AIScreen` 的 `ShowMessage(String)` 在 VM 内 `getString`（`AiSettingsViewModel.kt:59,246...`）；`DialogEvent.kt:15-18` 的 `ShowTimerDialog` 夹带 lambda；`PlaylistPickerDialog.kt:115-118` 一次点击拆两次回调；`TimerDialog.kt:180-186` UI 里把选项解析成 minutes | 统一 `SharedFlow<Effect>`，载荷用 `UiText`/资源引用，事件只带纯数据 |
| B17 | `library/pages/components/RadioCard.kt:90,105-113` | 叶子组件自己 `koinInject<DialogManager>()` + `scope.launch { showMessage }` 做额度不足提示；额度这一业务判定也留在 UI | `onToggleRadio` 收进 VM 做额度判定，拦截时发 Effect |
| B18 | `agent/cards/HelloCardDiscover.kt:80-91`、`HelloCardSingleTrack.kt:88-96`、`HelloCardAnchor.kt:134-142` | composable 用 `remember` 持曲目元数据、`LaunchedEffect` 起协程调 `resolveTracks` 查库并 `associateBy` 拼 Map；`LrcParser.parse` 歌词解析也在 UI | 补全后的 `SlideCard` 快照由 `HelloCardsViewModel` 产出 |
| B19 | `agent/cards/HelloCardRadioStatus.kt:77-183` | UI 文件里的 `suspend buildRadioStatusCard` 直连 MasterAgent（`queryRadioStationTheme`/`queryRadioPlaylist`/`queryNowPlayingContext`），且 `getString` 结果存进数据类 | 移入 domain/VM，文案保留资源引用 |
| B20 | `common/components/base/MyButton.kt:334-357`、`:78-85` + `common/components/Capsule.kt:52-54` | 通用组件直接持 `NavBackStack` 硬编码 `Routes.Library.Search`；参数含 `MusicExtra`/`MusicLabel`/`MusicSortKey` 等 domain 对象并在组件内拼业务串 | 导航交调用方/Effect；改收 `List<String>` / `UiOption` |
| B21 | `settings/pages/IntroScreen.kt:434,481` + `common/dialogs/MusicScanDialog.kt:54-55` | 把 `LibraryViewModel` 当 composable 参数层层透传；`libraryViewModel: LibraryViewModel?` 的 `!= null` 恒真 | 弹窗只收 uiState + 动作 lambda；参数改非空 |
| B22 | `player/pages/AppRoot.kt:571-579`、`player/pages/PlaylistArea.kt:60,82,85,96` | 组合根重复实现 `onPlayPause` 判断（屏级 VM 已有封装）；`onPlayItem: suspend (MusicInfo)->Unit` 实现并不 suspend，却在 UI `scope.launch` 里执行；`callbacks`/`config` 每次重组新建 | 复用同一 VM 动作；`onPlayItem` 改普通函数；配置用 `remember` 稳定 |
| B23 | `player/pages/PlayerScreen.kt:236-245` + `AppRoot.kt:632-645` | 定时器确认是「UI 注册 `onConfirm` lambda → VM 存进 `StateFlow<TimerDialogConfig?>` → 宿主回调执行」；函数类型存进 StateFlow 不可序列化、进程重建丢失，`onDismiss` 是空 lambda | VM 发一次性 Effect / uiState 表达 dialog |
| B24 | `agent/monitor/AgentMonitorViewModel.kt:101` | 在 `combine` 的 transform 里写另一个 `StateFlow`，而它又是 `uiState` 的输入 → 隐式反馈环、顺序依赖 | 改 `map` 派生 |

### C. 死代码 / 低优先（一批）

- **死状态 / 死方法**：`PlaylistViewModel._allMusicForAddPickerState`（只写不读）、`loadAllMusicForAddPicker`、`createPlaylistAsync`、`getSelectedPlaylist(LabelName)` 重载；`PlaylistScreenUiState.isListEditMode`（VM 写入、全仓无读取，真正在 `MusicListState` 的 remember 里 → 双份状态）；`DialogViewModel.removeMusic()`；`AuditLogViewModel.onClearAll()`/`onDeleteById()`；`PlayerCallbacks.onTimerClick/onCancelTimer` 与 `PlayerScreenViewModel` 同名方法；`AiSettingsViewModel` 的 `autoBatchProcess` 整条死链；`RecommendationViewModel.init` 里与初值相同的无意义重设；`AudioEffectsScreen.isDragging` 只写不读。
- **死分支 / 冗余**：`RadioEntryCard.kt:97-103` 配额分支不可达（按钮已 disabled）；`AppRoot.kt:270` `radioConsoleScope` 死变量；`PlayerScreen.kt:472,565` 对非空 Int 用 `?: 0`；`TabPageIndicator.kt:45-46`、`HelloCardCoverflow.kt:382`、`AgentNoticeBar.kt:86-90`、`CompanionBubble.kt:223-227` 两分支相同；`IntroScreen.kt:546,555-560` compact 两分支相同（注释声称收紧了布局）；`LibrarySettingsScreen.kt:727` UNSUPPORTED 死分支；`UserUsageDataScreen.kt:1008` `ReportNarrativeCard` 无调用；`:1115` `forEachIndexed` 的 idx 未用；`SongDetailScreen.kt:218-220` 重试按钮文案是 `loading`。
- **死参数 / 未接线**：`agent/cards` 全目录无 `onLongClick =` 赋值 → `FamilyDispatch.onLongClick` 与 8 个卡片的 `!= null` 分支全死；`HelloCardCoverflow.kt:98` `permanentLock` 无调用方传入；`BottomFusionBar.kt:137` `progress` 死参数；`AIScreen.kt:177,390,558,562` 死参数/死变量/过时注释；`RadioConsole.kt:130` `onPlayTrack` 默认 `{}`。
- **重复实现 / 口径不一**：`ListScreen.kt:124-130` 与 `PlaylistManageScreen.kt:109-115` 排序口径逐字复制；`AuditLogScreen.kt:179-209,218-246` FilterRow/FilterRail 重复；`AgentMonitorScreen.kt:374` 与 `AIScreen.kt:540` 的 `fmtTokens` 公式不同却注释称「同口径」；`HelloCardsState` 非 data class。
- **文案硬编码**：`HelloCardNarrative.kt:105` 中文、`HelloCardEnrichTracking.kt:54` 英文 `chunk`、`AgentMonitorScreen.kt:362` 原始 outcome 码、`MyButton.kt` 的 `contentDescription` 英文。
- **主题扩展点**：`HearableMusicPlayerTheme.kt:16` `dynamicColor` 死参数（3 个平台壳调用方都只传 `darkTheme`，Theme composable 本身非死代码）；`ThemeExtensions.kt:27-41` `currentExtension` 全局 var 不可观察且 `setThemeExtension` 无调用点。
- **`collectAsState(initial)` 首帧**：`IntroScreen.kt:113-118`、`UserScreen.kt:182`、`CustomScreen.kt:107-112`、`GalleryScreen.kt:75-76`、`ArtistScreen.kt:55`、`AlbumScreen.kt:55`、`SearchScreen.kt:86`、`AppRoot.kt:170-176` 对 StateFlow 用了带 initial 的 Flow 重载。已核实各 `.stateIn` 初值与传入的 `DEFAULT_HAZE_*` 常量**完全一致**，故不存在值错误，只剩一帧闪烁 → 低优先；`CustomScreen.kt:106` 的 `customMode` 与 `UserScreen.kt:181` 的 `userName` 是普通 `Flow`，initial 必需，**不算问题**。
- **未使用 import（成批）**：`chat/ChatKoinModule.kt` 约 19 个；`ChatScreen.kt`、`CompanionBubble.kt`、`ChatAgentGateway.kt`；`AIScreen.kt`、`AgentConfigScreen.kt`；`BottomFusionBar.kt`、`RadioConsole.kt`、`FusionSidebar.kt`、`AgentNoticeBar.kt`；`AuditLogScreen.kt`、`AgentMonitorScreen.kt`；`agent/cards` 多个 Family 卡片的 `dp`；`HomeScreen.kt` 6 个、`RadioCard.kt` 6 个、`RecommendListScreen.kt:48`、`FeatureEntryRow.kt` 5 个；`UserScreen.kt` 7 个、`UserUsageDataScreen.kt:89`、`SettingScreen.kt:24`、`IntroScreen.kt:39-41`、`DatabaseTooNewScreen.kt:13`、`SubScreen.kt`、`TabScreen.kt`、`InputDialog.kt`、`MessageToast.kt`、`AddSongToPlaylistDialog.kt`、`DialogEvent.kt:5 ConfirmDecision`、`AudioEffectViewModel.kt:3 Stable`；`common/components` 下 10 个文件的 `dp`。均为静态检索结论，需编译告警确认。
- **其它**：`PlaylistPickerDialog.kt:149` 非空断言 `!!`；`AppRootViewModel.kt:102-108` `runCatching{}.getOrNull()` 吞异常，查不到曲目时点节目单静默无反馈；`MiniPlayerBar.kt:90-102` `while(true)` 无 `isActive`；`DotPager.kt:30-33` `rememberPagerState` 不随 `pageCount` 变化且 `pageContent[page]()` 无边界保护；`PlayerScreenNestedScroll.kt:30` remember key 含每次重组新建的 lambda；`PlayerScreen.kt:855-863` SeekBar 的 `LaunchedEffect(currentPosition)` 不含 duration。

---

## 4. 已闭环的非问题（审查者主动撤回 / 降权 / 改判）

| 原条目 | 结论 |
|---|---|
| `common/util/LoadStateExtensions.kt` 的 `onXxx` 回调式工具会造成组合期重复副作用 | **撤回**。全项目生产代码零调用点（`asLoadState`/`runCatchingToLoadState` 只命中定义与有限流单测，`.onSuccess/.onError/.onLoading/.onEmpty` 仅命中定义处） |
| `RecommendListScreen.kt:119-124` 的 `produceState { paletteFor(seedUri) }` 是「自建 VM 取回业务结果自己编排」 | **撤回该归因**。`seedUri` 是 `String?`、key 稳定；`produceState` 会取消旧协程；结果只用于 `lerp` 纯渲染，不写回 VM、不入队、不导航 |
| `collectAsState(initial)` 首帧会「回跳到默认值/值错误」 | **降为低**。默认值一致，只余一帧闪烁；见 C 节 |
| `LibrarySettingsScreen.kt:131-142` 的 `settingsEffect.collect` 是组合期副作用 | **改判合规**。`LibraryViewModel.kt:332-337` 是标准 `SharedFlow<Effect>`，该处是标准 `LaunchedEffect(Unit){ collect {} }` 消费 |
| `HearableMusicPlayerTheme` 整个 composable 无消费者 | **措辞更正**。`dynamicColor` 死参数坐实，但 3 个平台壳（iOS/Android/Desktop）都在调用该 Theme |
| `library/pages/GalleryScreen.kt` 与 `PlaylistScreen` 会「双份弹 toast」 | **措辞更正**。两 collector 各 `showMessage` → 两次 emit 到同一 `dialogEvent` → `AppRoot.kt:199` **单点**消费，后果是覆盖 / 相等 message 被吞 / `DROP_OLDEST` 丢事件，**不是显示两次** |
| `CustomScreen.kt:106` / `UserScreen.kt:181` 的 `collectAsState(initial)` | **排除**。`customMode`、`userName` 编译期静态类型是普通 `Flow<String>`，initial 必需 |

另需说明：`LibraryViewModel` 侧已由审查者完整通读，**确无单一聚合 uiState**，暴露 11 条散装 `StateFlow` + `settingsUiState` + `galleryUiState` + `settingsEffect`。事实无分歧，只是归类见第 6 节。

---

## 5. 同根因去重口径

| 合并组 | 包含 |
|---|---|
| ThemeViewModel 多实例 | `common` 问题 1 = `player` 问题 1 = `library` 新增 C = `agent` H3 的 VM 部分 → 一条根因、4 类触发点（A7） |
| SongDetail 重复订阅 | `library` 问题 3 + `player` 问题 7 升级 → 一条根因、两处 UI 触发点（A3），修复点在 VM 一处 |
| dialogEvent 链路 | `player` 问题 3/5/6 + `settings` 中 15 / 严重 8 / 低 25 → 同根因，宿主侧与定义侧分别登记（A5/A6） |
| 竖屏 resolveTracks 断链 | `agent` M7 + `library` 低 12 校正 → 一条（A4） |
| pager 预加载 | `library` 新增 A/B + 补强 13 + `settings` 中 26 → 核对后合并（A10/A11） |
| **不合并** | 每帧落库：`CustomScreen`（A8）与 `AudioEffectsScreen`（A9）是不同页面的同型缺陷，分别保留 |

---

## 6. 待裁定的 3 处

1. **`LibraryViewModel` 的 11 条散装 `StateFlow` 归类**：一方判「有意的跨屏共享 VM 投影例外」（理由：跨屏共享、独立持久化，硬聚合会让任一标签列表变化触发整屏重组；真正需要收口的屏级交互态已各自聚合），另一方按判据字面判「严重形态问题」。事实一致，需择一或并列注明来源。
2. **每帧落库的严重度口径**：两处分别保留，但两边原报告的严重度标注不一致，需统一。
3. **pager 预加载相关条目是否重叠**：`GalleryScreen` 死清理 / `HomeViewModel` 常驻订阅 / `UserScreen` 启动期加载，核对后合并以免重复计数。

---

## 7. 建议修复批次

- **第 1 批（真 bug，且多为上轮改动引入或漏改）**：竖屏 `resolveTracks` 断链（A4）、Gallery toast 死分支（A1）、SongDetail 重复常驻订阅（A3）、`HelloCardsState` 改 `data class`、补做未落地的 `AgentConfigScreen` 双栏归一（第 2 节）。
- **第 2 批（其余行为 bug）**：A5/A6 一次性事件链路、A7 ThemeViewModel 作用域与缓存、A12/A13/A14 状态与并发、A8/A9 每帧落库、A16/A17 列表与滑杆。
- **第 3 批（形态债）**：AIScreen 单一订阅、HelloCards 卡片数据快照、AgentMonitor 能力快照、IntroScreen/UserScreen/LibrarySettingsScreen 状态聚合、DialogViewModel 出口归一、单双栏实现合并。
- **第 4 批（清理）**：死代码、未使用 import、硬编码文案、重复实现统一。

每批完成后需重跑 `ktlint` / `compileAll` / `testAll`，并**回代码核实改动确已落地**（本轮教训见第 2 节）。

---

## 8. 覆盖范围与未覆盖

**已逐行读完**：`player/pages`(8)、`player/components`(4)、`player/viewmodel`(5)、`AppRoot.kt`/`AppRootViewModel.kt`；`library/pages`(11)、`library/viewmodel`(6)、`library/pages/components`(含 musiclist 共 14)、`playlist/pages`(2)、`playlist/viewmodel`(2)；`agent/chat`(7)、`agent/config`(4)、`agent/monitor`(4)、`agent/shell`(7)、`agent/cards`(14)；`settings/pages`(8)、`settings/viewmodel`(8)、`common/pages`(6)、`common/dialogs`(16)；`common/components`、`common/design`、`common/layout`、`common/navigation`、`common/text`、`common/util`、`common/viewmodel` 共 48；`MainShell.kt`。

**未通读 / 仅定点检索**：`agent/AgentVisuals.kt`、`agent/port/ControllerAgentPorts.kt`、`playlist/UserPlaylistVisibility.kt`；`platform/PlaybackController.kt` 与各平台 `actual` 实现；`domain`/`data` 层实现；`strings.xml` 资源文案；Koin `koinViewModel()` 与 Navigation3 `ViewModelStoreOwner` 绑定实现（故不断言 `SongDetailScreen` 与 `PlayerScreen` 是否共用 owner）；`DialogEvent.kt` 全文（由 settings 侧核实）；`NavDisplay` 场景策略（故 ThemeViewModel 实例数只给「稳态 2、上界依导航深度」的相对表述）；`HelloCardCoverflow` 自绘手势算法数值正确性；`FusionSidebar.kt` 若干疑似未使用 import 未逐个验证，故未列入 C 节。

**验证状态**：清点期间未编译、未运行、未看编译器告警；所有「未使用 import / 死方法 / 不可达分支」为静态检索结论。第 1 节的机械核验是清点开始前单独跑的，与本清点结论互不替代。
