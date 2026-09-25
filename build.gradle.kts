import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    id("org.jetbrains.compose") version libs.versions.composeMultiplatform.get() apply false
}

val versionName: String = findProperty("hmp.versionName")?.toString() ?: "unknown"
val versionCode: String = findProperty("hmp.versionCode")?.toString() ?: "0"
val projectDirFile: File = projectDir
val isMacOS: Boolean = org.gradle.internal.os.OperatingSystem.current().isMacOsX
val buildTargetLabel: String = System.getenv("HMP_BUILD_TARGET") ?: "all"

// ── Helper: 按模块存在性挂依赖 ──────────────────────────────────────────
//
// settings.gradle.kts 按环境变量 HMP_BUILD_TARGET 决定 include 哪些模块：
//   "desktop" → 含 :desktop:* 但**不含** :android:app
//   "android" → 含 :android:* 但**不含** :desktop:app
//   未设置    → 全部 7 个模块
//
// 因此跨平台任务**不能写死 dependsOn 目标**，否则在目标切换后会出现
// "Task with path '...' not found in root project" 的悬空依赖
// （历史上 releaseStorybook 就是这样失效的）。
//
// 用 findProject() 判断比硬编码模块集更稳 —— 模块列表变了也不用改任务定义。
fun Task.maybeDepends(path: String) {
    val projectPath = path.substringBeforeLast(':')
    if (rootProject.findProject(projectPath) != null) dependsOn(path)
}

// ── Helper: copy file to releases/ ──────────────────────────────────────

fun copyToReleases(src: File, destName: String, category: String) {
    val outDir = projectDirFile.resolve("releases/$category")
    outDir.mkdirs()
    if (src.exists()) {
        Files.copy(src.toPath(), outDir.resolve(destName).toPath(), StandardCopyOption.REPLACE_EXISTING)
        println("OK -> releases/$category/$destName")
    } else {
        println("!! Not found: $src")
    }
}

// ── 验证：单元测试 ──────────────────────────────────────────────────────
//
// 测试 JVM 内存限制：
//   本机内存受限时，测试任务会 fork 一个测试 JVM，它与 Gradle daemon 的堆叠加
//   后可能触发 OS 杀进程（典型现象：日志停在 "> Task :xxx:desktopTest"，
//   报 "gradle daemon disappeared unexpectedly"）。
//   这里把测试 JVM 堆限制在 1G 以内，给 daemon 留出余量。
//   需要更大堆的个别测试可在模块脚本里覆盖 maxHeapSize。
allprojects {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1024m"
        minHeapSize = "256m"
    }
}
//
// 背景：release.yml 原先调用 `:shared:test :shared-ui:testDebugUnitTest`，
// 但这两个任务名在当前构建中**都不存在**（shared 是 KMP 模块，无 `test`；
// shared-ui 无 android 变体的 `testDebugUnitTest`）。该步骤带
// `continue-on-error: true`，任务名报错被吞掉，导致 CI 名义上在跑测试、
// 实际从未执行过任何单元测试。
//
// 实测可用的测试任务（gradlew tasks --all，2026-09-15）：
//   shared        : desktopTest / allTests
//   shared-ui     : desktopTest / allTests / testAndroid / testAndroidHostTest
//   shared-ios    : allTests
//   android:app   : test / testDebugUnitTest / connectedDebugAndroidTest
//   android:core-player : test / testDebugUnitTest / connectedDebugAndroidTest
//   desktop:app / desktop:core-player : desktopTest / allTests
//
// 选型说明：
//   - 用 desktopTest 而非 allTests：allTests 在 shared/shared-ui 上会连带
//     iOS 目标，Windows 上因缺 iOS 工具链会失败。
//   - 不纳入 connectedDebugAndroidTest：需真机/模拟器，不属于单元测试。

