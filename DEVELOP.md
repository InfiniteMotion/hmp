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
- **ios**: iOS 应用模块，原生壳（原生层：AppDelegate / 播放引擎 / MediaSession / 桥，共 17 个 Swift 文件）+ 共享 Compose UI
- **storybook**: 组件展示与文档模块 (Kotlin/Wasm) —— **已移出构建**（`380f225` 起不在 `settings.gradle.kts` 中），源码保留

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
- iOS 端通过 `AppDelegate` 调用 `KoinKt.doInitKoin()` 初始化

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
- 参考 [Room KMP 配置文档](docs/ROOM_KMP_SETUP.md)
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

#### 9. 导航系统：Navigation 3 + 自研 NavController（三端共用）

**选择理由**：Navigation 3 提供了类型安全的导航方式，支持编译时路由检查和参数验证。为同时满足移动端单栏栈式导航与桌面端多面板响应式导航，项目在 Navigation 3 之上叠了一层自研 NavController + NavigationGraph（含深度链接支持），并在 `shared-ui` 的 commonMain 中实现，三端共用。

**实现细节**：
- 使用 @Serializable 注解定义路由，集中式路由管理，支持类型安全的参数传递
- 自研 NavController + NavigationGraph 实现多面板路由，支持 Compact/Expanded 布局切换
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
│   │   │       │   ├── database/    # Room 数据库与 DAO
│   │   │       │   ├── mapper/      # 数据映射器
│   │   │       │   ├── network/     # Ktor 网络层
│   │   │       │   └── util/        # 工具类与 expect 声明
│   │   │       ├── domain/           # 领域层
│   │   │       │   ├── model/       # 领域模型
│   │   │       │   └── usecase/     # Use Cases
│   │   │       ├── di/               # 依赖注入配置
│   │   │       └── shared/          # 共享资源加载
│   │   ├── androidMain/              # Android特定代码
│   │   │   └── kotlin/com/hmp/
│   │   │       └── data/
│   │   │           └── repository/   # Android Repository实现
│   │   ├── iosMain/                 # iOS特定代码
│   │   │   └── kotlin/com/hmp/
│   │   │       └── data/
│   │   │           └── repository/   # iOS Repository实现
│   ├── build.gradle.kts              # 共享模块构建配置
│   └── shared.podspec               # CocoaPods配置
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
│   ├── 7_x/                          # 当前进行中的工作（按项目线分区）
│   │   ├── A shared-ui/              # 共享 UI 提取线（v7.0 → v7.1，已完成）
│   │   └── B agent-build/            # AI Agent 线（方向 B，已随 v7.2.0 交付）
│   │       ├── design/               # 设计资料（要建成什么样）
│   │       └── taskbook/             # 推进计划（做到哪了）
│   └── 5_9, 5_10, 6_1, 6_12/         # 历史版本开发方案（存档）
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
- Android Studio Ladybug | 2024.2.1
- Kotlin 2.2.21
- Gradle 9.0
- Android SDK 36
- AGP (Android Gradle Plugin) 9.0.0

#### iOS
- Xcode 17.0 或更高版本
- Swift 5.0 或更高版本
- CocoaPods 1.16.0 或更高版本
- macOS 14.0 或更高版本

#### Desktop
- JDK 21 或更高版本
- Gradle 9.0
- FFmpeg（构建时自动下载）

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

# 生成共享Kotlin框架
./gradlew :shared:generateDummyFramework

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

# 构建 Linux DEB + AppImage（需 Linux）
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
- ✅ 桌面端三平台打包（macOS DMG / Windows MSI / Linux DEB+AppImage）

#### 进行中

- 🔄 单元测试覆盖
- 🔄 性能优化与内存泄漏修复

#### 待完成

- 📝 完善文档
- 📝 代码注释与格式化
- 📝 错误处理与异常捕获

### 代码风格

项目遵循Kotlin官方代码风格指南，使用ktlint进行代码检查。

