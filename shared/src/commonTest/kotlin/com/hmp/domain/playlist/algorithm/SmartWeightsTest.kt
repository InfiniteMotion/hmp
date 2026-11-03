package com.hmp.domain.playlist.algorithm

import com.hmp.domain.playlist.algorithm.core.SmartWeights
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * D1（内部黑盒权重）的判据：
 * 同 seed 可复现、不同 seed 换偏重、归一化、主维度确实是最大项、连续两次尽量不重复。
 */
class SmartWeightsTest {

    @Test
    fun sameSeed_producesSameWeights() {
        val a = SmartWeights.forSeed(42L)
        val b = SmartWeights.forSeed(42L)

        assertEquals(a, b, "同 seed 必须逐位一致（可复现）")
    }

    @Test
    fun differentSeed_changesWeights() {
        val a = SmartWeights.forSeed(42L)
        val b = SmartWeights.forSeed(43L)

        assertNotEquals(a.seed, b.seed)
        assertNotEquals(a.weights, b.weights, "不同 seed 必须换一套权重")
    }

    @Test
    fun weights_areNormalizedAndPositive() {
        for (seed in 0L..99L) {
            val set = SmartWeights.forSeed(seed)
            assertEquals(1.0, set.weights.values.sum(), 1e-9, "seed=$seed 权重和必须为 1")
            assertTrue(set.weights.values.all { it > 0.0 }, "seed=$seed 不允许出现非正权重")
        }
    }

    @Test
    fun emphasis_isTheLargestWeight() {
        for (seed in 0L..199L) {
            val set = SmartWeights.forSeed(seed)
            val max = set.weights.maxByOrNull { it.value }!!.key
            assertEquals(set.emphasis, max, "seed=$seed 的主维度必须是最大权重")
        }
    }

    @Test
    fun emphasis_variesAcrossSeeds() {
        val emphases = (0L..49L).map { SmartWeights.forSeed(it).emphasis }.toSet()
        assertTrue(emphases.size >= 3, "连续 50 个 seed 至少要覆盖 3 个偏重维度，实际=$emphases")
    }

    @Test
    fun avoidEmphasis_isRespected() {
        for (seed in 0L..49L) {
            val previous = SmartWeights.forSeed(seed).emphasis
            val next = SmartWeights.forSeed(seed + 1000L, avoidEmphasis = previous)
            assertNotEquals(previous, next.emphasis, "seed=$seed 连续两次不应推同一维度")
        }
    }

    @Test
    fun stableHash_isDeterministic() {
        assertEquals(
            SmartWeights.stableHash("radio:s1"),
            SmartWeights.stableHash("radio:s1"),
        )
        assertNotEquals(SmartWeights.stableHash("a"), SmartWeights.stableHash("b"))
        assertTrue(abs(SmartWeights.stableHash("radio:s1").toDouble()) > 0.0)
    }
}
