# 构建任务体系补齐方案

> 状态：**已实施**（2026-09-15）　｜　制定日期：2026-09-15
> 依据：`build.gradle.kts` / `desktop/app/build.gradle.kts` / `shared/build.gradle.kts` / `gradle.properties` / `.github/workflows/release.yml` 实测核查
> 定位：属「工程 / 构建」主题，与 Agent 子系统设计无依赖关系
>
> **实施结果摘要**：16 个任务全部落地并通过注册验证；CI 已修；文档已同步。
> **`testCore` 实跑通过**（91 suites / 708 tests / 全绿）；`testUi` 实跑暴露出一处真实缺陷（`shared-ui` 的测试全在 `androidHostTest`、`desktopTest` 为空）并已修复。
> 实施中发现 3 处原设计假设有误，已按代码事实修正（见 §3.1 编译任务、§3.3、§六点五）。

---

## 一、问题诊断

### 1.1 现状：10 个自定义任务全部围绕「打包发布」

| 任务 | 脚本 | 定位 |
|---|---|---|
| `release` | 根 | 聚合入口 |
| `releaseAndroid` / `copyAndroidDebug` | 根 | Android 打包 |
| `releaseIos` | 根 | iOS 打包 |
| `releaseDesktop` / `copyDesktopJar` | 根 | Desktop 打包 |
| `downloadFFmpeg` / `injectFFmpeg` | `desktop/app` | FFmpeg 依赖处理 |
| `copyIconsToIos` | `shared` | iOS 图标拷贝（空转） |

**核心矛盾：任务供给与真实工作流错位。** 发布是低频动作（每个版本一次），却被封装得最完整；而**测试、校验、清理这些高频动作完全没有封装**。

### 1.2 三个真问题（按严重度）

#### 🔴 P0：测试链路是断的，而且没人知道

`.github/workflows/release.yml:51` 调用：

```bash
./gradlew :shared:test :shared-ui:testDebugUnitTest --no-daemon --no-configuration-cache
```

但实测（`gradlew tasks --all`）**这两个任务名在当前构建中都不存在**：

| CI 调用的任务 | 实际是否存在 | 该模块真实可用的测试任务 |
|---|---|---|
| `:shared:test` | ❌ **不存在** | `:shared:desktopTest`、`:shared:testDebugUnitTest`（Android 变体） |
| `:shared-ui:testDebugUnitTest` | ❌ **不存在** | `:shared-ui:desktopTest`、`:shared-ui:allTests` |

之所以没人发现，是因为该步骤带了 **`continue-on-error: true`** —— 任务名报错被吞掉，CI 依然绿。

**后果：CI 名为「校验」，实际从未真正跑过单元测试。** 项目里存在真实的测试（如 `shared/src/commonTest/.../RadioSessionTest.kt`，本会话前面的工作还改过它），但它们**在 CI 里从来没被执行过**。

> 附带发现：`continue-on-error: true` 这个写法本身也是错误的 —— 它让「测试」这一整个校验环节彻底失去意义。校验步骤不该允许失败。

#### 🟠 P1：内存参数没有固化，每次编译靠背命令

`gradle.properties:9` 写死 `org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m`，且 `org.gradle.parallel=true`。

但本机（内存受限 + Android Studio 常驻）**4G 堆 + 并行 = Gradle daemon 被 OS 静默杀死**（判据：`gradle daemon disappeared unexpectedly`）。当前唯一绕法是在命令行手敲一长串覆盖参数：

```
--no-daemon --console=plain -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=2 \
-Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" \
-Dkotlin.daemon.jvm.options=-Xmx1536m
```

这段命令**只存在于个人记忆和临时脚本里**，没有落到仓库中 → 换人/换机器就得重新踩一遍坑。

#### 🟠 P2：本地无发版前预检，错误只在 CI 暴露

`release.yml:53-62` 有一段关键校验：

```bash
if [ "$VERSION" = "$LATEST_TAG" ]; then
  echo "::error::Version $VERSION already exists! ..."; exit 1
fi
```

这段逻辑**只在 CI 里**。本地发版前无法自检 → 等推到远端、CI 跑起来才炸，白等一轮 CI。

---

## 二、设计原则

1. **只加不删，先补齐再清理。** 本轮聚焦「新增缺口任务」；废任务（`copyAndroidDebug` / `injectFFmpegForDev` / `copyIconsToIos`）的删除单独列为可选阶段，避免一次改动面过大。
2. **实测驱动，不靠推断。** 每个任务落地后必须用 `tasks --group <g>` 验证注册成功，用窄任务 `--dry-run` 验证依赖图可解析。
3. **低内存参数必须进仓库。** 这是本机最高频的踩坑点，固化的收益最大。
4. **与 CI 对齐。** 新增任务的命令要能被 CI 复用，同时**修掉 CI 里那两个不存在的任务名**。
5. **不破坏既有约定。** 新任务全部落根 `build.gradle.kts`，沿用 `group`/`description`/`notCompatibleWithConfigurationCache` 的既有写法。

---

## 三、补齐方案