tasks.register("testAll") {
    group = "verification"
    description = "运行当前构建目标下全部单元测试（自动跳过未纳入构建的模块）"
    notCompatibleWithConfigurationCache("test fan-out task")

    // KMP 侧（shared / shared-ui 恒在构建中）
    // 注：shared-ui 的 desktopTest 为空，真正的用例在 androidHostTest
    dependsOn(":shared:desktopTest", ":shared-ui:desktopTest", ":shared-ui:testAndroidHostTest")

    // 平台侧（按目标存在性过滤，见文件顶部 maybeDepends 说明）
    maybeDepends(":android:app:testDebugUnitTest")
    maybeDepends(":android:core-player:testDebugUnitTest")
    maybeDepends(":desktop:app:desktopTest")
    maybeDepends(":desktop:core-player:desktopTest")

    doLast {
        println("OK 全部单元测试通过（HMP_BUILD_TARGET=$buildTargetLabel）")
    }
}

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
    // androidHostTest 不需要真机/模拟器（它是 JVM 上的 Robolectric 风格宿主测试），
    // 但仍需 Android SDK —— Android SDK 缺失的环境可改用 testUiDesktop。
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
    description = "只测 Desktop 侧（需构建目标含 desktop）"
    maybeDepends(":desktop:app:desktopTest")
    maybeDepends(":desktop:core-player:desktopTest")
}

tasks.register("testAndroid") {
    group = "verification"
    description = "只测 Android 侧（需构建目标含 android）"
    maybeDepends(":android:app:testDebugUnitTest")
    maybeDepends(":android:core-player:testDebugUnitTest")
}

// ── 验证：编译（不含打包）──────────────────────────────────────────────
//
// 用途：改 shared 的公共 API 可能只让 shared-ui 编译失败，而这种破坏在
// 跑测试时未必暴露。per-module 编译是廉价的「契约破坏检测」，比全量测试快。
//
// 实测注意（2026-09-15）：android:app / android:core-player **不暴露任何
// compile* 任务**（它们使用 AGP 内置 Kotlin，见 gradle.properties 的
// android.disallowKotlinSourceSets=false）。因此 Android 侧编译校验改用
// assembleDebug —— 它含编译但不做 release 打包。

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
    description = "编译 Desktop 侧各模块（需构建目标含 desktop）"
    maybeDepends(":desktop:app:compileKotlinDesktop")
    maybeDepends(":desktop:core-player:compileKotlinDesktop")
}

tasks.register("compileAndroid") {
    group = "build"
    description = "编译 Android 侧各模块（需构建目标含 android）"
    // 注：这些模块无 compile* 任务，用 assembleDebug 作编译校验
    maybeDepends(":android:app:assembleDebug")
    maybeDepends(":android:core-player:assembleDebug")
}

tasks.register("compileAll") {
    group = "build"
    description = "编译当前构建目标下全部模块（不含发布打包）"
    dependsOn("compileCore", "compileUi", "compileDesktop")
    if (rootProject.findProject(":android:app") != null) dependsOn("compileAndroid")
}

// ── Android ──────────────────────────────────────────────────────────────

tasks.register("releaseAndroid") {
    group = "release"
    description = "构建 Android Release APK + AAB，输出到 releases/android/"
    notCompatibleWithConfigurationCache("release copy task")

    dependsOn(":android:app:assembleRelease", ":android:app:bundleRelease")

    doLast {
        copyToReleases(
            projectDirFile.resolve("android/app/build/outputs/apk/release/app-release.apk"),
            "HMP-v${versionName}-release.apk", "android"
        )
        copyToReleases(
            projectDirFile.resolve("android/app/build/outputs/bundle/release/app-release.aab"),
            "HMP-v${versionName}-release.aab", "android"
        )
    }
}

tasks.register("copyAndroidDebug") {
    group = "release"
    description = "构建 Android Debug APK，输出到 releases/android/"
    notCompatibleWithConfigurationCache("release copy task")

    dependsOn(":android:app:assembleDebug")

    doLast {
        copyToReleases(
            projectDirFile.resolve("android/app/build/outputs/apk/debug/app-debug.apk"),
            "HMP-v${versionName}-debug.apk", "android"
        )
    }
}

// ── iOS ──────────────────────────────────────────────────────────────────

tasks.register("releaseIos") {
    group = "release"
    description = "构建 iOS Release Archive (仅 macOS)"
    notCompatibleWithConfigurationCache("release copy task")

    if (isMacOS) {
        dependsOn(":shared-ios:linkPodReleaseFrameworkIosArm64")

        doLast {
            val outDir = projectDirFile.resolve("releases/ios")
            outDir.mkdirs()

            val workspace = projectDirFile.resolve("ios/HMP.xcworkspace")
            val archivePath = outDir.resolve("HMP-v${versionName}.xcarchive").absolutePath

            val pb = ProcessBuilder(
                "xcodebuild",
                "-workspace", workspace.absolutePath,
                "-scheme", "HMP",
                "-configuration", "Release",
                "-sdk", "iphoneos",
                "-archivePath", archivePath,
                "archive"
            )
            pb.inheritIO()
            val exitCode = pb.start().waitFor()
            if (exitCode != 0) throw GradleException("xcodebuild failed with exit code $exitCode")
            println("OK Archive -> releases/ios/HMP-v${versionName}.xcarchive")
        }
    } else {
        doLast {
            println("!! iOS build requires macOS, skipped")
        }
    }
}

