# 架构实现审查报告（2026-09-28）· 修复归入 v7.3.0

**审查对象**：仓库当前实现整体（非某个 diff），`master` 历史含 v7.2.0（未产出包）/ v7.2.1 / v7.2.2（2026-09-28 已发布）
**规模基线**：769 个 `.kt`，源码约 4.8 万行；`:shared` 173 文件/27588 行（commonMain），`:shared-ui` 168 文件/36828 行
**方法**：机械排除（`build/` 生成码、Compose 资源访问器、KSP 产物不人肉读）→ **5 路分片并行审**（三端接缝 / domain+data / UI 层 / 构建拓扑 / 闸门与文档真值）→ 主审对**全部 S1 级结论逐条重跑证据**（下表标 ✔ 的即主审亲验；行号一律以本报告当日工作树为坐标，动手前先 grep 符号名）
**分级**：`S1` = 会造成错误行为 / 数据丢失 / 静默漏检 / 错误产物 → `S2` = 结构性债务，显著拖慢下阶段功能扩展 → `S3` = 一致性 / 可发现性 → `S4` = 风格
**修复版本**：v7.3.0（MINOR：含行为变更与 iOS 存储格式迁移，不走 PATCH）。待办台账见 [TODO](../../TODO.md) §六 R39–R56

---

## 一、总体判断

骨架选型是对的：六边形 port 层、Room 迁移链 1→9 完整注册、nav3 类型安全路由、统一日志（`HmpLog` 调用点 464 处、0 `println`，口径见 `docs/spec/hmp-log.md` §6；`LogTag` 38 条与代码逐字一致）、`expect/actual` **文件级 1:1:1 配对无孤儿**（主审实测：23 个 expect 宿主文件 / 29 处 expect 声明，androidMain·desktopMain·iosMain 各有 23 个 actual 宿主文件）。

问题在**落地全靠"逐文件自愿"，且没有一个独立于写入方的校验器**：

- 代码实物（编译、测试、事务、DI 装配、路由注册、三端镜像）与文档叙述之间**没有任何断言关系**；
- 唯一被机器守住的只有"版本号的派生点"，而 `sync --check` 与写入端共用同一套正则，属**自比而非独立 oracle**；
- 于是 107 个平台源集 `.kt` 里的 12 个 `*Impl*` 只有 2 个有对应测试文件（且都在 desktopTest，androidMain/iosMain 覆盖为 **0**），iOS 分支每次都是"未被编译验证的那一处"，而 CI 既不跑测试也不编译。

**因此 v7.3.0 的执行顺序必须是：先立闸门（R44/R43/R41闸门段/R47/R50），再修 S1（R39/R40/R42/R45/R46），最后才动结构（R48–R52）。** 在没有闸门的仓库里先做结构重构，风险大于收益。

---

## 二、S1 明细（数据 / 安全 / 错误产物）

### ✔ R39 备份与恢复整条路径无事务 —— 用户可触发的数据丢失
- **实测**：`withTransaction|inTransaction` 全仓命中 **0 次**；`@Transaction` 21 处只落在 `Music.kt`/`PlaylistItem.kt` 的 DAO 注解上。
- `shared/src/androidMain/.../PlaylistRepositoryImpl.android.kt:142-143` → `playlistDao.deleteAll()` + `playlistItemDao.deleteAll()` 后逐条 insert，无事务包裹；`MusicRepositoryImpl.android.kt:150-152` 三张表连删。desktop/ios 同型（`:129-`）。
- `ImportUserDataBackupUseCase.kt:23-28` 串行调 4 个仓库的 restore，`catch (Exception)` → `Result.failure`，**无回滚、无导入前安全副本**；`MusicRepositoryBase.kt:690/717/732` 用 REPLACE 语义。
- 可达性已确认：DI 注册 + `BackupViewModel.kt:74-86` + iOS 侧均调用；现有测试用 Fake 只测 happy roundtrip，丢失窗口未测。
- **动作**：批量写唯一入口收敛到 `AppDatabase.withTransaction`；导入前落一份安全副本；`catch` 复抛 `CancellationException`。
- **判据**：故意让第 3 步失败，第 1、2 步的写入必须回滚；`grep withTransaction shared/src` 命中且被 `domain/**/usecase` 的连续多写路径引用。

