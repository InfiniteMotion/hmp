package com.hearablemusic.player.ui.player.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.agent.shell.RadioConsoleController
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.platform.togglePlayPause
import com.hmp.domain.agent.quota.QuotaBucket
import com.hmp.domain.agent.quota.TokenQuotaGate
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.lyrics.LrcParser
import com.hmp.domain.lyrics.LyricLineData
import com.hmp.domain.lyrics.LyricsComponent
import com.hmp.domain.lyrics.LyricsTimingGenerator
import com.hmp.domain.lyrics.findCurrentLyricIndex
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 播放页整屏状态（7.5 P1 · UI 侧 UDF 收口）。
 *
 * 此前 `PlayerScreen` 直接订阅 10 条播放/队列流 + 1 条歌词配置流，再在 composable 里
 * 拼出 `PlayerUiState` 与 `LyricsSettingsState` —— 屏级聚合发生在 UI。
 * 现在由本 VM 完成：注入 [PlaybackController]（平台门面）与 [LyricsSettingsUseCase]，
 * **不注入 PlaybackViewModel / PlaylistQueueViewModel**，避免 VM 转发 VM。
 */
@Stable
data class PlayerScreenUiState(
    val player: PlayerUiState = PlayerUiState(),
    val lyrics: LyricsSettingsState = LyricsSettingsState(),
    /** 电台入口卡状态：原 `PlayerRadioViewModel` 已并入本屏唯一出口。 */
    val radio: PlayerRadioUiState = PlayerRadioUiState(),
)

/**
 * 播放页的屏级 VM。
 *
 * ⚠️ 作用域是**应用级**（`activityViewModel()`），这是设计而非遗漏（2026-11-10 用户口径）：
 * 播放页与四个 Tab 一样常驻，退出播放页（出栈）**不销毁状态**，再次进入恢复原样。
 * 不要改成 `koinViewModel()`；`check-udf.py` 的 `APP_SCOPED_VMS` 已按此登记。
 */
