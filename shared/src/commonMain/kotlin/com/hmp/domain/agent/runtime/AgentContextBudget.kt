package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmToolSpec
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * 每个 Agent 独立的 LLM 上下文窗口管理器（设计铁则 F2）。
 *
 * - 管**自己的**历史与窗口占用；不涉及全局日配额（那是 `GlobalTokenCounter`）
 * - 不涉及运行仲裁（那是 `AgentScheduler`）
 *
 * ## F12 起的两个关键改动
 *
 * **① 窗口是固定假设，不是实测值**（T3）：`maxContextTokens` 默认取
 * [EngineDefaults.AGENT_CONTEXT_WINDOW]（64K），**不按 Agent 分档、不探测端点**。
 *
 * **② 「常驻裁剪」与「超窗降级」是两件事**（T4）：
 * - **常驻裁剪** = `buildMessages` 只发最近 [recentMessagesToKeep] 条 → 每轮都跑，
 *   **决定基线大小**，是主机制；
 * - **超窗降级** = 仅当估算 prompt 逼近窗口时触发 → **安全网**。
 *
 * 改造前把后者挂在"窗口使用率的百分比"上，而基线天然接近不了上限，
 * 于是压缩分支几乎永不执行（"压缩空转"）。现在降级**只由前置守卫触发**。
 *
 * ## 前置守卫（消除静默故障）
 *
 * 组装完 messages（含 system + tools）后**先估算**：
 * 超 `窗口 × 安全系数` → 先按内部固定策略降级（裁历史 + 回注摘要）；
 * 仍超 → **拒绝发出**并返回带 [OVER_WINDOW_PREFIX] 的 Failed。
 * **不发出必失败的请求** —— 且失败原因与"模型判定 none"**可区分**。
 */
