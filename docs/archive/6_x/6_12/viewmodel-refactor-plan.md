# HMP ViewModel 作用域改造与划分方案

> 依据：Android 官方架构指南（ViewModel overview / State holders and UI state / ViewModel Scoping APIs / Navigation3 save-state）。
> 核心原则：**ViewModel 服务页面（导航目的地），功能复用放 domain/data 层**；跨目的地复用的业务逻辑必须封装在 data/domain 层组件中，而不是通过共享 ViewModel 实例。

## 1. 现状问题

- 全部 13 个 ViewModel 以 Koin `single` 注册（`UiKoinModule.kt`），生命周期 = 应用进程，`onCleared` / `viewModelScope` 永不生效。
- 大量「上帝对象」被跨页面共享：SettingsViewModel（15+ StateFlow）、PlaylistViewModel（4 种页面职责）、DialogViewModel（5 种弹窗 + 业务动作 + Router）。
- MainScreen 创建 9 个共享 VM 全链路传参（违反官方 "Don't pass ViewModel instances down"）。
- 项目使用 Navigation3，但未接入 `lifecycle-viewmodel-navigation3`，entry 内 ViewModel 默认绑定 Activity。

## 2. 目标形态

### 2.1 页面私有 ViewModel（绑定 Navigation3 Entry，pop 即清理）

每个导航目的地一个 ViewModel，`viewModel {}` 注册，页面内 `koinViewModel()` 默认获取（配合 NavDisplay 的 `rememberViewModelStoreNavEntryDecorator()`）。

| 目的地 | 页面 | 页面 ViewModel |
|---|---|---|
| Routes.Library.Search | SearchScreen | SearchViewModel（已有，改注册） |
| Routes.Library.SongDetail | SongDetailScreen | SongDetailViewModel（已有，改注册） |
| Routes.Player.AudioEffects | AudioEffectsScreen | AudioEffectViewModel（已有，改注册） |
| Routes.UserData.UserUsageData | UserUsageDataScreen | UserUsageDataViewModel（已有，改注册） |
| Routes.Player.Player | PlayerScreen | PlaybackViewModel / PlaylistQueueViewModel（播放投影，Activity 共享，见 2.2） |

### 2.2 共享残留 ViewModel（绑定 Activity，仅限真正的全局 UI 状态）

获取方式：页面内 `activityViewModel<T>()`（封装 `koinViewModel(viewModelStoreOwner = LocalActivity)`），不传参。

| ViewModel | 共享理由 |
|---|---|
| PlaybackViewModel / PlaylistQueueViewModel | 播放状态跨列表/播放/迷你栏展示；数据源为单例 MusicController，VM 为薄投影 |
| ThemeViewModel | 调色板驱动全局背景/UI |
| DialogViewModel / DialogManagerViewModel | 弹窗 UI 统一在 MainScreen 渲染，必须跨页面访问 |

### 2.3 待拆分（后续批次）

| 现有 | 拆分为 | 说明 |
|---|---|---|
| SettingsViewModel | ProfileViewModel / LyricsSettingsViewModel / AiSettingsViewModel / BackupViewModel / HazeViewModel | 绑定各自设置子页 |
| PlaylistViewModel | PlaylistScreenViewModel / PlaylistManageViewModel / ArtistAlbumViewModel | 按页面职责拆分 |
| DialogViewModel | MusicDetailDialogVM / CreatePlaylistDialogVM / PickerVM / TimerVM | 移除 Router 字段（改事件驱动） |
| LibraryViewModel | GalleryViewModel（Tabs 作用域） | 音乐库 Tab 内共享 |
| RecommendationViewModel | HomeViewModel / AI 页自取 | 数据源在 usecase 层 |

## 3. 关键技术决策

