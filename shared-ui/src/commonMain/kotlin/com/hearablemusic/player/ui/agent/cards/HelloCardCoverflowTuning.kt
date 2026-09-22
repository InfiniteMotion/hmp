package com.hearablemusic.player.ui.agent.cards

import androidx.compose.runtime.Immutable

/**
 * 卡片堆叠的视觉调参包 —— 让 `HelloCardCoverflow` 同一份实现同时服务横屏与竖屏。
 *
 * 为什么要参数化而不是复制两个组件：两屏的差异**只有下面这几个数**，
 * 而循环、手势、自动轮播、锁定、指示点这些逻辑完全一致。分开写会变成两份要同步维护的代码。
 *
 * ── 尺寸推导 ──
 * · [fillContainer] = true（竖屏）：**卡直接填满容器**。卡形由**容器的 aspectRatio** 决定
 *   （调用方给 `aspectRatio(10f/9f)`），与原来的 Pager 版一致 —— Pager 的
 *   `PageSize.Fill` 也是占满容器，卡形从来不是这里算出来的。
 * · [fillContainer] = false（横屏）：卡高 = 容器高 × [cardHeightFraction]，
 *   卡宽 = 卡高 × [aspect]，受容器宽封顶。横屏容器宽而矮，且左栏形状不由 aspectRatio 约束。
 *
 * ── 步长 k 与邻卡可见性 ──
 * 相邻卡中心距 = 卡高 × k + [cardSpacingDp]；邻卡（缩放后）区间下界 = 中心距 − 卡高 × scale/2，
 * 视口半高 = 卡高/2。两个不同的问题、两个不同的判据，**不要混淆**：
 *   · **"邻卡是否被中卡遮住"**（堆叠视觉）：中心距 > 卡高 × (0.5 + scale/2) 才露出（scale 0.88 → > 0.94 卡高）。
 *   · **"邻卡是否进入视口"**（是否一次只显示一张）：中心距 ≥ 卡高 × (0.5 + scale/2)；
 *     k = 1.0 时邻卡区间为 [+0.56, +1.44] 个卡高，完全越出视口。
 * 竖屏取 k = 1.0（一次只显示一张）；横屏走下方 [leanNeighbors] 斜倚堆叠（k 同为 1.0，但被显式忽略）。
 * [cardSpacingDp] 再额外拉开卡与卡：竖屏只会把邻卡推得更远（更安全），横屏则减弱露出量。
 * 详见 F14 §3.8 / §3.10。
 *
 * ── [leanNeighbors]：斜倚堆叠（横屏专用）──
 * 邻卡绕**内缘**（贴着中卡的那条边）翻转 —— 像 panel 斜靠在中卡上下，内缘被旋转轴钉死不动。
 * 步长恒为 **1.0 卡高**（邻卡内缘恰好贴住中卡外缘），缩放与旋转都以这条边为轴。
 * 外缘入容器的约束（rotationX 使卡垂直投影缩为 cos(倾角)）：
 *   `frac × (0.5 + scale × cos(倾角)) ≤ 0.5`
 *   ⇒ `倾角 ≥ arccos((1/(2·frac) − 0.5) / scale)`
 * 代入 `frac = 0.66`、`scale = 0.8` 得 `θ ≥ 71.2°` —— 取 **72°**（全窗口余 2–7dp，
 * 透视还会把向后收的外缘再拉进来一些，实际余量更大）。
 * 直观解释：中卡占容器 66%，上下余量带各仅 17%；0.8 缩放的邻卡靠"斜躺"把投影压进余量带 ——
 * 这就是"**中卡不动、邻卡 0.8、角度实现**"的几何依据（用户裁定）。
 *
 * ── 离开缩小 / 进入放大 ──
 * 卡随 `|d|` 增大分两段连续缩小，`|d| = 0` 时 scale = 1：
 *   · `|d| ∈ [0, 1]`：1 → [neighborScale]（中心 → 邻卡位）；
 *   · `|d| ∈ [1, 2]`：1 → [exitScale]（邻卡位 → 更外侧，即"离开时继续缩小"）。
 * 两段相乘，故 `|d| = 1` 处即 [neighborScale]，`|d| ≥ 2` 处为 `[neighborScale] × [exitScale]`。
 * [exitScale] 越小、缩小越明显；取 1.0 表示"离开后不再缩"（等同旧行为）。
 */
