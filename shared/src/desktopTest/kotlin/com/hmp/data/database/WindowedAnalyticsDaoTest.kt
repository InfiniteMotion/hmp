package com.hmp.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hmp.data.database.myenum.LabelCategory
import com.hmp.data.database.myenum.LabelName
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

/**
 * 窗口统计（方案 B）6 条 SQL 的 DAO 级契约测试。
 *
 * 这 6 条 SQL 此前只在 `MusicRepositoryBase.getWindowedAnalytics / getWindowedSourceBreakdown /
 * getWindowedTopLabels / getWindowedTopSongs / getWindowedRecentPlayback` 里被调用，没有单独的 DAO 断言，
 * 属于「结构化测试空白」（收尾第④步第2项）。本测试用真实 Room（BundledSQLiteDriver，同 MigrationTest）
 * 落库后回查，验证聚合语义而非 SQL 字符串。
 *
 * 6 条被测 SQL：
 *   1. PlaybackHistoryDao.getTotalDurationSince
 *   2. PlaybackHistoryDao.getWindowedPlaybackCount
 *   3. PlaybackHistoryDao.getTopSongsSince
 *   4. PlaybackHistoryDao.getRecentPlaybackSince
 *   5. PlaybackHistoryDao.getSourceBreakdownSince
 *   6. MusicLabelDao.getTopLabelsSince
 */
class WindowedAnalyticsDaoTest {

    /** 固定 cutoff：窗口 = playedAt >= CUTOFF。 */
    private val cutoff = 1_600_000_000_000L // ≈ 2020-09-13

    private val dbFile = File("build", "windowed_analytics_test.db")

    private lateinit var db: AppDatabase

    @BeforeTest
    fun setup() {
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
        db = Room.databaseBuilder<AppDatabase>(dbFile.path)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun teardown() {
        db.close()
        dbFile.delete()
    }

    /** 写入一批跨越窗口边界的播放记录 + 关联曲目 + 标签。 */
    private suspend fun seed() {
        db.musicDao().insertAll(
            listOf(
                Music(id = 1, title = "Song A", artist = "Artist A", album = "Album A", duration = 200_000, path = "/a.mp3", albumArtUri = "", isDeleted = false),
                Music(id = 2, title = "Song B", artist = "Artist B", album = "Album B", duration = 200_000, path = "/b.mp3", albumArtUri = "", isDeleted = false),
            )
        )
        db.playbackHistoryDao().insertAll(
            listOf(
                // 窗口内：musicId=1，1 分钟，已完播，来源 USER
                PlaybackHistory(musicId = 1, playedAt = cutoff + 200, playDuration = 60_000, isCompleted = true, source = "USER"),
                // 窗口内：musicId=2，2 分钟，未完播，来源 RADIO
                PlaybackHistory(musicId = 2, playedAt = cutoff + 1_000, playDuration = 120_000, isCompleted = false, source = "RADIO"),
                // 窗口内：musicId=1，30 秒，未完播，来源 NULL（应被来源分布排除）
                PlaybackHistory(musicId = 1, playedAt = cutoff + 500, playDuration = 30_000, isCompleted = false, source = null),
                // 窗口外（早于 cutoff）：不应计入任何窗口聚合
                PlaybackHistory(musicId = 2, playedAt = cutoff - 2_000_000_000L, playDuration = 999_999, isCompleted = true, source = "USER"),
            )
        )
        db.musicLabelDao().insertAll(
            listOf(
                MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.ROCK, source = "LLM"),
                MusicLabel(musicId = 2, type = LabelCategory.GENRE, label = LabelName.POP, source = "LLM"),
                MusicLabel(musicId = 2, type = LabelCategory.MOOD, label = LabelName.HAPPY, source = "LLM"),
            )
        )
    }

    @Test
    fun getTotalDurationSince_sumsOnlyInWindow() = runTest {
        seed()
        // 窗口内三条：60_000 + 120_000 + 30_000 = 210_000；窗口外 999_999 不计
        assertEquals(210_000L, db.playbackHistoryDao().getTotalDurationSince(cutoff))
    }

