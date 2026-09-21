package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.agent.runtime.JsonText
import com.hmp.domain.music.MusicInfo
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ===== 模型返回的 wire 类型 =====
//
// 这四个类型是「LLM 的 JSON 输出形状」，因此与解析器同处一文件：
// 协议与它的编解码器放在一起读，比散在编排类尾部更容易看出对应关系。

/** Round 1 枚举字段结果 */
@Serializable
internal data class EnumOnlyResult(
    val genre: List<String>,
    val mood: List<String>,
    val scenario: List<String>,
    val language: String = "",
    val era: String = "",
)

/** Round 2a 自由文本 Easy —— description + singerIntroduce */
@Serializable
internal data class FreeTextEasyResult(
    val description: String = "",
    val singerIntroduce: String = "",
)

/** Round 2b 自由文本 Facts —— 极易编造的字段 */
@Serializable
internal data class FreeTextFactsResult(
    val rewards: String = "",
    val lyric: String = "",
    val backgroundIntroduce: String = "",
    val relevantMusic: String = "",
)

/** Round 3 总体反思 patch —— 只修单字段 */
@Serializable
internal data class ReflectionPatch(
    val index: Int = 0,     // 歌曲在组内的序号（prompt 里从 1 开始；parse 后会规整为 0-based）
    val field: String = "",  // 字段名
    val fix: String = "",    // 修正值；"-暂无" 或 blank 表示置空
)

/**
 * Enrich 的 **模型输出解析器** —— "一段文本 → 结构化结果"。
 *
 * 从 `EnrichSubAgent` 抽出（约 260 行）。它与"5 轮编排"是两件事：
 * 编排决定**什么时候问什么**，这里决定**拿回来的文本怎么读**。
 * 拆开后解析逻辑可脱离 Agent 单独喂样例文本验证（LLM 输出畸形是常态）。
 *
 * 全部为**无状态纯函数**（除 `json` 编解码器）：输入文本 + 歌曲列表 → 结果 Map。
 * 因此这里的 `private` → 包内可见不构成可见性回退 ——
 * **`private` 保护的是不变量，而无状态纯函数没有不变量。**
 *
 * `agentId` 仅用于保留原有的诊断日志前缀（日志是排查模型输出异常的**唯一**线索，
 * 不能因为搬家而丢失）。
 */
