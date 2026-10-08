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
    @Query("DELETE FROM playlist_item WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItem(playlistId: Long)

    @Query("DELETE FROM playlist_item WHERE songId = :musicId AND playlistId = :playlistId")
    suspend fun deleteItemByIds(musicId: Long, playlistId: Long)

    // ── 歌单写路径的事务收口（一-2 / D3-03）──────────────────────────────────
    //
    // 为什么必须有这一组：v10 给 `playlist_item` 加了 `UNIQUE(playlistId, itemOrder)`，
    // 旧写法"读 MAX(itemOrder) → +1 → insert → 回 Kotlin 侧算统计 → 写 playlist"是四步跨语句的，
    // 并发添加会撞唯一索引当场抛（撞之前是静默同序号）。仓库只持有 DAO、拿不到数据库句柄，
    // 所以"写条目 + 重算 playlist 派生列"必须收进**同一个 @Transaction 方法**。
    // 顺带把三份逐字镜像的 `refreshPlaylistStats` 收成一条 SQL —— D3-14 的镜像复制因此少一处。
    // 派生列的重算是被条目写入触发的，所以事务待在条目 DAO 这一侧，而不是 PlaylistDao。

    /** 追加条目：序号在同一语句内取当前最大值 +1。重复添加同一首 = 移到末尾，与原 `@Insert(REPLACE)` 语义一致。 */
    @Query("""
        INSERT OR REPLACE INTO playlist_item (songId, playlistId, itemOrder)
        SELECT :songId, :playlistId, COALESCE(MAX(itemOrder), -1) + 1
        FROM playlist_item WHERE playlistId = :playlistId
    """)
    suspend fun insertWithNextOrder(playlistId: Long, songId: Long)

    @Query(
        "SELECT songId FROM playlist_item WHERE playlistId = :playlistId " +
            "ORDER BY itemOrder ASC, songId ASC"
    )
    suspend fun getSongIdsInOrder(playlistId: Long): List<Long>

    /** 两阶段重排的第一步：整体挪进负数区间（0→-1、1→-2 是双射，彼此不撞），第二步再落终值。 */
    @Query("UPDATE playlist_item SET itemOrder = -(itemOrder + 1) WHERE playlistId = :playlistId")
    suspend fun shiftOrdersToNegative(playlistId: Long)

    @Query("UPDATE playlist_item SET itemOrder = :itemOrder WHERE songId = :songId AND playlistId = :playlistId")
    suspend fun updateItemOrder(playlistId: Long, songId: Long, itemOrder: Int)

    /**
     * 重算派生列。语义与旧的 `refreshPlaylistStats` 逐字对齐：只统计未软删条目，
     * 封面**无条件**取首曲（"仅当自定义封面为空才回填"是 D3-07，改法在 一-4）。
     */
    @Query(
        """
        UPDATE playlist
        SET songCount = (
                SELECT COUNT(*) FROM playlist_item pi
                INNER JOIN music m ON m.id = pi.songId
                WHERE pi.playlistId = :playlistId AND m.isDeleted = 0
            ),
            totalDurationMs = (
                SELECT COALESCE(SUM(m.duration), 0) FROM playlist_item pi
                INNER JOIN music m ON m.id = pi.songId
                WHERE pi.playlistId = :playlistId AND m.isDeleted = 0
            ),
            updatedAt = :updatedAt
        WHERE id = :playlistId
        """
    )
    suspend fun refreshStats(playlistId: Long, updatedAt: Long)

    @Query(
        """
        UPDATE playlist
        SET coverUri = (
                SELECT m.albumArtUri FROM playlist_item pi
                INNER JOIN music m ON m.id = pi.songId
                WHERE pi.playlistId = :playlistId AND m.isDeleted = 0
                ORDER BY pi.itemOrder ASC LIMIT 1
            ),
            updatedAt = :updatedAt
        WHERE id = :playlistId
        """
    )
    suspend fun refreshCoverFromFirstItem(playlistId: Long, updatedAt: Long)

    /** 加曲 + 重算派生列，一个事务。 */
    @Transaction
    suspend fun addSongAndRefresh(playlistId: Long, songId: Long, updatedAt: Long) {
        insertWithNextOrder(playlistId, songId)
        refreshStats(playlistId, updatedAt)
        refreshCoverFromFirstItem(playlistId, updatedAt)
    }

    /** 移曲 + 重算派生列，一个事务（旧写法分三条语句，中途失败会留下"条目已删但计数没改"）。 */
    @Transaction
    suspend fun removeSongAndRefresh(playlistId: Long, songId: Long, updatedAt: Long) {
        deleteItemByIds(songId, playlistId)
        refreshStats(playlistId, updatedAt)
        refreshCoverFromFirstItem(playlistId, updatedAt)
    }

    /** 整表替换 + 重算派生列，一个事务。 */
    @Transaction
    suspend fun replaceItemsAndRefresh(playlistId: Long, musicList: List<MusicInfo>, updatedAt: Long) {
        deletePlaylistItem(playlistId)
        insertPlaylist(
            musicList.mapIndexed { index, musicInfo ->
                PlaylistItem(playlistId = playlistId, songId = musicInfo.music.id, itemOrder = index)
            }
        )
        refreshStats(playlistId, updatedAt)
        refreshCoverFromFirstItem(playlistId, updatedAt)
    }

    /**
     * 一次性重排 + 重算，一个事务。
     *
     * 为什么不能逐条 `updateItemOrder`：唯一索引下"把 B 换成 A 的旧序号"那一步必撞 ——
     * 置顶 / 重排是活的 UI 路径，等于把一个观感问题换成崩溃。两阶段保证任何中间态都不冲突。
     * 未列出的条目按原相对顺序接在后面（不会留在负值区间）；入参完整性校验要改返回类型，归 一-4（D3-04）。
     */
    @Transaction
    suspend fun reorderAndRefresh(playlistId: Long, orderedSongIds: List<Long>, updatedAt: Long) {
        val existing = getSongIdsInOrder(playlistId)
        shiftOrdersToNegative(playlistId)
        orderedSongIds.forEachIndexed { index, songId -> updateItemOrder(playlistId, songId, index) }
        val listed = orderedSongIds.toSet()
        existing.filterNot { it in listed }.forEachIndexed { offset, songId ->
            updateItemOrder(playlistId, songId, orderedSongIds.size + offset)
        }
        refreshStats(playlistId, updatedAt)
        refreshCoverFromFirstItem(playlistId, updatedAt)
    }
    @Query("SELECT * FROM playlist_item")
    suspend fun getAllPlaylistItems(): List<PlaylistItem>

    @Query("DELETE FROM playlist_item")
    suspend fun deleteAll()
}
