package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "music")
data class Music(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumArtUri: String,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "musicExtra")
data class MusicExtra(
    @PrimaryKey val id: Long,
    val lyrics: String? = null,
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val fileSize: Long? = null,
    val format: String? = null,
    val language: String? = null,
    val date: Long? = null,
    val recommendationIds: String? = null,
    val isGetExtraInfo : Boolean,
    val rewards : String? = null,
    val popLyric : String? = null,
    val singerIntroduce : String? = null,
    val backgroundIntroduce : String? = null,
    val description : String? = null,
    val relevantMusic : String? = null,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "userInfo")
data class UserInfo(
    @PrimaryKey val id: Long,
    val liked: Boolean = false,
    val disLiked: Boolean = false,
    val lastPlayed: Long? = null,
    val playCount: Int? = null,
    val skippedCount: Int? = null,
    val userRating: Int? = null,
    val inCustomPlaylistCount: Int? = null,
    val isDeleted: Boolean = false,
)

data class MusicIdPath(val id: Long, val path: String)

data class MusicExtraIdDate(val id: Long, val date: Long?)

/** FORGOTTEN 卡候选：曲目 ID + 上次播放时间（null=从未播放） */
data class ForgottenRow(val id: Long, val lastPlayed: Long?)

data class MusicInfo(
    @Embedded val music: Music,

    @Relation(
        parentColumn = "id",
        entityColumn = "id"
    )
    val extra: MusicExtra?,

    @Relation(
        parentColumn = "id",
        entityColumn = "id"
    )
    val userInfo: UserInfo?
)

