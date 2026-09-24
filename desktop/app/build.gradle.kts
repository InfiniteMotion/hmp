import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("org.jetbrains.compose")
    alias(libs.plugins.kotlin.compose)
}

// Desktop packaging requires a full JDK with jpackage (Android Studio JBR lacks it)
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":desktop:core-player"))
                implementation(project(":shared-ui"))
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.material3)
                implementation(libs.koin.core)
                implementation(libs.kermit)
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
    }
}

// ── FFmpeg 获取（按 OS + CPU 架构固定来源，SHA256 校验）──────────────────
//
// 为什么这么写（2026-09 修复）：
//   旧实现只判断操作系统（isMacOS），未判断 CPU 架构，于是 Apple Silicon 上从
//   evermeet.cx 拿到 x86_64 二进制（该站只出 Intel 构建），未装 Rosetta 时直接
//   `Exec failed, error: 86 (Bad CPU type in executable)`。
//   现改为按 (OS, 架构) 精确选源，并把 URL 与 SHA256 一并固定：
//     - 可复现：同一 commit 在任何机器上拿到同一份字节；
//     - 可核验：校验失败立即中断构建，不会静默用错二进制；
//     - 不进 git：二进制落在 build/ffmpeg/ 下由 Gradle 缓存，仓库不膨胀。
//
// 架构以「构建机」为准：jpackage 打包的是同架构 JRE，产物本身即单架构，
// 因此 ffmpeg 匹配构建机 == 匹配目标用户。

val ffmpegDir = layout.buildDirectory.dir("ffmpeg").get().asFile
val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
val isMacOS = org.gradle.internal.os.OperatingSystem.current().isMacOsX
val isLinux = org.gradle.internal.os.OperatingSystem.current().isLinux

val ffmpegFileName = if (isWindows) "ffmpeg.exe" else "ffmpeg"

data class FFmpegArtifact(
    val version: String,
    val url: String,
    val sha256: String,
    val archive: String = "zip",
)

// 升级 FFmpeg 时同步更新 version / url / sha256 三者。
val ffmpegArtifacts: Map<String, FFmpegArtifact> = mapOf(
    "macos/arm64" to FFmpegArtifact(
        version = "8.1.1",
        url = "https://ffmpeg.martin-riedl.de/download/macos/arm64/1778761665_8.1.1/ffmpeg.zip",
        sha256 = "a05b1a47bb3ac89a95a55eec713f8bbb347051bb07015f3b7d08fb62ed81a21e",
    ),
    "macos/amd64" to FFmpegArtifact(
        version = "8.1.1",
        url = "https://ffmpeg.martin-riedl.de/download/macos/amd64/1778768838_8.1.1/ffmpeg.zip",
        sha256 = "8cb711bfa6f66033112d708dc275220419d0fdb49c5b752f8db25f11a92d321f",
    ),
    "linux/amd64" to FFmpegArtifact(
        version = "8.1.1",
        url = "https://ffmpeg.martin-riedl.de/download/linux/amd64/1778762264_8.1.1/ffmpeg.zip",
        sha256 = "50b9360d9f0de1555bb4dd354c708427027562624d553e93bb26060059bef16a",
    ),
    "linux/arm64" to FFmpegArtifact(
        version = "8.1.1",
        url = "https://ffmpeg.martin-riedl.de/download/linux/arm64/1778760876_8.1.1/ffmpeg.zip",
        sha256 = "5499ff0fb22b051f21f1458ebfb461ab1994467f037b911f4188ddac6c189037",
    ),
    "windows/amd64" to FFmpegArtifact(
        version = "9.0.2",
        url = "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip",
        sha256 = "60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba",
    ),
)

val hostOs: String = when {
    isWindows -> "windows"
    isMacOS -> "macos"
    isLinux -> "linux"
    else -> throw GradleException("不支持的 FFmpeg 目标系统: ${System.getProperty("os.name")}")
}

val hostArch: String = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    "x86_64", "amd64" -> "amd64"
    else -> throw GradleException("不支持的 FFmpeg 目标架构: ${System.getProperty("os.arch")}")
}

// Windows on ARM 无独立构建，回落 x64（系统自带 x64 模拟层，可直接运行）。
val ffmpegArtifactKey: String = if (hostOs == "windows") "windows/amd64" else "$hostOs/$hostArch"

val ffmpegArtifact: FFmpegArtifact = ffmpegArtifacts[ffmpegArtifactKey]
    ?: throw GradleException("未为 $ffmpegArtifactKey 固定 FFmpeg 产物，请补充 ffmpegArtifacts")

