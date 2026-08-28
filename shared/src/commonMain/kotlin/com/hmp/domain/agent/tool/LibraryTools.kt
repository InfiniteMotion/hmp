package com.hmp.domain.agent.tool

import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/** 把音乐简要化为「标题 - 艺术家」文本（供回填上下文）。 */
internal fun MusicInfo.songDisplay(): String = "${music.title} - ${music.artist}"

/** 前三首略去重复，控制在上下文行数。 */
internal fun List<MusicInfo>.summaryLines(limit: Int): String =
    take(limit.coerceAtLeast(1)).joinToString("\n") { it.songDisplay() }

// ---------------- searchLibrary (read/silent) ----------------

class SearchLibraryTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.SEARCH_LIBRARY
    override val description = "在本地曲库中按关键词搜索歌曲(标题/艺术家)\n极少读数，几乎无成本\n改为精确指定歌曲时可直接描述曲名"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        StringParam(name = "query", description = "搜索关键词(标题或艺术家)"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val query = args.requireString("query")
        val found = repo.searchMusic(query).take(DEFAULT_RESULT_LIMIT)
        if (found.isEmpty()) return ToolResult.success("未在曲库中匹配到查询「$query」")
        val lines = found.summaryLines(DEFAULT_RESULT_LIMIT)
        return ToolResult.success("曲库检索「$query」命中 ${found.size} 首：\n$lines")
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