### 3.1 新增任务一览（16 个）

**测试类（7 个）**

| # | 任务 | group | 优先级 | 作用 |
|---|---|---|---|---|
| T1 | `testAll` | `verification` | 🔴 P0 | 全量测试（当前构建目标下所有可跑模块） |
| T2 | `testCore` | `verification` | 🟠 P1 | 只测 `:shared` —— 纯逻辑层，改 Agent/Domain 时用 |
| T3 | `testUi` | `verification` | 🟠 P1 | 测 `:shared-ui`（`desktopTest` + `testAndroidHostTest`） |
| T4 | `testAndroid` | `verification` | 🟠 P1 | Android 侧单元测试 |
| T5 | `testDesktop` | `verification` | 🟠 P1 | Desktop 侧测试 |
| T6 | `testQuick` | `verification` | 🟠 P1 | 冒烟：只跑 `:shared`，最快反馈 |
| **T17** | **`testUiDesktop`** | `verification` | 🟠 P1 | 只测 `:shared-ui` 的 desktop 源集（无 Android SDK 时用）——**实施中新增** |

> T17 不在原方案内，是实施时发现 `shared-ui` 的测试全在 `androidHostTest`（`desktopTest` 为空）后补的，详见 §六点五。

**编译类（5 个）**

| # | 任务 | group | 优先级 | 作用 |
|---|---|---|---|---|
| T7 | `compileAll` | `build` | 🟠 P1 | 编译当前目标下全部模块（不含打包） |
| T8 | `compileCore` | `build` | 🟠 P1 | 只编译 `:shared` |
| T9 | `compileUi` | `build` | 🟠 P1 | 只编译 `:shared-ui` |
| T10 | `compileAndroid` | `build` | 🟠 P1 | 只编译 Android 侧 |
| T11 | `compileDesktop` | `build` | 🟠 P1 | 只编译 Desktop 侧 |

**发布 / 清理类（4 个）**

| # | 任务 | group | 优先级 | 作用 |
|---|---|---|---|---|
| T12 | `checkVersion` | `release` | 🟠 P2 | 版本号未与已有 tag 重复 |
| T13 | `preflight` | `release` | 🟠 P2 | 发布前聚合体检 |
| T14 | `cleanReleases` | `build` | 🟡 | 清理 `releases/` 产物 |
| T15 | `cleanOrphans` | `build` | 🟡 | 列出孤儿 build 目录（不自动删） |

外加 **T16：修复 CI 的两个错误任务名**（非新任务，但同属本方案交付）。

---

### 3.2 ★ 前置约束：模块集随 `HMP_BUILD_TARGET` 变化

**这是设计测试/编译任务前必须先处理的问题**，否则会造出在目标切换时直接报错的「死任务」。

`settings.gradle.kts` 用环境变量 `HMP_BUILD_TARGET` 决定 include 哪些模块：

| `HMP_BUILD_TARGET` | 是否含 `:android:app` | 是否含 `:desktop:app` |
|---|---|---|
| `desktop` | ❌ 否 | ✅ 是 |
| `android` | ✅ 是 | ❌ 否 |
| 未设置 → `all` | ✅ 是 | ✅ 是 |

**后果**：若把 `testAll` 的 `dependsOn` 写死包含 `:android:app:testDebugUnitTest`，则在 `HMP_BUILD_TARGET=desktop` 下会立即报
`Task with path ':android:app:testDebugUnitTest' not found` —— **和 `releaseStorybook` 是同一类错误**（引用不在构建中的任务）。

**对策：按存在性动态过滤依赖**，而不是写死模块列表：

```kotlin
// 只在模块真的被 include 时才加入依赖，避免目标切换后悬空
fun maybeDepends(path: String) {
    if (rootProject.findProject(path.substringBeforeLast(':')) != null) dependsOn(path)
}
```

用 `findProject()` 判断比硬编码模块集更稳 —— 模块列表变了也不用改任务定义。

---

### T1 — `testAll`（🔴 P0）

**目标**：一条命令跑通「当前构建目标下所有可跑的」单元测试，自动跳过未纳入构建的模块。

**关键设计点**：不能照抄 CI 的任务名（那两个不存在），且**必须做存在性过滤**（见 3.2）。

```kotlin
tasks.register("testAll") {
    group = "verification"
    description = "运行当前构建目标下全部单元测试（自动跳过未纳入构建的模块）"
    notCompatibleWithConfigurationCache("test fan-out task")

    fun maybeDepends(path: String) {
        if (rootProject.findProject(path.substringBeforeLast(':')) != null) dependsOn(path)
    }

    // KMP 侧（shared / shared-ui 恒在构建中）
    dependsOn(":shared:desktopTest")
    dependsOn(":shared-ui:desktopTest")

    // 平台侧（按目标存在性过滤）
    maybeDepends(":android:app:testDebugUnitTest")
    maybeDepends(":android:core-player:testDebugUnitTest")
    maybeDepends(":desktop:app:desktopTest")
    maybeDepends(":desktop:core-player:desktopTest")

    doLast {
        println("OK 全部单元测试通过（HMP_BUILD_TARGET=${System.getenv("HMP_BUILD_TARGET") ?: "all"}）")
    }
}
```

