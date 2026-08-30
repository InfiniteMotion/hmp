# Hearable Music Player 待办事项

本文档仅包含可执行的任务列表，帮助我规划和跟踪个人项目的进展。

## 📋 相关文档

- [docs/README](docs/README.md) — 项目文档索引与各文档职责
- [ROADMAP](ROADMAP.md) — 功能状态与版本历史
- [设计文档](docs/5_10/ios-adaptation-design.md) — iOS 适配技术设计
- [实施计划](docs/5_10/ios-adaptation-plan.md) — v5.10 详细实施步骤
- [UI 差异对照](docs/5_10/ios-android-ui-diff.md) — iOS vs Android UI 原生优势与简化策略
- [Agent 设计总纲](docs/7_x/agent.md) — 音乐伙伴方案单一事实来源（定位/本体/认识论/能力面/交互实施/工程）
- [Agent 落地任务书](docs/7_x/agent-task-book.md) — 阶段制 M0-M7 实施规划（不绑定版本号，含验收标准与挂起参数默认值）

---

## v5.10：iOS 平台适配与双平台架构（v5 系列最终版本）

**现状**：Android 端已完成 KMP 迁移（Hilt → Koin，domain + data 层移入 shared），iOS 端 SwiftUI 界面已大规模实现（设计系统 ✅、通用组件 ✅、播放模块 ✅、ViewModel 全线接入 ✅），部分设置页面后端逻辑待 v6 阶段完善。

**技术路线**：KMP 共享核心层（domain + data），UI 和播放引擎保持平台原生，Monorepo 结构。

**实际进度**：
- P0-P5: ✅ 全部完成
- P6: ✅ 全部完成（核心功能已实现，P6.10/P6.11/P6.12/P6.15 的存根完善归入 v6）
- P7: ✅ 全部完成（部分设置页面后端为模拟实现，归入 v6）

---

### P0: Monorepo 项目骨架 ✅

- [x] **P0.1** 调整目录结构 — 现有模块移入 `android/`，创建 `shared/` 和 `ios/` 空目录
- [x] **P0.2** 更新根构建配置 — `settings.gradle.kts`、`build.gradle.kts`、`libs.versions.toml` 新增 KMP 依赖
- [x] **P0.3** 创建 shared KMP 模块 — `build.gradle.kts`（androidTarget + iosX64/Arm64/SimulatorArm64 + CocoaPods）+ 空目录结构
- [x] **P0.4** 更新 Android 模块路径 — 所有 `project(":core-data")` 等引用改为 `project(":android:core-data")`
- [x] **P0.5** 验证 — `./gradlew :android:app:assembleDebug` 通过 (126 tasks BUILD SUCCESSFUL)

### P1: core-domain 迁移 ✅

- [x] **P1.1** 移动 core-domain 源文件（36 个 .kt 文件）到 `shared/src/commonMain/kotlin/com/hmp/domain/`
- [x] **P1.2** 移除 Android 依赖 — `GetDailyMusicRecommendationUseCase.kt` 中 `android.util.Log` 替换为 `println`；移除 `javax.inject.Inject` 注解
- [x] **P1.3** 更新 shared 模块依赖 — 确认 commonMain 依赖完整
- [x] **P1.4** 更新 Android 端引用 — core-data/feature-ui/core-player 的依赖和 import 路径
- [x] **P1.5** 验证 — `./gradlew :shared:compileAndroidMain` 通过 + `./gradlew :android:app:assembleDebug` 通过 (117 tasks BUILD SUCCESSFUL in 26s)
- [x] **P1.6** 清理空模块 — 移除 `settings.gradle.kts` 中的 `include(":android:core-domain")`，删除目录

### P2: core-data 迁移 — Room ✅

- [x] **P2.1** 移动 Room 数据库文件（10 个）到 `shared/src/commonMain/kotlin/com/hmp/data/database/`
- [x] **P2.2** 改造 AppDatabase 为 KMP 模式 — `@ConstructedBy` + `expect object AppDatabaseConstructor`
- [x] **P2.3** 迁移 Room Migration — 简化迁移逻辑，使用 `fallbackToDestructiveMigration()`
- [x] **P2.4** 创建平台特定 Database Builder — androidMain（Context.getDatabasePath）+ iosMain（NSFileManager）
- [x] **P2.5** 检查 DAO 兼容性 — 移除 `PagingSource` 相关查询（KMP commonMain 不可用）
- [x] **P2.6** 验证 — `./gradlew :shared:compileAndroidMain` 通过（iOS 编译需要 macOS + Xcode 环境）

