# 7.5 期 · 30 屏逐屏判定台账

> **用途**：§4.2 每屏 1 笔提交的判定记录，收口时随 §4.3 归档。
> **口径**：顺序按 `NavigationGraph.kt` 的 entry 表；每屏对照 `state-audit.md` 判定「有业务状态 / 业务直连 → 改」还是「判定合规 → 代码不动，只留记录」。
> **覆盖缺口**：entry 表 26 条、去重 24 个组合式入口，覆盖不到组合根 / 宿主直挂 / Tab 壳内页 —— 那批在 §二 单列，收口前补扫。

## 一、entry 表顺序

> **施工顺序补记（2026-11-08）**：#4 原判合规，按「逐页就地闭环」口径改判为要改，**作为下一笔回头补**，排在 #8 之前。
> **提交整理（2026-11-09）**：B 组逐页改 4 笔 + C 组回头闭环 3 笔按用户要求合并为一笔 `refactor(udf): P1 逐页改与回头闭环`；A 组模板提交 `0ca250b1` 保持独立。#1–#8 的「提交」列指向该合并提交，#3 样板屏仍属 `0ca250b1`。
> **第二批（2026-11-09）**：#9-#12 四屏按同一口径合并为一笔 `refactor(udf): P1 逐页改 #9-#12`；这四行的「提交」列指向该笔。
> **第三批（2026-11-09）**：#13-#17 五条 entry 合并为一笔 `refactor(udf): P1 逐页改 #13-#17`；#18 起留待下一批，判定与改造不拆成半成品。
> **第四批（2026-11-09）**：#18 改 + #19 判合规，合并为一笔 `refactor(udf): P1 逐页改 #18-#19`。#20/#21 两个 agent 重屏（A 27 处、1325/1719 行）按规划「重的屏单独多花时间」后置到专门一批，不与轻屏混批；#22 起排在重屏之后，不预先判定。
> **第五批（2026-11-09）**：#20 按 §4.2「重的屏可拆 2 笔」先做第一笔（VM 出口收口），单独一笔 `refactor(udf): P1 #20 第一笔 - AI 设置 VM 出口收口`。
> **第六批（2026-11-09）**：#20 第二笔合并 `AIScreenContent` 转发层，单独一笔 `refactor(udf): P1 #20 第二笔 - 合并 AIScreenContent 转发层`。**施工复核后该屏实际要拆 3 笔**（原估 2 笔偏乐观）：第三笔是 A 档表单状态归位 + `MemoryManagementSection` 业务直连搬进 VM，改动面涉及注入 `MasterAgent` 与三端注册，故不与结构调整混在一笔里。进度表 #20 行如实标注为未闭环，不记为已完成。
> **第七批（2026-11-09，用户决议「先做 #22-#26 轻屏」）**：跳过两个重屏先收后面五条 entry，合并为一笔 `refactor(udf): P1 逐页改 #22-#26`；新增 `RecommendListViewModel` 并三端注册同改。
> **第八批（2026-11-09）**：#20 第三笔完成，该屏闭环，单独一笔 `refactor(udf): P1 #20 第三笔 - 表单状态与业务直连归 VM`。**至此 26 条 entry 中 25 条闭环，仅 #21 未闭环**；接下来是 #21、6 个非 entry 屏补扫与 §4.3 收口。
> **第九批（2026-11-09）**：#21 按「重屏拆 2 笔」做第一笔（屏级 VM + 19 处状态模型 + `snapshot()` 边界 + 辅助函数放开到 `internal` + 三端注册），单独一笔 `refactor(udf): P1 #21 第一笔 - Agent 配置屏级 VM`。
> **第十批（2026-11-09）**：#21 第二笔完成 UI 收口，该屏闭环，单独一笔 `refactor(udf): P1 #21 第二笔 - Agent 配置 UI 收口`。**至此 26 条 entry 全部闭环**（#1-#26），下一步转入 §2「非 entry 屏补扫」与 §4.3 收口。

| # | entry | 屏 | 判定 | 提交 |
|---:|---|---|---|---|
| 1 | `Main.Tabs` | `MainShell` | **合规，代码不动** —— 0 A / 1 B，`previousPage` 属「上一帧值」白名单 | — |
| 2 | `Library.SongDetail` | `SongDetailScreen` | **改**：单一 `SongDetailUiState` 出口、`selectedSection` 进 VM、`onRetry()`；跨屏 `PlayContent` 同步传 `.detail` | `refactor(udf): P1 逐页改与回头闭环`（合并提交） |
| 3 | `Library.EditMusicTags` | `EditMusicTagsScreen` | **改**：样板屏，模板提交已完成 | `0ca250b1` |
| 4 | `Player.Player` | `PlayerScreen` | **改（回头补）**：原判合规，按 11-08「逐页就地闭环」口径改判 —— 合并 `PlayContent` 整屏转发层、注入上移到唯一 Screen 入口；2 处 `selectedTab` 仍按滚动/分页白名单留 UI | `refactor(udf): P1 逐页改与回头闭环`（合并提交） |
| 5 | `Settings.Setting` | `SettingScreen` | **合规，代码不动** —— 纯菜单，无 `remember` 业务态、无业务直连 | — |
| 6 | `Settings.ProfileSettings` | `ProfileSettingsScreen` | **改**：新建屏级 `ProfileSettingsViewModel`（单一 `UiState` + `SharedFlow` 效果），A 2 处草稿进 VM、选图收进 VM、三端注册同改 | `refactor(udf): P1 逐页改与回头闭环`（合并提交） |
| 7 | `Settings.BackupSettings` | `BackupSettingsScreen` | **改**：`BackupViewModel` 收成单一 `BackupUiState(localBackups, pendingRestorePath)` + `SharedFlow` 效果；两处重复确认链合并、回调式 API 全删、死状态 `backupResult` 清掉；平台选图/分享收进 VM，三端注册加第 5 个 `get()` | `refactor(udf): P1 逐页改与回头闭环`（合并提交） |
| 8 | `Settings.LibrarySettings` | `LibrarySettingsScreen` | **改**：4 处确认链 `remember`（folderToHide / showFullRescanDialog / showKnownFolderPicker ×2）收进 `LibrarySettingsUiState`；系统目录选择器改走 `SharedFlow` 效果；用户动作统一 `onXxx()` | `refactor(udf): P1 逐页改与回头闭环`（合并提交） |
| 9 | `Settings.LyricsSettings` | `LyricsSettingsPage` | **改**：新建屏级 `LyricsSettingsPageViewModel` + 单一 `LyricsSettingsUiState` —— C 档 `selectedTab` 判为「决定编辑哪份配置」进 VM，A 档三份 config 收成一个 `configs: Map` 并改走长期订阅的 `getComponentConfig` 回流（UI 不做乐观更新，编辑走带锁 `updateComponentConfig`）；悬浮开关、能力位一并入状态；UseCase / PlatformServices / overlay 权限回调全归 VM；删 `LaunchedEffect` 兜底，改 `onSelectTab` 守卫 | `refactor(udf): P1 逐页改 #9-#12`（本批合并） |
| 10 | `Settings.AuditLog` | `AuditLogScreen` | **改**：`logs` / `filter` 两个出口收成 `AuditLogUiState`；`Filter` 提为顶层 `AuditLogFilter`；VM 补三端 `viewModel {}` 注册、页面改 `koinViewModel()`，删掉组合内 `remember { AuditLogViewModel(dao) }`；`applyFilter` → `onFilterSelected`。`AuditLogRow.expanded` 属展开收起白名单，留 UI | 同上 |
| 11 | `Settings.AgentMonitor` | `AgentMonitorScreen` | **改**：新建 `AgentMonitorViewModel`，A 档 `ledgerSnap` + C 档 `showClearConfirm` 收成 `AgentMonitorUiState`；`TokenLedgerMaintenance` 构造与清空/重载移进 VM、三端注册同改；`TokenMonitorCard` 确认链改走 `onClearRequested/Dismissed/Confirmed`。MasterAgent / QuotaGate / MemLogWriter 的运行时订阅属跨屏投影白名单留 UI；`CardLogSection.showDetail` 属展开白名单留 UI | 同上 |
| 12 | `Library.Search` | `SearchScreen` | **改**：`SearchUiState(query, showIntentStrip, results)` 收掉 A 档输入框、C 档条带偏好与 `SearchViewModel` 的旧 `searchState` 出口；按键统一 `onQueryChange`（删 `LaunchedEffect(query)` 二次触发）、「交给伙伴」走 `SharedFlow<SearchEffect>`；**按 §7 合并现存 `SearchScreenContent` 转发层**；`SearchDebounceTest` 四条判据同步改读 `uiState.value.results`，防抖/竞态契约不变 | 同上 |
| 13 | `Playlist.Playlist` | `PlaylistScreen` | **改**：`PlaylistViewModel` 的 5 个散装 StateFlow 出口全部转私有，`playlistUiState` 成为唯一出口（数据态 `combine` + 屏级交互态再 `combine`）；A 档 `renameValue` / `descriptionValue` 复核为「`InputDialog` 的 initialValue」，直接由歌单元数据派生，不再落地成状态；`showRenameDialog` / `showDescriptionDialog` / `isListEditMode` 进屏级交互态，动作走 `onXxx()`、离屏 `onPlaylistScreenLeft()` 清理；**按 §7 合并 `PlaylistScreenContent` 转发层**。滚动/折叠偏移/动画派生值留 UI | `refactor(udf): P1 逐页改 #13-#17`（本批合并） |
| 14 | `Playlist.CustomPlaylist` | `PlaylistScreen` | 与 #13 同屏，随该笔一起闭环 | 同上 |
| 15 | `Playlist.UserPlaylistManage` | `PlaylistManageScreen` | **改**：五态联动状态机收成 `PlaylistManageUiState(isEditMode, pendingDelete, pendingRename, renameValue, renameError)`；待删/待改名对象同时表达弹窗可见性，`onManageRenameDismissed()` 一次清三态；`renamePlaylist(id, name, onResult)` 的 UI 回调消失，错误码由 VM 写进状态、UI 只翻文案；离屏 `onManageScreenLeft()` 清理 | 同上 |
| 16 | `Library.Artist` | `ArtistScreen` | **改**：`ArtistAlbumViewModel` 的 4 个散装出口收成 `artistUiState` / `albumUiState` 两个聚合出口（每屏只订阅自己那个，不依赖「两屏实例必然隔离」）；**合并 `ArtistScreenContent` 转发层**；本屏无 `remember` 业务态，判定后只动分层与出口 | 同上 |
| 17 | `Library.Album` | `AlbumScreen` | **改**：与 #16 对称，订阅 `albumUiState`、合并 `AlbumScreenContent` 转发层 | 同上 |
| 18 | `Player.AudioEffects` | `AudioEffectsScreen` | **改**：`AudioEffectViewModel` 的 5 个散装 StateFlow 收成单一 `AudioEffectsUiState`（`combine` + `stateIn`，首帧取各源 StateFlow 的 `.value`，不另造默认状态）；**按 §7 合并 `AudioEffectsScreenContent` 转发层**。台账记的 A 2 处 `sliderValue` 复核为滑杆拖动中间态（与 `isDragging` 同组），按 §5 白名单留 UI，不制造 diff | `refactor(udf): P1 逐页改 #18-#19`（本批合并） |
| 19 | `Player.Lyrics` | `LyricsScreen` | **合规，代码不动** —— 3 处 `remember`（`isSettingsPanelVisible` / `isControlsVisible` / `lastInteractionTime`）全属展开收起与动画时间戳白名单；歌词 / 播放 / 主题态属组合根投影；无 content 转发层、无业务直连 | — |
| 20 | `AI.AI` | `AIScreen` | **三笔完成，该屏闭环**：① 9 个散装出口 → 单一 `AiSettingsUiState`；② 合并 20 参数转发层、删 8 个死参数；③ 10 处 A 档表单草稿收进 `AIScreenFormState`，`MasterAgent` 业务调用（`getGlobalAgentConfig` / `saveGlobalAgentConfig` / `forgetPortrait` / `clearAllMemory` / `currentPortraits`）全归 VM 并注入三端注册；B 档 4 处（`showPassword` / `presetExpanded` / `modelExpanded` / `expanded`）留 UI | 第一笔 `...VM 出口收口`；第二笔 `...合并 AIScreenContent 转发层`；第三笔 `refactor(udf): P1 #20 第三笔 - 表单状态与业务直连归 VM` |
| 21 | `AI.AgentConfig` | `AgentConfigScreen` | **两笔完成，该屏闭环**：① 新建 `AgentConfigViewModel` + `AgentConfigUiState`（19 处 A 档状态），`config` 持 `AgentPolicyConfig.snapshot()` 不可变副本而非 `MasterAgent` 内部活实例（§1.3），加载/保存/重置搬进 VM，`agentRole` 由 `onRoleChanged()` 上送；② UI 主函数改订阅 `uiState` 并解包成同名局部变量，`save`/`reset` 走 `onSave`/`onReset`，横竖屏两套布局里 23 处直接赋值改 `onXxx()`，VM 补 5 个 prompt 编辑动作；`expandedPromptKey` 属展开白名单留 UI | 第一笔 `...Agent 配置屏级 VM`；第二笔 `refactor(udf): P1 #21 第二笔 - Agent 配置 UI 收口` |
| 22 | `Custom.Custom` | `CustomScreen` | **改**：合并 14 参数 `CustomScreenContent` 转发层回唯一 Screen 入口（§7）；7 个外观设置值来自被多屏共享的 `SettingsViewModel`，按 §2 第 7 条属跨屏配置投影白名单，不强行聚合；本屏无 `remember` 业务态 | `refactor(udf): P1 逐页改 #22-#26`（本批合并） |
| 23 | `UserData.UserUsageData` | `UserUsageDataScreen` | **改**：`UserUsageDataViewModel` 的 4 个散装出口收成 `UserUsageDataUiState`（`combine` 4 路 + `stateIn`），本屏与 `UserScreen` 同步改订阅；`XxxWindowContent` 是按窗口拆的区块渲染组件、不是整屏转发层，保留；`scrollState` 留 UI | 同上 |
| 24 | `Companion.Chat` | `ChatScreen` | **改**：合并 10 参数 `ChatScreenContent` 转发层；`ChatUiState` 已是整屏状态无需重写，`quotaBlocked` 判为全局配额门跨屏投影白名单，`inputFocused` / `prevHeight` 属焦点与上一帧值白名单留 UI | 同上 |
| 25 | `Recommend.Daily` | `RecommendListScreen` | **改**：新建屏级 `RecommendListViewModel`（`onSourceChanged(source)` 上送导航参数 + 三端注册同改），把 `produceState` 里的 `MusicRepository.getMusicInfoByIds()` hydrate 与 payload 订阅收成 `RecommendListUiState`；封面取色 `palette` 属视觉派生白名单留 UI，播放队列与导航属 UI 门面 | 同上 |
| 26 | `Recommend.Private` | `RecommendListScreen` | 与 #25 同屏，随该笔一起闭环 | 同上 |