### ✔ R10（既有）+ 新增暴露面：iOS 密钥是 XOR 伪加密且密钥文件与密文同目录，工程还开着文件共享
- `shared/src/iosMain/.../SecureStorageHelper.ios.kt:57-70`：`simpleEncrypt/simpleDecrypt` 实为 `xor`，密钥文件名却叫 `.aes_key`（`:28`）；`keysDir = NSDocumentDirectory + "/.keys"`（`:17-25`）。
- `ios/HMP/project.yml:43` `UIFileSharingEnabled: true` → 沙箱 Documents 可经"文件"App 直接拷出，密钥与密文一起可被拿走。
- 三端密文互不可解，跨端恢复被 `runCatching` 静默吞（`SettingsRepositoryImpl.desktop.kt:239`）。
- **不新开编号**：并入既有 **R10**（换 Keychain），但 **R10 需追加两条**：① 关掉 `UIFileSharingEnabled` 或把密钥彻底移出 Documents；② 文件名 `.aes_key` 与实际算法的口径纠正（属误导性命名）。

### ✔ R40 iOS 的扫描目录设置整体是空壳
- `SettingsRepositoryImpl.ios.kt:189-190`：`override val scanDirectoryConfig = flowOf(ScanDirectoryConfig())`（恒默认值）、`override suspend fun saveScanDirectoryConfig(config) { }`（空实现）。
- `DeviceMusicScanner.ios.kt:28-42` 写死扫 Documents；`MusicRepositoryImpl.ios.kt:174-176` 构造时根本不接 `settingsRepository`。Android(`:347-366`)/Desktop(`:36-40`) 都真消费该配置。
- **动作**：实装，或先在 iOS 撤下设置入口。**判据**：iOS 上改扫描目录→重启→扫描结果随之变化；或该入口在 iOS UI 不可见且共享侧按平台隐藏。

### ✔ R41 在用的 NavKey 未注册 nav3 serializer（漏注册无编译期报错）
- `Routes.kt` 有 **30** 个 NavKey，`HmpNavBackStack.kt` 的 `SerializersModule` 只有 **28** 条 `subclass(`，且该文件内 `grep Agent` **零命中** → `Routes.AI.AgentConfig`（`Routes.kt:143`，跳转点 `AIScreen.kt:318,358`，`NavigationGraph.kt:173` 有 entry）与 `Routes.Settings.AgentMonitor`（`Routes.kt:132`，`NavigationGraph.kt:113` 有 entry）都未注册。
- 该文件自己的注释（`:20-22`）就写明"漏注册无编译期报错，仅在该 key 参与保存/恢复（进程重建/配置变更）时运行时报错"。
- 附带：`RoutesTest.kt:215-246` 手写 22 条 `is NavKey` 断言，**是恒真闸门**——新路由不注册也不会红。
- **动作**：补注册 + 把该测试改成"遍历 NavKey 集合 ⊖ 白名单 ⇒ 必须有 serializer 且必须有 entry"。**判据**：加一条路由而不写 `entry`/不注册，测试必红。

### ✔ R42 歌词设置双存储：写入端没有消费端
- 播放页读的是 legacy：`LyricsScreen.kt:104-110` ← `LyricsSettingsViewModel.kt:22` ← `LyricsSettingsUseCase.kt:85-105`，这些属性**全标 `@Deprecated("Use getComponentConfig(...)")`**；设置页写的是新 per-component JSON，全仓唯一新模型消费者是 Android 悬浮歌词（`FloatingLyricsOverlay.kt:72`）。
- **后果**：用户在设置页改字号/行距，播放页静默不生效。
- **动作**：读写收敛到同一模型，legacy 键做一次性迁移后删除。**判据**：设置页改值后，播放页取到同一值（一条 VM 级用例即可守）。

