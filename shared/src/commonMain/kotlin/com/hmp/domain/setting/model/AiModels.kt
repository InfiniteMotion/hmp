package com.hmp.domain.setting.model

import kotlinx.serialization.Serializable

/**
 * AI 访问模式
 */
enum class AiAccessMode {
    /** 免费体验（内置 Key，100 次额度） */
    FREE,
    /** 自定义配置（用户自填 endpoint + key） */
    CUSTOM,
    /** 付费模式（预留） */
    PAID
}

/**
 * AI 端点配置（统一 OpenAI 兼容格式）
 */
@Serializable
data class AiEndpointConfig(
    val endpoint: String = "",
    val apiKey: String = "",
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val isConfigured: Boolean = false
)