class PlayerScreenViewModel(
    private val playbackController: PlaybackController,
    lyricsSettingsUseCase: LyricsSettingsUseCase,
    private val masterAgent: MasterAgent,
    quotaGate: TokenQuotaGate,
    private val radioConsole: RadioConsoleController,
) : ViewModel() {

    /** `combine` 最多 5 路，拆成三段再合并。 */
    private data class PlaybackPart(
        val isPlaying: Boolean,
        val position: Long,
        val duration: Long,
        val mode: PlaybackMode,
        val timerRemaining: Long?,
    )

    private data class QueuePart(
        val music: MusicInfo?,
        val liked: Boolean,
        val playlist: List<MusicInfo>,
        val index: Int,
    )

    private val playbackPart = combine(
        playbackController.isPlaying,
        playbackController.currentPosition,
        playbackController.duration,
        playbackController.playbackMode,
        playbackController.timerRemaining,
    ) { playing, position, duration, mode, timer ->
        PlaybackPart(playing, position, duration, mode, timer)
    }

    private val queuePart = combine(
        playbackController.currentPlayingMusic,
        playbackController.likeStatus,
        playbackController.currentPlaylist,
        playbackController.currentIndex,
    ) { music, liked, playlist, index ->
        QueuePart(music, liked, playlist, index)
    }

    /** 解析 + 计时只在换歌/时长就绪时重算；位置变化只重算当前行（二分）。 */
    private data class TimedLyrics(
        val parsed: List<LyricLineData>,
        val lines: List<LyricLineData>,
        val lineEnds: List<Long>,
    )

    private val lyricsPart: Flow<LyricsPresentation?> = combine(
        playbackController.currentMusicLyrics.map { text -> text?.let(LrcParser::parse) },
        playbackController.duration,
    ) { parsed, duration ->
        parsed?.let {
            val total = duration.takeIf { value -> value > 0 }
            val timed = LyricsTimingGenerator.resolve(it, total)
            TimedLyrics(
                parsed = it,
                lines = timed,
                lineEnds = timed.indices.map { index -> LyricsTimingGenerator.lineEndMs(timed, index, total) },
            )
        }
    }
        .distinctUntilChanged()
        .combine(playbackController.currentPosition) { timed, position ->
            timed?.let {
                LyricsPresentation(
                    lines = it.lines,
                    lineEnds = it.lineEnds,
                    currentIndex = findCurrentLyricIndex(it.parsed, position),
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val radioPart = combine(
        masterAgent.radioState,
        masterAgent.radioCardState,
        quotaGate.exhausted,
    ) { state, card, exhausted ->
        PlayerRadioUiState(
            radioState = state,
            radioCard = card,
            quotaExhausted = QuotaBucket.RADIO in exhausted,
        )
    }

    val uiState: StateFlow<PlayerScreenUiState> = combine(
        playbackPart,
        queuePart,
        lyricsSettingsUseCase.configFlow(LyricsComponent.PLAYER),
        radioPart,
        lyricsPart,
    ) { playback, queue, lyricsConfig, radio, lyrics ->
        PlayerScreenUiState(
            player = PlayerUiState(
                musicInfo = queue.music,
                isPlaying = playback.isPlaying,
                currentPosition = playback.position,
                duration = playback.duration,
                playbackMode = playback.mode,
                remainingTime = playback.timerRemaining,
                isLiked = queue.liked,
                lyrics = lyrics,
                playlist = queue.playlist,
                currentIndex = queue.index,
            ),
            lyrics = LyricsSettingsState(
                lyricsOriginalTextSize = lyricsConfig.originalTextSize,
                lyricsTranslatedTextSize = lyricsConfig.translatedTextSize,
                lyricsCurrentTimeTextSize = lyricsConfig.currentTimeTextSize,
                lyricsLineSpacing = lyricsConfig.lineSpacing,
                lyricsKaraokeEnabled = lyricsConfig.karaokeEnabled,
                lyricsDisplayMode = lyricsConfig.displayMode,
                lyricsAlignment = lyricsConfig.alignment,
            ),
            radio = radio,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PlayerScreenUiState(),
    )

    // ── 播放动作：原本 UI 借 PlaybackViewModel / PlaylistQueueViewModel 两个薄包装发命令，
    //    现在由屏级 VM 直接转发 controller，PlayerScreen 只依赖一个 VM 完成读写。 ──
    fun onSeek(position: Long) = playbackController.seekTo(position)

    fun onPlayPause(isPlaying: Boolean) = playbackController.togglePlayPause(isPlaying)

    fun onNext() = playbackController.playNext()

    fun onPrevious() = playbackController.playPrevious()

    fun onPlaybackModeChange() = playbackController.togglePlaybackModeByOrder()

    fun onFavorite(music: MusicInfo, liked: Boolean) = playbackController.updateMusicLikedStatus(music, liked)

    fun onClearPlaylist() = playbackController.clearPlaylist()

    fun onPlayAt(music: MusicInfo) = playbackController.playAt(music)

    fun onMoveToTop(music: MusicInfo) = playbackController.moveToTop(music)

    fun onRemoveFromPlaylist(music: MusicInfo) = playbackController.removeFromPlaylist(music)

    fun loadLikedStatus(id: Long) = playbackController.getLikedStatus(id)

    fun loadMusicLabels(id: Long) = playbackController.getMusicLabels(id)

    fun loadMusicLyrics(id: Long) = playbackController.getMusicLyrics(id)

    // ── 电台入口卡（原 PlayerRadioViewModel）──
    fun onStartRadio() {
        viewModelScope.launch { masterAgent.startRadio() }
    }

    fun onStopRadio() {
        viewModelScope.launch { masterAgent.stopRadio() }
    }

    fun onOpenConsole() = radioConsole.open()
}
