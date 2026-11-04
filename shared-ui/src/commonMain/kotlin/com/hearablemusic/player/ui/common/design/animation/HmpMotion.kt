package com.hearablemusic.player.ui.common.design.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效体系（规范：docs/spec/hmp-design.md §5，2026-11-04 定稿，D8-07）。
 *
 * 四档时长 + 三档缓动 + 两档弹簧 + 场景工厂。动效是**感知节奏**，不随窗口档变化，
 * 因此保持 object 静态常量、不引入 CompositionLocal（与尺寸刻度的架构刻意不同）。
 *
 * 范围：只覆盖**通用 UI 动效**（页面转场 / 组件进出场 / 展开折叠 / 微反馈 / 状态切换）。
 * 特化动效（`DynamicBackground` 氛围循环、歌词呈现、悬浮窗吸附、封面旋转）**不收纳**，
 * 由各自模块持有参数对象（如 `LyricsPresentationParams`）。
 *
 * 旧动效 token 文件已在阶段 4 退场（含零引用的方向枚举）；本文件是动效唯一入口。
 */
object HmpMotion {

    // ── 时长四档（毫秒）──

    /** 即时反馈：按压 / 高亮 / 开关 */
    const val MICRO = 200

    /** 通用默认：组件进出场 / 展开折叠 / 标签切换 / Toast */
    const val STANDARD = 300

    /** 强调过渡：页面导航 / 较大组件出现消失 */
    const val EMPHASIS = 400

    /** 页面级转场 */
    const val PAGE = 800

    // ── 缓动三档（曲线沿用旧体系，不改视觉）──

    /** 通用默认：Ease In Out (0.4, 0, 0.2, 1) */
    val StandardEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** 元素进入：Ease Out (0.2, 0, 0.1, 1) */
    val EnterEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.1f, 1.0f)

    /** 元素退出：Ease In (0.6, 0, 0.8, 1) */
    val ExitEasing: Easing = CubicBezierEasing(0.6f, 0.0f, 0.8f, 1.0f)

    // ── 弹簧两档（后续需要可扩充，扩充须先说清语义场景）──

    /** 弹而不跳：胶囊展开 / 拖拽回弹 */
    fun <T> snappySpring(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 400f)

    /** 干脆利落：对话框 / 快速定位 */
    fun <T> crispSpring(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 600f)

    // ── 场景预设（时长 × 缓动的组合，业务不再手拼）──

    /** 页面级转场 */
    fun <T> page(): TweenSpec<T> = tween(durationMillis = PAGE, easing = StandardEasing)

    /** 强调过渡 */
    fun <T> emphasis(): TweenSpec<T> = tween(durationMillis = EMPHASIS, easing = StandardEasing)

    /** 通用默认（300 + standard，Compose 默认 easing 同曲线） */
    fun <T> standard(): TweenSpec<T> = tween(durationMillis = STANDARD, easing = StandardEasing)

    /** 组件进场 */
    fun <T> enter(): TweenSpec<T> = tween(durationMillis = STANDARD, easing = EnterEasing)

    /** 组件退场（用 micro 档，退场比进场快） */
    fun <T> exit(): TweenSpec<T> = tween(durationMillis = MICRO, easing = ExitEasing)

    /** 即时反馈 */
    fun <T> micro(): TweenSpec<T> = tween(durationMillis = MICRO, easing = StandardEasing)
}
