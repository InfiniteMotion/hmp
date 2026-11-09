package com.hearablemusic.player.ui.agent.shell

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.agent.runtime.sub.shared.RadioTrack
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 电台控制台整屏状态（7.5 P1 收尾：清参数注入）。
 *
 * `RadioConsole` 原来 `koinInject<MasterAgent>()` 后读三个只读流，
 * 现由 VM 聚合成单一出口；控制台本身不发动作（结束会话由调用方的 `onEndSession` 处理）。
 */
@Stable
data class RadioConsoleUiState(
    val radioState: RadioState?,
    val card: RadioCardState,
    val playlist: List<RadioTrack>,
)

class RadioConsoleViewModel(
    masterAgent: MasterAgent,
) : ViewModel() {

    val uiState: StateFlow<RadioConsoleUiState> = combine(
        masterAgent.radioState,
        masterAgent.radioCardState,
        masterAgent.radioPlaylist,
    ) { state, card, playlist ->
        RadioConsoleUiState(radioState = state, card = card, playlist = playlist)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RadioConsoleUiState(
            radioState = masterAgent.radioState.value,
            card = masterAgent.radioCardState.value,
            playlist = masterAgent.radioPlaylist.value,
        ),
    )
}
