package com.hmp.domain.agent.health

import com.hmp.domain.agent.quota.QuotaBucket
import com.hmp.domain.agent.quota.QuotaSnapshot
import com.hmp.domain.agent.quota.TokenQuotaGate
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Agent 体系的健康自检（2026-11-11）。
 *
 * 定位：**不阻止 Master 创建**（UI 到处直接注入它），只回答「现在能不能干活」。
 * `MasterAgent.initialize()` 不再硬编码「按覆盖率建富化 / 无条件启 Hello / 冷启画像」，
 * 而是先过这里；不通过就等状态变化后重检（见 [AgentHealthGate]）。
 */
data class AgentHealth(
    val llm: LlmHealth,
    val library: LibraryHealth,
    val memoryEnabled: Boolean,
    /** 用户是否在设置里启用了富化（并入同一份检查，避免入口另判一套）。 */
    val enrichEnabled: Boolean = true,
    val checkedAt: Long,
    /**
     * 已耗尽的分桶名（master / radio / enrich / hello）。
     * 与运行时 `StopSignal.shouldSoftStop()` 读的是 TokenQuotaGate 同一份
     * `exhaustedBuckets` —— 门控与子 Agent 不再各算一遍额度。
     */
    val exhaustedBuckets: Set<String> = emptySet(),
) {
    /** 配置可用且额度未耗尽（不含分桶判定）。 */
    val canUseLlm: Boolean get() = llm is LlmHealth.Ready

    /**
     * 只有「明确空库」才拦；读不到曲库（未接线 / 查询失败）按放行处理，
     * 与 LLM 侧「读失败返回 Ready」的保守约定一致 —— 测试与旧装配不该被误拦。
     */
    private val libraryBlocks: Boolean get() = library is LibraryHealth.Empty

    /** 主对话 / 画像走 master 桶。 */
    val canChat: Boolean get() = canUseLlm && "master" !in exhaustedBuckets

    /** 电台走 radio 桶。 */
    val canRadio: Boolean get() = canUseLlm && "radio" !in exhaustedBuckets

    /** Hello 走 hello 桶：没模型、明确空库、或自己的桶用完都不启（卡片由 UI 从缓存读）。 */
    val canHello: Boolean get() = canUseLlm && !libraryBlocks && "hello" !in exhaustedBuckets

    /** 富化走 enrich 桶：LLM + 有歌 + 用户开关开 + 自己的桶没用完。 */
    val canEnrich: Boolean get() = canUseLlm && !libraryBlocks && enrichEnabled && "enrich" !in exhaustedBuckets

    /** 一行摘要，供日志与 UI 直接展示。 */
    fun summary(): String = "llm=$llm, library=$library, memory=$memoryEnabled, enrichEnabled=$enrichEnabled, exhausted=$exhaustedBuckets"
}

sealed interface LlmHealth {
    /** 配置可用且额度未耗尽。 */
    data object Ready : LlmHealth

    /** 没有可用配置：没填 Key、内置 Key 缺失、或密文解不开。 */
    data class NotConfigured(val reason: String) : LlmHealth

    /** 配置可用，但配额已耗尽（免费额度用完 / 自定义限额到顶）。 */
    data object QuotaExhausted : LlmHealth
}

sealed interface LibraryHealth {
    data class Ready(val totalSongs: Int) : LibraryHealth

    data object Empty : LibraryHealth

    data class Unavailable(val reason: String) : LibraryHealth
}

/**
 * 健康检查器 —— 纯查询，无状态、无副作用（不启动任何服务）。
 *
 * 依赖全部可空：测试与旧装配不接线时按「无法判断 → 放行」处理，而不是崩、也不是误拦。
 */
