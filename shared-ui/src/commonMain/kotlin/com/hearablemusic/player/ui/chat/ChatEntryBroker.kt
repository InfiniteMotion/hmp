package com.hearablemusic.player.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * M1 锚点 → M5 对话入口经纪：外部入口（快捷条提交）先把待带话写入 [pendingInput]，
 * 再导航 Chat 页；ChatScreen 进入时消费一次并把输入作为首条发送（同 session_id 语义）。
 */
class ChatEntryBroker {
    val pendingInput = MutableStateFlow<String?>(null)
}