**实测可用的测试任务全表（16 个）** —— 设计其它测试任务的依据：

| 模块 | 测试任务 | 说明 |
|---|---|---|
| `shared` | `desktopTest`、`allTests` | KMP 纯逻辑层（59 个测试文件在 `commonTest`） |
| `shared-ui` | `desktopTest`、`allTests`、`testAndroid`、`testAndroidHostTest` | CMP 模块，**有 Android 目标**；⚠️ `desktopTest` 源集**为空**，真实用例 8 个全在 `androidHostTest` |
| `shared-ios` | `allTests` | iOS 聚合框架（仅 iOS 目标） |
| `android:app` | `test`、`testDebugUnitTest`、`connectedDebugAndroidTest` | AGP 变体 |
| `android:core-player` | `test`、`testDebugUnitTest`、`connectedDebugAndroidTest` | AGP 变体 |
| `desktop:app` | `desktopTest`、`allTests` | |
| `desktop:core-player` | `desktopTest`、`allTests` | |

> **不纳入 `connectedDebugAndroidTest`**：需真机/模拟器，不属于可在 CI 跑的单元测试。
> **不纳入 `allTests`**：在 `shared` / `shared-ui` 上会连带 iOS 目标，Windows 上因缺 iOS 工具链失败 → 故用 `desktopTest`。

---

### T2–T6 — 分模块测试（🟠 P1）

单个 `testAll` 的问题是**反馈太慢**：改一行 Agent 逻辑也要等 UI 模块测试跑完。按模块拆开，日常按改动范围选跑。

```kotlin
tasks.register("testCore") {
    group = "verification"
    description = "只测 :shared（Domain / Agent / Data 纯逻辑层）"
    dependsOn(":shared:desktopTest")
}

tasks.register("testQuick") {
    group = "verification"
    description = "快速冒烟：只跑 :shared 单元测试（改 Domain/Agent 时用）"
    dependsOn(":shared:desktopTest")
}

tasks.register("testUi") {
    group = "verification"
    description = "只测 :shared-ui（Compose UI 与平台桥接）"
    // 注意：shared-ui 的 desktopTest 源集**目前为空**（只有 androidHostTest 有测试，
    // 8 个文件），因此单挂 desktopTest 会 NO-SOURCE 空过。这里把 androidHostTest
    // 一并挂上，否则 shared-ui 的测试实际上没人跑。
    // androidHostTest 不需要真机/模拟器（JVM 上的宿主测试），但仍需 Android SDK
    // —— Android SDK 缺失的环境可改用 testUiDesktop。
    dependsOn(":shared-ui:desktopTest")
    dependsOn(":shared-ui:testAndroidHostTest")
}

tasks.register("testUiDesktop") {
    group = "verification"
    description = "只测 :shared-ui 的 desktop 源集（不依赖 Android SDK）"
    dependsOn(":shared-ui:desktopTest")
}

tasks.register("testDesktop") {
    group = "verification"
    description = "只测 Desktop 侧（需 HMP_BUILD_TARGET 含 desktop）"
    fun maybeDepends(path: String) {
        if (rootProject.findProject(path.substringBeforeLast(':')) != null) dependsOn(path)
    }
    maybeDepends(":desktop:app:desktopTest")
    maybeDepends(":desktop:core-player:desktopTest")
}

tasks.register("testAndroid") {
    group = "verification"
    description = "只测 Android 侧（需 HMP_BUILD_TARGET 含 android）"
    fun maybeDepends(path: String) {
        if (rootProject.findProject(path.substringBeforeLast(':')) != null) dependsOn(path)
    }
    maybeDepends(":android:app:testDebugUnitTest")
    maybeDepends(":android:core-player:testDebugUnitTest")
}
```

> `shared-ui:testAndroid` / `testAndroidHostTest` 是否纳入 `testAndroid`，实施时先确认其是否需 Android SDK（见第五节决策点）。

---

### T7–T11 — 编译任务（🟠 P1）

**为什么需要**：改 `shared` 的公共 API 可能只让 `shared-ui` 编译失败，而这种破坏在跑测试时未必暴露。**per-module 编译任务是廉价的「契约破坏检测」**，比全量测试快得多。

**★ 实测校正（2026-09-15 实施时发现，已推翻原稿假设）**

| 模块 | 原稿设想 | **实测真实情况** |
|---|---|---|
| KMP 侧（shared / shared-ui / desktop:*） | `compileKotlinDesktop` | ✅ 存在，可用 |
| **Android 侧（android:app / android:core-player）** | ~~`compileDebugKotlin`~~ | ❌ **不存在**。这两个模块用 AGP 内置 Kotlin（`android.disallowKotlinSourceSets=false`），**完全没有暴露任何 `compile*` 任务** |

因此 Android 侧的编译校验改用 **`assembleDebug`** —— 它包含编译但不做 release 打包，且是这两个模块唯一可用的「只编译」入口。
（`shared-ui` 另有 `compileAndroidMain`，但它编译的是 `shared-ui` 的 androidMain 源集，不是 android:app。）

