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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Speed
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

private data class KmpPhase(val id: String, val title: String, val items: List<String>)

@Composable
fun ProductJourneyPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.productJourney(lang),
        description = Strings.productJourneyDescription(lang),
        onBack = onBack,
    ) {
        // 区块1: 架构演进
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "架构演进" else "Architecture Evolution",
            description = if (lang == AppLanguage.ZH) {
                "从单体到模块化再到跨平台，三次架构变迁的时间线"
            } else {
                "Timeline of three architectural shifts: monolith -> modular -> cross-platform"
            },
        ) {
            ArchitectureEvolutionSection(lang)
        }

        // 区块2: 重大技术决策
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "重大技术决策" else "Major Technical Decisions",
            description = if (lang == AppLanguage.ZH) {
                "6 个关键决策及其背后的原因"
            } else {
                "6 key decisions and the reasoning behind them"
            },
        ) {
            MajorDecisionsSection(lang)
        }

        // 区块3: KMP 跨平台迁移
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "KMP 跨平台迁移" else "KMP Cross-Platform Migration",
            description = if (lang == AppLanguage.ZH) {
                "7 个阶段的实施过程，从 Monorepo 骨架到 iOS 端基础"
            } else {
                "7-phase implementation from Monorepo scaffold to iOS foundation"
            },
        ) {
            KmpMigrationSection(lang)
        }

        // 区块4: 关键数字
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "关键数字" else "Key Numbers",
            description = if (lang == AppLanguage.ZH) {
                "v5.10 KMP 跨平台重构的量化成果"
            } else {
                "Quantified results of the v5.10 KMP cross-platform refactoring"
            },
        ) {
            KeyNumbersSection(lang)
        }

        // 区块5: 未来规划
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "未来规划" else "Future Roadmap",
            description = if (lang == AppLanguage.ZH) {
                "短期、中期、长期三阶段规划"
            } else {
                "Short-term, mid-term, and long-term roadmap"
            },
        ) {
            FutureRoadmapSection(lang)
        }
    }
}

// ========== 区块1: 架构演进 ==========

@Composable
private fun ArchitectureEvolutionSection(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 阶段一
        EvolutionPhaseCard(
            phase = if (lang == AppLanguage.ZH) "阶段一" else "Phase 1",
            title = if (lang == AppLanguage.ZH) "单体架构" else "Monolith Architecture",
            version = "v1.0 - v4.0",
            period = "2025.03 - 2025.05",
            color = Color(0xFF4CAF50),
            icon = Icons.Filled.SdStorage,
            items = if (lang == AppLanguage.ZH) {
                listOf(
                    "所有代码集中在 app 模块",
                    "基础播放控制和播放列表功能",
                    "从零到第一个可运行版本",
                )
            } else {
                listOf(
                    "All code in a single app module",
                    "Basic playback control and playlist features",
                    "From zero to the first runnable version",
                )
            },
        )

        // 箭头连接
        ArrowConnector()

        // 阶段二
        EvolutionPhaseCard(
            phase = if (lang == AppLanguage.ZH) "阶段二" else "Phase 2",
            title = if (lang == AppLanguage.ZH) "模块化架构" else "Modular Architecture",
            version = "v5.0 - v5.9",
            period = "2025.11 - 2026.04",
            color = Color(0xFF2196F3),
            icon = Icons.Filled.DeviceHub,
            items = if (lang == AppLanguage.ZH) {
                listOf(
                    "拆分为 5 个模块 (app, core-data, core-domain, core-player, feature-ui)",
                    "引入 Hilt 依赖注入 + Media3",
                    "AI 多服务商支持、动态主题、毛玻璃效果",
                    "Navigation 3 迁移、Gradle 9.0 升级",
                    "ViewModel 职责拆分重构",
                )
            } else {
                listOf(
                    "Split into 5 modules (app, core-data, core-domain, core-player, feature-ui)",
                    "Introduced Hilt DI + Media3",
                    "Multi-provider AI, dynamic themes, blur effects",
                    "Navigation 3 migration, Gradle 9.0 upgrade",
                    "ViewModel responsibility refactoring",
                )
            },
        )

        // 箭头连接
        ArrowConnector()

        // 阶段三
        EvolutionPhaseCard(
            phase = if (lang == AppLanguage.ZH) "阶段三" else "Phase 3",
            title = if (lang == AppLanguage.ZH) "跨平台架构" else "Cross-Platform Architecture",
            version = "v5.10+",
            period = "2026.05 -",
            color = HDBlue,
            icon = Icons.Filled.PhoneIphone,
            items = if (lang == AppLanguage.ZH) {
                listOf(
                    "KMP Monorepo，共享 domain + data 层",
                    "Room KMP + Ktor + Koin 全面替换",
                    "8 组 expect/actual 平台抽象",
                    "iOS SwiftUI + AVFoundation 从零搭建",
                    "53 个 Swift 文件",
                )
            } else {
                listOf(
                    "KMP Monorepo, shared domain + data layers",
                    "Room KMP + Ktor + Koin full replacement",
                    "8 groups of expect/actual platform abstractions",
                    "iOS SwiftUI + AVFoundation built from scratch",
                    "53 Swift files",
                )
            },
        )
    }
}

