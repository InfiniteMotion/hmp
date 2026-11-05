# 横切线 X（构建 / CI / 发版 / 依赖 / 文档真值）

> 状态：本轮独立重读产物（2026-09-30 工作树，`feature/architecture-hardening` = v7.2.2 已发布代码）。
> 纪律：每条结论来自本会话 `grep` / `Read` 的 `file:line`；未继承 `review-7.3-architecture.md` / `review-7.3-code.md` / `TODO.md` 的编号与结论。计数仅作量级、不作判据。

---

## 1. 工程面清单 × 现状

| 工程面 | 现状 | 来源（亲验） |
|---|---|---|
| CI 闸门（pr-check / release） | 部分 | `.github/workflows/pr-check.yml`、`release.yml`（不跑单测、不编 iOS，设计如此但需明示） |
| 版本真源 → 派生点 | 应有 | `release.toml` + `scripts/sync-release.py`（L229–275、L278–297、L383–393）+ `pr-check.yml:152` `sync --check` |
| 版本读取回落 | 部分（危险默认） | `build.gradle.kts:16-17`、`android/app/build.gradle.kts:43-44`、`desktop/app/build.gradle.kts:332/357` |
| DI 模块图校验 | 未做 | 全仓 grep `checkModules\|verify(\|koin.verify` 源码 0 命中（仅 docs/TODO） |
| 导航 serializer 注册 | 部分（缺 2） | `Routes.kt` 30 个 NavKey vs `HmpNavBackStack.kt` 28 个 subclass |
| 测试覆盖工程面 | 部分（androidMain/iosMain 0） | 测试源集：`shared/{commonTest,desktopTest}`、`shared-ui/{commonTest,androidHostTest}`、`android/app/src/androidTest`、`desktop/core-player/src/desktopTest`；无 androidMain/iosMain 专用源集 |
| 静态风格闸门 | 未做 | 无 `.editorconfig`（Glob `**/.editorconfig` 无）、`*.gradle.kts` 无 detekt/ktlint 插件；`docs/ktlint-integration.md` 为「暂缓」方案 |
| 依赖矩阵一致性 | 部分 | `gradle/libs.versions.toml` 守护 KMP/Android/Desktop；iOS（XcodeGen + CocoaPods）不在其内 |
| storybook 模块 | 空壳（孤儿） | `settings.gradle.kts:36-65` 无 `include(":storybook")`；`build.gradle.kts:302-315` 注释说明已移除 |
| 文档真值 | 部分 | `DEVELOP.md:406-408` 已自纠「无机械化风格闸门」；`AGENTS.md` §八.14 描述与之相反（滞后） |

---

## 2. 目标状态

- **CI**：pr-check 与 release 维持「不跑单测、不编 iOS」的硬约束（与 `AGENTS.md` §一.5 一致），但文档须明确：CI 只守「版本自洽 / 资产可用 / 派生点一致 / 打包成功」四件事，`preflight` 才是唯一带测试的守门人。
- **版本真源**：`release.toml` 唯一真源不变；9 处派生点全部由 `sync-release.py` 写、`sync --check` 读；派生点读不到版本时应 fail-fast 而非回落默认串。
- **DI**：`:shared:desktopTest` 接 `koin-test` 的 `checkModules`，删任意 `single` 即红。
- **导航**：NavKey 与 serializer 注册 1:1 可校验；新增路由须三处（Routes / HmpNavBackStack / NavigationGraph）同步，漏一即测试红。
- **测试面**：androidMain/iosMain 平台实现至少有对应测试源集或 commonTest 覆盖；`testAll` 不再含 NO-SOURCE 空壳依赖。
- **静态闸门 / storybook**：二选一——要么启动 `docs/ktlint-integration.md` 方案并接 CI，要么从仓库删除 storybook 孤儿模块；不长期「暂缓但留代码」。

---

## 3. 本轮发现

### X-01 · CI 不跑单测、不编 iOS（亲验）
- **现状**：`pr-check.yml` jobs：`version`（L65 `./gradlew checkVersion checkReleaseConsistency`）、`ffmpeg-assets`（L126 跑 `check-ffmpeg-assets.py`）、`release-info`（L152 `sync --check` + notes）、`preflight`（L164 仅汇总）。`release.yml` jobs：`validate`（L63 同两任务）、`build-android`（L104 `assembleRelease`）、`build-desktop-{macos,windows,linux}`（L152/204/251 `packageDistributionForCurrentOS`）、`release`、`deploy-site`。**全仓无任何 `test` 任务、无 iOS 编译 job**。
- **影响**：代码改动无机器反馈；CI 只守版本/资产/派生点/打包。
- **动作**：~~维持「唯一守门人本机 `preflight`」硬约束；在 AGENTS/CI 注释里显式写明「CI 不跑单测、不编 iOS」。~~ **2026-09-30 用户决议改向**：编译与测试是必要的，按**「当前环境能跑就都跑」**实现——Linux runner 上跑得到的编译与测试一律纳入（`HMP_BUILD_TARGET=desktop ./gradlew compileAll`、`:android:app:assembleDebug`、以及不依赖 Robolectric 的测试任务）；**iOS 编译只在 macOS runner 可用时跑，缺环境不阻塞合入**。落地前本节现状（CI 无编译无测试）仍然成立。
- **判据**：故意提交一个语法错的 PR 在 `verify` 类 job 上变红；iOS 相关 job 在无 macOS runner 时**跳过而非失败**。落地清单见 `TODO.md` §六 决议 ①。