// ── Desktop ──────────────────────────────────────────────────────────────

tasks.register("releaseDesktop") {
    group = "release"
    description = "构建 Desktop Release 分发包，输出到 releases/desktop/"
    notCompatibleWithConfigurationCache("release copy task")

    dependsOn(":desktop:app:packageDistributionForCurrentOS")

    doLast {
        val distDir = projectDirFile.resolve("desktop/app/build/compose/binaries/main")
        // 只有真正是单文件分发格式的才 copy；AppImage 不是文件产物（见 desktop/app 注释）
        val extensions = setOf("msi", "dmg", "deb")
        distDir.walk().filter { it.isFile && it.extension in extensions }.forEach { f ->
            copyToReleases(f, f.name, "desktop")
        }
    }
}

tasks.register("copyDesktopJar") {
    group = "release"
    description = "构建 Desktop Uber JAR，输出到 releases/desktop/"
    notCompatibleWithConfigurationCache("release copy task")

    dependsOn(":desktop:app:packageUberJarForCurrentOS")

    doLast {
        val jarDir = projectDirFile.resolve("desktop/app/build/compose/jars")
        jarDir.listFiles()?.firstOrNull { it.extension == "jar" }?.let {
            copyToReleases(it, "HMP-v${versionName}-desktop.jar", "desktop")
        }
    }
}

// ── Storybook ────────────────────────────────────────────────────────────
//
// 已移除：原 releaseStorybook 任务依赖 ":storybook:wasmJsBrowserProductionWebpack"，
// 但 :storybook 模块早在 380f225「refactor(build): 阶段性关闭 storybook 模块」中
// 就从 settings.gradle.kts 移出，导致该任务依赖无法解析、并连带 ./gradlew release 失败。
//
// 恢复步骤（待 storybook 模块重新纳入构建时）：
//   1. settings.gradle.kts 的三个 buildTarget 分支中补 include(":storybook")
//   2. 在此处恢复 releaseStorybook（依赖 :storybook:wasmJsBrowserProductionWebpack，
//      产物目录 storybook/build/kotlin-webpack/wasmJs/productionExecutable 与
//      storybook/build/processedResources/wasmJs/main，输出到 releases/storybook/）
//   3. 在下方 release 任务的 dependsOn 中重新加入 "releaseStorybook"
//
// storybook/ 源码与构建脚本保留未动。

// ── 总入口 ───────────────────────────────────────────────────────────────

tasks.register("release") {
    group = "release"
    description = "构建所有平台 Release 产物"
    notCompatibleWithConfigurationCache("release copy task")

    // 注：releaseStorybook 已随 :storybook 模块移出构建而移除，见上方「Storybook」节
    dependsOn("releaseAndroid", "releaseDesktop")
    if (isMacOS) dependsOn("releaseIos")

    doLast {
        val iosNote = if (!isMacOS) " (requires macOS)" else ""
        println("")
        println("=====================================")
        println("  HMP v${versionName} (build ${versionCode}) Release Done")
        println("=====================================")
        println("  Output:    releases/")
        println("  Android:   releases/android/")
        println("  Desktop:   releases/desktop/")
        println("  iOS:       releases/ios/${iosNote}")
        println("=====================================")
    }
}

// ── 发布前预检 ──────────────────────────────────────────────────────────

