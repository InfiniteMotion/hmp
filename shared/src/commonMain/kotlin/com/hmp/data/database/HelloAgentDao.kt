package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

// ═══════════════════════════════════════════════════════════════════
// W0 HelloSubAgent DAO
// 两张表：hello_card_cache（推荐卡缓存）+ hello_report_narrative（报告叙事段）
// ═══════════════════════════════════════════════════════════════════

/**
 * HelloSubAgent 推荐卡缓存（W0）。
 *
 * 存 RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY 四种卡的 JSON 序列化内容。
 * - 同类型只保留最新一条（UI 取 getLatestOfAnyDate）
 * - 启动时从 DAO 恢复（SharedFlow 丢事件时的主动补偿）
 * - 每日刷新时写入新卡（供 P5 报告页 + 下次启动快速恢复）
 */
@Entity(tableName = "hello_card_cache")
data class HelloCardCache(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** RECOMMEND / DISCOVER / FORGOTTEN / ANNIVERSARY */
    val cardType: String,
    /** SlideContent 的 JSON 序列化（kotlinx.serialization） */
    val cardContentJson: String,
    val generatedAt: Long,
    /** yyyy-MM-dd，告诉 UI 这张卡是为哪一天生成的 */
    val generatedForDate: String,
)

@Dao
interface HelloCardCacheDao {
    @Insert
    suspend fun insert(cache: HelloCardCache): Long

    /** 取某类型最新一条（不限日期，启动恢复用） */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = :type ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestOfAnyDate(type: String): HelloCardCache?

    /** 取某类型某天的一条 */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = :type AND generatedForDate = :date LIMIT 1")
    suspend fun getLatest(type: String, date: String): HelloCardCache?

    /** 某天已生成的卡类型集合（补跑守卫用） */
    @Query("SELECT DISTINCT cardType FROM hello_card_cache WHERE generatedForDate = :date")
    suspend fun getLatestCardTypesByDate(date: String): List<String>

    @Query("DELETE FROM hello_card_cache WHERE cardType = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM hello_card_cache")
    suspend fun deleteAll()
}

/**
 * HelloSubAgent 报告叙事段（W0，供 P5 收听报告页）。
 *
 * 自适应频率：日均听歌时长 ≤30min → 周更新；30min~2h → 日更新；≥2h → 日更新。
 * - 每次生成写入 DAO，带 timeRange + avgDailyMinutes（当时的日均，用于下次自适应判断）
 * - P5 报告页直接读 DAO 显示，不阻塞 UI
 * - DAO 过期（超过生成频率 × 2）时 → 后台异步触发重新生成
 */
@Entity(tableName = "hello_report_narrative")
data class HelloReportNarrativeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** ALL / DAY / WEEK / MONTH / YEAR */
    val timeRange: String,
    /** 叙事段纯文本（后续可扩展为富文本 JSON） */
    val narrative: String,
    val generatedAt: Long,
    /** 生成当时的日均听歌时长（分钟），用于下次自适应判断 */
    @ColumnInfo(name = "avg_daily_minutes")
    val avgDailyMinutes: Float?,
)

@Dao
interface HelloReportNarrativeDao {
    @Insert
    suspend fun insert(narrative: HelloReportNarrativeEntity): Long

    @Query("SELECT * FROM hello_report_narrative WHERE timeRange = :range ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(range: String): HelloReportNarrativeEntity?

    @Query("DELETE FROM hello_report_narrative WHERE timeRange = :range")
    suspend fun deleteByRange(range: String)

    @Query("DELETE FROM hello_report_narrative")
    suspend fun deleteAll()
}
