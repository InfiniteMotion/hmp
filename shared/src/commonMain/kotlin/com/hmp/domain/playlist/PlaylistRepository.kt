package com.hmp.domain.playlist

import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    // Create/Delete Playlist
    suspend fun createPlaylist(name: String): Long
    suspend fun removePlaylist(name: String)
    suspend fun removePlaylistById(id: Long)

    // Playlist metadata and rename
    suspend fun getAllPlaylists(): List<Playlist>
    suspend fun getPlaylistMeta(id: Long): Playlist?
    suspend fun renamePlaylist(id: Long, newName: String)
    suspend fun updatePlaylistCover(id: Long, coverUri: String?)
    suspend fun updatePlaylistDescription(id: Long, description: String?)
    suspend fun setPlaylistPinned(id: Long, isPinned: Boolean)
    suspend fun incrementPlaylistPlayCount(id: Long)
    suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long)

    // Manage Items
    /**
     * 追加一首。**没有 `musicPath` 参数**（一-4 / D3-15 收尾）：v10 删掉 `playlist_item.songUrl` 之后
     * 它就是纯死参数，调用方还得为了喂它去查一次曲目路径 —— 现在条目只认 `musicId`，路径永远从 `music` 表读。
     */
    suspend fun addToPlaylist(playlistId: Long, musicId: Long)
    suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long)
    suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>)

    /**
     * 按完整目标顺序重排。返回 `false` = 入参不是"当前可见曲目"的一个完整排列（缺 / 多 / 重复），
     * 此时**什么都没改**（D3-04）。调用方必须把这个返回值当回事：UI 侧提示、Agent 侧回 failure，
     * 而不是像以前那样"传个子集也报成功"。
     */
    suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>): Boolean

    // Query
    fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>>
    suspend fun getPlaylistById(playlistId: Long): List<MusicInfo>
    suspend fun getPlaylistByIdList(playlistIdList: List<Long>): List<MusicInfo>

    // Flow-based query for reactive updates
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    // Snapshot Export/Import
    suspend fun exportPlaylistsSnapshot(): PlaylistsSnapshot
    suspend fun restoreFromSnapshot(snapshot: PlaylistsSnapshot)
}