package com.hmp.domain.agent.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EngineDefaults（引擎出厂默认参数表）契约测试。
 *
 * 这张表是 policy / runtime 共同消费的**下层常量**——数值本身即契约：
 * 改动任何一项都意味着行为变化，必须在这里显式留痕。
 */
class EngineDefaultsTest {

    @Test
    fun masterDefaults_stepBudgetAndTemperature() {
        assertEquals(8, EngineDefaults.STEP_BUDGET, "单任务步数预算（总纲 7.1）")
        assertEquals(0.7f, EngineDefaults.TEMPERATURE_MASTER)
    }

    @Test
    fun subAgentDefaults_perRoleConstants() {
        assertEquals(0.6f, EngineDefaults.TEMPERATURE_HELLO, "问候/推荐需要自然感")
        assertEquals(1, EngineDefaults.HELLO_DAILY_RECOMMEND_COUNT)
        assertEquals(8, EngineDefaults.HELLO_RECOMMEND_LIST_SIZE)

        assertEquals(3, EngineDefaults.TRUST_ESCALATION_THRESHOLD, "信任阶梯：连续隐式接受 3 次升一档")
        assertEquals(100, EngineDefaults.DAILY_CLOUD_QUOTA)
        assertEquals(0.9f, EngineDefaults.ENRICH_TARGET_COVERAGE)
        assertEquals(0.4f, EngineDefaults.TEMPERATURE_ENRICH, "标签分类任务，低温保证稳定 JSON")

        assertEquals(12, EngineDefaults.RADIO_TARGET_COUNT)
        assertEquals(0.2f, EngineDefaults.TEMPERATURE_RADIO, "编排任务，最低温")
        assertTrue(EngineDefaults.RADIO_AUTO_RENEW, "电台默认自动续歌")
    }

    @Test
    fun globalGuardrails_constants() {
        assertEquals(5_000L, EngineDefaults.CALL_COOLDOWN_MS)
        assertEquals(30_000L, EngineDefaults.EVENT_COOLDOWN_MS)
        assertEquals(600, EngineDefaults.MAX_TASK_STATE_CHARS)
        assertEquals(1_200, EngineDefaults.MAX_LIBRARY_LIST_CHARS)
        assertEquals(6, EngineDefaults.MAX_TOOL_RESULTS_KEPT)
    }

    @Test
    fun contextWindow_fixedAssumption() {
        assertEquals(64_000, EngineDefaults.AGENT_CONTEXT_WINDOW, "统一窗口假设值：固定常量，不探测不覆盖")
        assertEquals(0.9f, EngineDefaults.CONTEXT_WINDOW_SAFETY_FACTOR, "前置守卫安全系数")
        assertEquals(6, EngineDefaults.HISTORY_KEEP_COUNT, "上下文组装保留的最近消息条数")
    }

    @Test
    fun defaultTemperatureFor_mapsByRole() {
        assertEquals(0.7f, EngineDefaults.defaultTemperatureFor("master"))
        assertEquals(0.6f, EngineDefaults.defaultTemperatureFor("hello"))
        assertEquals(0.4f, EngineDefaults.defaultTemperatureFor("enrich"))
        assertEquals(0.2f, EngineDefaults.defaultTemperatureFor("radio"))
        assertEquals(0.7f, EngineDefaults.defaultTemperatureFor("anything-else"), "未知 role 回落 master 温度")
    }

    @Test
    fun temperatureOrdering_taskStabilityMonotonicallyCooler() {
        val master = EngineDefaults.defaultTemperatureFor("master")
        val hello = EngineDefaults.defaultTemperatureFor("hello")
        val enrich = EngineDefaults.defaultTemperatureFor("enrich")
        val radio = EngineDefaults.defaultTemperatureFor("radio")

        assertTrue(master > hello, "对话 > 问候（创造性递减）")
        assertTrue(hello > enrich, "问候 > 标签分类")
        assertTrue(enrich > radio, "标签分类 > 电台编排（编排要求最稳定的 JSON）")
    }
}
