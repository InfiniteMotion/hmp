# Hearable Music Player 开发文档

本文档详细记录了 Hearable Music Player 的技术架构、实现细节、开发流程和关键决策，旨在帮助开发者快速理解项目并参与开发。项目文档索引与职责说明见 [docs/README.md](docs/README.md)。

## 🏗️ 技术架构

### 整体架构

项目采用MVVM（Model-View-ViewModel）架构模式，结合Kotlin Multiplatform (KMP) 实现跨平台开发。UI 层使用 Compose Multiplatform，Android / Desktop / iOS 三端共用 `shared-ui` 中的一套 Compose UI，平台差异收口到各自的桥接层；业务逻辑、数据模型与 Repository 接口沉淀在 `shared` 模块，实现了清晰的职责分离和可维护性。

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  UI Layer       │     │ ViewModel Layer │     │   Domain Layer  │
│  (Compose       │────▶│  (Koin          │────▶│  (Use Cases,    │
│   Multiplatform │     │   ViewModel)    │     │   Repository)   │
│   三端共用)      │     └─────────────────┘     └─────────────────┘
└─────────────────┘                                   │
        │                                             ▼
        │               ┌─────────────────┐     ┌─────────────────┐
        │               │  Service Layer  │     │  Network Layer  │
        │               │  (Media3,       │     │  (Ktor          │
        │               │   FFmpeg/JNA,   │     │   Client)       │
        │               │   AVFoundation) │     └─────────────────┘
        │               └─────────────────┘              │
        ▼                                                ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Data Layer     │     │  Shared Module  │     │  Platform       │
│  (Room,         │     │  (KMP,          │     │  Specific      │
│   DataStore)    │     │   Koin)         │     │  Implementations│
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### 模块划分

项目采用模块化架构，将不同功能划分为独立的模块，降低耦合度并提高可维护性。目前已经完成了模块化重构，划分为以下核心模块：

#### 核心模块

- **shared**: 跨平台共享模块，包含业务逻辑、数据模型、Repository 接口和 Koin 依赖注入配置
- **shared-ui**: 跨平台共享 UI 模块，包含 Compose 页面与 ViewModel；**Android / Desktop / iOS 三端共用 commonMain 一套 UI**（v7.0 完成 Android/Desktop，v7.1 完成 iOS），平台差异收口到 androidMain / desktopMain / iosMain 桥接层
- **shared-ios**: iOS 聚合框架，把 `shared` + `shared-ui` 链接为单一 `sharedIos.framework` 接入 CocoaPods
- **android/app**: Android应用入口模块，包含MainActivity和Application类
- **android/core-player**: Android播放核心模块，包含Media3服务和播放控制逻辑
- **desktop/app**: Desktop应用入口模块，包含窗口管理、系统托盘和应用生命周期
- **desktop/core-player**: Desktop播放核心模块，包含FFmpeg音频引擎和播放控制逻辑
- **ios**: iOS 应用模块，原生壳（原生层：AppDelegate / 播放引擎 / MediaSession / Live Activity / 桥，共 22 个 Swift 文件 = HMP 18 + HMPNowPlaying 4）+ 共享 Compose UI
- **storybook**: 组件展示与文档模块 (Kotlin/Wasm) —— **已移出构建**（`380f225` 起不在 `settings.gradle.kts` 中），源码保留。**2026-09-30 决议（`docs/7_3/plan.md` §九 决策 6）：原地保留，定位为「设计沙盒」** —— 不恢复 `include`、不在任何构建图内、不保证可编译、无 CI 覆盖

### 模块间依赖关系

```
:shared-ui ──▶ :shared
:shared-ui ──▶ :android/core-player   (androidMain 桥接)
:shared-ui ──▶ :desktop/core-player   (desktopMain 桥接)
:android/app ──▶ :shared + :shared-ui
:desktop/app ──▶ :shared + :shared-ui + :desktop/core-player
:android/core-player ──▶ :shared
:desktop/core-player ──▶ :shared
:ios ──▶ :shared-ios (via CocoaPods；聚合 :shared + :shared-ui)
```

### 模块化进展

- ✅ 已完成模块划分和依赖配置
- ✅ 已创建跨平台shared模块，包含核心业务逻辑和数据模型
- ✅ 已将Android-specific代码移至android目录下的模块
- ✅ 已创建Desktop模块，包含Compose Multiplatform页面和组件
- ✅ 已创建iOS模块；v7.1 起 iOS 亦切换到共享层 Compose UI，原 SwiftUI 页面层删除，只保留原生壳
- ✅ 已配置CocoaPods集成（`shared-ios` 聚合框架），实现iOS对 `shared` + `shared-ui` 的依赖
- ✅ 已实现平台特定的Repository实现（Android、Desktop和iOS）
- ✅ 已实现桌面端自研音频引擎（FFmpeg + JNA）
- ✅ 已实现桌面端响应式布局系统（Compact/Expanded模式）

### 关键技术决策

#### 1. 跨平台框架：Kotlin Multiplatform Mobile (KMM)

**选择理由**：KMM允许使用Kotlin编写跨平台代码，在Android和iOS之间共享业务逻辑和数据模型，减少代码重复，提高开发效率。作为个人项目，我希望通过使用KMM来学习跨平台开发的最佳实践。

**实现细节**：
- 共享模块使用Kotlin Multiplatform插件
- 实现平台特定的Repository实现
- 使用Koin进行跨平台依赖注入
- 通过CocoaPods将shared模块集成到iOS项目

#### 2. 依赖注入：Koin (跨平台)

**选择理由**：Koin 是一个轻量级的依赖注入框架，完美支持 Kotlin Multiplatform。相比 Hilt，Koin 可以在 Android 和 iOS 之间共享依赖注入配置，减少了平台特定的代码。

