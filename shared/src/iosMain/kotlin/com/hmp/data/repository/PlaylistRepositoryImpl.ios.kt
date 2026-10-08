package com.hmp.data.repository

import com.hmp.data.database.MusicAllDao
import com.hmp.data.database.PlaylistDao
import com.hmp.data.database.PlaylistItem
import com.hmp.data.database.PlaylistItemDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.mapper.toDomain
import com.hmp.data.mapper.toEntity
import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistItem as PlaylistItemDomain
import com.hmp.domain.playlist.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val musicAllDao: MusicAllDao,
) : PlaylistRepository {

    private suspend fun refreshPlaylistStats(playlistId: Long) {
        val list = playlistItemDao.getPlaylistById(playlistId)
        val songCount = list.size
        val totalDurationMs = list.sumOf { it.music.duration }
        playlistDao.updateStats(playlistId, songCount, totalDurationMs, currentTimeMillis())

        val firstSongCover = list.firstOrNull()?.music?.albumArtUri
        playlistDao.updateCover(playlistId, firstSongCover, currentTimeMillis())
    }

    override suspend fun createPlaylist(name: String): Long {
        val now = currentTimeMillis()
        val entity = com.hmp.data.database.Playlist(name = name, createdAt = now, updatedAt = now)
        return playlistDao.insert(entity)
    }

    override suspend fun removePlaylist(name: String) {
        playlistDao.deletePlaylist(name = name)
    }

    override suspend fun removePlaylistById(id: Long) {
        playlistDao.deletePlaylistById(id)
    }

    override suspend fun getAllPlaylists(): List<Playlist> {
        return playlistDao.getAllPlaylists().map { it.toDomain() }
    }

    override suspend fun getPlaylistMeta(id: Long): Playlist? {
        return playlistDao.getPlaylistById(id)?.toDomain()
    }

    override suspend fun renamePlaylist(id: Long, newName: String) {
        playlistDao.renamePlaylist(id, newName, currentTimeMillis())
    }

    override suspend fun updatePlaylistCover(id: Long, coverUri: String?) {
        playlistDao.updateCover(id, coverUri, currentTimeMillis())
    }

    override suspend fun updatePlaylistDescription(id: Long, description: String?) {
        playlistDao.updateDescription(id, description, currentTimeMillis())
    }

    override suspend fun setPlaylistPinned(id: Long, isPinned: Boolean) {
        playlistDao.setPinned(id, isPinned, currentTimeMillis())
    }

    override suspend fun incrementPlaylistPlayCount(id: Long) {
        playlistDao.incrementPlayCount(id)
    }

    override suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long) {
        playlistDao.setLastPlayedAt(id, timestamp)
    }

    override suspend fun addToPlaylist(playlistId: Long, musicId: Long, musicPath: String) {
        val maxOrder = playlistItemDao.getMaxOrder(playlistId) ?: -1
        val item = PlaylistItemDomain(songId = musicId, playlistId = playlistId)
        playlistItemDao.insert(item.toEntity(itemOrder = maxOrder + 1))
        refreshPlaylistStats(playlistId)
    }

    override suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) {
        playlistItemDao.deleteItemByIds(musicId, playlistId)
        refreshPlaylistStats(playlistId)
    }

    override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) {
        playlistItemDao.resetPlaylistItems(playlistId, musicList.map { it.toEntity() })
        refreshPlaylistStats(playlistId)
    }

    override suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>) {
        // v10 起 itemOrder 唯一，逐条 UPDATE 会中途撞号 —— 交给 DAO 的两阶段重排（D3-03 的事务在 一-2/一-4）
        playlistItemDao.replaceOrder(playlistId, orderedMusicIds)
        refreshPlaylistStats(playlistId)
    }

    override fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> {
        return playlistItemDao.getMusicInfoInPlaylist(playlistId).map { list -> list.map { it.toDomain() } }
    }

    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylistsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> {
        return playlistItemDao.getPlaylistById(playlistId).map { it.toDomain() }
    }

    override suspend fun getPlaylistByIdList(playlistIdList: List<Long>): List<MusicInfo> {
        return musicAllDao.getPlaylistByIdList(playlistIdList).map { it.toDomain() }
    }

    override suspend fun exportPlaylistsSnapshot(): PlaylistsSnapshot {
        val playlists = playlistDao.getAllPlaylists().map { it.toDomain() }
        val items = playlistItemDao.getAllPlaylistItems().map {
            PlaylistItemDomain(songId = it.songId, playlistId = it.playlistId)
        }
        return PlaylistsSnapshot(playlists = playlists, playlistItems = items)
    }

    override suspend fun restoreFromSnapshot(snapshot: PlaylistsSnapshot) {
        playlistDao.deleteAll()
        playlistItemDao.deleteAll()
        playlistDao.insertAll(snapshot.playlists.map { it.toEntity() })

        val groupedItems = snapshot.playlistItems.groupBy { it.playlistId }
        val finalItems = groupedItems.flatMap { (_, list) ->
            list.mapIndexed { index, it ->
                PlaylistItem(songId = it.songId, playlistId = it.playlistId, itemOrder = index)
            }
        }
        playlistItemDao.insertPlaylist(finalItems)
    }
}