### ✔ R43 DI 装配无图校验
- `checkModules|verify()` 全仓 **0 命中**；12 个 module 文件、131 个 `single` + 3 `viewModel`，消费侧 **495** 处 `inject/get`。
- 三端启动校验强度不同：Android 只 eager 解 `MasterAgent`（`MusicApplication.kt:43-57`）、Desktop 只解控制器（`Main.kt:121`）、iOS 纯懒 → **"进页面才崩"在 iOS 成立**。另 `SharedModules.kt:140` 注释称"iOS 未提供 keepAlive"，而 `IosModules.kt:73` 已注册（事实反转，属 R21 家族）。
- **动作**：`:shared:desktopTest` 接 `koin-test` 的 `checkModules`。**判据**：删掉任意一条 `single`，测试红。

### ✔ R44 合入前闸门不编译、不测试；iOS 源集在 CI 里永不编译
- `pr-check.yml` 四个 job = version / ffmpeg-assets / release-info / verdict，`run:` 只有 `./gradlew checkVersion checkReleaseConsistency`（`:65`）；`release.yml` 的 validate 同样；三个 build job 全是打包，**没有 iOS job**。
- **零新代码可加的 iOS 编译闸门**：`shared/build.gradle.kts:86-91` 的 `kspIosArm64/iOSX64/iOS Simulator` 已配齐 → `:shared:compileKotlinIosArm64` 可直接作 macOS job；且 `commonTest` 依赖（`:77-83`）全是完整 KMP 构件（仅 `room-testing` 隔离在 desktopTest），故 `:shared:iosSimulatorArm64Test` 能在 macOS 上跑现有全部 commonTest，**无需改测试代码**。
- **动作**：pr-check 加 `HMP_BUILD_TARGET=desktop ./gradlew compileAll` + `:android:app:assembleDebug`；`release.yml` 或独立 workflow 加 macOS 编译 job（挂钟超时按 R31 教训）。**判据**：故意提交一个语法错的 PR 必红。
- 与 **R31** 的关系：R31 管"测试在 runner 上挂死未结案"，本条管"编译这道闸门根本没建"。两者都要，顺序是本条先行。

### ✔ R45 版本号读不到时静默回落到错误值
- `desktop/app/build.gradle.kts`：`packageVersion = findProperty("hmp.versionName") ?: "1.0.0"`、`msiPackageVersion` 同款回落、`dmgPackageVersion = "1"` **硬编码不随 versionCode**。桌面 job 不跑 `checkVersion`，所以属性缺失时文件名仍像 v7.2.2、包内版本却是 1.0.0。
- `build.gradle.kts:41-50` `copyToReleases`：找不到产物只 `println("!! Not found")` 不失败（CI 侧 `sync-release.py collect` 会失败，本机不会）。
- 这是既有 **R30**（android 的 `51000`/`"5.10.0"` 兜底）的同类，扩到 desktop 与本机收集。
- **判据**：删掉 `hmp.versionName` 后构建**硬失败**；`releases/` 缺件时任务失败。

### ✔ R46 生成的 podspec 入库，且被 Windows 写成反斜杠路径
- `shared-ios/shared_ios.podspec` 是被 track 的生成物（`git ls-files` 命中），当前工作树 `:45` 为 `spec.resources = ['build\compose\cocoapods\compose-resources']`（`git status: M`，**未提交的本地改动**）；`spec.version` 恒 `1.0.0`。
- `.gitattributes` 没有 `*.podspec` 规则，`shared/build.gradle.kts:29` 的 cocoapods 块又把它当产物生成 → 换 macOS/Linux 时资源路径可能静默丢或报错。
- **动作**：三选一（生成物出库并加 `.gitignore` / 入库但补 `text eol=lf` / 改由 `pod lib lint` 现场生成），并把 Windows 反斜杠写死纠正。**判据**：macOS `pod install` + 构建能取到 compose-resources（属 R38 同批实机验证）。

