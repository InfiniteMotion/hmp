package com.hearablemusic.player.ui.common.design.typography

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hearablemusic.player.ui.common.design.metrics.scaleWidthClassFor
import com.hearablemusic.player.ui.common.layout.WindowHeightSizeClass
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import kotlin.math.roundToInt

/**
 * 字阶角色（规范：docs/spec/hmp-design.md §3，2026-11-04 定稿）。
 *
 * 9 档字号数列随窗口档变化（[TYPE_SIZES_BY_WIDTH]，数组顺序与 [entries] 的 ordinal 一一对应）；
 * 行高比率 / 字距 / 默认字重 / trim 是跨档不变的排版属性，直接挂在角色上。
 * 本文件零 Compose 运行时依赖（FontWeight / LineHeightStyle / TextUnit 均为纯值对象），
 * 单测可直接断言数列与查表逻辑。
 */
enum class TypeRole(
    val lineHeightRatio: Float,
    val letterSpacing: TextUnit,
    val defaultWeight: FontWeight,
    val trim: LineHeightStyle.Trim,
) {
    /** 8–10sp 索引条、角标 */
    MICRO(1.35f, 0.sp, FontWeight.Normal, LineHeightStyle.Trim.Both),

    /** 11sp 标签、徽标（labelSmall） */
    CAPTION(1.45f, 0.25.sp, FontWeight.Medium, LineHeightStyle.Trim.Both),

    /** 12–13sp 次要信息、时间戳、bodySmall */
    FOOTNOTE(1.4f, 0.25.sp, FontWeight.Normal, LineHeightStyle.Trim.Both),

    /** 14–15sp 正文、bodyMedium */
    BODY(1.4f, 0.25.sp, FontWeight.Normal, LineHeightStyle.Trim.Both),

    /** 16–17sp 列表项标题、卡片标题、bodyLarge */
    SUBTITLE(1.35f, 0.25.sp, FontWeight.Medium, LineHeightStyle.Trim.Both),

    /** 18sp 区块标题、headlineMedium / titleLarge */
    HEADLINE(1.35f, 0.25.sp, FontWeight.Medium, LineHeightStyle.Trim.Both),

    /** 20–22sp 页面标题、headlineLarge */
    TITLE(1.35f, 0.15.sp, FontWeight.Bold, LineHeightStyle.Trim.Both),

    /** 24–27sp 播放器歌名、displayMedium / displaySmall */
    DISPLAY(1.25f, 0.15.sp, FontWeight.Bold, LineHeightStyle.Trim.Both),

    /** 32–40sp 页面大标题、displayLarge */
    DISPLAY_L(1.2f, 0.15.sp, FontWeight.Bold, LineHeightStyle.Trim.Both),
}

/**
 * 三窗口档字号数列（sp 整数；规范 §3.3）。
 * 数列刻意比间距温和（≈ +1 / +2 档，对比统一尺寸刻度的 1.25×–1.5×）：
 * 文字放大直接引发换行与截断，大屏排版气质由间距承担、字号微调即可。
 * 缩放跳变时机与统一尺寸刻度一致（600 / 840dp 断点）。
 */
internal val TYPE_SIZES_BY_WIDTH: Map<WindowWidthSizeClass, IntArray> = mapOf(
    WindowWidthSizeClass.Compact to intArrayOf(9, 11, 12, 14, 16, 18, 22, 28, 34),
    WindowWidthSizeClass.Medium to intArrayOf(10, 12, 13, 15, 18, 20, 24, 31, 37),
    WindowWidthSizeClass.Expanded to intArrayOf(11, 13, 14, 17, 19, 22, 26, 34, 41),
)

/**
 * 档位查表（规范 §3.3 缩放规则）：
 * - 手机横屏强制 Compact（高度受限，靠融合侧栏等布局切换消化，文字不放大）——与尺寸刻度共用 scaleWidthClassFor 判据；
 * - 矮横窗（Expanded 宽 × Compact 高，如桌面半高窗口）降一档取 Medium 数列；
 * - 其余按宽度档直取。
 */
fun typeSizesFor(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
    isPhoneLandscape: Boolean,
): IntArray = TYPE_SIZES_BY_WIDTH.getValue(
    // 与统一尺寸刻度共用同一判据（MetricScale.scaleWidthClassFor），
    // 保证字号与尺寸在 600 / 840dp 断点、手机横屏、矮横窗三处同步跳变。
    scaleWidthClassFor(widthSizeClass, heightSizeClass, isPhoneLandscape),
)

/** 某档下某角色的字号（sp 整数）。[sizes] 须来自 [typeSizesFor]。 */
fun sizeSp(role: TypeRole, sizes: IntArray): Int = sizes[role.ordinal]

/** 行高 = 字号 × 角色比率，四舍五入取整（sp）。 */
fun lineHeightSp(role: TypeRole, sizeSp: Int): Int = (role.lineHeightRatio * sizeSp).roundToInt()

/**
 * 等宽数字特性（fontFeatureSettings）：时间 / 时长 / 进度 / 统计数值用 `.tabular` 变体，
 * 消除高频刷新时比例数字的宽度抖动。
 * 前提：HarmonyOS Sans 需内置 tabular 数字特性，实施阶段一字形验证；不支持则本档作废（规范 §3.4）。
 */
const val TABULAR_FONT_FEATURE_SETTINGS = "tnum"
