package com.hmp.domain.agent.tool

import com.hmp.domain.setting.model.PlaybackHistory
import kotlinx.coroutines.flow.first

/** 播放历史的静态展示：标题 - 艺术家（数量上限）。
 *  时间戳不格式化（避免跨平台 now 依赖），附原始时间便于粗粒度排序。 */
internal fun List<PlaybackHistory>.historyLines(limit: Int): String =
    take(limit.coerceAtLeast(1)).mapIndexed { i, h ->
        "#${i + 1} musicId=${h.musicId} 播放时长=${h.playDuration / 1000}s completed=${h.isCompleted} 时间戳=${h.playedAt}"
    }.joinToString("\n")

// ---------------- getListenStats (read/silent) ----------------

class GetListenStatsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.GET_LISTEN_STATS
    override val description = "获取用户整体收听统计(总播放/常听/时长/类型分布)\n只读聚合，极低成本\n需要更细粒度时可用 getRecentHistory"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val s = repo.getUserUsageAnalytics()
        val topSongs = s.topPlayedSongs.take(3).joinToString("、") { "${it.title}" }
        return ToolResult.success(
            "累计播放 ${s.totalPlayCount} 次（跳过 ${s.totalSkipCount}），收听 ${s.totalListeningMinutes} 分钟，" +
                "喜爱 ${s.likedCount} 首，本周 ${s.thisWeekMinutes} 分钟；3 首最常听：$topSongs",
            detail = "topGenres=${s.topGenres.take(5).joinToString("、") { "${it.labelDisplayName}×${it.count}" }},\n" +
                "topMoods=${s.topMoods.take(5).joinToString("、") { "${it.labelDisplayName}×${it.count}" }}," +
                "topArtists=${s.topArtists.take(5).joinToString("、") { "${it.artistName}×${it.playCount}" }}",
        )
    }
}

// ---------------- getRecentHistory (read/silent) ----------------

class GetRecentHistoryTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.GET_RECENT_HISTORY
    override val description = "获取最近播放历史列表\n只读，成本极低\n与 getListenStats 叠加可洞察近期偏好"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 50, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val limit = args.optionalInt("limit") ?: DEFAULT_RESULT_LIMIT
        val history = repo.getRecentPlaybackHistoryGlobal(limit)
        if (history.isEmpty()) return ToolResult.success("暂无播放记录")
        val lines = history.mapIndexed { i, h ->
            val song = repo.getMusicInfoById(h.musicId).first()?.music
            val label = if (song != null) "${song.title} - ${song.artist}" else "未知曲目"
            "#${i + 1}《$label》 播放时长=${h.playDuration / 1000}s completed=${h.isCompleted} 时间戳=${h.playedAt}"
        }.joinToString("\n")
        return ToolResult.success("最近播放：\n$lines")
    }
}