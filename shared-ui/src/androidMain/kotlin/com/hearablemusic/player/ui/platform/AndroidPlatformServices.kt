package com.hearablemusic.player.ui.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.hearablemusicplayer.ui.player.floating.FloatingLyricsService
import com.hearablemusic.player.ui.R
import com.hmp.data.util.MusicTagEditor
import com.hmp.domain.music.EditableMusicTags
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PlatformServices 的 Android 实现（契约见 commonMain PlatformServices.kt）。
 *
 * 分享/文件选择/悬浮窗权限基于 FileProvider + ACTION_SEND /
 * ActivityResultContracts / ACTION_MANAGE_OVERLAY_PERMISSION 实现。
 * 文件选择与悬浮窗权限的 launcher 需挂在宿主 Activity 的 ActivityResultRegistry 上：
 * 由 AndroidPlatformServices 构造时传入 activity 并 register。
 */

/** 分享：FileProvider + ACTION_SEND。 */
class ShareServiceImpl(private val context: Context) : ShareService {

    override fun shareMusic(request: ShareMusicRequest) {
        val file = File(request.filePath)
        if (!file.exists()) {
            // 文件不存在：退化为分享文本（title - artist (album)）
            shareText(request.title, "${request.title} - ${request.artist} (${request.album})")
            return
        }
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, request.title)
            putExtra(Intent.EXTRA_TEXT, "${request.title} - ${request.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        dispatch(shareIntent, context.getString(R.string.share_music))
    }

    override fun shareText(subject: String, text: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        dispatch(shareIntent, context.getString(R.string.share_music))
    }

    override fun shareFile(filePath: String, mimeType: String, chooserTitle: String) {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(filePath)
        )
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        dispatch(shareIntent, chooserTitle)
    }

    private fun dispatch(intent: Intent, chooserTitle: String) {
        val chooser = Intent.createChooser(intent, chooserTitle)
        // 非 Activity Context（如 applicationContext）启动 Activity 需要新任务栈
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

/** 文件选择：GetContent / OpenDocument，launcher 注册在宿主 Activity 的 registry 上。 */
class FilePickerServiceImpl(private val context: Context) : FilePickerService {

    private var pickImageLauncher: ActivityResultLauncher<String>? = null
    private var openBackupLauncher: ActivityResultLauncher<Array<String>>? = null
    private val callbacks = mutableMapOf<String, (String?) -> Unit>()

    /** 由宿主 Activity（经 AndroidPlatformServices 构造）调用；可重复调用（Activity 重建后重挂）。 */
    fun register(host: ComponentActivity) {
        val registry = host.activityResultRegistry
        pickImageLauncher?.unregister()
        openBackupLauncher?.unregister()
        pickImageLauncher = registry.register(KEY_PICK_IMAGE, ActivityResultContracts.GetContent()) { uri ->
            dispatch(KEY_PICK_IMAGE, uri?.let(::copyAvatarImage))
        }
        openBackupLauncher = registry.register(KEY_OPEN_BACKUP, ActivityResultContracts.OpenDocument()) { uri ->
            dispatch(KEY_OPEN_BACKUP, if (uri != null) copyBackupToCache(uri) else null)
        }
    }

    override fun pickImage(onResult: (String?) -> Unit) {
        callbacks[KEY_PICK_IMAGE] = onResult
        pickImageLauncher?.launch("image/*")
    }

    override fun openBackupFile(onResult: (String?) -> Unit) {
        callbacks[KEY_OPEN_BACKUP] = onResult
        openBackupLauncher?.launch(arrayOf("application/json"))
    }

    // Android 不支持选择任意文件系统目录：SAF（OpenDocumentTree）返回的是 tree Uri，
    // **无法**转成 scanDirectoryConfig 所需的文件系统路径（配置存的是 `File(path)` 语义）。
    // 故目录添加改用「从系统媒体库已知文件夹中选择」——由 LibrarySettingsScreen 依据本标识切换。
    override val directorySelectionMode: DirectorySelectionMode = DirectorySelectionMode.KNOWN_FOLDERS

    override fun pickDirectory(onResult: (String?) -> Unit) = onResult(null)

    private fun dispatch(key: String, path: String?) {
        callbacks.remove(key)?.invoke(path)
    }

    /** 旧 ProfileSettingsScreen 行为：复制到 filesDir/user_avatar.jpg，返回绝对路径。 */
    private fun copyAvatarImage(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "user_avatar.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: IOException) {
            HmpLog.w(LogTag.UiCommon, e) { "🎨 copyAvatarImage failed | non-fatal | reason=${e.message}" }
            null
        }
    }

    /** 旧 BackupSettingsScreen 行为：复制到 cacheDir/restore_temp.json，返回绝对路径。 */
    private fun copyBackupToCache(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "restore_temp.json")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            tempFile.absolutePath
        } catch (e: Exception) {
            HmpLog.w(LogTag.UiSettings, e) { "⚙️ copyBackupToCache failed | non-fatal | reason=${e.message}" }
            null
        }
    }

    companion object {
        private const val KEY_PICK_IMAGE = "hmp_pick_image"
        private const val KEY_OPEN_BACKUP = "hmp_open_backup"
    }
}