tasks.register("checkVersion") {
    group = "release"
    description = "校验版本号：未与已有 tag 重复、versionCode 与 versionName 换算一致且严格递增（CI 的 validate 复用本任务）"
    notCompatibleWithConfigurationCache("读 git 与上一 tag 的文件内容")

    doLast {
        // 本机 bash shim 损坏，不能走 shell，直接进程调用
        fun gitOut(vararg args: String): String {
            val pb = ProcessBuilder(listOf("git") + args)
            pb.directory(projectDirFile)
            pb.redirectErrorStream(true)
            return pb.start().inputStream.bufferedReader().use { it.readText() }
        }

        val latestTag = gitOut("tag", "--sort=-v:refname").lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.removePrefix("v")

        val currentCode = versionCode.toIntOrNull()
            ?: throw GradleException("hmp.versionCode 不是整数: $versionCode")

        println("当前版本: $versionName (code $currentCode)")
        println("最新 tag: ${latestTag ?: "(无)"}")

        // ① versionName ↔ versionCode 必须自洽（VERSIONING.md §3：MAJOR*10000 + MINOR*1000 + PATCH）
        val parts = versionName.split(".")
        if (parts.size != 3 || parts.any { it.toIntOrNull() == null }) {
            throw GradleException("hmp.versionName 必须是 MAJOR.MINOR.PATCH，当前: $versionName")
        }
        val derivedCode = parts[0].toInt() * 10000 + parts[1].toInt() * 1000 + parts[2].toInt()
        if (derivedCode != currentCode) {
            throw GradleException(
                "versionCode 与 versionName 不自洽：$versionName 应推得 $derivedCode，" +
                    "实际 $currentCode（见 docs/VERSIONING.md §3）。"
            )
        }

        if (latestTag != null && versionName == latestTag) {
            throw GradleException(
                "版本号 $versionName 与已有 tag v$latestTag 重复！" +
                    "请先更新 gradle.properties 的 hmp.versionName / hmp.versionCode。"
            )
        }

        // ② versionCode 严格递增 —— 回退的包在 Android 上直接装不上，CI 原来完全不检查
        if (latestTag != null) {
            val prev = gitOut("show", "v$latestTag:gradle.properties")
            val prevCode = Regex("(?m)^hmp\\.versionCode=(\\d+)\\s*$").find(prev)?.groupValues?.get(1)?.toIntOrNull()
            if (prevCode == null) {
                println("  [!] tag v$latestTag 的 gradle.properties 里读不到 versionCode，跳过递增校验")
            } else if (currentCode <= prevCode) {
                throw GradleException(
                    "versionCode 必须严格递增：v$latestTag 已是 $prevCode，当前 $currentCode。"
                )
            } else {
                println("  [OK] versionCode 递增：$prevCode → $currentCode")
            }
        }
        println("OK 版本号自洽、未重复且递增")
    }
}

tasks.register("checkReleaseConsistency") {
    group = "release"
    description = "以 gradle.properties 为唯一真源，核对站点 / iOS 工程 / 文档里的版本声明是否同步（只读校验，不改文件）"
    notCompatibleWithConfigurationCache("执行期读取多个仓库文件")

    doLast {
        val expect = versionName
        val drift = mutableListOf<String>()

        fun read(rel: String): String {
            val f = rootDir.resolve(rel)
            if (!f.isFile) throw GradleException("文件不存在：$rel")
            return f.readText()
        }

        fun check(label: String, rel: String, pattern: Regex, allowSuffix: Boolean = false) {
            val actual = try {
                pattern.find(read(rel))?.groupValues?.get(1)?.trim()
            } catch (e: Exception) {
                drift += "$label —— 读取失败：${e.message}"
                return
            }
            if (actual == null) {
                drift += "$label —— $rel 里找不到声明（模式：${pattern.pattern}）"
                return
            }
            val ok = if (allowSuffix) actual.startsWith(expect) else actual == expect
            if (!ok) drift += "$label —— $rel 里是 \"$actual\"，应为 $expect"
            else println("  [OK] $label = $actual")
        }

        println("以 gradle.properties 的 hmp.versionName=$expect 为真源核对：")

        check("站点版本源", "site/js/config.js", Regex("(?m)^\\s*version:\\s*'([^']+)'"))
        check("站点 JSON-LD", "site/index.html", Regex("\"softwareVersion\"\\s*:\\s*\"([^\"]+)\""))
        check("iOS 工程 CFBundleShortVersionString", "ios/HMP/project.yml", Regex("(?m)^\\s*CFBundleShortVersionString:\\s*\"([^\"]+)\""))
        check("iOS 工程 MARKETING_VERSION", "ios/HMP/project.yml", Regex("(?m)^\\s*MARKETING_VERSION:\\s*\"([^\"]+)\""))
        check("iOS Info.plist", "ios/HMP/HMP/Info.plist", Regex("CFBundleShortVersionString</key>\\s*<string>\\s*([^<]+?)\\s*</string>"))
        check("Xcode 工程配置", "ios/HMP/HMP.xcodeproj/project.pbxproj", Regex("(?m)^\\s*MARKETING_VERSION\\s*=\\s*([^;]+);"))
        check("shared-ios 框架版本", "shared-ios/src/iosMain/kotlin/com/hmp/ios/Anchor.kt",
            Regex("SHARED_IOS_FRAMEWORK_VERSION[^=]*=\\s*\"([^\"]+)\""), allowSuffix = true)

        // 发版记录类：存在性检查（不是抓值）
        listOf(
            "site/changelog.html" to "<!-- v$expect -->",
            "ROADMAP.md" to "### v$expect (",
        ).forEach { (rel, needle) ->
            val text = try {
                read(rel)
            } catch (e: Exception) {
                drift += "$rel —— 读取失败：${e.message}"
                return@forEach
            }
            if (!text.contains(needle)) drift += "$rel —— 没有本次版本的条目（找不到 \"$needle\"）"
            else println("  [OK] $rel 已有 v$expect 条目")
        }

        if (drift.isNotEmpty()) {
            throw GradleException(
                "版本声明不一致（真源 gradle.properties = $expect）：\n" +
                    drift.joinToString("\n") { "  - $it" } +
                    "\n\n发版前请同步这些文件，或把它们接上自动生成（见 TODO R34）。"
            )
        }
        println("OK 版本声明全部与真源一致")
    }
}

