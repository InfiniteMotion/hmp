package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.domain.agent.card.NarrativeTimeRange
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.ToolRegistryView
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HelloSubAgent 门面副驾驶的**构造与降级契约**测试（不启动 runLoop）。
 *
 * HelloSubAgent 的全部依赖均可空注入（DAO null → 内存卡池；repo null → 取数降级），
 * 这里固定住"零依赖裸构造"这一底线：任何字段改动破坏了空依赖降级，都在此暴露。
 *
 * 不测 runLoop：它起常驻协程（分钟 tick + 每日刷新循环），在 runTest 虚拟时间下
 * 会无限消耗虚拟时钟；问候/记忆逻辑已由 HelloMemoryTest / HelloGreetingProfilesTest 覆盖。
 */
class HelloSubAgentTest {

    /** 零依赖构造：不传 DAO / Repository / LLM，全部走空安全降级路径。 */
    private fun newAgent(): HelloSubAgent = HelloSubAgent(
        contextBudget = AgentContextBudget(agentId = "hello", llmClient = null),
        toolRegistryView = ToolRegistryView.empty(null),
    )

    @Test
    fun construction_initialState_isPausedWithEmptyFacets() {
        val agent = newAgent()

        assertEquals(AgentRunState.PAUSED, agent.state(), "未启动时运行态 PAUSED")
        assertTrue(agent.cards.value.isEmpty(), "未启动时卡片池为空")
        assertNull(agent.dailyRecommendList.value, "每日推荐列表未生成（UI 视为生成中）")
        assertNull(agent.privateRecommendList.value, "私人推荐列表未生成")
    }

    @Test
    fun pauseResume_transitionsRunState() = runTest {
        val agent = newAgent()

        agent.pause()
        assertEquals(AgentRunState.PAUSED, agent.state())

        agent.resume()
        assertEquals(AgentRunState.RUNNING, agent.state())
    }

    @Test
    fun shutdown_withoutAnyDependency_completesSilently() = runTest {
        val agent = newAgent()

        agent.shutdown()

        assertTrue(agent.cards.value.isEmpty(), "shutdown 清空卡片池")
        assertEquals(AgentRunState.PAUSED, agent.state(), "直接 shutdown（未经 runLoop）运行态保持 PAUSED")
    }

    @Test
    fun regenerateReportNarrative_withoutDeps_returnsNull() = runTest {
        val agent = newAgent()

        val result = agent.regenerateReportNarrative(NarrativeTimeRange.WEEK)

        assertNull(result, "缺 musicRepository / narrativeDao 时静默降级返回 null，不抛异常")
    }
}