/** 权限：引导页运行时权限（音频读取 / 通知）+ 悬浮窗（ACTION_MANAGE_OVERLAY_PERMISSION 回调判定）。 */
class PermissionServiceImpl(private val context: Context) : PermissionService {

    private var overlayLauncher: ActivityResultLauncher<Intent>? = null
    private var overlayCallback: ((Boolean) -> Unit)? = null
    private var introPermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var introPermissionsCallback: ((Boolean) -> Unit)? = null

    /** 由宿主 Activity（经 AndroidPlatformServices 构造）调用；可重复调用（Activity 重建后重挂）。 */
    fun register(host: ComponentActivity) {
        overlayLauncher?.unregister()
        overlayLauncher = host.activityResultRegistry.register(
            KEY_OVERLAY_PERMISSION,
            ActivityResultContracts.StartActivityForResult()
        ) {
            // 与旧 LyricsSettingsPage 一致：不看返回码，直接以 canDrawOverlays 判定
            val callback = overlayCallback
            overlayCallback = null
            callback?.invoke(Settings.canDrawOverlays(context))
        }
        introPermissionsLauncher?.unregister()
        introPermissionsLauncher = host.activityResultRegistry.register(
            KEY_INTRO_PERMISSIONS,
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val callback = introPermissionsCallback
            introPermissionsCallback = null
            // 音频读取为必需（缺它 MediaStore 查不到歌）；通知为可选，被拒不阻断引导
            val audioGranted = results[Manifest.permission.READ_MEDIA_AUDIO]
                ?: context.hasAudioReadPermission()
            if (!audioGranted) {
                HmpLog.w(LogTag.UiCommon) { "🔐 引导页权限被拒 | READ_MEDIA_AUDIO | 曲库将为空" }
            }
            callback?.invoke(audioGranted)
        }
    }

    /**
     * 引导页权限申请：`READ_MEDIA_AUDIO`（必需，MediaStore 扫描依赖）+ `POST_NOTIFICATIONS`（可选，播放通知）。
     *
     * 回调语义：**以音频读取权限为准**。通知被拒不阻断引导（若两者都要，用户拒绝通知后会永久卡在引导页）。
     *
     * ⚠️ 本方法曾长期是 `onResult(true)` 空壳。v7.1 把 `IntroScreen` 从 `androidMain` 迁到 `commonMain` 后
     * 调用点出现而实现未补，导致首启权限框不弹、曲库为空（静默失败）。
     * 回归记录见 `docs/7_x/A shared-ui/UI层统一-能力搬迁点检.md` R1。
     */
    override fun requestIntroPermissions(onResult: (allGranted: Boolean) -> Unit) {
        val launcher = introPermissionsLauncher
        if (launcher == null) {
            // 理论不可达（register 在 AndroidPlatformServices 构造时调用）：
            // 退化时按当前真实授权状态回答，既不谎报成功、也不永久挂起引导。
            HmpLog.w(LogTag.UiCommon) { "🔐 introPermissionsLauncher 未注册，按当前授权状态回答" }
            onResult(context.hasAudioReadPermission())
            return
        }
        introPermissionsCallback = onResult
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    override fun requestOverlayPermission(onResult: (granted: Boolean) -> Unit) {
        overlayCallback = onResult
        overlayLauncher?.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
        )
    }

