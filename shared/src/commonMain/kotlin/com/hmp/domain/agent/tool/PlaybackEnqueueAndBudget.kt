package com.hmp.domain.agent.tool

// ---------------- playback_enqueue (write/confirm) ----------------
// 追加曲目到当前播放队列尾部（不切换当前播放对象，区别于 playback_play_at）。

import com.hmp.domain.agent.port.PlaybackCommand

class PlaybackEnqueueTool(
    deps: ToolDependencies,
) : AgentTool {
    private val port = deps.playbackCommandPort

    override val name = ToolNames.PLAYBACK_ENQUEUE
    override val description = "追加曲目到当前播放队列尾部（不切换当前播放对象）\n写操作，修改播放队列\n与 playback_play_at 区别：play_at 替换当前播放指针，本工具只追加"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "music_id", description = "要追加到队列尾部的歌曲ID", min = 1),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val musicId = args.requireLong("music_id")
        val (ok, text) = port.execute(PlaybackCommand.ADD_TO_QUEUE(musicId))
        return if (ok) ToolResult.success("已把曲目 $musicId 追加到播放队列尾部")
        else ToolResult.failure("追加入队失败：$text")
    }
}

// ---------------- agent_budget (read/silent) ----------------
// 返回当前云端配额快照（今日已用 / 剩余 / 是否本地兜底）。
// 注：需要 ToolDependencies 加 contextBudget 依赖——编译报错时补。

class AgentBudgetTool(
    deps: ToolDependencies,
) : AgentTool {
    // Stub：暂不扩 ToolDependencies 加 ContextBudget，返回占位快照
    private val nowPlaying = deps.nowPlayingContextProvider

    override val name = ToolNames.AGENT_BUDGET
    override val description = "返回 agent 会话配额快照（当前为占位实现）\n只读快照，极低成本"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val ctx = nowPlaying.getNowPlaying()
        return ToolResult.success(
            "Agent 配额（占位）：\n" +
                "  当前播放：${ctx.currentMusicInfo?.music?.title ?: "无"}\n" +
                "  播放状态：${if (ctx.isPlaying) "播放中" else "已暂停"}\n" +
                "  注：agent_budget 完整实现需扩 ToolDependencies 注入 ContextBudget"
        )
    }
}
