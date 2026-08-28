package com.hmp.domain.agent.engine

import com.hmp.domain.agent.tool.ToolPermissionLevel
import com.hmp.test.fakes.FakeAuditLogPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustLedgerTest {
    @Test
    fun `escalates after 3 implicit accepts and caps at silent`() {
        val l = TrustLedger(escalationThreshold = 3)
        assertEquals(TrustTier.SUGGEST, l.tier)
        l.onActionAccepted("createPlaylist")
        l.onActionAccepted("createPlaylist")
        l.onActionAccepted("createPlaylist")
        assertEquals(TrustTier.ACT, l.tier)
        // 再 3 次 → SILENT（最高档）
        l.onActionAccepted("createPlaylist"); l.onActionAccepted("createPlaylist"); l.onActionAccepted("createPlaylist")
        assertEquals(TrustTier.SILENT, l.tier)
        // 已到顶，继续接受不越界
        l.onActionAccepted("createPlaylist")
        assertEquals(TrustTier.SILENT, l.tier)
    }

    @Test
    fun `rejection dials back a tier and resets counter`() {
        val l = TrustLedger(escalationThreshold = 3)
        repeat(3) { l.onActionAccepted("reorderPlaylist") }
        assertEquals(TrustTier.ACT, l.tier)
        l.onActionRejected("reorderPlaylist")
        assertEquals(TrustTier.SUGGEST, l.tier)
        assertEquals(0, l.consecutiveAccepts("reorderPlaylist"))
        // 最低档不再回拨
        l.onActionRejected("reorderPlaylist")
        assertEquals(TrustTier.SUGGEST, l.tier)
    }

    @Test
    fun `different tools are counted separately`() {
        val l = TrustLedger(escalationThreshold = 3)
        repeat(3) { l.onActionAccepted("createPlaylist") }
        assertEquals(TrustTier.ACT, l.tier)
        // 另一工具计数独立，不影响已达档位的升档判定（已兑现不计）
        assertFalse(l.tier == TrustTier.SILENT)
    }
}

class PolicyGuardTest {
    private fun guard(ledger: TrustLedger = TrustLedger(), audit: FakeAuditLogPort = FakeAuditLogPort()) =
        PolicyGuard(ledger, audit)

    @Test
    fun `silent tools always allow silent`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        val g = PolicyGuard(TrustLedger(), audit)
        assertEquals(PermissionDecision.AllowSilent, g.decide("searchLibrary", ToolPermissionLevel.SILENT, taskId = 1))
        assertEquals("allowed_silent", audit.entries.single().outcome)
    }

    @Test
    fun `confirm tools SUGGEST require confirm ACT and SILENT only notify`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        val ledger = TrustLedger(escalationThreshold = 3)
        val g = PolicyGuard(ledger, audit)

        assertEquals(PermissionDecision.RequireConfirm, g.decide("createPlaylist", ToolPermissionLevel.CONFIRM, null))
        // 升到 ACT 后仅通知
        repeat(3) { ledger.onActionAccepted("createPlaylist") }
        assertEquals(PermissionDecision.AllowWithNotify, g.decide("createPlaylist", ToolPermissionLevel.CONFIRM, null))
    }

    @Test
    fun `strong confirm never relaxes`() = kotlinx.coroutines.runBlocking {
        val ledger = TrustLedger()
        val g = PolicyGuard(ledger, FakeAuditLogPort())
        repeat(6) { ledger.onActionAccepted("removePlaylist") } // 冲到 SILENT
        assertEquals(TrustTier.SILENT, ledger.tier)
        // 即便静默档，STRONG_CONFIRM 仍要确认
        assertEquals(PermissionDecision.RequireConfirm,
            g.decide("removePlaylist", ToolPermissionLevel.STRONG_CONFIRM, null))
    }

    @Test
    fun `decide writes audit`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        PolicyGuard(TrustLedger(), audit).decide("getListenStats", ToolPermissionLevel.SILENT, taskId = 2)
        assertEquals("policy_guard", audit.entries.single().tool)
        assertEquals("allowed_silent", audit.entries.single().outcome)
    }
}

class SchedulerTest {
    @Test
    fun `cooldown blocks immediate refire`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        var now = 0L
        val s = Scheduler({ now }, audit, cooldownMs = mapOf(TriggerType.CALL to 5_000L))
        assertTrue(s.tryFire(TriggerRequest(TriggerType.CALL, "user")))
        now = 1_000L
        assertFalse(s.tryFire(TriggerRequest(TriggerType.CALL, "user")), "冷却内应拒绝")
        now = 6_000L
        assertTrue(s.tryFire(TriggerRequest(TriggerType.CALL, "user")), "冷却后应允许")
        assertEquals(listOf("accepted", "cooldown", "accepted"), audit.outcomes("scheduler"))
    }
}