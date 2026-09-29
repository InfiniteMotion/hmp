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
└── storybook/       孤儿模块：有 build 脚本但未被 settings.gradle.kts include
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

- **Room 2.8.3 KMP**：`AppDatabase` **version = 9**，`exportSchema = true`，18 实体 / 19 DAO。schema json 在 `shared/schemas/com.hmp.data.database.AppDatabase/1..9.json`。迁移是手写的 `MIGRATION_1_2 … MIGRATION_8_9`，在三端各自的 `DatabaseBuilder.{android,desktop,ios}.kt` **分别注册**（漏一端就是单端升级崩溃）。
- **三端都去掉了 destructive 兜底**（A4）：新增实体/字段忘了写迁移 = 线上硬失败，不是静默重建。**改 schema 必须同时补迁移 + 三端注册 + 重导 schema json。**
- 仓库接口 4 个：`MusicRepository`、`PlaylistRepository`、`SettingsRepository`、`BackupFileRepository`。实现**按平台三份镜像**（`*Impl.{android,desktop,ios}.kt`，基于 `MusicRepositoryBase.kt` 1245 行），`SettingsRepositoryImpl.{desktop,ios}` 各约 500 行。
- UseCase 共 22 个（music 10 / setting 6 / backup 4 / playlist 2）。
- **Ktor 只用于用户自填的 AI API**，无网络取曲。Android 走 OkHttp engine、iOS 走 Darwin engine。
- **DataStore** 存偏好；`SecureStorageHelper` 存 API Key。

---

## 八、已知地雷（写代码前必读）

以下都在 [TODO](TODO.md) §六 R39–R56 / §一 挂了账，但**不知道就会踩**：

1. **`testAll` 会静默空过。** `build.gradle.kts:93` 硬 `dependsOn(":shared-ui:desktopTest")`，而该源集**目录不存在** → `NO-SOURCE` 绿。真正的用例在 `androidHostTest`（6 文件）和 `commonTest`（3 文件）。`build.gradle.kts:121-122` 那句"desktopTest 源集目前为空（只有 androidHostTest，8 个文件）"本身也是过期口径。
2. **`maybeDepends` 在错目标下静默丢依赖。** `export HMP_BUILD_TARGET=desktop` 后跑 `./gradlew testAndroid` **必绿且零工作**（R52）。
3. **`Routes.kt` 有 30 个 NavKey，`HmpNavBackStack.kt` 只注册了 28 个 serializer**，漏的正是 `Routes.AI.AgentConfig` 与 `Routes.Settings.AgentMonitor`。该文件的注释自己写明"漏注册无编译期报错，仅在该 key 参与保存/恢复时运行时报错"。**新增 NavKey 必须同时补 serializer 和 `NavigationGraph` 的 `entry<>`**（R41）。`RoutesTest` 里手写 22 条 `is NavKey` 断言是恒真闸门，不会替你把关。
4. **DI 无图校验**（`checkModules`/`verify()` 全仓 0 命中）。Android 只 eager 解 `MasterAgent`、Desktop 只解控制器、iOS 纯懒 → **"进页面才崩"在 iOS 成立**。新加 `single` 后本机至少跑一次启动（R43）。
5. **`withTransaction` / `inTransaction` 全仓 0 命中。** 备份恢复是 `deleteAll()` 后逐条 insert，`ImportUserDataBackupUseCase` 串行 4 个仓库 restore 后 `catch → Result.failure`，无回滚无安全副本（R39）。**新写批量落库请自己包事务。**
6. **版本号读不到时静默回落到错误值。** `android/app` 兜底 `51000`/`"5.10.0"`，`desktop/app` 兜底 `"1.0.0"` 且 `dmgPackageVersion = "1"` 硬编码（R45、R30）。
7. **`copyToReleases` 找不到产物只 `println("!! Not found")` 不失败** → `./gradlew release` 可以"成功"地产出零个文件（本机侧；CI 的 `sync-release.py collect` 会失败）。
8. **`DeviceMusicScanner.android.kt:50` 是已知 bug**：`val context = android.app.Application()`（新建 Application 无 base context，`contentResolver.query` 必失败），且未注入 Context。**这条不在任何待办里**，动 Android 扫描前先确认它是否已被某处绕过。
9. **iOS 的扫描目录设置是空壳**：`SettingsRepositoryImpl.ios.kt` 的 `scanDirectoryConfig = flowOf(ScanDirectoryConfig())` 恒默认值、`saveScanDirectoryConfig` 是空函数体，`DeviceMusicScanner.ios.kt` 写死扫 Documents（R40）。
10. **歌词设置双存储**：播放页读 `@Deprecated` 的 legacy 扁平键，设置页写新的 per-component JSON，全仓唯一新模型消费者是 Android 悬浮歌词 → 设置页改字号，播放页静默不生效（R42）。真身在 `domain/setting/usecase/LyricsSettingsUseCase.kt`。
11. **iOS 密钥不是加密**：`SecureStorageHelper.ios.kt` 的 `simpleEncrypt` 实为 XOR，密钥文件却叫 `.aes_key`，且落在 `Documents/.keys`（与密文同目录），而 `project.yml` 开着 `UIFileSharingEnabled: true` → 沙箱可经"文件"App 直接拷出（R10）。Desktop 用硬编码 keystore 口令。三端密文互不可解。
12. **`androidMain` / `iosMain` 的实现测试覆盖为 0。** 107 个平台源集文件里 12 个 `*Impl*`，只有 2 个有同名测试（都在 `shared/src/desktopTest`）。`shared/src` 下只有 `commonTest` 与 `desktopTest` 两个测试源集，**没有 androidHostTest、没有 iosTest**。
13. **`shared_ios.podspec` 是被 track 的生成物**，Windows 会把 `spec.resources` 写成反斜杠路径，`.gitattributes` 没有 `*.podspec` 规则（R46）。
14. **没有任何静态闸门**：无 ktlint、无 detekt、无 `.editorconfig`、无 `core.hooksPath`。`DEVELOP.md:396-400` 声称"使用 ktlint 检查"是**失真的**。新代码靠自觉保持一致性。
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
| **架构实现审查（v7.3.0 待办 R39–R56 的证据出处）** | [docs/7_3/review-7.3-architecture.md](docs/7_3/review-7.3-architecture.md) |
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