### X-02 · 版本真源 release.toml + 9 派生点由脚本唯一写（亲验）
- **现状**：`release.toml` 是唯一真源。`scripts/sync-release.py` 的 `overwrite_steps`（L229–275）写 7 个文件：`gradle.properties`（`hmp.versionCode`/`hmp.versionName`，L234–237）、`site/js/config.js`（`version`+`released`，L239–245）、`site/index.html`（JSON-LD `softwareVersion`，L247–249）、`shared-ios/.../Anchor.kt`（`SHARED_IOS_FRAMEWORK_VERSION`，L251–255）、`ios/HMP/project.yml`（2 处，L257–261）、`Info.plist`（L263–266）、`project.pbxproj`（`MARKETING_VERSION`×N，L268–274）；追加式 2 处：`ROADMAP.md` + `site/changelog.html`（L278–297、L383–389）= **9 派生点**。`pr-check.yml:152` 跑 `sync --check` 核对一致。
- **影响**：手改派生点会被 `sync --check` 拦下；派生点一致性有 CI 防线。
- **动作**：维持；禁止手改派生点，改版本只动 `release.toml` 后 `./gradlew syncVersion`。
- **判据**：`pr-check.yml:152` `python scripts/sync-release.py sync --check` 非零退出即红，落在 `release-info` job。

### X-03 · 版本读不到静默回落危险默认值（亲验）
- **现状**：`build.gradle.kts:16-17` `versionName="unknown"` / `versionCode="0"`（若 `hmp.versionName/Code` 缺失）；`android/app/build.gradle.kts:43-44` `versionCode=51000` / `versionName="5.10.0"`；`desktop/app/build.gradle.kts:332` `packageVersion ?: "1.0.0"`、`:357` `dmgPackageVersion = "1"`（硬编码）。均走 `?:` 默认，属性缺失时不报错。
- **影响**：若 `gradle.properties` 的 `hmp.version*` 缺失/过期且 `syncVersion` 未随 `release.toml` 提交，build 仍「成功」产出带错误/默认版本号的包。`CI` 的 `sync --check`（X-02）可兜住「未提交同步」情形，但默认串本身绕过 `checkVersion` 之外任何保护。
- **动作**：将关键默认改为 fail-fast（缺失即 `throw GradleException`），至少对 `versionCode`。
- **判据**：难以自动化（仅属性缺失触发）；靠 `syncVersion` 纪律 + `pr-check` 的 `sync --check` 兜底。谁验：发版前比对 `gradle.properties` 与 `release.toml`。
- **施工结果（2026-11-02，B 批）**：四处默认值全部改为 fail-fast——
  ① 根脚本 `hmp.versionName` / `hmp.versionCode` 缺失或空白即抛（删掉 `unknown` / `0`）；
  ② `android/app` 的 `51000` / `"5.10.0"` 删除，`versionCode` 非整数也抛；
  ③ `desktop/app` 顶端一次性求值 `hmpVersionName` / `hmpVersionCode`，`packageVersion` 与 `msiPackageVersion` 共用，`dmgPackageVersion` 由硬编码 `"1"` 改为 `versionCode.toString()`；
  ④ `copyToReleases` 缺件抛异常；`releaseDesktop` 扫不到任何 msi/dmg/deb 也抛；`copyDesktopJar` 缺 jar 抛。
  验证（均为本地实跑）：`./gradlew help` 正常；`help -Phmp.versionName=` 红在本条新增的提示上；
  `releaseAndroid -x assembleRelease -x bundleRelease` 红在「缺少发布产物」上；临时移走桌面产物目录后 `releaseDesktop -x packageDistributionForCurrentOS` 红在「未找到桌面分发产物」上（目录已恢复）。
  如实记：只验了**配置期**与**拷贝期**的失败路径，未跑真实打包（APK/DMG/MSI 全量构建不在本轮范围）。

### X-04 · DI 无模块图校验（亲验）
- **现状**：grep 口径 `checkModules|verify(|koin.verify`，全仓 `.kt` 源码 **0 命中**（仅在 `docs/7_3/review-7.3-architecture.md`、`TODO.md` 出现）。`startKoin`/`modules(` 在 `MusicApplication.kt`、`HmpDesktopApplication.kt`、`IosModules.kt`、`DesktopModules.kt`，但无 `checkModules`。
- **影响**：新加 `single` 后类型错/漏注入只在运行时暴露；iOS 纯懒 → 进页面才崩（与 `AGENTS.md` §三、`§八.4` 描述一致，本会话独立确认）。
- **动作**：在 `:shared:desktopTest` 接 `koin-test` 的 `checkModules`，至少一个聚合 module 图。
- **判据**：删掉任意一条 `single` → 测试红；落在 `:shared:desktopTest`。

