package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.agent.enrich.EnrichHealth
import com.hmp.domain.agent.enrich.EnrichWorkUnit
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicExtraTexts
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeLlmTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * EnrichSubAgent 自循环编排测试（不碰真实 LLM/DB）。
 *
 * 覆盖编排骨架的关键契约：
 * - 拉活返回 null 且覆盖率达标 → **自退出**（runLoop 收尾复位所有进度字段）
 * - 无 LLM 配置 → chunk 整体计入失败，不崩溃、继续循环直到验收退出
 * - LLM Round 1 失败 → 该 chunk 整体失败；**Round 0 预热失败不阻断**，Round 1 仍尝试
 * - pause/resume → runState 同步（Master 状态查询与 Capability 暴露依赖它）
 *
 * Enrich 的 6 轮 prompt 编排细节（枚举/自检/自由文本/反思）由
 * [EnrichResponseParserTest] 覆盖解析层，本文件不重复。
 */
class EnrichSubAgentTest {

    /** 脚本化富化仓库：按序吐工作单元；health 可定制；写库动作留痕。 */
    private class ScriptedEnrichRepo(
        workUnits: List<EnrichWorkUnit?>,
        private var health: EnrichHealth,
    ) : FakeAgentMusicRepository() {
        private val queue = workUnits.toMutableList()
        val fetchCalls: Int get() = fetchCount
        val extraWrites = mutableListOf<Long>()

        private var fetchCount = 0

        override suspend fun fetchNextEnrichWorkUnit(
            bigArtistThreshold: Int,
            mixGroupSize: Int,
        ): EnrichWorkUnit? {
            fetchCount++
            return if (queue.isNotEmpty()) queue.removeAt(0) else null
        }

        override suspend fun getEnrichHealth(): EnrichHealth = health

        override suspend fun updateMusicExtraTexts(musicId: Long, texts: MusicExtraTexts) {
            extraWrites += musicId
        }
    }

    private fun song(id: Long, title: String, artist: String) =
        MusicInfo(Music(id, title, artist, "Album", 180_000, "/$id.mp3", ""), null, null)

    private fun agent(
        repo: ScriptedEnrichRepo,
        transport: FakeLlmTransport? = null,
        targetCoverage: Float = 0.9f,
    ): EnrichSubAgent = EnrichSubAgent(
        agentId = "enrich",
        contextBudget = AgentContextBudget(agentId = "enrich", llmClient = transport),
        toolRegistryView = ToolRegistryView.empty(null),
        systemPrompt = "test-enrich-system",
        musicRepository = repo,
        presenceBus = null,
        enrichConfig = if (transport != null) AiEndpointConfig(isConfigured = true) else null,
        targetCoverage = targetCoverage,
        stopSignal = null,
    )

    @Test
    fun initialProgress_isIdle() {
        val agent = agent(ScriptedEnrichRepo(emptyList(), EnrichHealth(0, 0, 0)))

        // 逐字段断言：基类 runState 初值 PAUSED ≠ EnrichProgress.IDLE 的 UNREGISTERED
        // —— 后者是 UI 兜底语义，二者不同源；运行时视角 getProgress() 从 PAUSED 起步
        val progress = agent.getProgress()
        assertEquals(AgentRunState.PAUSED, progress.state, "运行时视角：基类 runState 初值 PAUSED")
        assertEquals(0, progress.processed)
        assertEquals(0, progress.failed)
        assertEquals(0, progress.success)
        assertNull(progress.currentArtist)
        assertEquals(EnrichProgress.IDLE, agent.progressState.value, "对外进度流初值 = 领域层唯一空进度 IDLE")
        assertEquals(CapabilityState.Status.IDLE, agent.stateFlow.value.status, "Capability 初始态 IDLE")
    }

