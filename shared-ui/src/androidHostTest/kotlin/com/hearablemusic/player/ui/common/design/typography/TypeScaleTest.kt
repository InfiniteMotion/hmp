package com.hearablemusic.player.ui.common.design.typography

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.hearablemusic.player.ui.common.layout.WindowHeightSizeClass
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字阶纯数据判据（taskbook D8-03 阶段 1 / 2）：
 * 数列完备与单调、档间递增、查表矩阵、M3 15 级派生。
 * TypeScale 零 Compose 运行时依赖，本测试不依赖 Robolectric。
 */
class TypeScaleTest {

    // ===== 数列完备性 =====

    @Test
    fun typeSizes_everyRoleHasPositiveSizeInEveryWidthClass() {
        for ((width, sizes) in TYPE_SIZES_BY_WIDTH) {
            assertEquals("数列长度须与角色数一致（ordinal 对应）：$width", TypeRole.entries.size, sizes.size)
            for (role in TypeRole.entries) {
                assertTrue(
                    "sizeSp($width, $role) 应为正整数，实际 ${sizeSp(role, sizes)}",
                    sizeSp(role, sizes) > 0,
                )
            }
        }
    }

    @Test
    fun typeSizes_strictlyIncreasingWithinEachWidthClass() {
        for ((width, sizes) in TYPE_SIZES_BY_WIDTH) {
            for (i in 1 until sizes.size) {
                assertTrue(
                    "同档数列须严格递增：$width 第 ${i - 1}→$i 档 ${sizes[i - 1]}→${sizes[i]}",
                    sizes[i] > sizes[i - 1],
                )
            }
        }
    }

