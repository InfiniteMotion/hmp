# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Hearable Music Player (HMP) — 一款跨平台本地音乐播放器，Android / Desktop / iOS 三端共用一套 Compose Multiplatform UI（`shared-ui`），业务层基于 Kotlin Multiplatform。iOS 仅保留原生壳与平台桥接（播放引擎、权限、Live Activity）。当前版本 v7.1.0。

**产品边界**：纯本地，不做在线/云同步、不引入账号、不做社交；仅保留用户自填 API 的 AI 推荐。

## 常用命令

### Android 构建
```bash
# 构建全部
./gradlew build

# Android Debug 构建
./gradlew :android:app:assembleDebug

# Android Release 构建
./gradlew :android:app:assembleRelease

# 安装 Debug APK 到设备
./gradlew :android:app:installDebug

# Shared 模块 Android 编译
./gradlew :shared:compileAndroidMain
```

### iOS 构建
```bash
# 生成聚合框架 + podspec（shared + shared-ui 单框架 sharedIos）
./gradlew :shared-ios:generateDummyFramework
./gradlew :shared-ios:podspec

# shared-ui / shared 模块 iOS 编译
./gradlew :shared-ui:compileKotlinIosSimulatorArm64
./gradlew :shared:compileKotlinIosSimulatorArm64

# 安装 CocoaPods 依赖（Podfile 已含 pod 'shared_ios'；Pod 脚本阶段自动 link + 同步产物）
cd ios && pod install

# 构建 iOS 模拟器 App（Apple Silicon；Xcode 26.6 需 iOS 26.5 模拟器运行时，
# 缺失时先 `xcodebuild -downloadPlatform iOS`；generic 目的地会自动装 26.5）
xcodebuild -workspace ios/HMP.xcworkspace -scheme HMP -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```
说明：
- **聚合框架**：`shared-ios` 模块把 `:shared` + `:shared-ui` 链接为单一 static framework
  (baseName `sharedIos`)，Swift 统一 `import sharedIos`；避免双静态框架 duplicate symbol 与
  动态框架的 Koin 全局分裂。Podfile 含 Kotlin 2.3 适配（`syncFramework` 已更名 →
  `linkPod{Debug|Release}FrameworkIos{...}` + 产物同步到 podspec vendored 路径 +
  shared-ui composeResources 按 `<bundle>/compose-resources/composeResources/<Res包>/` 布局拷贝）。
- navigation3-ui 无 ios_x64 构件 → shared-ui/configure `shared-ios` 均不启用 iosX64。
- 试点入口：`simctl launch ... com.hmp.HMP -hmp-pilot`（AppDelegate 内模态呈现设置中心 Compose 试点）。

### Desktop 构建
```bash
# 运行 Desktop 应用（开发调试）
./gradlew :desktop:app:run

# 构建当前 OS 安装包 (macOS DMG / Windows MSI / Linux DEB+AppImage)
./gradlew :desktop:app:packageDistributionForCurrentOS
```

### 单端构建
通过环境变量 `HMP_BUILD_TARGET`（`android` / `desktop` / `all`，默认 `all`）只包含对应平台的模块（见 `settings.gradle.kts`），单端任务链不需要另一端的构建产物，可加快配置与构建。

### 测试与检查
```bash
# 运行单元测试
./gradlew test

# 运行 Android 仪器测试
./gradlew connectedAndroidTest
```

### 运行单个测试
```bash
# 运行指定测试类
./gradlew :shared-ui:testDebugUnitTest --tests "com.hearablemusic.player.ui.common.navigation.RoutesTest"

# 运行指定测试方法
./gradlew :shared-ui:testDebugUnitTest --tests "com.hearablemusic.player.ui.common.navigation.RoutesTest.testRouteDefinition"
```

### 发布构建
```bash
# 构建 Android + Desktop Release 产物（输出到 releases/）；在 macOS 上额外含 iOS
./gradlew release

# 仅构建 Android Release (APK + AAB)
./gradlew releaseAndroid

# 仅构建 Desktop Release 分发包 (DMG/MSI/DEB/AppImage)
./gradlew releaseDesktop

# 仅构建 iOS Release Archive (需 macOS)
./gradlew releaseIos

# 辅助任务：Android Debug APK / Desktop Uber JAR
./gradlew copyAndroidDebug
./gradlew copyDesktopJar
```

