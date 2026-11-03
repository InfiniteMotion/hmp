package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.data.database.HelloCardCache
import com.hmp.data.database.HelloCardCacheDao
import com.hmp.data.util.localDayRange
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D6-06 判据：每日刷新的"今天"必须来自注入时钟。
 *
 * 做法：第一天让 DAO 返回四类卡都已生成 → 只查日期、不重跑；
 * 时钟前进一天后同一次检查 → 应重跑 `dailyRefreshOnce`（可观测信号是它第一步的
 * `repo.getMusicCount()`）。
 *
 * 不直接断言卡片内容：生成依赖曲库/标签/LLM，内容断言会把这条判据变成脆弱用例。
 */
class HelloDailyRefreshTest {

    /** 固定到本地时间正午，避免 +24h 跨时区边界抖动。 */
    private val day1: Long = localDayRange("2026-11-02")!!.first + 12 * 3_600_000L

    @Test
    fun advancingOneDay_rerunsDailyRefresh() = runTest {
        val dao = FakeCardDao(
            mapOf("2026-11-02" to listOf("RECOMMEND", "DISCOVER", "FORGOTTEN", "ANNIVERSARY")),
        )
        var now = day1
        val repo = FakeMusicRepository().apply { addMusic(music(1)) }
        val agent = HelloSubAgent(
            contextBudget = AgentContextBudget(agentId = "hello", llmClient = null),
            toolRegistryView = ToolRegistryView.empty(null),
            cardCacheDao = dao,
            musicRepository = repo,
            timeProvider = { now },
        )

        agent.checkAndRunDailyRefresh()
        assertEquals("2026-11-02", dao.lastQueriedDate, "首日应查当天的卡")
        assertEquals(0, repo.getMusicCountCalls, "当天卡片已齐，不应重跑每日刷新")

        now += 24 * 3_600_000L
        agent.checkAndRunDailyRefresh()
        assertEquals("2026-11-03", dao.lastQueriedDate, "跨天后日期键必须跟着注入时钟走")
        assertTrue(repo.getMusicCountCalls >= 1, "跨天后应重跑 dailyRefreshOnce")
    }

    private fun music(id: Long) = MusicInfo(
        music = Music(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            duration = 180_000,
            path = "/music/$id.mp3",
            albumArtUri = "",
        ),
        extra = null,
        userInfo = null,
    )
}

/** 只维护"某天有哪些卡"这一件事，其余查询一律空 —— 判据只看日期键与是否重跑。 */
private class FakeCardDao(private val typesByDate: Map<String, List<String>>) : HelloCardCacheDao {

    var lastQueriedDate: String? = null
        private set

    override suspend fun insert(cache: HelloCardCache): Long = 1

    override suspend fun getLatestOfAnyDate(type: String): HelloCardCache? = null

    override suspend fun getLatest(type: String, date: String): HelloCardCache? = null

    override suspend fun getLatestCardTypesByDate(date: String): List<String> {
        lastQueriedDate = date
        return typesByDate[date].orEmpty()
    }

    override suspend fun deleteSameDaySameType(type: String, date: String) = Unit

    override suspend fun deleteByType(type: String) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun countToday(type: String, startOfDay: Long): Int = 0

    override suspend fun getTodayAllEntities(startOfDay: Long): List<HelloCardCache> = emptyList()

    override suspend fun getYesterdayRecommends(yesterdayStart: Long, yesterdayEnd: Long): List<HelloCardCache> = emptyList()

    override suspend fun getWeeklyDiscoverCards(sevenDaysAgo: Long): List<HelloCardCache> = emptyList()

    override suspend fun getWeeklyForgottenCards(sevenDaysAgo: Long): List<HelloCardCache> = emptyList()

    override suspend fun sumTokensSince(since: Long): Long? = null
}
