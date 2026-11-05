package com.hmp.domain.setting.usecase

import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.lyrics.LyricsComponent
import com.hmp.domain.lyrics.LyricsComponentConfig
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsSettingsUseCaseTest {

    private val settingsRepository = FakeSettingsRepository()
    private val useCase = LyricsSettingsUseCase(settingsRepository)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ===== getComponentConfig =====

    @Test
    fun getComponentConfig_player_defaultIsEmptyJson_returnsDefault() = runTest {
        val config = useCase.getComponentConfig(LyricsComponent.PLAYER).first()
        assertEquals(LyricsComponentConfig.DEFAULT, config)
    }

    @Test
    fun getComponentConfig_savedConfig_returnsParsedConfig() = runTest {
        val custom = LyricsComponentConfig(
            originalTextSize = 20,
            translatedTextSize = 18,
            currentTimeTextSize = 22,
            lineSpacing = 10,
            displayMode = DisplayMode.LANG1,
            alignment = LyricsAlignment.LEFT,
        )
        val jsonStr = json.encodeToString(LyricsComponentConfig.serializer(), custom)
        settingsRepository.saveLyricsPlayerConfig(jsonStr)

        val config = useCase.getComponentConfig(LyricsComponent.PLAYER).first()
        assertEquals(20, config.originalTextSize)
        assertEquals(18, config.translatedTextSize)
        assertEquals(22, config.currentTimeTextSize)
        assertEquals(10, config.lineSpacing)
        assertEquals(DisplayMode.LANG1, config.displayMode)
        assertEquals(LyricsAlignment.LEFT, config.alignment)
    }

    @Test
    fun getComponentConfig_fullscreen_savedConfig() = runTest {
        val custom = LyricsComponentConfig(originalTextSize = 24, displayMode = DisplayMode.DUAL)
        settingsRepository.saveLyricsFullscreenConfig(json.encodeToString(LyricsComponentConfig.serializer(), custom))

        val config = useCase.getComponentConfig(LyricsComponent.FULLSCREEN).first()
        assertEquals(24, config.originalTextSize)
        assertEquals(DisplayMode.DUAL, config.displayMode)
    }

    @Test
    fun getComponentConfig_floating_savedConfig() = runTest {
        val custom = LyricsComponentConfig(originalTextSize = 12, alignment = LyricsAlignment.RIGHT)
        settingsRepository.saveLyricsFloatingConfig(json.encodeToString(LyricsComponentConfig.serializer(), custom))

        val config = useCase.getComponentConfig(LyricsComponent.FLOATING).first()
        assertEquals(12, config.originalTextSize)
        assertEquals(LyricsAlignment.RIGHT, config.alignment)
    }

    @Test
    fun getComponentConfig_invalidJson_returnsDefault() = runTest {
        settingsRepository.saveLyricsPlayerConfig("not valid json!!!")

        val config = useCase.getComponentConfig(LyricsComponent.PLAYER).first()
        assertEquals(LyricsComponentConfig.DEFAULT, config)
    }

    // ===== saveComponentConfig =====

    @Test
    fun saveComponentConfig_player_persistsToRepository() = runTest {
        val config = LyricsComponentConfig(originalTextSize = 30, lineSpacing = 8)
        useCase.saveComponentConfig(LyricsComponent.PLAYER, config)

        val saved = settingsRepository.getLyricsPlayerConfig()
        val parsed = json.decodeFromString(LyricsComponentConfig.serializer(), saved)
        assertEquals(30, parsed.originalTextSize)
        assertEquals(8, parsed.lineSpacing)
    }

    @Test
    fun saveComponentConfig_fullscreen_persistsToRepository() = runTest {
        val config = LyricsComponentConfig(displayMode = DisplayMode.LANG1)
        useCase.saveComponentConfig(LyricsComponent.FULLSCREEN, config)

        val parsed = json.decodeFromString(LyricsComponentConfig.serializer(), settingsRepository.getLyricsFullscreenConfig())
        assertEquals(DisplayMode.LANG1, parsed.displayMode)
    }

    @Test
    fun saveComponentConfig_floating_persistsToRepository() = runTest {
        val config = LyricsComponentConfig(alignment = LyricsAlignment.LEFT)
        useCase.saveComponentConfig(LyricsComponent.FLOATING, config)

        val parsed = json.decodeFromString(LyricsComponentConfig.serializer(), settingsRepository.getLyricsFloatingConfig())
        assertEquals(LyricsAlignment.LEFT, parsed.alignment)
    }

    // ===== getAllComponentConfigs =====

    @Test
    fun getAllComponentConfigs_default_returnsDefaultsForAll() = runTest {
        val allConfigs = useCase.getAllComponentConfigs()
        assertEquals(3, allConfigs.size)
        LyricsComponent.entries.forEach { component ->
            assertEquals(LyricsComponentConfig.DEFAULT, allConfigs[component])
        }
    }

    @Test
    fun getAllComponentConfigs_withSavedValues_returnsCorrectly() = runTest {
        val playerConfig = LyricsComponentConfig(originalTextSize = 20)
        val fullscreenConfig = LyricsComponentConfig(originalTextSize = 24)
        settingsRepository.saveLyricsPlayerConfig(json.encodeToString(LyricsComponentConfig.serializer(), playerConfig))
        settingsRepository.saveLyricsFullscreenConfig(json.encodeToString(LyricsComponentConfig.serializer(), fullscreenConfig))

        val allConfigs = useCase.getAllComponentConfigs()
        assertEquals(20, allConfigs[LyricsComponent.PLAYER]?.originalTextSize)
        assertEquals(24, allConfigs[LyricsComponent.FULLSCREEN]?.originalTextSize)
        assertEquals(LyricsComponentConfig.DEFAULT, allConfigs[LyricsComponent.FLOATING])
    }

    // ===== resolveConfig =====

    @Test
    fun resolveConfig_noLinkedTo_returnsOwnConfig() = runTest {
        val config = LyricsComponentConfig(originalTextSize = 20)
        settingsRepository.saveLyricsPlayerConfig(json.encodeToString(LyricsComponentConfig.serializer(), config))

        val resolved = useCase.resolveConfig(LyricsComponent.PLAYER)
        assertEquals(20, resolved.originalTextSize)
    }

    @Test
    fun resolveConfig_withLinkedTo_followsLink() = runTest {
        val playerConfig = LyricsComponentConfig(originalTextSize = 20)
        val fullscreenConfig = LyricsComponentConfig(linkedTo = "player")
        settingsRepository.saveLyricsPlayerConfig(json.encodeToString(LyricsComponentConfig.serializer(), playerConfig))
        settingsRepository.saveLyricsFullscreenConfig(json.encodeToString(LyricsComponentConfig.serializer(), fullscreenConfig))

        val resolved = useCase.resolveConfig(LyricsComponent.FULLSCREEN)
        assertEquals(20, resolved.originalTextSize)
    }

    @Test
    fun resolveConfig_circularLink_returnsOwnConfig() = runTest {
        val playerConfig = LyricsComponentConfig(originalTextSize = 20, linkedTo = "fullscreen")
        val fullscreenConfig = LyricsComponentConfig(originalTextSize = 24, linkedTo = "floating")
        settingsRepository.saveLyricsPlayerConfig(json.encodeToString(LyricsComponentConfig.serializer(), playerConfig))
        settingsRepository.saveLyricsFullscreenConfig(json.encodeToString(LyricsComponentConfig.serializer(), fullscreenConfig))

        val resolved = useCase.resolveConfig(LyricsComponent.PLAYER)
        assertEquals(20, resolved.originalTextSize)
    }

    // ===== resetComponentToDefault =====

    @Test
    fun resetComponentToDefault_resetsSavedConfig() = runTest {
        val custom = LyricsComponentConfig(originalTextSize = 99)
        useCase.saveComponentConfig(LyricsComponent.PLAYER, custom)
        assertEquals(99, useCase.getComponentConfig(LyricsComponent.PLAYER).first().originalTextSize)

        useCase.resetComponentToDefault(LyricsComponent.PLAYER)
        val config = useCase.getComponentConfig(LyricsComponent.PLAYER).first()
        assertEquals(LyricsComponentConfig.DEFAULT, config)
    }

    // ===== floatingLyricsEnabled =====

    @Test
    fun floatingLyricsEnabled_defaultIsFalse() = runTest {
        val enabled = useCase.floatingLyricsEnabled.first()
        assertFalse(enabled)
    }

    @Test
    fun floatingLyricsEnabled_saveAndRetrieve() = runTest {
        useCase.saveFloatingLyricsEnabled(true)
        assertTrue(useCase.floatingLyricsEnabled.first())

        useCase.saveFloatingLyricsEnabled(false)
        assertFalse(useCase.floatingLyricsEnabled.first())
    }

    // ===== D4-01：per-component 配置是唯一读路径 =====

    @Test
    fun configFlow_player_readsComponentConfig_notLegacyKeys() = runTest {
        val legacySizeBefore = settingsRepository.getLyricsOriginalTextSize()
        val legacyKaraokeBefore = settingsRepository.getLyricsKaraokeEnabled()

        settingsRepository.saveLyricsPlayerConfig(
            json.encodeToString(
                LyricsComponentConfig.serializer(),
                LyricsComponentConfig(originalTextSize = 20, lineSpacing = 11, karaokeEnabled = false),
            ),
        )

        val effective = useCase.configFlow(LyricsComponent.PLAYER).first()

        assertEquals(20, effective.originalTextSize)
        assertEquals(11, effective.lineSpacing)
        assertFalse(effective.karaokeEnabled)
        // 两套存储互不影响：写组件配置不动 legacy 键 —— 这正是 D4-01 立案时"改了不生效"的根因
        assertEquals(legacySizeBefore, settingsRepository.getLyricsOriginalTextSize())
        assertEquals(legacyKaraokeBefore, settingsRepository.getLyricsKaraokeEnabled())
    }

    @Test
    fun configFlow_player_resolvesLinkedToChain() = runTest {
        settingsRepository.saveLyricsPlayerConfig(
            json.encodeToString(
                LyricsComponentConfig.serializer(),
                LyricsComponentConfig(linkedTo = LyricsComponent.FULLSCREEN.key),
            ),
        )
        settingsRepository.saveLyricsFullscreenConfig(
            json.encodeToString(LyricsComponentConfig.serializer(), LyricsComponentConfig(originalTextSize = 26)),
        )

        assertEquals(26, useCase.configFlow(LyricsComponent.PLAYER).first().originalTextSize)
    }

    @Test
    fun updateComponentConfig_isReadModifyWrite_notFullOverwrite() = runTest {
        val initial = LyricsComponentConfig(
            originalTextSize = 15,
            lineSpacing = 9,
            karaokeEnabled = false,
            linkedTo = LyricsComponent.FLOATING.key,
        )
        settingsRepository.saveLyricsFullscreenConfig(
            json.encodeToString(LyricsComponentConfig.serializer(), initial),
        )

        useCase.updateComponentConfig(LyricsComponent.FULLSCREEN) { it.copy(originalTextSize = 30) }

        val saved = useCase.getComponentConfig(LyricsComponent.FULLSCREEN).first()
        assertEquals(30, saved.originalTextSize)
        assertEquals(9, saved.lineSpacing)
        assertFalse(saved.karaokeEnabled)
        assertEquals(LyricsComponent.FLOATING.key, saved.linkedTo)
    }

    @Test
    fun resetComponentToDefault_onlyResetsThatComponent() = runTest {
        settingsRepository.saveLyricsPlayerConfig(
            json.encodeToString(LyricsComponentConfig.serializer(), LyricsComponentConfig(originalTextSize = 20)),
        )
        settingsRepository.saveLyricsFullscreenConfig(
            json.encodeToString(LyricsComponentConfig.serializer(), LyricsComponentConfig(originalTextSize = 24)),
        )

        useCase.resetComponentToDefault(LyricsComponent.FULLSCREEN)

        assertEquals(20, useCase.getComponentConfig(LyricsComponent.PLAYER).first().originalTextSize)
        assertEquals(LyricsComponentConfig.DEFAULT, useCase.getComponentConfig(LyricsComponent.FULLSCREEN).first())
    }
}
