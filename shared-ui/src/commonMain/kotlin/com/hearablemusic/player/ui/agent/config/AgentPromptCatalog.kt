package com.hearablemusic.player.ui.agent.config

import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_card
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_card_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_essay
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_essay_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_artist
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_artist_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_fact
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_fact_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_history
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_history_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_listen
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_listen_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_lyric_gold
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_lyric_gold_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_quote
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_quote_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_story
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_story_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_group_forgotten
import com.hearablemusic.player.ui.generated.resources.agent_hello_group_greeting
import com.hearablemusic.player.ui.generated.resources.agent_hello_group_recommend
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_full
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_full_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_list
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_list_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_short
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_short_hint
import com.hmp.domain.agent.runtime.i18n.resolvePrompt
import org.jetbrains.compose.resources.StringResource

/**
 * Agent 配置页的 prompt 目录与解析（纯函数，非 composable）。
 *
 * 这些函数原先定义在 `AgentConfigScreen.kt` 里却被 ViewModel 调用（层级倒挂）；
 * 现在单独成文件：UI 只读取目录用于渲染，VM 负责解析、校验与保存判定。
 */

/**
 * 每个 Agent 在配置页编辑的「主 prompt key」。
 *
 * 必须与运行时实际消费的 key 一致，否则用户编辑的东西不会被引擎读取：
 *  - master：MasterAgent 组装对话 system prompt 用 "chat.system"
 *  - enrich：EnrichSubAgent 用 "enrich.system"
 *  - radio ：RadioSession 主播编排用 "radio.host"
 *  - hello ：多卡型，见 [helloPromptGroups]（此处取问候卡代表 key）
 */
internal fun defaultPromptKeyFor(role: String) = when (role) {
    "master" -> "chat.system"
    "enrich" -> "enrich.system"
    "radio" -> "radio.host"
    "hello" -> "hello.greeting.quote"
    else -> "$role.system"
}

/**
 * Hello 的多卡型 prompt 目录——与 HelloSubAgent 实际消费的 key 一一对应。
 *
 * 分三组对应三类卡片：问候（GREETING，按类型路由）→ 推荐（RECOMMEND）→ 怀旧（FORGOTTEN）。
 * 顺序即 UI 展示顺序；[PromptEntry.label] 是给用户看的卡名，不是内部 key。
 */
internal data class PromptEntry(val key: String, val label: UiText, val hint: UiText)

/** 目录项简写：label / hint 都是资源，包装成 [UiText] 以便渲染期解析。 */
private fun promptEntry(key: String, label: StringResource, hint: StringResource) = PromptEntry(key, label.asUiText(), hint.asUiText())

private val HELLO_GREETING_PROMPTS = listOf(
    promptEntry("hello.greeting.quote", Res.string.agent_hello_greeting_quote, Res.string.agent_hello_greeting_quote_hint),
    promptEntry("hello.greeting.lyric_gold", Res.string.agent_hello_greeting_lyric_gold, Res.string.agent_hello_greeting_lyric_gold_hint),
    promptEntry("hello.greeting.fact", Res.string.agent_hello_greeting_fact, Res.string.agent_hello_greeting_fact_hint),
    promptEntry("hello.greeting.story", Res.string.agent_hello_greeting_story, Res.string.agent_hello_greeting_story_hint),
    promptEntry("hello.greeting.artist", Res.string.agent_hello_greeting_artist, Res.string.agent_hello_greeting_artist_hint),
    promptEntry("hello.greeting.listen", Res.string.agent_hello_greeting_listen, Res.string.agent_hello_greeting_listen_hint),
    promptEntry("hello.greeting.history", Res.string.agent_hello_greeting_history, Res.string.agent_hello_greeting_history_hint),
)

private val HELLO_RECOMMEND_PROMPTS = listOf(
    promptEntry("hello.recommend.full", Res.string.agent_hello_recommend_full, Res.string.agent_hello_recommend_full_hint),
    promptEntry("hello.recommend.short", Res.string.agent_hello_recommend_short, Res.string.agent_hello_recommend_short_hint),
    promptEntry("hello.recommend.list", Res.string.agent_hello_recommend_list, Res.string.agent_hello_recommend_list_hint),
)

private val HELLO_FORGOTTEN_PROMPTS = listOf(
    promptEntry("hello.forgotten.essay", Res.string.agent_hello_forgotten_essay, Res.string.agent_hello_forgotten_essay_hint),
    promptEntry("hello.forgotten.card", Res.string.agent_hello_forgotten_card, Res.string.agent_hello_forgotten_card_hint),
)

/** Hello 的「分组 → 条目」全表，供 UI 分节渲染。 */
internal fun helloPromptGroups(): List<Pair<UiText, List<PromptEntry>>> = listOf(
    Res.string.agent_hello_group_greeting.asUiText() to HELLO_GREETING_PROMPTS,
    Res.string.agent_hello_group_recommend.asUiText() to HELLO_RECOMMEND_PROMPTS,
    Res.string.agent_hello_group_forgotten.asUiText() to HELLO_FORGOTTEN_PROMPTS,
)

/** 该 Agent 是否为多卡型 prompt（需要分组编辑器）。 */
internal fun isMultiPromptAgent(role: String) = role == "hello"

/** 计算某个 prompt key **当前实际生效**的文本，与运行时同源。 */
internal fun effectivePromptFor(
    key: String,
    preferredLang: String,
    globalReplyLang: String,
    promptOverrides: Map<String, String>,
): String = resolvePrompt(
    key = key,
    preferredLang = preferredLang,
    globalReplyLanguage = globalReplyLang,
    userOverrides = promptOverrides,
)

/** 计算某 Agent **当前实际生效**的 prompt 文本（单 prompt Agent 用）。 */
internal fun resolveEffectivePrompt(
    role: String,
    preferredLang: String,
    globalReplyLang: String,
    promptOverrides: Map<String, String>,
): String = effectivePromptFor(
    key = defaultPromptKeyFor(role),
    preferredLang = preferredLang,
    globalReplyLang = globalReplyLang,
    promptOverrides = promptOverrides,
)

/** 从 prompt 模板中提取所有 {{placeholder}} 占位符（不含括号）。 */
internal fun String.extractPlaceholders(): Set<String> {
    val regex = Regex("""\{\{\s*([a-zA-Z_][a-zA-Z0-9_\.]*)\s*\}\}""")
    return regex.findAll(this).map { it.groupValues[1] }.toSet()
}
