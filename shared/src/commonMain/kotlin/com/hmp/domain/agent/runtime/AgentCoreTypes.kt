package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.LlmMessage

/** 终止原因（熔断为何终止，入审计）。 */
enum class TerminationReason { ANSWERED, STEP_BUDGET_EXHAUSTED, CLOUD_QUOTA_EXHAUSTED, FAILED }

/** 一次工具执行记录（供结果回填与审计）。 */
data class ToolExecutionRecord(
    val toolName: String,
    val outcome: String, // success / failed / refused / skipped
    val summary: String,
    /**
     * 结构化回传（可解析 JSON 文本）——工具把机器可读结果放在这里，`summary` 保持人读。
     * 例：播放队列替换后回传实际入队的曲目清单。无结构化结果时为 null。
     */
    val detail: String? = null,
)

/**
 * 内建意图路由命中的标识 —— [AgentResult.intentHandled] 的**完整取值集合**。
 *
 * ⚠️ 用 enum 而不是字符串常量，是为了让"值域"从注释进到**编译期**：
 * 生产方（`MasterAgent.builtinIntents` 表）与消费方（UI 的 `ChatAgentGateway`）
 * 引用同一组符号 —— 新增意图、拼错标识都会在编译期暴露。
 *
 * 这是 F13 阶段 3c 记下的警告信号的实例：**注释里写契约，代码里各一份**。
 * 原先 `intentHandled: String?` 的值域只由一行 KDoc 描述（"值如 radio_start…"），
 * UI 侧靠 7 个裸字符串字面量去 `when` 匹配 —— 两边并无编译期联系，新增意图时
 * 忘记同步 UI **不会报错**，只是回复气泡静默落进错误分支（这类缺陷不崩溃、不报警）。
 */
enum class BuiltinIntentId {
    RADIO_START,
    RADIO_STOP,
    RADIO_RESUME,
    /** 「继续电台」但电台并未处于暂停态时的空操作（guard 拦截分支）。 */
    RADIO_RESUME_NOOP,
    ENRICH_START,
    ENRICH_STOP,
    ENRICH_PAUSE,
    ENRICH_RESUME,
    ENRICH_RESCAN,
    ENRICH_STATUS,
}

/** Agent 任务结果。 */
data class AgentResult(
    val text: String,
    val stepsUsed: Int,
    val toolCalls: List<ToolExecutionRecord>,
    val terminatedBy: TerminationReason,
    /**
     * 内建意图路由命中标记（MasterAgent.handleUserMessage 内部确定性匹配）。
     * 非 null 表示 Master 直接处理了子 Agent 生命周期，不走 LLM ReActLoop。
     * 取值集合见 [BuiltinIntentId]。
     */
    val intentHandled: BuiltinIntentId? = null,
)

// 注：确认门三件套（ConfirmGate / ConfirmRequest / ConfirmOutcome）
// 已下沉到 `port/ConfirmGate.kt` —— 它们是跨层契约，不属于引擎目录。

/** 一次运行的外部上下文输入（供组装 LLM system prompt + 首轮注入）。 */
data class RunContextInput(
    val taskState: String? = null,
    val libraryListText: String? = null,
    val libraryOverviewText: String? = null,
    // —— 首轮注入（第一次对话就该到 agent 的内容）——
    val personaText: String? = null,
    val recognitionText: String? = null,
    val timeOfDayText: String? = null,
    val nowPlayingText: String? = null,
    val userTitle: String? = null,
    /**
     * 用户认识模块渲染出的画像块（自带「仅供参考」标题，见 `agent-profile.md` §7.1）。
     *
     * ⚠️ 它进的是 **system prompt**，因此**整场任务的每一轮都可见** ——
     * 这正是 C2 从"会话内不读画像"改为"读了但不作为指令"的原因（契约 §8）。
     */
    val userProfileText: String? = null,
    // —— 跨轮记忆：上一轮及以前的 user/assistant 文本消息（按时间正序，越新越靠后）——
    val history: List<LlmMessage> = emptyList(),
)
