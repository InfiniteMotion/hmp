package com.hearablemusic.player.ui.library.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.search_failed
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.funnel.CommandLexicon
import com.hmp.domain.agent.funnel.FunnelResult
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.usecase.SearchMusicUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** 搜索页整屏状态（7.5 P1）：输入框、伙伴条带偏好与异步结果收进单一出口。 */
@Stable
data class SearchUiState(
    val query: String = "",
    val showIntentStrip: Boolean = true,
    /** 当前 query 是否命中「交给伙伴」意图（原在组合里每次重组重算）。 */
    val isIntent: Boolean = false,
    val results: LoadState<List<MusicInfo>> = LoadState.Idle,
)

/** 一次性效果：交给伙伴由组合点消费（VM 不持有导航与 ChatEntryBroker）。 */
sealed interface SearchEffect {
    data class TalkToCompanion(val query: String) : SearchEffect
}

/**
 * 搜索的防抖与竞态收口（D2-18）。
 *
 * 修前一次按键有两个调用点（`SearchScreen` 的 `LaunchedEffect` 与 `onSearchQueryChange`）、
 * 两个并发 `launch`、无取消无序号校验 —— 谁后写完谁生效，输入快时列表会闪回上一次的结果，
 * 且每键跑两遍全表 LIKE。
 *
 * 现在一次按键只留一个待执行 job，新按键**取消**旧 job（"旧结果不覆盖新结果"靠的是取消，
 * 不是防抖碰运气），并在打仓库前等一个防抖窗口。7.5 P1 后按键只走 [onQueryChange]，
 * UI 不再用 `LaunchedEffect(query)` 二次触发。
 */
class SearchViewModel(
    private val searchMusicUseCase: SearchMusicUseCase,
    private val playbackController: PlaybackController,
    private val debounceMillis: Long = DEBOUNCE_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SearchEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<SearchEffect> = _effect.asSharedFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        val intent = query.isNotEmpty() && CommandLexicon.classify(query) is FunnelResult.Upgrade
        _uiState.update { it.copy(query = query, isIntent = intent) }
        searchMusic(query)
    }

    fun onRetry() = searchMusic(_uiState.value.query)

    /** 播放：suspend 调用归 VM（不再借组合 scope，离屏即取消）。 */
    fun onPlay(musicInfo: MusicInfo) {
        viewModelScope.launch { playbackController.playWith(musicInfo) }
    }

    /** 「只是搜索」：本次会话不再弹伙伴条带。 */
    fun onDismissIntentStrip() {
        _uiState.update { it.copy(showIntentStrip = false) }
    }

    fun onTalkToCompanion(query: String) {
        _effect.tryEmit(SearchEffect.TalkToCompanion(query))
    }

    private fun searchMusic(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = LoadState.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMillis)
            _uiState.update { it.copy(results = LoadState.Loading) }
            try {
                val results = searchMusicUseCase(query)
                _uiState.update {
                    it.copy(
                        results = if (results.isEmpty()) LoadState.Empty else LoadState.Success(results),
                    )
                }
            } catch (e: CancellationException) {
                // 被更新的按键取消：这一拍的旧结果不落进 state，也不写 Error
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(results = LoadState.Error(e.message ?: getString(Res.string.search_failed)))
                }
            }
        }
    }

    companion object {
        /** 约 250ms：一次快速输入的几个按键合并成一次查询。 */
        const val DEBOUNCE_MS = 250L
    }
}
