package com.hmp.desktop

import java.awt.EventQueue
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

/**
 * Manages the system tray icon for HMP with playback controls.
 *
 * Provides a tray icon with a right-click popup menu containing:
 * - Previous / Play-Pause / Next playback controls
 * - Show HMP (bring window to front)
 * - Exit
 *
 * All AWT operations are dispatched on the EDT via [EventQueue.invokeLater].
 */
object SystemTrayManager {
    private var trayIcon: TrayIcon? = null
    private var onPlayPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrev: (() -> Unit)? = null
    private var onShowWindow: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null

    /**
     * Initialize the system tray icon.
     *
     * @param onPlayPause  Toggle play/pause
     * @param onNext       Skip to next track
     * @param onPrev       Skip to previous track
     * @param onShowWindow Bring the main window to front
     * @param onExit       Graceful exit callback (release resources, then exit)
     */
    fun init(
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrev: () -> Unit,
        onShowWindow: () -> Unit,
        onExit: () -> Unit,
    ) {
        if (!SystemTray.isSupported()) return

        this.onPlayPause = onPlayPause
        this.onNext = onNext
        this.onPrev = onPrev
        this.onShowWindow = onShowWindow
        this.onExit = onExit

        val icon = loadIcon()
        val popup = createPopupMenu()

        val newTrayIcon = TrayIcon(icon, "HMP - Hearable Music Player", popup)
        newTrayIcon.isImageAutoSize = true
        newTrayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    onShowWindow?.invoke()
                }
            }
        })

        trayIcon = newTrayIcon
        EventQueue.invokeLater {
            try {
                SystemTray.getSystemTray().add(newTrayIcon)
            } catch (_: Exception) {
                // System tray unavailable (e.g., headless environment)
            }
        }
    }

    /**
     * Update the tooltip to show the currently playing track.
     */
    fun updateTooltip(title: String) {
        EventQueue.invokeLater {
            trayIcon?.toolTip = "HMP - $title"
        }
    }

    /**
     * Remove the tray icon from the system tray.
     */
    fun dispose() {
        EventQueue.invokeLater {
            trayIcon?.let {
                SystemTray.getSystemTray().remove(it)
            }
            trayIcon = null
        }
    }

    private fun createPopupMenu(): PopupMenu {
        val popup = PopupMenu()

        val showItem = MenuItem("Show HMP")
        showItem.addActionListener { onShowWindow?.invoke() }
        popup.add(showItem)
        popup.addSeparator()

        val prevItem = MenuItem("Previous")
        prevItem.addActionListener { onPrev?.invoke() }
        popup.add(prevItem)

        val playPauseItem = MenuItem("Play / Pause")
        playPauseItem.addActionListener { onPlayPause?.invoke() }
        popup.add(playPauseItem)

        val nextItem = MenuItem("Next")
        nextItem.addActionListener { onNext?.invoke() }
        popup.add(nextItem)

        popup.addSeparator()

        val exitItem = MenuItem("Exit")
        exitItem.addActionListener {
            onExit?.invoke()
        }
        popup.add(exitItem)

        return popup
    }

    private fun loadIcon(): Image {
        // Try loading icon.png from classpath resources
        val stream = javaClass.classLoader.getResourceAsStream("icon.png")
            ?: javaClass.getResourceAsStream("/icon.png")
        return stream?.use { ImageIO.read(it) }
            ?: run {
                // Fallback: create a minimal 16x16 icon
                val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics()
                g.color = java.awt.Color(0x4CAF50)
                g.fillRect(0, 0, 16, 16)
                g.dispose()
                img
            }
    }
}
