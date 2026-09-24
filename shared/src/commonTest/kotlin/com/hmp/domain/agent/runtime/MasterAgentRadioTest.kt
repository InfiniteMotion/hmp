package com.hmp.domain.agent.runtime
import com.hmp.domain.agent.tool.createBaseToolRegistry

import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeAgentMusicRepository
import com.hmp.test.fakes.FakeAgentPlaylistRepository
import com.hmp.test.fakes.FakeLlmTransport
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun song(id: Long, title: String, artist: String) =
    MusicInfo(Music(id, title, artist, "Album", 180_000, "/$id.mp3", ""), null, null)

/**
 * MasterAgent 电台生命周期的确定性测试。
 *
 * 真机踩坑（2026-09-13）：RadioCard 用 rememberCoroutineScope.launch 调 startRadio，
 * 用户切页 → 组合树销毁 → 在途 LLM 开播调用被 "rememberCoroutineScope left the
 * composition" 取消 → startSession 永远没执行 → session=null，电台"开着"但
 * 决策内核已死，之后所有观测无人消费（"agent 不干活了"）。
 *
 * 修复：MasterAgent.startRadio 把开播整段跑在自己的常驻 scope 里（scope.async），
 * 调用方只是 await 拿返回值 —— 调用方作用域被销毁只影响拿不拿得到结果，节目照常开播。
 */
class MasterAgentRadioTest {

    /** 记录型播放端口（与 RadioSubAgentTest 的同名替身一致，此处独立声明避免跨文件耦合）。 */
    private class RecordingPlaybackPort : PlaybackCommandPort {
        val commands = mutableListOf<Pair<PlaybackCommand, CommandSource>>()

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
     * 调用方作用域在开播 LLM 在途时被取消 → 节目仍要完整开播：
     * 模型队列照常写入播放器（session 已建起、决策内核活着）。
     */
    @Test
    fun startRadio_callerScopeCancelled_programStillCompletes() = runBlocking {
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
        val master = MasterAgent(
            timeProvider = { 0L },
            musicRepository = musicRepo,
            chatToolRegistry = createBaseToolRegistry(
                ToolDependencies(
                    musicRepository = musicRepo,
                    playlistRepository = FakeAgentPlaylistRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    nowPlayingContextProvider = FakeNowPlayingContextProvider,
                    playbackCommandPort = playback,
                )
            ),
            radioTransport = transport,
            defaultLlmConfig = AiEndpointConfig(isConfigured = true),
            playbackPort = playback,
            nowPlayingProvider = FakeNowPlayingContextProvider,
        )

        // 模拟 RadioCard：一个随时会被销毁的作用域里调 startRadio
        val caller = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deferred = caller.async { master.startRadio(seed = "摇滚") }

        // 等开播 LLM 真的在途（此刻调用方取消 = 复现真机事故现场）
        withTimeout(10_000) { while (transport.calls.isEmpty()) delay(20) }
        caller.cancel()   // "rememberCoroutineScope left the composition"

        // 关键断言：节目照常开播 —— 开播结果落地，模型队列写入播放器
        withTimeout(15_000) {
            while (playback.replaceQueueIds == null) delay(20)
        }
        assertEquals(listOf(3L, 2L), playback.replaceQueueIds, "取消调用方后模型队列仍要落地（在播的 1 号剔除）")

        // 收口态：PLAYING（session 建起来了，决策内核活着）
        withTimeout(10_000) {
            while (master.queryRadioState() !is com.hmp.domain.agent.runtime.sub.radio.RadioState.PLAYING) delay(20)
        }
        assertTrue(master.queryRadioState() is com.hmp.domain.agent.runtime.sub.radio.RadioState.PLAYING)
    }

    /**
     * 真机踩坑（2026-09-14）：RadioSubAgent 每次开播都新建实例，UI 层 remember 缓存的是
     * Master 层固定转发流 —— 若直接透传子代理 playlist，缓存到的是开播前的空流/旧实例，
     * LLM 返回落 seedWhy 后卡片永远收不到（种子按语显示不出来）。
     * 回归：master.radioPlaylist（固定转发流）必须反映镜像更新——seedWhy 落在播头部。
     */
    @Test
    fun radioPlaylist_forwardedFlowReflectsSeedWhy() = runBlocking {
        val musicRepo = FakeAgentMusicRepository()
        listOf(song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙")).forEach {
            musicRepo.songs[it.music.id] = it
        }
        musicRepo.musicIdsByLabel[LabelName.ROCK] = listOf(1L, 2L, 3L)
        val playback = RecordingPlaybackPort()
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(
                    LlmEvent.TextDelta("""{"musicIds":[3,2,1],"reason":"夜行","whys":["收住夜色","缓缓落地"],"seedWhy":"回到起点"}"""),
                    LlmEvent.Completed,
                ),
            ),
        )
        val master = MasterAgent(
            timeProvider = { 0L },
            musicRepository = musicRepo,
            chatToolRegistry = createBaseToolRegistry(
                ToolDependencies(
                    musicRepository = musicRepo,
                    playlistRepository = FakeAgentPlaylistRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    nowPlayingContextProvider = FakeNowPlayingContextProvider,
                    playbackCommandPort = playback,
                )
            ),
            radioTransport = transport,
            defaultLlmConfig = AiEndpointConfig(isConfigured = true),
            playbackPort = playback,
            nowPlayingProvider = FakeNowPlayingContextProvider,
        )

