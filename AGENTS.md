# AGENTS.md

跨工具的项目速查，面向所有 AI 协作者（Claude Code / Qoder / Codex / Cursor / Copilot 等按 `agents.md` 规范读取本文件的工具）。

**本文件是 AI 协作者须知的唯一真源。** `CLAUDE.md` 只是指向这里的指针，不要往那边抄内容。

- 最新发布版本：**v7.2.2**（2026-09-28 发布）。产物：macOS arm64 DMG / Windows x86_64 MSI / Linux x86_64 DEB / Android APK+AAB + `SHA256SUMS.txt`；iOS 仅本机 Archive，不在 CI。
- **产品边界**：纯本地。不做在线/云同步、不引入账号、不做社交；只保留用户自填 API 的 AI 推荐。改动前如果会突破这条线，停下问用户。

---

## 一、硬约束（先读这段，违反代价最高）

1. **版本只改根目录 `release.toml`。** `gradle.properties`、iOS 三处（`project.yml` / `Info.plist` / `project.pbxproj`）、`site/js/config.js`、`site/index.html` JSON-LD、`shared-ios/.../Anchor.kt`、ROADMAP 与站点 changelog 条目**全部是 `scripts/sync-release.py` 的产物，禁止手改**。改完跑 `./gradlew syncVersion`。
2. **文档与代码冲突时以代码为准。** 本仓库的文档叙述与实物之间没有断言关系（只有版本派生点有）。`docs/7_3/review-7.3-architecture.md` §四 记录了 14 条已确认失真。
3. **行号坐标会过期。** 审查报告里的 `file.kt:123` 是当日工作树坐标，动手前先按符号名 grep。
4. **不要自己 `push` / 建 PR / 合并。** 发版前半程见 `skills/release-prep/SKILL.md`，其 ⑧ 步明确把这几件事交回用户。
5. **CI 既不跑单元测试、也不编译 iOS。** 唯一守门人是本机 `preflight`。这条决定了：你改完代码没有任何机器反馈，必须自己跑对任务（见下节）。
   > **2026-09-30 决议改向**：编译与测试是必要的，方针改为「**当前环境能跑就都跑**」——Linux runner 跑得到的编译/测试一律纳入 CI，iOS 编译只在 macOS runner 可用时跑、缺环境不阻塞合入。落地清单见 `TODO.md` §六 决议 ①（**该决议已暂缓，用户 2026-09-30：「CI 先放着」**）。**在本条落地之前，现状仍然成立**——别等 CI，自己跑。
6. **归档 `docs/archive/**` 只进不改**；`docs/7_3/review-7.3-architecture.md` 是带日期的审查证据，不要重写它的结论与行号。

---

## 二、常用命令

### 构建与运行

```bash
./gradlew :android:app:assembleDebug          # Android Debug
./gradlew :android:app:assembleRelease        # Android Release
./gradlew :desktop:app:run                    # Desktop 运行调试
./gradlew :desktop:app:packageDistributionForCurrentOS   # 当前 OS 安装包
./gradlew :shared:compileAndroidMain
./gradlew :shared-ui:compileKotlinDesktop
```

单端构建用环境变量 `HMP_BUILD_TARGET`（`android` / `desktop` / `all`，默认 `all`）只纳入对应模块，见 `settings.gradle.kts`。

### 测试 —— 本机必须用低内存包装脚本

本机 Android Studio 常驻占内存，默认 `-Xmx4096m + parallel` 会让 Gradle/Kotlin daemon 被 OS 静默杀死（报 `daemon disappeared`，典型停在 `> Task :shared:desktopTest`）。**用包装脚本代替 `gradlew`**：

```bash
./gradlew-lowmem.bat testCore        # Windows
./gradlew-lowmem testCore            # macOS / Linux / Git Bash
```

已固化的关键参数：Gradle daemon 堆 `-Xmx1024m` + Kotlin 编译器 `-Dkotlin.compiler.execution.strategy=in-process`。用 `1536m`/`2048m` 时编译能过但一 fork 测试 JVM 就被杀。`org.gradle.jvmargs` 是启动期属性，无法在 `build.gradle.kts` 里条件化覆盖，只能靠包装脚本。

| 改动范围 | 跑什么 |
|---|---|
| `shared` 的 Domain / Agent | `./gradlew-lowmem.bat testCore` |
| `shared-ui` 的 UI | `./gradlew-lowmem.bat testUi` |
| 改了公共 API | `./gradlew-lowmem.bat compileAll`（查下游破坏，比全量测试快得多） |
| 提交前 | `./gradlew-lowmem.bat testAll` |
| 发版前 | `./gradlew-lowmem.bat preflight` |

**跑 `shared-ui` 的测试要用 `testAndroidHostTest`，不是 `testDebugUnitTest`（该任务不存在）。**

```bash
./gradlew-lowmem.bat :shared-ui:testAndroidHostTest --tests "com.hearablemusic.player.ui.common.navigation.RoutesTest"
```

测试报告落在**被依赖的底层任务**目录（聚合任务不自己出报告）：`<模块>/build/reports/tests/<任务名>/index.html` 人看，`<模块>/build/test-results/<任务名>/TEST-*.xml` 机器读。任务 UP-TO-DATE 时沿用旧报告，要刷新加 `--rerun`。