> 注：`releaseStorybook` 已移除 —— `:storybook` 模块自 `380f225` 起不在构建中（见 `settings.gradle.kts`）。恢复方式见根 `build.gradle.kts` 的「Storybook」注释节。
> 上游 CI（`.github/workflows/release.yml`）不使用这些封装任务，而是直接调用各模块底层任务（如 `:android:app:assembleRelease`）并自行归集重命名产物。

### 自定义 Gradle 任务全清单

项目自行注册的任务共 **26 个**，分布在 3 个构建脚本中（`./gradlew tasks --group release` 只显示 `group = "release"` 的 8 个）：

**验证类：测试（7 个，group = `verification`）**

| 任务 | 作用 |
|---|---|
| `testAll` | 全部单元测试（按当前构建目标自动跳过未纳入的模块） |
| `testCore` / `testQuick` | 只测 `:shared`（纯逻辑层，最快反馈） |
| `testUi` | 测 `:shared-ui`（含 `desktopTest` + `testAndroidHostTest`） |
| `testUiDesktop` | 只测 `:shared-ui` 的 desktop 源集（不依赖 Android SDK） |
| `testDesktop` / `testAndroid` | 只测 Desktop / Android 侧 |

> ⚠️ **`shared-ui` 的测试全在 `androidHostTest` 源集**（8 个文件，如 `ChatViewModelTest` / `RouterTest`），
> `desktopTest` 源集**是空的**——所以只挂 `desktopTest` 会 `NO-SOURCE` 空过。
> `testUi` 因此同时挂两个源集；没有 Android SDK 的环境改用 `testUiDesktop`。

**测试报告自动生成（Gradle 内建，无需配置）**

跑完测试任务后，报告自动出现在**被依赖的底层任务**目录下（聚合任务如 `testAll` 不自己产出报告）：

```
<模块>/build/reports/tests/<任务名>/index.html   ← 人看（HTML，含每类明细、耗时、失败堆栈）
<模块>/build/test-results/<任务名>/TEST-*.xml    ← 机器读（CI 解析用）
```

例：`testCore` → `shared/build/reports/tests/desktopTest/index.html`（含 91 个类的分页 + 26 个包的分页）。

> 报告按**测试任务名**分目录，多模块跑完是**多份独立报告**，Gradle 不提供统一入口页。
> 任务若为 UP-TO-DATE 则沿用旧报告；要强制刷新加 `--rerun`，或先跑 `cleanAllTests`。

**验证类：编译（5 个，group = `build`）**

| 任务 | 作用 |
|---|---|
| `compileAll` | 编译当前目标下全部模块（不含打包） |
| `compileCore` / `compileUi` | 只编译 `:shared` / `:shared-ui` |
| `compileDesktop` / `compileAndroid` | 只编译 Desktop / Android 侧 |

> 编译任务用于检测「改公共 API 导致下游编译失败」——比全量测试快得多。
> Android 侧（`android:app` / `android:core-player`）**不暴露 `compile*` 任务**（用 AGP 内置 Kotlin），故用 `assembleDebug` 作编译校验。

**发布类（8 个，group = `release`）**

| 任务 | 脚本 | 作用 |
|---|---|---|
| `release` | 根 | 总入口，聚合 Android + Desktop（macOS 上含 iOS） |
| `releaseAndroid` / `copyAndroidDebug` | 根 | Android Release APK+AAB / Debug APK |
| `releaseIos` | 根 | iOS Archive（仅 macOS） |
| `releaseDesktop` / `copyDesktopJar` | 根 | Desktop 分发包 / Uber JAR |
| `checkVersion` | 根 | 校验版本号未与已有 git tag 重复 |
| `preflight` | 根 | 发布前预检：版本 + 测试 + 平台可产出告知 |

**清理类（2 个，group = `build`）**

