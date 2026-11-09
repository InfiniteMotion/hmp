package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.text.resolveForEffect
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.backup_scope_note
import com.hearablemusic.player.ui.generated.resources.backup_settings
import com.hearablemusic.player.ui.generated.resources.cancel_action
import com.hearablemusic.player.ui.generated.resources.confirm_restore
import com.hearablemusic.player.ui.generated.resources.delete
import com.hearablemusic.player.ui.generated.resources.export_backup
import com.hearablemusic.player.ui.generated.resources.export_backup_desc
import com.hearablemusic.player.ui.generated.resources.externaldrive
import com.hearablemusic.player.ui.generated.resources.ic_gallery_material_select_checkbox
import com.hearablemusic.player.ui.generated.resources.local_backup
import com.hearablemusic.player.ui.generated.resources.local_backup_desc
import com.hearablemusic.player.ui.generated.resources.no_local_backups
import com.hearablemusic.player.ui.generated.resources.refresh
import com.hearablemusic.player.ui.generated.resources.restore_action
import com.hearablemusic.player.ui.generated.resources.restore_backup
import com.hearablemusic.player.ui.generated.resources.restore_backup_desc
import com.hearablemusic.player.ui.generated.resources.restore_overwrites_note
import com.hearablemusic.player.ui.generated.resources.restore_successful
import com.hearablemusic.player.ui.generated.resources.trash
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hearablemusic.player.ui.settings.viewmodel.BackupEffect
import com.hearablemusic.player.ui.settings.viewmodel.BackupViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupSettingsScreen(
    navController: NavBackStack<NavKey>,
    viewModel: BackupViewModel = koinViewModel(),
    dialogManagerViewModel: DialogManagerViewModel = activityViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    val dialogManager = dialogManagerViewModel.dialogManager
    // 唯一状态出口：备份列表与待确认恢复路径都在这里
    val uiState by viewModel.uiState.collectAsState()

    // 一次性效果：VM 不持有 DialogManager，toast 由组合点消费
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BackupEffect.Message -> {
                    dialogManager.showMessage(effect.text.resolveForEffect())
                }

                is BackupEffect.RestoreSucceeded -> {
                    dialogManager.showMessage(getString(Res.string.restore_successful))
                }
            }
        }
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.backup_settings),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.section),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        ExportBackupSection(onExportBackup = viewModel::onExportBackup, isBusy = uiState.isBusy)
                        ImportBackupSection(onPickBackupFile = viewModel::onPickBackupFile, isBusy = uiState.isBusy)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.section)) {
                        ManageBackupsSection(
                            localBackups = uiState.localBackups,
                            onRestoreRequested = viewModel::onRestoreRequested,
                            onDeleteBackup = viewModel::onDeleteBackup,
                            onRefreshBackups = viewModel::onRefreshBackups,
                            isBusy = uiState.isBusy,
                        )
                    }
                }
            } else {
                // 1. 生成备份
                ExportBackupSection(onExportBackup = viewModel::onExportBackup, isBusy = uiState.isBusy)

                // 2. 导入备份
                ImportBackupSection(onPickBackupFile = viewModel::onPickBackupFile, isBusy = uiState.isBusy)

                // 3. 备份管理
                ManageBackupsSection(
                    localBackups = uiState.localBackups,
                    onRestoreRequested = viewModel::onRestoreRequested,
                    onDeleteBackup = viewModel::onDeleteBackup,
                    onRefreshBackups = viewModel::onRefreshBackups,
                    isBusy = uiState.isBusy,
                )
            } // else
            MiniPlayerSafeSpacer(height = metrics.bar)
        }
    }

    // 确认弹窗只有一份：由 pendingRestorePath 驱动，导入区与管理区共用
    if (uiState.pendingRestorePath != null) {
        RestoreConfirmDialog(
            onConfirm = viewModel::onRestoreConfirmed,
            onDismiss = viewModel::onRestoreDismissed,
            isBusy = uiState.isBusy,
        )
    }
}