## 二、非 entry 屏（收口前单独补扫）

| 屏 | 挂载点 | 判定 | 备注 |
|---|---|---|---|
| `AppRoot` | 组合根（772 行） | 待判定 | `state-audit.md` §五：`savedTabIndex` / `currentNotice` / `noticeIdCounter` / `messageToShowState` 宜进组合根 VM；独占 1 笔 |
| `IntroScreen` | Android `MainActivity.kt:129` / Desktop `Main.kt:339` / iOS `IosAppRoot.kt:50` | 待判定 | 三端宿主直挂、在 `AppRoot` 之前；A 1 + C 3（全倾向 A），隐式状态机 + 权限回调；大概率新建 `IntroViewModel` → 触发三端注册护栏，独占 1 笔 |
| `HomeScreen` | `MainShell` Tab 0 | 待判定 | |
| `GalleryScreen` | `MainShell` Tab 1 | 待判定 | C 档成对确认链 + `deleteCounter` 刷新触发器 |
| `ListScreen` | `MainShell` Tab 2 | 待判定 | |
| `UserScreen` | `MainShell` Tab 3 | 待判定 | |

> 上表是已核实的非 entry 项。30 屏口径的其余差额随施工补登，不预先编造清单。

## 三、每笔必查

- `bash ./ktlint check` = 0 处 + `./gradlew-smart compileAll` 绿
- 新增 / 拆分 VM → Android / Desktop / iOS 三处 `viewModel {}` 同改
- 重屏与状态机屏补 VM 级用例；`commonTest` 优先
- 判定合规的屏只留记录，**不制造 diff**

## 四、逐页施工中登记的缺口

- #7 `BackupSettingsScreen`：D7.md:377 的「导入/导出无互斥、无进行中状态」**已闭环** —— `BackupUiState.isBusy` 守卫 + `finally` 释放 + UI 按钮禁用与进度圈，见 §五 #2。
- #7 构造新增 `PlatformServices`：三端注册已同改，但本机 `compileAll` 不编译 iOS target，对称性靠两个 GraphVerificationTest + 人工。
- `UserScreen.kt:181` 换头像后头像不刷新：**已闭环** —— `UserSettingsUseCase`（Koin single）持有唯一 `avatarUri: StateFlow<String>` 镜像，写入即回流，`SettingsViewModel.avatarUri` 直接订阅，`UserScreen` 自动刷新；见 §五 #3。
- iOS target 本机不编译，新增 `ProfileSettingsViewModel` 的三端注册对称性靠人工 + 两个 GraphVerificationTest（Android / Desktop）。

**第二批（#9-#12）登记**：

- #9 C 档 `selectedTab` 复核结论：它决定「编辑哪一份组件配置」，不是纯切渲染块，按 `state-audit.md` §四「决定是否加载数据」判为进 VM。
- #9 三份配置改为长期订阅回流后 UI 不再乐观更新：连点加减号靠 `LyricsSettingsUseCase.updateComponentConfig()` 的 Mutex 读-改-写保证不丢值，无需在 VM 里再做乐观态。
- #9 / #11 各新增一个 VM，#10 补注册既有 VM，三处 `viewModel {}` 均 Android / Desktop / iOS 同改；本机 `compileAll` 不编译 iOS target，对称性靠人工 + 两个 GraphVerificationTest，属环境限制（同 §五 口径）。
- #11 的 MasterAgent / TokenQuotaGate / MemLogWriter 运行时订阅是跨屏共享数据流，按 `UdfConventions.md` §2 投影白名单留 UI，未为了「单一出口」把 143 处订阅复制进本屏。
- #12 `SearchScreenContent` 是现存 7 个 content 转发层之一，按 §7 在该屏这一笔里合并，未留形态债；`SearchDebounceTest` 是唯一受 API 变更影响的测试，四条判据改读 `uiState.value.results` 后契约不变。
- **未补 VM 级用例**：#9 / #10 / #11 三屏无 `commonTest` mock 基建（#12 有 `SearchDebounceTest` 覆盖防抖竞态），靠 `compileAll` / `testAll` / review 把关，如实登记不假装闭环。

**第三批（#13-#17）登记**：

