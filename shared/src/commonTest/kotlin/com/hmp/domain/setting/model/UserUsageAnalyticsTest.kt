package com.hmp.domain.setting.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserUsageAnalyticsTest {

    @Test
    fun construction_full() {
        val analytics = UserUsageAnalytics(
            totalPlayCount = 100,
            totalSkipCount = 20,
            likedCount = 50,
            totalListeningMinutes = 500,
            averageSessionMinutes = 25.5,
            completionRate = 0.8f,
            skipRate = 0.2f,
            thisWeekMinutes = 120,
            lastWeekMinutes = 100,
            topPlayedSongs = listOf(
                TopPlayedEntry(1, "Song A", "Artist A", 10),
            ),
            recentPlaybackWithTitle = listOf(
                RecentPlaybackEntry(1, "Song A", "Artist A", 1700000000L, 180000L, true, "playlist"),
            ),
            playSourceBreakdown = mapOf("playlist" to 60, "search" to 40),
            topGenres = listOf(LabelCountEntry("Rock", 30)),
            topMoods = listOf(LabelCountEntry("Energetic", 25)),
            topScenarios = listOf(LabelCountEntry("Workout", 15)),
            topArtists = listOf(ArtistCountEntry("Artist A", 20)),
            customPlaylistCount = 5,
            topSongsInPlaylists = listOf(TopPlayedEntry(1, "Song A", "Artist A", 8)),
        )
        assertEquals(100, analytics.totalPlayCount)
        assertEquals(20, analytics.totalSkipCount)
        assertEquals(50, analytics.likedCount)
        assertEquals(500, analytics.totalListeningMinutes)
        assertEquals(25.5, analytics.averageSessionMinutes)
        assertEquals(0.8f, analytics.completionRate)
        assertEquals(0.2f, analytics.skipRate)
        assertEquals(120, analytics.thisWeekMinutes)
        assertEquals(100, analytics.lastWeekMinutes)
        assertEquals(1, analytics.topPlayedSongs.size)
        assertEquals(1, analytics.recentPlaybackWithTitle.size)
        assertEquals(2, analytics.playSourceBreakdown.size)
        assertEquals(1, analytics.topGenres.size)
        assertEquals(1, analytics.topMoods.size)
        assertEquals(1, analytics.topScenarios.size)
        assertEquals(1, analytics.topArtists.size)
        assertEquals(5, analytics.customPlaylistCount)
        assertEquals(1, analytics.topSongsInPlaylists.size)
    }

    @Test
    fun construction_minimal_defaults() {
        val analytics = UserUsageAnalytics(
            totalPlayCount = 0,
            totalSkipCount = 0,
            likedCount = 0,
            totalListeningMinutes = 0,
            averageSessionMinutes = 0.0,
            completionRate = 0f,
            skipRate = 0f,
            thisWeekMinutes = 0,
            lastWeekMinutes = 0,
            topPlayedSongs = emptyList(),
            recentPlaybackWithTitle = emptyList(),
        )
        assertTrue(analytics.playSourceBreakdown.isEmpty())
        assertTrue(analytics.topGenres.isEmpty())
        assertTrue(analytics.topMoods.isEmpty())
        assertTrue(analytics.topScenarios.isEmpty())
        assertTrue(analytics.topArtists.isEmpty())
        assertEquals(0, analytics.customPlaylistCount)
        assertTrue(analytics.topSongsInPlaylists.isEmpty())
    }
}

class TopPlayedEntryTest {

    @Test
    fun construction() {
        val entry = TopPlayedEntry(1, "Song", "Artist", 42)
        assertEquals(1L, entry.musicId)
        assertEquals("Song", entry.title)
        assertEquals("Artist", entry.artist)
        assertEquals(42, entry.playCount)
    }
}

class RecentPlaybackEntryTest {

    @Test
    fun construction_completed() {
        val entry = RecentPlaybackEntry(
            musicId = 1,
            title = "Song",
            artist = "Artist",
            playedAt = 1700000000L,
            playDuration = 180000L,
            isCompleted = true,
            source = "playlist",
        )
        assertEquals(1L, entry.musicId)
        assertEquals(true, entry.isCompleted)
        assertEquals("playlist", entry.source)
    }

    @Test
    fun construction_skipped() {
        val entry = RecentPlaybackEntry(
            musicId = 2,
            title = "Song 2",
            artist = "Artist 2",
            playedAt = 1700100000L,
            playDuration = 30000L,
            isCompleted = false,
            source = null,
        )
        assertEquals(false, entry.isCompleted)
        assertNull(entry.source)
    }
}

class LabelCountEntryTest {

    @Test
    fun construction() {
        val entry = LabelCountEntry("Rock", 42)
        assertEquals("Rock", entry.labelDisplayName)
        assertEquals(42, entry.count)
    }
}

class ArtistCountEntryTest {

    @Test
    fun construction() {
        val entry = ArtistCountEntry("Artist Name", 15)
        assertEquals("Artist Name", entry.artistName)
        assertEquals(15, entry.playCount)
    }
}
