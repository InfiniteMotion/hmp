package com.hmp.di

import com.hmp.domain.enum.AiProviderType
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import org.koin.mp.KoinPlatform

/**
 * 旧 SwiftUI 设置页（P9 冻结，方向 A 7.2 A9 删除）的 AI 配置适配桥。
 *
 * 3ef88b19 后 Kotlin 侧 AI 配置为 AiAccessMode + AiEndpointConfig（统一 OpenAI 兼容 +
 * 免费体验模式），旧 SwiftUI 页面仍按 5 服务商枚举交互；本桥将旧语义映射到新存储：
 * - 切换服务商 → CUSTOM 模式 + 保存预设端点
 * - 读取 → FREE 回退 DEEPSEEK；CUSTOM 按端点前缀反查类型
 * - apiKey / model 原样透传（不回退、不清空）
 */
class IosSettingsBridge(
    private val useCase: UserSettingsUseCase,
) {
    suspend fun getCurrentProvider(): AiProviderType {
        val mode = useCase.getAiAccessMode()
        if (mode == AiAccessMode.FREE) return AiProviderType.DEEPSEEK
        val config = useCase.getCustomAiConfig()
        return providerOfEndpoint(config.endpoint)
    }

    suspend fun setCurrentProvider(provider: AiProviderType, apiKey: String, model: String) {
        useCase.saveAiAccessMode(AiAccessMode.CUSTOM)
        val old = useCase.getCustomAiConfig()
        useCase.saveCustomAiConfig(
            AiEndpointConfig(
                endpoint = provider.defaultEndpoint,
                apiKey = apiKey.ifEmpty { old.apiKey },
                selectedModel = model.ifEmpty { old.selectedModel },
                availableModels = old.availableModels,
                isConfigured = apiKey.isNotEmpty() || old.isConfigured,
            ),
        )
    }

    suspend fun getProviderConfig(): IosProviderConfigSnapshot {
        val config = useCase.getCustomAiConfig()
        return IosProviderConfigSnapshot(
            apiKey = config.apiKey,
            model = config.selectedModel,
            isConfigured = config.isConfigured,
        )
    }

    suspend fun saveProviderConfig(apiKey: String, model: String) {
        useCase.saveAiAccessMode(AiAccessMode.CUSTOM)
        val old = useCase.getCustomAiConfig()
        useCase.saveCustomAiConfig(
            old.copy(
                apiKey = apiKey,
                selectedModel = model,
                isConfigured = apiKey.isNotEmpty(),
            ),
        )
    }
}

/** Swift 侧读取快照（旧页面仅消费 apiKey/model/isConfigured）。 */
data class IosProviderConfigSnapshot(
    val apiKey: String,
    val model: String,
    val isConfigured: Boolean,
)

/** 按端点前缀反查服务商（预设端点为完整 /chat/completions URL，前缀比对本桥适用）。 */
private fun providerOfEndpoint(endpoint: String): AiProviderType = AiProviderType.entries.firstOrNull { provider ->
    endpoint.startsWith(provider.defaultEndpoint.takeWhile { it != '?' })
} ?: AiProviderType.DEEPSEEK

// ── Swift 顶层入口（KoinHelper 同款模式） ──

suspend fun getIosCurrentProvider(): AiProviderType = bridge().getCurrentProvider()

suspend fun setIosCurrentProvider(provider: AiProviderType, apiKey: String, model: String) = bridge().setCurrentProvider(provider, apiKey, model)

suspend fun getIosProviderConfig(): IosProviderConfigSnapshot = bridge().getProviderConfig()

suspend fun saveIosProviderConfig(apiKey: String, model: String) = bridge().saveProviderConfig(apiKey, model)

private fun bridge(): IosSettingsBridge = IosSettingsBridge(KoinPlatform.getKoin().get<UserSettingsUseCase>())
