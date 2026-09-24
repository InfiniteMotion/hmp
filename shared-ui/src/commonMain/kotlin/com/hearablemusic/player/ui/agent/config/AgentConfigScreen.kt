package com.hearablemusic.player.ui.agent.config
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.agent_ai_collapse
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_auto
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_en
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_zh
import com.hearablemusic.player.ui.generated.resources.agent_cfg_all_default
import com.hearablemusic.player.ui.generated.resources.agent_cfg_always_allow_count
import com.hearablemusic.player.ui.generated.resources.agent_cfg_auto_renew
import com.hearablemusic.player.ui.generated.resources.agent_cfg_builtin
import com.hearablemusic.player.ui.generated.resources.agent_cfg_change_global_hint
import com.hearablemusic.player.ui.generated.resources.agent_cfg_config_suffix
import com.hearablemusic.player.ui.generated.resources.agent_cfg_count_tracks
import com.hearablemusic.player.ui.generated.resources.agent_cfg_customized_count
import com.hearablemusic.player.ui.generated.resources.agent_cfg_daily_count
import com.hearablemusic.player.ui.generated.resources.agent_cfg_daily_count_value
import com.hearablemusic.player.ui.generated.resources.agent_cfg_desc_enrich
import com.hearablemusic.player.ui.generated.resources.agent_cfg_desc_hello
import com.hearablemusic.player.ui.generated.resources.agent_cfg_desc_master
import com.hearablemusic.player.ui.generated.resources.agent_cfg_desc_radio
import com.hearablemusic.player.ui.generated.resources.agent_cfg_disabled_hint
import com.hearablemusic.player.ui.generated.resources.agent_cfg_discard_changes
import com.hearablemusic.player.ui.generated.resources.agent_cfg_edit
import com.hearablemusic.player.ui.generated.resources.agent_cfg_effective_lang
import com.hearablemusic.player.ui.generated.resources.agent_cfg_endpoint_config
import com.hearablemusic.player.ui.generated.resources.agent_cfg_endpoint_custom
import com.hearablemusic.player.ui.generated.resources.agent_cfg_endpoint_url_label
import com.hearablemusic.player.ui.generated.resources.agent_cfg_endpoint_value
import com.hearablemusic.player.ui.generated.resources.agent_cfg_following_global
import com.hearablemusic.player.ui.generated.resources.agent_cfg_hello_prompt_desc
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_desc
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_follow_global
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_follow_global_default
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_follow_system
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_global_auto
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_global_en
import com.hearablemusic.player.ui.generated.resources.agent_cfg_lang_global_zh
import com.hearablemusic.player.ui.generated.resources.agent_cfg_language
import com.hearablemusic.player.ui.generated.resources.agent_cfg_loading
import com.hearablemusic.player.ui.generated.resources.agent_cfg_missing_placeholder
import com.hearablemusic.player.ui.generated.resources.agent_cfg_model_label
import com.hearablemusic.player.ui.generated.resources.agent_cfg_model_value
import com.hearablemusic.player.ui.generated.resources.agent_cfg_placeholder_default
import com.hearablemusic.player.ui.generated.resources.agent_cfg_placeholders_needed
import com.hearablemusic.player.ui.generated.resources.agent_cfg_playlist_len
import com.hearablemusic.player.ui.generated.resources.agent_cfg_prompt_desc
import com.hearablemusic.player.ui.generated.resources.agent_cfg_radio_params
import com.hearablemusic.player.ui.generated.resources.agent_cfg_reco_params
import com.hearablemusic.player.ui.generated.resources.agent_cfg_restore_default
import com.hearablemusic.player.ui.generated.resources.agent_cfg_restore_factory
import com.hearablemusic.player.ui.generated.resources.agent_cfg_save
import com.hearablemusic.player.ui.generated.resources.agent_cfg_status_default
import com.hearablemusic.player.ui.generated.resources.agent_cfg_status_overridden
import com.hearablemusic.player.ui.generated.resources.agent_cfg_status_unsaved
import com.hearablemusic.player.ui.generated.resources.agent_cfg_step_budget
import com.hearablemusic.player.ui.generated.resources.agent_cfg_step_budget_value
import com.hearablemusic.player.ui.generated.resources.agent_cfg_target_count
import com.hearablemusic.player.ui.generated.resources.agent_cfg_target_cov
import com.hearablemusic.player.ui.generated.resources.agent_cfg_temperature
import com.hearablemusic.player.ui.generated.resources.agent_cfg_trust_act
import com.hearablemusic.player.ui.generated.resources.agent_cfg_trust_desc
import com.hearablemusic.player.ui.generated.resources.agent_cfg_trust_silent
import com.hearablemusic.player.ui.generated.resources.agent_cfg_trust_suggest
import com.hearablemusic.player.ui.generated.resources.agent_cfg_trust_title
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_card
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_card_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_essay
import com.hearablemusic.player.ui.generated.resources.agent_hello_forgotten_essay_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_artist
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_artist_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_fact
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_fact_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_history
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_history_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_listen
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_listen_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_lyric_gold
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_lyric_gold_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_quote
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_quote_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_story
import com.hearablemusic.player.ui.generated.resources.agent_hello_greeting_story_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_group_forgotten
import com.hearablemusic.player.ui.generated.resources.agent_hello_group_greeting
import com.hearablemusic.player.ui.generated.resources.agent_hello_group_recommend
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_full
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_full_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_list
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_list_hint
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_short
import com.hearablemusic.player.ui.generated.resources.agent_hello_recommend_short_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hmp.domain.agent.policy.AgentPolicyConfig
import com.hmp.domain.agent.policy.TrustLevel
import com.hmp.domain.agent.config.EngineDefaults
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.i18n.resolvePrompt
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 通用 Agent 配置页——按 agentRole 参数决定渲染哪个 Agent 的配置表单。
 *
 * agentRole 取值："master" / "hello" / "enrich" / "radio"
 * 每个 role 渲染不同参数组 + 可选 prompt 编辑 + 可选状态只读卡。
 */