tasks.register("preflight") {
    group = "release"
    description = "发布前预检：版本号校验 + 全量单元测试 + 当前平台可产出产物告知"
    notCompatibleWithConfigurationCache("release preflight")

    dependsOn("checkVersion", "checkReleaseConsistency", "testAll")

    doLast {
        println("")
        println("=====================================")
        println("  发布前预检 (HMP v$versionName)")
        println("=====================================")
        println("  [OK] 版本号自洽 / 未重复 / 递增，且站点与 iOS 声明一致")
        println("  [OK] 全部单元测试通过")
        println("")
        println("  构建目标: $buildTargetLabel")
        println("  当前平台: ${if (isMacOS) "macOS" else "Windows/Linux"}")
        println("  可产出:   Android (APK+AAB) / Desktop (当前平台格式)")
        if (isMacOS) {
            println("            iOS (xcarchive)")
        } else {
            println("            iOS —— 不可产出（需 macOS）")
        }
        println("=====================================")
    }
}

// ── 清理 ────────────────────────────────────────────────────────────────

tasks.register("cleanReleases") {
    group = "build"
    description = "清理 releases/ 下的所有产物文件（保留目录本身与 .gitkeep）"

    doLast {
        val releasesDir = projectDirFile.resolve("releases")
        if (!releasesDir.exists()) {
            println("releases/ 不存在，跳过")
            return@doLast
        }
        // 注：releases/ 是 copyToReleases() 配置的输出目录（内部有 mkdirs()，
        //     目录会自重建），因此只删文件、保留 .gitkeep，不动目录结构。
        var count = 0
        var bytes = 0L
        releasesDir.walkTopDown()
            .filter { it.isFile && it.name != ".gitkeep" }
            .forEach { f ->
                bytes += f.length()
                if (f.delete()) count++
            }
        val mb = String.format("%.1f", bytes / 1024.0 / 1024.0)
        println("已清理 $count 个文件，释放 $mb MB（保留 .gitkeep）")
    }
}

tasks.register("cleanOrphans") {
    group = "build"
    description = "列出孤儿 build 目录（父模块已不在构建中）—— 只打印清单，不自动删除"

    doLast {
        // 判定：父模块目录下有 build/，但该模块不在 settings.gradle.kts 中
        val knownModules = setOf(
            "android/app", "android/core-player",
            "desktop/app", "desktop/core-player",
            "shared", "shared-ui", "shared-ios",
        )
        val scanRoots = listOf("android", "desktop", "shared", "shared-ui")
        println("孤儿 build 目录扫描结果：")
        var found = 0
        scanRoots.forEach { root ->
            val dir = projectDirFile.resolve(root)
            if (!dir.isDirectory) return@forEach
            dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                val rel = "$root/${sub.name}"
                if (sub.resolve("build").exists() && rel !in knownModules) {
                    println("  [孤儿] $rel/build  （父模块不在 settings.gradle.kts 中）")
                    found++
                }
            }
        }
        if (found == 0) {
            println("  未发现孤儿 build 目录")
        } else {
            println("")
            println("共 $found 个。确认后请手动删除；本任务不会自动删。")
        }
    }
}
