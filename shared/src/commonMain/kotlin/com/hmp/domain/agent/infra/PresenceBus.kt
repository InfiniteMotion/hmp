package com.hmp.domain.agent.infra

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 存在感事件（M4-T5 PresenceBus）：伙伴唯一嗓音的来源，事件 → 侧条/DJ 线等消费点。
 *
 * 消费点在 UI 层（M5 建 UI 消费）。引擎平面仅 emit，不感知 UI 形态 ——
 * 保持双平面思想：呈现层可自由换，安全不随呈现松动。
 *
 * ⚠️ **`CompanionBadge`（底栏胶囊徽标圆点）已于 2026-09-19 废弃删除**：该通道从未接线
 * （`RadioSubAgent` 发过事件、`CompanionCapsule` 从未渲染），产品上也已放弃圆点方案。
 * 底栏伙伴胶囊的存在感改由**直接订阅 `radioState`** 呈现（电台开启期间换图标 + 高亮，
 * 见 `CompanionCapsule` 的电台态）。所以：**不要再往这里加"圆点/徽标"类事件**。
 */
sealed interface PresenceEvent {
    /** 任务进行阶段（三点脉动 + 可由用户取消，M5-T1）。 */
    data class TaskProgress(val phase: String, val active: Boolean) : PresenceEvent

    /** 云端额度耗尽 → 本地兜底已接管（能力受限但永不失能）。 */
    data object CloudQuotaExhausted : PresenceEvent

    /** 切歌信号（曲间）——HelloSubAgent 据此刷新问候卡（M6-T3）。 */
    data object DjBlank : PresenceEvent

    /** 用户连跳 N 首（跳过感知重排触发，M6-T2）。 */
    data class SkipDetected(val consecutiveCount: Int, val trackTitle: String? = null) : PresenceEvent

    /** T3 SubAgent 执行进度（供 Master 验收 + UI 后台状态显示）。 */
    data class AgentProgress(
        val agentId: String,
        val processed: Int,
        val total: Int,
    ) : PresenceEvent
}

/**
 * PresenceBus：伙伴存在的唯一发声通道 —— 广播事件流（多消费点）。
 */
class PresenceBus {
    private val eventFlow = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 16)

    val events: Flow<PresenceEvent> = eventFlow.asSharedFlow()

    /** 广播事件。非阻塞 tryEmit，依赖 extraBufferCapacity=16 保证不丢事件。 */
    fun emit(event: PresenceEvent) {
        eventFlow.tryEmit(event)
    }
}