@Composable
fun AgentConfigScreen(
    agentRole: String,
    navController: NavBackStack<NavKey>,
    masterAgent: MasterAgent = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val settingsRepo: SettingsRepository = koinInject()

    // ── per-Agent 策略配置 ──
    // 注意：getAgentPolicyConfig 返回的是 MasterAgent 内部持有的**可变实例本身**，
    // 因此这里用可变状态持有「当前实例引用」，save/reset 后重新取一次并回填，
    // 避免 reset 换新实例后 UI 仍指着旧实例（表现为"改了没变化"）。
    var policyConfig by remember { mutableStateOf(masterAgent.getAgentPolicyConfig(agentRole)) }
    val resolved = policyConfig.resolvedFor(agentRole)

    // ── AI 端点配置（per-Agent 独立 endpoint / null = 跟随全局）──
    // 初始值走异步加载（LaunchedEffect），不在首次组合时 runBlocking 阻塞主线程。
    var endpointLoaded by remember { mutableStateOf(false) }
    var useCustomEndpoint by remember { mutableStateOf(false) }
    var customEndpoint by remember { mutableStateOf("") }
    var customApiKey by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }
    // 全局默认 endpoint 只读预览（同样异步加载）
    var globalEndpoint by remember { mutableStateOf(AiEndpointConfig()) }

    // ── 本地状态（从 policyConfig 读初始值）──
    var temperature by remember { mutableFloatStateOf(resolved.temperature) }
    var stepBudget by remember { mutableIntStateOf(resolved.runtimeParams.stepBudget) }
    var dailyRecommendCount by remember { mutableIntStateOf(resolved.runtimeParams.dailyRecommendCount) }
    var recommendListSize by remember { mutableIntStateOf(resolved.runtimeParams.recommendListSize) }
    var targetCoverage by remember { mutableFloatStateOf(resolved.runtimeParams.targetCoverage) }
    var targetCount by remember { mutableIntStateOf(resolved.runtimeParams.targetCount) }
    var autoRenew by remember { mutableStateOf(resolved.runtimeParams.autoRenew) }
    var preferredLang by remember { mutableStateOf(resolved.preferredLang) }
    // Agent 启用开关
    var agentEnabled by remember { mutableStateOf(policyConfig.enabled) }
    // 权限与信任（每个 Agent 各自持有 trustLevel）
    var trustLevel by remember { mutableIntStateOf(resolved.trustLevel) }
    // 全局回复语言（供 system prompt 生效语言提示用；纯内存读，避免在 if 内重复调用）
    val globalReplyLang = masterAgent.getGlobalAgentConfig().replyLanguage

    // ── Prompt 编辑状态 ──
    // 单 prompt Agent（master/enrich/radio）：一个编辑框
    // 多卡型 Agent（hello）：key → 编辑中文本 的 Map，分组逐项编辑
    val multiPrompt = isMultiPromptAgent(agentRole)

    // 单 prompt：编辑框初始内容 = 当前实际生效的 prompt（用户覆盖优先，否则按语言解析出厂默认）。
    // 以 preferredLang 为 key：切换语言时同步刷新为对应语言的出厂默认。
    val factoryPrompt = remember(agentRole, preferredLang) {
        resolveEffectivePrompt(
            role = agentRole,
            preferredLang = preferredLang,
            globalReplyLang = globalReplyLang,
            promptOverrides = emptyMap(),  // 强制取出厂默认，不受用户覆盖影响
        )
    }
    val hasOverride = !resolved.promptOverrides[defaultPromptKeyFor(agentRole)].isNullOrBlank()
    var systemPrompt by remember(agentRole) {
        mutableStateOf(
            resolveEffectivePrompt(
                role = agentRole,
                preferredLang = resolved.preferredLang,
                globalReplyLang = globalReplyLang,
                promptOverrides = resolved.promptOverrides,
            )
        )
    }
    // 用户是否已经动过编辑框（用于区分"未改动"与"改回原样的空"）
    var promptDirty by remember(agentRole) { mutableStateOf(false) }

    // 多卡型：每项编辑框的文本（key → 当前编辑值），初值 = 生效文本
    val helloEntries = remember(agentRole) {
        helloPromptGroups().flatMap { it.second }
    }
    var multiPromptTexts by remember(agentRole) {
        mutableStateOf(
            helloEntries.associate { it.key to effectivePromptFor(it.key, resolved.preferredLang, globalReplyLang, resolved.promptOverrides) }
        )
    }
    // 哪些项被用户手动改过（决定保存时是否落覆盖）
    var multiPromptDirty by remember(agentRole) { mutableStateOf(emptySet<String>()) }
    // 当前展开编辑的项（一次只开一个，避免 12 个长文本框把页面撑爆）
    var expandedPromptKey by remember(agentRole) { mutableStateOf<String?>(null) }

    // 首次进入：异步加载 per-Agent endpoint 与全局 endpoint（含密钥解密，不可在主线程做）
    LaunchedEffect(agentRole) {
        val perAgent = settingsRepo.getAgentEndpointConfig(agentRole)
        useCustomEndpoint = perAgent != null
        customEndpoint = perAgent?.endpoint ?: ""
        customApiKey = perAgent?.apiKey ?: ""
        customModel = perAgent?.selectedModel ?: ""
        endpointLoaded = true
        globalEndpoint = settingsRepo.getActiveAiConfig()
    }

    val save = {
        val cfg = masterAgent.getAgentPolicyConfig(agentRole)
        cfg.temperature = temperature
        cfg.preferredLang = preferredLang
        cfg.enabled = agentEnabled
        // 权限与信任对所有 Agent 都渲染，故在 when 之外统一写入
        // （此前只写在 master 分支 → Hello/Enrich/Radio 页的档位是假交互）
        cfg.trustLevel = trustLevel
        when (agentRole) {
            "master" -> {
                cfg.runtimeParams["stepBudget"] = stepBudget.toString()
            }
            "hello" -> {
                cfg.runtimeParams["dailyRecommendCount"] = dailyRecommendCount.toString()
                cfg.runtimeParams["recommendListSize"] = recommendListSize.toString()
            }
            "enrich" -> { cfg.runtimeParams["targetCoverage"] = targetCoverage.toString() }
            "radio" -> {
                cfg.runtimeParams["targetCount"] = targetCount.toString()
                cfg.runtimeParams["autoRenew"] = autoRenew.toString()
            }
        }
        // Prompt 落库判定：
        //  - 多卡型（hello）→ 按 key 逐项判定（见下）
        //  - 内容与出厂默认一致 → 移除覆盖（回落出厂默认，避免存一份冗余副本）
        //  - 内容为空 或 缺失必需占位符 → 移除覆盖（引擎运行时注入会失败，回落更安全）
        //  - 其余情况 → 写入完全替换
        if (multiPrompt) {
            helloEntries.forEach { entry ->
                val edited = multiPromptTexts[entry.key].orEmpty()
                val factory = effectivePromptFor(entry.key, "global", globalReplyLang, emptyMap())
                    .ifBlank { effectivePromptFor(entry.key, preferredLang, globalReplyLang, emptyMap()) }
                val sameAsFactory = factory.isNotBlank() && edited.trim() == factory.trim()
                if (edited.isBlank() || sameAsFactory) {
                    cfg.promptOverrides.remove(entry.key)
                } else {
                    cfg.promptOverrides[entry.key] = edited
                }
            }
        } else {
            val promptKey = defaultPromptKeyFor(agentRole)
            val templateIsBlank = factoryPrompt.isBlank()
            val missing = factoryPrompt.extractPlaceholders()
                .filter { !systemPrompt.contains("{{$it}}") }
            val sameAsFactory = !templateIsBlank && systemPrompt.trim() == factoryPrompt.trim()
            when {
                systemPrompt.isBlank() || missing.isNotEmpty() || sameAsFactory ->
                    cfg.promptOverrides.remove(promptKey)
                else -> cfg.promptOverrides[promptKey] = systemPrompt
            }
        }
        scope.launch {
            masterAgent.saveAgentPolicyConfig(agentRole, cfg)
            // Per-Agent AI endpoint config
            if (useCustomEndpoint && customEndpoint.isNotBlank()) {
                settingsRepo.saveAgentEndpointConfig(
                    agentRole,
                    AiEndpointConfig(
                        endpoint = customEndpoint.trim(),
                        apiKey = customApiKey,
                        selectedModel = customModel.trim(),
                        isConfigured = customEndpoint.isNotBlank() && customApiKey.isNotBlank()
                    )
                )
            } else {
                settingsRepo.saveAgentEndpointConfig(agentRole, null)  // 清除 → 跟随全局
            }
            // 热更新所有 Agent 的 config（从 SettingsRepository 重读）
            masterAgent.updateAiConfig()
            // 回填 UI 持有的实例引用，保证展示值（信任档位/免确认项数）与落盘一致
            policyConfig = masterAgent.getAgentPolicyConfig(agentRole)
        }; Unit
    }

    val reset = {
        val original = masterAgent.getAgentPolicyConfig(agentRole)
        val cfg = AgentPolicyConfig()
        cfg.temperature = null  // 回落默认
        cfg.runtimeParams.clear()
        cfg.promptOverrides.clear()
        cfg.preferredLang = "global"
        // 保持 trustLevel / alwaysAllow 不变（那是权限组，不属于"行为参数"）
        cfg.trustLevel = original.trustLevel
        cfg.alwaysAllow.addAll(original.alwaysAllow)
        scope.launch {
            masterAgent.saveAgentPolicyConfig(agentRole, cfg)
            settingsRepo.saveAgentEndpointConfig(agentRole, null)  // 清除 per-Agent endpoint → 跟随全局
            masterAgent.updateAiConfig()
            // 关键：saveAgentPolicyConfig 已把 MasterAgent 内部字段换成新实例，
            // 此处必须回填，否则 UI 仍指着旧实例（重置后展示值与再次保存都会错乱）
            policyConfig = masterAgent.getAgentPolicyConfig(agentRole)
        }; Unit
        // 本地状态也重置
        temperature = EngineDefaults.defaultTemperatureFor(agentRole)
        stepBudget = EngineDefaults.STEP_BUDGET
        dailyRecommendCount = 1
        recommendListSize = 8
        targetCoverage = EngineDefaults.ENRICH_TARGET_COVERAGE
        targetCount = EngineDefaults.RADIO_TARGET_COUNT
        autoRenew = EngineDefaults.RADIO_AUTO_RENEW
        // prompt 编辑框恢复为「出厂默认文本」（语言也回落 global，故按 global 重新解析）
        systemPrompt = resolveEffectivePrompt(
            role = agentRole,
            preferredLang = "global",
            globalReplyLang = globalReplyLang,
            promptOverrides = emptyMap(),
        )
        promptDirty = false
        // 多卡型：所有项恢复为出厂默认文本，并清空改动标记
        multiPromptTexts = helloEntries.associate {
            it.key to effectivePromptFor(it.key, "global", globalReplyLang, emptyMap())
        }
        multiPromptDirty = emptySet()
        expandedPromptKey = null
        preferredLang = "global"
        agentEnabled = true
        useCustomEndpoint = false
        customEndpoint = ""
        customApiKey = ""
        customModel = ""
        trustLevel = original.trustLevel
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = agentLabelFor(agentRole) + stringResource(Res.string.agent_cfg_config_suffix),
    ) {
        val dimens = LocalHMPDimens.current
        // F14-T1 自适应：竖屏/窄窗单栏限宽居中；横屏（手机横屏 / 平板、桌面宽窗）改双栏，
        // 左栏 = 短表单（身份/模型/端点/权限/专属参数），右栏 = System Prompt 编辑器，
        // 底部操作横贯两栏。Compact maxWidth = Dp.Unspecified → 单栏不加约束，与改造前逐像素一致。
        val window = LocalWindowSizeInfo.current
        val isWide = window.isExpanded || window.isMedium
        val isLandscape = window.isLandscape
        val formMaxWidth = if (isWide) 600.dp else Dp.Unspecified
        // 双栏下「宽行」（多行 Prompt 编辑器）独占一栏，宽度即栏宽，不再另外限宽。
        val wideRowMaxWidth = if (isWide && !isLandscape) 720.dp else Dp.Unspecified
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        if (isLandscape) {
        // ── 横屏双栏 ──
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = dimens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            ) {
                ConfigFormLeftColumn(
                    agentRole = agentRole,
                    agentEnabled = agentEnabled,
                    onAgentEnabledChange = { agentEnabled = it },
                    preferredLang = preferredLang,
                    onPreferredLangSelected = { newLang ->
                        preferredLang = newLang
                        if (multiPrompt) {
                            multiPromptTexts = helloEntries.associate { entry ->
                                val keepEdited = multiPromptDirty.contains(entry.key)
                                entry.key to if (keepEdited) {
                                    multiPromptTexts[entry.key].orEmpty()
                                } else {
                                    effectivePromptFor(entry.key, newLang, globalReplyLang, emptyMap())
                                }
                            }
                        } else if (!promptDirty && !hasOverride) {
                            systemPrompt = resolveEffectivePrompt(
                                role = agentRole,
                                preferredLang = newLang,
                                globalReplyLang = globalReplyLang,
                                promptOverrides = emptyMap(),
                            )
                        }
                    },
                    temperature = temperature,
                    onTemperatureChange = { temperature = (it * 100).toInt() / 100f },
                    useCustomEndpoint = useCustomEndpoint,
                    onUseCustomEndpointChange = { useCustomEndpoint = it },
                    customEndpoint = customEndpoint,
                    onCustomEndpointChange = { customEndpoint = it },
                    customApiKey = customApiKey,
                    onCustomApiKeyChange = { customApiKey = it },
                    customModel = customModel,
                    onCustomModelChange = { customModel = it },
                    endpointLoaded = endpointLoaded,
                    globalEndpoint = globalEndpoint,
                    trustLevel = trustLevel,
                    onTrustLevelChange = { trustLevel = it },
                    alwaysAllowCount = policyConfig.alwaysAllow.size,
                    stepBudget = stepBudget,
                    onStepBudgetChange = { stepBudget = it },
                    dailyRecommendCount = dailyRecommendCount,
                    onDailyRecommendCountChange = { dailyRecommendCount = it },
                    recommendListSize = recommendListSize,
                    onRecommendListSizeChange = { recommendListSize = it },
                    targetCoverage = targetCoverage,
                    onTargetCoverageChange = { targetCoverage = (it * 100).toInt() / 100f },
                    targetCount = targetCount,
                    onTargetCountChange = { targetCount = it },
                    autoRenew = autoRenew,
                    onAutoRenewChange = { autoRenew = it },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacing.md),
                ) {
                    SystemPromptSection(
                        multiPrompt = multiPrompt,
                        helloEntries = helloEntries,
                        resolvedOverrides = resolved.promptOverrides,
                        expandedPromptKey = expandedPromptKey,
                        onExpandedPromptKeyChange = { expandedPromptKey = it },
                        multiPromptTexts = multiPromptTexts,
                        onMultiPromptTextsChange = { multiPromptTexts = it },
                        multiPromptDirty = multiPromptDirty,
                        onMultiPromptDirtyChange = { multiPromptDirty = it },
                        preferredLang = preferredLang,
                        globalReplyLang = globalReplyLang,
                        systemPrompt = systemPrompt,
                        onSystemPromptChange = { systemPrompt = it; promptDirty = true },
                        onRestoreFactoryPrompt = { systemPrompt = factoryPrompt; promptDirty = true },
                        onDiscardPromptChanges = { systemPrompt = factoryPrompt; promptDirty = false },
                        promptDirty = promptDirty,
                        hasOverride = hasOverride,
                        factoryPrompt = factoryPrompt,
                    )
                }
                Spacer(Modifier.height(dimens.spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = reset, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.agent_cfg_restore_default)) }
                    Button(onClick = save, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.agent_cfg_save)) }
                }
            }
        }
        } else {
        Column(
            modifier = Modifier
                .widthIn(max = formMaxWidth)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(dimens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
        ) {
            // ═══ ① 身份与启用 ═══
            HMPCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agentLabelFor(agentRole), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(agentDescFor(agentRole), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = agentEnabled, onCheckedChange = { agentEnabled = it })
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.agent_cfg_disabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ═══ ② 模型与语言（所有 Agent 都有）═══
            HMPCard {
                Text(stringResource(Res.string.agent_cfg_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(
                        SegmentedOption("global", stringResource(Res.string.agent_cfg_lang_follow_global)),
                        SegmentedOption("zh", stringResource(Res.string.agent_ai_lang_zh)),
                        SegmentedOption("en", stringResource(Res.string.agent_ai_lang_en)),
                        SegmentedOption("auto", stringResource(Res.string.agent_ai_lang_auto)),
                    ),
                    selectedOption = preferredLang,
                    onOptionSelected = { newLang ->
                        preferredLang = newLang
                        // 用户未手动改过 prompt 时，切换语言即刷新为对应语言的出厂默认，
                        // 否则编辑框会停留在切换前的语言文本上，与实际生效内容不一致。
                        if (multiPrompt) {
                            // 多卡型：只刷新"未被手动改过"的项
                            multiPromptTexts = helloEntries.associate { entry ->
                                val keepEdited = multiPromptDirty.contains(entry.key)
                                entry.key to if (keepEdited) {
                                    multiPromptTexts[entry.key].orEmpty()
                                } else {
                                    effectivePromptFor(entry.key, newLang, globalReplyLang, emptyMap())
                                }
                            }
                        } else if (!promptDirty && !hasOverride) {
                            systemPrompt = resolveEffectivePrompt(
                                role = agentRole,
                                preferredLang = newLang,
                                globalReplyLang = globalReplyLang,
                                promptOverrides = emptyMap(),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.agent_cfg_lang_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.agent_cfg_temperature), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = (it * 100).toInt() / 100f },
                        valueRange = 0f..2f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(temperature.toString(), modifier = Modifier.width(48.dp))
                }
            }

            // ═══ ③ AI 端点配置（所有 Agent 都有 —— per-Agent 独立 endpoint）═══
            HMPCard {
                Text(stringResource(Res.string.agent_cfg_endpoint_config), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(
                        SegmentedOption("global", stringResource(Res.string.agent_cfg_lang_follow_global_default)),
                        SegmentedOption("custom", stringResource(Res.string.agent_cfg_endpoint_custom)),
                    ),
                    selectedOption = if (useCustomEndpoint) "custom" else "global",
                    onOptionSelected = { useCustomEndpoint = it == "custom" },
                )
                Spacer(Modifier.height(8.dp))
                if (useCustomEndpoint) {
                    OutlinedTextField(
                        value = customEndpoint,
                        onValueChange = { customEndpoint = it },
                        label = { Text(stringResource(Res.string.agent_cfg_endpoint_url_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://api.deepseek.com/v1") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-...") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = { Text(stringResource(Res.string.agent_cfg_model_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("deepseek-chat / gpt-4o-mini / ...") },
                    )
                } else {
                    // 全局默认只读预览（异步加载，加载完成前显示占位）
                    Text(
                        stringResource(Res.string.agent_cfg_following_global),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (!endpointLoaded) stringResource(Res.string.agent_cfg_loading)
                        else buildString {
                            append(stringResource(Res.string.agent_cfg_endpoint_value, if (globalEndpoint.endpoint.isNotBlank()) globalEndpoint.endpoint else stringResource(Res.string.agent_cfg_builtin)))
                            if (globalEndpoint.selectedModel.isNotBlank()) append(stringResource(Res.string.agent_cfg_model_value, globalEndpoint.selectedModel))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.agent_cfg_change_global_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ═══ ④ 信任与权限（所有 Agent 各自持有 trustLevel）═══
            HMPCard {
                Text(stringResource(Res.string.agent_cfg_trust_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.agent_cfg_trust_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    options = listOf(
                        SegmentedOption(TrustLevel.SUGGEST.toString(), stringResource(Res.string.agent_cfg_trust_suggest)),
                        SegmentedOption(TrustLevel.ACT.toString(), stringResource(Res.string.agent_cfg_trust_act)),
                        SegmentedOption(TrustLevel.SILENT.toString(), stringResource(Res.string.agent_cfg_trust_silent)),
                    ),
                    selectedOption = trustLevel.toString(),
                    onOptionSelected = { trustLevel = it.toIntOrNull() ?: TrustLevel.SUGGEST },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.agent_cfg_always_allow_count, policyConfig.alwaysAllow.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Master 专属：执行预算（并入本组 —— 同属"我可以自己做到什么程度"）
                if (agentRole == "master") {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(Res.string.agent_cfg_step_budget), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = stepBudget.toFloat(),
                            onValueChange = { stepBudget = it.toInt() },
                            valueRange = 3f..15f,
                            steps = 11,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.agent_cfg_step_budget_value, stepBudget), modifier = Modifier.width(48.dp))
                    }
                }
            }

            // ═══ ⑤ 专属参数（按 agent 能力差异化）═══
            if (agentRole == "hello") {
                HMPCard {
                    Text(stringResource(Res.string.agent_cfg_reco_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.agent_cfg_daily_count))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = dailyRecommendCount.toFloat(),
                            onValueChange = { dailyRecommendCount = it.toInt() },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(Res.string.agent_cfg_daily_count_value, dailyRecommendCount))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.agent_cfg_playlist_len))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = recommendListSize.toFloat(),
                            onValueChange = { recommendListSize = it.toInt() },
                            valueRange = 5f..20f,
                            steps = 14,
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(Res.string.agent_cfg_count_tracks, recommendListSize))
                    }
                }
            }

            if (agentRole == "enrich") {
                HMPCard {
                    Text(stringResource(Res.string.agent_cfg_target_cov), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = targetCoverage,
                            onValueChange = { targetCoverage = (it * 100).toInt() / 100f },
                            valueRange = 0.5f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${(targetCoverage * 100).toInt()}%")
                    }
                }
            }

            if (agentRole == "radio") {
                HMPCard {
                    Text(stringResource(Res.string.agent_cfg_radio_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.agent_cfg_target_count))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = targetCount.toFloat(),
                            onValueChange = { targetCount = it.toInt() },
                            valueRange = 8f..30f,
                            steps = 21,
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(Res.string.agent_cfg_count_tracks, targetCount))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.agent_cfg_auto_renew))
                        Switch(checked = autoRenew, onCheckedChange = { autoRenew = it })
                    }
                }
            }

            // ═══ ⑥ System Prompt ═══
            // 值推导：生效语言提示 + 出厂默认（供恢复/比对）
            val effectiveLang = when (preferredLang) {
                "zh" -> stringResource(Res.string.agent_ai_lang_zh)
                "en" -> stringResource(Res.string.agent_ai_lang_en)
                "auto" -> stringResource(Res.string.agent_cfg_lang_follow_system)
                else -> when (globalReplyLang) {  // "global"
                    "en" -> stringResource(Res.string.agent_cfg_lang_global_en)
                    "auto" -> stringResource(Res.string.agent_cfg_lang_global_auto)
                    else -> stringResource(Res.string.agent_cfg_lang_global_zh)
                }
            }

            if (multiPrompt) {
                // ── hello：多卡型分组编辑器 ──
                HMPCard {
                    val overriddenCount = helloEntries.count { entry ->
                        !resolved.promptOverrides[entry.key].isNullOrBlank()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (overriddenCount > 0) stringResource(Res.string.agent_cfg_customized_count, overriddenCount, helloEntries.size) else stringResource(Res.string.agent_cfg_all_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overriddenCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.agent_cfg_hello_prompt_desc, effectiveLang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    helloPromptGroups().forEach { (groupName, entries) ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            groupName.asString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        entries.forEach { entry ->
                            val isOpen = expandedPromptKey == entry.key
                            val edited = multiPromptTexts[entry.key].orEmpty()
                            val factory = effectivePromptFor(entry.key, preferredLang, globalReplyLang, emptyMap())
                            val overridden = !resolved.promptOverrides[entry.key].isNullOrBlank()
                            val changed = multiPromptDirty.contains(entry.key)

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 条目行：点开/收起
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { expandedPromptKey = if (isOpen) null else entry.key }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(entry.label.asString(), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.width(6.dp))
                                            val badge = when {
                                                changed -> stringResource(Res.string.agent_cfg_status_unsaved)
                                                overridden -> stringResource(Res.string.agent_cfg_status_overridden)
                                                else -> stringResource(Res.string.agent_cfg_status_default)
                                            }
                                            Text(
                                                badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    changed -> MaterialTheme.colorScheme.error
                                                    overridden -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                        }
                                        Text(
                                            entry.hint.asString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        if (isOpen) stringResource(Res.string.agent_ai_collapse) else stringResource(Res.string.agent_cfg_edit),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                if (isOpen) {
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = edited,
                                        onValueChange = { new ->
                                            multiPromptTexts = multiPromptTexts + (entry.key to new)
                                            multiPromptDirty = multiPromptDirty + entry.key
                                        },
                                        modifier = Modifier
                                            .widthIn(max = wideRowMaxWidth)
                                            .fillMaxWidth()
                                            .height(180.dp),
                                        placeholder = { Text(stringResource(Res.string.agent_cfg_placeholder_default)) },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        androidx.compose.material3.TextButton(onClick = {
                                            multiPromptTexts = multiPromptTexts + (entry.key to factory)
                                            multiPromptDirty = multiPromptDirty + entry.key
                                        }) { Text(stringResource(Res.string.agent_cfg_restore_default)) }
                                        if (changed) {
                                            androidx.compose.material3.TextButton(onClick = {
                                                multiPromptTexts = multiPromptTexts +
                                                    (entry.key to (resolved.promptOverrides[entry.key] ?: factory))
                                                multiPromptDirty = multiPromptDirty - entry.key
                                            }) { Text(stringResource(Res.string.agent_cfg_discard_changes)) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            } else {
                // ── master / enrich / radio：单 prompt 编辑器 ──
                HMPCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (hasOverride) stringResource(Res.string.agent_cfg_status_overridden) else stringResource(Res.string.agent_cfg_status_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasOverride) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.agent_cfg_prompt_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.agent_cfg_effective_lang, effectiveLang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it; promptDirty = true },
                        modifier = Modifier
                            .widthIn(max = wideRowMaxWidth)
                            .fillMaxWidth()
                            .height(220.dp),
                        placeholder = { Text(stringResource(Res.string.agent_cfg_placeholder_default)) },
                    )

                    // 占位符护栏（编辑时实时显示）—— 以出厂默认模板为基准
                    if (systemPrompt.isNotBlank()) {
                        val requiredPlaceholders = factoryPrompt.extractPlaceholders()
                        if (requiredPlaceholders.isNotEmpty()) {
                            val userHas = requiredPlaceholders.filter { systemPrompt.contains("{{$it}}") }
                            val missing = requiredPlaceholders.filter { !systemPrompt.contains("{{$it}}") }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(Res.string.agent_cfg_placeholders_needed, userHas.size, requiredPlaceholders.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                requiredPlaceholders.joinToString("  ") { ph ->
                                    val ok = ph in userHas
                                    if (ok) "✓ {{$ph}}" else "✗ {{$ph}}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (missing.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (missing.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.agent_cfg_missing_placeholder, missing.joinToString(", ") { "{{$it}}" }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // 操作：恢复出厂默认 / 放弃改动
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val templateLang = when (preferredLang) {
                            "en" -> stringResource(Res.string.agent_ai_lang_en)
                            else -> stringResource(Res.string.agent_ai_lang_zh)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { systemPrompt = factoryPrompt; promptDirty = true }
                        ) {
                            Text(stringResource(Res.string.agent_cfg_restore_factory, templateLang))
                        }
                        if (promptDirty || hasOverride) {
                            androidx.compose.material3.TextButton(
                                onClick = { systemPrompt = factoryPrompt; promptDirty = false }
                            ) {
                                Text(stringResource(Res.string.agent_cfg_discard_changes))
                            }
                        }
                    }
                }
            }

            // ═══ ⑦ 底部操作（随内容滚动到底，不悬浮）═══
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = reset, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.agent_cfg_restore_default)) }
                Button(onClick = save, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.agent_cfg_save)) }
            }
            // 给全局悬浮音乐胶囊预留底部空间
            MiniPlayerSafeSpacer(height = 56.dp)
        }
        }
        }
    }
}

