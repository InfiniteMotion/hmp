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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
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
import com.hearablemusic.player.ui.library.viewmodel.FolderInfo
import com.hearablemusic.player.ui.library.viewmodel.HiddenFolderInfo
import com.hearablemusic.player.ui.library.viewmodel.KnownFolderPickerTarget
import com.hearablemusic.player.ui.library.viewmodel.LibrarySettingsEffect
import com.hearablemusic.player.ui.library.viewmodel.LibrarySettingsViewModel
import com.hearablemusic.player.ui.platform.DirectorySelectionMode
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibrarySettingsScreen(
    navController: NavBackStack<NavKey>,
    // 屏级 VM：本屏交互态与数据投影都在这里；扫描动作经 LibraryScanUseCase（与引导页同一条）
    settingsViewModel: LibrarySettingsViewModel = koinViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    // 单一状态出口：交互态 + 本屏数据投影（总量 / 扫描进度 / 目录 / 隐藏文件夹）
    val uiState by settingsViewModel.uiState.collectAsState()
    // 已知文件夹列表被横竖屏两处复用，转换只做一次
    val knownFolderPaths = remember(uiState.scannedFolders) {
        uiState.scannedFolders.map { it.path }
    }

    // 权限提示条（R1b）：无音频读取权限时曲库必然为空 —— 给出可见原因与「去设置」入口。
    // 权限态进 uiState；查询仍留在 UI 门面，进入时查一次 + ON_RESUME 重查（从系统设置返回即更新）。
    val platformServices: PlatformServices = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.onPermissionChecked(platformServices.permission.hasMusicReadAccess())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        settingsViewModel.onPermissionChecked(platformServices.permission.hasMusicReadAccess())
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 一次性效果：系统目录选择器由组合点消费，VM 不持有 PlatformServices
    LaunchedEffect(Unit) {
        settingsViewModel.effect.collect { effect ->
            when (effect) {
                is LibrarySettingsEffect.PickDirectory -> {
                    platformServices.filePicker.pickDirectory { picked ->
                        picked?.let { settingsViewModel.onDirectoryPicked(effect.target, it) }
                    }
                }
            }
        }
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.library_settings),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
        ) {
            if (uiState.hasMusicReadAccess == false && uiState.musicCount == 0) {
                PermissionNoticeBanner(onOpenSettings = platformServices.permission::openAppSettings)
            }
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.section),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        // 1. 音乐库统计
                        LibraryStatsSection(
                            musicCount = uiState.musicCount,
                            analyzedCount = uiState.analyzedCount,
                        )

                        // 2. 扫描选项
                        ScanOptionsSection(
                            isScanning = uiState.isScanning,
                            onIncrementalScan = settingsViewModel::onIncrementalScanRequested,
                            showFullRescanConfirm = uiState.showFullRescanConfirm,
                            onFullRescanRequested = settingsViewModel::onFullRescanRequested,
                            onFullRescanConfirmed = settingsViewModel::onFullRescanConfirmed,
                            onFullRescanDismissed = settingsViewModel::onFullRescanDismissed,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        LibraryManagementSection(
                            folders = uiState.scannedFolders,
                            hiddenFolders = uiState.hiddenFolders,
                            pendingHideFolder = uiState.pendingHideFolder,
                            onHideFolderRequested = settingsViewModel::onHideFolderRequested,
                            onHideFolderConfirmed = settingsViewModel::onHideFolderConfirmed,
                            onHideFolderDismissed = settingsViewModel::onHideFolderDismissed,
                            onUnhideFolder = settingsViewModel::onUnhideFolder,
                        )
                        DirectoryManagementSections(
                            scanDirectories = uiState.scanDirectories,
                            blockedDirectories = uiState.blockedDirectories,
                            knownFolders = knownFolderPaths,
                            knownFolderPicker = uiState.knownFolderPicker,
                            onAddScanRequested = { mode -> settingsViewModel.onAddDirectoryRequested(KnownFolderPickerTarget.SCAN, mode) },
                            onAddBlockedRequested = { mode -> settingsViewModel.onAddDirectoryRequested(KnownFolderPickerTarget.BLOCKED, mode) },
                            onRemoveScan = settingsViewModel::removeScanDirectory,
                            onRemoveBlocked = settingsViewModel::removeBlockedDirectory,
                            onFolderPicked = settingsViewModel::onDirectoryPicked,
                            onPickerDismiss = settingsViewModel::onKnownFolderDismissed,
                        )
                    }
                }
            } else {
                LibraryStatsSection(musicCount = uiState.musicCount, analyzedCount = uiState.analyzedCount)
                ScanOptionsSection(
                    isScanning = uiState.isScanning,
                    onIncrementalScan = settingsViewModel::onIncrementalScanRequested,
                    showFullRescanConfirm = uiState.showFullRescanConfirm,
                    onFullRescanRequested = settingsViewModel::onFullRescanRequested,
                    onFullRescanConfirmed = settingsViewModel::onFullRescanConfirmed,
                    onFullRescanDismissed = settingsViewModel::onFullRescanDismissed,
                )
                LibraryManagementSection(
                    folders = uiState.scannedFolders,
                    hiddenFolders = uiState.hiddenFolders,
                    pendingHideFolder = uiState.pendingHideFolder,
                    onHideFolderRequested = settingsViewModel::onHideFolderRequested,
                    onHideFolderConfirmed = settingsViewModel::onHideFolderConfirmed,
                    onHideFolderDismissed = settingsViewModel::onHideFolderDismissed,
                    onUnhideFolder = settingsViewModel::onUnhideFolder,
                )
                DirectoryManagementSections(
                    scanDirectories = uiState.scanDirectories,
                    blockedDirectories = uiState.blockedDirectories,
                    knownFolders = knownFolderPaths,
                    knownFolderPicker = uiState.knownFolderPicker,
                    onAddScanRequested = { mode -> settingsViewModel.onAddDirectoryRequested(KnownFolderPickerTarget.SCAN, mode) },
                    onAddBlockedRequested = { mode -> settingsViewModel.onAddDirectoryRequested(KnownFolderPickerTarget.BLOCKED, mode) },
                    onRemoveScan = settingsViewModel::removeScanDirectory,
                    onRemoveBlocked = settingsViewModel::removeBlockedDirectory,
                    onFolderPicked = settingsViewModel::onDirectoryPicked,
                    onPickerDismiss = settingsViewModel::onKnownFolderDismissed,
                )
            }
            MiniPlayerSafeSpacer(height = metrics.bar)
        }
    }
}

