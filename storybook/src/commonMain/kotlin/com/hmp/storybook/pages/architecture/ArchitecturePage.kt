package com.hmp.storybook.pages.architecture

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun ArchitecturePage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.architecture(lang),
        description = Strings.architectureDescription(lang),
        onBack = onBack,
    ) {
        // 版本信息
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "版本信息" else "Version Info",
            description = if (lang == AppLanguage.ZH) {
                "HMP v5.10 当前使用的核心技术版本"
            } else {
                "Core technology versions for HMP v5.10"
            },
        ) {
            VersionInfoCard(lang)
        }

        // 架构概览: 5 层架构
        ComponentShowcase(
            title = Strings.overview(lang),
            description = if (lang == AppLanguage.ZH) {
                "HMP 采用 5 层架构: UI层(平台原生) / ViewModel层(Koin+StateFlow) / Domain层(shared) / Data层(shared) / 播放引擎(平台原生)"
            } else {
                "HMP uses 5-layer architecture: UI(Native) / ViewModel(Koin+StateFlow) / Domain(shared) / Data(shared) / Playback Engine(Native)"
            },
        ) {
            ArchitectureDiagram(lang)
        }

        // 模块结构
        ComponentShowcase(
            title = Strings.moduleStructure(lang),
            description = if (lang == AppLanguage.ZH) {
                "Monorepo 结构: :shared, :android:app, :android:core-player, :shared-ui, :ios, :storybook"
            } else {
                "Monorepo: :shared, :android:app, :android:core-player, :shared-ui, :ios, :storybook"
            },
        ) {
            ModuleStructureDiagram(lang)
        }

        // 技术栈表格
        ComponentShowcase(
            title = Strings.techStack(lang),
            description = if (lang == AppLanguage.ZH) {
                "各平台使用的技术栈完整对照"
            } else {
                "Complete tech stack comparison across platforms"
            },
        ) {
            TechStackTable(lang)
        }

        // KMP 跨平台机制: expect/actual 8 组接口
        ComponentShowcase(
            title = Strings.kmpPlatform(lang),
            description = if (lang == AppLanguage.ZH) {
                "通过 expect/actual 实现 8 组平台特定接口"
            } else {
                "8 groups of platform-specific interfaces via expect/actual"
            },
        ) {
            ExpectActualList(lang)
        }

        // 数据库实体
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "数据库实体" else "Database Entities",
            description = if (lang == AppLanguage.ZH) {
                "8 个 Room 实体: Music, MusicExtra, UserInfo, MusicLabel, Playlist, PlaylistItem, PlaybackHistory, ListeningDuration"
            } else {
                "8 Room entities: Music, MusicExtra, UserInfo, MusicLabel, Playlist, PlaylistItem, PlaybackHistory, ListeningDuration"
            },
        ) {
            DatabaseEntitiesList(lang)
        }

        // UseCase 列表
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "UseCase 列表" else "UseCase List",
            description = if (lang == AppLanguage.ZH) {
                "19 个 UseCase 覆盖音乐管理、播放控制、歌单操作等核心业务"
            } else {
                "19 UseCases covering music management, playback control, playlist operations, etc."
            },
        ) {
            UseCaseList(lang)
        }
    }
}

@Composable
private fun VersionInfoCard(lang: AppLanguage) {
    val versions = if (lang == AppLanguage.ZH) {
        listOf(
            "HMP 版本" to "v5.10",
            "Kotlin" to "2.2.21",
            "Compose Multiplatform" to "1.8.2",
            "Gradle" to "9.1.0",
        )
    } else {
        listOf(
            "HMP Version" to "v5.10",
            "Kotlin" to "2.2.21",
            "Compose Multiplatform" to "1.8.2",
            "Gradle" to "9.1.0",
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        versions.forEach { (key, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        HDBlue.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = HDBlue,
                )
            }
        }
    }
}

