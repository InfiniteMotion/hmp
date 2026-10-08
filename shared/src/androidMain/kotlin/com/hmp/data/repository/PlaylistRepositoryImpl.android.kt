package com.hmp.data.repository

import com.hmp.data.database.MusicAllDao
import com.hmp.data.database.PlaylistDao
import com.hmp.data.database.PlaylistItemDao
import com.hmp.data.mapper.toDomain
import com.hmp.data.mapper.toEntity
import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistItem
import com.hmp.domain.playlist.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val musicAllDao: MusicAllDao,
) : PlaylistRepository {

    override suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        val entity = Playlist(
            name = name,
            createdAt = now,
            updatedAt = now
        ).toEntity()
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
        playlistDao.renamePlaylist(id, newName, System.currentTimeMillis())
    }

    override suspend fun updatePlaylistCover(id: Long, coverUri: String?) {
        playlistDao.updateCover(id, coverUri, System.currentTimeMillis())
    }

    override suspend fun updatePlaylistDescription(id: Long, description: String?) {
        playlistDao.updateDescription(id, description, System.currentTimeMillis())
    }

    override suspend fun setPlaylistPinned(id: Long, isPinned: Boolean) {
        playlistDao.setPinned(id, isPinned, System.currentTimeMillis())
    }

    override suspend fun incrementPlaylistPlayCount(id: Long) {
        playlistDao.incrementPlayCount(id)
    }

    override suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long) {
        playlistDao.setLastPlayedAt(id, timestamp)
    }


    override suspend fun addToPlaylist(playlistId: Long, musicId: Long, musicPath: String) {
        // v10 删了 songUrl，musicPath 已是死参数；签名清理与入参完整性校验（改返回类型）归 一-4
        playlistItemDao.addSongAndRefresh(playlistId, musicId, System.currentTimeMillis())
    }

    override suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) {
        playlistItemDao.removeSongAndRefresh(playlistId, musicId, System.currentTimeMillis())
    }

    override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) {
        playlistItemDao.replaceItemsAndRefresh(playlistId, musicList.map { it.toEntity() }, System.currentTimeMillis())
    }

    override suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>) {
        playlistItemDao.reorderAndRefresh(playlistId, orderedMusicIds, System.currentTimeMillis())
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
            PlaylistItem(
                                songId = it.songId,
                playlistId = it.playlistId
            )
        }
        return PlaylistsSnapshot(
            playlists = playlists,
            playlistItems = items
        )
    }

    override suspend fun restoreFromSnapshot(snapshot: PlaylistsSnapshot) {
        playlistDao.deleteAll()
        playlistItemDao.deleteAll()
        val playlists = snapshot.playlists.map { it.toEntity() }
        playlistDao.insertAll(playlists)

        val groupedItems = snapshot.playlistItems.groupBy { it.playlistId }
        val finalItems = groupedItems.flatMap { (_, list) ->
            list.mapIndexed { index, it ->
                com.hmp.data.database.PlaylistItem(
                                        songId = it.songId,
                    playlistId = it.playlistId,
                    itemOrder = index
                )
            }
        }

        playlistItemDao.insertPlaylist(finalItems)
    }
}
