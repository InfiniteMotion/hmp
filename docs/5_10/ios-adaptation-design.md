# HMP iOS 适配与双平台维护工作流 — 设计文档

**日期**：2026-04-19
**状态**：待审阅
**范围**：KMP 共享核心层 + iOS 原生 UI + Monorepo 双平台维护

---

## 1. 背景与目标

HMP（Hearable Music Player）是一款纯 Android 本地音乐播放器，当前版本 v5.9，采用 Kotlin + Jetpack Compose + MVVM 模块化架构。项目定位为个人技术探索。

**目标**：
1. 将 HMP 适配到 iOS 平台
2. 建立长期的双平台同时维护工作流
3. 通过 KMP 共享 domain + data 层代码（约 40-50%），UI 和播放引擎保持平台原生

**约束**：
- 产品边界不变：纯本地、无账号、无社交、仅保留用户自填 API 的 AI 推荐
- 一次性重构（非渐进式迁移）
- Monorepo 仓库结构
- 开发者 iOS 经验为零，此项目本身即为技术探索

---

## 2. 技术选型

### 2.1 技术栈映射

| 层级 | 现有 Android 技术 | KMP 迁移后 | 迁移说明 |
|------|-------------------|-----------|---------|
| 数据库 | Room 2.8.3 | Room 2.8.x（KMP 模式） | Room 2.7+ 官方支持 KMP，含 iOS。使用 `@ConstructedBy` + `expect object` 模式，`BundledSQLiteDriver` 保证跨平台一致性 |
| 偏好存储 | DataStore Preferences | DataStore Preferences Core | `androidx.datastore:datastore-preferences-core` 已支持 KMP |
| 网络请求 | Retrofit + OkHttp | Ktor Client | KMP 原生 HTTP 客户端，替代 Retrofit。支持 ContentNegotiation、日志拦截器 |
| JSON 序列化 | Gson | kotlinx.serialization | KMP 原生序列化库，替代 Gson |
| 依赖注入 | Hilt | Koin | Hilt 不支持 iOS，Koin 是 KMP 生态中最成熟的 DI 框架 |
| 音乐标签解析 | Jaudiotagger | expect/actual 封装 | `commonMain` 定义接口，`androidMain` 用 Jaudiotagger，`iosMain` 用 AVAssetReader |
| 播放引擎 | Media3 (ExoPlayer) | 平台各自实现 | Android: Media3 不变；iOS: AVPlayer + AVAudioSession |
| UI 框架 | Jetpack Compose | 平台各自实现 | Android: Compose 不变；iOS: SwiftUI |
| 导航系统 | Navigation 3 | 平台各自实现 | Android: Nav3 不变；iOS: NavigationStack |
| 构建工具 | Gradle 9.0 | Gradle 9.x + Xcode | Android 用 Gradle，iOS 用 Xcode（通过 Gradle 集成 KMP 框架） |

### 2.2 选型理由

- **保留 Room 而非 SQLDelight**：Room 2.7+ 已官方支持 KMP（含 iOS），现有 Entity/DAO 可直接迁移，避免重写全部 SQL。使用 `BundledSQLiteDriver` 保证跨平台 SQLite 版本一致。
- **Ktor 替代 Retrofit**：Retrofit 不支持 KMP，Ktor 是 JetBrains 官方的 KMP HTTP 客户端，API 风格与 Retrofit 类似（声明式 + 协程）。
- **Koin 替代 Hilt**：Hilt 依赖 Android 框架，无法在 iOS 使用。Koin 纯 Kotlin 实现，支持 KMP，且 API 简洁。
- **kotlinx.serialization 替代 Gson**：Gson 依赖 JVM，kotlinx.serialization 是编译期生成的 KMP 原生序列化方案。

---

## 3. Monorepo 项目结构