internal object EnrichResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val EMPTY_FACT_MARKERS = setOf(
        "-暂无", "none", "null", "待定", "不详", "未知", "暂无相关信息",
        "tbd", "n/a", "unknown", "-", "--", "—",
    )

    // ===== 各轮次解析 =====

    /** Round 1 枚举批量 */
    fun parseEnumBatch(text: String, songs: List<MusicInfo>, agentId: String): Map<Long, EnumOnlyResult> {
        val elements = extractJsonArrayElements(text, agentId)
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] parseEnumBatch: extracted ${elements.size} JSON elements, expecting ${songs.size} songs" }
        val result = mutableMapOf<Long, EnumOnlyResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<EnumOnlyResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] song ${song.music.id} (${song.music.title}) Round 1 parse failed: ${e.message}" }
                }
        }
        HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] parseEnumBatch: ${result.size}/${songs.size} songs parsed OK" }
        return result
    }

    /** Round 1.5 枚举自检 —— LLM 返回完整枚举对象列表（全量 replace，不是 partial patch） */
    fun parseEnumSelfCheckFullList(text: String, songs: List<MusicInfo>, agentId: String): Map<Long, EnumOnlyResult> {
        val elements = extractJsonArrayElements(text, agentId)
        val result = mutableMapOf<Long, EnumOnlyResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<EnumOnlyResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] song ${song.music.id} Round 1.5 parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** 把 Round 1.5 的全量 replace 应用到 enumMap（patch 非空字段才覆盖） */
    fun applyEnumReplacements(
        enumMap: MutableMap<Long, EnumOnlyResult>,
        patchMap: Map<Long, EnumOnlyResult>,
        songs: List<MusicInfo>,
    ) {
        for (song in songs) {
            val id = song.music.id
            val original = enumMap[id] ?: continue
            val patch = patchMap[id] ?: continue
            // 枚举自检只应修正有问题的字段；覆盖策略：patch 非空就用 patch 的
            val merged = original.copy(
                genre = patch.genre.ifEmpty { original.genre },
                mood = patch.mood.ifEmpty { original.mood },
                scenario = patch.scenario.ifEmpty { original.scenario },
                language = patch.language.ifBlank { original.language },
                era = patch.era.ifBlank { original.era },
            )
            enumMap[id] = merged
        }
    }

    /** Round 2a 自由文本 Easy */
    fun parseFreeTextEasyBatch(text: String, songs: List<MusicInfo>, agentId: String): Map<Long, FreeTextEasyResult> {
        val elements = extractJsonArrayElements(text, agentId)
        val result = mutableMapOf<Long, FreeTextEasyResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<FreeTextEasyResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] song ${song.music.id} Round 2a parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** Round 2b 自由文本 Facts */
    fun parseFreeTextFactsBatch(text: String, songs: List<MusicInfo>, agentId: String): Map<Long, FreeTextFactsResult> {
        val elements = extractJsonArrayElements(text, agentId)
        val result = mutableMapOf<Long, FreeTextFactsResult>()
        for ((index, elementText) in elements.withIndex()) {
            if (index >= songs.size) break
            val song = songs[index]
            runCatching { json.decodeFromString<FreeTextFactsResult>(elementText) }
                .onSuccess { result[song.music.id] = it }
                .onFailure { e ->
                    HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] song ${song.music.id} Round 2b parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** 合并 enum + easy + facts → 完整 EnrichDraft */
    fun mergeAll(
        enum: EnumOnlyResult?,
        easy: FreeTextEasyResult?,
        facts: FreeTextFactsResult?,
    ): EnrichDraft {
        return EnrichDraft(
            genre = enum?.genre ?: emptyList(),
            mood = enum?.mood ?: emptyList(),
            scenario = enum?.scenario ?: emptyList(),
            language = enum?.language?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
            era = enum?.era?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
            description = easy?.description ?: "",
            singerIntroduce = easy?.singerIntroduce ?: "",
            rewards = facts?.rewards?.normalizeFacts() ?: "",
            lyric = facts?.lyric?.normalizeFacts() ?: "",
            backgroundIntroduce = facts?.backgroundIntroduce?.normalizeFacts() ?: "",
            relevantMusic = facts?.relevantMusic?.normalizeFacts() ?: "",
        )
    }

    /** Round 3 总体反思 patch 列表 */
    fun parseReflectionPatches(text: String, songs: List<MusicInfo>, agentId: String): List<ReflectionPatch> {
        val elements = extractJsonArrayElements(text, agentId)
        val result = mutableListOf<ReflectionPatch>()
        for (elementText in elements) {
            runCatching { json.decodeFromString<ReflectionPatch>(elementText) }
                .onSuccess {
                    // index 可能是 1-based（LLM 习惯），规整到 0-based
                    val idx = it.index - 1
                    if (idx in songs.indices) {
                        result.add(it.copy(index = idx))
                    }
                }
                .onFailure { e ->
                    HmpLog.w(LogTag.AgentEnrich, e) { "📚 [$agentId] Round 3 patch parse failed: ${e.message}" }
                }
        }
        return result
    }

    /** 把 Round 3 patches 应用到 draftResults（按 index 找 song） */
    fun applyReflectionPatches(
        draftResults: MutableMap<Long, EnrichDraft>,
        patches: List<ReflectionPatch>,
        songs: List<MusicInfo>,
    ) {
        for (patch in patches) {
            val song = songs.getOrNull(patch.index) ?: continue
            val id = song.music.id
            val current = draftResults[id] ?: continue
            draftResults[id] = applyPatch(current, patch)
        }
    }

    /** 单字段 patch 应用（通过 copy + when 分支，类型安全） */
    fun applyPatch(current: EnrichDraft, patch: ReflectionPatch, agentId: String = ""): EnrichDraft {
        val fix = patch.fix.trim()
        val isEmptyMarker = fix.isBlank() || EMPTY_FACT_MARKERS.any { fix.equals(it, ignoreCase = true) }
        return when (patch.field.lowercase()) {
            "genre" -> current.copy(genre = if (isEmptyMarker) emptyList() else fix.split(',').map { it.trim() }.filter { it.isNotBlank() })
            "mood" -> current.copy(mood = if (isEmptyMarker) emptyList() else fix.split(',').map { it.trim() }.filter { it.isNotBlank() })
            "scenario" -> current.copy(scenario = if (isEmptyMarker) emptyList() else fix.split(',').map { it.trim() }.filter { it.isNotBlank() })
            "language" -> current.copy(language = if (isEmptyMarker) "UNKNOWN" else fix.trim())
            "era" -> current.copy(era = if (isEmptyMarker) "UNKNOWN" else fix.trim())
            "description" -> current.copy(description = if (isEmptyMarker) "" else fix)
            "singerintroduce" -> current.copy(singerIntroduce = if (isEmptyMarker) "" else fix)
            "rewards" -> current.copy(rewards = if (isEmptyMarker) "" else fix)
            "lyric" -> current.copy(lyric = if (isEmptyMarker) "" else fix)
            "backgroundintroduce" -> current.copy(backgroundIntroduce = if (isEmptyMarker) "" else fix)
            "relevantmusic" -> current.copy(relevantMusic = if (isEmptyMarker) "" else fix)
            else -> {
                HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] Round 3 unknown patch field: ${patch.field} — ignoring" }
                current
            }
        }
    }

    // ===== 内部工具 =====

    /** "-暂无" / "NONE" / "null" 等 → 空字符串（事实字段归一化） */
    private fun String.normalizeFacts(): String {
        val trimmed = this.trim()
        if (trimmed.isBlank()) return ""
        if (EMPTY_FACT_MARKERS.any { trimmed.equals(it, ignoreCase = true) }) return ""
        return trimmed
    }

    /**
     * 抽出 JSON array 里的各个对象。**算法在 [JsonText]，日志留在本处** ——
     * 只有 Agent 侧才知道 agentId；而"模型没给数组包装"是这里最该被看见的异常。
     */
    private fun extractJsonArrayElements(text: String, agentId: String): List<String> {
        val cleaned = JsonText.extractJsonBlock(text).trim()
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        val firstBrace = cleaned.indexOf('{')
        // ⚠️ 「有没有数组包装」必须确认 `[` 出现在第一个 `{` **之前**。
        // 只看 `indexOf('[') >= 0` 会被**字段内的数组括号**骗过：
        // `{"genre":["摇滚"],"mood":[],"scenario":[]}` 是裸对象，却含 `[`，
        // 于是走进数组分支 → 切出 0 个对象 → 本该生效的裸对象兜底永远不执行。
        // Round 1 的三个字段全是数组，所以模型一旦漏掉数组包装，整轮结果会**静默丢光**。
        val hasArrayWrapper = firstBracket >= 0 && lastBracket > firstBracket &&
            (firstBrace < 0 || firstBracket < firstBrace)
        if (hasArrayWrapper) {
            val arrayContent = cleaned.substring(firstBracket + 1, lastBracket)
            val objs = JsonText.splitJsonObjects(arrayContent)
            HmpLog.i(LogTag.AgentEnrich) { "📚 [$agentId] extractJsonArrayElements: found array [$firstBracket..$lastBracket], split → ${objs.size} objects" }
            return objs
        }
        // 没有正确的数组包装 → 尝试兜底解析
        // 没有数组包装 → 兜底：既可能是单个裸对象，也可能是"对象序列"（`{...},{...}`，无方括号）。
        // 两种情况统一交给 splitJsonObjects（它能正确切），切不出来再整段当单对象试一次 ——
        // 原先写成 `startsWith('{') -> listOf(cleaned)` 会把对象序列当成**一个**对象，
        // 反序列化必然失败，于是同为兜底的第二条分支也被短路掉了。
        val fallback = when {
            cleaned.startsWith('{') ->
                JsonText.splitJsonObjects(cleaned).ifEmpty { listOf(cleaned) }
            cleaned.contains('}') && cleaned.contains('{') -> JsonText.splitJsonObjects(cleaned)
            else -> emptyList()
        }
        HmpLog.w(LogTag.AgentEnrich) { "📚 [$agentId] extractJsonArrayElements: NO array wrapper! cleaned(prefix)=${cleaned.take(200)}, fallback → ${fallback.size} objects" }
        return fallback
    }
}
