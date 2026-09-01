package com.hmp.domain.agent.runtime

import kotlinx.coroutines.sync.Mutex

/**
 * Agent 停止/暂停信号——每个 Agent 构造时选一个实现。
 *
 * 两个实现：
 * - [AlwaysRunningStopSignal] MasterAgent 用：永不暂停
 * - [SchedulerStopSignal] Enrich/Radio 用：真正挂起等 Scheduler resume
 */
interface StopSignal {
    /** 软停止：跑完当前 step 就退出循环（token 配额耗尽 / 外部 shutdown） */
    fun shouldSoftStop(): Boolean

    /** 挂起等待（Scheduler pause 时挂起，resume 时立即恢复）——用 Mutex 实现零唤醒 */
    suspend fun waitResume()
}

/** MasterAgent 用——永不暂停，只查 token/step 熔断（ReActLoop 内部也会查 stepBudget） */
class AlwaysRunningStopSignal(
    private val tokenCounter: GlobalTokenCounter? = null,
) : StopSignal {
    override fun shouldSoftStop(): Boolean = tokenCounter?.shouldStop() ?: false
    override suspend fun waitResume() {}  // Master 永不暂停，空实现
}

/**
 * EnrichSubAgent / RadioSubAgent 用——真正挂起。
 *
 * 替换旧实现里的 while(PAUSED) { delay(500) } 轮询：
 * - 用 Mutex 实现真正挂起 → 零 CPU 唤醒 / 零延迟恢复
 * - 配合 AgentScheduler 的 onResume/onPause 回调：
 *   Scheduler 把状态改成 PAUSED 时调 onPause → 锁住门闩，Agent 下一个 waitResume() 挂起
 *   Scheduler 把状态改成 RUNNING 时调 onResume → 释放门闩，挂起的 Agent 立即恢复
 *
 * 门闩不变式：pauseRequested=true 时 pauseMutex 必为 locked（由 [onSchedulerPaused] 持有）；
 * Agent 的 waitResume 通过 lock()/unlock() 配对穿越门闩，**不持有锁跨批次**——
 * 旧版 tryLock 成功后不释放，Agent 第二个批次就自锁在自己持有的锁上（已修复）。
 *
 * 使用方式：
 *   val stopSignal = SchedulerStopSignal(tokenCounter)
 *   scheduler.registerAgent(AgentRegistration(
 *       agentId = "enrich",
 *       priority = ENRICH,
 *       onPause = { stopSignal.onSchedulerPaused() },
 *       onResume = { stopSignal.onSchedulerResumed() },
 *   ))
 */
class SchedulerStopSignal(
    private val tokenCounter: GlobalTokenCounter? = null,
) : StopSignal {

    /** 门闩：pauseRequested=true 时由 onSchedulerPaused 持锁 */
    private val pauseMutex = Mutex(locked = false)

    /** 是否已请求暂停（@Volatile 保证 waitResume 快速路径的可见性） */
    @Volatile
    private var pauseRequested: Boolean = false

    override fun shouldSoftStop(): Boolean = tokenCounter?.shouldStop() ?: false

    /**
     * Agent 每次循环开始时调用。
     * - 未暂停（正常 RUNNING）：快速路径直接返回，零开销
     * - 已暂停：lock() 挂起（零 CPU 唤醒），直到 onSchedulerResumed 释放门闩；
     *   拿到锁后立即释放，恢复 unlocked 基态
     * - while 语义：穿越门闩后若期间又被 pause（pauseRequested 重新置 true），继续等待
     */
    override suspend fun waitResume() {
        while (pauseRequested) {
            try {
                pauseMutex.lock()  // ← 真正挂起，直到 Scheduler 调 onSchedulerResumed()
            } finally {
                // lock/unlock 配对：立即释放，绝不持锁跨批次（旧版死锁根因）
                runCatching { pauseMutex.unlock() }
            }
        }
    }

    /**
     * Scheduler 状态变 PAUSED 时调（通过 AgentRegistration.onPause 回调）。
     * 幂等：重复 pause 直接返回。suspend 是因为用 lock()（而非 tryLock）锁门闩——
     * 若 Agent 恰处于 waitResume 的瞬时持锁窗口，等它释放后再锁，保证 pause 不丢失。
     */
    suspend fun onSchedulerPaused() {
        if (pauseRequested) return
        pauseRequested = true
        pauseMutex.lock()
    }

    /** Scheduler 状态变 RUNNING 时调（通过 AgentRegistration.onResume 回调） */
    fun onSchedulerResumed() {
        pauseRequested = false
        try { pauseMutex.unlock() } catch (_: IllegalStateException) { /* already unlocked */ }
    }
}