@Dao
interface MusicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(musics: List<Music>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(music: Music)

    @Query("SELECT * FROM music WHERE id = :id AND isDeleted = 0")
    fun getMusicById(id: Long): Flow<Music?>

    @Query("SELECT COUNT(*) FROM music WHERE isDeleted = 0")
    fun getMusicCount(): Flow<Int>

    @Query("SELECT id FROM music WHERE isDeleted = 0")
    suspend fun getAllActiveIds(): List<Long>

    @Query("UPDATE music SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun markDeletedByIds(ids: List<Long>)

    @Query("UPDATE music SET isDeleted = 0 WHERE id IN (:ids)")
    suspend fun markActiveByIds(ids: List<Long>)

    @Query("DELETE FROM music WHERE id IN (:ids)")
    suspend fun deleteMusicByIds(ids: List<Long>)

    @Query("DELETE FROM music")
    suspend fun deleteAll()

    @Query("SELECT id, path FROM music WHERE isDeleted = 1")
    suspend fun getDeletedMusicIdAndPath(): List<MusicIdPath>

    @Query("UPDATE music SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateMusicTags(id: Long, title: String, artist: String, album: String)
}

@Dao
interface MusicExtraDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(extras: List<MusicExtra>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(extra: MusicExtra)

    @Query("SELECT * FROM musicExtra WHERE id = :id")
    suspend fun getExtraById(id: Long): MusicExtra?

    @Query("SELECT lyrics FROM musicExtra WHERE id = :id")
    suspend fun getLyricsById(id: Long): String?

    @Query("DELETE FROM musicExtra WHERE id IN (:ids)")
    suspend fun deleteExtraByIds(ids: List<Long>)

    @Query("DELETE FROM musicExtra")
    suspend fun deleteAll()

    @Query("SELECT id, date FROM musicExtra")
    suspend fun getAllIdAndDate(): List<MusicExtraIdDate>

    @Query("SELECT * FROM musicExtra WHERE id=:id")
    suspend fun getExtraFieldsById(id: Long): MusicExtra?

    @Query("SELECT COUNT(*) FROM musicExtra WHERE isGetExtraInfo = true")
    fun getExtraInfoNum(): Flow<Int>

    @Query("SELECT id FROM musicExtra WHERE isGetExtraInfo = 1 AND isDeleted = 0")
    suspend fun getIdsWithExtraInfo(): List<Long>

    @Query("""
        UPDATE musicExtra SET
            isGetExtraInfo = true,
            rewards = :rewards,
            popLyric = :popLyric,
            singerIntroduce = :singerIntroduce,
            backgroundIntroduce = :backgroundIntroduce,
            description = :description,
            relevantMusic = :relevantMusic
        WHERE id = :id
    """)
    suspend fun updateExtraFieldsById(
        id: Long,
        rewards: String?,
        popLyric: String?,
        singerIntroduce: String?,
        backgroundIntroduce: String?,
        description: String?,
        relevantMusic: String?
    )

    @Query("""
        SELECT COUNT(*) FROM musicExtra
        WHERE isGetExtraInfo = false AND isDeleted = 0
    """)
    fun getMusicWithoutExtraCount(): Flow<Int>

    @Query("SELECT id FROM musicExtra WHERE isDeleted = 0")
    suspend fun getAllActiveIds(): List<Long>

    @Query("UPDATE musicExtra SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun markDeletedByIds(ids: List<Long>)

    @Query("UPDATE musicExtra SET isDeleted = 0 WHERE id IN (:ids)")
    suspend fun markActiveByIds(ids: List<Long>)

    @Query("SELECT * FROM musicExtra")
    suspend fun getAllExtras(): List<MusicExtra>
}

@Dao
interface UserInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(userInfos: List<UserInfo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userInfo: UserInfo)

    @Query("UPDATE userInfo SET liked = :liked WHERE id = :id")
    suspend fun updateLikedStatus(id: Long, liked: Boolean)

    @Query("UPDATE userInfo SET playCount = COALESCE(playCount, 0) + 1 WHERE id = :id")
    suspend fun incrementPlayCountOnly(id: Long): Int

    @Query("UPDATE userInfo SET skippedCount = COALESCE(skippedCount, 0) + 1 WHERE id = :id")
    suspend fun incrementSkippedCountOnly(id: Long): Int

    @Transaction
    suspend fun incrementPlayCount(id: Long) {
        val rowsAffected = incrementPlayCountOnly(id)
        if (rowsAffected == 0) {
            insert(UserInfo(id = id, playCount = 1))
        }
    }

    @Transaction
    suspend fun incrementSkippedCount(id: Long) {
        val rowsAffected = incrementSkippedCountOnly(id)
        if (rowsAffected == 0) {
            insert(UserInfo(id = id, skippedCount = 1))
        }
    }

    @Query("UPDATE userInfo SET lastPlayed = :timestamp WHERE id = :id")
    suspend fun updateLastPlayedOnly(id: Long, timestamp: Long): Int

    @Transaction
    suspend fun updateLastPlayed(id: Long, timestamp: Long) {
        val rowsAffected = updateLastPlayedOnly(id, timestamp)
        if (rowsAffected == 0) {
            insert(UserInfo(id = id, lastPlayed = timestamp))
        }
    }

    @Query("SELECT * FROM userInfo WHERE id = :id")
    suspend fun getUserInfoById(id: Long): UserInfo?

    @Query("SELECT liked FROM userInfo WHERE id = :id")
    suspend fun getLikedStatus(id: Long): Boolean

    @Query("DELETE FROM userInfo WHERE id IN (:ids)")
    suspend fun deleteUserInfoByIds(ids: List<Long>)

    @Query("DELETE FROM userInfo")
    suspend fun deleteAll()

    @Query("SELECT id FROM userInfo WHERE isDeleted = 0")
    suspend fun getAllActiveIds(): List<Long>

    @Query("UPDATE userInfo SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun markDeletedByIds(ids: List<Long>)

    @Query("UPDATE userInfo SET isDeleted = 0 WHERE id IN (:ids)")
    suspend fun markActiveByIds(ids: List<Long>)

    @Query("SELECT * FROM userInfo")
    suspend fun getAllUserInfos(): List<UserInfo>

    /** days 天内未播放的曲目 (id, lastPlayedMs)。lastPlayed 为 null 表示从未播放过。按最久未播排序。 */
    @Query("""
        SELECT id, lastPlayed FROM userInfo
        WHERE isDeleted = 0 AND (lastPlayed IS NULL OR lastPlayed < :thresholdMs)
        ORDER BY lastPlayed ASC
        LIMIT :limit
    """)
    suspend fun getForgottenIds(thresholdMs: Long, limit: Int): List<ForgottenRow>
}

@Dao
interface MusicAllDao {

    @Transaction
    @Query("SELECT * FROM music WHERE id = :id AND isDeleted = 0")
    fun getMusicInfoById(id: Long): Flow<MusicInfo?>

    @Transaction
    @Query("SELECT * FROM music WHERE isDeleted = 0 AND id IN (SELECT id FROM musicExtra WHERE isGetExtraInfo = false AND isDeleted = 0)")
    fun getMusicInfoWithMissingExtra(): Flow<List<MusicInfo>>

    @Query("SELECT COUNT(*) FROM musicExtra WHERE isGetExtraInfo = 0")
    fun getMusicWithMissingExtraCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM music WHERE isDeleted = 0 AND (title LIKE :query OR artist LIKE :query)")
    suspend fun searchMusic(query: String): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music WHERE isDeleted = 0 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomMusicInfo(): MusicInfo?

    @Transaction
    @Query("""
        SELECT * FROM music
        WHERE isDeleted = 0 AND id IN (
            SELECT id FROM musicExtra WHERE isGetExtraInfo = 0 AND isDeleted = 0
        )
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomMusicInfoWithMissingExtra(): MusicInfo?

    @Transaction
    @Query("""
        SELECT * FROM music
        WHERE isDeleted = 0 AND id IN (
            SELECT id FROM musicExtra WHERE isGetExtraInfo = 1 AND isDeleted = 0
        )
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomMusicInfoWithExtra(): MusicInfo?

    @Transaction
    @Query("SELECT * FROM music WHERE isDeleted = 0 AND id IN (:ids)")
    suspend fun getPlaylistByIdList(ids: List<Long>): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music ORDER BY title ASC")
    fun getAllMusicInfoSortedByTitle(): Flow<List<MusicInfo>>

    @Transaction
    @Query("SELECT * FROM music ORDER BY id ASC")
    fun getAllMusicInfoSortedById(): Flow<List<MusicInfo>>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.isDeleted = 0 ORDER BY music.id ASC")
    suspend fun getAllMusicInfoAsListById(): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.isDeleted = 0 ORDER BY music.title ASC")
    suspend fun getAllMusicInfoAsListByTitle(): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.isDeleted = 0 ORDER BY music.artist ASC")
    suspend fun getAllMusicInfoAsListByArtist(): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.isDeleted = 0 ORDER BY music.album ASC")
    suspend fun getAllMusicInfoAsListByAlbum(): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.isDeleted = 0 ORDER BY music.duration ASC")
    suspend fun getAllMusicInfoAsListByDuration(): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.artist = :artist AND music.isDeleted = 0 ORDER BY music.id ASC")
    suspend fun getMusicInfoByArtist(artist: String): List<MusicInfo>

    @Transaction
    @Query("SELECT * FROM music LEFT JOIN musicExtra ON music.id = musicExtra.id LEFT JOIN userInfo ON music.id = userInfo.id WHERE music.album = :album AND music.isDeleted = 0 ORDER BY music.id ASC")
    suspend fun getMusicInfoByAlbum(album: String): List<MusicInfo>
}
