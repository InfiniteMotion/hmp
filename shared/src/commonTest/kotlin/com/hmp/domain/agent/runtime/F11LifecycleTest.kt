package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.KeepAliveReason
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeAgentPlaylistRepository
import com.hmp.test.fakes.FakeAiExtraEnrichPort
import com.hmp.test.fakes.FakeLlmTransport
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

private fun song(id: Long, title: String, artist: String) =
    MusicInfo(Music(id, title, artist, "Album", 180_000, "/$id.mp3", ""), null, null)

/**
 * 记录型保活端口——验证 MasterAgent 在电台启停时正确声明 / 撤销 RADIO_ACTIVE（F11-L1 接线）。
 */
private class RecordingKeepAlivePort : AgentKeepAlivePort {
    val calls = mutableListOf<Pair<KeepAliveReason, Boolean>>()
    override fun setKeepAlive(reason: KeepAliveReason, active: Boolean) {
        calls += reason to active
    }

    /** 仅看 RADIO_ACTIVE 的声明序列（true=声明保活，false=撤销）。 */
    val radioActive get() = calls.filter { it.first == KeepAliveReason.RADIO_ACTIVE }
}

/**
 * 记录型播放端口（与 MasterAgentRadioTest 同构，此处独立声明避免跨文件耦合）。
 */
private class RecordingPlaybackPort : PlaybackCommandPort {
    private val commands = mutableListOf<Pair<PlaybackCommand, CommandSource>>()

    override suspend fun execute(
        command: PlaybackCommand,
        source: CommandSource,
    ): Pair<Boolean, String> {
        commands += command to source
        return true to "ok"
    }

    val replaceQueueIds: List<Long>?
        get() = commands
            .map { it.first }
            .filterIsInstance<PlaybackCommand.REPLACE_QUEUE>()
            .lastOrNull()
            ?.musicIds
}

/**
 * F11-L1 生命周期接线回归：
 * - startRadio 成功后必须声明 RADIO_ACTIVE（保活诉求发出 → iOS/Android 据此延长进程寿命）。
 * - stopRadio 必须撤销 RADIO_ACTIVE（不再保活，进程可正常退场）。
 *
 * 这是"Agent 后台存活"的可断言证据：不靠真机，单测即可证明启停与保活端口联动。
 */
class F11LifecycleTest {

    @Test
    fun startRadio_declaresRadioKeepAlive_andStopRevokes() = runBlocking {
        val musicRepo = FakeAgentMusicRepository()
        listOf(song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙")).forEach {
            musicRepo.songs[it.music.id] = it
        }
        musicRepo.musicIdsByLabel[LabelName.ROCK] = listOf(1L, 2L, 3L)
        val playback = RecordingPlaybackPort()
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
            ),
        )
        val keepAlive = RecordingKeepAlivePort()
        val master = MasterAgent(
            timeProvider = { 0L },
            musicRepository = musicRepo,
            chatToolRegistry = ToolRegistry.create(
                ToolDependencies(
                    musicRepository = musicRepo,
                    playlistRepository = FakeAgentPlaylistRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    nowPlayingContextProvider = FakeNowPlayingContextProvider,
                    playbackCommandPort = playback,
                    enrichPort = FakeAiExtraEnrichPort(),
                )
            ),
            radioTransport = transport,
            defaultLlmConfig = AiEndpointConfig(isConfigured = true),
            playbackPort = playback,
            nowPlayingProvider = FakeNowPlayingContextProvider,
            keepAlivePort = keepAlive,
        )

        // 开播 → 等节目真正开起来（队列落地 = session 建立 = updateRadioKeepAlive(true) 触发）
        master.startRadio(seed = "摇滚")
        withTimeout(15_000) {
            while (keepAlive.radioActive.none { it.second }) delay(20)
        }
        assertTrue(
            keepAlive.radioActive.any { it.second },
            "startRadio 成功后必须声明 RADIO_ACTIVE（保活诉求发出）",
        )

        // 关台 → 撤销保活
        master.stopRadio()
        withTimeout(10_000) {
            while (keepAlive.radioActive.none { !it.second }) delay(20)
        }
        assertTrue(
            keepAlive.radioActive.any { !it.second },
            "stopRadio 必须撤销 RADIO_ACTIVE（不再保活）",
        )
    }
}
