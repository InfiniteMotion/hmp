package com.hearablemusic.player.ui.common.dialogs.viewmodel

import androidx.navigation3.runtime.NavKey
import com.hmp.domain.agent.port.ConfirmStep

sealed class DialogEvent {
    data class Message(
        val message: String,
        val duration: MessageDuration = MessageDuration.Short,
        /** 由 DialogManager 单调取号：同文同毫秒的两条消息也要能重新驱动 toast。 */
        val id: Long,
    ) : DialogEvent()

    data class ShareMusic(
        val title: String,
        val artist: String,
        val album: String,
        val filePath: String,
    ) : DialogEvent()

    /**
     * 导航请求：由 UI 层（持有 RouteNavigator 的组合点）消费。
     * ViewModel 不持有导航器引用，避免生命周期/旋转后引用失效。
     * 消费端按事件逐条处理（单点 collect），不再靠实例 id 驱动 LaunchedEffect key。
     */
    data class NavRequest(
        val route: NavKey,
    ) : DialogEvent()

    /**
     * 确认链弹窗（STRONG_CONFIRM 双确认链的 UI 载体）。
     * [steps] 全部确认 → 用户 approve 了整条链。
     * 当前 step 由 [stepIndex] 指示（初始 0）。
     * [id] 唯一标识本次链；DialogManager 靠它回传决策。
     */
    data class ConfirmChain(
        val steps: List<ConfirmStep>,
        val stepIndex: Int = 0,
        /** 由 DialogManager 的单调计数器分配，必须显式传入。 */
        val id: Long,
    ) : DialogEvent()
}

enum class MessageDuration {
    Short,
    Long,
}
