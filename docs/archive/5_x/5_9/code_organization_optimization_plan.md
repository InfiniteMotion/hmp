# 代码组织优化计划

## 项目概述

这是一个基于 Android Jetpack Compose 的音乐播放器应用，采用多模块架构（app, feature-ui, core-player, core-domain, core-data），使用 Hilt 进行依赖注入，MVVM 架构模式。

***

## 当前架构分析

### 1. 模块结构

```
HMP/
├── app/                    # 应用入口模块
├── feature-ui/             # UI 层模块
│   ├── pages/              # 页面（Screen）
│   ├── components/         # 可复用组件
│   ├── viewmodel/          # ViewModel 层
│   ├── dialogs/            # 对话框组件
│   └── util/               # 工具类
├── core-player/            # 播放控制核心
├── core-domain/            # 领域层（UseCase, Repository 接口）
└── core-data/              # 数据层（Repository 实现, Database）
```

### 2. 技术栈

* **UI**: Jetpack Compose + Material3

* **导航**: Navigation3 (androidx.navigation3)

* **依赖注入**: Hilt

* **架构**: MVVM

* **异步**: Kotlin Coroutines + Flow

* **媒体播放**: Media3 (ExoPlayer)

***

## 显著问题分析

### 问题 1: ViewModel 职责过重（God ViewModel）

**位置**: `feature-ui/src/main/java/.../viewmodel/PlayControlViewModel.kt`

**问题描述**:

* `PlayControlViewModel` 承担了过多职责：

  * 播放控制委托（play/pause/seek/next/previous）

  * 播放列表管理（add/remove/move/clear）

  * 音频效果管理（equalizer, bass boost, reverb）

  * 调色板颜色提取（UI 主题相关）

  * 播放列表生成算法配置

  * 收藏状态管理

  * 定时器控制

**代码证据**:

```kotlin
@HiltViewModel
class PlayControlViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicController: MusicController,
    private val generatePlaylistUseCase: GeneratePlaylistUseCase,
    private val settingsRepository: SettingsRepository,
    private val dialogManager: DialogManager
) : ViewModel() {
    // 超过 300 行代码，处理 10+ 种不同职责
}
```

**影响**:

* 违反单一职责原则（SRP）

* 难以测试（需要 mock 大量依赖）

* 代码可读性差

* 维护困难

***

### 问题 2: ViewModel 直接暴露给 UI 层过多状态

**位置**: `feature-ui/src/main/java/.../pages/player/PlayerScreen.kt`

**问题描述**:

* `PlayerScreen` 从 `PlayControlViewModel` 和 `SettingsViewModel` 收集了 15+ 个状态

* 所有状态都通过参数传递给 `PlayContent`，导致参数列表过长

**代码证据**:

```kotlin
@Composable
fun PlayerScreen(
    viewModel: PlayControlViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    navController: NavBackStack<NavKey>
) {
    // 收集 15+ 个状态
    val musicInfo by viewModel.currentPlayingMusic.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    // ... 更多状态
    
    // 传递给 PlayContent 的参数过多
    PlayContent(
        musicInfo = musicInfo,
        isPlaying = isPlaying,
        // ... 20+ 个参数
    )
}
```

**影响**:

* 组件接口臃肿

* 状态管理混乱

* 难以追踪数据流

***

### 问题 3: 页面加载方式不统一

**位置**: 多个 Screen 文件

**问题描述**:

* 不同页面使用不同的 ViewModel 获取方式：

  * 有的使用 `hiltViewModel()`

  * 有的使用 `hiltViewModel(LocalContext.current as ComponentActivity)`

  * 有的从参数传入

**代码证据**:

```kotlin
// PlayerScreen.kt - 使用默认方式
fun PlayerScreen(
    viewModel: PlayControlViewModel = hiltViewModel(),
    ...
)

// ListScreen.kt - 使用 Activity 上下文
fun ListScreen(
    playlistViewModel: PlaylistViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    dialogViewModel: DialogViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    ...
)

// SearchScreen.kt - 混合使用
fun SearchScreen(
    searchViewModel: SearchViewModel = hiltViewModel(),
    playControlViewModel: PlayControlViewModel = hiltViewModel(),
    dialogViewModel: DialogViewModel = hiltViewModel(),
    navController: NavBackStack<NavKey>
)
```

**影响**:

* ViewModel 生命周期不一致

* 可能导致状态丢失或重复创建

* 代码风格不统一

***

### 问题 4: 包结构混乱

**位置**: `feature-ui/src/main/java/.../`

**问题描述**:

* `dialogs` 和 `dialog` 两个包并存（dialogs/ 和 dialog/）

* `components` 包下组件分类不清晰

* 页面组织方式不一致（有的按功能分包，有的直接放在 pages/ 下）