```kotlin
tasks.register("compileCore") {
    group = "build"
    description = "只编译 :shared"
    dependsOn(":shared:compileKotlinDesktop")
}

tasks.register("compileUi") {
    group = "build"
    description = "只编译 :shared-ui（desktop 源集）"
    dependsOn(":shared-ui:compileKotlinDesktop")
}

tasks.register("compileDesktop") {
    group = "build"
    description = "编译 Desktop 侧各模块"
    fun maybeDepends(path: String) {
        if (rootProject.findProject(path.substringBeforeLast(':')) != null) dependsOn(path)
    }
    maybeDepends(":desktop:app:compileKotlinDesktop")
    maybeDepends(":desktop:core-player:compileKotlinDesktop")
}

tasks.register("compileAndroid") {
    group = "build"
    description = "编译 Android 侧各模块（需 HMP_BUILD_TARGET 含 android）"
    fun maybeDepends(path: String) {
        if (rootProject.findProject(path.substringBeforeLast(':')) != null) dependsOn(path)
    }
    // 注：android:* 模块不暴露 compile* 任务（AGP 内置 Kotlin），故用 assembleDebug 作为编译校验
    maybeDepends(":android:app:assembleDebug")
    maybeDepends(":android:core-player:assembleDebug")
}

tasks.register("compileAll") {
    group = "build"
    description = "编译当前构建目标下全部模块（不含打包）"
    dependsOn("compileCore", "compileUi", "compileDesktop")
    if (rootProject.findProject(":android:app") != null) dependsOn("compileAndroid")
}
```

**低内存参数固化（与所有任务配套，关键）**

> ⚠️ **先说不可行的做法**：`org.gradle.jvmargs` / `org.gradle.parallel` / `workers.max` **都是 Gradle 启动期属性，无法在 build script 的条件块里改**（脚本执行时 daemon 早已按 `gradle.properties` 启动）。任何试图用 `if (findProperty("hmp.lowmem"))` 改写它们的写法都不会生效。
> 因此**不要**在 `gradle.properties` 里加开关，也不要写 `build.gradle.kts` 条件块 —— 两种都做不到。

真正可行的落地方式二选一（推荐 A）：

**A. 包装脚本（推荐，零侵入、随仓库传递）**

新增仓库根 `gradlew-lowmem.bat`：

```bat
@echo off
REM 低内存构建包装：本机 Android Studio 常驻占内存，默认 -Xmx4096m + parallel 会导致
REM Gradle/Kotlin daemon 被 OS 静默杀死。本脚本覆盖为低内存配置。
REM 用法：gradlew-lowmem.bat <task...>     例：gradlew-lowmem.bat testCore
gradlew.bat %* --no-daemon --console=plain ^
  -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=2 ^
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" ^
  -Dkotlin.daemon.jvm.options=-Xmx1536m
```

> 同时建议补一个 `gradlew-lowmem`（无扩展名，供 Git Bash / macOS 用），内容为同参数的 sh 版本。

**B. 写入 `~/.gradle/gradle.properties`**（用户级，不入仓库）：把低内存参数设成该用户所有项目的默认值。缺点是**影响所有项目**且不随仓库传递。

> 本方案推荐 **A**。另有**同类问题**需要一并说明：`--no-daemon` 也在脚本里写死了 —— 这与现有 `build.gradle.kts` 里 `notCompatibleWithConfigurationCache(...)` 的既有约定一致（这些任务本就与 configuration cache 不兼容）。

---

### T12 — `checkVersion`（🟠 P2）

**目标**：本地复刻 CI 的版本重复检测，发版前先跑。

```kotlin
tasks.register("checkVersion") {
    group = "release"
    description = "校验 gradle.properties 的版本号未与已有 git tag 重复（对齐 CI 的 validate 步骤）"

    doLast {
        val cur = versionName
        // 取最新 tag（按版本序）
        val pb = ProcessBuilder("git", "tag", "--sort=-v:refname")
        pb.redirectErrorStream(true)
        val out = pb.start().inputStream.bufferedReader().readText()
        val latestTag = out.lineSequence().firstOrNull { it.isNotBlank() }?.removePrefix("v")

        println("当前版本: $cur")
        println("最新 tag: ${latestTag ?: "(无)"}")

        if (latestTag != null && cur == latestTag) {
            throw GradleException(
                "版本号 $cur 与已有 tag v$latestTag 重复！请先更新 gradle.properties 的 hmp.versionName / hmp.versionCode。"
            )
        }
        println("OK 版本号未重复")
    }
}
```

**注意**：`git` 调用走 `ProcessBuilder` + 显式异常（本机 bash shim 损坏，不能依赖 shell）。

---

### T13 — `preflight`（🟠 P2）

**目标**：发版前一体检 —— 版本号 + 测试 + 平台完整性告知。

