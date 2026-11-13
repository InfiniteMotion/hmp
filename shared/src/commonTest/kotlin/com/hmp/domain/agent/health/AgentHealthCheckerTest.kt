package com.hmp.domain.agent.health

import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.TokenLedgerEntry
import com.hmp.data.database.TokenTimeBucket
import com.hmp.domain.agent.quota.TokenQuotaGate
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 健康门控的判据（2026-11-11）：
 * 决定 initialize() 之后要不要拉起富化 / 画像，以及「不通过」时是否会误启。
 */
class AgentHealthCheckerTest {

    private fun checker(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        music: FakeMusicRepository = FakeMusicRepository(),
        quota: TokenQuotaGate? = null,
    ) = AgentHealthChecker(
        settingsRepository = settings,
        quotaGate = quota,
        musicRepository = music,
        timeProvider = { 0L },
    )

    private fun song(id: Long) = MusicInfo(
        Music(id, "曲目$id", "歌手", "专辑", 180_000, "/$id.mp3", ""),
        null,
        null,
    )

    @Test
    fun `免费模式且空库 → LLM 就绪但不可富化`() = runTest {
        val health = checker().check()
        assertIs<LlmHealth.Ready>(health.llm)
        assertIs<LibraryHealth.Empty>(health.library)
        assertTrue(health.canUseLlm)
        assertFalse(health.canEnrich, "没有歌就不该建富化")
    }

    @Test
    fun `自定义模式未配置 → 判定未配置`() = runTest {
        val settings = FakeSettingsRepository()
        settings.saveAiAccessMode(AiAccessMode.CUSTOM)
        settings.saveCustomAiConfig(AiEndpointConfig())

        val health = checker(settings = settings).check()
        assertIs<LlmHealth.NotConfigured>(health.llm)
        assertFalse(health.canUseLlm)
        assertFalse(health.canEnrich)
    }

    @Test
    fun `配置就绪且有歌 → 可以富化`() = runTest {
        val music = FakeMusicRepository()
        music.addMusic(song(1))

        val health = checker(music = music).check()
        val library = assertIs<LibraryHealth.Ready>(health.library)
        assertEquals(1, library.totalSongs)
        assertTrue(health.canEnrich)
    }

    @Test
    fun `配额耗尽 → LLM 不可用`() = runTest {
        val settings = FakeSettingsRepository()
        val gate = TokenQuotaGate(
            ledgerDao = exhaustedDao(),
            settingsRepository = settings,
            timeProvider = { 0L },
        )

        val health = checker(settings = settings, quota = gate).check()
        assertIs<LlmHealth.QuotaExhausted>(health.llm)
        assertFalse(health.canUseLlm)
    }

    @Test
    fun `记忆开关来自全局配置（默认开启）`() = runTest {
        assertTrue(checker().check().memoryEnabled)
    }

    @Test
    fun `空库 → Hello 与富化都不启`() = runTest {
        val health = checker().check()
        assertFalse(health.canHello)
        assertFalse(health.canEnrich)
    }

    @Test
    fun `富化开关关闭 → 不可富化但 LLM 仍可用`() = runTest {
        val music = FakeMusicRepository()
        music.addMusic(song(1))
        val health = AgentHealthChecker(
            settingsRepository = FakeSettingsRepository(),
            quotaGate = null,
            musicRepository = music,
            timeProvider = { 0L },
            enrichEnabledProvider = { false },
        ).check()
        assertTrue(health.canUseLlm)
        assertFalse(health.canEnrich, "用户关掉富化后，健康检查就该判不通过")
    }

    @Test
    fun `hello 分桶耗尽但总池充足 → 只挡 Hello`() = runTest {
        val settings = FakeSettingsRepository()
        val music = FakeMusicRepository()
        music.addMusic(song(1))
        val gate = TokenQuotaGate(
            ledgerDao = bucketDao("hello", 10_000_000L),
            settingsRepository = settings,
            timeProvider = { 0L },
        )
        val health = checker(settings = settings, music = music, quota = gate).check()
        assertIs<LlmHealth.Ready>(health.llm)
        assertEquals(setOf("hello"), health.exhaustedBuckets)
        assertFalse(health.canHello, "hello 桶耗尽就不该启 Hello")
        assertTrue(health.canEnrich)
        assertTrue(health.canRadio)
        assertTrue(health.canChat)
    }

    @Test
    fun `enrich 分桶耗尽 → 只挡富化`() = runTest {
        val settings = FakeSettingsRepository()
        val music = FakeMusicRepository()
        music.addMusic(song(1))
        val gate = TokenQuotaGate(
            ledgerDao = bucketDao("enrich", 25_000_000L),
            settingsRepository = settings,
            timeProvider = { 0L },
        )
        val health = checker(settings = settings, music = music, quota = gate).check()
        assertEquals(setOf("enrich"), health.exhaustedBuckets)
        assertFalse(health.canEnrich)
        assertTrue(health.canHello)
        assertTrue(health.canRadio)
    }

    @Test
    fun `总池耗尽 → 四个能力全不可用`() = runTest {
        val settings = FakeSettingsRepository()
        val gate = TokenQuotaGate(
            ledgerDao = exhaustedDao(),
            settingsRepository = settings,
            timeProvider = { 0L },
        )
        val health = checker(settings = settings, quota = gate).check()
        assertIs<LlmHealth.QuotaExhausted>(health.llm)
        assertFalse(health.canChat)
        assertFalse(health.canRadio)
        assertFalse(health.canHello)
        assertFalse(health.canEnrich)
    }

    @Test
    fun `依赖未接线 → 无法判断时放行（不误拦测试与旧装配）`() = runTest {
        val health = AgentHealthChecker(null, null, null, { 0L }).check()
        assertIs<LlmHealth.Ready>(health.llm)
        assertTrue(health.canUseLlm)
        assertTrue(health.canHello, "库读不到 ≠ 空库")
        assertTrue(health.canEnrich)
        assertTrue(health.canRadio)
        assertTrue(health.canChat)
    }

    /** 造一个「指定桶已用满」的账本替身；免费模式总池 1 亿，各桶 40/25/25/10 百万。 */
    private fun bucketDao(label: String, used: Long): TokenLedgerDao = object : TokenLedgerDao {
        override suspend fun insert(entry: TokenLedgerEntry): Long = 0L
        override suspend fun count(): Long = 1L
        override suspend fun earliestMs(): Long? = 0L
        override suspend fun sumByAgent(sinceMs: Long): List<TokenAggregateRow> = emptyList()
        override suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<TokenAggregateRow> = listOf(
            TokenAggregateRow(
                label = label,
                promptTokens = used,
                completionTokens = 0L,
                totalTokens = used,
                estimatedRows = 0L,
                calls = 1L,
            ),
        )

        override suspend fun sumByEndpoint(sinceMs: Long): List<TokenAggregateRow> = emptyList()
        override suspend fun sumByHour(sinceMs: Long): List<TokenTimeBucket> = emptyList()
        override suspend fun sumByDay(sinceMs: Long): List<TokenTimeBucket> = emptyList()
        override suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<TokenTimeBucket> = emptyList()
        override suspend fun sumTotal(): Long = used
        override suspend fun sumTotalSince(sinceMs: Long): Long = used
        override suspend fun deleteAll() {}
    }

    /** 总池被打满的账本替身。 */
    private fun exhaustedDao(): TokenLedgerDao = bucketDao("master", 200_000_000L)
}
