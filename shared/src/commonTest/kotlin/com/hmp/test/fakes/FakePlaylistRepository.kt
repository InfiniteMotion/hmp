package com.hmp.test.fakes

import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow

class FakePlaylistRepository : PlaylistRepository {

    private val playlists = mutableListOf<Playlist>()
    private val playlistItems = mutableMapOf<Long, MutableList<MusicInfo>>()
    private var nextId = 1L
    private val _playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())

    private fun updateFlow() {
        _playlistsFlow.value = playlists.toList()
    }

    override suspend fun createPlaylist(name: String): Long {
        val id = nextId++
        playlists.add(Playlist(id = id, name = name))
        playlistItems[id] = mutableListOf()
        updateFlow()
        return id
    }

    override suspend fun getPlaylistByName(name: String): Playlist? = playlists.find { it.name == name }

    override suspend fun removePlaylistById(id: Long) {
        playlists.removeAll { it.id == id }
        playlistItems.remove(id)
        updateFlow()
    }

    override suspend fun getAllPlaylists(): List<Playlist> = playlists.toList()

    override suspend fun getPlaylistMeta(id: Long): Playlist? =
        playlists.find { it.id == id }

    override suspend fun renamePlaylist(id: Long, newName: String) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(name = newName)
            updateFlow()
        }
    }

    override suspend fun updatePlaylistCover(id: Long, coverUri: String?) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(coverUri = coverUri)
            updateFlow()
        }
    }

    override suspend fun updatePlaylistDescription(id: Long, description: String?) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(description = description)
            updateFlow()
        }
    }

    override suspend fun setPlaylistPinned(id: Long, isPinned: Boolean) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(isPinned = isPinned)
            updateFlow()
        }
    }

    override suspend fun incrementPlaylistPlayCount(id: Long) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(playCount = playlists[index].playCount + 1)
            updateFlow()
        }
    }

    override suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(lastPlayedAt = timestamp)
            updateFlow()
        }
    }

    override suspend fun addToPlaylist(playlistId: Long, musicId: Long) {
        val items = playlistItems.getOrPut(playlistId) { mutableListOf() }
        // Create a minimal MusicInfo for storage
        items.add(
            MusicInfo(
                music = com.hmp.domain.music.Music(
                    id = musicId, title = "", artist = "", album = "",
                    duration = 0L, path = "/$musicId.mp3", albumArtUri = ""
                ),
                extra = null, userInfo = null
            )
        )
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(songCount = items.size)
            updateFlow()
        }
    }

    override suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) {
        playlistItems[playlistId]?.removeAll { it.music.id == musicId }
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val count = playlistItems[playlistId]?.size ?: 0
            playlists[index] = playlists[index].copy(songCount = count)
            updateFlow()
        }
    }

    override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) {
        playlistItems[playlistId] = musicList.toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(songCount = musicList.size)
            updateFlow()
        }
    }

    /**
     * 与真仓库同一条契约（D3-04）：入参必须是当前内容的完整排列，
     * 缺 / 多 / 重复都返回 false 且**一个都不改**。以前这里是"照单全收 + 静默丢掉对不上的 id"，
     * 于是仓库层的校验在替身里根本不存在，相关用例等于没测。
     */
    override suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>): Boolean {
        val items = playlistItems[playlistId] ?: return orderedMusicIds.isEmpty()
        if (orderedMusicIds.distinct().size != orderedMusicIds.size) return false
        if (orderedMusicIds.toSet() != items.map { it.music.id }.toSet()) return false
        playlistItems[playlistId] = orderedMusicIds.mapNotNull { id -> items.find { it.music.id == id } }.toMutableList()
        return true
    }

    override fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> {
        return _playlistsFlow.map { playlistItems[playlistId]?.toList() ?: emptyList() }
    }

    override suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> =
        playlistItems[playlistId]?.toList() ?: emptyList()

    override suspend fun getPlaylistByIdList(playlistIdList: List<Long>): List<MusicInfo> =
        playlistIdList.flatMap { playlistItems[it] ?: emptyList() }

    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = _playlistsFlow.asStateFlow()

    override suspend fun exportPlaylistsSnapshot(): PlaylistsSnapshot =
        PlaylistsSnapshot(playlists = playlists.toList(), playlistItems = emptyList())

    /** 供 D7-02 / R39 的判据断言：让恢复的最后一步按指定异常失败。 */
    var failRestoreWith: Throwable? = null

    override suspend fun restoreFromSnapshot(snapshot: PlaylistsSnapshot) {
        failRestoreWith?.let { throw it }
        playlists.clear()
        playlists.addAll(snapshot.playlists)
        playlistItems.clear()
        nextId = (playlists.maxOfOrNull { it.id } ?: 0L) + 1
        updateFlow()
    }
}