class AgentHealthChecker(
    private val settingsRepository: SettingsRepository?,
    private val quotaGate: TokenQuotaGate?,
    private val musicRepository: MusicRepository?,
    private val timeProvider: () -> Long,
    /** 富化开关由 Master 提供（策略配置不在 settingsRepository 的查询契约里）。 */
    private val enrichEnabledProvider: () -> Boolean = { true },
) {

    suspend fun check(): AgentHealth {
        // snapshot() 内部 ensureSeededLocked 会刷新 exhaustedBuckets；取一次同时
        // 供总池判定与四个分桶判定，避免门控与运行时读到不同时刻的数据。
        val snapshot = quotaGate?.let { runCatching { it.snapshot() }.getOrNull() }
        return AgentHealth(
            llm = checkLlm(snapshot),
            library = checkLibrary(),
            memoryEnabled = checkMemoryEnabled(),
            enrichEnabled = enrichEnabledProvider(),
            checkedAt = timeProvider(),
            exhaustedBuckets = checkBuckets(snapshot),
        )
    }

    private suspend fun checkLlm(snapshot: QuotaSnapshot?): LlmHealth {
        // 未接线 / 快照读失败（测试、旧装配）无法判断 → 放行。
        val repo = settingsRepository ?: return LlmHealth.Ready
        val config = runCatching { repo.getActiveAiConfig() }.getOrNull()
            ?: return LlmHealth.NotConfigured("没有可用的 AI 配置")
        if (!config.isConfigured) {
            return LlmHealth.NotConfigured("AI 配置不完整（缺端点或密钥）")
        }
        if (snapshot != null && snapshot.totalCap > 0L && snapshot.totalUsed >= snapshot.totalCap) {
            return LlmHealth.QuotaExhausted
        }
        return LlmHealth.Ready
    }

    /** 直接问闸门本身，与三个 SubAgent 的 shouldSoftStop() 同源，不另算一遍。 */
    private fun checkBuckets(snapshot: QuotaSnapshot?): Set<String> {
        val gate = quotaGate ?: return emptySet()
        if (snapshot == null) return emptySet()
        return QuotaBucket.entries
            .filter { gate.isBucketExhausted(it.bucketId) }
            .map { it.bucketId }
            .toSet()
    }

    private suspend fun checkLibrary(): LibraryHealth {
        val repo = musicRepository ?: return LibraryHealth.Unavailable("曲库未接线")
        val health = runCatching { repo.getEnrichHealth() }.getOrNull()
            ?: return LibraryHealth.Unavailable("曲库健康度读取失败")
        return if (health.totalSongCount == 0) {
            LibraryHealth.Empty
        } else {
            LibraryHealth.Ready(health.totalSongCount)
        }
    }

    private suspend fun checkMemoryEnabled(): Boolean {
        val repo = settingsRepository ?: return false
        return runCatching { repo.getGlobalAgentConfig().memoryEnabled }.getOrDefault(false)
    }
}

/**
 * 健康门控 —— 检查一次、把结果交给回调；并发调用串行化，保证「后到的检查看到最新状态」。
 *
 * 重检由 `MasterAgent` 在状态变化点显式触发（AI 配置变更 / 曲库变更），
 * 不做轮询：没变化时没有任何开销。
 */
class AgentHealthGate(
    private val checker: AgentHealthChecker,
    private val onEvaluated: suspend (AgentHealth) -> Unit,
) {
    private val mutex = Mutex()

    @Volatile
    private var latest: AgentHealth? = null

    /** 最近一次检查结果（启动前的状态查询用）。 */
    val last: AgentHealth? get() = latest

    suspend fun evaluate(): AgentHealth = mutex.withLock {
        val health = checker.check()
        latest = health
        onEvaluated(health)
        health
    }

    /**
     * 只读检查：供 `start*` / `handleUserMessage` 入口复用同一份条件。
     * 刻意**不加锁、也不触发 onEvaluated** —— evaluate() 的回调会调到 start*，
     * 加锁会自锁，触发回调会递归拉起服务。
     */
    suspend fun inspect(): AgentHealth = checker.check().also { latest = it }
}