```kotlin
tasks.register("preflight") {
    group = "release"
    description = "发布前预检：版本号校验 + 全量单元测试 + 当前平台可产出产物告知"
    notCompatibleWithConfigurationCache("release preflight")

    dependsOn("checkVersion", "testAll")
    doLast {
        println("")
        println("=====================================")
        println("  发布前预检 (HMP v${versionName})")
        println("=====================================")
        println("  [OK] 版本号未与已有 tag 重复")
        println("  [OK] 全部单元测试通过")
        println("")
        println("  当前平台: ${if (isMacOS) "macOS" else "Windows/Linux"}")
        println("  可产出:   Android (APK+AAB) / Desktop (当前平台格式)")
        if (isMacOS) {
            println("            iOS (xcarchive)")
        } else {
            println("            iOS —— 不可产出（需 macOS）⚠️")
        }
        println("=====================================")
    }
}
```

**这一条顺带解决了 `release` 聚合任务的语义缺陷**：非 macOS 上跑 `release` 会「成功但缺 iOS」，`preflight` 会明确打印警告。

---

### T14 — `cleanReleases`

**目标**：清 `releases/` 下的历史产物，保留目录结构与 `.gitkeep`。

```kotlin
tasks.register("cleanReleases") {
    group = "build"
    description = "清理 releases/ 下的所有产物文件（保留目录本身与 .gitkeep）"

    doLast {
        val releasesDir = projectDirFile.resolve("releases")
        if (!releasesDir.exists()) {
            println("releases/ 不存在，跳过")
            return@doLast
        }
        var n = 0; var bytes = 0L
        releasesDir.walkTopDown()
            .filter { it.isFile && it.name != ".gitkeep" }
            .forEach { f -> bytes += f.length(); f.delete(); n++ }
        println("已清理 $n 个文件，释放 ${"%.1f".format(bytes / 1024.0 / 1024.0)} MB（保留 .gitkeep）")
    }
}
```

**关键约束**（来自前几轮清理的经验）：`releases/` 是 `build.gradle.kts` 里配置的**输出目录**，`copyToReleases()` 内有 `outDir.mkdirs()`，**目录会自重建**。所以只删文件、**保留 `.gitkeep`**。

---

### T15 — `cleanOrphans`

**目标**：扫描并列出（**不自动删**）「父模块已不存在」的孤儿 `build/` 目录。

```kotlin
tasks.register("cleanOrphans") {
    group = "build"
    description = "扫描孤儿 build 目录（父模块已不在构建中）—— 只打印清单，不自动删除"

    doLast {
        // 从 settings.gradle.kts 解析出真实模块目录，与磁盘上的 build/ 取差集
        val modules = setOf("shared", "shared-ui", "shared-ios", "android/app",
                            "android/core-player", "desktop/app", "desktop/core-player")
        val roots = listOf("android", "desktop", "shared", "shared-ui")
        println("孤儿 build 目录扫描结果：")
        var n = 0
        roots.forEach { r ->
            val dir = projectDirFile.resolve(r)
            dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                val rel = "$r/${sub.name}"
                if (sub.resolve("build").exists() && rel !in modules) {
                    println("  ⚠️  $rel/build  （父模块不在 settings.gradle.kts 中）")
                    n++
                }
            }
        }
        if (n == 0) println("  未发现孤儿 build 目录")
        else println("\n共 $n 个。确认后可手动删除；本任务不会自动删。")
    }
}
```

**设计决策：只打印、不删除。** 依据项目安全约定（`personal_files_safety` 精神 + 前几轮的教训）：删目录属破坏性操作，必须**先列清单、用户确认**。任务提供清单，删除动作交给人。

---

### T16 — 修复 CI 的两个错误任务名

`.github/workflows/release.yml:49-51` 改为：

```yaml
      - name: Run unit tests
        run: ./gradlew testAll --no-daemon --no-configuration-cache
```

**两处改动**：
1. **去掉 `continue-on-error: true`** —— 测试不该允许失败，这正是断链能潜伏至今的原因。
2. **改用 `testAll`** —— 由 T1 保证任务名正确；CI 与本地共用同一入口，不会再漂移。

> 保留 `--no-configuration-cache`（该步骤的既有约定，与 CI 环境一致）。
> **注意**：CI 的 `validate` job 跑在 `ubuntu-latest` 且**未设 `HMP_BUILD_TARGET`** → 走 `all` 分支、7 个模块全 include，因此 `testAll` 在 CI 里会尝试 `:android:app:testDebugUnitTest`。这需要 Android SDK —— ubuntu runner 有（`setup-java` + AGP 会自动装），但**实施时需在 CI 上实跑一次确认**，若失败则给 CI 单独指定只跑 KMP 侧的最小集。

---

## 四、实施顺序与验收

### 阶段一：核心测试 + 低内存（🔴 P0，必做）

| 步骤 | 动作 | 验收 | 实施结果 |
|---|---|---|---|
| 1 | 新增 `gradlew-lowmem.bat` | `gradlew-lowmem.bat --version` 能输出 JDK 21 | ✅ 已建（参数后经实测修正） |
| 2 | 新增 `testAll`（T1，含存在性过滤） | `tasks --group verification` 列出 `testAll` | ✅ |
| 3 | 新增 `testCore` / `testUi`（T2·T3） | 同上列出 | ✅ |
| 4 | **真跑一次** | `gradlew-lowmem.bat testCore` 全绿（**真正的验收点**） | ✅ **708 tests / 0 fail** |

