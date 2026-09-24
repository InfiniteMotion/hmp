package com.hmp.domain.setting.usecase

import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserSettingsUseCaseTest {

    private val settingsRepository = FakeSettingsRepository()
    private val useCase = UserSettingsUseCase(settingsRepository)

    @Test
    fun customMode_defaultValue() = runTest {
        assertEquals("default", useCase.customMode.first())
    }

    @Test
    fun saveAndReadThemeMode() = runTest {
        useCase.saveThemeMode("dark")
        assertEquals("dark", useCase.customMode.first())
    }

    @Test
    fun userName_defaultEmpty() = runTest {
        assertEquals("", useCase.userName.first())
    }

    @Test
    fun saveAndReadUserName() = runTest {
        useCase.saveUserName("Alice")
        assertEquals("Alice", useCase.userName.first())
    }

    @Test
    fun isFirstLaunch_defaultTrue() = runTest {
        assertTrue(useCase.isFirstLaunch.first())
    }

    @Test
    fun saveIsFirstLaunch_updates() = runTest {
        useCase.saveIsFirstLaunch(false)
        assertFalse(useCase.isFirstLaunch.first())
    }

    @Test
    fun backgroundStyle_defaultFluid() = runTest {
        assertEquals("FLUID", useCase.backgroundStyle.first())
    }

    @Test
    fun hazeMode_defaultCustom() = runTest {
        assertEquals("custom", useCase.hazeMode.first())
    }

    @Test
    fun saveAndReadHazeMode() = runTest {
        useCase.saveHazeMode("none")
        assertEquals("none", useCase.hazeMode.first())
    }

    @Test
    fun aiAccessMode_defaultFree() = runTest {
        assertEquals(AiAccessMode.FREE, useCase.aiAccessMode.first())
    }

    @Test
    fun saveAndReadAiAccessMode() = runTest {
        useCase.saveAiAccessMode(AiAccessMode.CUSTOM)
        assertEquals(AiAccessMode.CUSTOM, useCase.getAiAccessMode())
    }

    @Test
    fun aiFreeTrialRemainingCount_default100() = runTest {
        assertEquals(100, useCase.aiFreeTrialRemainingCount.first())
    }

    @Test
    fun decrementAiFreeTrialCount_decrements() = runTest {
        useCase.decrementAiFreeTrialCount()
        assertEquals(99, useCase.aiFreeTrialRemainingCount.first())
    }

    @Test
    fun decrementAiFreeTrialCount_clampsAtZero() = runTest {
        repeat(101) { useCase.decrementAiFreeTrialCount() }
        assertEquals(0, useCase.aiFreeTrialRemainingCount.first())
    }

    @Test
    fun saveAndReadCustomAiConfig() = runTest {
        val config = AiEndpointConfig(endpoint = "https://api.test.com", apiKey = "key123", selectedModel = "gpt-4")
        useCase.saveCustomAiConfig(config)
        val loaded = useCase.getCustomAiConfig()
        assertEquals("https://api.test.com", loaded.endpoint)
        assertEquals("key123", loaded.apiKey)
    }

    @Test
    fun getActiveAiConfig_freeMode_returnsConfigured() = runTest {
        val config = useCase.getActiveAiConfig()
        assertTrue(config.isConfigured)
    }

    @Test
    fun getActiveAiConfig_customMode_returnsCustomConfig() = runTest {
        val custom = AiEndpointConfig(endpoint = "https://custom.api", apiKey = "key", selectedModel = "model1")
        useCase.saveCustomAiConfig(custom)
        useCase.saveAiAccessMode(AiAccessMode.CUSTOM)
        val config = useCase.getActiveAiConfig()
        assertEquals("https://custom.api", config.endpoint)
    }

    @Test
    fun isLoadMusic_defaultFalse() = runTest {
        assertFalse(useCase.isLoadMusic.first())
    }

    @Test
    fun saveAndReadIsLoadMusic() = runTest {
        useCase.saveIsLoadMusic(true)
        assertTrue(useCase.isLoadMusic.first())
    }

    @Test
    fun autoBatchProcess_defaultTrue() = runTest {
        assertTrue(useCase.autoBatchProcess.first())
    }

    @Test
    fun saveAndReadAutoBatchProcess() = runTest {
        useCase.saveAutoBatchProcess(false)
        assertFalse(useCase.autoBatchProcess.first())
    }
}
