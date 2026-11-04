package com.hearablemusic.player.ui.common.design.typography

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.hearablemusic.player.ui.common.layout.rememberAppWindowSizeInfo
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.harmonyos_sans_black
import com.hearablemusic.player.ui.generated.resources.harmonyos_sans_bold
import com.hearablemusic.player.ui.generated.resources.harmonyos_sans_light
import com.hearablemusic.player.ui.generated.resources.harmonyos_sans_medium
import com.hearablemusic.player.ui.generated.resources.harmonyos_sans_regular
import com.hearablemusic.player.ui.generated.resources.harmonyos_sans_thin
import org.jetbrains.compose.resources.Font

/**
 * 业务唯一排版入口（规范：docs/spec/hmp-design.md §3.2）。
 * 由 AppRoot provides；使用点 `val typo = LocalHmpTypo.current` 后取角色样式。
 * `MaterialTheme.typography.*` 是退场 API：业务禁止新增引用（存量按 D8-03 阶段 4A 清零），
 * 仅 M3 组件内部仍读它——由 [m3ComponentTypography] 喂一份内部投影。
 */
val LocalHmpTypo = staticCompositionLocalOf<HmpTypo> {
    error("HmpTypo not provided")
}

/**
 * 字阶组合层（规范 §3.2）。持有 FontFamily（CMP 资源 Font() 是 @Composable API，归本层）
 * 并把 TypeScale 的尺寸 / 默认字重 / 字距 / trim 组合成最终 TextStyle。
 */
object HMPTypoTokens {

    /**
     * HarmonyOS Sans 字重族。官方字重止于 6 档（Thin/Light/Regular/Medium/Bold/Black，无 600），
     * 进字阶角色的只有 Regular / Medium / Bold 三档（Thin / Light / Black 仅供特殊场景显式引用，
     * 禁止新增角色，规范 §3.1）。
     */
    val MyFontFamily: FontFamily
        @Composable get() = FontFamily(
            Font(Res.font.harmonyos_sans_black, FontWeight.Black),
            Font(Res.font.harmonyos_sans_bold, FontWeight.Bold),
            Font(Res.font.harmonyos_sans_medium, FontWeight.Medium),
            Font(Res.font.harmonyos_sans_regular, FontWeight.Normal),
            Font(Res.font.harmonyos_sans_light, FontWeight.Light),
            Font(Res.font.harmonyos_sans_thin, FontWeight.Thin),
        )

    /**
     * 解析当前窗口档的 9 角色样式。直接读平台 `rememberAppWindowSizeInfo()`——
     * 三个 MaterialTheme 装配点（AppRoot / HearableMusicPlayerTheme / IosComposePilot）的
     * `LocalWindowSizeInfo` provides 层次不同，只有平台 API 在所有位置可安全求值。
     */
    @Composable
    fun rememberHmpTypo(): HmpTypo {
        val info = rememberAppWindowSizeInfo()
        val family = MyFontFamily
        val sizes = typeSizesFor(info.widthSizeClass, info.heightSizeClass, info.isPhoneLandscape)
        return buildHmpTypo(family, sizes)
    }
}

/** 9 个尺寸角色的最终 TextStyle（按窗口档解析后）。变体用本文件的扩展函数叠加。 */
@Immutable
data class HmpTypo(
    val micro: TextStyle,
    val caption: TextStyle,
    val footnote: TextStyle,
    val body: TextStyle,
    val subtitle: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val display: TextStyle,
    val displayL: TextStyle,
)

internal fun buildHmpTypo(family: FontFamily, sizes: IntArray): HmpTypo = HmpTypo(
    micro = roleStyle(TypeRole.MICRO, family, sizes),
    caption = roleStyle(TypeRole.CAPTION, family, sizes),
    footnote = roleStyle(TypeRole.FOOTNOTE, family, sizes),
    body = roleStyle(TypeRole.BODY, family, sizes),
    subtitle = roleStyle(TypeRole.SUBTITLE, family, sizes),
    headline = roleStyle(TypeRole.HEADLINE, family, sizes),
    title = roleStyle(TypeRole.TITLE, family, sizes),
    display = roleStyle(TypeRole.DISPLAY, family, sizes),
    displayL = roleStyle(TypeRole.DISPLAY_L, family, sizes),
)

private fun roleStyle(role: TypeRole, family: FontFamily, sizes: IntArray): TextStyle {
    val size = sizeSp(role, sizes)
    return TextStyle(
        fontFamily = family,
        fontWeight = role.defaultWeight,
        fontSize = size.sp,
        lineHeight = lineHeightSp(role, size).sp,
        letterSpacing = role.letterSpacing,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, role.trim),
    )
}

// ── 显式变体（规范 §3.4：默认 + 显式覆盖；覆盖泛滥即角色切分错误的信号） ──

/** 同尺寸异字重：标签 / 按钮文字（Medium）。 */
fun TextStyle.medium(): TextStyle = copy(fontWeight = FontWeight.Medium)

/** 多行正文：关闭行高裁剪（首尾行距保留）。 */
fun TextStyle.multiline(): TextStyle = copy(
    lineHeightStyle = lineHeightStyle?.copy(trim = LineHeightStyle.Trim.None)
        ?: LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** 等宽数字：时间 / 时长 / 进度 / 统计数值（前提：字体含 tnum 特性，规范 §3.4）。 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FONT_FEATURE_SETTINGS)

// ── M3 组件内部投影（internal，非业务 API） ──

/**
 * 喂饱 M3 组件内部文字的投影（Button 的 label、TextField、Dialog 标题等读
 * `MaterialTheme.typography`）：零字重覆盖，槽位字重全随角色默认，就近映射。
 * **业务代码禁止引用此函数与 `MaterialTheme.typography.*`**——业务排版一律走
 * [LocalHmpTypo] 的 9 角色。存量 `TypographyTokens.kt` 在 D8-03 阶段 4A 清零后删除。
 */
internal fun m3ComponentTypography(typo: HmpTypo): Typography = Typography(
    displayLarge = typo.displayL,
    displayMedium = typo.display,
    displaySmall = typo.title,
    headlineLarge = typo.title,
    headlineMedium = typo.headline,
    headlineSmall = typo.subtitle,
    titleLarge = typo.headline,
    titleMedium = typo.subtitle,
    titleSmall = typo.body,
    bodyLarge = typo.body,
    bodyMedium = typo.body,
    bodySmall = typo.footnote,
    labelLarge = typo.body,
    labelMedium = typo.footnote,
    labelSmall = typo.caption,
)