**实现细节**：
- 全平台使用 Koin：从 Android 的 Hilt 迁移至 Koin
- 共享模块：通过 `koinViewModel()` 获取 ViewModel，使用 `single`/`factory` 创建依赖
- 平台特定实现通过 `expect/actual` 机制注入
- iOS 端通过 `AppDelegate` 调用 `IosUiKoinModuleKt.installKoinIosWithSharedUi()` 初始化（内部完成 `sharedModule` + `iosPlatformModule` + UI 模块的装配）

#### 3. 状态管理：Kotlin Flow/StateFlow

**选择理由**：Kotlin Flow和StateFlow提供了一种简洁的方式来管理UI状态，并且支持异步操作和线程切换。作为个人项目，我希望通过使用Flow来学习响应式编程的思想。

**实现细节**：
- ViewModel暴露StateFlow给UI层
- UI层使用collectAsState()订阅状态变化
- 所有状态更新都通过Flow进行，避免竞态条件

#### 5. 媒体播放：AndroidX Media3 (Android) + AVFoundation (iOS)

**选择理由**：AndroidX Media3是Google推出的新一代媒体播放框架，提供了统一的API，支持多种媒体格式和播放场景。作为个人项目，我希望通过使用Media3来学习现代Android媒体播放的最佳实践。

**实现细节**：
- Android端：MusicPlayService管理ExoPlayer和MediaSession，实现音频焦点管理和通知控制
- iOS端：使用AVFoundation框架实现音频播放，支持后台播放和远程控制
- 平台特定实现通过共享接口统一管理

#### 6. 数据存储：Room KMP + DataStore KMP (跨平台)

**选择理由**：Room 2.7+ 支持 Kotlin Multiplatform，可以在 Android 和 iOS 之间共享数据库代码。配合 SQLite Bundled 驱动，实现了真正的跨平台数据存储。DataStore KMP 提供了跨平台的偏好设置存储方案。

**实现细节**：
- Room KMP 配置跨平台数据库，使用 `@ConstructedBy` 和 `expect/actual` 模式
- KSP 代码生成器为各平台生成数据库实现
- SQLite Bundled 驱动提供跨平台 SQLite 支持
- DataStore KMP 存储主题、音量等偏好设置
- Repository 层通过 `expect/actual` 实现平台特定的数据访问

**配置要点**：
- 参考 [Room KMP 配置文档](docs/room-kmp-setup.md)
- 关键：不要在 `commonMainMetadata` 上运行 KSP
- 使用 `BundledSQLiteDriver` 作为跨平台驱动
- iOS 使用 `NSDocumentDirectory` 存储数据库文件

#### 7. 网络请求：Ktor Client (跨平台)

**选择理由**：Ktor Client 是 Kotlin 官方推出的跨平台网络请求框架，支持 Android、iOS 等多个平台。通过使用不同的引擎（Android 使用 OkHttp，iOS 使用 Darwin），实现了真正的跨平台网络请求代码共享。

**实现细节**：
- 共享模块：使用 Ktor Client 定义 API 接口和请求逻辑
- Android 端：使用 OkHttp 引擎，支持连接池、拦截器等高级特性
- iOS 端：使用 Darwin 引擎，基于原生 NSURLSession
- 统一配置：超时、重试策略、日志记录在 commonMain 中定义
- 实现失败重试和指数退避策略

#### 8. AI服务集成：多服务商支持

**选择理由**：为了提供更灵活的 AI 推荐服务，项目支持多个 AI 服务商（DeepSeek、OpenAI、Claude、通义千问、文心一言）。用户可以根据自己的需求选择不同的服务商。

**实现细节**：
- 统一的 API 适配器层，封装不同服务商的 API 调用
- API 密钥加密存储，保障安全性
- 支持 API 连接测试功能
- 用户可在配置界面自由切换服务商

#### 9. 导航系统：Navigation 3 + 自研 Router（三端共用）

**选择理由**：Navigation 3 提供了类型安全的导航方式，支持编译时路由检查和参数验证。为同时满足移动端单栏栈式导航与桌面端多面板响应式导航，项目在 Navigation 3 之上叠了一层自研 `Router`（含深度链接支持），并在 `shared-ui` 的 commonMain 中实现，三端共用。

**实现细节**：
- 使用 `@Serializable` 注解定义路由（`common/navigation/Routes.kt` 的 `object Routes` 下 30 个 `NavKey`），集中式路由管理，支持类型安全的参数传递
- `Router` / `interface RouteNavigator`（`rememberRouter()` 取得）封装 `navigateTo` / `navigateReplace` / `popBackStack` / `clearBackStack` 等，底层是 `rememberHmpNavBackStack()` 建的 nav3 回退栈 + `NavigationGraph` 的 `entry<>` 映射
- ⚠️ **新增 `NavKey` 必须同时做两件事**：在 `HmpNavBackStack.kt` 的 `SerializersModule` 注册 serializer，并在 `NavigationGraph.kt` 补 `entry<>`。漏注册**没有编译期报错**，只在该 key 参与保存/恢复时运行时报错（当前有两条路由漏注册，见 `AGENTS.md` §八 与 TODO **R41**）
- 导航逻辑位于 `shared-ui` commonMain，Android / Desktop / iOS 三端复用同一套路由定义
- 平台差异（返回手势、窗口尺寸判定）收口到各自平台的桥接层

#### 10. 视觉效果：毛玻璃效果

**选择理由**：毛玻璃效果（Haze）可以提升UI的视觉层次感和现代感，与动态背景结合使用效果更佳。

