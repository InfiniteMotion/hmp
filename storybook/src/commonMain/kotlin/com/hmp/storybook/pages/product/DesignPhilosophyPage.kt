package com.hmp.storybook.pages.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.HDRed
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun DesignPhilosophyPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.designPhilosophy(lang),
        description = Strings.designPhilosophyDescription(lang),
        onBack = onBack,
    ) {
        // 区块1: 纯本地优先
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "纯本地优先" else "Local-First",
            description = if (lang == AppLanguage.ZH) {
                "坚持纯本地路线，不做云同步、不引入账号、不做社交，尊重用户隐私"
            } else {
                "Committed to local-first: no cloud sync, no accounts, no social features, respecting user privacy"
            },
        ) {
            LocalFirstSection(lang)
        }

        // 区块2: 平台原生 UI
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "平台原生 UI" else "Platform-Native UI",
            description = if (lang == AppLanguage.ZH) {
                "共享核心逻辑，UI 保持平台原生，各取所长"
            } else {
                "Share core logic, keep UI platform-native, leverage each platform's strengths"
            },
        ) {
            NativeUISection(lang)
        }

        // 区块3: 渐进式演进
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "渐进式演进" else "Progressive Evolution",
            description = if (lang == AppLanguage.ZH) {
                "每次变迁都是在前一步基础上渐进演进，而非推倒重来"
            } else {
                "Each evolution builds incrementally on the previous step, never a complete rewrite"
            },
        ) {
            ProgressiveEvolutionSection(lang)
        }

        // 区块4: 先设计后编码
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "先设计后编码" else "Design Before Code",
            description = if (lang == AppLanguage.ZH) {
                "v5.10 重构方法论：先出设计文档，分阶段逐步执行"
            } else {
                "v5.10 refactoring methodology: design docs first, phased execution"
            },
        ) {
            DesignFirstSection(lang)
        }

        // 区块5: AI 推荐策略
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "AI 推荐策略" else "AI Recommendation Strategy",
            description = if (lang == AppLanguage.ZH) {
                "唯一网络功能：用户自填 API Key 的多服务商 AI 推荐"
            } else {
                "The only network feature: multi-provider AI recommendations with user-supplied API keys"
            },
        ) {
            AIRecommendationSection(lang)
        }

        // 区块6: 动态主题系统
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "动态主题系统" else "Dynamic Theme System",
            description = if (lang == AppLanguage.ZH) {
                "从专辑封面提取颜色，播放时动态切换，三种背景风格 + 毛玻璃效果"
            } else {
                "Extract colors from album covers, dynamic switching during playback, 3 background styles + blur effects"
            },
        ) {
            DynamicThemeSection(lang)
        }

        // 区块7: 视觉语言
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "视觉语言" else "Visual Language",
            description = if (lang == AppLanguage.ZH) {
                "品牌色、字体、动效、圆角体系构成完整的视觉识别系统"
            } else {
                "Brand colors, typography, motion, and corner radius form a complete visual identity system"
            },
        ) {
            VisualLanguageSection(lang)
        }
    }
}

// ========== 区块1: 纯本地优先 ==========