### 发布

```bash
./gradlew syncVersion              # release.toml → 所有派生点
./gradlew preflight                # checkVersion + checkReleaseConsistency + testAll
./gradlew release                  # Android + Desktop（macOS 上含 iOS），产物进 releases/
./gradlew releaseAndroid | releaseDesktop | releaseIos
./gradlew copyAndroidDebug | copyDesktopJar
```

`python scripts/sync-release.py` 的子命令：`inspect` / `sync --write|--check` / `notes` / `version` / `assets` / `collect` / `mark-void`。

> ⚠️ **`sync --check` 只证明"抄对了"，不证明"写得对"**：派生点与 `release.toml` 一致，不代表 `release.toml` 里的口径符合实物。发版前读一遍 `sync-release.py notes` 的输出，逐条去仓库里找反证。

> 上游 CI 不用这些封装任务，而是直接调各模块底层任务并自行归集重命名产物。

### iOS

```bash
./gradlew :shared-ios:podspec && ./gradlew :shared-ios:generateDummyFramework
cd ios && pod install
./gradlew :shared-ui:compileKotlinIosSimulatorArm64
xcodebuild -workspace ios/HMP.xcworkspace -scheme HMP -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```

Xcode 26.6 需 iOS 26.5 模拟器运行时，缺失时先 `xcodebuild -downloadPlatform iOS`。

---

## 三、模块拓扑

```
HMP/
├── shared/          KMP 业务层（domain + data），com.hmp
├── shared-ui/       Compose Multiplatform UI + ViewModel，com.hearablemusic.player.ui
├── android/app/     Android 入口（3 个 Kotlin 文件）
├── android/core-player/  Media3 ExoPlayer 引擎
├── desktop/app/     Desktop 入口（无边框窗口、托盘、单实例）
├── desktop/core-player/  FFmpeg + JNA 自研引擎
├── ios/             iOS 原生壳（XcodeGen + CocoaPods，22 个 Swift 文件）
├── shared-ios/      iOS 聚合框架（shared + shared-ui → sharedIos）
└── storybook/       设计沙盒（2026-09-30 决议保留）：有 build 脚本但未被 settings.gradle.kts include，不在构建图内、不保证可编译
```

依赖关系：

```
:shared-ui ──▶ :shared
:shared-ui ──▶ :android:core-player   (androidMain 桥接)
:shared-ui ──▶ :desktop:core-player   (desktopMain 桥接)
:android:app / :desktop:app ──▶ :shared + :shared-ui
:android:core-player / :desktop:core-player ──▶ :shared
:ios ──▶ :shared-ios (CocoaPods，聚合 :shared + :shared-ui)
```

**分层**：UI（三端共享 `shared-ui` commonMain）→ ViewModel（`koinViewModel()` + StateFlow）→ Domain（UseCase + 领域模型，`shared/.../domain/`）→ Data（Repository + Room + Ktor，`shared/.../data/`）→ 播放引擎（各端）。

**DI**：全端 Koin 4.2.2（Android 已从 Hilt 迁走）。commonMain 的 `di/SharedModules.kt` 是 `sharedModule`，平台侧 `AndroidModules` / `DesktopModules` / `IosModules`，UI 侧 `UiKoinModule`（Android/Desktop）/ `IosUiKoinModule`（iOS）。iOS 在 `AppDelegate` 里 `installKoinIosWithSharedUi()`。**注意：`AuditLogViewModel` 未在任何 Koin module 注册**，靠 `remember { ... }` 构造。

---

## 四、跨平台机制

`expect`/`actual` 在文件级 **1:1:1 配对，三端无孤儿**。已知的 expect：`DeviceMusicScanner`、`MusicTagParser`、`SecureStorageHelper`、`stringToPinyinSortKey()`、`getRoomDatabase()`、`AppDatabaseConstructor`（无手写 actual，由 Room KSP 生成）、`DataStoreFactory`、`createHttpClient()` / `createJson()`、`currentTimeMillis()`、`rememberStatusBarsController()`、`platform/Synchronized.kt` 与 `Volatile.kt`（Android/Desktop 是 `actual typealias` 到 `kotlin.jvm.*`，iOS 是真正的 `actual annotation class`）。

平台差异在 UI 侧收口到 `shared-ui/src/commonMain/.../ui/platform/` 的接口：`PlaybackController`（137 行，约 17 个 StateFlow + 30 个方法，接口已冻结）、`AlbumArtPixelsLoader`、`PlatformServices`（`Share` / `FilePicker` / `Permission` / `MusicTagEdit` / `Haptic` / `FloatingLyrics` 的门面）。三端实现：`MusicControllerPlaybackAdapter` / `DesktopMusicControllerPlaybackAdapter` / `IosPlaybackController` + `IosPlaybackStateSink`。

**iOS 是"双桥"**：Kotlin 侧 `IosPlaybackController`（object 单例）持有 StateFlow 状态面，Swift 侧 `PlaybackBridge.swift` 把命令送进去、把 Observation 状态推给 `IosPlaybackStateSink`；跨边界用 `Any`/`String` 规避 `SharedUi` 前缀的重名类型。`IosAlbumArtPixelsLoader` 是纯 skiko 实现，不经 Swift。Swift 另注册 `MusicMetadataParser` / `ArtworkExtractor` 两桥进 `shared` 的 `MusicTagParser.ios.kt`。

