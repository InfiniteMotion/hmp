package com.hearablemusic.player.ui.common.pages.base

import com.hearablemusic.player.ui.common.design.colors.ColorTokens
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.remember
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.unknown
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

/**
 * 动态背景风格枚举
 */
enum class BackgroundStyle {
    FLUID,  // 流体极光 (推荐)
    SPOTS,  // 沉浸光斑 (经典)
    BLUR    // 复古模糊 (简约)
}

/**
 * 辅助函数：色相偏移
 * @param amount 偏移角度 (0-360)
 */
fun Color.shiftHue(amount: Float): Color {
    // RGB→HSV→偏移→RGB（与 android.graphics.Color.colorToHSV/HSVToColor 数学等价的纯 Kotlin 实现）
    val r = this.red; val g = this.green; val b = this.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val delta = max - min
    val v = max
    val s = if (max == 0f) 0f else delta / max
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta % 6f + 6f) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val newH = (h + amount + 360f) % 360f
    // HSV→RGB
    val c = (1f - abs(2f * s - 1f)) * v
    val x = c * (1f - abs((newH / 60f) % 2f - 1f))
    val m = v - c
    val (r0, g0, b0) = when {
        newH < 60f -> Triple(c, x, 0f)
        newH < 120f -> Triple(x, c, 0f)
        newH < 180f -> Triple(0f, c, x)
        newH < 240f -> Triple(0f, x, c)
        newH < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r0 + m, g0 + m, b0 + m)
}

/**
 * 动态背景主入口组件
 */
@Composable
fun DynamicBackground(
    albumArtUri: String?,
    paletteColors: PaletteColors,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    style: BackgroundStyle = BackgroundStyle.FLUID // 默认为流体效果
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (style) {
            BackgroundStyle.FLUID -> FluidBackground(albumArtUri, isDarkTheme)
            BackgroundStyle.SPOTS -> SpotsBackground(paletteColors, isDarkTheme)
            BackgroundStyle.BLUR -> BlurBackground(albumArtUri, isDarkTheme)
        }

        // 通用遮罩层：确保内容可读性
        // 暗色模式：顶部和底部渐变遮罩
        if (isDarkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Transparent,
                                Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
        } else {
            // 亮色模式：整体极淡的白色遮罩，增加通透感
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }
    }
}

/**
 * 1. 流体极光背景 (Fluid Aurora)
 * 基于双层图片相位漂移，模拟液体流动效果
 */
