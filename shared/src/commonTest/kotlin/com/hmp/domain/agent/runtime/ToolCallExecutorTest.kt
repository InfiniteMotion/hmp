package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.AgentPolicyConfig
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.port.ConfirmGate
import com.hmp.domain.agent.port.ConfirmOutcome
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.ToolPermissionLevel
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.agent.tool.spec.ToolResult
import com.hmp.test.fakes.FakeAuditLogPort
import com.hmp.test.fakes.FakeConfirmGate
import com.hmp.test.fakes.RecordingTool
import com.hmp.test.fakes.ThrowingTool
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolCallExecutorTest {

    private val audit = FakeAuditLogPort()

    private fun executor(
        registryTools: List<com.hmp.domain.agent.tool.spec.AgentTool>,
        policyGuard: PolicyGuard? = PolicyGuard(audit),
        confirmGate: ConfirmGate? = null,
        trackMessages: Boolean = true,
    ) = ToolCallExecutor(
        registry = ToolRegistry(registryTools),
        policyGuard = policyGuard,
        confirmGate = confirmGate,
        auditLog = audit,
        trackMessages = trackMessages,
    )

    private fun toolCall(name: String, args: String = "{}") =
        LlmEvent.ToolCall(id = "id_$name", name = name, argumentsJson = args)

    // ===== 批量权限确认 =====

    @Test
    fun `policyGuard 为 null 时全自动通过`() = runTest {
        val ex = executor(listOf(RecordingTool()), policyGuard = null)
        val approvals = ex.batchDecideApprovals(AgentPolicy.master(), listOf(toolCall("recording_tool")))
        assertEquals(true, approvals["id_recording_tool"])
    }

    @Test
    fun `Phase0 alwaysAllow 直接放行`() = runTest {
        val policy = AgentPolicy.master(
            config = AgentPolicyConfig(alwaysAllow = mutableSetOf("recording_tool")),
        )
        val ex = executor(listOf(RecordingTool()))
        val approvals = ex.batchDecideApprovals(policy, listOf(toolCall("recording_tool")))
        assertEquals(true, approvals["id_recording_tool"])
    }

    @Test
    fun `STRONG_CONFIRM 无确认门时安全兜底否决`() = runTest {
        val ex = executor(listOf(RecordingTool(name = "strong_tool", permissionLevel = ToolPermissionLevel.STRONG_CONFIRM)))
        val approvals = ex.batchDecideApprovals(AgentPolicy.master(), listOf(toolCall("strong_tool")))
        assertEquals(false, approvals["id_strong_tool"])
    }

    @Test
    fun `STRONG_CONFIRM 确认门 AllowAlways 放行并写入白名单`() = runTest {
        val policy = AgentPolicy.master(confirmGate = FakeConfirmGate { ConfirmOutcome.AllowAlways })
        val ex = executor(
            listOf(RecordingTool(name = "strong_tool", permissionLevel = ToolPermissionLevel.STRONG_CONFIRM)),
            confirmGate = policy.confirmGate,
        )
        val approvals = ex.batchDecideApprovals(policy, listOf(toolCall("strong_tool"))) {
            policy.config.alwaysAllow.addAll(it)
        }
        assertEquals(true, approvals["id_strong_tool"])
        assertTrue(policy.config.alwaysAllow.contains("strong_tool"))
    }

    // ===== 单工具执行 =====

    @Test
    fun `未知工具返回 skipped`() = runTest {
        val ex = executor(listOf(RecordingTool()))
        val rec = ex.executeOne(toolCall("ghost_tool"), messages = null, approved = null)
        assertEquals("skipped", rec.outcome)
    }

    @Test
    fun `approved 为 false 返回 refused 不执行`() = runTest {
        val ex = executor(listOf(RecordingTool()))
        val messages = mutableListOf<LlmMessage>()
        val rec = ex.executeOne(toolCall("recording_tool"), messages = messages, approved = false)
        assertEquals("refused", rec.outcome)
        // 拒绝并非静默：拒绝路径会 appendToolResult 回写 tool 消息，让 LLM 知道工具被跳过
        assertEquals(1, messages.size)
        assertEquals("（用户拒绝执行，已跳过）", messages.single().content)
    }

    @Test
    fun `成功执行回传 tool 结果消息`() = runTest {
        val ex = executor(listOf(RecordingTool()))
        val messages = mutableListOf<LlmMessage>()
        val rec = ex.executeOne(toolCall("recording_tool"), messages = messages, approved = true)
        assertEquals("success", rec.outcome)
        assertEquals(1, messages.size)
        assertEquals("tool", messages.first().role)
        assertEquals("recorded", messages.first().content)
    }

    @Test
    fun `执行异常被兜底为 failed`() = runTest {
        val ex = executor(listOf(ThrowingTool(name = "throwing_tool")))
        val rec = ex.executeOne(toolCall("throwing_tool"), messages = null, approved = true)
        assertEquals("failed", rec.outcome)
    }

    @Test
    fun `trackMessages 关闭时不回传消息`() = runTest {
        val ex = executor(listOf(RecordingTool()), trackMessages = false)
        val messages = mutableListOf<LlmMessage>()
        ex.executeOne(toolCall("recording_tool"), messages = messages, approved = true)
        assertTrue(messages.isEmpty())
    }
}
