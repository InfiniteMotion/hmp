package com.hmp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hmp.data.network.BuiltInApiKeyProvider
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl
    private lateinit var tempFile: File
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setup() {
        tempFile = File.createTempFile("test_settings", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { tempFile }
        repo = SettingsRepositoryImpl(dataStore, json)
    }

    @After
    fun teardown() {
        tempFile.delete()
    }

    // ===== Basic Settings =====

    @Test
    fun isFirstLaunch_defaultTrue() = runTest {
        assertTrue(repo.isFirstLaunch.first())
    }

    @Test
    fun saveIsFirstLaunch() = runTest {
        repo.saveIsFirstLaunch(false)
        assertFalse(repo.isFirstLaunch.first())
    }

    @Test
    fun userName_defaultUser() = runTest {
        assertEquals("User", repo.userName.first())
    }

    @Test
    fun saveUserName() = runTest {
        repo.saveUserName("TestUser")
        assertEquals("TestUser", repo.userName.first())
    }

    @Test
    fun themeMode_defaultDefault() = runTest {
        assertEquals("default", repo.themeMode.first())
    }

    @Test
    fun saveThemeMode() = runTest {
        repo.saveThemeMode("dark")
        assertEquals("dark", repo.themeMode.first())
    }

    @Test
    fun backgroundStyle_defaultFluid() = runTest {
        assertEquals("FLUID", repo.backgroundStyle.first())
    }

    @Test
    fun saveBackgroundStyle() = runTest {
        repo.saveBackgroundStyle("SOLID")
        assertEquals("SOLID", repo.backgroundStyle.first())
    }

    // ===== Playback State =====

    @Test
    fun currentMusicId_defaultNull() = runTest {
        assertNull(repo.currentMusicId.first())
    }

    @Test
    fun saveCurrentMusicId() = runTest {
        repo.saveCurrentMusicId(42)
        assertEquals(42L, repo.currentMusicId.first())
    }

    @Test
    fun currentPosition_defaultZero() = runTest {
        assertEquals(0L, repo.currentPosition.first())
    }

    @Test
    fun saveCurrentPosition() = runTest {
        repo.saveCurrentPosition(5000L)
        assertEquals(5000L, repo.currentPosition.first())
    }

    // ===== Avatar =====

    @Test
    fun avatarUri_defaultNull() = runTest {
        assertNull(repo.getAvatarUri())
    }

    @Test
    fun saveAvatarUri() = runTest {
        repo.saveAvatarUri("content://avatar")
        assertEquals("content://avatar", repo.getAvatarUri())
    }

    // ===== Audio Effects =====

    @Test
    fun equalizerPreset_defaultZero() = runTest {
        assertEquals(0, repo.equalizerPreset.first())
    }

    @Test
    fun saveEqualizerPreset() = runTest {
        repo.saveEqualizerPreset(3)
        assertEquals(3, repo.equalizerPreset.first())
    }

    @Test
    fun bassBoostLevel_defaultZero() = runTest {
        assertEquals(0, repo.bassBoostLevel.first())
    }

    @Test
    fun saveBassBoostLevel() = runTest {
        repo.saveBassBoostLevel(7)
        assertEquals(7, repo.bassBoostLevel.first())
    }

    @Test
    fun isSurroundSoundEnabled_defaultFalse() = runTest {
        assertFalse(repo.isSurroundSoundEnabled.first())
    }

    @Test
    fun saveSurroundSoundEnabled() = runTest {
        repo.saveSurroundSoundEnabled(true)
        assertTrue(repo.isSurroundSoundEnabled.first())
    }

    @Test
    fun reverbPreset_defaultZero() = runTest {
        assertEquals(0, repo.reverbPreset.first())
    }

    @Test
    fun saveReverbPreset() = runTest {
        repo.saveReverbPreset(2)
        assertEquals(2, repo.reverbPreset.first())
    }

    // ===== Lyrics Config =====

    @Test
    fun lyricsPlayerConfig_defaultNotNull() = runTest {
        assertNotNull(repo.lyricsPlayerConfig.first())
    }

    @Test
    fun saveLyricsPlayerConfig() = runTest {
        val config = """{"originalTextSize":20,"lineSpacing":8}"""
        repo.saveLyricsPlayerConfig(config)
        assertEquals(config, repo.getLyricsPlayerConfig())
    }

    @Test
    fun lyricsFullscreenConfig_saveAndGet() = runTest {
        repo.saveLyricsFullscreenConfig("""{"originalTextSize":24}""")
        assertEquals("""{"originalTextSize":24}""", repo.getLyricsFullscreenConfig())
    }

    @Test
    fun lyricsFloatingConfig_saveAndGet() = runTest {
        repo.saveLyricsFloatingConfig("""{"originalTextSize":12}""")
        assertEquals("""{"originalTextSize":12}""", repo.getLyricsFloatingConfig())
    }

    // ===== Legacy Lyrics =====

    @Test
    fun legacy_lyricsOriginalTextSize_default14() = runTest {
        assertEquals(14, repo.getLyricsOriginalTextSize())
    }

    @Test
    fun legacy_saveLyricsOriginalTextSize() = runTest {
        repo.saveLyricsOriginalTextSize(20)
        assertEquals(20, repo.getLyricsOriginalTextSize())
    }

    @Test
    fun legacy_lyricsDisplayMode_defaultDual() = runTest {
        assertEquals(DisplayMode.DUAL, repo.getLyricsDisplayMode())
    }

    @Test
    fun legacy_saveLyricsDisplayMode() = runTest {
        repo.saveLyricsDisplayMode(DisplayMode.LANG1)
        assertEquals(DisplayMode.LANG1, repo.getLyricsDisplayMode())
    }

    @Test
    fun legacy_lyricsAlignment_defaultCenter() = runTest {
        assertEquals(LyricsAlignment.CENTER, repo.getLyricsAlignment())
    }

    @Test
    fun legacy_saveLyricsAlignment() = runTest {
        repo.saveLyricsAlignment(LyricsAlignment.LEFT)
        assertEquals(LyricsAlignment.LEFT, repo.getLyricsAlignment())
    }

    // ===== AI Config =====

    @Test
    fun aiAccessMode_defaultFree() = runTest {
        assertEquals(AiAccessMode.FREE, repo.getAiAccessMode())
    }

    @Test
    fun saveAiAccessMode() = runTest {
        repo.saveAiAccessMode(AiAccessMode.CUSTOM)
        assertEquals(AiAccessMode.CUSTOM, repo.getAiAccessMode())
    }

    @Test
    fun customAiConfig_defaultEmpty() = runTest {
        val config = repo.getCustomAiConfig()
        assertEquals("", config.endpoint)
        assertEquals("", config.apiKey)
    }

    @Test
    fun saveCustomAiConfig() = runTest {
        val config = AiEndpointConfig(endpoint = "https://api.test.com", apiKey = "sk-test", selectedModel = "gpt-4")
        repo.saveCustomAiConfig(config)
        val saved = repo.getCustomAiConfig()
        assertEquals("https://api.test.com", saved.endpoint)
        assertEquals("sk-test", saved.apiKey)
        assertEquals("gpt-4", saved.selectedModel)
    }

    @Test
    fun aiFreeTrialRemainingCount_default() = runTest {
        val count = repo.getAiFreeTrialRemainingCount()
        assertTrue(count > 0)
    }

    @Test
    fun decrementAiFreeTrialCount() = runTest {
        val initial = repo.getAiFreeTrialRemainingCount()
        repo.decrementAiFreeTrialCount()
        assertEquals(initial - 1, repo.getAiFreeTrialRemainingCount())
    }

    // ===== Gallery Sort =====

    @Test
    fun galleryOrderBy_defaultTitle() = runTest {
        assertEquals("title", repo.galleryOrderBy.first())
    }

    @Test
    fun saveGalleryOrderBy() = runTest {
        repo.saveGalleryOrderBy("artist")
        assertEquals("artist", repo.galleryOrderBy.first())
    }

    // ===== Playlist Algorithm =====

    @Test
    fun defaultAlgorithmType_defaultOptimized() = runTest {
        assertEquals("OPTIMIZED_SIMILARITY", repo.getDefaultAlgorithmType())
    }

    @Test
    fun saveDefaultAlgorithmType() = runTest {
        repo.saveDefaultAlgorithmType("CHAIN_SIMILARITY")
        assertEquals("CHAIN_SIMILARITY", repo.getDefaultAlgorithmType())
    }

    @Test
    fun defaultWeightTemplate_defaultBalanced() = runTest {
        assertEquals("BALANCED", repo.getDefaultWeightTemplate())
    }

    @Test
    fun saveDefaultWeightTemplate() = runTest {
        repo.saveDefaultWeightTemplate("HEAVY_BASS")
        assertEquals("HEAVY_BASS", repo.getDefaultWeightTemplate())
    }

    // ===== Scan Config =====

    @Test
    fun scanDirectoryConfig_defaultNotNull() = runTest {
        val config = repo.scanDirectoryConfig.first()
        assertNotNull(config)
    }

    // ===== Haze =====

    @Test
    fun hazeMode_defaultCustom() = runTest {
        assertEquals("custom", repo.hazeMode.first())
    }

    @Test
    fun saveHazeMode() = runTest {
        repo.saveHazeMode("preset")
        assertEquals("preset", repo.hazeMode.first())
    }

    @Test
    fun hazeBlurRadius_default20() = runTest {
        assertEquals(20f, repo.hazeBlurRadius.first())
    }

    @Test
    fun saveHazeBlurRadius() = runTest {
        repo.saveHazeBlurRadius(30f)
        assertEquals(30f, repo.hazeBlurRadius.first())
    }

    @Test
    fun hazeIntensity_default0_6() = runTest {
        assertEquals(0.6f, repo.hazeIntensity.first())
    }

    @Test
    fun saveHazeIntensity() = runTest {
        repo.saveHazeIntensity(0.8f)
        assertEquals(0.8f, repo.hazeIntensity.first())
    }

    // ===== Floating Lyrics =====

    @Test
    fun floatingLyricsEnabled_defaultFalse() = runTest {
        assertFalse(repo.floatingLyricsEnabled.first())
    }

    @Test
      fun saveFloatingLyricsEnabled() = runTest {
          repo.saveFloatingLyricsEnabled(true)
          assertTrue(repo.floatingLyricsEnabled.first())
      }

      // ===== Karaoke Lyrics =====

      @Test
      fun lyricsKaraokeEnabled_defaultTrue() = runTest {
          assertTrue(repo.lyricsKaraokeEnabled.first())
      }

      @Test
      fun saveLyricsKaraokeEnabled() = runTest {
          repo.saveLyricsKaraokeEnabled(false)
          assertFalse(repo.lyricsKaraokeEnabled.first())
          assertFalse(repo.getLyricsKaraokeEnabled())
      }

      // ===== Export/Import Snapshot =====

    @Test
    fun exportAppSettingsSnapshot_containsDefaults() = runTest {
        val snapshot = repo.exportAppSettingsSnapshot()
        assertEquals("default", snapshot.themeMode)
        assertEquals("FLUID", snapshot.backgroundStyle)
    }

    @Test
    fun restoreFromSnapshot_appliesValues() = runTest {
        val snapshot = com.hmp.domain.backup.AppSettingsSnapshot(
            themeMode = "dark",
            backgroundStyle = "SOLID",
            hazeMode = "custom",
            hazeMaterialPreset = "regular",
            hazeBlurRadius = 25f,
            hazeNoiseFactor = 0.2f,
            hazeTintAlpha = 0.3f,
            hazeIntensity = 0.5f,
            autoBatchProcess = true,
            aiAccessMode = "FREE",
            customAiEndpoint = "",
            customAiModel = ""
        )
        repo.restoreFromSnapshot(snapshot)
        assertEquals("dark", repo.themeMode.first())
        assertEquals("SOLID", repo.backgroundStyle.first())
    }

    private fun assertNotNull(value: Any?) {
        kotlin.test.assertNotNull(value)
    }
}