**聚合框架**：`shared-ios` 把 `:shared` + `:shared-ui` 链成单个 static framework（baseName `sharedIos`），Swift 统一 `import sharedIos` —— 避免双静态框架的 duplicate symbol 与双动态框架的 Koin 全局分裂。**只编 `iosArm64` + `iosSimulatorArm64`**：`navigation3-ui` 没有 `ios_x64` 构件，Intel 模拟器走 Rosetta。`ios/HMP/project.yml` 未声明 app-extension target，`HMPNowPlaying`（Live Activity）扩展不在 XcodeGen 里。

图标资源在 `shared-ui` 的 composeResources，编译期 `when` 穷尽映射（`common/util/LabelExtensions.kt` 的 `LabelName.iconRes` 无 `else` 分支，新增 `LabelName` 会编译失败）；例外是 `agent/AgentVisuals.kt` 用运行时 `Map<String, DrawableResource>` 按 agentRole 取。

---

## 五、三端播放引擎的现实差异

改播放相关功能前先看这张表，否则会给某端写出静默空实现。

| 能力 | Android | Desktop | iOS |
|---|---|---|---|
| 引擎 | Media3 ExoPlayer（`MusicPlayService.kt`，845 行，裸 `Service` + `MediaSession.Builder`，**不是** `MediaSessionService`；`ForwardingPlayer` 伪造 `hasNext/Previous` 把系统指令路由回自己） | FFmpeg **子进程**（`ProcessBuilder`）+ `javax.sound.SourceDataLine`；JNA 只用于 Windows 窗口/DWM | AVPlayer（`PlayerEngine.swift` + `MusicPlayerController.swift`） |
| 时长探测 | Media3 | `ffmpeg -f null -` **会解码整首歌**再正则解析 | AVAsset |
| seek | 原生 | **杀进程重启 `-ss`** | 原生 |
| gapless | — | **无**（`preloadCurrentMusicInfo` 只预载元数据） | 无 |
| 音效 | `AudioEffectManager`（Equalizer/BassBoost/Virtualizer/Reverb） | **全是 `/* TODO */` 空桩**（`DesktopMusicController.kt:730-749`） | 部分 |
| 封面取像素 | Coil | Skia（`Image.makeFromEncoded`，min-edge/32 步长采样） | Skia |
| 标签解析 | JAudioTagger 3.0.1 | JAudioTagger，封面落盘 `~/.hmp/covers/` | AVAsset 元数据经 Swift 桥 |
| 锁屏/蓝牙/媒体键 | 有（通知 + `MediaStyleNotificationHelper`） | **无**，仅托盘菜单 | 有（`MediaSession/` 五文件 + Live Activity） |

- Android 的**音乐扫描在 `:shared` 不在 `:android:core-player`**（`DeviceMusicScanner.android.kt`，MediaStore + `MediaMetadataRetriever`）。
- Desktop 的 FFmpeg 供给全在 `desktop/app/build.gradle.kts`：`downloadFFmpeg`(L129) 按 SHA256 从 `InfiniteMotion/hmp` 的 `ffmpeg-binaries` Release 拉，优先离线 `ffmpeg-vendor/<os>/<arch>/`；`injectFFmpeg`(L248) 拷进 **jlink runtime image**。运行时 `resolveFfmpegPath()` 顺序：`-Dhmp.ffmpeg.path` → `java.home/bin/ffmpeg` → OS 常见目录 → PATH，每档都真跑一次 `ffmpeg -version` 以拒绝架构不符。
- ⚠️ `TargetFormat.AppImage` 是 jpackage 的 app-image 解包目录，**产不出 `.AppImage`**，该格式已移除，Linux 只发 DEB。
- ⚠️ macOS 的 app image **没有 `bin` 目录**（jlink 只出 `Contents/Home/{lib,legal,conf}`），`injectFFmpeg` 已改为归一化到 `java.home`。

---

## 六、AI Agent 子系统（改动量最大的地方）

**Agent 不是附加功能，它是本项目的重心**：`shared/.../domain/agent/` 有 78 个文件，超过其余 domain 包之和；加上 `shared-ui/.../ui/agent/` 约 161 个文件（含测试）。

