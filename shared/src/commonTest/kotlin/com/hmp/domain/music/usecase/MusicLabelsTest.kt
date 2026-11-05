package com.hmp.domain.music.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicLabelsTest {

    @Test
    fun defaultValues_allEmpty() {
        val labels = MusicLabels()
        assertTrue(labels.genres.isEmpty())
        assertTrue(labels.moods.isEmpty())
        assertTrue(labels.scenarios.isEmpty())
        assertNull(labels.language)
        assertNull(labels.era)
    }

    @Test
    fun customValues() {
        val labels = MusicLabels(
            genres = listOf("Rock", "Pop"),
            moods = listOf("Energetic", "Happy"),
            scenarios = listOf("Workout", "Party"),
            language = "English",
            era = "2020s",
        )
        assertEquals(2, labels.genres.size)
        assertEquals("Rock", labels.genres[0])
        assertEquals(2, labels.moods.size)
        assertEquals("Energetic", labels.moods[0])
        assertEquals(2, labels.scenarios.size)
        assertEquals("English", labels.language)
        assertEquals("2020s", labels.era)
    }

    @Test
    fun genresOnly() {
        val labels = MusicLabels(genres = listOf("Jazz", "Blues"))
        assertEquals(2, labels.genres.size)
        assertTrue(labels.moods.isEmpty())
        assertNull(labels.language)
    }

    @Test
    fun languageOnly() {
        val labels = MusicLabels(language = "Chinese")
        assertTrue(labels.genres.isEmpty())
        assertEquals("Chinese", labels.language)
    }
}
