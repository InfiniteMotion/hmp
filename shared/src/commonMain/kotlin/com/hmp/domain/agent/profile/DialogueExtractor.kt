package com.hmp.domain.agent.profile

import com.hmp.domain.agent.port.LlmMessage

/**
 * 对话自动抽取（契约 `agent-profile.md` v3.4 §4.4 / §10.1「会话事件」行）。
 *
 * 对话结束后从用户消息中抽取音乐偏好，写 `T2_DIALOGUE`（0.35）证据、默认落 L2、
 * **不自动升 L3** —— 被播放行为确证（同槽位出现 `T0_BEHAVIOR` 证据）才随置信度合并走高。
 *
 * 三条约束在这里落地（契约 §4.4 的表格）：
 *
 * 1. **落点必须进闭集** —— 菜单只有三个谓词（[allowedPredicates]），LLM 只能从中选，
 *    解析器再挡一道。不提供自由文本槽位。
 * 2. **底线：推断「音乐选择」，不推断「人」** —— 菜单里没有任何关于处境/情绪/人格的槽位，
 *    结构上就写不进去；prompt 再明说一遍。
 * 3. **值要短** —— 自由短语限长（[MAX_VALUE_LEN]），防把整句对话当偏好存下来。
 *
 * 成本设计：**先过线索闸门**（[shouldExtract]），绝大多数消息一个 LLM 调用都不花；
 * 只有用户真的在表达音乐偏好时才发起抽取。
 */
object DialogueExtractor {

    // ── 闭集菜单（与 PortraitType 的槽位闭集对齐，不可扩到音乐选择之外）──
    const val SLOT_LEADING_GENRE = "taste_portrait.leadingGenre"
    const val SLOT_TOP_ARTISTS = "taste_portrait.topArtists"
    const val SLOT_DISLIKED_STYLES = "taste_portrait.dislikedStyles"

    val allowedPredicates: Set<String> = setOf(SLOT_LEADING_GENRE, SLOT_TOP_ARTISTS, SLOT_DISLIKED_STYLES)

    /** 值的长度上限：偏好是短语，不是日记 */
    const val MAX_VALUE_LEN = 12

    /** 单条消息最多抽几条 —— 宁缺毋滥 */
    private const val MAX_ITEMS = 3

    /**
     * 线索闸门：消息里出现偏好表达的字样才发起抽取。
     *
     * 刻意保守 —— 漏抽的代价是"这次没记住"（下回还会说），误抽的代价是一次
     * 无意义 LLM 调用 + 一条噪声证据。不含这些字样的消息直接返回 false。
     */
    fun shouldExtract(userMessage: String): Boolean {
        val cues = listOf(
            "喜欢", "不喜欢", "讨厌", "爱听", "不爱听", "不想听", "别推", "少推",
            "常听", "一直在听", "最近在听", "循环", "想听", "来点", "风格", "曲风",
            "歌手", "民谣", "摇滚", "流行", "爵士", "古典", "电子", "说唱", "嘻哈",
            "金属", "乡村", "蓝调", "民乐", "轻音乐", "纯音乐",
        )
        return cues.any { userMessage.contains(it) }
    }

    /** 抽取调用的消息组。系统提示只给菜单与格式，不给自由发挥空间。 */
    fun buildMessages(userMessage: String): List<LlmMessage> = listOf(
        LlmMessage(
            role = "system",
            content = """
                你是音乐偏好抽取器。从用户消息里抽取**对音乐选择的偏好**，输出 JSON 数组，
                每项形如 {"slot":"...","value":"..."}，规则：
                1. slot 只能取以下三个值之一（菜单之外一律不输出）：
                   - "$SLOT_LEADING_GENRE"：用户说喜欢/想听某类音乐（value=风格词，如"爵士"）
                   - "$SLOT_TOP_ARTISTS"：用户点名欣赏的歌手（value=歌手名）
                   - "$SLOT_DISLIKED_STYLES"：用户说不喜欢/不想听某类（value=短语，如"快歌"）
                2. value 用不超过 $MAX_VALUE_LEN 字的中文短语，照用户的原话提炼，不要发挥。
                3. **只记录音乐选择本身**。用户的情绪、处境、性格一律不抽 —— 你没有资格推断。
                4. 最多 $MAX_ITEMS 条。没有可抽取的输出 []，不要解释。
                只输出 JSON，不要其他文字。
            """.trimIndent(),
        ),
        LlmMessage(role = "user", content = userMessage),
    )

    /** 抽取结果（已过闸门：谓词在闭集内、值已消毒）。 */
    data class Extraction(val predicate: String, val value: String)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * 解析 LLM 输出。任何一步不合格就丢弃该项（**宁可不记，不可错记**）：
     * - 只认 JSON 数组（容忍 ```json 围栏与前后噪声）；
     * - slot 不在 [allowedPredicates] → 丢弃；
     * - value 空白/超长/含换行 → 丢弃。
     */
    fun parse(text: String): List<Extraction> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arrayText = text.substring(start, end + 1)
        val array = runCatching {
            json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(arrayText)
        }.getOrNull() ?: return emptyList()

        return array.mapNotNull { item ->
            val slot = item["slot"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return@mapNotNull null
            val value = item["value"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return@mapNotNull null
            if (slot !in allowedPredicates) return@mapNotNull null
            val cleaned = value.trim()
            if (cleaned.isEmpty() || cleaned.length > MAX_VALUE_LEN || cleaned.contains('\n')) return@mapNotNull null
            Extraction(predicate = slot, value = cleaned)
        }.distinctBy { it.predicate to it.value }
            .take(MAX_ITEMS)
    }
}