### P3: core-data 迁移 — 网络 ✅

- [x] **P3.1** 移动 `MultiProviderApiAdapter.kt` 到 `shared/src/commonMain/kotlin/com/hmp/data/network/`
- [x] **P3.2** 重写 MultiProviderApiAdapter — OkHttp → Ktor Client，Gson → kotlinx.serialization（DTO 添加 `@Serializable`）
- [x] **P3.3** 创建平台特定 HttpClient — androidMain（OkHttp engine）+ iosMain（Darwin engine）
- [x] **P3.4** 验证 — `./gradlew :shared:compileAndroidMain` 通过（iOS 编译需要 macOS + Xcode 环境）

### P4: core-data 迁移 — DI / 标签 / 存储 / 工具 ✅

- [x] **P4.1** 目录结构创建完成
- [x] **P4.2a** 设备音乐扫描 expect/actual — `DeviceMusicScanner` ✅
- [x] **P4.3** 音乐标签解析 expect/actual — `MusicTagParser` ✅
- [x] **P4.4** 安全存储 expect/actual — `SecureStorageHelper` ✅
- [x] **P4.5** 拼音排序 expect/actual — `stringToPinyinSortKey()` ✅
- [x] **P4.6** DataStore KMP 配置 — `DataStoreFactory` expect/actual ✅
- [x] **P4.1b** 移动 Repository 实现（4 个）和 Mapper（2 个）到 shared ✅
- [x] **P4.7** 配置 Koin DI 模块 ✅
- [x] **P4.8** 配置 shared 模块测试源集 ✅
- [x] **P4.9** 验证 — `./gradlew :shared:compileAndroidMain` 通过 ✅

### P5: Android 端适配 ✅

- [x] **P5.1** 更新 Android 模块依赖 — 删除 `android/core-data` 和 `android/core-domain` 模块，所有消费者依赖 `:shared`
- [x] **P5.1b** 更新 core-player 依赖 — 改为依赖 `:shared`，将 `MusicController` 切换为 Koin 管理
- [x] **P5.2** 更新 feature-ui 依赖 — 改为依赖 `:shared`，添加 Koin Compose 依赖，移除 Hilt
- [x] **P5.3** 更新 ViewModel 注入方式 — 14 个 ViewModel 从 `@HiltViewModel` 切换为 `koinViewModel()`
- [x] **P5.4** 更新 Application 类 — `@HiltAndroidApp` → `startKoin { modules(sharedModule, androidPlatformModule) }`
- [x] **P5.5** 更新包名引用 — `com.hearablemusic.player.domain/data` → `com.hmp.domain/data`
- [x] **P5.6** 移除 feature-ui 中的 Gson 依赖
- [x] **P5.7** 移除 feature-ui 中的 Pinyin4j 依赖
- [x] **P5.8** 验证 — `assembleDebug` + `assembleRelease` 通过 ✅

### P6: iOS 端基础 ✅

> v5.10 完成状态：Kotlin iOS 编译 ✅，Koin 已启用，Xcode 编译通过 + 模拟器运行，核心功能已实现。存根完善（P6.10-P6.12/P6.15）归入 v6。

#### 阶段一：Kotlin iOS 编译 ✅

- [x] **P6.1** SecureStorageHelper.ios.kt — 存根实现（encrypt/decrypt 返回原文），编译通过，功能待完善 → 见 P6.10
- [x] **P6.2** BackupFileRepositoryImpl.ios.kt — 存根实现（saveBackup 返回路径，loadBackup 抛异常），编译通过，功能待完善 → 见 P6.13
- [x] **P6.3** DeviceMusicScanner.ios.kt — 存根实现（isDirectoryAtPath 返回 false，元数据硬编码），编译通过，功能待完善 → 见 P6.11
- [x] **P6.4** SettingsRepositoryImpl.ios.kt — ✅ 已修复，`currentTimeMillis()` 正确使用
- [x] **P6.5** 验证 — `./gradlew :shared:compileKotlinIosSimulatorArm64` 编译通过（需 macOS 环境）✅