| 任务 | 作用 |
|---|---|
| `cleanReleases` | 清理 `releases/` 产物（保留目录与 `.gitkeep`） |
| `cleanOrphans` | 列出孤儿 `build/` 目录（**只打印，不自动删**） |

**FFmpeg（3 个，`desktop/app`，group = `desktop`）**：`downloadFFmpeg` / `injectFFmpeg` / `injectFFmpegForDev`

**iOS 图标（1 个，`shared`，无 group）**：`copyIconsToIos`（源目录不存在，当前空转）

> `tasks --all` 在本项目下共解析出 **616→631** 个任务（模块集不同会变），除上表 26 个外全部由 Gradle 及各插件自动生成，非本项目编写。

**低内存构建（本机内存受限时必用）**

本机 Android Studio 常驻占内存，默认 `-Xmx4096m + parallel` 会让 Gradle/Kotlin daemon 被 OS 静默杀死（报 `daemon disappeared`）。用包装脚本代替 `gradlew`：

```bash
./gradlew-lowmem.bat testCore        # Windows
./gradlew-lowmem testCore            # macOS / Linux / Git Bash
```

> **实测关键参数**（2026-09-15 反复验证）：Gradle daemon 堆必须是 **`-Xmx1024m`**，
> 且 Kotlin 编译器走 **`-Dkotlin.compiler.execution.strategy=in-process`**（不再另起 Kotlin daemon）。
> 用 `1536m`/`2048m` 时编译能过，但一到 `:shared:desktopTest`（要 fork 测试 JVM）daemon 就被杀掉。
> 这两个值已固化进包装脚本。
>
> 注：`org.gradle.jvmargs` 等是 Gradle **启动期属性**，无法在 `build.gradle.kts` 里条件化覆盖，只能用包装脚本。

**日常开发选任务**

| 改动范围 | 建议命令 |
|---|---|
| `shared` 的 Domain/Agent | `./gradlew-lowmem.bat testCore` |
| `shared-ui` 的 UI | `./gradlew-lowmem.bat testUi` |
| 改了公共 API | `./gradlew-lowmem.bat compileAll`（快速查下游是否破坏） |
| 提交前 | `./gradlew-lowmem.bat testAll` |
| 发版前 | `./gradlew preflight` |

## 架构概览

### 目录结构
```
HMP/
├── shared/                    # KMP 共享业务模块 (domain + data 层)
│   └── src/
│       ├── commonMain/        # 跨平台共享代码
│       ├── androidMain/       # Android 特定实现
│       ├── desktopMain/       # Desktop (JVM) 特定实现
│       └── iosMain/           # iOS 特定实现
├── shared-ui/                 # 共享 UI 模块 (Compose 页面, ViewModel)
│   └── src/
│       ├── commonMain/        # Android/Desktop/iOS 三端共享 UI（v7.0 Android/Desktop；v7.1 iOS）
│       ├── androidMain/       # Android 桥接层
│       ├── desktopMain/       # Desktop 桥接层
│       └── iosMain/           # iOS 桥接层（PlaybackController 双桥 / PlatformServices / 触觉等，A3-A4）
├── android/                   # Android 平台
│   ├── app/                   # 入口模块 (MainActivity, Application)
│   └── core-player/           # 播放核心 (Media3 服务, 播放控制)
├── desktop/                   # Desktop 平台 (Compose Multiplatform)
│   ├── app/                   # 入口模块 (Main.kt, 无边框窗口, 托盘, 单实例)
│   └── core-player/           # 播放核心 (FFmpeg + JNA 音频引擎)
├── ios/                       # iOS 平台（原生壳 + 平台桥接）
│   └── HMP/                   # Xcode 项目（XcodeGen + CocoaPods）
├── shared-ios/                # iOS 聚合框架（shared + shared-ui → sharedIos，方向 A A1）
└── storybook/                 # 组件展示 (Kotlin/Wasm) — 已移出构建，见 settings.gradle.kts
```