- **编排**：`MasterAgent`（`runtime/MasterAgent.kt`，1759 行 / 68 方法，DI 里 20 个构造依赖的巨型 single）是门面，真正的 ReAct 引擎是 **`runtime/ReActLoop.kt`** —— `stepBudget`（Master 8 / Radio 仲裁 4），组合 `LlmCallExecutor`（一层）+ `ToolCallExecutor`（二层）+ `PolicyGuard` + `ConfirmGate` + `AuditLogPort` + `PresenceBus` + `StopSignal` + `TokenMeter`，返回 `AgentResult(text, stepsUsed, toolCalls, terminatedBy)`。
- **子 Agent**：`HelloSubAgent`（1844 行）、`RadioSubAgent`（1732 行，含 `RadioSession`）、`EnrichSubAgent`（刻意绕过 ReActLoop 走单次 LLM 调用，`trackMessages=false`），都在 `runtime/sub/`，契约见 `runtime/sub/shared/SubAgent.kt`。
- **工具**：`tool/spec/ToolNames.kt` 声明 **30** 个（播放控制/状态、歌单增删改排序、曲库检索与统计、标签读写、画像读写、`agent_budget`、`capability_status`），经 `ToolCatalog.createBaseToolRegistry()` → `ToolRegistry` 注册，`allLlmSpecs` 喂给模型。`ToolRegistryView` 是零消费的死抽象。
- **端口层（六边形）**：`domain/agent/port/` 12 个接口 —— `LlmTransport`/`LlmEvent`、`AgentMessageStore`、`AuditLogPort`、`AgentKeepAlivePort`、`PlaybackCommandPort`/`NowPlayingContextProvider`、`PlaybackObservationSink`、`Capability`、`ConfirmGate`、`TimeProvider`、`WallClock`、`ToolPermissionLevel`。
- **LLM**：单一 OpenAI 兼容契约 `LlmTransport.streamChat(): Flow<LlmEvent>`（`TextDelta | ToolCall | Usage | Completed | Failed`），全程 SSE 流式；实现在 `data/network/`（`OpenAiLlmTransport` + `MultiProviderApiAdapter` + `SseParser`）。`domain/enum/AiProviderType.kt` 预置 OpenAI / DeepSeek / Anthropic(`/v1/messages`) / Dashscope / 文心。API Key 经 `SecureStorageHelper` 加密后存 DataStore。
- **UI 消费**：`ChatViewModel` + `ChatAgentGateway`/`MasterChatGateway` 把 `ChatAgentEvent` 转成 `CompanionMessage` 气泡；另有 `AgentConfigScreen`（1658 行，**无 ViewModel**）、`AgentMonitorScreen`、`BottomFusionBar`、13 张 `HelloCard*`。
- **持久化**：Room 表 `agent_message`、`agent_task`、`agent_audit_log`、`token_ledger`（只存 `endpointHost`）、`hello_card_cache`、`hello_report_narrative`、`user_profile_evidence/portrait/narrative`、`forgotten_delivery`。
- **F10 语音会话已挂起**：无 `RealtimeVoiceTransport`、全仓无 WebSocket，只剩 `GlobalAgentConfig.voiceEnabled = false` 和 `AIScreen.kt` 里一个锁死的 `VoiceSection()`。**别顺手实现它**。

---

## 七、数据层

- **`music.isDeleted` 有两个成因**（一-2 B 批起）：用户主动移除、以及"这次扫描没见到这个文件"。可见性过滤仍只看 `isDeleted`，
  但**用户意图记在 `userInfo.removedByUser`**，扫描只读它、永不写它 —— 因此设置页的「已移除」列表（`getDeletedMusicIdsGroupedByFolder` → `MusicDao.getRemovedMusicIdAndPath`）只列用户移除的那些，
  缺文件的曲目不在里面。想恢复可见性走 `RestoreToLibraryUseCase`（同时清两位置）。