// 已就绪指纹：把「选了哪个产物」写进磁盘，供 downloadFFmpeg 判断是否复用本地二进制。
// 只靠 `target.exists()` 不够 —— 上一版在 Apple Silicon 上下错过 x86_64 构建，
// 本地那份坏二进制会一直被复用（并被注入 DMG）。指纹变了就必须重下。
val ffmpegFingerprint =
    "${ffmpegArtifactKey}@${ffmpegArtifact.version} sha256=${ffmpegArtifact.sha256}"

val ffmpegMarker = File(ffmpegDir, "ffmpeg-artifact.txt")

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadFFmpeg by tasks.registering {
    group = "desktop"
    description = "下载并校验 FFmpeg 静态二进制（按当前 OS + CPU 架构固定来源）"
    notCompatibleWithConfigurationCache("uses project object references")
    // 产物选择作为输入：换架构 / 换版本 / 换 URL 都会让本任务自动失效重跑，
    // 不会沿用上一轮（可能架构不匹配）的产物。
    inputs.property("artifactKey", ffmpegArtifactKey)
    inputs.property("version", ffmpegArtifact.version)
    inputs.property("sha256", ffmpegArtifact.sha256)
    outputs.file(File(ffmpegDir, ffmpegFileName))
    outputs.file(ffmpegMarker)

    doLast {
        ffmpegDir.mkdirs()
        val target = File(ffmpegDir, ffmpegFileName)
        // 「已就绪」= 文件在 **且** 指纹与当前产物一致。两者缺一都重下，
        // 否则旧二进制（缺指纹或指纹过期）会被继续打包进产物。
        val markerMatches = ffmpegMarker.exists() && ffmpegMarker.readText().trim() == ffmpegFingerprint
        if (target.exists() && markerMatches) {
            println("FFmpeg 已就绪，跳过下载: $target")
            return@doLast
        }
        if (target.exists()) {
            println("FFmpeg 与当前产物不符（$ffmpegArtifactKey ${ffmpegArtifact.version}），重新下载")
            target.delete()
        }

        println("下载 FFmpeg ${ffmpegArtifact.version} [$ffmpegArtifactKey]")
        println("  ${ffmpegArtifact.url}")
        val tempFile = File(ffmpegDir, "ffmpeg-download.tmp")
        URI(ffmpegArtifact.url).toURL().openStream().use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }

        val actualSha256 = sha256Of(tempFile)
        if (!actualSha256.equals(ffmpegArtifact.sha256, ignoreCase = true)) {
            tempFile.delete()
            throw GradleException(
                "FFmpeg SHA256 校验失败，构建已中断。\n" +
                    "  期望: ${ffmpegArtifact.sha256}\n" +
                    "  实际: $actualSha256\n" +
                    "  来源: ${ffmpegArtifact.url}\n" +
                    "  若上游确已更新，请同步更新 ffmpegArtifacts 中该条目的 version/url/sha256。"
            )
        }
        println("  SHA256 校验通过")

        val tempExtractDir = File(ffmpegDir, "extract")
        tempExtractDir.deleteRecursively()
        tempExtractDir.mkdirs()

        when (ffmpegArtifact.archive) {
            "zip" -> project.copy {
                from(project.zipTree(tempFile))
                into(tempExtractDir)
            }
            "tar.xz" -> {
                val process = ProcessBuilder(
                    "tar", "xf", tempFile.absolutePath, "-C", tempExtractDir.absolutePath
                ).inheritIO().start()
                if (process.waitFor() != 0) throw GradleException("tar 解包失败")
            }
            else -> throw GradleException("未知归档格式: ${ffmpegArtifact.archive}")
        }

        val found = tempExtractDir.walk().firstOrNull { it.isFile && it.name == ffmpegFileName }
            ?: throw GradleException("归档内未找到 $ffmpegFileName")
        found.copyTo(target, overwrite = true)
        target.setExecutable(true)

        tempFile.delete()
        tempExtractDir.deleteRecursively()
        // 落指纹：下次构建据此判断「本地这份是不是当前产物」，无需重新下载。
        ffmpegMarker.writeText(ffmpegFingerprint)
        println("FFmpeg 就绪: $target")
    }
}

// ── Inject FFmpeg into native distribution ──────────────────────────────

