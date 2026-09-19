package com.hmp.domain.agent.sub

import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.FakeNowPlayingContextProvider
import com.hmp.domain.agent.port.LlmEvent
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PauseEvent
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.TrackOutcome
import com.hmp.domain.agent.port.TrackSettledEvent
import com.hmp.domain.agent.port.currentLocalMoment
import com.hmp.domain.agent.port.dayPart
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.agent.tool.ToolDependencies
import com.hmp.domain.agent.tool.ToolNames
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 测试用曲目构造（文件级——嵌套 Fixture 也需要访问）。 */
private fun song(id: Long, title: String, artist: String) =
    MusicInfo(Music(id, title, artist, "Album", 180_000, "/$id.mp3", ""), null, null)

/**
 * RadioSubAgent spec 驱动实现的确定性测试。
 *
 * 覆盖 Start 流程的四条路径：
 * 1. LLM 定队列成功 → 只替换「当前播放曲之后」，在播那首不动
 * 2. LLM 调用失败 → 保留本地兜底，但秒开推送仍然发生
 * 3. LLM 只回文本没给 id → 保留本地兜底
 * 4. 无 LLM 配置 → 纯本地
 *
 * 全部断言「秒开推送」与「换批」都以 [CommandSource.AGENT_INTERNAL] 下发——
 * 这是避免 MasterAgent 把电台自己的编排误判为用户跳过的关键约束。
 */
class RadioSubAgentTest {

    /** 记录型播放端口：把电台下发的指令全部记下来供断言。 */
    private class RecordingPlaybackPort : PlaybackCommandPort {
        val commands = mutableListOf<Pair<PlaybackCommand, CommandSource>>()

        /** 每条指令下发的回调（测试里用它模拟播放引擎对 PLAY 的真实反应）。 */
        var onCommand: ((PlaybackCommand, CommandSource) -> Unit)? = null

        override suspend fun execute(
            command: PlaybackCommand,
            source: CommandSource,
        ): Pair<Boolean, String> {
            commands += command to source
            onCommand?.invoke(command, source)
            return true to "ok"
        }

        /** 最后一次 REPLACE_QUEUE 的曲目 ID（未发生则 null）。 */
        val replaceQueueIds: List<Long>?
            get() = commands
                .map { it.first }
                .filterIsInstance<PlaybackCommand.REPLACE_QUEUE>()
                .lastOrNull()
                ?.musicIds

        val allInternal: Boolean
            get() = commands.all { it.second == CommandSource.AGENT_INTERNAL }
    }

    private class Fixture {
        val musicRepo = FakeAgentMusicRepository()
        val playback = RecordingPlaybackPort()
        val deps = ToolDependencies(
            musicRepository = musicRepo,
            playlistRepository = FakeAgentPlaylistRepository(),
            settingsRepository = FakeSettingsRepository(),
            nowPlayingContextProvider = FakeNowPlayingContextProvider,
            playbackCommandPort = playback,
            enrichPort = FakeAiExtraEnrichPort(),
        )
        val fullRegistry = ToolRegistry.create(deps)

        /** 三首同标签曲目，供本地保底命中。 */
        fun seedLibrary() {
            listOf(song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙")).forEach {
                musicRepo.songs[it.music.id] = it
            }
            musicRepo.musicIdsByLabel[LabelName.ROCK] = listOf(1L, 2L, 3L)
        }

        fun agent(
            transport: FakeLlmTransport?,
            nowPlaying: com.hmp.domain.agent.port.NowPlayingContextProvider = FakeNowPlayingContextProvider,
            retained: RadioConversation? = null,
            playback: RecordingPlaybackPort = this.playback,
        ): RadioSubAgent = RadioSubAgent(
            contextBudget = AgentContextBudget(
                agentId = "radio",
                maxContextTokens = 64_000,
                llmClient = transport,
            ),
            toolRegistryView = ToolRegistryView.radio(fullRegistry),
            musicRepository = musicRepo,
            playbackPort = playback,
            nowPlayingProvider = nowPlaying,
            presenceBus = null,
            auditLog = null,
            observationBus = null,
            retained = retained,
            radioConfig = if (transport != null) AiEndpointConfig(isConfigured = true) else null,
            targetCount = 3,
            stopSignal = null,
        )
    }

