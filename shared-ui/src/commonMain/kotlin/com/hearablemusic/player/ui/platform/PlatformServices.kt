package com.hearablemusic.player.ui.platform

import com.hmp.domain.music.EditableMusicTags

/**
 * 平台能力接口组（冻结版，方案 §5.4）。
 *
 * 收口 UI 中散落的 Context/Intent 调用（分享/文件选择/权限/触觉/悬浮歌词），
 * 接口 + Koin 注入，非 expect/actual——expect/actual 只留给无状态纯逻辑。
 * Desktop 给空实现/等价实现。
 *
 * 调用点映射表：docs/7_x/A shared-ui/接口冻结-调用点映射表.md
 */

/** 分享请求（DialogEvent.ShareMusic 的平台无关形态）。 */
data class ShareMusicRequest(
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
)

/** 分享：音频文件（FileProvider + ACTION_SEND）与纯文本。 */
interface ShareService {
    /** 分享音频文件；文件不存在时实现内部退化为分享文本。 */
    fun shareMusic(request: ShareMusicRequest)

    fun shareText(subject: String, text: String)

    /** 分享任意本地文件（备份导出等；FileProvider + ACTION_SEND）。chooserTitle 为分享面板标题。 */
    fun shareFile(filePath: String, mimeType: String, chooserTitle: String)
}

/** 文件选择（ActivityResultContracts 的平台无关形态）。 */
interface FilePickerService {
    /** 选择图片（GetContent，MIME 为 image 全类型）：封面/头像选择。返回 Uri 字符串，取消为 null。 */
    fun pickImage(onResult: (String?) -> Unit)

    /** 选择备份文件（OpenDocument）：备份恢复入口。返回 Uri 字符串，取消为 null。 */
    fun openBackupFile(onResult: (String?) -> Unit)
}

/** 权限请求。 */
interface PermissionService {
    /** 引导页多权限请求（音频/通知等），全部授予回调 true。 */
    fun requestIntroPermissions(onResult: (allGranted: Boolean) -> Unit)

    /** 悬浮窗权限（ACTION_MANAGE_OVERLAY_PERMISSION），授权结果回调。 */
    fun requestOverlayPermission(onResult: (granted: Boolean) -> Unit)

    /** 当前是否已授予悬浮窗权限。 */
    fun hasOverlayPermission(): Boolean

    /**
     * 请求对指定曲目文件的写权限（Android: RecoverableSecurityException → IntentSender 流程）。
     */
    fun requestMusicWriteAccess(musicId: Long, onResult: (granted: Boolean) -> Unit)
}

/** 音乐文件写权限请求结果（MusicTagEditService.requestMusicWriteAccess 回调）。 */
enum class MusicWriteAccessResult {
    /** 用户已授权（旧流程 Activity.RESULT_OK），可继续 SAF 写入。 */
    GRANTED,

    /** 用户拒绝授权（旧流程静默返回，无提示）。 */
    DENIED,

    /** MediaStore 中查不到该曲目文件（旧流程 music_not_found 提示场景）。 */
    NOT_FOUND
}

/** 音乐标签编辑页平台桥：封面选图/解码 + MediaStore 写权限请求 + SAF Uri 写入（EditMusicTagsScreen 迁移引入）。 */
interface MusicTagEditService {
    /**
     * 选封面图（GetContent，MIME 为 image 全类型）：返回按旧逻辑处理（超 512KB 时解码采样压缩为 JPEG 90）的字节；
     * 取消/失败为 null。
     */
    fun pickCoverImage(onResult: (ByteArray?) -> Unit)

    /**
     * 对指定曲目文件发起写权限请求（旧 MediaStore.createWriteRequest + StartIntentSenderForResult 流程）。
     */
    fun requestMusicWriteAccess(filePath: String, onResult: (MusicWriteAccessResult) -> Unit)

    /**
     * 经 SAF 授权的内容 Uri 写入标签（旧 saveWithUri 的 MusicTagEditor.writeTags(uri, ...) 段；
     * Uri 由实现内部按 filePath 反查 MediaStore）。
     */
    fun writeTagsViaSaf(filePath: String?, tags: EditableMusicTags, onResult: (Result<Unit>) -> Unit)
}

/** 触觉反馈效果（对应 Android HapticFeedbackConstants）。 */
enum class HapticEffect {
    TICK,          // CLOCK_TICK：微调/刻度
    VIRTUAL_KEY,   // 虚拟按键
    LONG_PRESS,    // 长按
    CONTEXT_CLICK, // 上下文点击
    KEYBOARD_PRESS,// 键盘按压
    CONFIRM,       // 确认
    REJECT,        // 拒绝/失败
    DRAG_START,    // 拖拽起始
}

/** 触觉反馈（现 HapticFeedbackHelper 的平台无关形态；Desktop 空实现）。 */
interface HapticService {
    fun perform(effect: HapticEffect)
}

/** 悬浮歌词控制（Android 前台 Service；Desktop 空实现）。 */
interface FloatingLyricsController {
    /** 启动悬浮歌词（实现内部处理 overlay 权限检查与开关持久化之外的启动细节）。 */
    fun start()

    /** 停止悬浮歌词。 */
    fun stop()
}

/**
 * 平台服务聚合（非空、Koin 注入）。
 *
 * 说明（冻结决策）：
 * - MediaSessionController（锁屏/通知桥接）在 Android 上完全位于 core-player 服务内部，
 *   UI 无任何直接调用点，故不设组；阶段二如需暴露再按流程增补。
 * - AppLifecycleService（minimize/close）无 UI 调用点，不设组。
 */
interface PlatformServices {
    val share: ShareService
    val filePicker: FilePickerService
    val permission: PermissionService
    val musicTagEdit: MusicTagEditService
    val haptic: HapticService
    val floatingLyrics: FloatingLyricsController
}
