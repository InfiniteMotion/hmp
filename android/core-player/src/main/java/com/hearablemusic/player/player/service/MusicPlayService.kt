package com.hearablemusic.player.player.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import coil.imageLoader
import coil.request.ImageRequest
import com.hmp.domain.music.Music
import com.hearablemusic.player.player.AudioEffectManager
import com.hearablemusic.player.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

interface PlayControl {
    fun play()
    fun pause()
    fun playSingleMusic(music: Music)
    fun seekTo(position: Long)
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun stopMusic()
    fun prepareMusic(music: Music)
    fun isMusicLoaded(path: String): Boolean
    fun proceedMusic()
    fun updateLyricLine(lyric: String?)

    // 音效控制方法
    fun setEqualizerPreset(preset: Int)
    fun setBassBoost(level: Int)
    fun setSurroundSound(enabled: Boolean)
    fun setReverb(preset: Int)
    fun setCustomEqualizer(bandLevels: FloatArray)
    fun getEqualizerPresets(): List<String>
    fun getCurrentEqualizerPreset(): Int
    fun getBassBoostLevel(): Int
    fun isSurroundSoundEnabled(): Boolean
    fun getReverbPreset(): Int
    fun getEqualizerBandCount(): Int
    fun getEqualizerBandLevelRange(): Pair<Int, Int>
    fun getCurrentEqualizerBandLevels(): FloatArray
}

@UnstableApi
class MusicPlayService : Service(), PlayControl {

    companion object {
        const val ACTION_PLAY = "com.hearablemusic.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.hearablemusic.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.hearablemusic.player.ACTION_NEXT"
        const val ACTION_PREV = "com.hearablemusic.player.ACTION_PREV"
    }

    private val binder = MusicPlayServiceBinder()

    private val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    // 自定义 Player 包装器,让系统认为始终有上/下一首
    private lateinit var customPlayer: ForwardingPlayer

    // MediaSession 实例
    private lateinit var mediaSession: MediaSession

    // 播放完成监听器接口
    interface OnMusicCompleteListener {
        fun onPlaybackEnded()
        fun onPlaybackPrev()
        fun onPlaybackNext() // 新增：用户手动切换下一首
        fun onPlayStateChanged(isPlaying: Boolean) // 新增
    }

    private var playbackListener: OnMusicCompleteListener? = null

