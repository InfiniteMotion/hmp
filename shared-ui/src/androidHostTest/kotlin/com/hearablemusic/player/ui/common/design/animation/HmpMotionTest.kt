package com.hearablemusic.player.ui.common.design.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效体系纯数据判据（taskbook D8-07 阶段 1 / hmp-design.md §5）：
 * 四档递增值、三档缓动互异、两档弹簧参数、场景工厂的档位组合。
 * 本测试不依赖 Robolectric，落在 :shared-ui:testAndroidHostTest。
 */
class HmpMotionTest {

    // ===== P1 四档时长 =====

    @Test
    fun durations_areTheAgreedFourTiers() {
        assertEquals(
            listOf(200, 300, 400, 800),
            listOf(HmpMotion.MICRO, HmpMotion.STANDARD, HmpMotion.EMPHASIS, HmpMotion.PAGE),
        )
    }

    @Test
    fun durations_areStrictlyIncreasing() {
        val d = listOf(HmpMotion.MICRO, HmpMotion.STANDARD, HmpMotion.EMPHASIS, HmpMotion.PAGE)
        for (i in 1 until d.size) {
            assertTrue("档位须严格递增：${d[i - 1]} → ${d[i]}", d[i] > d[i - 1])
        }
    }

    // ===== 缓动三档 =====

    @Test
    fun easings_areDistinctInstances() {
        assertNotSame(HmpMotion.StandardEasing, HmpMotion.EnterEasing)
        assertNotSame(HmpMotion.StandardEasing, HmpMotion.ExitEasing)
        assertNotSame(HmpMotion.EnterEasing, HmpMotion.ExitEasing)
    }

    // ===== 弹簧两档 =====

    @Test
    fun snappySpring_hasExpectedParams() {
        val s = HmpMotion.snappySpring<Float>()
        assertEquals("snappy.dampingRatio", 0.7f, s.dampingRatio, 0f)
        assertEquals("snappy.stiffness", 400f, s.stiffness, 0f)
    }

    @Test
    fun crispSpring_hasExpectedParams() {
        val s = HmpMotion.crispSpring<Float>()
        assertEquals("crisp.dampingRatio", 0.6f, s.dampingRatio, 0f)
        assertEquals("crisp.stiffness", 600f, s.stiffness, 0f)
    }

    // ===== 场景工厂 =====

    @Test
    fun sceneSpecs_useExpectedDurationAndEasing() {
        val page = HmpMotion.page<Float>()
        assertEquals("page.duration", HmpMotion.PAGE, page.durationMillis)
        assertSame("page.easing", HmpMotion.StandardEasing, page.easing)

        val emphasis = HmpMotion.emphasis<Float>()
        assertEquals("emphasis.duration", HmpMotion.EMPHASIS, emphasis.durationMillis)
        assertSame("emphasis.easing", HmpMotion.StandardEasing, emphasis.easing)

        val standard = HmpMotion.standard<Float>()
        assertEquals("standard.duration", HmpMotion.STANDARD, standard.durationMillis)
        assertSame("standard.easing", HmpMotion.StandardEasing, standard.easing)

        val enter = HmpMotion.enter<Float>()
        assertEquals("enter.duration", HmpMotion.STANDARD, enter.durationMillis)
        assertSame("enter.easing", HmpMotion.EnterEasing, enter.easing)

        val exit = HmpMotion.exit<Float>()
        assertEquals("exit.duration", HmpMotion.MICRO, exit.durationMillis)
        assertSame("exit.easing", HmpMotion.ExitEasing, exit.easing)

        val micro = HmpMotion.micro<Float>()
        assertEquals("micro.duration", HmpMotion.MICRO, micro.durationMillis)
        assertSame("micro.easing", HmpMotion.StandardEasing, micro.easing)
    }

    @Test
    fun sceneSpecs_coverAllFourDurations() {
        val used = setOf(
            HmpMotion.page<Float>().durationMillis,
            HmpMotion.emphasis<Float>().durationMillis,
            HmpMotion.enter<Float>().durationMillis,
            HmpMotion.exit<Float>().durationMillis,
            HmpMotion.micro<Float>().durationMillis,
        )
        assertEquals(
            setOf(HmpMotion.MICRO, HmpMotion.STANDARD, HmpMotion.EMPHASIS, HmpMotion.PAGE),
            used,
        )
    }
}