- **施工结果（2026-10-08，X-闸 / 另一半）**：图校验从 1 条变 **3 条**，`koin-test` 只进测试源集（`:shared` desktopTest、`shared-ui` desktopTest 与 androidHostTest），照 `room-testing` 先例不进 commonTest：
  - `shared/src/desktopTest/.../di/KoinGraphVerificationTest.kt` —— `sharedModule + desktopPlatformModule`，钉住领域/数据层自身的图（不依赖 UI 是否存在）。
  - `shared-ui/src/desktopTest/.../di/DesktopKoinGraphVerificationTest.kt` —— 桌面**完整装配**（`sharedModule + desktopPlatformModule + desktopPlayerModule + desktopUiModule`，对齐 `initKoinDesktop(...)`）。
  - `shared-ui/src/androidHostTest/.../di/AndroidAppKoinGraphVerificationTest.kt` —— Android **完整装配**（`sharedModule + androidPlatformModule + playerModule + uiModule`，对齐 `MusicApplication.onCreate`）。
  - 三条都用 `verify()`（纯静态，"runs no definition and needs no mock"）而不是 `checkModules()`（4.0 起废弃，且**会实例化每条定义** —— 装配图里有 Room / DataStore / `androidContext()`，跑起来就是碰开发者真实的 `~/.hmp`）。
  - 三点实测才找到的坑：① `listOf(a,b).verifyAll()` 只按**主类型**建索引，看不见 `singleOf(::X) bind I::class` 的次类型（实测把 `MusicRepository` 报成缺失），必须 `module { includes(...) }.verify()` 展平；② `ToolRegistry` 由 `createBaseToolRegistry(get())` 返回，校验会读它的构造器把就地组装的 `tools: List<Tool>` 当待注入 —— 这类要走 `injectedParameters(definition<ToolRegistry>(List::class))`，放 `extraTypes` 不生效；③ `GlobalTokenCounter` / `TokenMeter` 是 `:shared` 的 `internal` 类，跨模块点不到名，所以在 shared-ui 那两条只能整体放行 `Function0`（`:shared` 那条按定义逐条钉住）。
  - **实测信号（红→绿都做过）**：注释掉 `single<MusicAllDao>` → `:shared` 那条红；注释掉桌面侧 `single<PlaybackController>` → 桌面那条红并报 `Missing definition for '[field:'playbackController' - type:PlaybackController]' in definition '[Factory: …ViewModel]'`，而 Android 那条不受影响（两条独立）；恢复后 `testAll` 全绿。
  - ⚠️ **已知假阴性，别把绿当保证**：**带默认值的构造参数被静态校验视为可缺省**。实测注释掉 `single { PresenceBus() }` 后三条**全绿**，而 `MasterAgent` 装配处写的是 `chatPresenceBus = get()`（形参 `PresenceBus? = null`）—— 运行期照样抛。所以本闸门覆盖的是"必传依赖"（ViewModel 构造、`singleOf` 的仓库/DAO 链），可选参数那条路径仍只能靠运行期与真机。
  - **剩余盲区（如实记账）**：① iOS 装配（`IosModules` + `IosUiKoinModule` + `KoinHelper`）在任何本机可编译的源集里都拼不出来；② `MainActivity.onCreate` 里用 `loadModules` 动态注册的 `single<PlatformServices> { AndroidPlatformServices(...) }` —— 静态组合看不见，Android 那条只能把它登记进 `extraTypes`，注册被删不会红（这是"进页面才崩"的真实残留）。
  - **2026-10-28 补记（盲区 ① 的实证）**：`IosUiKoinModule.kt` 里 `single<PlaybackController> { IosPlaybackController }` 因历史编码损坏被并进了上一行的 `//` 注释 —— 编译照过、三条图校验照绿，而 iOS 上 `koinInject<PlaybackController>()`（`HelloCardAnchor.kt:129`、`HelloCardStack.kt:73`）会抛 `NoDefinitionFound`。已修（拆开注释与代码行 + 还原该文件 22 个 `U+FFFD`），修后用**注册面行数比对**替代校验：android/desktop/ios = 22/23/23（iOS 多的是 `PlatformServices`，Android 在 Activity 注册），修前 iOS 少一条。这不能替代图校验，但能抓住"整条绑定消失"这类事故。
  - 全量结果（2026-10-08）：`testAll` 六个源集 1181 例 / 0 失败 —— `:shared:desktopTest` 969、`:shared-ui:desktopTest` 42、`:shared-ui:testAndroidHostTest` 94、`:android:core-player` 51、`:desktop:core-player` 24、`:android:app` 1（模板桩）。

