package com.hearablemusic.player.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.data.database.AgentAuditLog
import com.hmp.data.database.AgentAuditLogDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * M6-T4 审计日志页 ViewModel。
 * 从 AgentAuditLogDao 读取全部记录，支持按 tool（动作类型）筛选。
 */
class AuditLogViewModel(
    private val dao: AgentAuditLogDao,
) : ViewModel() {

    /** 筛选类别：全部 / 按 tool 名 */
    sealed class Filter {
        object All : Filter()
        data class ByTool(val tool: String) : Filter()
    }

    private val _filter = MutableStateFlow<Filter>(Filter.All)
    val filter: StateFlow<Filter> get() = _filter

    private val _logs = MutableStateFlow<List<AgentAuditLog>>(emptyList())
    val logs: StateFlow<List<AgentAuditLog>> get() = _logs

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _logs.value = when (val f = _filter.value) {
                is Filter.All -> runCatching { dao.getAll() }.getOrDefault(emptyList())
                is Filter.ByTool -> runCatching { dao.queryByTool(f.tool) }.getOrDefault(emptyList())
            }
        }
    }

    fun applyFilter(f: Filter) {
        _filter.value = f
        refresh()
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { dao.deleteAll() }
            refresh()
        }
    }

    fun deleteById(id: Long) {
        viewModelScope.launch {
            runCatching { dao.deleteById(id) }
            refresh()
        }
    }
}
