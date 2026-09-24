package com.hmp.desktop.player

import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AudioEffectSettings
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
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
import kotlinx.coroutines.withTimeoutOrNull

class DesktopMusicController(
    private val audioEngine: AudioEngine,
    private val currentPlaybackUseCase: CurrentPlaybackUseCase,
    private val playbackHistoryUseCase: PlaybackHistoryUseCase,
    private val timerUseCase: TimerUseCase,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val settingsRepository: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        /** 播放进度采集间隔：保证逐字（卡拉 OK）渐变足够平滑 */
        const val PROGRESS_TRACKING_INTERVAL_MS = 100L
        /** 播放位置持久化节流间隔 */
        const val PROGRESS_PERSIST_INTERVAL_MS = 5_000L
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

    // Override for playWith() when the song isn't in the current playlist
    private val _currentPlayingMusicOverride = MutableStateFlow<MusicInfo?>(null)

    // Current Music
    val currentPlayingMusic: StateFlow<MusicInfo?> = combine(
        _currentPlayingMusicOverride,
        currentPlaylist,
        currentIndex
    ) { override, playlist, index ->
        override ?: playlist.getOrNull(index)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    // Like Status
    var likeStatus = MutableStateFlow(false)

    // Labels & Lyrics
    private val _currentMusicLabels = MutableStateFlow<List<MusicLabel?>>(emptyList())
    val currentMusicLabels: StateFlow<List<MusicLabel?>> = _currentMusicLabels

    private val _currentMusicLyrics = MutableStateFlow<String?>(null)
    val currentMusicLyrics: StateFlow<String?> = _currentMusicLyrics

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

    // Playback tracking
    private var currentPlaybackHistoryId: Long? = null
    private var totalPlayedDurationInSession: Long = 0L
    private val skipThresholdMs = 20000L
    private val skipThresholdPercent = 0.15f

    // Timer
    private var timerJob: Job? = null
    val timerRemaining: StateFlow<Long?> = timerUseCase.timerRemaining

    init {
        audioEngine.onPlaybackComplete = {
            onMusicComplete()
        }
        audioEngine.onError = { e ->
            showToast("Playback error: ${e.message}")
        }

        scope.launch {
            loadPlaylistFromSettings()
            restoreLastPosition()
        }

        // 注意：这里**不再**常驻收集 DB 队列回流。内存队列是播放的事实源，DB 只是
        // 持久化副本：运行中用 DB 快照覆写内存会把刚入队的曲目抹掉——而写库失败
        // （系统歌单行缺失导致 FK 必败）时回流永远带空快照，每次都清空队列，正是
        // 播放胶囊「闪一下消失」的根因。DB 快照只在启动恢复
        // （[loadPlaylistFromSettings]）时采纳一次。

        scope.launch {
            currentPlayingMusic
                .filterNotNull()
                .collectLatest { musicInfo ->
                    preloadCurrentMusicInfo(musicInfo)
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
        } catch (_: Exception) {}
    }

    private suspend fun loadPlaylistFromSettings() {
        try {
            val playlistId = currentPlayListId.filterNotNull().first()
            val currentMusicId = currentPlaybackUseCase.getCurrentMusicId().first()
            val list = managePlaylistUseCase.getMusicInfoInPlaylist(playlistId).first()
            _currentPlaylist.value = list
            _playbackMode.value = PlaybackMode.SEQUENTIAL
            _currentIndex.value = list.indexOfFirst { it.music.id == currentMusicId }.takeIf { it >= 0 } ?: 0
        } catch (_: Exception) {}
    }

    fun preloadCurrentMusicInfo(musicInfo: MusicInfo) {
        _duration.value = musicInfo.music.duration
        getLikedStatus(musicInfo.music.id)
        getMusicLabels(musicInfo.music.id)
        getMusicLyrics(musicInfo.music.id)
    }

    // region Playback Controls

    fun play() {
        val music = currentPlayingMusic.value ?: return
        if (audioEngine.isLoaded() && !audioEngine.isPlaying() && audioEngine.isPaused()) {
            audioEngine.resume()
            playStartTime = currentTimeMillis()
            lastDurationRecordTime = playStartTime
            startProgressTracking()
            _isPlaying.value = true
            return
        }
        playMusic(music)
    }

    fun pause() {
        if (playStartTime > 0) {
            val elapsed = currentTimeMillis() - playStartTime
            if (elapsed > 0) {
                scope.launch {
                    try {
                        playbackHistoryUseCase.recordListeningDuration(elapsed)
                    } catch (_: Exception) {}
                }
            }
        }
        audioEngine.pause()
        _isPlaying.value = false
        stopProgressTracking()
        persistCurrentPosition(_currentPosition.value)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun playMusic(musicInfo: MusicInfo) {
        scope.launch {
            endCurrentPlaybackSession(isCompleted = false)

            val playlist = _currentPlaylist.value
            val existingIndex = playlist.indexOfFirst { it.music.id == musicInfo.music.id }
            if (existingIndex >= 0) {
                _currentPlayingMusicOverride.value = null
                _currentIndex.value = existingIndex
            } else {
                _currentPlayingMusicOverride.value = musicInfo
            }

            audioEngine.stop()
            audioEngine.play(musicInfo.music.path)

            _isPlaying.value = true
            _duration.value = musicInfo.music.duration
            _currentPosition.value = 0L

            currentPlaybackUseCase.saveCurrentMusicId(musicInfo.music.id)

            playStartTime = currentTimeMillis()
            lastDurationRecordTime = playStartTime
            totalPlayedDurationInSession = 0L

            startProgressTracking()
            startNewPlaybackSession(musicInfo.music.id, "direct")
            addToRecent(musicInfo)
        }
    }

    fun playNext() {
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return

        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> _currentIndex.value
            PlaybackMode.SHUFFLE -> generateRandomIndex(_currentIndex.value, playlist.size)
            PlaybackMode.SEQUENTIAL -> {
                val next = _currentIndex.value + 1
                if (next >= playlist.size) 0 else next
            }
        }

        _currentIndex.value = nextIndex
        val music = playlist.getOrNull(nextIndex) ?: return
        playMusic(music)
    }

    fun playPrevious() {
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return

        val prevIndex = when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> _currentIndex.value
            PlaybackMode.SHUFFLE -> generateRandomIndex(_currentIndex.value, playlist.size)
            PlaybackMode.SEQUENTIAL -> {
                val prev = _currentIndex.value - 1
                if (prev < 0) playlist.size - 1 else prev
            }
        }

        _currentIndex.value = prevIndex
        val music = playlist.getOrNull(prevIndex) ?: return
        playMusic(music)
    }

    fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun setVolume(volume: Float) {
        audioEngine.setVolume(volume)
    }

    fun togglePlaybackMode() {
        _playbackMode.value = when (_playbackMode.value) {
            PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
        }
    }

    private fun generateRandomIndex(currentIdx: Int, size: Int): Int {
        if (size <= 1) return 0
        var randomIndex: Int
        do {
            randomIndex = kotlin.random.Random.nextInt(size)
        } while (randomIndex == currentIdx)
        return randomIndex
    }

    // endregion

    // region Progress Tracking

    fun startProgressTracking() {
        if (progressJob?.isActive == true) return

        progressJob = scope.launch {
            var lastPersistMs = 0L
            while (isActive) {
                val pos = audioEngine.getCurrentPosition()
                _currentPosition.value = pos
                // 持久化当前播放进度（节流，避免高频写盘）
                val now = System.currentTimeMillis()
                if (now - lastPersistMs >= PROGRESS_PERSIST_INTERVAL_MS) {
                    lastPersistMs = now
                    persistCurrentPosition(pos)
                }
                recordListeningDurationPeriodically()
                delay(PROGRESS_TRACKING_INTERVAL_MS)
            }
        }
    }

    fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun persistCurrentPosition(position: Long) {
        scope.launch {
            try {
                settingsRepository.saveCurrentPosition(position)
            } catch (_: Exception) {}
        }
    }

    private fun recordListeningDurationPeriodically() {
        val now = currentTimeMillis()
        if (playStartTime == 0L) {
            playStartTime = now
            lastDurationRecordTime = now
            return
        }
        if (now - lastDurationRecordTime >= durationRecordThreshold) {
            val elapsed = now - lastDurationRecordTime
            scope.launch {
                try {
                    playbackHistoryUseCase.recordListeningDuration(elapsed)
                } catch (_: Exception) {}
            }
            lastDurationRecordTime = now
        }
    }

    // endregion

    // region Playback History

    private fun startNewPlaybackSession(musicId: Long, source: String?) {
        scope.launch {
            try {
                val historyId = playbackHistoryUseCase.startPlaybackSession(musicId, source)
                currentPlaybackHistoryId = historyId
            } catch (_: Exception) {}
        }
    }

    private fun endCurrentPlaybackSession(isCompleted: Boolean) {
        val historyId = currentPlaybackHistoryId ?: return
        val music = currentPlayingMusic.value ?: return

        scope.launch {
            try {
                val currentPos = _currentPosition.value
                val duration = music.music.duration

                val isSkip = !isCompleted && (
                    currentPos < skipThresholdMs ||
                    (duration > 0 && currentPos < duration * skipThresholdPercent)
                )

                if (isCompleted) {
                    playbackHistoryUseCase.completePlaybackSession(historyId, music.music.id, currentPos)
                } else {
                    playbackHistoryUseCase.skipPlaybackSession(historyId, music.music.id, currentPos, isSkip)
                }

                currentPlaybackHistoryId = null
                totalPlayedDurationInSession = 0L
            } catch (_: Exception) {}
        }
    }

    // endregion

    // region Music Complete

    private fun onMusicComplete() {
        scope.launch {
            val music = currentPlayingMusic.value
            if (music != null) {
                endCurrentPlaybackSession(isCompleted = true)
            }

            when (_playbackMode.value) {
                PlaybackMode.REPEAT_ONE -> {
                    music?.let { playMusic(it) }
                }
                PlaybackMode.SHUFFLE, PlaybackMode.SEQUENTIAL -> {
                    playNext()
                }
            }
        }
    }

    // endregion

    // region Timer

    fun startTimer(durationMs: Long) {
        timerJob?.cancel()
        timerUseCase.setTimerRemaining(durationMs)
        timerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                timerUseCase.decrementTimer(1000)
            }
            pause()
            showToast("Timer ended")
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        timerUseCase.cancelTimer()
    }

    // endregion

    // region Playlist Management

    fun setPlaylist(list: List<MusicInfo>, startIndex: Int = 0) {
        _currentPlaylist.value = list
        _currentIndex.value = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
        val music = list.getOrNull(_currentIndex.value) ?: return
        playMusic(music)
    }

    /**
     * 把 [snapshot] 整条写回当前播放列表。
     *
     * 必须由调用方在**改完内存队列的那一刻**传入快照，不能在协程里惰性读
     * `_currentPlaylist.value`：协程真正运行时队列可能已被后续操作改过，
     * 那样写回的就不是「这次操作对应的队列状态」。
     *
     * 写失败不上抛（播放不因 DB 故障中断），但必须可见——外键失败多半意味着
     * 系统歌单行缺失，DefaultPlaylistGuard 的自愈事件应与此日志对照排查。
     */
    private fun persistCurrentPlaylistToDatabase(snapshot: List<MusicInfo>) {
        scope.launch {
            try {
                val playlistId = currentPlayListId.filterNotNull().first()
                managePlaylistUseCase.resetPlaylistItems(playlistId, snapshot)
            } catch (e: Exception) {
                HmpLog.w(LogTag.PlayerCore, e) {
                    "播放队列持久化失败 size=${snapshot.size}（目标歌单行缺失或 DB 异常）"
                }
            }
        }
    }

    private fun addToRecent(musicInfo: MusicInfo) {
        scope.launch {
            try {
                val recentId = withTimeoutOrNull(1000) {
                    recentPlayListId.firstOrNull()
                }
                if (recentId != null) {
                    managePlaylistUseCase.addToPlaylist(recentId, musicInfo.music.id, musicInfo.music.path)
                }
            } catch (_: Exception) {}
        }
    }

    fun addToPlaylist(musicInfo: MusicInfo) {
        if (_currentPlaylist.value.none { it.music.id == musicInfo.music.id }) {
            _currentPlaylist.value = _currentPlaylist.value + musicInfo
            persistCurrentPlaylistToDatabase(_currentPlaylist.value)
        }
    }

    fun removeFromPlaylist(musicInfo: MusicInfo) {
        _currentPlaylist.value = _currentPlaylist.value.filter { it.music.id != musicInfo.music.id }
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
        updateCurrentIndex()
    }

    fun addToNextPlay(musicInfo: MusicInfo) {
        val currentIdx = _currentIndex.value
        val newList = _currentPlaylist.value.toMutableList()

        val existingIndex = newList.indexOfFirst { it.music.id == musicInfo.music.id }
        if (existingIndex != -1) {
            newList.removeAt(existingIndex)
            if (existingIndex <= currentIdx) {
                _currentIndex.value = (currentIdx - 1).coerceAtLeast(0)
            }
        }

        val adjustedCurrentIndex = _currentIndex.value
        val insertIndex = if (newList.isEmpty()) {
            0
        } else {
            (adjustedCurrentIndex + 1).coerceAtMost(newList.size)
        }

        newList.add(insertIndex, musicInfo)
        _currentPlaylist.value = newList
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
    }

    fun clearPlaylist() {
        _currentPlaylist.value = emptyList()
        _currentIndex.value = 0
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
    }

    fun moveToTop(musicInfo: MusicInfo) {
        val playlist = _currentPlaylist.value.toMutableList()
        val index = playlist.indexOfFirst { it.music.id == musicInfo.music.id }
        if (index > 0) {
            val item = playlist.removeAt(index)
            playlist.add(0, item)
            _currentPlaylist.value = playlist
            persistCurrentPlaylistToDatabase(_currentPlaylist.value)
            updateCurrentIndex()
            showToast("已置顶：${musicInfo.music.title}")
        }
    }

    fun addAllToPlaylistByShuffle(musicInfoList: List<MusicInfo>) {
        _currentPlaylist.value = musicInfoList.shuffled()
        _playbackMode.value = PlaybackMode.SHUFFLE
        _currentIndex.value = 0
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
        val music = _currentPlaylist.value.firstOrNull() ?: return
        playMusic(music)
    }

    fun addAllToPlaylistInOrder(musicInfoList: List<MusicInfo>) {
        _currentPlaylist.value = musicInfoList
        _playbackMode.value = PlaybackMode.SEQUENTIAL
        _currentIndex.value = 0
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
        val music = musicInfoList.firstOrNull() ?: return
        playMusic(music)
    }

    fun playHeartMode() {
        scope.launch {
            val currentMusic = currentPlayingMusic.value
            if (currentMusic != null) {
                try {
                    val similarSongs = currentPlaybackUseCase.getSimilarSongsByWeightedLabels(
                        currentMusic.music.id,
                        limit = 10
                    )
                    if (similarSongs.isNotEmpty()) {
                        val newList = listOf(currentMusic) + similarSongs
                        _currentPlaylist.value = newList
                        _playbackMode.value = PlaybackMode.SEQUENTIAL
                        _currentIndex.value = 0
                        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
                        playMusic(currentMusic)
                        showToast("为你推荐${similarSongs.size}首心动歌曲")
                    } else {
                        showToast("未找到相似歌曲")
                    }
                } catch (_: Exception) {
                    showToast("心动模式暂不可用")
                }
            } else {
                val playlist = _currentPlaylist.value
                if (playlist.isNotEmpty()) {
                    _currentIndex.value = generateRandomIndex(_currentIndex.value, playlist.size)
                    playlist.getOrNull(_currentIndex.value)?.let { playMusic(it) }
                }
            }
        }
    }

    // endregion

    // region Now Playing Info

    fun getLikedStatus(musicId: Long) {
        scope.launch {
            try {
                likeStatus.value = currentPlaybackUseCase.getLikedStatus(musicId)
            } catch (_: Exception) {}
        }
    }

    fun getCurrentLikedStatus(musicId: Long? = null): Boolean = likeStatus.value

    fun updateMusicLikedStatus(musicInfo: MusicInfo, isLiked: Boolean) {
        scope.launch {
            try {
                currentPlaybackUseCase.updateLikedStatus(musicInfo.music.id, isLiked)
                val likedId = likedPlayListId.filterNotNull().first()
                if (isLiked) {
                    managePlaylistUseCase.addToPlaylist(likedId, musicInfo.music.id, musicInfo.music.path)
                } else {
                    managePlaylistUseCase.removeItemFromPlaylist(musicInfo.music.id, likedId)
                }
            } catch (_: Exception) {}
        }
        likeStatus.value = isLiked
    }

    fun updateMusicLikedStatus(musicId: Long, isLiked: Boolean) {
        scope.launch {
            try {
                currentPlaybackUseCase.updateLikedStatus(musicId, isLiked)
                val musicInfo = currentPlayingMusic.value
                if (musicInfo != null) {
                    val likedId = likedPlayListId.filterNotNull().first()
                    if (isLiked) {
                        managePlaylistUseCase.addToPlaylist(likedId, musicId, musicInfo.music.path)
                    } else {
                        managePlaylistUseCase.removeItemFromPlaylist(musicId, likedId)
                    }
                }
            } catch (_: Exception) {}
        }
        likeStatus.value = isLiked
    }

    fun getMusicLabels(musicId: Long) {
        scope.launch {
            try {
                _currentMusicLabels.value = currentPlaybackUseCase.getMusicLabels(musicId)
            } catch (_: Exception) {}
        }
    }

    fun getMusicLyrics(musicId: Long) {
        scope.launch {
            try {
                _currentMusicLyrics.value = currentPlaybackUseCase.getMusicLyrics(musicId)
            } catch (_: Exception) {}
        }
    }

    // endregion

    fun release() {
        stopProgressTracking()
        endCurrentPlaybackSession(isCompleted = false)
        persistCurrentPosition(_currentPosition.value)
        persistCurrentPlaylistToDatabase(_currentPlaylist.value)
        audioEngine.release()
    }

    // region Desktop-specific aliases

    fun playOrResume() = play()
    fun pauseMusic() = pause()
    fun togglePlaybackModeByOrder() = togglePlaybackMode()

    fun playAt(musicInfo: MusicInfo) {
        val playlist = _currentPlaylist.value
        val index = playlist.indexOfFirst { it.music.id == musicInfo.music.id }
        if (index >= 0) {
            _currentIndex.value = index
            playMusic(playlist[index])
        }
    }

    fun playWith(musicInfo: MusicInfo) {
        addToPlaylist(musicInfo)
        playMusic(musicInfo)
    }

    fun startTimer(minutes: Int) {
        startTimer(durationMs = minutes.toLong() * 60 * 1000)
    }

    // Audio effects stubs
    fun initializeAudioEffects() { /* TODO: implement */ }
    val audioEffectSettings: StateFlow<AudioEffectSettings> = MutableStateFlow(AudioEffectSettings())
    private val _equalizerBandCount = MutableStateFlow(0)
    val equalizerBandCount: StateFlow<Int> = _equalizerBandCount.asStateFlow()
    private val _equalizerBandLevelRange = MutableStateFlow(Pair(0, 0))
    val equalizerBandLevelRange: StateFlow<Pair<Int, Int>> = _equalizerBandLevelRange.asStateFlow()
    private val _equalizerPresets = MutableStateFlow<List<String>>(emptyList())
    val equalizerPresets: StateFlow<List<String>> = _equalizerPresets.asStateFlow()
    private val _currentEqualizerBandLevels = MutableStateFlow(FloatArray(0))
    val currentEqualizerBandLevels: StateFlow<FloatArray> = _currentEqualizerBandLevels.asStateFlow()
    fun getCurrentEqualizerPreset(): Int = 0
    fun setEqualizerPreset(preset: Int) { /* TODO: implement */ }
    fun setCustomEqualizer(bandLevels: FloatArray) { /* TODO: implement */ }
    fun getBassBoostLevel(): Int = 0
    fun setBassBoost(level: Int) { /* TODO: implement */ }
    fun getReverbPreset(): Int = 0
    fun setReverb(preset: Int) { /* TODO: implement */ }
    fun isSurroundSoundEnabled(): Boolean = false
    fun setSurroundSound(enabled: Boolean) { /* TODO: implement */ }

    // endregion
}
