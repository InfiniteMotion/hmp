package com.hmp.storybook.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.pages.architecture.ArchitecturePage
import com.hmp.storybook.pages.components.AlbumCoverPage
import com.hmp.storybook.pages.components.AvatarPage
import com.hmp.storybook.pages.components.CapsuleButtonPage
import com.hmp.storybook.pages.components.DailyHeroCardPage
import com.hmp.storybook.pages.components.MiniPlayerBarPage
import com.hmp.storybook.pages.components.MusicRowPage
import com.hmp.storybook.pages.design.AnimationPage
import com.hmp.storybook.pages.design.ColorPalettePage
import com.hmp.storybook.pages.design.TypographyPage
import com.hmp.storybook.pages.product.DesignPhilosophyPage
import com.hmp.storybook.pages.product.ProductJourneyPage
import com.hmp.storybook.pages.screens.HomeScreenPage
import com.hmp.storybook.pages.screens.PlayerScreenPage
import com.hmp.storybook.pages.screens.PlaylistScreenPage
import com.hmp.storybook.pages.screens.SettingsScreenPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

/**
 * 导航状态，在顶层管理
 */
@Composable
fun rememberStorybookNavController(): StorybookNavController = remember { StorybookNavController() }

class StorybookNavController {
    var currentRoute by mutableStateOf<StorybookRoute>(StorybookRoute.Home)
        private set

    fun navigateTo(route: StorybookRoute) {
        currentRoute = route
    }

    fun navigateBack() {
        currentRoute = StorybookRoute.Home
    }
}

/**
 * 导航宿主，根据当前路由渲染对应页面
 */
@Composable
fun StorybookNavHost(
    navController: StorybookNavController,
) {
    when (navController.currentRoute) {
        StorybookRoute.Home -> HomePageContent(navController)
        StorybookRoute.ColorPalette -> ColorPalettePage(onBack = { navController.navigateBack() })
        StorybookRoute.Typography -> TypographyPage(onBack = { navController.navigateBack() })
        StorybookRoute.Animation -> AnimationPage(onBack = { navController.navigateBack() })
        StorybookRoute.MiniPlayerBar -> MiniPlayerBarPage(onBack = { navController.navigateBack() })
        StorybookRoute.MusicRow -> MusicRowPage(onBack = { navController.navigateBack() })
        StorybookRoute.DailyHeroCard -> DailyHeroCardPage(onBack = { navController.navigateBack() })
        StorybookRoute.AlbumCover -> AlbumCoverPage(onBack = { navController.navigateBack() })
        StorybookRoute.CapsuleButton -> CapsuleButtonPage(onBack = { navController.navigateBack() })
        StorybookRoute.Avatar -> AvatarPage(onBack = { navController.navigateBack() })
        StorybookRoute.HomeScreen -> HomeScreenPage(onBack = { navController.navigateBack() })
        StorybookRoute.PlayerScreen -> PlayerScreenPage(onBack = { navController.navigateBack() })
        StorybookRoute.PlaylistScreen -> PlaylistScreenPage(onBack = { navController.navigateBack() })
        StorybookRoute.SettingsScreen -> SettingsScreenPage(onBack = { navController.navigateBack() })
        StorybookRoute.Architecture -> ArchitecturePage(onBack = { navController.navigateBack() })
        StorybookRoute.ProductJourney -> ProductJourneyPage(onBack = { navController.navigateBack() })
        StorybookRoute.DesignPhilosophy -> DesignPhilosophyPage(onBack = { navController.navigateBack() })
    }
}

/**
 * 首页内容 - 产品概览 + 分类导航入口
 */