- #13 的重命名 / 描述两个弹窗在当前 UI 里**没有打开入口**（既有死代码，实际入口是 `showEditPlaylistDialog`）；`isListEditMode` 也当前无消费方。本笔只收状态归属与分层，不删 UI、不制造无关 diff，如实登记。
- #13 `loadAllMusicForAddPicker { ... }` / `addItemsToPlaylist(onComplete = ...)` 两处 continuation 保留在组合点：回调结果只用于继续 UI 流程（弹出选曲对话框 / 显示 toast），不写回业务状态，按 `UdfConventions.md` §3 的判据不属于「VM 回调式 API」。
- #13 / #15 共用 activity 作用域的 `PlaylistViewModel`：屏级交互态按屏拆成 `playlistUiState` / `manageUiState` 两个出口，各自 `onPlaylistScreenLeft()` / `onManageScreenLeft()` 清理；`userCustomPlaylistsState` / `systemPlaylistIds` 是跨屏共享数据投影白名单，仍由两屏订阅。
- #16 / #17 的 `getSelectedXxxMusicList(name)` 保留原名：它是 `LaunchedEffect(key)` 的生命周期加载，不是用户动作，符合 §3；两个聚合出口拆开是为了不依赖「两屏 VM 实例必然隔离」这一前提。
- **未补 VM 级用例**：#13-#17 无 `commonTest` mock 基建（Playlist 域另有 Robolectric 构造坑，D3-06），靠 `compileAll` / `testAll` / review 把关，如实登记。

**第四批（#18-#19）登记**：

- #18 判定改判：`state-audit.md` §二 把 2 处 `sliderValue` 记为 A 档，逐条复核后判为 §5「拖动中间态」B 档；`EqualizerBand` 的 `remember(currentLevel)` 会随外部值重置本地拖动值，是刻意的同步行为，保留不动。规则已补进 `UdfConventions.md` §5。
- `AudioEffectsUiState` 含 `FloatArray`，`data class` 的 `equals` 是引用比较；播放控制器每次推送的都是新数组，`StateFlow` 仍能正常触发重组，无需改成 `List`，此处登记以免后人误判为缺陷。
- #18 的 5 个源都是 `StateFlow`，`stateIn` 首帧直接取各自 `.value`，没有另造一份默认值口径。
- #19 按 §1.2「覆盖 ≠ 重写」只留判定记录，**代码零 diff**。
- #20/#21 两个 agent 重屏后置到专门一批，本节不预判其结论；#22 起也排在重屏之后，不预先编造判定。
- **未补 VM 级用例**：#18 无 `commonTest` mock 基建，靠 `compileAll` / `testAll` / review 把关，如实登记。

**第五批（#20 第一笔）登记**：

- `AiSettingsViewModel` 原有 9 个 public `StateFlow`，全部转为私有数据源，只暴露 `uiState`；`combine` 最多 5 路，拆成 `baseUiState`（5 路）与最终 5 路再 `copy`，没有引入数组转换或第二套状态类型。
- `AiSettingsUiState.quotaUsage` 的默认值是 `TokenSnapshot(0L, 0L)`（`TokenSnapshot` 无无参构造）；`stateIn` 的初值显式取 `quotaUsage.value`，不会真的用到这个默认值口径。
- 主函数把 `uiState` 解包成与原先 `collectAsState()` 同名的局部变量；除 `TokenLimitsCard` 一处订阅改成 `vm.uiState.collectAsState().value.globalConfig` 外，`AIScreenContent` 及其下游子区块未改。这是刻意把第一笔的 diff 限制在 VM + 两处订阅点，便于 review。首轮编译曾因这处遗漏报 `Unresolved reference 'perAgentDailyTokenQuota'`，已修。
- **该屏未闭环，不算已完成**：20 参数 `AIScreenContent` 转发层、`CustomConfigContent` / `TokenLimitSection` / `MemorySection` 里的 8 处 A 档表单状态、`showPassword` / `presetExpanded` / `modelExpanded` 等 B 档复核，全部留给 #20 第二笔。按 §4.2 拆笔，不是把形态债推给 §4.3 收口。
- #21 尚未开工，`AgentConfigScreen` 直接持有 `MasterAgent` 返回的可变 `AgentPolicy` 实例，第二笔须按 §1.3 用 `snapshot()` 隔断，并补一条「reset 后展示值 = 新实例值」的回归用例。
- **未补 VM 级用例**：本笔只做出口聚合，靠 `compileAll` / `testAll` / review 把关，如实登记。

**第六批（#20 第二笔）登记**：

- 合并做法：删掉旧 `AIScreen` 主函数，把 20 参数的 `AIScreenContent` 签名换成主函数签名 + 状态订阅 + 局部 `onXxx` lambda，body 一行未改；这样 diff 集中在两个函数头，便于 review。`@OptIn(ExperimentalMaterial3Api::class)` 随 body 移到唯一 Screen 入口上。
- **删掉 8 个死参数**：`musicWithExtraCount` / `progress` / `autoBatchProcess` / `onAutoBatchProcessChange` / `startAutoProcessExtraInfo` / `pauseProcess` / `resumeProcess` / `cancelProcess` 从 Screen 一路透传到 Content 却从未被消费，合并时一并清除；`libraryViewModel` 参数暂时保留（调用点仍按默认注入，避免顺带改导航图），仅作 warning 级未使用。
- **B 档逐条复核（留 UI）**：`showPassword`（密码可见性）、`presetExpanded` / `modelExpanded`（下拉展开）、`expanded`（折叠态）均属 §5「展开收起 / 视觉可见性」。
- **A 档逐条复核（第三笔搬）**：`endpointValue` / `apiKeyValue` / `modelValue`、`enabled` / `globalLimit` / `perAgent` 三组表单草稿，`portraits`（业务数据）、`showConfirmClear`（成对确认链）、`memoryEnabled`（持久化开关）—— 复核后约 **10 处**，比 `state-audit.md` 记的 8 处多识别出 `perAgent` 与 `saved`。
- `saved` 复核：本质是「已保存」的一次性提示，不该做成布尔状态，第三笔按 §4 走 `SharedFlow` 效果而不是塞进 UiState。
- **第三笔的额外改动面**：`MemoryManagementSection` 里 `masterAgent.userMemory.currentPortraits()` 与 `saveGlobalAgentConfig()` 是 composable 直连业务对象（§3），要闭环就得给 `AiSettingsViewModel` 注入 `MasterAgent` 并同步改三端 `viewModel {}` 注册；这与结构调整是不同的验证面，故单独成笔。
- 本笔只做结构合并，`compileAll` 已绿；`testAll` 随本批提交一起跑，未补 VM 级用例，如实登记。

**第七批（#22-#26）登记**：

- **两处回归及修复**（首轮 `compileAll` 报错）：① `UserScreen` 也在订阅 `UserUsageDataViewModel.windowed` / `timeRange`，VM 出口转 private 后编译失败 —— 已让 `UserScreen` 同步改订阅 `uiState`，保持单一出口而不是把两个 Flow 重新公开；② `ChatScreen` 合并后 `state` 变成委托属性，`state.pendingConfirm != null && !state.pendingConfirm.submitted` 的 smart cast 失效 —— 已改成 `collectAsState().value` 非委托订阅。
- #22 的 7 个设置值、`ChatScreen` 的 `quotaBlocked`、#25/#26 的 `payload` 都判为**跨屏共享数据流投影白名单**（§2 第 7 条）：它们来自被多屏共享的 `SettingsViewModel` / `TokenQuotaGate` / `MasterAgent`，为凑「单一出口」复制进本屏会造成多份口径，故不强行聚合，如实登记。
- #25/#26 的 `produceState` 只保留 `palette`（封面取色 → 整页深色基调，纯视觉派生）；仓库 hydrate 与 payload → items 的派生归 VM。播放队列追加与导航仍在组合点（UI 门面）。
- **`RecommendListViewModel` 不把 `source` 放进构造参数**：首版用 `parametersOf(source)`，首轮 `testAll` 的 `DesktopKoinGraphVerificationTest` 报 `MissingKoinDefinitionException: Missing definition for '[field:'source' - RecommendSource]'`（verify 无法解析 parametersOf 注入的类型）。已改为无参构造 + `onSourceChanged(source)` 由 Screen 在 `LaunchedEffect(source)` 上送，三端注册恢复普通 `viewModel { RecommendListViewModel(get(), get()) }`，图验证可用。本机不编译 iOS target，对称性靠人工 + 两个 GraphVerificationTest，发行前用 macOS 验。
- #23 的 `XxxWindowContent`（Overview / Taste / Ranking / Recent）是区块渲染组件而不是整屏 content 转发层，按 §7 允许保留。
- **测试同步**：`UserUsageDataWindowTest` 有 7 处直读 `vm.windowed.value`，已改成 `vm.uiState.value.windowed`；同时把 `UserUsageDataViewModel.uiState` 的 `stateIn` 从 `WhileSubscribed(5000)` 改成 `Eagerly` —— 前者在无订阅者时不收集，`.value` 只会返回初始空 bundle，测试会拿到假失败。四个源都是本地 `MutableStateFlow`，常驻收集无额外开销。
- **未补 VM 级用例**：本批 5 条 entry 无 `commonTest` mock 基建，靠 `compileAll` / `testAll` / review 把关，如实登记。

**第八批（#20 第三笔，该屏闭环）登记**：

