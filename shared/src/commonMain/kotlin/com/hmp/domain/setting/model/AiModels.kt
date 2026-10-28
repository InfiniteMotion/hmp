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
    val isConfigured: Boolean = false,
    /**
     * 存过密钥但**解不开** —— Android 换机 / 云备份恢复后的常态（D7-01）。
     *
     * 必须有这个三态区分，不能只看 `apiKey.isEmpty()`：
     * "用户存了空 key"与"密文回来了、密钥没回来"都表现为空串，
     * 但前者什么都不该提示、后者必须告诉用户"请重新填写 API Key"——
     * 否则用户看到的是"我明明填过，怎么又空了"。
     */
    val keyNeedsRefill: Boolean = false
)

