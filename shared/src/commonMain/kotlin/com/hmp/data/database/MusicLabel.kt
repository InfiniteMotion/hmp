package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hmp.data.database.myenum.LabelCategory
import com.hmp.data.database.myenum.LabelName
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "musicLabel",
    primaryKeys = ["musicId", "label"]
)
data class MusicLabel(
    val musicId: Long,
    val type: LabelCategory,
    val label: LabelName,
    /** 认识来源：LLM（模型富化）/ USER（用户修正，永不被模型覆盖）/ AGENT（agent 主动写入）。v1 存量行为 null，按 LLM 旧认识处理。 */
    val source: String? = null,
    /** 可信度 0-1：行为确证/证伪动态调整（设计总纲 3.2 规则 ②） */
    val confidence: Double? = null,
    /** 认识建立时间（审计四问·何时建立） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long? = null,
    /** 最近修正/确证时间（审计四问·被确证过吗） */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,
)


@Dao
interface MusicLabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: MusicLabel)

    @Query("SELECT * FROM musicLabel WHERE musicId = :musicId")
    suspend fun getLabelsById(musicId: Long): List<MusicLabel>

    @Query("""
    SELECT label
    FROM musicLabel
    WHERE type = :type
    GROUP BY label
    ORDER BY COUNT(*) DESC
""")
    fun getLabelsByType(type: LabelCategory): Flow<List<LabelName>>

    @Query("""
    SELECT musicId
    FROM musicLabel
    WHERE label = :label
""")
    suspend fun getMusicIdListByType(label: LabelName): List<Long>

    @Query("SELECT * FROM musicLabel")
    suspend fun getAllLabels(): List<MusicLabel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(labels: List<MusicLabel>)
}