### ✔ R47 三端镜像内容漂移零防护
- 数量对称但**内容镜像**：`PlaylistRepositoryImpl.{desktop,ios}.kt` 逐行 diff≈0、`SettingsRepositoryImpl.{desktop,ios}` 各 ~500 行、`MusicRepositoryImpl.desktop.kt:234-262` 与 `.ios.kt:224-253` 注释互称"本端排序刻意不同"实则逐字相同。`getRoomDatabase`/`createJson` 三端逐字相同（迁移列表各写一份 → 新增 migration 漏一端就是单端升级崩溃）。
- 主审实测覆盖面：平台源集 `.kt` 共 **107** 个（shared android/desktop/ios = 20/20/23，shared-ui = 15/13/16），其中 `*Impl*.kt` **12** 个，有对应测试文件的只有 **2** 个（`shared/src/desktopTest/.../PlaylistRepositoryImplTest.kt`、`SettingsRepositoryImplTest.kt`）；`shared/src` 下**只有 commonTest 与 desktopTest 两个测试源集**（无 androidHostTest、无 iosTest）→ androidMain 与 iosMain 的实现覆盖为 **0**。
- 已发生过的事故样本：commit `17d9d61`（A3 `enabled` 不落盘，修复需 3×4 行同步）。
- **动作**：`commonTest` 加一条源集扫描断言（每个 expect 名在 androidMain/desktopMain/iosMain 各有含 `actual` 的同名文件）+ 把"三端刻意差异"写成白名单常量；真正的下沉（Settings/Playlist/Backup 抽 base）列 R48 之后的结构线。
- **判据**：某端删掉一个 actual 文件，闸门红；白名单外的"三端逐字相同"文件被识别为可下沉候选。

---

## 三、S2 明细（结构性债务）

> ✔ = 主审当日亲自重跑过证据；未标 ✔ 的条目为分片实测（口径都写在证据列里，两个分片独立统计相互印证的会注明），动手前按符号名复验。

