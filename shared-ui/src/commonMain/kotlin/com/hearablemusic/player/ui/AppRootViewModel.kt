package com.hearablemusic.player.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.agent.shell.AgentNotice
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.default_playlist
import com.hearablemusic.player.ui.generated.resources.heart
import com.hearablemusic.player.ui.generated.resources.notice_off_taste
import com.hearablemusic.player.ui.generated.resources.notice_skipped
import com.hearablemusic.player.ui.generated.resources.recently_played
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.startup.DefaultPlaylistGuard
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.infra.PresenceEvent
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * 组合根整屏状态（7.5 P1 非 entry 屏补扫）。
 *
 * 只装「组合根自己拥有」的 UI 状态；播放态、主题态等跨屏投影仍按 §2 白名单留在各处。
 */
@Stable
data class AppRootUiState(
    val notice: AgentNotice? = null,
    /** 电台状态：底栏胶囊与电台控制台都用它（原来在 AppRoot 里 `masterAgent.radioState` 直读）。 */
    val radioState: RadioState? = null,
)

/**
 * 组合根 VM：PresenceBus 事件 → 侧条通知的投影。
 *
 * 此前 `currentNotice` / `noticeIdCounter` 用 `remember` 落在 `AppRoot` 的
 * `LaunchedEffect` 里，属 §四「PresenceBus 事件投影，宜进组合根 VM」。
 */
class AppRootViewModel(
    presenceBus: PresenceBus,
    private val masterAgent: MasterAgent,
    private val musicRepository: MusicRepository,
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val settingsRepository: SettingsRepository,
    private val playbackController: PlaybackController,
    private val lyricsSettingsUseCase: LyricsSettingsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppRootUiState())
    val uiState: StateFlow<AppRootUiState> = _uiState.asStateFlow()

    private var noticeIdCounter = 0L
    private var noticeDismissJob: Job? = null

    init {
        viewModelScope.launch {
            presenceBus.events.collectLatest { event ->
                if (event is PresenceEvent.SkipDetected) {
                    noticeIdCounter += 1
                    val id = noticeIdCounter
                    // 文本只带引用与参数，解析留给组合期（避免把语言冻结在事件发生那一刻）
                    val title: UiText = event.trackTitle?.let { UiText.Raw(it) }
                        ?: UiText.Res(Res.string.notice_off_taste)
                    _uiState.update {
                        it.copy(
                            notice = AgentNotice(
                                id = id,
                                message = UiText.Res(Res.string.notice_skipped, listOf(title)),
                                showUndo = false,
                            ),
                        )
                    }
                    // 4s 自动退场归 VM：组件只负责渲染，不再自己起计时协程
                    noticeDismissJob?.cancel()
                    noticeDismissJob = viewModelScope.launch {
                        delay(NOTICE_AUTO_DISMISS_MS)
                        _uiState.update { state ->
                            if (state.notice?.id == id) state.copy(notice = null) else state
                        }
                    }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            masterAgent.radioState.collect { state ->
                _uiState.update { it.copy(radioState = state) }
            }
        }
    }

    /**
     * 是否应当启动悬浮歌词（配置读取与异常处理在 VM）。
     * 真正的平台启动仍留在组合点：PlatformServices 在 Android 由 MainActivity 动态注册，
     * 注入 VM 会让静态 Koin 图校验看不到它。
     */
    suspend fun shouldStartFloatingLyrics(): Boolean = try {
        lyricsSettingsUseCase.floatingLyricsEnabled.first()
    } catch (e: Exception) {
        HmpLog.w(LogTag.UiCommon) { "读取悬浮歌词配置失败：${e.message}" }
        false
    }

    // ── 组合根自己的编排：电台会话、节目单跳曲、启动自愈 ──────────────

    /** 应用回到前台：把「无事件的状态变化」交给引擎重检（额度跨天重置等）。 */
    fun onAppForeground() {
        masterAgent.onAppForeground()
    }

    fun onEndRadioSession() {
        viewModelScope.launch { masterAgent.stopRadio() }
    }

    /** 控制台点节目单：按 id 查回曲目并直接入队定位播放（结果不回传 UI，§3）。 */
    fun onPlayRadioTrack(musicId: Long) {
        viewModelScope.launch {
            val info = runCatching { musicRepository.getMusicInfoByIds(listOf(musicId)) }
                .getOrNull()?.firstOrNull()
            if (info != null) {
                playbackController.playWith(info)
            } else {
                HmpLog.w(LogTag.UiCommon) { "电台节目单跳曲失败：musicId=$musicId 不在曲库" }
            }
        }
    }

    /** 启动不变量：系统歌单存在性校验 + 自愈（每次启动一次）。 */
    fun ensureDefaultPlaylists() {
        viewModelScope.launch {
            DefaultPlaylistGuard(managePlaylistUseCase, settingsRepository).ensureAll(
                currentName = getString(Res.string.default_playlist),
                likedName = getString(Res.string.heart),
                recentName = getString(Res.string.recently_played),
            )
        }
    }

    private companion object {
        const val NOTICE_AUTO_DISMISS_MS = 4000L
    }
}
