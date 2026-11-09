package com.hearablemusic.player.ui.player.viewmodel

import androidx.compose.runtime.Stable
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.lyrics.LyricLineData
import com.hmp.domain.music.MusicInfo

/** 播放页电台入口卡的投影（原 `PlayerRadioViewModel` 的整屏状态，已并入屏级 VM）。 */
@Stable
data class PlayerRadioUiState(
    val radioState: RadioState? = null,
    val radioCard: RadioCardState = RadioCardState(theme = null, upcomingCount = 0, nextTitle = null, lastAdjust = null),
    val quotaExhausted: Boolean = false,
)

/**
 * 歌词的渲染快照（7.5 复审）。
 *
 * 解析（[com.hmp.domain.lyrics.LrcParser]）、逐字计时（[com.hmp.domain.lyrics.LyricsTimingGenerator]）
 * 与当前行查找都在 VM 内完成 —— UI 不再在组合期调用 domain 解析器，也不再用 `remember` 缓存派生结果。
 * `lineEnds` 与 [lines] 等长；`currentIndex` 由播放位置二分得到（-1 = 无行）。
 */
@Stable
data class LyricsPresentation(
    val lines: List<LyricLineData> = emptyList(),
    val lineEnds: List<Long> = emptyList(),
    val currentIndex: Int = -1,
)

@Stable
data class PlayerUiState(
    val musicInfo: MusicInfo? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val remainingTime: Long? = null,
    val isLiked: Boolean = false,
    val lyrics: LyricsPresentation? = null,
    val playlist: List<MusicInfo> = emptyList(),
    val currentIndex: Int = 0,
)
