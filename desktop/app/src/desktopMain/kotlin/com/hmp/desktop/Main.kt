package com.hmp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hmp.desktop.player.DesktopMusicController
import com.hearablemusic.player.ui.AppRoot
import com.hearablemusic.player.ui.common.design.theme.ThemeExtensionManager
import com.hearablemusic.player.ui.common.layout.LocalTitleBarInset
import com.hearablemusic.player.ui.common.util.LocalAppViewModelStoreOwner
import com.hmp.desktop.DwmHelper
import com.sun.jna.platform.win32.WinDef
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.koin.core.context.GlobalContext
import java.awt.EventQueue
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import com.hmp.initKermit
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

fun main() {
    val releaseBuild = System.getProperty("hmp.release-build")?.toBooleanStrictOrNull() ?: false
    initKermit(if (releaseBuild) Severity.Warn else Severity.Debug)
    val t0 = System.currentTimeMillis()

    // File-based startup log for diagnosing installed/packaged builds
    // where stdout is not visible. Written to %LOCALAPPDATA%\HMP\logs\.
    val fileLog: File? = try {
        val logDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "HMP/logs")
        logDir.mkdirs()
        val logFile = File(logDir, "startup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.log")
        logFile.createNewFile()
        logFile
    } catch (e: Throwable) {
        Logger.w(null, "Startup") { "Cannot create startup log file: ${e.message}" }
        null
    }

    fun stamp(label: String) {
        val line = "[Startup] +${System.currentTimeMillis() - t0}ms — $label"
        Logger.i(null, "Startup") { line }
        fileLog?.appendText(line + "\n")
    }

    // HiDPI scaling: must be set before any AWT/Compose class is loaded
    System.setProperty("sun.java2d.dpiaware", "true")
    System.setProperty("sun.java2d.scaling.enabled", "false")
    val isMacOS = System.getProperty("os.name", "").lowercase().contains("mac")
    System.setProperty("skiko.renderApi", if (isMacOS) "METAL" else "OPENGL")
    System.setProperty("awt.useSystemAAFontSettings", "on")

    stamp("main() entry, HiDPI properties set")

    // Single-instance guard: exit immediately if another instance is running
    if (!SingleInstanceGuard.tryAcquire()) {
        Logger.w(null, "Startup") { "HMP is already running. Exiting." }
        return
    }

    stamp("single-instance guard passed")

    // Register Koin modules (lazy — no instances created yet)
    HmpDesktopApplication.init()

    stamp("Koin init done")

    // Pre-warm JNA native library on background thread so the first
    // DwmHelper call in detectSystemDarkMode() doesn't pay the DLL load cost.
    thread(name = "hmp-jna-prewarm", isDaemon = true) {
        try { DwmHelper.isSystemDark() } catch (_: Throwable) {}
        stamp("JNA pre-warm done")
    }

    // Pre-warm heavy dependencies on a background thread.
    // This triggers Room database creation, DataStore, and core UseCase resolution
    // so Compose first frame is not blocked by I/O.
    val prewarmLatch = CountDownLatch(1)
    thread(name = "hmp-prewarm", isDaemon = true) {
        GlobalContext.get().get<DesktopMusicController>()
        stamp("background pre-warm done (Room+FFmpeg+UseCase resolved)")
        prewarmLatch.countDown()
    }

    application {
        stamp("compose first frame begin")

        // Pre-warm thread always finishes before compose enters.
        // Blocking await() returns near-instantly, eliminating the
        // LaunchedEffect coroutine dispatch delay of ~356ms.
        prewarmLatch.await()
        stamp("pre-warm completed, entering composition")
        val state = rememberWindowState(
            size = DpSize(1200.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )

        // nav3 desktop 返回接线——
        // NavDisplay 内部经 NavigationBackHandler 消费返回；非 Android 侧无系统返回手势，
        // 由根 DirectNavigationEventInput 驱动（Escape 键 → backCompleted → pop 栈）。
        // parent = null：显式声明根 dispatcher（默认从 CompositionLocal 找 parent，
        // 但此处正是 provides 之前，必须显式 null 才不抛 ISE）
        val backInput = remember { DirectNavigationEventInput() }
        val navEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
        DisposableEffect(navEventOwner) {
            navEventOwner.navigationEventDispatcher.addInput(backInput)
            onDispose { navEventOwner.navigationEventDispatcher.removeInput(backInput) }
        }

        // 应用级 VM owner（commonMain activityViewModel() 的 desktop 宿主）
        val appViewModelStoreOwner = remember { DesktopViewModelStoreOwner() }

        // Live system dark mode state — updated by platform theme watcher
        var systemIsDark by remember { mutableStateOf(detectSystemDarkMode()) }
        val timed0 = remember { stamp("detectSystemDarkMode() called"); 0 }

        // User theme preference ("light" / "dark" / "default")
        val settingsRepo = remember {
            val r = GlobalContext.get().get<com.hmp.domain.setting.SettingsRepository>()
            stamp("SettingsRepository resolved")
            r
        }
        val themeMode by settingsRepo.themeMode.collectAsState(initial = "default")

        // App-level dark mode: respects user override, falls back to system
        val appIsDark = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> systemIsDark
        }

        // Resolved synchronously — pre-warm thread already finished,
        // Koin cache hit, no suspension needed.
        val musicController = remember {
            stamp("resolving musicController synchronously")
            GlobalContext.get().get<DesktopMusicController>().also {
                stamp("musicController ready, rendering MainScreen")
            }
        }

        Window(
            onCloseRequest = {
                musicController.release()
                SystemTrayManager.dispose()
                SingleInstanceGuard.release()
                exitApplication()
            },
            state = state,
            undecorated = true,
            transparent = true,
            icon = painterResource("icon.png"),
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape -> {
                            // nav3 返回（NavDisplay 内部 pop back stack）
                            backInput.backCompleted()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        ) {
            val timedContent = remember { stamp("Window content first composition"); 0 }

            // Deferred platform theme setup — runs after first frame, avoiding
            // ~149ms composition delay for AWT lookup and DWM watcher registration.
            LaunchedEffect(Unit) {
                stamp("Deferred platform theme setup — entering")
                val awtWindow = java.awt.Window.getWindows().firstOrNull()
                if (isMacOS) {
                    watchMacOSTheme { isDark -> systemIsDark = isDark }
                } else {
                    if (awtWindow != null) {
                        val hwnd = getHwndFromAwt(awtWindow)
                        if (hwnd != null) DwmHelper.setWindowHandle(hwnd)
                    }
                    DwmHelper.watchSystemTheme { isDark -> systemIsDark = isDark }
                }
                stamp("Deferred platform theme setup — done")
            }

            // Clip entire window content to rounded corners.
            val cornerRadius = 20.dp
            val tColorScheme = System.currentTimeMillis()
            val staticColorScheme = ThemeExtensionManager.getColorScheme(appIsDark)
            stamp("ThemeExtensionManager.getColorScheme() done (took ${System.currentTimeMillis() - tColorScheme}ms)")
            MaterialTheme(colorScheme = staticColorScheme) {
                val timedMT = remember { stamp("MaterialTheme scope entered (first composition)"); 0 }
                val shape = RoundedCornerShape(cornerRadius)
                Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // musicController is already resolved — no LoadingScreen needed
                    DisposableEffect(Unit) {
                        onDispose { musicController.release() }
                    }

                    // Initialize system tray with playback controls
                    LaunchedEffect(Unit) {
                        val window = java.awt.Window.getWindows().firstOrNull()
                        SystemTrayManager.init(
                            onPlayPause = { musicController.togglePlayPause() },
                            onNext = { musicController.playNext() },
                            onPrev = { musicController.playPrevious() },
                            onShowWindow = {
                                EventQueue.invokeLater {
                                    window?.let {
                                        it.isVisible = true
                                        it.toFront()
                                        it.repaint()
                                    }
                                }
                            },
                            onExit = {
                                musicController.release()
                                SystemTrayManager.dispose()
                                SingleInstanceGuard.release()
                                exitApplication()
                            }
                        )
                    }

                    // Update tray tooltip when current song changes
                    val currentMusic by musicController.currentPlayingMusic.collectAsState()
                    LaunchedEffect(currentMusic) {
                        currentMusic?.let { music ->
                            val title = music.music.title.ifBlank { File(music.music.path).name }
                            val artist = music.music.artist.ifBlank { "Unknown Artist" }
                            SystemTrayManager.updateTooltip("$title - $artist")
                        }
                    }

                    // 入口 commonMain 共享层 AppRoot——
                    // 双 provides：应用级 VM owner（activityViewModel 契约）+ nav3 返回 dispatcher
                    //（NavDisplay 的 NavigationBackHandler 在非 Android 上从该 CompositionLocal 发现 dispatcher）
                    // LocalTitleBarInset：CustomTitleBar 悬浮叠加在 AppRoot 之上，内容需为其让位
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalAppViewModelStoreOwner provides appViewModelStoreOwner,
                        LocalNavigationEventDispatcherOwner provides navEventOwner,
                        LocalTitleBarInset provides TITLE_BAR_HEIGHT,
                    ) {
                        AppRoot(darkTheme = appIsDark)
                    }

                    // Collect playback state reactively for immersive title bar
                    // Uses collectAsState to stay in sync with MainScreen's same underlying StateFlow
                    val isPlaying by musicController.isPlaying.collectAsState()

                    // Title bar overlays on top — transparent when playing for immersive look
                    CustomTitleBar(
                        isDarkTheme = appIsDark,
                        isPlaying = isPlaying,
                        onMinimize = {
                            val awtWindow = java.awt.Window.getWindows().firstOrNull()
                            awtWindow?.let { WindowHelper.minimizeAwt(it) }
                        },
                        onClose = {
                            musicController.release()
                            SystemTrayManager.dispose()
                            SingleInstanceGuard.release()
                            exitApplication()
                        }
                    )
                }
            }
            }
        }
    }

    // Cleanup on normal exit (after application{} returns)
    SingleInstanceGuard.release()
}