@Composable
private fun ExportBackupSection(
    onExportBackup: () -> Unit,
    isBusy: Boolean,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current

    TitleWidget(title = stringResource(Res.string.export_backup)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.cozy),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.export_backup_desc),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            // D7-12③：把"备份里有什么、没有什么"写在导出按钮上方。
            // 快照只覆盖 14 项设置（歌单 / 曲目用户态 / 听歌统计 / 外观与 AI 端点），
            // 不写这一句，用户会以为 API Key、歌词与音效也备进去了 —— 恢复后才发现"丢了"。
            Text(
                text = stringResource(Res.string.backup_scope_note),
                style = typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportBackup,
                enabled = !isBusy,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(metrics.compact),
                        strokeWidth = metrics.hair,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(painter = painterResource(Res.drawable.externaldrive), contentDescription = null)
                    Spacer(modifier = Modifier.width(metrics.compact))
                    Text(stringResource(Res.string.export_backup))
                }
            }
        }
    }
}

@Composable
private fun ImportBackupSection(
    onPickBackupFile: () -> Unit,
    isBusy: Boolean,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current

    TitleWidget(title = stringResource(Res.string.restore_backup)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.cozy),
        ) {
            Text(
                text = stringResource(Res.string.restore_backup_desc),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickBackupFile,
                enabled = !isBusy,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(metrics.compact),
                        strokeWidth = metrics.hair,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(painter = painterResource(Res.drawable.ic_gallery_material_select_checkbox), contentDescription = null)
                    Spacer(modifier = Modifier.width(metrics.compact))
                    Text(stringResource(Res.string.restore_backup))
                }
            }
        }
    }
}

@Composable
private fun ManageBackupsSection(
    localBackups: List<String>,
    onRestoreRequested: (String) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onRefreshBackups: () -> Unit,
    isBusy: Boolean,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current

    TitleWidget(title = stringResource(Res.string.local_backup)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.compact),
        ) {
            Text(
                text = stringResource(Res.string.local_backup_desc),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(onClick = onRefreshBackups, enabled = !isBusy) {
                    Icon(
                        painter = painterResource(Res.drawable.externaldrive),
                        contentDescription = stringResource(Res.string.refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (localBackups.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_local_backups),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(metrics.cozy),
                )
            } else {
                localBackups.forEach { filePath ->
                    BackupItem(
                        filePath = filePath,
                        onRestore = { onRestoreRequested(filePath) },
                        onDelete = { onDeleteBackup(filePath) },
                        isBusy = isBusy,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupItem(
    filePath: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isBusy: Boolean,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    // commonMain 不使用平台文件 API：文件名取路径末段；
    // 备份命名固定为 hearable-backup-v{N}-yyyyMMdd-HHmmss.json，日期从文件名解析（与旧 File.lastModified() 展示一致）
    val fileName = filePath.substringAfterLast('/')
    val fileDate = Regex("""(\d{8})-(\d{6})\.json""").find(fileName)?.destructured?.let { (d, t) ->
        "${d.substring(0, 4)}/${d.substring(4, 6)}/${d.substring(6, 8)} ${t.substring(0, 2)}:${t.substring(2, 4)}"
    } ?: ""
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(metrics.base),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(metrics.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.compact),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = typo.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fileDate,
                    style = typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore, enabled = !isBusy) {
                Icon(
                    painter = painterResource(Res.drawable.ic_gallery_material_select_checkbox),
                    contentDescription = stringResource(Res.string.restore_action),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete, enabled = !isBusy) {
                Icon(
                    painter = painterResource(Res.drawable.trash),
                    contentDescription = stringResource(Res.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isBusy: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.confirm_restore)) },
        // 原来这里又渲染了一遍标题（"确认恢复"当正文），等于什么都没说明。
        // 恢复是破坏性操作，正文该写清"会覆盖什么、不会带回什么"（D7-12③）。
        text = { Text(stringResource(Res.string.restore_overwrites_note)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) {
                Text(stringResource(Res.string.restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel_action))
            }
        },
    )
}
