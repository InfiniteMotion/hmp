package com.hearablemusic.player.ui.agent.config
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.Res
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
import com.hearablemusic.player.ui.generated.resources.agent_cfg_global_unavailable
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
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hmp.domain.agent.policy.TrustLevel
import com.hmp.domain.setting.model.AiEndpointConfig
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    viewModel: AgentConfigViewModel = koinViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    // 唯一状态出口：19 处表单草稿与策略配置快照都由 VM 提供
    val state by viewModel.uiState.collectAsState()
    val policyConfig = state.config
    val resolved = state.resolved
    val agentEnabled = state.agentEnabled
    val preferredLang = state.preferredLang
    val temperature = state.temperature
    val useCustomEndpoint = state.useCustomEndpoint
    val customEndpoint = state.customEndpoint
    val customApiKey = state.customApiKey
    val customModel = state.customModel
    val endpointLoaded = state.endpointLoaded
    val globalEndpoint = state.globalEndpoint
    val trustLevel = state.trustLevel
    val stepBudget = state.stepBudget
    val dailyRecommendCount = state.dailyRecommendCount
    val recommendListSize = state.recommendListSize
    val targetCoverage = state.targetCoverage
    val targetCount = state.targetCount
    val autoRenew = state.autoRenew

    // 全局回复语言（供 system prompt 生效语言提示用；纯内存读，避免在 if 内重复调用）
    val globalReplyLang = state.globalReplyLang

    // ── Prompt 编辑：文本与 dirty 标记在 VM，展开项属 §5 白名单留 UI ──
    val multiPrompt = isMultiPromptAgent(agentRole)
    val helloEntries = remember(agentRole) {
        helloPromptGroups().flatMap { it.second }
    }
    // 出厂默认、覆盖位与占位符校验全部由 VM 派生（原在组合期计算并回灌 VM）
    val hasOverride = state.hasOverride
    val placeholderCheck = state.placeholderCheck
    val systemPrompt = state.systemPrompt
    val promptDirty = state.promptDirty
    val multiPromptTexts = state.multiPromptTexts
    val multiPromptDirty = state.multiPromptDirty
    // 当前展开编辑的项（一次只开一个，避免 12 个长文本框把页面撑爆）
    var expandedPromptKey by remember(agentRole) { mutableStateOf<String?>(null) }

    // 首次进入 / 切换 Agent：VM 异步加载策略快照、per-Agent endpoint 与全局 endpoint
    LaunchedEffect(agentRole) {
        viewModel.onRoleChanged(agentRole)
    }

    // 保存 / 重置逻辑全部在 VM（含 prompt 落库判定、endpoint 持久化、snapshot 回填）
    val save = { viewModel.onSave() }

    val reset = {
        viewModel.onReset()
        expandedPromptKey = null
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = agentLabelFor(agentRole) + stringResource(Res.string.agent_cfg_config_suffix),
    ) {
        // F14-T1 自适应：竖屏/窄窗单栏限宽居中；横屏（手机横屏 / 平板、桌面宽窗）改双栏，
        // 左栏 = 短表单（身份/模型/端点/权限/专属参数），右栏 = System Prompt 编辑器，
        // 底部操作横贯两栏。Compact maxWidth = Dp.Unspecified → 单栏不加约束，与改造前逐像素一致。
        val window = LocalWindowSizeInfo.current
        val isWide = window.isExpanded || window.isMedium
        val isLandscape = window.isLandscape
        val formMaxWidth = if (isWide) LayoutConstraints.WidthBreakpointMedium else Dp.Unspecified
        // 双栏下「宽行」（多行 Prompt 编辑器）独占一栏，宽度即栏宽，不再另外限宽。
        val wideRowMaxWidth = if (isWide && !isLandscape) LayoutConstraints.ContentMaxWidthWide else Dp.Unspecified
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (isLandscape) {
                // ── 横屏双栏 ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.section, vertical = metrics.section),
                    horizontalArrangement = Arrangement.spacedBy(metrics.section),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                    ) {
                        ConfigFormLeftColumn(
                            agentRole = agentRole,
                            agentEnabled = agentEnabled,
                            onAgentEnabledChange = viewModel::onAgentEnabledChange,
                            preferredLang = preferredLang,
                            onPreferredLangSelected = viewModel::onPreferredLangChange,
                            temperature = temperature,
                            onTemperatureChange = viewModel::onTemperatureChange,
                            useCustomEndpoint = useCustomEndpoint,
                            onUseCustomEndpointChange = viewModel::onUseCustomEndpointChange,
                            customEndpoint = customEndpoint,
                            onCustomEndpointChange = viewModel::onCustomEndpointChange,
                            customApiKey = customApiKey,
                            onCustomApiKeyChange = viewModel::onCustomApiKeyChange,
                            customModel = customModel,
                            onCustomModelChange = viewModel::onCustomModelChange,
                            endpointLoaded = endpointLoaded,
                            globalEndpoint = globalEndpoint,
                            trustLevel = trustLevel,
                            onTrustLevelChange = viewModel::onTrustLevelChange,
                            alwaysAllowCount = policyConfig.alwaysAllow.size,
                            stepBudget = stepBudget,
                            onStepBudgetChange = viewModel::onStepBudgetChange,
                            dailyRecommendCount = dailyRecommendCount,
                            onDailyRecommendCountChange = viewModel::onDailyRecommendCountChange,
                            recommendListSize = recommendListSize,
                            onRecommendListSizeChange = viewModel::onRecommendListSizeChange,
                            targetCoverage = targetCoverage,
                            onTargetCoverageChange = viewModel::onTargetCoverageChange,
                            targetCount = targetCount,
                            onTargetCountChange = viewModel::onTargetCountChange,
                            autoRenew = autoRenew,
                            onAutoRenewChange = viewModel::onAutoRenewChange,
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
                            verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                        ) {
                            SystemPromptSection(
                                multiPrompt = multiPrompt,
                                helloEntries = helloEntries,
                                resolvedOverrides = resolved.promptOverrides,
                                expandedPromptKey = expandedPromptKey,
                                onExpandedPromptKeyChange = { expandedPromptKey = it },
                                multiPromptTexts = multiPromptTexts,
                                multiPromptDirty = multiPromptDirty,
                                onMultiPromptEntryChange = viewModel::onMultiPromptEntryChange,
                                onMultiPromptRestoreFactory = viewModel::onMultiPromptRestoreFactory,
                                onMultiPromptRevert = viewModel::onMultiPromptRevert,
                                preferredLang = preferredLang,
                                globalReplyLang = globalReplyLang,
                                systemPrompt = systemPrompt,
                                onSystemPromptChange = viewModel::onSystemPromptChange,
                                onRestoreFactoryPrompt = viewModel::onRestoreFactoryPrompt,
                                onDiscardPromptChanges = viewModel::onDiscardPromptChanges,
                                promptDirty = promptDirty,
                                hasOverride = hasOverride,
                                placeholderCheck = placeholderCheck,
                            )
                        }
                        Spacer(Modifier.height(metrics.cozy))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.compact),
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
                        .padding(metrics.section),
                    verticalArrangement = Arrangement.spacedBy(metrics.section),
                ) {
                    // ①-⑥ 与横屏分支共用同一组卡片：单双栏只差外层容器，不再各写一遍。
                    ConfigFormLeftColumn(
                        agentRole = agentRole,
                        agentEnabled = agentEnabled,
                        onAgentEnabledChange = viewModel::onAgentEnabledChange,
                        preferredLang = preferredLang,
                        onPreferredLangSelected = viewModel::onPreferredLangChange,
                        temperature = temperature,
                        onTemperatureChange = viewModel::onTemperatureChange,
                        useCustomEndpoint = useCustomEndpoint,
                        onUseCustomEndpointChange = viewModel::onUseCustomEndpointChange,
                        customEndpoint = customEndpoint,
                        onCustomEndpointChange = viewModel::onCustomEndpointChange,
                        customApiKey = customApiKey,
                        onCustomApiKeyChange = viewModel::onCustomApiKeyChange,
                        customModel = customModel,
                        onCustomModelChange = viewModel::onCustomModelChange,
                        endpointLoaded = endpointLoaded,
                        globalEndpoint = globalEndpoint,
                        trustLevel = trustLevel,
                        onTrustLevelChange = viewModel::onTrustLevelChange,
                        alwaysAllowCount = policyConfig.alwaysAllow.size,
                        stepBudget = stepBudget,
                        onStepBudgetChange = viewModel::onStepBudgetChange,
                        dailyRecommendCount = dailyRecommendCount,
                        onDailyRecommendCountChange = viewModel::onDailyRecommendCountChange,
                        recommendListSize = recommendListSize,
                        onRecommendListSizeChange = viewModel::onRecommendListSizeChange,
                        targetCoverage = targetCoverage,
                        onTargetCoverageChange = viewModel::onTargetCoverageChange,
                        targetCount = targetCount,
                        onTargetCountChange = viewModel::onTargetCountChange,
                        autoRenew = autoRenew,
                        onAutoRenewChange = viewModel::onAutoRenewChange,
                    )
                    SystemPromptSection(
                        multiPrompt = multiPrompt,
                        helloEntries = helloEntries,
                        resolvedOverrides = resolved.promptOverrides,
                        expandedPromptKey = expandedPromptKey,
                        onExpandedPromptKeyChange = { expandedPromptKey = it },
                        multiPromptTexts = multiPromptTexts,
                        multiPromptDirty = multiPromptDirty,
                        onMultiPromptEntryChange = viewModel::onMultiPromptEntryChange,
                        onMultiPromptRestoreFactory = viewModel::onMultiPromptRestoreFactory,
                        onMultiPromptRevert = viewModel::onMultiPromptRevert,
                        preferredLang = preferredLang,
                        globalReplyLang = globalReplyLang,
                        systemPrompt = systemPrompt,
                        onSystemPromptChange = viewModel::onSystemPromptChange,
                        onRestoreFactoryPrompt = viewModel::onRestoreFactoryPrompt,
                        onDiscardPromptChanges = viewModel::onDiscardPromptChanges,
                        promptDirty = promptDirty,
                        hasOverride = hasOverride,
                        placeholderCheck = placeholderCheck,
                        wideRowMaxWidth = wideRowMaxWidth,
                    )
                    // ═══ ⑦ 底部操作（随内容滚动到底，不悬浮）═══
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.compact),
                    ) {
                        Button(onClick = reset, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.agent_cfg_restore_default)) }
                        Button(onClick = save, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.agent_cfg_save)) }
                    }
                    // 给全局悬浮音乐胶囊预留底部空间
                    MiniPlayerSafeSpacer(height = metrics.bar)
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
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    // ═══ ① 身份与启用 ═══
    HMPCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(agentLabelFor(agentRole), style = typo.subtitle, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(metrics.micro))
                Text(agentDescFor(agentRole), style = typo.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = agentEnabled, onCheckedChange = onAgentEnabledChange)
        }
        Spacer(Modifier.height(metrics.compact))
        Text(stringResource(Res.string.agent_cfg_disabled_hint), style = typo.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // ═══ ② 模型与语言 ═══
    HMPCard {
        Text(stringResource(Res.string.agent_cfg_language), style = typo.subtitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(metrics.compact))
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
        Spacer(Modifier.height(metrics.micro))
        Text(
            stringResource(Res.string.agent_cfg_lang_desc),
            style = typo.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(metrics.cozy))
        Text(stringResource(Res.string.agent_cfg_temperature), style = typo.subtitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(metrics.compact))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = temperature,
                onValueChange = onTemperatureChange,
                valueRange = 0f..2f,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(metrics.compact))
            Text(temperature.toString(), modifier = Modifier.width(metrics.bar))
        }
    }

    // ═══ ③ AI 端点配置 ═══
    HMPCard {
        Text(stringResource(Res.string.agent_cfg_endpoint_config), style = typo.subtitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(metrics.compact))
        SegmentedControl(
            modifier = Modifier.fillMaxWidth(),
            options = listOf(
                SegmentedOption("global", stringResource(Res.string.agent_cfg_lang_follow_global_default)),
                SegmentedOption("custom", stringResource(Res.string.agent_cfg_endpoint_custom)),
            ),
            selectedOption = if (useCustomEndpoint) "custom" else "global",
            onOptionSelected = { onUseCustomEndpointChange(it == "custom") },
        )
        Spacer(Modifier.height(metrics.compact))
        if (useCustomEndpoint) {
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = onCustomEndpointChange,
                label = { Text(stringResource(Res.string.agent_cfg_endpoint_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.deepseek.com/v1") },
            )
            Spacer(Modifier.height(metrics.compact))
            OutlinedTextField(
                value = customApiKey,
                onValueChange = onCustomApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
            )
            Spacer(Modifier.height(metrics.compact))
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
                style = typo.body,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(metrics.micro))
            Text(
                if (!endpointLoaded) {
                    stringResource(Res.string.agent_cfg_loading)
                } else {
                    buildString {
                        append(stringResource(Res.string.agent_cfg_endpoint_value, if (globalEndpoint.endpoint.isNotBlank()) globalEndpoint.endpoint else stringResource(Res.string.agent_cfg_builtin)))
                        if (globalEndpoint.selectedModel.isNotBlank()) append(stringResource(Res.string.agent_cfg_model_value, globalEndpoint.selectedModel))
                    }
                },
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (endpointLoaded && !globalEndpoint.isConfigured) {
                Spacer(Modifier.height(metrics.micro))
                Text(
                    stringResource(Res.string.agent_cfg_global_unavailable),
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(metrics.micro))
            Text(
                stringResource(Res.string.agent_cfg_change_global_hint),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ═══ ④ 信任与权限 ═══
    HMPCard {
        Text(stringResource(Res.string.agent_cfg_trust_title), style = typo.subtitle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(metrics.compact))
        Text(
            stringResource(Res.string.agent_cfg_trust_desc),
            style = typo.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(metrics.compact))
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
        Spacer(Modifier.height(metrics.micro))
        Text(
            stringResource(Res.string.agent_cfg_always_allow_count, alwaysAllowCount),
            style = typo.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (agentRole == "master") {
            Spacer(Modifier.height(metrics.cozy))
            Text(stringResource(Res.string.agent_cfg_step_budget), style = typo.subtitle, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(metrics.compact))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = stepBudget.toFloat(),
                    onValueChange = { onStepBudgetChange(it.toInt()) },
                    valueRange = 3f..15f,
                    steps = 11,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(metrics.compact))
                Text(stringResource(Res.string.agent_cfg_step_budget_value, stepBudget), modifier = Modifier.width(metrics.bar))
            }
        }
    }

    // ═══ ⑤ 专属参数 ═══
    when (agentRole) {
        "hello" -> HMPCard {
            Text(stringResource(Res.string.agent_cfg_reco_params), style = typo.subtitle, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(metrics.base))
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
            Spacer(Modifier.height(metrics.compact))
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
            Text(stringResource(Res.string.agent_cfg_target_cov), style = typo.subtitle, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(metrics.compact))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = targetCoverage,
                    onValueChange = onTargetCoverageChange,
                    valueRange = 0.5f..1f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(metrics.compact))
                Text("${(targetCoverage * 100).toInt()}%")
            }
        }

        "radio" -> HMPCard {
            Text(stringResource(Res.string.agent_cfg_radio_params), style = typo.subtitle, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(metrics.base))
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
            Spacer(Modifier.height(metrics.base))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
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
    multiPromptDirty: Set<String>,
    onMultiPromptEntryChange: (String, String) -> Unit,
    onMultiPromptRestoreFactory: (String) -> Unit,
    onMultiPromptRevert: (String) -> Unit,
    preferredLang: String,
    globalReplyLang: String,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    onRestoreFactoryPrompt: () -> Unit,
    onDiscardPromptChanges: () -> Unit,
    promptDirty: Boolean,
    hasOverride: Boolean,
    placeholderCheck: PlaceholderCheck?,
    /** 单栏宽窗下给多行编辑器限宽；双栏传默认 Unspecified（栏宽即上限）。 */
    wideRowMaxWidth: Dp = Dp.Unspecified,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
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
                Text("System Prompt", style = typo.subtitle, fontWeight = FontWeight.Bold)
                Text(
                    if (overriddenCount > 0) stringResource(Res.string.agent_cfg_customized_count, overriddenCount, helloEntries.size) else stringResource(Res.string.agent_cfg_all_default),
                    style = typo.caption,
                    color = if (overriddenCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(metrics.micro))
            Text(
                stringResource(Res.string.agent_cfg_hello_prompt_desc, effectiveLang),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            helloPromptGroups().forEach { (groupName, entries) ->
                Spacer(Modifier.height(metrics.base))
                Text(
                    groupName.asString(),
                    style = typo.footnote.medium(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(metrics.micro))
                entries.forEach { entry ->
                    val isOpen = expandedPromptKey == entry.key
                    val edited = multiPromptTexts[entry.key].orEmpty()
                    val overridden = !resolvedOverrides[entry.key].isNullOrBlank()
                    val changed = multiPromptDirty.contains(entry.key)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 条目行：点开/收起
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(metrics.compact))
                                .clickable { onExpandedPromptKeyChange(if (isOpen) null else entry.key) }
                                .padding(vertical = metrics.compact, horizontal = metrics.micro),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.label.asString(), style = typo.body)
                                    Spacer(Modifier.width(metrics.micro))
                                    val badge = when {
                                        changed -> stringResource(Res.string.agent_cfg_status_unsaved)
                                        overridden -> stringResource(Res.string.agent_cfg_status_overridden)
                                        else -> stringResource(Res.string.agent_cfg_status_default)
                                    }
                                    Text(
                                        badge,
                                        style = typo.caption,
                                        color = when {
                                            changed -> MaterialTheme.colorScheme.error
                                            overridden -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                Text(
                                    entry.hint.asString(),
                                    style = typo.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (isOpen) stringResource(Res.string.agent_ai_collapse) else stringResource(Res.string.agent_cfg_edit),
                                style = typo.footnote.medium(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (isOpen) {
                            Spacer(Modifier.height(metrics.micro))
                            OutlinedTextField(
                                value = edited,
                                onValueChange = { new -> onMultiPromptEntryChange(entry.key, new) },
                                modifier = Modifier
                                    .widthIn(max = wideRowMaxWidth)
                                    .fillMaxWidth()
                                    .height(LayoutConstraints.CardHeight),
                                placeholder = { Text(stringResource(Res.string.agent_cfg_placeholder_default)) },
                            )
                            Spacer(Modifier.height(metrics.micro))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(metrics.compact),
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = { onMultiPromptRestoreFactory(entry.key) },
                                ) { Text(stringResource(Res.string.agent_cfg_restore_default)) }
                                if (changed) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { onMultiPromptRevert(entry.key) },
                                    ) { Text(stringResource(Res.string.agent_cfg_discard_changes)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(metrics.micro))
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
                Text("System Prompt", style = typo.subtitle, fontWeight = FontWeight.Bold)
                Text(
                    if (hasOverride) stringResource(Res.string.agent_cfg_status_overridden) else stringResource(Res.string.agent_cfg_status_default),
                    style = typo.caption,
                    color = if (hasOverride) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(metrics.micro))
            Text(
                stringResource(Res.string.agent_cfg_prompt_desc),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(metrics.compact))
            Text(
                stringResource(Res.string.agent_cfg_effective_lang, effectiveLang),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(metrics.compact))

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                modifier = Modifier
                    .widthIn(max = wideRowMaxWidth)
                    .fillMaxWidth()
                    .height(LayoutConstraints.PanelHeightLarge),
                placeholder = { Text(stringResource(Res.string.agent_cfg_placeholder_default)) },
            )

            // 占位符护栏（编辑时实时显示）—— 校验结果由 VM 派生，这里只渲染
            placeholderCheck?.let { check ->
                Spacer(Modifier.height(metrics.micro))
                Text(
                    stringResource(Res.string.agent_cfg_placeholders_needed, check.present.size, check.required.size),
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    check.required.joinToString("  ") { ph ->
                        if (ph in check.present) "✓ {{$ph}}" else "✗ {{$ph}}"
                    },
                    style = typo.footnote,
                    color = if (check.missing.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (check.missing.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.agent_cfg_missing_placeholder, check.missing.joinToString(", ") { "{{$it}}" }),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 操作：恢复出厂默认 / 放弃改动
            Spacer(Modifier.height(metrics.compact))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.compact),
            ) {
                val templateLang = when (preferredLang) {
                    "en" -> stringResource(Res.string.agent_ai_lang_en)
                    else -> stringResource(Res.string.agent_ai_lang_zh)
                }
                androidx.compose.material3.TextButton(
                    onClick = onRestoreFactoryPrompt,
                ) {
                    Text(stringResource(Res.string.agent_cfg_restore_factory, templateLang))
                }
                if (promptDirty || hasOverride) {
                    androidx.compose.material3.TextButton(
                        onClick = onDiscardPromptChanges,
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