@Composable
private fun LibraryManagementSection(
    folders: List<FolderInfo>,
    hiddenFolders: List<HiddenFolderInfo>,
    pendingHideFolder: String?,
    onHideFolderRequested: (String) -> Unit,
    onHideFolderConfirmed: () -> Unit,
    onHideFolderDismissed: () -> Unit,
    onUnhideFolder: (List<Long>) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    TitleWidget(title = stringResource(Res.string.library_management)) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.base),
        ) {
            Text(
                text = stringResource(Res.string.scanned_folders),
                style = typo.body.medium(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (folders.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_scanned_folders),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(metrics.cozy),
                )
            } else {
                folders.forEach { folder ->
                    FolderItem(
                        folder = folder,
                        onHideClick = { onHideFolderRequested(folder.path) },
                    )
                }
            }
            if (hiddenFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(metrics.compact))
                Text(
                    text = stringResource(Res.string.hidden_folders),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                hiddenFolders.forEach { hidden ->
                    HiddenFolderItem(
                        hidden = hidden,
                        onUnhideClick = { onUnhideFolder(hidden.musicIds) },
                    )
                }
            }
        }
    }
    if (pendingHideFolder != null) {
        AlertDialog(
            onDismissRequest = onHideFolderDismissed,
            title = { Text(stringResource(Res.string.confirm_hide_folder)) },
            confirmButton = {
                TextButton(
                    onClick = onHideFolderConfirmed,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onHideFolderDismissed) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FolderItem(
    folder: FolderInfo,
    onHideClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(metrics.base),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(metrics.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.base),
        ) {
            Icon(
                painter = painterResource(Res.drawable.rectangle_on_rectangle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(metrics.section),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.path,
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.folder_songs_count, folder.songCount),
                    style = typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onHideClick) {
                Text(stringResource(Res.string.hide_folder), style = typo.footnote.medium())
            }
        }
    }
}

@Composable
private fun HiddenFolderItem(
    hidden: HiddenFolderInfo,
    onUnhideClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(metrics.base),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(metrics.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.base),
        ) {
            Icon(
                painter = painterResource(Res.drawable.rectangle_on_rectangle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(metrics.section),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hidden.path,
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.folder_songs_count, hidden.songCount),
                    style = typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            TextButton(onClick = onUnhideClick) {
                Text(stringResource(Res.string.unhide_folder), style = typo.footnote.medium())
            }
        }
    }
}

