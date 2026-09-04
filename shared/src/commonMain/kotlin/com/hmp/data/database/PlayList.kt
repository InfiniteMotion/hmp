package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Entity(tableName = "playlist")
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

    @Query("DELETE FROM playlist WHERE name = :name")
    suspend fun deletePlaylist(name: String)

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
