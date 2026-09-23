package com.hearablemusic.player.ui.agent.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════
// HelloCardCoverflow —— 垂直卡片堆叠（横竖屏共用的唯一实现）
//
// 为什么**不复用** VerticalPager：
//   ① Pager 无循环能力（官方 Foundation 至今没有 loop 开关）；
//   ② 它的 contentPadding / pageSpacing 会互相抵消，调不出想要的露出量（见 F14 §3.7）；
//   ③ Pager 按页序绘制，z 层序要靠 zIndex 事后纠正。
//   自绘则位置、缩放、旋转、层序全部可控。
//
// 横竖屏差异**全部收敛在 `CardStackTuning`** —— 本文件只认参数，不认屏幕方向。
// 手势：上下拖拽切页 + 点击卡片 + 自动轮播 + 电台永久锁。
//
// ── 无限循环 ──
//   `offset` **不钳制**：可以一路增大/减小穿过 0 与 n 边界。核心是三个函数：
//     · `ringDelta`  —— 环形最短有符号距离（锁定时算最短路径用）
//     · `normalize`  —— 静态归一化到 [0, n)，防浮点精度劣化
//     · 渲染处用"展开索引 rawIndex 算 d，取卡时才取模" —— 保证位置/外观连续、无穿帮
//   锁定语义：电台激活只**停自动轮播 + 滑到 RADIO_STATUS**，不限制用户拖拽。
// ═══════════════════════════════════════════════════════════════════

private const val CF_AUTO_ROTATE_MS = 4000L
private const val CF_USER_LOCK_MS = 5000L
private const val CF_DRAG_THRESHOLD_PX = 70f
/** 甩动速度阈值（px/ms）：超过则即使位移不足也翻页 */
private const val CF_FLING_VELOCITY_PX_PER_MS = 1.2f
/** 估速时忽略超过此值的事件间隔（ms）—— 停顿后的首个 delta 不代表速度 */
private const val CF_VELOCITY_WINDOW_MS = 100f

/**
 * 落定动画：带一点点回弹的弹簧。
 * dampingRatio 0.8 略欠阻尼（翻页有"吸住"的重量感），stiffness 420 让中短距离落定干脆不拖沓。
 */
private val settleSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 420f)

/**
 * 垂直卡片堆叠。
 *
 * @param modifier 容器 modifier。**容器高度决定卡高**（见 [CardStackTuning.cardHeightFraction]）。
 * @param tuning 视觉调参包，横竖屏各一套（[CardStackTuning.Landscape] / [CardStackTuning.Portrait]）
 * @param onCardClick 卡片点击回调
 * @param permanentLock 外部强制锁定（电台激活）
 */
