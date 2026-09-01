package com.hmp.domain.agent.policy

import com.hmp.domain.agent.tool.ToolPermissionLevel
import com.hmp.test.fakes.FakeAuditLogPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TrustLedgerTest {
    @Test
    fun `escalates after 3 implicit accepts and caps at silent`() {
        val cfg = AgentPolicyConfig()  // trustLevel 默认 0
        val l = TrustLedger(cfg, escalationThreshold = 3)
        assertEquals(TrustLevel.SUGGEST, cfg.trustLevel)
        l.onActionAccepted("createPlaylist")
        l.onActionAccepted("createPlaylist")
        l.onActionAccepted("createPlaylist")
        assertEquals(TrustLevel.ACT, cfg.trustLevel)
        // 再 3 次 → SILENT（最高档）
        l.onActionAccepted("createPlaylist"); l.onActionAccepted("createPlaylist"); l.onActionAccepted("createPlaylist")
        assertEquals(TrustLevel.SILENT, cfg.trustLevel)
        // 已到顶，继续接受不越界
        l.onActionAccepted("createPlaylist")
        assertEquals(TrustLevel.SILENT, cfg.trustLevel)
    }

    @Test
    fun `rejection dials back a tier and resets counter`() {
        val cfg = AgentPolicyConfig()
        val l = TrustLedger(cfg, escalationThreshold = 3)
        repeat(3) { l.onActionAccepted("reorderPlaylist") }
        assertEquals(TrustLevel.ACT, cfg.trustLevel)
        l.onActionRejected("reorderPlaylist")
        assertEquals(TrustLevel.SUGGEST, cfg.trustLevel)
        assertEquals(0, l.consecutiveAccepts("reorderPlaylist"))
        // 最低档不再回拨
        l.onActionRejected("reorderPlaylist")
        assertEquals(TrustLevel.SUGGEST, cfg.trustLevel)
    }

    @Test
    fun `manualPromote sets trustLevel directly`() {
        val cfg = AgentPolicyConfig()
        val l = TrustLedger(cfg)
        l.manualPromote(TrustLevel.SILENT)
        assertEquals(TrustLevel.SILENT, cfg.trustLevel)
        // 超出范围会被 coerceIn
        l.manualPromote(99)
        assertEquals(TrustLevel.SILENT, cfg.trustLevel)
    }
}

class PolicyGuardTest {
    private fun guard(audit: FakeAuditLogPort = FakeAuditLogPort()) =
        PolicyGuard(audit)

    private fun masterPolicy(config: AgentPolicyConfig = AgentPolicyConfig()) =
        AgentPolicy.master(config = config)

    @Test
    fun `silent tools always allow silent`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        val g = PolicyGuard(audit)
        val policy = masterPolicy()
        assertEquals(PermissionDecision.AllowSilent, g.decide(policy, "searchLibrary", ToolPermissionLevel.SILENT, taskId = 1))
        assertEquals("allowed_silent", audit.entries.single().outcome)
    }

    @Test
    fun `confirm tools SUGGEST require confirm ACT and SILENT only notify`() = kotlinx.coroutines.runBlocking {
        val g = PolicyGuard(FakeAuditLogPort())

        // SUGGEST (trustLevel=0) → RequireConfirm
        var policy = masterPolicy(AgentPolicyConfig(trustLevel = TrustLevel.SUGGEST))
        assertEquals(PermissionDecision.RequireConfirm, g.decide(policy, "createPlaylist", ToolPermissionLevel.CONFIRM, null))

        // ACT (trustLevel=1) → AllowWithNotify
        policy = masterPolicy(AgentPolicyConfig(trustLevel = TrustLevel.ACT))
        assertEquals(PermissionDecision.AllowWithNotify, g.decide(policy, "createPlaylist", ToolPermissionLevel.CONFIRM, null))

        // SILENT (trustLevel=2) → AllowWithNotify
        policy = masterPolicy(AgentPolicyConfig(trustLevel = TrustLevel.SILENT))
        assertEquals(PermissionDecision.AllowWithNotify, g.decide(policy, "createPlaylist", ToolPermissionLevel.CONFIRM, null))
    }

    @Test
    fun `strong confirm never relaxes regardless of trustLevel`() = kotlinx.coroutines.runBlocking {
        val g = PolicyGuard(FakeAuditLogPort())
        val policy = masterPolicy(AgentPolicyConfig(trustLevel = TrustLevel.SILENT))
        assertEquals(PermissionDecision.RequireConfirm,
            g.decide(policy, "removePlaylist", ToolPermissionLevel.STRONG_CONFIRM, null))
    }

    @Test
    fun `decide writes audit`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        PolicyGuard(audit).decide(masterPolicy(), "getListenStats", ToolPermissionLevel.SILENT, taskId = 2)
        assertEquals("getListenStats", audit.entries.single().tool)
        assertEquals("allowed_silent", audit.entries.single().outcome)
    }

    @Test
    fun `enrich agent denies confirm level tools via Phase1 agent identity gate`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        val g = PolicyGuard(audit)
        val enrichPolicy = AgentPolicy.enrich()  // default maxLevel=1 (NOTIFY)

        assertEquals(PermissionDecision.Deny, g.decide(enrichPolicy, "createPlaylist", ToolPermissionLevel.CONFIRM, null))
        assertEquals(PermissionDecision.AllowSilent, g.decide(enrichPolicy, "searchLibrary", ToolPermissionLevel.SILENT, null))
    }

    @Test
    fun `Phase0 alwaysAllow whitelist bypasses all later phases`() = kotlinx.coroutines.runBlocking {
        val audit = FakeAuditLogPort()
        val g = PolicyGuard(audit)
        val policy = AgentPolicy.master(
            config = AgentPolicyConfig(
                trustLevel = TrustLevel.SUGGEST,
                alwaysAllow = mutableSetOf("editPlaylist"),
            )
        )

        // CONFIRM 级 + SUGGEST 档 → 本来 RequireConfirm，但白名单命中 → AllowSilent
        assertEquals(PermissionDecision.AllowSilent, g.decide(policy, "editPlaylist", ToolPermissionLevel.CONFIRM, null))
        // STRONG_CONFIRM 级也被白名单绕过
        assertEquals(PermissionDecision.AllowSilent, g.decide(policy, "editPlaylist", ToolPermissionLevel.STRONG_CONFIRM, null))
        // 没在白名单里的 CONFIRM 级 → 正常 RequireConfirm
        assertEquals(PermissionDecision.RequireConfirm, g.decide(policy, "createPlaylist", ToolPermissionLevel.CONFIRM, null))
    }
}