    // 耳机拔插和蓝牙断开广播接收器
    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // 耳机拔出,暂停播放
                    HmpLog.d(LogTag.PlayerService) { "📡 Audio becoming noisy, pausing playback" }
                    exoPlayer.pause()
                    playbackListener?.onPlayStateChanged(false)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // 蓝牙断开,暂停播放
                    HmpLog.d(LogTag.PlayerService) { "📡 Bluetooth disconnected, pausing playback" }
                    exoPlayer.pause()
                    playbackListener?.onPlayStateChanged(false)
                }
            }
        }
    }

    private var isReceiverRegistered = false
    
    // 歌词替换：缓存原始歌曲信息
    private var originalTitle: String? = null
    private var originalArtist: String? = null
    private var currentLyricLine: String? = null

    // 音效管理器
    private val audioEffectManager = AudioEffectManager()
    
    // 音效相关成员变量
    private var equalizerPreset = 0
    private var bassBoostLevel = 0
    private var surroundSoundEnabled = false
    private var reverbPreset = 0
    private var customEqualizerLevels = floatArrayOf()
    
    // 均衡器预设列表
    private val equalizerPresets = listOf(
        "正常", "摇滚", "流行", "古典", "爵士", "蓝调", "电子", "嘻哈", "金属", "乡村"
    )
    
    // 音效初始化标志
    private var isAudioEffectInitialized = false
    private var audioEffectInitRetryCount = 0
    private val maxAudioEffectInitRetries = 3

    // 绑定播放完成回调
    fun setOnMusicCompleteListener(listener: OnMusicCompleteListener) {
        playbackListener = listener
        HmpLog.d(LogTag.PlayerService) { "📡 OnMusicCompleteListener set: ${true}" }
    }

    // 返回 Binder 实例
    override fun onBind(intent: Intent): IBinder = binder

    inner class MusicPlayServiceBinder : Binder() {
        fun getService(): MusicPlayService = this@MusicPlayService
    }


    // 创建通知频道
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "music_channel", // 通知频道 ID
            "音乐播放",         // 通知频道名称
            NotificationManager.IMPORTANCE_LOW // 重要性:低,避免打扰
        ).apply {
            description = "播放控制通知"
            setSound(null, null) // API 36 建议显式设置声音
        }

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    // 创建通知
    @SuppressLint("RestrictedApi")
    private fun buildNotification(
        music: Music,
        albumArtBitmap: Bitmap?,
        mainActivityClass: Class<*>,
        lyricLine: String? = null
    ): Notification {
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, mainActivityClass),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // API 36 要求使用 PendingIntent.FLAG_IMMUTABLE
        val prevPending = PendingIntent.getBroadcast(
            this, 1, Intent(this, MusicNotificationReceiver::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePending = PendingIntent.getBroadcast(
            this, 2, Intent(this, MusicNotificationReceiver::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPending = PendingIntent.getBroadcast(
            this, 3, Intent(this, MusicNotificationReceiver::class.java).setAction(ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPending = PendingIntent.getBroadcast(
            this, 4, Intent(this, MusicNotificationReceiver::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isPlaying = exoPlayer.isPlaying

        val title = lyricLine ?: music.title
        val artist = if (lyricLine != null) {
            val songInfo = buildString {
                append(originalTitle ?: music.title)
                append(" — ")
                append(originalArtist ?: music.artist)
            }
            songInfo
        } else {
            music.artist
        }

        val builder = NotificationCompat.Builder(this, "music_channel")
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.player_d)
            .setContentIntent(mainPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.backward_end_fill, "上一首", prevPending
                ).build()
            )
            .addAction(
                if (isPlaying)
                    NotificationCompat.Action.Builder(R.drawable.pause, "暂停", pausePending).build()
                else
                    NotificationCompat.Action.Builder(R.drawable.play_fill, "播放", playPending).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.forward_end_fill, "下一首", nextPending
                ).build()
            )
            .setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)

        // 只在有封面时设置大图标
        if (albumArtBitmap != null) {
            builder.setLargeIcon(albumArtBitmap)
        }

        return builder.build()
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate() {
        super.onCreate()
        // ExoPlayer 已通过 Koin 注入,这里只需配置
        exoPlayer.apply {
            // 设置重复模式,让系统知道有下一首
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        getCurrentPlayingMusic()?.let { updateNotificationWithCover(it) }
                        // 尝试初始化音效
                        initializeAudioEffectsIfNeeded()
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        playbackListener?.onPlaybackEnded()
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    HmpLog.d(LogTag.PlayerService) { "📡 onIsPlayingChanged: $isPlaying" }
                    getCurrentPlayingMusic()?.let { updateNotificationPlaybackState(it) }
                    playbackListener?.onPlayStateChanged(isPlaying)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    HmpLog.e(LogTag.PlayerService, error) { "📡 Player error: ${error.message}" }
                    playbackListener?.onPlayStateChanged(false)
                }
            })
        }
        createNotificationChannel()

        // 注册耳机拔插和蓝牙断开广播接收器
        registerAudioDeviceReceiver()

        // 创建自定义 Player 包装器
        customPlayer = object : ForwardingPlayer(exoPlayer) {
            // 重写方法让系统认为始终有上/下一首
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            // 重写 seekToNext 方法
            override fun seekToNext() {
                HmpLog.d(LogTag.PlayerService) { "📡 ForwardingPlayer.seekToNext() called" }
                playbackListener?.onPlaybackNext()
            }

            // 重写 seekToPrevious 方法
            override fun seekToPrevious() {
                HmpLog.d(LogTag.PlayerService) { "📡 ForwardingPlayer.seekToPrevious() called" }
                playbackListener?.onPlaybackPrev()
            }

            // 告诉系统有下一首
            override fun hasNextMediaItem(): Boolean = true

            // 告诉系统有上一首
            override fun hasPreviousMediaItem(): Boolean = true
        }

        // 创建 Media3 MediaSession,使用自定义 Player
        mediaSession = MediaSession.Builder(this, customPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        )
                        .setAvailablePlayerCommands(
                            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                        )
                        .build()
                }

                // 添加命令拦截,确保系统命令被正确处理
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    HmpLog.d(LogTag.PlayerService) { "📡 onPlayerCommandRequest: $playerCommand, listener: ${playbackListener != null}" }
                    when (playerCommand) {
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            HmpLog.d(LogTag.PlayerService) { "📡 Seek to next requested" }
                            // 在主线程调用 listener
                            CoroutineScope(Dispatchers.Main).launch {
                                playbackListener?.onPlaybackNext()
                            }
                            return SessionResult.RESULT_SUCCESS
                        }
                        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            HmpLog.d(LogTag.PlayerService) { "📡 Seek to previous requested" }
                            // 在主线程调用 listener
                            CoroutineScope(Dispatchers.Main).launch {
                                playbackListener?.onPlaybackPrev()
                            }
                            return SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            })
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterAudioDeviceReceiver()
        audioEffectManager.release()
        mediaSession.release()
        exoPlayer.release()
    }

    // 注册音频设备广播接收器
    private fun registerAudioDeviceReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            registerReceiver(audioBecomingNoisyReceiver, filter)
            isReceiverRegistered = true
            HmpLog.d(LogTag.PlayerService) { "📡 Audio device receiver registered" }
        }
    }

    // 注销音频设备广播接收器
    private fun unregisterAudioDeviceReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(audioBecomingNoisyReceiver)
                isReceiverRegistered = false
                HmpLog.d(LogTag.PlayerService) { "📡 Audio device receiver unregistered" }
            } catch (e: IllegalArgumentException) {
                HmpLog.e(LogTag.PlayerService, e) { "📡 Receiver already unregistered" }
            }
        }
    }

    // 播放指定音乐
    override fun prepareMusic(music: Music) {
        originalTitle = music.title
        originalArtist = music.artist
        currentLyricLine = null
        val mediaItem = MediaItem.Builder()
            .setUri(music.path)
            .setMediaId(music.id.toString()) // 用id作为唯一标识
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(music.title)
                    .setArtist(music.artist)
                    .setAlbumTitle(music.album)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

    }

    override fun play() { exoPlayer.play() }
    override fun pause() { exoPlayer.pause() }

    override fun playSingleMusic(music: Music) {
        cacheMusic(music)
        prepareMusic(music)
        exoPlayer.play()
    }

    // 继续播放
    override fun proceedMusic() = exoPlayer.play()

    // 停止播放并清空播放内容
    override fun stopMusic() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    // 判断是否已加载目标音频
    override fun isMusicLoaded(path: String): Boolean {
        val current = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        return current == path && exoPlayer.playbackState != Player.STATE_IDLE
    }

    // 跳转到指定位置(毫秒)
     override fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }

    // 获取当前播放进度(毫秒)
    override fun getCurrentPosition(): Long = exoPlayer.currentPosition

    // 获取当前音频总时长(毫秒)
    override fun getDuration(): Long = exoPlayer.duration

    // 假设你有一个 musicMap: Map<Long, Music>
    private val musicMap = mutableMapOf<Long, Music>()

    // 添加音乐时同步到 map
    private fun cacheMusic(music: Music) {
        musicMap[music.id] = music
    }

    // 获取当前播放音乐
    private fun getCurrentPlayingMusic(): Music? {
        val mediaId = exoPlayer.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return musicMap[mediaId]
    }

    // MainActivity类引用,需要从外部设置
    private var mainActivityClass: Class<*>? = null
    
    fun setMainActivityClass(activityClass: Class<*>) {
        mainActivityClass = activityClass
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                exoPlayer.play()
                getCurrentPlayingMusic()?.let { updateNotificationPlaybackState(it) }
                playbackListener?.onPlayStateChanged(true) // 新增
            }
            ACTION_PAUSE -> {
                exoPlayer.pause()
                getCurrentPlayingMusic()?.let { updateNotificationPlaybackState(it) }
                playbackListener?.onPlayStateChanged(false) // 新增
            }
            ACTION_NEXT -> playbackListener?.onPlaybackNext()
            ACTION_PREV -> playbackListener?.onPlaybackPrev()
        }

        val music = getCurrentPlayingMusic()
        music?.let {
            // 切换到主线程
            CoroutineScope(Dispatchers.Main).launch {
                val notification = buildNotification(it, null, mainActivityClass ?: return@launch)
                // API 34+ 需要指定前台服务类型
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(1, notification)
                }

                // 异步加载封面
                CoroutineScope(Dispatchers.IO).launch {
                    val request = ImageRequest.Builder(this@MusicPlayService)
                        .data(music.albumArtUri)
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        // 回到主线程更新通知
                        CoroutineScope(Dispatchers.Main).launch {
                            val updatedNotification = buildNotification(it, bitmap, mainActivityClass ?: return@launch)
                            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(1, updatedNotification)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    // 只用已有封面刷新通知(不重新加载封面)
    private fun updateNotificationPlaybackState(music: Music) {
        CoroutineScope(Dispatchers.Main).launch {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notification = buildNotification(music, currentAlbumArtBitmap, mainActivityClass ?: return@launch, currentLyricLine)
            manager.notify(1, notification)
        }
    }

    // 更新歌词行 → 通知 + MediaItem metadata
    override fun updateLyricLine(lyric: String?) {
        currentLyricLine = lyric
        val music = getCurrentPlayingMusic() ?: return

        // 更新通知（轻量，直接刷新）
        CoroutineScope(Dispatchers.Main).launch {
            val notification = buildNotification(music, currentAlbumArtBitmap, mainActivityClass ?: return@launch, lyric)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1, notification)
        }

        // 更新 MediaItem metadata（锁屏 / 蓝牙 / 车载）
        val currentItem = exoPlayer.currentMediaItem ?: return
        val newTitle = lyric ?: (originalTitle ?: music.title)
        val newArtist = if (lyric != null) {
            "${originalTitle ?: music.title} — ${originalArtist ?: music.artist}"
        } else {
            originalArtist ?: music.artist
        }
        val newItem = currentItem.buildUpon()
            .setMediaMetadata(
                currentItem.mediaMetadata.buildUpon()
                    .setTitle(newTitle)
                    .setArtist(newArtist)
                    .build()
            ).build()
        exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, newItem)
    }

    private var currentAlbumArtBitmap: Bitmap? = null
    // 刷新通知(重新加载封面)
    private fun updateNotificationWithCover(music: Music) {
        // 先显示默认封面
        val notification = buildNotification(music, null, mainActivityClass ?: return, currentLyricLine)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(1, notification)
        }

        // 异步加载封面
        CoroutineScope(Dispatchers.IO).launch {
            val request = ImageRequest.Builder(this@MusicPlayService)
                .data(music.albumArtUri)
                .size(256)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    currentAlbumArtBitmap = bitmap // 缓存封面
                    val updatedNotification = buildNotification(music, bitmap, mainActivityClass ?: return@launch, currentLyricLine)
                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(1, updatedNotification)
                }
            }
        }
    }
    
    // 初始化音效（延迟初始化，等待播放器准备就绪）
    private fun initializeAudioEffectsIfNeeded() {
        if (isAudioEffectInitialized || audioEffectInitRetryCount >= maxAudioEffectInitRetries) {
            return
        }
        
        audioEffectInitRetryCount++
        
        try {
            val sessionId = exoPlayer.audioSessionId
            if (sessionId > 0) {
                val success = audioEffectManager.initialize(sessionId)
                if (success) {
                    isAudioEffectInitialized = true
                    HmpLog.d(LogTag.PlayerService) { "📡 Audio effects initialized successfully" }
                    
                    // 恢复之前的音效设置
                    restoreAudioEffectSettings()
                } else {
                    HmpLog.w(LogTag.PlayerService) { "📡 Failed to initialize audio effects (attempt $audioEffectInitRetryCount)" }
                }
            } else {
                HmpLog.w(LogTag.PlayerService) { "📡 Invalid audio session ID: $sessionId" }
            }
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerService, e) { "📡 Error initializing audio effects" }
        }
    }
    
    // 恢复音效设置
    private fun restoreAudioEffectSettings() {
        if (equalizerPreset != 0) {
            audioEffectManager.setEqualizerPreset(equalizerPreset)
        }
        if (bassBoostLevel != 0) {
            audioEffectManager.setBassBoostStrength((bassBoostLevel * 10).toShort())
        }
        if (surroundSoundEnabled) {
            audioEffectManager.setVirtualizerEnabled(true)
        }
        if (reverbPreset != 0) {
            audioEffectManager.setReverbPreset(reverbPreset.toShort())
        }
        if (customEqualizerLevels.isNotEmpty()) {
            customEqualizerLevels.forEachIndexed { index, level ->
                audioEffectManager.setEqualizerBandLevel(index, level.toInt().toShort())
            }
        }
    }
    
    // 音效控制方法实现
    override fun setEqualizerPreset(preset: Int) {
        if (audioEffectManager.setEqualizerPreset(preset)) {
            equalizerPreset = preset
            HmpLog.d(LogTag.PlayerService) { "📡 Set equalizer preset: $preset" }
        } else {
            HmpLog.w(LogTag.PlayerService) { "📡 Failed to set equalizer preset: $preset" }
        }
    }
    
    override fun setBassBoost(level: Int) {
        val strength = (level * 10).toShort()
        if (audioEffectManager.setBassBoostStrength(strength)) {
            bassBoostLevel = level
            HmpLog.d(LogTag.PlayerService) { "📡 Set bass boost level: $level (strength: $strength)" }
        } else {
            HmpLog.w(LogTag.PlayerService) { "📡 Failed to set bass boost level: $level" }
        }
    }
    
    override fun setSurroundSound(enabled: Boolean) {
        if (audioEffectManager.setVirtualizerEnabled(enabled)) {
            surroundSoundEnabled = enabled
            HmpLog.d(LogTag.PlayerService) { "📡 Set surround sound: $enabled" }
        } else {
            HmpLog.w(LogTag.PlayerService) { "📡 Failed to set surround sound: $enabled" }
        }
    }
    
    override fun setReverb(preset: Int) {
        if (audioEffectManager.setReverbPreset(preset.toShort())) {
            reverbPreset = preset
            HmpLog.d(LogTag.PlayerService) { "📡 Set reverb preset: $preset" }
        } else {
            HmpLog.w(LogTag.PlayerService) { "📡 Failed to set reverb preset: $preset" }
        }
    }
    
    override fun setCustomEqualizer(bandLevels: FloatArray) {
        var success = true
        bandLevels.forEachIndexed { index, level ->
            if (!audioEffectManager.setEqualizerBandLevel(index, level.toInt().toShort())) {
                success = false
            }
        }
        
        if (success) {
            customEqualizerLevels = bandLevels
            HmpLog.d(LogTag.PlayerService) { "📡 Set custom equalizer levels: ${bandLevels.contentToString()}" }
        } else {
            HmpLog.w(LogTag.PlayerService) { "📡 Failed to set some custom equalizer levels" }
        }
    }
    
    override fun getEqualizerPresets(): List<String> {
        return equalizerPresets
    }
    
    override fun getCurrentEqualizerPreset(): Int {
        return equalizerPreset
    }
    
    override fun getBassBoostLevel(): Int {
        return bassBoostLevel
    }
    
    override fun isSurroundSoundEnabled(): Boolean {
        return surroundSoundEnabled
    }
    
    override fun getReverbPreset(): Int {
        return reverbPreset
    }
    
    override fun getEqualizerBandCount(): Int {
        return audioEffectManager.getEqualizerBandCount()
    }
    
    override fun getEqualizerBandLevelRange(): Pair<Int, Int> {
        val range = audioEffectManager.getEqualizerBandLevelRange()
        return Pair(range.first.toInt(), range.second.toInt())
    }
    
    override fun getCurrentEqualizerBandLevels(): FloatArray {
        // 从音效管理器获取当前各频段的增益值
        val levels = audioEffectManager.getCurrentEqualizerBandLevels()
        return if (levels.isNotEmpty()) {
            FloatArray(levels.size) { levels[it].toFloat() }
        } else {
            FloatArray(audioEffectManager.getEqualizerBandCount()) { 0f }
        }
    }
}