- `AIScreenFormState` 收 10 处 A 档草稿（endpoint / apiKey / model、限额开关与全局/逐 Agent 限额、记忆开关与侧写列表、清空确认、回复语言）；`_formState` 单独一路 `combine` 进 `dataUiState`，**数据态刷新不会把用户正在编辑的草稿覆盖回默认值**（这是把表单字段直接塞进 `baseUiState` 会踩的坑）。
- **`saved` 二次复核改判**：它是「自上次编辑后已保存」的持续可见表单状态，不是一次性事件，故保留为 `tokenLimitsSaved` 布尔字段，不做 `SharedFlow` 效果；§六 第六批里「应走效果」的初判作废，以本条为准。
- `MasterAgent` 注入 `AiSettingsViewModel`（三端 `viewModel {}` 各加第 4 个 `get()`）；记忆区的 `getGlobalAgentConfig` / `saveGlobalAgentConfig` / `forgetPortrait` / `clearAllMemory` / `currentPortraits` 与回复语言区的保存全部改走 VM 的 `onXxx()`，composable 不再直连业务对象。
- `AiAccessSection` → `CustomConfigContent` 显式逐层传 `vm`（同一实例），没有让子区块自己 `koinViewModel()`，避免不同 ViewModelStoreOwner 拿到不同实例。
- `loadCustomAiConfig()` 现在同步 endpoint / model 回 form，UI 里 `LaunchedEffect(customConfig) { modelValue = ... }` 的二次回填已删；保存限额沿用原 `takeIf { it > 0 }` 口径，行为不变。
- B 档 4 处（`showPassword` / `presetExpanded` / `modelExpanded` / `expanded`）按 §5 留 UI，未制造 diff。
- **未补 VM 级用例**：本笔涉及 `MasterAgent` / `UserSettingsUseCase` 等多个依赖，无 `commonTest` mock 基建，靠 `compileAll` / `testAll` / review 把关，如实登记。

**第九批（#21 第一笔）登记**：

- `AgentConfigUiState` 共 21 个字段，覆盖原 `AgentConfigScreen` 主函数里 19 处 A 档 `remember`；`resolved` 作为派生 getter，随 `config` + `role` 重算。
- **§1.3 边界落实**：`config` 持 `getAgentPolicyConfig(role).snapshot()` 的**不可变副本**，保存时在副本上改再 `saveAgentPolicyConfig(role, cfg)` 整体替换，不再原地写 `MasterAgent` 内部活引用；保存/重置后重新 `snapshot()` 回填，避免「reset 换新实例后 UI 仍指旧实例」。
- `agentRole` **不放进 VM 构造参数**，由 `onRoleChanged(role)` 上送 —— 沿用 `RecommendListViewModel` 踩过的坑：`parametersOf` 注入会让三端 `GraphVerificationTest` 报 `MissingKoinDefinitionException`。
- 5 个 prompt 辅助函数（`defaultPromptKeyFor` / `helloPromptGroups` / `isMultiPromptAgent` / `effectivePromptFor` / `resolveEffectivePrompt`）与 `String.extractPlaceholders()` 由 `private` 放宽到 `internal` 供同模块 VM 复用，**没有复制第二份解析逻辑**。
- `expandedPromptKey` 判为 §5「展开收起」白名单，留 UI，不进 VM。
- **第二笔清单**：主函数改订阅 `uiState` 并把字段解包成同名局部变量（body 其余不动）；`val save = { viewModel.onSave() }` / `val reset = { viewModel.onReset(); expandedPromptKey = null }`；约 30 处 `onXxxChange = { field = it }` 改成 `viewModel::onXxxChange`；`LaunchedEffect(agentRole)` 改成 `viewModel.onRoleChanged(agentRole)`。
- **未补 VM 级用例**：本笔只建状态模型与逻辑，无 `commonTest` mock 基建，靠 `compileAll` / `testAll` / review 把关，如实登记。

**第十批（#21 第二笔，该屏闭环）登记**：

- UI 主函数把 `uiState` 解包成与旧 `remember` 同名的局部变量（`policyConfig` / `resolved` / `temperature` / `systemPrompt` …），body 里 1000 余行引用一行未改；`save` / `reset` 两个 lambda 直接委托 `viewModel.onSave()` / `onReset()`，重置后额外清 `expandedPromptKey`。
- **23 处直接赋值改成 `onXxx()`**：其中横屏分支有一套与 `ConfigFormLeftColumn` 重复的简化表单（身份开关 / 温度 / endpoint 三连 / 信任档 / 四组专属参数），逐一改走 VM；另补 5 个 prompt 编辑动作（`onRestoreFactoryPrompt` / `onDiscardPromptChanges` / `onMultiPromptEntryChange` / `onMultiPromptRestoreFactory` / `onMultiPromptRevert`）。
- **施工教训**：首轮编译报 25 处 `'val' cannot be reassigned`。原因是前一轮只扫了 `onXxxChange = { field = it }` 形态，漏掉 body 里 `Switch(onCheckedChange = { field = it })`、`Slider(onValueChange = { field = ... })`、`TextButton(onClick = { field = ... })` 等**同一变量的其它赋值入口**。把 `remember` 状态上移到 VM 时，必须按变量名全量扫赋值点，不能只扫 `onXxxChange` 命名；这条已写进本节，后续屏直接照做。
- `expandedPromptKey` 属 §5「展开收起」白名单，留 UI；`factoryPrompt` / `helloEntries` / `multiPrompt` / `hasOverride` 是派生值不是状态，也留 UI。
- **至此 26 条 entry 全部闭环**。下一步按 `UdfConventions.md` §6「非 entry 屏补扫」处理 `AppRoot` / `IntroScreen` / 4 个 Tab 屏，再进 §4.3 收口。
- **未补 VM 级用例**：本笔只做 UI 收口，无 `commonTest` mock 基建，靠 `compileAll` / `testAll` / review 把关，如实登记。

## 五、回头闭环队列（11-08 口径：不推给收口）

| 序 | 项 | 处理 |
|---:|---|---|
| 1 | #4 `PlayerScreen` / `PlayContent` 整屏转发层 | **已闭环**：合并为唯一 `PlayerScreen` 入口、删除 `PlayContent.kt`、注入上移 |
| 2 | #7 `BackupSettings` 导入/导出无 `isBusy` 防重入（D7.md:377） | **已闭环**：导出/恢复/删除/刷新统一 `launchExclusive` 守卫，异常也经 `finally` 释放；UI 禁用 + 进度圈 |
| 3 | #6 / `UserScreen` 换头像后头像不刷新 | **已闭环**：UseCase 共享镜像 + `saveAvatarUri` 回流 + `getAvatarUri` 回填；新增 `UserSettingsUseCaseAvatarTest` 两条判据 |

> 环境限制类（iOS target 本机不编译）不在此列：三端注册对称性靠两个 GraphVerificationTest + 人工，发行前用 macOS 验，属发行前置，不是形态债。
- #7 防重入未补 VM 级并发自动用例：该屏无测试基建（`commonTest` 无 mockk，`desktopTest` / `androidHostTest` 有），该笔靠守卫 + UI 禁用 + review 把关。
- 头像镜像边界：备份恢复等直接写 DataStore 的路径绕过 `UserSettingsUseCase`，靠 `UserScreen` 的 `LaunchedEffect(Unit) { getAvatarUri() }` 回填；`commonTest` 已覆盖此路径。

- 该笔顺带修 `shared/src/desktopMain/.../BackupFileRepositoryImpl.desktop.kt` 的既有缺陷：`ownedFile()` 返回 `canonicalFile`，macOS 上临时目录 `/var` 是 `/private/var` 的符号链接，导致幂等导入返回路径与 `saveBackup` 口径不一致、误再复制一份（`BackupFileRepositoryImplTest:108` 稳定失败）。改为 canonical 判断安全性、返回原始 `File`。证据：该实现与测试文件在该笔改动清单里无交集，测试 setup 只用临时目录、不依赖 UseCase。
- `testCore` 全量并行时 `AgentSchedulerTest` 曾偶发失败一次；单独重跑该类全绿，随后全量 `testCore` + `testUi` 全绿。属既有 flaky（Agent 运行时并发），归 P2 / 7.6，不在 7.5 P1 范围。

## 六、#8 取舍登记（11-08）

- `LibraryViewModel` 是 activity 作用域、8 个依赖、被多屏共享的全局 VM。散装数据出口（`musicCount` / `scannedFolders` / `hiddenFolders` / `scanDirectoryConfig` / `isScanning`）保留为**跨屏共享数据投影白名单**，未强行聚合进整屏状态；屏级交互态单独用 `settingsUiState` 一个出口，并 `onScreenLeft()` 清理，避免把弹窗带到别的页面。规则见 `UdfConventions.md` §2。
- 该屏**无整屏 content 转发层**（只有局部 Section），符合 §7。平台只读能力查询（`hasMusicReadAccess` / `directorySelectionMode`）留在 UI 门面；系统目录选择器 `pickDirectory` 经 `LibrarySettingsEffect.PickDirectory` 由组合点消费，VM 不持有 `PlatformServices`，因此三端注册无需改动。
- 其余既有业务动作（`refreshMusicList` / `restoreToLibrary` / `removeScanDirectory` 等）仍直接绑 VM 既有方法，未统一改名；本次只把涉及确认链与目录操作的动作收敛为 `onXxx()`。属「覆盖 ≠ 重写」，如实登记。

## 七、非 entry 屏补扫（11-09 起）

> `UdfConventions.md` §6：`NavigationGraph.kt` 的 entry 表只有 26 条，覆盖不到组合根 / 宿主直挂 / Tab 壳内页。按「跑完 entry 表再补扫一遍」执行，逐屏登记。