**当前结构**:

```
ui/
├── components/
│   ├── musiclist/          # 音乐列表相关组件
│   ├── PlaylistArea.kt
│   ├── MiniPlayerBar.kt
│   └── ...
├── pages/
│   ├── base/               # 基础页面组件
│   ├── player/             # 播放器相关页面
│   ├── playlist/           # 播放列表相关页面
│   ├── settings/           # 设置相关页面
│   ├── HomeScreen.kt       # 直接放在 pages/ 下
│   ├── ListScreen.kt
│   └── ...
├── dialogs/                # 对话框
├── dialog/                 # 另一个对话框包！
└── viewmodel/              # 所有 ViewModel 混在一起
```

**影响**:

* 难以找到相关文件

* 代码组织不清晰

* 新开发者难以快速上手

***

### 问题 5: 状态管理分散

**位置**: 多个 ViewModel

**问题描述**:

* UI 状态（如加载状态、错误状态）分散在各个 ViewModel 中

* 没有统一的状态封装模式

* 部分页面没有加载状态管理

**代码证据**:

```kotlin
// LibraryViewModel.kt - 有简单的错误状态
private val _scanErrorMessage = MutableStateFlow<String?>(null)
val scanErrorMessage: StateFlow<String?> = _scanErrorMessage

// PlayControlViewModel.kt - 没有统一的 UI 状态
// 只有各种业务状态，没有 Loading/Error 状态

// HomeScreen.kt - 直接判断 null 作为加载状态
if (dailyMusic == null) {
    // 显示无数据状态
}
```

**影响**:

* 用户体验不一致

* 错误处理不完善

* 难以实现统一的加载/错误 UI

***

### 问题 6: Navigation 路由管理问题

**位置**: `feature-ui/src/main/java/.../util/Routes.kt`

**问题描述**:

* 路由分散定义，部分路由在 MainScreen 中硬编码

* 路由参数传递方式不统一

* 深层链接（Deep Link）支持不完善

**代码证据**:

```kotlin
// Routes.kt 定义了路由
object Routes {
    @Serializable object Player : NavKey
    @Serializable data class Playlist(val name: String) : NavKey
    // ...
}

// MainScreen.kt 中大量路由处理逻辑
entry<Routes.Player> { ... }
entry<Routes.Playlist> { route -> ... }
// 路由和页面映射分散
```

**影响**:

* 导航逻辑难以维护

* 路由参数类型安全无法保证

* 添加新页面需要修改多个地方

***

### 问题 7: 依赖注入深度不一致

**位置**: 多个文件

**问题描述**:

* 有些 ViewModel 注入了大量依赖（如 PlayControlViewModel 有 5 个依赖）

* 有些 ViewModel 直接在 init 中启动数据加载，没有统一模式

* 部分依赖应该使用接口但使用了具体实现

**代码证据**:

```kotlin
// PlayControlViewModel.kt - 依赖过多
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicController: MusicController,
    private val generatePlaylistUseCase: GeneratePlaylistUseCase,
    private val settingsRepository: SettingsRepository,
    private val dialogManager: DialogManager
)

// LibraryViewModel.kt - 也有多个依赖
@Inject constructor(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val loadMusicFromDeviceUseCase: LoadMusicFromDeviceUseCase,
    private val syncMusicFromDeviceIncrementalUseCase: SyncMusicFromDeviceIncrementalUseCase,
    private val removeFromLibraryUseCase: RemoveFromLibraryUseCase,
    private val restoreToLibraryUseCase: RestoreToLibraryUseCase,
    private val getDeletedMusicIdsGroupedByFolderUseCase: GetDeletedMusicIdsGroupedByFolderUseCase
)
```

**影响**:

* ViewModel 难以测试

* 依赖关系复杂

* 违反依赖倒置原则

***

### 问题 8: 组件复用性不足

**位置**: `feature-ui/src/main/java/.../components/`

**问题描述**:

* 组件参数过多，使用复杂

* 缺乏统一的组件设计规范

* 部分组件与业务逻辑耦合

**代码证据**:

```kotlin
// MusicListConfig.kt - 配置类过于复杂
data class MusicListConfig(
    val header: HeaderConfig,
    val item: ItemConfig,
    val list: ListConfig,
    val edit: EditConfig,
    val indexJump: IndexJumpConfig,
    val scrollbar: ScrollbarConfig,
    val currentPlaying: CurrentPlayingConfig,
    val callbacks: MusicListCallbacks
)

// 使用需要大量配置
val config = defaultMusicListConfig(callbacks).copy(
    header = HeaderConfig.None,
    item = ItemConfig(...),
    edit = EditConfig(enabled = false),
    currentPlaying = CurrentPlayingConfig(...),
)
```

**影响**:

* 组件使用门槛高

