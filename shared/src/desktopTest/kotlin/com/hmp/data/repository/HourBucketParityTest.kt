package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.ListeningDuration
import com.hmp.data.database.PlaybackHistory
import com.hmp.data.util.currentHour
import com.hmp.data.util.localDateString
import com.hmp.data.util.localHourOfDay
import com.hmp.data.util.todayDateString
import com.hmp.test.db.createTestDatabase
import com.hmp.test.repo.InMemoryMusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 一-3 / D5-01 + D5-02 + D5-03 的时间与窗口口径判据。
 *
 * 三件事都在"同一个数字有两套算法"这一类里：
 * - D5-01：SQL 用 `strftime(..., 'localtime')`，Kotlin 用 `(ms / 3_600_000) % 24`（那是 **UTC**）；
 * - D5-02：`days` 参数在时段维度被 `coerceAtLeast(1)` 夹掉，"全部"只剩 24 小时；
 * - D5-03：`days` 只当除数，"最近 N 天日均"实为"有史以来总量 ÷ N"。
 *
 * 全部用真 Room + 真仓库实现（`InMemoryMusicRepository`）—— 这类口径问题只有走真引擎才暴露得出
 * `strftime` 与 Kotlin 的分歧。主机时区 = Asia/Shanghai（UTC+8），所以下面的"跨 UTC 日界"构造成立；
 * 在 UTC 主机上这些断言会自然相等（那时它们只保证不回归，抓不到曾经的回归 —— 这点如实记在此处）。
 */
class HourBucketParityTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: InMemoryMusicRepository
    private val zone: ZoneId = ZoneId.systemDefault()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = InMemoryMusicRepository(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    /** 本地时区某一天的 HH:mm → epoch 毫秒（刻意不用被测函数族构造输入）。 */
    private fun atLocal(dayOffset: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.now(zone).plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private suspend fun seedHistory(vararg playedAtMs: Long) {
        playedAtMs.forEachIndexed { index, ms ->
            db.playbackHistoryDao().insert(
                PlaybackHistory(musicId = 100L + index, playedAt = ms, playDuration = 60_000L, isCompleted = true)
            )
        }
    }

    // ═══ D5-01：SQL 与 Kotlin 必须是同一个小时 ═══

    @Test
    fun localHour_isTheSameNumberOnBothSides() = runTest {
        // 本地 23:30（= UTC 15:30）。旧 Kotlin 侧算出 15，SQL 侧算出 23 —— 画像与页面差 8 小时。
        val moment = atLocal(dayOffset = -1, hour = 23, minute = 30)
        seedHistory(moment)

        val fromSql = db.playbackHistoryDao().getHourlyDistribution(atLocal(-30, 0)).single().hour
        val fromKotlin = localHourOfDay(moment)

        assertEquals(23, fromSql, "SQL 侧口径变了（strftime 的 'localtime' 那半）")
        assertEquals(23, fromKotlin, "Kotlin 侧又回到 UTC 自算了")
        assertEquals(fromSql, fromKotlin, "两套时区口径并存 —— 这正是 D5-01 记的账")
    }

    @Test
    fun behaviorSnapshot_usesLocalHour_notUtc() = runTest {
        seedHistory(atLocal(-1, 23, 30), atLocal(-1, 22, 15))

        val snapshot = repo.getBehaviorSnapshot(90)

        assertEquals(
            mapOf(23 to 1, 22 to 1),
            snapshot.hourHistogram,
            "hourHistogram 喂给画像的『夜猫子』判定，旧实现是 UTC 小时（会整体偏移一个时区）"
        )
    }

    /**
     * 跨 UTC 日界但同一本地日：旧 `dayKey = ms / 86_400_000` 把 UTC+8 的"本地今天 00:30"
     * 算成 UTC 昨天 → `activeDays` 少算一天。
     */
    @Test
    fun activeDays_countsLocalCalendarDays() = runTest {
        seedHistory(atLocal(0, 0, 30), atLocal(0, 1, 30))   // 同一本地日的两条

        assertEquals(1, repo.getBehaviorSnapshot(90).activeDays, "本地同一天该只算 1 天")

        seedHistory(atLocal(-1, 23, 30))                   // 昨天夜里 23:30 → 另一个本地日
        assertEquals(2, repo.getBehaviorSnapshot(90).activeDays, "跨了本地零点才算第二天")
    }

    /** `currentHour()` 与 `localHourOfDay(now)` 必须同值（UI 侧那处 UTC 自算收敛到这一条口径）。 */
    @Test
    fun currentHour_matchesTheInstantBasedHelper() {
        assertEquals(currentHour(), localHourOfDay(System.currentTimeMillis()))
    }

    // ═══ D5-01 的另一半 + D5-11 改写段：本地日区间 ≡ strftime 的本地日 ═══

    @Test
    fun playedOnUsesLocalDayRange() = runTest {
        val today = todayDateString()
        val ids = listOf(1L, 2L)
        ids.forEachIndexed { index, id ->
            db.musicDao().insert(
                com.hmp.data.database.Music(
                    id = id, title = "t$index", artist = "a", album = "b",
                    duration = 100L, path = "/$id.mp3", albumArtUri = "",
                )
            )
            db.playbackHistoryDao().insert(
                PlaybackHistory(musicId = id, playedAt = atLocal(0, 0, 30), playDuration = 1L)
            )
        }

        assertEquals(ids.toSet(), repo.getMusicIdsPlayedOn(today).toSet(), "本地今日 00:30 的播放要算进今天")
        assertTrue(repo.getMusicIdsPlayedOn(localDateString(atLocal(-3, 12))).isEmpty(), "三天前不该出现在今天")
    }

    // ═══ D5-02：「全部」不被夹成 1 天 ═══

    @Test
    fun hourlyDistribution_honoursAllWindow() = runTest {
        val recent = atLocal(-3, 12)
        val ancient = atLocal(-730, 12)   // 两年前
        seedHistory(recent, ancient)

        val buckets = repo.getHourlyDistribution(windowDays = -1)   // NarrativeTimeRange.ALL.toDays()

        assertEquals(2, buckets.sumOf { it.playCount }, "「全部」窗口该覆盖两年前的记录，而不是只有最近 24 小时")
        assertEquals(
            2,
            repo.getWindowedAnalytics(days = -1).totalPlayCount,
            "时段维度与其余四个维度必须是同一批记录"
        )
    }

    @Test
    fun finiteWindow_stillBoundsCorrectly() = runTest {
        seedHistory(atLocal(-3, 12), atLocal(-730, 12))

        assertEquals(1, repo.getHourlyDistribution(windowDays = 7).sumOf { it.playCount })
    }

    // ═══ D5-03：days 是窗口，不只是除数 ═══

    @Test
    fun avgDailyListening_minutesIsPerActiveDayInsideWindow() = runTest {
        val dao = db.listeningDurationDao()
        dao.insertAll(
            listOf(
                ListeningDuration(date = localDateString(atLocal(-2, 12)), duration = 60 * 60_000L, updatedAt = 1L),
                ListeningDuration(date = localDateString(atLocal(-1, 12)), duration = 60 * 60_000L, updatedAt = 2L),
                // 400 天前的一条巨量记录：旧实现会把整张表加起来除以 7
                ListeningDuration(date = localDateString(atLocal(-400, 12)), duration = 60_000 * 60_000L, updatedAt = 3L),
            )
        )

        val week = repo.getAvgDailyListeningMinutes(7)
        assertEquals(60f, week, 0.001f, "窗口内只有 2 个听歌日、各 60 分钟 → 日均 60，而不是全表 ÷ 7")

        val all = repo.getAvgDailyListeningMinutes(-1)
        assertTrue(all > 60f, "「全部」要看得见那条历史巨量记录（三个听歌日的平均），实际 $all")
        assertEquals((60f + 60f + 60_000f) / 3f, all, 0.001f)
    }

    @Test
    fun avgDailyListening_emptyWindow_returnsZero_notHistoricalTotal() = runTest {
        db.listeningDurationDao().insertAll(
            listOf(ListeningDuration(date = localDateString(atLocal(-400, 12)), duration = 60 * 60_000L, updatedAt = 1L))
        )

        assertEquals(0f, repo.getAvgDailyListeningMinutes(7), "窗口内没听歌就是 0，不该拿历史总量顶替")
    }
}
