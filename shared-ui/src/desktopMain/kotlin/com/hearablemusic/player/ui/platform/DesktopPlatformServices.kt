package com.hearablemusic.player.ui.platform

import com.hmp.data.util.MusicTagEditor
import com.hmp.domain.music.EditableMusicTags
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * PlatformServices 的 Desktop 实现（契约见 commonMain PlatformServices.kt）。
 *
 * 与 Android 实现的差异（均记录于各 Impl KDoc）：
 * - 分享：无系统分享面板——文件用系统默认程序打开、
 *   文本走剪贴板，为「等价实现」
 * - 文件选择：JFileChooser（线程模型见 FilePickerServiceImpl）
 * - 权限：桌面无运行时权限体系，恒授权
 * - 标签编辑：无 SAF，直接 MusicTagEditor 写文件（:shared desktopMain 的 jaudiotagger 实现）
 * - 触觉/悬浮歌词：空实现（无对应硬件/系统能力）
 */

/** 分享：文件用系统默认程序打开（Desktop.open），文本复制到剪贴板。 */
class DesktopShareServiceImpl : ShareService {

    override fun shareMusic(request: ShareMusicRequest) {
        val file = File(request.filePath)
        if (file.exists()) {
            openWithSystemApp(file)
        } else {
            // 文件不存在：退化为复制文本（对齐 Android 实现的降级行为）
            copyToClipboard("${request.title} - ${request.artist} (${request.album})")
        }
    }

    override fun shareText(subject: String, text: String) {
        copyToClipboard(if (subject.isBlank()) text else "$subject\n$text")
    }

    override fun shareFile(filePath: String, mimeType: String, chooserTitle: String) {
        val file = File(filePath)
        if (file.exists()) openWithSystemApp(file)
    }

    private fun openWithSystemApp(file: File) {
        runCatching { Desktop.getDesktop().open(file) }
    }

    private fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(text), null)
        }
    }
}

/**
 * 文件选择：JFileChooser。
 *
 * 线程模型：showOpenDialog 为模态阻塞调用，若在 Compose 渲染线程（AWT EDT）直接调用
 * 会冻结 UI 渲染——放到守护线程执行，结果经 SwingUtilities.invokeLater 回到 EDT
 * （Compose Desktop 状态更新在 EDT 上是安全的）。
 */
class DesktopFilePickerServiceImpl : FilePickerService {

    override fun pickImage(onResult: (String?) -> Unit) {
        showOpenDialog(IMAGE_FILTER_DESCRIPTION, arrayOf("jpg", "jpeg", "png", "webp")) { path ->
            onResult(path)
        }
    }

    override fun openBackupFile(onResult: (String?) -> Unit) {
        showOpenDialog(BACKUP_FILTER_DESCRIPTION, arrayOf("json", "zip", "hmp")) { path ->
            onResult(path)
        }
    }

    override val directorySelectionMode: DirectorySelectionMode = DirectorySelectionMode.ARBITRARY_PATH

    override fun pickDirectory(onResult: (String?) -> Unit) {
        showDirectoryDialog(onResult)
    }

    /** 目录选择：线程模型与 [showOpenDialog] 一致（守护线程弹窗 + invokeLater 回 EDT）。 */
    private fun showDirectoryDialog(onResult: (String?) -> Unit) {
        Thread {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isMultiSelectionEnabled = false
            }
            val result = chooser.showOpenDialog(null)
            val path = if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else {
                null
            }
            SwingUtilities.invokeLater { onResult(path) }
        }.apply { isDaemon = true }.also { it.start() }
    }

    private fun showOpenDialog(
        filterDescription: String,
        extensions: Array<String>,
        onResult: (String?) -> Unit,
    ) {
        Thread {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter(filterDescription, *extensions)
                isMultiSelectionEnabled = false
            }
            val result = chooser.showOpenDialog(null)
            val path = if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else {
                null
            }
            SwingUtilities.invokeLater { onResult(path) }
        }.apply { isDaemon = true }.also { it.start() }
    }

    private companion object {
        const val IMAGE_FILTER_DESCRIPTION = "Image Files (*.jpg, *.jpeg, *.png, *.webp)"
        const val BACKUP_FILTER_DESCRIPTION = "Backup Files (*.json, *.zip, *.hmp)"
    }
}

/** 权限：桌面无运行时权限体系，恒授权（保留签名以兼容 commonMain 调用流程）。 */
class DesktopPermissionServiceImpl : PermissionService {