### 模块依赖关系
```
:shared-ui ──▶ :shared
:shared-ui ──▶ :android:core-player   (androidMain 桥接)
:shared-ui ──▶ :desktop:core-player   (desktopMain 桥接)
:android:app ──▶ :shared + :shared-ui
:desktop:app ──▶ :shared + :shared-ui + :desktop:core-player
:android:core-player ──▶ :shared
:desktop:core-player ──▶ :shared
:ios ──▶ :shared-ios (via CocoaPods；聚合 :shared + :shared-ui)
```

### 架构分层
- **UI 层**: Android / Desktop / iOS 三端共享 `shared-ui` commonMain（Compose，平台差异收口到 androidMain/desktopMain/iosMain 桥接层：`PlaybackController` / `AlbumArtPixelsLoader` / `PlatformServices` 接口，位于 `ui/platform/`）
- **ViewModel 层**: 位于 shared-ui，Koin 注入 (`koinViewModel()`)，StateFlow 状态管理
- **Domain 层**: Use Cases + 领域模型，位于 `shared/src/commonMain/kotlin/com/hmp/domain/`
- **Data 层**: Repository + Room Database + Ktor 网络，位于 `shared/src/commonMain/kotlin/com/hmp/data/`
- **播放引擎**: Android (Media3 ExoPlayer) / Desktop (FFmpeg + JNA 自研引擎) / iOS (AVFoundation)

### 依赖注入
- **Shared 模块**: Koin (`io.insert-koin:koin-core`)
- **Android 端**: Koin + Koin Compose (已从 Hilt 迁移)
- iOS 端通过 `AppDelegate` 调用 `KoinKt.doInitKoin()` 初始化

### 跨平台机制 (expect/actual)
以下接口通过 `expect` 在 commonMain 声明，`actual` 在 androidMain/desktopMain/iosMain 分别实现：
- `DeviceMusicScanner` — 设备音乐扫描
- `MusicTagParser` — 音乐标签解析
- `SecureStorageHelper` — 安全存储 (API Key 加密)
- `stringToPinyinSortKey()` — 拼音排序键
- `getRoomDatabase()` — Room 数据库构建
- `AppDatabaseConstructor` — Room 数据库构造器
- `DataStoreFactory` — DataStore 构建
- `createHttpClient()` / `createJson()` — Ktor HTTP 客户端 + JSON 配置 (Android: OkHttp engine / iOS: Darwin engine)
- `currentTimeMillis()` — 平台时间戳

图标资源已迁移至 `shared-ui` 的 composeResources 编译期资源（exhaustive when 映射，不再运行时动态加载）

## 关键技术栈

| 领域 | Android | Desktop | Shared (KMP) | iOS |
|------|---------|---------|--------------|-----|
| UI | shared-ui Compose + Material3 + Haze 毛玻璃 | shared-ui Compose (响应式 Compact/Expanded) | — | shared-ui Compose（v7.1 起，壳层不再持有页面） |
| 导航 | shared-ui 共享导航 (Navigation 3 类型安全) | 与 Android 共用 shared-ui | — | 与 Android 共用 shared-ui |
| 播放 | Media3 ExoPlayer | FFmpeg + JNA 自研引擎 | — | AVFoundation |
| 数据库 | Room KMP | Room KMP | Room KMP | Room KMP |
| 偏好存储 | DataStore | DataStore KMP | DataStore KMP | UserDefaults |
| 网络 | — | — | Ktor Client | Ktor (Darwin engine) |
| 序列化 | kotlinx.serialization | kotlinx.serialization | kotlinx.serialization | — |
| DI | Koin | Koin | Koin | Koin |
| 图片加载 | Coil | — | shared-ui 内统一 | shared-ui 内统一 |
| 标签解析 | Jaudiotagger + pinyin4j | — | 平台特定 | AVAsset 元数据 |

## 开发注意事项

