package com.hearablemusic.player.ui.common.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.HMPTypoTokens
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.m3ComponentTypography

@Composable
fun HearableMusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = ThemeExtensionManager.getColorScheme(darkTheme)
    // 字阶 9 角色 + 统一尺寸刻度 12 档（均随窗口档缩放，共用同一档位判据）
    val typo = HMPTypoTokens.rememberHmpTypo()
    val metrics = HMPMetricTokens.rememberHmpMetrics()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = m3ComponentTypography(typo),
    ) {
        CompositionLocalProvider(
            LocalHmpTypo provides typo,
            LocalHmpMetrics provides metrics,
        ) {
            content()
        }
    }
}
