package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.activityViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.library.viewmodel.FolderInfo
import com.hearablemusic.player.ui.library.viewmodel.HiddenFolderInfo
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.platform.DirectorySelectionMode
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.add_directory
import com.hearablemusic.player.ui.generated.resources.analyzed_songs
import com.hearablemusic.player.ui.generated.resources.blocked_directories
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.confirm
import com.hearablemusic.player.ui.generated.resources.confirm_full_rescan
import com.hearablemusic.player.ui.generated.resources.confirm_hide_folder
import com.hearablemusic.player.ui.generated.resources.directory_rescan_hint
import com.hearablemusic.player.ui.generated.resources.folder_songs_count
import com.hearablemusic.player.ui.generated.resources.full_rescan
import com.hearablemusic.player.ui.generated.resources.full_rescan_desc
import com.hearablemusic.player.ui.generated.resources.full_rescan_warning
import com.hearablemusic.player.ui.generated.resources.go_to_settings
import com.hearablemusic.player.ui.generated.resources.hidden_folders
import com.hearablemusic.player.ui.generated.resources.hide_folder
import com.hearablemusic.player.ui.generated.resources.incremental_load
import com.hearablemusic.player.ui.generated.resources.incremental_scan_desc
import com.hearablemusic.player.ui.generated.resources.library_management
import com.hearablemusic.player.ui.generated.resources.library_permission_required
import com.hearablemusic.player.ui.generated.resources.library_settings
import com.hearablemusic.player.ui.generated.resources.library_stats
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.media_center
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.no_blocked_directories
import com.hearablemusic.player.ui.generated.resources.no_scan_directories
import com.hearablemusic.player.ui.generated.resources.no_scanned_folders
import com.hearablemusic.player.ui.generated.resources.pick_folder_from_library
import com.hearablemusic.player.ui.generated.resources.rectangle_on_rectangle
import com.hearablemusic.player.ui.generated.resources.remove
import com.hearablemusic.player.ui.generated.resources.scan_directories
import com.hearablemusic.player.ui.generated.resources.scan_options
import com.hearablemusic.player.ui.generated.resources.scanned_folders
import com.hearablemusic.player.ui.generated.resources.scanning
import com.hearablemusic.player.ui.generated.resources.total_songs
import com.hearablemusic.player.ui.generated.resources.trash
import com.hearablemusic.player.ui.generated.resources.unhide_folder
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun LibrarySettingsScreen(
    navController: NavBackStack<NavKey>,
    libraryViewModel: LibraryViewModel = activityViewModel()
) {
    val musicCount by libraryViewModel.musicCount.collectAsState(initial = 0)
    val analyzedCount by libraryViewModel.musicWithExtraCount.collectAsState(initial = 0)
    val scannedFolders by libraryViewModel.scannedFolders.collectAsState()
    val hiddenFolders by libraryViewModel.hiddenFolders.collectAsState()
    val scanDirConfig by libraryViewModel.scanDirectoryConfig.collectAsState()
    val isScanning by libraryViewModel.isScanning.collectAsState(initial = false)

    // 权限提示条（R1b）：无音频读取权限时曲库必然为空 —— 给出可见原因与「去设置」入口，
    // 避免「静默空库」。读取随每次重组刷新，从系统设置返回后即可更新。
    val platformServices: PlatformServices = koinInject()
    val hasMusicReadAccess = platformServices.permission.hasMusicReadAccess()

    LaunchedEffect(Unit) {
        libraryViewModel.loadHiddenFolders()
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.library_settings)
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!hasMusicReadAccess && musicCount == 0) {
                PermissionNoticeBanner(onOpenSettings = platformServices.permission::openAppSettings)
            }
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // 1. 音乐库统计
            LibraryStatsSection(
                musicCount = musicCount,
                analyzedCount = analyzedCount
            )
            
            // 2. 扫描选项
            ScanOptionsSection(
                isScanning = isScanning,
                onIncrementalScan = libraryViewModel::refreshMusicList,
                onFullRescan = libraryViewModel::fullRescan
            )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        LibraryManagementSection(
                            folders = scannedFolders, hiddenFolders = hiddenFolders,
                            onHideFolder = libraryViewModel::hideFolder, onUnhideFolder = libraryViewModel::restoreToLibrary
                        )
                        DirectoryManagementSections(
                            scanDirectories = scanDirConfig.scanDirectories,
                            blockedDirectories = scanDirConfig.blockedDirectories,
                            knownFolders = scannedFolders.map { it.path },
                            onAddScan = libraryViewModel::addScanDirectory,
                            onRemoveScan = libraryViewModel::removeScanDirectory,
                            onAddBlocked = libraryViewModel::addBlockedDirectory,
                            onRemoveBlocked = libraryViewModel::removeBlockedDirectory
                        )
                    }
                }
            } else {
                LibraryStatsSection(musicCount = musicCount, analyzedCount = analyzedCount)
                ScanOptionsSection(isScanning = isScanning, onIncrementalScan = libraryViewModel::refreshMusicList, onFullRescan = libraryViewModel::fullRescan)
                LibraryManagementSection(folders = scannedFolders, hiddenFolders = hiddenFolders, onHideFolder = libraryViewModel::hideFolder, onUnhideFolder = libraryViewModel::restoreToLibrary)
                DirectoryManagementSections(
                    scanDirectories = scanDirConfig.scanDirectories,
                    blockedDirectories = scanDirConfig.blockedDirectories,
                    knownFolders = scannedFolders.map { it.path },
                    onAddScan = libraryViewModel::addScanDirectory,
                    onRemoveScan = libraryViewModel::removeScanDirectory,
                    onAddBlocked = libraryViewModel::addBlockedDirectory,
                    onRemoveBlocked = libraryViewModel::removeBlockedDirectory
                )
            }
            MiniPlayerSafeSpacer(height = 56.dp)
        }
    }
}

