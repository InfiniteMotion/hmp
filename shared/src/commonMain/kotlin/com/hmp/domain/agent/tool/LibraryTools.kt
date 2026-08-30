package com.hmp.domain.agent.tool

import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/** 把音乐简要化为「标题 - 艺术家」文本（供回填上下文）。 */
internal fun MusicInfo.songDisplay(): String = "${music.title} - ${music.artist}"

/** 检索结果行：带序号与 music_id（id 供 controlPlayback.play_by_id 使用），控制在上下文行数。 */
internal fun List<MusicInfo>.summaryLines(limit: Int): String =
    take(limit.coerceAtLeast(1)).withIndex().joinToString("\n") { (i, it) ->
        "${i + 1}. ${it.music.title} - ${it.music.artist} (id=${it.music.id})"
    }

/** 标签过滤上限（避免标签命中大量曲目时 N 次查询）。 */
private const val MAX_LABEL_RESULTS = 10

/** 中文别名 / 英文枚举名 → LabelName。标签显示名是英文枚举(如 JAZZ)，查询词常为中文(爵士)。 */
private val LABEL_ALIASES: Map<String, LabelName> = mapOf(
    "rock" to LabelName.ROCK, "摇滚" to LabelName.ROCK,
    "pop" to LabelName.POP, "流行" to LabelName.POP,
    "jazz" to LabelName.JAZZ, "爵士" to LabelName.JAZZ,
    "classical" to LabelName.CLASSICAL, "古典" to LabelName.CLASSICAL, "古典乐" to LabelName.CLASSICAL,
    "hiphop" to LabelName.HIPHOP, "嘻哈" to LabelName.HIPHOP,
    "electronic" to LabelName.ELECTRONIC, "电子" to LabelName.ELECTRONIC,
    "folk" to LabelName.FOLK, "民谣" to LabelName.FOLK,
    "rnb" to LabelName.RNB,
    "metal" to LabelName.METAL, "金属" to LabelName.METAL,
    "country" to LabelName.COUNTRY, "乡村" to LabelName.COUNTRY,
    "blues" to LabelName.BLUES, "蓝调" to LabelName.BLUES,
    "reggae" to LabelName.REGGAE,
    "punk" to LabelName.PUNK, "朋克" to LabelName.PUNK,
    "funk" to LabelName.FUNK,
    "soul" to LabelName.SOUL, "灵魂" to LabelName.SOUL,
    "indie" to LabelName.INDIE, "独立" to LabelName.INDIE,
    "happy" to LabelName.HAPPY, "开心" to LabelName.HAPPY, "高兴" to LabelName.HAPPY,
    "sad" to LabelName.SAD, "悲伤" to LabelName.SAD, "伤感" to LabelName.SAD,
    "energetic" to LabelName.ENERGETIC, "激昂" to LabelName.ENERGETIC, "燃" to LabelName.ENERGETIC,
    "calm" to LabelName.CALM, "平静" to LabelName.CALM, "安静" to LabelName.CALM, "舒缓" to LabelName.CALM,
    "romantic" to LabelName.ROMANTIC, "浪漫" to LabelName.ROMANTIC,
    "angry" to LabelName.ANGRY, "愤怒" to LabelName.ANGRY,
    "lonely" to LabelName.LONELY, "孤独" to LabelName.LONELY,
    "uplifting" to LabelName.UPLIFTING, "励志" to LabelName.UPLIFTING,
    "mysterious" to LabelName.MYSTERIOUS, "神秘" to LabelName.MYSTERIOUS,
    "dark" to LabelName.DARK, "黑暗" to LabelName.DARK,
    "melancholy" to LabelName.MELANCHOLY, "忧郁" to LabelName.MELANCHOLY,
    "hopeful" to LabelName.HOPEFUL, "希望" to LabelName.HOPEFUL,
    "workout" to LabelName.WORKOUT, "运动" to LabelName.WORKOUT,
    "sleep" to LabelName.SLEEP, "睡眠" to LabelName.SLEEP, "睡前" to LabelName.SLEEP,
    "party" to LabelName.PARTY, "聚会" to LabelName.PARTY,
    "driving" to LabelName.DRIVING, "驾车" to LabelName.DRIVING, "开车" to LabelName.DRIVING,
    "study" to LabelName.STUDY, "学习" to LabelName.STUDY, "工作" to LabelName.STUDY,
    "relax" to LabelName.RELAX, "放松" to LabelName.RELAX,
    "dinner" to LabelName.DINNER, "晚餐" to LabelName.DINNER,
)

/** 查询词中命中的标签（含中文别名与英文枚举名）。 */
private fun labelAliasesFor(query: String): List<LabelName> {
    val q = query.lowercase()
    return LABEL_ALIASES.entries
        .filter { (alias, _) -> q.contains(alias.lowercase()) }
        .map { it.value }
        .distinct()
}

// ---------------- searchLibrary (read/silent) ----------------

class SearchLibraryTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.SEARCH_LIBRARY
    override val description = "在本地曲库中按关键词搜索歌曲(标题/艺术家)，结果每行带 id（供 play_by_id 播放）；查询命中风格/情绪/场景标签时也会返回该类曲目\n极少读数，几乎无成本\n用户要播某首/某歌手的歌时，先搜这里拿到该曲的 id，再用控制播放的 play_by_id"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        StringParam(name = "query", description = "搜索关键词(标题/艺术家/或风格情绪场景标签如 爵士/摇滚/深夜/运动)"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val query = args.requireString("query")
        // 1) 标题/艺术家 LIKE
        val byText = repo.searchMusic(query)
        // 2) 标签过滤（查询词命中风格/情绪/场景标签名 → 返回该类曲目）
        val byLabel = labelAliasesFor(query)
            .flatMap { label -> repo.getMusicIdListByType(label) }
            .distinct()
            .take(MAX_LABEL_RESULTS)
            .mapNotNull { repo.getMusicInfoById(it).first() }
        val merged = (byText + byLabel).distinctBy { it.music.id }
        if (merged.isEmpty()) return ToolResult.success("未在曲库中匹配到查询「$query」")
        val lines = merged.summaryLines(DEFAULT_RESULT_LIMIT)
        return ToolResult.success("曲库检索「$query」命中 ${merged.size} 首：\n$lines")
    }
}

// ---------------- enrichSong (notify) ----------------

class EnrichSongTool(
    private val deps: ToolDependencies,
) : AgentTool {
    override val name = ToolNames.ENRICH_SONG
    override val description = "为单曲生成 AI 富化信息(风格/情绪/场景/歌词简介)\n涉及云端调用，有成本与耗时\n建议【已开唱】或用户主动询问风格时再用"
    override val permissionLevel = ToolPermissionLevel.NOTIFY
    override val params = listOf(
        StringParam(name = "title", description = "歌曲标题"),
        StringParam(name = "artist", description = "艺术家", required = false, allowEmpty = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val title = args.requireString("title")
        val artist = args.optionalString("artist").orEmpty()
        val result = deps.enrichPort.enrich(title, artist)
        return result.fold(
            onSuccess = { info ->
                ToolResult.success(
                    "「$title」富贵化完成：风格 ${info.genre.take(4).joinToString("、")}、" +
                        "情绪 ${info.mood.take(4).joinToString("、")}、场景 ${info.scenario.take(4).joinToString("、")}",
                    detail = listOf(
                        "era=${info.era}",
                        "language=${info.language}",
                        "description=${info.description.take(120)}",
                    ).joinToString("\n"),
                )
            },
            onFailure = { e -> ToolResult.failure("「$title」富贵化失败：${e.message ?: "未知原因"}") },
        )
    }
}