#### 阶段二：KMP 框架集成与 DI 打通 ✅

- [x] **P6.6** Podfile 构建脚本 — `post_install` 中 `[CP-User] Build shared` 阶段调用 Gradle 编译 `shared.framework`，`FRAMEWORK_SEARCH_PATHS` 正确指向 `shared/build/cocoapods/framework` ✅
- [x] **P6.8** 接入 Swift 层 Koin 初始化 — `AppDelegate.swift` 中 `KoinInitializer()` 已启用，`import shared` 已添加 ✅
- [x] **P6.9** 验证 — Xcode 编译通过 + 模拟器可运行 + Koin 初始化成功 ✅

#### 阶段三：iOS 核心功能实现

- [ ] **P6.10** 完善 SecureStorageHelper + DeviceMusicScanner — SecureStorageHelper 仍为存根（encrypt/decrypt 返回原文），需实现 Keychain 加密存储；DeviceMusicScanner 已实现 NSFileManager 目录扫描 + FNV-1a 稳定 ID + MusicTagParser Bridge 委托
- [ ] **P6.11** 完善 MusicTagParser.ios.kt — 已实现 Bridge 模式委托 Swift `MusicMetadataParser`，需完善元数据提取（bitRate/sampleRate 等技术信息）和歌词解析（ID3 或 LRC）
- [ ] **P6.12** 实现 PinyinSortKey.ios.kt — 使用 `CFStringTransform`（`kCFStringTransformToLatin` + `kCFStringTransformStripDiacritics`）将中文转拼音排序键（当前：直接返回原字符串）
- [x] **P6.13** 实现 MusicRepositoryImpl — 完整 835 行实现：扫描/持久化/增量同步/标签/AI 推荐/播放历史/分析/备份快照 ✅（2026-05-09）
- [x] **P6.14** 实现 PlaylistRepositoryImpl — DAO 操作委托 Room DAO，全平台复用 ✅（2026-04-26）
- [ ] **P6.15** 实现 BackupFileRepositoryImpl — 基于 `NSFileManager` 实现备份文件读写，补全 SettingsRepository 的 `backupSettings()`/`restoreSettings()`（当前：saveBackup 仅返回路径，loadBackup 抛异常）
- [x] **P6.16** 播放引擎实现 — `PlayerEngine.swift`（AVPlayer + addPeriodicTimeObserver 进度回调 + 播放结束通知）+ `MusicPlayerController.swift`（@Observable 单例编排器，播放/暂停/seek/切歌/队列/播放模式/历史/睡眠定时）+ `AudioSessionManager.swift`（耳机断开自动暂停）+ `NowPlayingInfoManager.swift`（锁屏远程控制）+ `HMPMediaSession.swift`（协调器）+ `LiveActivityManager.swift`（Live Activity）+ `RemoteCommandManager.swift`（锁屏命令）+ `DataStore.ios.kt` 修复持久化 ✅（2026-04-26）
- [x] **P6.17** 基础 SwiftUI 界面 — `MainTabView`（音乐库/播放/列表/设置 4 个 Tab）+ `LibraryView`（歌曲列表+点击播放+播放全部）+ `MiniPlayerBar`（实时播放状态）+ `PlayerScreen`（可拖动进度条/播放控制/队列Sheet/收藏/播放模式）✅（2026-04-26）
- [x] **P6.18** 验证 — 模拟器播放验证 ✅，锁屏控制 + Live Activity 已实现（真机验证待做）

### P7: iOS SwiftUI 界面迁移 ✅

> **对照 Android feature-ui 模块逐一映射**。Android 端使用 Navigation3 + HorizontalPager + Material3 + Haze 毛玻璃；iOS 端使用 NavigationStack + TabView(.page) + SwiftUI 原生 + Liquid Glass（iOS 26+ 液态玻璃，回退 .regularMaterial）。
>
> v5.10 完成状态：全部模块界面已实现，部分设置页面后端为模拟数据（归入 v6 阶段 P9）。