@Immutable
internal data class CardStackTuning(
    /** 卡高 / 容器高（仅 [fillContainer] = false 时生效） */
    val cardHeightFraction: Float,
    /** true：卡填满容器（竖屏，卡形由调用方 aspectRatio 决定）；false：按比例算（横屏） */
    val fillContainer: Boolean,
    /** 卡形：宽 / 高（仅 [fillContainer] = false 时生效） */
    val aspect: Float,
    /** 卡高上限（dp），防止超大窗口下卡显得笨重 */
    val maxCardHeightDp: Float,
    /** 步长系数 k = 相邻卡中心距 / 卡高。见类注释的可见性约束。[leanNeighbors] = true 时忽略 */
    val stepFactor: Float,
    /**
     * 斜倚堆叠：忽略 [stepFactor]（步长恒为 1.0 卡高），邻卡以内缘为旋转轴斜靠在中卡上下，
     * 靠大倾角把投影收进容器 —— 而不是靠缩小卡片。约束与推导见类注释 [leanNeighbors] 一节。
     */
    val leanNeighbors: Boolean,
    /** 卡与卡之间的虚拟间距（dp），叠加在步长之上。见类注释 */
    val cardSpacingDp: Float,
    /** 邻卡缩放（居中标 1.0） */
    val neighborScale: Float,
    /** 更外侧卡（`|d| ≥ 2`）的额外缩小系数，见类注释的"离开缩小"。取 1.0 = 不缩 */
    val exitScale: Float,
    /** 邻卡透明度 */
    val neighborAlpha: Float,
    /** 邻卡 rotationX 倾角（度） */
    val neighborRotation: Float,
    /** 透视强度（越大越平） */
    val cameraDistance: Float,
) {
    companion object {
        /** 横屏宽窗左栏：中卡不变、邻卡 0.8 缩放 + 绕内缘斜倚的 3D 堆叠，卡形 9:8，按容器高定尺寸 */
        val Landscape = CardStackTuning(
            cardHeightFraction = 0.66f,
            fillContainer = false,
            aspect = 9f / 8f,
            maxCardHeightDp = 520f,
            // leanNeighbors = true 时忽略 stepFactor（步长恒为 1.0 卡高），这里只是占位
            stepFactor = 1.02f,
            leanNeighbors = true,
            cardSpacingDp = 0f,
            // 用户裁定 0.8：靠角度而非缩小实现完整入画
            neighborScale = 0.8f,
            // |d|≥2 的卡已整体出画（外缘 1.5 卡高起），缩小只影响拖动过渡瞬间
            exitScale = 0.8f,
            // 斜倚卡是完整的视觉层（非残影），透明度给到 0.65
            neighborAlpha = 0.65f,
            // 斜倚角：θ ≥ arccos((1/(2×0.66) − 0.5)/0.8) = 71.2°，取 72° 留余量（见类注释）
            neighborRotation = 72f,
            cameraDistance = 14f,
        )

        /**
         * 竖屏：**一次只显示一张**（复刻原 Pager 版观感）、卡填满容器。
         *
         * `stepFactor = 1.0`：相邻卡中心距 = 卡高（= 容器高），即邻卡正好落在**下一个容器高度**处，
         * 完全在视口之外 —— 等价于原 Pager「页与页平铺」的效果（`PageSize.Fill` + 页间距）。
         * 再叠加 `cardSpacingDp = 12`：把邻卡推得更远，滚动时卡与卡之间有清晰的分隔感。
         *
         * **切换效果 = 纯位移 + 淡入淡出**（`neighborScale = exitScale = 1.0`、`neighborRotation = 0`）：
         * 竖屏卡占满一整屏，"整页 3D 翻转 + 缩到 55%"会显得夸张、眩晕；而竖屏语义本就是
         * **单页翻页**（不是横屏那种"叠卡的 Coverflow"），故卡只做上下平移，靠 [neighborAlpha]
         * 淡入淡出完成新旧交替 —— 最接近原生翻页、无 3D 眩晕感。
         *
         * ⚠️ 不要用"可见性临界 0.5 + scale/2 = 0.94"来追求"不露邻卡"：
         *    那个临界只保证**邻卡被中卡遮住**（堆叠语义），而 k = 0.94 时相邻卡中心只隔 0.94 个卡高，
         *    两张卡几乎完全重叠 —— 渲染出来是"一堆卡叠在一起"，不是"一张卡 + 后面空着"。
         *    要"一次只显示一张"，须让邻卡整体移出视口：邻卡区间下界 = k − scale/2 ≥ 容器半高 0.5
         *    ⇒ **k ≥ 0.5 + scale/2 = 0.94**，而 k = 1.0 时邻卡区间为 [+0.56, +1.44]，安全越出。
         */
        val Portrait = CardStackTuning(
            cardHeightFraction = 1f,
            fillContainer = true,
            aspect = 10f / 9f,
            maxCardHeightDp = Float.MAX_VALUE,
            stepFactor = 1.0f,
            leanNeighbors = false,
            cardSpacingDp = 12f,
            // 不缩放（纯位移）：竖屏整页翻转/缩小会眩晕，见上方说明
            neighborScale = 1f,
            exitScale = 1f,
            // 淡入淡出：d=0 全显、d≥1 全隐，居中的平滑半透
            neighborAlpha = 0f,
            // 不旋转（无 3D 倾角）
            neighborRotation = 0f,
            cameraDistance = 14f,
        )
    }
}
