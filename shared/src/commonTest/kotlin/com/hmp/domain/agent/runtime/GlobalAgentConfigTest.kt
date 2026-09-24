package com.hmp.domain.agent.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GlobalAgentConfig（全局 Agent 配置）默认值契约测试。
 *
 * 该配置存独立 DataStore key，DataStore 为空时**全部字段回落代码默认**（老用户零迁移）——
 * 默认值本身就是兼容性契约。
 */
class GlobalAgentConfigTest {

    @Test
    fun datastoreKey_isStable() {
        assertEquals("agent_global", GlobalAgentConfig.DATASTORE_KEY, "DataStore key 变更 = 老用户配置丢失，禁止静默修改")
    }

    @Test
    fun defaults_matchContract() {
        val config = GlobalAgentConfig()

        assertEquals(500_000, config.dailyTokenQuota, "日 Token 配额（跨所有 Agent 共享）")
        assertEquals("zh", config.replyLanguage, "默认中文回复")
        assertFalse(config.voiceEnabled, "嗓音与耳朵（M7 gate）默认关闭")
        assertTrue(config.memoryEnabled, "记忆功能默认开启")
    }

    @Test
    fun copy_overridesSingleField() {
        val config = GlobalAgentConfig().copy(dailyTokenQuota = 100, replyLanguage = "en")

        assertEquals(100, config.dailyTokenQuota)
        assertEquals("en", config.replyLanguage)
        assertFalse(config.voiceEnabled, "未覆盖字段保持默认")
        assertTrue(config.memoryEnabled)
    }
}