    override fun requestIntroPermissions(onResult: (allGranted: Boolean) -> Unit) {
        onResult(true)
    }

    override fun requestOverlayPermission(onResult: (granted: Boolean) -> Unit) {
        onResult(true)
    }

    override fun hasOverlayPermission(): Boolean = true

    /** 桌面无运行时权限体系：文件系统可直接读取。 */
    override fun hasMusicReadAccess(): Boolean = true

    override fun openAppSettings() = Unit

    override fun requestMusicWriteAccess(musicId: Long, onResult: (granted: Boolean) -> Unit) {
        onResult(true)
    }
}

/**
 * 标签编辑桥：选图（JFileChooser）+ 直接文件写入（无 SAF）。
 *
 * 与 Android 实现的差异：
 * - requestMusicWriteAccess：无系统授权流程，按「文件存在与否」返回 GRANTED/NOT_FOUND
 * - writeTagsViaSaf：直接走 MusicTagEditor.writeTags(filePath, tags)
 *   （:shared desktopMain 的 jaudiotagger 实现，内部自带 canWrite 检查）
 * - pickCoverImage 压缩：>512KB 时 skiko 重编码 JPEG 90（无 Android 的 inSampleSize
 *   降采样，仅格式转换降体积）
 */
class DesktopMusicTagEditServiceImpl : MusicTagEditService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun pickCoverImage(onResult: (ByteArray?) -> Unit) {
        Thread {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter(
                    "Image Files (*.jpg, *.jpeg, *.png, *.webp)",
                    "jpg", "jpeg", "png", "webp"
                )
                isMultiSelectionEnabled = false
            }
            val result = chooser.showOpenDialog(null)
            val bytes = if (result == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    chooser.selectedFile.readBytes().takeIf { it.isNotEmpty() }
                        ?.let(::compressIfOversized)
                }.getOrNull()
            } else {
                null
            }
            SwingUtilities.invokeLater { onResult(bytes) }
        }.apply { isDaemon = true }.also { it.start() }
    }

    override fun requestMusicWriteAccess(
        filePath: String,
        onResult: (MusicWriteAccessResult) -> Unit,
    ) {
        val result = if (File(filePath).exists()) {
            MusicWriteAccessResult.GRANTED
        } else {
            MusicWriteAccessResult.NOT_FOUND
        }
        SwingUtilities.invokeLater { onResult(result) }
    }

    override fun writeTagsViaSaf(
        filePath: String?,
        tags: EditableMusicTags,
        onResult: (Result<Unit>) -> Unit,
    ) {
        scope.launch {
            val result = if (filePath == null) {
                Result.failure(IllegalStateException("Music file path is null"))
            } else {
                MusicTagEditor.writeTags(filePath, tags)
            }
            SwingUtilities.invokeLater { onResult(result) }
        }
    }

    /** >512KB 时重编码 JPEG 90（对齐 Android 侧的体积上限语义）。 */
    private fun compressIfOversized(bytes: ByteArray): ByteArray {
        if (bytes.size <= MAX_COVER_BYTES) return bytes
        return runCatching {
            Image.makeFromEncoded(bytes)
                .encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)
                ?.bytes
        }.getOrNull() ?: bytes
    }

    private companion object {
        const val MAX_COVER_BYTES = 512 * 1024
        const val JPEG_QUALITY = 90
    }
}

/** 触觉：桌面无触觉硬件，空实现。 */
class DesktopHapticServiceImpl : HapticService {
    override fun perform(effect: HapticEffect) {}
}

/** 悬浮歌词：桌面无悬浮窗体系，空实现。 */
class DesktopFloatingLyricsServiceImpl : FloatingLyricsController {
    override fun start() {}
    override fun stop() {}
}

/**
 * 聚合实现：desktopMain 的 DesktopUiKoinModule 注册（Koin 单例）。
 * 桌面无需宿主 Activity/Context，构造无参。
 */
class DesktopPlatformServices : PlatformServices {
    override val share: ShareService = DesktopShareServiceImpl()
    override val filePicker: FilePickerService = DesktopFilePickerServiceImpl()
    override val permission: PermissionService = DesktopPermissionServiceImpl()
    override val musicTagEdit: MusicTagEditService = DesktopMusicTagEditServiceImpl()
    override val haptic: HapticService = DesktopHapticServiceImpl()
    override val floatingLyrics: FloatingLyricsController = DesktopFloatingLyricsServiceImpl()
}