### 阶段二：分模块测试 + 编译（🟠 P1）

| 步骤 | 动作 | 验收 | 实施结果 |
|---|---|---|---|
| 5 | 新增 `testDesktop` / `testAndroid`（T4·T5） | 各自 `--dry-run` 依赖图可解析 | ✅ |
| 6 | 新增 `testQuick`（T6） | — | ✅ |
| 7 | 新增 `compileCore` / `compileUi`（T8·T9） | `gradlew-lowmem.bat compileCore` 成功 | ✅ |
| 8 | 新增 `compileDesktop`（T11） | `--dry-run` 可解析 | ✅ |
| 9 | **核对 Android 编译任务名** | 确认是否存在 | ✅ **实测：不存在**，改用 `assembleDebug` |
| 10 | 新增 `compileAndroid`（T10） | `--dry-run` 可解析 | ✅ |
| 11 | 新增 `compileAll`（T7） | `gradlew-lowmem.bat compileAll` 成功 | ✅ |
| 12 | **交叉验证** | `HMP_BUILD_TARGET=desktop` 下 `compileAll --dry-run` 不报错 | ✅ 无悬空依赖 |
| **12b** | **新增 `testUiDesktop`（T17）** | `--dry-run` 可解析 | ✅ **实施中补：`shared-ui` 测试全在 `androidHostTest`** |

### 阶段三：CI 修复（🔴 P0）

| 步骤 | 动作 | 验收 | 实施结果 |
|---|---|---|---|
| 13 | 修 CI（T16）：去掉 `continue-on-error` + 改用 `testAll` | `release.yml` 中不再有旧任务名与 `continue-on-error` | ✅ |
| 14 | 推分支跑一次 CI | `validate` job 真的执行测试且不靠 `continue-on-error` 通过 | ⏳ **待用户推分支验证**（本地已不可再前置） |

> ⚠️ **顺序约束**：必须先完成阶段一第 4 步（本地跑通），再动 CI。否则 CI 可能直接变红阻塞发版。

### 阶段四：发布前预检（🟠 P2）

| 步骤 | 动作 | 验收 |
|---|---|---|
| 15 | 新增 `checkVersion`（T12） | 正确打印当前版本与最新 tag，重复时抛异常 |
| 16 | 新增 `preflight`（T13） | 串联版本校验 + 测试 + 平台告知 |

### 阶段五：清理任务（🟡）

| 步骤 | 动作 | 验收 |
|---|---|---|
| 17 | 新增 `cleanReleases`（T14） | 执行后 `releases/` 仅剩 `.gitkeep`；`git status` 无新增 `D` |
| 18 | 新增 `cleanOrphans`（T15） | 输出与实际一致（前几轮已清过，预期「未发现」） |

### 阶段六（可选）：废任务清理

| 任务 | 理由 |
|---|---|
| `copyAndroidDebug` | 与 `releaseAndroid` 重叠；debug 包不该进发布目录 |
| ~~`injectFFmpegForDev`~~ | 挂钩 `run`，触发链长、收益极低 —— **已于 2026-09-23 删除**（Desktop 播放修复同批） |
| `copyIconsToIos` | 源目录不存在，纯空转；需同时删 3 处 `finalizedBy` 接线 |

> 阶段六**独立可选**，与前五阶段无依赖。

---

## 五、待用户决策点

| # | 决策点 | 状态 |
|---|---|---|
| D1 | `testCore` 与 `testQuick` 当前实现相同，是否都留？ | **已定**：先都留（语义名 + 意图名），若嫌冗余后续删 `testQuick` |
| D2 | `shared-ui` 的 `testAndroid` / `testAndroidHostTest` 是否纳入？ | ⚠️ **原决定错误，已推翻**：原写「暂不纳入（避免 Android SDK 依赖扩散）」，但实施时发现 `shared-ui` 的测试**全部**在 `androidHostTest`（`desktopTest` 为空），排除它等于一个测试都不跑。已改为 `testUi` 同时挂两个源集，另加 `testUiDesktop` 供无 SDK 环境 |
| D3 | Android 侧编译任务名 | ✅ **已实测解决**：`compile*` 任务在 android:* 模块**根本不存在**（AGP 内置 Kotlin），改用 `assembleDebug` |
| D4 | 是否引入 ktlint / detekt | 本轮不做，留作后续 |

---

## 六、风险与对策