```
HMP/
├── shared/                              # KMP 共享模块
│   ├── build.gradle.kts                 # KMP 多目标配置 (android + iosX64 + iosArm64 + iosSimulatorArm64)
│   ├── src/
│   │   ├── commonMain/                  # 跨平台共享代码
│   │   │   └── com/hmp/
│   │   │       ├── domain/              # 领域层
│   │   │       │   ├── model/           # 领域模型 (MusicInfo, Playlist, Song 等)
│   │   │       │   ├── repository/      # Repository 接口
│   │   │       │   └── usecase/         # Use Cases
│   │   │       ├── data/                # 数据层
│   │   │       │   ├── database/        # Room Entity, DAO, AppDatabase
│   │   │       │   ├── repository/      # Repository 实现
│   │   │       │   ├── network/         # Ktor AI 适配器 (MultiProviderApiAdapter)
│   │   │       │   ├── tag/             # expect MusicTagParser
│   │   │       │   └── datastore/       # DataStore 偏好存储封装
│   │   │       └── di/                  # Koin 模块定义 (sharedModule, dataModule, domainModule)
│   │   ├── commonTest/                  # 共享层单元测试
│   │   ├── androidMain/                 # Android 平台特定
│   │   │   └── com/hmp/
│   │   │       ├── data/
│   │   │       │   ├── database/        # Room Android Builder (Context.getDatabasePath)
│   │   │       │   ├── tag/             # actual MusicTagParser (Jaudiotagger)
│   │   │       │   └── datastore/       # DataStore Android 初始化
│   │   │       └── di/                  # Koin Android 特定模块
│   │   ├── iosMain/                     # iOS 平台特定
│   │   │   └── com/hmp/
│   │   │       ├── data/
│   │   │       │   ├── database/        # Room iOS Builder (NSFileManager)
│   │   │       │   ├── tag/             # actual MusicTagParser (AVAssetReader)
│   │   │       │   └── datastore/       # DataStore iOS 初始化
│   │   │       └── di/                  # Koin iOS 特定模块
│   │   └── iosTest/
│   └── schemas/                         # Room schema 导出
│
├── android/                             # Android 应用
│   ├── app/                             # 应用入口 (原 app 模块，依赖 shared)
│   │   ├── src/main/
│   │   │   ├── java/com/hmp/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── HMPApplication.kt    # startKoin { modules(sharedModule, androidModule) }
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── feature-ui/                      # Compose UI (不变，改为依赖 shared)
│   │   ├── src/main/
│   │   │   └── java/com/hmp/ui/
│   │   │       ├── common/              # 通用组件、设计系统、导航
│   │   │       ├── library/             # 音乐库页面
│   │   │       ├── player/              # 播放器页面
│   │   │       ├── playlist/            # 播放列表页面
│   │   │       └── settings/            # 设置页面
│   │   └── build.gradle.kts
│   ├── core-player/                     # Media3 播放核心 (不变)
│   │   ├── src/main/
│   │   │   └── java/com/hmp/player/
│   │   │       ├── controller/          # MusicController
│   │   │       ├── service/             # MusicPlayService
│   │   │       └── di/                  # Hilt/Koin Player 模块
│   │   └── build.gradle.kts
│   └── build.gradle.kts
│
├── ios/                                 # iOS 应用 (Xcode 项目)
│   ├── HMP.xcodeproj/
│   ├── HMP/
│   │   ├── App/
│   │   │   ├── HMPApp.swift             # @main 入口
│   │   │   └── AppDelegate.swift        # initKoin { modules(sharedModule, iosModule) }
│   │   ├── Features/                    # SwiftUI 视图（按功能模块组织）
│   │   │   ├── Library/                 # 音乐库（Home, Gallery, Search, Artist, Album）
│   │   │   ├── Player/                  # 播放器（NowPlaying, Lyrics, Queue）
│   │   │   ├── Playlist/                # 播放列表
│   │   │   ├── Settings/                # 设置（AI, AudioEffect, Theme, User）
│   │   │   └── Common/                  # 通用组件、设计系统
│   │   ├── Player/                      # AVPlayer 封装
│   │   │   ├── PlayerService.swift      # 播放控制（play, pause, next, previous, seek）
│   │   │   ├── AudioSessionManager.swift # 音频焦点、耳机/蓝牙/来电处理
│   │   │   └── NowPlayingManager.swift   # 锁屏/控制中心媒体控制
│   │   └── DI/
│   │       └── Koin.swift               # Koin iOS 初始化辅助
│   └── Tests/
│
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml               # 统一版本目录
├── build.gradle.kts                     # 根构建文件
├── settings.gradle.kts                  # 多项目配置
├── gradle.properties
├── .gitignore
├── docs/
│   ├── README.md
│   ├── VERSIONING.md
│   └── 7_x/                             # 项目线设计资料与推进计划
├── README.md
├── ROADMAP.md
├── DEVELOP.md
└── TODO.md
```

