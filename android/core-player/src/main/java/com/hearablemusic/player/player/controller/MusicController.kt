package com.hearablemusic.player.player.controller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.lyrics.LrcParser
import com.hmp.domain.lyrics.LyricLineData
import com.hmp.domain.lyrics.findCurrentLyricIndex
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AudioEffectSettings
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import com.hearablemusic.player.player.service.MusicPlayService
import com.hearablemusic.player.player.service.PlayControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class MusicController(
    private val context: Context,
    private val currentPlaybackUseCase: CurrentPlaybackUseCase,
    private val playbackHistoryUseCase: PlaybackHistoryUseCase,
    private val timerUseCase: TimerUseCase,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val settingsRepository: SettingsRepository
) : MusicPlayService.OnMusicCompleteListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        /** 播放进度采集间隔：保证逐字（卡拉 OK）渐变足够平滑 */
        const val PROGRESS_TRACKING_INTERVAL_MS = 100L
        /** 播放位置持久化节流间隔 */
        const val PROGRESS_PERSIST_INTERVAL_MS = 5_000L
    }

    private var playControl: PlayControl? = null
    private var activityClass: Class<*>? = null

    fun setTargetActivityClass(clazz: Class<*>) {
        activityClass = clazz
    }
    
    // Service Connection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? MusicPlayService.MusicPlayServiceBinder)?.getService()
            if (service != null) {
                activityClass?.let { service.setMainActivityClass(it) }
                bindPlayControl(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bindPlayControl(null)
        }
    }

    fun bindService() {
        val intent = Intent(context, MusicPlayService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        try {
            context.unbindService(connection)
        } catch (e: Exception) {
            Log.e("MusicController", "Error unbinding service", e)
        }
        bindPlayControl(null)
    }

    fun bindPlayControl(service: PlayControl?) {
        this.playControl = service
        if (service is MusicPlayService) {
            service.setOnMusicCompleteListener(this)
        }
        // 绑定后恢复音效设置
        if (service != null) {
            restoreAudioEffectSettings()
            // 恢复播放进度
            scope.launch {
                val lastPos = _currentPosition.value
                if (lastPos > 0 && !_isPlaying.value) {
                    service.seekTo(lastPos)
                }
            }
        }
    }

    // Events
    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
    }

    private val _toastEvent = MutableSharedFlow<UiEvent.ShowToast>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val toastEvent = _toastEvent.asSharedFlow()

    private fun showToast(message: String) {
        scope.launch {
            _toastEvent.emit(UiEvent.ShowToast(message))
        }
    }

    // Playback State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMiniPlayerVisible = MutableStateFlow(true)
    val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible.asStateFlow()

    fun setMiniPlayerVisible(visible: Boolean) {
        _isMiniPlayerVisible.value = visible
    }

    // Playlist
    private val _currentPlaylist = MutableStateFlow<List<MusicInfo>>(emptyList())
    val currentPlaylist: StateFlow<List<MusicInfo>> = _currentPlaylist.asStateFlow()

    // IDs
    private val currentPlayListId = settingsRepository.currentPlaylistId
    private val likedPlayListId = settingsRepository.likedPlaylistId
    private val recentPlayListId = settingsRepository.recentPlaylistId

    // Index
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Current Music
    val currentPlayingMusic: StateFlow<MusicInfo?> = combine(
        currentPlaylist,
        currentIndex
    ) { playlist, index ->
        playlist.getOrNull(index)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    // Like Status
    var likeStatus = MutableStateFlow(false)

    // Labels & Lyrics
    private val _currentMusicLabels = MutableStateFlow<List<MusicLabel?>>(emptyList())
    val currentMusicLabels: StateFlow<List<MusicLabel?>> = _currentMusicLabels

    private val _currentMusicLyrics = MutableStateFlow<String?>(null)
    val currentMusicLyrics: StateFlow<String?> = _currentMusicLyrics

    // 歌词追踪：在 Controller 层解析 LRC + 判定当前行，不依赖 UI
    private val parsedLyrics = mutableListOf<LyricLineData>()
    private var lastLyricCheckIndex = -1

    // Playback Mode
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    // Position & Duration
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var progressJob: Job? = null

    private var playStartTime: Long = 0L
    private var lastDurationRecordTime: Long = 0L
    private val durationRecordThreshold = 30000L

    // 互动数据记录追踪
    private var currentPlaybackHistoryId: Long? = null
    private var totalPlayedDurationInSession: Long = 0L
    private val skipThresholdMs = 20000L // 20秒以内切歌算跳过
    private val skipThresholdPercent = 0.15f // 播放不足15%算跳过

    // Timer
    private var timerJob: Job? = null
    val timerRemaining: StateFlow<Long?> = timerUseCase.timerRemaining

    // Audio Effects
    private val _audioEffectSettings = MutableStateFlow(AudioEffectSettings())
    val audioEffectSettings: StateFlow<AudioEffectSettings> = _audioEffectSettings.asStateFlow()

    private val _equalizerPresets = MutableStateFlow<List<String>>(emptyList())
    val equalizerPresets: StateFlow<List<String>> = _equalizerPresets.asStateFlow()

    private val _equalizerBandCount = MutableStateFlow(0)
    val equalizerBandCount: StateFlow<Int> = _equalizerBandCount.asStateFlow()

    private val _equalizerBandLevelRange = MutableStateFlow(Pair(0, 0))
    val equalizerBandLevelRange: StateFlow<Pair<Int, Int>> = _equalizerBandLevelRange.asStateFlow()

    private val _currentEqualizerBandLevels = MutableStateFlow(floatArrayOf())
    val currentEqualizerBandLevels: StateFlow<FloatArray> = _currentEqualizerBandLevels.asStateFlow()

    init {
        // 初始化播放列表和进度
        scope.launch {
            loadPlaylistFromSettings()
            restoreLastPosition()
        }

        // 监听默认播放列表的变化
        scope.launch {
            currentPlayListId
                .filterNotNull()
                .collectLatest { playlistId ->
                    managePlaylistUseCase.getMusicInfoInPlaylist(playlistId)
                        .collect { playlist ->
                            _currentPlaylist.value = playlist
                            // 更新索引
                            updateCurrentIndex()
                        }
                }
        }

        // 监听当前播放音乐的变化
        scope.launch {
            currentPlayingMusic
                .filterNotNull()
                .collectLatest { musicInfo ->
                    preloadCurrentMusicInfo(musicInfo)
                }
        }

        // 监听播放位置变化 → 判定当前歌词行 → 推送到锁屏/通知
        scope.launch {
            _currentPosition.collect { position ->
                if (parsedLyrics.isEmpty()) return@collect
                val index = findCurrentLyricIndex(parsedLyrics, position)
                if (index != lastLyricCheckIndex) {
                    lastLyricCheckIndex = index
                    playControl?.updateLyricLine(parsedLyrics[index].originalText)
                }
            }
        }
    }

    private fun updateCurrentIndex() {
        val current = currentPlayingMusic.value
        if (current != null) {
            _currentIndex.value = _currentPlaylist.value.indexOfFirst { it.music.id == current.music.id }
                .takeIf { it >= 0 } ?: 0
        }
    }

    private suspend fun restoreLastPosition() {
        try {
            val lastPos = settingsRepository.currentPosition.first()
            _currentPosition.value = lastPos
            // 如果服务已经绑定，尝试seek到该位置
            playControl?.seekTo(lastPos)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private suspend fun loadPlaylistFromSettings() {
        try {
            val playlistId = currentPlayListId.filterNotNull().first()
            val currentMusicId = currentPlaybackUseCase.getCurrentMusicId().first()
            val list = managePlaylistUseCase.getMusicInfoInPlaylist(playlistId).first()
            _currentPlaylist.value = list
            _playbackMode.value = PlaybackMode.SEQUENTIAL
            _currentIndex.value = list.indexOfFirst { it.music.id == currentMusicId }.takeIf { it >= 0 } ?: 0
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun preloadCurrentMusicInfo(musicInfo: MusicInfo) {
        _duration.value = musicInfo.music.duration
        getLikedStatus(musicInfo.music.id)
        getMusicLabels(musicInfo.music.id)
        getMusicLyrics(musicInfo.music.id)
    }

    fun startProgressTracking() {
        if (progressJob?.isActive == true) return

        progressJob = scope.launch {
            var lastPersistMs = 0L
            while (isActive) {
                playControl?.let { svc ->
                    val pos = svc.getCurrentPosition()
                    _currentPosition.value = pos
                    // 持久化当前播放进度（节流，避免高频写盘）
                    val now = System.currentTimeMillis()
                    if (now - lastPersistMs >= PROGRESS_PERSIST_INTERVAL_MS) {
                        lastPersistMs = now
                        persistCurrentPosition(pos)
                    }
                    recordListeningDurationPeriodically()
                }
                delay(PROGRESS_TRACKING_INTERVAL_MS)
            }
        }
    }
    
    private fun persistCurrentPosition(position: Long) {
        scope.launch {
            try {
                settingsRepository.saveCurrentPosition(position)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun recordListeningDurationPeriodically() {
        val now = System.currentTimeMillis()
        if (_isPlaying.value && playStartTime > 0 &&
            (now - lastDurationRecordTime) >= durationRecordThreshold) {
            val duration = now - playStartTime
            totalPlayedDurationInSession += duration
            scope.launch {
                playbackHistoryUseCase.recordListeningDuration(duration)
            }
            lastDurationRecordTime = now
            playStartTime = now
        }
    }

    fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun clearPlaylist() {
        _currentPlaylist.value = emptyList()
        _currentIndex.value = 0
        persistCurrentPlaylistToDatabase()
    }



    fun isMusicLoaded(path: String): Boolean? {
        return playControl?.isMusicLoaded(path)
    }

    fun playOrResume() {
        if (playControl == null) {
            Log.e("MusicController", "playOrResume: playControl is null")
            return
        }
        playStartTime = System.currentTimeMillis()
        lastDurationRecordTime = playStartTime
        val path = currentMusicPath()
        if (path != null && isMusicLoaded(path) == true) {
            scope.launch { playControl?.proceedMusic() }
            showToast("继续")
            // 如果是从暂停状态恢复，确保UI进度与Service同步
            scope.launch {
                val currentPos = playControl?.getCurrentPosition() ?: 0L
                if (currentPos > 0) {
                    _currentPosition.value = currentPos
                }
            }
        } else {
            // 如果是初始状态（未加载），则尝试恢复上次进度播放
            val lastPos = _currentPosition.value
            if (lastPos > 0) {
                 scope.launch { 
                     playCurrentTrack("Resume", startPosition = lastPos)
                 }
            } else {
                 scope.launch { 
                     playCurrentTrack("Resume")
                 }
            }
        }
        startProgressTracking()
    }

    fun pauseMusic() {
        if (playControl == null) {
            Log.e("MusicController", "pauseMusic: playControl is null")
            return
        }
        if (playStartTime > 0) {
            val duration = System.currentTimeMillis() - playStartTime
            if (duration > 0) {
                totalPlayedDurationInSession += duration
                scope.launch {
                    playbackHistoryUseCase.recordListeningDuration(duration)
                }
            }
        }
        scope.launch { playControl?.pause() }
        showToast("暂停")
        playStartTime = 0L
        lastDurationRecordTime = 0L
    }

    fun seekTo(position: Long) {
        scope.launch {
            if (!_isPlaying.value) {
                playCurrentTrack("Resume")
            }
            playControl?.seekTo(position)
        }
    }

    fun updateLyricLine(lyric: String?) {
        playControl?.updateLyricLine(lyric)
    }

    private fun togglePlaybackMode(newMode: PlaybackMode) {
        _playbackMode.value = newMode
    }

    fun togglePlaybackModeByOrder() {
        val next = when (_playbackMode.value) {
            PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
        }
        togglePlaybackMode(next)
    }

    fun addAllToPlaylistInOrder(playlist: List<MusicInfo>) {
        scope.launch {
            _currentPlaylist.value = playlist
            togglePlaybackMode(PlaybackMode.SEQUENTIAL)
            _currentIndex.value = 0
            persistCurrentPlaylistToDatabase()
            playCurrentTrack("Order")
        }
    }

    fun addAllToPlaylistByShuffle(playlist: List<MusicInfo>) {
        scope.launch {
            _currentPlaylist.value = playlist
            togglePlaybackMode(PlaybackMode.SHUFFLE)
            _currentIndex.value = 0
            persistCurrentPlaylistToDatabase()
            playCurrentTrack("Shuffle")
        }
    }

    private fun generateRandomIndex(currentIndex: Int, size: Int): Int {
        if (size <= 1) return 0
        var randomIndex: Int
        do {
            randomIndex = kotlin.random.Random.nextInt(size)
        } while (randomIndex == currentIndex)
        return randomIndex
    }

    fun playNext() = scope.launch {
        if (_currentPlaylist.value.isEmpty()) return@launch
        if (_playbackMode.value != PlaybackMode.REPEAT_ONE) {
            _currentIndex.value = when (_playbackMode.value) {
                PlaybackMode.SHUFFLE -> {
                    generateRandomIndex(_currentIndex.value, _currentPlaylist.value.size)
                }
                else -> {
                    // 顺序播放
                    (_currentIndex.value + 1).mod(_currentPlaylist.value.size)
                }
            }
        }
        showToast("下一曲")
        playCurrentTrack("Next")
    }

    fun playPrevious() = scope.launch {
        if (_currentPlaylist.value.isEmpty()) return@launch
        if (_playbackMode.value != PlaybackMode.REPEAT_ONE) {
            _currentIndex.value = when (_playbackMode.value) {
                PlaybackMode.SHUFFLE -> {
                    generateRandomIndex(_currentIndex.value, _currentPlaylist.value.size)
                }
                else -> {
                    // 顺序播放
                    (_currentIndex.value - 1).mod(_currentPlaylist.value.size)
                }
            }
        }
        showToast("上一曲")
        playCurrentTrack("Previous")
    }

    override fun onPlaybackEnded() {
        endCurrentPlaybackSession(isCompleted = true)
        playNext()
    }

    override fun onPlaybackNext() {
        playNext()
    }

    override fun onPlaybackPrev() {
        playPrevious()
    }

    override fun onPlayStateChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    private fun playCurrentTrack(source: String, startPosition: Long = 0L) {
        if (playControl == null) {
            Log.e("MusicController", "playCurrentTrack: playControl is null")
            return
        }

        // 结束上一个会话（如果有的话）
        endCurrentPlaybackSession(isCompleted = false)

        stopProgressTracking()
        val track = _currentPlaylist.value.getOrNull(_currentIndex.value) ?: return

        scope.launch {
            try {
                val recentId = withTimeoutOrNull(1000) {
                    recentPlayListId.firstOrNull()
                }
                if (recentId != null) {
                    managePlaylistUseCase.addToPlaylist(recentId, track.music.id, track.music.path)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        persistCurrentMusic(track.music.id)
        _currentPosition.value = startPosition
        _duration.value = track.music.duration

        // Media3 操作必须在 Main dispatcher
        scope.launch {
            playControl?.playSingleMusic(track.music)
            if (startPosition > 0) {
                playControl?.seekTo(startPosition)
            }
        }

        // 重置会话追踪数据
        playStartTime = System.currentTimeMillis()
        lastDurationRecordTime = playStartTime
        totalPlayedDurationInSession = 0L

        startProgressTracking()
        startNewPlaybackSession(track.music.id, source)
    }

    private fun startNewPlaybackSession(musicId: Long, source: String?) {
        scope.launch {
            currentPlaybackHistoryId = playbackHistoryUseCase.startPlaybackSession(musicId, source)
        }
    }

    private fun endCurrentPlaybackSession(isCompleted: Boolean) {
        val historyId = currentPlaybackHistoryId ?: return
        val currentMusic = currentPlayingMusic.value ?: return
        val musicId = currentMusic.music.id
        
        // 计算最后的播放时长
        if (_isPlaying.value && playStartTime > 0) {
            totalPlayedDurationInSession += (System.currentTimeMillis() - playStartTime)
        }
        
        val duration = totalPlayedDurationInSession
        val totalDuration = currentMusic.music.duration
        
        scope.launch {
            if (isCompleted) {
                playbackHistoryUseCase.completePlaybackSession(historyId, musicId, totalDuration)
            } else {
                // 只要没有播放完成（手动切换或停止），就视作跳过
                playbackHistoryUseCase.skipPlaybackSession(historyId, musicId, duration, true)
            }
        }
        
        currentPlaybackHistoryId = null
        totalPlayedDurationInSession = 0L
        playStartTime = 0L
    }

    fun addToPlaylist(musicInfo: MusicInfo) {
        if (_currentPlaylist.value.none { it.music.id == musicInfo.music.id }) {
            _currentPlaylist.value = _currentPlaylist.value + musicInfo
            persistCurrentPlaylistToDatabase()
        }
    }
    
    fun addToNextPlay(musicInfo: MusicInfo) {
        val currentIndex = _currentIndex.value
        val newList = _currentPlaylist.value.toMutableList()
        
        // 检查歌曲是否已在播放列表中
        val existingIndex = newList.indexOfFirst { it.music.id == musicInfo.music.id }
        
        if (existingIndex != -1) {
            // 歌曲已存在，先移除
            newList.removeAt(existingIndex)
            // 如果移除的歌曲在当前播放索引之前或就是当前播放的歌曲，需要调整当前索引
            if (existingIndex <= currentIndex) {
                _currentIndex.value = (currentIndex - 1).coerceAtLeast(0)
            }
        }
        
        // 计算插入位置（当前播放位置的下一首）
        val adjustedCurrentIndex = _currentIndex.value
        val insertIndex = if (newList.isEmpty()) {
            0
        } else {
            (adjustedCurrentIndex + 1).coerceAtMost(newList.size)
        }
        
        // 插入歌曲
        newList.add(insertIndex, musicInfo)
        _currentPlaylist.value = newList
        persistCurrentPlaylistToDatabase()
    }

    private fun switchToMusicInPlaylist(musicInfo: MusicInfo) {
        val index = _currentPlaylist.value.indexOfFirst { it.music.id == musicInfo.music.id }
        _currentIndex.value = if (index != -1) index else 0
    }

    fun removeFromPlaylist(musicInfo: MusicInfo) {
        _currentPlaylist.value = _currentPlaylist.value.filter { it.music.id != musicInfo.music.id }
        persistCurrentPlaylistToDatabase()
        // 更新索引
        updateCurrentIndex()
    }
    
    fun moveToTop(musicInfo: MusicInfo) {
        val currentList = _currentPlaylist.value.toMutableList()
        val index = currentList.indexOfFirst { it.music.id == musicInfo.music.id }
        if (index > 0) {
            val item = currentList.removeAt(index)
            currentList.add(0, item)
            _currentPlaylist.value = currentList
            persistCurrentPlaylistToDatabase()
            // 更新索引
            updateCurrentIndex()
            showToast("已置顶：${musicInfo.music.title}")
        }
    }

    fun playAt(musicInfo: MusicInfo) {
        switchToMusicInPlaylist(musicInfo)
        playCurrentTrack("Manual")
    }

    suspend fun playWith(musicInfo: MusicInfo) {
        addToPlaylist(musicInfo)
        playAt(musicInfo)
    }

    private fun currentMusicPath(): String? {
        return _currentPlaylist.value.getOrNull(_currentIndex.value)?.music?.path
    }

    fun updateMusicLikedStatus(musicInfo: MusicInfo, liked: Boolean) {
        scope.launch {
            currentPlaybackUseCase.updateLikedStatus(musicInfo.music.id, liked)
            try {
                val likedId = likedPlayListId.filterNotNull().first()
                if (liked) {
                    managePlaylistUseCase.addToPlaylist(likedId, musicInfo.music.id, musicInfo.music.path)
                } else {
                    managePlaylistUseCase.removeItemFromPlaylist(musicInfo.music.id, likedId)
                }
            } catch (e: Exception) {
                // Ignore
            }
            getLikedStatus(musicInfo.music.id)
        }
    }

    fun getLikedStatus(musicId: Long) {
        scope.launch {
            likeStatus.value = currentPlaybackUseCase.getLikedStatus(musicId)
        }
    }
    
    suspend fun getCurrentLikedStatus(musicId: Long): Boolean {
        return currentPlaybackUseCase.getLikedStatus(musicId)
    }

    fun playHeartMode() {
        scope.launch {
            val currentMusic = currentPlayingMusic.value ?: return@launch
            val similarSongs = currentPlaybackUseCase.getSimilarSongsByWeightedLabels(currentMusic.music.id, limit = 10)
            if (similarSongs.isNotEmpty()) {
                val newList = listOf(currentMusic) + similarSongs
                _currentPlaylist.value = newList
                _currentIndex.value = 0
                playCurrentTrack("HeartMode")
                showToast("为你推荐${similarSongs.size}首心动歌曲")
            } else {
                showToast("未找到相似歌曲")
            }
        }
    }

    fun startTimer(minutes: Int) {
        timerUseCase.setTimerRemaining((minutes * 60 * 1000L))
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                timerUseCase.decrementTimer(1000)
                if (timerUseCase.isTimerExpired()) {
                    pauseMusic()
                    timerUseCase.cancelTimer()
                    break
                }
            }
        }
    }
    
    fun cancelTimer() {
        timerJob?.cancel()
        timerUseCase.cancelTimer()
    }

    fun getMusicLabels(musicId: Long) {
        scope.launch {
            _currentMusicLabels.value = currentPlaybackUseCase.getMusicLabels(musicId)
        }
    }

    fun getMusicLyrics(musicId: Long) {
        scope.launch {
            // 切歌时先清除旧歌词
            parsedLyrics.clear()
            lastLyricCheckIndex = -1
            playControl?.updateLyricLine(null)

            val lyricsText = currentPlaybackUseCase.getMusicLyrics(musicId)
            _currentMusicLyrics.value = lyricsText

            if (lyricsText != null) {
                parsedLyrics.addAll(LrcParser.parse(lyricsText))
            }
        }
    }

    private fun persistCurrentMusic(id: Long) {
        scope.launch { currentPlaybackUseCase.saveCurrentMusicId(id) }
    }

    private fun persistCurrentPlaylistToDatabase() {
        scope.launch {
            try {
                val playlistId = currentPlayListId.filterNotNull().first()
                managePlaylistUseCase.resetPlaylistItems(playlistId, _currentPlaylist.value)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    // Audio Effect Logic
    private var saveAudioEffectJob: Job? = null
    
    private fun restoreAudioEffectSettings() {
        scope.launch {
            try {
                val equalizerPreset = settingsRepository.equalizerPreset.first()
                val bassBoostLevel = settingsRepository.bassBoostLevel.first()
                val isSurroundSoundEnabled = settingsRepository.isSurroundSoundEnabled.first()
                val reverbPreset = settingsRepository.reverbPreset.first()
                val customLevels = settingsRepository.customEqualizerLevels.first()
                
                playControl?.let { control ->
                    control.setEqualizerPreset(equalizerPreset)
                    control.setBassBoost(bassBoostLevel)
                    control.setSurroundSound(isSurroundSoundEnabled)
                    control.setReverb(reverbPreset)
                    if (customLevels.isNotEmpty()) {
                        control.setCustomEqualizer(customLevels)
                    }
                }
                
                _audioEffectSettings.value = AudioEffectSettings(
                    equalizerPreset = equalizerPreset,
                    bassBoostLevel = bassBoostLevel,
                    isSurroundSoundEnabled = isSurroundSoundEnabled,
                    reverbPreset = reverbPreset,
                    customEqualizerLevels = customLevels
                )
                
            } catch (e: Exception) {
                Log.e("MusicController", "Failed to restore audio effect settings", e)
            }
        }
    }
    
    fun initializeAudioEffects() {
        playControl?.let { control ->
            _equalizerPresets.value = control.getEqualizerPresets()
            _equalizerBandCount.value = control.getEqualizerBandCount()
            _equalizerBandLevelRange.value = control.getEqualizerBandLevelRange()
            _currentEqualizerBandLevels.value = control.getCurrentEqualizerBandLevels()
            
            _audioEffectSettings.value = AudioEffectSettings(
                equalizerPreset = control.getCurrentEqualizerPreset(),
                bassBoostLevel = control.getBassBoostLevel(),
                isSurroundSoundEnabled = control.isSurroundSoundEnabled(),
                reverbPreset = control.getReverbPreset(),
                customEqualizerLevels = control.getCurrentEqualizerBandLevels()
            )
        }
    }
    
    fun setEqualizerPreset(preset: Int) {
        playControl?.let { control ->
            control.setEqualizerPreset(preset)
            _audioEffectSettings.value = _audioEffectSettings.value.copy(
                equalizerPreset = preset
            )
            saveAudioEffectSetting {
                settingsRepository.saveEqualizerPreset(preset)
            }
        }
    }
    
    fun setBassBoost(level: Int) {
        playControl?.let { control ->
            control.setBassBoost(level)
            _audioEffectSettings.value = _audioEffectSettings.value.copy(
                bassBoostLevel = level
            )
            saveAudioEffectSetting {
                settingsRepository.saveBassBoostLevel(level)
            }
        }
    }
    
    fun setSurroundSound(enabled: Boolean) {
        playControl?.let { control ->
            control.setSurroundSound(enabled)
            _audioEffectSettings.value = _audioEffectSettings.value.copy(
                isSurroundSoundEnabled = enabled
            )
            saveAudioEffectSetting {
                settingsRepository.saveSurroundSoundEnabled(enabled)
            }
        }
    }
    
    fun setReverb(preset: Int) {
        playControl?.let { control ->
            control.setReverb(preset)
            _audioEffectSettings.value = _audioEffectSettings.value.copy(
                reverbPreset = preset
            )
            saveAudioEffectSetting {
                settingsRepository.saveReverbPreset(preset)
            }
        }
    }
    
    fun setCustomEqualizer(bandLevels: FloatArray) {
        playControl?.let { control ->
            control.setCustomEqualizer(bandLevels)
            _currentEqualizerBandLevels.value = bandLevels
            _audioEffectSettings.value = _audioEffectSettings.value.copy(
                customEqualizerLevels = bandLevels
            )
            saveAudioEffectSetting {
                settingsRepository.saveCustomEqualizerLevels(bandLevels)
            }
        }
    }
    
    private fun saveAudioEffectSetting(save: suspend () -> Unit) {
        saveAudioEffectJob?.cancel()
        saveAudioEffectJob = scope.launch {
            delay(500)
            try {
                save()
            } catch (e: Exception) {
                Log.e("MusicController", "Failed to save audio effect setting", e)
            }
        }
    }
    
    fun getCurrentEqualizerPreset(): Int {
        return playControl?.getCurrentEqualizerPreset() ?: 0
    }
    
    fun getBassBoostLevel(): Int {
        return playControl?.getBassBoostLevel() ?: 0
    }
    
    fun isSurroundSoundEnabled(): Boolean {
        return playControl?.isSurroundSoundEnabled() ?: false
    }
    
    fun getReverbPreset(): Int {
        return playControl?.getReverbPreset() ?: 0
    }
    
    fun getCurrentEqualizerBandLevels(): FloatArray {
        return playControl?.getCurrentEqualizerBandLevels() ?: floatArrayOf()
    }
    
    fun release() {
        timerJob?.cancel()
        timerJob = null
        endCurrentPlaybackSession(isCompleted = false)
        stopProgressTracking()
        unbindService()
        scope.launch {
            persistCurrentPlaylistToDatabase()
        }
    }
}
