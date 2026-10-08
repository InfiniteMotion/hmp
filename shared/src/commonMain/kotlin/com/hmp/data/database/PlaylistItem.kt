package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "playlist_item",
    primaryKeys = ["songId", "playlistId"],
    foreignKeys = [
        ForeignKey(
        entity = Playlist::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["playlistId"]),
        // v10（D3-03，决策 1）：同一歌单内 itemOrder 唯一 —— 此前并发 addToPlaylist 会读到
        // 同一个 MAX(itemOrder) 写出重复序号，`ORDER BY itemOrder` 的相对顺序退化成 SQLite 自选。
        Index(value = ["playlistId", "itemOrder"], unique = true),
    ]
)

/**
 * 歌单条目。
 *
 * v10（D3-15）删掉了 `songUrl` 列：它是插入那一刻的路径快照，所有读路径都是
 * `INNER JOIN music` 取 `music.path`，全仓零消费；曲目改名/换设备/跨端恢复后它会与真实路径
 * 不一致，却仍然在场，容易误导后来人"可以按它直接播"。
 */
data class PlaylistItem(
    val songId: Long,
    val playlistId: Long,
    val itemOrder: Int = 0
)

@Dao
interface PlaylistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(items: List<PlaylistItem>)

    @Transaction
    @Query("""
        SELECT music.* FROM music
        INNER JOIN playlist_item ON music.id = playlist_item.songId
        WHERE playlist_item.playlistId = :playlistId AND music.isDeleted = 0
        ORDER BY playlist_item.itemOrder ASC
    """)
    fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>>

    @Transaction
    @Query("""
    SELECT music.* FROM music
    INNER JOIN playlist_item ON music.id = playlist_item.songId
    WHERE playlist_item.playlistId = :playlistId AND music.isDeleted = 0
    ORDER BY playlist_item.itemOrder ASC
""")
    suspend fun getPlaylistById(playlistId: Long): List<MusicInfo>

    @Query("SELECT MAX(itemOrder) FROM playlist_item WHERE playlistId = :playlistId")
    suspend fun getMaxOrder(playlistId: Long): Int?

    @Query("DELETE FROM playlist_item WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItem(playlistId: Long)

    @Query("DELETE FROM playlist_item WHERE songId = :musicId AND playlistId = :playlistId")
    suspend fun deleteItemByIds(musicId: Long, playlistId: Long)

    @Query("UPDATE playlist_item SET itemOrder = :itemOrder WHERE songId = :songId AND playlistId = :playlistId")
    suspend fun updateItemOrder(playlistId: Long, songId: Long, itemOrder: Int)

    /** 读当前顺序（v10 起 `itemOrder` 唯一，逐条 UPDATE 会中途撞号，所以重排要整体做）。 */
    @Query("SELECT songId FROM playlist_item WHERE playlistId = :playlistId ORDER BY itemOrder ASC, songId ASC")
    suspend fun getSongIdsInOrder(playlistId: Long): List<Long>

    // 先全部挪到负数区间（0→-1、1→-2，双射，彼此不撞），再落最终值。
    @Query("UPDATE playlist_item SET itemOrder = -(itemOrder + 1) WHERE playlistId = :playlistId")
    suspend fun shiftOrdersToNegative(playlistId: Long)

    /**
     * 一次性重排歌单顺序（v10 唯一索引下唯一安全的写法）。
     *
     * 为什么必须有这条：`UNIQUE(playlistId, itemOrder)` 建起来之后，旧写法
     * `orderedIds.forEachIndexed { updateItemOrder(...) }` 会在"把 B 换成 A 的旧序号"那一步
     * 当场 `SQLITE_CONSTRAINT` —— 置顶 / 重排是活的 UI 路径，等于把一个观感问题换成崩溃。
     * 两阶段（先整体取负、再落终值）保证任何中间态都不冲突。
     *
     * 未列出的条目按**原相对顺序**接在后面（不会留在负值区间）。对入参完整性的校验在 一-4（D3-04）。
     */
    @Transaction
    suspend fun replaceOrder(playlistId: Long, orderedSongIds: List<Long>) {
        val existing = getSongIdsInOrder(playlistId)
        shiftOrdersToNegative(playlistId)
        orderedSongIds.forEachIndexed { index, songId ->
            updateItemOrder(playlistId, songId, index)
        }
        val listed = orderedSongIds.toSet()
        existing.filterNot { it in listed }.forEachIndexed { offset, songId ->
            updateItemOrder(playlistId, songId, orderedSongIds.size + offset)
        }
    }

    @Transaction
    suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) {
        deletePlaylistItem(playlistId)
        val items = musicList.mapIndexed { index, musicInfo ->
            PlaylistItem(
                playlistId = playlistId,
                songId = musicInfo.music.id,
                itemOrder = index
            )
        }
        insertPlaylist(items)
    }

    @Query("SELECT * FROM playlist_item")
    suspend fun getAllPlaylistItems(): List<PlaylistItem>

    @Query("DELETE FROM playlist_item")
    suspend fun deleteAll()
}