| 序 | 屏 | 判定 | 提交 |
|---:|---|---|---|
| 1 | `AppRoot` | **改**：`currentNotice` / `noticeIdCounter` 是 PresenceBus 事件的 UI 投影（`state-audit.md` §四判 A），已收进新建组合根 `AppRootViewModel` 的单一 `AppRootUiState`；`AgentNoticeBar` 改读 `appRootState.notice`、dismiss 走 `onNoticeDismissed()` | `refactor(udf): P1 非 entry 屏补扫 - AppRoot 通知投影归 VM` |
| 2 | `ListScreen` | **改**：合并 9 参数 `ListScreenContent` 转发层回唯一 Screen 入口（§7）；5 个标签列表与自建歌单来自共享的 `PlaylistViewModel`，按 §2 第 7 条作投影白名单订阅；本屏无 `remember` 业务态 | `refactor(udf): P1 非 entry 屏补扫 - Tab 壳内页转发层` |
| 3 | `UserScreen` | **改**：合并 6 参数 `UserScreenContent` 转发层；`userName` / `avatarUri` / `listeningData` 是跨屏投影，`windowed` / `rangeLabel` 来自 `UserUsageDataViewModel.uiState`；`D5-09` 窗口语义注释随 `rangeLabel` 一起搬到主函数 | 同上 |
| 4 | `HomeScreen` | **合规，代码不动** —— 无屏级 `remember` 业务态、无 content 转发层，外层是 `TabScreen` 壳，数据全部来自共享 VM 投影 | — |
| 5 | `GalleryScreen` | **改**：合并 21 参数 `GalleryScreenContent` 转发层；两组成对确认链 `(bool + 待决对象)` 合并为 `GalleryUiState.pendingRemove` / `pendingBatchRemove`（有值即显示，dismiss 一次清干净），`deleteCounter` 改 `deleteToken` 作列表重建动画 key；状态收进 `LibraryViewModel.galleryUiState`（沿用 `settingsUiState` 先例）+ `onGalleryScreenLeft()` 离屏清理 | `refactor(udf): P1 非 entry 屏补扫 - Gallery 确认链归 VM` |
| 6 | `IntroScreen` | **改**：4 处阶段态收进新建 `IntroViewModel` 的单一 `IntroUiState`；「权限授予 → 自动进入下一步」的推进从 `LaunchedEffect(isPermissionGiven)` 移进 `onPermissionResult()`，UI 只上送回调；`aiAccessMode` / haze 系列判跨屏投影白名单留原样 | `refactor(udf): P1 非 entry 屏补扫 - Intro 阶段态归 VM` |

**AppRoot 补扫登记**：

- 组合根 VM 只装「组合根自己拥有」的 UI 状态；播放态、主题态等跨屏投影仍按 §2 第 7 条留在各处，没有为了凑单一出口把全局播放状态复制进 `AppRootUiState`。
- `messageToShowState` 复核结论：它是 `DialogEvent.Message` 的渲染缓存（`MessageToast` 内部按 `id` 做显示/退场动画），不是业务状态；进 VM 会与既有 `DialogEvent` 通道重复，按 §5 白名单保留。
- `savedTabIndex` 是 `pagerState.currentPage` 的 `rememberSaveable` 镜像（配置重建后恢复 tab），属导航态白名单；若后续要求跨进程恢复，再迁 `SavedStateHandle`，不在本阶段范围内。
- 三端 `viewModel { AppRootViewModel(get()) }` 同改；iOS target 本机不编译，对称性靠人工 + 两个 GraphVerificationTest。

## 八、§4.3 收口记录（11-09）

| 项 | 状态 |
|---|---|
| `scripts/check-udf.py` | **已建**：三条机械规则（整屏 content 转发层 / 成对确认链 / composable 直连业务对象），按注入类型判定不按关键字；平台门面、卡片渲染、跨屏投影白名单逐条登记在脚本内 |
| CI 挂载 | **已挂** `pr-check.yml` 的 `lint` job（`python3 scripts/check-udf.py`）；`preflight` 的 `needs` 已含 `lint`，红则拦合入 |
| 首次全量扫描 | 3 处：`HelloCardRadioStatus` 直连 `MasterAgent`（判卡片渲染白名单，逐文件登记）、`PlaylistViewModel` 两个跨屏投影出口（判白名单）、`ArtistAlbumViewModel` 是脚本误报（已修判定逻辑：按屏拆多个 `XxxUiState` 出口是允许的）。重跑**通过** |
| `UdfConventions.md` | §6 的「门禁过完 30 屏再开」改为「已全量开」，写明三条规则与白名单口径 |
| `AGENTS.md` | 7.5 状态由「已建、未开工」更新为「26 entry + 6 非 entry 屏全部闭环，门禁已挂 CI」 |
| 版本 | 本行原记 `75000` / `7.5.0`：11-09 时是 `gradle.properties` 被提前手改的值，真源仍 `7.4.0`（见下方 11-10 / 11-11 订正）；**11-11 已做只标注 bump**，`release.toml` 与 9 个派生点归一为 `7.5.0 / 75000` |

**未做 / 留给发行前置**：

- iOS target 本机不编译，三端注册对称性靠两个 GraphVerificationTest + 人工，发行前用 macOS 验（既有口径，不算形态债）。
- `DEVELOP.md` 在仓库里不存在（`AGENTS.md` 是 AI 协作者唯一真源），故「同步 DEVELOP.md」一项无对象，如实登记。
- 门禁只覆盖可机械判定的三条；「状态该不该进 VM / 派生值是否合理」仍靠 review 清单，脚本不假装能判。
- VM 级用例缺口已在各批如实登记（无 `commonTest` mock 基建的屏靠 `compileAll` / `testAll` / review 把关），收口不补造测试。

**11-10 订正（两处台账失真）**：

- 「`DEVELOP.md` 在仓库里不存在」**与事实不符**：该文件受 git 跟踪（最近一次改动为 `ed2c829 docs: UDF 规划定稿`）。它只是尚未写入 7.5 的 UDF / VM 约定 —— 约定真源仍是 `UdfConventions.md` + 本台账，`AGENTS.md` 的文档索引保持原样。
- 「版本 `75000` / `7.5.0`」**当时不是真源事实**：`release.toml` 自 `eab94fb bump: 7.4.0` 起一直是 7.4.0，但收口提交 `6df3409` 曾**手改派生点** `gradle.properties` 为 `75000 / 7.5.0`（连同 `BUILT_IN_AI_MODEL=deepseek-flash`），其余 8 个派生点仍是 7.4.0 —— 这正是 11-11 bump 前 `sync --check` 报 1 处不一致、`inspect` 的 `drift` 含 `gradle.properties` 的原因。**11-11 补记**：用户决意做一笔只标注 bump 后，`release.toml` 改为 7.5.0 并跑 `sync --write`，手改的 `gradle.properties` 值恰好与真源一致、漂移归零（脚本对该文件显示「跳过」）；本条「bump 属发行前置」的口径**已被 11-11 用户决议取代**，见 §十四。

## 九、审计清单逐条核销（11-09，用户质疑后补）

> **背景**：此前只按屏施工、没有回头对 `state-audit.md` 逐条核销，导致「看起来全做完」。本表是唯一核销口径：每个审计项必须落在「已进 VM / 改判白名单（附理由）/ 未处理」三态之一。

### A 档 45 处

| 审计项 | 处数 | 核销 | 说明 |
|---|---:|---|---|
| `AgentConfigScreen` | 19 | **已进 VM** | `AgentConfigUiState` 21 字段（含 `systemPrompt` / `multiPromptTexts`） |
| `AIScreen` | 8 | **已进 VM** | `AIScreenFormState`（`endpoint` / `apiKey` / `model` / `tokenLimitsEnabled` / `tokenLimitsSaved` / `replyLanguage` / `portraits` / `memoryEnabled`） |
| `PlaylistManageScreen` | 4 | **已进 VM** | `PlaylistManageUiState` |
| `LyricsSettingsPage` | 3 | **已进 VM** | `configs: Map<LyricsComponent, ...>` |
| `ProfileSettingsScreen` | 2 | **已进 VM** | `uriImg` / `name` 已不在 composable 里 |
| `AgentMonitorScreen` | 1 | **已进 VM** | `ledgerSnap` 已不在 |
| `IntroScreen` | 1 | **已进 VM** | `IntroViewModel` |
| `SearchScreen` | 1 | **已进 VM** | `uiState.query` |
| `SongDetailScreen` | 1 | **已进 VM** | `uiState.selectedSection` |
| `AudioEffectsScreen` | 2 | **改判 B** | 滑杆拖动中间态，§5 白名单；`UdfConventions.md` §5 已补判据 |
| `AppRoot`（`messageToShowState`） | 1 | **改判 B** | `DialogEvent.Message` 的渲染缓存，`MessageToast` 自带退场计时；进 VM 会与既有 DialogEvent 通道重复 |
| `PlaylistScreen`（`renameValue`） | 1 | **派生化** | 只是 `InputDialog` 的 `initialValue`，由 `playlistName` 派生，不落地成状态 |
| `InputDialog`（`inputValue`） | 1 | **改判 B（本轮补登记）** | 通用对话框组件内部输入草稿，没有宿主 VM；与 `MusicPickerDialog` / `TimerDialog` 同属「全局对话框内部表单」 |
| **合计** | **45** | 40 进 VM + 5 改判/派生 | 无「未处理」 |

### C 档 38 处中的分歧项（本轮补登记）

| 审计项 | 本轮判定 | 理由 |
|---|---|---|
| `MusicPickerDialog.currentSelectedIds` | **B** | 对话框内部多选草稿，随弹窗生命周期存在；`DialogManager` 是事件通道，没有屏级 VM 可承载 |
| `TimerDialog.selectedOption` / `customMinutesInput` | **B** | 同上，定时器表单草稿 |
| `BottomFusionBar.fusionState` | **B** | 融合栏的拖拽 / 融合手势中间态（`initialFusionState` 由外部传入），§5「拖拽 / 手势」 |
| `PlayerScreen.selectedTab` ×2 | **B** | 只切「控制 / 歌词」渲染块，不决定加载哪份数据 —— 审计标「判定」，按判据落在 B |
| `AppRoot.savedTabIndex` | **B** | `pagerState.currentPage` 的 `rememberSaveable` 镜像，配置重建恢复；跨进程恢复再迁 `SavedStateHandle` |