1. **Navigation3 Entry 级 ViewModelStore**：升级 lifecycle 至 2.11.0，接入 `androidx.lifecycle:lifecycle-viewmodel-navigation3`，在 NavDisplay 传入 `entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator())`。此后 entry 内 `koinViewModel()` 默认绑定该 entry。
2. **注册方式**：全部改为 Koin `viewModel {}` DSL（`org.koin.androidx.viewmodel.dsl.viewModel`）；`DialogManager()` 保持 `single`（非 VM 组件）。
3. **共享获取**：`activityViewModel<T>()` 扩展，显式绑定 Activity，保证跨 entry 共享且随 Activity 销毁清理。
4. **依赖**：feature-ui / app 显式声明 `koin-androidx-compose-viewmodel`；feature-ui 声明 `lifecycle-viewmodel-navigation3`。

## 4. 实施批次

- [x] 第一批（基础）：依赖升级、NavDisplay decorator、13 个 VM 注册改造、MainActivity `by viewModel`、共享获取点 `activityViewModel()` 统一 — commit 88f0fd8
- [x] 第二批：拆分 SettingsViewModel → BackupViewModel / AiSettingsViewModel / LyricsSettingsViewModel — commit cc14047
- [x] 第三批：拆分 PlaylistViewModel 的 Artist/Album 职责 → ArtistAlbumViewModel — commit cd39891
- [x] Router 修复：DialogViewModel 移除 RouteNavigator 引用，改事件驱动导航 — commit 5e82c11
- [x] 第四批：拆除 MainScreen→navigationGraph→页面的 VM 传参链（页面各自 activityViewModel/koinViewModel 自取）
- [ ] 可选后续：DialogViewModel 弹窗职责拆分、Library/Recommendation 页面化（当前保留共享，理由见下）
- [ ] 收尾：全局检查 `single` 残留与 owner 一致性；编译 + 单测 + 行为验证（旋转、pop、弹窗、泄漏）

### 当前共享 / 页面私有分布（第四批前）

- 共享（Activity 级，`activityViewModel()`）：Playback / PlaylistQueue / Playlist / Theme / Dialog / DialogManager / Settings / Library / Recommendation
- 页面私有（Entry 级，`koinViewModel()`）：Search / SongDetail / UserUsageData / AudioEffect / Backup / AiSettings / LyricsSettings / ArtistAlbum
- 说明：
  - PlaylistViewModel 保留共享——List/Gallery/Manage 三页共享自定义歌单集合状态，强行拆分会导致状态分裂。
  - LibraryViewModel / RecommendationViewModel 保留共享——音乐库集合、AI 批量处理进度是跨页面真实共享状态（数据源在 usecase 层，VM 为状态投影）。
  - DialogViewModel 保留为统一弹窗状态管理层——5 种弹窗 UI 在 MainScreen 统一渲染，属跨页面共享 UI 状态。

## 5. 验证要点

- 页面私有 VM：进入页面新建、退出销毁（LeakCanary / dumpsys 验证无残留）。
- 共享 VM：设置页修改后各页面同步；旋转屏幕状态保留。
- 弹窗：Player / 列表页打开收藏、分享、加歌单弹窗正常（共享 DialogViewModel 单实例）。
- 编译：`:android:app:compileDebugKotlin` 与 feature-ui 单测。

## 6. 验证结果（2026-08-11）

- 编译：feature-ui / app `compileDebugKotlin` 全部通过（JDK17 默认环境，toolchain 21）。
- 单元测试（JDK21）：全量 103 个测试，85 通过、18 失败。
  - 通过：RouterTest（17，已改用真实 NavBackStack 消除挂起）、NavigationGraphTest（4，已修正断言）、RoutesTest（19）、util 包（40）。
  - 失败：DeepLinkHandlerTest（18）——本地 JVM 无法 mock `android.net.Uri`，需引入 Robolectric（预存测试基建问题，与本次改造无关）。
- 行为验证（旋转 / 弹窗 / 页面进出 / 泄漏）需在真机或模拟器执行：验证清单见第 5 节。
