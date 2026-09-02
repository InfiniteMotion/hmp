package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.platform.Volatile

/**
 * T1 基础设施：SubAgent 抽象基类。
 *
 * 设计铁则：
 * - F1：SubAgent 只有执行权，无决策权。生命周期全归 Master 管理。
 * - F2：每个 SubAgent 绑定独立 AgentContextBudget（独立 LLM 实例 + 独立窗口）。
 * - F3：暂停/恢复由 AgentScheduler 触发（priority-based 纯规则）。
 * - F6：SubAgent 不知道全局状态（电量/网络/富化进度），只做派发什么 → 执行什么。
 *
 * 暂停/恢复机制（v7.1 简化）：
 *   Scheduler 直接桥接到子类持有的 StopSignal（Mutex 挂起/唤醒），
 *   不再通过 SubAgent.suspendCoroutine/resumeCoroutine 中转。
 *   MasterAgent.pause/resume 也调子类暴露的 pause()/resume() 桥接 StopSignal。
 *
 * RadioSubAgent（M6 待实现）：
 * ```kotlin
 * class RadioSubAgent(...) : SubAgent() {
 *     override suspend fun pause() = stopSignal?.onSchedulerPaused()
 *     override suspend fun resume() = stopSignal?.onSchedulerResumed()
 *     override suspend fun runLoop() { ... }
 * }
 * ```
 */
abstract class SubAgent(
    /** 子 Agent 唯一标识（如 "enrich" / "radio"） */
    val agentId: String,
    /** 独立 LLM 上下文窗口 */
    protected val contextBudget: AgentContextBudget,
    /** 权限过滤后的工具视图（Enrich 只能看 library_* + song_* 等） */
    protected val toolRegistryView: ToolRegistryView,
) {
    @Volatile
    protected var isActive: Boolean = false

    /** 当前运行状态 */
    @Volatile
    protected var runState: AgentRunState = AgentRunState.PAUSED
        protected set

    // ===== 对外接口（只有 Master 能调；Scheduler 直接桥接 StopSignal） =====

    /** Master 下令销毁的唯一入口；子类可覆写做资源清理 */
    open suspend fun shutdown() {
        isActive = false
        contextBudget.releaseLlmClient()
        Logger.i("Agent.SubAgent") { "[$agentId] shutdown complete" }
    }

    /**
     * 暂停执行循环（Master LLM 通过 enrich_pause 工具 → MasterAgent.pauseEnrich() 调）。
     * 默认空操作——子类 override 桥接到 StopSignal。
     * 注意：Scheduler 触发的 pause 不经过这里，直接桥接 StopSignal.onSchedulerPaused()。
     */
    open suspend fun pause() {
        runState = AgentRunState.PAUSED
        Logger.i("Agent.SubAgent") { "[$agentId] pause() called (default impl — no stopSignal to bridge)" }
    }

    /**
     * 恢复执行循环（Master LLM 通过 enrich_resume 工具 → MasterAgent.resumeEnrich() 调）。
     * 默认空操作——子类 override 桥接到 StopSignal。
     */
    open suspend fun resume() {
        runState = AgentRunState.RUNNING
        Logger.i("Agent.SubAgent") { "[$agentId] resume() called (default impl — no stopSignal to bridge)" }
    }

    /** 当前运行状态（Master 查询用） */
    fun state(): AgentRunState = runState

    // ===== 子类实现 =====

    /** 子类必须实现的执行循环。 */
    abstract suspend fun runLoop()
}
