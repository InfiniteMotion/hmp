package com.hmp.data.database.myenum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelConvertersTest {

    private val converters = LabelConverters()

    @Test
    fun fromLabelCategory_genre() {
        assertEquals("GENRE", converters.fromLabelCategory(LabelCategory.GENRE))
    }

    @Test
    fun fromLabelCategory_mood() {
        assertEquals("MOOD", converters.fromLabelCategory(LabelCategory.MOOD))
    }

    @Test
    fun toLabelCategory_genre() {
        assertEquals(LabelCategory.GENRE, converters.toLabelCategory("GENRE"))
    }

    @Test
    fun toLabelCategory_mood() {
        assertEquals(LabelCategory.MOOD, converters.toLabelCategory("MOOD"))
    }

    @Test
    fun fromLabelName_rock() {
        assertEquals("ROCK", converters.fromLabelName(LabelName.ROCK))
    }

    @Test
    fun toLabelName_rock() {
        assertEquals(LabelName.ROCK, converters.toLabelName("ROCK"))
    }

    @Test
    fun labelCategory_roundTrip() {
        LabelCategory.entries.forEach { category ->
            val str = converters.fromLabelCategory(category)
            val restored = converters.toLabelCategory(str)
            assertEquals(category, restored)
        }
    }

    @Test
    fun labelName_roundTrip() {
        // Test a subset of label names
        val testLabels = listOf(
            LabelName.ROCK, LabelName.POP, LabelName.JAZZ,
            LabelName.HAPPY, LabelName.SAD,
            LabelName.WORKOUT, LabelName.SLEEP,
            LabelName.ENGLISH, LabelName.CHINESE,
            LabelName.SIXTIES, LabelName.TWENTY_TWENTIES,
            LabelName.UNKNOWN,
        )
        testLabels.forEach { label ->
            val str = converters.fromLabelName(label)
            val restored = converters.toLabelName(str)
            assertEquals(label, restored)
        }
    }
}

class DataLabelNameTest {

    @Test
    fun match_exactName() {
        assertEquals(LabelName.ROCK, LabelName.match("ROCK"))
        assertEquals(LabelName.POP, LabelName.match("POP"))
    }

    @Test
    fun match_caseInsensitive() {
        assertEquals(LabelName.ROCK, LabelName.match("rock"))
        assertEquals(LabelName.HAPPY, LabelName.match("Happy"))
    }

    @Test
    fun match_unknown_returnsNull() {
        assertNull(LabelName.match("NONEXISTENT"))
    }

    @Test
    fun match_empty_returnsNull() {
        assertNull(LabelName.match(""))
    }
}

class DataLabelCategoryTest {

    @Test
    fun allCategories_exist() {
        val categories = LabelCategory.entries
        assertEquals(5, categories.size)
    }
}
