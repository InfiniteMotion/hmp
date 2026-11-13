package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.createBaseToolRegistry
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeAgentPlaylistRepository
import com.hmp.test.fakes.FakeLlmTransport
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 二-3 判据：MasterAgent 的生命周期收口与并发安全。
 *
 * D6-02 —— `close()`/`shutdown()` 必须取消主作用域。7 个转发/监听协程全挂在 `scope` 下，
 * 平台直接 terminate（没先 stopRadio）时它们会继续观察 StateFlow 并拖住进程退出。
 * D6-03 —— `pauseRadio` / `resumeRadio` 与 `stopRadio` 共享 `radioLifecycleMutex`；
 * 这里只做顺序化 smoke（stop 之后 pause/resume 不得复活电台、不得抛），竞态本身没有确定性判据，见 D6 施工结果。
 */
class MasterAgentLifecycleTest {

    private class RecordingPlaybackPort : PlaybackCommandPort {
        val commands = mutableListOf<Pair<PlaybackCommand, CommandSource>>()

        override suspend fun execute(command: PlaybackCommand, source: CommandSource): Pair<Boolean, String> {
            commands += command to source
            return true to "ok"
        }
    }

    private fun song(id: Long, title: String, artist: String) = MusicInfo(Music(id, title, artist, "Album", 180_000, "/$id.mp3", ""), null, null)

    private fun buildMaster(
        playback: PlaybackCommandPort,
        transport: FakeLlmTransport,
        enrichTransport: FakeLlmTransport? = null,
    ): MasterAgent {
        val musicRepo = FakeAgentMusicRepository()
        listOf(song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙")).forEach {
            musicRepo.songs[it.music.id] = it
        }
        musicRepo.musicIdsByLabel[LabelName.ROCK] = listOf(1L, 2L, 3L)
        return MasterAgent(
            timeProvider = { 0L },
            musicRepository = musicRepo,
            chatToolRegistry = createBaseToolRegistry(
                ToolDependencies(
                    musicRepository = musicRepo,
                    playlistRepository = FakeAgentPlaylistRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    nowPlayingContextProvider = FakeNowPlayingContextProvider,
                    playbackCommandPort = playback,
                ),
            ),
            radioTransport = transport,
            enrichTransport = enrichTransport,
            defaultLlmConfig = AiEndpointConfig(isConfigured = true),
            playbackPort = playback,
            nowPlayingProvider = FakeNowPlayingContextProvider,
        )
    }

    private fun radioTransport() = FakeLlmTransport(
        perTurnScript = listOf(
            listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
        ),
    )

    @Test
    fun close_cancelsMasterScope_soForwardersDoNotOutliveTheApp() = runBlocking {
        val master = buildMaster(RecordingPlaybackPort(), radioTransport())
        master.startRadio(seed = "摇滚")
        withTimeout(15_000) {
            while (master.queryRadioState() !is RadioState.PLAYING) delay(20)
        }
        assertTrue(
            master.activeScopeChildren() > 0,
            "电台开播后主作用域里应有转发/监听协程，否则这条用例没有压到 D6-02",
        )

        master.close()

        withTimeout(5_000) {
            while (master.activeScopeChildren() > 0) delay(10)
        }
        assertEquals(0, master.activeScopeChildren(), "close() 之后主作用域里不得残留活动协程")
    }

    @Test
    fun enrich_isSingleton_acrossStopAndStart() = runBlocking {
        val master = buildMaster(RecordingPlaybackPort(), radioTransport(), enrichTransport = FakeLlmTransport())
        master.startEnrich(null)
        val first = master.subAgents["enrich"]
        assertTrue(first != null, "首次 start 应创建实例")

        master.stopEnrich()
        master.startEnrich(null)
        val second = master.subAgents["enrich"]

        // 单例（2026-11-13）：停止不等于销毁，重开必须复用同一实例（否则暂停/进度会被重置）。
        assertTrue(first === second, "停止后重开必须复用同一个 Enrich 实例")
        master.stopEnrich()
    }

    @Test
    fun pauseOrResume_afterStop_doesNotResurrectTheRadio() = runBlocking {
        val master = buildMaster(RecordingPlaybackPort(), radioTransport())
        master.startRadio(seed = "摇滚")
        withTimeout(15_000) {
            while (master.queryRadioState() !is RadioState.PLAYING) delay(20)
        }

        master.stopRadio()
        // 顺序化 smoke：停止之后再来 pause/resume，不得复活、不得抛
        master.pauseRadio()
        master.resumeRadio()

        assertNull(master.queryRadioState(), "停止后 pause/resume 不能把电台状态带回来")
        // 单例（2026-11-13）：实例常驻，判据改为「运行已停止」而不是「实例已移除」。
        assertTrue(master.subAgents["radio"] != null, "单例：实例应保留，避免下次开台重建")
    }
}