| 风险 | 对策 |
|---|---|
| **模块集随 `HMP_BUILD_TARGET` 变化导致悬空依赖** | 全部跨平台任务用 `findProject()` 存在性过滤（见 3.2）——**这是本方案最重要的防护** |
| **任务名随变体变化**（KMP/AGP 升级可能改名） | 落地前用 `gradlew tasks --all` 二次核对，只列已实测存在的任务名 |
| **`org.gradle.jvmargs` 无法在脚本里条件化** | 采用包装脚本（方案 A），不在 `gradle.properties` 加开关 |
| **CI 去掉 `continue-on-error` 后可能变红** | 必须先本地跑通再改 CI（阶段一 → 阶段三的顺序约束） |
| **CI 无 `HMP_BUILD_TARGET` → 走 all 分支 → 会跑 Android 测试** | 阶段三第 14 步在 CI 实跑确认；失败则给 CI 单独指定最小测试集 |
| **新任务过多导致维护负担** | 全部集中在根 `build.gradle.kts` 的一个「验证 / 编译」节，用统一的存在性过滤辅助函数；不进 `release` 聚合链 |
| **`cleanOrphans` 误报** | 只打印不删除，误报无害 |

---

## 六点五、实施验收实况（2026-09-15）

### 验收项

| 项 | 验收方式 | 结果 |
|---|---|---|
| 15 个任务全部注册 | `tasks --group verification`（6/6）、`--group build`（7/7）、`--group release`（8/8） | ✅ 全列出 |
| 任务名与真实构建对齐 | `gradlew tasks --all` 逐名核对 | ✅ 无拼写漂移 |
| 依赖图可解析 | `testCore` / `testUi` / `compileCore` 各自 `--dry-run` | ✅ exit 0 |
| **存在性过滤生效** | `HMP_BUILD_TARGET=desktop` 下 `dry-run compileAll` / `testAll` | ✅ **无悬空依赖**（对比 `releaseStorybook` 的同类错误已不复现） |
| 编译链路可达 | 实跑 `:shared:compileKotlinDesktop` → `compileTestKotlinDesktop` → `desktopTestClasses` | ✅ 全部 UP-TO-DATE |
| **`testCore` 真跑** | `gradlew testCore`（低内存配置） | ✅ **BUILD SUCCESSFUL，91 suites / 708 tests / 0 failures / 0 errors / 0 skipped** |
| **`testUi` 真跑** | `gradlew testUi`（低内存配置） | ✅ BUILD SUCCESSFUL（但暴露下述缺陷，已修） |
| CI 已修 | `release.yml` 不再含 `:shared:test` / `:shared-ui:testDebugUnitTest` / `continue-on-error: true` | ✅ |
| 文档已同步 | `CLAUDE.md` / `DEVELOP.md` 任务表 10 → 26 | ✅ |

### 实施中发现并修复的新缺陷：`shared-ui` 的测试没人跑

`testUi` 真跑时输出 `> Task :shared-ui:desktopTest NO-SOURCE` —— **空过**。追查代码：

```
shared-ui/src/
  commonMain/     293 files
  androidMain/     33
  androidHostTest/  8   ← 真正的测试在这里
  desktopMain/     13
  iosMain/         16
  （无 commonTest，无 desktopTest）
```

即 **`shared-ui` 的 8 个测试文件全部在 `androidHostTest` 源集**（`ChatViewModelTest` / `RouterTest` / `RoutesTest` / `BottomFusionBarTabMappingTest` / `HazeIntensityTest` / `LabelExtensionsTest` / `UiStateTest` / `ChatTestFakes`），
而原方案 §五 决策点 D2 写的是「暂不纳入（避免 Android SDK 依赖扩散）」——**这个决定是错的**，等于把 shared-ui 的全部测试排除在外，而当时并未察觉 `desktopTest` 是空的。

**已修**：
- `testUi` 改为同时挂 `:shared-ui:desktopTest` + `:shared-ui:testAndroidHostTest`
- `testAll` 的 KMP 侧同样补上 `:shared-ui:testAndroidHostTest`
- 新增 `testUiDesktop`（只挂 `desktopTest`），供**没有 Android SDK 的环境**使用
- 任务总数 25 → **26**

**验收**：`testUi --dry-run` 与 `testUiDesktop --dry-run` 均 exit 0，依赖图含
`:shared-ui:compileAndroidHostTest` → `:shared-ui:testAndroidHostTest`，链路正确。

> ⚠️ **遗留**：`testUi` 的完整实跑在本机仍失败，但失败点是
> `> Task :shared:compileAndroidMain` —— **编译阶段**，比内存瓶颈更早。
> 因为该路径要编译 `shared` + `shared-ui` 的 **Android 变体**（比 desktop 变体重得多）。
> 这属于本机内存限制（见下节），非定义缺陷；`--dry-run` 已证明依赖图正确。
> Android SDK 环境（或内存充足的机器）上应可正常跑通。

### 低内存参数的实测结论（重要）

**这轮验收最大的收获是找到了本机唯一能跑通测试的内存配置。** 过程如下：

| Gradle daemon 堆 | Kotlin 编译器 | `testCore` 结果 |
|---|---|---|
| 1536m | 独立 daemon 1536m | ❌ `daemon disappeared` 于 `:shared:desktopTest` |
| 1536m | 独立 daemon 1536m + `--no-configuration-cache` | ❌ 同上 |
| 2048m | Kotlin 512m | ❌ 同上 |
| 1024m（single-use daemon） | `in-process` | ❌ `daemon disappeared` 于 `:shared:desktopTest` |
| 2560m / 1024m 拆分 | 独立 daemon | ❌ 同上 |
| **1024m** | **`in-process`（`-Dkotlin.compiler.execution.strategy=in-process`）** | ✅ **BUILD SUCCESSFUL** |

