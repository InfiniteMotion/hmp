package com.hmp.domain.lyrics

/**
 * 「静谧辉光」旗舰默认歌词呈现的视觉参数（纯常量与纯函数，可单测）。
 *
 * 渲染层（Android/Desktop）按此组参数实现；所有逐帧变化均在绘制阶段完成，
 * 参数本身不涉及任何平台 API。
 */
object LyricsPresentationParams {

    // ==================== 排版与层次 ====================

    /** 行切换动画时长（毫秒） */
    const val LINE_TRANSITION_MS = 250

    /** 聚焦窗口外（distance >= 2）文字模糊半径（dp） */
    const val FAR_BLUR_RADIUS_DP = 2.5f

    /** 当前行透明度 */
    const val CURRENT_ALPHA = 1f

    /** 距离衰减起始透明度（distance = 1 时） */
    const val DISTANCE_ALPHA_START = 0.7f

    /** 距离衰减透明度下限 */
    const val DISTANCE_ALPHA_MIN = 0.35f

    /** 距离衰减跨度：从 0.7 线性衰减到 0.35 所需行距 */
    const val DISTANCE_ALPHA_FADE_SPAN = 3

    // ==================== 当前行文字效果 ====================

    /** 光晕呼吸 alpha 下限/上限 */
    const val GLOW_ALPHA_MIN = 0.18f
    const val GLOW_ALPHA_MAX = 0.38f

    /** 光晕呼吸周期（毫秒） */
    const val GLOW_PERIOD_MS = 2600f

    /** 光晕模糊半径（dp） */
    const val GLOW_BLUR_RADIUS_DP = 12f

    // ==================== 逐字动效 ====================

    /** 当前字符放大倍数 */
    const val CHAR_EMPHASIS_SCALE = 1.2f

    /** 当前字符放大进入/缩小的过渡时长（毫秒） */
    const val EMPHASIS_TRANSITION_MS = 200

    /** 焦点字符放大后的防拥挤边距系数（相对字号）：字符节点左右各预留该比例 */
    const val EMPHASIS_MARGIN_FACTOR = 0.05f

    // ==================== 纯函数 ====================

    /**
     * 距离透明度曲线：当前行 1f；distance=1 起 0.7 线性衰减至下限 0.35，
     * distance >= 1 + [DISTANCE_ALPHA_FADE_SPAN] 时钳制在下限。
     */
    fun distanceAlpha(distance: Int): Float {
        if (distance <= 0) return CURRENT_ALPHA
        val t = (distance - 1).toFloat() / DISTANCE_ALPHA_FADE_SPAN
        return (DISTANCE_ALPHA_START - (DISTANCE_ALPHA_START - DISTANCE_ALPHA_MIN) * t)
            .coerceIn(DISTANCE_ALPHA_MIN, DISTANCE_ALPHA_START)
    }

    /**
     * 距离模糊半径（dp）：当前行与上下行（distance 0/1）保持清晰，
     * 聚焦窗口外（distance >= 2）施加模糊。
     */
    fun blurRadiusDp(distance: Int): Float = if (distance >= 2) FAR_BLUR_RADIUS_DP else 0f

    /** 光晕呼吸 alpha：phase01 取 0..1（正弦相位归一化） */
    fun glowAlpha(phase01: Float): Float {
        val p = phase01.coerceIn(0f, 1f)
        return GLOW_ALPHA_MIN + (GLOW_ALPHA_MAX - GLOW_ALPHA_MIN) * p
    }
}
