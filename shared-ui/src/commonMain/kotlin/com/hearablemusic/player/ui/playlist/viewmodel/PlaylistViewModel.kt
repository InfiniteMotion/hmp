package com.hearablemusic.player.ui.playlist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.util.LoadState
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.default_playlist
import com.hearablemusic.player.ui.generated.resources.heart
import com.hearablemusic.player.ui.generated.resources.recently_played
import com.hearablemusic.player.ui.playlist.userVisiblePlaylists
import com.hearablemusic.player.ui.startup.DefaultPlaylistGuard
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.music.usecase.MusicLabelUseCase
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.SystemPlaylists
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * 歌单域的数据 VM（7.5 拆分后只剩跨屏共享的投影与动作）。
 *
 * 归属这里的东西必须**至少被两屏消费**：
 * - 五组标签歌单名（列表页的标签卡片）；
 * - 自建歌单列表 + 系统歌单 id（列表页 / 曲库页加歌 / 详情页 / 管理页）；
 * - 「批量加歌」这一条跨屏动作（曲库页批量加入歌单）。
 *
 * 拆出去的屏级状态见 [PlaylistScreenViewModel] / [PlaylistManageViewModel]：
 * 选中歌单、弹窗、管理页五态与两屏的 CRUD 动作都在各自 entry 作用域里。
 */
class PlaylistViewModel(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val musicLabelUseCase: MusicLabelUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val genrePlaylistName = musicLabelUseCase.getLabelNamesByType(LabelCategory.GENRE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moodPlaylistName = musicLabelUseCase.getLabelNamesByType(LabelCategory.MOOD)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scenarioPlaylistName = musicLabelUseCase.getLabelNamesByType(LabelCategory.SCENARIO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val languagePlaylistName = musicLabelUseCase.getLabelNamesByType(LabelCategory.LANGUAGE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eraPlaylistName = musicLabelUseCase.getLabelNamesByType(LabelCategory.ERA)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userCustomPlaylistsState = MutableStateFlow<LoadState<List<Playlist>>>(LoadState.Idle)
    val userCustomPlaylistsState: StateFlow<LoadState<List<Playlist>>> = _userCustomPlaylistsState

    /**
     * 系统歌单 id（当前 / 心动 / 最近），供管理页按 id 排除（D3-17①）。
     *
     * 单独暴露而不是在 VM 里再算一份"管理页专用列表"：两个列表状态迟早会不同步，
     * 而管理页要的只是"同一份数据减去几个 id"，那是纯函数 [manageablePlaylists] 的事。
     */
    private val _systemPlaylistIds = MutableStateFlow<Set<Long>>(emptySet())
    val systemPlaylistIds: StateFlow<Set<Long>> = _systemPlaylistIds.asStateFlow()

    init {
        initializeDefaultPlaylists()
        // 唯一一条歌单订阅：原来每个动作后都调一次 loadUserCustomPlaylists()，
        // 而每次调用都新起一个永不结束的 collector（同一份库被订阅 N 份）。
        // 现在 Room 的 getAllPlaylistsFlow 是唯一来源，增删改自动回流。
        observePlaylists()
    }

    private fun initializeDefaultPlaylists() {
        viewModelScope.launch {
            // 判据已升级（原为 id==null 才建）：id 指向的 DB 行不存在也算失效——
            // destructive migration 清库后 DataStore 残留旧 id 会让旧判据永远不触发。
            // 逻辑收敛在 DefaultPlaylistGuard，与 AppRoot 启动自愈共用一份实现。
            DefaultPlaylistGuard(managePlaylistUseCase, settingsRepository).ensureAll(
                currentName = getString(Res.string.default_playlist),
                likedName = getString(Res.string.heart),
                recentName = getString(Res.string.recently_played),
            )
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            managePlaylistUseCase.getAllPlaylistsFlow()
                .catch {
                    _userCustomPlaylistsState.value = LoadState.Error(it.message ?: "Failed to load playlists")
                }
                .collect { all ->
                    // 口径只有一份：见 `UserPlaylistVisibility.kt`（D3-06 / D3-17①）
                    _systemPlaylistIds.value = SystemPlaylists.ids(
                        currentId = settingsRepository.getCurrentPlaylistId(),
                        likedId = settingsRepository.getLikedPlaylistId(),
                        recentId = settingsRepository.getRecentPlaylistId(),
                    )
                    val playlists = userVisiblePlaylists(all)
                    _userCustomPlaylistsState.value = if (playlists.isEmpty()) {
                        LoadState.Empty
                    } else {
                        LoadState.Success(playlists)
                    }
                }
        }
    }
}
