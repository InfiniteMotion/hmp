package com.hearablemusic.player.ui.agent.config

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmp.domain.agent.config.ResolvedAgentConfig
import com.hmp.domain.agent.policy.AgentPolicyConfig
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Agent 配置页整屏状态（7.5 P1）。
 *
 * `config` 是 `AgentPolicyConfig.snapshot()` 得到的**不可变副本** —— 不再持有
 * `MasterAgent.getAgentPolicyConfig()` 返回的内部可变实例（§1.3 边界）。
 * 19 处原本散在 composable 里的表单状态全部收在这里。
 */
@Stable
data class AgentConfigUiState(
    val role: String = "",
    val config: AgentPolicyConfig = AgentPolicyConfig(),
    val endpointLoaded: Boolean = false,
    val useCustomEndpoint: Boolean = false,
    val customEndpoint: String = "",
    val customApiKey: String = "",
    val customModel: String = "",
    val globalEndpoint: AiEndpointConfig = AiEndpointConfig(),
    val temperature: Float = 0f,
    val stepBudget: Int = 0,
    val dailyRecommendCount: Int = 1,
    val recommendListSize: Int = 8,
    val targetCoverage: Float = 0f,
    val targetCount: Int = 0,
    val autoRenew: Boolean = false,
    val preferredLang: String = "global",
    val agentEnabled: Boolean = true,
    val trustLevel: Int = 0,
    val systemPrompt: String = "",
    val promptDirty: Boolean = false,
    val multiPromptTexts: Map<String, String> = emptyMap(),
    val multiPromptDirty: Set<String> = emptySet(),
    /** 全局回复语言：生效语言提示用。 */
    val globalReplyLang: String = "",
    /** 出厂默认 prompt（当前语言）——VM 派生，UI 只读。 */
    val factoryPrompt: String = "",
    /** hello 多卡型各条目的出厂默认 prompt。 */
    val multiPromptFactories: Map<String, String> = emptyMap(),
    /** 单 prompt 是否已被用户覆盖。 */
    val hasOverride: Boolean = false,
    /** 单 prompt 的占位符校验；systemPrompt 为空或无占位符时为 null。 */
    val placeholderCheck: PlaceholderCheck? = null,
) {
    val resolved: ResolvedAgentConfig get() = config.resolvedFor(role)
}

/** 单 prompt 的占位符校验结果；UI 只负责渲染，不再自己解析模板。 */
data class PlaceholderCheck(
    val required: List<String>,
    val present: List<String>,
    val missing: List<String>,
)

