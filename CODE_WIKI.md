# Hearable Music Player — Code Wiki

> 跨平台本地音乐播放器的结构化代码百科文档。覆盖整体架构、模块职责、关键类/函数、依赖关系与运行方式。
> 适用版本：v7.2.0（master 最新发布 v7.1.0；v7.2.0 发布中）。

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [模块划分与职责](#3-模块划分与职责)
4. [模块间依赖关系](#4-模块间依赖关系)
5. [shared 模块详细说明](#5-shared-模块详细说明)
6. [shared-ui 模块详细说明](#6-shared-ui-模块详细说明)
7. [平台壳模块说明](#7-平台壳模块说明)
8. [AI Agent 体系](#8-ai-agent-体系)
9. [依赖与技术栈](#9-依赖与技术栈)
10. [项目运行方式](#10-项目运行方式)
11. [测试体系](#11-测试体系)
12. [发布与 CI/CD](#12-发布与-cicd)

---

## 1. 项目概述

**Hearable Music Player (HMP)** 是一款纯本地的跨平台音乐播放器，覆盖 **Android / Desktop / iOS** 三端。

- **技术栈核心**：Kotlin Multiplatform (KMP) + Compose Multiplatform + SwiftUI 壳层
- **UI 共享方式**：三端共用 `shared-ui` 模块的一套 Compose UI（v7.0 完成 Android/Desktop，v7.1 完成 iOS），平台差异收口到各自的桥接层
- **架构模式**：MVVM（ViewModel + StateFlow 状态管理）
- **产品边界**：纯本地，不做云同步/账号/社交；AI 推荐仅依赖用户自填 API
- **应用版本**：7.2.0（versionCode 72000）
- **包名**：Android `com.hearablemusic.player`；Shared (KMP) `com.hmp`；iOS `com.hearablemusic.HMP`

### 核心能力一览

| 能力域 | 说明 |
|---|---|
| 本地音乐扫描 | 自动扫描设备音乐文件、读取 ID3 标签、入库 |
| 播放控制 | 播放/暂停/上下首/快进快退/均衡器/睡眠定时 |
| 播放列表 | 创建/重命名/删除、拖拽排序、智能歌单生成 |
| AI 推荐 | 多服务商（DeepSeek/OpenAI/Claude/通义/文心），每日推荐刷新策略 |
| AI Agent | 自然语言对话、歌单生成、AI 电台、听歌报告与用户画像 |
| 歌词显示 | 滚动歌词、卡拉OK 时序、独立歌词页、悬浮歌词 |
| 用户界面 | 个人主页、听歌热力图、动态背景、毛玻璃效果、暗/亮主题 |
| 国际化 | 14 种语言字符串资源 |

---

## 2. 整体架构

项目采用 **MVVM + 分层 + 跨平台共享** 的混合架构：

```
┌─────────────────────────────────────────────────────────────┐
│  平台壳层 (App Entry)                                        │
│  Android: MainActivity / MusicApplication                   │
│  Desktop: Main.kt / HmpDesktopApplication                   │
│  iOS:     HMPApp.swift / AppDelegate                        │
└────────────────────────┬────────────────────────────────────┘
                         │ 初始化 Koin、挂载 Compose UI、注册平台桥
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  UI 层 (shared-ui/commonMain) — 三端共享 Compose UI          │
│  AppRoot → NavDisplay → MainShell(Tabs) → 各功能页面          │
│  ViewModel (Koin koinViewModel) + StateFlow                 │
└────────────────────────┬────────────────────────────────────┘
                         │ 调用 Use Case，平台差异走 expect/actual
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Domain 层 (shared/commonMain)                               │
│  Use Cases + 领域模型 + Repository 接口 + AI Agent 运行时     │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Data 层 (shared/commonMain + 平台 actual)                   │
│  Repository 实现 + Room KMP DAO + Ktor 网络                  │
│  DataStore KMP 偏好 + 平台特定扫描/标签解析/安全存储          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Service 层 (平台 core-player)                               │
│  Android: Media3 ExoPlayer + MediaSession                   │
│  Desktop: FFmpeg + JNA 自研音频引擎                          │
│  iOS:     AVFoundation + MPNowPlayingInfo                    │
└─────────────────────────────────────────────────────────────┘
```

### 关键架构决策

1. **跨平台框架 KMP**：业务逻辑、数据模型、Repository 接口沉淀在 `shared`，平台实现走 `expect/actual`。
2. **依赖注入 Koin（已从 Hilt 迁移）**：全平台共用，配置集中在 `SharedModules` + 平台 `*Modules`，iOS 端在 `AppDelegate` 启动 Koin。
3. **状态管理 Flow/StateFlow**：ViewModel 暴露 StateFlow，UI 用 `collectAsState()` 订阅。
4. **媒体播放平台原生**：Android Media3、Desktop FFmpeg+JNA、iOS AVFoundation，统一通过 `PlaybackController` 接口收口。
5. **数据存储 Room KMP + DataStore KMP**：`@ConstructedBy` + `expect/actual` + SQLite Bundled 驱动。
6. **网络 Ktor Client**：Android 用 OkHttp 引擎，iOS 用 Darwin 引擎，Desktop 用 Java 引擎。
7. **AI 多服务商**：统一 `OpenAiCompatibleAdapter` 适配 DeepSeek/OpenAI/Claude/通义/文心；API Key 加密存储。
8. **导航 Navigation 3**：类型安全、`@Serializable` 路由，叠加自研 `NavController` + `NavigationGraph` 实现多面板响应式导航。
9. **毛玻璃 Haze**：弹窗/底部栏毛玻璃，与动态背景结合。

---

## 3. 模块划分与职责

项目采用模块化架构，按 `settings.gradle.kts` 的 `HMP_BUILD_TARGET` 环境变量动态纳入模块。

| 模块 | 类型 | 职责 |
|---|---|---|
| `:shared` | KMP 库 | 跨平台共享的 Domain 层（Use Cases / 模型 / Agent 运行时）+ Data 层（Repository 接口 + Room + Ktor + DI 配置） |
| `:shared-ui` | KMP 库 | 三端共享 Compose UI（页面 / 组件 / 设计系统 / 导航）+ ViewModel；Android/Desktop/iOS 桥接层 |
| `:shared-ios` | KMP 库 | iOS 聚合框架，把 `:shared` + `:shared-ui` 链接为单一 `sharedIos.framework` 接入 CocoaPods |
| `:android:app` | Android App | Android 入口：`MainActivity`、`MusicApplication`、权限、签名、BuildConfig |
| `:android:core-player` | Android Lib | Android 播放核心：Media3 `MusicPlayService` + `MusicController` + 通知/媒体会话 |
| `:desktop:app` | KMP (JVM) | Desktop 入口：`Main.kt`、无边框窗口、系统托盘、单实例守卫、CustomTitleBar |
| `:desktop:core-player` | KMP (JVM) | Desktop 播放核心：`FFmpegAudioEngine` + `DesktopMusicController` |
| `:ios` | Xcode 项目 | iOS 原生壳 + 平台桥接（播放引擎/MediaSession/LiveActivity/权限）+ 共享 Compose UI |
| `:storybook` | KMP (Wasm) | 组件展示，**已移出构建**（源码保留，见 settings.gradle.kts） |

### 构建目标过滤

`settings.gradle.kts` 按 `HMP_BUILD_TARGET` 纳入模块：

- `android` → `:android:app` + `:android:core-player` + `:shared-ui` + 占位 `:desktop:core-player`
- `desktop` → `:desktop:app` + `:desktop:core-player` + `:shared-ui` + 占位 `:android:core-player`
- `all`（默认）→ 全部 7 个模块

> 占位 include 仅为 `shared-ui` 的 `androidMain`/`desktopMain` 中 `project()` 声明提供存在性，避免 configure-on-demand 下悬空依赖。

---

## 4. 模块间依赖关系

```
:shared-ui ──▶ :shared                          (业务能力)
:shared-ui ──▶ :android:core-player             (androidMain 桥接)
:shared-ui ──▶ :desktop:core-player            (desktopMain 桥接)

:android:app ──▶ :shared + :shared-ui
:desktop:app ──▶ :shared + :shared-ui + :desktop:core-player

:android:core-player ──▶ :shared
:desktop:core-player ──▶ :shared

:shared-ios ──▶ :shared + :shared-ui            (api + export，聚合为单框架)
:ios (Xcode) ──▶ :shared-ios                    (经 CocoaPods：pod 'shared_ios')
```

依赖方向原则：
- **共享层向下依赖**：`shared-ui → shared`，不允许反向。
- **平台壳向上依赖**：`android:app` / `desktop:app` 依赖共享层 + 平台 core-player。
- **平台 core-player 依赖 shared**：复用领域模型与 Repository。
- **iOS 单框架**：避免双静态框架的 duplicate symbol 与 Koin 全局分裂。

---

## 5. shared 模块详细说明

`shared` 是 KMP 共享模块，包名 `com.hmp`。源集结构：

```
shared/src/
├── commonMain/kotlin/com/hmp/   # 跨平台共享代码
│   ├── data/                    # 数据层
│   ├── domain/                  # 领域层 + AI Agent
│   ├── di/SharedModules.kt      # Koin 共享模块装配
│   ├── log/                     # 日志（HmpLog / LogTag / MemLogWriter）
│   ├── platform/                # Synchronized.kt / Volatile.kt（expect）
│   ├── KermitInit.kt            # Kermit 日志初始化
│   └── PlatformLog.kt
├── androidMain/                 # Android actual（Repository / Scanner / 标签 / DI）
├── desktopMain/                 # Desktop actual（Repository / DI / 日志）
├── iosMain/                     # iOS actual（DI / Settings 桥 / Koin Helper）
├── commonTest/                  # 纯逻辑单元测试
└── desktopTest/                 # Room 迁移测试、DAO 测试、Repository 测试
```

### 5.1 Data 层

路径：`com.hmp.data`

#### 数据库（Room KMP）

- [AppDatabase.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/database/AppDatabase.kt)：`@Database` 主类，**version=9**，18 个实体表（音乐/歌单/播放历史/听歌时长/Agent 任务/审计/消息/Hello 卡缓存/画像证据侧写叙事/遗忘送达/Token 账本）。
  - `expect object AppDatabaseConstructor`：Room KMP 构造器（平台 actual 实现）。
  - `expect fun getRoomDatabase(builder)`：平台构建入口。
  - 内置 8 个 Migration（MIGRATION_1_2 … MIGRATION_8_9），覆盖 Agent 表/画像/Token 账本演进。
  - DAO 18 个：`musicDao` / `playlistDao` / `agentTaskDao` / `tokenLedgerDao` / `userProfileEvidenceDao` 等。
- 实体：[Music.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/database/Music.kt)、`Playlist`、`PlaylistItem`、`PlaybackHistory`、`ListeningDuration`、`AgentTask`、`AgentAuditLog`、`AgentMessage`、`TokenLedger`、`UserProfile` 等。

#### 网络层（Ktor）

路径：`com.hmp.data.network`

- [HttpClient.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/network/HttpClient.kt)：`expect fun createHttpClient()` / `createJson()`，平台 actual 选引擎。
- [MultiProviderApiAdapter.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/network/MultiProviderApiAdapter.kt)：多 AI 服务商统一适配（OpenAI 兼容协议），支持 DeepSeek/OpenAI/Claude/通义/文心。
- [OpenAiLlmTransport.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/network/OpenAiLlmTransport.kt)：Agent 用的 LLM 传输实现（含 SSE 流式）。
- [SseParser.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/network/SseParser.kt)：Server-Sent Events 解析。
- `BuiltInApiKey.kt`：内置 AI Key Provider 接口（Android BuildConfig 注入）。
- `dto/ApiDtos.kt`：API 请求/响应 DTO。

#### Repository

- [MusicRepositoryBase.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/data/repository/MusicRepositoryBase.kt)：跨平台共享的 Repository 基类（DAO 调用 + 业务逻辑），平台 actual 继承。
- 平台实现：`MusicRepositoryImpl.android.kt` / `.ios`（在 androidMain / iosMain），Desktop 复用 `MusicRepositoryBase`。
- 其他 Repository：`BackupFileRepositoryImpl`、`PlaylistRepositoryImpl`、`SettingsRepositoryImpl`（平台 actual）。

#### 工具与 expect 声明

路径：`com.hmp.data.util`

- `DeviceMusicScanner.kt`（expect）：设备音乐扫描。
- `MusicTagParser.kt` / `MusicTagEditor.kt`（expect）：ID3 标签读写。
- `SecureStorageHelper.kt`（expect）：API Key 加密存储。
- `PinyinSortKey.kt` / `PinyinLookupTable.kt`：中文拼音排序。
- `DataStore.kt`（expect）：DataStore 构建。
- `DateFormats.kt`：日期格式化。

### 5.2 Domain 层

路径：`com.hmp.domain`

#### 领域模型

- `music/MusicModels.kt`：`MusicInfo`、`MusicExtraTexts`、`EditableMusicTags` 等。
- `playlist/PlaylistModels.kt`：`Playlist`、`PlaylistItem`。
- `setting/model/`：`AiModels`、`AudioEffectModels`、`ListeningDuration`、`PlaybackHistory`、`ScanDirectoryConfig`、`UserUsageAnalytics`、`WindowedUsageAnalytics`。
- `backup/UserBackupSnapshot.kt`：备份快照。
- `enum/`：`AiProviderType`、`LabelCategory`、`LabelName`、`PlaybackMode`。
- `config/LyricsConfig.kt`、`lyrics/`：歌词解析（`LrcParser`、`KaraokeTiming`）。

#### Repository 接口

- [MusicRepository.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/domain/music/MusicRepository.kt)：音乐仓库接口，**约 60 个方法**，覆盖查询/扫描/标签/相似推荐/听歌统计/富化健康度/画像快照/窗口分析。
- `PlaylistRepository.kt`、`SettingsRepository.kt`、`BackupFileRepository.kt`。

#### Use Cases

路径：`com.hmp.domain.*.usecase`

- **music**：`GetAllMusicUseCase`、`SearchMusicUseCase`、`LoadMusicFromDeviceUseCase`、`SyncMusicFromDeviceIncrementalUseCase`、`GetDailyMusicRecommendationUseCase`、`EditMusicTagsUseCase`、`MusicLabelUseCase`、`RemoveFromLibraryUseCase`、`RestoreToLibraryUseCase`、`GetDeletedMusicIdsGroupedByFolderUseCase`。
- **playlist**：`ManagePlaylistUseCase`、`GeneratePlaylistUseCase`（智能歌单算法 `algorithm/` 含相似度计算 `SimilarityCalculator`、权重 `WeightManager`、策略 `SimilarityStrategies`）。
- **setting**：`UserSettingsUseCase`、`LyricsSettingsUseCase`、`CurrentPlaybackUseCase`、`PlaybackHistoryUseCase`、`GetUserUsageDataUseCase`、`TimerUseCase`。
- **backup**：`ExportUserDataBackupUseCase`、`ImportUserDataBackupUseCase`、`GetBackupsUseCase`、`DeleteBackupUseCase`。

### 5.3 DI 装配

[SharedModules.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/di/SharedModules.kt)：`sharedModule` 单例装配：

- 1 个 HttpClient + 1 个 Adapter（共享连接池）
- 4 个命名 `LlmTransport`（`AGENT_CHAT` / `AGENT_ENRICH` / `AGENT_HELLO` / `AGENT_RADIO`，每 Agent 独立 Transport）
- `PlaybackObservationBus`（观测面总线，单例）
- `GlobalTokenCounter` + `TokenMeter`（F12 Token 计量基础设施）
- `MasterAgent` 单例（注入全部依赖，启动时 `initialize()`，AI 配置热监听）
- 全部 Use Case（`single { ... }`）

平台模块：`androidPlatformModule` / `DesktopModules` / `IosModules` 提供 Room 数据库、平台 Repository、DataStore 等。

---

## 6. shared-ui 模块详细说明

`shared-ui` 是三端共享 UI 模块，包名 `com.hearablemusic.player.ui`。源集结构：

```
shared-ui/src/
├── commonMain/kotlin/.../ui/         # 三端共享 UI + ViewModel
│   ├── AppRoot.kt / MainShell.kt    # 应用壳 + Tab 壳
│   ├── agent/                       # AI 对话/电台/Hello 卡/控制台/监控
│   ├── common/                      # 组件/设计系统/对话框/布局/导航/工具/ViewModel
│   ├── library/                     # 音乐库页面（首页/画廊/列表/搜索/详情/标签编辑）
│   ├── platform/                    # expect 平台服务接口（PlaybackController 等）
│   ├── player/                      # 播放页/歌词/迷你播放栏
│   ├── playlist/                    # 歌单页
│   ├── settings/                    # 设置/备份/音频效果/用户页
│   └── startup/DefaultPlaylistGuard.kt
├── commonMain/composeResources/     # 共享资源（图标 webp/xml + 字体 + 14 语言 strings）
├── androidMain/                      # Android actual（PlatformServices/PlaybackController 适配）
├── desktopMain/                      # Desktop actual
└── iosMain/                          # iOS actual（双桥 PlaybackController / PlatformServices / 触觉）
```

### 6.1 应用壳与导航

- [AppRoot.kt](file:///workspace/shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/AppRoot.kt)：**应用壳根 Composable**。职责：
  - `NavDisplay`（Navigation 3）宿主 + 转场动画
  - Tabs（`MainShell`）+ 全部二级页路由
  - 播放态动态主题 + 动态背景（`DynamicBackground`）
  - 自适应布局：`BottomFusionBar`（底部融合栏）/ `FusionSidebar`（横屏侧栏）/ `TabPageIndicator`
  - 全局 `DialogHost` + `MessageToast` + `ConfirmChain` 双确认链
  - `RadioConsole`（电台控制台浮层）
  - `AgentNoticeBar`（PresenceBus 事件侧条）
  - 平台能力经 `PlatformServices`（分享/悬浮歌词）
- [MainShell.kt](file:///workspace/shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/MainShell.kt)：`HorizontalPager` 4 页（Home/Gallery/List/User），切页触觉反馈。
- 导航：`common/navigation/`
  - [Routes.kt](file:///workspace/shared-ui/src/commonMain/kotlin/com/hearablemusic/player/ui/common/navigation/Routes.kt)：**集中式路由定义**，`@Serializable` NavKey（Main/Player/Library/Playlist/Settings/AI/Companion/Recommend 等模块）。
  - `NavigationGraph.kt`：路由 → 页面 Composable 映射。
  - `Router.kt` / `HmpNavBackStack.kt`：自研 NavController + 序列化注册表。

### 6.2 设计系统

路径：`common/design/`

- `core/DesignSystem.kt`：设计系统入口。
- `colors/ColorTokens.kt`：色彩 token。
- `typography/TypographyTokens.kt`：字体（HarmonyOS Sans 家族）。
- `dimens/HMPDimens.kt`：尺寸 token（`LocalHMPDimens`）。
- `animation/AnimationTokens.kt`：动画时长/缓动。
- `theme/HearableMusicPlayerTheme.kt` / `ThemeManager.kt` / `ThemeExtensions.kt`：主题管理（含 `ThemeExtensionManager` 动态调色板生成）。

### 6.3 通用组件

- `common/components/base/`：`HMPCard`、`HMPTextField`、`MyButton`、`DefaultEmpty/Error/Loading`、`UiStateContent`。
- `common/components/`：`Avatar`、`Capsule`、`SectionHeader`、`SegmentedControl`、`SharedLabelIcon`、`TabPageIndicator`。
- `common/dialogs/`：`CreatePlaylistDialog`、`MusicDetailDialog`、`MusicPickerDialog`、`PlaylistPickerDialog`、`TimerDialog`、`MusicScanDialog`；base `ConfirmDialog`/`InputDialog`/`MessageToast`/`ScrimDialog`；`DialogManager` + `DialogViewModel` + `DialogManagerViewModel`。
- `common/layout/WindowSizeClass.kt`：响应式断点（Compact/Medium/Expanded + `useFusionSidebar`/`isLandscape`）。
- `common/util/`：`HapticFeedbackHelper`、`HazeIntensity`、`PlatformFile`/`PlatformImage`/`PlatformPath`/`PlatformTime`/`PlatformHaptics`（expect）、`UiState`、`activityViewModel`（应用级 VM owner 契约）。

### 6.4 平台桥接（expect）

路径：`ui/platform/`

- `PlaybackController.kt`（expect）：播放控制统一接口，平台 actual 委托各端引擎。
- `PlatformServices.kt`（expect）：分享/文件选择/悬浮窗权限/标签编辑桥/触觉/悬浮歌词。
- `AlbumArtPixelsLoader.kt`、`StatusBars.kt`、`PlatformTime.kt`。

### 6.5 功能页面与 ViewModel

| 功能域 | 页面 | ViewModel |
|---|---|---|
| 音乐库 | `HomeScreen` / `GalleryScreen` / `ListScreen` / `SearchScreen` / `SongDetailScreen` / `AlbumScreen` / `ArtistScreen` / `EditMusicTagsScreen` / `RecommendListScreen` | `LibraryViewModel` / `SearchViewModel` / `SongDetailViewModel` / `EditMusicTagsViewModel` |
| 播放器 | `PlayerScreen` / `LyricsScreen` / `AdvancedLyrics` / `KaraokeLyricText` / `MiniPlayerBar` / `PlaylistArea` / `TechnicalInfoCard` | `PlaybackViewModel` / `PlaylistQueueViewModel` / `PlayerUiState` |
| 歌单 | `PlaylistScreen` / `PlaylistManageScreen` | `PlaylistViewModel` / `ArtistAlbumViewModel` |
| 设置 | `SettingScreen` / `UserScreen` / `AudioEffectsScreen` / `BackupSettingsScreen` / `LibrarySettingsScreen` / `LyricsSettingsPage` / `ProfileSettingsScreen` / `UserUsageDataScreen` | `SettingsViewModel` / `AudioEffectViewModel` / `BackupViewModel` / `LyricsSettingsViewModel` / `RecommendationViewModel` / `UserUsageDataViewModel` / `ThemeViewModel` |
| AI/Agent | `ChatScreen` / `AIScreen` / `AgentConfigScreen` / `AgentMonitorScreen` / `AuditLogScreen` / `RadioConsole` | `ChatViewModel` / `AiSettingsViewModel` / `AuditLogViewModel` |

---

## 7. 平台壳模块说明

### 7.1 Android (`:android:app` + `:android:core-player`)

#### `:android:app`（包 `com.hearablemusic.player`）

- [MusicApplication.kt](file:///workspace/android/app/src/main/java/com/hearablemusic/player/MusicApplication.kt)：`Application` 子类。
  - `initKermit()` 日志初始化、`MusicTagEditor.init()`
  - `startKoin { modules(sharedModule, androidPlatformModule, builtInAiModule, playerModule, uiModule) }`
  - 主动解析 `MasterAgent` 单例触发 `initialize()`；JVM shutdown hook 兜底清理
- [MainActivity.kt](file:///workspace/android/app/src/main/java/com/hearablemusic/player/MainActivity.kt)：`ComponentActivity`。
  - `musicController.bindService()` 绑定 Media3 服务
  - 注册 `AndroidPlatformServices`（分享/文件选择/悬浮窗/标签桥/触觉/悬浮歌词）
  - `enableEdgeToEdge` + `setContent { AppRoot(darkTheme) }`
  - 首启 `IntroScreen` 分流；权限授予后自动批处理推荐
- `build.gradle.kts`：签名 unified 配置、BuildConfig 注入内置 AI Key、minSdk 33/targetSdk 37、R8 混淆。

#### `:android:core-player`（包 `com.hearablemusic.player.player`）

- `service/MusicPlayService.kt`：Media3 `MediaSessionService`，管理 ExoPlayer + MediaSession + 通知。
- `controller/MusicController.kt`：播放控制器，UI 与 Service 的桥。
- `service/MusicNotificationReceiver.kt`：通知控制接收器。
- `AudioEffectManager.kt`：均衡器/低音增强/环绕音。
- `keepalive/AndroidAgentKeepAlivePort.kt`：Agent 后台保活端口（F11，前台服务）。
- `di/PlayerKoinModule.kt`：播放器 Koin 模块。

### 7.2 Desktop (`:desktop:app` + `:desktop:core-player`)

#### `:desktop:app`（包 `com.hmp.desktop`）

- [Main.kt](file:///workspace/desktop/app/src/desktopMain/kotlin/com/hmp/desktop/Main.kt)：`fun main()` 入口。
  - `initKermit()` + 文件启动日志（`%LOCALAPPDATA%\HMP\logs\`）
  - HiDPI/Skiko 渲染属性设置（METAL/OPENGL）
  - `SingleInstanceGuard.tryAcquire()` 单实例守卫
  - `HmpDesktopApplication.init()` Koin 装配
  - 后台预热线程（Room + FFmpeg + UseCase）
  - `application { Window(undecorated=true) { AppRoot() } }`
  - nav3 desktop 返回接线（`DirectNavigationEventInput`，Escape 键）
  - 系统暗色模式监听（Windows DWM / macOS `defaults read`）
- `CustomTitleBar.kt`：无边框窗口自定义标题栏。
- `SystemTrayManager.kt`：系统托盘（播放控制/显示窗口/退出）。
- `SingleInstanceGuard.kt`：单实例锁。
- `WindowHelper.kt` / `DwmHelper.kt`：窗口操作 + Windows DWM 主题 API（JNA）。
- `DesktopViewModelStoreOwner.kt`：应用级 VM owner。
- `HmpDesktopApplication.kt`：Koin 装配。
- `build.gradle.kts`：FFmpeg 下载/SHA256 校验/注入（按 OS+架构固定源）、jpackage 打包（DMG/MSI/DEB/AppImage）。

#### `:desktop:core-player`（包 `com.hmp.desktop.player`）

- `FFmpegAudioEngine.kt`：FFmpeg + JNA 自研音频引擎（实现 `AudioEngine`）。
- `AudioEngine.kt`：音频引擎接口。
- `DesktopMusicController.kt`：播放控制器，委托 `FFmpegAudioEngine`，实现 `PlaybackController`。
- `di/DesktopPlayerModule.kt`：Koin 模块。

### 7.3 iOS (`:ios`)

XcodeGen + CocoaPods 项目，包 `com.hearablemusic.HMP`。

- `HMP/HMPApp.swift`：`@main App`，`@UIApplicationDelegateAdaptor(AppDelegate.self)`。
- `HMP/AppDelegate.swift`：
  - `KermitInitKt.initKermitForIos()`
  - `MPMediaLibrary.requestAuthorization` 音乐库权限
  - `IosUiKoinModuleKt.installKoinIosWithSharedUi()` 一次性装配 shared + shared-ui
  - `MetadataParserBridge().register(MusicMetadataParser())` 标签桥
  - `ArtworkBridge().register(ArtworkExtractor())` 封面桥
  - `MusicPlayerController.shared.initializeDefaultPlaylists()`
  - `PlaybackBridge.install()` / `PlatformServicesBridge.install()` 双桥（方向 A）
- `HMP/Features/Player/`：
  - `PlayerEngine.swift` / `MusicPlayerController.swift`：AVFoundation 播放引擎。
  - `AudioSessionManager.swift`：音频会话。
  - `MediaSession/`：`HMPMediaSession` / `RemoteCommandManager` / `NowPlayingInfoManager` / `LiveActivityManager` / `ArtworkLoader` / `CoverCache` / `HMPLiveActivityAttributes`。
  - `PlaybackBridge.swift` / `PlatformServicesBridge.swift`：Swift → Kotlin `PlaybackController` / `PlatformServices` 双桥。
  - `Common/Util/ArtworkExtractor.swift`、`Features/Library/MusicMetadataParserBridge.swift`。
- `HMPNowPlaying/`：Live Activity 扩展（`HMPNowPlayingLiveActivity.swift`）。
- `Podfile`：`pod 'shared_ios'`（聚合框架），Kotlin 2.3 适配（`linkPod{Debug|Release}FrameworkIos` + 产物同步）。

### 7.4 shared-ios 聚合框架

[:shared-ios](file:///workspace/shared-ios/build.gradle.kts)：把 `:shared` + `:shared-ui` 链接为单一 static framework `sharedIos`，Swift 统一 `import sharedIos`。
- 解决双静态框架 duplicate symbol 与动态框架 Koin 全局分裂。
- 仅 `iosArm64` + `iosSimulatorArm64`（navigation3-ui 无 iosX64 构件）。

---

## 8. AI Agent 体系

方向 B（已随 v7.2.0 交付 F1–F14）。位于 `shared/commonMain/kotlin/com/hmp/domain/agent/`。

### 8.1 运行时（runtime/）

- [MasterAgent.kt](file:///workspace/shared/src/commonMain/kotlin/com/hmp/domain/agent/runtime/MasterAgent.kt)：**唯一大脑**（铁则 F1）。
  - ① **对话能力**：`handleUserMessage()` 多轮 LLM 对话 + tool_result 回传 + PolicyGuard 许可门 + ConfirmGate 批量确认 + 审计。
  - ② **后台管理**：`startEnrich/stopEnrich/pauseEnrich/resumeEnrich/rescanEnrich`、`startRadio/stopRadio`、`startHello`。
  - ③ **全局基础设施**：`AgentScheduler`（priority 仲裁 pause/resume）+ `GlobalTokenCounter`（日配额）。
  - 装配 4 个 SubAgent（Chat/Enrich/Hello/Radio），每 Agent 独立 `LlmTransport`。
- `ReActLoop.kt`：ReAct 推理循环。
- `LlmCallExecutor.kt` / `ToolCallExecutor.kt`：LLM 调用与工具执行。
- `ContextAssembler.kt`：上下文装配。
- `AgentContextBudget.kt` / `TokenMeter.kt` / `GlobalTokenCounter.kt`：Token 计量与窗口预算（F12）。
- `AgentScheduler.kt`：后台调度仲裁。
- `sub/`：四个 SubAgent
  - `enrich/EnrichSubAgent.kt`：富化（拉活/处理/验收/重试）。
  - `hello/HelloSubAgent.kt`：每日 Hello 卡（推荐/问候/发现/遗忘/周年纪念/封面/叙事）。
  - `radio/RadioSubAgent.kt`：AI 电台（生成歌单/续队列）。
  - `shared/SubAgent.kt`：基类。

### 8.2 工具（tool/）

- `spec/ToolRegistry.kt` / `ToolSpec.kt` / `ToolNames.kt`：工具注册表（共 32 工具：27 基础 + 5 enrich_*）。
- `LibraryTools.kt` / `LibraryBatchBTools.kt`：曲库查询/批量。
- `PlaylistTools.kt`：歌单操作。
- `PlaybackEnqueueAndBudget.kt` / `ContextPlaybackTools.kt`：播放入队与上下文。
- `EnrichTools.kt`：富化专属工具。
- `ProfileTools.kt` / `StatsTools.kt`：画像与统计。
- `ToolCatalog.kt` / `ToolDependencies.kt`。

### 8.3 策略与端口（policy/ + port/）

- `policy/`：`AgentPolicy`、`PolicyGuard`（许可护栏）、`TrustLedger`（信任账本）、`TrustLevel`。
- `port/`：`LlmTransport`、`PlaybackCommandPort`、`PlaybackObservation`（观测面）、`AuditLogPort`、`AgentMessageStore`、`AgentKeepAlivePort`（保活）、`ConfirmGate`/`ConfirmChain`（批量确认）、`Capability`/`ToolPermissionLevel`、`TimeProvider`/`WallClock`。

### 8.4 用户画像（profile/）

- `BehaviorModeler.kt` / `BPortraitModeler.kt`：行为建模。
- `LibraryModeler.kt`：曲库建模。
- `PortraitComposer.kt` / `UserProfileRenderer.kt`：画像合成与叙事渲染。
- `PersonalityCard.kt` / `PortraitType.kt` / `ProfileNarrative.kt` / `UserMemory.kt`。
- 三快照：`LibraryContentSnapshot` / `LibraryStateSnapshot` / `BehaviorSnapshot`（见 `MusicRepository` 接口）。

### 8.5 基础设施（infra/ + persona/ + card/ + enrich/ + config/）

- `infra/`：`PresenceBus`（存在感事件总线）、`SessionStore`（会话存储）。
- `persona/CompanionProfile.kt`：伙伴人格。
- `card/`：`CardPool`、`SlideModels`（Hello 卡片池）。
- `enrich/EnrichModels.kt`：富化数据模型。
- `config/`：`AgentConfigModels`、`EngineDefaults`。

### 8.6 UI 侧（shared-ui/agent/）

- `chat/`：`ChatScreen` / `ChatViewModel` / `ChatAgentGateway` / `ChatEntryBroker` / `ChatKoinModule` / `CompanionBubble` / `CompanionMessage`。
- `cards/`：12 种 Hello 卡 Composable（`HelloCardGreeting`/`Discover`/`Recommend`/`Anniversary`/`Coverflow`/`Narrative`/`RadioStatus`/`SingleTrack`/`EnrichTracking` 等）。
- `config/`：`AIScreen` / `AgentConfigScreen` / `AiSettingsViewModel`。
- `monitor/`：`AgentMonitorScreen` / `AuditLogScreen` / `AuditLogViewModel`。
- `shell/`：`AgentNoticeBar` / `BottomFusionBar` / `FusionSidebar` / `CompanionCapsule` / `RadioConsole`。
- `port/ControllerAgentPorts.kt`：UI 侧端口适配器。

---

## 9. 依赖与技术栈

### 9.1 版本集中管理

[gradle/libs.versions.toml](file:///workspace/gradle/libs.versions.toml) 集中管理所有依赖版本与库坐标，规范命名（去前缀 `compose-*` / `navigation3-*` / `lifecycle-*`）。

### 9.2 核心版本（v7.2.0）

| 领域 | 库 | 版本 |
|---|---|---|
| Kotlin | kotlin | 2.3.21 |
| AGP | com.android.tools.build:gradle | 9.1.1 |
| Compose MP | org.jetbrains.compose | 1.11.1 |
| Material3 | compose.material3 | 1.11.0-alpha07 |
| Navigation3 | androidx.navigation3 | 1.1.1 |
| Lifecycle | androidx.lifecycle | 2.11.0-beta01 |
| Room | androidx.room | 2.8.3 |
| SQLite | androidx.sqlite:sqlite-bundled | 2.6.1 |
| DataStore | androidx.datastore | 1.1.7 |
| Ktor | io.ktor | 3.1.1 |
| Media3 | androidx.media3 | 1.8.0 |
| Koin | io.insert-koin | 4.2.2 |
| Kermit | co.touchlab:kermit | 2.1.0 |
| Coil3 | io.coil-kt.coil3 | 3.3.0 |
| Haze | dev.chrisbanes.haze | 1.7.2 |
| JNA | net.java.dev.jna | 5.16.0 |
| Jaudiotagger | net.jthink:jaudiotagger | 3.0.1 |
| kotlinx-serialization | 1.7.3 |
| kotlinx-coroutines | 1.10.1 |
| 测试 | junit 4.13.2 / mockk 1.13.13 / turbine 1.2.0 / robolectric 4.14.1 |

### 9.3 平台技术对照

| 领域 | Android | Desktop | Shared (KMP) | iOS |
|---|---|---|---|---|
| UI | shared-ui Compose + Material3 + Haze | shared-ui Compose (响应式) | — | shared-ui Compose（v7.1 起） |
| 导航 | shared-ui Navigation 3 | 与 Android 共用 | — | 与 Android 共用 |
| 播放 | Media3 ExoPlayer | FFmpeg + JNA 自研引擎 | — | AVFoundation |
| 数据库 | Room KMP | Room KMP | Room KMP | Room KMP |
| 偏好存储 | DataStore | DataStore KMP | DataStore KMP | UserDefaults |
| 网络 | — | — | Ktor Client | Ktor (Darwin) |
| 序列化 | kotlinx.serialization | 同左 | 同左 | — |
| DI | Koin | Koin | Koin | Koin |
| 图片 | Coil3 (ktor 网络引擎) | shared-ui 内统一 | shared-ui 内统一 | shared-ui 内统一 |
| 标签解析 | Jaudiotagger + pinyin4j | Jaudiotagger | 平台特定 | AVAsset 元数据 |

### 9.4 跨平台机制（expect/actual）

`commonMain` 声明 `expect`，平台 `actual` 实现：

- `DeviceMusicScanner` — 设备音乐扫描
- `MusicTagParser` / `MusicTagEditor` — 标签读写
- `SecureStorageHelper` — API Key 加密
- `stringToPinyinSortKey()` — 拼音排序键
- `getRoomDatabase()` / `AppDatabaseConstructor` — Room 构建
- `DataStoreFactory` — DataStore 构建
- `createHttpClient()` / `createJson()` — Ktor + JSON
- `currentTimeMillis()` — 平台时间戳
- `PlaybackController` / `PlatformServices` — 播放控制与平台服务（在 shared-ui）
- `Synchronized` / `Volatile` — K/N 并发原语

---

## 10. 项目运行方式

### 10.1 环境要求

| 平台 | 工具 | 版本 |
|---|---|---|
| 通用 | JDK | 21（Desktop jpackage 要求 Gradle Daemon 运行于 JDK 21） |
| 通用 | Gradle | 9.x |
| Android | Android Studio | Ladybug 2024.2.1+ |
| Android | Android SDK | compileSdk 36/37, minSdk 33 |
| iOS | Xcode | 17.0+ |
| iOS | CocoaPods | 1.16.0+ |
| iOS | macOS | 14.0+，iOS 部署目标 26.3 |
| Desktop | FFmpeg | 构建时按 OS+架构自动下载（SHA256 校验） |

### 10.2 系统要求（运行时）

- Android 13 (API 33)+
- Desktop: macOS 14+ / Windows 10+ / Ubuntu 22.04+
- iOS 26.0+

### 10.3 常用命令

#### Android

```bash
./gradlew build                          # 构建全部
./gradlew :android:app:assembleDebug      # Debug APK
./gradlew :android:app:assembleRelease     # Release APK（R8 混淆）
./gradlew :android:app:installDebug        # 安装到设备
./gradlew :shared:compileAndroidMain       # 编译 shared Android
```

#### iOS

```bash
./gradlew :shared-ios:generateDummyFramework   # 生成聚合框架 + podspec
cd ios && pod install                          # 安装 CocoaPods 依赖
open HMP.xcworkspace                           # Xcode 打开工作空间

# 模拟器构建（Apple Silicon）
xcodebuild -workspace ios/HMP.xcworkspace -scheme HMP -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```

#### Desktop

```bash
./gradlew :desktop:app:run                              # 运行（开发调试）
./gradlew :desktop:app:packageDistributionForCurrentOS  # 当前 OS 安装包
```

#### 单端构建（环境变量）

```bash
HMP_BUILD_TARGET=android ./gradlew build   # 只构建 Android 相关模块
HMP_BUILD_TARGET=desktop ./gradlew build   # 只构建 Desktop 相关模块
```

#### 测试

```bash
./gradlew test                              # 单元测试
./gradlew connectedAndroidTest              # Android 仪器测试
```

### 10.4 首次使用流程

1. 克隆：`git clone https://github.com/InfiniteMotion/HMP.git`
2. **Android**：Android Studio 打开 → 构建 → 首启请求 `READ_MEDIA_AUDIO` 权限 → 扫描 → 浏览播放
3. **Desktop**：`./gradlew :desktop:app:run` → 设置中选择扫描目录 → 扫描 → 浏览播放
4. **iOS**：生成聚合框架 → `pod install` → Xcode 构建 → 首启请求音乐库权限 → 扫描 → 浏览播放

---

## 11. 测试体系

### 11.1 测试任务（自定义 26 个）

| 类别 | 任务 | 说明 |
|---|---|---|
| 验证-测试 | `testAll` | 全部单元测试（按构建目标自动跳过未纳入模块） |
| 验证-测试 | `testCore` / `testQuick` | 只测 `:shared`（Domain/Agent/Data 纯逻辑） |
| 验证-测试 | `testUi` | 测 `:shared-ui`（`desktopTest` + `testAndroidHostTest`） |
| 验证-测试 | `testUiDesktop` | 只测 `:shared-ui` desktop 源集（不依赖 Android SDK） |
| 验证-测试 | `testDesktop` / `testAndroid` | 只测对应平台侧 |
| 验证-编译 | `compileAll` / `compileCore` / `compileUi` / `compileDesktop` / `compileAndroid` | 编译校验（检测公共 API 破坏） |
| 发布 | `release` / `releaseAndroid` / `releaseIos` / `releaseDesktop` / `copyAndroidDebug` / `copyDesktopJar` / `checkVersion` / `preflight` | 发布构建 |
| 清理 | `cleanReleases` / `cleanOrphans` | 清理产物 / 列出孤儿 build 目录 |

> ⚠️ `shared-ui` 测试全在 `androidHostTest` 源集（`desktopTest` 为空），故 `testUi` 同时挂两个源集。
> Android 侧（`android:app` / `android:core-player`）不暴露 `compile*` 任务，`compileAndroid` 用 `assembleDebug`。

### 11.2 测试报告

跑完测试任务后，报告自动出现在被依赖的底层任务目录下：
- `<模块>/build/reports/tests/<任务名>/index.html`（人看）
- `<模块>/build/test-results/<任务名>/TEST-*.xml`（CI 解析）

### 11.3 测试源集分布

- `shared/commonTest`：纯逻辑（`OpenAiLlmTransportTest`、`PinyinLookupTableTest`）
- `shared/desktopTest`：Room 迁移测试（`AppDatabaseMigrationTest`）、DAO 测试（`MusicDaoTest` 等）、Repository 测试、标签解析测试
- `shared-ui/androidHostTest`：8 个文件（`ChatViewModelTest` / `RouterTest` 等，JVM Robolectric 风格）
- `shared-ui/desktopTest`：当前为空
- `desktop/core-player/desktopTest`：`DesktopMusicControllerTest` / `DesktopPlaybackQueueTest` 等

### 11.4 低内存构建

本机内存受限时用包装脚本（daemon 堆压到 1024m + Kotlin 编译器 in-process）：

```bash
./gradlew-lowmem testCore        # macOS/Linux/Git Bash
./gradlew-lowmem.bat testCore    # Windows
```

### 11.5 日常开发选任务

| 改动范围 | 建议命令 |
|---|---|
| `shared` 的 Domain/Agent | `testCore` |
| `shared-ui` 的 UI | `testUi`（无 Android SDK 用 `testUiDesktop`） |
| 改了公共 API | `compileAll`（快速查下游破坏） |
| 提交前 | `testAll` |
| 发版前 | `preflight` |

---

## 12. 发布与 CI/CD

### 12.1 版本号管理

集中维护在 [gradle.properties](file:///workspace/gradle.properties)：

```properties
hmp.versionCode=72000
hmp.versionName=7.2.0
```

各模块通过 `project.findProperty("hmp.versionCode")` 引用。

### 12.2 分支策略

- `master`：已发布版本（MINOR/MAJOR 从 release/X.Y 合并，PATCH 可直接改并打 tag）
- `release/X.Y`：发版集成分支，feature 合入后 PR 到 master 触发发布
- `feature/*` / `fix/*`：功能/修复分支

### 12.3 发布流程

1. 确定版本类型（MAJOR/MINOR/PATCH）
2. 从 master 拉出 `release/X.Y`，合并各 feature 分支
3. 更新 `gradle.properties` 的 `hmp.versionCode` / `hmp.versionName`
4. 更新 [ROADMAP.md](file:///workspace/ROADMAP.md) 新版本条目
5. 本地构建：`./gradlew release`（输出 `releases/`，含 Android APK+AAB / Desktop DMG+MSI+DEB+AppImage；macOS 额外 iOS Archive）
6. release/X.Y PR 到 master，CI 自动构建并发布 GitHub Release

### 12.4 本地发布任务

```bash
./gradlew release           # 总入口（Android + Desktop；macOS 含 iOS）
./gradlew releaseAndroid    # Android APK + AAB
./gradlew releaseDesktop    # Desktop 分发包
./gradlew releaseIos        # iOS Archive（仅 macOS）
./gradlew copyAndroidDebug  # Debug APK
./gradlew copyDesktopJar    # Desktop Uber JAR
./gradlew preflight         # 发布前预检（版本号 + 测试 + 平台可产出告知）
```

### 12.5 CI/CD（`.github/workflows/release.yml`）

- **触发**：`release/*` 分支 PR 合并到 `master` 时自动触发
- **test job**：`./gradlew testAll` + 版本号重复检测
- **desktop-macos/windows/linux job**：并行构建桌面三平台安装包
- **release job**：构建 Android APK+AAB，汇总桌面产物，基于上一 tag 生成 changelog，创建 GitHub Release + SHA256 校验
- **deploy-site job**：将 `site/` 部署到 GitHub Pages（非 Storybook）

> CI 不使用封装任务，而是直接调用各模块底层任务并自行归集重命名产物。

### 12.6 FFmpeg 获取（Desktop）

[desktop/app/build.gradle.kts](file:///workspace/desktop/app/build.gradle.kts) 内置 `downloadFFmpeg` / `injectFFmpeg` 任务：
- 按 (OS, 架构) 固定 URL + SHA256（macOS arm64/amd64、linux amd64/arm64、windows amd64）
- SHA256 校验失败立即中断构建
- 二进制落 `build/ffmpeg/`，不进 git
- 指纹文件 `ffmpeg-artifact.txt` 判断是否复用本地二进制
- `injectFFmpeg` 在 app image 生成后、格式打包前注入 runtime bin 目录

---

## 附录：关键文件索引

| 类别 | 文件 |
|---|---|
| 项目概览 | [README.md](file:///workspace/README.md) |
| AI 协作速查 | [CLAUDE.md](file:///workspace/CLAUDE.md) |
| 技术架构 | [DEVELOP.md](file:///workspace/DEVELOP.md) |
| 版本历史 | [ROADMAP.md](file:///workspace/ROADMAP.md) |
| 任务列表 | [TODO.md](file:///workspace/TODO.md) |
| 文档索引 | [docs/README.md](file:///workspace/docs/README.md) |
| 设计系统 | [docs/DESIGN_SYSTEM.md](file:///workspace/docs/DESIGN_SYSTEM.md) |
| Room KMP 配置 | [docs/ROOM_KMP_SETUP.md](file:///workspace/docs/ROOM_KMP_SETUP.md) |
| 版本规范 | [docs/VERSIONING.md](file:///workspace/docs/VERSIONING.md) |
| 日志规范 | [docs/LOGGING.md](file:///workspace/docs/LOGGING.md) |
| Agent 设计总纲 | [docs/7_x/B agent-build/design/agent.md](file:///workspace/docs/7_x/B%20agent-build/design/agent.md) |
| Agent 推进计划 | [docs/7_x/B agent-build/taskbook/README.md](file:///workspace/docs/7_x/B%20agent-build/taskbook/README.md) |
| v7.2 审查报告 | [docs/7_x/B agent-build/review-7.2.md](file:///workspace/docs/7_x/B%20agent-build/review-7.2.md) |
| 根构建脚本 | [build.gradle.kts](file:///workspace/build.gradle.kts) |
| 模块配置 | [settings.gradle.kts](file:///workspace/settings.gradle.kts) |
| 版本目录 | [gradle/libs.versions.toml](file:///workspace/gradle/libs.versions.toml) |
| Gradle 属性 | [gradle.properties](file:///workspace/gradle.properties) |
| CI 工作流 | [.github/workflows/release.yml](file:///workspace/.github/workflows/release.yml) |

---

© 2026 Hearable Music Player | Code Wiki 自动生成