| | 编号 | 发现 | 证据 | 判据要点 |
|---|---|---|---|---|
| | **R48** | UI 状态归属分裂：主流走 VM（20 VM/29 屏），但 **agent 区整块裸奔**——1658 行的屏 0 VM、17 处 `remember{mutableState}`，保存/校验/提示词覆盖判定全在 composable 的 `save` lambda 里，并直接改写 `masterAgent` 活实例 | `AgentConfigScreen.kt:161,167,173-243,256-306`；`AppRoot.kt:135-144,206,237-244,766`（8 `koinInject`/7 `LaunchedEffect`/4 scope/16 `collectAsState`）；`LyricsSettingsPage.kt:82,101-136` | 具体化并扩展既有 **R15**：agent 区每屏有 VM，composable 内 0 挂起调用 |
| | **R49** | （注：三份规范已于 2026-09-28 按统一格式重写，本条列的三处失实在重写中已改正——4 Tab→3、NavigationRail 判据、iOS=SwiftUI；**token 强制与白名单仍未做**，R49 未结案）设计 token 是"自愿制"：硬编码 hex 色 **53 处/13 文件**（扣 token 文件 37 处/12 文件）、`fontSize` 字面量 **43 处/9 文件**、`RoundedCornerShape(N.dp)` **148**、动效毫秒 **56**、`.dp` 字面量 **1444 处/91 文件** vs `dimens.*` 仅 209 处/24 文件（14%）；卡片两套并存（`HMPCard` 28 站 vs 裸 `Card(/Surface(` 57 站）；336 个 `@Composable` 只有 81 个带 `modifier: Modifier`（24%）。**其中 53 / 1444 两个计数由 UI 分片与闸门分片独立统计得出，结果一致** | `AgentVisuals.kt:47-60`、`ListScreen.kt:90-93`、`AppRoot.kt:493,502`、`SubScreen.kt:59,65` | 先在 `docs/spec/hmp-design.md` 落**硬约定 + 例外白名单**，再加机械检查；文档现状连 Modifier 约定都没有 |
| ✔ | **R50** | 无任何静态闸门：无 detekt、无 ktlint、无 `.editorconfig`、无 `core.hooksPath`（`.git/hooks` 只有 10 字节空 `post-checkout`/`post-commit`）。而 `DEVELOP.md:396-400` 声称"使用 ktlint 检查"并列出 `.editorconfig`/`ktlint.gradle` —— **三样都不存在**；`docs/ktlint-integration.md`(261 行) 通篇是待办方案且前提失效（其 §20/§147 假设的 `ci.yml` 不存在，而 CI 恰好不跑测试 → 格式化无人验收，卡死在 R31） | 全仓 `*.kts/toml/yml/properties` grep ktlint\|detekt\|spotless = 0 | 只做**增量守门**（新/改文件必过），不做全量格式化 |
| | **R51** | 构建拓扑平铺：1582 行 build 脚本 / 无 convention plugin；root `build.gradle.kts` 540 行里**发版链 285 行（53%）**、`checkVersion` 自带 git shim 62 行、24 个自定义 task 中 **11 个** `notCompatibleWithConfigurationCache`（全仓 13）。实测重复只集中在 **3 段**：android JVM21+proguard 逐字 2 份、desktop targets+测试源集 4 份、`settings.gradle.kts` pluginManagement/dependencyResolutionManagement 镜像 2 份 | `android/app:73-83` vs `core-player:29-38`；`shared:62-83`/`shared-ui:114-138`/`desktop-core:9-22`；`settings:5-15` vs `22-32` | 只值得抽这 ~60 行。**代价要写清**：build-logic 里失去 `libs.` 访问器；且抽象会掩盖"iOS target 集刻意不一致"（`shared:26-36` vs `shared-ios:27-41`）这类有意差异 |
| | **R52** | 三套构建图互不一致：`settings.gradle.kts:38-56` 用 `HMP_BUILD_TARGET` 条件 include，立论注释靠"configure-on-demand 下不配置该模块"，但 `gradle.properties:15-16` 已明确停用 COD → desktop 分支仍全量配置 `:android:core-player`，CI 能过只因 runner 预装 Android SDK。`build.gradle.kts:34-37` 的 `maybeDepends` 在错目标下静默丢依赖 | 最小踩坑场景：`export HMP_BUILD_TARGET=desktop` 后跑 `./gradlew testAndroid` **必绿**（任务实际零工作） | 本地(无 env)/CI(有 env)/IDE 三图取并集做校验，或收敛成单一全图 + 任务级过滤 |
| | **R53** | 发版真源链路的结构边界：① 写与核共用同一套正则 → 锚点位移类错误能同时骗过 `--check`；② `site/js/config.js` 取**首个** `version:`、`pbxproj` 全量替换 MARKETING_VERSION、ROADMAP 生成块内手改被无警告覆盖；③ 真源集不全——iOS build number 恒 `1`、部署目标三源（26.3 / 16.0 / 文档）、三套包名空间 | `scripts/sync-release.py:240,243,273,282-286,404`；`project.yml:51`、`Info.plist:22`、`pbxproj:654`；`desktop/app:356`；`shared:30-31` | 引入独立 oracle（例：从产物侧读回真实版本比对，而不是再跑一遍同一正则），并把 `pbxproj`/`Info.plist` 收敛成一份 `.xcconfig`（与 R38 同批决策） |
| ✔ | **R54** | 文档真值失真已影响对外：14 条已确认失真（详见 §四）。其中 **ROADMAP 的一条已随 v7.2.2 进入公开 Release Notes**："`release.toml` 已修正 README/CLAUDE/DEVELOP 版本口径与 Kotlin 2.3.21" —— 实际 DEVELOP 仍写 2.2.21、CLAUDE 仍写 v7.2.1 | 见 §四 逐条 | 与既有 **R36** 合并执行：硬计数改脚本生成，散文只核标记段 |
| ✔ | **R55** | `storybook` 是孤儿模块：有自己的 `build.gradle.kts`(42 行) 却**未被 `settings.gradle.kts` include**，不依赖 `:shared-ui` 而自带 fork 组件与旧版 icons(1.7.3)；仓库唯一 lockfile（`kotlin-js-store/wasm/yarn.lock`）服务的正是它 | `settings.gradle.kts:36-64`、`storybook/build.gradle.kts:27-35` | 决策项（删除 / 归位 / 明确声明为设计沙盒），不进重构范围 |
| | **R56** | 依赖矩阵无人守：Coil2 与 Coil3 并存（`shared-ui:70-73`）、`kotlinx-coroutines` 主库 1.10.1 vs 测试 1.9.0、`koin-compose` 别名**只存在于注释里**（活雷）、alpha/beta 进生产、`android/app:7` 与 `core-player:5` 绕过 alias 硬编码版本、KSP 在两模块被 apply 却**零 processor** | `gradle/libs.versions.toml:4,11-17,29-30,42,116` | 一条 catalog 使用率与一致性断言（定义未用/使用未定义/同库多版本即红） |