    @Test
    fun pauseResume_updatesRunState() = runTest {
        val agent = agent(ScriptedEnrichRepo(emptyList(), EnrichHealth(0, 0, 0)))

        agent.pause()
        assertEquals(AgentRunState.PAUSED, agent.getProgress().state)

        agent.resume()
        assertEquals(AgentRunState.RUNNING, agent.getProgress().state)
    }

    @Test
    fun runLoop_selfExits_whenCoverageAlreadyMet() = runTest {
        val repo = ScriptedEnrichRepo(
            workUnits = emptyList(),
            health = EnrichHealth(enrichedSongCount = 3, totalSongCount = 3, lowConfidenceCount = 0),
        )
        val agent = agent(repo)

        val job = launch { agent.runLoop() }
        job.join()

        val progress = agent.getProgress()
        assertEquals("完成", progress.phase, "覆盖率达标 → 验收后自退出")
        assertEquals(AgentRunState.UNREGISTERED, progress.state)
        assertEquals(0, progress.processed)
        assertEquals(1, repo.fetchCalls, "首次拉活即见底，只拉一次")
    }

    @Test
    fun runLoop_withoutLlmConfig_chunkFailsThenSelfExits() = runTest {
        val repo = ScriptedEnrichRepo(
            workUnits = listOf(
                EnrichWorkUnit.ArtistGroup("周杰伦", listOf(song(1, "晴天", "周杰伦"), song(2, "七里香", "周杰伦"))),
            ),
            health = EnrichHealth(2, 2, 0),
        )
        val agent = agent(repo)   // enrichConfig = null

        val job = launch { agent.runLoop() }
        job.join()

        val progress = agent.getProgress()
        assertEquals(2, progress.processed, "processed 在进 chunk 时即累计（含失败 chunk）")
        assertEquals(2, progress.failed, "无 LLM 配置 → chunk 整体计入失败")
        assertEquals(0, progress.success)
        assertEquals(0, progress.chunkTotal, "收尾复位：chunkTotal 归零")
        assertEquals(0, progress.chunkIndex)
        assertNull(progress.currentArtist, "收尾复位：currentArtist 清空")
        assertEquals("完成", progress.phase)
        assertEquals(2, repo.fetchCalls, "处理完唯一工作单元后，第二次拉活返回 null → 验收自退出")
    }

    @Test
    fun runLoop_mixedGroup_withoutConfig_failsChunk() = runTest {
        val repo = ScriptedEnrichRepo(
            workUnits = listOf(
                EnrichWorkUnit.MixedGroup(
                    listOf(song(1, "一", "甲"), song(2, "二", "乙"), song(3, "三", "丙")),
                ),
            ),
            health = EnrichHealth(3, 3, 0),
        )
        val agent = agent(repo)

        val job = launch { agent.runLoop() }
        job.join()

        assertEquals(3, agent.getProgress().failed, "混合组同样走无配置失败路径")
        assertEquals(0, agent.getProgress().success)
        assertEquals("完成", agent.getProgress().phase)
    }

    @Test
    fun runLoop_llmRound1Fails_abortsChunkButStillExits() = runTest {
        val transport = FakeLlmTransport(failOnCall = true)
        val repo = ScriptedEnrichRepo(
            workUnits = listOf(
                EnrichWorkUnit.ArtistGroup("周杰伦", listOf(song(1, "晴天", "周杰伦"))),
            ),
            health = EnrichHealth(1, 1, 0),
        )
        val agent = agent(repo, transport = transport)

        val job = launch { agent.runLoop() }
        job.join()

        val progress = agent.getProgress()
        assertEquals(1, progress.failed, "Round 1 失败 → 该 chunk 全部计入失败")
        assertEquals(0, progress.success)
        assertEquals(2, transport.calls.size, "预热 1 次 + 枚举 1 次：预热失败不阻断，Round 1 仍要尝试")
        assertEquals("完成", progress.phase, "LLM 持续失败也会在曲库见底后正常退出（不挂死）")
    }
}