@Composable
private fun LocalFirstSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 核心理念
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = HDBlue.copy(alpha = 0.06f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = HDBlue,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (lang == AppLanguage.ZH) {
                        "坚持纯本地路线：不做云同步、不引入账号、不做社交"
                    } else {
                        "Committed to local-first: no cloud sync, no accounts, no social features"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 4 个"不做"卡片
        val donts = if (lang == AppLanguage.ZH) {
            listOf(
                Triple(Icons.Filled.CloudOff, "不做云同步", "所有数据存储在用户设备本地，无需网络即可使用全部功能"),
                Triple(Icons.Filled.PersonOff, "不引入账号系统", "无需注册登录，打开即用，无账号泄露风险"),
                Triple(Icons.Filled.Close, "不做社交功能", "不添加好友、不分享动态、不做社区，专注音乐播放本身"),
                Triple(Icons.Filled.VisibilityOff, "不收集用户数据", "无埋点、无分析、无遥测，用户行为完全私密"),
            )
        } else {
            listOf(
                Triple(Icons.Filled.CloudOff, "No Cloud Sync", "All data stored locally on device; full functionality without network"),
                Triple(Icons.Filled.PersonOff, "No Account System", "No registration or login required; open and play immediately"),
                Triple(Icons.Filled.Close, "No Social Features", "No friends, no sharing, no community; focus on music playback"),
                Triple(Icons.Filled.VisibilityOff, "No Data Collection", "No analytics, no telemetry, no tracking; user behavior stays private"),
            )
        }

        donts.forEach { (icon, title, desc) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 设计原因
        val reasons = if (lang == AppLanguage.ZH) {
            listOf(
                "尊重用户隐私 -- 零数据收集，用户行为完全私密",
                "零依赖外部服务 -- 无需网络即可使用全部功能",
                "离线可用 -- 地铁、飞行模式等场景不受影响",
                "数据完全由用户掌控 -- 备份还原均在本地完成",
            )
        } else {
            listOf(
                "Respect user privacy -- Zero data collection, completely private",
                "Zero dependency on external services -- Full functionality offline",
                "Always available -- Works in subway, airplane mode, etc.",
                "User controls all data -- Backup and restore done locally",
            )
        }

        Text(
            text = if (lang == AppLanguage.ZH) "设计原因" else "Design Rationale",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        reasons.forEach { reason ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.width(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ========== 区块2: 平台原生 UI ==========

@Composable
private fun NativeUISection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 理念
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = HDBlue.copy(alpha = 0.06f),
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) {
                        "共享核心逻辑 (domain + data)，UI 保持平台原生实现"
                    } else {
                        "Share core logic (domain + data), keep UI platform-native"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Android
        PlatformCard(
            icon = Icons.Filled.PhoneAndroid,
            platform = "Android",
            color = Color(0xFF3DDC84),
            items = if (lang == AppLanguage.ZH) {
                listOf("Jetpack Compose", "Material Design 3", "Haze 毛玻璃效果", "Navigation 3")
            } else {
                listOf("Jetpack Compose", "Material Design 3", "Haze blur effects", "Navigation 3")
            },
        )

        // iOS
        PlatformCard(
            icon = Icons.Filled.PhoneIphone,
            platform = "iOS",
            color = Color(0xFF007AFF),
            items = if (lang == AppLanguage.ZH) {
                listOf("SwiftUI", "Liquid Glass 液态玻璃", "NavigationStack", "AVFoundation")
            } else {
                listOf("SwiftUI", "Liquid Glass", "NavigationStack", "AVFoundation")
            },
        )

        // 对比表格: iOS 原生更优
        Text(
            text = if (lang == AppLanguage.ZH) "iOS 原生优势" else "iOS Native Advantages",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        val nativeAdvantages = if (lang == AppLanguage.ZH) {
            listOf(
                "SegmentedControl -- 94% 代码节省",
                "索引条 -- 94% 代码节省",
                "搜索 -- 开箱即用",
                "下滑关闭 -- 原生支持",
                "毛玻璃 -- 系统级 GPU 加速",
            )
        } else {
            listOf(
                "SegmentedControl -- 94% code savings",
                "Index bar -- 94% code savings",
                "Search -- Out of the box",
                "Swipe to dismiss -- Native support",
                "Blur -- System-level GPU acceleration",
            )
        }

        nativeAdvantages.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.width(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 需要自定义的组件
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (lang == AppLanguage.ZH) "需要自定义的组件" else "Components Requiring Custom Implementation",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        val customComponents = if (lang == AppLanguage.ZH) {
            listOf("ColorTokens", "DynamicBackground", "MiniPlayerBar", "歌词滚动")
        } else {
            listOf("ColorTokens", "DynamicBackground", "MiniPlayerBar", "Lyrics scrolling")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            customComponents.forEach { component ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HDRed.copy(alpha = 0.08f),
                    ),
                ) {
                    Text(
                        text = component,
                        style = MaterialTheme.typography.labelMedium,
                        color = HDRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformCard(
    icon: ImageVector,
    platform: String,
    color: Color,
    items: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.06f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = platform,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            items.forEach { item ->
                Text(
                    text = "  $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ========== 区块3: 渐进式演进 ==========

@Composable
private fun ProgressiveEvolutionSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val phases = if (lang == AppLanguage.ZH) {
            listOf(
                Triple(
                    "单体架构",
                    "v1.0 - v4.0",
                    "所有代码集中在一个模块，从零搭建基础播放功能",
                ),
                Triple(
                    "模块化架构",
                    "v5.0 - v5.9",
                    "拆分为 5 个模块，引入 Hilt + Media3，但不改变整体结构",
                ),
                Triple(
                    "跨平台架构",
                    "v5.10+",
                    "在模块化基础上提取共享层，新增 iOS 端，不改变 Android 端",
                ),
            )
        } else {
            listOf(
                Triple(
                    "Monolith",
                    "v1.0 - v4.0",
                    "All code in one module, built basic playback from scratch",
                ),
                Triple(
                    "Modular",
                    "v5.0 - v5.9",
                    "Split into 5 modules, introduced Hilt + Media3, without changing overall structure",
                ),
                Triple(
                    "Cross-Platform",
                    "v5.10+",
                    "Extracted shared layer from modular base, added iOS, unchanged Android",
                ),
            )
        }

        val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), HDBlue)

        phases.forEachIndexed { index, (title, version, desc) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors[index].copy(alpha = 0.06f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors[index],
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = version,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 箭头连接（最后一个不加）
            if (index < phases.size - 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ========== 区块4: 先设计后编码 ==========

@Composable
private fun DesignFirstSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 方法论
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = HDBlue.copy(alpha = 0.06f),
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = HDBlue,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (lang == AppLanguage.ZH) "v5.10 重构方法论" else "v5.10 Refactoring Methodology",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 步骤
        val steps = if (lang == AppLanguage.ZH) {
            listOf(
                Triple(Icons.Filled.DesignServices, "先出设计文档", "1650 行设计文档和实施计划，覆盖架构、模块、迁移路径"),
                Triple(Icons.Filled.Speed, "分阶段执行", "分 7 阶段逐步执行 (P0-P6)，每阶段编译验证"),
                Triple(Icons.Filled.CheckCircle, "先排查再决策", "Room KMP vs SQLDelight：排查后发现问题根因是 Gradle 配置，修复成本远低于迁移"),
            )
        } else {
            listOf(
                Triple(Icons.Filled.DesignServices, "Design docs first", "1650 lines of design docs and implementation plan covering architecture, modules, migration path"),
                Triple(Icons.Filled.Speed, "Phased execution", "7 phases (P0-P6), compile verification after each phase"),
                Triple(Icons.Filled.CheckCircle, "Investigate before deciding", "Room KMP vs SQLDelight: investigation revealed root cause was Gradle config; fix cost far lower than migration"),
            )
        }

        steps.forEach { (icon, title, desc) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = HDBlue,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ========== 区块5: AI 推荐策略 ==========

@Composable
private fun AIRecommendationSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 为什么保留 AI 推荐
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = HDBlue.copy(alpha = 0.06f),
            ),
        ) {
            Row(modifier = Modifier.padding(14.dp)) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = HDBlue,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (lang == AppLanguage.ZH) {
                        "AI 推荐是 HMP 唯一的网络功能，帮助用户发现新音乐"
                    } else {
                        "AI recommendation is HMP's only network feature, helping users discover new music"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 多服务商支持
        val providers = if (lang == AppLanguage.ZH) {
            "支持 5 家 AI 服务商：DeepSeek / OpenAI / Claude / 通义千问 / 文心一言"
        } else {
            "Supports 5 AI providers: DeepSeek / OpenAI / Claude / Qwen / ERNIE Bot"
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "多服务商支持" else "Multi-Provider Support",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = providers,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 用户选择权
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "用户选择权" else "User Control",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val userControlItems = if (lang == AppLanguage.ZH) {
                    listOf("自由切换 AI 服务商", "API 密钥加密存储 (SecureStorageHelper)")
                } else {
                    listOf("Freely switch between AI providers", "API keys encrypted (SecureStorageHelper)")
                }
                userControlItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.width(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 刷新策略
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "每日推荐刷新策略" else "Daily Recommendation Refresh Strategy",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val refreshStrategies = if (lang == AppLanguage.ZH) {
                    listOf("按时间刷新 -- 每日固定时间更新", "按启动次数 -- 每 N 次启动刷新", "智能刷新 -- 基于播放习惯动态调整")
                } else {
                    listOf("Time-based -- Refresh at a fixed time daily", "Launch-based -- Refresh every N launches", "Smart refresh -- Dynamically adjust based on listening habits")
                }
                refreshStrategies.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.width(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 智能播放列表生成
        Text(
            text = if (lang == AppLanguage.ZH) "智能播放列表生成" else "Smart Playlist Generation",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (lang == AppLanguage.ZH) {
                "基于用户音乐库和收听历史，通过 AI 生成个性化播放列表推荐"
            } else {
                "Generate personalized playlist recommendations based on user's music library and listening history via AI"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ========== 区块6: 动态主题系统 ==========

@Composable
private fun DynamicThemeSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Palette 颜色提取
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "Palette 颜色提取" else "Palette Color Extraction",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lang == AppLanguage.ZH) {
                        "从专辑封面提取 8 种颜色，构建完整的动态色彩方案"
                    } else {
                        "Extract 8 colors from album cover to build a complete dynamic color scheme"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 颜色预览条
                val paletteColors = listOf(
                    Color(0xFF1A237E),
                    Color(0xFF283593),
                    Color(0xFF3949AB),
                    Color(0xFF5C6BC0),
                    Color(0xFF7986CB),
                    Color(0xFF9FA8DA),
                    Color(0xFFC5CAE9),
                    Color(0xFFE8EAF6),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    paletteColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color),
                        )
                    }
                }
            }
        }

        // 动态切换
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "动态切换" else "Dynamic Switching",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val switchItems = if (lang == AppLanguage.ZH) {
                    listOf("播放时 -- 从专辑封面提取颜色，动态切换主题", "暂停时 -- 恢复到预置主题")
                } else {
                    listOf("Playing -- Extract colors from album cover, dynamically switch theme", "Paused -- Restore to preset theme")
                }
                switchItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.width(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 三种背景风格
        Text(
            text = if (lang == AppLanguage.ZH) "三种动态背景风格" else "Three Dynamic Background Styles",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        val bgStyles = if (lang == AppLanguage.ZH) {
            listOf(
                Triple("FLUID", "流体极光", Color(0xFF4CAF50)),
                Triple("SPOTS", "沉浸光斑", Color(0xFF2196F3)),
                Triple("BLUR", "复古模糊", Color(0xFFFF9800)),
            )
        } else {
            listOf(
                Triple("FLUID", "Fluid Aurora", Color(0xFF4CAF50)),
                Triple("SPOTS", "Immersive Spots", Color(0xFF2196F3)),
                Triple("BLUR", "Retro Blur", Color(0xFFFF9800)),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bgStyles.forEach { (name, desc, color) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = color.copy(alpha = 0.08f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 毛玻璃效果
        Text(
            text = if (lang == AppLanguage.ZH) "毛玻璃效果" else "Blur Effects",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )

        val blurItems = if (lang == AppLanguage.ZH) {
            listOf(
                "Android: Haze 库，5 档预设模糊强度",
                "iOS: Liquid Glass 液态玻璃，系统级 GPU 加速",
            )
        } else {
            listOf(
                "Android: Haze library, 5 preset blur intensity levels",
                "iOS: Liquid Glass, system-level GPU acceleration",
            )
        }

        blurItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Brush,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.width(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ========== 区块7: 视觉语言 ==========

@Composable
private fun VisualLanguageSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 品牌色
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "品牌色" else "Brand Colors",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // HDBlue
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HDBlue),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "HDBlue",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Klein Blue",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "#002FA7",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // HDRed
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HDRed),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "HDRed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "HD Red",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "#C92C2C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 字体
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = HDBlue,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == AppLanguage.ZH) "字体：HarmonyOS Sans" else "Font: HarmonyOS Sans",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (lang == AppLanguage.ZH) {
                        "6 种字重：Thin / Light / Regular / Medium / Bold / Black"
                    } else {
                        "6 weights: Thin / Light / Regular / Medium / Bold / Black"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 动效
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "动效体系" else "Motion System",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val motionItems = if (lang == AppLanguage.ZH) {
                    listOf(
                        "4 档持续时间 -- MICRO 200ms / SHORT 300ms / MEDIUM 500ms / BACKGROUND 3000ms",
                        "3 组缓动函数 -- EASE_IN_OUT / EASE_OUT / EASE_IN",
                        "3 种弹簧配置 -- Bouncy / Gentle / Stiff",
                    )
                } else {
                    listOf(
                        "4 duration tiers -- MICRO 200ms / SHORT 300ms / MEDIUM 500ms / BACKGROUND 3000ms",
                        "3 easing curves -- EASE_IN_OUT / EASE_OUT / EASE_IN",
                        "3 spring configs -- Bouncy / Gentle / Stiff",
                    )
                }
                motionItems.forEach { item ->
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        // 圆角体系
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (lang == AppLanguage.ZH) "圆角体系" else "Corner Radius System",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))

                val corners = if (lang == AppLanguage.ZH) {
                    listOf(
                        "28dp" to "大卡片 (DailyHeroCard)",
                        "20dp" to "用户卡片 (UserCard)",
                        "16dp" to "设置项 (SettingItem)",
                        "12dp" to "列表行 (MusicRow)",
                        "8dp" to "小元素 (标签/图标)",
                    )
                } else {
                    listOf(
                        "28dp" to "Large cards (DailyHeroCard)",
                        "20dp" to "User cards (UserCard)",
                        "16dp" to "Settings items (SettingItem)",
                        "12dp" to "List rows (MusicRow)",
                        "8dp" to "Small elements (tags/icons)",
                    )
                }

                val cornerColors = listOf(HDBlue, Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF9800))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    corners.forEachIndexed { index, (size, usage) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(size.removeSuffix("dp").toInt().dp))
                                    .background(cornerColors[index].copy(alpha = 0.2f)),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = size,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cornerColors[index],
                            )
                            Text(
                                text = usage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
