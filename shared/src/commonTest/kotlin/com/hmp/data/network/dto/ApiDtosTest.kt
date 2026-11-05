package com.hmp.data.network.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun openAiStyleRequest_serialization() {
        val request = OpenAiStyleRequest(
            model = "gpt-4o",
            messages = listOf(OpenAiMessage(role = "user", content = "Hello")),
            temperature = 0.7f,
        )
        val jsonStr = json.encodeToString(OpenAiStyleRequest.serializer(), request)
        assertTrue(jsonStr.contains("gpt-4o"))
        assertTrue(jsonStr.contains("Hello"))
    }

    @Test
    fun openAiStyleResponse_deserialization() {
        val jsonStr = """{"id":"chatcmpl-123","choices":[{"message":{"role":"assistant","content":"Hi"}}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}"""
        val response = json.decodeFromString(OpenAiStyleResponse.serializer(), jsonStr)
        assertEquals("chatcmpl-123", response.id)
        assertEquals("Hi", response.choices?.first()?.message?.content)
        assertEquals(10, response.usage?.promptTokens)
        assertEquals(15, response.usage?.totalTokens)
    }

    @Test
    fun openAiStyleResponse_nullChoices() {
        val jsonStr = """{"id": "test"}"""
        val response = json.decodeFromString(OpenAiStyleResponse.serializer(), jsonStr)
        assertEquals("test", response.id)
        assertNull(response.choices)
    }

    @Test
    fun modelsResponse_deserialization() {
        val jsonStr = """{"data":[{"id":"gpt-4o","owned_by":"openai"},{"id":"gpt-4o-mini","owned_by":"openai"}]}"""
        val response = json.decodeFromString(ModelsResponse.serializer(), jsonStr)
        assertEquals(2, response.data?.size)
        assertEquals("gpt-4o", response.data?.get(0)?.id)
        assertEquals("openai", response.data?.get(0)?.ownedBy)
    }

    @Test
    fun modelsResponse_nullData() {
        val response = json.decodeFromString(ModelsResponse.serializer(), """{}""")
        assertNull(response.data)
    }

    @Test
    fun musicInfoResponse_deserialization() {
        val jsonStr = """{"genre":["Rock","Pop"],"mood":["Energetic"],"scenario":["Workout"],"language":"English","era":"2020s"}"""
        val response = json.decodeFromString(MusicInfoResponse.serializer(), jsonStr)
        assertEquals(2, response.genre.size)
        assertEquals("English", response.language)
    }

    @Test
    fun musicInfoResponse_defaults() {
        val response = json.decodeFromString(MusicInfoResponse.serializer(), """{}""")
        assertTrue(response.genre.isEmpty())
        assertEquals("", response.language)
    }

    @Test
    fun openAiUsage_defaults() {
        val usage = OpenAiUsage()
        assertEquals(0, usage.promptTokens)
        assertEquals(0, usage.totalTokens)
    }

    @Test
    fun modelItem_deserialization() {
        val item = json.decodeFromString(ModelItem.serializer(), """{"id":"gpt-4o","owned_by":"openai"}""")
        assertEquals("gpt-4o", item.id)
        assertEquals("openai", item.ownedBy)
    }
}
