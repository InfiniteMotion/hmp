package com.hearablemusic.player.ui.common.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.hearablemusic.player.ui.common.design.colors.ColorTokens
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors
import kotlin.math.pow

object ThemeManager {

    fun getLightColorScheme(): ColorScheme = lightColorScheme(
        primary = ColorTokens.LightPrimary,
        onPrimary = ColorTokens.LightOnPrimary,
        primaryContainer = ColorTokens.LightPrimaryContainer,
        onPrimaryContainer = ColorTokens.LightOnPrimaryContainer,
        secondary = ColorTokens.LightSecondary,
        background = ColorTokens.LightBackground,
        surface = ColorTokens.LightSurface,
        error = ColorTokens.LightError,
        surfaceTint = ColorTokens.LightPrimary,
    )

    fun getDarkColorScheme(): ColorScheme = darkColorScheme(
        primary = ColorTokens.DarkPrimary,
        onPrimary = ColorTokens.DarkOnPrimary,
        primaryContainer = ColorTokens.DarkPrimaryContainer,
        onPrimaryContainer = ColorTokens.DarkOnPrimaryContainer,
        secondary = ColorTokens.DarkSecondary,
        background = ColorTokens.DarkBackground,
        surface = ColorTokens.DarkSurface,
        error = ColorTokens.DarkError,
        surfaceTint = ColorTokens.DarkPrimary,
    )

    fun generateDynamicColorScheme(paletteColors: PaletteColors, isDarkTheme: Boolean): ColorScheme {
        val rawPrimary = paletteColors.primary
        val bg = paletteColors.background
        val accent = paletteColors.accent

        // 确保 primary 与当前模式的实际背景对比度 ≥ 3:1
        val actualBg = if (isDarkTheme) bg else ColorTokens.LightBackground
        val primary = ensureContrast(rawPrimary, actualBg, 3.0f)

        return if (isDarkTheme) {
            darkColorScheme(
                primary = primary,
                onPrimary = ColorTokens.DarkOnPrimary,
                primaryContainer = primary.copy(alpha = 0.15f),
                onPrimaryContainer = ColorTokens.DarkOnPrimaryContainer,
                secondary = accent,
                onSecondary = ColorTokens.DarkOnPrimary,
                background = bg,
                surface = bg,
                error = ColorTokens.DarkError,
                surfaceTint = primary.copy(alpha = 0.06f),
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = ColorTokens.LightOnPrimary,
                primaryContainer = primary.copy(alpha = 0.10f),
                onPrimaryContainer = ColorTokens.LightOnPrimaryContainer,
                secondary = accent,
                onSecondary = ColorTokens.LightOnPrimary,
                background = ColorTokens.LightBackground,
                surface = ColorTokens.LightSurface,
                error = ColorTokens.LightError,
                surfaceTint = primary.copy(alpha = 0.05f),
            )
        }
    }

    // ── WCAG 对比度辅助 ──────────────────────────────────────────
    private fun relativeLuminance(r: Int, g: Int, b: Int): Float {
        fun linearize(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) {
                s / 12.92
            } else {
                ((s + 0.055) / 1.055).pow(2.4)
            }
        }
        return (0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)).toFloat()
    }

    private fun colorLuminance(color: Color): Float {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return relativeLuminance(r, g, b)
    }

    private fun contrastRatio(l1: Float, l2: Float): Float {
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** 朝远离背景方向推进直到对比度 ≥ target */
    private fun ensureContrast(
        color: Color,
        background: Color,
        target: Float,
    ): Color {
        var r = (color.red * 255).toInt().coerceIn(0, 255)
        var g = (color.green * 255).toInt().coerceIn(0, 255)
        var b = (color.blue * 255).toInt().coerceIn(0, 255)
        val bgR = (background.red * 255).toInt().coerceIn(0, 255)
        val bgG = (background.green * 255).toInt().coerceIn(0, 255)
        val bgB = (background.blue * 255).toInt().coerceIn(0, 255)
        val bgLum = relativeLuminance(bgR, bgG, bgB)

        for (i in 0 until 15) {
            val curLum = relativeLuminance(r, g, b)
            if (contrastRatio(curLum, bgLum) >= target) break
            val dr = (r - bgR).let { if (it == 0) if (bgR < 128) 1 else -1 else it / 4 }
            val dg = (g - bgG).let { if (it == 0) if (bgG < 128) 1 else -1 else it / 4 }
            val db = (b - bgB).let { if (it == 0) if (bgB < 128) 1 else -1 else it / 4 }
            r = (r + dr).coerceIn(0, 255)
            g = (g + dg).coerceIn(0, 255)
            b = (b + db).coerceIn(0, 255)
        }
        return Color(
            0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong(),
        )
    }

    fun getPresetColorScheme(isDarkTheme: Boolean): ColorScheme = if (isDarkTheme) {
        getDarkColorScheme()
    } else {
        getLightColorScheme()
    }
}
