package com.hmp.domain.agent.infra

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 存在感事件（M4-T5 PresenceBus）：伙伴唯一嗓音的来源，事件 → 徽标/侧条/DJ 消费点。
 *
 * 消费点在 UI 层（M5 建 UI 消费）：徽标驱动底栏伙伴胶囊（M1-T6 已备锚点），侧条/DJ 线预留。
 * 引擎平面仅 emit，不感知 UI 形态 —— 保持双平面思想：呈现层可自由换，安全不随呈现松动。
 */
sealed interface PresenceEvent {
    /** 伙伴徽标（底栏胶囊圆点）。 */
    data class CompanionBadge(val visible: Boolean, val label: String? = null) : PresenceEvent

    /** 有通知侧条可展示（4s 退场 + 撤销，M5-T3）。 */
    data class NoticeAvailable(val text: String) : PresenceEvent

    /** 任务进行阶段（三点脉动 + 可由用户取消，M5-T1）。 */
    data class TaskProgress(val phase: String, val active: Boolean) : PresenceEvent

    /** 云端额度耗尽 → 本地兜底已接管（能力受限但永不失能）。 */
    data object CloudQuotaExhausted : PresenceEvent

    /** DJ 衔接空白（电台曲间一句，M6-T3）。 */
    data object DjBlank : PresenceEvent

    /** T3 SubAgent 执行进度（供 Master 验收 + UI 后台状态显示）。 */
    data class AgentProgress(
        val agentId: String,
        val processed: Int,
        val total: Int,
    ) : PresenceEvent
}

/**
 * PresenceBus：伙伴存在的唯一发声通道。合成徽标态 + 广播事件流。
 * 徽标态以 StateFlow 暴露（UI 可收集最新值），事件流以 SharedFlow 广播（多消费点）。
 */
class PresenceBus {
    private val badgeFlow = MutableStateFlow(PresenceEvent.CompanionBadge(visible = false))
    private val eventFlow = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 16)

    val badgeState: StateFlow<PresenceEvent.CompanionBadge> = badgeFlow
    val events: Flow<PresenceEvent> = eventFlow.asSharedFlow()

    /** 广播事件；徽标事件同步更新合成态。非阻塞 tryEmit，依赖 extraBufferCapacity=16 保证不丢事件。 */
    fun emit(event: PresenceEvent) {
        if (event is PresenceEvent.CompanionBadge) badgeFlow.value = event
        eventFlow.tryEmit(event)
    }
}