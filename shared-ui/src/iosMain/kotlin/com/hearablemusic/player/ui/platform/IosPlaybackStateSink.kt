package com.hearablemusic.player.ui.platform

import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicInfo

/**
 * iOS 播放状态汇聚器（A4：Swift 引擎状态 → Kotlin StateFlow）。
 *
 * Swift 侧的 MusicPlayerController 是 @Observable 单例，Kotlin 无法直接观察；
 * 由 Swift 桥（PlaybackBridge.swift）用 Observation 把状态变更推送到本对象，
 * 再转发到 [IosPlaybackController] 的 StateFlow。
 *
 * 跨框架类型面：sharedUi framework 会把 shared 的 domain 类型（MusicInfo 等）以
 * SharedUi 前缀重导出，Swift 侧与 shared framework 的类型名成双份；为避免双名冲突，
 * 跨边界一律用 Any（底层为同一 ObjC 类，运行时强转安全）与 String/Int 等基础类型。
 * PlaybackMode 走 name（"SEQUENTIAL" 等）字符串，流光 `List<MusicInfo>` 由
 * [updatePlaylist] 做元素级强转。
 *
 * 所有方法须在主线程调用（Swift 侧 MainActor 保证）。
 */
class IosPlaybackStateSink internal constructor(
    internal val controller: IosPlaybackController,
) {
    fun updateCurrentMusic(music: Any?) {
        controller._currentPlayingMusic.value = music as? MusicInfo
    }

    fun updateIsPlaying(playing: Boolean) {
        controller._isPlaying.value = playing
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        controller._currentPosition.value = positionMs
        controller._duration.value = durationMs
    }

    fun updatePlaybackMode(mode: String) {
        controller._playbackMode.value = PlaybackMode.entries.firstOrNull { it.name == mode }
            ?: PlaybackMode.SEQUENTIAL
    }

    fun updatePlaylist(list: List<Any>) {
        controller._currentPlaylist.value = list.mapNotNull { it as? MusicInfo }
    }

    fun updateCurrentIndex(index: Int) {
        controller._currentIndex.value = index
    }

    fun updateLikedStatus(liked: Boolean) {
        controller._likeStatus.value = liked
    }

    fun updateLyrics(lyrics: String?) {
        controller._currentMusicLyrics.value = lyrics
    }

    fun updateTimerRemaining(ms: Long?) {
        controller._timerRemaining.value = ms
    }

    fun updateMiniPlayerVisible(visible: Boolean) {
        controller._isMiniPlayerVisible.value = visible
    }
}

/**
 * iOS 播放命令桥（A4：Kotlin PlaybackController → Swift 引擎）。
 *
 * Swift 侧注册闭包（闭包捕获 MusicPlayerController.shared 并调用其方法）；
 * Kotlin 侧在方法体内判空后调用。MusicInfo 以 Any 跨越框架边界（见上）。
 * 未注册（桥未接入）时静默跳过，UI 可先行验证。
 */
object IosPlaybackCommands {
    var playWith: ((Any) -> Unit)? = null
    var playOrResume: (() -> Unit)? = null
    var pauseMusic: (() -> Unit)? = null
    var playNext: (() -> Unit)? = null
    var playPrevious: (() -> Unit)? = null
    var seekTo: ((Long) -> Unit)? = null
    var togglePlaybackModeByOrder: (() -> Unit)? = null
    var setMiniPlayerVisible: ((Boolean) -> Unit)? = null
    var playAt: ((Any) -> Unit)? = null
    var addToPlaylist: ((Any) -> Unit)? = null
    var addToNextPlay: ((Any) -> Unit)? = null
    var removeFromPlaylist: ((Any) -> Unit)? = null
    var moveToTop: ((Any) -> Unit)? = null
    var clearPlaylist: (() -> Unit)? = null
    var addAllToPlaylistInOrder: ((List<Any>) -> Unit)? = null
    var addAllToPlaylistByShuffle: ((List<Any>) -> Unit)? = null
    var playHeartMode: (() -> Unit)? = null
    var updateMusicLikedStatus: ((Any, Boolean) -> Unit)? = null
    var startTimer: ((Int) -> Unit)? = null
    var cancelTimer: (() -> Unit)? = null
}
