package com.hmp.test.fakes

import com.hmp.domain.agent.port.ConfirmGate
import com.hmp.domain.agent.port.ConfirmOutcome
import com.hmp.domain.agent.port.ConfirmRequest
import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.runtime.StopSignal
import com.hmp.domain.agent.runtime.SystemConditions
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.spec.AgentTool
import com.hmp.domain.agent.tool.spec.ToolArgs
import com.hmp.domain.agent.tool.spec.ToolParam
import com.hmp.domain.agent.tool.spec.ToolResult
import com.hmp.domain.agent.port.ToolPermissionLevel
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

// ───────────────────────── 确认门 Fake ─────────────────────────

/**
 * 脚本化确认门：按策略对每条 ConfirmRequest 返回决策；记录所有请求供断言。
 */
class FakeConfirmGate(
    private val strategy: (ConfirmRequest) -> ConfirmOutcome = { ConfirmOutcome.AllowOnce },
) : ConfirmGate {
    val requestBatches = mutableListOf<List<ConfirmRequest>>()
    override suspend fun request(reqs: List<ConfirmRequest>): List<ConfirmOutcome> {
        requestBatches += reqs
        return reqs.map { strategy(it) }
    }
}

// ───────────────────────── 停止信号 Fake ─────────────────────────

/**
 * 可脚本化的 StopSignal 替身（ReActLoop / SchedulerStopSignal 测试用）。
 * 实现引擎的 internal 接口 [StopSignal]，同模块可见。
 */
class FakeStopSignal(
    private val softStop: Boolean = false,
) : StopSignal {
    override fun shouldSoftStop(): Boolean = softStop
    override suspend fun waitResume() {}
}

// ───────────────────────── 系统条件 Fake ─────────────────────────

/**
 * 可脚本化的系统条件替身（AgentScheduler 调度规则测试用）。
 * SystemConditions 是 runtime 包的顶层 internal 接口，同模块测试可见。
 */
class FakeSystemConditions(
    var batteryLevelValue: Float = 1.0f,
    var wifiConnected: Boolean = true,
) : SystemConditions {
    override fun batteryLevel(): Float = batteryLevelValue
    override fun isWifiConnected(): Boolean = wifiConnected
}

/**
 * 全收型播放端口替身：所有指令照单全收并返回成功。
 * 引擎层测试（ReActLoop / Scheduler）不关心播放指令内容，只要 ToolDependencies 可装配。
 */
object FakePlaybackCommandPort : PlaybackCommandPort {
    override suspend fun execute(command: PlaybackCommand, source: CommandSource): Pair<Boolean, String> =
        true to "ok"
}

// ───────────────────────── 工具 Fake ─────────────────────────

/**
 * 记录型工具：[execute] 直接捕获原始参数（绕过 schema 校验，测试工具回传链路用），
 * 返回成功结果。便于断言 ReActLoop 是否真的把工具调用结果回传给了 LLM。
 */
class RecordingTool(
    override val name: String = "recording_tool",
    override val permissionLevel: ToolPermissionLevel = ToolPermissionLevel.SILENT,
    private val resultSummary: String = "recorded",
) : AgentTool {
    val receivedArgs = mutableListOf<JsonObject>()
    override val description: String = "test recording tool"
    override val params: List<ToolParam> = emptyList()
    override suspend fun run(args: ToolArgs): ToolResult = ToolResult.success(resultSummary)
    override suspend fun execute(arguments: JsonObject): ToolResult {
        receivedArgs += arguments
        return ToolResult.success(resultSummary)
    }
}

/** 执行即抛异常的工具，验证 ToolCallExecutor 的失败兜底分支。 */
class ThrowingTool(
    override val name: String = "throwing_tool",
    override val permissionLevel: ToolPermissionLevel = ToolPermissionLevel.SILENT,
) : AgentTool {
    override val description: String = "throws on run"
    override val params: List<ToolParam> = emptyList()
    override suspend fun run(args: ToolArgs): ToolResult = throw RuntimeException("boom")
}

// ───────────────────────── 测试装配辅助 ─────────────────────────

/** 一个空 AiEndpointConfig，仅用于让编译期签名满足（FakeLlmTransport 不会真正发请求）。 */
fun testConfig(): AiEndpointConfig = AiEndpointConfig()

/** 构造测试用 ToolDependencies：默认全部接内存 Fake，避免触碰真实数据库/播放器。 */
fun testToolDependencies(
    musicRepository: com.hmp.domain.music.MusicRepository = FakeAgentMusicRepository(),
    playlistRepository: com.hmp.domain.playlist.PlaylistRepository = FakeAgentPlaylistRepository(),
    settingsRepository: com.hmp.domain.setting.SettingsRepository = FakeSettingsRepository(),
): ToolDependencies = ToolDependencies(
    musicRepository = musicRepository,
    playlistRepository = playlistRepository,
    settingsRepository = settingsRepository,
    nowPlayingContextProvider = FakeNowPlayingContextProvider,
    playbackCommandPort = FakePlaybackCommandPort,
)

/** 在 FakeAgentMusicRepository 中塞一首歌，返回构造好的 MusicInfo。 */
fun seedMusic(
    repo: FakeAgentMusicRepository,
    id: Long,
    title: String,
    artist: String = "Artist$id",
): MusicInfo {
    val info = MusicInfo(
        music = Music(id, title, artist, "Album$id", 180_000, "/path/$id.mp3", ""),
        extra = null,
        userInfo = null,
    )
    repo.songs[id] = info
    return info
}

/** 脚本化 LlmTransport：每轮重放同一序列。 */
fun scriptedTransport(vararg events: com.hmp.domain.agent.port.LlmEvent): LlmTransport =
    FakeLlmTransport(script = events.toList())

/** 脚本化 LlmTransport：按轮次消费（多轮 tool-loop 场景）。 */
fun perTurnTransport(vararg turns: List<com.hmp.domain.agent.port.LlmEvent>): LlmTransport =
    FakeLlmTransport(perTurnScript = turns.map { it })

/** 调用即失败的 LlmTransport（模拟 HTTP/网络错误路径）。 */
fun failingTransport(): LlmTransport = FakeLlmTransport(failOnCall = true)

/** 构造一条 user 消息。 */
fun userMsg(content: String): LlmMessage = LlmMessage(role = "user", content = content)