#### P7-A: 设计系统与基础 ✅

- [x] **P7.1** ColorTokens.swift — 品牌色 `HDBlue(#002FA7)` / `HDRed(#C92C2C)` + 浅色/深色主题完整色板，对应 Android `ColorTokens.kt`
- [x] **P7.2** TypographyTokens.swift — HarmonyOS Sans 字体族（6 字重）+ Material3 Typography 完整定义（displayLarge 40pt ~ labelSmall 11pt），sp→pt 1:1 映射，对应 Android `TypographyTokens.kt`
- [x] **P7.3** AnimationTokens.swift — 持续时间（MICRO 200ms / TRANSITION 400ms / COMPLEX 650ms / BACKGROUND 3000ms）+ 缓动函数（EASE_IN_OUT / EASE_OUT / EASE_IN 对应 UnitCurve）+ Spring 配置，对应 Android `AnimationTokens.kt`
- [x] **P7.4** HMPTheme.swift — 主题入口，`@Environment` 注入 ColorTokens + TypographyTokens，支持浅色/深色/动态取色切换，对应 Android `HearableMusicPlayerTheme.kt` + `DesignSystem.kt`

#### P7-B: 通用组件 ✅

- [x] **P7.5** UiState 泛型体系 — `enum UiState<T>` (idle/loading/success/error/empty) + `@ViewBuilder` 条件视图（iOS 无需独立 UiStateContent 组件，SwiftUI 天然支持），对应 Android `UiState.kt` + `UiStateContent.kt`
- [x] **P7.6** 状态占位组件 — SwiftUI 原生 `ProgressView` / `ContentUnavailableView` 支持，无需独立实现
- [x] **P7.7** SegmentedControl — **改用原生 `SegmentedControl` / `Picker(.segmented)`**，自带滑块动画+无障碍+动态类型，无需手写
- [x] **P7.8** TabPageIndicator.swift — 顶部胶囊圆点页面指示器（带颜色动画），对应 Android `TabPageIndicator.kt`
- [x] **P7.9** 弹窗基础 — `MusicDetailDialog` / `CreatePlaylistDialog` / `TimerDialog` 已实现；`ConfirmDialog`/`InputDialog` 改用原生 `.alert()`
- [x] **P7.10** 业务弹窗 — `MusicDetailDialog` / `CreatePlaylistDialog` / `TimerDialog` ✅，`HMPDialogs` 汇总
- [x] **P7.11** 页面模板 — `TabScreen` + `SubScreen` 已实现；`DynamicBackground` 待实现（FluidBackgroundView 占位）
- [x] **P7.12** 小组件 — `Avatar` / `Capsule` / `TitleWidget` / `AlbumCover` / `MiniPlayerBar` 全部实现
- [x] **P7.13** 触觉反馈 — `HapticManager` (lightClick/click/confirm/longPress/dragStart/contextClick)，对应 Android `HapticFeedback.kt`

#### P7-C: 导航框架 ✅

- [x] **P7.14** Route 枚举 — `HMPRoute: Hashable` + `TabItem: CaseIterable`，包含所有路由 + 底部 Tab
- [x] **P7.15** HMPApp.swift 改造 — `AppDelegate` 已创建，`@UIApplicationDelegateAdaptor` 已添加，Koin 已通过 `KoinInitializer().doInit()` 启用，Swift Bridge（MetadataParserBridge / ArtworkBridge）已注册 ✅
- [x] **P7.16** MainTabView — `TabView(.page)` + `MiniPlayerBar` overlay，含 `LibraryView`/`PlayerView`/`PlaylistView`/`SettingsView`

#### P7-D: 音乐库模块（对照 Android `ui/library/`）✅