- **Room 2.8.3 KMP**：`AppDatabase` **version = 10**，`exportSchema = true`，18 实体 / 19 DAO。schema json 在 `shared/schemas/com.hmp.data.database.AppDatabase/1..10.json`。迁移是手写的 `MIGRATION_1_2 … MIGRATION_9_10`，统一收在 `AppDatabase.ALL_MIGRATIONS` 一个常量里，三端 `DatabaseBuilder.{android,desktop,ios}.kt` 都 `addMigrations(*AppDatabase.ALL_MIGRATIONS)` —— **不再有逐端手抄的名单**（2026-10-08 批 0 收口，漏一端曾是单端升级崩溃的来源）。v10 = 批一收口的一次迁移（13 条索引 + 删 `playlist_item.songUrl` + 后补的 `userInfo.removedByUser`；未发布期间并批，不再另开 v11），需求与判据的唯一真源是 `docs/7_3/v10-migration.md`。
- **迁移链的门禁**：`AppDatabaseMigrationTest` 跑逐环 + `1→10` 全链 + 链连续性；故意写坏一条 DDL 会红。新增版本必须**同时**改 `@Database.version` + 追加 `MIGRATION_{n-1}_{n}` 与 `ALL_MIGRATIONS` + 重导 schema json + 同步测试里的 `latestSchemaVersion`，少一处都有断言红（v10 施工时实测过这条红法）。
- **三端都去掉了 destructive 兜底**（A4）：新增实体/字段忘了写迁移 = 线上硬失败，不是静默重建。
- **降级有守卫，但没有 Room 回调**（一-7）：`AppDatabaseGuard` 在 build 之前探 `PRAGMA user_version`，库比代码新时记 error 日志 + 抛 `DatabaseTooNewException`，三端启动路径先 `probe` 挂状态、`AppRoot` 在解析任何 DAO 之前分流到提示页。别改回 `RoomDatabase.Callback`：Room 的版本校验发生在建连接过程中，`onOpen` 到不了，挂了等于没挂。代码版本读的是 `AppDatabase.CODE_SCHEMA_VERSION`（Room 不把注解版本暴露成常量），**bump 版本要同时改 `@Database.version` 与它**，迁移测试断言三者一致。
- **改 schema 必须同时补迁移（并追加进 `ALL_MIGRATIONS`）+ 重导 schema json。** 三端注册现在由常量结构性保证，不必手工三处同步。
- 仓库接口 4 个：`MusicRepository`、`PlaylistRepository`、`SettingsRepository`、`BackupFileRepository`。实现**按平台三份镜像**（`*Impl.{android,desktop,ios}.kt`，基于 `MusicRepositoryBase.kt` 1245 行），`SettingsRepositoryImpl.{desktop,ios}` 各约 500 行。
- UseCase 共 22 个（music 10 / setting 6 / backup 4 / playlist 2）。
- **播放时长的单一口径**（一-3 C3）：`PlaybackHistory.playDuration` = **本次会话累计实听毫秒**（不含 seek 空档，与 `listeningDuration` 的日累计同语义）—— 不是引擎当前位置，也不是曲目元数据时长。三端各自的结算点算的就是这一个数，新增播放入口时别顺手改回另外两种。
- **歌单的两个"看不见"契约**（一-4 A2 / A3）：① `PlaylistRepository.reorderPlaylistItems` 返回 `Boolean`，校验在 `PlaylistItemDao.reorderAndRefresh` 里（不在仓库、也不在 UseCase —— Agent 工具绕开 UseCase 直连仓库），`false` = 入参不是**当前可见曲目**（`music.isDeleted = 0`）的完整排列，此时一个字都没写；新增重排入口别绕过它。② `playlist.songCount` / `totalDurationMs` 是缓存列，**曲目可见性变了也要重算** —— `removeFromLibrary` / `restoreToLibrary` / 扫描的"没扫到"分支都调 `refreshStatsForPlaylistsContaining`，新增软删入口时忘了它就会"标题 10 首、列表 9 首"。封面则相反：`fillCoverFromFirstItemIfAbsent` 只在歌单没有封面时回填，手动设过的不再被首曲覆写。
- **系统歌单只剩"按 id"这一条路**（一-4 B1 / B2）：`removePlaylist(name)` 已从 DAO / 仓库 / UseCase 整条删掉（它绕过保护，还会级联清空用户的心动 / 最近播放），只有 `getPlaylistByName` 留着；启动自愈遇到悬空 id 时**接管同名行**，不删不建。删除保护是 `ManagePlaylistUseCase.removePlaylistById` 返回 `false`，**不是抛异常**（抛过一次，Android 上删"心动"即崩溃），谓词只在 `domain/playlist/SystemPlaylists.kt` 一份，Agent 的 `playlist_delete` 也调它。
- **`playlist_item.itemOrder` 是用户资产**：领域 `PlaylistItem` 现在带 `itemOrder`，导出查询带 `ORDER BY playlistId, itemOrder`，恢复时按它排序后重编号（旧快照全 0 会撞 `UNIQUE(playlistId,itemOrder)`，所以不"原样写回"）。别再往三端 Impls 里各写一份落库逻辑。
- **播放来源是闭集**：取值真源 `shared/.../domain/music/PlaybackSources.kt`（以 Android 已发布的拼写为准，库里已有历史数据，改名会让同一来源在饼图里裂成两条）。⚠️ iOS 侧是 **Swift 里复制的一份**（`MusicPlayerController.swift` 的 `enum PlaybackSource`）—— Kotlin `object` 的导出名在本仓库没有已验证先例，而 CI 不编译 iOS，猜错就是一次真机构建失败；**改取值要两头对**。
- **Ktor 只用于用户自填的 AI API**，无网络取曲。Android 走 OkHttp engine、iOS 走 Darwin engine。
- **DataStore** 存偏好；`SecureStorageHelper` 存 API Key。

---

## 八、已知地雷（写代码前必读）

以下都在 [TODO](TODO.md) §六 R39–R56 / §一 挂了账，但**不知道就会踩**：

1. **`shared-ui` 的测试在两个源集，都要挂。** `androidHostTest`（94 例）+ `desktopTest`（42 例，2026-10-08 才把 `src/desktopTest/` 目录建出来；`build.gradle.kts` 早先就声明了该源集、一直缺目录 → `:shared-ui:desktopTest` 长期 NO-SOURCE 静默绿）。`commonTest` 的用例会同时进这两个目标。`testAll`/`testUi` 现已同时挂两者；无 Android SDK 时可用 `testUiDesktop`（现在有真内容）。同类坑已一并处理：`desktop/app` 的 `desktopTest` 源集也是只声明没有目录，现已建出并放 `SingleInstanceGuardTest`（单实例锁 4 例）。**教训**：`maybeDepends`/`dependsOn` 挂了某个 test 任务不等于它有内容 —— 判据是 `<模块>/build/test-results/<任务>/TEST-*.xml` 里有没有用例。
   - **`PlaylistViewModel` 在 `shared-ui` 的测试源集里构造不出来**：它的 `init` 会调 `initializeDefaultPlaylists()`，那里解析 compose 资源名 `getString(Res.string.heart)`，而该源集没接 Robolectric —— 实跑报 `Method getSystem in android.content.res.Resources not mocked`。要写 VM 级判据，先按 `docs/7_3/domain/D3.md` D3-06 的退路把规则抽成纯函数（`userVisiblePlaylists`），或给该源集配资源解析。
