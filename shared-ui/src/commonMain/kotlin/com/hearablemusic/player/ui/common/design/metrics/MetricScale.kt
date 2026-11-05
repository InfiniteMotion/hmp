package com.hearablemusic.player.ui.common.design.metrics

import com.hearablemusic.player.ui.common.layout.WindowHeightSizeClass
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass

/**
 * 统一尺寸刻度（规范：docs/spec/hmp-design.md §10，2026-11-04 定稿）。
 *
 * 全应用只有这一把尺：间距 / 圆角 / 图标 / 控件尺寸 / 描边 / 模糊半径都从
 * [MetricStep] 的 12 档里取值，档值随窗口档查表（[METRIC_SIZES_BY_WIDTH]）。
 * 本文件零 Compose 运行时依赖，只返回整数 dp，单测可直接断言数列与查表。
 *
 * 与排版字阶共用 [scaleWidthClassFor] 判据，保证字号与尺寸在 600 / 840dp
 * 断点、手机横屏、矮横窗三处的跳变时机完全一致（hmp-design.md §10.4）。
 */
enum class MetricStep {
    /** 2dp 贴合间距 / 描边 */
    HAIR,

    /** 4dp 图标与文字、极小内边距 */
    MICRO,

    /** 8dp 组内相邻 */
    COMPACT,

    /** 12dp 常规组内 / 卡片小内边距 */
    BASE,

    /** 16dp 组间 / 页面内边距 */
    COZY,

    /** 24dp 分区之间 */
    SECTION,

    /** 32dp 大按钮 / 卡片圆角 */
    BLOCK,

    /** 48dp 底栏 / 控件高 */
    BAR,

    /** 80dp 列表行高 */
    ROW,

    /** 120dp 小面板 */
    PANEL,

    /** 200dp 封面卡 */
    CARD,

    /** 320dp 主视觉 */
    HERO,
}

/**
 * 三窗口档尺寸数列（整数 dp，数组顺序与 [MetricStep.entries] 的 ordinal 一一对应）。
 *
 * - [MetricStep.HAIR] 跨档恒定：贴合间距是结构性尺寸，随窗口放大会把紧凑控件撑散
 *   （同排版字距「跨档不缩放」，§3.4）。
 * - 其余档严格递增，且 Compact < Medium < Expanded；大屏排版气质由尺寸承担
 *   （§3.3：字号刻意温和，尺寸承担主要缩放幅度）。
 * - 前 6 档直接继承旧尺寸体系 spacing / corner 的整数值，让大批存量迁移零视觉变化；
 *   后 4 档为原本没有 token 的大尺寸新补，按 1.4–1.5× 派生。
 * - 数列为原生整数，不用 1.25× / 1.5× 缩放因子：整数可单测精确断言、
 *   可与设计稿直接对数，且各档可按语义单独微调。
 */
internal val METRIC_SIZES_BY_WIDTH: Map<WindowWidthSizeClass, IntArray> = mapOf(
    WindowWidthSizeClass.Compact to intArrayOf(2, 4, 8, 12, 16, 24, 32, 48, 80, 120, 200, 320),
    WindowWidthSizeClass.Medium to intArrayOf(2, 5, 10, 14, 20, 30, 40, 60, 96, 144, 240, 384),
    WindowWidthSizeClass.Expanded to intArrayOf(2, 6, 12, 16, 24, 36, 48, 72, 112, 168, 288, 448),
)

/**
 * 尺寸档位判据——排版字阶与统一尺寸刻度共用，跳变时机必须一致：
 * - 手机横屏强制 Compact（高度受限，靠融合侧栏等布局切换消化）；
 * - 矮横窗（Expanded 宽 × Compact 高，如桌面半高窗口）降一档取 Medium；
 * - 其余按宽度档直取。
 */
fun scaleWidthClassFor(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
    isPhoneLandscape: Boolean,
): WindowWidthSizeClass = when {
    isPhoneLandscape -> WindowWidthSizeClass.Compact

    widthSizeClass == WindowWidthSizeClass.Expanded &&
        heightSizeClass == WindowHeightSizeClass.Compact -> WindowWidthSizeClass.Medium

    else -> widthSizeClass
}

/** 取当前窗口档的 12 档尺寸数列（供 [HMPMetricTokens] 组合层消费）。 */
fun metricSizesFor(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
    isPhoneLandscape: Boolean,
): IntArray = METRIC_SIZES_BY_WIDTH.getValue(
    scaleWidthClassFor(widthSizeClass, heightSizeClass, isPhoneLandscape),
)

/** 某档下某刻度的尺寸（整数 dp）。[sizes] 须来自 [metricSizesFor]。 */
fun sizeDp(step: MetricStep, sizes: IntArray): Int = sizes[step.ordinal]