- [x] **P7.17** MusicList 组件族 — `MusicList` / `MusicRow` / `DailyHeroCard` / `MusicListIndexStrip` 已实现（内置 HomeScreen）
- [x] **P7.18** HomeScreen — 每日推荐卡片 + 心动歌单入口，集成 `TabScreen` + `TitleWidget` + `MusicList`
- [x] **P7.19** GalleryScreen — 画廊/浏览，完整网格布局 + 空状态
- [x] **P7.20** ListScreen — 音乐列表页，完整实现：用户歌单/常用歌单/场景歌单/流派/心情/探索
- [x] **P7.21** SearchScreen — **改用原生 `.searchable(text:)` modifier**，自带搜索栏动画+取消按钮+结果切换
- [x] **P7.22** SongDetailScreen — 歌曲详情页，海报 + 技术信息卡片 + 统计/介绍/歌词标签页 + 用户统计网格 ✅
- [x] **P7.23** ArtistScreen — 艺术家页，完整状态管理 + 播放控制 + 音乐列表 ✅
- [x] **P7.24** AlbumScreen — 专辑页，完整状态管理 + 播放控制 + 音乐列表 ✅
- [x] **P7.25** CustomScreen — 自定义主题/界面配置页
- [x] **P7.26** LibraryViewModel — `allMusic` / `orderBy` / `scanState` 等状态管理，对应 Android `LibraryViewModel.kt`
- [x] **P7.27** SearchViewModel + SongDetailViewModel — 搜索状态 + 歌曲详情+标签+播放历史 ✅

#### P7-E: 播放器模块（对照 Android `ui/player/`）✅

- [x] **P7.28** PlayerScreen — 播放主界面，专辑封面 + 可拖动进度条 + 播放控制（播放/暂停/上首/下首/播放模式/收藏）+ 队列 Sheet + 歌词/音效入口 ✅
- [x] **P7.29** LyricsScreen — 歌词显示页 + 歌词设置面板，支持 `.sheet` + `.presentationDetents` ✅
- [x] **P7.30** TechnicalInfoCard — 技术信息卡片(比特率/采样率/格式)，已在 SongDetailScreen 中实现
- [x] **P7.31** MiniPlayerBar — 全局悬浮迷你播放器（播放/暂停/歌名/艺术家/点击展开），接入 MusicPlayerController 实时状态 ✅
- [x] **P7.32** PlaybackViewModel — 不需要独立 VM，`MusicPlayerController` @Observable 单例直接驱动 UI ✅
- [x] **P7.33** PlaylistQueueViewModel — 基础队列管理由 MusicPlayerController 覆盖，智能列表生成待完善

#### P7-F: 播放列表模块（对照 Android `ui/playlist/`）✅

- [x] **P7.34** PlaylistScreen — 播放列表详情页（参数：name 或 playlistId），歌曲列表 + 排序 + 编辑模式
- [x] **P7.35** PlaylistManageScreen — 用户歌单管理页，创建/重命名/删除/排序/置顶
- [x] **P7.36** PlaylistViewModel — 标签/CRUD/艺术家/专辑浏览/批量操作，对应 Android `PlaylistViewModel.kt` ✅

#### P7-G: 设置模块（对照 Android `ui/settings/`）🔶

- [x] **P7.37** SettingScreen — 设置中心页，导航到子页面（个人资料/备份/音乐库等）✅
- [x] **P7.38** ProfileSettingsScreen — 个人资料设置（用户名/头像），PhotosPicker 头像选择 ✅
- [x] **P7.39** UserScreen — 用户页（Tab 内），每日推荐 + 使用统计 + AI 配置入口
- [x] **P7.40** AIScreen — AI 配置页，Provider 切换 + API Key + 模型选择 + 连接测试 + 批量处理 ✅（UI 完整，后端部分为模拟数据待接入真实 API）
- [x] **P7.41** AudioEffectsScreen — 音效调节页，均衡器 + 低音增强 + 环绕声 + 混响
- [x] **P7.42** BackupSettingsScreen — 备份/还原 UI 完整 ✅（后端 exportBackup/restoreBackup 为模拟实现待替换）
- [x] **P7.43** LibrarySettingsScreen — 音乐库设置 UI 完整 ✅（部分操作如隐藏文件夹为空实现待补全）
- [x] **P7.44** UserUsageDataScreen — 使用数据统计页 UI 完整 ✅（当前为模拟数据，待接入 ViewModel 真实数据）
- [x] **P7.45** SettingsViewModel + RecommendationViewModel + AudioEffectViewModel — 设置/AI推荐/音效 3 个 ViewModel ✅（UserUsageDataViewModel 待独立实现）

#### P7-H: 验证 🔶

