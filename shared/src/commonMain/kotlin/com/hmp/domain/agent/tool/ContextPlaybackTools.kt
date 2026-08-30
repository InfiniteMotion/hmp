package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.first

// ---------------- getNowPlayingContext (read/silent) ----------------

class GetNowPlayingContextTool(
    deps: ToolDependencies,
) : AgentTool {
    private val provider = deps.nowPlayingContextProvider

    override val name = ToolNames.GET_NOW_PLAYING_CONTEXT
    override val description = "获取当前正在播放的歌曲及播放状态\n只读，极低成本\n作为相似推荐/播放上下文的锚点"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val ctx = provider.getNowPlaying()
        val info = ctx.currentMusicInfo
        return if (info != null) {
            ToolResult.success(
                "当前播放「${info.music.title} - ${info.music.artist}」" +
                    (if (ctx.isPlaying) "（播放中）" else "（暂停中）") +
                    " ${formatPositionMs(ctx.currentPositionMs)}/${formatPositionMs(ctx.durationMs)}",
            )
        } else {
            ToolResult.success("当前无播放曲目")
        }
    }
}

// ---------------- getSimilarSongs (read/silent) ----------------
// 歌单类工具不在播放指令集内——相似推荐基于曲库计算得出，属只读。

class GetSimilarSongsTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository
    private val provider = deps.nowPlayingContextProvider

    override val name = ToolNames.GET_SIMILAR_SONGS
    override val description = "基于当前/指定歌曲按风格权重推荐相似歌曲\n只读计算，低成本\n说明推荐理由时可引用其风格标签"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        LongParam(name = "musicId", description = "锚点歌曲ID；缺省自动取当前播放", required = false),
        IntParam(name = "limit", description = "返回条数", required = false, min = 1, max = 20, clamp = true),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val anchorId = args.optionalLong("musicId")
            ?: provider.getNowPlaying().currentMusicId
            ?: return ToolResult.failure("缺少锚点歌曲：请指定 musicId 或先有正在播放的歌曲")
        val limit = args.optionalInt("limit") ?: DEFAULT_RESULT_LIMIT
        val similar = repo.getSimilarSongsByWeightedLabels(anchorId, limit)
        if (similar.isEmpty()) return ToolResult.success("未找到与歌曲 $anchorId 足够相似的歌曲")
        return ToolResult.success("与「${anchorId}」相似的歌曲：\n${similar.summaryLines(limit)}")
    }
}

// ---------------- getMusicExtra (read/silent) ----------------

class GetMusicExtraTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.GET_MUSIC_EXTRA
    override val description = "查看单曲已保存的富化资料(风格/情绪/歌词等)\n只读，极低成本\n尚未富化时不返回标签类信息"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = listOf(
        LongParam(name = "musicId", description = "歌曲ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val musicId = args.requireLong("musicId")
        val extra = repo.getMusicExtraById(musicId)
        if (!extra.errorInfo.isNullOrBlank()) {
            return ToolResult.failure("歌曲 $musicId 尚未获得富化资料：${extra.errorInfo.take(120)}")
        }
        return ToolResult.success(
            "歌曲 $musicId 富化资料：风格 ${extra.genre.take(4).joinToString("、")}、" +
                "情绪 ${extra.mood.take(4).joinToString("、")}、场景 ${extra.scenario.take(4).joinToString("、")}、" +
                "语言 ${extra.language}、年代 ${extra.era}",
        )
    }
}

// ---------------- controlPlayback (write/confirm) ----------------

class ControlPlaybackTool(
    deps: ToolDependencies,
) : AgentTool {
    private val port = deps.playbackCommandPort

    override val name = ToolNames.CONTROL_PLAYBACK
    override val description = "控制当前播放：\n- play=继续播放当前 / pause=暂停 / next=下一首 / previous=上一首\n- play_by_id=播放指定曲目（music_id 填 searchLibrary/getSimilarSongs 结果里的 id）\n- seek_to=跳转到 position_ms；shuffle_on/off、repeat_one/repeat_all/repeat_off 切模式\n影响当前播放，属写操作\n用户要播某首/某歌手的歌时用 play_by_id + 检索到的 id；只有「继续/暂停当前」才用 play/pause"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        EnumParam(
            name = "command",
            allowed = listOf(
                "play", "pause", "next", "previous",
                "seek_to", "play_by_id",
                "shuffle_on", "shuffle_off",
                "repeat_one", "repeat_all", "repeat_off",
            ),
            description = "播放控制指令",
        ),
        LongParam(name = "position_ms", description = "seek_to 的目标位置(毫秒)", required = false, min = 0),
        LongParam(name = "music_id", description = "play_by_id 的目标歌曲ID（取 searchLibrary/getSimilarSongs 结果的 id=；仅为 play_by_id 必填）", required = false, min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val command = args.requireString("command")
        val built = buildCommand(command, args) ?: return ToolResult.failure("指令「$command」缺少必要参数")
        val (ok, text) = port.execute(built)
        return if (ok) ToolResult.success("已执行：${built.displayName}")
        else ToolResult.failure("播放控制失败：$text")
    }

    private fun buildCommand(raw: String, args: ToolArgs): PlaybackCommand? = when (raw) {
        "play" -> PlaybackCommand.PLAY
        "pause" -> PlaybackCommand.PAUSE
        "next" -> PlaybackCommand.NEXT
        "previous" -> PlaybackCommand.PREVIOUS
        "seek_to" -> args.optionalLong("position_ms")?.let { PlaybackCommand.SEEK_TO(it) }
        "play_by_id" -> args.optionalLong("music_id")?.let { PlaybackCommand.PLAY_BY_ID(it) }
        "shuffle_on" -> PlaybackCommand.SHUFFLE_ON
        "shuffle_off" -> PlaybackCommand.SHUFFLE_OFF
        "repeat_one" -> PlaybackCommand.REPEAT_ONE_ON
        "repeat_all" -> PlaybackCommand.REPEAT_ALL_ON
        "repeat_off" -> PlaybackCommand.REPEAT_OFF
        else -> null
    }
}