**实现细节**：
- 使用 Haze 库实现毛玻璃效果
- 支持动态背景风格选择
- 可配置的模糊强度和颜色叠加
- 应用于弹窗、底部栏等组件

## 📦 项目结构

```
Hearable Music Player/
├── shared/                           # 跨平台共享模块
│   ├── src/
│   │   ├── commonMain/               # 共享代码
│   │   │   └── kotlin/com/hmp/
│   │   │       ├── data/             # 数据层
│   │   │       │   ├── database/    # Room 数据库（AppDatabase v9）与 19 个 DAO
│   │   │       │   ├── mapper/      # 数据映射器
│   │   │       │   ├── network/     # Ktor 网络层（**只服务用户自填的 AI API**）
│   │   │       │   ├── repository/  # 跨平台仓库基类（MusicRepositoryBase 等）
│   │   │       │   └── util/        # DeviceMusicScanner / MusicTagParser / SecureStorageHelper / DataStoreFactory 等 expect 声明
│   │   │       ├── domain/           # 领域层（按子域分包，不是 model/ + usecase/ 两包）
│   │   │       │   ├── agent/       # **AI Agent 体系：本项目最大的包**（runtime/ tool/ profile/ card/ policy/ port/ infra/ config/ persona/）
│   │   │       │   ├── music/       # 音乐域模型 + UseCase
│   │   │       │   ├── setting/     # 设置域 + UseCase（含 LyricsSettingsUseCase）
│   │   │       │   ├── playlist/    # 歌单域 + UseCase
│   │   │       │   ├── backup/      # 备份恢复域 + UseCase
│   │   │       │   ├── lyrics/      # 歌词（LrcParser 等）
│   │   │       │   ├── enum/        # AiProviderType / AlgorithmType 等枚举
│   │   │       │   └── config/      # 全局 Agent 配置
│   │   │       ├── platform/         # 平台抽象（Synchronized / Volatile 的 expect）
│   │   │       ├── log/              # HmpLog / LogTag 日志门面
│   │   │       ├── di/               # sharedModule（Koin）
│   │   │       └── KermitInit.kt / PlatformLog.kt
│   │   ├── androidMain/              # Android 侧 actual（data/di/AndroidModules、DatabaseBuilder.android、*Impl.android 等）
│   │   ├── desktopMain/              # Desktop 侧 actual（di/DesktopModules、DatabaseBuilder.desktop、*Impl.desktop 等）
│   │   ├── iosMain/                  # iOS 侧 actual（di/IosModules + KoinHelper、DatabaseBuilder.ios、*Impl.ios 等）
│   │   ├── commonTest/               # 跨平台单测（kotlin.test + Fake，见「测试流程」）
│   │   └── desktopTest/              # 唯一含 DB / Repository 集成测试的源集
│   ├── build.gradle.kts              # 共享模块构建配置
│   └── shared.podspec                # ⚠️ 遗留物：`:shared` 的 cocoapods 块生成它（baseName `shared`），但 **Podfile 集成的是 `shared-ios/shared_ios.podspec`**（baseName `sharedIos`，聚合 shared + shared-ui）。iOS 接入只看后者
├── shared-ui/                        # 三端共享 UI 模块（Compose Multiplatform）
│   ├── src/
│   │   ├── commonMain/kotlin/com/hearablemusic/player/ui/
│   │   │   ├── common/               # 通用组件、主题、导航
│   │   │   ├── library/              # 音乐库页面
│   │   │   ├── player/               # 播放页面
│   │   │   ├── playlist/             # 播放列表页面
│   │   │   ├── settings/             # 设置页面
│   │   │   └── platform/             # expect 声明与平台服务接口
│   │   ├── commonMain/composeResources/  # 共享资源
│   │   ├── androidMain/              # Android 侧 actual 实现
│   │   ├── desktopMain/              # Desktop 侧 actual 实现
│   │   └── iosMain/                  # iOS 侧 actual 实现（ObjC bridge）
│   └── build.gradle.kts
├── shared-ios/                       # iOS 聚合 framework（导出 shared + shared-ui）
│   └── build.gradle.kts
├── android/                          # Android平台代码
│   ├── app/                          # Android应用入口
│   │   ├── src/main/
│   │   │   ├── java/com/hearablemusic/player/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── MusicApplication.kt
│   │   │   └── res/                  # 资源文件
│   │   └── build.gradle.kts          # 模块构建配置
│   └── core-player/                  # 播放核心模块（Media3）
│       ├── src/main/java/com/hearablemusic/player/
│       │   └── player/               # 播放控制逻辑
│       └── build.gradle.kts
├── desktop/                           # Desktop平台代码
│   ├── app/                           # Desktop应用入口
│   │   ├── src/desktopMain/kotlin/com/hmp/desktop/
│   │   │   ├── Main.kt               # 应用入口
│   │   │   ├── CustomTitleBar.kt     # 无边框窗口标题栏
│   │   │   ├── SystemTrayManager.kt  # 系统托盘管理
│   │   │   └── WindowHelper.kt       # 窗口工具
│   │   └── build.gradle.kts
│   └── core-player/                   # 桌面播放核心模块（FFmpeg）
│       ├── src/desktopMain/kotlin/com/hmp/desktop/player/
│       │   ├── FFmpegAudioEngine.kt   # FFmpeg音频引擎
│       │   └── DesktopMusicController.kt # 播放控制器
│       └── build.gradle.kts
├── ios/                              # iOS平台代码
│   ├── HMP/                          # iOS应用
│   │   ├── HMP/                      # 应用壳（原生文件已大幅收敛，见模块化进展）
│   │   │   ├── HMPApp.swift          # iOS应用入口（挂载共享 Compose UI）
│   │   │   └── Platform/            # 平台桥接（播放器、权限、Live Activity 等）
│   │   ├── HMPNowPlaying/           # Live Activity 扩展
│   │   └── HMP.xcodeproj            # Xcode项目文件
│   ├── Podfile                       # CocoaPods配置
│   └── HMP.xcworkspace              # Xcode工作空间
├── docs/                             # 项目文档
│   ├── 7_3/                          # 当前版本线（v7.3.0）的工作目录
│   ├── spec/                         # 长期规范：hmp-release / hmp-log / hmp-design
│   ├── archive/                      # 已收口版本线，两层：<家族>/<版本>
│   │   ├── 5_x/{5_9,5_10}/  6_x/{6_1,6_12}/
│   │   └── 7_x/{7_1,7_2}/            # 7_1 共享 UI 提取线；7_2 Agent 线（design/ + taskbook/）
│   └── README.md                     # 文档索引（含目录与命名约定、搬家清单）
├── gradle/
│   └── wrapper/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## 🚀 开发流程

### 开发环境

#### Android
- Android Studio（Ladybug 2024.2.1 为早期记录，当前 AGP 9.1.1 需更新版本的 Studio）
- Kotlin 2.3.21
- Gradle 9.3.1（wrapper 实测：`gradle/wrapper/gradle-wrapper.properties`）
- Android SDK compileSdk **37** / targetSdk **37** / minSdk 33
- AGP (Android Gradle Plugin) 9.1.1

> 以上版本号的真源是 `gradle/libs.versions.toml` 与 `android/app/build.gradle.kts`，本节只是快照；两者冲突时以那两个文件为准（见 `AGENTS.md` §九）。

#### iOS
- Xcode 26.x（iOS 26.5 模拟器运行时；缺失时先 `xcodebuild -downloadPlatform iOS`）
- Swift 6 工具链（随 Xcode 26 分发）
- CocoaPods 1.16.0 或更高版本
- macOS 14.0 或更高版本；应用部署目标 26.3，`shared` 的 CocoaPods 部署目标 16.0

#### Desktop
- JDK 21 或更高版本（Desktop 的 jpackage 要求 Gradle Daemon 运行在 JDK 21，配置见 `gradle.properties` 注释）
- Gradle 9.3.1
- FFmpeg（桌面端构建时按「OS + 架构」从本仓库 Release `ffmpeg-binaries` 下载，SHA256 校验不符即失败；详见 docs/spec/hmp-release.md）

### 构建项目

#### Android
```bash
# 克隆项目
git clone https://github.com/InfiniteMotion/HMP.git