        master.startRadio(seed = "摇滚")

        // LLM 返回 → seedWhy 落镜像头部 → 固定转发流必须在有限跳内反映出来
        withTimeout(10_000) {
            while (master.radioPlaylist.value.firstOrNull()?.why != "回到起点") delay(20)
        }
        val forwarded = master.radioPlaylist.value
        assertEquals("回到起点", forwarded.first().why, "种子曲按语要经固定转发流到达 UI")
        assertEquals(listOf(3L, 2L), forwarded.drop(1).map { it.musicId }, "镜像头部=在播 1 号，其余为模型队列")
        assertEquals("收住夜色", forwarded.first { it.musicId == 3L }.why, "whys 按 id 对齐落库")
    }

    // ── 回归（v7.2.0 review A1）：生命周期不得自锁 / 不得等一个还在自旋的旧循环 ──

    private class Fixture {
        val musicRepo = FakeAgentMusicRepository()
        val playback = RecordingPlaybackPort()
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
            ),
        )
        val master = MasterAgent(
            timeProvider = { 0L },
            musicRepository = musicRepo,
            chatToolRegistry = createBaseToolRegistry(
                ToolDependencies(
                    musicRepository = musicRepo,
                    playlistRepository = FakeAgentPlaylistRepository(),
                    settingsRepository = FakeSettingsRepository(),
                    nowPlayingContextProvider = FakeNowPlayingContextProvider,
                    playbackCommandPort = playback,
                )
            ),
            radioTransport = transport,
            defaultLlmConfig = AiEndpointConfig(isConfigured = true),
            playbackPort = playback,
            nowPlayingProvider = FakeNowPlayingContextProvider,
        )

        init {
            listOf(song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙")).forEach {
                musicRepo.songs[it.music.id] = it
            }
            musicRepo.musicIdsByLabel[LabelName.ROCK] = listOf(1L, 2L, 3L)
        }
    }

    /**
     * 暂停后再开电台。修复前两处在同一把 `radioLifecycleMutex` 里等死：
     * ① `startRadio` 开头无条件 `radioRunLoopJob.join()`，而 `pauseRadio` 只翻状态、
     *    不清 `isActive`，旧 runLoop 仍在自旋 → join 永不返回；
     * ② 走到「先停再重建」时锁内调公开 `stopRadio()`，重入同一把非重入 Mutex → 自锁。
     * 之后所有 start/stop 全部挂起，只能杀进程。
     */
    @Test
    fun startRadio_afterPause_doesNotHang() = runBlocking {
        val f = Fixture()
        f.master.startRadio(seed = "摇滚")
        withTimeout(10_000) { while (f.master.queryRadioState() !is com.hmp.domain.agent.runtime.sub.radio.RadioState.PLAYING) delay(20) }
        f.master.pauseRadio()
        val callsBefore = f.transport.calls.size

        withTimeout(20_000) { f.master.startRadio(seed = "摇滚") }

        // 真的重建并跑了新一轮（不是短路返回空队列）
        withTimeout(20_000) { while (f.transport.calls.size <= callsBefore) delay(20) }
    }

    /** 关闭后再开电台：走公开 `stopRadio()` 收摊路径，同样不得卡死。 */
    @Test
    fun startRadio_afterStop_doesNotHang() = runBlocking {
        val f = Fixture()
        f.master.startRadio(seed = "摇滚")
        withTimeout(10_000) { while (f.master.queryRadioState() !is com.hmp.domain.agent.runtime.sub.radio.RadioState.PLAYING) delay(20) }
        f.master.stopRadio()
        val callsBefore = f.transport.calls.size

        withTimeout(20_000) { f.master.startRadio(seed = "摇滚") }
        withTimeout(20_000) { while (f.transport.calls.size <= callsBefore) delay(20) }
    }
}