@Composable
private fun ArchitectureDiagram(lang: AppLanguage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // UI 层 (平台原生)
        ArchitectureLayer(
            name = if (lang == AppLanguage.ZH) "UI 层 (平台原生)" else "UI Layer (Platform Native)",
            color = Color(0xFF4CAF50),
            items = listOf(
                "Android: Jetpack Compose + Material3 + Navigation 3",
                "iOS: SwiftUI + NavigationStack",
            ),
        )

        // ViewModel 层 (Koin+StateFlow)
        ArchitectureLayer(
            name = if (lang == AppLanguage.ZH) "ViewModel 层 (Koin + StateFlow)" else "ViewModel Layer (Koin + StateFlow)",
            color = Color(0xFF2196F3),
            items = listOf(
                "Koin 依赖注入",
                "StateFlow 状态管理",
                "平台原生 ViewModel",
            ),
        )

        // Domain 层 (shared)
        ArchitectureLayer(
            name = if (lang == AppLanguage.ZH) "Domain 层 (shared)" else "Domain Layer (shared)",
            color = HDBlue,
            items = listOf(
                "19 个 UseCases",
                "领域模型 (Music, Playlist, UserInfo...)",
                "位于 shared/src/commonMain/kotlin/com/hmp/domain/",
            ),
        )

        // Data 层 (shared)
        ArchitectureLayer(
            name = if (lang == AppLanguage.ZH) "Data 层 (shared)" else "Data Layer (shared)",
            color = Color(0xFF9C27B0),
            items = listOf(
                "Repository 实现",
                "8 个 Room 实体",
                "Ktor 网络层",
                "位于 shared/src/commonMain/kotlin/com/hmp/data/",
            ),
        )

        // 播放引擎 (平台原生)
        ArchitectureLayer(
            name = if (lang == AppLanguage.ZH) "播放引擎 (平台原生)" else "Playback Engine (Platform Native)",
            color = Color(0xFFFF9800),
            items = listOf(
                "Android: Media3 ExoPlayer",
                "iOS: AVFoundation",
                "独立模块 :android:core-player",
            ),
        )
    }
}

