package com.hearablemusic.player.ui.common.dialogs.controller

import com.hmp.domain.agent.port.ConfirmDecision
import com.hmp.domain.agent.port.ConfirmStep
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogEvent
import com.hearablemusic.player.ui.common.dialogs.viewmodel.MessageDuration
import com.hearablemusic.player.ui.common.util.nowEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 全局 DialogManager——管两种能力：
 * 1) 普通 toast/message/timer/share：沿用 DialogEvent 发射 + UI 消费的既有模式
 * 2) 确认链（STRONG_CONFIRM 双确认链）：[requestConfirm] 挂起等待整条链完成，
 *    UI 层每步通过 [advanceConfirmStep]/[denyConfirmChain]/[cancelConfirmChain] 推进
 */
class DialogManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _dialogEvent = MutableSharedFlow<DialogEvent?>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dialogEvent: SharedFlow<DialogEvent?> = _dialogEvent.asSharedFlow()

    // ═══════════════════════════════════════════════════════════
    // 确认链状态（STRONG_CONFIRM）
    // id → (steps, 当前 stepIndex, 挂起 continuation)
    // ═══════════════════════════════════════════════════════════

    private data class PendingConfirm(
        val steps: List<ConfirmStep>,
        var stepIndex: Int,
        var continuation: (ConfirmDecision) -> Unit,
    )

    private val pendingConfirms = mutableMapOf<Long, PendingConfirm>()

    /**
     * 请求用户做一条确认链。挂起直到 UI 层全部通过 / 拒绝 / 取消。
     * STRONG_CONFIRM 场景传 2 步：第一步 Agent 意图，第二步系统权限确认。
     */
    suspend fun requestConfirm(steps: List<ConfirmStep>): ConfirmDecision =
        suspendCancellableCoroutine { cont ->
            if (steps.isEmpty()) {
                // 没 step → 直接 Approved
                cont.resume(ConfirmDecision.Approved)
                return@suspendCancellableCoroutine
            }
            val id = nowEpochMillis()
            val pending = PendingConfirm(
                steps = steps,
                stepIndex = 0,
                continuation = { decision ->
                    if (cont.isActive && !cont.isCompleted) cont.resume(decision)
                }
            )
            pendingConfirms[id] = pending

            // 发射第一个 step 给 UI
            scope.launch {
                _dialogEvent.emit(
                    DialogEvent.ConfirmChain(steps = steps, stepIndex = 0, id = id)
                )
            }

            cont.invokeOnCancellation {
                // 调用方协程被取消（罕见，UI dismiss 走的是 cancelConfirmChain）
                pendingConfirms.remove(id)
            }
        }

    /** UI 层：用户点当前 step 的「确认」。如果还有下一步 → 推进；否则整条链 Approved。 */
    fun advanceConfirmStep(id: Long) {
        val pending = pendingConfirms[id] ?: return
        val nextIndex = pending.stepIndex + 1
        if (nextIndex >= pending.steps.size) {
            // 全部通过
            pendingConfirms.remove(id)
            pending.continuation(ConfirmDecision.Approved)
            scope.launch { _dialogEvent.emit(null) }
        } else {
            pending.stepIndex = nextIndex
            scope.launch {
                _dialogEvent.emit(
                    DialogEvent.ConfirmChain(
                        steps = pending.steps,
                        stepIndex = nextIndex,
                        id = id,
                    )
                )
            }
        }
    }

    /** UI 层：用户点当前 step 的「取消」→ 整条链 Denied。 */
    fun denyConfirmChain(id: Long) {
        val pending = pendingConfirms.remove(id) ?: return
        pending.continuation(ConfirmDecision.Denied)
        scope.launch { _dialogEvent.emit(null) }
    }

    /** UI 层：用户关闭弹窗（dismissRequest）→ 整条链 Cancelled。 */
    fun cancelConfirmChain(id: Long) {
        val pending = pendingConfirms.remove(id) ?: return
        pending.continuation(ConfirmDecision.Cancelled)
        scope.launch { _dialogEvent.emit(null) }
    }

    // ═══════════════════════════════════════════════════════════
    // 普通弹窗 / toast（沿用既有模式）
    // ═══════════════════════════════════════════════════════════

    fun showDialog(event: DialogEvent) {
        scope.launch {
            _dialogEvent.emit(event)
        }
    }

    fun showMessage(message: String, duration: MessageDuration = MessageDuration.Short) {
        showDialog(DialogEvent.Message(message, duration))
    }

    fun showTimerDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit = {}) {
        showDialog(DialogEvent.ShowTimerDialog(onConfirm, onDismiss))
    }

    fun dismissTimerDialog() {
        showDialog(DialogEvent.DismissTimerDialog)
    }

    fun dismissDialog() {
        scope.launch {
            _dialogEvent.emit(null)
        }
    }

    fun shareMusic(title: String, artist: String, album: String, filePath: String) {
        showDialog(DialogEvent.ShareMusic(title, artist, album, filePath))
    }
}
