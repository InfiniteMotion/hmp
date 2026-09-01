package com.hmp.desktop

import com.sun.jna.NativeLibrary
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import co.touchlab.kermit.Logger

/**
 * Windows DWM API helper — disables Mica/Acrylic, sets solid title bar color,
 * and listens to system theme changes via registry polling.
 */
object DwmHelper {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
    private const val DWMWA_SYSTEMBACKDROP_TYPE = 38
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMSBT_NONE = 0
    private const val DWMWCP_ROUND = 2

    private val THEME_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private val THEME_VALUE = "AppsUseLightTheme"

    // Cached HWND — set once from the AWT window, reused for all DWM calls
    @Volatile
    private var cachedHwnd: WinDef.HWND? = null

    /**
     * Register the main application window handle for DWM operations.
     * Call this once from the AWT Window reference after the window is created.
     */
    fun setWindowHandle(hwnd: WinDef.HWND) {
        cachedHwnd = hwnd
        Logger.i(null, "DwmHelper") { "Window handle registered: ${hwnd.pointer}" }
    }

    /** Start native window drag from the cached HWND (for Aero Snap support). */
    fun startDrag() {
        cachedHwnd?.let { WindowHelper.startDrag(it) }
    }

    /**
     * Apply solid-color title bar matching the given background color.
     */
    fun apply(captionArgb: Int) {
        if (!isWindows()) return

        val hwnd = cachedHwnd ?: findWindowByPid()
        if (hwnd == null) {
            Logger.w(null, "DwmHelper") { "No window handle available" }
            return
        }
        cachedHwnd = hwnd

        val luminance = captionArgb.luminance()
        val isDark = luminance < 0.5
        val textArgb = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        Logger.d(null, "DwmHelper") { "apply: caption=#${Integer.toHexString(captionArgb)} isDark=$isDark hwnd=${hwnd.pointer}" }

        dwmSetAttr(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_NONE)
        dwmSetAttr(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (isDark) 1 else 0)
        dwmSetAttr(hwnd, DWMWA_CAPTION_COLOR, argbToAbgr(captionArgb))
        dwmSetAttr(hwnd, DWMWA_TEXT_COLOR, argbToAbgr(textArgb))
    }

    /**
     * Extend the client area into the native title bar region so the app
     * background renders behind the caption buttons (Win11-style integration).
     */
    fun extendFrameIntoClientArea(hwnd: WinDef.HWND) {
        if (!isWindows()) return
        try {
            val lib = NativeLibrary.getInstance("dwmapi")
            val fn = lib.getFunction("DwmExtendFrameIntoClientArea")
            val margins = MARGINS().apply {
                cxLeftWidth = 0
                cxRightWidth = 0
                cyTopHeight = -1
                cyBottomHeight = 0
            }
            margins.write()
            val hr = fn.invokeInt(arrayOf(hwnd, margins))
            if (hr != 0) {
                Logger.w(null, "DwmHelper") { "DwmExtendFrameIntoClientArea failed: HRESULT=0x${Integer.toHexString(hr)}" }
            }
            margins.read()
        } catch (e: Throwable) {
            Logger.e(e, "DwmHelper") { "DwmExtendFrameIntoClientArea exception: ${e.message}" }
        }
    }

    /**
     * Enable native rounded corners (Win11). No-op on older Windows versions.
     */
    fun enableRoundedCorners(hwnd: WinDef.HWND) {
        if (!isWindows()) return
        dwmSetAttr(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND)
    }

    /**
     * Read the current Windows system dark mode preference.
     * @return true = dark mode, false = light mode.
     */
    fun isSystemDark(): Boolean {
        if (!isWindows()) return false
        return try {
            Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER, THEME_KEY, THEME_VALUE
            ) == 0
        } catch (_: Throwable) { false }
    }

    /**
     * Poll the system theme every [intervalMs] and invoke [callback] when it changes.
     * Returns a dispose function. Runs on a daemon thread.
     */
    fun watchSystemTheme(intervalMs: Long = 2000, callback: (isDark: Boolean) -> Unit): () -> Unit {
        if (!isWindows()) return {}

        val thread = Thread({
            try {
                var lastDark = isSystemDark()
                callback(lastDark) // emit initial value
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(intervalMs)
                    val dark = isSystemDark()
                    if (dark != lastDark) {
                        Logger.i(null, "DwmHelper") { "System theme changed: isDark=$dark" }
                        lastDark = dark
                        callback(dark)
                    }
                }
            } catch (_: InterruptedException) { /* normal shutdown */ }
        }, "dwm-theme-watcher")
        thread.isDaemon = true
        thread.start()
        return { thread.interrupt() }
    }

    // ── internal ──────────────────────────────────────────────────────

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").lowercase().contains("win")

    private fun findWindowByPid(): WinDef.HWND? {
        val pid = ProcessHandle.current().pid().toInt()
        val found = arrayOf<WinDef.HWND?>(null)

        com.sun.jna.platform.win32.User32.INSTANCE.EnumWindows({ hwnd, _ ->
            val wPid = IntByReference()
            com.sun.jna.platform.win32.User32.INSTANCE.GetWindowThreadProcessId(hwnd, wPid)
            if (wPid.value == pid && com.sun.jna.platform.win32.User32.INSTANCE.IsWindowVisible(hwnd)) {
                found[0] = hwnd
                false
            } else true
        }, null)

        return found[0]
    }

    private fun dwmSetAttr(hwnd: WinDef.HWND, attr: Int, value: Int) {
        try {
            val lib = NativeLibrary.getInstance("dwmapi")
            val fn = lib.getFunction("DwmSetWindowAttribute")
            val valueRef = IntByReference(value)
            val hr = fn.invokeInt(arrayOf(hwnd, attr, valueRef, 4))
            if (hr != 0) {
                Logger.w(null, "DwmHelper") { "attr=$attr failed: HRESULT=0x${Integer.toHexString(hr)}" }
            }
        } catch (e: Throwable) {
            Logger.e(e, "DwmHelper") { "attr=$attr exception: ${e.message}" }
        }
    }

    private fun argbToAbgr(argb: Int): Int {
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }

    private fun Int.luminance(): Double {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    }
}

/** Win32 MARGINS struct for DwmExtendFrameIntoClientArea. */
@FieldOrder("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
class MARGINS : Structure() {
    @JvmField var cxLeftWidth: Int = 0
    @JvmField var cxRightWidth: Int = 0
    @JvmField var cyTopHeight: Int = 0
    @JvmField var cyBottomHeight: Int = 0
}
