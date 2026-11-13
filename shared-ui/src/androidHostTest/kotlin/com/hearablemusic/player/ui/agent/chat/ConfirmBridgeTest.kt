package com.hearablemusic.player.ui.agent.chat

import com.hmp.domain.agent.port.ConfirmOutcome
import com.hmp.domain.agent.port.ConfirmRequest
import com.hmp.domain.agent.port.ToolPermissionLevel
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 确认超时（2026-11-13）：不静默挂起、不假装用户拒绝，返回 Timeout 并让本轮可结束。 */
class ConfirmBridgeTest {

    private fun request() = ConfirmRequest("strong_tool", "args", ToolPermissionLevel.STRONG_CONFIRM)

    @Test
    fun `超时后返回全 Timeout 且后续提交无效`() = runTest {
        val bridge = ConfirmBridge()
        var turnId: String? = null
        bridge.onRequest = { id, _ -> turnId = id }

        val pending = async { bridge.awaitApprovals(listOf(request())) }
        runCurrent()
        advanceTimeBy(ConfirmBridge.CONFIRM_TIMEOUT_MS + 1)

        assertEquals(listOf(ConfirmOutcome.Timeout), pending.await())
        assertFalse(
            bridge.submit(turnId!!, listOf(ConfirmOutcome.AllowOnce)),
            "已超时的一轮不能再被提交唤醒",
        )
    }

    @Test
    fun `按时提交返回用户决策`() = runTest {
        val bridge = ConfirmBridge()
        var turnId: String? = null
        bridge.onRequest = { id, _ -> turnId = id }

        val pending = async { bridge.awaitApprovals(listOf(request())) }
        runCurrent()
        bridge.submit(turnId!!, listOf(ConfirmOutcome.AllowOnce))

        assertEquals(listOf(ConfirmOutcome.AllowOnce), pending.await())
    }
}