    @Test
    fun typeSizes_strictlyIncreasingAcrossWidthClassesPerRole() {
        val compact = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)
        val medium = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Medium)
        val expanded = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Expanded)
        for (role in TypeRole.entries) {
            val c = sizeSp(role, compact)
            val m = sizeSp(role, medium)
            val e = sizeSp(role, expanded)
            assertTrue("档间须递增 $role: Compact $c < Medium $m", c < m)
            assertTrue("档间须递增 $role: Medium $m < Expanded $e", m < e)
        }
    }

    // ===== 角色排版属性 =====

    @Test
    fun typeRole_lineHeightRatioGreaterThanOne() {
        for (role in TypeRole.entries) {
            assertTrue("$role.lineHeightRatio 应 > 1（行高大于字号）", role.lineHeightRatio > 1f)
            val lh = lineHeightSp(role, sizeSp(role, TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)))
            assertTrue("$role 行高（$lh）须大于字号", lh > sizeSp(role, TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)))
        }
    }

    @Test
    fun typeRole_lineHeightRoundsAsSpecified() {
        // 规范 §3.4 比率的换算抽样：micro 9×1.35=12.15→12；title 22×1.35=29.7→30
        assertEquals(12, lineHeightSp(TypeRole.MICRO, 9))
        assertEquals(30, lineHeightSp(TypeRole.TITLE, 22))
    }

    @Test
    fun typeRole_metadataComplete() {
        for (role in TypeRole.entries) {
            assertNotNull("$role.defaultWeight", role.defaultWeight)
            assertNotNull("$role.letterSpacing", role.letterSpacing)
            assertTrue("$role.letterSpacing 应非负", role.letterSpacing.value >= 0f)
            assertNotNull("$role.trim", role.trim)
        }
    }

    @Test
    fun typeRole_defaultWeightsMatchSpec() {
        // 规范 §3.3 默认字重列（HarmonyOS Sans 无 600，字阶 3 档：Regular/Medium/Bold）
        assertEquals(FontWeight.Normal, TypeRole.MICRO.defaultWeight)
        assertEquals(FontWeight.Medium, TypeRole.CAPTION.defaultWeight)
        assertEquals(FontWeight.Normal, TypeRole.FOOTNOTE.defaultWeight)
        assertEquals(FontWeight.Normal, TypeRole.BODY.defaultWeight)
        assertEquals(FontWeight.Medium, TypeRole.SUBTITLE.defaultWeight)
        assertEquals(FontWeight.Medium, TypeRole.HEADLINE.defaultWeight)
        assertEquals(FontWeight.Bold, TypeRole.TITLE.defaultWeight)
        assertEquals(FontWeight.Bold, TypeRole.DISPLAY.defaultWeight)
        assertEquals(FontWeight.Bold, TypeRole.DISPLAY_L.defaultWeight)
    }

    // ===== 查表矩阵（规范 §3.3 缩放规则） =====

    @Test
    fun typeSizesFor_phoneLandscapeForcesCompact() {
        val expected = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact)
        for (height in WindowHeightSizeClass.entries) {
            for (width in WindowWidthSizeClass.entries) {
                assertEquals(
                    "手机横屏强制 Compact：width=$width height=$height",
                    expected.toList(),
                    typeSizesFor(width, height, isPhoneLandscape = true).toList(),
                )
            }
        }
    }

    @Test
    fun typeSizesFor_shortLandscapeWindowDropsToMedium() {
        // 矮横窗：Expanded 宽 × Compact 高（桌面半高窗口）→ Medium 数列
        assertEquals(
            TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Medium).toList(),
            typeSizesFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Compact, isPhoneLandscape = false).toList(),
        )
    }

    @Test
    fun typeSizesFor_normalCasesFollowWidthClass() {
        val compact = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact).toList()
        val medium = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Medium).toList()
        val expanded = TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Expanded).toList()
        assertEquals(compact, typeSizesFor(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Medium, false).toList())
        assertEquals(medium, typeSizesFor(WindowWidthSizeClass.Medium, WindowHeightSizeClass.Medium, false).toList())
        assertEquals(expanded, typeSizesFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Medium, false).toList())
        assertEquals(expanded, typeSizesFor(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Expanded, false).toList())
    }

    // ===== M3 组件内部投影（零覆盖，就近映射；业务排版不走此处） =====

    private val typo = buildHmpTypo(FontFamily.Default, TYPE_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact))
    private val m3 = m3ComponentTypography(typo)

    @Test
    fun m3ComponentTypography_all15StylesNonNull() {
        assertEquals(34f, m3.displayLarge.fontSize.value)
        assertEquals(28f, m3.displayMedium.fontSize.value)
        assertEquals(22f, m3.displaySmall.fontSize.value)
        assertEquals(22f, m3.headlineLarge.fontSize.value)
        assertEquals(18f, m3.headlineMedium.fontSize.value)
        assertEquals(16f, m3.headlineSmall.fontSize.value)
        assertEquals(18f, m3.titleLarge.fontSize.value)
        assertEquals(16f, m3.titleMedium.fontSize.value)
        assertEquals(14f, m3.titleSmall.fontSize.value)
        assertEquals(14f, m3.bodyLarge.fontSize.value)
        assertEquals(14f, m3.bodyMedium.fontSize.value)
        assertEquals(12f, m3.bodySmall.fontSize.value)
        assertEquals(14f, m3.labelLarge.fontSize.value)
        assertEquals(12f, m3.labelMedium.fontSize.value)
        assertEquals(11f, m3.labelSmall.fontSize.value)
    }

    @Test
    fun m3ComponentTypography_zeroWeightOverride() {
        // 投影零字重覆盖：每个槽位字重 = 其角色默认（规范修订：M3 槽位不再是业务 API）
        assertEquals(FontWeight.Bold, m3.headlineLarge.fontWeight) // title 默认
        assertEquals(FontWeight.Medium, m3.headlineMedium.fontWeight) // headline 默认
        assertEquals(FontWeight.Medium, m3.titleLarge.fontWeight) // headline 默认
        assertEquals(FontWeight.Normal, m3.titleSmall.fontWeight) // body 默认
        assertEquals(FontWeight.Normal, m3.bodyLarge.fontWeight) // body 默认
        assertEquals(FontWeight.Normal, m3.labelLarge.fontWeight) // body 默认
        assertEquals(FontWeight.Medium, m3.labelSmall.fontWeight) // caption 默认
    }

    @Test
    fun m3ComponentTypography_carriesLineHeightAndTrim() {
        // displayL@Compact：34×1.2 = 40.8 → 41；角色带首尾行距裁剪
        assertEquals(41f, m3.displayLarge.lineHeight.value)
        assertEquals(LineHeightStyle.Trim.Both, typo.body.lineHeightStyle?.trim)
    }

    // ===== 显式变体 =====

    @Test
    fun variants_mediumMultilineTabular() {
        assertEquals(FontWeight.Medium, typo.body.medium().fontWeight)
        assertEquals(
            LineHeightStyle.Trim.None,
            typo.body.multiline().lineHeightStyle?.trim,
        )
        assertEquals(TABULAR_FONT_FEATURE_SETTINGS, typo.footnote.tabular().fontFeatureSettings)
        // 原样式不被变体污染
        assertEquals(FontWeight.Normal, typo.body.fontWeight)
        assertEquals(LineHeightStyle.Trim.Both, typo.body.lineHeightStyle?.trim)
        assertNull(typo.footnote.fontFeatureSettings)
    }
}
