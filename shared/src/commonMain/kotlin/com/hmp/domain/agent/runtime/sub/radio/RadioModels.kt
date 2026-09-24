package com.hmp.domain.agent.runtime.sub.radio

// ═══════════════════════════════════════════════════════════════════
// 电台数据模型 —— Agent 与 UI 之间的**词汇表**
// ═══════════════════════════════════════════════════════════════════
//
// 原先位于 RadioSubAgent.kt 末尾与实现同文件（约 100 行）。
// 这些类型是**对外契约**：MasterAgent 读 RadioState/RadioCardState，
// shared-ui 订阅 radioState / radioMessageFlow。而 Agent 内部流程
// （开播、编排、重排、结算）是**实现**。两者变化原因不同，分开放。


// ═══════════════════════════════════════════════════════════════════
// 电台数据模型
// ═══════════════════════════════════════════════════════════════════

sealed class RadioState {
    /** 电台空闲（未启动 / 已停止） */
    data object IDLE : RadioState()

    /** 电台正在构建 playlist（进度真实数据来自 RadioSubAgent 内部步骤） */
    data class BUILDING(
        /** 进度百分比 0-100（阶段性跳变，非实时） */
        val progressPercent: Int = 0,
        /** 当前动作文字（如"AI 正在理解你的喜好..."、"LLM 补充..."） */
        val actionText: String = "正在初始化...",
        /** 目标曲目数 */
        val targetCount: Int = 8,
        /** 已生成曲目数（BUILDING 阶段通常 0，PLAYING 阶段等于 currentCount） */
        val currentCount: Int = 0,
    ) : RadioState()

    /** 电台播放中 */
    data class PLAYING(
        /** 当前 playlist 曲目数 */
        val currentCount: Int,
        /** 目标曲目数（通常等于 currentCount） */
        val targetCount: Int,
    ) : RadioState()

    /** 电台已暂停（用户点了收音机卡；playlist 与当前曲目保留，恢复即续播） */
    data class PAUSED(
        /** 暂停时 playlist 曲目数 */
        val currentCount: Int = 0,
        /** 暂停时的目标曲目数 */
        val targetCount: Int = 0,
    ) : RadioState()
}

/**
 * 电台卡片的展示状态（C1 + §3.3 2026-09-13 决议）。
 *
 * 刻意分成两段 —— UI 不该自己去拼「哪些会变」：
 * - **固定**：整档不变，开播那一刻确定
 * - **动态**：随播放与队列调整变化
 *
 * [lastAdjust] 可以是主播的编排思路（结构化 reason 字段的渲染，单句截断）——
 * 这是 C1 的唯一松动出口；仍不允许拼接其他模型文字或长篇文案。
 */
data class RadioCardState(
    /** 【固定】本档主题（用户种子），null = 自动电台 */
    val theme: String?,
    /** 【动态】还有几首待播（不含在播那首） */
    val upcomingCount: Int,
    /** 【动态】下一首曲名 */
    val nextTitle: String?,
    /** 【动态】最近一次调整：主播编排思路或固定文案（「已换一批」/「已补 5 首」），null = 本档还没调过 */
    val lastAdjust: String?,
)

/**
 * 电台启动来源（W 阶段）——用于审计与种子兜底策略区分。
 */
enum class RadioTrigger {
    /** 首页收音机卡 / 播放页点击（默认） */
    HOME_CLICK,

    /** 对话内触发（"来点电台"）——chatContext 可兜底做种子 */
    CHAT_INPUT,

    /** 连跳 2+ 首触发的重排重建 */
    SKIP_REORDER,

    /** 暂停后恢复 */
    RESUME,
    ;

    companion object {
        /** 从对话原文推断触发来源（恢复类措辞 → RESUME，其余一律 CHAT_INPUT） */
        fun fromChatInput(input: String): RadioTrigger {
            val lower = input.lowercase()
            val resumeWords = listOf("恢复", "继续", "接着", "resume", "continue")
            return if (resumeWords.any { it in lower }) RESUME else CHAT_INPUT
        }
    }
}

/**
 * 电台短时消息（W 阶段）——UI 的 RADIO_STATUS 卡右侧实时提示，4s 自动退场。
 */
sealed class RadioMessage {
    /** 连跳触发重排："跳过 N 首，正在重选..." */
    data class ReorderSkipped(val count: Int) : RadioMessage()

    /** LLM 富化阶段："AI 正在优化歌单..." */
    data object EnrichOptimizing : RadioMessage()

    /** 续歌提示："续播「曲名」" */
    data class TrackContinuing(val title: String) : RadioMessage()

    /** 主题就绪："「蓝调」已就绪" */
    data class ThemeChanged(val theme: String) : RadioMessage()
}
