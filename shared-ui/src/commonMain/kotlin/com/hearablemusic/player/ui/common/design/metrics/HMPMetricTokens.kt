package com.hearablemusic.player.ui.common.design.metrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.layout.rememberAppWindowSizeInfo

/**
 * 业务唯一尺寸入口（规范：docs/spec/hmp-design.md §10.2）。
 * 由 AppRoot / HearableMusicPlayerTheme / IosComposePilot 三个装配点 provides；
 * 使用点 `val metrics = LocalHmpMetrics.current` 后取 [MetricStep] 档位或组件预设。
 */
val LocalHmpMetrics = staticCompositionLocalOf<HmpMetrics> {
    error("HmpMetrics not provided")
}

/**
 * 尺寸组合层（规范 §10.2）。把 [MetricScale] 的整数数列转换成 Dp，并按组件族
 * 提供预设；非 UI 作用域（单测 / 预览）用 [Compact] 静态基准。
 */
object HMPMetricTokens {

    /** Compact 档静态基准，供单测与预览；业务 UI 一律走 [LocalHmpMetrics]。 */
    val Compact: HmpMetrics = buildHmpMetrics(
        METRIC_SIZES_BY_WIDTH.getValue(WindowWidthSizeClass.Compact),
    )

    /**
     * 解析当前窗口档的 12 档尺寸。直接读平台 `rememberAppWindowSizeInfo()`——
     * 三个装配点的 `LocalWindowSizeInfo` provides 层次不同，只有平台 API 在所有位置可安全求值
     * （与 HMPTypoTokens.rememberHmpTypo 同理由）。
     */
    @Composable
    fun rememberHmpMetrics(): HmpMetrics {
        val info = rememberAppWindowSizeInfo()
        return buildHmpMetrics(
            metricSizesFor(info.widthSizeClass, info.heightSizeClass, info.isPhoneLandscape),
        )
    }
}

/**
 * 统一尺寸刻度在当前窗口档下的 Dp 视图。
 * 12 档对所有 UI 用途共享同一把尺——间距、圆角、图标、控件尺寸不再各有各的数列；
 * [stroke] 是跨档恒定的结构性档，[capsulePreset] 等是组件族预设（语义别名，不是第二套数列）。
 */
@Immutable
data class HmpMetrics(
    val hair: Dp,
    val micro: Dp,
    val compact: Dp,
    val base: Dp,
    val cozy: Dp,
    val section: Dp,
    val block: Dp,
    val bar: Dp,
    val row: Dp,
    val panel: Dp,
    val card: Dp,
    val hero: Dp,
    val stroke: StrokeMetrics,
    val capsulePreset: ComponentPreset,
    val cardPreset: ComponentPreset,
    val listItemPreset: ComponentPreset,
    val dialogPreset: ComponentPreset,
    val screenPreset: ComponentPreset,
)

/** 描边 / 细线：小于最小整数档的结构性半档，跨窗口档恒定。 */
@Immutable
data class StrokeMetrics(
    val hairline: Dp,
    val thin: Dp,
)

/** 组件族预设：把某类组件常用的内边距 / 间隙 / 圆角 / 图标尺寸绑成一组。 */
@Immutable
data class ComponentPreset(
    val padding: Dp,
    val gap: Dp,
    val corner: Dp,
    val icon: Dp,
)

internal fun buildHmpMetrics(sizes: IntArray): HmpMetrics {
    fun dp(step: MetricStep): Dp = sizeDp(step, sizes).dp
    return HmpMetrics(
        hair = dp(MetricStep.HAIR),
        micro = dp(MetricStep.MICRO),
        compact = dp(MetricStep.COMPACT),
        base = dp(MetricStep.BASE),
        cozy = dp(MetricStep.COZY),
        section = dp(MetricStep.SECTION),
        block = dp(MetricStep.BLOCK),
        bar = dp(MetricStep.BAR),
        row = dp(MetricStep.ROW),
        panel = dp(MetricStep.PANEL),
        card = dp(MetricStep.CARD),
        hero = dp(MetricStep.HERO),
        // 0.5dp / 1dp 细线由最小档派生，避免在业务侧出现小于刻度的半档字面量
        stroke = StrokeMetrics(
            hairline = dp(MetricStep.HAIR) / 4f,
            thin = dp(MetricStep.HAIR) / 2f,
        ),
        capsulePreset = ComponentPreset(
            padding = dp(MetricStep.BASE),
            gap = dp(MetricStep.COMPACT),
            corner = dp(MetricStep.BLOCK),
            icon = dp(MetricStep.SECTION),
        ),
        cardPreset = ComponentPreset(
            padding = dp(MetricStep.COZY),
            gap = dp(MetricStep.COMPACT),
            corner = dp(MetricStep.COZY),
            icon = dp(MetricStep.SECTION),
        ),
        listItemPreset = ComponentPreset(
            padding = dp(MetricStep.BASE),
            gap = dp(MetricStep.COMPACT),
            corner = dp(MetricStep.MICRO),
            icon = dp(MetricStep.COZY),
        ),
        dialogPreset = ComponentPreset(
            padding = dp(MetricStep.SECTION),
            gap = dp(MetricStep.BASE),
            corner = dp(MetricStep.BLOCK),
            icon = dp(MetricStep.SECTION),
        ),
        screenPreset = ComponentPreset(
            padding = dp(MetricStep.COZY),
            gap = dp(MetricStep.SECTION),
            corner = 0.dp,
            icon = dp(MetricStep.SECTION),
        ),
    )
}