@Composable
internal fun HelloCardCoverflow(
    modifier: Modifier = Modifier,
    tuning: CardStackTuning = CardStackTuning.Landscape,
    onCardClick: ((SlideCard) -> Unit)? = null,
    permanentLock: Boolean = false,
) {
    val state = rememberHelloCardsState()
    val cards = state.cards
    val locked = permanentLock || state.permanentLock

    // 当前"焦点索引"（浮点：拖动时是中间值，动画/静止时是整数）。
    // ⚠️ 循环模式下这个值是**不钳制**的：它可以无限增大/减小，通过 ringDelta()
    //    映射回真实卡号。这样"从最后一张继续往下滚"就是 offset 从 n-1 → n，
    //    而 n 经取模后是第 0 张，位置连续、无穿帮。
    //    静态时用 normalize() 把它收进 [0, n) —— 防止长时间运行后浮点精度劣化。
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    var lastAutoRotateAt by remember { mutableStateOf(currentTimeMillis()) }
    var userLockUntil by remember { mutableStateOf(0L) }

    // 卡数量变化 → 把 offset 收进合法区间（循环模式下"合法"是取模意义，见 normalize）
    LaunchedEffect(cards.size) {
        if (cards.isEmpty()) return@LaunchedEffect
        offset.snapTo(normalize(offset.value, cards.size))
    }

    // 自动轮播 + 临时锁（与 Pager 版同语义）
    LaunchedEffect(cards.size, locked) {
        while (isActive) {
            delay(120)
            if (locked || cards.size <= 1) continue
            val now = currentTimeMillis()
            if (userLockUntil in 1..<now) userLockUntil = 0L
            if (userLockUntil > now) continue
            if (now - lastAutoRotateAt < CF_AUTO_ROTATE_MS) continue
            // 循环模式下**不再判断"是否到尾"** —— 直接 +1 继续往下滑。
            // offset 变成 n 时，渲染层的 `rawIndex` 仍是连续的 n，只是取模后落到第 0 张，
            // 视觉上就是从底部滑上来的"下一张"，中间没有跳变。
            offset.animateTo(offset.value + 1f, animationSpec = settleSpec)
            // 动画结束后归一化，避免 offset 无限增长导致浮点精度劣化。
            // 仅在**动画确实跑完**时归一化：`animateTo` 是挂起函数，若被新的
            // animateTo/snapTo 抢占（拖动打断），此处不该再抢一次快照。
            if (offset.value >= cards.size || offset.value < 0f) {
                offset.snapTo(normalize(offset.value, cards.size))
            }
            lastAutoRotateAt = currentTimeMillis()
        }
    }

    // 永久锁（电台激活）—— 滑到 RADIO_STATUS 并停住（**只停自动轮播，不限制拖拽**）。
    // 用 ringDelta 取环形最短路径：从第 1 张滑到第 n-1 张时，应该**往回**走一格，
    // 而不是绕一整圈。
    LaunchedEffect(locked, cards.size) {
        if (!locked || cards.isEmpty()) return@LaunchedEffect
        val idx = cards.indexOfFirst { it.type == SlideType.RADIO_STATUS }
        if (idx < 0) return@LaunchedEffect
        val delta = ringDelta(idx.toFloat() - offset.value, cards.size)
        offset.animateTo(offset.value + delta, animationSpec = settleSpec)
        offset.snapTo(normalize(offset.value, cards.size))
    }

    BoxWithConstraints(modifier = modifier) {
        // 尺寸全部来自 tuning —— 本组件不感知横竖屏。
        val density = LocalDensity.current
        val cardHeight: Dp
        val cardWidth: Dp
        if (tuning.fillContainer) {
            // 竖屏：卡填满容器，卡形由调用方的 aspectRatio 决定（与原 Pager 版一致）
            cardWidth = maxWidth
            cardHeight = maxHeight
        } else {
            // 横屏：按容器高定卡高，卡宽 = 卡高 × aspect（受容器宽封顶）
            val h = (maxHeight.value * tuning.cardHeightFraction)
                .coerceAtMost(tuning.maxCardHeightDp)
            cardHeight = h.dp
            cardWidth = cardHeight * tuning.aspect
        }
        // 相邻卡中心距 = 卡高 × k + 虚拟间距（后者把卡与卡推得更开，见 CardStackTuning.cardSpacingDp）。
        // leanNeighbors 斜倚堆叠模式：步长恒为 1.0 卡高 —— 邻卡内缘恰好贴住中卡外缘，
        // 缩放/旋转都以内缘为轴（见下方 transformOrigin），外缘靠大倾角收进容器。
        val stepFactorEffective = if (tuning.leanNeighbors) 1f else tuning.stepFactor
        val stepPx = with(density) { (cardHeight * stepFactorEffective + tuning.cardSpacingDp.dp).toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 邻卡按 d×stepPx 偏移，d 可到 ±2 —— 竖屏卡与容器等高时，邻卡会落到
                // 容器外一两倍卡高，不裁剪就会盖住下方内容（如"为你推荐"）。裁掉。
                .clipToBounds()
                // 上下拖拽切页。
                // ⚠️ 循环模式下**不再用 `locked` 做门禁**：锁定只停自动轮播，用户拖拽依旧自由
                //    （用户裁定"锁定也循环"）。故 pointerInput 的 key 里去掉 locked。
                .pointerInput(cards.size) {
                    if (cards.size <= 1) return@pointerInput
                    var acc = 0f        // 本次拖拽累计位移（px）
                    var vTime = 0L      // 上一定位事件的时间戳（估速用）
                    var velocity = 0f   // 松手瞬间的速度（px/ms）
                    var total = 0f      // 本次拖拽累计绝对位移（区分"点按"与"拖动"）
                    var startIndex = 0  // 拖拽起点的整数格（翻页目标以此为基准，避免跳两格）
                    val threshold = CF_DRAG_THRESHOLD_PX

                    fun settle(target: Float) {
                        scope.launch {
                            offset.animateTo(target, animationSpec = settleSpec)
                            // 动画跑完才归一化：被打断（新拖动）时不抢快照
                            if (offset.value >= cards.size || offset.value < 0f) {
                                offset.snapTo(normalize(offset.value, cards.size))
                            }
                        }
                    }

                    // 落点到最近整数格（保证拖动后**一定**停在整点，不会卡在半路）
                    fun settleToNearest() {
                        val base = offset.value.roundToIntCompat()
                        val target = base + if (offset.value - base >= 0.5f) 1 else 0
                        settle(target.toFloat())
                    }

                    detectVerticalDragGestures(
                        onDragStart = {
                            acc = 0f; vTime = 0L; velocity = 0f; total = 0f
                            // 起点格必须在此刻取：拖动中 offset 是小数，
                            // 若在 onDragEnd 再 round，1.6 会算成 2、再 +1 变 3（跳两格）。
                            startIndex = offset.value.roundToIntCompat()
                            haptic.performDragStart()
                            // 拖拽立刻打断轮播动画，避免"手在拖、卡在自动走"的抢焦
                            scope.launch { offset.stop() }
                        },
                        onDragEnd = {
                            // 判定"是否翻页"：位移过阈值 **或** 快速甩动（flick）。
                            // 甩动判据让"轻快一划"也能翻页 —— 只靠位移会逼用户拖满 70px。
                            val pageTurn = kotlin.math.abs(acc) > threshold ||
                                kotlin.math.abs(velocity) > CF_FLING_VELOCITY_PX_PER_MS
                            if (pageTurn) {
                                // 翻一格：方向由位移（或甩动方向）决定，基准是**起点格**
                                val dir = if (acc != 0f) (if (acc < 0) 1 else -1)
                                else (if (velocity < 0) 1 else -1)
                                settle((startIndex + dir).toFloat())
                            } else {
                                // ⚠️ 未过阈值也必须吸附回最近整点 ——
                                //    否则 offset 停在 2.37 这种小数位，卡会永久卡在半路。
                                settleToNearest()
                            }
                            // 只有真正拖动过（非单纯点按）才落触觉 + 临时锁轮播
                            if (total > CF_DRAG_THRESHOLD_PX / 4f) {
                                haptic.performGestureEnd()
                                userLockUntil = currentTimeMillis() + CF_USER_LOCK_MS
                                // 同时重置轮播计时：否则若在轮播到期前一刻拖动，
                                // 锁一解除（5s 后）就会立刻自动翻页，观感突兀。
                                lastAutoRotateAt = currentTimeMillis()
                            }
                        },
                        // 手势被系统打断（如父级滚动抢占）时同样要落定，否则卡停在半路
                        onDragCancel = { settleToNearest() },
                        onVerticalDrag = { _, delta ->
                            acc += delta
                            total += kotlin.math.abs(delta)
                            // 估速：用 delta / Δt，Δt 取事件间隔；忽略过大的间隔（停顿后首个 delta 不代表速度）
                            val now = currentTimeMillis()
                            if (vTime != 0L) {
                                val dt = (now - vTime).toFloat()
                                if (dt in 1f..CF_VELOCITY_WINDOW_MS) {
                                    velocity = 0.7f * velocity + 0.3f * (delta / dt)
                                }
                            }
                            vTime = now
                            // 拖动时跟手（上拖 delta<0 → offset 增）。
                            // 循环模式下**不钳制** —— 可以一路拖过界，松手后落点自然取模。
                            scope.launch {
                                offset.snapTo(offset.value - delta / stepPx)
                            }
                        },
                    )
                },
        ) {
            // 只渲染焦点附近 ±2 张（Coverflow 无需渲染全部）。
            //
            // ⚠️ 循环模式下的关键点：**渲染用"展开索引"，只有取卡时才取模**。
            //   - `rawIndex = base + step` 是**连续序列**上的位置，它**不取模**。
            //     于是 `d = rawIndex - center` 永远落在 ±2 内，位置/缩放/旋转全都自然连续，
            //     不需要在这里做"最短路径"判断。
            //   - 只有 `cardIndex = ((rawIndex % n) + n) % n` 这一步把展开索引映射回真实卡号。
            //   若反过来（先取模再算 d），第 0 张在 center=n-1 时会被算出 d = -(n-1)，直接穿帮。
            val center = offset.value
            val base = center.roundToIntCompat()
            val n = cards.size

            if (n > 0) for (step in -2..2) {
                val rawIndex = base + step
                val d = rawIndex - center                // 展开索引下的带符号距离：>0 在下方
                val absD = kotlin.math.abs(d)
                if (absD > 2.5f) continue
                val cardIndex = ((rawIndex % n) + n) % n // 环形取模 → 真实卡号
                val eased = FastOutSlowInEasing.transform(absD.coerceIn(0f, 1f))
                val isCenter = absD < 0.5f

                // 离开缩小 / 进入放大：分两段连续缩放。
                //   段① |d| ∈ [0,1]：1 → neighborScale（中心 → 邻卡位）
                //   段② |d| ∈ [1,2]：1 → exitScale（邻卡位 → 更外侧，即"离开时继续缩小"）
                // 两段相乘，故 |d|=1 处恰为 neighborScale、|d|≥2 处为 neighborScale × exitScale。
                val nearT = eased                                   // 0→1 段已由 eased 提供
                val farEased = FastOutSlowInEasing.transform(((absD - 1f) / 1f).coerceIn(0f, 1f))
                val scale = (1f - nearT * (1f - tuning.neighborScale)) *
                    (1f - farEased * (1f - tuning.exitScale))
                // alpha 只走一段：到邻卡位即降到 neighborAlpha 后不再继续淡（再往外已基本出画）
                val alpha = 1f - nearT * (1f - tuning.neighborAlpha)
                val rotX = if (d > 0) -tuning.neighborRotation * eased
                else tuning.neighborRotation * eased

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(cardWidth)
                        .height(cardHeight)
                        // 手动定位：把卡片中心按 `d × stepPx` 纵向偏移
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints)
                            layout(p.width, p.height) {
                                p.placeRelative(
                                    x = (constraints.maxWidth - p.width) / 2,
                                    y = ((constraints.maxHeight - p.height) / 2 + (d * stepPx)).toInt(),
                                )
                            }
                        }
                        // 层序：离中心越近越高（修正"后页盖前页"）
                        .zIndex(10f - absD)
                        .graphicsLayer {
                            // 斜倚堆叠：旋转轴钉在邻卡的**内缘**（贴中卡那条边）——
                            //   上邻卡（d<0）绕下缘、下邻卡（d>0）绕上缘。内缘位置不随缩放/旋转移动，
                            //   卡像 panel 斜靠在中卡上下，外缘向后收进容器。
                            // 中卡（|d|<0.5 视觉上）scale=1、rotation=0，轴点取哪都不影响渲染。
                            transformOrigin = when {
                                !tuning.leanNeighbors -> TransformOrigin.Center
                                d < 0f -> TransformOrigin(0.5f, 1f)
                                else -> TransformOrigin(0.5f, 0f)
                            }
                            cameraDistance = tuning.cameraDistance
                            rotationX = rotX
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                ) {
                    // key(cardId)：循环取模会让**同一槽位**在不同时刻轮到不同卡。
                    // 若不显式给 key，Compose 按"槽位"复用节点 —— 归一化（5.0→0.0）时
                    // 槽位里换了一张卡，卡内的状态（如 AsyncImage 已加载的封面）会被错误继承/重置。
                    // 用 cardId 作 key，保证"同一张卡无论滚到哪个槽位"都复用同一个 composition。
                    key(cards[cardIndex].cardId) {
                        FamilyDispatch(
                            card = cards[cardIndex],
                            modifier = Modifier.fillMaxSize(),
                            onCardClick = onCardClick,
                            isCurrentPage = isCenter,
                        )
                    }
                }
            }

            // 右侧指示点（主题色：浅底可见）
            val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                cards.forEachIndexed { index, _ ->
                    // 高亮判据也必须走**环形距离**：直接用 |index − offset| 在循环模式下会跳
                    //（offset 归一化到 0 附近时，最后一张的点会错误地熄灭）。
                    val active = kotlin.math.abs(ringDelta(index.toFloat() - offset.value, n)) < 0.5f
                    Box(
                        modifier = Modifier
                            .size(if (active) 6.dp else 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (active) dotColor else dotColor.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * 环形最短有符号距离：把任意实数 `raw`（可能是 `i - center` 的原始差值）
 * 折算到 `[-n/2, n/2]` 区间。
 *
 * 为什么必须这样算：Coverflow 的 `d` 同时决定**位置**（`y = d×step`）与**外观**（缩放/旋转/透明度）。
 * 非循环时 `d = i - center` 天然落在 ±2 内；循环后 `i` 可能来自取模前的越界索引，
 * 例如 `n = 5`、`center = 4`、要展示第 0 张：直接减得 `d = -4`（卡片飞到屏幕上方很远处），
 * 但环形的正确答案是 `d = +1`（从**底部**滑上来）。
 *
 * 注意入参是**带符号实差值**而非两个整数索引 —— 拖动过程中 `center` 是小数，
 * 差值也必须是小数，否则跟手动画会抖动。
 */
private fun ringDelta(raw: Float, n: Int): Float {
    if (n <= 0) return 0f
    val half = n / 2f
    var d = raw % n
    if (d > half) d -= n
    if (d < -half) d += n
    return d
}

/** 把任意浮点焦点收进 `[0, n)`：静态时调用，防止 offset 无限增长导致浮点精度劣化 */
private fun normalize(value: Float, n: Int): Float {
    if (n <= 0) return 0f
    return ((value % n) + n) % n
}

/** 取整（避免依赖 kotlin.math.roundToInt 的浮点边界差异） */
private fun Float.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
