package com.hmp.data.network

import com.hmp.domain.setting.model.AiEndpointConfig

/**
 * 内置 API Key 配置（免费体验 / 付费模式共用）
 * 通过 Koin 注入，各平台提供自己的实现
 */
class BuiltInApiKeyProvider(
    private val endpoint: String = "",
    private val apiKey: String = "",
    private val model: String = "",
) {
    fun getConfig(): AiEndpointConfig = AiEndpointConfig(
        endpoint = endpoint,
        apiKey = apiKey,
        selectedModel = model,
        isConfigured = apiKey.isNotBlank(),
    )
}
