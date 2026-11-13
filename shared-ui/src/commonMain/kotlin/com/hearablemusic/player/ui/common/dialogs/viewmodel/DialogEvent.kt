package com.hearablemusic.player.ui.common.dialogs.viewmodel

import androidx.navigation3.runtime.NavKey

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
}

enum class MessageDuration {
    Short,
    Long,
}