### X-05 · 导航 serializer 漏注册 2 个 NavKey，无编译期报错（亲验）
- **现状**：`Routes.kt` 定义 **30** 个 NavKey（L24–L182，grep `: NavKey` 共 30 行）。`HmpNavBackStack.kt` 的 `SerializersModule` 注册 **28** 个 `subclass`（L26–L60）。缺 `Routes.AI.AgentConfig`（`Routes.kt:143`）与 `Routes.Settings.AgentMonitor`（`Routes.kt:132`）。`NavigationGraph.kt` 两者都有 `entry<>`（L113、L173），但 serializer 注册缺。`Routes.kt:15` 注释自陈「漏注册无编译期报错，仅在该 key 参与保存/恢复时运行时报错」。
- **影响**：`AgentConfig`/`AgentMonitor` 是真实可达路由（`AgentMonitorScreen.kt:164-167`、`FeatureEntryRow.kt:86` 导航至此）；进程重建/配置变更触发回栈反序列化时抛 `SerializationException`，无编译保护。
- **动作**：在 `HmpNavBackStack.kt` 补两条 `subclass`；并加遍历断言（`RoutesTest` 现有断言是 `is NavKey` 恒真，不把关）。
- **判据**：需运行期 save/restore，CI 无法自动化。谁验：Android「不保留活动」+ 进 `AgentConfig`/`AgentMonitor` 后回退；或 macOS 上 Xcode 运行 iOS 触发进程重建。

- **施工结果（2026-10-08，三-1）**：两条 `subclass` 已补，且"漏注册无编译期报错"这一半已被机器接管 ——
  `shared-ui/src/androidHostTest/.../NavRegistrationGateTest.kt` 遍历反射出的每个 NavKey 做真实多态往返 + `entry<>` 覆盖比对，
  实测无人注册的新路由会被精确点名。**仍待验的是运行期那半**（真机"不保留活动"后回栈恢复），本机不可达，登记在 `../plan.md` §六。
  细节与两个反射陷阱（`@Serializable` data class 的嵌套 `$$serializer`、合成构造器）记在 `D8.md` D8-01 的施工结果段。

### X-06 · 测试覆盖工程面：androidMain / iosMain 为 0（亲验）
- **现状**：测试源集（`find */src/*Test*` 非 build）仅 `shared/{commonTest,desktopTest}`、`shared-ui/{commonTest,androidHostTest}`、`android/app/src/androidTest`、`desktop/core-player/src/desktopTest`。**无 `shared/src/androidHostTest`、无 `shared/src/iosTest`、无 androidMain/iosMain 专用测试源集**。即 `shared/src/androidMain`、`shared/src/iosMain` 的平台实现（`*Impl.android/ios.kt`、`DeviceMusicScanner.*.kt` 等）无对应测试源集覆盖。
- **影响**：平台特定逻辑回归无闸门；iOS 改动只能本机 Xcode 验。
- **动作**：跨平台逻辑尽量进 `commonTest`；平台特定路径在 `androidHostTest` / 本机 iOS 运行期验。
- **判据**：谁验——androidMain 改完跑 `:android:app:testDebugUnitTest` / `:android:core-player:testDebugUnitTest`；iosMain 改完需 macOS + Xcode 运行（无自动）。属「部分」。

- **施工结果（2026-11-05，android 半边开启）**：`shared` 第一次有了 `androidHostTest` 源集。
  - **先砍掉一个假 expect**：`stringToPinyinSortKey` 的三端实现**逐字相同**（iOS 只多一行注释），而它依赖的
    `PinyinLookupTable` 本来就在 commonMain —— 这个 `expect` 从第一天起就没有平台差异。合并进 commonMain 普通函数，
    删掉 3 个 `actual` 文件与 `expect` 声明；新增 `commonTest` **5 例**（ASCII 小写、汉字折叠为纯拉丁、标点丢弃、
    全丢弃回落、空串），一次覆盖 desktop / android / iOS 三个目标。
    断言只钉"折叠后的形状"，不硬编码具体拼音 —— 表是从 Unihan 生成的，硬编码会把测试绑死在数据文件上。
  - **开启源集**：`shared/build.gradle.kts` 的 `android { withHostTest { } }`（AGP 9 KMP 库插件默认关闭，
    源集目录随之从 `androidUnitTest` 改名 `androidHostTest`，与 `:shared-ui` 同款）；依赖只进该源集
    （`junit` / `kotlinx-coroutines-test` / `robolectric` / `room-testing`），**不进 commonTest** ——
    `room-testing` 与 `koin-test` 都踩过"污染 iOS 测试元数据解析"。根 `build.gradle.kts` 的 `testAll`
    同步挂上 `:shared:testAndroidHostTest`。
  - **第一批用例 10 例**：`DateFormatsAndroidTest` 6 例（`DateFormats.android` 53 行此前零测试；只断言与时区无关的
    不变量 —— 日期字符串往返、当天跨度 23–25h、`MM-dd` 是本地日期后缀、小时/ISO 周几范围、距次日 0 点在 (0, 25h]，
    这样在 UTC / UTC+8 / 有夏令时的时区都成立），不需要 Robolectric；`SettingsRepositoryAndroidTest` 4 例
    （Robolectric + DataStore 键名往返：liked / recent / current 互不串键、重复写入覆盖）。
  - **如实记 1（原计划要搬的那份不搬了）**：`PlaylistRepositoryImpl.{android,desktop}` 逐行 diff 后**只差时间源与
    import 别名**，搬它的 17 例等于用同一套 DAO 再测一遍，收益接近 0。真正分歧在三份实现之间：
    `SettingsRepositoryImpl` 差 **298 行**、`MusicRepositoryImpl` 差 **321 行**、`BackupFileRepositoryImpl` 差 **67 行**
    —— 这才是该测的地方，所以第一批选了 Settings。Playlist 那三份的正确处置是**下沉成公共基类**（D3-14），不是补测试。
  - **如实记 2（用例里写进注释了）**：Robolectric 在同一测试类内复用 Application，`preferencesDataStore` 的实例与
    落盘文件跨用例保留 —— 首版 `assertNull("首次启动为空")` 因此红了两次。现在每条用例用不同值并断言"写进去的就是读出来的"，
    残留骗不过去；但**「首次启动默认值」这一层仍未测**，要测得先让 `SettingsRepositoryImpl` 能注入独立 DataStore。
  - **仍未覆盖**：`MusicRepositoryImpl.android`（338 行）、`MusicTagEditor.android`（190 行，依赖 MediaStore/文件标签）、
    `BackupFileRepositoryImpl.android`（140 行，依赖 SAF）、`SecureStorageHelper.android`（Keystore，Robolectric 支持有限）；
    `iosMain` 仍是 0，需 macOS。

