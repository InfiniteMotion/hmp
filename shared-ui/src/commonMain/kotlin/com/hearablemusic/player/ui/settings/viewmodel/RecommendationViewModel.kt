package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecommendationViewModel(
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val userSettingsUseCase: UserSettingsUseCase,
    private val currentPlaybackUseCase: CurrentPlaybackUseCase,
    /** MasterAgent（可选——没有 Agent 模块时 UI 降级） */
    private val masterAgent: MasterAgent? = null,
) : ViewModel() {

    // 每日推荐歌曲
    val dailyMusic = MutableStateFlow<MusicInfo?>(null)
    private val _dailyMusicInfo = MutableStateFlow<DailyMusicInfo?>(null)
    val dailyMusicInfo: StateFlow<DailyMusicInfo?> = _dailyMusicInfo
    private val _dailyMusicLabel = MutableStateFlow<List<MusicLabel?>>(emptyList())
    val dailyMusicLabel: StateFlow<List<MusicLabel?>> = _dailyMusicLabel

    // 心动歌单（相似歌曲）
    private val _heartbeatList = MutableStateFlow<List<MusicInfo>>(emptyList())
    val heartbeatList: StateFlow<List<MusicInfo>> = _heartbeatList

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

    /**
     * 获取每日推荐音乐
     */
    fun getDailyMusicInfo() {
        viewModelScope.launch {
            // 先增加启动计数
            userSettingsUseCase.incrementAppLaunchCount()

            // 检查是否需要刷新
            val shouldRefresh = userSettingsUseCase.shouldRefreshDailyRecommendation()

            if (shouldRefresh) {
                refreshDailyMusicInfo()
            } else {
                if (dailyMusic.value == null) {
                    val savedMusicId = userSettingsUseCase.getCurrentDailyMusicId()
                    if (savedMusicId != null && savedMusicId > 0) {
                        val recommendation = getDailyRecommendationUseCase.getMusicWithExtraById(savedMusicId)
                        if (recommendation?.musicInfo != null) {
                            dailyMusic.value = recommendation.musicInfo
                            _dailyMusicInfo.value = recommendation.dailyMusicInfo
                            _dailyMusicLabel.value = recommendation.labels
                        } else {
                            refreshDailyMusicInfo()
                        }
                    } else {
                        refreshDailyMusicInfo()
                    }
                }
            }
        }
    }

    /**
     * 手动刷新每日推荐
     */
    fun refreshDailyMusicInfo() {
        viewModelScope.launch {
            val recommendation = getDailyRecommendationUseCase.getRandomMusicWithExtra()
            dailyMusic.value = recommendation.musicInfo
            _dailyMusicInfo.value = recommendation.dailyMusicInfo
            _dailyMusicLabel.value = recommendation.labels

            recommendation.musicInfo?.music?.id?.let { musicId ->
                userSettingsUseCase.saveCurrentDailyMusicId(musicId)
            }
            userSettingsUseCase.updateLastDailyRefreshTimestamp()
        }
    }

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
     * 清除处理结果（UI-only）
     */
    fun clearProcessingResult() {
        // 旧版 _processingResult 已删除
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

        // 监听每日推荐变化，自动获取相似歌曲
        viewModelScope.launch {
            dailyMusic.filterNotNull().collectLatest { music ->
                _heartbeatList.value = listOf(music) +
                        currentPlaybackUseCase.getSimilarSongsByWeightedLabels(music.music.id, 10)
            }
        }
    }
}
