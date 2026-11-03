package com.hearablemusic.player.ui.agent.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.api_key_invalid
import com.hearablemusic.player.ui.generated.resources.connected
import com.hearablemusic.player.ui.generated.resources.fetch_models_failed
import com.hearablemusic.player.ui.generated.resources.fetched_n_models
import com.hearablemusic.player.ui.generated.resources.test_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * AI 设置页（AIScreen）的页面级 ViewModel。
 * 包含 AI 接入配置与自动批处理。
 */
class AiSettingsViewModel(
    private val userSettingsUseCase: UserSettingsUseCase,
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    private val quotaGate: com.hmp.domain.agent.quota.TokenQuotaGate,
) : ViewModel() {

    // AI Config
    val aiAccessMode: StateFlow<AiAccessMode> = userSettingsUseCase.aiAccessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiAccessMode.FREE)

    /** 免费额度快照（100M 总额，来自账本回填，重启不归零）。 */
    val quotaUsage: StateFlow<com.hmp.domain.agent.runtime.TokenSnapshot> = quotaGate.usage

    /** 内置端点是否可用 —— 免费入口的显示条件是"有可用的内置服务"，不是平台。 */
    val freeAvailable: StateFlow<Boolean> = flow {
        emit(userSettingsUseCase.isBuiltInAiConfigured())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 全局 Agent 配置（含自定义限额）。 */
    private val _globalConfig = MutableStateFlow(com.hmp.domain.agent.runtime.GlobalAgentConfig())
    val globalConfig: StateFlow<com.hmp.domain.agent.runtime.GlobalAgentConfig> = _globalConfig

    init {
        // 首次进设置页就把额度从账本回填，否则显示的是 0 / 100M 的初始值
        viewModelScope.launch { quotaGate.snapshot() }
        viewModelScope.launch {
            _globalConfig.value = userSettingsUseCase.getGlobalAgentConfig()
        }
    }

    /** 保存自定义限额；空 map / 0 表示不限。 */
    fun saveTokenLimits(
        enabled: Boolean,
        globalDailyQuota: Int,
        perAgentDailyQuota: Map<String, Long>,
    ) {
        viewModelScope.launch {
            val updated = _globalConfig.value.copy(
                tokenLimitsEnabled = enabled,
                dailyTokenQuota = globalDailyQuota,
                perAgentDailyTokenQuota = perAgentDailyQuota,
            )
            userSettingsUseCase.saveGlobalAgentConfig(updated)
            _globalConfig.value = updated
        }
    }

    private val _customAiConfig = MutableStateFlow(AiEndpointConfig())
    val customAiConfig: StateFlow<AiEndpointConfig> = _customAiConfig

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels

    fun loadCustomAiConfig() {
        viewModelScope.launch {
            _customAiConfig.value = userSettingsUseCase.getCustomAiConfig()
        }
    }

    fun switchAiAccessMode(mode: AiAccessMode) {
        viewModelScope.launch {
            userSettingsUseCase.saveAiAccessMode(mode)
        }
    }

    fun saveCustomAiConfig(endpoint: String, apiKey: String, model: String) {
        viewModelScope.launch {
            val config = AiEndpointConfig(
                endpoint = endpoint,
                apiKey = apiKey,
                selectedModel = model,
                isConfigured = endpoint.isNotBlank() && apiKey.isNotBlank()
            )
            userSettingsUseCase.saveCustomAiConfig(config)
            _customAiConfig.value = config
        }
    }

    fun fetchAvailableModels(endpoint: String, apiKey: String) {
        viewModelScope.launch {
            _isTestingApi.value = true
            try {
                val config = AiEndpointConfig(endpoint = endpoint, apiKey = apiKey, isConfigured = true)
                val result = getDailyRecommendationUseCase.fetchModels(config)
                result.onSuccess { models ->
                    _availableModels.value = models
                    _apiTestResult.value = ApiTestResult.Success(getString(Res.string.fetched_n_models, models.size))
                }.onFailure { e ->
                    _apiTestResult.value = ApiTestResult.Error(getString(Res.string.fetch_models_failed, e.message ?: ""))
                }
            } catch (e: Exception) {
                _apiTestResult.value = ApiTestResult.Error(getString(Res.string.fetch_models_failed, e.message ?: ""))
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    // API Test
    sealed class ApiTestResult {
        data class Success(val message: String) : ApiTestResult()
        data class Error(val message: String) : ApiTestResult()
    }

    private val _apiTestResult = MutableStateFlow<ApiTestResult?>(null)
    val apiTestResult: StateFlow<ApiTestResult?> = _apiTestResult

    private val _isTestingApi = MutableStateFlow(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi

    fun testAiConnection(endpoint: String, apiKey: String) {
        viewModelScope.launch {
            _isTestingApi.value = true
            _apiTestResult.value = null

            try {
                val config = AiEndpointConfig(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    isConfigured = true
                )

                val isValid = getDailyRecommendationUseCase.validateProviderApiKey(config)
                _apiTestResult.value = if (isValid) {
                    ApiTestResult.Success(getString(Res.string.connected))
                } else {
                    ApiTestResult.Error(getString(Res.string.api_key_invalid))
                }
            } catch (e: Exception) {
                _apiTestResult.value = ApiTestResult.Error(getString(Res.string.test_failed, e.message ?: ""))
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    fun clearApiTestResult() {
        _apiTestResult.value = null
    }

    // Auto Batch Process
    val autoBatchProcess: StateFlow<Boolean> = userSettingsUseCase.autoBatchProcess
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveAutoBatchProcess(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsUseCase.saveAutoBatchProcess(enabled)
        }
    }
}
