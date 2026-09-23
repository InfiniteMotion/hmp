package com.hearablemusic.player.ui.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.usecase.LoadMusicFromDeviceUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.music.usecase.SyncMusicFromDeviceIncrementalUseCase
import com.hmp.domain.setting.model.ScanDirectoryConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase

import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.common.util.fileParentOf
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.scan_failed
import com.hearablemusic.player.ui.generated.resources.unknown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class FolderInfo(
    val path: String,
    val songCount: Int
)

data class HiddenFolderInfo(
    val path: String,
    val songCount: Int,
    val musicIds: List<Long>
)

data class ScanResult(
    val musicList: List<MusicInfo> = emptyList(),
    val scannedFolderCount: Int = 0
)

/**
 * 音乐库 ViewModel。
 */
class LibraryViewModel(
    private val getAllMusicUseCase: GetAllMusicUseCase,
    private val loadMusicFromDeviceUseCase: LoadMusicFromDeviceUseCase,
    private val syncMusicFromDeviceIncrementalUseCase: SyncMusicFromDeviceIncrementalUseCase,
    private val removeFromLibraryUseCase: RemoveFromLibraryUseCase,
    private val restoreToLibraryUseCase: RestoreToLibraryUseCase,
    private val getDeletedMusicIdsGroupedByFolderUseCase: GetDeletedMusicIdsGroupedByFolderUseCase,
    private val userSettingsUseCase: UserSettingsUseCase,
    /**
     * 用户认识模块（画像，契约 agent-profile.md v3.6）。
     *
     * v3.6 起画像**归属 MasterAgent**（`masterAgent.userMemory`），UI 层只经 Master 触达。
     * 挂在 ViewModel 上而不是让库层反向调 agent：**"库变了"这件事只有库层知道**，
     * 而画像的重建是它的下游反应。调用点见 [refreshUserProfile]。
     */
    private val masterAgent: MasterAgent,
) : ViewModel() {

    private val _orderBy = MutableStateFlow("title")
    val orderBy: MutableStateFlow<String> = _orderBy
    fun updateOrderBy(orderBy: String) {
        _orderBy.value = orderBy
    }

    private val _orderType = MutableStateFlow("ASC")
    val orderType: StateFlow<String> = _orderType
    fun updateOrderType(orderType: String) {
        _orderType.value = orderType
    }

    private val _allMusic = MutableStateFlow<List<MusicInfo>>(emptyList())
    val allMusic: StateFlow<List<MusicInfo>> = _allMusic

    private val _scanState = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val scanState: StateFlow<UiState<ScanResult>> = _scanState

    fun getAllMusic() {
        viewModelScope.launch {
            _allMusic.value = getAllMusicUseCase(_orderBy.value, _orderType.value)
        }
    }

    fun removeFromLibrary(ids: List<Long>) {
        viewModelScope.launch {
            removeFromLibraryUseCase(ids)
            getAllMusic()
            loadHiddenFolders()
            refreshUserProfile()
        }
    }

    fun restoreToLibrary(ids: List<Long>) {
        viewModelScope.launch {
            restoreToLibraryUseCase(ids)
            getAllMusic()
            loadHiddenFolders()
            refreshUserProfile()
        }
    }

    fun hideFolder(folderPath: String) {
        viewModelScope.launch {
            val ids = _allMusic.value
                .filter { fileParentOf(it.music.path) == folderPath }
                .map { it.music.id }
            if (ids.isNotEmpty()) {
                removeFromLibraryUseCase(ids)
                getAllMusic()
                loadHiddenFolders()
                refreshUserProfile()
            }
        }
    }

    private val _hiddenFolders = MutableStateFlow<List<HiddenFolderInfo>>(emptyList())
    val hiddenFolders: StateFlow<List<HiddenFolderInfo>> = _hiddenFolders

    fun loadHiddenFolders() {
        viewModelScope.launch {
            val grouped = getDeletedMusicIdsGroupedByFolderUseCase()
            _hiddenFolders.value = grouped.map { (path, ids) ->
                HiddenFolderInfo(path = path, songCount = ids.size, musicIds = ids)
            }
        }
    }

    // ── 目录管理（R3 恢复）────────────────────────────────────────────────
    // 扫描目录 / 屏蔽目录配置。语义按平台落地：Desktop 为文件系统扫描根；
    // Android 为 MediaStore 查询的 include/exclude 过滤；iOS 不渲染对应区块。
    // 此前该配置在 UI 层无任何读写入口（桌面旧 UI 层删除时丢失），见
    // docs/7_x/A shared-ui/UI层统一-能力搬迁点检.md R3。
    val scanDirectoryConfig: StateFlow<ScanDirectoryConfig> = userSettingsUseCase.scanDirectoryConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanDirectoryConfig())

    fun addScanDirectory(path: String) = updateScanDirectoryConfig { current ->
        if (path.isBlank() || path in current.scanDirectories) current
        else current.copy(scanDirectories = current.scanDirectories + path)
    }

    fun removeScanDirectory(path: String) = updateScanDirectoryConfig { current ->
        current.copy(scanDirectories = current.scanDirectories - path)
    }

    fun addBlockedDirectory(path: String) = updateScanDirectoryConfig { current ->
        if (path.isBlank() || path in current.blockedDirectories) current
        else current.copy(blockedDirectories = current.blockedDirectories + path)
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
            // 目录变更后立即重扫，避免「改了设置却没反应」（旧行为：等用户手动重扫）
            fullRescan()
        }
    }

    val musicCount: StateFlow<Int> = getAllMusicUseCase
        .getMusicCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val musicWithExtraCount: StateFlow<Int> = getAllMusicUseCase
        .getMusicWithExtraCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isScanning = loadMusicFromDeviceUseCase.isScanning()

    val scannedFolders: StateFlow<List<FolderInfo>> = _allMusic.map { list ->
        list.groupBy { music ->
            try {
                fileParentOf(music.music.path) ?: getString(Res.string.unknown)
            } catch (e: Exception) {
                getString(Res.string.unknown)
            }
        }.map { (path, songs) ->
            FolderInfo(path, songs.size)
        }.sortedByDescending { it.songCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshMusicList() {
        viewModelScope.launch(Dispatchers.Default) {
            _scanState.value = UiState.Loading
            syncMusicFromDeviceIncrementalUseCase()
                .onSuccess {
                    val musicList = getAllMusicUseCase(_orderBy.value, _orderType.value)
                    _allMusic.value = musicList
                    val folderCount = musicList.mapNotNull { music ->
                        fileParentOf(music.music.path)
                    }.distinct().size
                    _scanState.value = UiState.Success(ScanResult(musicList, folderCount))
                    // 首次扫描完成后持久化标记，用于触发 AI 自动补全等一次性逻辑
                    userSettingsUseCase.saveIsLoadMusic(true)
                    refreshUserProfile()
                }
                .onFailure { e ->
                    _scanState.value = UiState.Error(e.message ?: getString(Res.string.scan_failed))
                }
        }
    }

    fun fullRescan() {
        viewModelScope.launch(Dispatchers.Default) {
            _scanState.value = UiState.Loading
            loadMusicFromDeviceUseCase()
                .onSuccess {
                    val musicList = getAllMusicUseCase(_orderBy.value, _orderType.value)
                    _allMusic.value = musicList
                    val folderCount = musicList.mapNotNull { music ->
                        fileParentOf(music.music.path)
                    }.distinct().size
                    _scanState.value = UiState.Success(ScanResult(musicList, folderCount))
                    // 首次扫描完成后持久化标记，用于触发 AI 自动补全等一次性逻辑
                    userSettingsUseCase.saveIsLoadMusic(true)
                    refreshUserProfile()
                }
                .onFailure { e ->
                    _scanState.value = UiState.Error(e.message ?: getString(Res.string.scan_failed))
                }
        }
    }

    /**
     * 库变了就重建画像。
     *
     * 契约 `agent-profile.md` §4.1.4：曲库建模**事件驱动**，不放进每日聚合 ——
     * 否则用户导入 3000 首之后要等到次日凌晨，期间 agent 面对的是空画像。
     *
     * 失败不影响库操作本身：[UserMemory] 内部已吞掉异常并记日志。
     */
    private fun refreshUserProfile() {
        // v3.6 收口：UI 只发「库变了」命令，画像重建的协调在 Master 内部
        //（MasterAgent.onLibraryMutated 内 launch，失败不影响库操作本身）
        masterAgent.onLibraryMutated()
    }

    init {
        getAllMusic()
    }
}