@Composable
private fun LibraryStatsSection(
    musicCount: Int,
    analyzedCount: Int,
) {
    val metrics = LocalHmpMetrics.current
    TitleWidget(title = stringResource(Res.string.library_stats)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
        ) {
            StatsCard(
                title = stringResource(Res.string.total_songs),
                value = musicCount.toString(),
                icon = Res.drawable.music_note_list,
                modifier = Modifier.weight(1f),
            )
            StatsCard(
                title = stringResource(Res.string.analyzed_songs),
                value = analyzedCount.toString(),
                icon = Res.drawable.media_center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    value: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(metrics.cozy),
        modifier = modifier,
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.padding(metrics.cozy),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(metrics.section),
            )
            Spacer(modifier = Modifier.height(metrics.compact))
            Text(
                text = value,
                style = typo.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = typo.footnote.medium(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanOptionsSection(
    isScanning: Boolean,
    onIncrementalScan: () -> Unit,
    showFullRescanConfirm: Boolean,
    onFullRescanRequested: () -> Unit,
    onFullRescanConfirmed: () -> Unit,
    onFullRescanDismissed: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    TitleWidget(title = stringResource(Res.string.scan_options)) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.cozy),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
            ) {
                // 增量扫描选项
                ScanOptionCard(
                    title = stringResource(Res.string.incremental_load),
                    description = stringResource(Res.string.incremental_scan_desc),
                    icon = Res.drawable.magnifyingglass,
                    onClick = onIncrementalScan,
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f),
                )

                // 全量重建选项
                ScanOptionCard(
                    title = stringResource(Res.string.full_rescan),
                    description = stringResource(Res.string.full_rescan_desc),
                    icon = Res.drawable.trash,
                    onClick = onFullRescanRequested,
                    enabled = !isScanning,
                    isDestructive = true,
                    modifier = Modifier.weight(1f),
                )
            }

            if (isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(metrics.section),
                        strokeWidth = metrics.hair,
                    )
                    Spacer(modifier = Modifier.width(metrics.compact))
                    Text(
                        text = stringResource(Res.string.scanning),
                        style = typo.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showFullRescanConfirm) {
        AlertDialog(
            onDismissRequest = onFullRescanDismissed,
            title = { Text(stringResource(Res.string.confirm_full_rescan)) },
            text = { Text(stringResource(Res.string.full_rescan_warning)) },
            confirmButton = {
                TextButton(
                    onClick = onFullRescanConfirmed,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onFullRescanDismissed) {
                    Text(stringResource(Res.string.cancel))
                }
            },
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
    isDestructive: Boolean = false,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(metrics.cozy),
        colors = CardDefaults.cardColors(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .padding(metrics.cozy)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.compact),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(metrics.block),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Text(
                text = title,
                style = typo.subtitle,
                color = if (isDestructive && enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = description,
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── 目录管理（R3 恢复）──────────────────────────────────────────────────────
// 此前该能力随旧桌面 UI 层（desktop/feature-ui）一并被删：数据层（ScanDirectoryConfig +
// SettingsRepository）完好，但 UI 与写入入口缺失，配置恒为空、扫描永远回退默认目录。
// 见 docs/archive/7_x/7_1/UI层统一-能力搬迁点检.md R3。

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
    knownFolderPicker: KnownFolderPickerTarget?,
    onAddScanRequested: (DirectorySelectionMode) -> Unit,
    onAddBlockedRequested: (DirectorySelectionMode) -> Unit,
    onRemoveScan: (String) -> Unit,
    onRemoveBlocked: (String) -> Unit,
    onFolderPicked: (KnownFolderPickerTarget, String) -> Unit,
    onPickerDismiss: () -> Unit,
) {
    val platformServices: PlatformServices = koinInject()
    if (platformServices.filePicker.directorySelectionMode == DirectorySelectionMode.UNSUPPORTED) return

    DirectorySection(
        title = stringResource(Res.string.scan_directories),
        emptyText = stringResource(Res.string.no_scan_directories),
        directories = scanDirectories,
        knownFolders = knownFolders,
        showPicker = knownFolderPicker == KnownFolderPickerTarget.SCAN,
        onAddRequested = onAddScanRequested,
        onRemove = onRemoveScan,
        onFolderPicked = { path -> onFolderPicked(KnownFolderPickerTarget.SCAN, path) },
        onPickerDismiss = onPickerDismiss,
    )
    DirectorySection(
        title = stringResource(Res.string.blocked_directories),
        emptyText = stringResource(Res.string.no_blocked_directories),
        directories = blockedDirectories,
        knownFolders = knownFolders,
        showPicker = knownFolderPicker == KnownFolderPickerTarget.BLOCKED,
        onAddRequested = onAddBlockedRequested,
        onRemove = onRemoveBlocked,
        onFolderPicked = { path -> onFolderPicked(KnownFolderPickerTarget.BLOCKED, path) },
        onPickerDismiss = onPickerDismiss,
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
    showPicker: Boolean,
    onAddRequested: (DirectorySelectionMode) -> Unit,
    onRemove: (String) -> Unit,
    onFolderPicked: (String) -> Unit,
    onPickerDismiss: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val platformServices: PlatformServices = koinInject()
    val mode = platformServices.filePicker.directorySelectionMode
    val typo = LocalHmpTypo.current

    TitleWidget(title = title) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.base),
        ) {
            if (directories.isEmpty()) {
                Text(
                    text = emptyText,
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(metrics.base),
                )
            } else {
                directories.forEach { path ->
                    DirectoryItem(path = path, onRemoveClick = { onRemove(path) })
                }
            }

            TextButton(
                onClick = {
                    when (mode) {
                        DirectorySelectionMode.ARBITRARY_PATH,
                        DirectorySelectionMode.KNOWN_FOLDERS,
                        -> onAddRequested(mode)

                        DirectorySelectionMode.UNSUPPORTED -> Unit
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(Res.string.add_directory))
            }

            Text(
                text = stringResource(Res.string.directory_rescan_hint),
                style = typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPicker) {
        // 排除已在该列表中的目录，避免重复添加
        val candidates = knownFolders.filter { it !in directories }
        AlertDialog(
            onDismissRequest = onPickerDismiss,
            title = { Text(stringResource(Res.string.pick_folder_from_library)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.no_scanned_folders),
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        candidates.forEach { path ->
                            Text(
                                text = path,
                                style = typo.body,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFolderPicked(path) }
                                    .padding(vertical = metrics.base),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onPickerDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DirectoryItem(path: String, onRemoveClick: () -> Unit) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(metrics.base),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.base, vertical = metrics.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = path,
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.remove),
                style = typo.footnote.medium(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onRemoveClick)
                    .padding(start = metrics.base, top = metrics.micro, bottom = metrics.micro),
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
    val metrics = LocalHmpMetrics.current
    TitleWidget(title = stringResource(Res.string.library_permission_required)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.cozy, vertical = metrics.compact),
            horizontalAlignment = Alignment.End,
        ) {
            TextButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(Res.string.go_to_settings))
            }
        }
    }
}