---

## 4. 共享层详细设计

### 4.1 Room 数据库 KMP 迁移

**数据库定义**（`commonMain`）：

```kotlin
@Database(
    entities = [Music::class, MusicExtra::class, UserInfo::class, MusicLabel::class, Playlist::class, PlaylistItem::class, PlaybackHistory::class, ListeningDuration::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(LabelConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun musicExtraDao(): MusicExtraDao
    abstract fun userInfoDao(): UserInfoDao
    abstract fun musicAllDao(): MusicAllDao
    abstract fun musicLabelDao(): MusicLabelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun listeningDurationDao(): ListeningDurationDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

**平台 Builder**：

- `androidMain`：使用 `Context.getDatabasePath()` 获取路径
- `iosMain`：使用 `NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory)` 获取路径
- 两者均使用 `BundledSQLiteDriver()` 保证 SQLite 版本一致

**迁移要点**：
- Migration 回调参数从 `SupportSQLiteDatabase` 改为 `SQLiteConnection`
- `@RawQuery` 参数从 `SupportSQLiteQuery` 改为 `RoomRawQuery`
- 移除所有 `SupportSQLite*` API 使用

### 4.2 网络层迁移（Retrofit → Ktor）

**AI 适配器重写**：

```kotlin
// commonMain
class KtorMultiProviderApiAdapter(
    private val httpClient: HttpClient
) {
    suspend fun callProvider(
        provider: AiProviderType,
        apiKey: String,
        requestBody: String
    ): AiApiResult<String> {
        // Ktor 请求实现，适配 DeepSeek/OpenAI/Claude/Qwen/Ernie 五家 API
    }
}
```

**模型序列化**：所有请求/响应模型改用 `@Serializable` 注解。

### 4.3 依赖注入（Hilt → Koin）

**共享层模块**（`commonMain`）：

```kotlin
val sharedModule = module {
    single { getRoomDatabase(getDatabaseBuilder()) }
    single<MusicRepository> { MusicRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get(), get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<BackupFileRepository> { BackupFileRepositoryImpl(get(), get()) }
    single { createHttpClient() }
    single { KtorMultiProviderApiAdapter(get()) }
    // Use Cases...
    single { GetAllMusicUseCase(get()) }
    single { SearchMusicUseCase(get()) }
    single { ManagePlaylistUseCase(get(), get()) }
    // ... 其余 UseCase
}
```

**Android 初始化**：

```kotlin
class HMPApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@HMPApplication)
            modules(sharedModule, androidPlayerModule, androidUiModule)
        }
    }
}
```

**iOS 初始化**：

```swift
// AppDelegate.swift
func application(_ application: UIApplication,
                 didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    KoinKt.doInitKoin { koin in
        koin.apply { _ in
            // iOS 特定模块
        }
    }
    return true
}
```

### 4.4 音乐标签解析（expect/actual）

```kotlin
// commonMain
expect class MusicTagParser() {
    suspend fun parseTags(filePath: String): MusicTags?
    suspend fun getLyrics(filePath: String): String?
}

