package com.hearablemusic.player.ui.common.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.music.MusicInfo
import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * 封面取色主题 VM。
 *
 * 架构分层：
 * - 像素获取 → [AlbumArtPixelsLoader]（平台基础设施，各平台实现）
 * - 播放状态 → 冻结接口 [PlaybackController]
 * - 取色算法 → 本类（3D 直方图峰值检测 + WCAG 对比度）
 */
class ThemeViewModel(
    private val pixelsLoader: AlbumArtPixelsLoader,
    playbackController: PlaybackController
) : ViewModel() {

    private val paletteCache = mutableMapOf<String, PaletteColors>()
    private val _paletteColors = MutableStateFlow(PaletteColors())
    val paletteColors: StateFlow<PaletteColors> = _paletteColors.asStateFlow()

    val currentPlayingMusic: StateFlow<MusicInfo?> = playbackController.currentPlayingMusic

    init {
        viewModelScope.launch {
            currentPlayingMusic
                .filterNotNull()
                .collectLatest { musicInfo ->
                    extractPaletteColors(musicInfo.music.albumArtUri)
                }
        }
    }

    private fun extractPaletteColors(albumArtUri: String?) {
        if (albumArtUri == null) {
            _paletteColors.value = PaletteColors()
            return
        }

        paletteCache[albumArtUri]?.let {
            _paletteColors.value = it
            return
        }

        viewModelScope.launch {
            try {
                val pixels = pixelsLoader.loadPixels(albumArtUri)
                val colors = if (pixels != null) {
                    withContext(Dispatchers.Default) { analyzeColors(pixels) }
                } else {
                    PaletteColors()
                }

                if (paletteCache.size >= 50) {
                    paletteCache.remove(paletteCache.keys.first())
                }
                paletteCache[albumArtUri] = colors
                _paletteColors.value = colors
            } catch (e: Exception) {
                _paletteColors.value = PaletteColors()
            }
        }
    }

    /**
     * G6：对任意封面 URI 取色，**不写全局主题态**（供推荐页英雄区等页面局部使用）。
     * 复用同一 paletteCache / pixelsLoader / analyzeColors。
     */
    suspend fun paletteFor(albumArtUri: String?): PaletteColors {
        if (albumArtUri.isNullOrBlank()) return PaletteColors()
        paletteCache[albumArtUri]?.let { return it }
        return try {
            val pixels = pixelsLoader.loadPixels(albumArtUri)
            val colors = if (pixels != null) {
                withContext(Dispatchers.Default) { analyzeColors(pixels) }
            } else {
                PaletteColors()
            }
            if (paletteCache.size >= 50) {
                paletteCache.remove(paletteCache.keys.first())
            }
            paletteCache[albumArtUri] = colors
            colors
        } catch (e: Exception) {
            PaletteColors()
        }
    }

    // ── 3D 颜色直方图 + 峰值检测 ─────────────────────────────────

    private data class ColorPeak(
        val r: Int, val g: Int, val b: Int,
        val weight: Float
    )

    private fun analyzeColors(pixels: IntArray): PaletteColors {
        val total = pixels.size
        if (total == 0) return PaletteColors()

        val BINS = 24
        val hist = FloatArray(BINS * BINS * BINS)
        for (px in pixels) {
            if ((px ushr 24) <= 0x40) continue
            val r = ((px shr 16) and 0xFF) * BINS / 256
            val g = ((px shr 8) and 0xFF) * BINS / 256
            val b = (px and 0xFF) * BINS / 256
            val idx = (r * BINS * BINS) + (g * BINS) + b
            hist[idx] += 1f
        }

        val rawPeaks = mutableListOf<ColorPeak>()
        for (r in 0 until BINS) {
            for (g in 0 until BINS) {
                for (b in 0 until BINS) {
                    val idx = (r * BINS * BINS) + (g * BINS) + b
                    val v = hist[idx]
                    if (v <= 0f) continue
                    var isPeak = true
                    check@ for (dr in -1..1) {
                        val nr = r + dr; if (nr !in 0 until BINS) continue
                        for (dg in -1..1) {
                            val ng = g + dg; if (ng !in 0 until BINS) continue
                            for (db in -1..1) {
                                if (dr == 0 && dg == 0 && db == 0) continue
                                val nb = b + db; if (nb !in 0 until BINS) continue
                                if (hist[(nr * BINS * BINS) + (ng * BINS) + nb] >= v) {
                                    isPeak = false; break@check
                                }
                            }
                        }
                    }
                    if (isPeak) {
                        rawPeaks.add(ColorPeak(
                            r = (r * 256 + 128) / BINS,
                            g = (g * 256 + 128) / BINS,
                            b = (b * 256 + 128) / BINS,
                            weight = v / total.toFloat()
                        ))
                    }
                }
            }
        }

        if (rawPeaks.isEmpty()) return PaletteColors()

        rawPeaks.sortByDescending { it.weight }

        val merged = mutableListOf<ColorPeak>()
        for (candidate in rawPeaks) {
            if (merged.any { rgbDist(candidate, it) < 30f }) continue
            merged.add(candidate)
        }

        val threshold = 0.006f
        val filtered = merged.filter { it.weight >= threshold }
        var detected = if (filtered.size >= 2) filtered
            else merged.take(2).ifEmpty { filtered }

        if (detected.size < 3) {
            val padded = detected.toMutableList()
            data class PadAnchor(val peak: ColorPeak, val sat: Float, val lgh: Float, val hue: Float)
            val anchors = detected.map { p ->
                val hsl = rgbToHsl(p.r, p.g, p.b)
                PadAnchor(p, hsl[1], hsl[2], hsl[0])
            }.sortedByDescending { it.sat }
            val minW = detected.minOf { it.weight }
            var idx = 0
            while (padded.size < 3) {
                val a = anchors[idx % anchors.size]
                val h = (a.hue + (padded.size - detected.size + 1) * (360f / (4 - detected.size))) % 360f
                val s = (a.sat * 1.4f).coerceIn(0.45f, 1f)
                val l = (a.lgh * (0.5f + (idx % 3) * 0.3f)).coerceIn(0.12f, 0.85f)
                val (r, g, b) = hslToRgb(h, s, l)
                padded.add(ColorPeak(r, g, b, minW * 0.4f))
                idx++
            }
            detected = padded
        }

        data class LabeledPeak(val peak: ColorPeak, val saturation: Float, val lightness: Float, val hue: Float)

        val labeled = detected.map { p ->
            val hsl = rgbToHsl(p.r, p.g, p.b)
            LabeledPeak(p, hsl[1], hsl[2], hsl[0])
        }

        fun ColorPeak.toColor() = Color(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())

        val totalW = detected.sumOf { it.weight.toDouble() }.toFloat()
        val peaks = detected.map { it.toColor() }
        val peakWeights = detected.map { (it.weight / totalW).coerceIn(0f, 1f) }

        val background = labeled
            .sortedBy { it.lightness + it.saturation * 0.3f }
            .first().peak.toColor()

        val bgLum = relativeLuminance(background)

        val primaryCandidate = labeled
            .sortedByDescending { it.saturation }
            .firstOrNull { contrastRatio(it.peak.toColor(), bgLum) >= 3.0f }

        val primary = if (primaryCandidate != null) {
            primaryCandidate.peak.toColor()
        } else {
            val best = labeled.maxByOrNull { it.saturation }!!.peak
            ensureContrast(best.toColor(), background, 3.0f)
        }

        val pr = (primary.red * 255f).toInt().coerceIn(0, 255)
        val pg = (primary.green * 255f).toInt().coerceIn(0, 255)
        val pb = (primary.blue * 255f).toInt().coerceIn(0, 255)
        val primaryHue = rgbToHsl(pr, pg, pb)[0]

        val accent = detected
            .map { it.toColor() }
            .maxByOrNull { hueDist(it, primaryHue) }!!
            .let { if (contrastRatio(it, bgLum) < 2.0f) primary else it }

        return PaletteColors(
            peaks = peaks,
            peakWeights = peakWeights,
            primary = primary,
            background = background,
            accent = accent
        )
    }

    private fun rgbDist(a: ColorPeak, b: ColorPeak): Float {
        val dr = a.r - b.r; val dg = a.g - b.g; val db = a.b - b.b
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    // ── WCAG 对比度与辅助函数 ──────────────────────────────────

    private fun linearize(c: Float): Float {
        val s = c / 255f
        return if (s <= 0.04045f) s / 12.92f
        else ((s + 0.055f) / 1.055f).pow(2.4f).toFloat()
    }

    private fun relativeLuminance(r: Int, g: Int, b: Int): Float =
        0.2126f * linearize(r.toFloat()) + 0.7152f * linearize(g.toFloat()) + 0.0722f * linearize(b.toFloat())

    private fun relativeLuminance(color: Color): Float {
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        return relativeLuminance(r, g, b)
    }

    private fun contrastRatio(lum1: Float, lum2: Float): Float {
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun contrastRatio(color: Color, bgLum: Float): Float =
        contrastRatio(relativeLuminance(color), bgLum)

    private fun ensureContrast(color: Color, background: Color, target: Float): Color {
        val bgR = (background.red * 255f).toInt()
        val bgG = (background.green * 255f).toInt()
        val bgB = (background.blue * 255f).toInt()
        var r = (color.red * 255f).toInt()
        var g = (color.green * 255f).toInt()
        var b = (color.blue * 255f).toInt()

        for (i in 0 until 20) {
            if (contrastRatio(relativeLuminance(r, g, b), relativeLuminance(bgR, bgG, bgB)) >= target) break
            r += ((r - bgR) * 0.15f).toInt().coerceIn(1, 30) * if (r > bgR) 1 else -1
            g += ((g - bgG) * 0.15f).toInt().coerceIn(1, 30) * if (g > bgG) 1 else -1
            b += ((b - bgB) * 0.15f).toInt().coerceIn(1, 30) * if (b > bgB) 1 else -1
            r = r.coerceIn(0, 255); g = g.coerceIn(0, 255); b = b.coerceIn(0, 255)
        }
        return Color(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }

    private fun hueDist(color: Color, refHue: Float): Float {
        val r = (color.red * 255f).toInt()
        val g = (color.green * 255f).toInt()
        val b = (color.blue * 255f).toInt()
        val h = rgbToHsl(r, g, b)[0]
        val diff = kotlin.math.abs(h - refHue)
        return if (diff > 180f) 360f - diff else diff
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Triple(
            ((r1 + m) * 255f).toInt().coerceIn(0, 255),
            ((g1 + m) * 255f).toInt().coerceIn(0, 255),
            ((b1 + m) * 255f).toInt().coerceIn(0, 255))
    }

    private fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val l = (max + min) / 2f

        if (max == min) {
            return floatArrayOf(0f, 0f, l)
        }

        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

        val h = when (max) {
            rf -> ((gf - bf) / d + (if (gf < bf) 6 else 0)) / 6f
            gf -> ((bf - rf) / d + 2) / 6f
            else -> ((rf - gf) / d + 4) / 6f
        }

        return floatArrayOf(h * 360f, s, l)
    }
}
