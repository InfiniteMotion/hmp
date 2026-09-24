package com.hearablemusic.player.ui.common.dialogs.viewmodel

import androidx.navigation3.runtime.NavKey
import com.hmp.domain.agent.port.ConfirmDecision
import com.hmp.domain.agent.port.ConfirmStep
import com.hearablemusic.player.ui.common.util.nowEpochMillis

sealed class DialogEvent {
    data class Message(
        val message: String,
        val duration: MessageDuration = MessageDuration.Short,
        val id: Long = nowEpochMillis()
    ) : DialogEvent()

    data class ShowTimerDialog(
        val onConfirm: (Int) -> Unit,
        val onDismiss: () -> Unit = {}
    ) : DialogEvent()

    object DismissTimerDialog : DialogEvent()

    data class ShareMusic(
        val title: String,
        val artist: String,
        val album: String,
        val filePath: String
    ) : DialogEvent()

    /**
     * 导航请求：由 UI 层（持有 RouteNavigator 的组合点）消费。
     * ViewModel 不持有导航器引用，避免生命周期/旋转后引用失效。
     * id 保证每次事件实例唯一（即使 route 相同），使消费方 LaunchedEffect 可重新触发。
     */
    data class NavRequest(
        val route: NavKey,
        val id: Long = nowEpochMillis()
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
        val id: Long = nowEpochMillis()
    ) : DialogEvent()
}

enum class MessageDuration {
    Short,
    Long
}