@Composable
fun FluidBackground(
    albumArtUri: String?,
    isDarkTheme: Boolean
) {
    val metrics = LocalHmpMetrics.current
    // 外层容器必须裁切，否则模糊效果会溢出屏幕边界
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RectangleShape)
    ) {
        val density = LocalDensity.current
        val maxPx = with(density) { maxOf(maxWidth, maxHeight).toPx() }

        // 背景底色 - 亮色模式改用极淡灰，增加层次感
        val baseColor = if (isDarkTheme) ColorTokens.DarkBackground else ColorTokens.LightBackgroundAlt
        Box(modifier = Modifier.fillMaxSize().background(baseColor))

        // 动画控制
        val transition = rememberInfiniteTransition(label = "fluidBackground")

        // 缓慢的流动曲线
        val flowEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

        // 偏移量改用视口百分比（不再固定像素），窗口缩放时自动适配
        val offsetFrac1 by transition.animateFloat(
            initialValue = -0.14f, targetValue = 0.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = flowEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "offsetFrac1"
        )
        val rotation1 by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 60000, easing = LinearEasing), // 60s 周期
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation1"
        )

        // 图层2动画：垂直漂移 + 逆时针旋转 (对冲)
        val offsetFrac2 by transition.animateFloat(
            initialValue = -0.11f, targetValue = 0.11f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 25000, easing = flowEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "offsetFrac2"
        )
        val rotation2 by transition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 90000, easing = LinearEasing), // 90s 周期
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation2"
        )

        // 底层旋转 (新增) - 逆时针缓慢旋转
        val baseRotation by transition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 120000, easing = LinearEasing), // 120s 极慢周期
                repeatMode = RepeatMode.Restart
            ),
            label = "baseRotation"
        )

        // 缩放呼吸 — 提高到 4.2x 确保旋转+偏移后仍全覆盖
        val scale by transition.animateFloat(
            initialValue = 4.2f,
            targetValue = 4.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = flowEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        // 图片透明度控制 - 亮色模式提升透明度，防止太淡
        val imageAlpha = if (isDarkTheme) 0.6f else 0.4f

        // 增加饱和度和对比度的矩阵
        val colorMatrix = ColorMatrix().apply {
            setToSaturation(1.6f) // 提升60%饱和度，去灰

            // 亮色模式额外增加对比度
            if (!isDarkTheme) {
                val contrast = 1.2f
                val translate = (-.5f * contrast + .5f) * 255f
                this.timesAssign(
                    ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
        val colorFilter = ColorFilter.colorMatrix(colorMatrix)

        // 使用 Crossfade 实现淡入淡出切换
        Crossfade(
            targetState = albumArtUri,
            animationSpec = tween(durationMillis = 1000), // 1秒淡入淡出
            label = "albumArtCrossfade"
        ) { currentAlbumArtUri ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 图层1 - 氛围层 (底层)
                Image(
                    painter = rememberAsyncImagePainter(
                        model = currentAlbumArtUri,
                        placeholder = painterResource(Res.drawable.unknown)
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                        .offset { IntOffset((offsetFrac1 * maxPx).roundToInt(), 0) }
                        .graphicsLayer {
                            rotationZ = baseRotation // 底层独立旋转
                            alpha = imageAlpha
                        }
                        .blur(metrics.block) // 大幅降低模糊，保留轮廓
                )

                // 图层2 (叠加层) - 流动层 (顶层)
                Image(
                    painter = rememberAsyncImagePainter(
                        model = currentAlbumArtUri,
                        placeholder = painterResource(Res.drawable.unknown)
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale * 1.1f)
                        .offset { IntOffset(0, (offsetFrac2 * maxPx).roundToInt()) }
                        .graphicsLayer {
                            rotationZ = rotation1 // 顶层顺时针
                            alpha = imageAlpha * 0.7f
                        }
                        .blur(metrics.section) // 极低模糊，清晰的流动纹理
                )
            }
        }
    }
}

/**
 * 2. 沉浸光斑背景 (Immersive Light Blooms)
 *
 * 5 个独立光斑在不同轨道上以可见速度漂移，各自呼吸脉动。
 * 所有坐标按视口百分比计算——天然适配窗口缩放，永不露边。
 */
@Composable
fun SpotsBackground(
    paletteColors: PaletteColors,
    isDarkTheme: Boolean
) {
    val metrics = LocalHmpMetrics.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val baseBackgroundColor = if (isDarkTheme) ColorTokens.DarkBackground else ColorTokens.LightBackground
        Box(modifier = Modifier.fillMaxSize().background(baseBackgroundColor))

        // ── 权重驱动：3-7 个光斑 ──────────────────────────────
        val allPeaks = paletteColors.peaks.ifEmpty { listOf(paletteColors.primary) }
        val allWeights = paletteColors.peakWeights.ifEmpty { allPeaks.map { 1f / allPeaks.size } }
        val bloomCount = allPeaks.size.coerceIn(3, 7)
        val peaks = allPeaks.take(bloomCount)
        val weights = allWeights.take(bloomCount)
        val maxW = weights.maxOrNull() ?: 1f

        val colors = List(bloomCount) { i ->
            val c = peaks[i]; val gray = (c.red + c.green + c.blue) / 3f
            val sat = Color((gray + (c.red - gray) * 1.6f).coerceIn(0f, 1f),
                            (gray + (c.green - gray) * 1.6f).coerceIn(0f, 1f),
                            (gray + (c.blue - gray) * 1.6f).coerceIn(0f, 1f), 1f)
            val f = 0.4f + weights[i] / maxW * 0.6f
            if (f >= 1f) Color(sat.red + (1f - sat.red) * (f - 1f).coerceIn(0f, 1f),
                               sat.green + (1f - sat.green) * (f - 1f).coerceIn(0f, 1f),
                               sat.blue + (1f - sat.blue) * (f - 1f).coerceIn(0f, 1f), 1f)
            else Color(sat.red * f, sat.green * f, sat.blue * f, 1f)
        }

        data class Bloom(
            val minX: Float, val maxX: Float, val periodX: Int,
            val minY: Float, val maxY: Float, val periodY: Int,
            val radius: Float, val breathePeriodMs: Int
        )

        val blooms = remember(peaks) {
            val rng = kotlin.random.Random(peaks.hashCode())
            val LO = 0.02f; val HI = 0.98f
            fun span(a: Float, b: Float) = rng.nextFloat() * (b - a) + minOf(a, b)

            List(bloomCount) { i ->
                val w = weights[i] / maxW
                val radius = 0.28f + w * 0.82f
                val cx = 0.5f + (rng.nextFloat() - 0.5f) * (1f - w) * 0.94f
                val cy = 0.5f + (rng.nextFloat() - 0.5f) * (1f - w) * 0.94f
                val hsx = 0.06f + (1f - w) * span(0.15f, 0.28f)
                val hsy = 0.06f + (1f - w) * span(0.15f, 0.28f)
                val pBase = (20000 + w * 30000).toInt()
                Bloom(minX = (cx - hsx).coerceIn(LO, HI), maxX = (cx + hsx).coerceIn(LO, HI),
                    periodX = rng.nextInt(8000) + pBase,
                    minY = (cy - hsy).coerceIn(LO, HI), maxY = (cy + hsy).coerceIn(LO, HI),
                    periodY = rng.nextInt(8000) + pBase + 4000,
                    radius = radius,
                    breathePeriodMs = rng.nextInt(8000) + (14000 + ((1f - w) * 6000f).toInt()))
            }
        }

        val alphas = List(bloomCount) { i ->
            val a = 0.20f + (weights[i] / maxW) * 0.45f
            if (isDarkTheme) a else a * 0.75f
        }

        // ── 动画：X/Y 独立漂移 + 呼吸 ──────────────────────────
        val transition = rememberInfiniteTransition(label = "spotsCanvas")
        val ease = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

        val startPhases = remember(peaks) {
            val rng = kotlin.random.Random(peaks.hashCode() + 42)
            List(bloomCount) { rng.nextFloat() to rng.nextFloat() }
        }
        val posX = List(bloomCount) { i ->
            val min = blooms[i].minX; val max = blooms[i].maxX
            transition.animateFloat(min + (max - min) * startPhases[i].first, max,
                infiniteRepeatable(tween(blooms[i].periodX, easing = ease), RepeatMode.Reverse), "px$i")
        }
        val posY = List(bloomCount) { i ->
            val min = blooms[i].minY; val max = blooms[i].maxY
            transition.animateFloat(min + (max - min) * startPhases[i].second, max,
                infiniteRepeatable(tween(blooms[i].periodY, easing = ease), RepeatMode.Reverse), "py$i")
        }
        val scales = List(bloomCount) { i ->
            val w = weights[i] / maxW
            val (minS, maxS) = (0.84f + w * 0.04f) to (1.16f - w * 0.04f)
            transition.animateFloat(minS, maxS,
                infiniteRepeatable(tween(blooms[i].breathePeriodMs, easing = ease), RepeatMode.Reverse), "sc$i")
        }
        val breatheAlphas = List(bloomCount) { i ->
            transition.animateFloat(0.88f, 1.0f,
                infiniteRepeatable(tween(blooms[i].breathePeriodMs, easing = ease), RepeatMode.Reverse), "ba$i")
        }

        data class NoiseDot(val xf: Float, val yf: Float, val gray: Float, val alpha: Float)
        val noiseDots = remember(peaks) {
            val rng = kotlin.random.Random(peaks.hashCode() + 777)
            List(800) { NoiseDot(rng.nextFloat(), rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 0.05f) }
        }

        // ── 绘制：最重光斑先绘（底色），轻的光斑后绘（上层）──
        Canvas(modifier = Modifier.fillMaxSize().blur(metrics.cozy)) {
            val w = size.width; val h = size.height; val maxDim = maxOf(w, h)
            val drawOrder = (0 until bloomCount).sortedByDescending { weights[it] }
            for (i in drawOrder) {
                val b = blooms[i]; val cx = posX[i].value * w; val cy = posY[i].value * h
                val radius = b.radius * maxDim * scales[i].value
                val a = (alphas[i] * breatheAlphas[i].value).coerceIn(0f, 1f)
                val c = colors[i]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = a),
                            c.copy(alpha = a * 0.92f), c.copy(alpha = a * 0.82f),
                            c.copy(alpha = a * 0.70f), c.copy(alpha = a * 0.56f),
                            c.copy(alpha = a * 0.42f), c.copy(alpha = a * 0.30f),
                            c.copy(alpha = a * 0.20f), c.copy(alpha = a * 0.12f),
                            c.copy(alpha = a * 0.06f), c.copy(alpha = a * 0.025f),
                            c.copy(alpha = a * 0.008f), Transparent),
                        center = Offset(cx, cy), radius = radius),
                    center = Offset(cx, cy), radius = radius)
            }
            // 噪点抖动
            for (d in noiseDots) {
                drawCircle(Color(d.gray, d.gray, d.gray, d.alpha),
                    1.8f, Offset(d.xf * w, d.yf * h))
            }
        }
    }
}

/**
 * 3. 复古模糊背景 (Retro Blur)
 * 极简的模糊放大效果，最省电
 */
@Composable
fun BlurBackground(
    albumArtUri: String?,
    isDarkTheme: Boolean
) {
    val metrics = LocalHmpMetrics.current
    Box(modifier = Modifier.fillMaxSize()) {
        val baseColor = if (isDarkTheme) ColorTokens.DarkBackground else ColorTokens.LightBackground
        Box(modifier = Modifier.fillMaxSize().background(baseColor))

        val transition = rememberInfiniteTransition(label = "blurBackground")
        val scale by transition.animateFloat(
            initialValue = 1.2f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        val imageAlpha = if (isDarkTheme) 0.4f else 0.3f

        Image(
            painter = rememberAsyncImagePainter(
                model = albumArtUri,
                placeholder = painterResource(Res.drawable.unknown)
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .graphicsLayer { alpha = imageAlpha }
                .blur(metrics.bar) // 适度模糊，保留更多轮廓
        )
    }
}
