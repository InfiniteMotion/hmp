package com.hmp.data.database

import com.hmp.test.db.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var musicExtraDao: MusicExtraDao
    private lateinit var userInfoDao: UserInfoDao
    private lateinit var musicAllDao: MusicAllDao

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        musicDao = db.musicDao()
        musicExtraDao = db.musicExtraDao()
        userInfoDao = db.userInfoDao()
        musicAllDao = db.musicAllDao()
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    private fun music(id: Long, title: String = "Song$id", artist: String = "Artist$id", path: String = "/$id.mp3") =
        Music(id = id, title = title, artist = artist, album = "Album", duration = 100000L, path = path, albumArtUri = "")

    private fun extra(id: Long, hasExtra: Boolean = true) =
        MusicExtra(id = id, isGetExtraInfo = hasExtra)

    private fun userInfo(id: Long, liked: Boolean = false) =
        UserInfo(id = id, liked = liked)

    @Test
    fun insertAndGetById() = runTest {
        musicDao.insert(music(1))
        val result = musicDao.getMusicById(1).first()
        assertNotNull(result)
        assertEquals("Song1", result.title)
    }

    @Test
    fun getMusicCount_empty() = runTest {
        assertEquals(0, musicDao.getMusicCount().first())
    }

    @Test
    fun getMusicCount_afterInsert() = runTest {
        musicDao.insertAll(listOf(music(1), music(2), music(3)))
        assertEquals(3, musicDao.getMusicCount().first())
    }

    @Test
    fun getAllActiveIds_excludesDeleted() = runTest {
        musicDao.insertAll(listOf(music(1), music(2), music(3)))
        musicDao.markDeletedByIds(listOf(2))
        assertEquals(setOf(1L, 3L), musicDao.getAllActiveIds().toSet())
    }

    @Test
    fun markDeletedByIds_hidesFromGetById() = runTest {
        musicDao.insertAll(listOf(music(1), music(2)))
        musicDao.markDeletedByIds(listOf(1))
        assertNull(musicDao.getMusicById(1).first())
        assertNotNull(musicDao.getMusicById(2).first())
    }

    @Test
    fun markActiveByIds_restoresDeleted() = runTest {
        musicDao.insert(music(1))
        musicDao.markDeletedByIds(listOf(1))
        assertNull(musicDao.getMusicById(1).first())
        musicDao.markActiveByIds(listOf(1))
        assertNotNull(musicDao.getMusicById(1).first())
    }

    @Test
    fun deleteMusicByIds_removesPermanently() = runTest {
        musicDao.insertAll(listOf(music(1), music(2)))
        musicDao.deleteMusicByIds(listOf(1))
        assertNull(musicDao.getMusicById(1).first())
        assertNotNull(musicDao.getMusicById(2).first())
    }

    @Test
    fun deleteAll() = runTest {
        musicDao.insertAll(listOf(music(1), music(2), music(3)))
        musicDao.deleteAll()
        assertEquals(0, musicDao.getMusicCount().first())
    }

    @Test
    fun updateMusicTags_updatesFields() = runTest {
        musicDao.insert(music(1, title = "Old Title", artist = "Old Artist"))
        musicDao.updateMusicTags(id = 1, title = "New Title", artist = "New Artist", album = "New Album")
        val result = musicDao.getMusicById(1).first()
        assertNotNull(result)
        assertEquals("New Title", result.title)
        assertEquals("New Artist", result.artist)
        assertEquals("New Album", result.album)
    }

    @Test
    fun updateMusicTags_missingId_doesNothing() = runTest {
        musicDao.updateMusicTags(id = 999, title = "T", artist = "A", album = "B")
        assertNull(musicDao.getMusicById(999).first())
    }

    @Test
    fun getDeletedMusicIdAndPath() = runTest {
        musicDao.insertAll(listOf(music(1, path = "/a.mp3"), music(2, path = "/b.mp3")))
        musicDao.markDeletedByIds(listOf(1))
        val deleted = musicDao.getDeletedMusicIdAndPath()
        assertEquals(1, deleted.size)
        assertEquals("/a.mp3", deleted[0].path)
    }

    @Test
    fun insert_conflictReplaces() = runTest {
        musicDao.insert(music(1, title = "Old"))
        musicDao.insert(music(1, title = "New"))
        assertEquals("New", musicDao.getMusicById(1).first()?.title)
    }

    // ===== MusicExtraDao =====

    @Test
    fun extra_insertAndGet() = runTest {
        musicExtraDao.insert(extra(1))
        assertNotNull(musicExtraDao.getExtraById(1))
        assertTrue(musicExtraDao.getExtraById(1)!!.isGetExtraInfo)
    }

    @Test
    fun extra_getLyrics() = runTest {
        musicExtraDao.insert(MusicExtra(id = 1, isGetExtraInfo = true, lyrics = "test lyrics"))
        assertEquals("test lyrics", musicExtraDao.getLyricsById(1))
    }

    @Test
    fun extra_updateFields() = runTest {
        musicExtraDao.insert(extra(1, hasExtra = false))
        musicExtraDao.updateExtraFieldsById(1, "rw", "pl", "si", "bi", "desc", "rel")
        val result = musicExtraDao.getExtraById(1)!!
        assertTrue(result.isGetExtraInfo)
        assertEquals("rw", result.rewards)
        assertEquals("pl", result.popLyric)
    }

    @Test
    fun extra_getIdsWithExtraInfo() = runTest {
        musicExtraDao.insertAll(listOf(extra(1, true), extra(2, false), extra(3, true)))
        assertEquals(setOf(1L, 3L), musicExtraDao.getIdsWithExtraInfo().toSet())
    }

    // ===== UserInfoDao =====

    @Test
    fun userInfo_insertAndGetLiked() = runTest {
        userInfoDao.insert(userInfo(1, liked = true))
        assertTrue(userInfoDao.getLikedStatus(1))
    }

    @Test
    fun userInfo_updateLiked() = runTest {
        userInfoDao.insert(userInfo(1, liked = false))
        userInfoDao.updateLikedStatus(1, true)
        assertTrue(userInfoDao.getLikedStatus(1))
    }

    @Test
    fun userInfo_incrementPlayCount() = runTest {
        userInfoDao.insert(userInfo(1))
        userInfoDao.incrementPlayCount(1)
        userInfoDao.incrementPlayCount(1)
        assertEquals(2, userInfoDao.getUserInfoById(1)?.playCount)
    }

    @Test
    fun userInfo_incrementPlayCount_autoInsertsIfMissing() = runTest {
        userInfoDao.incrementPlayCount(99)
        assertEquals(1, userInfoDao.getUserInfoById(99)?.playCount)
    }

    @Test
    fun userInfo_updateLastPlayed_insertsIfMissing() = runTest {
        userInfoDao.updateLastPlayed(1, 12345L)
        val info = userInfoDao.getUserInfoById(1)
        assertNotNull(info)
        assertEquals(12345L, info.lastPlayed)
    }

    @Test
    fun userInfo_getAllAndDeleteAll() = runTest {
        userInfoDao.insertAll(listOf(userInfo(1), userInfo(2), userInfo(3)))
        assertEquals(3, userInfoDao.getAllUserInfos().size)
        userInfoDao.deleteAll()
        assertTrue(userInfoDao.getAllUserInfos().isEmpty())
    }

    // ===== MusicAllDao (composite queries) =====

    @Test
    fun musicAll_getMusicInfoById_withRelations() = runTest {
        musicDao.insert(music(1))
        musicExtraDao.insert(extra(1))
        userInfoDao.insert(userInfo(1, liked = true))
        val info = musicAllDao.getMusicInfoById(1).first()!!
        assertEquals("Song1", info.music.title)
        assertNotNull(info.extra)
        assertTrue(info.userInfo!!.liked)
    }

    @Test
    fun musicAll_getMusicInfoById_deleted_returnsNull() = runTest {
        musicDao.insert(music(1))
        musicDao.markDeletedByIds(listOf(1))
        assertNull(musicAllDao.getMusicInfoById(1).first())
    }

    @Test
    fun musicAll_searchByTitle() = runTest {
        musicDao.insertAll(listOf(
            music(1, title = "Hello World"),
            music(2, title = "Goodbye"),
            music(3, title = "Hello Again")
        ))
        assertEquals(2, musicAllDao.searchMusic("%Hello%").size)
    }

    @Test
    fun musicAll_searchByArtist() = runTest {
        musicDao.insertAll(listOf(
            music(1, title = "Song A", artist = "Taylor"),
            music(2, title = "Song B", artist = "Adele")
        ))
        val results = musicAllDao.searchMusic("%Taylor%")
        assertEquals(1, results.size)
        assertEquals("Song A", results[0].music.title)
    }

    @Test
    fun musicAll_search_excludesDeleted() = runTest {
        musicDao.insertAll(listOf(music(1, title = "Hello"), music(2, title = "Hello 2")))
        musicDao.markDeletedByIds(listOf(1))
        val results = musicAllDao.searchMusic("%Hello%")
        assertEquals(1, results.size)
        assertEquals(2L, results[0].music.id)
    }

    @Test
    fun musicAll_sortedById() = runTest {
        musicDao.insertAll(listOf(music(3), music(1), music(2)))
        musicExtraDao.insertAll(listOf(extra(3), extra(1), extra(2)))
        userInfoDao.insertAll(listOf(userInfo(3), userInfo(1), userInfo(2)))
        val results = musicAllDao.getAllMusicInfoAsListById()
        assertEquals(1L, results[0].music.id)
        assertEquals(2L, results[1].music.id)
        assertEquals(3L, results[2].music.id)
    }
}
