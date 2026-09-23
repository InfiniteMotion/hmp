package com.hmp.domain.backup

import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserBackupSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun defaultValues() {
        val snapshot = UserBackupSnapshot()
        assertEquals(1, snapshot.version)
        assertEquals(0L, snapshot.createdAt)
        assertNull(snapshot.appSettings)
        assertTrue(snapshot.musicUserState.userInfos.isEmpty())
        assertTrue(snapshot.playlists.playlists.isEmpty())
        assertTrue(snapshot.listeningStats.listeningDurations.isEmpty())
    }

    @Test
    fun appSettingsSnapshot_defaults() {
        val settings = AppSettingsSnapshot()
        assertNull(settings.userName)
        assertNull(settings.avatarUri)
        assertEquals("default", settings.themeMode)
        assertEquals("FLUID", settings.backgroundStyle)
        assertEquals("custom", settings.hazeMode)
        assertEquals("regular", settings.hazeMaterialPreset)
        assertEquals(20f, settings.hazeBlurRadius)
        assertEquals(0.15f, settings.hazeNoiseFactor)
        assertEquals(0.22f, settings.hazeTintAlpha)
        assertEquals(0f, settings.hazeIntensity)
        assertEquals(true, settings.autoBatchProcess)
        assertEquals("FREE", settings.aiAccessMode)
    }

    @Test
    fun userInfoSnapshot() {
        val info = UserInfoSnapshot(
            id = 1,
            liked = true,
            playCount = 42
        )
        assertEquals(1L, info.id)
        assertEquals(true, info.liked)
        assertEquals(false, info.disLiked)
        assertNull(info.lastPlayed)
        assertEquals(42, info.playCount)
    }

    @Test
    fun musicLabelSnapshot() {
        val label = MusicLabelSnapshot(
            musicId = 1,
            label = LabelName.ROCK,
            category = LabelCategory.GENRE
        )
        assertEquals(1L, label.musicId)
        assertEquals(LabelName.ROCK, label.label)
        assertEquals(LabelCategory.GENRE, label.category)
    }

    @Test
    fun fullSnapshot_serialization_roundTrip() {
        val snapshot = UserBackupSnapshot(
            version = 1,
            createdAt = 1700000000L,
            appSettings = AppSettingsSnapshot(
                userName = "TestUser",
                themeMode = "dark"
            ),
            musicUserState = MusicUserStateSnapshot(
                userInfos = listOf(UserInfoSnapshot(id = 1, liked = true)),
                labels = listOf(MusicLabelSnapshot(1, LabelName.POP, LabelCategory.GENRE))
            ),
            playlists = PlaylistsSnapshot(),
            listeningStats = ListeningStatsSnapshot()
        )

        val jsonString = json.encodeToString(UserBackupSnapshot.serializer(), snapshot)
        val restored = json.decodeFromString(UserBackupSnapshot.serializer(), jsonString)

        assertEquals(snapshot.version, restored.version)
        assertEquals(snapshot.createdAt, restored.createdAt)
        assertEquals("TestUser", restored.appSettings?.userName)
        assertEquals("dark", restored.appSettings?.themeMode)
        assertEquals(1, restored.musicUserState.userInfos.size)
        assertEquals(true, restored.musicUserState.userInfos[0].liked)
        assertEquals(1, restored.musicUserState.labels.size)
        assertEquals(LabelName.POP, restored.musicUserState.labels[0].label)
    }
}