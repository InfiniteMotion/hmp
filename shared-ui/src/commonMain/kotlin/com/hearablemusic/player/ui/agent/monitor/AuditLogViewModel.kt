package com.hearablemusic.player.ui.agent.monitor

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.data.database.AgentAuditLog
import com.hmp.data.database.AgentAuditLogDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 审计日志页整屏状态（7.5 P1）：记录列表与当前筛选是同一屏的两面，收进单一出口。 */
@Stable
data class AuditLogUiState(
    val logs: List<AgentAuditLog> = emptyList(),
    val filter: AuditLogFilter = AuditLogFilter.All,
)

/** 筛选类别：全部 / 按 tool 名 */
sealed interface AuditLogFilter {
    data object All : AuditLogFilter

    data class ByTool(val tool: String) : AuditLogFilter
}

/**
 * M6-T4 审计日志页 ViewModel（7.5 P1）。
 *
 * 由三端 `viewModel {}` 注册、页面用 `koinViewModel()` 获取；此前页面在组合里
 * `remember { AuditLogViewModel(dao) }` 自行构造，既绕过 Koin 也拿不到正确的生命周期。
 */
class AuditLogViewModel(
    private val dao: AgentAuditLogDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onFilterSelected(filter: AuditLogFilter) {
        _uiState.update { it.copy(filter = filter) }
        refresh()
    }

    private var refreshJob: Job? = null

    private fun refresh() {
        // 快速切筛选：取消上一次查询，避免慢的旧结果后到覆盖新结果。
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val logs = when (val f = _uiState.value.filter) {
                is AuditLogFilter.All -> runCatching { dao.getAll() }.getOrDefault(emptyList())
                is AuditLogFilter.ByTool -> runCatching { dao.queryByTool(f.tool) }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(logs = logs) }
        }
    }
}