- **施工结果（2026-11-05，第二批 14 例）**：上一批留下"仍未覆盖"四个文件，本批收掉三个的一半以上。
  - **`BackupFileRepositoryAndroidTest` 7 例**：D7-05 的路径守卫（越界拒读拒删、`..` 逃逸拒绝、越界文件原样无伤）
    在 **Android 份实现**上重钉一遍 —— 它与 desktop 是照抄关系，抄丢一笔守卫就是安全洞而不是功能 bug；
    另有存读往返（落点必须是 `filesDir/backups`）、列表过滤（`isBackupFileName`）与排序（新的在前）、
    导入三态（复制进目录并可见 / 已在目录内幂等 / 来源缺失不留半成品）。
  - **`MusicTagEditorAndroidTest` 4 例**（复用 desktop 的最小 WAV 夹具，jaudiotagger 对 `.wav` 支持完整标签与封面，
    桌面 6 例已证明）：文本标签写读一致、缺文件走 `Result.failure` 不许抛、**只读文件必须给
    `NeedsStorageAccessException`**（分区存储的核心分支，且文件内容不许被半途改坏）、PNG 封面字节可读回。
    未测：SAF `writeTags(Uri)` 重载（Robolectric 模拟 SAF 授权流成本超出收益）、"未 init"分支
    （`appContext` 是 object 静态位无重置入口，用例顺序不保证，4 行分支风险可接受）。
  - **`MusicRepositoryScanAndroidTest` 3 例**：`MusicRepositoryImpl.android` 真正只属于 Android 的那段
    （MediaStore 查询 → 白名单/5 秒下限 → 标签解析 → 三表落库）第一次有了机器反馈。
    手法是注册假 MediaStore provider（`ShadowContentResolver.registerProviderInternal("media", …)`），
    从公共入口 `loadMusicFromDevice()` 打进去，断言落在**数据库行**上。三条：白名单 + 5 秒下限
    （D2-09，钉住"1 分钟下限不会回来"的 Kotlin 侧语义）、MediaStore 行 → 三表映射
    （albumArtUri 由 albumId 拼、MIME→format、SIZE→fileSize、userInfo 默认行）、重扫保留已有 `musicExtra.date`
    与 `isGetExtraInfo`（用户资产不被扫描冲掉）。
    探针边界（测试 KDoc 里也写了）：假 provider **不执行 selection**，所以"60 秒下限写回 SQL"这种回归
    这条测不到，钉的是 Kotlin 侧闸门；SQL 目录过滤与 jaudiotagger→MediaMetadataRetriever 回退链仍无判据。
  - **环境坑两枚，留给后来人**：
    ① `BundledSQLiteDriver` 的 JVM 原生库（`sqliteJni.dll`）**不随 android 变体的单测 classpath 分发**
    （那批 natives 只打进 desktop 用的 `-jvm` 构件），androidHostTest 里建库直接 `UnsatisfiedLinkError`。
    解法是**不设驱动**：Room 在 Android 目标默认走框架驱动，Robolectric 在 JVM 上原生模拟
    `android.database.sqlite`，测试跑的仍是真 SQL；
    ② Robolectric 4.14.1 注册假 provider 的可用 API 是 `ShadowContentResolver.registerProviderInternal`，
    旧文档常引用的 `registerProvider` 已不在公开面上。
  - **剩余**：`SecureStorageHelper.android`（`AndroidKeyStore` provider 在 JVM/Robolectric 上不存在，
    `encrypt`/`decrypt` 起不来 —— 要测得先把"取密钥"抽象成可注入接口，属结构调整，不在补测试批次里做）；
    `iosMain` 仍为 0，需 macOS（§五）。