@Composable
private fun HomePageContent(navController: StorybookNavController) {
    val lang = LocalAppLanguage.current
    val isZh = lang == AppLanguage.ZH

    Column(modifier = Modifier.fillMaxWidth()) {
        // ========== Hero 区域 ==========
        Text(
            text = "HMP",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = HDBlue,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Strings.subtitle(lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isZh) {
                "Kotlin/Wasm + Compose Multiplatform 驱动的交互式产品技术文档"
            } else {
                "Interactive product tech docs powered by Kotlin/Wasm + Compose Multiplatform"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 核心数据卡片 (4 列) ==========
        val coreData = if (isZh) {
            listOf("版本" to "v5.10", "功能" to "20+", "平台" to "Android + iOS", "迭代" to "13 个月")
        } else {
            listOf("Version" to "v5.10", "Features" to "20+", "Platforms" to "Android + iOS", "Iteration" to "13 months")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            coreData.forEach { (label, value) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = HDBlue.copy(alpha = 0.08f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HDBlue)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 产品定位 ==========
        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = Strings.productPositioning(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isZh) {
                        "一款现代化的跨平台本地音乐播放器，基于 Kotlin Multiplatform 构建，Android 端使用 Jetpack Compose，iOS 端使用 SwiftUI。坚持纯本地路线，不做云同步、不引入账号、不做社交，仅保留用户自填 API 的 AI 推荐。"
                    } else {
                        "A modern cross-platform local music player built with Kotlin Multiplatform. Android uses Jetpack Compose, iOS uses SwiftUI. Committed to local-first: no cloud sync, no accounts, no social features \u2014 only user-configured AI recommendations."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== 产品边界 ==========
        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = Strings.productBoundary(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                val boundaries = if (isZh) {
                    listOf(Icons.Filled.CloudOff to "不做在线/云同步", Icons.Filled.PersonOff to "不引入账号系统", Icons.Filled.ChatBubbleOutline to "不做社交功能", Icons.Filled.VisibilityOff to "不收集用户数据")
                } else {
                    listOf(Icons.Filled.CloudOff to "No cloud sync", Icons.Filled.PersonOff to "No account system", Icons.Filled.ChatBubbleOutline to "No social features", Icons.Filled.VisibilityOff to "No user data collection")
                }

                boundaries.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.width(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(HDBlue.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(imageVector = Icons.Filled.SmartToy, contentDescription = null, tint = HDBlue, modifier = Modifier.width(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isZh) "例外：用户自填 API Key 的 AI 推荐" else "Exception: User-configured AI recommendations with own API key",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HDBlue,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ========== 产品概览导航 ==========
        SectionTitle(if (isZh) "产品概览" else "Product Overview")
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickNavCard(
                icon = Icons.Filled.Explore,
                title = Strings.productJourney(lang),
                desc = if (isZh) "架构演进 · 重大决策 · KMP 迁移" else "Architecture · Decisions · KMP migration",
                color = HDBlue,
                onClick = { navController.navigateTo(StorybookRoute.ProductJourney) },
                modifier = Modifier.weight(1f),
            )
            QuickNavCard(
                icon = Icons.Filled.Lightbulb,
                title = Strings.designPhilosophy(lang),
                desc = if (isZh) "本地优先 · 原生 UI · 动态主题" else "Local-first · Native UI · Dynamic theme",
                color = Color(0xFFFF9800),
                onClick = { navController.navigateTo(StorybookRoute.DesignPhilosophy) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ========== 技术文档导航 ==========
        SectionTitle(if (isZh) "技术文档" else "Technical Docs")
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickNavCard(
                icon = Icons.Filled.Palette,
                title = Strings.designSystem(lang),
                desc = if (isZh) "色彩 · 排版 · 动效" else "Colors · Typography · Animation",
                count = "3",
                color = Color(0xFF4CAF50),
                onClick = { navController.navigateTo(StorybookRoute.ColorPalette) },
                modifier = Modifier.weight(1f),
            )
            QuickNavCard(
                icon = Icons.Filled.Widgets,
                title = Strings.components(lang),
                desc = if (isZh) "播放条 · 歌曲行 · 卡片 · 按钮" else "Player · Row · Card · Button",
                count = "6",
                color = Color(0xFF2196F3),
                onClick = { navController.navigateTo(StorybookRoute.MiniPlayerBar) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickNavCard(
                icon = Icons.Filled.PhoneAndroid,
                title = Strings.screens(lang),
                desc = if (isZh) "首页 · 播放 · 歌单 · 设置" else "Home · Player · Playlist · Settings",
                count = "4",
                color = Color(0xFF9C27B0),
                onClick = { navController.navigateTo(StorybookRoute.HomeScreen) },
                modifier = Modifier.weight(1f),
            )
            QuickNavCard(
                icon = Icons.Filled.AccountTree,
                title = Strings.architecture(lang),
                desc = if (isZh) "5 层架构 · 模块 · 技术栈" else "5-layer · Modules · Tech stack",
                count = "1",
                color = Color(0xFFFF9800),
                onClick = { navController.navigateTo(StorybookRoute.Architecture) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ========== 技术栈标签 ==========
        SectionTitle(if (isZh) "技术栈" else "Tech Stack")
        Spacer(modifier = Modifier.height(12.dp))

        val techTags = listOf(
            "Kotlin 2.2.21", "Compose Multiplatform 1.8.2", "Kotlin/Wasm",
            "Material Design 3", "HarmonyOS Sans", "MVVM", "Koin",
            "Room KMP", "Ktor", "Navigation 3", "Haze", "Media3", "Coil",
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            techTags.forEach { tag ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun QuickNavCard(
    icon: ImageVector,
    title: String,
    desc: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    Card(
        modifier = modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (count != null) {
                    Text(text = count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