### 结论

- **不存在「所有状态都进 VM」这回事**：审计 174 处里约 97 处本就该留 UI，真正要搬的是 45 处 A 档 + 部分 C 档。
- 45 处 A 档已全部有归属（40 进 VM、5 改判/派生），C 档分歧项本轮补齐登记。
- **此前的缺口是「核销」不是「施工」**：代码大多改对了，但没有逐条写进 ledger，导致无法自证。本表补上这一环；后续任何「全部完成」的结论都必须以本表为准。

## 十、UI 侧 UDF 成立性独立调查（11-09）

> 背景：`check-udf.py` 只覆盖「转发层 / 成对确认链 / 注入」三条，**不能回答「UDF 是否成立」**。本节用独立脚本 `scripts/audit-udf-ui.py` 按 5 条判据重新量了一遍，不复用上面的结论。（该脚本已于 11-10 并入 `check-udf.py`，作为非阻塞提示；下面的记录保留当时的独立运行结果。）

### 判据与首轮结果

| 判据 | 首轮 | 核实后 |
|---|---:|---|
| R1 状态归属（Screen 里是否还有业务 `remember`） | 8 | **0**（8 条全是 §5 白名单：对话框内部表单 / 滑杆中间态 / tab 切换） |
| R2 单一出口（VM 散装 `StateFlow`） | 11 | **0**（全部是已登记的共享 VM 投影例外） |
| R3 动作形态（Screen 直接调 VM 业务方法） | 9 | **0**（3 条是 `load`/初始化，6 条真违规已修） |
| R4 副作用归属（Screen 出现业务类型名） | 7 | **0**（4 条注释、3 条 import 残留已删） |
| R5 一次性效果（`StateFlow<T?>` + `clear`） | 2 | **1 条线索**（`PlaylistQueueViewModel` 的可空是数据语义，误报） |

### 真违规与修复

1. **`AiSettingsViewModel.apiTestResult` 是伪一次性事件**：`StateFlow<ApiTestResult?>` + `clearApiTestResult()`，正是 §4 明令禁止的形态。已改为 `SharedFlow<AiSettingsEffect>`，UI 用 `LaunchedEffect(Unit) { effect.collect { ... } }` 消费；`ApiTestResult` 与 `clearApiTestResult()` 一并删除。
2. **`AudioEffectsScreen` 4 处**直接调 `viewModel.setCustomEqualizer(...)` → 改名 `onCustomEqualizerChanged(...)`。
3. **`UserUsageDataScreen` 2 处**直接调 `selectDimension` / `selectTimeRange` → 改名 `onDimensionSelected` / `onTimeRangeSelected`。
4. 命名归位：`initializeAudioEffects` → `onInitializeAudioEffects`；`tracksByIds` → `loadTracksByIds`（符合 §3 的 `onXxx` / `loadXxx` 约定）。
5. 删除 3 个未使用 import 残留（`AgentConfigScreen` / `IntroScreen` / `HomeScreen`）。

### 结论

- **UI 侧 UDF 成立**：4 条判据 0 违规，剩 1 条线索经人工判定为误报。
- 但这个「成立」比 `check-udf.py` 的通过更严格：**伪一次性事件、动作命名、import 残留这三类，原门禁都看不见**。~~`audit-udf-ui.py` 作为巡检工具保留~~（11-10 已并入 `check-udf.py` 作为非阻塞提示，脚本删除；R4/R5 仍不计入退出码）。
- 遗留的诚实边界：`LibraryViewModel` 的 9 个散装出口让「单一出口」在全局 VM 上不成立 —— 这是有意的共享投影设计，不是漏改；已在上表按例外登记。**11-10 已按 §十一 拆分下沉，这一条不再成立**。

### 参数注入债清理（11-09）

门禁扩展出「`x: T = koinInject()` 参数注入」形态后，暴露 18 处既有债，清理到 **13 处**：

| 已清 | 处理 |
|---|---|
| `IntroScreen` | `UserSettingsUseCase` → `IntroViewModel`，`aiAccessMode` 进 `IntroUiState` |
| `AIScreen` | `AgentQuickEntries` 的 `masterAgent` 是死参数，连注入一并删除 |
| `HomeScreen` | 新建 `HomeViewModel`：两条推荐流 + hello 桶额度合成 `HomeUiState`，`getMusicInfoByIds` 收成 `tracksByIds()` |

**剩余 13 处不是「还没搬」**：

- 6 处卡片 + 2 处看板（`AgentMonitorScreen`）读的是 agent 的**只读投影**。关键判定：`masterAgent.capability(name)` 返回 `Capability?`，而实现是 `_subAgents[name] as? Capability` —— **是 sub-agent 实例本身**。搬进 `UiState` 只是把业务对象换了个容器，语义没有变好。真正修法是引擎侧提供不可变能力快照，属 **P2 / 7.6.0**。
- 5 处是组合根 / 壳（`AppRoot` 启动编排、`RadioConsole`），生命周期就是 App 本身。

因此本阶段**不再强行压缩基线**；`scripts/udf-inject-baseline.txt` 的作用是阻止新增，清理留给 7.6.0 的引擎快照与后续组件接口重构。

## 十一、数据 VM / 屏级 VM 拆分（2026-11-10）

> 动因：§十 的结论把 `LibraryViewModel` / `PlaylistViewModel` 的散装出口当成「有意的共享投影」，但那只是把问题登记成例外 —— 应用级 VM 越滚越大，屏级交互态（确认链 / 弹窗 / 选中对象）跟着 App 活到底，离屏只能靠 `onScreenLeft()` 手动擦。本轮按「**数据归可观察流，交互态归 entry 作用域 VM**」重构。

### 数据层（`shared`）

| 新增 | 说明 |
|---|---|
| `MusicAllDao.observeAllMusicInfoAsListById()` | 与一次性查询同一条 SQL，Room 驱动回流 |
| `MusicRepository.observeAllMusicInfoAsList(key, direction)` | 与 `getAllMusicInfoAsList` 共用 `comparatorFor`（排序口径仍只有一份） |
| `MusicDao.observeRemovedMusicIdAndPath()` / `MusicRepository.observeDeletedMusicIdsGroupedByFolder()` | 「已隐藏文件夹」随删除 / 恢复回流；分组仍走 `parentPathOf` 一份 |
| `LibraryScanUseCase` | 扫描的唯一入口：`incremental()` / `full()` + 标记已加载 + 通知画像重建；增量的选择只有一处（D2-03） |

### 页面归属（`shared-ui`）

| 原归属 | 现归属 | 内容 |
|---|---|---|
| `LibraryViewModel`（已删除） | `GalleryViewModel`（Tabs entry） | 曲库列表 / 排序 / 单删+批删确认链 / 删除动作 |
| | `LibrarySettingsViewModel`（设置页 entry） | 权限位 / 隐藏文件夹确认 / 目录配置 / 全量+增量扫描 / 总量·已分析数·扫描目录·隐藏目录投影 |
| | `IntroViewModel` | 扫描进度与曲量进 `IntroUiState`；`onScanRequested` 直接调 `LibraryScanUseCase` |
| `PlaylistViewModel` | `PlaylistViewModel`（数据 VM） | 只留跨屏投影：五组标签名、自建歌单列表、系统歌单 id、批量加歌 |
| | `PlaylistScreenViewModel`（详情 entry） | 选中歌单 / 元数据 / 条目流 / 弹窗 / 加歌候选与完成效果 / 条目增删排序 |
| | `PlaylistManageViewModel`（管理 entry） | 编辑模式 / 待删 / 待改名 / 草稿 / 校验错误 / 删除·改名·置顶 |

- **`loadUserCustomPlaylists()` 删除**：原来它在每个动作后被调用一次，每次调用都新起一个永不结束的 collector（同一份库被订阅 N 次）。现在 `init` 里只有一条 `getAllPlaylistsFlow()` 订阅，增删改由 Room 回流。
- **`onScreenLeft()` 全部删除**：四个屏级 VM 绑定各自 entry，销毁即清理。
- **`scanState`（`LoadState<ScanResult>`）删除**：全仓零读取方，错误也从未被渲染。

### 门禁与约定

- `scripts/check-udf.py` 增加第四条 `vm-scope`：`activityViewModel()` 只允许 `APP_SCOPED_VMS` 白名单里的类型，屏级 VM 必须 `koinViewModel()`；同时把 `LibraryViewModel` 从「多出口豁免」里移除。
- `UdfConventions` §2 的样板由 `LibraryViewModel.settingsUiState` / `PlaylistViewModel.playlistUiState` 改为四个屏级 VM；「全局 VM 的适用条件」改为「跨屏数据归可观察流，交互态归屏级 VM」。
- 三端 `viewModel {}` 同步：新增 `GalleryViewModel` / `LibrarySettingsViewModel` / `PlaylistScreenViewModel` / `PlaylistManageViewModel`，`PlaylistViewModel` 4→3 依赖，`IntroViewModel` 1→3 依赖，`LibraryViewModel` 注册删除。
- 判据迁移：`LibraryScanPolicyTest` → `LibrarySettingsScanPolicyTest`（改目录 / 屏蔽目录 → 只调 `incremental`、绝不调 `full`）。

### C 类残留收口（11-10）