**配置文件**：
- `.editorconfig`: 编辑器配置
- `ktlint.gradle`: ktlint配置

### 测试流程

项目采用分层测试策略，确保代码质量和功能正确性。

#### 单元测试

- 使用JUnit 4进行单元测试
- 测试Repository、Use Cases和ViewModel
- 运行命令：`./gradlew test`

#### 仪器测试

- 使用AndroidX Test进行仪器测试
- 测试UI交互和服务功能
- 运行命令：`./gradlew connectedAndroidTest`

### 版本控制

项目使用Git进行版本控制，采用Git Flow工作流。

**分支策略**（与版本规范一致，详见 [docs/VERSIONING.md](docs/VERSIONING.md) 分支与发版）：
- `master`: 已发布版本；MINOR/MAJOR 通过从 release/X.Y 合并更新，PATCH 可在 master 上直接改并打 tag
- `develop-android` / `develop-ios` / `develop-desktop` / `develop-shared`: 各平台独立开发分支
- `release/X.Y`: 发版集成分支，各 develop 合入后 PR 到 master
- `feature/*`: 功能分支，从对应 develop 拉出，开发完毕后合并回
- `fix/*`: 修复分支，合并回对应 develop 或（若仅 PATCH 热修）合并回 master

### 构建与发布

#### 构建类型

- `debug`: 调试版本，包含调试信息
- `release`: 发布版本，经过混淆和优化

#### 版本号管理

版本号集中维护在 `gradle.properties` 中：

```properties
hmp.versionCode=72004
hmp.versionName=7.2.4
```

各模块通过 `project.findProperty("hmp.versionCode")` 引用，避免多处手动同步。

#### 发布流程

版本号与发布步骤详见 **[docs/VERSIONING.md](docs/VERSIONING.md)**，摘要如下：

1. **确定版本类型**：按变更内容决定升级 MAJOR / MINOR / PATCH，得到新版本号（如 7.2.0）
2. **创建 release 分支**：从 master 拉出 `release/X.Y`，将各 develop 分支合入
3. **更新版本号**：在 `gradle.properties` 中更新 `hmp.versionCode` 和 `hmp.versionName`
4. **更新 ROADMAP**：在 [ROADMAP.md](ROADMAP.md) 中新增该版本条目与「当前版本」
5. 本地构建发布包：`./gradlew release`（输出到 `releases/` 目录，含 Android + Desktop；macOS 上额外含 iOS）
   - `./gradlew releaseAndroid` — 仅 Android（APK + AAB）
   - `./gradlew releaseDesktop` — 仅 Desktop（DMG/MSI/DEB/AppImage）
   - `./gradlew releaseIos` — 仅 iOS（需 macOS）
   - `./gradlew copyAndroidDebug` / `copyDesktopJar` — 辅助：Debug APK / Uber JAR
6. 将 release/X.Y PR 到 master，CI 自动构建并发布 GitHub Release

> 注：`releaseStorybook` 已移除 —— `:storybook` 模块自 `380f225` 起移出构建（见 `settings.gradle.kts`）。
> 上述封装任务仅便于本地使用；CI 不使用它们，而是直接调用各模块底层任务并自行归集产物。

#### 自定义 Gradle 任务

项目自行注册的任务共 **26 个**：

| 类别 | 任务 |
|---|---|
| **测试**（`verification`） | `testAll`、`testCore`、`testQuick`、`testUi`、`testUiDesktop`、`testDesktop`、`testAndroid` |
| **编译**（`build`） | `compileAll`、`compileCore`、`compileUi`、`compileDesktop`、`compileAndroid` |
| **发布**（`release`） | `release`、`releaseAndroid`、`copyAndroidDebug`、`releaseIos`、`releaseDesktop`、`copyDesktopJar`、`checkVersion`、`preflight` |
| **清理**（`build`） | `cleanReleases`、`cleanOrphans` |
| **FFmpeg**（`desktop`） | `downloadFFmpeg`、`injectFFmpeg`（`injectFFmpegForDev` 已于 2026-09 移除，`run` 直接依赖 `downloadFFmpeg`） |
| **iOS 图标** | `copyIconsToIos`（空转） |