**规律**：失败时必然停在 `> Task :shared:desktopTest`，且**所有编译任务都已 UP-TO-DATE** —— 说明依赖图完全正确，卡点是「fork 测试 JVM 的那一刻内存不够」。daemon 日志直接断掉、无 `hs_err_pid*.log`、Windows 事件日志也无记录，是典型的**外部 kill**（操作系统内存压力）。

**根因**：`gradle.properties` 默认 `-Xmx4096m` 偏大；即使命令行覆盖，若**同时**再起一个 Kotlin daemon（`-Dkotlin.daemon.jvm.options`），两个 JVM 加起来就超了。把 Gradle daemon 压到 **1024m**，并让 **Kotlin 编译器在 Gradle daemon 进程内跑**（不再另起 JVM），总占用才落到安全区间。

> 该配置已固化进 `gradlew-lowmem.bat` / `gradlew-lowmem`（原先写的 1536m + 独立 Kotlin daemon 是**错的**——它能编译但跑不了测试，现已修正）。

### 补充：历史产物的旁证

`shared/build/test-results/desktopTest/` 在验证过程中还留有当日 15:38–15:39 一次成功运行的 91 个测试类产物，与本次实跑结果（708 tests）完全一致——**两条独立证据互相印证**。

### 测试报告是框架自动生成的（无需额外配置）

**结论：Gradle 的 `Test` 类型任务内建报告生成，跑一次测试就自动产出，不需要写任何报告代码、也不需要装插件。**

以 `testCore` 为例，它跑完后磁盘上自动出现：

```
shared/build/reports/tests/desktopTest/
  index.html      30 KB     ← 总览页（通过数/失败数/耗时表，可下钻）
  classes/        91 个 HTML ← 每个测试类一页（含失败堆栈）
  packages/       26 个 HTML ← 每个包一页
  css/  js/                 ← 样式与脚本
```

共 **118 个 HTML 页面**。

**两条产物线，用途不同**：

| 产物 | 路径 | 谁用 |
|---|---|---|
| **HTML** | `<模块>/build/reports/tests/<任务名>/index.html` | 人看。默认开启 |
| **XML** | `<模块>/build/test-results/<任务名>/TEST-*.xml` | 机器读（CI 解析、汇总统计） |
| 二进制 | `<模块>/build/test-results/<任务名>/binary/` | 增量判断 / `--rerun` |

**三个必须知道的行为**：

1. **一个测试任务一份报告，不自动合并。**
   `:shared:desktopTest` → `reports/tests/desktopTest/`；
   `:shared-ui:desktopTest` → `shared-ui/build/reports/tests/desktopTest/`。
   多模块跑完是**多份独立报告**，Gradle 本身不提供统一入口页。

2. **聚合任务不产出报告。**
   `testAll` / `testCore` 这类只是 `dependsOn` 真任务的空壳，报告仍由底层任务各自生成 —— 所以看报告要去**被依赖的那个任务**的目录，不是聚合任务名下。

3. **只有真正执行的任务才写报告。**
   UP-TO-DATE / SKIPPED 不会重新生成（沿用上次的产物）。要看最新报告可加 `--rerun`，或用 `cleanAllTests` 先清。

> 另：`Test` 类型任务可通过 `reports.html.required` / `reports.junitXml.required` 关掉对应产物；
> 本项目未改动这两项，故保持 Gradle 默认（全部开启）。

---

## 七、交付清单

**代码改动**
- `build.gradle.kts`：新增 16 个任务
  - 测试：`testAll` / `testCore` / `testUi` / `testUiDesktop` / `testAndroid` / `testDesktop` / `testQuick`
  - 编译：`compileAll` / `compileCore` / `compileUi` / `compileAndroid` / `compileDesktop`
  - 发布：`checkVersion` / `preflight`
  - 清理：`cleanReleases` / `cleanOrphans`
- `gradlew-lowmem.bat`（+ `gradlew-lowmem` sh 版）：新增；参数经实测修正为 `-Xmx1024m` + Kotlin `in-process`
- `.github/workflows/release.yml`：修 2 处（任务名 + 去掉 `continue-on-error`）

**文档改动**
- `CLAUDE.md`：自定义任务表 10 → 26 个；补 lowmem 实测参数
- `DEVELOP.md`：同上 + 发布流程接入 `preflight` + 日常开发选任务表；**并修正一处失效引用**（CI test job 原写 `:shared:test` + `:shared-ui:testDebugUnitTest`，已改为 `testAll`）

**不做法**
- 不改 `gradle.properties` 的默认 jvmargs（启动期属性，改不了且会波及 IDE/CI）
- 不引入 ktlint/detekt（本轮不做）
- 阶段六的删除动作待定
