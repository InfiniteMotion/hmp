package com.hearablemusic.player.ui.platform

import com.hearablemusic.player.ui.common.util.IosHapticServiceInstance
import com.hmp.domain.music.MusicLabel

/**
 * iOS 平台服务桥（A3/A4：Swift 侧能力 → commonMain PlatformServices）。
 *
 * 与 IosPlaybackCommands 同模式：Swift 桥（PlaybackBridge.swift）在启动时注册闭包，
 * Kotlin 侧判空转发；未注册的能力静默降级（null 分支返回等价空实现）。
 *
 * 语义对齐：
 * - 分享/文件选择：Swift 侧用 UIActivityViewController / PHPicker / UIDocumentPicker
 * - 触觉：Taptic Engine（见 PlatformHaptics.ios.kt）
 * - 悬浮歌词：iOS 无悬浮窗体系，空实现（与 Desktop 一致）
 * - 权限：iOS 媒体库权限在 AppDelegate 引导；无悬浮窗权限概念 → 恒授权
 * - 标签编辑：写权限恒授权（沙盒 Documents 可写），写入走 shared MusicTagEditor
 */
object IosPlatformServicesBridge {

    // ── ShareService ──

    var shareMusic: ((ShareMusicRequest) -> Unit)? = null
    var shareText: ((String, String) -> Unit)? = null
    var shareFile: ((String, String, String) -> Unit)? = null

    // ── FilePickerService ──

    var pickImage: ((onResult: (String?) -> Unit) -> Unit)? = null
    var openBackupFile: ((onResult: (String?) -> Unit) -> Unit)? = null

    // ── MusicTagEditService 选图 ──

    var pickCoverImage: ((onResult: (ByteArray?) -> Unit) -> Unit)? = null

    // ── 悬浮歌词（iOS 空实现，保留注册口以便未来 Live Activity 扩展） ──

    var floatingLyricsStart: (() -> Unit)? = null
    var floatingLyricsStop: (() -> Unit)? = null
}

/** 分享：Swift UIActivityViewController。 */
internal class IosShareService : ShareService {
    override fun shareMusic(request: ShareMusicRequest) {
        IosPlatformServicesBridge.shareMusic?.invoke(request)
    }

    override fun shareText(subject: String, text: String) {
        IosPlatformServicesBridge.shareText?.invoke(subject, text)
    }

    override fun shareFile(filePath: String, mimeType: String, chooserTitle: String) {
        IosPlatformServicesBridge.shareFile?.invoke(filePath, mimeType, chooserTitle)
    }
}

/** 文件选择：Swift PHPicker / UIDocumentPicker。 */
internal class IosFilePickerService : FilePickerService {
    override fun pickImage(onResult: (String?) -> Unit) =
        IosPlatformServicesBridge.pickImage?.invoke(onResult) ?: onResult(null)

    override fun openBackupFile(onResult: (String?) -> Unit) =
        IosPlatformServicesBridge.openBackupFile?.invoke(onResult) ?: onResult(null)

    // iOS 沙箱内无「自定义扫描目录」概念：音乐来自 Documents 目录（Files App 导入），
    // 故相关区块不渲染（见 LibrarySettingsScreen），本方法恒回调 null。
    override val directorySelectionMode: DirectorySelectionMode = DirectorySelectionMode.UNSUPPORTED

    override fun pickDirectory(onResult: (String?) -> Unit) = onResult(null)
}

/**
 * 权限：iOS 媒体库权限在 AppDelegate 首启引导（MPMediaLibrary requestAuthorization）；
 * 无悬浮窗权限概念 → 恒授权；曲目写权限按沙盒语义恒可写。
 */
internal class IosPermissionService : PermissionService {
    override fun requestIntroPermissions(onResult: (allGranted: Boolean) -> Unit) = onResult(true)
    override fun requestOverlayPermission(onResult: (granted: Boolean) -> Unit) = onResult(true)
    override fun hasOverlayPermission(): Boolean = true

    /** iOS 沙箱内 Documents 目录恒可读：媒体库权限已在 AppDelegate 引导。 */
    override fun hasMusicReadAccess(): Boolean = true

    override fun openAppSettings() = Unit
    override fun requestMusicWriteAccess(musicId: Long, onResult: (granted: Boolean) -> Unit) = onResult(true)
}

/** 标签编辑桥：选图走 Swift PHPicker；写权限恒授权；写入走 shared MusicTagEditor。 */
internal class IosMusicTagEditService : MusicTagEditService {

    override fun pickCoverImage(onResult: (ByteArray?) -> Unit) =
        IosPlatformServicesBridge.pickCoverImage?.invoke(onResult) ?: onResult(null)

    override fun requestMusicWriteAccess(filePath: String, onResult: (MusicWriteAccessResult) -> Unit) {
        val exists = platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(filePath)
        onResult(if (exists) MusicWriteAccessResult.GRANTED else MusicWriteAccessResult.NOT_FOUND)
    }

    override fun writeTagsViaSaf(filePath: String?, tags: com.hmp.domain.music.EditableMusicTags, onResult: (Result<Unit>) -> Unit) {
        val result = if (filePath == null) {
            Result.failure(IllegalStateException("Music file path is null"))
        } else {
            com.hmp.data.util.MusicTagEditor.writeTags(filePath, tags)
        }
        onResult(result)
    }
}

/** 悬浮歌词：iOS 无悬浮窗体系，空实现（与 Desktop 一致）。 */
internal class IosFloatingLyricsService : FloatingLyricsController {
    override fun start() { IosPlatformServicesBridge.floatingLyricsStart?.invoke() }
    override fun stop() { IosPlatformServicesBridge.floatingLyricsStop?.invoke() }
}

/**
 * 聚合实现：iosMain 的 IosUiKoinModule 注册（Koin 单例）。
 * 构造无参（共享 `IosHapticService` 单例，见 PlatformHaptics.ios.kt）。
 */
internal class IosPlatformServices : PlatformServices {
    override val share: ShareService = IosShareService()
    override val filePicker: FilePickerService = IosFilePickerService()
    override val permission: PermissionService = IosPermissionService()
    override val musicTagEdit: MusicTagEditService = IosMusicTagEditService()
    override val haptic: HapticService = IosHapticServiceInstance
    override val floatingLyrics: FloatingLyricsController = IosFloatingLyricsService()
}