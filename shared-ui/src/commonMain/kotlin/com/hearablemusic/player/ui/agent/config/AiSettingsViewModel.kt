package com.hearablemusic.player.ui.agent.config

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.api_key_invalid
import com.hearablemusic.player.ui.generated.resources.config_saved
import com.hearablemusic.player.ui.generated.resources.connected
import com.hearablemusic.player.ui.generated.resources.fetch_models_failed
import com.hearablemusic.player.ui.generated.resources.fetched_n_models
import com.hearablemusic.player.ui.generated.resources.please_enter_api_key
import com.hearablemusic.player.ui.generated.resources.test_failed
import com.hmp.domain.agent.profile.PortraitDraft
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AI 设置页的表单草稿（7.5 P1 第三笔）。
 *
 * 原分散在 `CustomConfigContent` / `TokenLimitsCard` / `ReplyLanguageSection` /
 * `MemoryManagementSection` 四个区块的 `remember` 状态全部收到这里。
 * `showPassword` / `presetExpanded` / `modelExpanded` / `expanded` 属视觉可见性白名单，仍在 UI。
 */
@Stable
data class AIScreenFormState(
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val tokenLimitsEnabled: Boolean = false,
    val globalDailyLimit: String = "",
    val perAgentLimits: Map<String, String> = emptyMap(),
    val tokenLimitsSaved: Boolean = false,
    val memoryEnabled: Boolean = true,
    val portraits: List<PortraitDraft> = emptyList(),
    val showConfirmClear: Boolean = false,
    val replyLanguage: String = "",
)

/** AI 设置页的一次性效果（§4：不用 `StateFlow<Result?>` + clear 模拟）。 */
sealed interface AiSettingsEffect {
    data class ShowMessage(val text: UiText) : AiSettingsEffect
}

/** AI 设置页整屏状态（7.5 P1）：数据态 + 表单草稿收成唯一出口。 */
@Stable
data class AiSettingsUiState(
    val aiAccessMode: AiAccessMode = AiAccessMode.FREE,
    val quotaUsage: com.hmp.domain.agent.runtime.TokenSnapshot = com.hmp.domain.agent.runtime.TokenSnapshot(0L, 0L),
    val freeAvailable: Boolean = true,
    val globalConfig: com.hmp.domain.agent.runtime.GlobalAgentConfig = com.hmp.domain.agent.runtime.GlobalAgentConfig(),
    val customAiConfig: AiEndpointConfig = AiEndpointConfig(),
    val availableModels: List<String> = emptyList(),
    val isTestingApi: Boolean = false,
    val form: AIScreenFormState = AIScreenFormState(),
)

/**
 * AI 设置页（AIScreen）的页面级 ViewModel。
 *
 * 7.5 P1 第三笔：表单草稿与 `MasterAgent` 业务调用全部收进 VM，composable 只上送 `onXxx()`。
 */