### X-07 · 静态风格闸门缺失（亲验）
- **现状**：无 `.editorconfig`（Glob `**/.editorconfig` 无命中）；`*.gradle.kts` grep `id("...detekt"|"...ktlint")` 无匹配；`docs/ktlint-integration.md` 标题即「暂缓 —— 等 agent 分支线合并后再启动」。当前 `DEVELOP.md:406-408` 已如实写「目前没有任何机械化风格闸门」，并指向该暂缓方案。
- **影响**：代码风格靠人工，存量不一致累积。
- **动作**：二选一——启动 `docs/ktlint-integration.md` 方案接 CI，或明确永久不做。
- **判据**：谁验——CI `lint` job 人为改缩进变红（方案 T5）；当前未做。
- **附加 doc-truth（亲验）**：`AGENTS.md` §八.14 称「`DEVELOP.md:396-400` 声称使用 ktlint 检查是失真的」，但当前 `DEVELOP.md:406` 实际写「没有任何机械化风格闸门」——**AGENTS 该条已过时**（doc-vs-doc 失真，双方行号均亲验）。建议修 AGENTS.md 该条。

- **2026-11-05 决议：移出 v7.3 待办**（用户决定，记于 `TODO.md` §七 R50）。
  - **不是"判定不做"**：方案 `docs/ktlint-integration.md` 261 行仍然有效，实测数据（三档 37,155 / 17,803 / 7,521；关 4 条规则 80 处；残留 107 处）也仍然成立。
  - **移出的理由是时机**：方案 T0 要求"工作区收口 + 分支线合并"，而整个 7.3 期间工作区反复处于几十项未提交的状态（当前 68 项）；格式化会落在 446 个文件上，与功能 diff 混在一起无法 review。这与「做不做」无关，是「什么时候做才不亏」。
  - **重启条件（三条同时满足）**：`git status` 干净且只剩一条活跃开发线；本版发版完成（`release.toml` 与 tag 不再重复）；确认只做**增量守门**（存量 107 处不清理）。
  - **恢复入口**：`docs/ktlint-integration.md` §7 的 T1 / T3 / T4 / T5 / T6（跳过 T2 全量格式化）。仍需一句话确认的两处：规则集（ktlint `intellij_idea` 是否够，不引 detekt）、`PlaybackCommandPort.kt` 那 10 处 `SEEK_TO` / `SHUFFLE_ON` 是改名还是 `@Suppress`。
  - 本条**不再作为 v7.3 的剩余项统计**（`plan.md` 的 X 线计数已同步）。

### X-08 · storybook 孤儿模块（亲验）
- **现状**：`settings.gradle.kts:36-65` 三个 `buildTarget` 分支均未 `include(":storybook")`；`storybook/build.gradle.kts` 存在但不在构建图。`build.gradle.kts:302-315` 注释说明原 `releaseStorybook` 任务因模块移出构建导致依赖无法解析、连 `./gradlew release` 失败，故已移除任务、保留源码与构建脚本。
- **现状判定**：空壳（有代码/构建脚本，不在任何构建图/CI）。
- **影响**：死代码占用维护注意力；无人验证其能否编。
- **动作**：要么删除 `storybook/`，要么按 `build.gradle.kts:308-313` 三步恢复 `include` + `releaseStorybook`。
- **2026-09-30 决策 6（见 `../plan.md` §九）：原地保留，声明为设计沙盒** —— 不恢复 `include`、不改代码、不保证可编译；声明已落 `AGENTS.md` §三 与 `DEVELOP.md` 模块结构。原"删除"建议作废。
- **判据**：`grep ":storybook"` 在 `settings.gradle.kts` 与根 `build.gradle.kts` 均无 include（亲验）。改判条件：恢复 include 后 `./gradlew :storybook:wasmJsBrowserProductionWebpack` 绿。

### X-09 · testAll 静默空过（shared-ui:desktopTest 为 NO-SOURCE）（亲验）
- **现状**：`testAll`（`build.gradle.kts:86-104`）依赖 `:shared:desktopTest`、`:shared-ui:desktopTest`、`:shared-ui:testAndroidHostTest`。L92 注释自认「shared-ui 的 desktopTest 为空，真正的用例在 androidHostTest」。Glob `shared-ui/src/desktopTest/**` 无目录 → `:shared-ui:desktopTest` 为 **NO-SOURCE** → 静默绿。本地 `preflight`（L459 依赖 `testAll`）同样漏跑该源集。
- **影响**：本机 `preflight` 与（若 CI 改跑）`testAll` 都对 shared-ui 的 desktop 源集「假绿」。
- **动作**：从 `testAll` 移除不存在的 `:shared-ui:desktopTest`（shared-ui 测试已挂 `testAndroidHostTest`），或用 `maybeDepends` 包裹 + 源集存在性断言。
- **判据**：`./gradlew :shared-ui:desktopTest` 报 NO-SOURCE；落在 `testAll` / `preflight`。