data class MusicTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val bitrate: Int?,
    val sampleRate: Int?,
    val coverArt: ByteArray?
)
```

```kotlin
// androidMain
actual class MusicTagParser {
    actual suspend fun parseTags(filePath: String): MusicTags? {
        // 使用 MediaMetadataRetriever 获取基础信息（标题、艺术家、专辑、时长、比特率等）
    }

    actual suspend fun getLyrics(filePath: String): String? {
        // 使用 Jaudiotagger 读取内嵌歌词
    }
}
```

```kotlin
// iosMain
actual class MusicTagParser {
    actual suspend fun parseTags(filePath: String): MusicTags? {
        // 使用 AVAsset 获取基础信息
    }

    actual suspend fun getLyrics(filePath: String): String? {
        // 使用 AVAssetReader 或第三方 Swift 库读取内嵌歌词
    }
}
```

---

## 4.5 expect/actual 完整清单

以下是共享层中所有 expect/actual 的汇总：

| expect/actual | 用途 | Android 实现 | iOS 实现 |
|---|---|---|---|
| `getDatabaseBuilder()` | Room 数据库 Builder | `Context.getDatabasePath()` | `NSFileManager` + `NSDocumentDirectory` |
| `createDataStore()` | DataStore Preferences 初始化 | `Context.filesDir` + `createWithPath` | `NSDocumentDirectory` + `createWithPath` |
| `DeviceMusicScanner` | 设备音乐文件扫描 | `MediaStore` + `MediaMetadataRetriever` | `FileManager` + `AVAsset` |
| `MusicTagParser` | 音乐标签解析（标题、歌词等） | `MediaMetadataRetriever` + `Jaudiotagger` | `AVAsset` + `AVAssetReader` |
| `SecureStorageHelper` | API Key 加密存储 | Android KeyStore + AES-GCM | iOS Keychain Services |
| `stringToPinyinSortKey()` | 中文拼音排序键 | Pinyin4j | `CFStringTransform(kCFStringTransformToLatin)` |

---

## 4.6 设备音乐扫描抽象（expect/actual）

`MusicRepositoryImpl` 中的 `loadMusicFromDevice()` 和 `syncMusicFromDeviceIncremental()` 大量使用 Android 专有 API（`MediaStore`、`MediaMetadataRetriever`）。需要将扫描逻辑抽象为平台特定实现。

```kotlin
// commonMain
expect class DeviceMusicScanner() {
    suspend fun scanAllMusic(): List<ScannedMusicFile>
    suspend fun scanIncremental(sinceLastScan: List<MusicExtraIdDate>): List<ScannedMusicFile>
}

data class ScannedMusicFile(
    val path: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val dateModified: Long,
    val size: Long
)
```

- `androidMain`：使用 `MediaStore.Audio.Media` 查询 + `MediaMetadataRetriever` 获取元数据
- `iosMain`：使用 `FileManager` 扫描 Documents 目录 + `AVAsset` 获取元数据

`MusicRepositoryImpl` 构造函数注入 `DeviceMusicScanner`，替代直接调用 Android API。

### 4.6 MusicRepositoryImpl 重构策略

当前 `MusicRepositoryImpl` 构造函数有 12 个参数（9 个 DAO + MultiProviderApiAdapter + Gson + Context），重构后为 13 个参数（移除 Gson/Context，新增 DeviceMusicScanner + MusicTagParser + SecureStorageHelper）。重构方案：

1. **移除 `Context` 参数**：通过 `DeviceMusicScanner`（expect/actual）和 `SecureStorageHelper`（expect/actual）替代所有 `Context` 用法
2. **移除 `Gson` 参数**：替换为 `Json`（kotlinx.serialization）
3. **注入 `DeviceMusicScanner`**：替代 `loadMusicFromDevice`/`syncMusicFromDeviceIncremental` 中的 `MediaStore` 调用
4. **注入 `MusicTagParser`**：替代 `getLyrics()` 中的 `Jaudiotagger` 调用
5. **注入 `SecureStorageHelper`**：替代 `validateProviderApiKey()` 中的 `SecureStorage` 调用

重构后的构造函数：

```kotlin
class MusicRepositoryImpl(
    private val musicDao: MusicDao,
    private val musicExtraDao: MusicExtraDao,
    private val userInfoDao: UserInfoDao,
    private val musicAllDao: MusicAllDao,
    private val musicLabelDao: MusicLabelDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val listeningDurationDao: ListeningDurationDao,
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val multiProviderApiAdapter: KtorMultiProviderApiAdapter,
    private val deviceMusicScanner: DeviceMusicScanner,
    private val musicTagParser: MusicTagParser,
    private val secureStorageHelper: SecureStorageHelper
) : MusicRepository
```

---

## 5. iOS 端设计

### 5.1 播放引擎（AVPlayer）

| Android (Media3) | iOS (AVFoundation) | 说明 |
|---|---|---|
| ExoPlayer | AVPlayer | 核心播放器 |
| MediaSession | MPNowPlayingInfoCenter + RemoteCommandCenter | 锁屏/控制中心 |
| AudioFocus | AVAudioSession | 音频焦点管理 |
| MediaNotification | UNUserNotification (可选) | 通知栏控制 |

### 5.2 UI 架构（SwiftUI）

采用与 Android 端对等的功能模块划分：

| Android 模块 | iOS 对应 | 说明 |
|---|---|---|
| library/ | Features/Library/ | 音乐库（Home, Gallery, Search, Artist, Album） |
| player/ | Features/Player/ | 播放器（NowPlaying, Lyrics, Queue） |
| playlist/ | Features/Playlist/ | 播放列表管理 |
| settings/ | Features/Settings/ | 设置（AI, AudioEffect, Theme, User） |
| common/ | Features/Common/ | 通用组件、设计系统 |

### 5.3 iOS 开发学习路径

作为零经验 iOS 开发者，建议按以下顺序学习：

1. **Swift 语言基础** — 语法、可选值、协议、异步（async/await）
2. **SwiftUI 基础** — 视图声明、状态管理（@State, @Binding, @Observable）
3. **AVFoundation 播放** — AVPlayer、AVAudioSession
4. **KMP 集成** — 在 Xcode 中引入 Gradle 构建的 KMP 框架
5. **迭代开发** — 按功能模块逐个实现，每个模块对应 Android 端已有功能

---

## 6. 双平台维护工作流

### 6.1 Git 分支策略

> **注意**：v5.10 期间使用 `develop-5.10` 单分支开发。v6.0 起调整为按平台拆分的 develop 分支模式，详见 [docs/VERSIONING.md](../VERSIONING.md)。

v5.10 期间实际使用：

```
master ─────────────────────────────────────  (已发布版本)
  │
