package com.hmp.storybook.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import hmp_storybook.generated.resources.Res
import hmp_storybook.generated.resources.noto_sans_sc_regular
import org.jetbrains.compose.resources.Font

// HMP 品牌色
val HDBlue = Color(0xFF002FA7)
val HDRed = Color(0xFFC92C2C)

// 语言环境
val LocalAppLanguage = compositionLocalOf { AppLanguage.ZH }

enum class AppLanguage(val code: String, val displayName: String) {
    ZH("zh", "中文"),
    EN("en", "English"),
}

// 使用 NotoSansSC 中文字体（通过 Compose Resources 加载，支持 Skia 渲染）
@Composable
private fun appFontFamily(): FontFamily = FontFamily(Font(Res.font.noto_sans_sc_regular))

@Composable
fun StorybookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: AppLanguage = LocalAppLanguage.current,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val fontFamily = appFontFamily()

    CompositionLocalProvider(
        LocalAppLanguage provides language,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = storybookTypography(fontFamily),
            content = content,
        )
    }
}

// 自定义 Typography，与 HMP Android 端 TypographyTokens 保持一致
@Composable
private fun storybookTypography(fontFamily: FontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.25.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.25.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.25.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// 浅色主题 - 完整语义色，与 HMP Android ColorTokens 对齐
private val LightColorScheme = lightColorScheme(
    // 品牌色映射
    primary = HDRed,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1976D2),
    onPrimaryContainer = Color(0xFFB00020),
    secondary = HDBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3FF),
    onSecondaryContainer = Color(0xFF001B3D),
    tertiary = Color(0xFF6E5676),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7D8FE),
    onTertiaryContainer = Color(0xFF271430),
    error = Color(0xFF1976D2),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // 背景层级
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B1F),
    // Surface 变体层级 (HMP 侧边栏/卡片使用)
    surfaceDim = Color(0xFFE0E0E3),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainer = Color(0xFFEFEDF1),
    surfaceContainerHigh = Color(0xFFEAE7EC),
    surfaceContainerHighest = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    // 轮廓
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    // 反转
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF90CAF9),
)

// 深色主题 - 完整语义色，与 HMP Android ColorTokens 对齐
private val DarkColorScheme = darkColorScheme(
    // 品牌色映射
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFCF6679),
    onPrimaryContainer = Color(0xFF1976D2),
    secondary = Color(0xFFF48FB1),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDABCE2),
    onTertiary = Color(0xFF3D2945),
    tertiaryContainer = Color(0xFF55405D),
    onTertiaryContainer = Color(0xFFF7D8FE),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    // 背景层级
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE3E2E6),
    // Surface 变体层级
    surfaceDim = Color(0xFF121212),
    surfaceBright = Color(0xFF38383B),
    surfaceContainerLowest = Color(0xFF0D0D0F),
    surfaceContainerLow = Color(0xFF1A1A1E),
    surfaceContainer = Color(0xFF1E1E22),
    surfaceContainerHigh = Color(0xFF28282C),
    surfaceContainerHighest = Color(0xFF333337),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    // 轮廓
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    // 反转
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFF002FA7),
)
