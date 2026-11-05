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
import com.hmp.domain.playlist.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 歌单仓库 —— **单一实现，三端共用**（D3-14 / 一-8，2026-11-05 收口）。
 *
 * 它曾在 androidMain / desktopMain / iosMain 各放一份逐字镜像（约 120 行 ×3），
 * 分叉只有两处**风格**而非语义：
 * - 时间源：Android 写 `System.currentTimeMillis()`，另两端写 commonMain 的 `currentTimeMillis()`
 *   （expect/actual，Android 的 actual 就是同一句 System 调用，见 `PlatformTime.android.kt`）；
 * - `createPlaylist` 建实体：Android 走 `Playlist(...).toEntity()`，另两端直接 new 实体 —— 产物逐字段相同。
 *
 * 这种"镜像 + 伪分叉"的维护成本是真实的：D2-04 那次改 `resetPlaylistItems`，同一处改动要在
 * 三个文件里各做一遍，漏掉任何一份就是三端行为分叉。时间源既已收进 `currentTimeMillis()`，
 * 这里不再存在任何平台差异，实现整体搬进 commonMain，三个平台文件删除。
 * 与 `MusicRepositoryBase` 的区别：后者仍有真实平台代码（扫描/标签/存储），所以是"基类 + 平台子类"；
 * 本类没有，就不该有平台文件。
 */
class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val musicAllDao: MusicAllDao,
) : PlaylistRepository {

    override suspend fun createPlaylist(name: String): Long {
        val now = currentTimeMillis()
        val entity = Playlist(name = name, createdAt = now, updatedAt = now).toEntity()
        return playlistDao.insert(entity)
    }

    override suspend fun getPlaylistByName(name: String): Playlist? = playlistDao.getPlaylistByName(name)?.toDomain()

    override suspend fun removePlaylistById(id: Long) {
        playlistDao.deletePlaylistById(id)
    }

    override suspend fun getAllPlaylists(): List<Playlist> = playlistDao.getAllPlaylists().map { it.toDomain() }

    override suspend fun getPlaylistMeta(id: Long): Playlist? = playlistDao.getPlaylistById(id)?.toDomain()

    override suspend fun renamePlaylist(id: Long, newName: String) {
        playlistDao.renamePlaylist(id, newName, currentTimeMillis())
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

    override suspend fun addToPlaylist(playlistId: Long, musicId: Long) {
        playlistItemDao.addSongAndRefresh(playlistId, musicId, currentTimeMillis())
    }

    override suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) {
        playlistItemDao.removeSongAndRefresh(playlistId, musicId, currentTimeMillis())
    }

    override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) {
        // 只传 id：条目写入不需要实体图（D2-04）。构造实体只为取 `.music.id`
        // 会把"实体恒 isDeleted=false"这个值一路搬到 DAO 门口，纯属给复活留门。
        playlistItemDao.replaceItemsAndRefresh(playlistId, musicList.map { it.music.id }, currentTimeMillis())
    }

    override suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>): Boolean = playlistItemDao.reorderAndRefresh(playlistId, orderedMusicIds, currentTimeMillis())

    override fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> = playlistItemDao.getMusicInfoInPlaylist(playlistId).map { list -> list.map { it.toDomain() } }

    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = playlistDao.getAllPlaylistsFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> = playlistItemDao.getPlaylistById(playlistId).map { it.toDomain() }

    override suspend fun getPlaylistByIdList(playlistIdList: List<Long>): List<MusicInfo> = musicAllDao.getPlaylistByIdList(playlistIdList).map { it.toDomain() }

    override suspend fun exportPlaylistsSnapshot(): PlaylistsSnapshot {
        val playlists = playlistDao.getAllPlaylists().map { it.toDomain() }
        // toDomain 现在带上 itemOrder（D3-02）—— 顺序是这次快照要保的东西
        val items = playlistItemDao.getAllPlaylistItems().map { it.toDomain() }
        return PlaylistsSnapshot(playlists = playlists, playlistItems = items)
    }

    override suspend fun restoreFromSnapshot(snapshot: PlaylistsSnapshot) {
        playlistDao.deleteAll()
        playlistItemDao.deleteAll()
        playlistDao.insertAll(snapshot.playlists.map { it.toEntity() })

        // D3-02：按 itemOrder 排好后逐歌单重编为连续序号。
        // 刻意不"原样写回快照里的 itemOrder"：旧快照没有这个字段（全是默认 0），
        // 原样写回会让第二首起就撞 v10 的 UNIQUE(playlistId,itemOrder)（D3-03 建的）。
        // 重编号保持相对顺序，所以对"用户排出来的顺序"是无损的。
        val finalItems = snapshot.playlistItems
            .groupBy { it.playlistId }
            .flatMap { (playlistId, list) ->
                list.sortedBy { it.itemOrder }.mapIndexed { index, item ->
                    PlaylistItem(songId = item.songId, playlistId = playlistId, itemOrder = index)
                }
            }
        playlistItemDao.insertPlaylist(finalItems)
    }
}
