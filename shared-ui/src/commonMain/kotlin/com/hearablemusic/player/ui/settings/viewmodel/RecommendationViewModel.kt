package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.ListeningDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 收听时长投影 + 富化启动入口（7.5 复审后只剩实际消费面）。
 *
 * 原 `pendingMusicCount` / `processingProgress` / `isProcessingExtraInfo` 与
 * `pauseProcessing` / `resumeProcessing` / `cancelProcessing` 全仓零消费
 * （B9 之后进度展示已归 AgentMonitor / UserUsageData），随复审删除；
 * `GetAllMusicUseCase` 也只服务那条死流，一并从依赖里去掉。
 */
class RecommendationViewModel(
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    /** MasterAgent（可选 —— 没有 Agent 模块时 UI 降级） */
    private val masterAgent: MasterAgent? = null,
) : ViewModel() {

    /** 近 7 日收听时长（用户页折线）。 */
    val recentListeningDurations: StateFlow<List<ListeningDuration>> = getDailyRecommendationUseCase
        .getRecentListeningDurations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 防重入：同一实例只派发一次（与旧 `isProcessingExtraInfo` 守卫同义）。 */
    private var autoProcessStarted = false

    /** 启动富化（引导页「AI 自动补全」入口）。 */
    fun startAutoProcessWithCurrentProvider() {
        val agent = masterAgent ?: return
        if (autoProcessStarted) return
        autoProcessStarted = true
        viewModelScope.launch { agent.startEnrich(null) }
    }
}
