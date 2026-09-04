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

    /** 今日播放过的 musicId（SQL strftime，避免 getAllHistory 全表拉取）。 */
    @Query("""
        SELECT DISTINCT musicId FROM PlaybackHistory
        WHERE strftime('%m-%d', playedAt / 1000, 'unixepoch', 'localtime') = :mmdd
    """)
    suspend fun getMusicIdsPlayedOn(mmdd: String): List<Long>
}

/** Room 查询结果：DAO GROUP BY 聚合每首歌的累计时长 + 播放次数。 */
data class MusicDurationDaoRow(
    val musicId: Long,
    val totalMs: Long,
    val playCount: Int,
)

/** Room 查询结果中间行：ANNIVERSARY 候选。 */
data class AnniversaryCandidateRow(
    val musicId: Long,
    val firstPlayedAt: Long,
)