**S3/S4 记录级**（本批不单独编号，动手时顺带清）：`internal` 修饰符 174 处但无稳定策略文件；`docs/spec/hmp-design.md:3` 停在 v5.10 并称 iOS=SwiftUI/SF Pro（实际 iOS 跑同一套 `AppRoot` + Compose）；`docs/README.md:68,91` 的 F 章数与 CLAUDE 口径互斥（9 vs 14）；Fake 无契约测试（8 个 Fake ↔ 4 接口 × 14 Impl，无任何机制能发现漂移）。

---

## 四、✔ R54 的失真清单（全部主审重跑，不是转抄文档）

| 文档:行 | 声称 | 实测真值 |
|---|---|---|
| `CLAUDE.md:7` / `README.md:219` | 最新发布 **v7.2.1**、**下一版 v7.2.2（未开工）** | `v7.2.2` tag 已存在且已发布（2026-09-28），GitHub Release 标 Latest |
| `CLAUDE.md:296` | 应用版本 7.2.1 / versionCode 72001 | `gradle.properties` = **7.2.2 / 72002** |
| `CLAUDE.md:344` | v7.2.2 范围 = R1–R17、R20–R30 | v7.2.2 已发布且这批**一条未动**；需重挂到 v7.3.0（本次即做此事） |
| `DEVELOP.md:273,276` | Kotlin **2.2.21** / AGP **9.0.0** | `libs.versions.toml:2-3` = Kotlin **2.3.21** / AGP **9.1.1** |
| `DEVELOP.md:396-400` | 用 ktlint；有 `.editorconfig`、`ktlint.gradle` | 三者全不存在（见 R50） |
| `DEVELOP.md:438-442,453` | 版本号集中在 `gradle.properties`，手改它（示例 72001/7.2.1） | 真源是 `release.toml`，派生点禁手改（R34 已结案） |
| `DEVELOP.md:457,519` | `releaseDesktop` 含 AppImage / 任务口径 | AppImage 已放弃（`1b7cca4`）；release 组 **10** 个而非 8 |
| `DEVELOP.md:473` | release 组 8 个 task | `group="release"` 实为 **10**（漏 `syncVersion`、`checkReleaseConsistency`）；CLAUDE:118 的 27 对，CLAUDE:183 的"上表 26"与 DEVELOP 的 26 错 |
| `CLAUDE.md:130` + `build.gradle.kts:121` | shared-ui 测试"全在 androidHostTest，8 文件"，desktopTest 源集为空 | androidHostTest **6**（其中 1 个是 fakes），另有 commonTest **3**；desktopTest 目录不存在。`CLAUDE.md:85,88` 教的 `:shared-ui:testDebugUnitTest` **任务不存在**，应为 `testAndroidHostTest` |
| `README.md:83` / `DEVELOP.md:46` | 原生层 17 个 Swift 文件 | **22**（HMP 18 + HMPNowPlaying 4） |
| `TODO.md:63`(I2) | 待删 `MusicPlayService.swift` | `find ios` 零命中，**已删** → 待办过期 |
| `docs/ktlint-integration.md:20,147,248` | 闸门放 `ci.yml`、"CI 完全不调自定义 Gradle 任务" | 无 `ci.yml`；`pr-check.yml:65` 与 release validate 都在调自定义任务 → 前提失效 |
| 原 `docs/ci-pipeline-diagnosis.md` | — | 审查后（2026-09-28 目录重排）已删除并并入 `docs/spec/hmp-release.md`，本表其余项的 CI 口径以该文件为准 |
| `ROADMAP.md:292`（→ 公开 Notes） | 已修正 DEVELOP/CLAUDE/README 版本口径 | 见上，未修正 |