/**
 * Detect system dark mode setting.
 * - Windows: reads registry via DwmHelper
 * - macOS: reads AppleInterfaceStyle via defaults(1)
 */
private fun detectSystemDarkMode(): Boolean {
    val os = System.getProperty("os.name", "").lowercase()
    return when {
        os.contains("win") -> {
            try { DwmHelper.isSystemDark() } catch (_: Throwable) { false }
        }
        os.contains("mac") -> {
            try {
                val proc = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start()
                proc.inputStream.bufferedReader().readText().trim() == "Dark"
            } catch (_: Throwable) { false }
        }
        else -> false
    }
}

/**
 * Poll AppleInterfaceStyle for theme changes on macOS.
 * Returns a dispose function. Runs on a daemon thread.
 */
private fun watchMacOSTheme(intervalMs: Long = 2000, callback: (isDark: Boolean) -> Unit): () -> Unit {
    val thread = Thread({
        try {
            var lastDark = detectSystemDarkMode()
            callback(lastDark)
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(intervalMs)
                val dark = detectSystemDarkMode()
                if (dark != lastDark) {
                    lastDark = dark
                    callback(dark)
                }
            }
        } catch (_: InterruptedException) { /* normal shutdown */ }
    }, "macos-theme-watcher")
    thread.isDaemon = true
    thread.start()
    return { thread.interrupt() }
}

/**
 * Extract the native HWND from an AWT Window via its peer.
 * Compose Desktop creates an AWT Window under the hood; the peer holds the HWND.
 */
private fun getHwndFromAwt(window: java.awt.Window): WinDef.HWND? {
    return try {
        // Access the peer field via reflection (package-private in java.awt.Component)
        val peerField = java.awt.Component::class.java.getDeclaredField("peer")
        peerField.isAccessible = true
        val peer = peerField.get(window) ?: return null

        // The hwnd field lives on WComponentPeer in the class hierarchy
        val hwndPtr = findFieldValue(peer, "hwnd") as? Long
        if (hwndPtr != null) {
            return WinDef.HWND(com.sun.jna.Pointer(hwndPtr))
        }
        Logger.w(null, "Main") { "Could not find hwnd field on peer" }
        null
    } catch (e: Throwable) {
        Logger.e(e, "Main") { "Could not extract HWND from AWT: ${e.message}" }
        null
    }
}

private fun findFieldValue(obj: Any, fieldName: String): Any? {
    var cls: Class<*>? = obj.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(obj)
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    return null
}
