package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.TimeProvider

import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.TokenLedgerEntry
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * 进程级单一计量口（F12-T1）。
 *
 * **一次 LLM 调用写三处**，对应三个不同的问题：
 *
 * | 出口 | 形态 | 回答什么 |
 * |---|---|---|
 * | [GlobalTokenCounter] | 单值（当日累加）| "今天还能不能继续调" |
 * | [TokenLedgerDao] | 每次调用一行明细 | "谁（agent / 端点 / 模型）在什么时候花了多少" |
 * | 调用方自行更新 budget | 单值（最近一次 prompt）| "这次请求塞得进吗" |
 *
 * **为什么不让 budget 自己记账**：budget 是 per-Agent 实例、随 Agent start 构造。
 * 改为**调用方上报、meter 只聚合**，避免引入"注册表 / 反查实例"这类易错耦合
 * （`design/agent-token.md` §3）。
 *
 * **实测优先、估算兜底**：端点返回 usage 时记真值（`measured=true`）；不返回时回落估算并
 * **打标 `measured=false`** —— 看板必须能区分两者，否则又回到"用假数字骗自己"。
 */
internal class TokenMeter(
    private val counter: GlobalTokenCounter? = null,
    private val ledgerDao: TokenLedgerDao? = null,
    private val timeProvider: TimeProvider,
) {

    /**
     * 记一次 LLM 调用。
     *
     * @param usage 端点返回的真实用量；null 表示没拿到
     * @param messages 本次请求的完整消息（估算兜底用）
     * @param outputText 本次输出文本（估算兜底用）
     * @param failed 调用是否失败。**无 usage 的失败调用不记账**（见下）
     * @return 落库的明细行；未记账时返回 null
     */
    suspend fun record(
        agentId: String,
        config: AiEndpointConfig?,
        usage: LlmEvent.Usage?,
        messages: List<LlmMessage> = emptyList(),
        outputText: String = "",
        failed: Boolean = false,
        taskId: String? = null,
    ): TokenLedgerEntry? {
        val measured = usage != null
        val entry = if (measured) {
            TokenLedgerEntry(
                createdAt = timeProvider(),
                agentId = agentId,
                endpointHost = hostOf(config?.endpoint),
                model = config?.selectedModel.orEmpty().take(64),
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                cachedTokens = usage.cachedTokens,
                measured = true,
                taskId = taskId,
            )
        } else {
            // 拿不到 usage：失败调用没有消费凭据，记估算只会污染账本 → 不记
            if (failed) return null
            val estimated = estimateTokens(messages, outputText)
            TokenLedgerEntry(
                createdAt = timeProvider(),
                agentId = agentId,
                endpointHost = hostOf(config?.endpoint),
                model = config?.selectedModel.orEmpty().take(64),
                promptTokens = estimated,
                completionTokens = 0,
                cachedTokens = 0,
                measured = false,
                taskId = taskId,
            )
        }

        // ① 当日累加（配额仲裁的输入）
        counter?.recordTokens((entry.promptTokens + entry.completionTokens).toLong())

        // ② 明细账本（分账 / 分时的唯一事实来源）
        //    落库失败不致命：计量是诊断能力，不得拖垮对话/电台
        ledgerDao?.let { dao ->
            runCatching { dao.insert(entry) }.onFailure { e ->
                HmpLog.w(LogTag.AgentContext, e) { "📐 [TokenMeter] ledger insert failed (non-fatal)" }
            }
        }

        HmpLog.d(LogTag.AgentContext) {
            "📐 [TokenMeter] agent=$agentId measured=$measured prompt=${entry.promptTokens} " +
                "completion=${entry.completionTokens} model=${entry.model}"
        }
        return entry
    }

    /**
     * 估算兜底（`measured=false`）。
     *
     * 口径与改造前的 `ReActLoop.estimateTokens` 一致（`length × 0.7`，保底 200）。
     * **已知偏差**（这是它降级为兜底、而真值走 usage 的原因）：
     * - 对英文/混合文本**高估约 2.8 倍**（英语约 4 字符/token）；
     * - **不计工具 schema**（27 个声明每次全量发出）。
     * 即同一次调用里**高估与低估同时存在** → 别指望靠调系数修准，只能靠真值。
     */
    fun estimateTokens(messages: List<LlmMessage>, outputText: String = ""): Int {
        var total = 0.0
        messages.forEach { msg ->
            total += (msg.content?.length ?: 0) * 0.7
            msg.toolCalls?.forEach { tc -> total += tc.argumentsJson.length * 0.7 }
        }
        total += outputText.length * 0.7
        return total.toInt().coerceAtLeast(MIN_ESTIMATE_TOKENS)
    }

    /**
     * 从端点 URL 取**主机名**。
     *
     * ⚠️ 只存 host 是硬约束（`design/agent-token.md` §3.1）：完整 URL 可能含路径令牌，
     * 而"分端点"这个维度用 host 已经足够。同时剥掉 userinfo（`user:pass@host`）防凭证入账。
     */
    private fun hostOf(endpoint: String?): String {
        val raw = endpoint?.trim().orEmpty()
        if (raw.isEmpty()) return UNKNOWN_HOST
        val noScheme = raw.substringAfter("://", raw)
        val noUserInfo = noScheme.substringAfterLast('@', noScheme)
        return noUserInfo
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank { UNKNOWN_HOST }
            .take(64)
    }

    companion object {
        /** 估算保底值（防止极端场景把一次调用记成 0） */
        const val MIN_ESTIMATE_TOKENS: Int = 200

        /** 拿不到端点时的主机名占位 */
        const val UNKNOWN_HOST: String = "unknown"

        /** Agent 身份常量（账本的 `agent_id` 维度；与 `SharedModules` 的 transport 命名对齐） */
        const val AGENT_MASTER: String = "master"
        const val AGENT_RADIO: String = "radio"
        const val AGENT_ENRICH: String = "enrich"
        const val AGENT_HELLO: String = "hello"
        const val AGENT_PROFILE: String = "profile"
    }
}