### 版本信息
- 应用版本: 7.1.0 (versionCode 71000)
- JDK 工具链: 21（Desktop jpackage 要求 Gradle Daemon 运行于 JDK 21，配置说明见 `gradle.properties` 注释）
- Kotlin: 2.3.21
- AGP: 9.0.0
- Gradle: 9.x
- Android SDK: compileSdk 36, minSdk 33, targetSdk 36
- Koin: 4.2.2（4.2.2 起 iOS 端与 lifecycle 2.10 稳定 ID 对齐，修复 Koin 反射探测 SavedStateHandle 的 IrLinkageError）
- Ktor: 3.1.1
- Room: 2.8.3
- iOS 部署目标: 26.3 (应用目标), 16.0 (shared 模块 CocoaPods)

### 包名
- Android: `com.hearablemusic.player`
- Shared (KMP): `com.hmp`
- iOS: `com.hearablemusic.HMP`（2026-08-23 真机调试修改：原 com.hmp.HMP 不满足免费团队唯一标识注册，project.yml bundleIdPrefix 同时改为 com.hearablemusic）

### 分支策略
- `master`: 已发布版本
- `release/X.Y`: 发版集成分支，feature 分支合入后 PR 到 master 触发自动发布（合并后远程 release 分支删除）
- `feature/*`: 功能分支
- `fix/*`: 修复分支

> 无长期存活的 develop-* 分支；历史文档中提到的 develop 系列分支已不再使用。

### 版本号管理
版本号集中维护在 `gradle.properties` 中 (`hmp.versionCode` / `hmp.versionName`)，各模块通过 `project.findProperty()` 引用。

### 发布与 CI/CD
- 本地构建：`./gradlew release`（输出到 `releases/`，含 Android APK+AAB / Desktop DMG+MSI+DEB+AppImage；iOS Archive 仅 macOS）
- 自动发布：`release/*` 分支 PR 合并到 `master` 时，`.github/workflows/release.yml` 自动构建并发布 GitHub Release（Android + Desktop 产物 + SHA256 校验），并部署 `site/` 到 GitHub Pages
- 发版流程：feature/* 合入 `release/X.Y` → 验证通过（单测 + 版本号重复检测）→ PR 到 master 触发发布
- 详见 [docs/VERSIONING.md](docs/VERSIONING.md)

> 历史说明：早期文档称 CI「部署 Storybook 到 GitHub Pages」，实际 deploy-site job 上传的是 `site/` 目录（手工维护的产品站点），且 Storybook 相关 workflow 已在 v6.10 移除。

### 已知待完成任务 (TODO.md)
- v7.x 三大方向：A) KMP 重写 iOS UI / B) AI 功能 Agent 化 / C) 播放功能增强补齐（编排 7.1-7.4，方向论证见 ROADMAP「未来发展方向」，任务分解见 TODO.md「v7.x 阶段」）
- v6 遗留：P8.1 iOS 安全存储真加密（现 XOR 伪加密 → CryptoKit AES-GCM + Keychain）；P9 已由方向 A 取代冻结
- T3: Repository 通用逻辑提取到 commonMain 共享基类

### 文档索引
- [README.md](README.md) — 项目概览
- [docs/README.md](docs/README.md) — **文档索引**：各文档职责 + 历史版本方案 + 当前 Agent 设计资料
- [DEVELOP.md](DEVELOP.md) — 技术架构与开发流程
- [ROADMAP.md](ROADMAP.md) — 版本历史与功能状态 (单一事实来源)
- [TODO.md](TODO.md) — 可执行任务列表
- [docs/VERSIONING.md](docs/VERSIONING.md) — 版本号规范
- [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) — 设计系统规范
- [docs/ROOM_KMP_SETUP.md](docs/ROOM_KMP_SETUP.md) — Room KMP 跨平台数据库配置指南
- **AI Agent 体系（v7.3 方向 B）**
  - [docs/7_x/B agent-build/design/agent.md](docs/7_x/B%20agent-build/design/agent.md) — 设计总纲（单一事实来源）
  - [docs/7_x/B agent-build/taskbook/README.md](docs/7_x/B%20agent-build/taskbook/README.md) — 推进计划（f1–f9 阶段族）
- **历史版本方案（已完成，备查）**：`docs/5_9/`、`docs/5_10/`、`docs/6_1/`、`docs/6_12/`
