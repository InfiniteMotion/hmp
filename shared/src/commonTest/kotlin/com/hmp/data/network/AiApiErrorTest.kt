package com.hmp.data.network

import kotlin.test.Test
import kotlin.test.assertTrue

class AiApiErrorTest {

    @Test
    fun networkError_displayMessage() {
        val error = AiApiError.NetworkError("连接超时")
        assertTrue(error.toDisplayMessage().contains("网络"))
        assertTrue(error.toDisplayMessage().contains("连接超时"))
    }

    @Test
    fun authError_displayMessage() {
        val error = AiApiError.AuthError("invalid key")
        assertTrue(error.toDisplayMessage().contains("认证"))
    }

    @Test
    fun rateLimitError_displayMessage() {
        val error = AiApiError.RateLimitError("too many requests")
        assertTrue(error.toDisplayMessage().contains("频繁"))
    }

    @Test
    fun serverError_displayMessage() {
        val error = AiApiError.ServerError(500, "internal error")
        val msg = error.toDisplayMessage()
        assertTrue(msg.contains("服务器"))
        assertTrue(msg.contains("500"))
    }

    @Test
    fun parseError_displayMessage() {
        val error = AiApiError.ParseError("invalid json")
        assertTrue(error.toDisplayMessage().contains("解析"))
    }

    @Test
    fun unknownError_displayMessage() {
        val error = AiApiError.UnknownError("something weird")
        val msg = error.toDisplayMessage()
        assertTrue(msg.contains("未知"))
        assertTrue(msg.contains("something weird"))
    }
}
