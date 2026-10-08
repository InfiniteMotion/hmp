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

### X-04 · DI 无模块图校验（亲验）
- **现状**：grep 口径 `checkModules|verify(|koin.verify`，全仓 `.kt` 源码 **0 命中**（仅在 `docs/7_3/review-7.3-architecture.md`、`TODO.md` 出现）。`startKoin`/`modules(` 在 `MusicApplication.kt`、`HmpDesktopApplication.kt`、`IosModules.kt`、`DesktopModules.kt`，但无 `checkModules`。
- **影响**：新加 `single` 后类型错/漏注入只在运行时暴露；iOS 纯懒 → 进页面才崩（与 `AGENTS.md` §三、`§八.4` 描述一致，本会话独立确认）。
- **动作**：在 `:shared:desktopTest` 接 `koin-test` 的 `checkModules`，至少一个聚合 module 图。
- **判据**：删掉任意一条 `single` → 测试红；落在 `:shared:desktopTest`。

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

### X-07 · 静态风格闸门缺失（亲验）
- **现状**：无 `.editorconfig`（Glob `**/.editorconfig` 无命中）；`*.gradle.kts` grep `id("...detekt"|"...ktlint")` 无匹配；`docs/ktlint-integration.md` 标题即「暂缓 —— 等 agent 分支线合并后再启动」。当前 `DEVELOP.md:406-408` 已如实写「目前没有任何机械化风格闸门」，并指向该暂缓方案。
- **影响**：代码风格靠人工，存量不一致累积。
- **动作**：二选一——启动 `docs/ktlint-integration.md` 方案接 CI，或明确永久不做。
- **判据**：谁验——CI `lint` job 人为改缩进变红（方案 T5）；当前未做。
- **附加 doc-truth（亲验）**：`AGENTS.md` §八.14 称「`DEVELOP.md:396-400` 声称使用 ktlint 检查是失真的」，但当前 `DEVELOP.md:406` 实际写「没有任何机械化风格闸门」——**AGENTS 该条已过时**（doc-vs-doc 失真，双方行号均亲验）。建议修 AGENTS.md 该条。

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

### X-10 · maybeDepends 在错目标下静默零工作（亲验）
- **现状**：`build.gradle.kts:34-37` `maybeDepends` 用 `rootProject.findProject(projectPath)` 判存在再 `dependsOn`。设 `HMP_BUILD_TARGET=desktop` 时 `:android:app` 不在构建，`testAndroid`（L143–148）仅 `maybeDepends(...testDebugUnitTest)` → 无依赖 → 必绿零工作。
- **影响**：错目标下跑 `testAndroid`/`testDesktop` 给人「通过」错觉。
- **动作**：错目标时 fail-fast（如 `throw` 提示「请在该模块所属目标下运行」）而非静默绿。
- **判据**：`HMP_BUILD_TARGET=desktop ./gradlew testAndroid` 绿零工作（可复现，待本地执行确认细节）。

### X-11 · 依赖矩阵：KMP 侧有单源守护，iOS 侧独立（亲验，部分）
- **现状**：`gradle/libs.versions.toml` 集中 `[versions]`（agp 9.1.1 / kotlin 2.3.21 / compose 1.11.1 / room 2.8.3 / ktor 3.1.1 / koin 4.2.2 等，`libs.versions.toml:1-40`）；`build.gradle.kts:5-13` 用 `alias(libs.plugins.*)`。**KMP/Android/Desktop 依赖一致性有守护**。iOS 侧依赖（XcodeGen `ios/HMP/project.yml` + CocoaPods）不在该目录守护范围内——`sync-release.py` 仅写 `project.yml` 的版本号字段（L257–261），不守护 pod 版本。
- **影响**：iOS pod 版本漂移无单源核对。
- **动作**：评估把 iOS pod 版本收敛或加核对脚本。
- **判据**：待验——读 `ios/HMP/project.yml` 与 Podfile 确认 pod 版本是否另有单源（Glob `ios/HMP/Podfile` 当前无命中，需确认 pod 声明位置）。

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
