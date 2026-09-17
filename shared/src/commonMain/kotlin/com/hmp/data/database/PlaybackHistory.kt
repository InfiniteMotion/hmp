package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val musicId: Long,
    val playedAt: Long,
    val playDuration: Long = 0,
    val isCompleted: Boolean = false,
    val source: String? = null
)

@Dao
interface PlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playback: PlaybackHistory): Long

    @Query("UPDATE PlaybackHistory SET playDuration = :duration, isCompleted = :isCompleted WHERE id = :id")
    suspend fun updatePlaybackRecord(id: Long, duration: Long, isCompleted: Boolean)

    @Query("SELECT * FROM PlaybackHistory ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<PlaybackHistory>

    @Query("SELECT * FROM PlaybackHistory WHERE musicId = :musicId ORDER BY playedAt DESC LIMIT :limit")
    fun getHistoryForMusic(musicId: Long, limit: Int): Flow<List<PlaybackHistory>>

    @Query("DELETE FROM PlaybackHistory")
    suspend fun deleteAll()

    @Query("SELECT * FROM PlaybackHistory")
    suspend fun getAllHistory(): List<PlaybackHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<PlaybackHistory>)

    /** ANNIVERSARY 候选：按 musicId GROUP BY 取首次 playedAt，匹配月日，按 firstPlayedAt 升序（最久在前）。 */
    @Query("""
        SELECT musicId, MIN(playedAt) AS firstPlayedAt
        FROM PlaybackHistory
        WHERE strftime('%m-%d', playedAt / 1000, 'unixepoch', 'localtime') = :mmdd
        GROUP BY musicId
        ORDER BY firstPlayedAt ASC
    """)
    suspend fun getAnniversaryCandidates(mmdd: String): List<AnniversaryCandidateRow>

    /** 某首歌在某一天的播放次数（用于 ANNIVERSARY thatDayPlays）。 */
    @Query("""
        SELECT COUNT(*) FROM PlaybackHistory
        WHERE musicId = :musicId AND playedAt >= :dayStart AND playedAt < :dayEnd
    """)
    suspend fun countPlaysInRange(musicId: Long, dayStart: Long, dayEnd: Long): Int

    /** 某首歌累计播放次数（比 userInfo.playCount 精确，因为 PlaybackHistory 每次都写）。 */
    @Query("SELECT COUNT(*) FROM PlaybackHistory WHERE musicId = :musicId")
    suspend fun getPlayCountForMusic(musicId: Long): Int

    /** 某首歌累计听歌时长（毫秒）。 */
    @Query("SELECT COALESCE(SUM(playDuration), 0) FROM PlaybackHistory WHERE musicId = :musicId")
    suspend fun getTotalDurationForMusic(musicId: Long): Long

    /** 每首歌累计时长 + 播放次数视图（SQL GROUP BY，性能远优于 getAllHistory + Kotlin groupBy）。 */
    @Query("""
        SELECT musicId, COALESCE(SUM(playDuration), 0) AS totalMs, COUNT(*) AS playCount
        FROM PlaybackHistory
        GROUP BY musicId
    """)
    suspend fun getAllMusicDurationsGrouped(): List<MusicDurationDaoRow>

    /**
     * 画像·行为建模：窗口内的播放明细（**带曲目时长**，用于算"跳过点"）。
     *
     * 跳过点 = `playDuration / trackDuration` —— 没有这个 JOIN 就只有绝对秒数，
     * 而"听了 30 秒"在 3 分钟的歌和 8 分钟的歌里含义完全不同。
     */
    @Query("""
        SELECT h.musicId AS musicId, h.playedAt AS playedAt, h.playDuration AS playDuration,
               h.isCompleted AS isCompleted, COALESCE(m.duration, 0) AS trackDuration
        FROM PlaybackHistory h
        LEFT JOIN music m ON m.id = h.musicId
        WHERE h.playedAt >= :sinceMs
        ORDER BY h.playedAt ASC
    """)
    suspend fun getPlayRowsSince(sinceMs: Long): List<PlaybackRow>

    /** 画像·行为建模：每首歌的**首次**播放时间（判断窗口内新歌 vs 老歌回归）。 */
    @Query("SELECT musicId, MIN(playedAt) AS firstPlayedAt FROM PlaybackHistory GROUP BY musicId")
    suspend fun getFirstPlayedAtPerTrack(): List<AnniversaryCandidateRow>

    /** 今日播放过的 musicId（SQL strftime，避免 getAllHistory 全表拉取）。 */
    @Query("""
        SELECT DISTINCT musicId FROM PlaybackHistory
        WHERE strftime('%m-%d', playedAt / 1000, 'unixepoch', 'localtime') = :mmdd
    """)
    suspend fun getMusicIdsPlayedOn(mmdd: String): List<Long>

    /**
     * F9-T1：时段分布——窗口内按小时分桶的播放次数 + 累计时长。
     * 给报告页时段分布 chart 用（UI 层消费：24 小时 × 播放次数柱图）。
     *
     * 只返回「实际有数据」的桶；UI 层补零位。
     * SQLite strftime('%H') 返回 00-23 字符串，Room 直接解析为 Int。
     */
    @Query("""
        SELECT CAST(strftime('%H', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               COUNT(*) AS playCount,
               COALESCE(SUM(playDuration), 0) AS totalMs
        FROM PlaybackHistory
        WHERE playedAt >= :sinceMs
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getHourlyDistribution(sinceMs: Long): List<HourlyBucketRow>

    /** 窗口内总时长（毫秒）。 */
    @Query("""
        SELECT COALESCE(SUM(playDuration), 0)
        FROM PlaybackHistory
        WHERE playedAt >= :cutoff
    """)
    suspend fun getTotalDurationSince(cutoff: Long): Long

    /** 窗口内播放统计（总播放数 / 跳过数 / 完播率）。 */
    @Query("""
        SELECT
          COUNT(*) AS total,
          SUM(CASE WHEN isCompleted THEN 0 ELSE 1 END) AS skipped,
          AVG(CASE WHEN isCompleted THEN 1.0 ELSE 0.0 END) AS completionRate
        FROM PlaybackHistory
        WHERE playedAt >= :cutoff
    """)
    suspend fun getWindowedPlaybackCount(cutoff: Long): WindowedCountRow

    /** 窗口内 Top N 歌曲（LEFT JOIN music 拿歌名歌手）。 */
    @Query("""
        SELECT h.musicId AS musicId, m.title AS title, m.artist AS artist,
               COUNT(*) AS playCnt
        FROM PlaybackHistory h
        LEFT JOIN music m ON m.id = h.musicId
        WHERE h.playedAt >= :cutoff
        GROUP BY h.musicId
        ORDER BY playCnt DESC
        LIMIT :limit
    """)
    suspend fun getTopSongsSince(cutoff: Long, limit: Int): List<TopSongRow>

    /** 窗口内最近 N 条播放（LEFT JOIN music 拿歌名歌手）。 */
    @Query("""
        SELECT h.musicId AS musicId, m.title AS title, m.artist AS artist,
               h.playedAt AS playedAt, h.playDuration AS playDuration,
               h.isCompleted AS isCompleted, h.source AS source
        FROM PlaybackHistory h
        LEFT JOIN music m ON m.id = h.musicId
        WHERE h.playedAt >= :cutoff
        ORDER BY h.playedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentPlaybackSince(cutoff: Long, limit: Int): List<RecentRow>

    /** 窗口内播放来源分布。 */
    @Query("""
        SELECT source, COUNT(*) AS cnt
        FROM PlaybackHistory
        WHERE playedAt >= :cutoff AND source IS NOT NULL
        GROUP BY source
    """)
    suspend fun getSourceBreakdownSince(cutoff: Long): List<SourceBreakdownRow>
}

