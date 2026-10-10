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

    /**
     * 歌单里**看得见**的曲目 id（按当前顺序）。
     *
     * 重排入参的合法性以这一份为准，而不是 `getSongIdsInOrder` 的全量：
     * UI 与 Agent 拿到的列表都过滤了软删曲目（`music.isDeleted = 0`），
     * 拿全量去比会把这些曲目当成"你没列出来"而拒绝一次正常的重排。
     */
    @Query(
        """
        SELECT pi.songId FROM playlist_item pi
        INNER JOIN music m ON m.id = pi.songId
        WHERE pi.playlistId = :playlistId AND m.isDeleted = 0
        ORDER BY pi.itemOrder ASC
        """
    )
    suspend fun getVisibleSongIdsInOrder(playlistId: Long): List<Long>

    /** 两阶段重排的第一步：整体挪进负数区间（0→-1、1→-2 是双射，彼此不撞），第二步再落终值。 */
    @Query("UPDATE playlist_item SET itemOrder = -(itemOrder + 1) WHERE playlistId = :playlistId")
    suspend fun shiftOrdersToNegative(playlistId: Long)

    @Query("UPDATE playlist_item SET itemOrder = :itemOrder WHERE songId = :songId AND playlistId = :playlistId")
    suspend fun updateItemOrder(playlistId: Long, songId: Long, itemOrder: Int)

    /**
     * 重算派生列（`songCount` / `totalDurationMs`）：只统计未软删条目。
     *
     * 封面不在这里动 —— 它由 [fillCoverFromFirstItemIfAbsent] 单独管（D3-07）：
     * 计数与时长是纯派生值，每次都要跟着条目变；封面可能是用户手动设的，
     * 每次条目变动就覆写等于"设完封面再加一首歌就被抹掉"。
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

    /**
     * D3-07：仅在歌单**还没有封面**时回填首曲封面。
     *
     * 旧写法是无条件覆写，于是 `updatePlaylistCover` 设进去的自定义封面，
     * 会在下一次加/删/排时被首曲专辑图悄悄换掉 —— 而这条覆写是四家入口共用的
     * （`addSongAndRefresh` / `removeSongAndRefresh` / `replaceItemsAndRefresh` / `reorderAndRefresh`），
     * 所以没有任何一个调用点能让用户保住自己选的图。
     */
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
        WHERE id = :playlistId AND (coverUri IS NULL OR coverUri = '')
        """
    )
    suspend fun fillCoverFromFirstItemIfAbsent(playlistId: Long, updatedAt: Long)

    /**
     * D3-08：**曲目可见性变了**（软删 / 恢复）之后重算受影响歌单的派生列。
     *
     * 为什么单独一条：歌单的 `songCount` / `totalDurationMs` 是缓存列，原先只在"条目被增删"时重算；
     * 而"曲目从曲库被移除"是软删 `music.isDeleted`，根本不碰 `playlist_item` —— 于是详情页头部
     * 继续显示"10 首 / 45 分钟"，列表实际只剩 9 首（取消软删时反向也错）。
     * 可见性判断跟着 [refreshStats] 用的同一份 `music.isDeleted`，所以这里只补触发点。
     */
    @Query(
        """
        UPDATE playlist
        SET songCount = (
                SELECT COUNT(*) FROM playlist_item pi
                INNER JOIN music m ON m.id = pi.songId
                WHERE pi.playlistId = playlist.id AND m.isDeleted = 0
            ),
            totalDurationMs = (
                SELECT COALESCE(SUM(m.duration), 0) FROM playlist_item pi
                INNER JOIN music m ON m.id = pi.songId
                WHERE pi.playlistId = playlist.id AND m.isDeleted = 0
            ),
            updatedAt = :updatedAt
        WHERE id IN (SELECT DISTINCT playlistId FROM playlist_item WHERE songId IN (:songIds))
        """
    )
    suspend fun refreshStatsForPlaylistsContaining(songIds: List<Long>, updatedAt: Long)

    /** 加曲 + 重算派生列，一个事务。 */
    @Transaction
    suspend fun addSongAndRefresh(playlistId: Long, songId: Long, updatedAt: Long) {
        insertWithNextOrder(playlistId, songId)
        refreshStats(playlistId, updatedAt)
        fillCoverFromFirstItemIfAbsent(playlistId, updatedAt)
    }

    /** 移曲 + 重算派生列，一个事务（旧写法分三条语句，中途失败会留下"条目已删但计数没改"）。 */
    @Transaction
    suspend fun removeSongAndRefresh(playlistId: Long, songId: Long, updatedAt: Long) {
        deleteItemByIds(songId, playlistId)
        refreshStats(playlistId, updatedAt)
        fillCoverFromFirstItemIfAbsent(playlistId, updatedAt)
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
        fillCoverFromFirstItemIfAbsent(playlistId, updatedAt)
    }

    /**
     * 一次性重排 + 重算，一个事务；**返回是否真的重排了**（D3-04）。
     *
     * 为什么不能逐条 `updateItemOrder`：唯一索引下"把 B 换成 A 的旧序号"那一步必撞 ——
     * 置顶 / 重排是活的 UI 路径，等于把一个观感问题换成崩溃。两阶段保证任何中间态都不冲突。
     *
     * 校验放在这里（而不是三份仓库实现里）：读"应当是什么顺序"与写顺序在同一个事务里，
     * 拒绝时**一个字都不写**，所以"部分写入"在这个形状上结构性不可能。
     * 旧行为是"没列出的曲目接在后面 + 照样报成功"，Agent 传个子集过来，
     * 用户看到的顺序就和模型宣称的重排结果不一致，而工具返回 success。
     *
     * 未列出的**软删**曲目仍按原相对顺序接在后面（它们不参与本次排列，但也不能留在负值区间）。
     */
    @Transaction
    suspend fun reorderAndRefresh(playlistId: Long, orderedSongIds: List<Long>, updatedAt: Long): Boolean {
        val visible = getVisibleSongIdsInOrder(playlistId)
        if (orderedSongIds.distinct().size != orderedSongIds.size) return false
        if (orderedSongIds.toSet() != visible.toSet()) return false

        val existing = getSongIdsInOrder(playlistId)
        shiftOrdersToNegative(playlistId)
        orderedSongIds.forEachIndexed { index, songId -> updateItemOrder(playlistId, songId, index) }
        val listed = orderedSongIds.toSet()
        existing.filterNot { it in listed }.forEachIndexed { offset, songId ->
            updateItemOrder(playlistId, songId, orderedSongIds.size + offset)
        }
        refreshStats(playlistId, updatedAt)
        fillCoverFromFirstItemIfAbsent(playlistId, updatedAt)
        return true
    }
    /**
     * 全表导出（备份用）。
     *
     * `ORDER BY playlistId, itemOrder` 是 D3-02 的一半：原先这条没有 `ORDER BY`，
     * 于是"恢复时按导出顺序重编号"编的其实是 SQLite 恰好返回的行序 —— 用户手排的顺序
     * 在导出这一步就已经不可恢复了，而它看起来一直在正常工作（小库里行序常与插入序一致）。
     */
    @Query("SELECT * FROM playlist_item ORDER BY playlistId ASC, itemOrder ASC")
    suspend fun getAllPlaylistItems(): List<PlaylistItem>

    @Query("DELETE FROM playlist_item")
    suspend fun deleteAll()
}
