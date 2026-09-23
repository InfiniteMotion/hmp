package com.hearablemusic.player.ui.platform

import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.setting.model.AudioEffectSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放控制接口（冻结版，方案 §5.3）。
 *
 * UI/presentation 只依赖此接口；Media3 / FFmpeg 实现类型不得外泄到 commonMain。
 * 成员面按全量调用点盘点后冻结，增补成员需同步两侧适配器。
 *
 * 实现落点：
 * - Android：androidMain MusicControllerPlaybackAdapter（适配 Media3）
 * - Desktop：desktopMain DesktopMusicControllerPlaybackAdapter（适配 FFmpeg）
 *
 * 调用点映射表：docs/7_x/A shared-ui/接口冻结-调用点映射表.md
 */
interface PlaybackController {

    // ── 播放状态流 ──

    /** 当前曲目（含封面/歌词等聚合信息），无曲目时为 null。 */
    val currentPlayingMusic: StateFlow<MusicInfo?>

    /** 是否正在播放。 */
    val isPlaying: StateFlow<Boolean>

    /** 当前播放位置（ms）。 */
    val currentPosition: StateFlow<Long>

    /** 当前曲目总时长（ms）。 */
    val duration: StateFlow<Long>

    /** 播放模式：顺序 / 随机 / 单曲循环。 */
    val playbackMode: StateFlow<PlaybackMode>

    /** 当前播放队列。 */
    val currentPlaylist: StateFlow<List<MusicInfo>>

    /** 当前曲目在队列中的下标。 */
    val currentIndex: StateFlow<Int>

    /** 当前曲目收藏状态（单曲维度）。 */
    val likeStatus: StateFlow<Boolean>

    /** 当前曲目标签列表（智能歌单/心动模式展示用）。 */
    val currentMusicLabels: StateFlow<List<MusicLabel?>>

    /** 当前曲目歌词原文（LRC 文本，由 UI 侧解析）。 */
    val currentMusicLyrics: StateFlow<String?>

    /** 定时关闭剩余时间（ms），未启用时为 null。 */
    val timerRemaining: StateFlow<Long?>

    /** MiniPlayer 显隐状态。 */
    val isMiniPlayerVisible: StateFlow<Boolean>

    // ── 音效状态流（AudioEffects 页） ──

    val audioEffectSettings: StateFlow<AudioEffectSettings>
    val equalizerPresets: StateFlow<List<String>>
    val equalizerBandCount: StateFlow<Int>
    val equalizerBandLevelRange: StateFlow<Pair<Int, Int>>
    val currentEqualizerBandLevels: StateFlow<FloatArray>

    // ── 播放控制 ──

    /** 列表项「点击播放」入口：播放指定曲目并接管队列上下文。 */
    suspend fun playWith(music: MusicInfo)

    fun playOrResume()
    fun pauseMusic()
    fun playNext()
    fun playPrevious()
    fun seekTo(positionMs: Long)

    /** 三态循环切换：顺序 → 随机 → 单曲循环 → 顺序。 */
    fun togglePlaybackModeByOrder()

    fun setMiniPlayerVisible(visible: Boolean)

    // ── 队列管理 ──

    /** 从当前下标处开始播放指定曲目（不重建队列）。 */
    fun playAt(music: MusicInfo)

    /** 追加到队列尾部。 */
    fun addToPlaylist(music: MusicInfo)

    /** 插入为下一首播放。 */
    fun addToNextPlay(music: MusicInfo)

    fun removeFromPlaylist(music: MusicInfo)
    fun moveToTop(music: MusicInfo)
    fun clearPlaylist()
    fun addAllToPlaylistInOrder(playlist: List<MusicInfo>)
    fun addAllToPlaylistByShuffle(playlist: List<MusicInfo>)

    /** 心动模式：以当前曲目为种子按标签权重生成并播放队列。 */
    fun playHeartMode()

    // ── 收藏 ──

    fun updateMusicLikedStatus(music: MusicInfo, liked: Boolean)
    fun getLikedStatus(musicId: Long)

    /** 挂起读取指定曲目的收藏状态（弹窗即时展示用）。 */
    suspend fun getCurrentLikedStatus(musicId: Long): Boolean

    // ── 曲目元数据 ──

    fun getMusicLabels(musicId: Long)
    fun getMusicLyrics(musicId: Long)

    // ── 定时关闭 ──

    fun startTimer(minutes: Int)
    fun cancelTimer()

    // ── 音效控制 ──

    fun initializeAudioEffects()
    fun setEqualizerPreset(preset: Int)
    fun setBassBoost(level: Int)
    fun setSurroundSound(enabled: Boolean)
    fun setReverb(preset: Int)
    fun setCustomEqualizer(bandLevels: FloatArray)

    // ── 进度追踪生命周期 ──
    // Android：驱动服务端进度轮询/持久化；Desktop：空实现。

    fun startProgressTracking()
    fun stopProgressTracking()
}