# 进入项目目录
cd HMP

# 构建项目
./gradlew build

# 运行应用
./gradlew installDebug
```

#### iOS
```bash
# 克隆项目（如果尚未克隆）
git clone https://github.com/InfiniteMotion/HMP.git

# 进入项目目录
cd HMP

# 生成聚合框架（shared + shared-ui → 单一 sharedIos.framework）+ podspec
./gradlew :shared-ios:generateDummyFramework
./gradlew :shared-ios:podspec

# 安装CocoaPods依赖
cd ios && pod install

# 使用Xcode打开工作空间
open HMP.xcworkspace

# 在Xcode中构建并运行应用
```

#### Desktop
```bash
# 运行桌面应用
./gradlew :desktop:app:run

# 构建 macOS DMG（需 macOS）
./gradlew :desktop:app:packageDistributionForCurrentOS

# 构建 Windows MSI（需 Windows）
./gradlew :desktop:app:packageDistributionForCurrentOS

# 构建 Linux DEB（需 Linux）
./gradlew :desktop:app:packageDistributionForCurrentOS
```

### 开发进展

#### 已完成

- ✅ 模块化架构设计与实现
- ✅ 核心音乐播放功能（Media3集成）
- ✅ 本地音乐扫描与Room数据库存储
- ✅ 多 AI 服务商集成与管理
- ✅ Jetpack Compose UI界面搭建
- ✅ Koin 依赖注入配置（已从 Hilt 迁移）
- ✅ 基本的播放控制功能
- ✅ 触觉反馈增强用户体验
- ✅ 动态主题设置与切换
- ✅ 音频效果调节功能
- ✅ 按艺术家分类浏览
- ✅ 自定义主题设置
- ✅ 优化播放页面UI及交互体验
- ✅ 更新状态沉浸并优化用户播放界面
- ✅ 统一调整UI组件颜色适配主题色彩体系
- ✅ 修复播放进度调整失败的漏洞
- ✅ 引入TabScreen模板
- ✅ 实现每日推荐刷新策略系统
- ✅ API 密钥加密存储机制
- ✅ Navigation 3 迁移与类型安全导航
- ✅ Gradle 9.0 升级
- ✅ 代码混淆与包体积优化
- ✅ ViewModel 职责拆分与代码组织优化
- ✅ 专辑页面功能
- ✅ 音乐文件分享功能
- ✅ 毛玻璃视觉效果
- ✅ Kotlin Multiplatform Mobile (KMM)集成
- ✅ iOS平台支持
- ✅ CocoaPods配置与集成
- ✅ 平台特定Repository实现
- ✅ Room KMP 跨平台数据库配置
- ✅ Ktor Client 跨平台网络请求
- ✅ 全平台 Koin 依赖注入迁移
- ✅ CI/CD 自动发布（GitHub Actions Release 工作流）
- ✅ 桌面端平台支持（Compose Multiplatform + FFmpeg 音频引擎）
- ✅ 桌面端响应式布局（Compact/Expanded 模式、多面板导航）
- ✅ 桌面端三平台打包（macOS DMG / Windows MSI / Linux DEB）

#### 进行中

- 🔄 单元测试覆盖
- 🔄 性能优化与内存泄漏修复

#### 待完成

- 📝 完善文档
- 📝 代码注释与格式化
- 📝 错误处理与异常捕获

### 代码风格

项目遵循 Kotlin 官方代码风格指南，但**目前没有任何机械化风格闸门**：仓库里**不存在** `.editorconfig`、`ktlint.gradle`，也没有 detekt / spotless，`.git/hooks` 下无 `core.hooksPath` 配置。全仓 `*.kts/toml/yml/properties` 里 grep `ktlint|detekt|spotless` 零命中。

方案与落地步骤写在 [docs/ktlint-integration.md](docs/ktlint-integration.md)（**暂缓**，等 agent 分支线合并后启动；该文档的 CI 前提已失效，见 TODO **R50**）。在那之前，新代码靠人工保持一致。

### 测试流程

项目采用分层测试策略，确保代码质量和功能正确性。

#### 单元测试

- **测试框架是 `kotlin.test`**（`commonTest` 里 316 处引用，`org.junit` 零命中）；Android/Desktop 侧另用 MockK 1.13.13 与 Robolectric 4.14.1
- 测试覆盖 Repository、Use Cases、ViewModel 与 Agent 运行时
- ⚠️ **不要用裸 `./gradlew test`**：本项目的口径是自定义聚合任务，且本机必须走低内存包装脚本 —— `./gradlew-lowmem.bat testAll`（或按改动范围选 `testCore` / `testUi`，见「日常开发选任务」）
- ⚠️ 分布不均：`:shared` 有 `commonTest`（94 文件）+ `desktopTest`（14 文件，**唯一的 DB / Repository 集成测试**），但**没有 `androidHostTest`、没有 `iosTest`** → `androidMain` / `iosMain` 的实现零测试覆盖（TODO **R47**）
- 报告落在被依赖的底层任务目录：`<模块>/build/reports/tests/<任务名>/index.html`

#### 仪器测试

- 使用 AndroidX Test，但 `android/app/src/androidTest` 目前只有 `ExampleInstrumentedTest` 占位模板，**没有真实用例**
- 运行命令：`./gradlew connectedAndroidTest`（需连设备/模拟器）
- 真机核验项（iOS 锁屏 / Live Activity / F11 后台存活 / 三端首启引导）记在 TODO **R29**，属发版前必做

### 版本控制

项目使用 Git 进行版本控制，采用**简化的 feature → release → master 流**，**不是 Git Flow**。

**分支策略**（与版本规范一致，详见 [docs/spec/hmp-release.md](docs/spec/hmp-release.md) 分支与发版）：
- `master`: 已发布版本，**保护分支**；MINOR/MAJOR 通过从 `release/X.Y.Z` 合并更新，PATCH 可在 master 上直接改并打 tag
- `feature/<线>`: **长期开发线，一条线一个分支**（实例 `feature/agent-build` / `feature/site-sync` / `feature/music-tag-edit`）。日常开发直接在其上进行，按阶段族一族一笔提交，不为小改动另开分支
- `fix/*`: 小修（修 bug、改配置、改 commit message）
- `release/X.Y.Z`: **发版集成分支，只在发布窗口出现** —— 从 `master` 拉出、把开发线合进来、PR 回 `master`；**合并后远程删除**

> ⚠️ **`release` 分支名必须三段式**（`release/7.2.2`，不是 `release/7.2`）：`pr-check.yml:72-81` 把 `${HEAD_REF#release/}` 与 `sync-release.py version` 做**全等比较**，写成 `release/7.3` 会被硬拦。
>
> ⚠️ **反过来，开发线不要取名成 `release/*`**：version job 的判据是 `startsWith(github.head_ref, 'release/')`，名字以 `release/` 开头就会跑 `checkVersion`，而开发线不 bump 版本号 → 必红。

> ⚠️ **没有任何长期存活的 `develop-*` 分支**（`git branch -a` 实测零命中）。历史文档里的 `develop-android` / `develop-ios` / `develop-desktop` / `develop-shared` 已废弃，别再从它们拉分支或往它们合并。

### 构建与发布

#### 构建类型

- `debug`: 调试版本，包含调试信息
- `release`: 发布版本，经过混淆和优化

#### 版本号管理

**真源是仓库根的 [`release.toml`](release.toml)**（只描述当前版本：`version` / `date` / 对外文案 `[[section]]` / 产物清单 `[[artifact]]`）。

```toml
version = "7.2.2"          # versionCode 不写，由 MAJOR*10000 + MINOR*1000 + PATCH 派生
date = "2026-09-28"
```

`gradle.properties` 的 `hmp.versionName`/`versionCode`、iOS 三处（`project.yml` / `Info.plist` / `project.pbxproj`）、`site/js/config.js`、`site/index.html` 的 JSON-LD、`shared-ios/.../Anchor.kt`、ROADMAP 与站点 changelog 条目 —— **全部是 `scripts/sync-release.py` 写出的派生产物，禁止手改**。改完版本跑 `./gradlew syncVersion`。

各模块仍用 `project.findProperty()` 取版本，只是取值来源变了。发版工序与判据见 [skills/release-prep/SKILL.md](skills/release-prep/SKILL.md)。

> 这条规则是 v7.2.2 才落地的（TODO **R34/R35 已结案**）。此前版本声明分散在 9 处由人抄写，正是那批漂移的来源；旧文档里"版本号集中在 `gradle.properties`、手改它"的说法已全部作废。

#### 发布流程

版本号与发布步骤详见 **[docs/spec/hmp-release.md](docs/spec/hmp-release.md)**，摘要如下：

1. **确定版本类型**：按变更内容决定升级 MAJOR / MINOR / PATCH，得到新版本号
2. **切发版分支**：从 `master` 拉出**三段式**的 `release/X.Y.Z`，把对应开发线 `feature/<line>` 合进来
3. **只改 `release.toml`**：换 `version`（`date` 可留空，发布当天补），然后 `./gradlew syncVersion` —— 派生点由脚本写，**不要手改 `gradle.properties`、不要手写 ROADMAP 条目**
4. **本机预检（唯一守门人，CI 不跑单测）**：`./gradlew-lowmem.bat preflight`（= `checkVersion` + `checkReleaseConsistency` + `testAll`）
5. **本机构建产物**：`./gradlew release`（输出到 `releases/`，含 Android + Desktop；macOS 上额外含 iOS）
   - `./gradlew releaseAndroid` — 仅 Android（APK + AAB）
   - `./gradlew releaseDesktop` — 仅 Desktop（**DMG / MSI / DEB**）
   - `./gradlew releaseIos` — 仅 iOS（需 macOS）
   - `./gradlew copyAndroidDebug` / `copyDesktopJar` — 辅助：Debug APK / Uber JAR
6. **开 PR 跑 Pre-release Check**，合并后 `release.yml` 自动构建并发布 GitHub Release

> ⚠️ **Linux 只发 DEB，没有 AppImage**：`TargetFormat.AppImage` 是 jpackage 的 app-image 解包目录，产不出 `.AppImage` 文件，该格式已移除。下载页上指向 AppImage 的按钮也已在 v7.2.2 一并撤掉（曾点开必然 404）。
>
> 注：`releaseStorybook` 已移除 —— `:storybook` 模块自 `380f225` 起移出构建（见 `settings.gradle.kts`）。
> 上述封装任务仅便于本地使用；CI 不使用它们，而是直接调用各模块底层任务并自行归集产物。

#### 自定义 Gradle 任务

项目自行注册的任务共 **27 个**，分布在 3 个构建脚本（根 `build.gradle.kts` 24 + `desktop/app/build.gradle.kts` 2 + `shared/build.gradle.kts` 1）。`./gradlew tasks --group release` 只显示 group=release 的那 **10** 个：

| 类别 | 任务 |
|---|---|
| **测试**（`verification`，7） | `testAll`、`testCore`、`testQuick`、`testUi`、`testUiDesktop`、`testDesktop`、`testAndroid` |
| **编译**（`build`，5） | `compileAll`、`compileCore`、`compileUi`、`compileDesktop`、`compileAndroid` |
| **发布**（`release`，10） | `release`、`releaseAndroid`、`copyAndroidDebug`、`releaseIos`、`releaseDesktop`、`copyDesktopJar`、`checkVersion`、`preflight`、**`syncVersion`**、**`checkReleaseConsistency`** |
| **清理**（`build`，2） | `cleanReleases`、`cleanOrphans` |
| **FFmpeg**（`desktop/app`，2） | `downloadFFmpeg`、`injectFFmpeg`（`injectFFmpegForDev` 已于 2026-09 移除） |
| **iOS 图标**（`shared`，1） | `copyIconsToIos`（空转） |

> 注：`android:app` / `android:core-player` **不暴露 `compile*` 任务**（AGP 内置 Kotlin），故 `compileAndroid` 用 `assembleDebug`。
> `cleanOrphans` **只打印清单、不自动删除** —— 删除属破坏性操作，需人工确认。
> `run` 不再依赖 `injectFFmpeg`：它通过 **`-Dhmp.ffmpeg.path` 直接指向 `build/ffmpeg/`**（`desktop/app/build.gradle.kts:312`），开发时二进制就位即可。
> **`shared-ui` 的测试在 `androidHostTest`（6 文件）与 `commonTest`（3 文件）两个源集**：`desktopTest` 的**目录根本不存在**，`build.gradle.kts` 却仍硬 `dependsOn(":shared-ui:desktopTest")` → 那一档 `NO-SOURCE` 空过。故 `testUi` 同时挂两个源集；无 Android SDK 时用 `testUiDesktop`（但它只覆盖 desktop 侧）。详见 `AGENTS.md` §八 第 1 条与 TODO **R52**。
> ⚠️ `maybeDepends` 在错误的 `HMP_BUILD_TARGET` 下会**静默丢依赖**：`export HMP_BUILD_TARGET=desktop` 后跑 `./gradlew testAndroid` 必绿且零工作（TODO **R52**）。

#### 低内存构建

本机 Android Studio 常驻占内存，默认 `-Xmx4096m` + `parallel=true` 会让 Gradle/Kotlin daemon 被 OS 静默杀死（日志停在 "Reusing configuration cache."，报 `daemon disappeared`，无 hs_err）。

**用包装脚本代替 `gradlew`**：

```bash
./gradlew-lowmem.bat testCore   # Windows
./gradlew-lowmem testCore       # macOS / Linux / Git Bash
```

> `org.gradle.jvmargs` / `parallel` / `workers.max` 是 Gradle **启动期属性**，无法在 `build.gradle.kts` 里条件化覆盖 —— 只能用包装脚本在命令行层面覆盖。这是引入 `gradlew-lowmem` 的原因。
>
> **实测关键参数**（2026-09-15）：daemon 堆必须压到 **`1024m`**，Kotlin 编译器走 **`in-process`**。
> `1536m`/`2048m` 能编译但会在 fork 测试 JVM 时被 OS 杀掉；已固化进包装脚本。

#### 日常开发选任务

| 改动范围 | 建议命令 |
|---|---|
| `shared` 的 Domain / Agent | `./gradlew-lowmem.bat testCore` |
| `shared-ui` 的 UI | `./gradlew-lowmem.bat testUi` |
| `shared-ui` 的 UI（无 Android SDK） | `./gradlew-lowmem.bat testUiDesktop` |
| 改了公共 API | `./gradlew-lowmem.bat compileAll`（快速定位下游编译破坏） |
| 提交前 | `./gradlew-lowmem.bat testAll` |
| 发版前 | `./gradlew-lowmem.bat preflight`（CI 不跑单测，这是唯一守门人） |

其余数百个任务（`assemble*` / `bundle*` / `link*` / `compile*` / `package*` 等）均由 Gradle 与各插件自动生成，非本项目编写。

#### CI/CD 工作流

项目有两个 GitHub Actions 工作流，职责分工：**pr-check 答「能不能合」，release 答「合了之后怎么发」**。

**① `.github/workflows/pr-check.yml` —— 合入前预检**

`release/*` 分支的 PR 打开/更新时自动跑（draft 跳过），不产发布物、不写仓库。共 **4 个 job**：

- **Version & declarations**（`version`）：调 `checkVersion` + `checkReleaseConsistency`，即发版时那一对 Gradle 任务的**同一个实现**，不在 CI 里重写一套规则；另把分支名后缀（`release/X.Y.Z`）与 **`python scripts/sync-release.py version` 的返回值**比对 —— 真源是 `release.toml`，**不是**派生的 `gradle.properties`（`pr-check.yml:74`）
- **Release info**（`release-info`，**所有非 draft PR 都跑**）：`sync --check` 核对派生点 + 预渲染将要公开的 Notes 正文
- **FFmpeg binaries**（`ffmpeg-assets`）：逐个下载 `ffmpeg-binaries` Release 的二进制，校验 SHA256，并解析 Mach-O / ELF / PE 头部确认真实 CPU 架构与 map key 匹配（脚本见 `.github/scripts/check-ffmpeg-assets.py`）
- **Verdict**（`preflight`）：把以上结论汇成表格写进 job summary，任一失败则 PR 检查不通过

> ⚠️ **两个 workflow 都不跑单元测试**（2026-09-24 起，`testAll` 在 runner 上静默挂死、成因未结案，TODO **R31**）。本机 `preflight` 是唯一把关。
>
> feature PR **不会**触发 `version` job：`checkVersion` 要求 `versionCode` 相对上一 tag 严格递增，而普通功能 PR 不 bump 版本号，跑必红。这是刻意的分区。
>
> 本地复现：`./gradlew checkVersion checkReleaseConsistency` + `python3 .github/scripts/check-ffmpeg-assets.py`

**② `.github/workflows/release.yml` —— 正式发布**

- **触发条件**：`release/*` 分支的 PR **合并**到 `master` 时自动触发（`closed` + `merged == true`）；也可手动 `workflow_dispatch`
- **dry_run**：手动运行时勾选，会在**不发布**的前提下跑完四端构建、产物齐全断言与 Notes 渲染，并把最终正文打进日志 —— 只有 `Create tag and GitHub Release` 这一步被 gate 住。用于验证打包工具链与 Notes 文案是否真的可用（PR 阶段对 `release.yml` 零反馈，只认这条路）
- **validate job**：跑 `checkVersion` + `checkReleaseConsistency`（带 `timeout-minutes: 10`）
- **build-android / build-desktop-macos / build-desktop-windows / build-desktop-linux job**：并行构建桌面三平台安装包与 Android 产物。Android job 会解码 `secrets.KEYSTORE_BASE64` 成签名库；Windows 需 choco 装 WiX；Linux 只产 DEB 并用 `dpkg-deb -c` 断言 FFmpeg 在包里。**CI 没有任何 iOS job** —— iOS 只能本机 `releaseIos`（TODO **R44**）
- **release job**：用 `sync-release.py collect` 汇总产物并按 `release.toml` 的 `[[artifact]]` 改名，硬断言必需文件齐全后生成 `SHA256SUMS.txt`；**Release Notes 正文由 `sync-release.py notes` 从 `release.toml` 渲染**（commit 分类只作附录），最后打 tag 并创建 GitHub Release
  > 旧口径"正文取 ROADMAP 本次版本条目"已作废：那套 awk 抽取没有终止边界，v7.2.1 曾把 172 行内部章节灌进公开说明（发布后人工删）。现 ROADMAP **不再被程序解析**（TODO **R35 已结案**）。
- **deploy-site job**：将手工维护的产品站点 `site/` 部署到 GitHub Pages（非 Storybook）；只核对一致性，不现场改写内容，`dry_run` 下跳过

## 🎯 关键实现细节

### 音乐扫描与解析

**流程**：
1. 申请存储权限
2. 扫描设备中的音乐文件（各端走各自平台 API）
3. 解析标签（Android/Desktop 用 JAudioTagger；iOS 经 Swift 桥用 AVAsset 元数据）
4. 将音乐信息存储到 Room 数据库
5. 通过 Repository 暴露给 UI 层

**关键代码**（扫描在 `:shared` 的平台源集，**不在** `:android:core-player`）：
- `shared/src/commonMain/.../data/util/DeviceMusicScanner.kt`：`expect` 声明；actual 在 `DeviceMusicScanner.{android,desktop,ios}.kt`（Android 用 MediaStore + `MediaMetadataRetriever`）
- `shared/src/commonMain/.../data/util/MusicTagParser.kt`：`expect` 声明；iOS actual 委托给 Swift 注册的 `MusicMetadataParser` 桥
- `shared/src/commonMain/.../domain/music/MusicRepository.kt`：数据访问接口（实现在各端 `*Impl.{android,desktop,ios}.kt`，基于 `MusicRepositoryBase.kt`）
- ⚠️ `DeviceMusicScanner.android.kt` 有一处已知缺陷（自建 `Application()` 无 base context），动 Android 扫描前先看 `AGENTS.md` §八 第 8 条

### 播放控制

**流程**：
1. 用户选择歌曲
2. ViewModel 更新播放队列
3. Service / 引擎层启动播放器
4. 媒体会话同步播放状态
5. 通知栏 / 锁屏显示播放控制

**关键代码**（三端各自的实现，统一由 `shared-ui` 的 `PlaybackController` 接口对上供给 UI）：
- `shared-ui/src/commonMain/.../ui/platform/PlaybackController.kt`：三端共享的播放控制契约（接口已冻结）
- `android/core-player/.../player/controller/MusicController.kt`：Android 服务绑定与队列状态机
- `android/core-player/.../player/service/MusicPlayService.kt`：ExoPlayer + `MediaSession` + 通知栏（**裸 `Service`，不是 `MediaSessionService`**；`MediaSession` 就在本文件内，没有独立的 `MediaSessionManager.kt`）
- `desktop/core-player/.../player/DesktopMusicController.kt` + `FFmpegAudioEngine.kt`：Desktop 侧控制器与 FFmpeg 子进程引擎
- `ios/HMP/HMP/PlayerEngine.swift` + `MusicPlayerController.swift`：iOS AVFoundation 引擎，经 `PlaybackBridge.swift` 接到 Kotlin 侧的 `IosPlaybackController` / `IosPlaybackStateSink`
- UI 侧适配器：`MusicControllerPlaybackAdapter`（Android）/ `DesktopMusicControllerPlaybackAdapter`（Desktop）

### AI推荐功能

**流程**：
1. 用户选择 AI 服务商并配置 API 密钥
2. 系统自动或手动触发推荐（根据刷新策略）
3. ViewModel调用Use Case
4. Repository请求当前选中的 AI 服务商 API
5. 解析推荐结果并生成标签
6. 展示推荐歌曲和 AI 生成的扩展信息

**关键代码**：
- `shared/src/commonMain/.../data/network/MultiProviderApiAdapter.kt`：多服务商 API 适配器（统一 OpenAI 兼容契约，SSE 流式）
- `shared/src/commonMain/.../domain/music/usecase/GetDailyMusicRecommendationUseCase.kt`：推荐用例
- `shared/src/commonMain/.../domain/setting/SettingsRepository.kt`：API 密钥存储（经 `SecureStorageHelper`；⚠️ iOS 侧目前是 XOR 而非真加密，见 TODO **R10**）
- `shared-ui/src/commonMain/.../ui/agent/config/AIScreen.kt`：AI 服务商配置界面

> 方向 B 之后，AI 推荐不再只走这条单层链路。对话、电台、曲库富化、画像报告都由 `shared/.../domain/agent/` 的 Agent 体系驱动（`MasterAgent` 门面 + `ReActLoop` 引擎 + 30 个工具），LLM 调用统一经 `domain/agent/port/LlmTransport`。改 AI 相关功能前先看 `AGENTS.md` §六。

### 每日推荐刷新策略

**功能**：
- 按时间刷新：用户可设置间隔小时数（默认24小时）
- 按启动次数刷新：用户可设置启动次数（默认3次）
- 智能刷新：预留接口，后续可根据听歌习惯智能判断
- 持久化存储：重启后保持同一首每日推荐

**关键代码**：
- `shared/src/commonMain/.../domain/setting/usecase/UserSettingsUseCase.kt`：刷新策略判断逻辑
- `shared/src/commonMain/.../domain/setting/SettingsRepository.kt`：刷新配置存储
- `shared-ui/src/commonMain/.../RecommendationViewModel.kt`：刷新控制逻辑（**不存在 `MusicViewModel.kt`**）
- `shared-ui/src/commonMain/.../settings/pages/SettingScreen.kt`：刷新策略配置界面

## 📚 学习资源

### 官方文档

- [Kotlin官方文档](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose官方文档](https://developer.android.com/jetpack/compose)
- [AndroidX Media3官方文档](https://developer.android.com/jetpack/androidx/releases/media3)
- [Koin官方文档](https://insert-koin.io/docs/setup/koin)
- [Kotlin Multiplatform官方文档](https://kotlinlang.org/docs/multiplatform.html)
- [SwiftUI官方文档](https://developer.apple.com/documentation/swiftui/)

### 推荐教程

- [Jetpack Compose Tutorial](https://developer.android.com/codelabs/jetpack-compose-basics)
- [Android MVVM Architecture](https://developer.android.com/topic/architecture)
- [Media3 Playback Tutorial](https://developer.android.com/codelabs/media3-getting-started)
- [Kotlin Multiplatform Mobile Tutorial](https://kotlinlang.org/docs/multiplatform-mobile-getting-started.html)
- [SwiftUI Tutorial](https://developer.apple.com/tutorials/swiftui/)

## 🤝 贡献指南

作为个人项目，我欢迎任何形式的贡献和反馈。如果您有任何建议或问题，请随时联系我。

### 贡献方式

1. 提交Issue报告bug或提出功能建议
2. 提交Pull Request修复bug或添加新功能
3. 提供使用反馈和改进建议

### 代码规范

- 遵循Kotlin官方代码风格指南
- 使用Jetpack Compose的最佳实践
- 保持代码简洁、可读性强
- 添加必要的注释和文档

## 📄 许可证

该项目使用MIT许可证 - 详情请查看LICENSE文件

---

© 2026 Hearable Music Player | Developed by WLYB