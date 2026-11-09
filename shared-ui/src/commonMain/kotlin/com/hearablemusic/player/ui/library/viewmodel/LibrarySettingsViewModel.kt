package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.fileParentOf
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hearablemusic.player.ui.platform.DirectorySelectionMode
import com.hmp.domain.agent.port.LibraryMutatedNotifier
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.music.usecase.LibraryScanUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.setting.model.ScanDirectoryConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class FolderInfo(
    val path: String,
    val songCount: Int,
)

data class HiddenFolderInfo(
    val path: String,
    val songCount: Int,
    val musicIds: List<Long>,
)

/** #8 目录选择器要写回哪一份配置。 */
enum class KnownFolderPickerTarget { SCAN, BLOCKED }

/**
 * 曲库设置页的整屏状态（7.5 屏级 VM）：交互态 + 本屏独占的数据投影。
 *
 * `musicCount` / `isScanning` 也在这里 —— 本屏用的是「曲库总量 + 扫描进度」，
 * 与引导页同源但各自订阅；不再需要为一屏去共享一个应用级 VM。
 */
data class LibrarySettingsUiState(
    val pendingHideFolder: String? = null,
    val showFullRescanConfirm: Boolean = false,
    val knownFolderPicker: KnownFolderPickerTarget? = null,
    /** 音频读取权限（UI 在 ON_RESUME 时上报查询结果，状态进 VM 供 banner 响应式渲染）。null = 尚未查过。 */
    val hasMusicReadAccess: Boolean? = null,
    val musicCount: Int = 0,
    val isScanning: Boolean = false,
    val analyzedCount: Int = 0,
    val scannedFolders: List<FolderInfo> = emptyList(),
    val hiddenFolders: List<HiddenFolderInfo> = emptyList(),
    val scanDirectories: List<String> = emptyList(),
    val blockedDirectories: List<String> = emptyList(),
)

/** 一次性效果：系统目录选择器由组合点消费，VM 不持有 PlatformServices。 */
sealed interface LibrarySettingsEffect {
    data class PickDirectory(val target: KnownFolderPickerTarget) : LibrarySettingsEffect
}

/**
 * 曲库设置页的屏级 ViewModel（绑定该页 entry）。
 *
 * 数据侧全部是可观察流：扫描落库、隐藏 / 恢复、其它入口的删除都会自己回流，
 * 本 VM 不再维护「什么时候该重查」的记忆。目录配置与隐藏动作只服务本屏，因此也在这里。
 */
class LibrarySettingsViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val getDeletedMusicIdsGroupedByFolderUseCase: GetDeletedMusicIdsGroupedByFolderUseCase,
    private val removeFromLibraryUseCase: RemoveFromLibraryUseCase,
    private val restoreToLibraryUseCase: RestoreToLibraryUseCase,
    private val scanUseCase: LibraryScanUseCase,
    private val userSettingsUseCase: UserSettingsUseCase,
    private val libraryMutated: LibraryMutatedNotifier,
) : ViewModel() {

    /** 交互态私有源；对 UI 暴露的是与数据投影合并后的 [uiState]。 */
    private val _interaction = MutableStateFlow(LibrarySettingsUiState())

    private val scannedFolders: Flow<List<FolderInfo>> = getAllMusicUseCase
        .observeAllMusic(MusicSortKey.ID, SortDirection.ASC)
        .map { list ->
            list.groupBy { music ->
                try {
                    fileParentOf(music.music.path) ?: getString(Res.string.unknown)
                } catch (e: Exception) {
                    getString(Res.string.unknown)
                }
            }.map { (path, songs) ->
                FolderInfo(path, songs.size)
            }.sortedByDescending { it.songCount }
        }

    private val hiddenFolders: Flow<List<HiddenFolderInfo>> = getDeletedMusicIdsGroupedByFolderUseCase
        .observe()
        .map { grouped ->
            grouped.map { (path, ids) -> HiddenFolderInfo(path = path, songCount = ids.size, musicIds = ids) }
        }

    private data class DataPart(
        val musicCount: Int,
        val isScanning: Boolean,
        val analyzedCount: Int,
        val scannedFolders: List<FolderInfo>,
    )

    private data class FolderPart(
        val hiddenFolders: List<HiddenFolderInfo>,
        val scanDirectories: List<String>,
        val blockedDirectories: List<String>,
    )

    val uiState: StateFlow<LibrarySettingsUiState> = combine(
        combine(
            getAllMusicUseCase.getMusicCount(),
            scanUseCase.isScanning,
            getAllMusicUseCase.getMusicWithExtraCount(),
            scannedFolders,
        ) { count, scanning, analyzed, folders ->
            DataPart(count, scanning, analyzed, folders)
        },
        combine(hiddenFolders, userSettingsUseCase.scanDirectoryConfig) { hidden, dirs ->
            FolderPart(hidden, dirs.scanDirectories, dirs.blockedDirectories)
        },
        _interaction,
    ) { data, folders, interaction ->
        interaction.copy(
            musicCount = data.musicCount,
            isScanning = data.isScanning,
            analyzedCount = data.analyzedCount,
            scannedFolders = data.scannedFolders,
            hiddenFolders = folders.hiddenFolders,
            scanDirectories = folders.scanDirectories,
            blockedDirectories = folders.blockedDirectories,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibrarySettingsUiState())

    private val _effect = MutableSharedFlow<LibrarySettingsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<LibrarySettingsEffect> = _effect.asSharedFlow()

    /** UI 在进入页面与 ON_RESUME 时各上报一次权限查询结果（查询本身留在 UI 门面）。 */
    fun onPermissionChecked(granted: Boolean) {
        _interaction.update { it.copy(hasMusicReadAccess = granted) }
    }

    // ── 扫描 ────────────────────────────────────────────────────────────

    fun onIncrementalScanRequested() {
        viewModelScope.launch { scanUseCase.incremental() }
    }

    fun onFullRescanRequested() {
        _interaction.update { it.copy(showFullRescanConfirm = true) }
    }

    fun onFullRescanDismissed() {
        _interaction.update { it.copy(showFullRescanConfirm = false) }
    }

    fun onFullRescanConfirmed() {
        _interaction.update { it.copy(showFullRescanConfirm = false) }
        viewModelScope.launch { scanUseCase.full() }
    }

    // ── 隐藏 / 恢复文件夹 ───────────────────────────────────────────────

    fun onHideFolderRequested(path: String) {
        _interaction.update { it.copy(pendingHideFolder = path) }
    }

    fun onHideFolderDismissed() {
        _interaction.update { it.copy(pendingHideFolder = null) }
    }

    fun onHideFolderConfirmed() {
        val path = _interaction.value.pendingHideFolder ?: return
        _interaction.update { it.copy(pendingHideFolder = null) }
        viewModelScope.launch {
            // 确认动作才查一次全库：隐藏一个文件夹是低频操作，不值得常驻第二条订阅
            val ids = getAllMusicUseCase(MusicSortKey.ID, SortDirection.ASC)
                .filter { fileParentOf(it.music.path) == path }
                .map { it.music.id }
            if (ids.isEmpty()) return@launch
            removeFromLibraryUseCase(ids)
            libraryMutated.onLibraryMutated()
        }
    }

    fun onUnhideFolder(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            restoreToLibraryUseCase(ids)
            libraryMutated.onLibraryMutated()
        }
    }

    // ── 目录管理（R3 恢复）─────────────────────────────────────────────
    // 扫描目录 / 屏蔽目录配置。语义按平台落地：Desktop 为文件系统扫描根；
    // Android 为 MediaStore 查询的 include/exclude 过滤；iOS 不渲染对应区块。

    fun addScanDirectory(path: String) = updateScanDirectoryConfig { current ->
        if (path.isBlank() || path in current.scanDirectories) {
            current
        } else {
            current.copy(scanDirectories = current.scanDirectories + path)
        }
    }

    fun removeScanDirectory(path: String) = updateScanDirectoryConfig { current ->
        current.copy(scanDirectories = current.scanDirectories - path)
    }

    fun addBlockedDirectory(path: String) = updateScanDirectoryConfig { current ->
        if (path.isBlank() || path in current.blockedDirectories) {
            current
        } else {
            current.copy(blockedDirectories = current.blockedDirectories + path)
        }
    }

    fun removeBlockedDirectory(path: String) = updateScanDirectoryConfig { current ->
        current.copy(blockedDirectories = current.blockedDirectories - path)
    }

    private fun updateScanDirectoryConfig(transform: (ScanDirectoryConfig) -> ScanDirectoryConfig) {
        viewModelScope.launch {
            // 从数据源读当前值（而非 StateFlow.value）：未订阅时后者仍是初始空值，
            // 直接用它做 transform 会把已有配置覆盖掉。
            val current = userSettingsUseCase.scanDirectoryConfig.first()
            val updated = transform(current)
            if (updated == current) return@launch
            userSettingsUseCase.saveScanDirectoryConfig(updated)
            // 目录变更后立即重扫，避免「改了设置却没反应」（旧行为：等用户手动重扫）。
            // 走增量那条：D2-03 —— 用户只是屏蔽一个铃声文件夹，全量会把收藏、播放数、
            // 歌词、已富化文案一起清掉（destructive 路径）。
            scanUseCase.incremental()
        }
    }

    /** 添加目录：桌面走系统选择器（一次性效果），Android 走内置已知文件夹列表。 */
    fun onAddDirectoryRequested(target: KnownFolderPickerTarget, mode: DirectorySelectionMode) {
        when (mode) {
            DirectorySelectionMode.ARBITRARY_PATH -> _effect.tryEmit(
                LibrarySettingsEffect.PickDirectory(target),
            )

            DirectorySelectionMode.KNOWN_FOLDERS -> _interaction.update { it.copy(knownFolderPicker = target) }

            DirectorySelectionMode.UNSUPPORTED -> Unit
        }
    }

    fun onKnownFolderDismissed() {
        _interaction.update { it.copy(knownFolderPicker = null) }
    }

    fun onDirectoryPicked(target: KnownFolderPickerTarget, path: String) {
        _interaction.update { it.copy(knownFolderPicker = null) }
        when (target) {
            KnownFolderPickerTarget.SCAN -> addScanDirectory(path)
            KnownFolderPickerTarget.BLOCKED -> addBlockedDirectory(path)
        }
    }
}
