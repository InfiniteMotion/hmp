package com.hearablemusic.player.ui.platform

import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.setting.model.AudioEffectSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PlaybackController 的 iOS 实现（A4：桥接包装 Swift 播放引擎）。
 *
 * 采用「状态汇聚 + 命令转发」双桥模式（与 Android/Desktop 的单一委托对象不同，
 * 因 Swift 引擎位于 ObjC 互操作边界外）：
 * - 状态（StateFlow 只读侧）：Swift MusicPlayerController 经 [IosPlaybackStateSink] 推送
 * - 命令（方法侧）：转发到 [IosPlaybackCommands] 中 Swift 注册的闭包
 *
 * 单例（object）：Swift 桥需要访问同一实例的 [stateSink] 推送状态；
 * iosUiModule 中 single<PlaybackController> 提供同一引用。
 *
 * 音效族与 Desktop 一致为 stub（iOS 无系统均衡器；Swift 侧 AudioEffectsScreen 为模拟数据）。
 */
object IosPlaybackController : PlaybackController {

    internal val _currentPlayingMusic = MutableStateFlow<MusicInfo?>(null)
    internal val _isPlaying = MutableStateFlow(false)
    internal val _currentPosition = MutableStateFlow(0L)
    internal val _duration = MutableStateFlow(0L)
    internal val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    internal val _currentPlaylist = MutableStateFlow<List<MusicInfo>>(emptyList())
    internal val _currentIndex = MutableStateFlow(-1)
    internal val _likeStatus = MutableStateFlow(false)
    internal val _currentMusicLabels = MutableStateFlow<List<MusicLabel?>>(emptyList())
    internal val _currentMusicLyrics = MutableStateFlow<String?>(null)
    internal val _timerRemaining = MutableStateFlow<Long?>(null)
    internal val _isMiniPlayerVisible = MutableStateFlow(false)
    internal val _audioEffectSettings = MutableStateFlow(AudioEffectSettings())
    internal val _equalizerPresets = MutableStateFlow<List<String>>(emptyList())
    internal val _equalizerBandCount = MutableStateFlow(0)
    internal val _equalizerBandLevelRange = MutableStateFlow(0 to 0)
    internal val _currentEqualizerBandLevels = MutableStateFlow(FloatArray(0))

    /** 供 Swift 桥获取状态汇聚器（推送入口）。 */
    val stateSink: IosPlaybackStateSink by lazy { IosPlaybackStateSink(this) }

    // ── 播放状态流 ──

    override val currentPlayingMusic: StateFlow<MusicInfo?> get() = _currentPlayingMusic
    override val isPlaying: StateFlow<Boolean> get() = _isPlaying
    override val currentPosition: StateFlow<Long> get() = _currentPosition
    override val duration: StateFlow<Long> get() = _duration
    override val playbackMode: StateFlow<PlaybackMode> get() = _playbackMode
    override val currentPlaylist: StateFlow<List<MusicInfo>> get() = _currentPlaylist
    override val currentIndex: StateFlow<Int> get() = _currentIndex
    override val likeStatus: StateFlow<Boolean> get() = _likeStatus
    override val currentMusicLabels: StateFlow<List<MusicLabel?>> get() = _currentMusicLabels
    override val currentMusicLyrics: StateFlow<String?> get() = _currentMusicLyrics
    override val timerRemaining: StateFlow<Long?> get() = _timerRemaining
    override val isMiniPlayerVisible: StateFlow<Boolean> get() = _isMiniPlayerVisible

    // ── 音效状态流（iOS stub） ──

    override val audioEffectSettings: StateFlow<AudioEffectSettings> get() = _audioEffectSettings
    override val equalizerPresets: StateFlow<List<String>> get() = _equalizerPresets
    override val equalizerBandCount: StateFlow<Int> get() = _equalizerBandCount
    override val equalizerBandLevelRange: StateFlow<Pair<Int, Int>> get() = _equalizerBandLevelRange
    override val currentEqualizerBandLevels: StateFlow<FloatArray> get() = _currentEqualizerBandLevels