**不作判据的一项**：`CLAUDE.md:143` 的"desktopTest 报告 91 类/26 包"取自 `build/reports/` 过期产物（现值 119 类/33 包/964 用例）。**这类数字本就不该人写进文档**，属 R54 里"硬计数改脚本生成"的直接对象，不要按行号去逐条对齐。

---

## 五、主审复核：否决与下调（误报记录）

1. **"expect 30 vs actual 108 数量失衡"→ 误报**。分片复核为 32 个 expect 全部 1:3 对齐、富余数是 object 成员 `actual` 与 `typealias`；主审用行锚定正则另测得 **29 处 expect 声明 / 23 个宿主文件，三端各有 23 个 actual 宿主文件**（文件级 1:1:1，无孤儿）。**两个总数都别当判据** —— expect 计数随正则口径浮动，配对关系要用"文件级有无 actual"来断言（这正是 R47 闸门的形态）。真问题在镜像内容，不在数量。
2. **"107 个平台实现文件只有 6 个有对应测试（≈5.6%）"→ 口径混用，已按主审实测改写**。实测：平台源集 `.kt` 共 107（shared 20/20/23、shared-ui 15/13/16）；其中 `*Impl*.kt` **12** 个；有同名测试文件的 **2** 个（都在 `shared/src/desktopTest`）；`shared/src` 下只有 `commonTest`+`desktopTest` 两个测试源集，**无 androidHostTest / 无 iosTest** → android/ios 实现覆盖 0。结论（三端镜像零防护）不变，且比原表述更硬。
3. **"AgentConfig 有 6 个真实跳转点"→ 数量夸大**。实测 `Routes.AI.AgentConfig` 跳转 2 处（`AIScreen.kt:318,358`）。但"未注册 serializer"这一事实成立，S1 保留。
4. **`domain/lyrics/LyricsSettingsUseCase.kt`（R42 引用路径）不存在**，真身在 `domain/setting/usecase/LyricsSettingsUseCase.kt`。结论不变，路径已改正。
5. **原 `docs/ci-pipeline-diagnosis.md` 被分片判为"陈旧文档"** —— 审查当时它已带快照横幅并指向版本规范的 CI 段与 TODO §五，属**已处置**，不重复立案；随后在 2026-09-28 的目录重排中删除、内容并入 `docs/spec/hmp-release.md`。
6. **"PlaylistRepositoryImpl desktop↔ios 逐行相同"降级为 S2 证据**：那是重复而非缺陷，不当 bug 报。
7. **从 `build/reports/`、`build/generated/` 取的统计一律不作判据**（生成码 163 个 `.kt`、`kspCaches` backups 都在其中）。`.kt` 总数按口径不同为 785（全量含生成）/ 623（仅 `src/`）/ 769（工作树含测试不含生成）——报告里凡引用计数都标了口径。
8. **子代理声明但主审未能证实的**（不写成事实，只在 §七 留验证条件）：未注册 NavKey 是否**必然**崩（取决于 nav3 `SavedStateConfiguration` 是否只在真正写盘时序列化，未运行验证）；`getAllMusicInfoAsList` 的 Android tie-break 是刻意还是漂移（注释两头互斥）；KSP 2.3.11 与 Kotlin 2.3.21 是否仍受版本前缀强约束（离线无法验）。

---

