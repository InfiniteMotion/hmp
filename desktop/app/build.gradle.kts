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
//     - 二进制托管到本仓库 Release `ffmpeg-binaries`（github.com 同源），由发版人
//       手动上传；CI 只从 Release 同源下载，不负责上传。
//       （注：GitHub 网页上传单文件 ≤25MB，超大的 windows 包需走 API / gh CLI 上传。）
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
        version = "9.0",
        url = "https://github.com/InfiniteMotion/hmp/releases/download/ffmpeg-binaries/ffmpeg-9.0-macos-arm64.zip",
        sha256 = "5267ef149ee0d208057a1b316aac079b661b0476574dee5da7d225769773c603",
    ),
    "linux/amd64" to FFmpegArtifact(
        version = "9.0.2",
        url = "https://github.com/InfiniteMotion/hmp/releases/download/ffmpeg-binaries/ffmpeg-9.0.2-linux-amd64.zip",
        sha256 = "fa8ecf4abbd290d98f7d188b8649cc6b391ae209a98452be955a15aab1909d7f",
    ),
    "windows/amd64" to FFmpegArtifact(
        version = "9.0.2",
        url = "https://github.com/InfiniteMotion/hmp/releases/download/ffmpeg-binaries/ffmpeg-9.0.2-windows-amd64.zip",
        sha256 = "37e69a271258197a13187ac9864a558c7325b34885f4f77ef0aa316f226150be",
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

        // 归档落地文件：优先用仓库预置（ffmpeg-vendor/<os>/<arch>/ffmpeg[.exe]），
        // 缺失再联网下载。预置让 CI 不依赖第三方个人站点、字节可复现；
        // 下载则带重试，缓解 runner 出网抖动。两者都过同一道 SHA256 校验。
        val vendorFile = File(project.rootDir, "ffmpeg-vendor/$ffmpegArtifactKey/$ffmpegFileName")
        val archiveFile = File(ffmpegDir, "ffmpeg-download.tmp")

        if (vendorFile.isFile) {
            println("使用仓库预置 FFmpeg: $vendorFile")
            vendorFile.copyTo(archiveFile, overwrite = true)
        } else {
            println("下载 FFmpeg ${ffmpegArtifact.version} [$ffmpegArtifactKey]")
            println("  ${ffmpegArtifact.url}")
            var lastErr: Exception? = null
            repeat(3) { attempt ->
                try {
                    URI(ffmpegArtifact.url).toURL().openStream().use { input ->
                        archiveFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    lastErr = null
                    return@repeat
                } catch (e: Exception) {
                    lastErr = e
                    println("  下载失败（第 ${attempt + 1} 次），1s 后重试: ${e.message}")
                    Thread.sleep(1000)
                }
            }
            if (lastErr != null) {
                throw GradleException(
                    "FFmpeg 下载失败（已重试 3 次）: ${ffmpegArtifact.url}\n" +
                        "  最后一次错误: ${lastErr!!.message}\n" +
                        "  若 CI 出网受限，可把对应二进制放到 ffmpeg-vendor/$ffmpegArtifactKey/$ffmpegFileName 走预置通道。"
                )
            }
        }

        val actualSha256 = sha256Of(archiveFile)
        if (!actualSha256.equals(ffmpegArtifact.sha256, ignoreCase = true)) {
            archiveFile.delete()
            throw GradleException(
                "FFmpeg SHA256 校验失败，构建已中断。\n" +
                    "  期望: ${ffmpegArtifact.sha256}\n" +
                    "  实际: $actualSha256\n" +
                    "  来源: ${ffmpegArtifact.url}\n" +
                    if (vendorFile.isFile)
                        "  你预置的 ffmpeg-vendor/$ffmpegArtifactKey/$ffmpegFileName 与 ffmpegArtifacts 声明不一致，请同步 version/url/sha256。\n"
                    else
                        "  若上游确已更新，请同步更新 ffmpegArtifacts 中该条目的 version/url/sha256。\n"
            )
        }
        println("  SHA256 校验通过")

        val tempExtractDir = File(ffmpegDir, "extract")
        tempExtractDir.deleteRecursively()
        tempExtractDir.mkdirs()

        when (ffmpegArtifact.archive) {
            "zip" -> project.copy {
                from(project.zipTree(archiveFile))
                into(tempExtractDir)
            }
            "tar.xz" -> {
                val process = ProcessBuilder(
                    "tar", "xf", archiveFile.absolutePath, "-C", tempExtractDir.absolutePath
                ).inheritIO().start()
                if (process.waitFor() != 0) throw GradleException("tar 解包失败")
            }
            else -> throw GradleException("未知归档格式: ${ffmpegArtifact.archive}")
        }

        val found = tempExtractDir.walk().firstOrNull { it.isFile && it.name == ffmpegFileName }
            ?: throw GradleException("归档内未找到 $ffmpegFileName")
        found.copyTo(target, overwrite = true)
        target.setExecutable(true)

        archiveFile.delete()
        tempExtractDir.deleteRecursively()
        // 落指纹：下次构建据此判断「本地这份是不是当前产物」，无需重新下载。
        ffmpegMarker.writeText(ffmpegFingerprint)
        println("FFmpeg 就绪: $target")
    }
}

