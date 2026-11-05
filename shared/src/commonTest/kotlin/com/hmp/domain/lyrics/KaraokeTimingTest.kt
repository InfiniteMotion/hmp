package com.hmp.domain.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsTimingGeneratorTest {

    @Test
    fun generateUniform_splitsCharsAcrossSingWindow() {
        val timings = LyricsTimingGenerator.generateUniform("你好", 0L, 1000L, 0.8f)
        assertEquals(2, timings.size)
        assertEquals(0L, timings[0].startMs)
        assertEquals(400L, timings[0].endMs)
        assertEquals(400L, timings[1].startMs)
        assertEquals(800L, timings[1].endMs)
    }

    @Test
    fun generateUniform_ignoresWhitespace() {
        val timings = LyricsTimingGenerator.generateUniform("A B", 0L, 1000L, 0.8f)
        assertEquals(2, timings.size)
    }

    @Test
    fun generateUniform_emptyText_returnsEmpty() {
        assertTrue(LyricsTimingGenerator.generateUniform("   ", 0L, 1000L).isEmpty())
        assertTrue(LyricsTimingGenerator.generateUniform("", 0L, 1000L).isEmpty())
    }

    @Test
    fun generateUniform_zeroDuration_returnsSingleSegment() {
        val timings = LyricsTimingGenerator.generateUniform("你好", 500L, 500L)
        assertEquals(1, timings.size)
        assertEquals(500L, timings[0].startMs)
        assertEquals(500L, timings[0].endMs)
    }

    @Test
    fun lineEndMs_usesNextLineTimestamp() {
        val lines = listOf(
            LyricLineData(0L, "A"),
            LyricLineData(1000L, "B"),
        )
        assertEquals(1000L, LyricsTimingGenerator.lineEndMs(lines, 0, 5000L))
    }

    @Test
    fun lineEndMs_lastLine_withDuration_capsAtDuration() {
        val lines = listOf(LyricLineData(3000L, "A"))
        assertEquals(8000L, LyricsTimingGenerator.lineEndMs(lines, 0, 8000L))
    }

    @Test
    fun lineEndMs_lastLine_withoutDuration_usesFallback() {
        val lines = listOf(LyricLineData(3000L, "A"))
        assertEquals(13000L, LyricsTimingGenerator.lineEndMs(lines, 0, null))
    }

    @Test
    fun resolve_fillsUniformTimingsAndTranslation() {
        val parsed = listOf(
            LyricLineData(0L, "Hello", "你好"),
            LyricLineData(1000L, "World"),
        )
        val resolved = LyricsTimingGenerator.resolve(parsed, 5000L)
        assertEquals(2, resolved.size)
        assertTrue(resolved[0].charTimings.isNotEmpty())
        assertTrue(resolved[0].translatedCharTimings.isNotEmpty())
        assertEquals("Hello".count { !it.isWhitespace() }, resolved[0].charTimings.size)
        assertEquals("你好".length, resolved[0].translatedCharTimings.size)
        // 最后一行：end = min(5000, 1000+10000) = 5000；S2 唱完点 = 1000 + 0.8*4000 = 4200
        assertEquals(4200L, resolved[1].charTimings.last().endMs)
    }

    @Test
    fun resolve_keepsEnhancedTimingsAndNormalizesEnd() {
        val parsed = listOf(
            LyricLineData(
                timestamp = 0L,
                originalText = "我",
                charTimings = listOf(CharTiming("我", 100L, -1L)),
            ),
            LyricLineData(1000L, "Next"),
        )
        val resolved = LyricsTimingGenerator.resolve(parsed, 5000L)
        assertEquals(1, resolved[0].charTimings.size)
        assertEquals(1000L, resolved[0].charTimings[0].endMs)
    }
}

class FindKaraokeProgressTest {

    @Test
    fun emptyTimings_linearProgress() {
        val progress = findKaraokeProgress(emptyList(), 0L, 1000L, 500L)
        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun positionAtBounds_returnsZeroAndOne() {
        val timings = listOf(CharTiming("A", 0L, 400L), CharTiming("B", 400L, 800L))
        assertEquals(0f, findKaraokeProgress(timings, 0L, 1000L, 0L))
        assertEquals(1f, findKaraokeProgress(timings, 0L, 1000L, 1000L))
        assertEquals(1f, findKaraokeProgress(timings, 0L, 1000L, 2000L))
        assertEquals(0f, findKaraokeProgress(timings, 0L, 1000L, -100L))
    }

    @Test
    fun piecewiseInterpolation_acrossSegments() {
        val timings = listOf(CharTiming("A", 0L, 400L), CharTiming("B", 400L, 800L))
        // 第一片段中点
        assertEquals(0.25f, findKaraokeProgress(timings, 0L, 1000L, 200L), 0.001f)
        // 第二片段起点
        assertEquals(0.5f, findKaraokeProgress(timings, 0L, 1000L, 400L), 0.001f)
        // 第二片段中点
        assertEquals(0.75f, findKaraokeProgress(timings, 0L, 1000L, 600L), 0.001f)
    }

    @Test
    fun zeroLengthSegment_advancesImmediately() {
        val timings = listOf(CharTiming("A", 0L, 0L), CharTiming("B", 0L, 800L))
        val progress = findKaraokeProgress(timings, 0L, 1000L, 100L)
        // 100ms 落在第二片段内：(1 + 100/800) / 2
        assertEquals(0.5625f, progress, 0.001f)
    }

    @Test
    fun invalidLineRange_returnsOne() {
        assertEquals(1f, findKaraokeProgress(emptyList(), 1000L, 1000L, 500L))
    }
}

class KaraokePositionInterpolatorTest {

    @Test
    fun playing_advancesByElapsedNanos() {
        val position = KaraokePositionInterpolator.interpolatePosition(
            lastPositionMs = 10_000L,
            lastTimestampNanos = 1_000_000_000L,
            nowNanos = 1_500_000_000L,
            isPlaying = true,
        )
        assertEquals(10_500L, position)
    }

    @Test
    fun paused_returnsLastPosition() {
        val position = KaraokePositionInterpolator.interpolatePosition(
            lastPositionMs = 10_000L,
            lastTimestampNanos = 1_000_000_000L,
            nowNanos = 2_000_000_000L,
            isPlaying = false,
        )
        assertEquals(10_000L, position)
    }

    @Test
    fun zeroElapsed_returnsLastPosition() {
        val position = KaraokePositionInterpolator.interpolatePosition(
            lastPositionMs = 7_000L,
            lastTimestampNanos = 5_000_000_000L,
            nowNanos = 5_000_000_000L,
            isPlaying = true,
        )
        assertEquals(7_000L, position)
    }

    @Test
    fun speed_appliesToAdvancement() {
        val position = KaraokePositionInterpolator.interpolatePosition(
            lastPositionMs = 10_000L,
            lastTimestampNanos = 1_000_000_000L,
            nowNanos = 1_500_000_000L,
            isPlaying = true,
            speed = 2f,
        )
        assertEquals(11_000L, position)
    }

    @Test
    fun monotonic_neverGoesBackwards() {
        // 帧时钟回退等异常情况下不倒退
        val position = KaraokePositionInterpolator.interpolatePosition(
            lastPositionMs = 10_000L,
            lastTimestampNanos = 2_000_000_000L,
            nowNanos = 1_000_000_000L,
            isPlaying = true,
        )
        assertEquals(10_000L, position)
    }
}
