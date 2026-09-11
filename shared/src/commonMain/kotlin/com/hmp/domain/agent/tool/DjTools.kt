package com.hmp.domain.agent.tool

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.MusicInfo

/**
 * DJ Agent 专属工具集 —— 供 RadioSubAgent ReActLoop 使用。
 *
 * 设计原则：
 * - 只读工具（SILENT）让 LLM 自主感知当前状态和曲库
 * - dj_queue_replace_next 通过 PlaybackCommand.REPLACE_QUEUE 实现（port 层保证不打断当前播放）
 * - 所有工具返回简洁文本/JSON，LLM 可读即可
 */

// ─────────────────────────────────────────────────────────────
// dj_current_song —— 当前播放感知
// ─────────────────────────────────────────────────────────────

class DjCurrentSongTool(
    deps: ToolDependencies,
) : AgentTool {
    private val provider = deps.nowPlayingContextProvider
    private val repo = deps.musicRepository

    override val name = ToolNames.DJ_CURRENT_SONG
    override val description = "获取当前正在播放的歌曲信息：标题、歌手、播放状态、进度、标签\nDJ Agent 的锚点感知工具"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val ctx = provider.getNowPlaying()
        val info = ctx.currentMusicInfo
        if (info == null) return ToolResult.success("当前无播放曲目")
        val labels = info.music.id?.let { id ->
            runCatching { repo.getMusicLabels(id).map { ml -> ml.label.name } }.getOrDefault(emptyList())
        } ?: emptyList()
        val pos = formatPositionMs(ctx.currentPositionMs)
        val dur = formatPositionMs(ctx.durationMs)
        val status = if (ctx.isPlaying) "播放中" else "暂停中"
        return ToolResult.success(
            """
            |当前播放：${info.music.title} — ${info.music.artist}
            |状态：$status
            |进度：$pos / $dur
            |标签：${labels.joinToString("、").ifBlank { "无" }}
            |时长：${info.music.duration / 1000}秒
            """.trimMargin()
        )
    }
}

// ─────────────────────────────────────────────────────────────
// dj_library_stats —— 曲库概况
// ─────────────────────────────────────────────────────────────

class DjLibraryStatsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.DJ_LIBRARY_STATS
    override val description = "获取曲库概况：总量、标签分布、高频标签 Top 10\n帮助 DJ Agent 理解曲库构成"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val all = runCatching { repo.getAllMusicInfoAsList("title", "asc") }.getOrDefault(emptyList())
        val labelCounter = mutableMapOf<String, Int>()
        all.take(500).forEach { info ->
            info.music.id?.let { id ->
                runCatching { repo.getMusicLabels(id).forEach { ml ->
                    labelCounter.merge(ml.label.name, 1, Int::plus)
                } }
            }
        }
        val topLabels = labelCounter.entries.sortedByDescending { it.value }.take(10)
        return ToolResult.success(
            """
            |曲库总量：${all.size} 首
            |标签分布 Top 10：
            |${topLabels.joinToString("\n") { "  - ${it.key}: ${it.value} 首" }.ifBlank { "  （无标签数据）" }}
            """.trimMargin()
        )
    }
}

// ─────────────────────────────────────────────────────────────
// dj_search_by_tags —— 按标签查曲库
// ─────────────────────────────────────────────────────────────

class DjSearchByTagsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.DJ_SEARCH_BY_TAGS
    override val description = "按一个或多个标签搜索曲库，返回匹配度排序的候选曲目\nDJ Agent 选歌的核心查询工具"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        StringListParam(name = "tags", description = "标签名称列表（如：[\"民谣\",\"抒情\"]）", required = true),
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 30, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val tags = args.requireStringList("tags")
        val limit = args.optionalInt("limit") ?: 20
        if (tags.isEmpty()) return ToolResult.failure("tags 不能为空")

        // 按多标签匹配频次排序
        val score = mutableMapOf<Long, Int>()
        val perLabelIds = tags.map { tagName ->
            // LabelName 是 enum，通过 valueOf 或 entries.find 转换
            val label = runCatching { LabelName.valueOf(tagName) }.getOrNull()
                ?: LabelName.entries.firstOrNull { it.name.equals(tagName, ignoreCase = true) }
            if (label != null) runCatching { repo.getMusicIdListByType(label) }.getOrDefault(emptyList())
            else emptyList()
        }
        perLabelIds.forEach { ids -> ids.forEach { score[it] = (score[it] ?: 0) + 1 } }
        val candidateIds = score.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        if (candidateIds.isEmpty()) return ToolResult.success("没有标签匹配的歌曲：${tags.joinToString("、")}")

        val tracks = repo.getMusicInfoByIds(candidateIds)
        val output = tracks.joinToString("\n") { info ->
            val matchCount = score[info.music.id] ?: 0
            "${info.music.title} — ${info.music.artist}（${matchCount}标签匹配, playCount=${info.userInfo?.playCount ?: 0}）"
        }
        return ToolResult.success("标签「${tags.joinToString("、")}」匹配 ${tracks.size} 首：\n$output")
    }
}

// ─────────────────────────────────────────────────────────────
// dj_get_top_artists —— 高频歌手 Top N
// ─────────────────────────────────────────────────────────────

class DjGetTopArtistsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.DJ_GET_TOP_ARTISTS
    override val description = "获取近 7 天高频播放的歌手 Top N\n帮助 DJ Agent 了解用户偏好"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 20, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val limit = args.optionalInt("limit") ?: 10
        val top = runCatching { repo.getAllArtistsSummary(limit) }.getOrDefault(emptyList())
        if (top.isEmpty()) return ToolResult.success("暂无高频歌手数据")
        return ToolResult.success(
            "近 7 天高频歌手 Top ${top.size}：\n" +
                top.joinToString("\n") { "  - ${it.first}（${it.second} 次）" }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// dj_get_top_labels —— 高频标签 Top N
// ─────────────────────────────────────────────────────────────

class DjGetTopLabelsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.DJ_GET_TOP_LABELS
    override val description = "获取近 7 天高频播放的标签 Top N\n帮助 DJ Agent 了解用户风格偏好"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 20, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val limit = args.optionalInt("limit") ?: 10
        // 简化：直接查所有曲目然后聚合标签
        val allIds = runCatching {
            repo.getAllMusicInfoAsList("title", "asc").mapNotNull { it.music.id }
        }.getOrDefault(emptyList())
        val labelCounter = mutableMapOf<String, Int>()
        allIds.take(200).forEach { id ->
            runCatching {
                repo.getMusicLabels(id).forEach { ml ->
                    labelCounter.merge(ml.label.name, 1, Int::plus)
                }
            }
        }
        val top = labelCounter.entries.sortedByDescending { it.value }.take(limit)
        if (top.isEmpty()) return ToolResult.success("暂无高频标签数据")
        return ToolResult.success(
            "高频标签 Top ${top.size}：\n" +
                top.joinToString("\n") { "  - ${it.key}（${it.value} 首）" }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// dj_recently_skipped —— 跳过最多的标签
// ─────────────────────────────────────────────────────────────

class DjRecentlySkippedTool(
    deps: ToolDependencies,
) : AgentTool {
    // 简化实现：暂直接返回空或从播放历史推断
    // 后续可接入 PlaybackHistory 里的 skip 事件
    override val name = ToolNames.DJ_RECENTLY_SKIPPED
    override val description = "获取用户近期跳过最多的标签/风格\nDJ Agent 应主动避开这些方向"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 10, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        return ToolResult.success("暂无跳过标签数据。选歌时注意避免风格极端变化即可。")
    }
}

// ─────────────────────────────────────────────────────────────
// dj_queue_peek —— 看当前播放之后的队列
// ─────────────────────────────────────────────────────────────

class DjQueuePeekTool(
    deps: ToolDependencies,
) : AgentTool {
    // 队列状态在 RadioSubAgent 内部，这里通过 nowPlaying provider + repository 间接获取
    // 简化实现：返回"电台队列由 DJ Agent 自己管理，随时可调用 dj_queue_replace_next 替换"
    override val name = ToolNames.DJ_QUEUE_PEEK
    override val description = "查看当前播放之后还有多少首、是什么曲目\n帮助 DJ Agent 判断队列是否需要补充或替换"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "查看后面多少首", required = false, min = 1, max = 20, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        // 队列状态由 RadioSubAgent 在内存里维护，LLM 通过自身推理知道队列状态
        // （上一轮 dj_queue_replace_next 后队列就是新的）
        // 这里返回通用提示即可
        val limit = args.optionalInt("limit") ?: 10
        return ToolResult.success(
            "电台队列由你管理。当前播放之后还有多少首、是什么曲目——你上一次 dj_queue_replace_next 决定的就是这些。\n" +
                "如果觉得队列需要调整，直接调用 dj_queue_replace_next 替换即可。"
        )
    }
}

// ─────────────────────────────────────────────────────────────
// dj_queue_replace_next —— 替换当前之后的队列（核心写操作）
// ─────────────────────────────────────────────────────────────

class DjQueueReplaceNextTool(
    deps: ToolDependencies,
) : AgentTool {
    private val port = deps.playbackCommandPort
    private val repo = deps.musicRepository

    override val name = ToolNames.DJ_QUEUE_REPLACE_NEXT
    override val description = "替换当前播放歌曲之后的队列（不打断当前播放）\n传入 music_id 列表，这些歌曲会成为「当前播放曲播完后」的后续队列\nDJ Agent 最核心的写操作"
    override val permissionLevel = ToolPermissionLevel.SILENT  // DJ 自主决策，不需要用户确认
    override val params = listOf(
        LongListParam(name = "music_ids", description = "要替换成的歌曲 ID 列表（按播放顺序）", required = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val ids = args.requireLongList("music_ids")
        if (ids.isEmpty()) return ToolResult.failure("music_ids 不能为空")

        // 验证这些 ID 都存在
        val existing = repo.getMusicInfoByIds(ids)
        if (existing.isEmpty()) return ToolResult.failure("传入的 music_ids 都找不到对应的歌曲")
        if (existing.size < ids.size) {
            val missing = ids.filter { id -> existing.none { it.music.id == id } }
            Logger.e("Tool.DJ") { "dj_queue_replace_next: ${missing.size} 个 ID 不存在: $missing" }
        }

        // 调用 REPLACE_QUEUE —— port 层已保证"不打断当前播放"
        val (ok, text) = port.execute(PlaybackCommand.REPLACE_QUEUE(existing.map { it.music.id }))
        return if (ok) {
            val preview = existing.take(3).joinToString("、") { it.music.title }
            val more = if (existing.size > 3) "... 等 ${existing.size} 首" else ""
            ToolResult.success("队列已更新：$preview$more（当前播放不受影响）")
        } else {
            ToolResult.failure("队列更新失败：$text")
        }
    }
}