class AgentContextBudget(
    /** Agent 唯一标识（master / radio / enrich / hello） */
    val agentId: String,
    /** 上下文窗口假设值（默认全部 Agent 统一 64K；见 [EngineDefaults.AGENT_CONTEXT_WINDOW]） */
    val maxContextTokens: Int = EngineDefaults.AGENT_CONTEXT_WINDOW,
    /** 绑定的独立 LlmTransport 实例（与其他 Agent 物理隔离；暴露供 LlmCallExecutor 复用）。
     *  HelloSubAgent 等不调 LLM 的 Agent 可传 null。 */
    val llmClient: LlmTransport?,
    /** F12-T1：唯一记账口。null = 不记账（测试 / 未接线场景）。 */
    val tokenMeter: TokenMeter? = null,
    /** 前置守卫的安全系数（prompt 估算超过 `窗口 × 此值` 即降级/拒绝） */
    private val windowSafetyFactor: Float = EngineDefaults.CONTEXT_WINDOW_SAFETY_FACTOR,
    /** 常驻裁剪保留的最近消息条数 */
    private val recentMessagesToKeep: Int = EngineDefaults.HISTORY_KEEP_COUNT,
) {
    private val history = mutableListOf<LlmMessage>()

    /**
     * 压缩后**回注**的摘要。
     *
     * 单独存而不是塞进 `history`：`buildMessages` 用 `takeLast(n)` 裁剪，
     * 放在历史头部的摘要会被立刻裁掉 —— 那就等于"有损丢弃"而非压缩。
     * 这里把它作为 system 之后的固定一条，**每轮都发**。
     */
    private var historySummary: String? = null

    /** 最近一次**实测** prompt tokens（窗口占用的真相；0 = 尚未实测过）。 */
    private var measuredPromptTokens: Int = 0

    // 说明：不同 Agent 之间的 LLM 调用是并发的（Enrich 跑的时候 Radio 也可以生成推荐），
    // 只防止**同一个 Agent 多实例并发**（由 MasterAgent 层的 lifecycleMutex + Job join 保证）。
    // rate limit 是 per-key per-minute 的，Agent 间并发不会打爆；多实例才会光速重试耗尽配额。

    /** 最近一次实测的 prompt 占用（0 表示还没实测过，此时窗口占用未知）。 */
    fun measuredWindowTokens(): Int = measuredPromptTokens

    /** 当前可用于 prompt 的 token 预算（前置守卫上限）。 */
    fun promptBudgetTokens(): Int = (maxContextTokens * windowSafetyFactor).toInt()

    /** 历史消息条数（诊断用）。 */
    fun historySize(): Int = history.size

    /** 向上下文追加消息（仅入历史；压缩已改由前置守卫触发，不再按百分比自动压）。 */
    fun appendMessages(messages: List<LlmMessage>) {
        history.addAll(messages)
    }

    /**
     * 执行一次 LLM 调用（完整走 AgentContextBudget）：
     * 组装 → **前置守卫**（必要时降级，仍超则拒绝）→ 流式调用 → 计量收口。
     */
    suspend fun callLlm(
        config: AiEndpointConfig,
        systemPrompt: String,
        newMessages: List<LlmMessage>,
        tools: List<LlmToolSpec>? = null,
        temperature: Float = 0.3f,
    ): Flow<LlmEvent> {
        appendMessages(newMessages)
        var messages = buildMessages(systemPrompt)

        // ── 前置守卫：不发出必失败的请求 ──
        val cap = promptBudgetTokens()
        if (estimatePromptTokens(messages, tools) > cap) {
            compressHistory()
            messages = buildMessages(systemPrompt)
        }
        val estimated = estimatePromptTokens(messages, tools)
        if (estimated > cap) {
            val reason = overWindowMessage(estimated, cap)
            HmpLog.w(LogTag.AgentContext) { "📐 $reason" }
            return flowOf(LlmEvent.Failed(reason))
        }

        val client = llmClient
            ?: error("AgentContextBudget[$agentId] llmClient is null, cannot call LLM")
        val upstream = client.streamChat(
            config = config,
            messages = messages,
            tools = tools,
            temperature = temperature,
        )
        return flow {
            var usage: LlmEvent.Usage? = null
            var failed = false
            val out = StringBuilder()
            upstream.collect { event ->
                when (event) {
                    is LlmEvent.Usage -> usage = event
                    is LlmEvent.Failed -> failed = true
                    is LlmEvent.TextDelta -> out.append(event.text)
                    else -> Unit
                }
                emit(event)
            }
            // 计量收口：真值优先，估算兜底并打标（measured=true/false）
            val recorded = tokenMeter?.record(
                agentId = agentId,
                config = config,
                usage = usage,
                messages = messages,
                outputText = out.toString(),
                failed = failed,
            )
            if (recorded?.measured == true) measuredPromptTokens = recorded.promptTokens
        }
    }

    /**
     * 非流式便捷调用：收集流式输出拼接成完整文本。
     *
     * 适用于一次性 JSON 生成、富化管道等不需要流式打字机效果的场景。
     * 返回 null 表示 LLM 调用失败（网络、超时、HTTP 错误、**超窗**）。
     */
    suspend fun callLlmText(
        config: AiEndpointConfig,
        systemPrompt: String,
        newMessages: List<LlmMessage>,
        tools: List<LlmToolSpec>? = null,
        temperature: Float = 0.3f,
    ): String? {
        val flow = callLlm(config, systemPrompt, newMessages, tools, temperature)
        val textBuffer = StringBuilder()
        var failedMessage: String? = null
        // 不串行化：不同 Agent 之间并发调用 LLM，只靠 MasterAgent 层的单实例保护防多实例
        flow.collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> textBuffer.append(event.text)
                is LlmEvent.Failed -> failedMessage = event.message
                is LlmEvent.Usage -> Unit // 计量已在 callLlm 收口
                is LlmEvent.Completed -> Unit
                is LlmEvent.ToolCall -> Unit // 非流式场景忽略 tool calls
            }
        }
        return if (failedMessage != null) {
            HmpLog.e(LogTag.AgentContext) {
                if (isOverWindowFailure(failedMessage)) "📐 $failedMessage"
                else "📐 [$agentId] callLlmText failed: $failedMessage"
            }
            null
        } else {
            textBuffer.toString()
        }
    }

    /**
     * 构建 LLM 调用的完整 messages 列表：`system` + （压缩摘要）+ 常驻裁剪后的历史。
     * 新消息已在 `callLlm` 里进了 history，这里直接从历史构建——避免重复。
     */
    fun buildMessages(systemPrompt: String): List<LlmMessage> {
        val result = mutableListOf<LlmMessage>()
        result.add(LlmMessage(role = "system", content = systemPrompt))
        historySummary?.let { result.add(LlmMessage(role = "system", content = it)) }
        result.addAll(history.takeLast(recentMessagesToKeep))
        return result
    }

    /** 释放 LLM 客户端（SubAgent shutdown 时调用） */
    fun releaseLlmClient() {
        // LlmTransport 无关闭接口（纯接口 + Flow），此处清理历史即可
        clearHistory()
    }

    /** 清空历史（新会话 / 批次间防串味） */
    fun clearHistory() {
        history.clear()
        historySummary = null
        measuredPromptTokens = 0
    }

    /**
     * 历史压缩（降级动作之一）：丢弃早期消息，并把**摘要回注**（[historySummary]）。
     *
     * 改造前只丢弃、不回注 —— 那是"有损丢弃"而非压缩：模型连"曾经有过什么"都不知道。
     */
    private fun compressHistory(): String? {
        val keepFrom = (history.size - recentMessagesToKeep).coerceAtLeast(0)
        if (keepFrom <= 0) return null
        val discardedCount = keepFrom
        repeat(discardedCount) { history.removeAt(0) }

        val summary = "[历史压缩] 已丢弃 $discardedCount 条早期消息以腾出窗口" +
            "（当前保留最近 ${history.size} 条）"
        historySummary = if (historySummary.isNullOrBlank()) summary else "$historySummary\n$summary"
        HmpLog.i(LogTag.AgentContext) {
            "📐 [$agentId] $summary (窗口假设 ${maxContextTokens}，估算占用 ${estimatePromptTokens(buildMessages(""), null)})"
        }
        return summary
    }

    /**
     * 估算"本次要发的 prompt"占多少 token —— 前置守卫的判据。
     *
     * **该估算是保守高估**，已知偏差：`length × 0.7` 对英文/混合文本高估约 2.8 倍
     * （英语约 4 字符/token）。高估对**守卫方向是安全的**（宁可早拒），
     * 但**记账方向**必须用真值 —— 所以它只用于预检，不用于入账。
     *
     * 与旧实现的两点差异：① **计入工具 schema**（27 个声明每次全量发出，
     * 旧实现完全不计，是明显低估项）；② 计入回注摘要。
     */
    fun estimatePromptTokens(messages: List<LlmMessage>, tools: List<LlmToolSpec>?): Int {
        var total = 0.0
        messages.forEach { msg ->
            total += MESSAGE_OVERHEAD_TOKENS
            total += (msg.content?.length ?: 0) * 0.7
            msg.toolCalls?.forEach { tc -> total += tc.argumentsJson.length * 0.7 }
            if (msg.toolCallId != null) total += MESSAGE_OVERHEAD_TOKENS
        }
        tools?.forEach { spec ->
            total += spec.name.length
            total += spec.description?.length ?: 0
            total += spec.parameters?.toString()?.length ?: 0
        }
        return (total * (if (tools.isNullOrEmpty()) 1.0 else 0.7)).toInt()
    }

    /** 超窗失败的统一措辞 —— 让日志/错误能一眼区分"超窗"与"模型判定 none"。 */
    private fun overWindowMessage(need: Int, cap: Int): String =
        "$OVER_WINDOW_PREFIX agent=$agentId 需 $need > 有效上限 $cap" +
            "（窗口假设 ${maxContextTokens} × 安全系数 $windowSafetyFactor）→ 已拒绝并降级；" +
            "该模型窗口可能 <${maxContextTokens / 1000}K，本应用不支持"

    companion object {
        /** 超窗失败消息前缀（调用方据此区分"超窗"与其它失败）。 */
        const val OVER_WINDOW_PREFIX: String = "[超窗]"

        /** 每条消息的固定结构开销（role + 分隔符） */
        private const val MESSAGE_OVERHEAD_TOKENS = 4

        /** 判断一次失败是否由"超窗前置拒绝"造成。 */
        fun isOverWindowFailure(message: String?): Boolean =
            message?.startsWith(OVER_WINDOW_PREFIX) == true
    }
}