- [ ] **P7.46** 音乐库模块验证 — 扫描→列表→搜索→详情→艺术家→专辑，功能对比 Android
- [ ] **P7.47** 播放器模块验证 — 播放→暂停→上下曲→进度→歌词→队列→心动模式
- [ ] **P7.48** 播放列表模块验证 — 创建→编辑→排序→添加歌曲→删除→歌单管理
- [ ] **P7.49** 设置模块验证 — 主题切换→AI 配置→音效→备份还原→使用数据

---

## v6 阶段：iOS 功能补全与双平台对齐

v5.10 是 v5 系列的最后一个版本，标志着跨平台架构搭建完成。v6 阶段将聚焦 iOS 侧不完善部分的补全，以及双平台功能对齐。

### P8: iOS 存根替换（2026-08 复核更新）

- [ ] **P8.1** SecureStorageHelper.ios.kt — 升级为真加密：现实现为 XOR 伪加密 + 明文密钥文件，应改为 CryptoKit AES-GCM + Keychain 存密钥（shared 层代码，方向 A 重写后仍保留，优先级高）
- [x] **P8.2** PinyinSortKey.ios.kt — 已实现内置 PinyinLookupTable 拼音查表（原「返回原字符串」描述过时）
- [x] **P8.3** BackupFileRepositoryImpl.ios.kt — 已实现真实备份文件读写（NSFileManager JSON 落盘，非存根）

### P9: iOS 设置页面后端实现（❄️ 冻结，由方向 A 取代）

> KMP 重写后这些 SwiftUI 页面将被删除，在其后端投入属一次性投入，故冻结；如方向 A 计划变更再重启。

- ~~P9.1 AIScreen — 替换模拟数据，接入真实 API 调用~~
- ~~P9.2 BackupSettingsScreen — 替换模拟 exportBackup/restoreBackup~~
- ~~P9.3 LibrarySettingsScreen — 补全隐藏文件夹等操作的实际实现~~
- ~~P9.4 UserUsageDataScreen — 替换模拟数据，接入真实 ViewModel 数据~~
- ~~P9.5 UserUsageDataViewModel — 独立实现使用数据统计 ViewModel~~

### P10: iOS 验证与清理（部分并入 v7.x）

- [x] **P10.1** 真机验证 — iPhone 13（iOS 26.x，免费个人团队 6MWD22QB9Z）构建/安装/启动通过，
  Koin（shared+shared-ui）初始化正常、无异常（2026-08-23）。锁屏控制 + Live Activity 的
  **交互级核验**需在设备上按下 Home 锁屏实操：锁屏播放控制（暂停/切歌）、灵动岛 Live Activity 状态
- [ ] **P10.2** MusicPlayService.swift 清理 — 已被 PlayerEngine 替代，可删除
- [x] **P10.3** 双平台功能对齐验证 — 由 v7.x 方向 A（KMP 重写 iOS）从架构上消除双实现，自然达成

### 技术债务

- [ ] **T3** Repository 通用逻辑提取到 commonMain 共享基类，减少平台 actual 中的重复实现（SettingsRepositoryImpl 三平台各 ~500 行高度重复）

---

## v7.x 阶段：三大方向任务分解（2026-08 制定）

> 方向论证见 ROADMAP.md「未来发展方向 · v7.x 阶段」。版本编排：**7.1** = A-Phase1 + C-批1；**7.2** = A-Phase2/3 + C-批2；**7.3** = B；**7.4** = 收尾。

### 方向 A：KMP 重写 iOS UI（Compose 取代 SwiftUI）

**Phase 1 — 地基（7.1）**

> ✅ 2026-08-23 完成（模拟器验证通过）。关键决策：Kotlin 2.2.21 → **2.3.21**（navigation3 全系 iOS klib 由 2.3 编译器产出，2.2 无法消费）；iOS 架构定案为**单一聚合框架 `shared-ios`**（shared + shared-ui 两 klib 链接为 sharedIos.framework，规避双静态框架 duplicate symbol 与动态框架 Koin 全局分裂）；**不启用 iosX64**（navigation3-ui 无 ios_x64 构件）；Xcode 26.6 需 iOS 26.5 模拟器运行时（`xcodebuild -downloadPlatform iOS`）。模拟器构建须 `ARCHS=arm64 ONLY_ACTIVE_ARCH=YES`（Apple Silicon）。