- **施工结果（2026-10-08，X-testAll）**：先按"目录不存在"摘掉 `testAll` / `testUi` 的 `:shared-ui:desktopTest` 依赖，随后补另一半时发现 **`shared-ui/build.gradle.kts:121` 早就声明了 `desktopTest` 源集并配好依赖，只是 `src/desktopTest/` 目录一直没建** —— 这才是 NO-SOURCE 的真因。补出目录（放桌面装配图校验）后：
  - `testAll` / `testUi` **重新挂回** `:shared-ui:desktopTest`（前提变了：现在它是 4 套件 42 例的真实任务，且 commonTest 那批用例从此在 desktop / androidHost 两个目标上都跑）。
  - `testUiDesktop` 因此**不删**了 —— 它原本是"无 Android SDK 环境的唯一入口"，但挂在空源集上必然 NO-SOURCE 绿；现在源集有内容，它重新变成有用而不骗人的任务。（本轮已按此决定在脚本注释里写明理由。）
  - 顺手删掉 `build.gradle.kts` 里"desktopTest 源集目前为空（只有 androidHostTest，8 个文件）"那句过期硬计数。
  - 同类残留**已一并处理**（同日续做）：`desktop/app` 同样只有 `desktopMain`，`testAll` 挂的 `:desktop:app:desktopTest` 也是 NO-SOURCE 绿。现已建出 `desktop/app/src/desktopTest/`（`build.gradle.kts` 加 `kotlin("test")` 依赖），放 `SingleInstanceGuardTest` 4 例 —— 单实例锁的取锁/拒锁/释放后可重启/崩溃残留不阻塞启动。实测信号：把 `release()` 里的 `fileLock?.release()` 与 `lockFile?.close()` 注释掉 → 只有 `releaseAllowsNextStart` 红，其余三条不受影响。桌面 app 从此在 `testAll` 里有真实内容。

### X-10 · maybeDepends 在错目标下静默零工作（亲验）
- **现状**：`build.gradle.kts:34-37` `maybeDepends` 用 `rootProject.findProject(projectPath)` 判存在再 `dependsOn`。设 `HMP_BUILD_TARGET=desktop` 时 `:android:app` 不在构建，`testAndroid`（L143–148）仅 `maybeDepends(...testDebugUnitTest)` → 无依赖 → 必绿零工作。
- **影响**：错目标下跑 `testAndroid`/`testDesktop` 给人「通过」错觉。
- **动作**：错目标时 fail-fast（如 `throw` 提示「请在该模块所属目标下运行」）而非静默绿。
- **判据**：`HMP_BUILD_TARGET=desktop ./gradlew testAndroid` 绿零工作（可复现，待本地执行确认细节）。
- **施工结果（2026-11-02，B 批）**：新增 `Task.requireTarget(modulePath)`，只在 `testAndroid` / `testDesktop` 注册块里调用——
  目标不含对应模块时抛 `GradleException`，提示改用 `android` / `desktop` / `all`。
  **没有改 `maybeDepends`**：`testAll` / `compileAll` 正依赖它「按当前目标跳过不存在的模块」，
  改全局会让单目标下的全量任务自炸（`compileAll` 无条件依赖 `compileDesktop`）。
  验证：`HMP_BUILD_TARGET=desktop ./gradlew testAndroid` 红在新增提示上；同目标下 `testAll --dry-run` 仍成功。
  如实记：域章节原文这条「判据」写的是缺陷复现（绿零工作）；本次按 `plan.md` §五 **X-目标** 的目标口径执行（不再静默绿），未改写原判据文本。

### X-11 · 依赖矩阵：KMP 侧有单源守护，iOS 侧独立（亲验，部分）
- **现状**：`gradle/libs.versions.toml` 集中 `[versions]`（agp 9.1.1 / kotlin 2.3.21 / compose 1.11.1 / room 2.8.3 / ktor 3.1.1 / koin 4.2.2 等，`libs.versions.toml:1-40`）；`build.gradle.kts:5-13` 用 `alias(libs.plugins.*)`。**KMP/Android/Desktop 依赖一致性有守护**。iOS 侧依赖（XcodeGen `ios/HMP/project.yml` + CocoaPods）不在该目录守护范围内——`sync-release.py` 仅写 `project.yml` 的版本号字段（L257–261），不守护 pod 版本。
- **影响**：iOS pod 版本漂移无单源核对。
- **动作**：评估把 iOS pod 版本收敛或加核对脚本。
- **判据**：待验——读 `ios/HMP/project.yml` 与 Podfile 确认 pod 版本是否另有单源（Glob `ios/HMP/Podfile` 当前无命中，需确认 pod 声明位置）。

