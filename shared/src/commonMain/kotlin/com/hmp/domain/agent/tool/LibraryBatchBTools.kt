package com.hmp.domain.agent.tool

import com.hmp.domain.enum.LabelCategory
import kotlinx.coroutines.flow.first

// ---------------- library_tags (read/silent) ----------------

class LibraryTagsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.LIBRARY_TAGS
    override val description = "列出曲库中已存在的风格/情绪/场景标签（按出现次数降序）\n只读，极低成本\n归档/清理决策前先看标签分布"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        EnumParam(
            name = "category",
            allowed = listOf("genre", "mood", "scenario", "all"),
            description = "标签分类（缺省 all 返回三类）",
            required = false,
        ),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val cat = args.optionalString("category") ?: "all"
        val targets = when (cat) {
            "genre" -> listOf(LabelCategory.GENRE)
            "mood" -> listOf(LabelCategory.MOOD)
            "scenario" -> listOf(LabelCategory.SCENARIO)
            else -> listOf(LabelCategory.GENRE, LabelCategory.MOOD, LabelCategory.SCENARIO)
        }
        val lineList = mutableListOf<String>()
        for (c in targets) {
            val labels = repo.getLabelNamesByType(c).first()
            val catZh = when (c) {
                LabelCategory.GENRE -> "风格"
                LabelCategory.MOOD -> "情绪"
                LabelCategory.SCENARIO -> "场景"
                else -> c.name
            }
            lineList += if (labels.isEmpty()) "  （${catZh}：暂无）"
            else "  ${catZh}：${labels.take(10).joinToString("、") { it.displayCn() }}"
        }
        return ToolResult.success("标签概览：\n${lineList.joinToString("\n")}")
    }
}

// ---------------- library_songs_by_tag (read/silent) ----------------

class LibrarySongsByTagTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.LIBRARY_SONGS_BY_TAG
    override val description = "按标签名（中文或英文枚举）查出该类下的所有歌曲\n只读，极低成本\nlibrary_tags 返回的标签名可直接传"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        StringParam(name = "tag_name", description = "标签名（支持中文别名如 爵士、摇滚、深夜、运动）"),
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 30, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val tagName = args.requireString("tag_name")
        val limit = args.optionalInt("limit") ?: DEFAULT_RESULT_LIMIT
        val matched = labelAliasesFor(tagName)
        if (matched.isEmpty()) return ToolResult.failure("未识别标签「$tagName」，请用中文别名或英文枚举")
        val songIds = matched.flatMap { repo.getMusicIdListByType(it) }.distinct().take(limit)
        if (songIds.isEmpty()) return ToolResult.success("标签「$tagName」下暂无歌曲")
        val songs = songIds.mapNotNull { repo.getMusicInfoById(it).first() }
        return ToolResult.success("标签「${matched.first().displayCn()}」下 ${songs.size} 首：\n${songs.summaryLines(limit)}")
    }
}

// ---------------- library_songs_by_artist (read/silent) ----------------

class LibrarySongsByArtistTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.LIBRARY_SONGS_BY_ARTIST
    override val description = "查询某个歌手的全部曲目\n只读，极低成本\nlibrary_artists 返回的歌手名可直接传"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        StringParam(name = "artist", description = "歌手全名"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val artist = args.requireString("artist")
        val songs = repo.getMusicListByArtist(artist)
        if (songs.isEmpty()) return ToolResult.success("曲库中暂无「$artist」的歌曲")
        return ToolResult.success("「$artist」共 ${songs.size} 首：\n${songs.summaryLines(30)}")
    }
}

// ---------------- library_songs_by_album (read/silent) ----------------

class LibrarySongsByAlbumTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.LIBRARY_SONGS_BY_ALBUM
    override val description = "查询某个专辑的全部曲目\n只读，极低成本"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        StringParam(name = "album", description = "专辑全名"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val album = args.requireString("album")
        val songs = repo.getMusicListByAlbum(album)
        if (songs.isEmpty()) return ToolResult.success("曲库中暂无专辑「$album」")
        return ToolResult.success("专辑「$album」共 ${songs.size} 首：\n${songs.summaryLines(30)}")
    }
}

// ---------------- library_artists (read/silent) ----------------
// 需底层 getAllArtistsSummary(limit): List<Pair<String, Int>> 聚合查询。

class LibraryArtistsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.LIBRARY_ARTISTS
    override val description = "列出曲库中全部歌手及其作品数（按作品数降序）\n只读聚合，极低成本\n归档/清理决策前先看分布"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "返回 Top N 歌手", required = false, min = 1, max = 50, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val limit = args.optionalInt("limit") ?: 20
        val summary: List<Pair<String, Int>> = repo.getAllArtistsSummary(limit)
        if (summary.isEmpty()) return ToolResult.success("曲库暂无歌手数据")
        val lines = summary.joinToString("\n") { (artist, count) -> "  $artist × $count" }
        return ToolResult.success("Top ${summary.size} 歌手：\n$lines")
    }
}

// ---------------- library_albums (read/silent) ----------------
// 需底层 getAllAlbumsSummary(limit): List<Pair<String, Int>> 聚合查询。

class LibraryAlbumsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.LIBRARY_ALBUMS
    override val description = "列出曲库中全部专辑及其曲目数（按曲目数降序）\n只读聚合，极低成本"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "返回 Top N 专辑", required = false, min = 1, max = 50, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val limit = args.optionalInt("limit") ?: 20
        val summary: List<Pair<String, Int>> = repo.getAllAlbumsSummary(limit)
        if (summary.isEmpty()) return ToolResult.success("曲库暂无专辑数据")
        val lines = summary.joinToString("\n") { (album, count) -> "  $album × $count" }
        return ToolResult.success("Top ${summary.size} 专辑：\n$lines")
    }
}
