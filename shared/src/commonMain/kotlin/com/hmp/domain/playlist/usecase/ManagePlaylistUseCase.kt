package com.hmp.domain.playlist.usecase

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.playlist.SystemPlaylists
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * 播放列表管理Use Case
 */
class ManagePlaylistUseCase(
    
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * 创建播放列表
     */
    suspend fun createPlaylist(name: String): Long {
        return playlistRepository.createPlaylist(name)
    }

    /**
     * 按名查歌单（没有则 null）。
     *
     * 这里**没有** `removePlaylist(name)`：按名删除绕过下面的系统歌单保护，
     * 而 v10 之后名字有唯一索引，"按名"与"按 id"已是等价的寻址方式，只留安全的那一条（D3-05②）。
     */
    suspend fun findPlaylistByName(name: String): Playlist? =
        playlistRepository.getPlaylistByName(name)

    /**
     * 按 ID 删除播放列表；系统列表（默认 / 红心 / 最近）不可删。
     *
     * 被拒时返回 `false` 而**不再抛异常**（D3-17③）：原先的 `IllegalArgumentException`
     * 一路穿到 `viewModelScope.launch` 外面，而全仓没有协程异常兜底 ——
     * 用户在管理页点一下"删除心动"就是一次崩溃。保护本身是对的，表达方式错了。
     */
    suspend fun removePlaylistById(id: Long): Boolean {
        if (isSystemPlaylist(id)) return false
        playlistRepository.removePlaylistById(id)
        return true
    }

    /** 是不是系统歌单。UI 用它隐藏删除 / 重命名入口，Agent 用它回 failure。 */
    suspend fun isSystemPlaylist(id: Long): Boolean = SystemPlaylists.isSystem(
        playlistId = id,
        currentId = settingsRepository.getCurrentPlaylistId(),
        likedId = settingsRepository.getLikedPlaylistId(),
        recentId = settingsRepository.getRecentPlaylistId(),
    )

    /**
     * 获取所有播放列表
     */
    suspend fun getAllPlaylists(): List<Playlist> {
        return playlistRepository.getAllPlaylists()
    }

    /**
     * 获取所有播放列表（Flow版本，用于响应式更新）
     */
    fun getAllPlaylistsFlow(): Flow<List<Playlist>> {
        return playlistRepository.getAllPlaylistsFlow()
    }

    /**
     * 根据 ID 获取播放列表元数据（名称等）
     */
    suspend fun getPlaylistMeta(id: Long): Playlist? {
        return playlistRepository.getPlaylistMeta(id)
    }

    /**
     * 重命名播放列表
     */
    suspend fun renamePlaylist(id: Long, newName: String) {
        playlistRepository.renamePlaylist(id, newName)
    }

    suspend fun updatePlaylistCover(id: Long, coverUri: String?) {
        playlistRepository.updatePlaylistCover(id, coverUri)
    }

    suspend fun updatePlaylistDescription(id: Long, description: String?) {
        playlistRepository.updatePlaylistDescription(id, description)
    }

    suspend fun setPlaylistPinned(id: Long, isPinned: Boolean) {
        playlistRepository.setPlaylistPinned(id, isPinned)
    }

    suspend fun incrementPlaylistPlayCount(id: Long) {
        playlistRepository.incrementPlaylistPlayCount(id)
    }

    suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long) {
        playlistRepository.setPlaylistLastPlayedAt(id, timestamp)
    }

    /**
     * 根据ID获取播放列表中的歌曲列表
     */
    suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> {
        return playlistRepository.getPlaylistById(playlistId)
    }

    /**
     * 获取播放列表中的音乐(Flow)
     */
    fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> {
        return playlistRepository.getMusicInfoInPlaylist(playlistId)
    }

    /**
     * 添加音乐到播放列表
     */
    suspend fun addToPlaylist(playlistId: Long, musicId: Long) {
        playlistRepository.addToPlaylist(playlistId, musicId)
    }

    /**
     * 从播放列表中移除音乐
     */
    suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) {
        playlistRepository.removeItemFromPlaylist(musicId, playlistId)
    }

    /**
     * 重置播放列表内容
     */
    suspend fun resetPlaylistItems(playlistId: Long, playlist: List<MusicInfo>) {
        playlistRepository.resetPlaylistItems(playlistId, playlist)
    }

    /**
     * 调整播放列表内歌曲顺序
     */
    /** `false` = 入参不是当前可见曲目的完整排列，什么都没改（D3-04）。 */
    suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>): Boolean =
        playlistRepository.reorderPlaylistItems(playlistId, orderedMusicIds)
}