// ── 横屏双栏：左栏（短表单聚合）──

/**
 * 横屏双栏左栏：①身份 ②模型与语言 ③AI 端点 ④权限与信任 ⑤专属参数。
 *
 * 与单栏分支渲染的是同一批卡片，仅拆到独立 Composable 以便双栏复用；
 * 状态全部由调用方持有（本函数无状态），保证单栏/双栏行为一致。
 */
@Composable
private fun ConfigFormLeftColumn(
    agentRole: String,
    agentEnabled: Boolean,
    onAgentEnabledChange: (Boolean) -> Unit,
    preferredLang: String,
    onPreferredLangSelected: (String) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    useCustomEndpoint: Boolean,
    onUseCustomEndpointChange: (Boolean) -> Unit,
    customEndpoint: String,
    onCustomEndpointChange: (String) -> Unit,
    customApiKey: String,
    onCustomApiKeyChange: (String) -> Unit,
    customModel: String,
    onCustomModelChange: (String) -> Unit,
    endpointLoaded: Boolean,
    globalEndpoint: AiEndpointConfig,
    trustLevel: Int,
    onTrustLevelChange: (Int) -> Unit,
    alwaysAllowCount: Int,
    stepBudget: Int,
    onStepBudgetChange: (Int) -> Unit,
    dailyRecommendCount: Int,
    onDailyRecommendCountChange: (Int) -> Unit,
    recommendListSize: Int,
    onRecommendListSizeChange: (Int) -> Unit,
    targetCoverage: Float,
    onTargetCoverageChange: (Float) -> Unit,
    targetCount: Int,
    onTargetCountChange: (Int) -> Unit,
    autoRenew: Boolean,
    onAutoRenewChange: (Boolean) -> Unit,
) {
    // ═══ ① 身份与启用 ═══
    HMPCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(agentLabelFor(agentRole), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(agentDescFor(agentRole), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = agentEnabled, onCheckedChange = onAgentEnabledChange)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.agent_cfg_disabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // ═══ ② 模型与语言 ═══
    HMPCard {
        Text(stringResource(Res.string.agent_cfg_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            modifier = Modifier.fillMaxWidth(),
            options = listOf(
                SegmentedOption("global", stringResource(Res.string.agent_cfg_lang_follow_global)),
                SegmentedOption("zh", stringResource(Res.string.agent_ai_lang_zh)),
                SegmentedOption("en", stringResource(Res.string.agent_ai_lang_en)),
                SegmentedOption("auto", stringResource(Res.string.agent_ai_lang_auto)),
            ),
            selectedOption = preferredLang,
            onOptionSelected = onPreferredLangSelected,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.agent_cfg_lang_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.agent_cfg_temperature), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = temperature,
                onValueChange = { onTemperatureChange((it * 100).toInt() / 100f) },
                valueRange = 0f..2f,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(temperature.toString(), modifier = Modifier.width(48.dp))
        }
    }

    // ═══ ③ AI 端点配置 ═══
    HMPCard {
        Text(stringResource(Res.string.agent_cfg_endpoint_config), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            modifier = Modifier.fillMaxWidth(),
            options = listOf(
                SegmentedOption("global", stringResource(Res.string.agent_cfg_lang_follow_global_default)),
                SegmentedOption("custom", stringResource(Res.string.agent_cfg_endpoint_custom)),
            ),
            selectedOption = if (useCustomEndpoint) "custom" else "global",
            onOptionSelected = { onUseCustomEndpointChange(it == "custom") },
        )
        Spacer(Modifier.height(8.dp))
        if (useCustomEndpoint) {
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = onCustomEndpointChange,
                label = { Text(stringResource(Res.string.agent_cfg_endpoint_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.deepseek.com/v1") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customApiKey,
                onValueChange = onCustomApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customModel,
                onValueChange = onCustomModelChange,
                label = { Text(stringResource(Res.string.agent_cfg_model_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("deepseek-chat / gpt-4o-mini / ...") },
            )
        } else {
            Text(
                stringResource(Res.string.agent_cfg_following_global),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (!endpointLoaded) stringResource(Res.string.agent_cfg_loading)
                else buildString {
                    append(stringResource(Res.string.agent_cfg_endpoint_value, if (globalEndpoint.endpoint.isNotBlank()) globalEndpoint.endpoint else stringResource(Res.string.agent_cfg_builtin)))
                    if (globalEndpoint.selectedModel.isNotBlank()) append(stringResource(Res.string.agent_cfg_model_value, globalEndpoint.selectedModel))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.agent_cfg_change_global_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ═══ ④ 信任与权限 ═══
    HMPCard {
        Text(stringResource(Res.string.agent_cfg_trust_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.agent_cfg_trust_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            modifier = Modifier.fillMaxWidth(),
            options = listOf(
                SegmentedOption(TrustLevel.SUGGEST.toString(), stringResource(Res.string.agent_cfg_trust_suggest)),
                SegmentedOption(TrustLevel.ACT.toString(), stringResource(Res.string.agent_cfg_trust_act)),
                SegmentedOption(TrustLevel.SILENT.toString(), stringResource(Res.string.agent_cfg_trust_silent)),
            ),
            selectedOption = trustLevel.toString(),
            onOptionSelected = { onTrustLevelChange(it.toIntOrNull() ?: TrustLevel.SUGGEST) },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.agent_cfg_always_allow_count, alwaysAllowCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (agentRole == "master") {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.agent_cfg_step_budget), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = stepBudget.toFloat(),
                    onValueChange = { onStepBudgetChange(it.toInt()) },
                    valueRange = 3f..15f,
                    steps = 11,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.agent_cfg_step_budget_value, stepBudget), modifier = Modifier.width(48.dp))
            }
        }
    }

    // ═══ ⑤ 专属参数 ═══
    when (agentRole) {
        "hello" -> HMPCard {
            Text(stringResource(Res.string.agent_cfg_reco_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.agent_cfg_daily_count))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = dailyRecommendCount.toFloat(),
                    onValueChange = { onDailyRecommendCountChange(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.agent_cfg_daily_count_value, dailyRecommendCount))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.agent_cfg_playlist_len))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = recommendListSize.toFloat(),
                    onValueChange = { onRecommendListSizeChange(it.toInt()) },
                    valueRange = 5f..20f,
                    steps = 14,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.agent_cfg_count_tracks, recommendListSize))
            }
        }

        "enrich" -> HMPCard {
            Text(stringResource(Res.string.agent_cfg_target_cov), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = targetCoverage,
                    onValueChange = { onTargetCoverageChange((it * 100).toInt() / 100f) },
                    valueRange = 0.5f..1f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("${(targetCoverage * 100).toInt()}%")
            }
        }

        "radio" -> HMPCard {
            Text(stringResource(Res.string.agent_cfg_radio_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.agent_cfg_target_count))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = targetCount.toFloat(),
                    onValueChange = { onTargetCountChange(it.toInt()) },
                    valueRange = 8f..30f,
                    steps = 21,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.agent_cfg_count_tracks, targetCount))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.agent_cfg_auto_renew))
                Switch(checked = autoRenew, onCheckedChange = onAutoRenewChange)
            }
        }
    }
}