// FFmpeg 注入分两条通道，互为保险：
//   ① jpackage `--resource-dir`：CMP 对每种格式（DMG/MSI/DEB/AppImage）都会传，
//      落点 $APPDIR/resources/ffmpeg，运行时按 `compose.application.resources.dir` 命中。
//      这条通道**不依赖任务顺序**，是 Windows/Linux 唯一的通道（见下方长注释）。
//   ② app image 的 runtime/bin（即运行时 java.home/bin）：仅 macOS 的 DMG 会经过
//      createDistributable，作为 ① 之外的第二落点保留。
//
// ① 的投放：把下载好的 ffmpeg 声明为 prepareAppResources（CMP 的 Sync 任务，产出目录就是
// jpackage `--resource-dir` 的来源）的**输入文件**，由 Sync 自己搬运到目的地。
// 不能用 doLast/doFirst 拷贝：本项目未启用 appResourcesRootDir，该任务在无输入时是
// NO-SOURCE，而 NO-SOURCE 会跳过**整个任务的所有动作**（本机两次实测，doLast/doFirst 都不执行）。
// 声明成输入后任务不再 NO-SOURCE，且 ffmpeg 变化会让 Sync 自动重跑。
//
// 不挂在 injectFFmpeg 上的原因：injectFFmpeg 被 createDistributable 以 finalizedBy 绑定，
// 而 createDistributable 依赖 prepareAppResources，反向依赖会成环
// （实测 "Circular dependency between the following tasks"）。
// prepareAppResources 由 compose 插件在脚本求值之后才注册，故要等 afterEvaluate 才能按名取到。
afterEvaluate {
    tasks.named<Sync>("prepareAppResources") {
        // ffmpeg 是 downloadFFmpeg 的产物，Sync 直接引用它 → 必须显式声明依赖
        // （否则 Gradle 9 以「uses this output ... without declaring an explicit dependency」直接失败）
        dependsOn(downloadFFmpeg)
        // 注入的 ffmpeg 必须盖在 Sync 的 into() 之后，否则 from 会被改写到别的子目录
        val destDir = destinationDir
        from(File(ffmpegDir, ffmpegFileName)) { into(".") }
        into(destDir)
    }
}

val injectFFmpeg by tasks.registering {
    group = "desktop"
    description = "把 FFmpeg 注入 app image 的 runtime/bin（通道 ②，macOS DMG 路径）"
    notCompatibleWithConfigurationCache("uses project object references")
    dependsOn(downloadFFmpeg)

    doLast {
        val binDir = layout.buildDirectory.dir("compose/binaries/main").get().asFile
        if (!binDir.exists()) {
            // Windows/Linux 不产出 main/app（根因见 TODO R32），通道 ① 已覆盖，不再当失败
            println("· 无 app image 目录（Windows/Linux 正常），资源目录通道已覆盖")
            return@doLast
        }

        val ffmpegSrc = File(ffmpegDir, ffmpegFileName)
        if (!ffmpegSrc.exists()) {
            throw GradleException("FFmpeg 尚未就绪，无法注入: $ffmpegSrc")
        }

        // 归一到「装着 JVM 的那一层」= 运行时 java.home（jlink 布局：Windows 是
        // bin/server/jvm.dll，macOS/Linux 是 lib/server/libjvm.*）；macOS 的 runtime 里
        // 原本没有 bin 层，故缺就补建。
        val roots = binDir.walk().filter { it.isDirectory }.mapNotNull { d ->
            when {
                // macOS / Linux：java.home 这一层直接带 lib/server
                File(d, "lib/server").isDirectory -> d
                // Windows：server 在 bin/ 下，往上两级才是 java.home
                d.name == "server" && d.parentFile?.name == "bin" -> d.parentFile?.parentFile
                else -> null
            }
        }.distinctBy { it.absolutePath }.toList()

        // 旧实现在找不到目标时静默通过，正是「DMG 里没有 ffmpeg」被长期掩盖的原因
        if (roots.isEmpty()) {
            throw GradleException(
                "app image 已生成但未找到 jlink runtime（判据：lib/server 或 bin/server），FFmpeg 未注入。\n" +
                    "  binDir=$binDir\n" +
                    "  实际目录树（前 80 项，据此核对布局）：\n" +
                    binDir.walk().filter { it.isDirectory }.take(80)
                        .joinToString("\n") { "    " + it.relativeTo(binDir).path }
            )
        }
        val targets = roots.map { File(it, "bin").also { b -> b.mkdirs() } }

        for (target in targets) {
            val dest = File(target, ffmpegFileName)
            ffmpegSrc.copyTo(dest, overwrite = true)
            dest.setExecutable(true)
            println("OK Injected FFmpeg -> ${dest.absolutePath}")
        }
    }
}