@Composable
private fun EvolutionPhaseCard(
    phase: String,
    title: String,
    version: String,
    period: String,
    color: Color,
    icon: ImageVector,
    items: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.06f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = phase,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row {
                        Text(
                            text = version,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = period,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                            .align(Alignment.CenterVertically),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrowConnector() {
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

// ========== 区块2: 重大技术决策 ==========

@Composable
private fun MajorDecisionsSection(lang: AppLanguage) {
    val decisions = if (lang == AppLanguage.ZH) {
        listOf(
            MajorDecision(
                title = "v5.0 模块化重构",
                time = "2025.11",
                decision = "从单体拆分为 5 模块，引入 Hilt + Media3",
                reason = "代码量增长导致维护困难，需要清晰的模块边界和职责分离",
                color = Color(0xFF4CAF50),
            ),
            MajorDecision(
                title = "v5.3 多 AI 服务商",
                time = "2025.12",
                decision = "支持 5 家 AI 服务商而非绑定单一提供商",
                reason = "避免供应商锁定，给用户更多选择，降低单点故障风险",
                color = Color(0xFF2196F3),
            ),
            MajorDecision(
                title = "v5.8 Navigation 3",
                time = "2026.04",
                decision = "从字符串路由迁移到类型安全导航",
                reason = "编译时类型检查，消除路由拼写错误，支持深度链接",
                color = Color(0xFF9C27B0),
            ),
            MajorDecision(
                title = "v5.10 KMP 跨平台",
                time = "2026.05",
                decision = "共享业务逻辑，UI 保持平台原生",
                reason = "业务逻辑复用减少重复代码，原生 UI 保证各平台最佳体验",
                color = HDBlue,
            ),
            MajorDecision(
                title = "Room KMP vs SQLDelight",
                time = "2026.05",
                decision = "排查后选择修复 Room 而非迁移 SQLDelight",
                reason = "Room KMP 问题根因是 Gradle 配置，修复成本远低于全量迁移",
                color = Color(0xFFFF9800),
            ),
            MajorDecision(
                title = "平台原生 UI",
                time = "2026.05",
                decision = "不用 Compose Multiplatform UI，而是各平台原生 UI",
                reason = "SegmentedControl 94% 代码节省、毛玻璃系统级 GPU 加速等原生优势",
                color = HDRed,
            ),
        )
    } else {
        listOf(
            MajorDecision(
                title = "v5.0 Modular Refactoring",
                time = "2025.11",
                decision = "Split monolith into 5 modules, introduced Hilt + Media3",
                reason = "Growing codebase made maintenance difficult; needed clear module boundaries and separation of concerns",
                color = Color(0xFF4CAF50),
            ),
            MajorDecision(
                title = "v5.3 Multi-AI Providers",
                time = "2025.12",
                decision = "Support 5 AI providers instead of binding to a single one",
                reason = "Avoid vendor lock-in, give users more choices, reduce single point of failure",
                color = Color(0xFF2196F3),
            ),
            MajorDecision(
                title = "v5.8 Navigation 3",
                time = "2026.04",
                decision = "Migrate from string-based routes to type-safe navigation",
                reason = "Compile-time type checking, eliminate route typos, support deep linking",
                color = Color(0xFF9C27B0),
            ),
            MajorDecision(
                title = "v5.10 KMP Cross-Platform",
                time = "2026.05",
                decision = "Share business logic, keep UI platform-native",
                reason = "Business logic reuse reduces duplication; native UI ensures best experience per platform",
                color = HDBlue,
            ),
            MajorDecision(
                title = "Room KMP vs SQLDelight",
                time = "2026.05",
                decision = "Chose to fix Room instead of migrating to SQLDelight",
                reason = "Room KMP issue root cause was Gradle config; fix cost far lower than full migration",
                color = Color(0xFFFF9800),
            ),
            MajorDecision(
                title = "Platform-Native UI",
                time = "2026.05",
                decision = "No Compose Multiplatform UI; use native UI for each platform",
                reason = "SegmentedControl 94% code savings, system-level GPU-accelerated blur, etc.",
                color = HDRed,
            ),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        decisions.forEach { decision ->
            DecisionCard(decision, lang)
        }
    }
}

private data class MajorDecision(
    val title: String,
    val time: String,
    val decision: String,
    val reason: String,
    val color: Color,
)

@Composable
private fun DecisionCard(decision: MajorDecision, lang: AppLanguage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(decision.color),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = decision.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = decision.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (lang == AppLanguage.ZH) "决策：${decision.decision}" else "Decision: ${decision.decision}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lang == AppLanguage.ZH) "原因：${decision.reason}" else "Reason: ${decision.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ========== 区块3: KMP 跨平台迁移 ==========

@Composable
private fun KmpMigrationSection(lang: AppLanguage) {
    val phases = if (lang == AppLanguage.ZH) {
        listOf(
            KmpPhase("P0", "Monorepo 骨架搭建", listOf("创建 shared/android/ios/storybook 四大模块", "配置 KMP Gradle 插件和源集")),
            KmpPhase("P1", "Domain 层迁移", listOf("43 个 .kt 文件迁移到 shared/src/commonMain", "UseCase + Entity + Repository 接口")),
            KmpPhase("P2", "Room KMP 改造", listOf("11 个数据库文件改造", "Entity + DAO + Migration 全部 KMP 化")),
            KmpPhase("P3", "网络层 Retrofit->Ktor", listOf("替换为 Ktor Client + kotlinx.serialization", "支持 Darwin 引擎")),
            KmpPhase("P4", "DI/标签/存储", listOf("10 组 expect/actual 平台抽象", "Hilt -> Koin 全面替换")),
            KmpPhase("P5", "Android 端适配", listOf("14 个 ViewModel 迁移到 Koin", "DataStore KMP 适配")),
            KmpPhase("P6", "iOS 端基础", listOf("53 个 Swift 文件", "SwiftUI + AVFoundation + NavigationStack")),
        )
    } else {
        listOf(
            KmpPhase("P0", "Monorepo Scaffold", listOf("Create shared/android/ios/storybook four modules", "Configure KMP Gradle plugin and source sets")),
            KmpPhase("P1", "Domain Layer Migration", listOf("43 .kt files migrated to shared/src/commonMain", "UseCase + Entity + Repository interfaces")),
            KmpPhase("P2", "Room KMP Refactoring", listOf("11 database files refactored", "Entity + DAO + Migration fully KMP-compatible")),
            KmpPhase("P3", "Network Layer Retrofit->Ktor", listOf("Replaced with Ktor Client + kotlinx.serialization", "Darwin engine support")),
            KmpPhase("P4", "DI/Tags/Storage", listOf("10 groups of expect/actual platform abstractions", "Hilt -> Koin full replacement")),
            KmpPhase("P5", "Android Adaptation", listOf("14 ViewModels migrated to Koin", "DataStore KMP adaptation")),
            KmpPhase("P6", "iOS Foundation", listOf("53 Swift files", "SwiftUI + AVFoundation + NavigationStack")),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        phases.forEachIndexed { index, phase ->
            val phaseColor = when (index) {
                0 -> Color(0xFF4CAF50)
                1 -> Color(0xFF2196F3)
                2 -> Color(0xFF9C27B0)
                3 -> Color(0xFFFF9800)
                4 -> HDBlue
                5 -> HDRed
                else -> Color(0xFF607D8B)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = phaseColor.copy(alpha = 0.06f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = phase.id,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = phaseColor,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = phase.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    phase.items.forEach { item ->
                        Text(
                            text = "  $item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ========== 区块4: 关键数字 ==========

@Composable
private fun KeyNumbersSection(lang: AppLanguage) {
    val numbers = if (lang == AppLanguage.ZH) {
        listOf(
            "1650" to "行设计文档 + 实施计划",
            "43" to "个 Domain 文件迁移",
            "11" to "个 Room 数据库文件改造",
            "10" to "组 expect/actual 平台抽象",
            "14" to "个 ViewModel DI 迁移",
            "53" to "个 iOS Swift 文件",
        )
    } else {
        listOf(
            "1650" to "Lines of design docs + implementation plan",
            "43" to "Domain files migrated",
            "11" to "Room database files refactored",
            "10" to "expect/actual platform abstractions",
            "14" to "ViewModels DI migrated",
            "53" to "iOS Swift files",
        )
    }

    val colors = listOf(
        HDBlue,
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFF9C27B0),
        Color(0xFFFF9800),
        HDRed,
    )

    // 3x2 grid
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        numbers.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEachIndexed { colIndex, (number, label) ->
                    val colorIndex = rowIndex * 3 + colIndex
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors[colorIndex].copy(alpha = 0.08f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = number,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors[colorIndex],
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
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

// ========== 区块5: 未来规划 ==========

@Composable
private fun FutureRoadmapSection(lang: AppLanguage) {
    val roadmaps = if (lang == AppLanguage.ZH) {
        listOf(
            Triple(
                "短期 (1-2 月)",
                Color(0xFF4CAF50),
                listOf(
                    "单元测试覆盖",
                    "性能优化",
                    "Bug 修复",
                ),
            ),
            Triple(
                "中期 (3-6 月)",
                Color(0xFF2196F3),
                listOf(
                    "桌面小组件",
                    "标签编辑",
                    "电池优化",
                ),
            ),
            Triple(
                "长期 (6-12 月)",
                HDBlue,
                listOf(
                    "应用商店",
                    "车载/穿戴扩展",
                ),
            ),
        )
    } else {
        listOf(
            Triple(
                "Short-term (1-2 mo)",
                Color(0xFF4CAF50),
                listOf(
                    "Unit test coverage",
                    "Performance optimization",
                    "Bug fixes",
                ),
            ),
            Triple(
                "Mid-term (3-6 mo)",
                Color(0xFF2196F3),
                listOf(
                    "Desktop widgets",
                    "Tag editing",
                    "Battery optimization",
                ),
            ),
            Triple(
                "Long-term (6-12 mo)",
                HDBlue,
                listOf(
                    "App store release",
                    "Auto/wearable extensions",
                ),
            ),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        roadmaps.forEach { (title, color, items) ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = color.copy(alpha = 0.06f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    items.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.width(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
