package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.TypeConverters

// ═══════════════════════════════════════════════════════════════════
// W0 HelloSubAgent DAO
// 两张表：hello_card_cache（推荐卡缓存 + 生成历史 + 记忆协调）
//      + hello_report_narrative（报告叙事段，P5 用）
// ═══════════════════════════════════════════════════════════════════

/**
 * HelloSubAgent 卡片缓存 + 生成历史 + 记忆协调。
 *
 * 一条记录同时承载三个职责：
 * 1. 卡片恢复：App 启动时从 Room 反序列化 cardContentJson 恢复到 CardPool
 * 2. 生成历史：跨天/跨周查询，用于"昨日 RECOMMEND 避开"、"7 天 DISCOVER label 去重"
 * 3. 记忆协调：跨卡片去重（同日同歌手不同卡、同日同 label）
 *
 * 保留全量历史（不自动 purge），Room 存储开销可忽略。
 */
@Entity(tableName = "hello_card_cache")
@TypeConverters(HelloCardConverters::class)
data class HelloCardCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** RECOMMEND / GREETING / DISCOVER / FORGOTTEN / ANNIVERSARY */
    val cardType: String,
    /** SlideContent 的 JSON 序列化（启动恢复用） */
    val cardContentJson: String,
    val generatedAt: Long,
    /** yyyy-MM-dd，告诉 UI 这张卡是为哪一天生成的 */
    val generatedForDate: String,

    // ── 生成元信息（记忆协调 + 统计用） ──
    @ColumnInfo(name = "llm_used")
    val llmUsed: Boolean = false,
    @ColumnInfo(name = "generation_duration_ms")
    val generationDurationMs: Long? = null,
    @ColumnInfo(name = "llm_prompt_tokens")
    val llmPromptTokens: Int? = null,
    @ColumnInfo(name = "llm_response_tokens")
    val llmResponseTokens: Int? = null,

    // ── RECOMMEND 专用 ──
    @ColumnInfo(name = "recommend_song_ids")
    val recommendSongIds: List<String>? = null,
    @ColumnInfo(name = "recommend_artists")
    val recommendArtists: List<String>? = null,
    @ColumnInfo(name = "recommend_labels")
    val recommendLabels: List<String>? = null,

    // ── GREETING 专用 ──
    @ColumnInfo(name = "greeting_type")
    val greetingType: String? = null,
    @ColumnInfo(name = "greeting_mentioned_artists")
    val greetingMentionedArtists: List<String>? = null,

    // ── DISCOVER 专用 ──
    @ColumnInfo(name = "discover_labels")
    val discoverLabels: List<String>? = null,

    // ── FORGOTTEN 专用 ──
    @ColumnInfo(name = "forgotten_artists")
    val forgottenArtists: List<String>? = null,
    @ColumnInfo(name = "forgotten_song_ids")
    val forgottenSongIds: List<String>? = null,

    // ── ANNIVERSARY 专用 ──
    @ColumnInfo(name = "anniversary_artist")
    val anniversaryArtist: String? = null,
    @ColumnInfo(name = "anniversary_subject")
    val anniversarySubject: String? = null,
)

/** Room TypeConverter：List<String> ↔ 逗号分隔字符串 */
class HelloCardConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.joinToString("\u001F")
    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        if (value == null) null else value.split("\u001F").filter { it.isNotEmpty() }
}

@Dao
interface HelloCardCacheDao {
    @Insert
    suspend fun insert(cache: HelloCardCache): Long

    /** 取某类型最新一条（不限日期，启动恢复用） */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = :type ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestOfAnyDate(type: String): HelloCardCache?

    /** 取某类型某天的一条 */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = :type AND generatedForDate = :date ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(type: String, date: String): HelloCardCache?

    /** 某天已生成的卡类型集合（补跑守卫用） */
    @Query("SELECT DISTINCT cardType FROM hello_card_cache WHERE generatedForDate = :date")
    suspend fun getLatestCardTypesByDate(date: String): List<String>

    /** 同类型同日的所有记录（写新卡前删旧的，保留历史但同日只留最新） */
    @Query("DELETE FROM hello_card_cache WHERE cardType = :type AND generatedForDate = :date")
    suspend fun deleteSameDaySameType(type: String, date: String)

    /** 清除同类型全历史（启动恢复前的降级清理，慎用） */
    @Query("DELETE FROM hello_card_cache WHERE cardType = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM hello_card_cache")
    suspend fun deleteAll()

    // ── 记忆协调查询 ──

    /** 今日某类型生成次数（ANNIVERSARY 防重复 LLM call 用） */
    @Query("SELECT COUNT(*) FROM hello_card_cache WHERE cardType = :type AND generatedAt >= :startOfDay")
    suspend fun countToday(type: String, startOfDay: Long): Int

    /** 今日所有记录（按 cardType 分组提取字段在 Kotlin 层做） */
    @Query("SELECT * FROM hello_card_cache WHERE generatedAt >= :startOfDay")
    suspend fun getTodayAllEntities(startOfDay: Long): List<HelloCardCache>

    /** 昨日所有 RECOMMEND 记录 */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = 'RECOMMEND' AND generatedAt >= :yesterdayStart AND generatedAt < :yesterdayEnd")
    suspend fun getYesterdayRecommends(yesterdayStart: Long, yesterdayEnd: Long): List<HelloCardCache>

    /** 7 天内 DISCOVER 记录 */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = 'DISCOVER' AND generatedAt >= :sevenDaysAgo")
    suspend fun getWeeklyDiscoverCards(sevenDaysAgo: Long): List<HelloCardCache>

    /** 7 天内 FORGOTTEN 记录 */
    @Query("SELECT * FROM hello_card_cache WHERE cardType = 'FORGOTTEN' AND generatedAt >= :sevenDaysAgo")
    suspend fun getWeeklyForgottenCards(sevenDaysAgo: Long): List<HelloCardCache>

    /** 生成 token 统计（最近 N 天） */
    @Query("SELECT SUM(llm_prompt_tokens + IFNULL(llm_response_tokens, 0)) FROM hello_card_cache WHERE generatedAt >= :since AND llm_used = 1")
    suspend fun sumTokensSince(since: Long): Long?
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