2. **`maybeDepends` 在错目标下静默丢依赖。** `export HMP_BUILD_TARGET=desktop` 后跑 `./gradlew testAndroid` **必绿且零工作**（R52）。

3. **新增 NavKey 要同时动三处**：`Routes.kt` 声明、`HmpNavBackStack.kt` 的 `subclass` 注册、`NavigationGraph.kt` 的 `entry<>`。漏 serializer 过去**无编译期报错**，只在该 key 参与保存/恢复时运行期崩（"进页面转一圈就崩"），而 `RoutesTest` 那条手写 22 条 `is NavKey` 断言是恒真的、不把关。2026-10-08（三-1）起这道关由 `shared-ui/src/androidHostTest/.../NavRegistrationGateTest.kt` 守着：反射遍历每个 NavKey 做多态往返 + `entry` 覆盖比对，漏一处即红，新增路由自动进闸门。4 个 Tab key（`Main.Home/Gallery/List/User`）没有 `entry<>`，由 `MainShell` 的 HorizontalPager 承载 —— 它们在那个测试里是显式豁免名单，别当漏注册去补。
4. **DI 图校验：3 条，且只覆盖"必传依赖"。** `shared/src/desktopTest/.../KoinGraphVerificationTest.kt`（领域/数据层）+ `shared-ui/src/desktopTest/.../DesktopKoinGraphVerificationTest.kt`（桌面完整装配）+ `shared-ui/src/androidHostTest/.../AndroidAppKoinGraphVerificationTest.kt`（Android 完整装配）。都用 koin-test `verify()` 静态校验，**不实例化定义**（`checkModules` 会实例化 → 等于打开你本机 `~/.hmp` 的真库与 DataStore）。两个必须知道的限制：① **带默认值的构造参数被当成可缺省** —— 实测删掉 `single { PresenceBus() }` 三条全绿，而 `MasterAgent` 装配处写的是 `chatPresenceBus = get()`（形参 `PresenceBus? = null`），运行期照样抛；② iOS 装配任何本机源集都拼不出，`MainActivity` 里 `loadModules` 动态注册的 `PlatformServices` 也看不见。所以新加 `single` 除了跑校验还要本机跑一次启动（R43）。
5. **批量写的事务要靠你自己包，没有闸门替你查。** 2026-10-08（一-2 A 批）之后这条不再是"全仓 0 事务"：`domain/backup/TransactionRunner.kt`（实现在 `data/database/RoomTransactionRunner.kt`）把导入的四条 restore 收进单个 Room 事务，导入前必先落一份 `pre-restore-*.json` 安全副本；歌单的增删改排序收在 `PlaylistItemDao` 的 `@Transaction` 写族里。**仍然没有静态检查**——新写跨表批量落库要么走这些现成入口，要么自己包 `useWriterConnection { deferredTransaction }`；而且**设置那条走 DataStore，进不了 Room 事务**（它被排在事务块的最后，就是为了把不对称窗口缩到"DataStore 自己写失败"）。
    - v10 那条唯一索引的崩溃后果已在 一-2 收口：重排走 `PlaylistItemDao.reorderAndRefresh`（两阶段：先整体取负再落终值），添加侧 `getMaxOrder` 已删、取号并进 `insertWithNextOrder` 一条语句。
    - **设备扫描落库**（2026-10-09，一-2 B 批）同样收了口：三端各一份的「`deleteAll()` ×3 再分批重灌」合成 `MusicRepositoryBase.persistScannedLibrary` 一份，跑在单个事务里，语义是按 id 的 upsert。
      平台侧现在只剩 `performMusicScan()`（"设备上有哪些歌"）—— **别再往三端各抄一遍落库**；新增的可注入点就是那个 `protected abstract`。
