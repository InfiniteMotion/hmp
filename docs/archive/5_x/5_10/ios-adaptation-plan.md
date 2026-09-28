# HMP v5.10 — iOS 适配实施计划

**基于设计文档**：[2026-04-19-ios-adaptation-design.md](ios-adaptation-design.md)
**版本**：v5.10.0
**范围**：P0-P7 全部阶段（Monorepo 骨架 → 共享层迁移 → Android 适配 → iOS 基础 → iOS 功能）

---

## 阶段总览

| 阶段 | 名称 | 依赖 | 验证标准 |
|------|------|------|---------|
| P0 | Monorepo 项目骨架 | 无 | `./gradlew :shared:build` 编译通过（空模块） |
| P1 | core-domain 迁移 | P0 | 共享领域层编译通过，Android 端可编译 |
| P2 | core-data 迁移 — Room | P1 | Room KMP 模式编译通过，数据库跨平台可用 |
| P3 | core-data 迁移 — 网络 | P2 | Ktor AI 适配器编译通过 |
| P4 | core-data 迁移 — DI/标签/存储/工具 | P3 | 共享层完整编译通过 |
| P5 | Android 端适配 | P4 | Android 端所有现有功能正常工作 |
| P6 | iOS 端基础 | P5 | iOS 端可编译运行，能播放音乐 |
| P7 | iOS 端功能 | P6 | iOS 端功能对齐 Android |

---

## P0: Monorepo 项目骨架

**目标**：建立 Monorepo 目录结构，配置 KMP Gradle，创建空模块骨架。

### 步骤

#### P0.1 调整目录结构

将现有模块移入 `android/` 子目录：

```bash
mkdir android
git mv app android/app
git mv core-data android/core-data
git mv core-domain android/core-domain
git mv core-player android/core-player
git mv feature-ui android/feature-ui
```

创建 `shared/` 和 `ios/` 空目录。

#### P0.2 更新根构建配置

**`settings.gradle.kts`**：
```kotlin
rootProject.name = "Hearable Music Player"

// KMP 共享模块
include(":shared")

// Android 应用模块（路径调整）
include(":android:app")
include(":android:core-data")
include(":android:core-domain")
include(":android:core-player")
include(":android:feature-ui")
```

**`build.gradle.kts`**（根）：
- 添加 `alias(libs.plugins.kotlin.multiplatform) apply false`
- 添加 `alias(libs.plugins.androidx.room) apply false`
- 保留现有 Android 插件声明

**`gradle/libs.versions.toml`** 新增：
```toml
[versions]
koin = "4.0.4"
ktor = "3.1.1"
sqlite = "2.6.1"
# datastorePreferences 已存在于现有 toml 中（版本 1.1.7），无需重复定义

[libraries]
# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-androidx-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-androidx-compose-viewmodel", version.ref = "koin" }

# Ktor
ktor-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

# Room KMP
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
androidx-room-sqlite-wrapper = { module = "androidx.room:room-sqlite-wrapper", version.ref = "roomVersion" }

# DataStore KMP（复用现有 datastorePreferences 版本）
androidx-datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastorePreferences" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidx-room = { id = "androidx.room", version.ref = "roomVersion" }
```

#### P0.3 创建 shared KMP 模块

**`shared/build.gradle.kts`**：
```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    kotlin("native.cocoapods")
}

kotlin {
    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        name = "shared"
        summary = "HMP shared module"
        version = "1.0.0"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../ios/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.ktor.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.logging)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

创建空目录结构：
```
shared/src/
  commonMain/kotlin/com/hmp/
    domain/
    data/
    di/
  androidMain/kotlin/com/hmp/
  iosMain/kotlin/com/hmp/
  commonTest/kotlin/com/hmp/