// ── 横屏双栏：右栏（System Prompt 编辑器）──

/**
 * 右栏：⑥ System Prompt —— 单 prompt（master/enrich/radio）与多卡型（hello）两套编辑器。
 *
 * [resolvedOverrides] 只需传 `resolved.promptOverrides`（用于判断各项是否已被覆盖）。
 */
@Composable
private fun SystemPromptSection(
    multiPrompt: Boolean,
    helloEntries: List<PromptEntry>,
    resolvedOverrides: Map<String, String?>,
    expandedPromptKey: String?,
    onExpandedPromptKeyChange: (String?) -> Unit,
    multiPromptTexts: Map<String, String>,
    onMultiPromptTextsChange: (Map<String, String>) -> Unit,
    multiPromptDirty: Set<String>,
    onMultiPromptDirtyChange: (Set<String>) -> Unit,
    preferredLang: String,
    globalReplyLang: String,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    onRestoreFactoryPrompt: () -> Unit,
    onDiscardPromptChanges: () -> Unit,
    promptDirty: Boolean,
    hasOverride: Boolean,
    factoryPrompt: String,
) {
    // 生效语言提示
    val effectiveLang = when (preferredLang) {
        "zh" -> stringResource(Res.string.agent_ai_lang_zh)
        "en" -> stringResource(Res.string.agent_ai_lang_en)
        "auto" -> stringResource(Res.string.agent_cfg_lang_follow_system)
        else -> when (globalReplyLang) {
            "en" -> stringResource(Res.string.agent_cfg_lang_global_en)
            "auto" -> stringResource(Res.string.agent_cfg_lang_global_auto)
            else -> stringResource(Res.string.agent_cfg_lang_global_zh)
        }
    }

    if (multiPrompt) {
        // ── hello：多卡型分组编辑器 ──
        HMPCard {
            val overriddenCount = helloEntries.count { entry ->
                !resolvedOverrides[entry.key].isNullOrBlank()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (overriddenCount > 0) stringResource(Res.string.agent_cfg_customized_count, overriddenCount, helloEntries.size) else stringResource(Res.string.agent_cfg_all_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overriddenCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.agent_cfg_hello_prompt_desc, effectiveLang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            helloPromptGroups().forEach { (groupName, entries) ->
                Spacer(Modifier.height(14.dp))
                Text(
                    groupName.asString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                entries.forEach { entry ->
                    val isOpen = expandedPromptKey == entry.key
                    val edited = multiPromptTexts[entry.key].orEmpty()
                    val factory = effectivePromptFor(entry.key, preferredLang, globalReplyLang, emptyMap())
                    val overridden = !resolvedOverrides[entry.key].isNullOrBlank()
                    val changed = multiPromptDirty.contains(entry.key)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 条目行：点开/收起
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onExpandedPromptKeyChange(if (isOpen) null else entry.key) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.label.asString(), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.width(6.dp))
                                    val badge = when {
                                        changed -> stringResource(Res.string.agent_cfg_status_unsaved)
                                        overridden -> stringResource(Res.string.agent_cfg_status_overridden)
                                        else -> stringResource(Res.string.agent_cfg_status_default)
                                    }
                                    Text(
                                        badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            changed -> MaterialTheme.colorScheme.error
                                            overridden -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                Text(
                                    entry.hint.asString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (isOpen) stringResource(Res.string.agent_ai_collapse) else stringResource(Res.string.agent_cfg_edit),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (isOpen) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = edited,
                                onValueChange = { new ->
                                    onMultiPromptTextsChange(multiPromptTexts + (entry.key to new))
                                    onMultiPromptDirtyChange(multiPromptDirty + entry.key)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                placeholder = { Text(stringResource(Res.string.agent_cfg_placeholder_default)) },
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                androidx.compose.material3.TextButton(onClick = {
                                    onMultiPromptTextsChange(multiPromptTexts + (entry.key to factory))
                                    onMultiPromptDirtyChange(multiPromptDirty + entry.key)
                                }) { Text(stringResource(Res.string.agent_cfg_restore_default)) }
                                if (changed) {
                                    androidx.compose.material3.TextButton(onClick = {
                                        onMultiPromptTextsChange(
                                            multiPromptTexts + (entry.key to (resolvedOverrides[entry.key] ?: factory))
                                        )
                                        onMultiPromptDirtyChange(multiPromptDirty - entry.key)
                                    }) { Text(stringResource(Res.string.agent_cfg_discard_changes)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    } else {
        // ── master / enrich / radio：单 prompt 编辑器 ──
        HMPCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (hasOverride) stringResource(Res.string.agent_cfg_status_overridden) else stringResource(Res.string.agent_cfg_status_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasOverride) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.agent_cfg_prompt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.agent_cfg_effective_lang, effectiveLang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                placeholder = { Text(stringResource(Res.string.agent_cfg_placeholder_default)) },
            )

            // 占位符护栏（编辑时实时显示）—— 以出厂默认模板为基准
            if (systemPrompt.isNotBlank()) {
                val requiredPlaceholders = factoryPrompt.extractPlaceholders()
                if (requiredPlaceholders.isNotEmpty()) {
                    val userHas = requiredPlaceholders.filter { systemPrompt.contains("{{$it}}") }
                    val missing = requiredPlaceholders.filter { !systemPrompt.contains("{{$it}}") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.agent_cfg_placeholders_needed, userHas.size, requiredPlaceholders.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        requiredPlaceholders.joinToString("  ") { ph ->
                            val ok = ph in userHas
                            if (ok) "✓ {{$ph}}" else "✗ {{$ph}}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missing.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (missing.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.agent_cfg_missing_placeholder, missing.joinToString(", ") { "{{$it}}" }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 操作：恢复出厂默认 / 放弃改动
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val templateLang = when (preferredLang) {
                    "en" -> stringResource(Res.string.agent_ai_lang_en)
                    else -> stringResource(Res.string.agent_ai_lang_zh)
                }
                androidx.compose.material3.TextButton(
                    onClick = onRestoreFactoryPrompt
                ) {
                    Text(stringResource(Res.string.agent_cfg_restore_factory, templateLang))
                }
                if (promptDirty || hasOverride) {
                    androidx.compose.material3.TextButton(
                        onClick = onDiscardPromptChanges
                    ) {
                        Text(stringResource(Res.string.agent_cfg_discard_changes))
                    }
                }
            }
        }
    }
}

// ── 辅助函数 ──

private fun agentLabelFor(role: String) = when (role) {
    "master" -> "MasterAgent"
    "hello" -> "HelloSubAgent"
    "enrich" -> "EnrichSubAgent"
    "radio" -> "RadioSubAgent"
    else -> role
}

@Composable
private fun agentDescFor(role: String) = when (role) {
    "master" -> stringResource(Res.string.agent_cfg_desc_master)
    "hello" -> stringResource(Res.string.agent_cfg_desc_hello)
    "enrich" -> stringResource(Res.string.agent_cfg_desc_enrich)
    "radio" -> stringResource(Res.string.agent_cfg_desc_radio)
    else -> ""
}

/**
 * 每个 Agent 在配置页编辑的「主 prompt key」。
 *
 * 必须与运行时实际消费的 key 一致，否则用户编辑的东西不会被引擎读取：
 *  - master：MasterAgent 组装对话 system prompt 用 "chat.system"
 *  - enrich：EnrichSubAgent 用 "enrich.system"
 *  - radio ：RadioSession 主播编排用 "radio.host"
 *  - hello ：多卡型，见 [helloPromptEntries]（此处取问候卡代表 key）
 */
private fun defaultPromptKeyFor(role: String) = when (role) {
    "master" -> "chat.system"
    "enrich" -> "enrich.system"
    "radio" -> "radio.host"
    "hello" -> "hello.greeting.quote"
    else -> "${role}.system"
}

/**
 * Hello 的多卡型 prompt 目录——与 HelloSubAgent 实际消费的 key 一一对应。
 *
 * 分三组对应三类卡片：问候（GREETING，按类型路由）→ 推荐（RECOMMEND）→ 怀旧（FORGOTTEN）。
 * 顺序即 UI 展示顺序；[PromptEntry.label] 是给用户看的卡名，不是内部 key。
 */
private data class PromptEntry(val key: String, val label: UiText, val hint: UiText)

/** 目录项简写：label / hint 都是资源，包装成 [UiText] 以便渲染期解析。 */
private fun promptEntry(key: String, label: StringResource, hint: StringResource) =
    PromptEntry(key, label.asUiText(), hint.asUiText())

private val HELLO_GREETING_PROMPTS = listOf(
    promptEntry("hello.greeting.quote", Res.string.agent_hello_greeting_quote, Res.string.agent_hello_greeting_quote_hint),
    promptEntry("hello.greeting.lyric_gold", Res.string.agent_hello_greeting_lyric_gold, Res.string.agent_hello_greeting_lyric_gold_hint),
    promptEntry("hello.greeting.fact", Res.string.agent_hello_greeting_fact, Res.string.agent_hello_greeting_fact_hint),
    promptEntry("hello.greeting.story", Res.string.agent_hello_greeting_story, Res.string.agent_hello_greeting_story_hint),
    promptEntry("hello.greeting.artist", Res.string.agent_hello_greeting_artist, Res.string.agent_hello_greeting_artist_hint),
    promptEntry("hello.greeting.listen", Res.string.agent_hello_greeting_listen, Res.string.agent_hello_greeting_listen_hint),
    promptEntry("hello.greeting.history", Res.string.agent_hello_greeting_history, Res.string.agent_hello_greeting_history_hint),
)

private val HELLO_RECOMMEND_PROMPTS = listOf(
    promptEntry("hello.recommend.full", Res.string.agent_hello_recommend_full, Res.string.agent_hello_recommend_full_hint),
    promptEntry("hello.recommend.short", Res.string.agent_hello_recommend_short, Res.string.agent_hello_recommend_short_hint),
    promptEntry("hello.recommend.list", Res.string.agent_hello_recommend_list, Res.string.agent_hello_recommend_list_hint),
)

private val HELLO_FORGOTTEN_PROMPTS = listOf(
    promptEntry("hello.forgotten.essay", Res.string.agent_hello_forgotten_essay, Res.string.agent_hello_forgotten_essay_hint),
    promptEntry("hello.forgotten.card", Res.string.agent_hello_forgotten_card, Res.string.agent_hello_forgotten_card_hint),
)

/** Hello 的「分组 → 条目」全表，供 UI 分节渲染。 */
private fun helloPromptGroups(): List<Pair<UiText, List<PromptEntry>>> = listOf(
    Res.string.agent_hello_group_greeting.asUiText() to HELLO_GREETING_PROMPTS,
    Res.string.agent_hello_group_recommend.asUiText() to HELLO_RECOMMEND_PROMPTS,
    Res.string.agent_hello_group_forgotten.asUiText() to HELLO_FORGOTTEN_PROMPTS,
)

/** 该 Agent 是否为多卡型 prompt（需要分组编辑器）。 */
private fun isMultiPromptAgent(role: String) = role == "hello"

/** 计算某个 prompt key **当前实际生效**的文本，与运行时同源。 */
private fun effectivePromptFor(
    key: String,
    preferredLang: String,
    globalReplyLang: String,
    promptOverrides: Map<String, String>,
): String = resolvePrompt(
    key = key,
    preferredLang = preferredLang,
    globalReplyLanguage = globalReplyLang,
    userOverrides = promptOverrides,
)

/**
 * 计算某 Agent **当前实际生效**的 prompt 文本（单 prompt Agent 用）。
 */
private fun resolveEffectivePrompt(
    role: String,
    preferredLang: String,
    globalReplyLang: String,
    promptOverrides: Map<String, String>,
): String = effectivePromptFor(
    key = defaultPromptKeyFor(role),
    preferredLang = preferredLang,
    globalReplyLang = globalReplyLang,
    promptOverrides = promptOverrides,
)

/** 从 prompt 模板中提取所有 {{placeholder}} 占位符（不含括号）。 */
private fun String.extractPlaceholders(): Set<String> {
    val regex = Regex("""\{\{\s*([a-zA-Z_][a-zA-Z0-9_\.]*)\s*\}\}""")
    return regex.findAll(this).map { it.groupValues[1] }.toSet()
}
