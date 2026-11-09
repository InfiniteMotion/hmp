# UDF 写法约定（7.5 P1 · 页面体系）

> **作用**：30 屏逐页改造的复制起点与判定口径。**不是框架**，不引入基类、不加依赖。
> **样板**：`../../library/viewmodel/EditMusicTagsViewModel.kt` + `../../library/pages/EditMusicTagsScreen.kt`。
> **依据**：仓库根 `docs/7_5/plan.md` §三、`docs/7_5/state-audit.md` §三。
> 施工中遇到模板没覆盖的形态，顺手往本文件补一条规则，不另开分支；30 屏过完后随收口提交同步 `DEVELOP.md` / `AGENTS.md`。

## 1. 名字（2026-11-06 决议）

| 名字 | 含义 | 位置 |
|---|---|---|
| `XxxUiState` | **整屏状态**，不可变 `data class`，一屏一个 | VM 同名文件 |
| `LoadState<T>` | 一次异步操作的结果（Idle / Loading / Success / Error / Empty），只作整屏状态的**字段类型** | `common/util/LoadState.kt` |
| `XxxEffect` | 一次性效果（toast / 导航 / 弹窗），`sealed interface` | VM 同名文件 |

项目里**不再有通用 `UiState` 类型**。`PlayerUiState` / `ChatUiState` / `DialogUiState` / `PlaylistScreenUiState` 是整屏状态，不是异步包装，不要改名、不要合并。
异步包装的配套件：`LoadStateExtensions.kt`（`Flow.asLoadState()` / `runCatchingToLoadState()`）与 `LoadStateContent`。

## 2. 状态：一个屏只有一个出口

```kotlin
data class XxxUiState(/* 业务字段 */, val isSaving: Boolean = false)

private val _uiState = MutableStateFlow(XxxUiState())
val uiState: StateFlow<XxxUiState> = _uiState.asStateFlow()
```

