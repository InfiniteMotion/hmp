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

class PlaybackAndListeningDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var historyDao: PlaybackHistoryDao
    private lateinit var durationDao: ListeningDurationDao

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        historyDao = db.playbackHistoryDao()
        durationDao = db.listeningDurationDao()
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    // ===== PlaybackHistoryDao =====

    @Test
    fun history_insert_returnsId() = runTest {
        val id = historyDao.insert(PlaybackHistory(musicId = 1, playedAt = 1000L))
        assertTrue(id > 0)
    }

    @Test
    fun history_updatePlaybackRecord() = runTest {
        val id = historyDao.insert(PlaybackHistory(musicId = 1, playedAt = 1000L))
        historyDao.updatePlaybackRecord(id, 30000L, true)
        val all = historyDao.getAllHistory()
        val record = all.find { it.id == id }!!
        assertEquals(30000L, record.playDuration)
        assertTrue(record.isCompleted)
    }

    @Test
    fun history_getRecentHistory() = runTest {
        historyDao.insert(PlaybackHistory(musicId = 1, playedAt = 1000L))
        historyDao.insert(PlaybackHistory(musicId = 2, playedAt = 2000L))
        historyDao.insert(PlaybackHistory(musicId = 3, playedAt = 3000L))
        val recent = historyDao.getRecentHistory(2)
        assertEquals(2, recent.size)
        // Should be ordered by playedAt DESC
        assertEquals(3000L, recent[0].playedAt)
        assertEquals(2000L, recent[1].playedAt)
    }

    @Test
    fun history_getHistoryForMusic() = runTest {
        historyDao.insert(PlaybackHistory(musicId = 1, playedAt = 1000L))
        historyDao.insert(PlaybackHistory(musicId = 2, playedAt = 2000L))
        historyDao.insert(PlaybackHistory(musicId = 1, playedAt = 3000L))
        val music1History = historyDao.getHistoryForMusic(1, 10).first()
        assertEquals(2, music1History.size)
    }

    @Test
    fun history_deleteAll() = runTest {
        historyDao.insert(PlaybackHistory(musicId = 1, playedAt = 1000L))
        historyDao.insert(PlaybackHistory(musicId = 2, playedAt = 2000L))
        historyDao.deleteAll()
        assertTrue(historyDao.getAllHistory().isEmpty())
    }

    // ===== ListeningDurationDao =====

    @Test
    fun duration_insertAndGetByDate() = runTest {
        durationDao.insert(ListeningDuration(date = "2024-01-01", duration = 60000L, updatedAt = 1000L))
        val result = durationDao.getDurationByDate("2024-01-01")
        assertNotNull(result)
        assertEquals(60000L, result.duration)
    }

    @Test
    fun duration_getByDate_nonExisting_returnsNull() = runTest {
        assertNull(durationDao.getDurationByDate("2099-01-01"))
    }

    @Test
    fun duration_updateDuration_addsToExisting() = runTest {
        durationDao.insert(ListeningDuration(date = "2024-01-01", duration = 60000L, updatedAt = 1000L))
        durationDao.updateDuration("2024-01-01", 30000L, 2000L)
        val result = durationDao.getDurationByDate("2024-01-01")!!
        assertEquals(90000L, result.duration)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun duration_getRecentDurations() = runTest {
        durationDao.insert(ListeningDuration(date = "2024-01-01", duration = 60000L, updatedAt = 1000L))
        durationDao.insert(ListeningDuration(date = "2024-01-02", duration = 90000L, updatedAt = 2000L))
        durationDao.insert(ListeningDuration(date = "2024-01-03", duration = 120000L, updatedAt = 3000L))
        val recent = durationDao.getRecentDurations(2).first()
        assertEquals(2, recent.size)
        // Ordered by date DESC
        assertEquals("2024-01-03", recent[0].date)
        assertEquals("2024-01-02", recent[1].date)
    }

    @Test
    fun duration_insertAll() = runTest {
        durationDao.insertAll(
            listOf(
                ListeningDuration(date = "2024-01-01", duration = 60000L, updatedAt = 1000L),
                ListeningDuration(date = "2024-01-02", duration = 90000L, updatedAt = 2000L),
            ),
        )
        assertEquals(2, durationDao.getAllDurations().size)
    }

    @Test
    fun duration_upsert_updatesExisting() = runTest {
        durationDao.insert(ListeningDuration(date = "2024-01-01", duration = 60000L, updatedAt = 1000L))
        // Insert again with same date -> REPLACE
        durationDao.insert(ListeningDuration(date = "2024-01-01", duration = 120000L, updatedAt = 2000L))
        val result = durationDao.getDurationByDate("2024-01-01")!!
        assertEquals(120000L, result.duration)
    }
}