class AiSettingsViewModel(
    private val userSettingsUseCase: UserSettingsUseCase,
    private val getDailyRecommendationUseCase: GetDailyMusicRecommendationUseCase,
    private val quotaGate: com.hmp.domain.agent.quota.TokenQuotaGate,
    private val masterAgent: MasterAgent,
) : ViewModel() {

    // ── 数据源（全部私有，只经 [uiState] 暴露）──
    private val aiAccessMode: StateFlow<AiAccessMode> = userSettingsUseCase.aiAccessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiAccessMode.FREE)

    private val quotaUsage: StateFlow<com.hmp.domain.agent.runtime.TokenSnapshot> = quotaGate.usage

    private val freeAvailable: StateFlow<Boolean> = flow {
        emit(userSettingsUseCase.isBuiltInAiConfigured())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _globalConfig = MutableStateFlow(com.hmp.domain.agent.runtime.GlobalAgentConfig())
    private val _customAiConfig = MutableStateFlow(AiEndpointConfig())
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())

    /** 一次性效果：原来用 `StateFlow<ApiTestResult?>` + `clearApiTestResult()` 模拟，违反 §4。 */
    private val _effect = MutableSharedFlow<AiSettingsEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<AiSettingsEffect> = _effect.asSharedFlow()
    private val _isTestingApi = MutableStateFlow(false)
    private val _formState = MutableStateFlow(AIScreenFormState())

    private val baseUiState: Flow<AiSettingsUiState> = combine(
        aiAccessMode,
        quotaUsage,
        freeAvailable,
        _globalConfig,
        _customAiConfig,
    ) { mode, quota, available, global, custom ->
        AiSettingsUiState(
            aiAccessMode = mode,
            quotaUsage = quota,
            freeAvailable = available,
            globalConfig = global,
            customAiConfig = custom,
        )
    }

    // 表单单独一路：数据态刷新时不会把用户正在编辑的草稿覆盖回默认值。
    private val dataUiState: Flow<AiSettingsUiState> = combine(baseUiState, _formState) { base, form ->
        base.copy(form = form)
    }

    /** 唯一状态出口；首帧取各源 StateFlow 的当前值，不另造一份默认值口径。 */
    val uiState: StateFlow<AiSettingsUiState> = combine(
        dataUiState,
        _availableModels,
        _isTestingApi,
    ) { base, models, testing ->
        base.copy(
            availableModels = models,
            isTestingApi = testing,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AiSettingsUiState(
            aiAccessMode = aiAccessMode.value,
            quotaUsage = quotaUsage.value,
            freeAvailable = freeAvailable.value,
            globalConfig = _globalConfig.value,
            customAiConfig = _customAiConfig.value,
            availableModels = _availableModels.value,
            isTestingApi = _isTestingApi.value,
            form = _formState.value,
        ),
    )

    init {
        viewModelScope.launch { quotaGate.snapshot() }
        viewModelScope.launch {
            val config = userSettingsUseCase.getGlobalAgentConfig()
            _globalConfig.value = config
            _formState.update {
                it.copy(
                    tokenLimitsEnabled = config.tokenLimitsEnabled,
                    globalDailyLimit = if (config.dailyTokenQuota > 0) config.dailyTokenQuota.toString() else "",
                    perAgentLimits = config.perAgentDailyTokenQuota.mapValues { (_, v) -> v.toString() },
                )
            }
        }
        viewModelScope.launch {
            val config = masterAgent.getGlobalAgentConfig()
            _formState.update { it.copy(memoryEnabled = config.memoryEnabled, replyLanguage = config.replyLanguage) }
            loadPortraits()
        }
    }

    private fun updateForm(transform: (AIScreenFormState) -> AIScreenFormState) {
        _formState.update(transform)
    }

    private suspend fun emitMessage(text: UiText) {
        // tryEmit 在 extraBufferCapacity 满时静默丢弃；提示类事件用 emit，宁可等一拍。
        _effect.emit(AiSettingsEffect.ShowMessage(text))
    }

    private suspend fun loadPortraits() {
        val portraits = runCatching { masterAgent.userMemory?.currentPortraits().orEmpty() }
            .getOrDefault(emptyList())
        _formState.update { it.copy(portraits = portraits) }
    }

    // ── 自定义接入表单 ──
    fun onEndpointChange(value: String) = updateForm { it.copy(endpoint = value) }

    fun onApiKeyChange(value: String) = updateForm { it.copy(apiKey = value) }

    fun onModelChange(value: String) = updateForm { it.copy(model = value) }

    fun onPresetSelected(endpoint: String, model: String) = updateForm {
        it.copy(endpoint = endpoint, model = model)
    }

    private var customConfigLoaded = false

    fun loadCustomAiConfig() {
        // 只在首次进入时灌一次草稿：重复组合/重进页面都不允许覆盖用户已经输入的内容。
        if (customConfigLoaded) return
        customConfigLoaded = true
        viewModelScope.launch {
            val config = userSettingsUseCase.getCustomAiConfig()
            _customAiConfig.value = config
            _formState.update {
                it.copy(
                    endpoint = if (it.endpoint.isBlank()) config.endpoint else it.endpoint,
                    model = if (it.model.isBlank()) config.selectedModel else it.model,
                )
            }
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
                isConfigured = endpoint.isNotBlank() && apiKey.isNotBlank(),
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
                    _formState.update {
                        if (it.model.isBlank()) it.copy(model = models.firstOrNull().orEmpty()) else it
                    }
                    emitMessage(Res.string.fetched_n_models.asUiText(models.size))
                }.onFailure { e ->
                    emitMessage(Res.string.fetch_models_failed.asUiText(e.message ?: ""))
                }
            } catch (e: Exception) {
                emitMessage(Res.string.fetch_models_failed.asUiText(e.message ?: ""))
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    /** UI 只上送「点了哪个按钮」：参数校验与提示都由 VM 走 AiSettingsEffect（§3 / §4）。 */
    fun onFetchModelsClicked() {
        val form = _formState.value
        if (form.endpoint.isBlank() || form.apiKey.isBlank()) {
            viewModelScope.launch { emitMessage(Res.string.please_enter_api_key.asUiText()) }
            return
        }
        fetchAvailableModels(form.endpoint, form.apiKey)
    }

    fun onTestConnectionClicked() {
        val form = _formState.value
        if (form.endpoint.isBlank() || form.apiKey.isBlank()) {
            viewModelScope.launch { emitMessage(Res.string.please_enter_api_key.asUiText()) }
            return
        }
        testAiConnection(form.endpoint, form.apiKey)
    }

    fun onSaveCustomConfigClicked() {
        val form = _formState.value
        if (form.endpoint.isBlank() || form.apiKey.isBlank()) {
            viewModelScope.launch { emitMessage(Res.string.please_enter_api_key.asUiText()) }
            return
        }
        saveCustomAiConfig(form.endpoint, form.apiKey, form.model)
        viewModelScope.launch { emitMessage(Res.string.config_saved.asUiText()) }
    }

    // API Test
    private fun testAiConnection(endpoint: String, apiKey: String) {
        viewModelScope.launch {
            _isTestingApi.value = true
            try {
                val config = AiEndpointConfig(endpoint = endpoint, apiKey = apiKey, isConfigured = true)
                val isValid = getDailyRecommendationUseCase.validateProviderApiKey(config)
                emitMessage(
                    if (isValid) {
                        Res.string.connected.asUiText()
                    } else {
                        Res.string.api_key_invalid.asUiText()
                    },
                )
            } catch (e: Exception) {
                emitMessage(Res.string.test_failed.asUiText(e.message ?: ""))
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    // ── 自定义限额表单 ──
    fun onTokenLimitsEnabledChange(enabled: Boolean) = updateForm {
        it.copy(tokenLimitsEnabled = enabled, tokenLimitsSaved = false)
    }

    fun onGlobalDailyLimitChange(value: String) = updateForm {
        it.copy(globalDailyLimit = value.filter(Char::isDigit), tokenLimitsSaved = false)
    }

    fun onPerAgentLimitChange(key: String, value: String) = updateForm {
        it.copy(
            perAgentLimits = it.perAgentLimits + (key to value.filter(Char::isDigit)),
            tokenLimitsSaved = false,
        )
    }

    fun onSaveTokenLimits() {
        val form = _formState.value
        val perAgent = if (form.tokenLimitsEnabled) {
            form.perAgentLimits.mapNotNull { (key, value) ->
                value.toLongOrNull()?.takeIf { it > 0 }?.let { key to it }
            }.toMap()
        } else {
            emptyMap()
        }
        saveTokenLimits(form.tokenLimitsEnabled, form.globalDailyLimit.toIntOrNull() ?: 0, perAgent)
        _formState.update { it.copy(tokenLimitsSaved = true) }
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

    // ── 回复语言 / 记忆管理（业务调用全在 VM）──
    fun onReplyLanguageChange(key: String) {
        _formState.update { it.copy(replyLanguage = key) }
        viewModelScope.launch {
            masterAgent.saveGlobalAgentConfig(masterAgent.getGlobalAgentConfig().copy(replyLanguage = key))
        }
    }

    fun onMemoryEnabledChange(enabled: Boolean) {
        _formState.update { it.copy(memoryEnabled = enabled) }
        viewModelScope.launch {
            masterAgent.saveGlobalAgentConfig(masterAgent.getGlobalAgentConfig().copy(memoryEnabled = enabled))
            loadPortraits()
        }
    }

    fun onForgetPortrait(portraitId: String) {
        viewModelScope.launch {
            masterAgent.forgetPortrait(portraitId)
            loadPortraits()
        }
    }

    fun onClearMemoryRequested() = updateForm { it.copy(showConfirmClear = true) }

    fun onClearMemoryDismissed() = updateForm { it.copy(showConfirmClear = false) }

    fun onClearAllMemory() {
        _formState.update { it.copy(showConfirmClear = false, portraits = emptyList()) }
        viewModelScope.launch {
            masterAgent.clearAllMemory()
            loadPortraits()
        }
    }
}
