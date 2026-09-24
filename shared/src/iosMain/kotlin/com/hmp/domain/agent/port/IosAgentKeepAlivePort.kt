package com.hmp.domain.agent.port

import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS 实现（F11-L3）：把 agent 的保活诉求映射到 iOS 有限后台任务。
 *
 * - PLAYING / RADIO_ACTIVE / PROACTIVE / ENRICH_TASK 全部用 [UIApplication.beginBackgroundTaskWithName]
 *   延长后台宽限期——覆盖"电台等模型回合""富化执行窗口"这类需要在后台短期跑完的场景，
 *   避免进程在模型返回前被系统挂起（验收：电台等模型时退后台 → 当前回合能完成；回前台续上）。
 * - 引用计数：同一 reason 多次 set(true)/set(false) 只认"0↔1"跳变，避免重复 begin/end 错配。
 * - 所有状态变更统一派发到主队列（串行）执行——UIKit 调用本就须在主线程，
 *   串行队列同时消除了并发竞态，无需额外锁（Kotlin/Native 无 `synchronized`）。
 *
 * 说明：周期 Enrich 的 BGProcessingTask 长期调度（需 Info.plist 声明 + AppDelegate 调度循环 +
 * 真实富化触发）超出本阶段"有限保活"范围（见 F11 taskbook L3 / §5），此处预留扩展点，
 * 暂不接入，避免引入不可验证的半成品。
 */
class IosAgentKeepAlivePort : AgentKeepAlivePort {

    private val counts = mutableMapOf<KeepAliveReason, Int>()
    private val tasks = mutableListOf<ULong>()

    override fun setKeepAlive(reason: KeepAliveReason, active: Boolean) {
        dispatch_async(dispatch_get_main_queue()) {
            val cur = (counts[reason] ?: 0) + if (active) 1 else -1
            counts[reason] = cur.coerceAtLeast(0)
            if (counts.values.any { it > 0 }) ensureTask() else endAllTasks()
        }
    }

    private fun ensureTask() {
        if (tasks.isNotEmpty()) return
        val id = UIApplication.sharedApplication.beginBackgroundTaskWithName("hmp.agent") {
            // 系统宽限期耗尽：主动结束，交由系统挂起。
            // 会话丢失属已接受边界（见 F11 D3「永不落盘」），此处不抢救。
            endAllTasks()
        }
        tasks.add(id)
    }

    private fun endAllTasks() {
        val app = UIApplication.sharedApplication
        tasks.forEach { app.endBackgroundTask(it) }
        tasks.clear()
    }
}
