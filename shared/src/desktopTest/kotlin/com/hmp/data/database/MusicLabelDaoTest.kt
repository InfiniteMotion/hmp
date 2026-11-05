package com.hmp.data.database

import com.hmp.data.database.myenum.LabelCategory
import com.hmp.data.database.myenum.LabelName
import com.hmp.test.db.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicLabelDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var labelDao: MusicLabelDao

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        labelDao = db.musicLabelDao()
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runTest {
        labelDao.insert(MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.POP))
        val labels = labelDao.getLabelsById(1)
        assertEquals(1, labels.size)
        assertEquals(LabelName.POP, labels[0].label)
        assertEquals(LabelCategory.GENRE, labels[0].type)
    }

    @Test
    fun getLabelsById_multiple() = runTest {
        labelDao.insertAll(
            listOf(
                MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.POP),
                MusicLabel(musicId = 1, type = LabelCategory.MOOD, label = LabelName.HAPPY),
                MusicLabel(musicId = 1, type = LabelCategory.SCENARIO, label = LabelName.WORKOUT),
            ),
        )
        val labels = labelDao.getLabelsById(1)
        assertEquals(3, labels.size)
    }

    @Test
    fun getLabelsByType() = runTest {
        labelDao.insertAll(
            listOf(
                MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.POP),
                MusicLabel(musicId = 2, type = LabelCategory.GENRE, label = LabelName.POP),
                MusicLabel(musicId = 3, type = LabelCategory.GENRE, label = LabelName.ROCK),
                MusicLabel(musicId = 4, type = LabelCategory.MOOD, label = LabelName.HAPPY),
            ),
        )
        val genreLabels = labelDao.getLabelsByType(LabelCategory.GENRE).first()
        assertEquals(2, genreLabels.size)
        // POP should be first (count=2 > count=1)
        assertEquals(LabelName.POP, genreLabels[0])
    }

    @Test
    fun getMusicIdListByType() = runTest {
        labelDao.insertAll(
            listOf(
                MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.POP),
                MusicLabel(musicId = 2, type = LabelCategory.GENRE, label = LabelName.ROCK),
                MusicLabel(musicId = 3, type = LabelCategory.GENRE, label = LabelName.POP),
            ),
        )
        val popIds = labelDao.getMusicIdListByType(LabelName.POP)
        assertEquals(setOf(1L, 3L), popIds.toSet())
    }

    @Test
    fun getAllLabels() = runTest {
        labelDao.insertAll(
            listOf(
                MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.POP),
                MusicLabel(musicId = 2, type = LabelCategory.MOOD, label = LabelName.HAPPY),
            ),
        )
        assertEquals(2, labelDao.getAllLabels().size)
    }

    @Test
    fun insert_conflictReplace() = runTest {
        labelDao.insert(MusicLabel(musicId = 1, type = LabelCategory.GENRE, label = LabelName.POP))
        // Same composite key -> replace
        labelDao.insert(MusicLabel(musicId = 1, type = LabelCategory.MOOD, label = LabelName.POP))
        val labels = labelDao.getLabelsById(1)
        assertEquals(1, labels.size)
        assertEquals(LabelCategory.MOOD, labels[0].type)
    }

    @Test
    fun getLabelsById_nonExisting_returnsEmpty() = runTest {
        assertTrue(labelDao.getLabelsById(999).isEmpty())
    }

    @Test
    fun getLabelsByType_empty() = runTest {
        val labels = labelDao.getLabelsByType(LabelCategory.GENRE).first()
        assertTrue(labels.isEmpty())
    }
}