    // ── 播放控制 ──

    override suspend fun playWith(music: MusicInfo) {
        IosPlaybackCommands.playWith?.invoke(music)
    }

    override fun playOrResume() {
        IosPlaybackCommands.playOrResume?.invoke()
    }
    override fun pauseMusic() {
        IosPlaybackCommands.pauseMusic?.invoke()
    }
    override fun playNext() {
        IosPlaybackCommands.playNext?.invoke()
    }
    override fun playPrevious() {
        IosPlaybackCommands.playPrevious?.invoke()
    }
    override fun seekTo(positionMs: Long) {
        IosPlaybackCommands.seekTo?.invoke(positionMs)
    }
    override fun togglePlaybackModeByOrder() {
        IosPlaybackCommands.togglePlaybackModeByOrder?.invoke()
    }
    override fun setMiniPlayerVisible(visible: Boolean) {
        IosPlaybackCommands.setMiniPlayerVisible?.invoke(visible)
    }

    // ── 队列管理 ──

    override fun playAt(music: MusicInfo) {
        IosPlaybackCommands.playAt?.invoke(music)
    }
    override fun addToPlaylist(music: MusicInfo) {
        IosPlaybackCommands.addToPlaylist?.invoke(music)
    }
    override fun addToNextPlay(music: MusicInfo) {
        IosPlaybackCommands.addToNextPlay?.invoke(music)
    }
    override fun removeFromPlaylist(music: MusicInfo) {
        IosPlaybackCommands.removeFromPlaylist?.invoke(music)
    }
    override fun moveToTop(music: MusicInfo) {
        IosPlaybackCommands.moveToTop?.invoke(music)
    }
    override fun clearPlaylist() {
        IosPlaybackCommands.clearPlaylist?.invoke()
    }
    override fun addAllToPlaylistInOrder(playlist: List<MusicInfo>) {
        IosPlaybackCommands.addAllToPlaylistInOrder?.invoke(playlist)
    }
    override fun addAllToPlaylistByShuffle(playlist: List<MusicInfo>) {
        IosPlaybackCommands.addAllToPlaylistByShuffle?.invoke(playlist)
    }
    override fun playHeartMode() {
        IosPlaybackCommands.playHeartMode?.invoke()
    }

    // ── 收藏 ──

    override fun updateMusicLikedStatus(music: MusicInfo, liked: Boolean) {
        IosPlaybackCommands.updateMusicLikedStatus?.invoke(music, liked)
    }

    override fun getLikedStatus(musicId: Long) {
        // 收藏状态经 Swift 桥推送（loadMetadata 中查询），此处无需轮询
    }

    override suspend fun getCurrentLikedStatus(musicId: Long): Boolean = _likeStatus.value

    // ── 曲目元数据 ──

    override fun getMusicLabels(musicId: Long) {
        // 智能歌单标签：iOS 侧引擎不提供，保持空列表
    }

    override fun getMusicLyrics(musicId: Long) {
        // 歌词经 Swift 桥推送（loadMetadata 中查询）
    }

    // ── 定时关闭 ──

    override fun startTimer(minutes: Int) {
        IosPlaybackCommands.startTimer?.invoke(minutes)
    }
    override fun cancelTimer() {
        IosPlaybackCommands.cancelTimer?.invoke()
    }

    // ── 音效控制（iOS stub，与 Desktop 同构） ──

    override fun initializeAudioEffects() {}
    override fun setEqualizerPreset(preset: Int) {}
    override fun setBassBoost(level: Int) {}
    override fun setSurroundSound(enabled: Boolean) {}
    override fun setReverb(preset: Int) {}
    override fun setCustomEqualizer(bandLevels: FloatArray) {}

    // ── 进度追踪生命周期（iOS 由 Swift 引擎侧驱动持久化，空实现） ──

    override fun startProgressTracking() {}
    override fun stopProgressTracking() {}
}