- **收口（2026-11-05，判据落成"正面确认"）**：判据要求的那次核对做完了，结论是**没有漂移面可守**。
  - **Podfile 在 `ios/Podfile`**（原 Glob 口径漏了它）。里面**没有任何第三方带版本 pod**：唯一 pod 是
    `pod 'shared_ios', :path => '../shared-ios'` —— 本地路径 podspec（`shared-ios/shared_ios.podspec`），
    `spec.version = '1.0.0'` 是占位符，全仓没有任何消费方（不进 app 版本串、不进 Info.plist、
    `sync-release.py` 不碰它）。"pod 版本漂移"这个担忧的前提（存在需要同步的外部版本号）不成立。
  - **app 版本的单源链是完整的**：`release.toml` → `scripts/sync-release.py:259-274` →
    `project.yml` 的 `CFBundleShortVersionString` / `MARKETING_VERSION` + `pbxproj`，这正是 X-03/X-版本
    已 fail-fast 守护的那条链，无需新增守护。
  - **一条观察记录（不动代码）**：`Podfile platform :ios, '26.3'` 与 `podspec ios.deployment_target '16.0'`、
    `project.yml deploymentTarget.iOS "26.3"` 三者并存。当前 project.yml 定在 26.3，podspec 的 16.0 只是
    更宽松的下界，无害；但若将来有人把 project.yml 降到 26.3 以下，Podfile 那行会**静默托住** Pods 工程的
    最低系统。改这三处任一都需要 macOS 实机验证打包，不在本机动作面内 —— 记录在此，改版部署目标时先看这行。

---

## 4. 四项横查（横切线改写）

**构建正确性**
- CI 不编 iOS、不跑单测（X-01）；`testAll` 含 NO-SOURCE 空壳依赖（X-09）；`maybeDepends` 错目标静默绿（X-10）。构建图本身能产出 Android/Desktop 包，但校验强度只在「能编过」。
- DI 无图校验（X-04）→ 编译过 ≠ 能注入。

**发版正确性**
- 版本真源 → 9 派生点由 `sync-release.py` 统一写、`sync --check` 在 `pr-check` 拦（X-02）；派生点读不到版本静默回落危险默认（X-03）。
- `release.yml` 有齐全断言（`assets` + SHA256SUMS + tag/Release），缺产物会拒发（读 `release.yml:356-366`）。但本机 `copyToReleases` 找不到产物只 `println("!! Not found")`（`build.gradle.kts:48`）。
- storybook 孤儿模块若被误恢复会再让 `./gradlew release` 依赖解析失败（X-08）。

**可观测性**
- 无编译期/CI 期可见的 DI、导航 serializer、版本回落告警；这些只在运行期（iOS 进页面、进程重建反序列化）才炸。
- 静态风格闸门缺失（X-07）→ 风格漂移无信号。

**测试覆盖**
- androidMain/iosMain 平台实现 0 覆盖（X-06）；`shared-ui` 的 desktop 源集在 `testAll` 被 NO-SOURCE 吃掉（X-09）。
- 唯一带测试的守门人 `preflight` 含 `testAll`，但其覆盖是「部分 + 部分静默空过」。

---

## 5. 验证缺口

- **需 macOS / Xcode**：X-05 的 iOS 侧 save/restore 崩、X-06 的 iosMain 改动、X-03 部分 iOS 派生点写回需 macOS 机器。
- **需真机/模拟器**：X-05 的「不保留活动」回退复现、X-06 的 androidTest 运行。
- **需特定构建目标**：X-10 的错目标静默绿需在 `HMP_BUILD_TARGET` 切换下复现。
- **需跑 gradle 任务**（本会话未跑，仅读脚本）：X-09 的 `:shared-ui:desktopTest` NO-SOURCE、X-04 的 `checkModules` 缺失、X-02 的 `sync --check` 实跑。均不要求构建全量，但要本地 gradle/Python 环境。

---

## 6. 刻意不做（带改判条件）

- ~~**CI 跑单测 / 编 iOS**：维持 `AGENTS.md` §一.5 硬约束——CI 不跑单测、不编 iOS~~ **已改判（2026-09-30 用户决议）**：方向改为「**当前环境能跑就都跑**」——Linux runner 跑得到的编译与测试纳入 CI；iOS 编译只在 macOS runner 可用时跑，缺环境不阻塞合入。故本条不再是"刻意不做"，转为 **X-01** 的落地项（清单见 `TODO.md` §六 决议 ①）。保留一条护栏备查：若 CI 测试在 runner 上出现挂死（**R31** 的未结案线索），按 `docs/7_3/taskbook/README.md` §四 的口径隔离 `:android:core-player`（全仓唯一带 Robolectric 的模块）或给它加挂钟超时，**不因此回退整条决议**。
- **把 ktlint/detekt 接进 Gradle 构建图**：`docs/ktlint-integration.md` 已定「不进 Gradle、走独立 CLI」。改判条件：若需要 CI 单命令闸门且 CLI 分发成本过高时重议。
- **storybook 模块立即删除或恢复**：当前留作「暂缓」空壳。改判条件：下一轮若决定做组件预览站则按 `build.gradle.kts:308-313` 恢复；若确认无用则删除 `storybook/`。
- **版本默认值改 fail-fast（X-03）**：当前保留默认串以求「打包不中断」。改判条件：确认 `syncVersion` 已作为发版硬步骤、且 `pr-check` 的 `sync --check` 能 100% 拦下未同步情形后，再将默认改为抛错。

---

**© 2026 Hearable Music Player · 横切线 X（独立重读）**
