package com.hmp.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltInApiKeyProviderTest {

    @Test
    fun getConfig_withKey_returnsConfigured() {
        val provider = BuiltInApiKeyProvider(
            endpoint = "https://api.example.com",
            apiKey = "sk-test123",
            model = "gpt-4",
        )
        val config = provider.getConfig()
        assertTrue(config.isConfigured)
        assertEquals("https://api.example.com", config.endpoint)
        assertEquals("sk-test123", config.apiKey)
        assertEquals("gpt-4", config.selectedModel)
    }

    @Test
    fun getConfig_emptyKey_returnsNotConfigured() {
        val provider = BuiltInApiKeyProvider(
            endpoint = "https://api.example.com",
            apiKey = "",
            model = "gpt-4",
        )
        val config = provider.getConfig()
        assertFalse(config.isConfigured)
    }

    @Test
    fun getConfig_blankKey_returnsNotConfigured() {
        val provider = BuiltInApiKeyProvider(
            endpoint = "https://api.example.com",
            apiKey = "   ",
            model = "gpt-4",
        )
        val config = provider.getConfig()
        assertFalse(config.isConfigured)
    }

    @Test
    fun getConfig_defaults_returnsEmptyConfig() {
        val provider = BuiltInApiKeyProvider()
        val config = provider.getConfig()
        assertFalse(config.isConfigured)
        assertEquals("", config.endpoint)
        assertEquals("", config.apiKey)
        assertEquals("", config.selectedModel)
    }
}
