package com.hmp.domain.agent.quota

import com.hmp.data.database.TokenLedgerDao
import com.hmp.domain.agent.port.TimeProvider
import com.hmp.domain.agent.runtime.TokenSnapshot
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.platform.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 配额分桶：账本里 5 个 agent_id 归并成 4 个桶（profile 是 Master 发起的画像任务，记进 master）。 */
enum class QuotaBucket(val bucketId: String) {
    MASTER("master"),
    RADIO("radio"),
    ENRICH("enrich"),
    HELLO("hello");

    companion object {
        fun ofLedgerAgent(agentId: String): QuotaBucket? = when (agentId) {
            "master", "profile" -> MASTER
            "radio" -> RADIO
            "enrich" -> ENRICH
            "hello" -> HELLO
            else -> null
        }
    }
}

/** 一次准入判定的结果。拒绝是**结构化返回值**，不抛异常 —— 调用方要能安静降级。 */
sealed interface QuotaDecision {
    data object Allowed : QuotaDecision

    data class Denied(
        val scope: Scope,
        val bucket: QuotaBucket?,
        val used: Long,
        val cap: Long,
    ) : QuotaDecision {
        enum class Scope {
            /** 总池用完（免费模式）。 */
            TOTAL,

            /** 这个 Agent 的分桶用完。 */
            BUCKET,
        }
    }
}

/** 给 UI 看的一份快照（不含写入能力）。 */
data class QuotaSnapshot(
    val mode: AiAccessMode,
    val totalCap: Long,
    val totalUsed: Long,
    val bucketCaps: Map<QuotaBucket, Long>,
    val bucketUsed: Map<QuotaBucket, Long>,
)

/**
 * Token 配额闸门（免费体验 / 自定义限额共用）。
 *
 * 口径（用户 2026-10-28 定）：
 * - **免费**：每台设备一次性 **100,000,000 token**，四个 Agent 按 **40 / 25 / 25 / 10** 分桶；
 *   **硬顶、不互相挪用** —— 任一分桶用完停该 Agent，总池用完四者全停。
 * - **自定义**：用户开关是否限额；开启后可选全局每日上限 + 每个 Agent 独立每日上限，空 = 不限。
 * - `PAID` 是预留模式，不设限。
 *
 * 状态来源是**账本**（`token_ledger.access_mode`），内存里只做增量缓存：
 * 启动/跨日时用 `SUM` 回填，进程重启不会把已用额度清零（旧 `GlobalTokenCounter` 的毛病）。
 */
