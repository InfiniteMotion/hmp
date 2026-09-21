package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.ListeningDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecommendationViewModel(
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    private val getAllMusicUseCase: GetAllMusicUseCase,
    /** MasterAgent（可选——没有 Agent 模块时 UI 降级） */
    private val masterAgent: MasterAgent? = null,
) : ViewModel() {

    // 待处理音乐数量
    val pendingMusicCount: StateFlow<Int> = getAllMusicUseCase
        .getMusicWithMissingExtraCount()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), 0)

    // 批量处理进度（桥接到 MasterAgent enrich status）
    data class BatchProcessingProgress(
        val totalCount: Int = 0,
        val processedCount: Int = 0,
        val success: Int = 0,
        val failed: Int = 0,
        val currentMusicTitle: String = "",
        val isProcessing: Boolean = false,
        val isPaused: Boolean = false
    ) {
        val progressPercent: Float
            get() = if (totalCount > 0) processedCount.toFloat() / totalCount else 0f
    }

    private val _processingProgress = MutableStateFlow(BatchProcessingProgress())
    val processingProgress: StateFlow<BatchProcessingProgress> = _processingProgress

    private val _isProcessingExtraInfo = MutableStateFlow(false)
    val isProcessingExtraInfo: StateFlow<Boolean> = _isProcessingExtraInfo

    // 收听时长
    val recentListeningDurations: StateFlow<List<ListeningDuration>> = getDailyRecommendationUseCase
        .getRecentListeningDurations()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())

    // ===== 富化生命周期桥接到 MasterAgent =====
    // 旧版 GetDailyMusicRecommendationUseCase 富化循环已删除，所有操作走 MasterAgent

    /**
     * 暂停富化（MasterAgent.enrichPause）
     */
    fun pauseProcessing() {
        viewModelScope.launch {
            masterAgent?.pauseEnrich()
            _processingProgress.value = _processingProgress.value.copy(isPaused = true)
        }
    }

    /**
     * 恢复富化（MasterAgent.enrichResume）
     */
    fun resumeProcessing() {
        viewModelScope.launch {
            masterAgent?.resumeEnrich()
            _processingProgress.value = _processingProgress.value.copy(isPaused = false)
        }
    }

    /**
     * 停止富化（MasterAgent.stopEnrich）
     */
    fun cancelProcessing() {
        viewModelScope.launch {
            masterAgent?.stopEnrich()
            _processingProgress.value = BatchProcessingProgress()
            _isProcessingExtraInfo.value = false
        }
    }

    /**
     * 启动富化（MasterAgent.startEnrich）
     * 旧版 getDailyRecommendationUseCase.autoProcessMissingExtraInfoWithCurrentProvider 已删除。
     */
    fun startAutoProcessWithCurrentProvider() {
        val agent = masterAgent ?: return
        if (_isProcessingExtraInfo.value) return

        viewModelScope.launch {
            _isProcessingExtraInfo.value = true
            agent.startEnrich(null) // 默认 targetCoverage=0.9f
            _processingProgress.value = _processingProgress.value.copy(
                isProcessing = true,
                isPaused = false,
            )
        }
    }

    init {
        _isProcessingExtraInfo.value = false
        _processingProgress.value = BatchProcessingProgress()
    }
}