    @Test
    fun getWindowedPlaybackCount_totalSkippedCompletionRate() = runTest {
        seed()
        val row = db.playbackHistoryDao().getWindowedPlaybackCount(cutoff)
        assertEquals(3, row.total, "窗口内应 3 条")
        assertEquals(2, row.skipped, "未完播 2 条（B、D）")
        // (1 + 0 + 0) / 3 = 0.3333...
        assertEquals(0.3333, row.completionRate, 0.001, "完播率应为 1/3")
    }

    @Test
    fun getTopSongsSince_ordersByPlayCount_andJoinsTitle() = runTest {
        seed()
        val top = db.playbackHistoryDao().getTopSongsSince(cutoff, 10)
        assertEquals(2, top.size, "两首歌有窗口内播放")
        // musicId=1 有 2 次（A、D），musicId=2 有 1 次（B）
        assertEquals(1L, top[0].musicId)
        assertEquals(2, top[0].playCnt)
        assertEquals("Song A", top[0].title)
        assertEquals("Artist A", top[0].artist)
        assertEquals(2L, top[1].musicId)
        assertEquals(1, top[1].playCnt)
    }

    @Test
    fun getTopSongsSince_limitCapsResults() = runTest {
        seed()
        val top = db.playbackHistoryDao().getTopSongsSince(cutoff, 1)
        assertEquals(1, top.size, "limit=1 应只返回 Top1")
        assertEquals(1L, top[0].musicId)
    }

    @Test
    fun getRecentPlaybackSince_ordersByPlayedAtDesc_andJoinsTitle() = runTest {
        seed()
        val recent = db.playbackHistoryDao().getRecentPlaybackSince(cutoff, 10)
        assertEquals(3, recent.size)
        // 降序：B(cutoff+1000) → D(cutoff+500) → A(cutoff+200)
        assertEquals(2L, recent[0].musicId)
        assertEquals("Song B", recent[0].title)
        assertEquals(1L, recent[1].musicId)
        assertEquals(1L, recent[2].musicId)
        // 来源 NULL 的行也能正确回查（LEFT JOIN music 不依赖 source）
        assertNull(recent[1].source)
    }

    @Test
    fun getSourceBreakdownSince_excludesNullSource_andGroups() = runTest {
        seed()
        val breakdown = db.playbackHistoryDao().getSourceBreakdownSince(cutoff).associate { it.source to it.cnt }
        // 窗口内：USER(1) + RADIO(1)；NULL 的 D 被 WHERE source IS NOT NULL 排除；窗口外的 USER 不计
        assertEquals(2, breakdown.size)
        assertEquals(1, breakdown["USER"])
        assertEquals(1, breakdown["RADIO"])
        // 窗口内非 NULL 来源共 2 条（USER + RADIO），与计数之和一致
        assertEquals(2, breakdown.values.sum(), "非 NULL 来源计数之和应为 2")
    }

    @Test
    fun getTopLabelsSince_joinsWindowedPlays_andGroupsByLabel() = runTest {
        seed()
        // 窗口内播放：musicId=1(2 次) + musicId=2(1 次)
        // GENRE：ROCK(musicId1, 2 次) > POP(musicId2, 1 次)
        val genre = db.musicLabelDao().getTopLabelsSince(cutoff, LabelCategory.GENRE, 10)
        assertEquals(2, genre.size)
        assertEquals(LabelName.ROCK, genre[0].label)
        assertEquals(2, genre[0].cnt)
        assertEquals(LabelName.POP, genre[1].label)
        assertEquals(1, genre[1].cnt)

        // MOOD 仅 musicId2 命中 → HAPPY:1
        val mood = db.musicLabelDao().getTopLabelsSince(cutoff, LabelCategory.MOOD, 10)
        assertEquals(1, mood.size)
        assertEquals(LabelName.HAPPY, mood[0].label)
        assertEquals(1, mood[0].cnt)
    }

    @Test
    fun getTopLabelsSince_categoryFilter_isIndependent() = runTest {
        seed()
        // GENRE 类别不应混入 MOOD 的 HAPPY
        val genre = db.musicLabelDao().getTopLabelsSince(cutoff, LabelCategory.GENRE, 10)
        assertTrue(genre.none { it.label == LabelName.HAPPY }, "GENRE 过滤不应包含 MOOD 标签")
    }
}
