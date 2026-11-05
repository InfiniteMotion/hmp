@file:OptIn(ExperimentalMaterial3Api::class)

package com.hmp.storybook.pages.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.PhoneFramePreview
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun SettingsScreenPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.settingsScreen(lang),
        description = if (lang == AppLanguage.ZH) {
            "HMP 设置页面 (SettingItem: RoundedCornerShape(12.dp), surfaceVariant 0.5f, 图标 32.dp primary) + UserScreen (用户卡片 20.dp/1dp边框, Avatar 100, 收听图表, 2x2 功能网格)"
        } else {
            "HMP Settings (SettingItem: RoundedCornerShape(12.dp), surfaceVariant 0.5f, icon 32.dp primary) + UserScreen (user card 20.dp/1dp border, Avatar 100, listening chart, 2x2 grid)"
        },
        onBack = onBack,
    ) {
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "设置页预览" else "Settings Preview",
            description = if (lang == AppLanguage.ZH) {
                "SettingItem 卡片 (12.dp圆角, surfaceVariant 0.5f) + 图标 32.dp primary + 列表项间距 16.dp + 卡片内边距 16.dp"
            } else {
                "SettingItem card (12.dp radius, surfaceVariant 0.5f) + icon 32.dp primary + list spacing 16.dp + card padding 16.dp"
            },
        ) {
            PhoneFramePreview {
                SettingsScreenContent(lang)
            }
        }

        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "用户页预览" else "User Screen Preview",
            description = if (lang == AppLanguage.ZH) {
                "用户卡片 (RoundedCornerShape(20.dp), 1dp边框) + Avatar(aSize=100) + 收听图表 + 2x2 功能网格"
            } else {
                "User card (RoundedCornerShape(20.dp), 1dp border) + Avatar(aSize=100) + Listening chart + 2x2 feature grid"
            },
        ) {
            PhoneFramePreview {
                UserScreenContent(lang)
            }
        }
    }
}

@Composable
private fun SettingsScreenContent(lang: AppLanguage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部标题
        Text(
            text = if (lang == AppLanguage.ZH) "设置" else "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 外观设置: 深色模式/主题色/毛玻璃效果/背景风格
            SettingsGroup(
                title = if (lang == AppLanguage.ZH) "外观" else "Appearance",
                items = listOf(
                    Triple(
                        Icons.Filled.DarkMode,
                        if (lang == AppLanguage.ZH) "深色模式" else "Dark Mode",
                        null,
                    ),
                    Triple(
                        Icons.Filled.Palette,
                        if (lang == AppLanguage.ZH) "主题色" else "Theme Color",
                        null,
                    ),
                    Triple(
                        Icons.Filled.Brush,
                        if (lang == AppLanguage.ZH) "毛玻璃效果" else "Blur Effect",
                        null,
                    ),
                    Triple(
                        Icons.Filled.Wallpaper,
                        if (lang == AppLanguage.ZH) "背景风格" else "Background Style",
                        null,
                    ),
                ),
                lang = lang,
            )

            // 播放设置: 音频效果/均衡器
            SettingsGroup(
                title = if (lang == AppLanguage.ZH) "播放" else "Playback",
                items = listOf(
                    Triple(
                        Icons.Filled.GraphicEq,
                        if (lang == AppLanguage.ZH) "音频效果" else "Audio Effects",
                        null,
                    ),
                    Triple(
                        Icons.Filled.Equalizer,
                        if (lang == AppLanguage.ZH) "均衡器" else "Equalizer",
                        null,
                    ),
                ),
                lang = lang,
            )

            // AI 设置: AI推荐/服务商配置
            SettingsGroup(
                title = "AI",
                items = listOf(
                    Triple(
                        Icons.Filled.AutoAwesome,
                        if (lang == AppLanguage.ZH) "AI 推荐" else "AI Recommendation",
                        null,
                    ),
                    Triple(
                        Icons.Filled.SmartDisplay,
                        if (lang == AppLanguage.ZH) "服务商配置" else "Provider Config",
                        null,
                    ),
                ),
                lang = lang,
            )

            // 数据管理: 备份还原/音乐库设置
            SettingsGroup(
                title = if (lang == AppLanguage.ZH) "数据" else "Data",
                items = listOf(
                    Triple(
                        Icons.Filled.Backup,
                        if (lang == AppLanguage.ZH) "备份与还原" else "Backup & Restore",
                        null,
                    ),
                    Triple(
                        Icons.Filled.LibraryMusic,
                        if (lang == AppLanguage.ZH) "音乐库设置" else "Library Settings",
                        null,
                    ),
                ),
                lang = lang,
            )

            // 关于: 版本 v5.10
            SettingsGroup(
                title = if (lang == AppLanguage.ZH) "关于" else "About",
                items = listOf(
                    Triple(
                        Icons.Filled.Info,
                        if (lang == AppLanguage.ZH) "版本信息" else "Version Info",
                        "v5.10",
                    ),
                ),
                lang = lang,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    items: List<Triple<ImageVector, String, String?>>,
    lang: AppLanguage,
) {
    // SettingItem: RoundedCornerShape(12.dp) 卡片
    // 卡片颜色: surfaceVariant.copy(alpha = 0.5f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            items.forEachIndexed { index, (icon, label, trailing) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    // 图标: 32.dp, primary 颜色
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    // 卡片内边距 16.dp
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (trailing != null) {
                        Text(
                            text = trailing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserScreenContent(lang: AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 用户卡片: RoundedCornerShape(20.dp), 1dp 边框
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                // Avatar: aSize = 100
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HDBlue, Color(0xFF6C63FF)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "HMP User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lang == AppLanguage.ZH) "音乐爱好者" else "Music Lover",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 收听图表
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = if (lang == AppLanguage.ZH) "收听统计" else "Listening Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 简化的柱状图
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    val days = listOf(
                        if (lang == AppLanguage.ZH) "一" else "M",
                        if (lang == AppLanguage.ZH) "二" else "T",
                        if (lang == AppLanguage.ZH) "三" else "W",
                        if (lang == AppLanguage.ZH) "四" else "T",
                        if (lang == AppLanguage.ZH) "五" else "F",
                        if (lang == AppLanguage.ZH) "六" else "S",
                        if (lang == AppLanguage.ZH) "日" else "S",
                    )
                    val heights = listOf(60, 40, 80, 55, 90, 70, 45)
                    days.forEachIndexed { index, day ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(heights[index].dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (index == 4) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        },
                                    ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 2x2 功能网格: 主题定制/音效/AI服务/设置
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FeatureGridItem(
                    icon = Icons.Filled.Palette,
                    label = if (lang == AppLanguage.ZH) "主题定制" else "Theme",
                    modifier = Modifier.weight(1f),
                )
                FeatureGridItem(
                    icon = Icons.Filled.GraphicEq,
                    label = if (lang == AppLanguage.ZH) "音效" else "Audio FX",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FeatureGridItem(
                    icon = Icons.Filled.AutoAwesome,
                    label = if (lang == AppLanguage.ZH) "AI 服务" else "AI Service",
                    modifier = Modifier.weight(1f),
                )
                FeatureGridItem(
                    icon = Icons.Filled.Settings,
                    label = if (lang == AppLanguage.ZH) "设置" else "Settings",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FeatureGridItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 20.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
