package com.hmp.domain.playlist.usecase

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
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
     * 删除播放列表（按名称）
     */
    suspend fun removePlaylist(name: String) {
        playlistRepository.removePlaylist(name)
    }

    /**
     * 按 ID 删除播放列表，仅允许删除用户自定义列表；系统列表（默认/红心/最近）不可删
     */
    suspend fun removePlaylistById(id: Long) {
        val currentId = settingsRepository.getCurrentPlaylistId()
        val likedId = settingsRepository.getLikedPlaylistId()
        val recentId = settingsRepository.getRecentPlaylistId()
        if (id == currentId || id == likedId || id == recentId) {
            throw IllegalArgumentException("Cannot delete system playlist")
        }
        playlistRepository.removePlaylistById(id)
    }

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