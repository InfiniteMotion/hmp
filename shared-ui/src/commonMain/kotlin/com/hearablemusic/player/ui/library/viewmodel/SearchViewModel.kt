package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.search_failed
import org.jetbrains.compose.resources.getString
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.usecase.SearchMusicUseCase
import com.hearablemusic.player.ui.common.util.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 搜索的防抖与竞态收口（D2-18）。
 *
 * 修前一次按键有两个调用点（`SearchScreen` 的 `LaunchedEffect` 与 `onSearchQueryChange`）、
 * 两个并发 `launch`、无取消无序号校验 —— 谁后写完谁生效，输入快时列表会闪回上一次的结果，
 * 且每键跑两遍全表 LIKE。
 *
 * 现在一次按键只留一个待执行 job，新按键**取消**旧 job（"旧结果不覆盖新结果"靠的是取消，
 * 不是防抖碰运气），并在打仓库前等一个防抖窗口。
 */
class SearchViewModel(
    private val searchMusicUseCase: SearchMusicUseCase,
    private val debounceMillis: Long = DEBOUNCE_MS,
) : ViewModel() {

    private val _searchState = MutableStateFlow<UiState<List<MusicInfo>>>(UiState.Idle)
    val searchState: StateFlow<UiState<List<MusicInfo>>> = _searchState

    private var searchJob: Job? = null

    fun searchMusic(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchState.value = UiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMillis)
            _searchState.value = UiState.Loading
            try {
                val results = searchMusicUseCase(query)
                _searchState.value = if (results.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(results)
                }
            } catch (e: CancellationException) {
                // 被更新的按键取消：这一拍的旧结果不落进 state，也不写 Error
                throw e
            } catch (e: Exception) {
                _searchState.value = UiState.Error(e.message ?: getString(Res.string.search_failed))
            }
        }
    }

    companion object {
        /** 约 250ms：一次快速输入的几个按键合并成一次查询。 */
        const val DEBOUNCE_MS = 250L
    }
}