6. **版本号读不到时静默回落到错误值。** `android/app` 兜底 `51000`/`"5.10.0"`，`desktop/app` 兜底 `"1.0.0"` 且 `dmgPackageVersion = "1"` 硬编码（R45、R30）。
7. **`copyToReleases` 找不到产物只 `println("!! Not found")` 不失败** → `./gradlew release` 可以"成功"地产出零个文件（本机侧；CI 的 `sync-release.py collect` 会失败）。
8. **`DeviceMusicScanner.android.kt:50` 是已知 bug**：`val context = android.app.Application()`（新建 Application 无 base context，`contentResolver.query` 必失败），且未注入 Context。**这条不在任何待办里**，动 Android 扫描前先确认它是否已被某处绕过。
9. **iOS 的扫描目录设置是空壳**：`SettingsRepositoryImpl.ios.kt` 的 `scanDirectoryConfig = flowOf(ScanDirectoryConfig())` 恒默认值、`saveScanDirectoryConfig` 是空函数体，`DeviceMusicScanner.ios.kt` 写死扫 Documents（R40）。
10. **歌词设置双存储**：播放页读 `@Deprecated` 的 legacy 扁平键，设置页写新的 per-component JSON，全仓唯一新模型消费者是 Android 悬浮歌词 → 设置页改字号，播放页静默不生效（R42）。真身在 `domain/setting/usecase/LyricsSettingsUseCase.kt`。
11. **iOS 密钥不是加密**：`SecureStorageHelper.ios.kt` 的 `simpleEncrypt` 实为 XOR，密钥文件却叫 `.aes_key`，且落在 `Documents/.keys`（与密文同目录），而 `project.yml` 开着 `UIFileSharingEnabled: true` → 沙箱可经"文件"App 直接拷出（R10）。Desktop 用硬编码 keystore 口令。三端密文互不可解。
12. **`androidMain` / `iosMain` 的实现测试覆盖为 0。** 107 个平台源集文件里 12 个 `*Impl*`，只有 2 个有同名测试（都在 `shared/src/desktopTest`）。`shared/src` 下只有 `commonTest` 与 `desktopTest` 两个测试源集，**没有 androidHostTest、没有 iosTest**。
13. **`shared_ios.podspec` 是被 track 的生成物**，Windows 会把 `spec.resources` 写成反斜杠路径，`.gitattributes` 没有 `*.podspec` 规则（R46）。
14. **没有任何静态闸门**：无 ktlint、无 detekt、无 `.editorconfig`、无 `core.hooksPath`。`DEVELOP.md:406` 现已如实写明这一点（方案仍在 `docs/ktlint-integration.md`，暂缓）。**2026-09-30 订正**：本条原称"`DEVELOP.md:396-400` 声称使用 ktlint 属失真"，该指控已随 DEVELOP 改写而失效——现在失真的是旧说法本身（独立复核见 `docs/7_3/domain/X.md` X-07 附加 doc-truth）。新代码靠自觉保持一致性。
15. **设计 token 是自愿制**：`.dp` 字面量 1444 处 vs `dimens.*` 仅 209 处；硬编码 hex 色 53 处；卡片两套并存（`HMPCard` vs 裸 `Card(`/`Surface(`）；336 个 `@Composable` 只有 81 个带 `modifier: Modifier`（R49）。

---

## 九、技术栈与版本

真源见 `gradle/libs.versions.toml` 与 `release.toml`，下表是快照：

| 项 | 值 |
|---|---|
| 应用版本 | 7.2.2（versionCode 72002） |
| Kotlin | 2.3.21 |
| AGP | 9.1.1 |
| Compose Multiplatform | 1.11.1 |
| Gradle | 9.x |
| JDK 工具链 | 21（Desktop jpackage 要求 Gradle Daemon 跑在 JDK 21） |
| Android SDK | compileSdk **37**，targetSdk **37**，minSdk 33 |
| Koin / Ktor / Room | 4.2.2 / 3.1.1 / 2.8.3 |
| iOS 部署目标 | 26.3（应用）/ 16.0（shared 的 CocoaPods） |

**包名**：Android `com.hearablemusic.player` ／ Shared(KMP) `com.hmp` ／ iOS `com.hearablemusic.HMP`。

**自定义 Gradle 任务共 27 个**（根 24 + `desktop/app` 的 `downloadFFmpeg`/`injectFFmpeg` + `shared` 的 `copyIconsToIos`），分布在 3 个构建脚本；`tasks --group release` 只显示 group=release 的 **10** 个。验证类 7（`testAll`/`testCore`/`testQuick`/`testUi`/`testUiDesktop`/`testDesktop`/`testAndroid`）、编译类 5（`compileAll`/`Core`/`Ui`/`Desktop`/`Android`；Android 侧不暴露 `compile*`，用 `assembleDebug` 代替）、发布类 10、清理类 2（`cleanReleases`、`cleanOrphans` 只列不删）、FFmpeg 2、iOS 图标 1（`copyIconsToIos` 源目录不存在，当前空转）。


**分支策略**（权威定义见 [docs/spec/hmp-release.md](docs/spec/hmp-release.md) §4）：
- `master`：已发布版本，**保护分支**，仅经 `release/X.Y.Z` 的 PR 合入
- `feature/<线>`：**长期开发线，一条线一个分支**（实例：`feature/agent-build`、`feature/site-sync`、`feature/music-tag-edit`）。直接在其上按阶段族提交（一族一笔），不为一两个改动另开分支
- `fix/*`：小修（修 bug、改配置、改 commit message）
- `release/X.Y.Z`：**发版集成分支，只在发布窗口出现** —— 从 `master` 拉出、把开发线合进来、PR 回 `master`，合并后远程删除
> ⚠️ **两个方向都别搞混**：
> ① **开发线不要取名成 `release/*`**。`pr-check.yml` 的 version job 判据是 `startsWith(github.head_ref, 'release/')`，一旦名字以 `release/` 开头，`checkVersion` 就会跑，而开发线不 bump 版本号 → 必红（这正是它被设计成只对 release 跑的原因）。
> ② **`release` 分支名必须三段式**（`release/7.2.2`，不是 `release/7.2`）：同一段逻辑把 `${HEAD_REF#release/}` 与 `sync-release.py version` 做**全等比较**，两段式会被硬拦。
> **无长期存活的 develop-\* 分支**：按平台拆的 `develop-android` / `develop-site` / `develop-ios` 三分支模式（v6.0 起提出）**从未落地，任何时候都不存在**。