> 注：`android:app` / `android:core-player` **不暴露 `compile*` 任务**（AGP 内置 Kotlin），故 `compileAndroid` 用 `assembleDebug`。
> `cleanOrphans` **只打印清单、不自动删除** —— 删除属破坏性操作，需人工确认。
> **`shared-ui` 的测试全在 `androidHostTest` 源集**（`desktopTest` 是空的），故 `testUi` 同时挂 `testAndroidHostTest`；无 Android SDK 时用 `testUiDesktop`。

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
| 发版前 | `./gradlew preflight` |

其余数百个任务（`assemble*` / `bundle*` / `link*` / `compile*` / `package*` 等）均由 Gradle 与各插件自动生成，非本项目编写。

#### CI/CD 自动发布

项目配置了 GitHub Actions 自动发布工作流 (`.github/workflows/release.yml`)：

- **触发条件**：`release/*` 分支的 PR 合并到 `master` 时自动触发
- **test job**：跑 `./gradlew testAll`（封装任务，见上「自定义 Gradle 任务」）并校验版本号未重复
- **desktop-macos / desktop-windows / desktop-linux job**：并行构建桌面三平台安装包
- **release job**：构建 Android APK + AAB，汇总桌面产物，基于上一个 tag 自动生成 changelog，创建 GitHub Release 并上传所有产物
- **deploy-site job**：将手工维护的产品站点 `site/` 部署到 GitHub Pages（非 Storybook）

## 🎯 关键实现细节

### 音乐扫描与解析

**流程**：
1. 申请存储权限
2. 扫描设备中的音乐文件
3. 使用Jaudiotagger解析ID3标签
4. 将音乐信息存储到Room数据库
5. 通过Repository暴露给UI层

**关键代码**：
- `MusicScanner.kt`: 音乐扫描逻辑
- `ID3Parser.kt`: ID3标签解析
- `MusicRepository.kt`: 数据访问封装

### 播放控制

**流程**：
1. 用户选择歌曲
2. ViewModel更新播放队列
3. Service层启动ExoPlayer
4. 媒体会话同步播放状态
5. 通知栏显示播放控制

**关键代码**：
- `PlayControlViewModel.kt`: 播放控制逻辑
- `MusicPlayService.kt`: 播放服务实现
- `MediaSessionManager.kt`: 媒体会话管理

### AI推荐功能

**流程**：
1. 用户选择 AI 服务商并配置 API 密钥
2. 系统自动或手动触发推荐（根据刷新策略）
3. ViewModel调用Use Case
4. Repository请求当前选中的 AI 服务商 API
5. 解析推荐结果并生成标签
6. 展示推荐歌曲和 AI 生成的扩展信息

**关键代码**：
- `MultiProviderApiAdapter.kt`: 多服务商 API 适配器
- `GetDailyMusicRecommendationUseCase.kt`: 推荐用例
- `SettingsRepository.kt`: API 密钥加密存储
- `AIScreen.kt`: AI 服务商配置界面

### 每日推荐刷新策略

**功能**：
- 按时间刷新：用户可设置间隔小时数（默认24小时）
- 按启动次数刷新：用户可设置启动次数（默认3次）
- 智能刷新：预留接口，后续可根据听歌习惯智能判断
- 持久化存储：重启后保持同一首每日推荐

**关键代码**：
- `UserSettingsUseCase.kt`: 刷新策略判断逻辑
- `SettingsRepository.kt`: 刷新配置存储
- `MusicViewModel.kt`: 刷新控制逻辑
- `SettingScreen.kt`: 刷新策略配置界面

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