@Composable
private fun ArchitectureLayer(
    name: String,
    color: Color,
    items: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            items.forEach { item ->
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

@Composable
private fun ModuleStructureDiagram(lang: AppLanguage) {
    val structure = """
HMP/
├── shared/                    # KMP 共享模块 (domain + data 层)
│   └── src/
│       ├── commonMain/        # 跨平台共享代码
│       ├── androidMain/       # Android 特定实现
│       └── iosMain/           # iOS 特定实现
├── android/                   # Android 平台
│   ├── app/                   # 入口模块
│   ├── core-player/           # 播放核心 (Media3)
│   └── feature-ui/            # UI 模块 (Compose)
├── ios/                       # iOS 平台 (SwiftUI)
│   └── HMP/                   # Xcode 项目
└── storybook/                 # 组件文档 (Wasm)
    """.trimIndent()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E),
        ),
    ) {
        Text(
            text = structure,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = Color(0xFFA6E3A1),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun TechStackTable(lang: AppLanguage) {
    val rows = listOf(
        if (lang == AppLanguage.ZH) {
            listOf("领域", "Android", "Shared (KMP)", "iOS")
        } else {
            listOf("Domain", "Android", "Shared (KMP)", "iOS")
        },
        listOf("UI", "Compose + M3", "--", "SwiftUI"),
        listOf("导航", "Navigation 3", "--", "NavigationStack"),
        listOf("播放", "Media3", "--", "AVFoundation"),
        listOf("数据库", "Room KMP", "Room KMP", "Room KMP"),
        listOf("偏好存储", "DataStore", "DataStore KMP", "UserDefaults"),
        listOf("网络", "--", "Ktor Client", "Ktor (Darwin)"),
        listOf("序列化", "kotlinx.serialization", "kotlinx.serialization", "--"),
        listOf("DI", "Koin", "Koin", "Koin"),
        listOf("图片加载", "Coil", "--", "AsyncImage"),
        listOf("模糊效果", "Haze", "--", "UIVisualEffectView"),
        listOf("状态管理", "StateFlow", "StateFlow", "@Observable"),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            val isHeader = index == 0
            val bgColor = if (isHeader) {
                HDBlue.copy(alpha = 0.1f)
            } else if (index % 2 == 0) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                Color.Transparent
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(4.dp))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
            ) {
                row.forEachIndexed { colIndex, cell ->
                    val weight = if (colIndex == 0) 1.2f else 1f
                    Text(
                        text = cell,
                        style = if (isHeader) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHeader) HDBlue else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(weight),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpectActualList(lang: AppLanguage) {
    val items = if (lang == AppLanguage.ZH) {
        listOf(
            "DeviceMusicScanner -- 设备音乐扫描",
            "MusicTagParser -- 音乐标签解析",
            "SecureStorageHelper -- 安全存储 (API Key 加密)",
            "PinyinSortKey -- 拼音排序键",
            "DatabaseBuilder -- Room 数据库构建器",
            "DataStoreFactory -- DataStore 构建",
            "HttpClient -- Ktor HTTP 客户端",
            "BackupFileRepository -- 备份文件读写",
        )
    } else {
        listOf(
            "DeviceMusicScanner -- Device music scanning",
            "MusicTagParser -- Music tag parsing",
            "SecureStorageHelper -- Secure storage (API Key encryption)",
            "PinyinSortKey -- Pinyin sort key",
            "DatabaseBuilder -- Room database builder",
            "DataStoreFactory -- DataStore factory",
            "HttpClient -- Ktor HTTP client",
            "BackupFileRepository -- Backup file I/O",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HDBlue),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DatabaseEntitiesList(lang: AppLanguage) {
    val entities = if (lang == AppLanguage.ZH) {
        listOf(
            "Music -- 音乐基础信息 (id, title, artist, album, duration, uri...)",
            "MusicExtra -- 音乐扩展信息 (bitrate, sampleRate, format, fileSize...)",
            "UserInfo -- 用户信息 (id, name, avatar, preferences...)",
            "MusicLabel -- 音乐标签 (musicId, label, type...)",
            "Playlist -- 歌单 (id, name, cover, description, createdAt...)",
            "PlaylistItem -- 歌单项 (playlistId, musicId, order, addedAt...)",
            "PlaybackHistory -- 播放历史 (musicId, playedAt, playDuration...)",
            "ListeningDuration -- 收听时长 (date, totalDuration, musicCount...)",
        )
    } else {
        listOf(
            "Music -- Base music info (id, title, artist, album, duration, uri...)",
            "MusicExtra -- Extended music info (bitrate, sampleRate, format, fileSize...)",
            "UserInfo -- User info (id, name, avatar, preferences...)",
            "MusicLabel -- Music labels (musicId, label, type...)",
            "Playlist -- Playlists (id, name, cover, description, createdAt...)",
            "PlaylistItem -- Playlist items (playlistId, musicId, order, addedAt...)",
            "PlaybackHistory -- Playback history (musicId, playedAt, playDuration...)",
            "ListeningDuration -- Listening duration (date, totalDuration, musicCount...)",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entities.forEach { entity ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                val parts = entity.split(" -- ")
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = parts[0],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = HDBlue,
                    )
                    if (parts.size > 1) {
                        Text(
                            text = parts[1],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UseCaseList(lang: AppLanguage) {
    val useCases = if (lang == AppLanguage.ZH) {
        listOf(
            "GetAllMusicUseCase -- 获取所有音乐",
            "GetMusicByIdUseCase -- 根据 ID 获取音乐",
            "SearchMusicUseCase -- 搜索音乐",
            "ScanDeviceMusicUseCase -- 扫描设备音乐",
            "GetPlaylistUseCase -- 获取歌单",
            "CreatePlaylistUseCase -- 创建歌单",
            "AddToPlaylistUseCase -- 添加到歌单",
            "RemoveFromPlaylistUseCase -- 从歌单移除",
            "ReorderPlaylistUseCase -- 歌单排序",
            "GetPlaybackHistoryUseCase -- 获取播放历史",
            "AddPlaybackHistoryUseCase -- 添加播放历史",
            "GetListeningStatsUseCase -- 获取收听统计",
            "GetUserInfoUseCase -- 获取用户信息",
            "UpdateUserInfoUseCase -- 更新用户信息",
            "GetMusicLabelsUseCase -- 获取音乐标签",
            "ToggleFavoriteUseCase -- 切换收藏",
            "GetFavoritesUseCase -- 获取收藏列表",
            "BackupDataUseCase -- 备份数据",
            "RestoreDataUseCase -- 还原数据",
        )
    } else {
        listOf(
            "GetAllMusicUseCase -- Get all music",
            "GetMusicByIdUseCase -- Get music by ID",
            "SearchMusicUseCase -- Search music",
            "ScanDeviceMusicUseCase -- Scan device music",
            "GetPlaylistUseCase -- Get playlist",
            "CreatePlaylistUseCase -- Create playlist",
            "AddToPlaylistUseCase -- Add to playlist",
            "RemoveFromPlaylistUseCase -- Remove from playlist",
            "ReorderPlaylistUseCase -- Reorder playlist",
            "GetPlaybackHistoryUseCase -- Get playback history",
            "AddPlaybackHistoryUseCase -- Add playback history",
            "GetListeningStatsUseCase -- Get listening stats",
            "GetUserInfoUseCase -- Get user info",
            "UpdateUserInfoUseCase -- Update user info",
            "GetMusicLabelsUseCase -- Get music labels",
            "ToggleFavoriteUseCase -- Toggle favorite",
            "GetFavoritesUseCase -- Get favorites list",
            "BackupDataUseCase -- Backup data",
            "RestoreDataUseCase -- Restore data",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        useCases.forEachIndexed { index, useCase ->
            val parts = useCase.split(" -- ")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 0) {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = parts[0],
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (parts.size > 1) {
                    Text(
                        text = parts[1],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