develop-5.10 ──────────────────────────────  (5.10 版本集成)
  │
  ├── feature/kmp-shared-xxx               (共享层功能变更)
  ├── feature/android-xxx                  (Android 专属变更)
  ├── feature/ios-xxx                      (iOS 专属变更)
  └── feature/cross-xxx                    (跨平台功能，共享层 + 双平台 UI)
```

**分支命名规则**：
- `feature/kmp-shared-*`：仅涉及 `shared/` 目录
- `feature/android-*`：仅涉及 `android/` 目录
- `feature/ios-*`：仅涉及 `ios/` 目录
- `feature/cross-*`：涉及 `shared/` + 至少一个平台目录

### 6.2 日常开发场景

**场景 A：修改共享层逻辑**
1. `git checkout develop && git pull`
2. `git checkout -b feature/kmp-shared-add-sort-option`
3. 在 `shared/` 中修改（模型、UseCase、Repository 等）
4. `./gradlew :shared:build` 验证编译
5. 分别在 Android 和 iOS 端验证功能
6. PR 合并回 `develop`

**场景 B：Android 专属 UI 调整**
1. `git checkout develop && git pull`
2. `git checkout -b feature/android-player-animation`
3. 仅修改 `android/` 目录
4. `./gradlew :android:app:assembleDebug` 验证
5. PR 合并回 `develop`（iOS 不受影响）

**场景 C：新增跨平台功能**
1. `git checkout develop && git pull`
2. `git checkout -b feature/cross-equalizer-presets`
3. 先在 `shared/` 中添加数据模型 + UseCase + Repository
4. 在 `android/` 中添加 Compose UI
5. 在 `ios/` 中添加 SwiftUI UI
6. 双平台分别验证
7. PR 合并回 `develop`

### 6.3 构建命令

```bash
# 共享层编译（所有平台）
./gradlew :shared:build

# Android 编译
./gradlew :android:app:assembleDebug
./gradlew :android:app:assembleRelease

# iOS 框架构建（供 Xcode 使用）
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:linkDebugFrameworkIosArm64

# 共享层测试
./gradlew :shared:allTests