* 重复代码多

* 难以维护

***

### 问题 9: 主题和样式管理分散

**位置**: 多个文件

**问题描述**:

* 颜色、字体、动画配置分散在多个文件中

* 动态主题逻辑分散在 MainScreen 和各个 ViewModel 中

* 缺少统一的 Design System

**代码证据**:

```kotlin
// MainScreen.kt 中处理主题
val colorScheme = if (isPlaying) {
    generateDynamicColorScheme(paletteColors, isDarkTheme)
} else {
    getPresetColorScheme(isDarkTheme)
}

// PlayControlViewModel.kt 中也有调色板逻辑
private val _paletteColors = MutableStateFlow(PaletteColors())

// AnimationConfig.kt 中只有部分动画配置
object AnimationConfig {
    const val TRANSITION = 300
    val EASE_IN_OUT = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
}
```

**影响**:

* 主题不一致

* 难以实现全局样式调整

* 动态主题逻辑分散

***

### 问题 10: 数据流设计问题

**位置**: 多个 ViewModel

**问题描述**:

* 部分数据流使用 `StateFlow`，部分使用普通 Flow

* 数据转换逻辑分散在 ViewModel 和 UI 层

* 缺少统一的错误处理机制

**代码证据**:

```kotlin
// 混合使用 StateFlow 和 Flow
val musicCount: StateFlow<Int> = getAllMusicUseCase
    .getMusicCount()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

// 有些直接使用 MutableStateFlow
private val _allMusic = MutableStateFlow<List<MusicInfo>>(emptyList())
val allMusic: StateFlow<List<MusicInfo>> = _allMusic

// 错误处理不一致
viewModelScope.launch {
    try {
        // 业务逻辑
    } catch (e: Exception) {
        // 有些记录日志，有些显示 Toast，有些静默处理
    }
}
```

**影响**:

* 数据流难以追踪

* 错误处理不一致

* 状态更新可能遗漏

***

## 优化建议

### 1. ViewModel 拆分

将 `PlayControlViewModel` 拆分为多个专门的 ViewModel：

* `PlaybackViewModel`: 播放控制（play/pause/seek）

* `PlaylistViewModel`: 播放列表管理

* `AudioEffectViewModel`: 音频效果

* `ThemeViewModel`: 主题/调色板管理

### 2. 统一状态管理

引入统一的 UI 状态封装：

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

### 3. 包结构重构

按功能模块重新组织包结构：

```
ui/
├── common/                 # 通用组件
│   ├── theme/
│   ├── animation/
│   └── components/
├── player/                 # 播放模块
│   ├── PlayerScreen.kt
│   ├── PlayerViewModel.kt
│   └── components/
├── library/                # 音乐库模块
├── playlist/               # 播放列表模块
├── settings/               # 设置模块
└── navigation/             # 导航相关
```

### 4. 统一 ViewModel 注入方式

制定 ViewModel 注入规范：

* 默认使用 `hiltViewModel()`

* 需要共享作用域的明确标注原因

* 统一使用 `@HiltViewModel` 注解

### 5. 组件接口简化

简化组件配置，提供更简洁的 API：

```kotlin
// 简化前
MusicList(
    musicInfoList = list,
    config = complexConfig,
    ...
)

// 简化后
MusicList(
    items = list,
    onItemClick = { ... },
    onMenuClick = { ... },
    // 其他常用回调
)
```

### 6. 导航架构优化

将导航逻辑集中到 Navigation Module：

* 定义统一的 Navigation Graph

* 使用类型安全的路由参数

* 分离导航逻辑和页面实现

### 7. 依赖注入优化

* 减少 ViewModel 依赖数量

* 使用 Facade 模式封装相关依赖

* 明确接口和实现的分离

***

## 优先级排序

| 优先级 | 问题             | 原因           |
| --- | -------------- | ------------ |
| P0  | ViewModel 职责过重 | 影响代码质量和可维护性  |
| P0  | 包结构混乱          | 影响开发效率和代码可读性 |
| P1  | 状态管理分散         | 影响用户体验和代码一致性 |
| P1  | 页面加载方式不统一      | 可能导致运行时问题    |
| P2  | 组件复用性不足        | 影响开发效率       |
| P2  | 导航路由管理问题       | 影响代码可维护性     |
| P3  | 主题和样式管理分散      | 影响 UI 一致性    |
| P3  | 数据流设计问题        | 影响代码健壮性      |

***

## 下一步行动

1. **确认优化范围**: 与团队确认需要优先解决的问题
2. **制定详细方案**: 针对每个问题制定具体的重构方案
3. **分阶段实施**: 按照优先级分阶段进行代码重构
4. **编写测试**: 为重构后的代码编写单元测试和集成测试
5. **文档更新**: 更新架构文档和开发规范

