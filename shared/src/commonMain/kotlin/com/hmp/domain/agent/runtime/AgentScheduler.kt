package com.hmp.domain.agent.runtime

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.hmp.platform.Volatile
import com.hmp.platform.Synchronized

/**
 * Agent 优先级：数值越小优先级越高。
 * - 1: Master Agent（永不暂停，唯一用户入口）
 * - 2: Radio SubAgent（需 WiFi + 电量≥20%）
 * - 3: Enrich SubAgent（需 WiFi + 电量≥50% + 日配额剩10%）
 */
enum class AgentPriority(val value: Int) {
    MASTER(1),
    RADIO(2),
    ENRICH(3),
}

/** Agent 注册请求（Scheduler.registerAgent 的参数） */
data class AgentRegistration(
    val agentId: String,
    val priority: AgentPriority,
    /** 估算的每分钟 Token 消耗（供日配额仲裁参考） */
    val tokenUsagePerMin: Long = 2_000L,
    /** 满足条件时触发的 resume 回调（重新激活协程） */
    val onResume: suspend () -> Unit = {},
    /** 不满足条件时触发的 pause 回调（挂起协程，但保留状态） */
    val onPause: suspend () -> Unit = {},
)

/** Agent 当前运行状态 */
enum class AgentRunState { RUNNING, PAUSED, UNREGISTERED }

/**
 * T1 基础设施：全局唯一 Agent 调度仲裁器（设计铁则 F3）。
 *
 * 纯规则、零 LLM。每秒循环判断系统条件（电量/网络/Token 日配额），
 * 决定每个已注册 Agent 是否应该 pause/resume。
 *
 * 与 AgentContextBudget 的区别：
 * - AgentScheduler 管「Agent 能不能跑」（运行时仲裁）
 * - AgentContextBudget 管「Agent 的 LLM 窗口会不会爆」（上下文管理）
 *
 * 优先级触发条件：
 * | Priority | 条件 |
 * |----------|------|
 * | MASTER (1) | 永不暂停（硬编码兜底） |
 * | RADIO (2) | 电量≥20% 或 WiFi |
 * | ENRICH (3) | 电量≥50% 且 WiFi 且 日配额剩≥10% |
 */
class AgentScheduler(
    private val timeProvider: TimeProvider,
    private val tokenCounter: GlobalTokenCounter,
    /** 系统条件提供者（expect/actual 桥接：Android 传 BatteryManager/ConnectivityManager） */
    private val systemConditions: SystemConditions,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val agents = mutableMapOf<String, AgentRegistration>()
    private val states = mutableMapOf<String, AgentRunState>()

    /** Scheduler 仲裁循环是否已启动 */
    @Volatile
    private var arbitrationStarted: Boolean = false

    // ===== Agent 注册/注销 =====

    /** 注册 Agent；返回当前初始状态 */
    fun registerAgent(registration: AgentRegistration): AgentRunState {
        agents[registration.agentId] = registration
        val initialState = decideState(registration)
        states[registration.agentId] = initialState
        if (initialState == AgentRunState.RUNNING) {
            scope.launch { runCatching { registration.onResume() } }
        }
        Logger.i("Agent.Scheduler") { "[Scheduler] registered ${registration.agentId} priority=${registration.priority} initial=$initialState" }
        return initialState
    }

    /** 注销 Agent（应用销毁或 Master 下令销毁子 Agent） */
    fun unregisterAgent(agentId: String) {
        agents.remove(agentId)
        states.remove(agentId)
        Logger.i("Agent.Scheduler") { "[Scheduler] unregistered $agentId" }
    }

    /** 查询 Agent 当前状态 */
    fun getState(agentId: String): AgentRunState = states[agentId] ?: AgentRunState.UNREGISTERED

    /** 获取所有已注册 Agent ID */
    fun registeredAgentIds(): List<String> = agents.keys.toList()

    // ===== 仲裁循环 =====

    /** 启动全局仲裁循环（每秒一次；幂等） */
    @Synchronized
    fun startArbitration() {
        if (arbitrationStarted) return
        arbitrationStarted = true
        scope.launch { arbitrationLoop() }
        Logger.i("Agent.Scheduler") { "[Scheduler] arbitration loop started" }
    }

    /** 停止仲裁循环（应用销毁时调用） */
    fun stopArbitration() {
        arbitrationStarted = false
        // 依次 pause 所有 Agent
        agents.forEach { (id, reg) ->
            scope.launch { runCatching { reg.onPause() } }
            states[id] = AgentRunState.PAUSED
        }
        Logger.i("Agent.Scheduler") { "[Scheduler] arbitration loop stopped" }
    }

    private suspend fun arbitrationLoop() {
        while (arbitrationStarted && scope.isActive) {
            agents.entries.forEach { (agentId, registration) ->
                val newState = decideState(registration)
                val oldState = states[agentId]
                if (oldState != newState) {
                    states[agentId] = newState
                    when (newState) {
                        AgentRunState.RUNNING -> {
                            Logger.i("Agent.Scheduler") { "[Scheduler] $agentId RESUMED (priority=${registration.priority})" }
                            runCatching { registration.onResume() }
                        }
                        AgentRunState.PAUSED -> {
                            Logger.i("Agent.Scheduler") { "[Scheduler] $agentId PAUSED (priority=${registration.priority})" }
                            runCatching { registration.onPause() }
                        }
                        AgentRunState.UNREGISTERED -> Unit
                    }
                }
            }
            delay(1000L) // 每秒仲裁一次
        }
    }

    // ===== 状态判定（纯规则，零 LLM） =====

    private fun decideState(registration: AgentRegistration): AgentRunState {
        return when (registration.priority) {
            AgentPriority.MASTER -> AgentRunState.RUNNING // 永不暂停
            AgentPriority.RADIO -> decideRadioState()
            AgentPriority.ENRICH -> decideEnrichState()
        }
    }

    /** Radio (2): 电量≥20% 或 WiFi */
    private fun decideRadioState(): AgentRunState {
        val batteryOk = systemConditions.batteryLevel() >= 0.2f
        val wifiOk = systemConditions.isWifiConnected()
        return if (batteryOk || wifiOk) AgentRunState.RUNNING else AgentRunState.PAUSED
    }

    /** Enrich (3): 电量≥50% 且 WiFi 且 日配额剩≥10% */
    private fun decideEnrichState(): AgentRunState {
        val batteryOk = systemConditions.batteryLevel() >= 0.5f
        val wifiOk = systemConditions.isWifiConnected()
        val quotaOk = !tokenCounter.shouldStop(GlobalTokenCounter.SCHEDULER_PAUSE_THRESHOLD)
        return if (batteryOk && wifiOk && quotaOk) AgentRunState.RUNNING else AgentRunState.PAUSED
    }
}

/**
 * 系统条件提供者（expect/actual 桥接层）。
 * T1 先用接口占位，实际平台实现留给平台层 DI 注入。
 */
interface SystemConditions {
    /** 当前电量百分比（0.0 ~ 1.0），未知返回 1.0 让仲裁继续 */
    fun batteryLevel(): Float

    /** 是否 WiFi 连接，未知返回 true（乐观假设） */
    fun isWifiConnected(): Boolean
}
