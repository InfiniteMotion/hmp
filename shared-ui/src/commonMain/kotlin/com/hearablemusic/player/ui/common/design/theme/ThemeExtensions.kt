package com.hearablemusic.player.ui.common.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors

/**
 * 主题方案的统一入口。
 *
 * 原先是「可替换扩展点」：一个全局 `currentExtension` + `setThemeExtension()`。
 * 但全仓从未有调用方设置扩展，且全局 var 不可观察 —— 只留下间接层与状态失真。
 * 现在直接委托 [ThemeManager]，保留这两个函数名给既有调用点。
 */
object ThemeExtensionManager {
    @Composable
    fun getColorScheme(isDarkTheme: Boolean): ColorScheme = ThemeManager.getPresetColorScheme(isDarkTheme)

    @Composable
    fun generateDynamicColorScheme(paletteColors: PaletteColors, isDarkTheme: Boolean): ColorScheme = ThemeManager.generateDynamicColorScheme(paletteColors, isDarkTheme)
}