- [x] **A1** shared-ui 增加 iOS targets（iosArm64/iosSimulatorArm64）编译通过，CocoaPods 导出接入 ios 工程（`shared-ios` 聚合框架 + `pod 'shared_ios'`；Kotlin 2.3 的 pod 产物在 `build/bin/<target>/podDebugFramework`，Podfile 阶段脚本负责同步到 podspec 的 vendored 路径，并将 shared-ui 的 composeResources 按 `<bundle>/compose-resources/composeResources/<Res包>/` 布局打进 app）
- [x] **A2** spike：navigation3 在 iOS 端可用性验证（设置试点用 NavDisplay + entryProvider + rememberHmpNavBackStack 跑通转场/保存状态；返回手势待 A5 真机单元验证）
- [x] **A3** shared-ui iosMain 桥接层（A4 双桥：`IosPlaybackController` 状态汇聚 + `IosPlaybackCommands` 命令闭包；`IosAlbumArtPixelsLoader` / `IosPlatformServices` / Taptic Engine 触觉 / `StatusBars.ios` / `ScrimDialog.ios`/ 8 个 expect actual + `iosUiModule` + `installKoinIosWithSharedUi`）
- [x] **A4** 桥接包装 Swift 播放引擎（`PlaybackBridge.swift` 闭包注册 + Observation 状态镜像；MusicPlayerController 补齐 addToPlaylist/removeFromPlaylist/moveToTop/playHeartMode/playAt(music)；NowPlayingInfo / RemoteCommand / LiveActivity 保持于 Swift 原生层未动）
- [x] **A5** 试点页面：设置中心 + 设置子页（ProfileSettings/Backup/Library/Lyrics）在 iOS 模拟器跑通（`xcrun simctl launch ... com.hmp.HMP -hmp-pilot`），无未捕获异常、品牌蓝渲染确认；Liquid Glass 观感取舍（Compose 无原生毛玻璃，Haze 近似 vs 关键页保留 SwiftUI）留待 A6-A9 迁移时逐页决策

**遗留（Phase 1 边界外）**
- 模拟器 = iPhone 17 Pro（iOS 26.5）；真机验证（P10.1 锁屏/Live Activity + Liquid Glass 观感）待真机
- 试点已删除的 XcodeGen shared scheme（HMP/HMPNowPlayingExtension .xcscheme）由 xcodegen 自动生成替代，archive 流程需回归确认

**Phase 2/3 — 按模块迁移（7.2）**

> ✅ 2026-08-23 全面替换完成（模拟器验证通过）：
> - 默认入口：`ContentView → IosAppRootKt.createAppRootViewController()`（共享层 Compose AppRoot 全模块运行）
> - **A9 完成**：删除 68 个 SwiftUI 页面/组件/Design/Swift ViewModel（git 记录），保留原生层；
>   `CoverCache` 从 AlbumCover.swift 提取保留（NowPlaying/LiveActivity 封面缓存）
> - **A10 完成**：iOS 壳收敛至 17 个 Swift 文件 = AppDelegate / ContentView 宿主 / 播放引擎簇 /
>   MediaSession（NowPlayingInfo/RemoteCommand/LiveActivity/封面）/ 桥（Playback/PlatformServices）/ 解析桥
> - Koin 4.2.2（与 lifecycle 2.10 对齐，消除 irLinkageError；Android/Desktop 回归全绿）
> - 剩余验证项（无真机/交互注入环境，记录待办）：真机锁屏/Live Activity（P10.1）、
>   逐模块交互级核验（播放/歌词/队列/歌单操作）、Liquid Glass 观感取舍

- [x] **A6** 库模块（Library / Home / Search / SongDetail）— 默认链路已运行（0 异常）
- [x] **A7** 播放器模块（Player / Lyrics / 队列）— 默认链路已运行（0 异常）
- [x] **A8** 播放列表模块 — 默认链路已运行（0 异常）
- [x] **A9** 设置与用户模块 — 已删除对应 SwiftUI 页面与 9 个重复 Swift ViewModel
- [x] **A10** iOS 壳收敛 — 仅保留 AppDelegate / 播放引擎 / LiveActivity / WidgetKit 等原生层（17 个 Swift 文件）

