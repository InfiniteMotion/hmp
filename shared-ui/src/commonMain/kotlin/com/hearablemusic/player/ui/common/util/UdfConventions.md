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

## 3. 事件：只上送，不回传

- 用户动作统一 `onXxx()`：`Unit` 返回，**不返回业务结果、不接回调**。
- 加载 / 写库 / UseCase / 平台服务调用全在 VM；composable 不直连业务对象。
- `LaunchedEffect(key) { viewModel.load(...) }` 只用于生命周期加载，其余副作用不留在 UI。
- 平台回调（写权限、选图、SAF）在 VM 内转成状态更新或效果发射，UI 不 `when(result)`。

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
- 形态照抄 `DialogManager` 的 `SharedFlow<DialogEvent>`；全局对话框仍走 `DialogManager`，本通道只管本屏效果。

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

全局播放态与主题态作为**组合根投影白名单**，不要求每屏各自聚合（否则 143 处订阅变成 30 份重复播放状态）。
注意：`show*Dialog` 与 `pending*` / `selected*` / `*Value` **成对出现时是业务确认链**，属进 VM 的 A 档，不能一律留 UI。

## 6. 边界与护栏

- **不推进引擎内部**：`ReActLoop` / Agent 运行时状态所有权是 P2（7.6.0），独立形态、独立验收。
- 每笔提交：`./ktlint check` = 0 处 + `./gradlew compileAll` 绿；新增 / 拆分 VM 必须 Android / Desktop / iOS **三处 `viewModel {}` 同改**。
- 新测试优先 `commonTest`；`PlaylistViewModel` 有 Robolectric 构造坑（D3-06），按抽纯函数处理。
- 门禁在 30 屏过完后一次性全量开（`scripts/check-udf.py`，按注入类型判定，不按关键字），本阶段不设增量基线。