| 项 | 结论 |
|---|---|
| `agent/cards` 的 `onLongClick` 全链路无赋值 | **用户确认是预留接口，不处理**（`FamilyDispatch` 与 8 张卡的分支保留） |
| `AuditLogScreen` 的 `FilterRow` / `FilterRail` | 抽公共 `AuditFilterChip`：选中判据与点击映射只写一份，横排/竖排只换容器 |
| `PlayerScreenNestedScroll` 的 `remember` key 含新 lambda | 回调改 `rememberUpdatedState`，key 只留 `dismissThreshold / offsetY / scope / density` —— 手势进行中不再重建连接 |
| 成批未使用 import | shared-ui 清 224 条；`.editorconfig` 开启 `ktlint_standard_no-unused-imports` 后 `ktlint format` 再清 shared / android / desktop / ios 共 158 条。**storybook 不在构建图内、无法用编译器复核，该目录豁免这条规则** |

#### 三端 Koin 注册收口（11-10）

- 32 个 `viewModel {}` + `DialogManager` / `RadioConsoleController` 两个 single 原先在三端 UI 模块各一份，现收进 commonMain 的 `di/UiViewModelModule.kt`；`chatGatewayModule` 的 include 也一并挪进去。
- 三端模块只剩平台分叉绑定：Android（Media3 adapter + Coil `androidContext()`）、Desktop（FFmpeg adapter + skiko loader + `PlatformServices`）、iOS（双桥 adapter + skiko loader + `PlatformServices`）；`uiModule` / `desktopUiModule` / `iosUiModule` 都只 `includes(uiViewModelModule)`。
- 语义不变的依据（Koin 4.2.2 源码 / 字节码核对）：common 的 `viewModel {}` 实现就是 `factory(...)`；Android 的 `org.koin.androidx.viewmodel.dsl.viewModel` 字节码同样构造 `Kind.Factory` + `FactoryInstanceFactory`。`koinViewModel()` 与 `by viewModel()` 都按期望类型取 factory，不关心注册时用哪家 DSL。
- 残留风险：三个 `GraphVerificationTest` 只做静态 `verify()`、不实例化 —— Android `by viewModel()` 与 Compose 取用仍需启动一次冒烟；iOS target 本机不编译，但未验面已从 32 行注册缩为 1 行 `includes` + 3 个平台绑定。

## 十二、UI UDF 复审与修复（2026-11-10 晚）

> 方法：三路并行通读 `shared-ui/commonMain` 全部 193 个 `.kt` + 主审逐条回代码复核；机械门禁（ktlint / check-udf / audit-udf-ui）仍为 0 —— 本节记录的是门禁看不见的运行时形态、作用域与生命周期问题。

### 真 bug 修复

1. **HelloCards 快照冻结子代理流**（B2 引入的回归）：`radioMessageFlow()` / `enrichProgressState()` / `helloCards` 原先直取当前子代理实例的流，而 Hello/Radio/Enrich 子代理都是异步创建、重建会换实例 → VM 首帧缓存到空流/旧实例。MasterAgent 新增固定转发流 `helloCards` / `radioMessage` / `enrichProgress`（与 `_radioPlaylist` 同模式，start 时建转发、stop 时 cancel + 复位），UI 拿到的实例从此稳定。
2. **AgentConfig 保存后「未保存」徽标不复位**：`onSave` 收尾改调 `onRoleChanged(role)`，按落盘结果重载表单，`promptDirty` / `multiPromptDirty` / 出厂模板 / 覆盖位 / 占位符检查一并重算。
3. **Chat 锚点带话仍会丢**：`sendText` 改返回「是否派发」，`sendPreloaded` 在运行中/待确认被拒时把文本**回落输入框**，broker 因此可安全清空（A22 的完整版）。
4. **AuditLog 筛选竞态**：`refreshJob` 取消旧查询，慢的旧结果不再覆盖新筛选。
5. **PlaylistScreen 标签加载未纳入取消**：`getSelectedPlaylist` 纳入 `currentPlaylistJob`，并同步清掉选中 id/meta（避免新标签已上屏、trailing 仍指旧 id 一帧）。

### 形态清理

- `SearchUiState.isIntent`：意图判定从组合期移入 VM（原每次重组重算 `CommandLexicon.classify`；该词表已于 2026-11-11 整体删除，`isIntent` 改为「输入非空即提供 AI 入口」）。
- `TabScreen` 不再持 `NavBackStack` / 硬编码 `Routes.Library.Search`，改收 `onSearchClick`（B20 同型残留）。
- 电台「已开启」谓词收成 `RadioState?.isActive()` 一份（AppRoot 与 RadioEntryCard 共用）。
- `BackupEffect.Message` 载荷改 `UiText`（异常文本用 `UiText.Raw`，资源用 `asUiText()`），消费端 `resolveForEffect()`。
- `RecommendationViewModel` 删除 6 条零消费 API（`pendingMusicCount` / `processingProgress` / `isProcessingExtraInfo` / `pauseProcessing` / `resumeProcessing` / `cancelProcessing`），`GetAllMusicUseCase` 依赖一并去掉（DI 3→2）。
- `ThemeViewModel.currentPlayingMusic` 转 private；`AiSettingsViewModel.testAiConnection` 收 private；Gallery 的 `onShare` 死变量删除；零调用组件 `PlaylistEntryCard` 删除。

### 口径订正（复审中确认）

- **播放页是常驻页**（与四个 Tab 同级，用户 2026-11-10 口径）：`PlayerScreenViewModel` 用 `activityViewModel()` 属设计，**不是**作用域漏网。已写进 VM 类注释、`PlayerScreen` 调用点、`check-udf.py` 的 `APP_SCOPED_VMS` 注释与 `UdfConventions` §2；勿改回 `koinViewModel()`。
- 两条子审误报：`AgentMonitorScreen` 按 label 查分账（`sumByAgent` 的 label 就是 `agent_id`）；`UserUsageDataViewModel` 的四条流本就 private（KDoc 与事实一致）。
- `PlaylistEffect.ShowMusicPicker(candidates: List<MusicInfo>)` 判为纯数据一次性载荷，不算夹带业务对象；口径待写进 §4。

### 第二批修复（11-10）

- **播放动作收进屏级 VM**：`playWith` 的 suspend 调用从 5 个屏的组合 scope（Gallery / Search / Artist / Album / PlaylistScreen）移入各自 VM 的 `onPlay()`；`GalleryViewModel` / `SearchViewModel` / `ArtistAlbumViewModel` / `PlaylistScreenViewModel` 增加 `PlaybackController` 依赖（DI 同改），UI 不再为一次播放持有 `rememberCoroutineScope`。
- **弹窗单一订阅**：`MusicDetailDialog` / `CreatePlaylistDialog` 不再自己 `collectAsState(activeDialog)`，由 AppRoot 选中状态后以 `state` 入参传入（与 MusicPicker / PlaylistPicker / Timer 同形）。
- **toast 队列**：AppRoot 的单槽 `messageToShow` 改 `messageQueue`（最多 5 条，按序展示，`MessageToast.onDismiss` 出队）——连续两条消息不再丢前一条。
- 复核撤回：`DialogViewModel` 内 `getString` 后发 toast 属「一次性副作用」，`UiText` 的 KDoc 明确允许（不必包 UiText）；本项不算违规，不再列入待办。

### 第三批：歌词呈现归一（11-10）

- `PlayerScreenViewModel` 新增 `lyricsPart`：`LrcParser.parse` / `LyricsTimingGenerator.resolve` / `lineEnds` 只在换歌/时长就绪时重算，`currentIndex` 按位置二分；整条链 `flowOn(Default)`，不在 Main 上解析。
- `PlayerUiState.lyrics` 由 `String?` 改为 `LyricsPresentation?`（lines + lineEnds + currentIndex）。
- `AdvancedLyrics` 退化为纯渲染：不再 import 任何 domain 解析器，也不再 `remember` 解析结果；`totalDurationMs` 参数随 `lineEnds` 预计算一并删除（`MusicInfoExtra` 同改）。
- `LyricsScreen` 不再直接 collect 四条播放流：改为共享常驻的 `PlayerScreenViewModel.uiState`（播放页常驻口径），歌词页与播放页现在读同一个快照，重复解析也消失。动作（seek / 迷你条恢复）仍走平台门面。
- 待手动回归：歌词滚动跟随、卡拉 OK 逐字、点行 seek、无歌词/纯翻译、时长未就绪（duration=0）回退。

### 第四批：弹窗归屏（11-10）

- 新增 `PlaylistEditorViewModel`（entry 作用域）：新建/编辑歌单的表单、名称 debounce 查重、落库与叠层选歌都在屏级 VM；结果以 `PlaylistEditorEffect.Saved(id)` 交回**发起屏**（列表/管理页跳新歌单，歌单页刷新元数据），失败提示走 `Message(UiText)`。
- `CreatePlaylistDialog` 改为 `state + 动作`，不再收 `DialogViewModel`；名称错误保持 `PlaylistNameError` 交渲染端翻译（`playlistNameErrorText` 收成共用）。
- `MusicPicker` / `PlaylistPicker` 从全局 `DialogViewModel` 下沉：PlaylistScreen 的「添加歌曲」与批量加歌、Gallery 的批量加歌、创建歌单内的选歌，全部由各自屏级 VM 持有状态并渲染弹窗；结果走 `onSongsPicked/onPlaylistPicked` + Effect。
- `DialogViewModel` 只剩真正全局的：音乐详情、定时器、以及音乐详情自身的「加入歌单」选择器（内部完成落库，无回调字段）。三个回调 lambda 字段（`onPlaylistCreated` / `onMusicPickerConfirm` / `onPlaylistPickerConfirm`）全部删除。
- 视觉保真：AppRoot 通过 `LocalAppHazeState` 把背景采样源下发给屏内弹窗，弹出时仍有毛玻璃。
- 同步清理：`PlaylistViewModel.addItemsToPlaylist` 随调用方迁走删除；`DialogViewModel` 去掉 `GetAllMusicUseCase` 依赖（DI 6→5）。
- 对话页播放动作收进 `ChatViewModel`：气泡内点歌（`playWith`，suspend）与整单顺序播放移到 VM，导航改走 `ChatEffect.NavigateToPlayer` 由组合点消费；ChatScreen 不再持 `PlaybackController`。
- 弹窗面复核：其余弹窗（音乐详情 / 定时器 / 扫描 / ConfirmDialog / InputDialog / 各屏内联 AlertDialog / 现在的选歌与选歌单）都只收 props + 组合点回调，VM 内无回调字段；`DialogManager` 的确认链只存挂起协程续体（带 `invokeOnCancellation` 清理），`ConfirmBridge.onRequest` 是每次 chat run 新建的桥内 lambda，均非屏级 continuation。顺手删除零调用且 API 过时的 `AddSongToPlaylistDialog`（`onAdd(musicId, path)`，path 早已不在条目里）。