// ── Application Configuration ───────────────────────────────────────────

compose.desktop {
    application {
        mainClass = "com.hmp.desktop.MainKt"

        jvmArgs += listOf(
            "-Xmx512m",
            "-Dfile.encoding=UTF-8",
            // Release 级别裁剪：命令行 -Phmp.release-build=true 时注入 Severity.Warn；
            // 默认 false → Severity.Debug（开发 run）。CI 构建在 release.yml 里带此属性。
            "-Dhmp.release-build=${project.findProperty("hmp.release-build")?.toString()?.toBoolean() == true}",
            // AWT DPI awareness — prevent Windows from applying bitmap upscaling
            "-Dsun.java2d.dpiaware=true",
            "-Dsun.java2d.scaling.enabled=false",
            // Skiko rendering pipeline — METAL on macOS, OpenGL elsewhere
            if (isMacOS) "-Dskiko.renderApi=METAL" else "-Dskiko.renderApi=OPENGL",
            "-Dskiko.vsync.enabled=false",
            // FFmpeg path for development (downloaded by downloadFFmpeg task)
            "-Dhmp.ffmpeg.path=${ffmpegDir.absolutePath}/$ffmpegFileName",
            // HiDPI text rendering
            "-Dawt.useSystemAAFontSettings=on",
            // Startup optimization: tiered compilation level 1 for faster class loading
            "-XX:+TieredCompilation",
            "-XX:TieredStopAtLevel=1",
            // Required for accessing AWT peer internals (HWND extraction on Windows)
            "--add-opens", "java.desktop/java.awt=ALL-UNNAMED"
        ) + (if (!isMacOS) listOf("--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED") else emptyList())

        nativeDistributions {
            modules += listOf(
                "java.net.http",       // Ktor HTTP client
                "jdk.unsupported",     // sun.misc.Unsafe
                "java.desktop",        // javax.sound
                "java.management",     // JMX
                "java.sql",            // Room/JDBC
                "java.transaction.xa"  // JDBC transactions
            )
            packageName = "HMP"
            packageVersion = project.findProperty("hmp.versionName")?.toString() ?: "1.0.0"
            description = "Hearable Music Player - A cross-platform local music player"
            vendor = "HMP"

            // Format must match current OS — plugin validates at configuration time
            if (isMacOS) {
                targetFormats(TargetFormat.Dmg)
            } else if (isWindows) {
                targetFormats(TargetFormat.Msi)
            } else {
                targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            }

            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
                bundleID = "com.hmp.desktop"
                dmgPackageVersion = "1"
            }

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                menu = true
                dirChooser = true
                shortcut = true
                // Consistent UpgradeCode ensures MSI detects previous version for in-place upgrade.
                // This UUID MUST remain the same across all versions — do NOT change it.
                upgradeUuid = "6ec556dd-5375-494f-ab38-f19bcdb497e7"
                // Explicit MSI package version — allows upgrade even if version format changes
                msiPackageVersion = project.findProperty("hmp.versionName")?.toString() ?: "1.0.0"
            }

            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
                debMaintainer = "hmp@hearmusic.app"
            }
        }
    }
}

// FFmpeg 注入必须发生在「app image 生成之后、格式打包之前」。
// 旧实现挂在 packageDistributionForCurrentOS 的 finalizedBy 上 —— finalizer 在整个
// 打包管线跑完之后才执行，此时 DMG 早已由 app image 打好，于是 ffmpeg 从未进入产物。
tasks.matching { it.name == "createDistributable" }.configureEach {
    finalizedBy(injectFFmpeg)
}
// prepareAppResources 是 CMP 的 Sync 任务，它的产出目录就是 jpackage `--resource-dir`
// 的来源。通道 ① 的投放挂在上面的 tasks.matching 块里，这里不再需要任何反向依赖
// （挂了会与 createDistributable 成环）。
tasks.matching {
    it.name in setOf("packageDmg", "packageMsi", "packageDeb", "packageAppImage")
}.configureEach {
    dependsOn(injectFFmpeg)
}

// 开发运行只需二进制就位：run 已通过 -Dhmp.ffmpeg.path 指向 build/ffmpeg/。
// 旧实现另注册 injectFFmpegForDev 往 user.home 写一份，在 macOS 上会污染
// ~/ffmpeg/bin/ 并覆盖用户自己装的 ffmpeg（且写的是 x86_64 构建）。
tasks.matching { it.name == "run" }.configureEach {
    dependsOn(downloadFFmpeg)
}