**发版链**：开发线 `feature/<线>` 合入三段式的 `release/X.Y.Z` → 改 `release.toml` → `./gradlew syncVersion` → **本机** `./gradlew-lowmem.bat preflight` → 开 PR 跑 Pre-release Check → 合并触发 `release.yml` 自动发布。

**CI 两个 workflow 都不含版本字面量与文案**，一律调脚本：`pr-check.yml`（合入前答"能不能合"：`version` 只在 `release/*` 跑、`ffmpeg-assets` 校 SHA256 与 Mach-O/ELF/PE 真实架构、`release-info` 对所有非 draft PR 跑 `sync --check` + 预渲染 Notes、`preflight` 汇总结论表）+ `release.yml`（合并后答"怎么发"：validate → 四端并行 → 齐全断言 + SHA256SUMS → tag + Release → 部署 `site/`；`workflow_dispatch` 勾 `dry_run` 会在不发布的前提下跑完构建与断言并把正文打进日志）。

---

## 十、文档索引

| 想知道什么 | 去哪儿 |
|---|---|
| 项目是什么、怎么装怎么用 | [README.md](README.md) |
| **版本历史与功能状态（单一事实来源）** | [ROADMAP.md](ROADMAP.md) |
| 现在要做什么、优先级 | [TODO.md](TODO.md) |
| 技术架构、模块划分、开发流程 | [DEVELOP.md](DEVELOP.md) |
| **AI 协作者须知（本文件）** | [AGENTS.md](AGENTS.md) |
| 版本号规范 / 真源分层 / 发版清单 / CI 与产物现状 | [docs/spec/hmp-release.md](docs/spec/hmp-release.md) |
| **发版工序与判据（agent 执行发版照它走）** | [skills/release-prep/SKILL.md](skills/release-prep/SKILL.md) |
| **v7.3.0 施工基线（九域 + 横切线，唯一工作分解入口）** | [docs/7_3/domain-baseline.md](docs/7_3/domain-baseline.md) → `docs/7_3/domain/D{1..9}.md` / [X.md](docs/7_3/domain/X.md)、[docs/7_3/v10-migration.md](docs/7_3/v10-migration.md)、施工条目 `docs/7_3/taskbook/` |
| **v7.3.0 施工计划（顺序真源，已评审）** | [docs/7_3/plan.md](docs/7_3/plan.md)（34 个工作包 / 判据落点 / 验收定义 / **8 条决策记录** / 里程碑） |
| 架构实现审查（2026-09-28，**历史证据，非施工入口**） | [docs/7_3/review-7.3-architecture.md](docs/7_3/review-7.3-architecture.md)（TODO §六 R39–R56 出处）、[review-7.3-code.md](docs/7_3/review-7.3-code.md) |
| 设计系统（色彩/字体/间距/组件） | [docs/spec/hmp-design.md](docs/spec/hmp-design.md) |
| 日志门面 `HmpLog` / `LogTag` 规范 | [docs/spec/hmp-log.md](docs/spec/hmp-log.md) |
| Room KMP 跨平台配置 | [docs/room-kmp-setup.md](docs/room-kmp-setup.md) |
| Agent 体系设计总纲（历史存档，只进不改） | [docs/archive/7_x/7_2/design/agent.md](docs/archive/7_x/7_2/design/agent.md) |
| v7.2.0 合入前审查 + 处置记录 | [docs/archive/7_x/7_2/review-7.2.md](docs/archive/7_x/7_2/review-7.2.md) |
| **完整索引与各文档职责** | [docs/README.md](docs/README.md) |

三大方向：**A** iOS KMP UI 重写（v7.1.0 已完成）／ **B** AI 功能 Agent 化（F1–F14 代码随 v7.2.0 合入、产物随 v7.2.1 分发，**F10 挂起**）／ **C** 播放功能增强（**C1–C9 未排期**）。

---

## 十一、维护本文件

**改本文件 = 改所有 AI 协作者的行为**，所以：

- 正文只放这一份。`CLAUDE.md` 是 `@AGENTS.md` 导入 + Claude 专属差异（Skill 清单、`.claude/settings.local.json`），**不要往它抄内容**；`DEVELOP.md` 讲架构细节、`README.md` 面向用户，也别把这三段搬过去。
- 新增/移动/删除文档时，逐条过 [docs/README.md](docs/README.md) 的**「搬家清单」**（那是该清单的唯一真源，本文件不复述）。清单第 3 条要特别留意：源码与 Swift 注释里可能有 `CLAUDE.md` / `docs/...` 的字面路径串，不在 md 链接检查覆盖范围内，只能按前缀全仓 grep。
- **不要在散文里写硬计数**（文件数、类数、包数、报告用例数）—— 它们必然失真，属 `TODO.md` 的 **R54**「硬计数改脚本生成」的对象。本文件里出现的计数是 2026-09-29 的快照，别当断言用。
- 版本号字面量是叙述性快照，真值由 `release.toml` → `syncVersion` 同步；「最新发布版本」这句至今**没有任何机器核对**（R36 / D-7），改版本时记得手动带上它。