// ── Inject FFmpeg into the jlink runtime image ───────────────────────────
//
// 注入对象必须是 jlink runtime image，而不是 app image（compose/binaries/main）。
// 运行时 FFmpegAudioEngine 的第一个可用候选是 `java.home/bin/ffmpeg`，而 java.home
// 指向的正是被打进产物的那份 runtime image —— 所以往它根下补一个 bin/ 就能命中。
//
// 旧实现注入 compose/binaries/main 是错的（详见文末 Wiring 注释）：
// Linux 的 DEB、Windows 的 MSI **根本不经过** compose/binaries/main —— 它们是
// jpackage 直接拿 runtime image + libs 打的。于是 ffmpeg 从未进入这两个产物。

val injectFFmpeg by tasks.registering {
    group = "desktop"
    description = "把 FFmpeg 注入 jlink runtime image（必须早于所有格式打包）"
    notCompatibleWithConfigurationCache("uses project object references")
    dependsOn(downloadFFmpeg)

    doLast {
        val runtimeImageTask = tasks.matching { it.name == "createRuntimeImage" }.firstOrNull()
            ?: throw GradleException("未找到 createRuntimeImage 任务")

        // 取 Compose 内部任务的产出目录：公开 API 只有 TaskOutputs，取不到 destinationDir。
        // 优先按目录名挑，次选唯一产出 —— 避免插件日后多声明一个输出就崩。
        val dirs = runtimeImageTask.outputs.files.files.filter { it.isDirectory }
        val runtimeRoot = dirs.firstOrNull { it.name == "runtime" } ?: dirs.singleOrNull()
            ?: throw GradleException(
                "无法确定 createRuntimeImage 的产出目录（jlink runtime image）。\n" +
                    "  候选: $dirs\n" +
                    "  若 Compose 改了输出声明，这里要跟着改。"
            )

        val ffmpegSrc = File(ffmpegDir, ffmpegFileName)
        if (!ffmpegSrc.exists()) {
            throw GradleException("FFmpeg 尚未就绪，无法注入: $ffmpegSrc")
        }
        // 旧实现在这里写的是「找不到就 return」，静默跳过 —— FFmpeg 缺席正是这样
        // 连续几个版本没被发现。目标不存在必须炸，不能退化成打包机上的愿望清单。
        if (!runtimeRoot.isDirectory) {
            throw GradleException(
                "runtime image 不存在，FFmpeg 无法注入: $runtimeRoot\n" +
                    "  这几乎总是意味着 injectFFmpeg 跑早了 —— 检查 createRuntimeImage 的 finalizedBy。"
            )
        }

        val dest = File(File(runtimeRoot, "bin").also { it.mkdirs() }, ffmpegFileName)
        ffmpegSrc.copyTo(dest, overwrite = true)
        dest.setExecutable(true, false)

        // 写完就地验一遍：不做「复制了就当成功」的假设。
        if (!dest.isFile || !dest.canExecute()) {
            throw GradleException("FFmpeg 注入后校验失败: ${dest.absolutePath}")
        }
        println("OK Injected FFmpeg -> ${dest.absolutePath}")
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
                // Linux 只发 DEB。这里曾经是 Deb + AppImage，但那是错的期待：
                // Compose 的 TargetFormat.AppImage **不是** Linux AppImage 便携格式，
                // 它是 jpackage 的 `--type app-image` —— 产出的是解包目录
                // main/app/<包名>/（bin/ + lib/），与 createDistributable 完全同源，
                // 连输出目录都是同一个。源码里 AppImage.fileExt 直接抛
                // "cannot have a file extension"，全插件也从不调用 appimagetool。
                // 于是这条格式配了等于把 app image 又生成一遍（多花几分钟），
                // 而 `.AppImage` 文件永远不会存在。
                // 真正的 AppImage 要我们自己用 appimagetool 组装 AppDir，见 v7.2.2 待办。
                targetFormats(TargetFormat.Deb)
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

// ── FFmpeg 注入的时机：锚点是 jlink runtime image，不是 createDistributable ──
//
// 旧实现锚错了两处，导致 Linux DEB / Windows MSI **从未带上 FFmpeg**（2026-09 修复）：
//
//   1. 锚点不存在。非 macOS 的 deb/msi 根本不经过 createDistributable，Compose 只在
//      macOS 把 createDistributable 接到打包任务上（configureJvmApplication 仅 macOS
//      传 createAppImage），Linux/Windows 是 jpackage 拿 jlink runtime image + libs
//      直接打的。而 packageDistributionForCurrentOS 的图里压根没有 createDistributable，
//      finalizedBy 挂不上。
//   2. 于是 injectFFmpeg 只剩一条「在 package* 之前」的边，Gradle 就在 downloadFFmpeg
//      之后立刻把它跑了 —— 那时什么产物都还没有，它打印「未发现分发包目录，跳过注入」
//      后静默 return。整条链路 BUILD SUCCESSFUL，直到有人去翻 deb 的内容才发现。
//
// runtime image 是三端唯一的公共中间产物（macOS 的 app image 也是拿它 --runtime-image
// 打出来的），finalizedBy 它 = 在所有格式打包之前、且在 runtime 产出之后。
tasks.matching {
    it.name in setOf("packageDmg", "packageMsi", "packageDeb", "packageExe")
}.configureEach {
    dependsOn(injectFFmpeg)
}
tasks.matching { it.name == "createRuntimeImage" }.configureEach {
    finalizedBy(injectFFmpeg)
}

// 开发运行只需二进制就位：run 已通过 -Dhmp.ffmpeg.path 指向 build/ffmpeg/。
// 旧实现另注册 injectFFmpegForDev 往 user.home 写一份，在 macOS 上会污染
// ~/ffmpeg/bin/ 并覆盖用户自己装的 ffmpeg（且写的是 x86_64 构建）。
tasks.matching { it.name == "run" }.configureEach {
    dependsOn(downloadFFmpeg)
}
