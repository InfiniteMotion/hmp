package com.hmp.desktop.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D1-05：seek 取消旧协程时，旧协程的 read 往往抛 IOException 而不是 CancellationException，
 * 不能把它当播放错误上报（否则每次拖进度条都弹「Playback error」）。
 */
class PlaybackErrorReportingTest {

    @Test
    fun currentActiveError_isReported() {
        assertTrue(
            shouldReportPlaybackError(
                isCancellation = false,
                isStopped = false,
                isActive = true,
                generation = 7,
                currentGeneration = 7,
            ),
        )
    }

    @Test
    fun cancellation_isNeverReported() {
        assertFalse(
            shouldReportPlaybackError(
                isCancellation = true,
                isStopped = false,
                isActive = true,
                generation = 7,
                currentGeneration = 7,
            ),
        )
    }

    @Test
    fun staleGeneration_isNotReported() {
        assertFalse(
            shouldReportPlaybackError(
                isCancellation = false,
                isStopped = false,
                isActive = true,
                generation = 6,
                currentGeneration = 7,
            ),
            "seek 已开启新一代播放，旧协程的异常必须丢弃",
        )
    }

    @Test
    fun stoppedOrInactive_isNotReported() {
        assertFalse(
            shouldReportPlaybackError(
                isCancellation = false,
                isStopped = true,
                isActive = true,
                generation = 7,
                currentGeneration = 7,
            ),
        )
        assertFalse(
            shouldReportPlaybackError(
                isCancellation = false,
                isStopped = false,
                isActive = false,
                generation = 7,
                currentGeneration = 7,
            ),
        )
    }
}
