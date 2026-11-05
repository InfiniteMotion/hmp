package com.hearablemusic.player.ui.common.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors

/**
 * 主题扩展接口，用于自定义主题
 */
interface ThemeExtension {
    fun getColorScheme(isDarkTheme: Boolean): ColorScheme
    fun generateDynamicColorScheme(paletteColors: PaletteColors, isDarkTheme: Boolean): ColorScheme
}

/**
 * 默认主题扩展实现
 */
class DefaultThemeExtension : ThemeExtension {
    override fun getColorScheme(isDarkTheme: Boolean): ColorScheme = ThemeManager.getPresetColorScheme(isDarkTheme)

    override fun generateDynamicColorScheme(paletteColors: PaletteColors, isDarkTheme: Boolean): ColorScheme = ThemeManager.generateDynamicColorScheme(paletteColors, isDarkTheme)
}

/**
 * 主题扩展管理类
 */
object ThemeExtensionManager {
    private var currentExtension: ThemeExtension = DefaultThemeExtension()

    fun setThemeExtension(extension: ThemeExtension) {
        currentExtension = extension
    }

    fun getThemeExtension(): ThemeExtension = currentExtension

    @Composable
    fun getColorScheme(isDarkTheme: Boolean): ColorScheme = currentExtension.getColorScheme(isDarkTheme)

    @Composable
    fun generateDynamicColorScheme(paletteColors: PaletteColors, isDarkTheme: Boolean): ColorScheme = currentExtension.generateDynamicColorScheme(paletteColors, isDarkTheme)
}
