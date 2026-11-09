package com.hearablemusic.player.ui.common.pages

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.LibraryScanUseCase
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首次引导整屏状态（7.5 P1 非 entry 屏补扫）。
 *
 * 原来 4 个 `remember { mutableXxxStateOf }` 分散在 `IntroScreen` 里，其中
 * 「权限授予 → 自动进入下一步」的推进逻辑还散在一个 `LaunchedEffect` 里；
 * 现在阶段推进全部落在 VM，UI 只上送回调。
 */
@Stable
data class IntroUiState(
    val step: Int = 0,
    val permissionGiven: Boolean = false,
    val showScanDialog: Boolean = false,
    val scanCompleted: Boolean = false,
    /** 扫描进度与曲库总量（仓库投影，进同一条出口）。 */
    val isScanning: Boolean = false,
    val musicCount: Int = 0,
    /** 扫描完成后是否需要自动触发 AI 批量补全（免费模式）。 */
    val aiAccessMode: AiAccessMode = AiAccessMode.FREE,
)

/** 引导页的跨屏动作：AI 补全在 RecommendationViewModel，由组合点转发（扫描已收进本 VM）。 */
sealed interface IntroEffect {
    data object StartAiAutoProcess : IntroEffect
}

class IntroViewModel(
    userSettingsUseCase: UserSettingsUseCase,
    private val scanUseCase: LibraryScanUseCase,
    getAllMusicUseCase: GetAllMusicUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntroUiState())
    val uiState: StateFlow<IntroUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<IntroEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<IntroEffect> = _effect.asSharedFlow()

    init {
        // 原来在 composable 里 koinInject<UserSettingsUseCase>() 直接读，现在经 VM。
        viewModelScope.launch {
            userSettingsUseCase.aiAccessMode.collect { mode ->
                _uiState.update { it.copy(aiAccessMode = mode) }
            }
        }
        // 扫描进度与总量是仓库投影；引导页只做渲染，扫描动作在本 VM 内发起。
        viewModelScope.launch {
            scanUseCase.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        viewModelScope.launch {
            getAllMusicUseCase.getMusicCount().collect { count ->
                _uiState.update { it.copy(musicCount = count) }
            }
        }
    }

    /**
     * 进入引导：桌面跳过权限步时直接从扫描步开始。
     *
     * 用 copy 而不是整份覆盖：`aiAccessMode` 由 init 里的异步 collect 写入，
     * 整体重建会把已拿到的值静默重置成 FREE，直接改变扫描完成后的 AI 补全行为。
     * 只重置本屏的步进态。
     */
    fun onStart(skipPermissionStep: Boolean) {
        _uiState.update {
            it.copy(
                step = if (skipPermissionStep) 1 else 0,
                permissionGiven = skipPermissionStep,
                showScanDialog = false,
                scanCompleted = false,
            )
        }
    }

    /** 权限结果：授予即自动进入下一步（原 `LaunchedEffect(isPermissionGiven)` 的职责）。 */
    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            if (granted && it.step == 0) {
                it.copy(permissionGiven = true, step = 1)
            } else {
                it.copy(permissionGiven = granted)
            }
        }
    }

    fun onSkipPermission() = _uiState.update { it.copy(step = 1) }

    /** 一次点击 = 一个动作：弹窗显隐与扫描本身都在 VM 内完成（7.5：不再经组合点转发）。 */
    fun onScanRequested() {
        _uiState.update { it.copy(showScanDialog = true) }
        viewModelScope.launch { scanUseCase.incremental() }
    }

    /** 扫描结束：推进步骤，并按 AI 接入模式决定是否自动触发批量补全（UI 不再读业务态分支）。 */
    fun onScanCompleted() {
        val autoProcess = _uiState.value.aiAccessMode == AiAccessMode.FREE
        _uiState.update {
            it.copy(showScanDialog = false, scanCompleted = true, step = 2)
        }
        if (autoProcess) {
            viewModelScope.launch { _effect.emit(IntroEffect.StartAiAutoProcess) }
        }
    }
}
