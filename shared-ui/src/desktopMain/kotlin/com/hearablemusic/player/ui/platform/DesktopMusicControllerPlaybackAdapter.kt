package com.hearablemusic.player.ui.platform

import com.hmp.desktop.player.DesktopMusicController
import com.hmp.domain.music.MusicInfo

/**
 * PlaybackController 的 Desktop 实现（方案 §5.3 / C12）。
 *
 * 薄委托层：将冻结接口转发给 FFmpeg 体系的 [DesktopMusicController]，
 * 与 Android 侧 MusicControllerPlaybackAdapter 同构。
 *
 * 签名差异处理：
 * - playWith/getCurrentLikedStatus：接口要求 suspend，DesktopMusicController 为普通函数，此处直接包装
 *   （suspend 调用非 suspend 合法，无额外挂起点）
 * - 音效族：DesktopMusicController 为 TODO stub（FFmpeg 无系统均衡器），1:1 转发保持占位
 */
class DesktopMusicControllerPlaybackAdapter(
    private val mc: DesktopMusicController,
) : PlaybackController {

    // ── 播放状态流 ──
    override val currentPlayingMusic get() = mc.currentPlayingMusic
    override val isPlaying get() = mc.isPlaying
    override val currentPosition get() = mc.currentPosition
    override val duration get() = mc.duration
    override val playbackMode get() = mc.playbackMode
    override val currentPlaylist get() = mc.currentPlaylist
    override val currentIndex get() = mc.currentIndex
    override val likeStatus get() = mc.likeStatus
    override val currentMusicLabels get() = mc.currentMusicLabels
    override val currentMusicLyrics get() = mc.currentMusicLyrics
    override val timerRemaining get() = mc.timerRemaining
    override val isMiniPlayerVisible get() = mc.isMiniPlayerVisible

    // ── 音效状态流（Desktop 为 stub 值） ──
    override val audioEffectSettings get() = mc.audioEffectSettings
    override val equalizerPresets get() = mc.equalizerPresets
    override val equalizerBandCount get() = mc.equalizerBandCount
    override val equalizerBandLevelRange get() = mc.equalizerBandLevelRange
    override val currentEqualizerBandLevels get() = mc.currentEqualizerBandLevels

    // ── 播放控制 ──
    override suspend fun playWith(music: MusicInfo) = mc.playWith(music)
    override fun playOrResume() = mc.playOrResume()
    override fun pauseMusic() = mc.pauseMusic()
    override fun playNext() = mc.playNext()
    override fun playPrevious() = mc.playPrevious()
    override fun seekTo(positionMs: Long) = mc.seekTo(positionMs)
    override fun togglePlaybackModeByOrder() = mc.togglePlaybackModeByOrder()
    override fun setMiniPlayerVisible(visible: Boolean) = mc.setMiniPlayerVisible(visible)

    // ── 队列管理 ──
    override fun playAt(music: MusicInfo) = mc.playAt(music)
    override fun addToPlaylist(music: MusicInfo) = mc.addToPlaylist(music)
    override fun addToNextPlay(music: MusicInfo) = mc.addToNextPlay(music)
    override fun removeFromPlaylist(music: MusicInfo) = mc.removeFromPlaylist(music)
    override fun moveToTop(music: MusicInfo) = mc.moveToTop(music)
    override fun clearPlaylist() = mc.clearPlaylist()
    override fun addAllToPlaylistInOrder(playlist: List<MusicInfo>) = mc.addAllToPlaylistInOrder(playlist)
    override fun addAllToPlaylistByShuffle(playlist: List<MusicInfo>) = mc.addAllToPlaylistByShuffle(playlist)
    override fun playHeartMode() = mc.playHeartMode()

    // ── 收藏 ──
    override fun updateMusicLikedStatus(music: MusicInfo, liked: Boolean) = mc.updateMusicLikedStatus(music, liked)
    override fun getLikedStatus(musicId: Long) = mc.getLikedStatus(musicId)
    override suspend fun getCurrentLikedStatus(musicId: Long): Boolean = mc.getCurrentLikedStatus(musicId)

    // ── 曲目元数据 ──
    override fun getMusicLabels(musicId: Long) = mc.getMusicLabels(musicId)
    override fun getMusicLyrics(musicId: Long) = mc.getMusicLyrics(musicId)

    // ── 定时关闭 ──
    override fun startTimer(minutes: Int) = mc.startTimer(minutes)
    override fun cancelTimer() = mc.cancelTimer()

    // ── 音效控制（Desktop stub） ──
    override fun initializeAudioEffects() = mc.initializeAudioEffects()
    override fun setEqualizerPreset(preset: Int) = mc.setEqualizerPreset(preset)
    override fun setBassBoost(level: Int) = mc.setBassBoost(level)
    override fun setSurroundSound(enabled: Boolean) = mc.setSurroundSound(enabled)
    override fun setReverb(preset: Int) = mc.setReverb(preset)
    override fun setCustomEqualizer(bandLevels: FloatArray) = mc.setCustomEqualizer(bandLevels)

    // ── 进度追踪生命周期 ──
    override fun startProgressTracking() = mc.startProgressTracking()
    override fun stopProgressTracking() = mc.stopProgressTracking()
}
