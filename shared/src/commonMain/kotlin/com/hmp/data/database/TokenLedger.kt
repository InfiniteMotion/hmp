package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Token 明细账本（F12-T1）。
 *
 * **一次 LLM 调用一行** —— 而不是一个累加数。理由（`design/agent-token.md` §3.1 D7）：
 * **明细是累计的超集**（累计 = `GROUP BY`），反过来不成立；只存累计是**不可逆降级**。
 * 项目还存在"只有分时能回答"的问题（判断 Enrich 是否趁夜间跑了一整库），
 * 而日配额本身就按天滚动（`GlobalTokenCounter.rollDayIfNeeded`）——时间是原生维度。
 *
 * 体积：~150 B/行 × 常态 ~300 行/天 ≈ **16 MB/年** → 判为可承受，故**默认永久保留、不做自动清理**；
 * 用户可在 Agent 看板手动清空（`TokenLedgerMaintenance.clearAll()`，D6-05）。
 *
 * ⚠️ **隐私边界**：只存 `endpoint_host`，**不存完整 URL、绝不存 apiKey**（完整 URL 可能含路径令牌）。
 * ⚠️ **它不是记忆**：本表**不喂给任何 LLM、不进 `UserMemory`** —— 与 F11「电台不落盘」的裁定不冲突，
 *    那条管的是"喂给模型的状态"（会变成不可审计的影子记忆），这条是"给用户看的本地诊断数据"。
 */
@Entity(
    tableName = "token_ledger",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["agent_id"]),
    ],
)
data class TokenLedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** 哪个 Agent 花的：master / radio / enrich / hello / profile */
    @ColumnInfo(name = "agent_id")
    val agentId: String,
    /** 端点主机名（如 `api.deepseek.com`）——**只存 host** */
    @ColumnInfo(name = "endpoint_host")
    val endpointHost: String,
    val model: String,
    @ColumnInfo(name = "prompt_tokens")
    val promptTokens: Int,
    @ColumnInfo(name = "completion_tokens")
    val completionTokens: Int,
    @ColumnInfo(name = "cached_tokens")
    val cachedTokens: Int,
    /** true = 端点返回的真实 usage；false = 估算兜底。**必须在图上可区分**，否则又回到"用假数字骗自己"。 */
    val measured: Boolean,
    /**
     * 这次调用花的是哪套凭据：`FREE`（内置 Key）/ `CUSTOM`（用户自填）/ `PAID`（预留）。
     *
     * 配额分账的唯一依据 —— 只看 `endpoint_host` 不可靠（用户完全可能把自定义端点配成同一个 host）。
     * 由 [com.hmp.domain.agent.runtime.TokenMeter] 记账时写入；存量行为默认 `FREE`（v10 内新增，未发布）。
     */
    @ColumnInfo(name = "access_mode", defaultValue = "FREE")
    val accessMode: String = "FREE",
    @ColumnInfo(name = "task_id")
    val taskId: String? = null,
)

/** 分账聚合行（按 Agent / 按端点·模型）。字段名与查询别名一一对应。 */
data class TokenAggregateRow(
    /** 分组标签：Agent id，或 `host / model` */
    val label: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    /** 该组中 measured=false（估算）的调用条数 */
    val estimatedRows: Long,
    /** 该组调用条数 */
    val calls: Long,
)

/** 分时聚合桶（小时 / 天）。 */
data class TokenTimeBucket(
    /** 桶起点（epoch ms，已按小时或天对齐） */
    val bucketStart: Long,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
)

@Dao
interface TokenLedgerDao {
    @Insert
    suspend fun insert(entry: TokenLedgerEntry): Long

    @Query("SELECT COUNT(*) FROM token_ledger")
    suspend fun count(): Long

    @Query("SELECT MIN(created_at) FROM token_ledger")
    suspend fun earliestMs(): Long?

    /** 分账：**按 Agent** 聚合。 */
    @Query(
        "SELECT agent_id AS label, " +
            "SUM(prompt_tokens) AS promptTokens, " +
            "SUM(completion_tokens) AS completionTokens, " +
            "SUM(prompt_tokens + completion_tokens) AS totalTokens, " +
            "SUM(CASE WHEN measured = 0 THEN 1 ELSE 0 END) AS estimatedRows, " +
            "COUNT(*) AS calls " +
            "FROM token_ledger WHERE created_at >= :sinceMs " +
            "GROUP BY agent_id ORDER BY totalTokens DESC"
    )
    suspend fun sumByAgent(sinceMs: Long): List<TokenAggregateRow>

