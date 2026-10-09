package com.hmp.data.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "music",
    // v10（D2-07）：这四列此前零索引，曲库增长时等值查询/排序全表扫。
    // ⚠️ title/artist/album 的 B-tree 索引救不了 `LIKE '%q%'` 中缀通配搜索（那属 D2-05 / FTS），
    // 它们治的是等值与排序 —— 别把"搜索变快"记在本批账上。
    indices = [
        Index("isDeleted"),
        Index("title"),
        Index("artist"),
        Index("album"),
    ],
)
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

@Entity(
    tableName = "musicExtra",
    // v10（D2-07）：富化管道按 (isGetExtraInfo, isDeleted) 找待处理曲目，此前全表扫。
    indices = [Index("isGetExtraInfo", "isDeleted")],
)
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

@Entity(
    tableName = "userInfo",
    // v10（D2-07）：收藏/播放统计的可见性过滤按 isDeleted，此前全表扫。
    indices = [Index("isDeleted")],
)
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
    /**
     * 用户主动"从曲库移除"（v10 / D2-02）。
     *
     * 为什么单开一列：`isDeleted` 有两个成因 —— 用户移除、以及"这次扫描没见到这个文件"。
     * 二者必须能被分辨，否则扫描落库时无法判断该不该把行恢复成可见：
     * 一律恢复会让"用户隐藏的歌"下次重扫全部复活（D2-02 的原件），一律不恢复则
     * 用户把文件夹挪回来也永远找不回这首歌。**扫描只读这一列，永不写它**；
     * 写在 `userInfo`（用户态表）而不是 `music`（扫描整行覆盖的表），就是为了让扫描抹不掉它。
     */
    val removedByUser: Boolean = false,
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

    /** 扫描落库用（D2-01）：判断"这条曲目库里有没有"必须含软删行，只取 active 会把隐藏的歌当新歌重写。 */
    @Query("SELECT id FROM music")
    suspend fun getAllIds(): List<Long>

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

    /**
     * 「用户主动隐藏」的曲目（D2-02）。与 [getDeletedMusicIdAndPath] 的区别就是本条的意义：
     * 后者混着"文件不在了"，前者只含用户意图，所以"已隐藏"管理页要按这一条列。
     */
    @Query(
        """
        SELECT music.id, music.path FROM music
        INNER JOIN userInfo ON music.id = userInfo.id
        WHERE music.isDeleted = 1 AND userInfo.removedByUser = 1
    """
    )
    suspend fun getRemovedMusicIdAndPath(): List<MusicIdPath>

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

    /** 扫描落库按批读回既有行（D2-01）：合并只补扫描拥有的列，富化结果与用户改过的歌词要留在原行上。 */
    @Query("SELECT * FROM musicExtra WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MusicExtra>

    @Upsert
    suspend fun upsertAll(items: List<MusicExtra>)

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

    /** G6：批量取全部已收藏（liked=1）且未删除的曲目 id */
    @Query("SELECT id FROM userInfo WHERE liked = 1 AND isDeleted = 0")
    suspend fun getLikedMusicIds(): List<Long>

    /** 画像·状态快照：明确不喜欢的数量（负向信号，比"喜欢什么"更能收紧推荐） */
    @Query("SELECT COUNT(*) FROM userInfo WHERE disLiked = 1 AND isDeleted = 0")
    suspend fun countDisliked(): Int

    /** 画像·状态快照：打过分的数量（用户显式给的分，T1 强度） */
    @Query("SELECT COUNT(*) FROM userInfo WHERE userRating IS NOT NULL AND isDeleted = 0")
    suspend fun countRated(): Int

    @Query("DELETE FROM userInfo WHERE id IN (:ids)")
    suspend fun deleteUserInfoByIds(ids: List<Long>)

    @Query("DELETE FROM userInfo")
    suspend fun deleteAll()

    @Query("SELECT id FROM userInfo WHERE isDeleted = 0")
    suspend fun getAllActiveIds(): List<Long>

    /** 扫描落库用（D2-01）：库里有没有这一行要看全部行，active 集合会把隐藏曲误判成新歌并覆盖用户数据。 */
    @Query("SELECT id FROM userInfo")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT id FROM userInfo WHERE removedByUser = 1")
    suspend fun getRemovedByUserIds(): List<Long>

    @Query("SELECT * FROM userInfo WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<UserInfo>

    @Upsert
    suspend fun upsertAll(items: List<UserInfo>)

    /**
     * 记用户移除意图（D2-02）。整行读出再改两列写回，因为 `userInfo` 的其它列都是用户资产
     * （`liked` / `playCount` / `lastPlayed`），部分列的 `@Insert(REPLACE)` 会把它们清零。
     */
    @Transaction
    suspend fun markRemovedByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        val existing = getByIds(ids).associateBy { it.id }
        upsertAll(
            ids.map {
                (existing[it] ?: UserInfo(id = it)).copy(removedByUser = true, isDeleted = true)
            }
        )
    }

    /** 用户恢复：意图位与可见位一起清（两成因里"用户移除"这一支被显式撤销）。 */
    @Transaction
    suspend fun clearRemovedByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        val existing = getByIds(ids).associateBy { it.id }
        upsertAll(
            ids.map {
                (existing[it] ?: UserInfo(id = it)).copy(removedByUser = false, isDeleted = false)
            }
        )
    }

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
