package com.hmp.domain.lyrics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsPresentationParamsTest {

    @Test
    fun distanceAlpha_currentLineIsOne() {
        assertEquals(1f, LyricsPresentationParams.distanceAlpha(0))
        assertEquals(1f, LyricsPresentationParams.distanceAlpha(-1))
    }

    @Test
    fun distanceAlpha_startsAtMaxAndFadesToMin() {
        assertEquals(LyricsPresentationParams.DISTANCE_ALPHA_START, LyricsPresentationParams.distanceAlpha(1))
        val mid = LyricsPresentationParams.distanceAlpha(2)
        assertTrue(mid < LyricsPresentationParams.DISTANCE_ALPHA_START)
        assertTrue(mid > LyricsPresentationParams.DISTANCE_ALPHA_MIN)
        assertEquals(LyricsPresentationParams.DISTANCE_ALPHA_MIN, LyricsPresentationParams.distanceAlpha(4))
        assertEquals(LyricsPresentationParams.DISTANCE_ALPHA_MIN, LyricsPresentationParams.distanceAlpha(10))
    }

    @Test
    fun distanceAlpha_isMonotonicallyDecreasing() {
        var previous = 1f
        for (d in 1..8) {
            val current = LyricsPresentationParams.distanceAlpha(d)
            assertTrue(current <= previous)
            previous = current
        }
    }

    @Test
    fun blurRadius_centerThreeRowsStaySharp() {
        assertEquals(0f, LyricsPresentationParams.blurRadiusDp(0))
        assertEquals(0f, LyricsPresentationParams.blurRadiusDp(1))
        assertEquals(LyricsPresentationParams.FAR_BLUR_RADIUS_DP, LyricsPresentationParams.blurRadiusDp(2))
        assertEquals(LyricsPresentationParams.FAR_BLUR_RADIUS_DP, LyricsPresentationParams.blurRadiusDp(5))
    }

    @Test
    fun glowAlpha_lerpsBetweenMinAndMax() {
        assertEquals(LyricsPresentationParams.GLOW_ALPHA_MIN, LyricsPresentationParams.glowAlpha(0f))
        assertEquals(LyricsPresentationParams.GLOW_ALPHA_MAX, LyricsPresentationParams.glowAlpha(1f))
        val mid = LyricsPresentationParams.glowAlpha(0.5f)
        assertTrue(abs(mid - (LyricsPresentationParams.GLOW_ALPHA_MIN + LyricsPresentationParams.GLOW_ALPHA_MAX) / 2f) < 0.001f)
    }

    @Test
    fun emphasisMarginFactor_isPositiveAndReasonable() {
        assertTrue(LyricsPresentationParams.EMPHASIS_MARGIN_FACTOR > 0f)
        assertTrue(LyricsPresentationParams.EMPHASIS_MARGIN_FACTOR < 0.5f)
    }
}
