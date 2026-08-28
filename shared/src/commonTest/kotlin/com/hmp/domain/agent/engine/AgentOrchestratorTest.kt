package com.hmp.domain.agent.engine

import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.FakePlaybackCommandPort
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeAgentPlaylistRepository
import com.hmp.test.fakes.FakeAiExtraEnrichPort
import com.hmp.test.fakes.FakeAuditLogPort
import com.hmp.test.fakes.FakeLlmTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentOrchestratorTest {

    private val config = AiEndpointConfig(endpoint = "http://x", selectedModel = "m")

    private fun song(id: Long, title: String, artist: String) =
        MusicInfo(Music(id, title, artist, "Album", 180_000, "/$id.mp3", ""), null, null)

    private class Fixture(
        transport: FakeLlmTransport,
        confirmGate: ConfirmGate = ConfirmGate { _, _ -> true },
        stepBudget: Int = EngineDefaults.STEP_BUDGET,
        cloudQuota: Int = EngineDefaults.DAILY_CLOUD_QUOTA,
    ) {
        val audit = FakeAuditLogPort()
        val music = FakeAgentMusicRepository()
        val playlists = FakeAgentPlaylistRepository()
        val ledger = TrustLedger()
        val policy = PolicyGuard(ledger, audit)
        val budget = ContextBudget({ 86_400_000L }, dailyCloudQuota = cloudQuota)
        val presence = PresenceBus()
        val orchestrator = AgentOrchestrator(
            transport = transport,
            registry = ToolRegistry.create(ToolDependencies(
                musicRepository = music,
                playlistRepository = playlists,
                nowPlayingContextProvider = FakeNowPlayingContextProvider,
                playbackCommandPort = FakePlaybackCommandPort,
                enrichPort = FakeAiExtraEnrichPort(),
            )),
            policyGuard = policy,
            contextBudget = budget,
            sessionStore = SessionStore({ 0L }),
            presenceBus = presence,
            auditLog = audit,
            confirmGate = confirmGate,
            stepBudget = stepBudget,
        )
    }

    private fun toolCall(id: String, name: String, args: String) = LlmEvent.ToolCall(id, name, args)

    @Test
    fun `direct answer returns answered in one step`() = runTest {
        val t = FakeLlmTransport(perTurnScript = listOf(
            listOf(LlmEvent.TextDelta("你好！"), LlmEvent.Completed),
        ))
        val fx = Fixture(t)
        val r = fx.orchestrator.run("打招呼", config)
        assertEquals(TerminationReason.ANSWERED, r.terminatedBy)
        assertEquals(1, r.stepsUsed)
        assertEquals("你好！", r.text)
        assertTrue(r.toolCalls.isEmpty())
        assertEquals(1, t.calls.size)
    }

    @Test
    fun `tool loop executes then answers`() = runTest {
        val t = FakeLlmTransport(perTurnScript = listOf(
            listOf(toolCall("c1", "searchLibrary", """{"query":"rock"}"""), LlmEvent.Completed),
            listOf(LlmEvent.TextDelta("找到了一首 Rock Anthem。"), LlmEvent.Completed),
        ))
        val fx = Fixture(t).also { it.music.songs[1L] = song(1, "Rock Anthem", "BB") }
        val r = fx.orchestrator.run("搜索摇滚", config)

        assertEquals(TerminationReason.ANSWERED, r.terminatedBy)
        assertEquals(2, r.stepsUsed)
        assertEquals("找到了一首 Rock Anthem。", r.text)
        assertEquals(1, r.toolCalls.size)
        assertEquals("searchLibrary", r.toolCalls[0].toolName)
        assertEquals("success", r.toolCalls[0].outcome)
        // 第二步请求应包含回传的 assistant tool_calls + tool 结果消息
        val secondCall = t.calls[1].messages
        assertTrue(secondCall.any { it.role == "assistant" && it.toolCalls != null })
        assertTrue(secondCall.any { it.role == "tool" && it.toolCallId == "c1" })
    }

    @Test
    fun `confirm gate deny marks tool refused and does not act`() = runTest {
        val t = FakeLlmTransport(perTurnScript = listOf(
            listOf(toolCall("c1", "createPlaylist", """{"name":"我的收藏"}"""), LlmEvent.Completed),
            listOf(LlmEvent.TextDelta("我没有创建。"), LlmEvent.Completed),
        ))
        val fx = Fixture(t, confirmGate = ConfirmGate { _, _ -> false })
        val r = fx.orchestrator.run("建一个歌单", config)

        assertEquals(TerminationReason.ANSWERED, r.terminatedBy)
        assertEquals("refused", r.toolCalls.single().outcome)
        assertEquals("refused", fx.audit.outcomes("createPlaylist").single())
        assertTrue(fx.playlists.playlists.isEmpty(), "被拒的歌单不应被创建")
    }

    @Test
    fun `step budget hard circuit break`() = runTest {
        val t = FakeLlmTransport(perTurnScript = listOf(   // 两轮都返回工具调用
            listOf(toolCall("c1", "getRecentHistory", """{}"""), LlmEvent.Completed),
        ))
        val fx = Fixture(t, stepBudget = 2)
        val r = fx.orchestrator.run("不断调用工具", config)

        assertEquals(TerminationReason.STEP_BUDGET_EXHAUSTED, r.terminatedBy)
        assertEquals(2, r.stepsUsed)
        assertEquals("circuit_break", fx.audit.outcomes("orchestrator").firstOrNull())
    }

    @Test
    fun `cloud quota exhausted falls back locally`() = runTest {
        val t = FakeLlmTransport(perTurnScript = listOf(
            listOf(LlmEvent.TextDelta("不该发生"), LlmEvent.Completed),
        ))
        val fx = Fixture(t, cloudQuota = 0) // 额度 0 → 首调即耗尽
        val r = fx.orchestrator.run("你好", config)

        assertEquals(TerminationReason.CLOUD_QUOTA_EXHAUSTED, r.terminatedBy)
        assertEquals(0, r.stepsUsed)
        assertEquals("budget_exhausted", fx.audit.outcomes("orchestrator").single())
    }

    @Test
    fun `llm failure maps to failed`() = runTest {
        val t = FakeLlmTransport(perTurnScript = listOf(listOf(LlmEvent.Failed("boom"))))
        val fx = Fixture(t)
        val r = fx.orchestrator.run("hi", config)
        assertEquals(TerminationReason.FAILED, r.terminatedBy)
        assertEquals("failed", fx.audit.outcomes("orchestrator").single())
    }
}