- **禁止第二个 `StateFlow` 出口**：`isSaving` / `saveResult` / `xxxState` 这类散装出口一律并进 `XxxUiState`。
- 更新统一 `_uiState.update { it.copy(...) }`；派生字段（`hasChanges` / `isSaving` 等）在**同一个入口**重算，不散在各回调里。
- 状态重置要保留与加载结果无关的字段（如平台能力位 `canWriteTags` 必须首帧就有，重置时不能回默认值）。
- 异步字段用 `Flow.asLoadState()` / `runCatchingToLoadState()` 与 `runCatching`，不要手写 `_x.value = Loading / Success / Error` 五连。
- **持续更新的流不套这两个桥接**：它们每次都先发 `Loading`。像播放历史这种长期 `collectLatest` 的流，要在原 `Success` 上增量更新数据（`_uiState.update { it.copy(detail = LoadState.Success(...)) }`），否则页面会随每次推送反复闪加载态。样板：`SongDetailViewModel.loadSongDetail()`。
- **屏级草稿不要塞进全局 VM**：`SettingsViewModel` 这类 activity 作用域、被多屏共享、十几个散装出口的 VM 只持有全局设置值；表单草稿、待确认预览属于单屏生命周期，建屏级 VM 自己读写 UseCase / 平台服务（记得三端 `viewModel {}` 同改）。样板：`ProfileSettingsViewModel`。代价要如实登记：全局 VM 里的镜像值不会自动同步，见 `docs/7_5/progress.md` §四。
- **成对确认链用一个 nullable 字段**：`show*Dialog + pending*` / `selected*` 拆成两个 `remember` 会不同步，且容易在多个 section 里各复制一份。用 `pendingXxx: T?` 同时表达「弹窗可见」与「待操作对象」，弹窗只渲染一份。样板：`BackupUiState.pendingRestorePath`。
- **常驻屏允许应用级 VM**：4 个 Tab 与**播放页**不随导航销毁（播放页与 Tab 同级常驻，2026-11-10 用户口径），它们的屏级 VM 用 `activityViewModel()` 并在 `check-udf.py` 的 `APP_SCOPED_VMS` 登记；其余 entry 屏一律 `koinViewModel()`，不要拿播放页当先例。
- **跨屏数据归数据 VM，屏级交互态归屏级 VM（entry 作用域）**：数据侧由 Room / UseCase 的可观察流承载（`GetAllMusicUseCase.observeAllMusic` / `ManagePlaylistUseCase.getAllPlaylistsFlow`），屏级 VM 直接注入 UseCase 订阅，**不需要「VM 转发 VM」** —— 因此不要为了省一个 VM 把交互态塞回应用级 VM。样板：`GalleryViewModel`（列表 + 排序 + 确认链）、`LibrarySettingsViewModel`（交互 + 投影 + 目录动作）、`PlaylistScreenViewModel` / `PlaylistManageViewModel`。应用级 VM 只留真正跨屏的类型，由 `check-udf.py` 的 `vm-scope` 规则守着。
- **跨屏共享的数据 Flow 是投影白名单**：`musicCount` / `folders` / `scanDirectoryConfig` 这类被多屏订阅的数据出口，不必为了凑「单一出口」复制进整屏状态；屏级 `XxxUiState` 只装本屏的交互态。平台只读能力查询（权限、目录模式）可留在 UI 门面；平台副作用选择器走 `SharedFlow` 效果由组合点消费。
- **同一份持久化的 N 份配置收进一个 `Map`，不要平铺成 N 个字段**：它们只是同一份数据的 N 个投影（`LyricsSettingsUiState.configs`）。长期订阅配置 Flow 让任何来源的写入回流到本屏，UI 不做乐观更新；编辑走带锁的读-改-写（UseCase 已有 `Mutex`）保证连点不丢值。样板：`LyricsSettingsPageViewModel`。
- **应用级 VM 若确实服务多屏，按屏拆 UiState 出口**：每屏只订阅自己那个 `XxxUiState`，不要为了「VM 只有一个出口」把两屏的交互态混进同一个 data class。**优先把它拆成 entry 作用域的屏级 VM**（2026-11-10 `PlaylistViewModel` 已按此下沉，见 `docs/7_5/progress.md` §十一）；拆不动时才保留「单出口 + 离屏清理」。跨屏共享的数据 Flow 仍按投影白名单处理。

## 3. 事件：只上送，不回传

- 用户动作统一 `onXxx()`：`Unit` 返回，**不返回业务结果、不接回调**。
- 加载 / 写库 / UseCase / 平台服务调用全在 VM；composable 不直连业务对象。
- `LaunchedEffect(key) { viewModel.load(...) }` 只用于生命周期加载，其余副作用不留在 UI。
- 平台回调（写权限、选图、SAF）在 VM 内转成状态更新或效果发射，UI 不 `when(result)`。
- **能在 VM 内直接调用的平台桥，回调就在 VM 内消费**：权限请求 / 选图这类桥已注册为 Koin single（Android 的 `ActivityResultLauncher` 也不要求在组合内调用），VM 持有 `PlatformServices` 即可完成闭环，不必绕一层 Effect。只有组合点才能完成的动作（导航、全局对话框、跨屏入口桥写入）才发 Effect。样板：`LyricsSettingsPageViewModel.onFloatingLyricsToggle()`。
- **带屏级 continuation 的弹窗归屏持有**：新建/编辑歌单、选歌、选歌单这类「确认结果要回到某个屏」的弹窗，状态放该屏的 VM、由该屏渲染，结果走 `onXxx()` / `Saved` Effect；不要把回调 lambda 存进应用级 VM。全局弹窗只留结果与屏无关的（音乐详情、定时器、全局 toast/确认链）。渐变视觉需要背景采样源时读 `LocalAppHazeState`。样板：`PlaylistEditorViewModel` + `PlaylistScreenViewModel.musicPicker/playlistPicker`。
- **组件级回调不是「VM 回调式 API」**：`InputDialog.onConfirm` / `ConfirmDialog.onConfirm` 这类弹窗组件的参数只用于继续 UI 流程，允许留在组合点；判据是「回调值会不会被写回 UiState」——会，就收进 VM 的 `onXxx()` / Effect；不会，就不用为了形式统一硬造 Effect。注意全局弹窗**不存回调 lambda**（见上条）。

