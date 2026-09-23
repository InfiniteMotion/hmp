package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * F9-T1 遗忘唤醒送达标记——极简表。
 *
 * 每条记录 = 一首被推送过"遗忘唤醒"卡的歌曲；
 * 后续 getForgottenTracks 查询时排除它（默认 30 天冷却期）。
 * 字段：(musicId PK, deliveredAt) —— 足够了，没有其他状态要跟踪。
 */
@Entity(tableName = "forgotten_delivery")
data class ForgottenDeliveryEntity(
    @PrimaryKey
    val musicId: Long,
    val deliveredAt: Long,
)

@Dao
interface ForgottenDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markDelivered(entity: ForgottenDeliveryEntity)

    @Query("SELECT COUNT(*) FROM forgotten_delivery WHERE musicId = :musicId")
    suspend fun exists(musicId: Long): Int

    /** 清掉 30 天前的送达标记（冷却期过后允许再次推送）。 */
    @Query("DELETE FROM forgotten_delivery WHERE deliveredAt < :cutoffMs")
    suspend fun cleanupExpired(cutoffMs: Long)
}
