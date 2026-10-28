package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


@Entity(tableName = "listeningDuration")
data class ListeningDuration(
    @PrimaryKey
    val date: String,
    val duration: Long,
    val updatedAt: Long
)

@Dao
interface ListeningDurationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(duration: ListeningDuration)

    @Query("SELECT * FROM listeningDuration ORDER BY date DESC LIMIT :limit")
    fun getRecentDurations(limit: Int): Flow<List<ListeningDuration>>

    @Query("SELECT * FROM listeningDuration WHERE date = :date")
    suspend fun getDurationByDate(date: String): ListeningDuration?

    @Query("UPDATE listeningDuration SET duration = duration + :additionalDuration, updatedAt = :updateTime WHERE date = :date")
    suspend fun updateDuration(date: String, additionalDuration: Long, updateTime: Long)

    @Query("SELECT * FROM listeningDuration")
    suspend fun getAllDurations(): List<ListeningDuration>

    /**
     * 窗口内的日累计（D5-03）。`date` 是 `yyyy-MM-dd` 文本主键，字典序即时间序，
     * 所以 `>= :fromDate` 就是"从该日起"，不需要额外排序或换算。
     */
    @Query("SELECT * FROM listeningDuration WHERE date >= :fromDate ORDER BY date ASC")
    suspend fun getDurationsSince(fromDate: String): List<ListeningDuration>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(durations: List<ListeningDuration>)

    @Query("DELETE FROM listeningDuration")
    suspend fun deleteAll()

    /** 整表替换（备份恢复）：只 REPLACE 会把快照里没有的日期留在库里，等于恢复不完全。 */
    @Transaction
    suspend fun replaceAll(items: List<ListeningDuration>) {
        deleteAll()
        insertAll(items)
    }
}