## 4. 一次性效果：SharedFlow，不是 StateFlow

```kotlin
// VM
private val _effect = MutableSharedFlow<XxxEffect>(
    replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val effect: SharedFlow<XxxEffect> = _effect.asSharedFlow()

// UI
LaunchedEffect(Unit) {
    viewModel.effect.collect { effect -> when (effect) { /* toast / 导航 / 弹窗 */ } }
}
```

- **不要**用 `StateFlow<Result?>` + `clearResult()` 模拟一次性事件：重订阅 / 旋转会重复弹。
- **VM 不持有** `NavBackStack` / `RouteNavigator` / `DialogManager`；效果只描述「发生了什么」，由组合点决定怎么展示与导航。
  - 例外：`DialogViewModel` 是**弹窗域自身**的 VM，`DialogManager` 是该域的内部事件通道（它同时是 NavRequest / ConfirmChain 的唯一生产者），按域内契约持有，不算跨层依赖。
  - 一次性 toast 文案可继续用挂起 `getString`（`UiText` KDoc 已明确允许）；只有要跨边界携带（进状态 / 数据类 / 卡片快照）时才用 `UiText`。
- 形态照抄 `DialogManager` 的 `SharedFlow<DialogEvent>`；全局对话框仍走 `DialogManager`，本通道只管本屏效果。
- **跨屏入口桥的写入归组合点**：VM 只描述「用户要把这句话交给伙伴」，`ChatEntryBroker.pendingInput` 与导航由消费 Effect 的组合点完成，VM 不持有 `NavBackStack` / `ChatEntryBroker`。样板：`SearchEffect.TalkToCompanion` + `SearchScreen` 的 `LaunchedEffect(Unit)`。

## 5. 留在 UI 的状态（7 类白名单）

以下**不进 VM**，判定口径见 `state-audit.md` §三：

| 类别 | 代表 |
|---|---|
| 滚动 / 分页 / 列表状态 | `listState`、`pagerState`、`scrollState` |
| 拖拽 / 手势 / 测量 | `dragOffset`、`trackWidth`、`isDragging` |
| 动画 / 时间戳 / 相位 | `glowPhase`、`lastAutoRotateAt`、`isExiting` |
| 展开收起 / 菜单 / 视觉可见性 | `expanded`、`showMoreMenu`、`showPassword` |
| 协程作用域（非状态） | `rememberCoroutineScope`，仅动画用途时保留 |
| 卡片渲染缓存 / 上一帧值 | `anchorCard`、`lastSongKey`、`prevHeight` |
| 拖动中间态（播放进度条） | `sliderPosition`、`isSeeking` |

- **全局对话框内部的表单草稿也留 UI**：`InputDialog.inputValue` / `MusicPickerDialog.currentSelectedIds` / `TimerDialog.selectedOption` 是弹窗自身的输入态，随弹窗生命周期存在，没有宿主 VM；`DialogManager` 是共享事件通道，不承载表单值。判据是「这个值有没有一个屏级 VM 可以归属」——没有就留组件。样板：三个全局对话框（`state-audit.md` §二/§四 判 A，施工时改判 B 并在 `progress.md` §九 登记）。
- **滑杆 / 拖拽的本地中间值属白名单，别按变量名误判**：`sliderValue` 这类名字在盘点时容易被记成 A 档表单值，但它只在拖动期间持有，松开后由外部状态回流，与播放进度条的 `sliderPosition` / `isSeeking` 同组。判据是「会不会被持久化、会不会决定加载哪份数据」；都不会就留 UI。样板：`BassBoostSlider` / `EqualizerBand`（#18 复核把 `state-audit.md` §二 的 A 2 处改判为 B）。