### 方向 B：AI 功能 Agent 化（7.3）

> 方案定稿见 [docs/7_x/agent.md](docs/7_x/agent.md)；落地任务分解（含验收标准、文件落点、挂起参数默认值）见 [docs/7_x/agent-task-book.md](docs/7_x/agent-task-book.md)（阶段制 M0-M7，不绑定版本号）。编号已对齐 B0-B6 体系；B0 无 agent 依赖，可提前还债。

- [x] **B0** AI 管道三端去重 + Room v2 迁移——MusicRepositoryImpl 三份（1034/910/899 行）中 AI 方法上提 commonMain（去重目标 30-40%，扫描/存储/标签写入等平台特强方法留 actual）+ 新建 agent_task/agent_audit_log/agent_message 三表 + MusicLabel 加 source/confidence/created_at/updated_at（标签溯源先行，迁移测试先行）（M0）
- [x] **B1** LlmTransport 流式协议——OpenAiCompatibleAdapter 扩展 tools（function-calling）+ 手动 SSE 解析（ByteReadChannel，避 Ktor 3.2 升级）+ temperature 1.3→0.2-0.4 + FakeLlmTransport（M2）
- [x] **B2** 工具层——ToolSpec DSL（schema 生成+校验）+ 十项工具（包装既有 UseCase）+ ToolRegistry + 工具结果回填语义（M3）
- [x] **B3** 引擎循环——AgentOrchestrator（步数预算 8）+ Scheduler/PolicyGuard/TrustLedger/ContextBudget/SessionStore/PresenceBus + PlaybackCommandPort（:shared 接口，三端适配器复用 Controller 桥）+ 双层预算（云端频率/额度配额）+ FakePlaybackCommandPort 确定性测试（M4）
- [x] **B4** 对话 UI（纯文字完整体验）——ChatScreen + 五类气泡（text/song/songlist/explain/confirm）+ 确认卡片流 + 锚点系统（三胶囊/轻量浮层/播放页重排/C 键）+ 两级漏斗 + 门面二期 + 搜索框条带（M1 + M5）
- [ ] **B5** 电台与事件接线——AI 电台三轮协作 / 跳过感知重排 / DJ 衔接预生成 / 存在感呈现（PresenceBus→徽标+侧条+问候轮换）+ 审计日志页（含撤销边界）（M6）
- [ ] **B6** 报告角色与语音档——听歌报告 / 遗忘唤醒 + 伙伴设置页六分区（AI 页演进）+ RealtimeVoiceTransport 实时语音（**独立 gate**：端点不可用即整体延期，v1 完整性不依赖语音）（M7）

### 方向 C：播放功能增强补齐

**第一批（7.1，纯引擎层，与方向 A 并行）**
- [ ] **C1** 播放速度：PlaybackController 接口扩展 + 三端实现（Media3 setPlaybackSpeed / FFmpeg atempo / AVPlayer rate）+ shared-ui UI
- [ ] **C2** 音频格式白名单统一到 commonMain 常量；iOS 补 opus（现 7 种 vs Desktop 9 种不一致）
- [ ] **C3** Gapless 无缝播放：Android（Media3 原生）+ Desktop（FFmpeg 预加载下一曲）+ iOS（AVPlayer 衔接）

**第二批（7.2）**
- [ ] **C4** Desktop 音效系统：FFmpeg avfilter（EQ / 低音 / 环绕），接 shared-ui AudioEffectViewModel 共享 UI
- [ ] **C5** ReplayGain：标签读取（扫描器）+ 播放增益应用（三端）
- [ ] **C6** 交叉淡入淡出（切歌 fade，三端引擎）
- [ ] **C7** Desktop 放行 DSD(DSF/DFF) / APE / WV（FFmpeg 原生可解，白名单 + 标签解析验证）

**待评估**
- [ ] **C8** bit-perfect 输出（Windows WASAPI 独占等）
- [ ] **C9** 桌面小组件（Android Glance / iOS WidgetKit）与手势操作

---

© 2026 Hearable Music Player | Developed by WLYB
