package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.data.database.HelloCardCache
import com.hmp.data.database.HelloCardCacheDao
import com.hmp.domain.agent.card.RecommendItemRecord
import com.hmp.domain.agent.card.RecommendListPayload
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.card.TimePhase
import com.hmp.domain.agent.card.cardType
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.ToolRegistryView
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * D2 判据：
 * ① 同一 type 连续写两次（不同日期）→ 只保留一行；
 * ② 启动恢复取**最近一次**，不要求是今天；
 * ③ 两个来源互不覆盖。
 */
class HelloRecommendPersistenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun newAgent(dao: HelloCardCacheDao): HelloSubAgent = HelloSubAgent(
        contextBudget = AgentContextBudget(agentId = "hello", llmClient = null),
        toolRegistryView = ToolRegistryView.empty(null),
        cardCacheDao = dao,
    )

    private fun payload(source: RecommendSource, date: String, trackId: Long): RecommendListPayload = RecommendListPayload(
        source = source,
        overview = "测试总述",
        phase = TimePhase.NIGHT,
        generatedAt = 1L,
        generatedForDate = date,
        items = listOf(RecommendItemRecord(trackId = trackId, reason = "测试按语")),
    )

    private fun cache(source: RecommendSource, date: String, at: Long, trackId: Long): HelloCardCache {
        val p = payload(source, date, trackId)
        return HelloCardCache(
            cardType = source.cardType(),
            cardContentJson = json.encodeToString(RecommendListPayload.serializer(), p),
            generatedAt = at,
            generatedForDate = date,
            llmUsed = false,
            recommendSongIds = listOf(trackId.toString()),
        )
    }

    @Test
    fun persist_overwritesSameType() = runTest {
        val dao = RecordingCardDao()
        val agent = newAgent(dao)

        agent.persistRecommendSnapshot(cache(RecommendSource.DAILY, "2026-11-02", at = 1L, trackId = 11L))
        agent.persistRecommendSnapshot(cache(RecommendSource.DAILY, "2026-11-03", at = 2L, trackId = 22L))

        val rows = dao.rows.filter { it.cardType == RecommendSource.DAILY.cardType() }
        assertEquals(1, rows.size, "同一 type 只应保留一行（覆盖，不按天追加）")
        assertEquals("2026-11-03", rows.single().generatedForDate, "保留的应是最新一次")
    }

    @Test
    fun persist_keepsTwoSourcesSeparate() = runTest {
        val dao = RecordingCardDao()
        val agent = newAgent(dao)

        agent.persistRecommendSnapshot(cache(RecommendSource.DAILY, "2026-11-03", at = 1L, trackId = 11L))
        agent.persistRecommendSnapshot(cache(RecommendSource.PRIVATE, "2026-11-03", at = 2L, trackId = 22L))

        assertEquals(2, dao.rows.size, "每日与私人应各一行")
    }

    @Test
    fun restore_usesLatestEvenWhenNotToday() = runTest {
        val dao = RecordingCardDao()
        // 昨天生成的一份（今天还没生成）
        dao.insert(cache(RecommendSource.DAILY, "2026-11-02", at = 1L, trackId = 33L))
        val agent = newAgent(dao)

        agent.restoreRecommendLists()

        val restored = agent.dailyRecommendList.value
        assertNotNull(restored, "应恢复上一次的结果，而不是留空")
        assertEquals("2026-11-02", restored.generatedForDate)
        assertEquals(33L, restored.items.single().trackId)
    }
}

/** 只实现本用例消费的语义：按 type 覆盖、按 generatedAt 取最新。 */
private class RecordingCardDao : HelloCardCacheDao {

    val rows = mutableListOf<HelloCardCache>()
    private var nextId = 1L

    override suspend fun insert(cache: HelloCardCache): Long {
        val id = nextId++
        rows += cache.copy(id = id)
        return id
    }

    override suspend fun getLatestOfAnyDate(type: String): HelloCardCache? = rows.filter { it.cardType == type }.maxByOrNull { it.generatedAt }

    override suspend fun getLatest(type: String, date: String): HelloCardCache? = rows.filter { it.cardType == type && it.generatedForDate == date }.maxByOrNull { it.generatedAt }

    override suspend fun getLatestCardTypesByDate(date: String): List<String> = rows.filter { it.generatedForDate == date }.map { it.cardType }.distinct()

    override suspend fun deleteSameDaySameType(type: String, date: String) {
        rows.removeAll { it.cardType == type && it.generatedForDate == date }
    }

    override suspend fun deleteByType(type: String) {
        rows.removeAll { it.cardType == type }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun countToday(type: String, startOfDay: Long): Int = rows.count { it.cardType == type && it.generatedAt >= startOfDay }

    override suspend fun getTodayAllEntities(startOfDay: Long): List<HelloCardCache> = rows.filter { it.generatedAt >= startOfDay }

    override suspend fun getYesterdayRecommends(yesterdayStart: Long, yesterdayEnd: Long): List<HelloCardCache> = rows.filter { it.cardType == "RECOMMEND" && it.generatedAt in yesterdayStart until yesterdayEnd }

    override suspend fun getWeeklyDiscoverCards(sevenDaysAgo: Long): List<HelloCardCache> = emptyList()

    override suspend fun getWeeklyForgottenCards(sevenDaysAgo: Long): List<HelloCardCache> = emptyList()

    override suspend fun sumTokensSince(since: Long): Long? = null
}