### 未处理（下一批）
- `PlaylistManageScreen` 在组合期做 `manageablePlaylists` / `sortedForManagement`（纯函数、无副作用，暂按渲染期纯投影接受）。
- `UserUsageDataViewModel` 的 `Eagerly` + init 即查（常驻 tab 启动期发查询；属性能/生命周期优化，非 UDF 形态）。
- 卡片业务构造函数（`buildAnchorCard` / `helloFallBackCard`）位于 UI 文件却被 VM 调用（纯函数、无 Compose 依赖；暂按同包内助手接受）。


### 第五批：AI 设置页自定义端点草稿被异步加载覆盖（11-10，用户报障）

- **根因**：B1 把表单草稿从 UI 的 `rememberSaveable` 收进 `AiSettingsViewModel` 后，`loadCustomAiConfig()` 无条件把保存的 `endpoint/model` 写回表单。它是一条异步读（DataStore + 密钥解密），若用户在读完前就输入（或读操作正好在「拉模型/测试」挂起期间完成），用户刚输入的地址会被**旧值覆盖** —— 表现为点「获取可用模型」时页面像被重置。重构前的本地字段只在 `model` 非空时同步，所以旧版没有这个现象。
- 修复：`loadCustomAiConfig()` 幂等（只灌一次）且只填空字段，绝不覆盖已输入草稿。
- 顺带两个真问题：
  - 拉模型成功后下拉框显示 `availableModels.first()`，但表单 `model` 仍为空 → 直接保存会存空模型，下次进入看着像被重置。现在成功后若 `model` 为空则回填首个模型。
  - `emitMessage` 用 `tryEmit` + 容量 1 + DROP_OLDEST，拉模型/测试结果可能被静默丢弃（「测试好像也有问题」）；改 `emit`，并给效果消费端加 `runCatching`，单条文案解析失败不再杀死整个 collector。

## 十三、11-10 复审收口

**门禁**：`ktlint check` = 0 处（含 `no-unused-imports`）、`python scripts/check-udf.py` = 0 违规（五条机械规则阻塞 + 四类巡检提示非阻塞）；`./gradlew-smart.bat compileAll`、`testAll` 全绿。

**本轮闭环**

- 门禁脚本合并：`audit-udf-ui.py` 并入 `check-udf.py`（R1/R3/R4/R5 作为非阻塞提示），不再维护两份 UDF 检查脚本；CI 仍只调 `check-udf.py`。
- UI 审计 A/B/C 三类全部核销（A11 四 Tab 常驻、`agent/cards` 的 `onLongClick` 预留为设计取舍）。
- 数据层可观察流 + `LibraryScanUseCase`；`LibraryViewModel` 删除，Gallery / 曲库设置 / 歌单详情 / 歌单管理各自 entry 级 VM。
- 三端 Koin 注册收进 commonMain 的 `uiViewModelModule`（32 个 `viewModel {}` + 两个跨端 single），平台模块只剩平台桥。
- 歌词呈现归一（`LyricsPresentation`，歌词页与播放页共用常驻 PlayerScreenViewModel）。
- 弹窗归屏：新建/编辑歌单、选歌、选歌单全部屏级持有；`DialogViewModel` 只剩音乐详情 / 定时器 / 详情内部加歌单，三个回调 lambda 字段清零。
- AI 设置页草稿被异步加载覆盖的回归修复；播放动作全部收进 VM（含 ChatViewModel）。

**已登记例外**（详见 `UdfConventions` §3/§4/§5）

- 播放页常驻 → `PlayerScreenViewModel` 用 `activityViewModel()`。
- `DialogViewModel` 持 `DialogManager`（弹窗域内部契约）。
- 展示层纯派生：Manage 纯函数投影、卡片构造函数、日期分桶、AgentMonitor 分账查找；`HelloCards` 卡片文案 `getString` 属 C 级。
- 13 处 `koinInject` 基线（agent 只读投影 + 组合根）归 7.6 引擎快照。
- `UserUsageDataViewModel` 启动即查属性能项（需先落 `LocalTabVisible`），不阻塞 7.5 形态收口。

**未在本机验证（发行前置，不是形态债）**

- iOS target 本机不编译：DI、歌词页、弹窗归屏均有 commonMain 改动，需 macOS 跑 `:shared` / `:shared-ui` 的 iOS 编译。
- Android / Desktop 手动回归：AI 设置「获取模型 / 测试」、歌词页滚动与卡拉 OK、四类弹窗、聊天点歌与整单播放、播放页常驻进出。
- 设计校准（`AGENTS.md` 明确为正式发行前置）。

## 十四、11-11 收口落定（提交与版本口径订正）

> 本节订正 §十三 末尾关于提交状态的失真表述，登记 P1 收口后的当前事实。§十三 的门禁结果、闭环项、已登记例外与验证边界均不变。

- **提交已落定**：收口提交 `6df3409`「refactor(udf): 7.5 P1 收口 —— 注入债清零、数据/屏级 VM 拆分、三端 DI 归一」（2026-11-11 10:48 +0800）；工作树干净，`feature/architecture-hardening` 与 `origin` 为 `0 / 0`。§十三 原记「改动未提交；amend 后 `ahead 1 / behind 1`，推远端需 `--force-with-lease`」**已作废**，不得再按该口径强推。
- **版本口径（11-11 用户决议后更新）**：P1 收口后补做一笔 **`bump: 7.5.0` 只标注提交** —— `release.toml` 的 `version` 7.4.0 → 7.5.0、`date` 留空、`notes` 换成 7.5 用户视角文案；`sync --write` 同步 9 个派生点，并向 ROADMAP / 站点 changelog 追加 v7.5.0 条目（`date` 留空、v7.4.0 的 Latest 降级）。**无 tag、无构建产物、无 push、不触发 `release.yml`**；正式发行仍等 §十三 的三项前置。**如实登记的缺口**：`changelog_entry()` 在 `date` 为空时只写 `Latest`、不加「未发布」徽章，该徽章须由 `sync-release.py mark-void` 单独打（`reason` 由人补，`skills/release-prep` 要求不代下结论）—— 本次未打，站点时间线上的 v7.5.0 目前是无日期的 Latest（文案本身已写明「开发阶段收口，不发行 / 本阶段不提供安装包」）；是否补标留给用户决定。原「bump 属发行前置、本阶段不提前改」（§八 11-10 订正）就此作废。`docs/7_5/` 转为已收口台账，不再新增施工批次。
- **bump 验证**：`inspect` 的 `drift` 归零、`sync --check` 9 项一致、`checkVersion` / `checkReleaseConsistency` BUILD SUCCESSFUL（versionCode `75000 > 72003`）、`./ktlint check` = 0 处、`check-udf.py` = 0 违规。按 7.4.0 bump 先例，本次未跑 `testAll`（无代码改动，只改版本与文档）。
- **P1 判定**：形态与门禁闭环，可转入下一阶段规划；**不等价于可发行** —— §十三 的 iOS 编译、Android / Desktop 手动回归与设计校准三项仍挂着。`docs/7_5/plan.md` §1.1 把 R15 / R48 列为 P1 对应台账债，但本台账未逐条核销这两条，`TODO.md` §六 对照表仍标「◐ 部分」——本次订正不代为结案，留待发行前置复核时按实物判定。
- **注入基线口径订正（§十三 例外条目）**：§十三 写「13 处 `koinInject` 基线（agent 只读投影 + 组合根）归 7.6 引擎快照」，那是 §十 11-09 的中间态；11-10 复审后 `scripts/udf-inject-baseline.txt` **基线为空**（文件注明「既有债已全部清理」），门禁改按 `check-udf.py` 的类型白名单判定。2026-11-11 实测：`shared-ui/commonMain` 排除 import 后约 20 余处活 `koinInject`，**无一处 composable 直接注入 `MasterAgent` 或调用 `capability()`**；全仓唯一 `capability()` 调用是 `AgentMonitorViewModel` 的 `masterAgent.capability(id)?.stateFlow`，且已在 VM 内转成 `CapabilityState` 快照。`check-udf.py` 里 `HelloCardRadioStatus.kt / MasterAgent` 的卡片渲染白名单是**残留登记**（该文件现只剩一句「由 HelloCardsViewModel 快照下发，卡片不再直连 MasterAgent」注释），可在 P2 收口时清掉。P2 的引擎快照范围以 [../7_6/plan.md](../7_6/plan.md) §二 实测为准，**不要按 13 处这个旧数字开工**。
- **下一阶段**：P2（Agent 运行时状态所有权 / 7.6.0）独立规划见 [../7_6/plan.md](../7_6/plan.md)，不共用 P1 批次与验收判据。
