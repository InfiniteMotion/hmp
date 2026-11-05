package com.hearablemusic.player.ui.common.design.colors

import androidx.compose.ui.graphics.Color

object ColorTokens {
    // 品牌颜色
    val HDBlue = Color(0xFF002FA7)
    val HDRed = Color(0xFFC92C2C)

    // 列表页标签分区强调色
    val HDGreen = Color(0xFF4E6E45)
    val HDPurple = Color(0xFF6A0DAD)
    val HDOrange = Color(0xFFFFBF00)

    // 浅色主题颜色
    val LightPrimary = HDRed
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFF1976D2)
    val LightOnPrimaryContainer = Color(0xFFB00020)
    val LightSecondary = HDBlue
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightError = Color(0xFF1976D2)

    // 深色主题颜色
    val DarkPrimary = Color(0xFF90CAF9)
    val DarkOnPrimary = Color(0xFF000000)
    val DarkPrimaryContainer = Color(0xFFCF6679)
    val DarkOnPrimaryContainer = Color(0xFF1976D2)
    val DarkSecondary = Color(0xFFF48FB1)
    val DarkBackground = Color(0xFF121212)
    val DarkSurface = Color(0xFF121212)
    val DarkError = Color(0xFFCF6679)

    /** 动态背景用的浅色底（比 LightBackground 略灰，避免纯白刺眼）。 */
    val LightBackgroundAlt = Color(0xFFF5F5F5)

    /** 无封面/取色失败时的调色板兜底。 */
    object PaletteFallback {
        val primary = Color(0xFF1E90FF)
        val background = ColorTokens.DarkBackground
        val accent = Color(0xFF444444)
    }

    /**
     * 平台壳层色值：悬浮歌词窗与桌面标题栏叠加在共享 Theme 之外，不随主题色板切换，
     * 故集中在此；业务与壳层一律引用本组，禁止自写 hex。
     */
    object Shell {
        /** Android 悬浮歌词窗固定深色底（约 90% 不透明黑） */
        val OverlayBackground = Color(0xE61A1A1A)

        /** 桌面标题栏关闭按钮 hover */
        val CloseButtonHover = Color(0xFFC42B1C)

        /** 桌面标题栏关闭按钮 press */
        val CloseButtonPressed = Color(0xFFB22419)
    }
}