- **展示层纯派生按渲染期投影接受**：`PlaylistManageScreen` 的 `manageablePlaylists` / `sortedForManagement`、卡片构造函数（`buildAnchorCard` / `helloFallBackCard`）、`MusicListConfig` 的日期分桶与「未知」哨兵、`AgentMonitorScreen` 的 token 分账行查找 —— 都是纯函数、无 I/O、无副作用，留在渲染侧，不再强制下沉。
- `HelloCardsViewModel` 的卡片文案 `getString` 属已知 C 级（语言切换后不回流），不进门禁。

全局播放态与主题态作为**组合根投影白名单**，不要求每屏各自聚合（否则 143 处订阅变成 30 份重复播放状态）。
注意：`show*Dialog` 与 `pending*` / `selected*` / `*Value` **成对出现时是业务确认链**，属进 VM 的 A 档，不能一律留 UI。

## 6. 边界与护栏

- **不推进引擎内部**：`ReActLoop` / Agent 运行时状态所有权是 P2（7.6.0），独立形态、独立验收。
- 每笔提交：`./ktlint check` = 0 处 + `./gradlew compileAll` 绿；新增 / 拆分 VM 必须 Android / Desktop / iOS **三处 `viewModel {}` 同改**。
- 新测试优先 `commonTest`；`PlaylistViewModel` 有 Robolectric 构造坑（D3-06），按抽纯函数处理。
- **门禁已全量开**：`scripts/check-udf.py` 已挂进 `pr-check.yml` 的 `lint` job。**五条机械规则阻塞合入** —— 不得有整屏 `XxxScreenContent` 转发层、不得用 `show*Dialog + pending*` 两个 `remember` 组成对确认链、composable 不得 `koinInject` 业务对象（平台门面按类型白名单逐条登记）、`XxxUiState` 之外不得有散装 public StateFlow 出口（共享投影白名单逐条登记）、`activityViewModel()` 只允许应用级 VM（屏级 VM 必须 `koinViewModel()`）。原 `audit-udf-ui.py` 的 R1/R3/R4/R5 已并入同一脚本，作为**非阻塞提示**（靠变量名/类型名启发式，需人工判定）。不可判定的仍靠 review 清单。
- **非 entry 屏补扫**：`NavigationGraph.kt` 的 entry 表只有 26 条，覆盖不到组合根 / 宿主直挂 / Tab 壳内页（`AppRoot`、`IntroScreen`、`HomeScreen` / `GalleryScreen` / `ListScreen` / `UserScreen` 等）。entry 表跑完后单独补扫一遍，逐屏在 `docs/7_5/progress.md` 登记，不预先假设它们已被排期。

## 7. 逐页就地闭环（2026-11-08 用户决议）

- **不再新建整屏 content 转发层**（`XxxScreenContent` / `XxxContent`）：UDF 后 Screen 只订阅单一 `uiState` 并上送 `onXxx()`，再拆一层只会把状态拆成一串参数透传，且状态容易在 content 里重新落地。**Screen 内部按区块拆纯渲染组件仍然允许**（如 `BackupItem` / `RestoreConfirmDialog` / `SongHeaderCard`），那不是转发层。
- **已存在的 content 层在改到该屏时合并**：把状态订阅与组装收回唯一 Screen 入口，不保留「Screen 收状态 → Content 逐字段转发」。现存 7 个 `XxxScreenContent` + `PlayContent` 全部纳入对应屏的那一笔。
- **逐页改 = 该屏全部 UDF 形态问题一次闭环**：状态归属、成对确认链、回调式 API、content 分层、平台副作用归属，都在这一笔里收干净。**不留形态债到收口**；§4.3 只负责门禁、契约、文档定稿与判定记录归档。
- 已越过或已提交的屏若发现漏项，**回头补一笔**，不推给收口；队列见 `docs/7_5/progress.md` §五。
- 例外：本机无法验证的环境限制（iOS target 编译）如实登记，不算形态债。
