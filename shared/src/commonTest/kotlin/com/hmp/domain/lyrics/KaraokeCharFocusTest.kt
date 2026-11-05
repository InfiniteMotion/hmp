package com.hmp.domain.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KaraokeCharFocusTest {

    private val timings = listOf(
        CharTiming("你", 0L, 1000L),
        CharTiming("好", 1000L, 2000L),
        CharTiming("世", 2000L, 3000L),
        CharTiming("界", 3000L, 4000L),
    )

    @Test
    fun emptyTimings_returnsZeroFocus() {
        assertEquals(CharFocus(0, 0f), findKaraokeCharFocus(emptyList(), 0L, 4000L, 1000L))
    }

    @Test
    fun invalidLineRange_returnsZeroFocus() {
        assertEquals(CharFocus(0, 0f), findKaraokeCharFocus(timings, 0L, 0L, 1000L))
    }

    @Test
    fun beforeLineStart_returnsFirstCharStart() {
        assertEquals(CharFocus(0, 0f), findKaraokeCharFocus(timings, 0L, 4000L, -100L))
    }

    @Test
    fun atSegmentStart_mapsToThatChar() {
        assertEquals(CharFocus(1, 0f), findKaraokeCharFocus(timings, 0L, 4000L, 1000L))
        assertEquals(CharFocus(3, 0f), findKaraokeCharFocus(timings, 0L, 4000L, 3000L))
    }

    @Test
    fun midSegment_returnsWithinProgress() {
        val focus = findKaraokeCharFocus(timings, 0L, 4000L, 2500L)
        assertEquals(2, focus.index)
        assertEquals(0.5f, focus.withinProgress)
    }

    @Test
    fun atLastSegmentEnd_returnsLastCharComplete() {
        assertEquals(CharFocus(3, 1f), findKaraokeCharFocus(timings, 0L, 4000L, 4000L))
    }

    @Test
    fun beyondLineEnd_returnsLastCharComplete() {
        assertEquals(CharFocus(3, 1f), findKaraokeCharFocus(timings, 0L, 4000L, 9000L))
    }

    @Test
    fun s2UniformTimings_mapsCharOrdinal() {
        val uniform = LyricsTimingGenerator.generateUniform("你好世界", 0L, 4000L, 1f)
        assertEquals(CharFocus(2, 0.5f), findKaraokeCharFocus(uniform, 0L, 4000L, 2500L))
        assertEquals(CharFocus(3, 1f), findKaraokeCharFocus(uniform, 0L, 4000L, 4000L))
    }

    @Test
    fun expandCharTimings_s2MapsEachCharToItsSegment() {
        val timings = listOf(
            CharTiming("你", 0L, 100L),
            CharTiming("好", 100L, 200L),
            CharTiming("世", 200L, 300L),
            CharTiming("界", 300L, 400L),
        )
        val expanded = expandCharTimings("你好世界", timings)
        assertEquals(listOf(timings[0], timings[1], timings[2], timings[3]), expanded)
    }

    @Test
    fun expandCharTimings_whitespaceCharsMapToNull() {
        val timings = listOf(
            CharTiming("A", 0L, 100L),
            CharTiming("B", 100L, 200L),
        )
        val expanded = expandCharTimings("A B", timings)
        assertEquals(timings[0], expanded[0])
        assertNull(expanded[1])
        assertEquals(timings[1], expanded[2])
    }

    @Test
    fun expandCharTimings_multiCharSegmentSharesTiming() {
        val timings = listOf(CharTiming("你好", 0L, 500L), CharTiming("世界", 500L, 1000L))
        val expanded = expandCharTimings("你好世界", timings)
        assertEquals(listOf(timings[0], timings[0], timings[1], timings[1]), expanded)
    }

    @Test
    fun expandCharTimings_emptyTimings_allNull() {
        val expanded = expandCharTimings("你好", emptyList())
        assertNull(expanded[0])
        assertNull(expanded[1])
    }
}