    override fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    override fun hasMusicReadAccess(): Boolean = context.hasAudioReadPermission()

    override fun openAppSettings() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            HmpLog.w(LogTag.UiCommon, e) { "🔐 打开应用设置页失败 | reason=${e.message}" }
        }
    }

    override fun requestMusicWriteAccess(musicId: Long, onResult: (granted: Boolean) -> Unit) {
        // 批内无调用点：保留签名，直接回调成功
        onResult(true)
    }

    companion object {
        private const val KEY_OVERLAY_PERMISSION = "hmp_overlay_permission"
        private const val KEY_INTRO_PERMISSIONS = "hmp_intro_permissions"
    }
}

/** 音频读取权限是否已授予（Android 13+ 起为必需运行时权限；minSdk = 33 故无版本分支）。 */
private fun Context.hasAudioReadPermission(): Boolean =
    checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * 音乐标签编辑页平台桥：
 * 封面选图 + 解码压缩、MediaStore.createWriteRequest 写权限请求、SAF Uri 写入
 * （MusicTagEditor.writeTags(uri, ...)）。
 */
class MusicTagEditServiceImpl(private val context: Context) : MusicTagEditService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var pickCoverLauncher: ActivityResultLauncher<String>? = null
    private var pickCoverCallback: ((ByteArray?) -> Unit)? = null

    private var writeRequestLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var writeRequestCallback: ((MusicWriteAccessResult) -> Unit)? = null

    /** 由宿主 Activity（经 AndroidPlatformServices 构造）调用；可重复调用（Activity 重建后重挂）。 */
    fun register(host: ComponentActivity) {
        val registry = host.activityResultRegistry
        pickCoverLauncher?.unregister()
        writeRequestLauncher?.unregister()
        pickCoverLauncher = registry.register(KEY_PICK_COVER, ActivityResultContracts.GetContent()) { uri ->
            val callback = pickCoverCallback
            pickCoverCallback = null
            if (callback == null) return@register
            if (uri == null) {
                // 旧行为：取消选图无副作用
                callback(null)
                return@register
            }
            scope.launch {
                val bytes = readAndCompressCover(uri)
                withContext(Dispatchers.Main) { callback(bytes) }
            }
        }
        writeRequestLauncher = registry.register(
            KEY_WRITE_REQUEST,
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            // 旧 EditMusicTagsScreen 行为：仅 RESULT_OK 继续保存，拒绝则静默
            val callback = writeRequestCallback
            writeRequestCallback = null
            callback?.invoke(
                if (result.resultCode == Activity.RESULT_OK) MusicWriteAccessResult.GRANTED
                else MusicWriteAccessResult.DENIED
            )
        }
    }

    override fun pickCoverImage(onResult: (ByteArray?) -> Unit) {
        pickCoverCallback = onResult
        pickCoverLauncher?.launch("image/*")
    }

    override fun requestMusicWriteAccess(filePath: String, onResult: (MusicWriteAccessResult) -> Unit) {
        // 旧流程：IO 线程按路径反查 MediaStore Uri；查不到即 music_not_found 场景，
        // 否则 MediaStore.createWriteRequest 弹系统确认框
        scope.launch {
            val uri = MusicTagEditor.queryMediaStoreUri(context, filePath)
            withContext(Dispatchers.Main) {
                if (uri == null) {
                    onResult(MusicWriteAccessResult.NOT_FOUND)
                    return@withContext
                }
                writeRequestCallback = onResult
                val pendingIntent = MediaStore.createWriteRequest(
                    context.contentResolver,
                    listOf(uri)
                )
                writeRequestLauncher?.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            }
        }
    }

    override fun writeTagsViaSaf(filePath: String?, tags: EditableMusicTags, onResult: (Result<Unit>) -> Unit) {
        // 旧 saveWithUri 的 MusicTagEditor.writeTags(uri, tags, scanPath) 段；
        // 授权 Uri 按 filePath 重查（同一 MediaStore 条目，授权结果对该 Uri 生效）
        scope.launch {
            val uri = filePath?.let { MusicTagEditor.queryMediaStoreUri(context, it) }
            val result = if (uri == null) {
                Result.failure(IllegalStateException("Music file not found: $filePath"))
            } else {
                MusicTagEditor.writeTags(uri, tags, scanPath = filePath)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** 旧 EditMusicTagsViewModel.readAndCompressCover 逐行搬移（阈值/采样/JPEG 90 保不变）。 */
    private fun readAndCompressCover(uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size <= MAX_COVER_BYTES) return bytes

            // 过大时压缩为 JPEG，避免写入超大封面
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val sample = calculateInSampleSize(options, MAX_COVER_DIMENSION, MAX_COVER_DIMENSION)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: return bytes
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /** 旧 EditMusicTagsViewModel.calculateInSampleSize 逐行搬移。 */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        var width = options.outWidth
        var height = options.outHeight
        while (width / (inSampleSize * 2) >= reqWidth || height / (inSampleSize * 2) >= reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    companion object {
        private const val KEY_PICK_COVER = "hmp_pick_cover_image"
        private const val KEY_WRITE_REQUEST = "hmp_music_write_request"
        private const val MAX_COVER_BYTES = 512 * 1024
        private const val MAX_COVER_DIMENSION = 1024
    }
}

/** 触觉反馈：包装 View.performHapticFeedback（写法沿用 androidMain HapticFeedback.kt）。 */
class HapticServiceImpl(private val context: Context) : HapticService {

    override fun perform(effect: HapticEffect) {
        val view = (context as? Activity)?.window?.decorView ?: return
        view.performHapticFeedback(
            toConstant(effect),
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    private fun toConstant(effect: HapticEffect): Int = when (effect) {
        HapticEffect.TICK -> HapticFeedbackConstants.CLOCK_TICK
        HapticEffect.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY
        HapticEffect.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        HapticEffect.CONTEXT_CLICK -> HapticFeedbackConstants.CONTEXT_CLICK
        HapticEffect.KEYBOARD_PRESS -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticEffect.CONFIRM -> HapticFeedbackConstants.CONFIRM
        HapticEffect.REJECT -> HapticFeedbackConstants.REJECT
        HapticEffect.DRAG_START ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.DRAG_START
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
    }
}

/** 悬浮歌词：Android 前台 Service 启停。 */
class FloatingLyricsServiceImpl(private val context: Context) : FloatingLyricsController {

    override fun start() {
        context.startService(Intent(context, FloatingLyricsService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, FloatingLyricsService::class.java))
    }
}

/**
 * 聚合实现：主线在 UiKoinModule 注册（Koin 单例）。
 *
 * @param context 常规 Context（建议 applicationContext；分享在非 Activity Context 下自动加 NEW_TASK）
 * @param activity 宿主 Activity：文件选择/悬浮窗权限/标签编辑 launcher 挂其 registry，触觉反馈用其 decorView
 */
class AndroidPlatformServices(
    context: Context,
    activity: ComponentActivity
) : PlatformServices {
    override val share: ShareService = ShareServiceImpl(context)
    override val filePicker: FilePickerServiceImpl =
        FilePickerServiceImpl(context).apply { register(activity) }
    override val permission: PermissionServiceImpl =
        PermissionServiceImpl(context).apply { register(activity) }
    override val musicTagEdit: MusicTagEditServiceImpl =
        MusicTagEditServiceImpl(context).apply { register(activity) }
    override val haptic: HapticService = HapticServiceImpl(activity)
    override val floatingLyrics: FloatingLyricsController = FloatingLyricsServiceImpl(context)
}