    /**
     * §7.0 Start 流程：本地秒开 → 模型定队列 → 只替换「当前播放曲之后」。
     *
     * 注意模型故意把在播的 1 号也列进去了 —— 这一条必须被硬剔除，
     * 「不许动在播那首」不能依赖模型自觉遵守。
     */
    @Test
    fun openingSelection_replacesQueueAfterCurrent() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
            ),
        )
        val agent = fx.agent(transport)

        val tracks = agent.startRadio(seed = "摇滚")

        // ① 返回最终队列：在播的 1 号 + 模型给的后续
        assertEquals(listOf(1L, 3L, 2L), tracks.map { it.musicId })
        assertEquals("夜航", tracks.first().title)
        assertTrue(agent.queryState() is RadioState.PLAYING)

        // ② 秒开推送：第一首起播 + 其余入队，全部 AGENT_INTERNAL
        val first = fx.playback.commands.first()
        assertTrue(first.first is PlaybackCommand.PLAY_BY_ID)
        assertEquals(1L, (first.first as PlaybackCommand.PLAY_BY_ID).musicId, "秒开应播本地保底第一首")
        assertTrue(fx.playback.commands.any { it.first is PlaybackCommand.ADD_TO_QUEUE })
        assertTrue(fx.playback.allInternal, "电台所有播放指令都必须标记 AGENT_INTERNAL")

        // ③ 模型给的队列经 REPLACE_QUEUE 落地，且**在播那首被剔除**
        assertEquals(listOf(3L, 2L), fx.playback.replaceQueueIds, "在播的 1 号不能被换掉")
    }

    /**
     * 模型给出的每首歌按语（whys）按 id 对齐落库——堆叠卡「主播按语」位的数据来源。
     * 长度不齐/缺失的歌回退占位文案「电台续播」，被剔除的在播曲不参与对齐。
     */
    @Test
    fun openingSelection_modelWhys_landOnPlaylistTracks() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(
                    LlmEvent.TextDelta(
                        """{"musicIds":[3,2,1],"reason":"夜行","whys":["收住夜色","缓缓落地"],"seedWhy":"回到起点"}""",
                    ),
                    LlmEvent.Completed,
                ),
            ),
        )
        val agent = fx.agent(transport)

        val tracks = agent.startRadio(seed = "摇滚")

        // whys 按 id 位置对齐：3→收住夜色，2→缓缓落地；1 是在播曲被剔除，它的按语走 seedWhy
        assertEquals(
            listOf("收住夜色", "缓缓落地"),
            tracks.drop(1).map { it.why },
            "模型按语逐首落到队列曲目上",
        )
        // seedWhy 落到镜像头部的在播曲（电台起点）——否则占位 why 被 UI 过滤，听众看不到
        assertEquals("回到起点", tracks.first().why, "种子曲按语来自模型的 seedWhy")
    }

    /**
     * 从零起播时旧队列未必为空（上一轮播放/电台的残留）。
     * playWith 是「追加 + 跳播」不清队列 —— 不清扫的话电台第一首会排到旧队列尾部，
     * 旧残留赖在队列前段，电台队列走完后接着放残留歌。
     */
    @Test
    fun startRadio_noCurrent_stalePlaylistGetsCleared() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        // 播放器：没有在播曲，但队列里留着 2 首旧歌
        val provider = FixedNowPlaying(null).apply { queueIds = listOf(99L, 98L) }
        val agent = fx.agent(transport = null, nowPlaying = provider)

        agent.startRadio(seed = "摇滚")

        val commands = fx.playback.commands.map { it.first }
        val clearIdx = commands.indexOfFirst { it is PlaybackCommand.SKIP_ALL }
        val playIdx = commands.indexOfFirst { it is PlaybackCommand.PLAY_BY_ID }
        assertTrue(clearIdx >= 0, "旧队列残留必须先清扫")
        assertTrue(playIdx > clearIdx, "清扫必须在起播之前")
        agent.stopRadio()
    }

    /**
     * S4：无端点时队列见底必须**真的**本地补歌。
     *
     * 这里曾经踩过坑：早先复用 `continueRadio()` 做保底，而它只是把本地镜像列表重新切一刀，
     * 播放器里的队列根本没变长 —— 队列见底时它什么都不会补。
     */
    @Test
    fun queueLow_withoutEndpoint_refillsLocally() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        // 曲库多放两首，本地补歌才有东西可补
        listOf(song(4, "补一", "丁"), song(5, "补二", "戊")).forEach {
            fx.musicRepo.songs[it.music.id] = it
        }
        val agent = fx.agent(transport = null)   // 无端点

        agent.startRadio(seed = "摇滚")
        val before = fx.playback.commands.count { it.first is PlaybackCommand.ADD_TO_QUEUE }

        agent.onQueueLow(1)
        // 决策内核跑在自己的 scope 上，只能等
        withTimeout(5_000) {
            while (fx.playback.commands.count { it.first is PlaybackCommand.ADD_TO_QUEUE } == before) {
                delay(50)
            }
        }

        val enqueued = fx.playback.commands
            .map { it.first }
            .filterIsInstance<PlaybackCommand.ADD_TO_QUEUE>()
            .map { it.musicId }
            .takeLast(2)
        assertEquals(listOf(4L, 5L), enqueued, "补的是曲库里还没排过的新歌")
        assertTrue(fx.playback.allInternal, "补歌也必须是 AGENT_INTERNAL")
    }

    /**
     * 「在播曲目」必须问播放器，不能靠本地计数推算。
     *
     * 真机验证踩过：换批走 AGENT_INTERNAL，不 emit 切歌事件，`playedCount` 永不增长，
     * 于是模型连续多轮拿到的「在播」都是开播那一首 —— 「在播不可替换」保护错了对象。
     */
    @Test
    fun contextSnapshot_followsNowPlaying_notStaleCounter() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }   // 队列 [1 夜航, 2 清晨, 3 回声]
        val provider = FixedNowPlaying(null)         // 开播时没有在播曲目 → 走完整路径

        val agent = fx.agent(transport = null, nowPlaying = provider)
        agent.startRadio(seed = "摇滚")
        // 此时 playedCount = 0，若按计数推算「在播」会得出第 1 首

        provider.id = 3L   // 播放器说现在播的是第 3 首
        val snap = agent.contextSnapshot()
        assertEquals(3L, snap.playingMusicId, "在播要以播放器为准，而不是 playedCount=0 推出的第 1 首")
        assertTrue(snap.upcoming.isEmpty(), "第 3 首之后没有待播了")

        provider.id = 1L
        val snap2 = agent.contextSnapshot()
        assertEquals(1L, snap2.playingMusicId)
        assertEquals(listOf("清晨", "回声"), snap2.upcoming.map { it.title })
        assertEquals(listOf(2L, 3L), snap2.upcoming.map { it.musicId }, "节目档案需要 id 构成队列")
    }

    /**
     * 播放器报得出队列时，快照必须以播放器为准，不能只信本地镜像。
     *
     * 真机 22:57「模型列表好像没换上」的排查盲区：镜像说换了 ≠ 播放器换了，
     * 只读镜像时模型永远看不到真实的队列偏差。
     */
    @Test
    fun contextSnapshot_prefersPlayerQueue_whenMirrorDiverges() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val provider = FixedNowPlaying(1L)
        val agent = fx.agent(transport = null, nowPlaying = provider)
        agent.startRadio(seed = "摇滚")   // 镜像队列 [1,2,3]

        // 模拟播放器队列与镜像分叉：REPLACE_QUEUE 部分失效（2 号没进去，混入两首别的）
        provider.queueIds = listOf(1L, 99L, 98L)

        val snap = agent.contextSnapshot()
        assertEquals(listOf(99L, 98L), snap.upcoming.map { it.musicId }, "队列真值应以播放器为准")
        assertEquals(2, snap.queueRemaining)
        assertTrue(
            snap.upcoming.all { it.title.startsWith("（id ") },
            "镜像里没有的歌，标题应回退为占位而不是编造",
        )
        agent.stopRadio()
    }

    /** id 可变的在播 provider（模拟播放过程中换歌；playing=false 模拟暂停态）。 */
    private class FixedNowPlaying(var id: Long?, var playing: Boolean = true) :
        com.hmp.domain.agent.port.NowPlayingContextProvider {
        /** 播放器侧队列（模拟 REPLACE_QUEUE 后播放器里的真实队列）；空 = 播放器不报队列。 */
        var queueIds: List<Long> = emptyList()

        override suspend fun getNowPlaying(): com.hmp.domain.agent.port.NowPlayingContext =
            com.hmp.domain.agent.port.NowPlayingContext(
                currentMusicId = id,
                currentMusicInfo = null,
                isPlaying = playing,
                currentPositionMs = 0,
                durationMs = 0,
                queueIds = queueIds,
            )
    }

    /**
     * 已有在播曲目时开电台：**不能顶掉当前这首**，队列只接在它之后。
     */
    @Test
    fun startWithCurrentPlaying_keepsItAndAppendsAfter() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        // 声称正在播第 3 首（它也在本地保底结果里）
        val agent = fx.agent(transport = null, nowPlaying = FixedNowPlaying(3L))

        val tracks = agent.startRadio(seed = "摇滚")

        assertEquals(3L, tracks.first().musicId, "队列头必须是当前在播的那首")
        assertTrue(
            fx.playback.commands.map { it.first }.none { it is PlaybackCommand.PLAY_BY_ID },
            "已有在播曲目时不该发 PLAY_BY_ID —— 那会把当前这首顶掉",
        )
    }

    /**
     * 「点开即播」兜底：有当前曲目 ≠ 正在出声。
     *
     * 上次关闭电台只暂停了播放；这次重开若没命中复用（换了主题/超窗口/队列被换过），
     * 会走 startAfterCurrent —— 它只接队列，从不按播放键，结果电台"开着"却没声音。
     * 当前曲目处于暂停态时必须补一次 PLAY（恢复当前曲，不是切歌）。
     */
    @Test
    fun startWithPausedCurrentTrack_resumesPlayback() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        // 当前曲目存在但暂停（典型：上次电台关闭后播放一直暂停着）
        val provider = FixedNowPlaying(3L, playing = false)
        // 模拟真实引擎：PLAY 生效后 isPlaying 变 true → 起声确认不应补刀
        fx.playback.onCommand = { cmd, _ -> if (cmd is PlaybackCommand.PLAY) provider.playing = true }
        val agent = fx.agent(transport = null, nowPlaying = provider)

        agent.startRadio(seed = "摇滚")

        val playCommands = fx.playback.commands.map { it.first }
        assertTrue(
            playCommands.any { it is PlaybackCommand.PLAY },
            "当前曲在暂停态时开播必须补 PLAY，否则电台开着没声音",
        )
        assertTrue(
            playCommands.none { it is PlaybackCommand.PLAY_BY_ID },
            "补 PLAY 起声成功时不得 PLAY_BY_ID —— 那会把当前曲从头重放",
        )
    }

    /**
     * 起声确认兜底：当前曲停在**曲目末端**（上一档最后一首放完后关的电台）时，
     * PLAY 恢复一个已到末尾的曲目是无声的 —— 状态 PLAYING、日志全对，就是没声音。
     * 起声确认发现 PLAY 无效后必须 PLAY_BY_ID 把当前曲重新起播。
     */
    @Test
    fun startWithTrackParkedAtEnd_fallsBackToPlayById() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        // 引擎无论如何都不出声（模拟曲目停在末端，PLAY 无效）
        val provider = FixedNowPlaying(3L, playing = false)
        val agent = fx.agent(transport = null, nowPlaying = provider)

        agent.startRadio(seed = "摇滚")

        withTimeout(5_000) {
            while (fx.playback.commands.none { it.first is PlaybackCommand.PLAY_BY_ID }) delay(50)
        }
        val playById = fx.playback.commands
            .map { it.first }
            .filterIsInstance<PlaybackCommand.PLAY_BY_ID>()
            .single()
        assertEquals(3L, playById.musicId, "PLAY 无声时要把当前曲重新起播，而不是死寂")
        assertTrue(
            fx.playback.commands.filter { it.first is PlaybackCommand.PLAY_BY_ID }
                .all { it.second == CommandSource.AGENT_INTERNAL },
            "起声兜底也必须是 AGENT_INTERNAL",
        )
    }

    /**
     * 开播情境必须点名**真正在播**的曲目（真机 22:57 事故回归测试）。
     *
     * 接播路径曾把本地保底队列第一首标成「在播·不可动」——模型把编排锚点
     * 定在了一首根本不会响的歌上（reason 承接《喜欢你》，实际在播的是《唯一》）。
     */
    @Test
    fun openingPrompt_namesRealPlayingTrack_notLocalFirst() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[1,2],"reason":"承接在播曲"}"""), LlmEvent.Completed),
            ),
        )
        val provider = FixedNowPlaying(3L, playing = true)   // 接播路径：3 正在播
        val agent = fx.agent(transport, nowPlaying = provider)

        agent.startRadio(seed = "摇滚")

        val openingPrompt = transport.calls.single().messages.last { it.role == "user" }.content.orEmpty()
        assertTrue(
            openingPrompt.contains("## 正在播（电台起点"),
            "开播情境必须点名真正在播的曲目",
        )
        assertTrue(
            openingPrompt.contains("3 | 回声"),
            "在播锚点应是当前曲 3，而不是本地保底的第一首",
        )
        assertFalse(
            openingPrompt.contains("【在播·不可动】"),
            "接播路径不得把保底队列第一首标成在播",
        )
        assertFalse(
            Regex("(?m)^1\\. 3 \\|").containsMatchIn(openingPrompt),
            "在播曲不应重复出现在保底队列列表里",
        )
        agent.stopRadio()
    }

    // ── 切歌过渡的瞬时 pause 去抖（真机 21:05:56 误暂停+误退出事故） ──

    /**
     * 播放器切歌会先停旧曲再起新曲，发一对 pause/resumed 事件。
     * 电台若见 pause 就信会误暂停；去抖后窗口后问播放器，还在播就当无事发生。
     */
    @Test
    fun transientPauseDuringSkip_doesNotPauseOrExit() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val provider = FixedNowPlaying(null)   // 从零起播 → PLAYING
        val agent = fx.agent(transport = null, nowPlaying = provider)
        agent.startRadio(seed = "摇滚")
        assertTrue(agent.queryState() is RadioState.PLAYING)

        // 切歌过渡：暂停事件到达，但播放器实际已经接着播下一首了
        agent.onPause(PauseEvent(resumed = false, atMs = nowMonotonicMs()))
        delay(1200)   // 越过 800ms 去抖窗口
        assertTrue(agent.queryState() is RadioState.PLAYING, "瞬时暂停不应把电台暂停")

        // 紧跟的 resumed 只是切歌过渡收尾，不该改变状态（更不该退出电台）
        agent.onPause(PauseEvent(resumed = true, atMs = nowMonotonicMs()))
        assertTrue(agent.queryState() is RadioState.PLAYING, "切歌后的继续不该退出电台")

        agent.stopRadio()
    }

    /** 真实的用户暂停：窗口后播放器确实停着 → 电台同步暂停。 */
    @Test
    fun realUserPause_pausesRadioAfterDebounce() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val provider = FixedNowPlaying(null)
        val agent = fx.agent(transport = null, nowPlaying = provider)
        agent.startRadio(seed = "摇滚")

        provider.playing = false   // 用户真的暂停了
        agent.onPause(PauseEvent(resumed = false, atMs = nowMonotonicMs()))

        withTimeout(5_000) {
            while (agent.queryState() !is RadioState.PAUSED) delay(50)
        }
        agent.stopRadio()
    }

    /**
     * 用户在电台暂停期间直接点了播放 → 电台**跟着恢复**（不退出，会话保留）。
     *
     * 契约见 `agent-radio.md` C3（2026-09-19 修订）：**用户的播放控制不退出电台**，
     * 终态只有手动关闭。回归守卫 —— 曾实现为"用户接管直接退电台"，与本契约矛盾。
     */
    @Test
    fun userResumeWhilePaused_radioResumesInsteadOfExiting() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val provider = FixedNowPlaying(null)
        val agent = fx.agent(transport = null, nowPlaying = provider)
        agent.startRadio(seed = "摇滚")
        assertTrue(agent.queryState() is RadioState.PLAYING)

        // ① 用户真的暂停 → 去抖后电台 PAUSED
        provider.playing = false
        agent.onPause(PauseEvent(resumed = false, atMs = nowMonotonicMs()))
        withTimeout(5_000) {
            while (agent.queryState() !is RadioState.PAUSED) delay(50)
        }

        // ② 用户在播放器里直接点播放 → 电台跟着恢复，**不退出**
        provider.playing = true
        agent.onPause(PauseEvent(resumed = true, atMs = nowMonotonicMs()))
        withTimeout(5_000) {
            while (agent.queryState() !is RadioState.PLAYING) delay(50)
        }
        assertTrue(agent.queryState() is RadioState.PLAYING, "用户恢复播放应让电台跟着恢复，而不是退出")

        agent.stopRadio()
    }

    /**
     * 卡片状态：固定信息与动态信息分开（C1）。
     *
     * 固定：主题（整档不变，开播时确定）
     * 动态：待播数 / 下首 / 最近调整（随播放与队列调整变化）
     */
    @Test
    fun cardState_themeIsFixed_adjustIsDynamic() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        listOf(song(4, "补一", "丁"), song(5, "补二", "戊")).forEach {
            fx.musicRepo.songs[it.music.id] = it
        }
        val agent = fx.agent(transport = null)

        agent.startRadio(seed = "摇滚")

        // ── 固定信息 ──
        // cardState 是 combine 出来的 StateFlow：源更新后发射要过一跳调度，
        // 立即同步读可能拿到初始值 —— 与本文件其他用例一致，轮询等待
        withTimeout(5_000) {
            while (agent.cardState.value.theme == null) delay(20)
        }
        assertEquals("摇滚", agent.cardState.value.theme, "主题整档不变")
        // ── 动态信息（初始）──
        assertNull(agent.cardState.value.lastAdjust, "本档还没调整过")
        assertEquals(2, agent.cardState.value.upcomingCount, "3 首里 1 首在播 → 还有 2 首待播")
        assertEquals("清晨", agent.cardState.value.nextTitle)

        // 触发一次本地补歌 → 动态部分要变
        agent.onQueueLow(1)
        withTimeout(5_000) {
            while (agent.cardState.value.lastAdjust == null) delay(50)
        }

        assertTrue(agent.cardState.value.lastAdjust!!.startsWith("已补"), "最近调整应更新")
        assertEquals("摇滚", agent.cardState.value.theme, "固定信息不受调整影响")

        agent.stopRadio()
        // cardState 由 combine 派生，发射是异步的，要等它追上
        withTimeout(5_000) {
            while (agent.cardState.value.theme != null) delay(50)
        }
        assertNull(agent.cardState.value.theme, "关闭后卡片状态清空")
        assertNull(agent.cardState.value.lastAdjust)
    }

    // ── 关闭后重开：复用上一段对话，省掉上下文组装 ──────────────

    /** 重开时下发的指令里，出现这两类就说明走了完整组装（被复用跳过的部分）。 */
    private fun List<PlaybackCommand>.rebuiltQueue(): Boolean =
        any { it is PlaybackCommand.ADD_TO_QUEUE || it is PlaybackCommand.REPLACE_QUEUE }

    @Test
    fun reopenWithinWindow_reusesConversation_skipsQueueAssembly() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
            ),
        )
        val first = fx.agent(transport).let { agent ->
            val tracks = agent.startRadio(seed = "摇滚")
            agent.stopRadio()
            tracks
        }

        // 刚关闭、播放器里还是原来那首、主题没变 → 命中复用
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = first.first().musicId,
            closedAtMs = nowMonotonicMs() - 1_000L,
            seed = "摇滚",
        )
        fx.playback.commands.clear()
        val second = fx.agent(transport, nowPlaying = FixedNowPlaying(first.first().musicId), retained = retained)
            .startRadio(seed = "摇滚")

        assertEquals(first.map { it.musicId }, second.map { it.musicId }, "复用时队列原样恢复")
        assertFalse(fx.playback.commands.map { it.first }.rebuiltQueue(), "复用时不应重建队列")
        assertTrue(fx.playback.commands.any { it.first is PlaybackCommand.PLAY }, "应当恢复播放")
    }

    /**
     * 播放器队列**稠密**（`replaceQueueWith` 保留已播前缀），镜像压缩为 `[在播, ...后续]`：
     * 复用判定只比「在播曲之后的尾巴」，前缀长度不同不影响复用。
     *
     * 回归守卫 —— 原实现整体比对 `queue != r.playlist.map { musicId }`，因前缀长度不同**永远不等**，
     * 导致真机 2026-09-19「关闭后再开没有复用上次内容」。
     */
    @Test
    fun reopenWithDensePlayerQueue_reusesWhenTailMatches() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
            ),
        )
        val first = fx.agent(transport).let { agent ->
            val tracks = agent.startRadio(seed = "摇滚")
            agent.stopRadio()
            tracks
        }
        val current = first.first().musicId
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = current,
            closedAtMs = nowMonotonicMs() - 1_000L,
            seed = "摇滚",
        )
        fx.playback.commands.clear()
        // 播放器队列带一段已播前缀（99），在播曲之后的部分与镜像一致 → 应复用
        val provider = FixedNowPlaying(current).apply {
            queueIds = listOf(99L) + first.map { it.musicId }
        }
        val second = fx.agent(transport, nowPlaying = provider, retained = retained)
            .startRadio(seed = "摇滚")

        assertEquals(first.map { it.musicId }, second.map { it.musicId }, "稠密队列 + 尾一致 → 仍应复用")
        assertFalse(fx.playback.commands.map { it.first }.rebuiltQueue(), "复用时不应重建队列")
    }

    /**
     * 复用必须把**会话档案**（节目档案 + 事实账本 + 计数）一起带过来，不能只续 `messages`。
     *
     * `messages` 只管"最近的对话质感"；而「已执行 / 编排思路 / 收听台账」是每轮 prompt
     * **从会话状态重渲染**的 —— 不恢复它们，续上后模型看到的就是空档案
     * （真机 2026-09-19："继续对话上下文不保留"）。
     *
     * 回归守卫 —— 原实现只 `resumeFrom(messages)`，`executed` / `intents` / `settled` 全部丢失。
     */
    @Test
    fun reopen_reusesFullSessionArchive_notJustMessages() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),
            ),
        )
        val first = fx.agent(transport).let { agent ->
            val tracks = agent.startRadio(seed = "摇滚")
            agent.stopRadio()
            tracks
        }
        val current = first.first().musicId
        val archive = RadioSessionArchive(
            executed = listOf("开播：模型定队列", "换批（3 首）"),
            intents = listOf("上一档的编排思路"),
            settled = listOf(
                TrackSettledEvent(
                    musicId = current,
                    title = "夜航",
                    outcome = TrackOutcome.SKIPPED_NEXT,
                    playedMs = 30_000,
                    totalMs = 180_000,
                    atMs = nowMonotonicMs() - 60_000,
                ),
            ),
            pauses = emptyList(),
            originMs = nowMonotonicMs() - 300_000,
            sentSettled = 1,
            sentPauses = 0,
            turnIndex = 3,
        )
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = current,
            closedAtMs = nowMonotonicMs() - 1_000L,
            seed = "摇滚",
            archive = archive,
        )
        val agent = fx.agent(transport, nowPlaying = FixedNowPlaying(current), retained = retained)
        agent.startRadio(seed = "摇滚")

        val restored = requireNotNull(agent.exportConversation()?.archive) { "复用后应能导出档案" }
        assertTrue(
            restored.executed.containsAll(archive.executed),
            "上一档「已执行」应随档案恢复（实际=${restored.executed}）",
        )
        assertEquals(archive.intents, restored.intents, "编排思路应随档案恢复")
        assertEquals(archive.settled.size, restored.settled.size, "收听台账应随档案恢复")
        assertEquals(archive.sentSettled, restored.sentSettled, "已送达计数应恢复（旧事实不误标 ★）")
        assertEquals(archive.turnIndex, restored.turnIndex, "轮次计数应恢复（TURN# 接着数）")
        assertEquals(archive.originMs, restored.originMs, "台账时基原点应恢复（第 N 分钟不重置）")
    }

    @Test
    fun reopenAfterWindow_startsFresh() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val first = listOf(
            song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙"),
        ).map { RadioTrack(it.music.id, it.music.title, it.music.artist, "") }

        // 超出兜底窗口 → 走完整 Start 流程
        val stale = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = 1L,
            closedAtMs = nowMonotonicMs() - (REUSE_BACKSTOP_MS + 60_000L),
            seed = "摇滚",
        )
        val agent = fx.agent(
            transport = null,
            nowPlaying = FixedNowPlaying(1L),
            retained = stale,
        )
        agent.startRadio(seed = "摇滚")

        assertTrue(fx.playback.commands.map { it.first }.rebuiltQueue(), "超窗口应重新走完整组装")
    }

    @Test
    fun reopenWithDifferentQueue_startsFresh() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val first = listOf(
            song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙"),
        ).map { RadioTrack(it.music.id, it.music.title, it.music.artist, "") }

        // 关闭时间很近，但播放器里已经是别的歌 → 队列被换过，不能复用
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = 1L,
            closedAtMs = nowMonotonicMs() - 1_000L,
            seed = "摇滚",
        )
        val agent = fx.agent(
            transport = null,
            nowPlaying = FixedNowPlaying(999L),   // 不在原队列里
            retained = retained,
        )
        agent.startRadio(seed = "摇滚")

        assertTrue(fx.playback.commands.map { it.first }.rebuiltQueue(), "队列已被换掉就应重新组装")
    }

    @Test
    fun reopenWithDifferentSeed_startsFresh() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val first = listOf(
            song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙"),
        ).map { RadioTrack(it.music.id, it.music.title, it.music.artist, "") }

        // 窗口内、队列没变，但用户换主题了（上一档摇滚，这次爵士）→ 不能沿用旧对话
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = 1L,
            closedAtMs = nowMonotonicMs() - 1_000L,
            seed = "摇滚",
        )
        val agent = fx.agent(
            transport = null,
            nowPlaying = FixedNowPlaying(1L),
            retained = retained,
        )
        val tracks = agent.startRadio(seed = "爵士")

        assertTrue(fx.playback.commands.map { it.first }.rebuiltQueue(), "换了主题就不该复用旧对话")
        // cardState 由 combine 派生，发射是异步的，要等它追上
        withTimeout(5_000) {
            while (agent.cardState.value.theme != "爵士") delay(50)
        }
        assertEquals("爵士", agent.cardState.value.theme, "新主题要落到卡片固定信息上")
    }

    @Test
    fun llmFailure_keepsLocalFallback_butStillInstantPlays() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        val agent = fx.agent(FakeLlmTransport(failOnCall = true))

        val tracks = agent.startRadio(seed = "摇滚")

        // 本地兜底三首（按标签命中）
        assertEquals(listOf(1L, 2L, 3L), tracks.map { it.musicId })
        assertTrue(agent.queryState() is RadioState.PLAYING)
        // 秒开推送仍发生，但没有 REPLACE_QUEUE（ReAct 未写队列）
        assertTrue(fx.playback.commands.isNotEmpty())
        assertNull(fx.playback.replaceQueueIds)
        assertTrue(fx.playback.allInternal)
    }

    /**
     * 开播调用失败要**重试一次**：冷启动第一发最怕瞬时网络抖动/端点 5xx，
     * 一失败就落本地保底会让用户"等了半天 AI 没排歌"。
     * 第 1 发失败、第 2 发成功 → 重试的结果应正常落地。
     */
    @Test
    fun openingCall_firstAttemptFails_retrySucceeds() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        val transport = FakeLlmTransport(
            perTurnScript = listOf(
                listOf(LlmEvent.Failed("scripted transient failure")),                        // 第 1 发：瞬时失败
                listOf(LlmEvent.TextDelta("""{"musicIds":[3,2,1]}"""), LlmEvent.Completed),   // 重试：给出队列
            ),
        )
        val agent = fx.agent(transport)

        val tracks = agent.startRadio(seed = "摇滚")

        assertEquals(2, transport.calls.size, "第 1 发失败后应重试一次，而不是直接放弃")
        // 重试拿到的模型队列正常落地（在播的 1 号被硬剔除）
        assertEquals(listOf(1L, 3L, 2L), tracks.map { it.musicId })
        assertEquals(listOf(3L, 2L), fx.playback.replaceQueueIds)
    }

    @Test
    fun llmWithoutQueueWrite_keepsLocalFallback() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        // 只回文本、没给 musicIds → 解析不出队列，保留本地兜底
        val agent = fx.agent(FakeLlmTransport(script = listOf(LlmEvent.TextDelta("这次先不换"), LlmEvent.Completed)))

        val tracks = agent.startRadio(seed = "摇滚")

        assertEquals(listOf(1L, 2L, 3L), tracks.map { it.musicId })
        assertNull(fx.playback.replaceQueueIds)
        assertTrue(agent.queryState() is RadioState.PLAYING)
    }

    @Test
    fun noLlmConfig_usesLocalOnly() = runTest {
        val fx = Fixture().apply { seedLibrary() }
        val agent = fx.agent(transport = null)

        val tracks = agent.startRadio(seed = "摇滚")

        assertEquals(listOf(1L, 2L, 3L), tracks.map { it.musicId })
        assertNull(fx.playback.replaceQueueIds)
        assertTrue(fx.playback.commands.isNotEmpty(), "无 LLM 也要秒开")
    }

    // ── 时段化复用判断（spec §7.1：情境换代替代 30 分钟死窗口） ──

    @Test
    fun reopenWhenDayPartChanged_startsFresh() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val first = listOf(
            song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙"),
        ).map { RadioTrack(it.music.id, it.music.title, it.music.artist, "") }

        // 队列没变、刚关闭，但时段变了（开播时段 ≠ 现在）→ 这是另一场节目
        val currentDayPart = com.hmp.domain.agent.port.currentLocalMoment().dayPart()
        val other = com.hmp.domain.agent.port.DayPart.entries.first { it != currentDayPart }
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = 1L,
            closedAtMs = nowMonotonicMs() - 1_000L,
            seed = "摇滚",
            dayPart = other,
        )
        val agent = fx.agent(
            transport = null,
            nowPlaying = FixedNowPlaying(1L),
            retained = retained,
        )
        agent.startRadio(seed = "摇滚")

        assertTrue(
            fx.playback.commands.map { it.first }.rebuiltQueue(),
            "时段变了就应视为新的一场，重新组装",
        )
    }

    @Test
    fun reopenSameDayPart_reusesEvenAfterWindow() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val first = listOf(
            song(1, "夜航", "甲"), song(2, "清晨", "乙"), song(3, "回声", "丙"),
        ).map { RadioTrack(it.music.id, it.music.title, it.music.artist, "") }

        // 超过旧的 30 分钟窗口，但时段没变、队列没变 → 仍应复用（时间只兜底，不再主判）
        val retained = RadioConversation(
            messages = listOf(LlmMessage(role = "system", content = "sys")),
            playlist = first,
            playingId = 1L,
            closedAtMs = nowMonotonicMs() - 40 * 60 * 1000L,
            seed = "摇滚",
            dayPart = com.hmp.domain.agent.port.currentLocalMoment().dayPart(),
        )
        fx.agent(transport = null, nowPlaying = FixedNowPlaying(1L), retained = retained)
            .startRadio(seed = "摇滚")

        assertFalse(
            fx.playback.commands.map { it.first }.rebuiltQueue(),
            "时段没变就不该因时长而开新档",
        )
    }

    // ── 瞬时暂停不进事实流（spec §7.2：切歌过渡的 pause/resumed 成对丢弃） ──

    @Test
    fun transientPausePair_notRecordedInFacts() = runBlocking {
        val fx = Fixture().apply { seedLibrary() }
        val provider = FixedNowPlaying(null)
        val agent = fx.agent(transport = null, nowPlaying = provider)
        agent.startRadio(seed = "摇滚")

        // 切歌过渡：瞬时 pause + 立即 resumed —— 成对丢弃
        agent.onPause(PauseEvent(resumed = false, atMs = nowMonotonicMs()))
        delay(50)
        agent.onPause(PauseEvent(resumed = true, atMs = nowMonotonicMs()))
        delay(1200)   // 越过去抖窗口，确认没有任何核查落地
        assertEquals(0, agent.debugRecordedPauseCount(), "瞬时对不应进事实账本")

        // 真暂停：确认后才进账本（入账经合并窗口，轮询等待）
        provider.playing = false
        agent.onPause(PauseEvent(resumed = false, atMs = nowMonotonicMs()))
        withTimeout(5_000) {
            while (agent.queryState() !is RadioState.PAUSED) delay(50)
        }
        withTimeout(5_000) {
            while (agent.debugRecordedPauseCount() < 1) delay(50)
        }
        assertEquals(1, agent.debugRecordedPauseCount(), "确认的真实暂停要进事实账本")
        agent.stopRadio()
    }
}
