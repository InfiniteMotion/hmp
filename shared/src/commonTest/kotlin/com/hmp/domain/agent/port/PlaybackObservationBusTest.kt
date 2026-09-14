package com.hmp.domain.agent.port

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 观测总线的行为：**只报用户的播放行为，不报我们自己的编排**。
 *
 * 真机日志里见过：一次 REPLACE_QUEUE 让播放器内部又走了一趟切歌，
 * 同一首歌 1ms 内被结算两次（11% 与 0%），而 0% 对模型是最强的负反馈 —— 必须滤掉。
 */
class PlaybackObservationBusTest {

    private fun settled(title: String, playedMs: Long) = TrackSettledEvent(
        musicId = 1L, title = title, outcome = TrackOutcome.SKIPPED_NEXT,
        playedMs = playedMs, totalMs = 240_000L, atMs = 0L,
    )

    // 注意：muteFor 基于真实单调时钟，因此这两个测试不能用 runTest（它走虚拟时间，
    // 虚拟时间推进不会让总线认为静默窗口已过）。用 runBlocking + 短窗口保持快速。

    @Test
    fun 静默期内丢弃结算与暂停() = runBlocking {
        val bus = PlaybackObservationBus()
        val seen = mutableListOf<TrackSettledEvent>()
        val pausesSeen = mutableListOf<PauseEvent>()
        val job = launch { bus.trackSettled.collect { seen += it } }
        val job2 = launch { bus.pauseEvents.collect { pausesSeen += it } }
        delay(20)

        bus.onTrackSettled(1, "真跳过", TrackOutcome.SKIPPED_NEXT, 30_000L, 240_000L)
        delay(20)
        assertEquals(1, seen.size, "正常结算要报出去")

        // 编排开始：静默 300ms
        bus.muteFor(300L)
        bus.onTrackSettled(1, "编排引起的假跳过", TrackOutcome.SKIPPED_NEXT, 0L, 240_000L)
        bus.onPauseStateChanged(false)
        delay(20)
        assertEquals(1, seen.size, "静默期内的假结算不能报")
        assertTrue(pausesSeen.isEmpty(), "编排引起的假暂停也不能报")

        delay(320L)   // 等过静默窗口
        bus.onTrackSettled(1, "静默过后", TrackOutcome.SKIPPED_NEXT, 20_000L, 240_000L)
        delay(20)
        assertEquals(2, seen.size, "静默结束后恢复正常上报")

        job.cancel(); job2.cancel()
    }

    @Test
    fun 静默会取更晚的那个截止时间() = runBlocking {
        val bus = PlaybackObservationBus()
        bus.muteFor(100L)
        bus.muteFor(400L)   // 更晚的应当生效
        delay(150L)         // 第一个窗口已过，第二个还没

        val seen = mutableListOf<TrackSettledEvent>()
        val job = launch { bus.trackSettled.collect { seen += it } }
        delay(20)

        bus.onTrackSettled(1, "X", TrackOutcome.SKIPPED_NEXT, 10_000L, 240_000L)
        delay(20)
        assertTrue(seen.isEmpty(), "短的窗口不该覆盖长的窗口")
        job.cancel()
    }
}