    /**
     * 配额分账：**按 Agent 聚合某个凭据模式**的用量。
     *
     * 免费额度要的是"这台设备总共花了多少"（`sinceMs = 0` + `mode = FREE`）；
     * 自定义限额要的是"今天花了多少"（`sinceMs = 当天起点` + `mode = CUSTOM`）。
     */
    @Query(
        "SELECT agent_id AS label, " +
            "SUM(prompt_tokens) AS promptTokens, " +
            "SUM(completion_tokens) AS completionTokens, " +
            "SUM(prompt_tokens + completion_tokens) AS totalTokens, " +
            "SUM(CASE WHEN measured = 0 THEN 1 ELSE 0 END) AS estimatedRows, " +
            "COUNT(*) AS calls " +
            "FROM token_ledger WHERE access_mode = :mode AND created_at >= :sinceMs " +
            "GROUP BY agent_id ORDER BY totalTokens DESC"
    )
    suspend fun sumByAgentForMode(sinceMs: Long, mode: String): List<TokenAggregateRow>

    /** 分账：**按端点 + 模型**聚合（换端点后会出现两行）。 */
    @Query(
        "SELECT endpoint_host || ' / ' || model AS label, " +
            "SUM(prompt_tokens) AS promptTokens, " +
            "SUM(completion_tokens) AS completionTokens, " +
            "SUM(prompt_tokens + completion_tokens) AS totalTokens, " +
            "SUM(CASE WHEN measured = 0 THEN 1 ELSE 0 END) AS estimatedRows, " +
            "COUNT(*) AS calls " +
            "FROM token_ledger WHERE created_at >= :sinceMs " +
            "GROUP BY endpoint_host, model ORDER BY totalTokens DESC"
    )
    suspend fun sumByEndpoint(sinceMs: Long): List<TokenAggregateRow>

    /** 分时：**小时桶**（日内节律）。 */
    @Query(
        "SELECT (created_at / 3600000) * 3600000 AS bucketStart, " +
            "SUM(prompt_tokens) AS promptTokens, " +
            "SUM(completion_tokens) AS completionTokens, " +
            "SUM(prompt_tokens + completion_tokens) AS totalTokens " +
            "FROM token_ledger WHERE created_at >= :sinceMs " +
            "GROUP BY bucketStart ORDER BY bucketStart ASC"
    )
    suspend fun sumByHour(sinceMs: Long): List<TokenTimeBucket>

    /** 分时：**天桶**（长期趋势）。 */
    @Query(
        "SELECT (created_at / 86400000) * 86400000 AS bucketStart, " +
            "SUM(prompt_tokens) AS promptTokens, " +
            "SUM(completion_tokens) AS completionTokens, " +
            "SUM(prompt_tokens + completion_tokens) AS totalTokens " +
            "FROM token_ledger WHERE created_at >= :sinceMs " +
            "GROUP BY bucketStart ORDER BY bucketStart ASC"
    )
    suspend fun sumByDay(sinceMs: Long): List<TokenTimeBucket>

    /**
     * 分时：**按 Agent 的小时桶**（用于定位"某小时 Enrich 突增"）。
     * 返回 label = agent_id 的逐桶明细。
     */
    @Query(
        "SELECT (created_at / 3600000) * 3600000 AS bucketStart, " +
            "SUM(prompt_tokens) AS promptTokens, " +
            "SUM(completion_tokens) AS completionTokens, " +
            "SUM(prompt_tokens + completion_tokens) AS totalTokens " +
            "FROM token_ledger WHERE created_at >= :sinceMs AND agent_id = :agentId " +
            "GROUP BY bucketStart ORDER BY bucketStart ASC"
    )
    suspend fun sumByHourForAgent(sinceMs: Long, agentId: String): List<TokenTimeBucket>

    /**
     * 全局累计总用量（所有历史，不分窗口）。
     *
     * ⚠️ 生产取数不要再直接用它：监控页已改为 [sumTotalSince] 的 30 天窗口（D6-05）。
     * 保留它只为诊断/测试对账，避免"全历史 SUM"再次回到 UI。
     */
    @Query("SELECT COALESCE(SUM(prompt_tokens + completion_tokens), 0) FROM token_ledger")
    suspend fun sumTotal(): Long

    /**
     * 窗口内总用量。
     *
     * 监控页从「全历史」改为「近 30 天」后走这条（`D6-05`）——
     * 裸 [sumTotal] 在库变大后是每次全表扫，且与同页的分账窗口口径不一致。
     */
    @Query("SELECT COALESCE(SUM(prompt_tokens + completion_tokens), 0) FROM token_ledger WHERE created_at >= :sinceMs")
    suspend fun sumTotalSince(sinceMs: Long): Long

    @Query("DELETE FROM token_ledger")
    suspend fun deleteAll()
}
