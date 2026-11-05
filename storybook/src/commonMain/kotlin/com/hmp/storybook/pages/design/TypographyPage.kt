package com.hmp.storybook.pages.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun TypographyPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.typography(lang),
        description = Strings.typographyDescription(lang),
        onBack = onBack,
    ) {
        // 字体族信息
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "字体族" else "Font Family",
            description = if (lang == AppLanguage.ZH) {
                "HarmonyOS Sans — 华为 HarmonyOS 系统字体，6 种字重（Thin/Light/Regular/Medium/Bold/Black）"
            } else {
                "HarmonyOS Sans — Huawei HarmonyOS system font, 6 weights (Thin/Light/Regular/Medium/Bold/Black)"
            },
        ) {
            val weights = listOf(
                "Thin" to FontWeight.Thin,
                "Light" to FontWeight.Light,
                "Regular" to FontWeight.Normal,
                "Medium" to FontWeight.Medium,
                "Bold" to FontWeight.Bold,
                "Black" to FontWeight.Black,
            )
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                weights.forEach { (name, weight) ->
                    Text(
                        text = "HarmonyOS Sans $name — HMP 音乐播放器 AaBbCc 你好世界",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = weight,
                    )
                }
            }
        }

        // Material3 Typography 规范
        ComponentShowcase(
            title = "Material3 Typography",
            description = if (lang == AppLanguage.ZH) {
                "完整的 Material3 排版体系，sp → pt 1:1 映射"
            } else {
                "Complete Material3 type scale, sp → pt 1:1 mapping"
            },
        ) {
            val typeStyles = listOf(
                "displayLarge" to MaterialTheme.typography.displayLarge,
                "displayMedium" to MaterialTheme.typography.displayMedium,
                "displaySmall" to MaterialTheme.typography.displaySmall,
                "headlineLarge" to MaterialTheme.typography.headlineLarge,
                "headlineMedium" to MaterialTheme.typography.headlineMedium,
                "headlineSmall" to MaterialTheme.typography.headlineSmall,
                "titleLarge" to MaterialTheme.typography.titleLarge,
                "titleMedium" to MaterialTheme.typography.titleMedium,
                "titleSmall" to MaterialTheme.typography.titleSmall,
                "bodyLarge" to MaterialTheme.typography.bodyLarge,
                "bodyMedium" to MaterialTheme.typography.bodyMedium,
                "bodySmall" to MaterialTheme.typography.bodySmall,
                "labelLarge" to MaterialTheme.typography.labelLarge,
                "labelMedium" to MaterialTheme.typography.labelMedium,
                "labelSmall" to MaterialTheme.typography.labelSmall,
            )
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                typeStyles.forEach { (name, style) ->
                    Text(
                        text = "$name",
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
