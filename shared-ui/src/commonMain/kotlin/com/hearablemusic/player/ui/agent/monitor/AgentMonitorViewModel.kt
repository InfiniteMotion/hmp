package com.hearablemusic.player.ui.agent.monitor

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.data.database.TokenLedgerDao
import com.hmp.domain.agent.port.CapabilitySnapshot
import com.hmp.domain.agent.quota.QuotaBucket
import com.hmp.domain.agent.quota.TokenLedgerMaintenance
import com.hmp.domain.agent.quota.TokenLedgerSnapshot
import com.hmp.domain.agent.quota.TokenQuotaGate
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.TokenSnapshot
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.log.LogEntry
import com.hmp.memLogWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Agent 看板整屏状态（7.5 P1）。
 *
 * 账本快照与「清空」确认链都是这一屏的业务状态；此前两者分别用 `remember` 落在
 * `AgentMonitorScreen` 与 `TokenMonitorCard` 里，DB 维护对象也在组合内构造。
 * MasterAgent 的运行时状态、配额门状态与内存日志属于跨屏共享的数据流投影，
 * 按 UdfConventions §2 白名单仍留在 UI 订阅。
 */
@Stable
data class AgentMonitorUiState(
    val snapshot: TokenLedgerSnapshot? = null,
    val showClearConfirm: Boolean = false,
    // 7.5 P1 收尾：TokenMonitorCard 原先自己 koinInject masterAgent / quotaGate 读这四个投影
    val tokenUsage: TokenSnapshot = TokenSnapshot(0L, 0L),
    val quotaMode: AiAccessMode = AiAccessMode.FREE,
    val bucketUsage: Map<QuotaBucket, Long> = emptyMap(),
    val exhaustedBuckets: Set<QuotaBucket> = emptySet(),
    // 只读快照：持有 CapabilitySnapshot 而非 sub-agent 的活句柄，卡片不再订阅 cap.stateFlow
    val radioState: CapabilitySnapshot? = null,
    val enrichState: CapabilitySnapshot? = null,
    val helloState: CapabilitySnapshot? = null,
    /** 全局日志按 Agent 桶分组后的快照，UI 只读，不再各自订阅 memLogWriter。 */
    val logsByBucket: Map<String, List<LogEntry>> = emptyMap(),
)

class AgentMonitorViewModel(
    dao: TokenLedgerDao,
    private val masterAgent: MasterAgent,
    private val quotaGate: TokenQuotaGate,
) : ViewModel() {

    private val ledger = TokenLedgerMaintenance(dao)

    private val _uiState = MutableStateFlow(AgentMonitorUiState())
    val uiState: StateFlow<AgentMonitorUiState> = _uiState.asStateFlow()

    private var capabilityJobs: List<Job> = emptyList()

    init {
        reload()
        viewModelScope.launch {
            memLogWriter.logs.collect { logs ->
                _uiState.update { it.copy(logsByBucket = logs.groupBy { entry -> entry.agent }) }
            }
        }
        viewModelScope.launch {
            masterAgent.tokenUsage.collect { usage ->
                _uiState.update { it.copy(tokenUsage = usage) }
            }
        }
        viewModelScope.launch {
            combine(
                quotaGate.currentMode,
                quotaGate.bucketUsage,
                quotaGate.exhausted,
            ) { mode, usage, exhausted -> Triple(mode, usage, exhausted) }
                .collect { (mode, usage, exhausted) ->
                    _uiState.update {
                        it.copy(quotaMode = mode, bucketUsage = usage, exhaustedBuckets = exhausted)
                    }
                }
        }
    }

    /**
     * 取能力句柄。sub-agent 是懒创建的，所以不能在 VM init 里取（那时可能还没建），
     * 由 Screen 在 `LaunchedEffect(Unit)` 里调用，时机与原来的 `remember { capability() }` 一致。
     */
    fun refreshCapabilities() {
        // 重复调用（重进页面）时先停掉旧订阅，避免同一状态被多路写。
        capabilityJobs.forEach { it.cancel() }
        capabilityJobs = listOf("radio", "enrich", "hello").map { id ->
            viewModelScope.launch {
                val stateFlow = masterAgent.capabilityState(id) ?: return@launch
                stateFlow.collect { state ->
                    _uiState.update {
                        when (id) {
                            "radio" -> it.copy(radioState = state)
                            "enrich" -> it.copy(enrichState = state)
                            else -> it.copy(helloState = state)
                        }
                    }
                }
            }
        }
    }

    fun onReloadRequested() = reload()

    fun onClearRequested() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun onClearDismissed() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun onClearConfirmed() {
        _uiState.update { it.copy(showClearConfirm = false) }
        viewModelScope.launch {
            runCatching { ledger.clearAll() }
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            runCatching { ledger.snapshot() }
                .onSuccess { snapshot -> _uiState.update { it.copy(snapshot = snapshot) } }
        }
    }
}