## 六、刻意不做（带可检验的理由）

| 项 | 不做的理由 | 何时改判 |
|---|---|---|
| 拆 `MasterAgent`(1759)/`HelloSubAgent`(1844) 上帝类 | 68/59 个方法、22 处 DAO 泄漏确实该拆，但**闸门未立前重构 = 裸奔**；且拆分会与既有 R22、R3–R6 的语义修复撞车，同批做只会让 review diff 失控 | R44+R43+R47 三道闸门转绿之后，随 v7.4 |
| 动 `PinyinLookupTable.kt`(1969 行) | Unihan 派生的数据表，人工维护才是错；它"大"但不"复杂" | 引入生成管线时才动，属工具线不属架构线 |
| ktlint **全量**格式化 | 实测违规规模会淹没整个 v7.3.0 的审查 diff（历史估计数千处/数百文件），且 T7 验收要求 CI 跑测试而 CI 恰好不跑（R31 未结案） | 只做增量守门；CI 恢复跑测后再谈全量 |
| `SettingsRepositoryImpl` 三端下沉到 commonMain 基类 | 单独立项，不与本批混做：三端各 ~500 行、涉及存储键语义，改错就是 R40 那类"某端静默空实现"。R47 的白名单常量先把可见性建立起来 | 闸门绿 + iOS 能进 CI 之后 |
| `storybook` 模块重构 | 它不在构建图里，任何改动都无法被验证 | 只做"删除/归位/声明为沙盒"决策（R55） |

---

## 七、验证缺口（写清什么必须什么条件才能结案）

- **iOS 全部运行期结论都欠实机验证**：R40 的空壳是否有 SwiftUI 旁路、Keychain 迁移是否平滑、`TagWriterBridge` 未注册是否导致 iOS 改标签必失败（`MusicTagEditor.ios.kt:16-18` 需注册，Swift 侧只注册了 Parser/Artwork 两桥，`AppDelegate.swift:21-22`）、R46 的 podspec 路径、R53 的 pbxproj 改写 —— 全部依赖 macOS。**低成本入口**：`:shared:compileKotlinIosArm64` + `:shared:iosSimulatorArm64Test`（见 R44，零新代码）。与既有 **R38** 同批做。
- **`project.yml` 与 `pbxproj` 双源并存**：未见 `xcodegen generate` 的再生流程，其中一个可能是死文件。判据要先问清再生方式，否则改哪个都对。
- **本机 preflight 与低内存互斥**：`gradle.properties:9,13`（4G + parallel）会杀 daemon，实际只有 `gradlew-lowmem`（1G + in-process）跑得动，而 `CLAUDE.md:334` 教的是前者 → "跑不动"会被当成"跳过"且不留痕迹。这是 R44 闸门必须进 CI 的核心理由。
- **迁移覆盖**：DB 已 `version=9`，迁移测试只到 7（既有 **R1/R2**），而 A4 之后已无 destructive 兜底 → R1/R2 随 v7.2.2 变成线上风险，v7.3.0 必须与 R39 同批（都要开 v10 迁移）。

---

## 八、v7.3.0 的执行顺序（建议分批 PR）

1. **闸门批**：R44（编译 + iOS 编译 job）、R43（checkModules）、R41 闸门段（路由遍历断言）、R47 闸门段（expect↔actual 扫描）、R56（catalog 断言）。此批不改业务代码，可并行。
2. **S1 批**：R39 事务、R40 iOS 设置、R42 歌词收敛、R45 版本回落、R46 podspec、R10 追加两条（iOS 密钥）。
3. **既有 R 批**：R1/R2/R26（都要开 v10 迁移，与 R39 同批）、R3/R4/R5/R6（引擎正确性）。
4. **结构批**：R48、R49、R50、R51、R52、R53、R54/R36。
5. **决策批**：R55（storybook 去留）、R17（不可逆工具的折中档）、R53 的 `.xcconfig` 收敛。

---

**© 2026 Hearable Music Player · 架构实现审查（v7.3.0 修复范围）**
