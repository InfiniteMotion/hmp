package com.hmp.domain.enum

import kotlinx.serialization.Serializable

/**
 * AI 预设端点（OpenAI 兼容格式）
 * 仅作为快捷填入的预设值，实际调用统一走 OpenAI 兼容接口
 */
object AiPresetEndpoints {
    data class Preset(
        val displayName: String,
        val endpoint: String,
        val defaultModel: String,
    )

    val DEEPSEEK = Preset(
        displayName = "DeepSeek",
        endpoint = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
    )

    val OPENAI = Preset(
        displayName = "OpenAI",
        endpoint = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
    )

    val ALL = listOf(DEEPSEEK, OPENAI)
}

/**
 * AI 服务商类型枚举。
 *
 * ⚠️ iOS 桥接保留款：3ef88b19 重构（统一 OpenAI 兼容）后在 Kotlin 侧移除本枚举，
 * 但 iOS SwiftUI 设置页（AIScreen / SettingsViewModel）仍引用；方向 A（KMP 重写 iOS）
 * 7.2 A9 删除对应 SwiftUI 页面时一并移除。
 */
@Serializable
enum class AiProviderType(
    val displayName: String,
    val defaultModel: String,
    val defaultEndpoint: String,
) {
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultModel = "deepseek-chat",
        defaultEndpoint = "https://api.deepseek.com/v1/chat/completions",
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-3.5-turbo",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
    ),
    CLAUDE(
        displayName = "Claude",
        defaultModel = "claude-3-haiku-20240307",
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
    ),
    QWEN(
        displayName = "通义千问",
        defaultModel = "qwen-turbo",
        defaultEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
    ),
    ERNIE(
        displayName = "文心一言",
        defaultModel = "ernie-bot-4",
        defaultEndpoint = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-bot-4",
    ),
    ;

    companion object {
        /** 根据名称获取服务商类型，默认返回 DEEPSEEK */
        fun fromName(name: String): AiProviderType = entries.find { it.name == name } ?: DEEPSEEK
    }
}
