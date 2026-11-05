package com.hmp.domain.enum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelNameTest {

    @Test
    fun match_exactName_returnsLabel() {
        assertEquals(LabelName.ROCK, LabelName.match("ROCK"))
        assertEquals(LabelName.POP, LabelName.match("POP"))
        assertEquals(LabelName.JAZZ, LabelName.match("JAZZ"))
    }

    @Test
    fun match_caseInsensitive_returnsLabel() {
        assertEquals(LabelName.ROCK, LabelName.match("rock"))
        assertEquals(LabelName.ROCK, LabelName.match("Rock"))
        assertEquals(LabelName.HIPHOP, LabelName.match("HipHop"))
    }

    @Test
    fun match_unknown_returnsNull() {
        assertNull(LabelName.match("UNKNOWN_LABEL"))
        assertNull(LabelName.match(""))
    }

    @Test
    fun match_moodLabels_works() {
        assertEquals(LabelName.HAPPY, LabelName.match("HAPPY"))
        assertEquals(LabelName.SAD, LabelName.match("sad"))
        assertEquals(LabelName.ENERGETIC, LabelName.match("Energetic"))
    }

    @Test
    fun match_scenarioLabels_works() {
        assertEquals(LabelName.WORKOUT, LabelName.match("WORKOUT"))
        assertEquals(LabelName.SLEEP, LabelName.match("sleep"))
        assertEquals(LabelName.DRIVING, LabelName.match("Driving"))
    }

    @Test
    fun match_languageLabels_works() {
        assertEquals(LabelName.ENGLISH, LabelName.match("ENGLISH"))
        assertEquals(LabelName.CHINESE, LabelName.match("chinese"))
        assertEquals(LabelName.JAPANESE, LabelName.match("Japanese"))
    }

    @Test
    fun match_eraLabels_works() {
        assertEquals(LabelName.SIXTIES, LabelName.match("SIXTIES"))
        assertEquals(LabelName.TWO_THOUSANDS, LabelName.match("TWO_THOUSANDS"))
        assertEquals(LabelName.TWENTY_TWENTIES, LabelName.match("twenty_twenties"))
    }

    @Test
    fun match_unknownEnum_works() {
        assertEquals(LabelName.UNKNOWN, LabelName.match("UNKNOWN"))
    }
}

class PlaybackModeTest {

    @Test
    fun allModes_exist() {
        val modes = PlaybackMode.entries
        assertEquals(3, modes.size)
        assertEquals(PlaybackMode.SEQUENTIAL, modes[0])
        assertEquals(PlaybackMode.REPEAT_ONE, modes[1])
        assertEquals(PlaybackMode.SHUFFLE, modes[2])
    }
}

class LabelCategoryTest {

    @Test
    fun allCategories_exist() {
        val categories = LabelCategory.entries
        assertEquals(5, categories.size)
        assertEquals(LabelCategory.GENRE, categories[0])
        assertEquals(LabelCategory.MOOD, categories[1])
        assertEquals(LabelCategory.SCENARIO, categories[2])
        assertEquals(LabelCategory.LANGUAGE, categories[3])
        assertEquals(LabelCategory.ERA, categories[4])
    }
}
