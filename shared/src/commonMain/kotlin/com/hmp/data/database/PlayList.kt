package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Entity(
    tableName = "playlist",
    // v10（D3-05，决策 2）：歌单名唯一性改由数据库保证 —— 此前只在创建弹窗里查一次，
    // Agent 的 playlist_create / playlist_rename 与跨端恢复都能造出同名行，
    // 而 DefaultPlaylistGuard 的按名删除会把同名行一起删掉。
    // ⚠️ 行为变化：`@Insert`（ABORT）撞唯一索引会抛，配套改动在 一-4（按 id 删除 + 校验下沉）。
    indices = [Index(value = ["name"], unique = true)],
)
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverUri: String? = null,
    val playCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastPlayedAt: Long? = null,
    val description: String? = null,
    val songCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val isPinned: Boolean = false
)
@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: Playlist): Long

    /**
     * 按名查一行（系统歌单自愈要"接管同名行"，见 `DefaultPlaylistGuard`）。
     *
     * 取代原先的 `deletePlaylist(name)`：**按名删除这条路径整条删掉**（D3-05②③）——
     * 它绕过 `removePlaylistById` 的系统歌单保护，而自愈恰恰在用它的地方
     * "把同名行删了再建一条空的"，等于用户的心动/最近播放在一次悬空 id 之后被清空。
     * `name` 自 v10 起有唯一索引，所以这一行是确定的。
     */
    @Query("SELECT * FROM playlist WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): Playlist?

    @Query("DELETE FROM playlist")
    suspend fun deleteAll()

    @Query("SELECT * FROM playlist")
    suspend fun getAllPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("UPDATE playlist SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renamePlaylist(id: Long, newName: String, updatedAt: Long)

    @Query("UPDATE playlist SET coverUri = :coverUri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCover(id: Long, coverUri: String?, updatedAt: Long)

    @Query("UPDATE playlist SET description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String?, updatedAt: Long)

    @Query("UPDATE playlist SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE playlist SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    @Query("UPDATE playlist SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun setLastPlayedAt(id: Long, timestamp: Long)

    @Query("UPDATE playlist SET songCount = :songCount, totalDurationMs = :totalDurationMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStats(id: Long, songCount: Int, totalDurationMs: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<Playlist>)

    @Query("SELECT * FROM playlist ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    /** 歌单创建纪念日候选：createdAt 的月-日匹配今天，按 createdAt 升序（最久在前）。 */
    @Query("""
        SELECT * FROM playlist
        WHERE strftime('%m-%d', createdAt / 1000, 'unixepoch', 'localtime') = :mmdd
        ORDER BY createdAt ASC
    """)
    suspend fun getAnniversaryPlaylists(mmdd: String): List<Playlist>

    /** 某歌单累计被播放次数（PlaybackHistory.source == 歌单名）。 */
    @Query("SELECT COUNT(*) FROM PlaybackHistory WHERE source = :playlistName")
    suspend fun countPlaybackForPlaylist(playlistName: String): Int
}
