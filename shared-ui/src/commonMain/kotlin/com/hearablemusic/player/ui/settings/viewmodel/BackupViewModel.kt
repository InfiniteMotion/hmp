package com.hearablemusic.player.ui.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.export_backup
import com.hearablemusic.player.ui.generated.resources.unknown_error
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hmp.domain.backup.usecase.DeleteBackupUseCase
import com.hmp.domain.backup.usecase.ExportUserDataBackupUseCase
import com.hmp.domain.backup.usecase.GetBackupsUseCase
import com.hmp.domain.backup.usecase.ImportUserDataBackupUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * 备份与恢复页整屏状态（7.5 P1）。
 * [pendingRestorePath] 同时表达「有确认弹窗」与「待恢复哪个文件」，
 * 原先导入区与管理区各存一份 `showRestoreDialog + selectedBackupFilePath`，重复且易不同步。
 */
data class BackupUiState(
    val localBackups: List<String> = emptyList(),
    val pendingRestorePath: String? = null,
    /** 导出 / 恢复 / 删除 / 刷新进行中；期间拒绝新操作，避免两条链同时改同一批表（D7 §4.2）。 */
    val isBusy: Boolean = false,
)

/** 一次性效果：toast 由组合点消费，VM 不持有 DialogManager / NavBackStack。 */
sealed interface BackupEffect {
    data class Message(val text: UiText) : BackupEffect
    data object RestoreSucceeded : BackupEffect
}

/**
 * 备份与恢复设置页的页面级 ViewModel。
 * 随 BackupSettings 页面生命周期创建/清理。
 */
class BackupViewModel(
    private val exportUserDataBackupUseCase: ExportUserDataBackupUseCase,
    private val importUserDataBackupUseCase: ImportUserDataBackupUseCase,
    private val getBackupsUseCase: GetBackupsUseCase,
    private val deleteBackupUseCase: DeleteBackupUseCase,
    private val platformServices: PlatformServices,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<BackupEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<BackupEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch {
            loadLocalBackups()
        }
    }

    /** 只读刷新；调用方已在互斥协程内，本函数不再开新协程。 */
    private suspend fun loadLocalBackups() {
        getBackupsUseCase().onSuccess { list ->
            _uiState.update { it.copy(localBackups = list) }
        }
    }

    /**
     * 所有会动数据的操作统一入口：守卫 + `finally` 释放。
     * 异常或平台回调抛错也不会把页面永久卡在 busy。
     */
    private fun launchExclusive(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onRefreshBackups() = launchExclusive {
        loadLocalBackups()
    }

    fun onDeleteBackup(filePath: String) = launchExclusive {
        deleteBackupUseCase(filePath).onSuccess { loadLocalBackups() }
    }

    fun onExportBackup() = launchExclusive {
        exportUserDataBackupUseCase()
            .onSuccess { filePath ->
                loadLocalBackups()
                platformServices.share.shareFile(
                    filePath,
                    "application/json",
                    getString(Res.string.export_backup),
                )
            }
            .onFailure { e ->
                _effect.emit(BackupEffect.Message(e.message?.let { UiText.Raw(it) } ?: Res.string.unknown_error.asUiText()))
            }
    }

    /** 选备份文件：平台副作用由 VM 发起，回调只把路径写回状态。 */
    fun onPickBackupFile() {
        if (_uiState.value.isBusy) return
        platformServices.filePicker.openBackupFile { path ->
            if (path != null) {
                _uiState.update { it.copy(pendingRestorePath = path) }
            }
        }
    }

    fun onRestoreRequested(filePath: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(pendingRestorePath = filePath) }
    }

    fun onRestoreDismissed() {
        _uiState.update { it.copy(pendingRestorePath = null) }
    }

    fun onRestoreConfirmed() {
        if (_uiState.value.isBusy) return
        val filePath = _uiState.value.pendingRestorePath ?: return
        _uiState.update { it.copy(pendingRestorePath = null) }
        launchExclusive {
            importUserDataBackupUseCase(filePath)
                .onSuccess { _effect.emit(BackupEffect.RestoreSucceeded) }
                .onFailure { e ->
                    _effect.emit(BackupEffect.Message(e.message?.let { UiText.Raw(it) } ?: Res.string.unknown_error.asUiText()))
                }
        }
    }
}