```

#### P0.4 更新 Android 模块路径

所有 Android 模块的 `build.gradle.kts` 中的 `project(":core-data")` 等引用更新为 `project(":android:core-data")`。

#### P0.5 验证

```bash
./gradlew :shared:build
./gradlew :android:app:assembleDebug
```

---

## P1: core-domain 迁移

**目标**：将 core-domain 移入 shared/commonMain，移除 Android 依赖。

### 步骤

#### P1.1 移动 core-domain 源文件

将 `android/core-domain/src/main/java/com/example/hearablemusicplayer/domain/` 下所有文件移入 `shared/src/commonMain/kotlin/com/hmp/domain/`。

**涉及文件清单**（36 个 .kt 文件）：
- `domain/backup/`：BackupFileRepository.kt, UserBackupSnapshot.kt, 4 个 UseCase
- `domain/config/`：DailyRefreshConfig.kt, LyricsConfig.kt
- `domain/enum/`：AiProviderType.kt, LabelCategory.kt, LabelName.kt, PlaybackMode.kt
- `domain/music/`：MusicModels.kt, MusicRepository.kt, 9 个 UseCase
- `domain/playlist/`：PlaylistModels.kt, PlaylistRepository.kt, algorithm/, 2 个 UseCase
- `domain/setting/`：SettingsRepository.kt, model/, 6 个 UseCase

#### P1.2 移除 Android 依赖

**`GetDailyMusicRecommendationUseCase.kt`**：
- 移除 `import android.util.Log`
- 替换为 `expect fun platformLog(tag: String, message: String)` 或使用 `kotlin.io.println` / Napier 等跨平台日志库

**所有 UseCase 中的 `javax.inject.Inject`**：
- Koin 不需要 `@Inject` 注解，构造函数注入是隐式的
- 保留 `@Inject` 注解不会报错（javax.inject 是纯 Java 接口），但建议统一移除以保持一致性

#### P1.3 更新 shared 模块依赖

`shared/build.gradle.kts` 的 `commonMain.dependencies` 中确保包含 `javax.inject:javax.inject`（如果保留注解）或移除相关引用。

#### P1.4 更新 Android 端引用

- `android/core-data` 的 `implementation(project(":android:core-domain"))` 改为 `implementation(project(":shared"))`
- `android/feature-ui` 同理
- `android/core-player` 同理
- 更新所有 `import com.hearablemusic.player.domain.*` 为 `import com.hmp.domain.*`

#### P1.6 清理空模块

P1 完成后，`android/core-domain` 模块的所有源文件已移入 `shared/`，该模块变为空壳。处理方式：
- 从 `settings.gradle.kts` 中移除 `include(":android:core-domain")`
- 删除 `android/core-domain/` 目录
- 后续所有原 `core-domain` 的消费者直接依赖 `:shared`

#### P1.5 验证

```bash
./gradlew :shared:build
./gradlew :android:app:assembleDebug
```

---

## P2: core-data 迁移 — Room

**目标**：将 Room Entity/DAO/Database 移入 shared/commonMain，配置 KMP 模式。

### 步骤

#### P2.1 移动 Room 数据库文件

将 `android/core-data/src/main/java/com/example/hearablemusicplayer/data/database/` 下所有文件移入 `shared/src/commonMain/kotlin/com/hmp/data/database/`。

**涉及文件**（10 个）：
- `AppDatabase.kt` — 需要改造为 KMP 模式
- `Music.kt` — Entity + DAO（MusicDao, MusicExtraDao, UserInfoDao, MusicAllDao）
- `PlayList.kt` — Entity + DAO（PlaylistDao）
- `PlaylistItem.kt` — Entity + DAO（PlaylistItemDao）
- `MusicLabel.kt` — Entity + DAO（MusicLabelDao）
- `DailyMusicInfo.kt` — 非 Entity 数据类
- `ListeningDuration.kt` — Entity + DAO（ListeningDurationDao）
- `PlaybackHistory.kt` — Entity + DAO（PlaybackHistoryDao）
- `myenum/Label.kt` — 枚举 + TypeConverter
- `myenum/PlaybackMode.kt` — 枚举

#### P2.2 改造 AppDatabase 为 KMP 模式

```kotlin
// shared/src/commonMain/kotlin/com/hmp/data/database/AppDatabase.kt
@Database(
    entities = [Music::class, MusicExtra::class, UserInfo::class, ...],
    version = 5,
    exportSchema = true
)
@TypeConverters(LabelConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun musicExtraDao(): MusicExtraDao
    // ... 其余 DAO
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

#### P2.3 迁移 Room Migration

将 4 个 Migration（MIGRATION_1_2 ~ MIGRATION_4_5）从 `SupportSQLiteDatabase` 迁移到 `SQLiteConnection`：

```kotlin
// 旧写法
object MIGRATION_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) { ... }
}

// 新写法
object MIGRATION_1_2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) { ... }
}
```

#### P2.4 创建平台特定 Database Builder

**`shared/src/androidMain/kotlin/com/hmp/data/database/DatabaseBuilder.android.kt`**：
```kotlin
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath("music_database.db")
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
}
```

**`shared/src/iosMain/kotlin/com/hmp/data/database/DatabaseBuilder.ios.kt`**：
```kotlin
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = documentDirectory() + "/music_database.db"
    return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
}

private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
```

**`shared/src/commonMain/kotlin/com/hmp/data/database/Database.kt`**：
```kotlin
fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
}
```

#### P2.5 检查 DAO 兼容性

根据 Room KMP 文档，以下 API 不可用：
- `@RawQuery` 使用 `SupportSQLiteQuery` → 改为 `RoomRawQuery`
- 阻塞式 DAO 方法（返回非 Flow/suspend 的查询）→ 改为 `suspend fun` 或 `Flow`
- `SupportSQLiteDatabase` 相关 API → 使用 `SQLiteConnection`

**需要检查的 DAO**：
- `MusicAllDao.getAllMusicInfoAsList()` — 使用了 `@RawQuery`，需改为 `RoomRawQuery`
- `MusicAllDao.getAllMusicInfoPaged()` — 返回 `PagingSource`，需确认 KMP 兼容性

#### P2.6 验证

```bash
./gradlew :shared:build
```

---

## P3: core-data 迁移 — 网络

**目标**：将 Retrofit/OkHttp/Gson 替换为 Ktor/kotlinx.serialization。

### 步骤

#### P3.1 移动网络相关文件

将 `android/core-data/src/main/java/com/example/hearablemusicplayer/data/network/MultiProviderApiAdapter.kt` 移入 `shared/src/commonMain/kotlin/com/hmp/data/network/`。

#### P3.2 重写 MultiProviderApiAdapter

**当前状态**：直接使用 OkHttp（Retrofit 已配置但未使用），Gson 序列化。

**迁移方案**：

1. **替换 OkHttp → Ktor Client**：
```kotlin
class KtorMultiProviderApiAdapter(
    private val httpClient: HttpClient
) {
    suspend fun callProvider(
        provider: AiProviderType,
        apiKey: String,
        requestBody: String  // JSON 字符串
    ): AiApiResult<String> {
        val url = when (provider) {
            AiProviderType.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
            AiProviderType.OPENAI -> "https://api.openai.com/v1/chat/completions"
            AiProviderType.CLAUDE -> "https://api.anthropic.com/v1/messages"
            AiProviderType.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
            AiProviderType.ERNIE -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
        }
        // Ktor 请求实现...
    }
}
```

2. **替换 Gson → kotlinx.serialization**：
   - 所有请求/响应 DTO（OpenAiStyleRequest, ClaudeRequest, QwenRequest, ErnieRequest 等）添加 `@Serializable` 注解
   - 替换 `gson.toJson()` → `Json.encodeToString()`
   - 替换 `gson.fromJson()` → `Json.decodeFromString()`

3. **平台特定 HttpClient Engine**：
   - `androidMain`：`OkHttp` engine（`ktor-client-okhttp`）
   - `iosMain`：`Darwin` engine（`ktor-client-darwin`）

#### P3.3 创建平台特定 HttpClient

**`shared/src/androidMain/kotlin/com/hmp/data/network/HttpClient.android.kt`**：
```kotlin
fun createHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) { json() }
        install(Logging) { level = LogLevel.BODY }
        defaultRequest {
            timeout { requestTimeoutMillis = 30_000 }
        }
    }
}
```

**`shared/src/iosMain/kotlin/com/hmp/data/network/HttpClient.ios.kt`**：
```kotlin
fun createHttpClient(): HttpClient {
    return HttpClient(Darwin) {
        install(ContentNegotiation) { json() }
        install(Logging) { level = LogLevel.BODY }
        defaultRequest {
            timeout { requestTimeoutMillis = 30_000 }
        }
    }
}
```

#### P3.4 验证

```bash
./gradlew :shared:build
```

---

## P4: core-data 迁移 — DI / 标签 / 存储 / 工具

**目标**：完成共享层剩余迁移，使 shared 模块完整可用。

### 步骤

#### P4.1 移动 Repository 实现和 Mapper

将以下文件移入 `shared/src/commonMain/kotlin/com/hmp/data/`：
- `repository/MusicRepositoryImpl.kt`
- `repository/PlaylistRepositoryImpl.kt`
- `repository/SettingsRepositoryImpl.kt`
- `repository/BackupFileRepositoryImpl.kt`
- `repository/Result.kt`
- `mapper/MusicMapper.kt`
- `mapper/PlaylistMapper.kt`

#### P4.2 处理 Repository 中的平台依赖

**MusicRepositoryImpl** 中需要处理：
- `Context` 参数 → 通过 expect/actual 注入（Android 用 `Context`，iOS 用无参）
- `MediaStore` 查询 → **抽象为 `DeviceMusicScanner` expect/actual**（见 P4.2a）
- `MediaMetadataRetriever` → 通过 `DeviceMusicScanner` 内部封装
- `Jaudiotagger` 读歌词 → expect/actual（见 P4.3）
- `PinyinSortKey` → expect/actual（见 P4.5）
- `Gson` → kotlinx.serialization
- `SecureStorage` → expect/actual（见 P4.4）

**SettingsRepositoryImpl** 中需要处理：
- `DataStore` 初始化 → 通过 expect/actual 提供创建函数
- `SecureStorage`（Android KeyStore）→ expect/actual（iOS 用 Keychain）

**BackupFileRepositoryImpl** 中需要处理：
- `Context` → 通过 expect/actual 的文件路径工具函数
- `Gson` → kotlinx.serialization

#### P4.2a 设备音乐扫描 expect/actual

`MusicRepositoryImpl` 的 `loadMusicFromDevice()` 和 `syncMusicFromDeviceIncremental()` 大量使用 `MediaStore`、`MediaMetadataRetriever` 等 Android API。需要将整个扫描逻辑抽象为平台特定实现：

**`shared/src/commonMain/kotlin/com/hmp/data/scanner/DeviceMusicScanner.kt`**：
```kotlin
expect class DeviceMusicScanner() {
    suspend fun scanAllMusic(): List<ScannedMusicFile>
    suspend fun scanIncremental(existingFiles: List<MusicExtraIdDate>): List<ScannedMusicFile>
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

**`shared/src/androidMain/.../DeviceMusicScanner.android.kt`**：
- 使用 `MediaStore.Audio.Media` 查询设备音乐文件
- 使用 `MediaMetadataRetriever` 获取元数据

**`shared/src/iosMain/.../DeviceMusicScanner.ios.kt`**：
- 使用 `FileManager` 扫描 Documents 目录
- 使用 `AVAsset` 获取元数据

#### P4.3 音乐标签解析 expect/actual

**`shared/src/commonMain/kotlin/com/hmp/data/tag/MusicTagParser.kt`**：
```kotlin
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

**`shared/src/androidMain/.../MusicTagParser.android.kt`**：
- 使用 `MediaMetadataRetriever` 获取基础信息
- 使用 `Jaudiotagger` 获取歌词

**`shared/src/iosMain/.../MusicTagParser.ios.kt`**：
- 使用 `AVAsset` 获取基础信息
- 使用 `AVAssetReader` 或第三方库获取歌词

#### P4.4 安全存储 expect/actual

**`shared/src/commonMain/kotlin/com/hmp/data/util/SecureStorage.kt`**：
```kotlin
expect class SecureStorageHelper() {
    suspend fun encrypt(plainText: String, key: String): String
    suspend fun decrypt(cipherText: String, key: String): String
}
```

**`shared/src/androidMain/.../SecureStorage.android.kt`**：
- 使用 Android KeyStore + AES-GCM（保留现有实现）

**`shared/src/iosMain/.../SecureStorage.ios.kt`**：
- 使用 iOS Keychain Services API

#### P4.5 拼音排序 expect/actual

**`shared/src/commonMain/kotlin/com/hmp/data/util/PinyinSortKey.kt`**：
```kotlin
expect fun stringToPinyinSortKey(s: String): String
```

**`shared/src/androidMain/.../PinyinSortKey.android.kt`**：
- 保留 Pinyin4j 实现

**`shared/src/iosMain/.../PinyinSortKey.ios.kt`**：
- 使用 `CFStringTokenizer` + `CFStringTransform(kCFStringTransformToLatin)` 将中文转为拼音

#### P4.6 DataStore KMP 配置

**`shared/src/commonMain/kotlin/com/hmp/data/datastore/PreferenceStore.kt`**：
```kotlin
expect fun createDataStore(): DataStore<Preferences>
```

**`shared/src/androidMain/.../PreferenceStore.android.kt`**：
```kotlin
actual fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { context.filesDir.resolve("player_preferences.preferences_pb").toPath() }
    )
}
```

**`shared/src/iosMain/.../PreferenceStore.ios.kt`**：
```kotlin
actual fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { NSFileManager.defaultManager.URLForDirectory(
            NSDocumentDirectory, NSUserDomainMask, null, true, null
        )!!.path + "/player_preferences.preferences_pb" }
    )
}
```

#### P4.7 配置 Koin DI 模块

**`shared/src/commonMain/kotlin/com/hmp/di/SharedModule.kt`**：
```kotlin
val sharedModule = module {
    // DataStore
    single { createDataStore() }

    // Database — getDatabaseBuilder() 是 expect/actual 函数，Android 端通过 Koin 注入 Context
    single { getRoomDatabase(getDatabaseBuilder()) }

    // Platform-specific helpers (expect/actual)
    single { DeviceMusicScanner() }
    single { MusicTagParser() }
    single { SecureStorageHelper() }

    // Network
    single { createHttpClient() }
    single { KtorMultiProviderApiAdapter(get()) }

    // Repositories (接口绑定实现类)
    single<MusicRepository> {
        MusicRepositoryImpl(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), // 9 DAO
            get(), // multiProviderApiAdapter
            get(), // deviceMusicScanner
            get(), // musicTagParser
            get()  // secureStorageHelper
        )
    }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get(), get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<BackupFileRepository> { BackupFileRepositoryImpl(get(), get()) }

    // Use Cases
    single { GetAllMusicUseCase(get()) }
    single { SearchMusicUseCase(get()) }
    single { LoadMusicFromDeviceUseCase(get()) }
    single { SyncMusicFromDeviceIncrementalUseCase(get()) }
    single { RemoveFromLibraryUseCase(get()) }
    single { RestoreToLibraryUseCase(get()) }
    single { GetDeletedMusicIdsGroupedByFolderUseCase(get()) }
    single { MusicLabelUseCase(get(), get()) }
    single { GetDailyMusicRecommendationUseCase(get(), get(), get()) }
    single { ManagePlaylistUseCase(get(), get()) }
    single { GeneratePlaylistUseCase(get(), get()) }
    single { UserSettingsUseCase(get()) }
    single { LyricsSettingsUseCase(get()) }
    single { CurrentPlaybackUseCase(get(), get(), get()) }
    single { PlaybackHistoryUseCase(get()) }
    single { GetUserUsageDataUseCase(get()) }
    single { TimerUseCase }
    single { ExportUserDataBackupUseCase(get(), get(), get(), get()) }
    single { ImportUserDataBackupUseCase(get(), get(), get(), get()) }
    single { GetBackupsUseCase(get()) }
    single { DeleteBackupUseCase(get()) }
}
```

**Android 端 Context 注入**：`getDatabaseBuilder()` 在 `androidMain` 中定义为 `expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>`，内部通过 `KoinJavaComponent.inject<Context>()` 获取 Context。Android 平台模块额外注册：

```kotlin
// shared/src/androidMain/kotlin/com/hmp/di/AndroidPlatformModule.kt
val androidPlatformModule = module {
    single<Context> { androidContext() }
}
```

#### P4.8 移动测试文件

将 `android/core-domain/src/test/` 下的测试文件移入 `shared/src/commonTest/`。

#### P4.9 验证

```bash
./gradlew :shared:build
./gradlew :shared:allTests
```

---

## P5: Android 端适配

**目标**：Android 端改为依赖 shared 模块，DI 从 Hilt 切换为 Koin（app/feature-ui 层），core-player 保留 Hilt。

### 步骤

#### P5.1 更新 Android 模块依赖

**`android/core-data/build.gradle.kts`**：
- 移除 Room、DataStore、Retrofit、Gson、OkHttp 依赖（已由 shared 提供）
- 保留 `implementation(project(":shared"))`
- 移除 Hilt（DI 已由 shared 的 Koin 提供）
- 仅保留 Android 平台特定的实现代码（如果有的话）

**实际上**：core-data 的所有实现已移入 shared，此模块可能变为空壳或直接删除。根据需要决定：
- 方案 A：删除 `android/core-data`，所有消费者直接依赖 `:shared`
- 方案 B：保留 `android/core-data` 作为 Android 平台特定的数据层扩展

**建议方案 A**（简化架构）。同时需要清理 `settings.gradle.kts` 中的 `include(":android:core-data")` 和 `include(":android:core-domain")`。

#### P5.1b 更新 core-player 依赖

**`android/core-player/build.gradle.kts`**：
- `implementation(project(":android:core-data"))` → `implementation(project(":shared"))`
- `implementation(project(":android:core-domain"))` → 移除（通过 shared 传递）
- **保留 Hilt**：core-player 的 `PlayerModule` 仅提供 `ExoPlayer` 实例（`@ServiceScoped`），不涉及 Repository 绑定，与 Koin 无冲突
- `MusicController` 的 UseCase 依赖（`CurrentPlaybackUseCase`、`PlaybackHistoryUseCase` 等）由 Koin 提供，Hilt 通过 `@Inject constructor` 自动解析。需要确保 Hilt 能访问到 Koin 注册的 UseCase — **解决方案**：在 `core-player` 的 Hilt 模块中添加桥接，或直接将 `MusicController` 也切换为 Koin 管理

**推荐**：将 `MusicController` 也切换为 Koin 管理（因为它已经依赖 Koin 提供的 UseCase），core-player 仅保留 `PlayerModule` 提供 `ExoPlayer`：

```kotlin
// shared/src/commonMain/kotlin/com/hmp/di/SharedModule.kt 中添加
single { MusicController(get(), get(), get(), get(), get()) }
// 参数: Context(通过 expect/actual), CurrentPlaybackUseCase, PlaybackHistoryUseCase, TimerUseCase, ManagePlaylistUseCase
```

这样 feature-ui 中的 PlaybackViewModel 也可以完全使用 Koin，彻底移除 Hilt。

#### P5.2 更新 feature-ui 依赖

**`android/feature-ui/build.gradle.kts`**：
- `implementation(project(":android:core-data"))` → `implementation(project(":shared"))`
- `implementation(project(":android:core-domain"))` → 移除（通过 shared 传递）
- 添加 Koin Compose 依赖：
  ```kotlin
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)
  ```
- 保留 Hilt（core-player 仍使用 Hilt，feature-ui 中的部分 ViewModel 可能仍需 Hilt）

**混合 DI 策略**：
- 共享层 UseCase → Koin 提供
- core-player 的 MusicController → Hilt 提供（core-player 保留 Hilt）
- ViewModel 同时使用 Koin（获取 UseCase）和 Hilt（获取 MusicController）

**替代方案**：将 core-player 的 DI 也切换为 Koin，彻底移除 Hilt。但这会增加 P5 工作量。

#### P5.3 更新 ViewModel 注入方式

**14 个 ViewModel 的迁移策略**：

对于仅注入 UseCase 的 ViewModel（如 LibraryViewModel、PlaylistViewModel 等）：
```kotlin
// 旧写法
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    ...
) : ViewModel()

// 新写法 — 使用 koinCompose 的 koinViewModel()
class LibraryViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    ...
) : ViewModel()

// 在 Composable 中获取
val viewModel: LibraryViewModel by koinViewModel()
```

对于注入 MusicController 的 ViewModel（如 PlaybackViewModel）：
```kotlin
// 如果 core-player 保留 Hilt
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val musicController: MusicController
) : ViewModel()
```

> **注意**：`MusicController` 本身注入了 UseCase（`CurrentPlaybackUseCase`、`PlaybackHistoryUseCase`、`TimerUseCase`、`ManagePlaylistUseCase`），这些 UseCase 由 Koin 提供。core-player 的 `PlayerModule` 仅提供 `ExoPlayer` 实例（`@ServiceScoped`），不涉及 Repository 绑定，因此 Hilt/Koin 混合使用不会产生冲突。

#### P5.4 更新 Application 类

```kotlin
// android/app/.../MusicApplication.kt
class MusicApplication : Application() {
    companion object {
        lateinit var instance: MusicApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startKoin {
            androidContext(this@MusicApplication)
            modules(sharedModule, androidPlatformModule)
        }
    }
}
```

#### P5.5 更新包名引用

所有 `import com.hearablemusic.player.domain.*` → `import com.hmp.domain.*`
所有 `import com.hearablemusic.player.data.*` → `import com.hmp.data.*`

#### P5.6 处理 feature-ui 中的 Gson 引用

`feature-ui/build.gradle.kts` 中有 `implementation(libs.gson)` 和注释 `// TODO: move to domain layer`。
- 将 Gson 使用迁移到 shared 层的 kotlinx.serialization
- 移除 feature-ui 的 Gson 依赖

#### P5.7 处理 Pinyin4j 引用

`feature-ui/build.gradle.kts` 中有 `implementation("com.belerweb:pinyin4j:2.5.1")`。
- PinyinSortKey 已在 P4.5 中移入 shared 的 expect/actual
- 移除 feature-ui 的 Pinyin4j 依赖

#### P5.8 验证

```bash
./gradlew :android:app:assembleDebug
./gradlew :android:app:assembleRelease
# 手动测试所有功能
```

**功能验证清单**：
- [ ] 音乐扫描和加载
- [ ] 播放控制（播放/暂停/上一首/下一首/快进/快退）
- [ ] 播放列表管理（创建/重命名/删除/排序/移除）
- [ ] AI 推荐（所有 5 个服务商）
- [ ] 搜索功能
- [ ] 歌词显示
- [ ] 设置页面（主题/音效/AI 配置/每日推荐策略）
- [ ] 用户主页和听歌统计
- [ ] 数据备份/恢复
- [ ] 音乐分享

---

## P6: iOS 端基础

**目标**：创建 iOS Xcode 项目，集成 KMP shared 框架，实现 AVPlayer 播放和基础 SwiftUI 界面。

### 步骤

#### P6.1 创建 Xcode 项目

```bash
cd ios
# 使用 Xcode 创建新项目，或手动创建项目结构
# Product Name: HMP
# Organization Identifier: com.hmp
# Interface: SwiftUI
# Language: Swift
# Minimum Deployment: iOS 16.0
```

#### P6.2 集成 KMP shared 框架

**方案**：使用 CocoaPods 集成 Gradle 构建的 KMP 框架。

1. 在 `ios/` 目录创建 `Podfile`：
```ruby
target 'HMP' do
  use_frameworks!
  platform :ios, '16.0'
  pod 'shared', :path => '../shared'
end
```

2. 运行 `pod install`

3. 在 Xcode 中打开 `HMP.xcworkspace`

#### P6.3 Koin iOS 初始化

**`ios/HMP/App/AppDelegate.swift`**：
```swift
import UIKit
import shared

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        KoinKt.doInitKoin()
        return true
    }
}
```

#### P6.4 AVPlayer 封装

**`ios/HMP/Player/PlayerService.swift`**：
```swift
import AVFoundation
import shared

@Observable
class PlayerService {
    private var player: AVPlayer?
    private var audioSession: AVAudioSession?

    var isPlaying: Bool = false
    var currentTrack: MusicInfo? = nil
    var currentTime: TimeInterval = 0
    var totalTime: TimeInterval = 0

    func setup() {
        audioSession = AVAudioSession.sharedInstance()
        try? audioSession?.setCategory(.playback)
        try? audioSession?.setActive(true)
    }

    func play(url: URL) {
        let item = AVPlayerItem(url: url)
        player = AVPlayer(playerItem: item)
        player?.play()
        isPlaying = true
    }

    func pause() { player?.pause(); isPlaying = false }
    func resume() { player?.play(); isPlaying = true }
    func seek(to time: TimeInterval) { player?.seek(to: CMTime(seconds: time, preferredTimescale: 600)) }
    func next() { /* 从队列获取下一首 */ }
    func previous() { /* 从队列获取上一首 */ }
}
```

**`ios/HMP/Player/AudioSessionManager.swift`**：
- 处理音频焦点（ interruptions）
- 耳机插拔事件
- 蓝牙连接

**`ios/HMP/Player/NowPlayingManager.swift`**：
- `MPNowPlayingInfoCenter` 配置
- `MPRemoteCommandCenter` 命令处理

#### P6.5 基础 SwiftUI 界面

**`ios/HMP/App/HMPApp.swift`**：
```swift
import SwiftUI

@main
struct HMPApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            MainTabView()
        }
    }
}
```

**`ios/HMP/Features/Common/MainTabView.swift`**：
```swift
struct MainTabView: View {
    var body: some View {
        TabView {
            LibraryView().tabItem { Label("音乐库", systemImage: "music.note.list") }
            PlayerView().tabItem { Label("播放", systemImage: "play.circle") }
            PlaylistView().tabItem { Label("列表", systemImage: "list.bullet") }
            SettingsView().tabItem { Label("设置", systemImage: "gearshape") }
        }
    }
}
```

#### P6.6 音乐扫描（iOS 端）

**`ios/HMP/Services/MusicScanner.swift`**：
```swift
import shared

class MusicScanner {
    func scanMusicFiles() async -> [Music] {
        // 使用 FileManager 扫描 Documents 目录
        // 通过 KMP shared 的 MusicTagParser 解析标签
        // 调用 shared 层的 Repository 存入数据库
    }
}
```

#### P6.7 验证

- Xcode 编译通过
- iOS 模拟器/真机可运行
- 能扫描并播放本地音乐文件
- 锁屏控制可用

---

## P7: iOS 端功能

**目标**：逐模块实现 iOS UI，功能对齐 Android 端。

### 步骤

#### P7.1 音乐库模块

| 页面 | 对应 Android | 功能 |
|------|-------------|------|
| `LibraryView` | HomeScreen | 首页：推荐、最近播放、快捷入口 |
| `GalleryView` | GalleryScreen | 全部歌曲列表（按歌曲/艺术家/专辑） |
| `SearchView` | SearchScreen | 关键词搜索 |
| `ArtistView` | ArtistScreen | 按艺术家浏览 |
| `AlbumView` | AlbumScreen | 按专辑浏览 |
| `SongDetailView` | SongDetailScreen | 歌曲详情 |

#### P7.2 播放器模块

| 页面 | 对应 Android | 功能 |
|------|-------------|------|
| `NowPlayingView` | PlayerScreen | 全屏播放界面（封面、进度、控制） |
| `LyricsView` | LyricsScreen | 歌词显示 |
| `QueueView` | PlaylistAreaView | 播放队列 |

#### P7.3 播放列表模块

| 页面 | 对应 Android | 功能 |
|------|-------------|------|
| `PlaylistListView` | PlaylistScreen | 播放列表管理 |
| `PlaylistDetailView` | PlaylistManageScreen | 列表内歌曲管理 |

#### P7.4 设置模块

| 页面 | 对应 Android | 功能 |
|------|-------------|------|
| `SettingsView` | SettingScreen | 通用设置 |
| `AISettingsView` | AIScreen | AI 服务商配置 |
| `AudioEffectView` | AudioEffectScreen | 音效调节 |
| `UserView` | UserScreen | 用户主页、听歌统计 |

#### P7.5 通用组件

| 组件 | 对应 Android | 功能 |
|------|-------------|------|
| 设计系统 | design/ | 颜色、字体、间距令牌 |
| 主题管理 | theme/ | 暗色/亮色主题切换 |
| 空状态/加载状态 | base/ | EmptyState, LoadingState |
| 对话框 | dialogs/ | 确认、输入、选择对话框 |

#### P7.6 验证

逐模块验证，每完成一个模块进行功能对比测试：

- [ ] 音乐库：扫描、浏览、搜索
- [ ] 播放器：播放控制、歌词、队列
- [ ] 播放列表：CRUD、排序
- [ ] 设置：主题、AI、音效、用户
- [ ] 通用：主题切换、空状态、对话框

---

## 风险与注意事项

### 高风险项

| 风险 | 缓解 |
|------|------|
| Room `@RawQuery` + `PagingSource` 在 KMP 模式下的兼容性 | 提前在 P2 验证，必要时重构查询方式 |
| `MusicRepositoryImpl` 大量使用 `Context`、`MediaStore` 等 Android API | 通过 `DeviceMusicScanner`（expect/actual）抽象扫描逻辑，`SecureStorageHelper`（expect/actual）替代 KeyStore 调用，`MusicTagParser`（expect/actual）替代 Jaudiotagger，彻底移除 `Context` 和 `Gson` 依赖 |
| Hilt + Koin 混合 DI 可能导致冲突 | 推荐将 `MusicController` 也切换为 Koin 管理，core-player 仅保留 `PlayerModule` 提供 `ExoPlayer`。core-player 的 `PlayerModule` 不涉及 Repository 绑定，无冲突风险 |
| iOS 端开发学习曲线 | P6 先搭建最小可运行框架，P7 按模块逐步实现 |

### 中风险项

| 风险 | 缓解 |
|------|------|
| Gradle + Xcode 构建集成 | 使用官方推荐的 CocoaPods 集成方案 |
| Pinyin4j 在 iOS 无替代 | 使用 Core Foundation 的 `CFStringTransform` |
| Jaudiotagger 歌词解析在 iOS 无替代 | 使用 `AVAsset` 或第三方 Swift 库 |

---

## 依赖关系图

```
P0 (骨架)
 └─→ P1 (domain)
       └─→ P2 (Room)
             └─→ P3 (网络)
                   └─→ P4 (DI/标签/存储)
                         └─→ P5 (Android 适配)
                               └─→ P6 (iOS 基础)
                                     └─→ P7 (iOS 功能)
```

每个阶段完成后必须验证通过才能进入下一阶段。

---

**最后更新**：2026-04-19
