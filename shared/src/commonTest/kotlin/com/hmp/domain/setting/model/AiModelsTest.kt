package com.hmp.domain.setting.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiEndpointConfigTest {

    @Test
    fun defaultValues() {
        val config = AiEndpointConfig()
        assertEquals("", config.endpoint)
        assertEquals("", config.apiKey)
        assertEquals("", config.selectedModel)
        assertTrue(config.availableModels.isEmpty())
        assertFalse(config.isConfigured)
    }

    @Test
    fun customValues() {
        val config = AiEndpointConfig(
            endpoint = "https://api.example.com/v1",
            apiKey = "sk-test-key",
            selectedModel = "gpt-4o",
            availableModels = listOf("gpt-4o", "gpt-4o-mini"),
            isConfigured = true
        )
        assertEquals("https://api.example.com/v1", config.endpoint)
        assertEquals("sk-test-key", config.apiKey)
        assertEquals("gpt-4o", config.selectedModel)
        assertEquals(2, config.availableModels.size)
        assertTrue(config.isConfigured)
    }
}

class AiAccessModeTest {

    @Test
    fun allModes_exist() {
        val modes = AiAccessMode.entries
        assertEquals(3, modes.size)
        assertEquals(AiAccessMode.FREE, modes[0])
        assertEquals(AiAccessMode.CUSTOM, modes[1])
        assertEquals(AiAccessMode.PAID, modes[2])
    }
}
