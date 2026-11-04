package com.hearablemusic.player.ui.common.design.metrics

import androidx.compose.ui.unit.Dp
import com.hearablemusic.player.ui.common.design.typography.TYPE_SIZES_BY_WIDTH
import com.hearablemusic.player.ui.common.design.typography.typeSizesFor
import com.hearablemusic.player.ui.common.layout.WindowHeightSizeClass
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一尺寸刻度纯数据判据（taskbook D8-03 阶段 1 / hmp-design.md §10.9 P1–P3）：
 * 数列完备与单调、跨档递增、查表矩阵、与字阶共用判据、组合层只引用档位。
 * 本测试不依赖 Robolectric，落在 :shared-ui:testAndroidHostTest。
 */
class MetricScaleTest {

    private fun HmpMetrics.stepValues(): List<Float> = listOf(
        hair.value, micro.value, compact.value, base.value, cozy.value, section.value,
        block.value, bar.value, row.value, panel.value, card.value, hero.value,
    )

    // ===== P1 数列完备性与单调 =====

    @Test
    fun metricSizes_everyStepHasPositiveSizeInEveryWidthClass() {
        for ((width, sizes) in METRIC_SIZES_BY_WIDTH) {
            assertEquals(
                "数列长度须与刻度档数一致（ordinal 对应）：$width",
                MetricStep.entries.size, sizes.size,
            )
            for (step in MetricStep.entries) {
                assertTrue(
                    "sizeDp($width, $step) 应为正整数，实际 ${sizeDp(step, sizes)}",
                    sizeDp(step, sizes) > 0,
                )
            }
        }
    }

    @Test
    fun metricSizes_strictlyIncreasingWithinEachWidthClass() {
        for ((width, sizes) in METRIC_SIZES_BY_WIDTH) {
            for (i in 1 until sizes.size) {
                assertTrue(
                    "同档数列须严格递增：$width 第 ${i - 1}→$i 档 ${sizes[i - 1]}→${sizes[i]}",
                    sizes[i] > sizes[i - 1],
                )
            }
        }
    }

    @Test
    fun metricSizes_increasingAcrossWidthClassesExceptHair() {
        val compact = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)
        val medium = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Medium)
        val expanded = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Expanded)
        for (step in MetricStep.entries) {
            val i = step.ordinal
            if (step == MetricStep.HAIR) {
                assertEquals("HAIR 跨档恒定：Compact", compact[i], medium[i])
                assertEquals("HAIR 跨档恒定：Medium", medium[i], expanded[i])
            } else {
                assertTrue("档间须递增 $step: Compact ${compact[i]} < Medium ${medium[i]}", compact[i] < medium[i])
                assertTrue("档间须递增 $step: Medium ${medium[i]} < Expanded ${expanded[i]}", medium[i] < expanded[i])
            }
        }
    }

    @Test
    fun sizeDp_mapsOrdinalToScale() {
        for ((_, sizes) in METRIC_SIZES_BY_WIDTH) {
            for (step in MetricStep.entries) {
                assertEquals("sizeDp($step)", sizes[step.ordinal], sizeDp(step, sizes))
            }
        }
    }

    // ===== P2 档位查表与字阶共用判据 =====

    @Test
    fun scaleWidthClassFor_phoneLandscapeForcesCompact() {
        for (width in WindowWidthSizeClass.entries) {
            for (height in WindowHeightSizeClass.entries) {
                assertEquals(
                    "手机横屏强制 Compact：$width / $height",
                    WindowWidthSizeClass.Compact,
                    scaleWidthClassFor(width, height, true),
                )
            }
        }
    }

    @Test
    fun scaleWidthClassFor_shortExpandedWindowDropsToMedium() {
        assertEquals(
            WindowWidthSizeClass.Medium,
            scaleWidthClassFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Compact, false),
        )
        assertEquals(
            WindowWidthSizeClass.Expanded,
            scaleWidthClassFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Medium, false),
        )
    }

    @Test
    fun scaleWidthClassFor_otherCasesUseWidthDirectly() {
        for (width in WindowWidthSizeClass.entries) {
            for (height in WindowHeightSizeClass.entries) {
                if (width == WindowWidthSizeClass.Expanded && height == WindowHeightSizeClass.Compact) continue
                assertEquals("$width / $height", width, scaleWidthClassFor(width, height, false))
            }
        }
    }

    @Test
    fun metricSizesFor_resolvesThroughSharedGate() {
        val compact = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)
        val medium = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Medium)
        assertArrayEquals(compact, metricSizesFor(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Medium, false))
        assertArrayEquals(compact, metricSizesFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Medium, true))
        assertArrayEquals(medium, metricSizesFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Compact, false))
    }

    @Test
    fun typeAndMetric_shareTheSameWidthClassGate() {
        for (width in WindowWidthSizeClass.entries) {
            for (height in WindowHeightSizeClass.entries) {
                for (landscape in listOf(false, true)) {
                    val expected = TYPE_SIZES_BY_WIDTH.getValue(scaleWidthClassFor(width, height, landscape))
                    assertArrayEquals(
                        "字阶须与尺寸刻度共用档位判据：$width / $height / landscape=$landscape",
                        expected,
                        typeSizesFor(width, height, landscape),
                    )
                }
            }
        }
    }

    // ===== P3 组合层只引用档位 =====

    @Test
    fun buildHmpMetrics_twelvePropertiesMatchScaleInEveryWidthClass() {
        for ((width, sizes) in METRIC_SIZES_BY_WIDTH) {
            assertEquals(
                "$width 下 12 档属性须逐一等于数列",
                sizes.map { it.toFloat() },
                buildHmpMetrics(sizes).stepValues(),
            )
        }
    }

    @Test
    fun compactStaticBaseline_matchesCompactScale() {
        val compact = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)
        assertEquals(compact.map { it.toFloat() }, HMPMetricTokens.Compact.stepValues())
    }

    @Test
    fun buildHmpMetrics_strokeDerivedFromSmallestStep() {
        val m = HMPMetricTokens.Compact
        assertEquals("hairline = HAIR / 4", m.hair.value / 4f, m.stroke.hairline.value, 0f)
        assertEquals("thin = HAIR / 2", m.hair.value / 2f, m.stroke.thin.value, 0f)
    }

    @Test
    fun buildHmpMetrics_presetsOnlyReferenceScale() {
        val allowed = METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)
            .map { it.toFloat() }.toSet() + 0f
        val m = HMPMetricTokens.Compact

        fun assertFromScale(dp: Dp, label: String) {
            assertTrue(
                "$label = ${dp.value}dp 须来自 12 档（或结构性 0）",
                dp.value in allowed,
            )
        }

        listOf(
            "capsule" to m.capsulePreset,
            "card" to m.cardPreset,
            "listItem" to m.listItemPreset,
            "dialog" to m.dialogPreset,
            "screen" to m.screenPreset,
        ).forEach { (name, preset) ->
            assertFromScale(preset.padding, "$name.padding")
            assertFromScale(preset.gap, "$name.gap")
            assertFromScale(preset.corner, "$name.corner")
            assertFromScale(preset.icon, "$name.icon")
        }
    }
}
