package com.hearablemusic.player.ui.common.dialogs.controller

import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogEvent
import com.hearablemusic.player.ui.common.dialogs.viewmodel.MessageDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 全局对话框事件出口：toast / 导航请求 / 分享。
 *
 * 2026-11-13（D6-08）：原 STRONG_CONFIRM 双确认链（`requestConfirm` + `pendingConfirms`
 * 裸 Map）是**无人调用的死路径** —— 唯一适配器 `DialogConfirmGateAdapter` 全仓零构造点，
 * 工具确认实际走 `ConfirmBridge` + 对话内联确认卡。已整体删除，这里只剩事件发射。
 */
class DialogManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 容量从 1 提到 16 且不再 DROP_OLDEST：同一帧连发多条（toast + 导航）
    // 时宁可让 emit 挂起等消费，也不静默丢事件。消费端是单点 collect，排空很快。
    private val _dialogEvent = MutableSharedFlow<DialogEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val dialogEvent: SharedFlow<DialogEvent> = _dialogEvent.asSharedFlow()

    /**
     * 普通事件的 id，只服务 `MessageToast` 的 key。
     * 取号点放在 emit 前的协程里，避免默认参数求值的线程不确定。
     */
    private val idMutex = Mutex()
    private var nextEventId = 0L

    private suspend fun allocateEventId(): Long = idMutex.withLock { ++nextEventId }

    fun showDialog(event: DialogEvent) {
        scope.launch {
            _dialogEvent.emit(event)
        }
    }

    fun showMessage(message: String, duration: MessageDuration = MessageDuration.Short) {
        scope.launch {
            _dialogEvent.emit(DialogEvent.Message(message, duration, allocateEventId()))
        }
    }

    fun shareMusic(title: String, artist: String, album: String, filePath: String) {
        showDialog(DialogEvent.ShareMusic(title, artist, album, filePath))
    }
}
