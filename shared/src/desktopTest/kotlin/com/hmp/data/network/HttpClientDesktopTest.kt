package com.hmp.data.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpClientDesktopTest {

    @Test
    fun createJson_returnsConfiguredInstance() {
        val json = createJson()
        assertNotNull(json)
        assertTrue(json.configuration.ignoreUnknownKeys)
        assertTrue(json.configuration.isLenient)
        assertTrue(json.configuration.encodeDefaults)
    }

    @Test
    fun createHttpClient_returnsWorkingClient() {
        val json = createJson()
        val client = createHttpClient(json)
        assertNotNull(client)
        client.close()
    }

    @Test
    fun createHttpClient_hasTimeoutPlugin() {
        val json = createJson()
        val client = createHttpClient(json)
        val plugin = client.pluginOrNull(HttpTimeout)
        assertNotNull(plugin, "HttpTimeout plugin should be installed")
        client.close()
    }

    @Test
    fun createJson_ignoresUnknownKeys() {
        @kotlinx.serialization.Serializable
        data class Simple(val name: String)
        val json = createJson()
        val result = json.decodeFromString<Simple>("""{"name": "test", "unknown": 42}""")
        assertEquals("test", result.name)
    }

    @Test
    fun createJson_encodesDefaults() {
        @kotlinx.serialization.Serializable
        data class WithDefault(val name: String, val count: Int = 42)
        val json = createJson()
        val encoded = json.encodeToString(WithDefault.serializer(), WithDefault("test"))
        assertTrue(encoded.contains("42"), "encodeDefaults should include default values")
    }
}
