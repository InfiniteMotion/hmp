package com.hearablemusic.player.ui.chat

import com.hmp.domain.agent.runtime.ToolExecutionRecord
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist

/** 清洗模型回复中的 Markdown 标记与 ASCII 引号（Compose Text 不解析 Markdown；裸露的 ** / * / ` 显破碎）。 */
internal fun cleanAgentMarkdown(text: String): String =
    text.replace("**", "").replace("__", "").replace("`", "").replace("*", "")
        // ASCII 双引号对 → 中文引号（「」更贴合中文排版）
        .replace(Regex("\"([^\"]*)\"")) { m -> "「${m.groupValues[1]}」" }

/**
 * M5-T2 五类气泡渲染类型（CompanionBubble 按 renderHint 分发）。
 *
 * 资料面「render_hint 驱动渲染」：UI 消息模型以 [CompanionRenderHint] 区分气泡形态，
 * 桥接层（ChatAgentGateway）把引擎回合映射到 [CompanionMessage]；文本/解释/确认真实，
 * song/songlist 数据模型就绪（引擎产出结构化曲目后自然填充，见 M6）。
 */
enum class CompanionRenderHint {
    /** 纯文字答复（默认；也承载用户气泡）。 */
    TEXT,
    /** 单曲卡片（AlbumCover + 题名 + 菜单）。 */
    SONG,
    /** 歌单列表（FixedMusicList 复用 + 尾部动作行）。 */
    SONGLIST,
    /** 计划轨迹（可折叠展开）。 */
    EXPLAIN,
    /** 确认矩阵（逐项勾选 + 照做/跳过）。 */
    CONFIRM,
}

/** 确认矩阵中的单一项（M5-T4）。 */
data class ConfirmItem(
    val id: String,
    val toolName: String,
    val argsSummary: String,
    val selected: Boolean,
    /** v7.1 新增：勾上后该工具将写入 agentPolicy.config.alwaysAllow，后续不再弹确认 */
    val alwaysAllow: Boolean = false,
)

/** 单条对话消息（UI 展平模型，含渲染类型与可选载荷）。 */
data class CompanionMessage(
    val id: Long,
    val fromUser: Boolean,
    val renderHint: CompanionRenderHint = CompanionRenderHint.TEXT,
    val text: String = "",
    /** [CompanionRenderHint.SONG] 单曲载荷。 */
    val song: MusicInfo? = null,
    /** [CompanionRenderHint.SONGLIST] 列表载荷。 */
    val songs: List<MusicInfo> = emptyList(),
    val playlist: Playlist? = null,
    /** [CompanionRenderHint.EXPLAIN] 步骤轨迹。 */
    val trail: List<String> = emptyList(),
    /** [CompanionRenderHint.CONFIRM] 确认矩阵。 */
    val confirmItems: List<ConfirmItem> = emptyList(),
    /** 执行回执文案（「已建 9 首，跳过 3 首」式），仅在确认卡执行完毕后非空。 */
    val receipt: String = "",
    /** 系统/单测注入的判定文案（渲染快照用）。 */
    val note: String = "",
)

/**
 * 由一次引擎回合（AgentResult + 确认矩阵 + 工具执行序）展平为 `assistant → UI` 的若干气泡。
 *
 * - 纯文本回合 → 1 条 TEXT 气泡。
 * - 含确认 → 1 条 CONFIRM 气泡；执行回执作为其 [CompanionMessage.receipt]，并可选追加一条
 *   结果 TEXT 气泡。
 */
internal fun buildAssistantBubbles(
    text: String,
    confirmItems: List<ConfirmItem>,
    records: List<ToolExecutionRecord>,
): List<CompanionMessage> {
    val out = mutableListOf<CompanionMessage>()
    if (confirmItems.isNotEmpty()) {
        val accepted = confirmItems.filter { it.selected }
        val skipped = confirmItems.size - accepted.size
        val receipt = if (accepted.isEmpty()) "本轮已全部跳过，未做改动。"
        else buildString {
            append("已按你勾选执行 ")
            append(accepted.size)
            append(" 项")
            if (skipped > 0) append("，跳过 $skipped 项")
            append("。")
        }
        out += CompanionMessage(
            id = 0, // 占位；由 ChatViewModel 统一 nextId() 赋值，避免撞键
            fromUser = false,
            renderHint = CompanionRenderHint.CONFIRM,
            confirmItems = confirmItems,
            receipt = receipt,
        )
    }
    val body = cleanAgentMarkdown(text.ifBlank { records.joinToString("\n") { "${it.toolName} → ${it.summary}" } })
    if (body.isNotBlank()) {
        out += CompanionMessage(
            id = 0,
            fromUser = false,
            renderHint = CompanionRenderHint.TEXT,
            text = body,
        )
    }
    return out
}