package com.hmp.desktop.player

import com.hmp.domain.music.MusicRepository
import com.hmp.domain.music.PlaybackSources
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 一-3 C3 判据：播放会话结算的**口径**与**退出时序**。
 *
 * 覆盖两条此前零测试的断言：
 * - **D5-04**：完播/结算写库的 `playDuration` 是「本次会话累计实听毫秒」，
 *   既不是引擎当前位置，也不是曲目元数据时长。三端里 Desktop 与 Android 曾各写一种
 *   （Desktop 用引擎位置、Android 用 `music.duration`），同一首歌在两端统计出两个量。
 * - **D5-07**：退出路径上 `releaseAndSettle()` **返回即代表落库完成**，且可重入
 *   （`DisposableEffect.onDispose` 与窗口关闭回调各触发一次，不得重复结算）。
 *   旧实现是 `scope.launch` 发完就走 + 立刻 `exitApplication()`，库里留下
 *   `playDuration = 0` 的僵尸行。
 *
 * 反证设计（变异探针已验）：
 * - 把 `endCurrentPlaybackSession` 的 `playedMs` 换成 `music.music.duration` → 第 1 条红
 *   （引擎位置/元数据时长都与"累计实听"数量级不同）；
 * - 把 `releaseAndSettle` 里的 `joinAll` 去掉 → 第 2 条红（返回时写入还没跑）。
 *
 * 时钟说明：`currentTimeMillis()` 是真实墙钟，所以「累计实听」在测试里≈0 毫秒。
 * 这正好是要断言的方向 —— 断言的是「写入值 ≪ 引擎位置 150 秒」，而不是精确毫秒数，
 * 因此不依赖调度器、也不赌 sleep 时长。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackSettlementTest {

    private class Recorded(
        val inserted: MutableList<PlaybackHistory> = mutableListOf(),
        val settlements: MutableList<Triple<Long, Long, Boolean>> = mutableListOf(),
        val listening: MutableList<Long> = mutableListOf(),
    )

    private fun newController(
        audio: FakeAudioEngine,
        log: Recorded,
    ): DesktopMusicController {
        val settings: SettingsRepository = stubInterface()
        val playlistRepo: PlaylistRepository = stubInterface()
        val controller = DesktopMusicController(
            audioEngine = audio,
            currentPlaybackUseCase = CurrentPlaybackUseCase(recordingRepository(log), playlistRepo, settings),
            playbackHistoryUseCase = PlaybackHistoryUseCase(recordingRepository(log)),
            timerUseCase = TimerUseCase(settings),
            managePlaylistUseCase = ManagePlaylistUseCase(playlistRepo, settings),
            settingsRepository = settings,
        )
        return controller
    }

    private fun track(id: Long, durationMs: Long = 180_000L) = testMusicInfo(id).let {
        it.copy(music = it.music.copy(duration = durationMs))
    }

    /** D5-04：结算写的是会话累计实听，不是引擎位置（150 秒）也不是元数据时长（200 秒）。 */
    @Test
    fun endOfSessionWritesCumulativeListenedMs_notEnginePositionOrMetadata(): TestResult =
        runTestWithMain {
            val audio = FakeAudioEngine().apply {
                setDuration(200_000L)
                setCurrentPosition(150_000L)
            }
            val log = Recorded()
            val controller = newController(audio, log)
            controller.playMusic(track(1L, durationMs = 200_000L))
            runCurrent()

            check(log.inserted.size == 1) { "会话未建立，后续断言无意义" }

            controller.releaseAndSettle()

            val settlement = log.settlements.singleOrNull()
                ?: error("releaseAndSettle() 返回时结算还没落库（D5-07 的等待没生效）")
            val (_, playedMs, isCompleted) = settlement

            assertFalse(isCompleted, "非自然播完的退出结算不该标完播")
            // 引擎位置 150 秒、元数据 200 秒：真实累计只可能是「测试跑起来的这几毫秒」
            assertTrue(playedMs < 5_000L, "playDuration=$playedMs 不是累计实听（更像引擎位置/元数据时长）")
        }

    /** D5-04 的自然播完分支：完播标记为真，时长仍是累计实听（旧实现这里写的是元数据时长）。 */
    @Test
    fun naturalCompletionMarksCompletedAndWritesCumulativeMs(): TestResult =
        runTestWithMain {
            val audio = FakeAudioEngine().apply {
                setDuration(200_000L)
                setCurrentPosition(150_000L)
            }
            val log = Recorded()
            val controller = newController(audio, log)
            controller.playMusic(track(1L, durationMs = 200_000L))
            runCurrent()

            audio.simulatePlaybackComplete()
            runCurrent()

            val settlement = log.settlements.singleOrNull() ?: error("完播没有结算")
            val (_, playedMs, isCompleted) = settlement
            assertTrue(isCompleted, "自然播完要标完播")
            assertTrue(playedMs < 5_000L, "playDuration=$playedMs 不是累计实听")
        }

    /** D5-07：可重入 —— 第二次 release 不重复结算，也不留下第二条历史记录。 */
    @Test
    fun releaseIsReentrant_secondCallDoesNotSettleTwice(): TestResult =
        runTestWithMain {
            val audio = FakeAudioEngine()
            val log = Recorded()
            val controller = newController(audio, log)
            controller.playMusic(track(1L))
            runCurrent()

            controller.releaseAndSettle()
            // 模拟 DisposableEffect.onDispose 与窗口关闭回调各触发一次
            controller.releaseAndSettle()

            assertEquals(1, log.settlements.size, "双次 release 重复结算了同一个会话")
            assertEquals(1, log.inserted.size, "release 不应新建播放会话")
            assertTrue(audio.releaseCalled, "releaseAndSettle 收尾要释放引擎")
        }

    /** D5-06：入口必须带来源，且取值落在三端共用的闭集里（iOS 曾恒传 null，来源分布恒空）。 */
    @Test
    fun playMusicRecordsKnownSource_notNull(): TestResult =
        runTestWithMain {
            val audio = FakeAudioEngine()
            val log = Recorded()
            val controller = newController(audio, log)
            controller.playMusic(track(1L))
            runCurrent()

            val history = log.inserted.singleOrNull() ?: error("会话没建立")
            assertEquals(PlaybackSources.MANUAL, history.source)
            assertTrue(PlaybackSources.isKnown(history.source), "来源取值必须在闭集内：${history.source}")
        }

    private fun runTestWithMain(body: suspend TestScope.() -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 只记「结算相关的三件事」的 [MusicRepository] 替身。
     *
     * 为什么不用现成的 [stubInterface] 直接断言：它的 handler 只拿到方法名，而这里要看的
     * 正是**传入的时长数值**（D5-04 的全部判据都在那个数上）。
     */
    private fun recordingRepository(log: Recorded): MusicRepository {
        val clazz = MusicRepository::class.java
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz),
        ) { _, method, args ->
            val name = method.name
            if (name == "insertPlayback") {
                val history = args?.firstOrNull { it is PlaybackHistory } as? PlaybackHistory
                if (history != null) log.inserted += history
                return@newProxyInstance (log.inserted.size).toLong()
            }
            if (name == "updatePlaybackRecord") {
                val numbers = args?.filterIsInstance<Long>().orEmpty()
                val flag = args?.filterIsInstance<Boolean>().orEmpty().firstOrNull() ?: false
                if (numbers.size >= 2) log.settlements += Triple(numbers[0], numbers[1], flag)
                return@newProxyInstance Unit
            }
            if (name == "recordListeningDuration") {
                args?.filterIsInstance<Long>().orEmpty().firstOrNull()?.let { log.listening += it }
                return@newProxyInstance Unit
            }
            defaultReturnValue(method.returnType)
        } as MusicRepository
    }

    private fun defaultReturnValue(rt: Class<*>): Any? = when (rt) {
        Void.TYPE -> Unit
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> when {
            Flow::class.java.isAssignableFrom(rt) -> kotlinx.coroutines.flow.emptyFlow<Any?>()
            List::class.java.isAssignableFrom(rt) -> emptyList<Any>()
            Set::class.java.isAssignableFrom(rt) -> emptySet<Any>()
            Map::class.java.isAssignableFrom(rt) -> emptyMap<Any, Any>()
            else -> null
        }
    }
}