class TokenQuotaGate(
    private val ledgerDao: TokenLedgerDao?,
    private val settingsRepository: SettingsRepository?,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()

    private var seededMode: AiAccessMode? = null
    private var seededDayStart: Long = -1L
    private var totalUsed: Long = 0L
    private val usedByBucket: MutableMap<QuotaBucket, Long> = mutableMapOf()
    private var bucketCaps: Map<QuotaBucket, Long> = FREE_BUCKET_CAPS
    private var totalCap: Long = FREE_TOTAL_TOKENS
    private var customLimitsEnabled: Boolean = false

    /** 看板订阅的快照（每次 seed / record 后刷新）。 */
    private val _usage = MutableStateFlow(TokenSnapshot(used = 0L, quota = FREE_TOTAL_TOKENS))
    val usage: StateFlow<TokenSnapshot> = _usage.asStateFlow()

    /** 供非 suspend 的停止信号读取：哪些桶已经用尽（每次 seed / record / check 后重算）。 */
    @Volatile
    private var exhaustedBuckets: Set<QuotaBucket> = emptySet()

    /** UI 订阅：当前已耗尽的桶（为空 = 都还能用）。 */
    private val _exhausted = MutableStateFlow<Set<QuotaBucket>>(emptySet())
    val exhausted: StateFlow<Set<QuotaBucket>> = _exhausted.asStateFlow()

    /** UI 订阅：各桶已用（看板四桶明细）。 */
    private val _bucketUsage = MutableStateFlow<Map<QuotaBucket, Long>>(emptyMap())
    val bucketUsage: StateFlow<Map<QuotaBucket, Long>> = _bucketUsage.asStateFlow()

    /** UI 订阅：当前凭据模式（免费总额 / 自定义日限额 / 预留）。 */
    private val _currentMode = MutableStateFlow(AiAccessMode.FREE)
    val currentMode: StateFlow<AiAccessMode> = _currentMode.asStateFlow()

    /** 准入判定。调用方在真正发 LLM 请求**之前**调用。 */
    suspend fun check(agentId: String): QuotaDecision = mutex.withLock {
        val mode = currentMode().also { _currentMode.value = it }
        ensureSeededLocked(mode)
        decideLocked(mode, agentId)
    }

    /** 记账。`TokenMeter` 落库后回调；tokens 为 prompt + completion。 */
    suspend fun record(agentId: String, tokens: Long) = mutex.withLock {
        if (tokens <= 0L) return@withLock
        totalUsed += tokens
        QuotaBucket.ofLedgerAgent(agentId)?.let { bucket ->
            usedByBucket[bucket] = (usedByBucket[bucket] ?: 0L) + tokens
        }
        refreshDerived()
    }

    /** 非 suspend：这个 Agent 的桶是否已经用尽（停止信号 / 调度器用）。 */
    fun isBucketExhausted(agentId: String): Boolean {
        val bucket = QuotaBucket.ofLedgerAgent(agentId) ?: return false
        return bucket in exhaustedBuckets
    }

    /** UI 快照。免费模式展示"总池 + 四桶"，自定义模式展示用户设定的上限。 */
    suspend fun snapshot(): QuotaSnapshot = mutex.withLock {
        val mode = currentMode()
        ensureSeededLocked(mode)
        QuotaSnapshot(
            mode = mode,
            totalCap = totalCap,
            totalUsed = totalUsed,
            bucketCaps = bucketCaps,
            bucketUsed = usedByBucket.toMap(),
        )
    }

    private suspend fun currentMode(): AiAccessMode =
        runCatching { settingsRepository?.getAiAccessMode() }.getOrNull() ?: AiAccessMode.FREE

    private suspend fun ensureSeededLocked(mode: AiAccessMode) {
        val dayStart = timeProvider().let { it - it % DAY_MS }
        val needsReseed = when (mode) {
            AiAccessMode.FREE -> seededMode != AiAccessMode.FREE
            AiAccessMode.CUSTOM -> seededMode != AiAccessMode.CUSTOM || seededDayStart != dayStart
            AiAccessMode.PAID -> seededMode != AiAccessMode.PAID
        }
        if (!needsReseed) return

        val used = when (mode) {
            AiAccessMode.FREE -> loadUsed(sinceMs = 0L, mode = MODE_FREE)
            AiAccessMode.CUSTOM -> loadUsed(sinceMs = dayStart, mode = MODE_CUSTOM)
            AiAccessMode.PAID -> emptyMap()
        }
        totalUsed = used.values.sum()
        usedByBucket.clear()
        used.forEach { (bucket, value) -> usedByBucket[bucket] = value }

        val config = runCatching { settingsRepository?.getGlobalAgentConfig() }.getOrNull()
        when (mode) {
            AiAccessMode.FREE -> {
                totalCap = FREE_TOTAL_TOKENS
                bucketCaps = FREE_BUCKET_CAPS
                customLimitsEnabled = false
            }
            AiAccessMode.CUSTOM -> {
                customLimitsEnabled = config?.tokenLimitsEnabled == true
                totalCap = (config?.dailyTokenQuota ?: 0).toLong()
                bucketCaps = config?.perAgentDailyTokenQuota
                    ?.mapNotNull { (key, cap) -> QuotaBucket.ofLedgerAgent(key)?.let { it to cap } }
                    ?.toMap()
                    .orEmpty()
            }
            AiAccessMode.PAID -> {
                totalCap = 0L
                bucketCaps = emptyMap()
                customLimitsEnabled = false
            }
        }
        seededMode = mode
        seededDayStart = dayStart
        refreshDerived()
    }

    private fun refreshDerived() {
        _usage.value = TokenSnapshot(used = totalUsed, quota = totalCap)
        exhaustedBuckets = when {
            totalCap > 0L && totalUsed >= totalCap -> QuotaBucket.entries.toSet()
            else -> bucketCaps.filter { (bucket, cap) -> cap > 0L && (usedByBucket[bucket] ?: 0L) >= cap }.keys
        }
        _exhausted.value = exhaustedBuckets
        _bucketUsage.value = usedByBucket.toMap()
    }

    private suspend fun loadUsed(sinceMs: Long, mode: String): Map<QuotaBucket, Long> {
        val dao = ledgerDao ?: return emptyMap()
        val rows = runCatching { dao.sumByAgentForMode(sinceMs = sinceMs, mode = mode) }.getOrNull()
            ?: return emptyMap()
        val result = mutableMapOf<QuotaBucket, Long>()
        rows.forEach { row ->
            val bucket = QuotaBucket.ofLedgerAgent(row.label) ?: return@forEach
            result[bucket] = (result[bucket] ?: 0L) + row.totalTokens
        }
        return result
    }

    private fun decideLocked(mode: AiAccessMode, agentId: String): QuotaDecision {
        val bucket = QuotaBucket.ofLedgerAgent(agentId) ?: return QuotaDecision.Allowed
        return when (mode) {
            AiAccessMode.FREE -> {
                if (totalUsed >= totalCap) {
                    QuotaDecision.Denied(QuotaDecision.Denied.Scope.TOTAL, bucket, totalUsed, totalCap)
                } else {
                    val cap = bucketCaps[bucket] ?: return QuotaDecision.Allowed
                    val used = usedByBucket[bucket] ?: 0L
                    if (used >= cap) {
                        QuotaDecision.Denied(QuotaDecision.Denied.Scope.BUCKET, bucket, used, cap)
                    } else {
                        QuotaDecision.Allowed
                    }
                }
            }
            AiAccessMode.CUSTOM -> {
                if (!customLimitsEnabled) return QuotaDecision.Allowed
                if (totalCap > 0L && totalUsed >= totalCap) {
                    return QuotaDecision.Denied(QuotaDecision.Denied.Scope.TOTAL, bucket, totalUsed, totalCap)
                }
                val cap = bucketCaps[bucket] ?: return QuotaDecision.Allowed
                val used = usedByBucket[bucket] ?: 0L
                if (used >= cap) {
                    QuotaDecision.Denied(QuotaDecision.Denied.Scope.BUCKET, bucket, used, cap)
                } else {
                    QuotaDecision.Allowed
                }
            }
            AiAccessMode.PAID -> QuotaDecision.Allowed
        }
    }

    companion object {
        /** 免费体验总额（用户 2026-10-28 定：100M token，一次性、用完即止）。 */
        const val FREE_TOTAL_TOKENS: Long = 100_000_000L

        /** 四桶比例：Master 40% / Radio 25% / Enrich 25% / Hello 10%（硬顶，不互相挪用）。 */
        val FREE_BUCKET_CAPS: Map<QuotaBucket, Long> = mapOf(
            QuotaBucket.MASTER to 40_000_000L,
            QuotaBucket.RADIO to 25_000_000L,
            QuotaBucket.ENRICH to 25_000_000L,
            QuotaBucket.HELLO to 10_000_000L,
        )

        const val MODE_FREE: String = "FREE"
        const val MODE_CUSTOM: String = "CUSTOM"

        private const val DAY_MS: Long = 86_400_000L
    }
}