@Composable
private fun LibraryManagementSection(
    folders: List<FolderInfo>,
    hiddenFolders: List<HiddenFolderInfo>,
    onHideFolder: (String) -> Unit,
    onUnhideFolder: (List<Long>) -> Unit
) {
    var folderToHide by remember { mutableStateOf<String?>(null) }
    TitleWidget(title = stringResource(Res.string.library_management)) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.scanned_folders),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (folders.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_scanned_folders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                )
            } else {
                folders.forEach { folder ->
                    FolderItem(
                        folder = folder,
                        onHideClick = { folderToHide = folder.path }
                    )
                }
            }
            if (hiddenFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.hidden_folders),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                hiddenFolders.forEach { hidden ->
                    HiddenFolderItem(
                        hidden = hidden,
                        onUnhideClick = { onUnhideFolder(hidden.musicIds) }
                    )
                }
            }
        }
    }
    if (folderToHide != null) {
        AlertDialog(
            onDismissRequest = { folderToHide = null },
            title = { Text(stringResource(Res.string.confirm_hide_folder)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        folderToHide?.let { onHideFolder(it) }
                        folderToHide = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToHide = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun FolderItem(
    folder: FolderInfo,
    onHideClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.rectangle_on_rectangle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(Res.string.folder_songs_count, folder.songCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onHideClick) {
                Text(stringResource(Res.string.hide_folder), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HiddenFolderItem(
    hidden: HiddenFolderInfo,
    onUnhideClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.rectangle_on_rectangle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hidden.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(Res.string.folder_songs_count, hidden.songCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onUnhideClick) {
                Text(stringResource(Res.string.unhide_folder), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
@Composable
private fun LibraryStatsSection(
    musicCount: Int,
    analyzedCount: Int
) {
    TitleWidget(title = stringResource(Res.string.library_stats)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatsCard(
                title = stringResource(Res.string.total_songs),
                value = musicCount.toString(),
                icon = Res.drawable.music_note_list,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = stringResource(Res.string.analyzed_songs),
                value = analyzedCount.toString(),
                icon = Res.drawable.media_center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    value: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanOptionsSection(
    isScanning: Boolean,
    onIncrementalScan: () -> Unit,
    onFullRescan: () -> Unit
) {
    var showFullRescanDialog by remember { mutableStateOf(false) }

    TitleWidget(title = stringResource(Res.string.scan_options)) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 增量扫描选项
                ScanOptionCard(
                    title = stringResource(Res.string.incremental_load),
                    description = stringResource(Res.string.incremental_scan_desc),
                    icon = Res.drawable.magnifyingglass,
                    onClick = onIncrementalScan,
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f)
                )
                
                // 全量重建选项
                ScanOptionCard(
                    title = stringResource(Res.string.full_rescan),
                    description = stringResource(Res.string.full_rescan_desc),
                    icon = Res.drawable.trash,
                    onClick = { showFullRescanDialog = true },
                    enabled = !isScanning,
                    isDestructive = true,
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    if (showFullRescanDialog) {
        AlertDialog(
            onDismissRequest = { showFullRescanDialog = false },
            title = { Text(stringResource(Res.string.confirm_full_rescan)) },
            text = { Text(stringResource(Res.string.full_rescan_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onFullRescan()
                        showFullRescanDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullRescanDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ScanOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    enabled: Boolean,
    isDestructive: Boolean = false
) {
    Card(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDestructive && enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── 目录管理（R3 恢复）──────────────────────────────────────────────────────
// 此前该能力随旧桌面 UI 层（desktop/feature-ui）一并被删：数据层（ScanDirectoryConfig +
// SettingsRepository）完好，但 UI 与写入入口缺失，配置恒为空、扫描永远回退默认目录。
// 见 docs/7_x/A shared-ui/UI层统一-能力搬迁点检.md R3。

/**
 * 扫描目录 / 屏蔽目录两个区块。
 *
 * 平台差异由 [DirectorySelectionMode] 决定：iOS 为 `UNSUPPORTED` → **整块不渲染**
 * （沙箱内音乐来自 Documents，无自定义目录概念）。
 */
@Composable
private fun DirectoryManagementSections(
    scanDirectories: List<String>,
    blockedDirectories: List<String>,
    knownFolders: List<String>,
    onAddScan: (String) -> Unit,
    onRemoveScan: (String) -> Unit,
    onAddBlocked: (String) -> Unit,
    onRemoveBlocked: (String) -> Unit
) {
    val platformServices: PlatformServices = koinInject()
    if (platformServices.filePicker.directorySelectionMode == DirectorySelectionMode.UNSUPPORTED) return

    DirectorySection(
        title = stringResource(Res.string.scan_directories),
        emptyText = stringResource(Res.string.no_scan_directories),
        directories = scanDirectories,
        knownFolders = knownFolders,
        onAdd = onAddScan,
        onRemove = onRemoveScan
    )
    DirectorySection(
        title = stringResource(Res.string.blocked_directories),
        emptyText = stringResource(Res.string.no_blocked_directories),
        directories = blockedDirectories,
        knownFolders = knownFolders,
        onAdd = onAddBlocked,
        onRemove = onRemoveBlocked
    )
}

/**
 * 单个目录列表区块：列表 + 移除 + 添加。
 *
 * 添加方式按平台切换：
 * - `ARBITRARY_PATH`（桌面）：系统目录选择器，可直接给出任意路径。
 * - `KNOWN_FOLDERS`（Android）：从**媒体库已知文件夹**中挑选 —— SAF 的 tree Uri 无法转成
 *   `scanDirectoryConfig` 所需的文件系统路径，故不走 SAF。
 */
@Composable
private fun DirectorySection(
    title: String,
    emptyText: String,
    directories: List<String>,
    knownFolders: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val platformServices: PlatformServices = koinInject()
    val mode = platformServices.filePicker.directorySelectionMode
    var showKnownFolderPicker by remember { mutableStateOf(false) }

    TitleWidget(title = title) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (directories.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp)
                )
            } else {
                directories.forEach { path ->
                    DirectoryItem(path = path, onRemoveClick = { onRemove(path) })
                }
            }

            TextButton(
                onClick = {
                    when (mode) {
                        DirectorySelectionMode.ARBITRARY_PATH ->
                            platformServices.filePicker.pickDirectory { picked -> picked?.let(onAdd) }

                        DirectorySelectionMode.KNOWN_FOLDERS -> showKnownFolderPicker = true

                        DirectorySelectionMode.UNSUPPORTED -> Unit
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(Res.string.add_directory))
            }

            Text(
                text = stringResource(Res.string.directory_rescan_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showKnownFolderPicker) {
        // 排除已在该列表中的目录，避免重复添加
        val candidates = knownFolders.filter { it !in directories }
        AlertDialog(
            onDismissRequest = { showKnownFolderPicker = false },
            title = { Text(stringResource(Res.string.pick_folder_from_library)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.no_scanned_folders),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        candidates.forEach { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAdd(path)
                                        showKnownFolderPicker = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKnownFolderPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DirectoryItem(path: String, onRemoveClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(Res.string.remove),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onRemoveClick)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * 权限提示条（R1b）：无音乐读取权限时曲库必然为空。
 *
 * 此前该情形**完全静默** —— 权限框不弹、扫描返回空集、界面只显示「0 首」而没有任何原因。
 */
@Composable
private fun PermissionNoticeBanner(onOpenSettings: () -> Unit) {
    TitleWidget(title = stringResource(Res.string.library_permission_required)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            TextButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(Res.string.go_to_settings))
            }
        }
    }
}