/** Room 查询结果行：每小时分桶的播放次数 + 累计时长。 */
data class HourlyBucketRow(
    val hour: Int,      // 0-23
    val playCount: Int,
    val totalMs: Long,
)

/** Room 查询结果：DAO GROUP BY 聚合每首歌的累计时长 + 播放次数。 */
data class MusicDurationDaoRow(
    val musicId: Long,
    val totalMs: Long,
    val playCount: Int,
)

/** Room 查询结果行：画像行为建模用的播放明细（含曲目时长）。 */
data class PlaybackRow(
    val musicId: Long,
    val playedAt: Long,
    val playDuration: Long,
    val isCompleted: Boolean,
    val trackDuration: Long,
)

/** Room 查询结果中间行：ANNIVERSARY 候选。 */
data class AnniversaryCandidateRow(
    val musicId: Long,
    val firstPlayedAt: Long,
)

/** 窗口内播放统计（一次 SQL 返回总播放数 + 跳过数 + 完播率）。 */
data class WindowedCountRow(
    val total: Int,
    val skipped: Int,
    val completionRate: Double,
)

/** 窗口 Top 歌曲（带歌名歌手）。 */
data class TopSongRow(
    val musicId: Long,
    val title: String,
    val artist: String,
    val playCnt: Int,
)

/** 窗口内最近播放（带歌名歌手 + 来源）。 */
data class RecentRow(
    val musicId: Long,
    val title: String,
    val artist: String,
    val playedAt: Long,
    val playDuration: Long,
    val isCompleted: Boolean,
    val source: String?,
)

/** 窗口内播放来源分布行。 */
data class SourceBreakdownRow(
    val source: String,
    val cnt: Int,
)