# Android 测试
./gradlew :android:app:testDebugUnitTest
```

### 6.4 CI/CD（GitHub Actions）

```yaml
name: CI
on: [push, pull_request]

jobs:
  shared-build:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - run: ./gradlew :shared:build

  android-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - run: ./gradlew :android:app:testDebugUnitTest

  ios-build:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### 6.5 版本管理

- 共享层版本跟随主项目 `MAJOR.MINOR.PATCH`
- 版本升级规则：
  - 共享层 API 变更（新增/修改 UseCase、模型等）→ MINOR
  - 仅平台 UI/播放引擎变更 → PATCH
  - 架构重大变更 → MAJOR
- ROADMAP.md 中每项变更标注影响平台：`[Android]` / `[iOS]` / `[Cross]`

---

## 7. 迁移执行策略

### 7.1 迁移阶段

| 阶段 | 内容 | 预计工作量 | 产出 |
|------|------|-----------|------|
| **P0: 项目骨架** | 创建 Monorepo 结构、KMP Gradle 配置、Xcode 项目初始化 | 小 | 可编译的空项目骨架 |
| **P1: domain 层迁移** | 将 core-domain 移入 shared/commonMain，移除 Android 依赖 | 小 | 共享领域层编译通过 |
| **P2: data 层迁移 — Room** | Room Entity/DAO 移入 commonMain，配置 KMP Builder | 中 | 数据库跨平台可用 |
| **P3: data 层迁移 — 网络** | Retrofit → Ktor，Gson → kotlinx.serialization | 中 | AI 推荐功能跨平台可用 |
| **P4: data 层迁移 — 其他** | DataStore KMP、标签解析 expect/actual、Koin DI | 中 | 共享层完整可用 |
| **P5: Android 端适配** | Android 模块改为依赖 shared，app/feature-ui 层 DI 切换为 Koin。推荐将 `MusicController` 也切换为 Koin 管理，core-player 仅保留 Hilt 的 `PlayerModule`（提供 ExoPlayer），其余 DI 全部由 Koin 处理。core-data 和 core-domain 模块删除 | 中 | Android 端功能恢复 |
| **P6: iOS 端基础** | AVPlayer 封装、SwiftUI 基础框架、Koin 初始化 | 大 | iOS 端可播放音乐 |
| **P7: iOS 端功能** | 逐模块实现 iOS UI（Library → Player → Playlist → Settings） | 大 | iOS 端功能对齐 |

### 7.2 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Room KMP 模式下某些 API 不可用 | 需要修改现有 DAO 查询 | 官方文档已列出不可用 API，提前检查并适配 |
| Ktor 迁移工作量大 | AI 适配器需要全部重写 | 逐个服务商迁移，先迁移一个验证流程 |
| Koin 替换 Hilt 后 ViewModel 注入方式变化 | Android 端 UI 层需要调整 | Koin 提供 `koinViewModel()` 扩展，推荐将 `MusicController` 也切换为 Koin 管理，彻底移除 Hilt |
| iOS 开发学习曲线 | iOS 端开发进度慢 | 先完成共享层，iOS 端可按模块逐步实现 |
| Gradle + Xcode 构建集成复杂 | 构建配置可能出问题 | 使用 Kotlin Multiplatform 官方推荐的 Gradle Xcode 集成方案 |
| 迁移后空模块清理 | core-data 和 core-domain 迁移后变为空壳 | 迁移完成后从 `settings.gradle.kts` 移除并删除目录 |

---

## 8. 成功标准

1. **共享层编译通过**：`./gradlew :shared:build` 成功编译 Android + iOS targets
2. **Android 端功能不变**：迁移后 Android 端所有现有功能正常工作
3. **iOS 端可运行**：iOS 端能扫描本地音乐、播放、管理播放列表
4. **双平台独立开发**：修改一个平台的 UI 代码不影响另一个平台
5. **共享层变更同步生效**：修改共享层逻辑后，双平台重新编译即可获得更新
6. **CI 流水线可用**：PR 自动触发共享层编译 + Android 测试 + iOS 编译验证

---

**最后更新**：2026-04-19