class AgentConfigViewModel(
    private val masterAgent: MasterAgent,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentConfigUiState())
    val uiState: StateFlow<AgentConfigUiState> = _uiState.asStateFlow()

    /** 导航参数由 Screen 在 `LaunchedEffect(agentRole)` 里上送（同 RecommendListViewModel 口径）。 */
    fun onRoleChanged(role: String) {
        val config = masterAgent.getAgentPolicyConfig(role).snapshot()
        val resolved = config.resolvedFor(role)
        val globalReplyLang = masterAgent.getGlobalAgentConfig().replyLanguage
        val entries = helloPromptGroups().flatMap { it.second }
        _uiState.value = derive(
            AgentConfigUiState(
                role = role,
                config = config,
                temperature = resolved.temperature,
                stepBudget = resolved.runtimeParams.stepBudget,
                dailyRecommendCount = resolved.runtimeParams.dailyRecommendCount,
                recommendListSize = resolved.runtimeParams.recommendListSize,
                targetCoverage = resolved.runtimeParams.targetCoverage,
                targetCount = resolved.runtimeParams.targetCount,
                autoRenew = resolved.runtimeParams.autoRenew,
                preferredLang = resolved.preferredLang,
                agentEnabled = config.enabled,
                trustLevel = resolved.trustLevel,
                systemPrompt = resolveEffectivePrompt(
                    role = role,
                    preferredLang = resolved.preferredLang,
                    globalReplyLang = globalReplyLang,
                    promptOverrides = resolved.promptOverrides,
                ),
                multiPromptTexts = if (isMultiPromptAgent(role)) {
                    entries.associate {
                        it.key to effectivePromptFor(it.key, resolved.preferredLang, globalReplyLang, resolved.promptOverrides)
                    }
                } else {
                    emptyMap()
                },
                globalReplyLang = globalReplyLang,
            ),
        )
        viewModelScope.launch {
            val perAgent = settingsRepo.getAgentEndpointConfig(role)
            _uiState.update {
                it.copy(
                    useCustomEndpoint = perAgent != null,
                    customEndpoint = perAgent?.endpoint ?: "",
                    customApiKey = perAgent?.apiKey ?: "",
                    customModel = perAgent?.selectedModel ?: "",
                    endpointLoaded = true,
                    globalEndpoint = settingsRepo.getActiveAiConfig(),
                )
            }
        }
    }

    // ── 表单动作 ──
    fun onAgentEnabledChange(value: Boolean) = update { it.copy(agentEnabled = value) }

    fun onPreferredLangChange(newLang: String) {
        val state = _uiState.value
        val role = state.role
        val globalReplyLang = masterAgent.getGlobalAgentConfig().replyLanguage
        if (isMultiPromptAgent(role)) {
            val entries = helloPromptGroups().flatMap { it.second }
            update { s ->
                s.copy(
                    preferredLang = newLang,
                    multiPromptTexts = entries.associate { entry ->
                        val keepEdited = s.multiPromptDirty.contains(entry.key)
                        entry.key to if (keepEdited) {
                            s.multiPromptTexts[entry.key].orEmpty()
                        } else {
                            effectivePromptFor(entry.key, newLang, globalReplyLang, emptyMap())
                        }
                    },
                )
            }
        } else {
            val hasOverride = !state.resolved.promptOverrides[defaultPromptKeyFor(role)].isNullOrBlank()
            update { s ->
                s.copy(
                    preferredLang = newLang,
                    systemPrompt = if (!s.promptDirty && !hasOverride) {
                        resolveEffectivePrompt(role, newLang, globalReplyLang, emptyMap())
                    } else {
                        s.systemPrompt
                    },
                )
            }
        }
    }

    fun onTemperatureChange(value: Float) = update { it.copy(temperature = (value * 100).toInt() / 100f) }

    fun onUseCustomEndpointChange(value: Boolean) = update { it.copy(useCustomEndpoint = value) }

    fun onCustomEndpointChange(value: String) = update { it.copy(customEndpoint = value) }

    fun onCustomApiKeyChange(value: String) = update { it.copy(customApiKey = value) }

    fun onCustomModelChange(value: String) = update { it.copy(customModel = value) }

    fun onTrustLevelChange(value: Int) = update { it.copy(trustLevel = value) }

    fun onStepBudgetChange(value: Int) = update { it.copy(stepBudget = value) }

    fun onDailyRecommendCountChange(value: Int) = update { it.copy(dailyRecommendCount = value) }

    fun onRecommendListSizeChange(value: Int) = update { it.copy(recommendListSize = value) }

    fun onTargetCoverageChange(value: Float) = update { it.copy(targetCoverage = (value * 100).toInt() / 100f) }

    fun onTargetCountChange(value: Int) = update { it.copy(targetCount = value) }

    fun onAutoRenewChange(value: Boolean) = update { it.copy(autoRenew = value) }

    fun onSystemPromptChange(value: String) = update { it.copy(systemPrompt = value, promptDirty = true) }

    /** 恢复出厂默认（仍标记为脏，保存时按「与出厂一致」移除覆盖）。 */
    fun onRestoreFactoryPrompt() = update { it.copy(systemPrompt = it.factoryPrompt, promptDirty = true) }

    /** 放弃改动：文本回到出厂默认，清脏标记。 */
    fun onDiscardPromptChanges() = update { it.copy(systemPrompt = it.factoryPrompt, promptDirty = false) }

    fun onMultiPromptEntryChange(key: String, text: String) = update {
        it.copy(
            multiPromptTexts = it.multiPromptTexts + (key to text),
            multiPromptDirty = it.multiPromptDirty + key,
        )
    }

    fun onMultiPromptRestoreFactory(key: String) = update {
        it.copy(
            multiPromptTexts = it.multiPromptTexts + (key to it.multiPromptFactories[key].orEmpty()),
            multiPromptDirty = it.multiPromptDirty + key,
        )
    }

    /** 放弃改动：回到用户已保存的覆盖值；没有覆盖时回出厂默认。 */
    fun onMultiPromptRevert(key: String) = update {
        val original = it.resolved.promptOverrides[key] ?: it.multiPromptFactories[key].orEmpty()
        it.copy(
            multiPromptTexts = it.multiPromptTexts + (key to original),
            multiPromptDirty = it.multiPromptDirty - key,
        )
    }

    /** 每次状态变更后重算派生值：出厂 prompt、多卡型出厂值、覆盖位与占位符校验。 */
    private fun update(transform: (AgentConfigUiState) -> AgentConfigUiState) {
        _uiState.update { derive(transform(it)) }
    }

    private fun derive(s: AgentConfigUiState): AgentConfigUiState {
        val factoryPrompt = resolveEffectivePrompt(
            role = s.role,
            preferredLang = s.preferredLang,
            globalReplyLang = s.globalReplyLang,
            promptOverrides = emptyMap(),
        )
        val multiFactories = if (isMultiPromptAgent(s.role)) {
            helloPromptGroups().flatMap { it.second }.associate { entry ->
                entry.key to effectivePromptFor(entry.key, s.preferredLang, s.globalReplyLang, emptyMap())
            }
        } else {
            emptyMap()
        }
        val required = factoryPrompt.extractPlaceholders().toList()
        val check = if (s.systemPrompt.isNotBlank() && required.isNotEmpty()) {
            val present = required.filter { s.systemPrompt.contains("{{$it}}") }
            PlaceholderCheck(
                required = required,
                present = present,
                missing = required.filter { it !in present },
            )
        } else {
            null
        }
        return s.copy(
            factoryPrompt = factoryPrompt,
            multiPromptFactories = multiFactories,
            hasOverride = !s.resolved.promptOverrides[defaultPromptKeyFor(s.role)].isNullOrBlank(),
            placeholderCheck = check,
        )
    }

    // ── 保存 / 重置 ──
    fun onSave() {
        val s = _uiState.value
        val role = s.role
        val cfg = s.config.snapshot()
        cfg.temperature = s.temperature
        cfg.preferredLang = s.preferredLang
        cfg.enabled = s.agentEnabled
        cfg.trustLevel = s.trustLevel
        when (role) {
            "master" -> cfg.runtimeParams["stepBudget"] = s.stepBudget.toString()

            "hello" -> {
                cfg.runtimeParams["dailyRecommendCount"] = s.dailyRecommendCount.toString()
                cfg.runtimeParams["recommendListSize"] = s.recommendListSize.toString()
            }

            "enrich" -> cfg.runtimeParams["targetCoverage"] = s.targetCoverage.toString()

            "radio" -> {
                cfg.runtimeParams["targetCount"] = s.targetCount.toString()
                cfg.runtimeParams["autoRenew"] = s.autoRenew.toString()
            }
        }
        if (isMultiPromptAgent(role)) {
            helloPromptGroups().flatMap { it.second }.forEach { entry ->
                val edited = s.multiPromptTexts[entry.key].orEmpty()
                // 与 UI 展示的出厂默认同源，避免「显示 zh 模板、判定却拿 global 模板」
                val factory = s.multiPromptFactories[entry.key].orEmpty()
                val sameAsFactory = factory.isNotBlank() && edited.trim() == factory.trim()
                if (edited.isBlank() || sameAsFactory) {
                    cfg.promptOverrides.remove(entry.key)
                } else {
                    cfg.promptOverrides[entry.key] = edited
                }
            }
        } else {
            val promptKey = defaultPromptKeyFor(role)
            val factory = s.factoryPrompt
            val missing = s.placeholderCheck?.missing.orEmpty()
            val sameAsFactory = factory.isNotBlank() && s.systemPrompt.trim() == factory.trim()
            when {
                s.systemPrompt.isBlank() || missing.isNotEmpty() || sameAsFactory ->
                    cfg.promptOverrides.remove(promptKey)

                else -> cfg.promptOverrides[promptKey] = s.systemPrompt
            }
        }
        viewModelScope.launch {
            masterAgent.saveAgentPolicyConfig(role, cfg)
            if (s.useCustomEndpoint && s.customEndpoint.isNotBlank()) {
                settingsRepo.saveAgentEndpointConfig(
                    role,
                    AiEndpointConfig(
                        endpoint = s.customEndpoint.trim(),
                        apiKey = s.customApiKey,
                        selectedModel = s.customModel.trim(),
                        isConfigured = s.customEndpoint.isNotBlank() && s.customApiKey.isNotBlank(),
                    ),
                )
            } else {
                settingsRepo.saveAgentEndpointConfig(role, null)
            }
            masterAgent.updateAiConfig()
            // 保存后按落盘结果重载整份表单：promptDirty / multiPromptDirty 复位，
            // 出厂模板、覆盖位与占位符检查一并重算 —— 否则「未保存」徽标在保存成功后继续挂着。
            onRoleChanged(role)
        }
    }

    fun onReset() {
        val role = _uiState.value.role
        val original = masterAgent.getAgentPolicyConfig(role)
        val cfg = AgentPolicyConfig()
        cfg.temperature = null // 回落默认
        cfg.runtimeParams.clear()
        cfg.promptOverrides.clear()
        cfg.preferredLang = "global"
        // 保持 trustLevel / alwaysAllow 不变（那是权限组，不属于"行为参数"）
        cfg.trustLevel = original.trustLevel
        cfg.alwaysAllow.addAll(original.alwaysAllow)
        viewModelScope.launch {
            masterAgent.saveAgentPolicyConfig(role, cfg)
            settingsRepo.saveAgentEndpointConfig(role, null)
            masterAgent.updateAiConfig()
            onRoleChanged(role)
        }
    }
}
