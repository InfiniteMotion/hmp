package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.domain.agent.card.RecommendItemRecord
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.card.TimePhase
import com.hmp.domain.agent.card.zhName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 曲库视图里的一行（喂给模型的最小字段集：不传歌词、不传完整路径）。 */
data class RecommendCandidate(
    val id: Long,
    val title: String,
    val artist: String?,
    val labels: List<String> = emptyList(),
    val playCount: Int,
    val liked: Boolean,
)

/** 模型返回的选择结果。 */
data class RecommendSelection(
    val overview: String,
    val items: List<RecommendItemRecord>,
)

/**
 * 每日两单的"模型在曲库视图内编排"（D3，radio 式）。
 *
 * 与 radio 开播的差别：不加"在播锚点"、不要求续排动作，只要求选 N 首 + 总述 + 每首一句。
 * 本地算法结果作为**参考段**给模型，可整体替换。
 *
 * 只做纯函数：拼 prompt、解析并校验 JSON。不碰仓库、不碰 LLM 调用。
 */
object HelloRecommendSelector {

    /** 曲库段的 token 预算（粗略：每行约 36 token）。超出时按播放次数取前 N 行并注明省略。 */
    const val LIBRARY_TOKEN_BUDGET: Int = 24_000
    const val TOKENS_PER_ROW: Int = 36
    const val MIN_LIBRARY_ROWS: Int = 40

    fun libraryRowBudget(): Int =
        (LIBRARY_TOKEN_BUDGET / TOKENS_PER_ROW).coerceAtLeast(MIN_LIBRARY_ROWS)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun buildPrompt(
        source: RecommendSource,
        phase: TimePhase,
        localPicks: List<RecommendCandidate>,
        library: List<RecommendCandidate>,
        totalCount: Int,
        omittedCount: Int,
        count: Int,
    ): String {
        val sourceDesc = when (source) {
            RecommendSource.DAILY -> "每日推荐"
            RecommendSource.PRIVATE -> "私人推荐（基于收藏与近期收听）"
        }
        val localIds = localPicks.map { it.id }.toSet()
        val rest = library.filter { it.id !in localIds }

        return buildString {
            appendLine("## 情境")
            appendLine("- 来源：$sourceDesc")
            appendLine("- 时段：${phase.zhName()}")
            appendLine("- 曲库：共 $totalCount 首")
            appendLine()
            appendLine("## 本地算法推荐（参考，可整体替换）")
            if (localPicks.isEmpty()) {
                appendLine("（无）")
            } else {
                localPicks.forEachIndexed { index, c ->
                    appendLine(row(index + 1, c, star = true))
                }
            }
            appendLine()
            appendLine(
                if (omittedCount > 0) {
                    "## 曲库（共 $totalCount 首，受上下文限制仅列 ${library.size} 首，已省略 $omittedCount 首）"
                } else {
                    "## 曲库（共 $totalCount 首，以下 ${rest.size} 首为本地推荐之外的曲目）"
                }
            )
            if (rest.isEmpty()) {
                appendLine("（无其他曲目）")
            } else {
                rest.forEachIndexed { index, c -> appendLine(row(index + 1, c, star = false)) }
            }
            appendLine()
            appendLine("## 任务")
            appendLine("请从上面的本地推荐与曲库中选出 $count 首，重排成播放顺序。")
            appendLine("规则：")
            appendLine("  · 本地推荐只是参考，可以全部采用、部分替换或整体重排")
            appendLine("  · 只能使用上面出现过的 id，不得编造")
            appendLine("  · 不要选重复 id；顺序即播放顺序")
            appendLine("输出严格 JSON（不要代码块、不要解释）：")
            appendLine("""{"overview":"不超过40字的开场","items":[{"id":123,"reason":"不超过24字的推荐语"}]}""")
        }
    }

    private fun row(index: Int, c: RecommendCandidate, star: Boolean): String {
        val artist = c.artist?.takeIf { it.isNotBlank() } ?: "未知艺人"
        val labels = c.labels.takeIf { it.isNotEmpty() }?.joinToString("/")?.let { " | 标签:$it" }.orEmpty()
        val liked = if (c.liked) " · 已收藏" else ""
        val mark = if (star) "★ " else ""
        return "[$index] $mark id=${c.id} | ${c.title} — $artist$labels | 播放 ${c.playCount} 次$liked"
    }

    /**
     * 解析模型输出。
     *
     * @param allowedIds 曲库视图 + 本地参考里出现过的 id；不在其中的条目直接丢弃
     * @param max 最多保留条数
     * @return null = 不可用（非 JSON / 无有效条目），调用方回落本地保底
     */
    fun parse(raw: String, allowedIds: Set<Long>, max: Int): RecommendSelection? {
        val body = extractJsonObject(raw) ?: return null
        val obj: JsonObject = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null

        val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.take(MAX_OVERVIEW_CHARS)
            .orEmpty()

        val array = obj["items"]?.jsonArray ?: return null
        val seen = LinkedHashSet<Long>()
        val items = ArrayList<RecommendItemRecord>(max)
        for (element in array) {
            if (items.size >= max) break
            val item = runCatching { element.jsonObject }.getOrNull() ?: continue
            val id = item["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
            if (id !in allowedIds || !seen.add(id)) continue
            val reason = item["reason"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.take(MAX_REASON_CHARS)
                .orEmpty()
            items += RecommendItemRecord(trackId = id, reason = reason.ifBlank { "为你挑的" })
        }
        return if (items.isEmpty()) null else RecommendSelection(overview = overview, items = items)
    }

    /** 去掉 ```json 围栏，截取第一个 `{` 到最后一个 `}`。 */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    const val MAX_OVERVIEW_CHARS: Int = 40
    const val MAX_REASON_CHARS: Int = 24
}
