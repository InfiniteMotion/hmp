package com.hmp.desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIOptions
import co.touchlab.kermit.Logger

/**
 * Native window operations for undecorated window management on Windows.
 * Handles window dragging (with Aero Snap), minimize, maximize/restore, and close.
 */
object WindowHelper {

    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val HTCAPTION = 0x0002
    private const val GWL_STYLE = -16
    private const val WS_MAXIMIZE = 0x01000000
    private const val SW_MAXIMIZE = 3
    private const val SW_RESTORE = 9
    private const val SW_MINIMIZE = 6

    private val user32: User32 by lazy { User32.INSTANCE }

    // Custom User32 + Gdi32 functions not in JNA's standard interfaces
    private val nativeLib: NativeExtra by lazy {
        Native.load("user32", NativeExtra::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
    private val gdi32: Gdi32Extra by lazy {
        Native.load("gdi32", Gdi32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    /**
     * Start native window dragging from the title bar area.
     * Provides native Aero Snap behavior on Windows.
     */
    fun startDrag(hwnd: WinDef.HWND) {
        nativeLib.ReleaseCapture()
        user32.SendMessage(hwnd, WM_NCLBUTTONDOWN, WinDef.WPARAM(HTCAPTION.toLong()), WinDef.LPARAM(0))
    }

    /**
     * Toggle between maximized and normal window state.
     */
    fun toggleMaximize(hwnd: WinDef.HWND) {
        if (isMaximized(hwnd)) {
            user32.ShowWindow(hwnd, SW_RESTORE)
        } else {
            user32.ShowWindow(hwnd, SW_MAXIMIZE)
        }
    }

    /**
     * Minimize the window via Win32.
     */
    fun minimize(hwnd: WinDef.HWND) {
        user32.ShowWindow(hwnd, SW_MINIMIZE)
    }

    /**
     * Minimize via AWT (works when HWND is not available).
     */
    fun minimizeAwt(window: java.awt.Window) {
        if (window is java.awt.Frame) {
            window.state = java.awt.Frame.ICONIFIED
        }
    }

    /**
     * Check if the window is currently maximized.
     */
    fun isMaximized(hwnd: WinDef.HWND): Boolean {
        val style = user32.GetWindowLong(hwnd, GWL_STYLE)
        return (style and WS_MAXIMIZE) != 0
    }

    /**
     * Clip the window to a rounded rectangle (8px radius). Call on window creation
     * and again on resize. Uses SetWindowRgn — the only reliable way for undecorated windows.
     */
    fun clipRoundedCorners(hwnd: WinDef.HWND, width: Int, height: Int, radius: Int = 8) {
        try {
            val rgn = gdi32.CreateRoundRectRgn(0, 0, width + 1, height + 1, radius * 2, radius * 2)
            nativeLib.SetWindowRgn(hwnd, rgn, true)
        } catch (e: Throwable) {
            Logger.e(e, "WindowHelper") { "clipRoundedCorners failed: ${e.message}" }
        }
    }

    /** Extended native functions: ReleaseCapture + SetWindowRgn. */
    private interface NativeExtra : com.sun.jna.Library {
        fun ReleaseCapture(): Boolean
        fun SetWindowRgn(hWnd: WinDef.HWND, hRgn: WinDef.HRGN, bRedraw: Boolean): Boolean
    }

    /** GDI functions for region operations. */
    private interface Gdi32Extra : com.sun.jna.Library {
        fun CreateRoundRectRgn(
            nLeftRect: Int, nTopRect: Int, nRightRect: Int, nBottomRect: Int,
            nWidthEllipse: Int, nHeightEllipse: Int
        ): WinDef.HRGN
    }
}
