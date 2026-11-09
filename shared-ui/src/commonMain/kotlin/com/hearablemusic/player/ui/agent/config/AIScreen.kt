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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.agent.agentIcon
import com.hearablemusic.player.ui.common.components.SectionHeader
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.text.resolveForEffect
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.fmtTokens
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_ai_access_header
import com.hearablemusic.player.ui.generated.resources.agent_ai_agent_mgmt_header
import com.hearablemusic.player.ui.generated.resources.agent_ai_collapse
import com.hearablemusic.player.ui.generated.resources.agent_ai_coming_soon
import com.hearablemusic.player.ui.generated.resources.agent_ai_custom_endpoint
import com.hearablemusic.player.ui.generated.resources.agent_ai_endpoint_prereq
import com.hearablemusic.player.ui.generated.resources.agent_ai_expand
import com.hearablemusic.player.ui.generated.resources.agent_ai_free_remaining
import com.hearablemusic.player.ui.generated.resources.agent_ai_free_tier
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_auto
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_en
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_follow_global_desc
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_voice_header
import com.hearablemusic.player.ui.generated.resources.agent_ai_lang_zh
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_about_you
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_count
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_disabled_note
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_empty
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_feature
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_forget
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_header
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_inferred
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_log_desc
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_log_title
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_log_view
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_more
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_off_desc
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_reset
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_reset_btn
import com.hearablemusic.player.ui.generated.resources.agent_ai_memory_reset_desc
import com.hearablemusic.player.ui.generated.resources.agent_ai_not_configured
import com.hearablemusic.player.ui.generated.resources.agent_ai_paid_mode
import com.hearablemusic.player.ui.generated.resources.agent_ai_reset_confirm_btn
import com.hearablemusic.player.ui.generated.resources.agent_ai_reset_dialog_text
import com.hearablemusic.player.ui.generated.resources.agent_ai_reset_dialog_title
import com.hearablemusic.player.ui.generated.resources.agent_ai_voice_desc
import com.hearablemusic.player.ui.generated.resources.agent_ai_voice_locked
import com.hearablemusic.player.ui.generated.resources.agent_ai_voice_title
import com.hearablemusic.player.ui.generated.resources.agent_token_limits_enable
import com.hearablemusic.player.ui.generated.resources.agent_token_limits_global
import com.hearablemusic.player.ui.generated.resources.agent_token_limits_per_agent
import com.hearablemusic.player.ui.generated.resources.agent_token_limits_save
import com.hearablemusic.player.ui.generated.resources.agent_token_limits_title
import com.hearablemusic.player.ui.generated.resources.ai_endpoint
import com.hearablemusic.player.ui.generated.resources.ai_fetch_models
import com.hearablemusic.player.ui.generated.resources.ai_free_exhausted
import com.hearablemusic.player.ui.generated.resources.ai_free_hint
import com.hearablemusic.player.ui.generated.resources.ai_free_remaining
import com.hearablemusic.player.ui.generated.resources.ai_free_unavailable
import com.hearablemusic.player.ui.generated.resources.ai_free_used
import com.hearablemusic.player.ui.generated.resources.ai_key_needs_refill
import com.hearablemusic.player.ui.generated.resources.ai_paid_coming_soon
import com.hearablemusic.player.ui.generated.resources.ai_paid_description
import com.hearablemusic.player.ui.generated.resources.ai_preset_quick_fill
import com.hearablemusic.player.ui.generated.resources.ai_tab_custom
import com.hearablemusic.player.ui.generated.resources.ai_tab_free
import com.hearablemusic.player.ui.generated.resources.ai_tab_paid
import com.hearablemusic.player.ui.generated.resources.api_key
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.config_saved
import com.hearablemusic.player.ui.generated.resources.configured
import com.hearablemusic.player.ui.generated.resources.enter_api_key_placeholder
import com.hearablemusic.player.ui.generated.resources.model_name
import com.hearablemusic.player.ui.generated.resources.save
import com.hearablemusic.player.ui.generated.resources.test
import com.hearablemusic.player.ui.generated.resources.title_ai
import com.hmp.domain.enum.AiPresetEndpoints
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 7.5 P1 第二笔：原 `AIScreenContent` 20 参数转发层已合并回唯一 Screen 入口。
 * 其中 8 个参数（musicWithExtraCount / progress / autoBatchProcess 及批处理四件套）一路透传却从未被消费，合并时一并删掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(
    aiSettingsViewModel: AiSettingsViewModel = koinViewModel(),
    navController: NavBackStack<NavKey>,
) {
    val dialogManager = activityViewModel<DialogManagerViewModel>().dialogManager
    val metrics = LocalHmpMetrics.current

    LaunchedEffect(Unit) {
        aiSettingsViewModel.loadCustomAiConfig()
    }

    // 唯一状态出口：AiSettingsViewModel 的数据源收进 uiState，这里解包成同名局部变量
    val aiUiState by aiSettingsViewModel.uiState.collectAsState()
    val aiAccessMode = aiUiState.aiAccessMode
    val freeQuota = aiUiState.quotaUsage
    val freeAvailable = aiUiState.freeAvailable
    val customConfig = aiUiState.customAiConfig
    val availableModels = aiUiState.availableModels
    val isTestingApi = aiUiState.isTestingApi
    // 表单是整屏唯一一份快照：各区块只收 form 与动作 lambda，不再自己 collect VM。
    val form = aiUiState.form

    val onModeChange = aiSettingsViewModel::switchAiAccessMode
    val onSaveCustomConfig = aiSettingsViewModel::onSaveCustomConfigClicked
    val onFetchModelsClicked = aiSettingsViewModel::onFetchModelsClicked
    val onTestConnectionClicked = aiSettingsViewModel::onTestConnectionClicked
    val onEndpointChange = aiSettingsViewModel::onEndpointChange
    val onApiKeyChange = aiSettingsViewModel::onApiKeyChange
    val onModelChange = aiSettingsViewModel::onModelChange
    val onPresetSelected = aiSettingsViewModel::onPresetSelected
    val onReplyLanguageChange = aiSettingsViewModel::onReplyLanguageChange
    val onTokenLimitsEnabledChange = aiSettingsViewModel::onTokenLimitsEnabledChange
    val onGlobalDailyLimitChange = aiSettingsViewModel::onGlobalDailyLimitChange
    val onPerAgentLimitChange = aiSettingsViewModel::onPerAgentLimitChange
    val onSaveTokenLimits = aiSettingsViewModel::onSaveTokenLimits
    val onMemoryEnabledChange = aiSettingsViewModel::onMemoryEnabledChange
    val onForgetPortrait = aiSettingsViewModel::onForgetPortrait
    val onClearMemoryRequested = aiSettingsViewModel::onClearMemoryRequested
    val onClearMemoryDismissed = aiSettingsViewModel::onClearMemoryDismissed
    val onClearAllMemory = aiSettingsViewModel::onClearAllMemory
    val onBackClick: () -> Unit = { navController.removeLastOrNull() }
    // 一次性效果：测试/拉模型的结果走 SharedFlow，不再用 StateFlow + clear 模拟。
    // 文本以 UiText 承载，解析在 UI 层（toast 属一次性副作用，用挂起 getString 即可）。
    LaunchedEffect(Unit) {
        aiSettingsViewModel.effect.collect { effect ->
            when (effect) {
                is AiSettingsEffect.ShowMessage -> {
                    // 单条文案解析失败不能杀掉 collector，否则之后的测试/拉模型提示全部丢失
                    val text = runCatching { effect.text.resolveForEffect() }.getOrNull() ?: return@collect
                    dialogManager.showMessage(text)
                }
            }
        }
    }

    SubScreen(
        onBackClick = onBackClick,
        title = stringResource(Res.string.title_ai),
    ) {
        // F14-T1 自适应：竖屏/窄窗单栏限宽居中（~600dp）；横屏（手机横屏 / 平板、桌面宽窗）
        // 改双栏 —— 左栏 = 接入方式 + 语言语音，右栏 = Agent 管理 + 记忆管理。
        // Compact maxWidth = Dp.Unspecified → 单栏不加约束，与改造前逐像素一致。
        val window = LocalWindowSizeInfo.current
        val isWide = window.isExpanded || window.isMedium
        val isLandscape = window.isLandscape
        val formMaxWidth = if (isWide) LayoutConstraints.WidthBreakpointMedium else Dp.Unspecified
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (isLandscape) {
                // ── 横屏双栏 ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.block, vertical = metrics.cozy),
                    horizontalArrangement = Arrangement.spacedBy(metrics.block),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(metrics.section),
                    ) {
                        // 分区 1：AI 接入方式
                        SectionHeader(stringResource(Res.string.agent_ai_access_header))
                        AiAccessSection(
                            aiAccessMode = aiAccessMode,
                            freeQuota = freeQuota,
                            freeAvailable = freeAvailable,
                            customConfig = customConfig,
                            availableModels = availableModels,
                            isTestingApi = isTestingApi,
                            form = form,
                            onModeChange = onModeChange,
                            onSaveCustomConfigClicked = onSaveCustomConfig,
                            onFetchModelsClicked = onFetchModelsClicked,
                            onTestConnectionClicked = onTestConnectionClicked,
                            onEndpointChange = onEndpointChange,
                            onApiKeyChange = onApiKeyChange,
                            onModelChange = onModelChange,
                            onPresetSelected = onPresetSelected,
                        )
                        if (aiAccessMode == AiAccessMode.CUSTOM) {
                            TokenLimitsCard(
                                form = form,
                                onTokenLimitsEnabledChange = onTokenLimitsEnabledChange,
                                onGlobalDailyLimitChange = onGlobalDailyLimitChange,
                                onPerAgentLimitChange = onPerAgentLimitChange,
                                onSaveTokenLimits = onSaveTokenLimits,
                            )
                        }
                        // 分区 3：语言和语音
                        SectionHeader(stringResource(Res.string.agent_ai_lang_voice_header))
                        ReplyLanguageSection(
                            replyLanguage = form.replyLanguage,
                            onReplyLanguageChange = onReplyLanguageChange,
                        )
                        VoiceSection()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(metrics.section),
                    ) {
                        // 分区 2：Agent 管理
                        SectionHeader(stringResource(Res.string.agent_ai_agent_mgmt_header))
                        AgentQuickEntries(
                            onAgentClick = { role -> navController.add(Routes.AI.AgentConfig(role)) },
                        )
                        // 分区 4：记忆管理
                        SectionHeader(stringResource(Res.string.agent_ai_memory_header))
                        MemoryManagementSection(
                            form = form,
                            navController = navController,
                            onMemoryEnabledChange = onMemoryEnabledChange,
                            onForgetPortrait = onForgetPortrait,
                            onClearMemoryRequested = onClearMemoryRequested,
                            onClearMemoryDismissed = onClearMemoryDismissed,
                            onClearAllMemory = onClearAllMemory,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = formMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(metrics.section),
                    verticalArrangement = Arrangement.spacedBy(metrics.section),
                ) {
                    // ═══ 分区 1：AI 接入方式（折叠态只显示概要，展开才配细节）═══
                    SectionHeader(stringResource(Res.string.agent_ai_access_header))
                    AiAccessSection(
                        aiAccessMode = aiAccessMode,
                        freeQuota = freeQuota,
                        freeAvailable = freeAvailable,
                        customConfig = customConfig,
                        availableModels = availableModels,
                        isTestingApi = isTestingApi,
                        form = form,
                        onModeChange = onModeChange,
                        onSaveCustomConfigClicked = onSaveCustomConfig,
                        onFetchModelsClicked = onFetchModelsClicked,
                        onTestConnectionClicked = onTestConnectionClicked,
                        onEndpointChange = onEndpointChange,
                        onApiKeyChange = onApiKeyChange,
                        onModelChange = onModelChange,
                        onPresetSelected = onPresetSelected,
                    )
                    if (aiAccessMode == AiAccessMode.CUSTOM) {
                        TokenLimitsCard(
                            form = form,
                            onTokenLimitsEnabledChange = onTokenLimitsEnabledChange,
                            onGlobalDailyLimitChange = onGlobalDailyLimitChange,
                            onPerAgentLimitChange = onPerAgentLimitChange,
                            onSaveTokenLimits = onSaveTokenLimits,
                        )
                    }

                    // ═══ 分区 2：Agent 管理 —— 监控看板 + 配置入口 ═══
                    SectionHeader(stringResource(Res.string.agent_ai_agent_mgmt_header))
                    AgentQuickEntries(
                        onAgentClick = { role -> navController.add(Routes.AI.AgentConfig(role)) },
                    )

                    // ═══ 分区 3：语言和语音 —— 回复语言（可配）+ 语音对话（M7 gate 占位）═══
                    SectionHeader(stringResource(Res.string.agent_ai_lang_voice_header))
                    ReplyLanguageSection(
                        replyLanguage = form.replyLanguage,
                        onReplyLanguageChange = onReplyLanguageChange,
                    )
                    VoiceSection()

                    // ═══ 分区 4：记忆管理 —— 侧写展示 / 重置 / 操作日志 ═══
                    SectionHeader(stringResource(Res.string.agent_ai_memory_header))
                    MemoryManagementSection(
                        form = form,
                        navController = navController,
                        onMemoryEnabledChange = onMemoryEnabledChange,
                        onForgetPortrait = onForgetPortrait,
                        onClearMemoryRequested = onClearMemoryRequested,
                        onClearMemoryDismissed = onClearMemoryDismissed,
                        onClearAllMemory = onClearAllMemory,
                    )

                    Spacer(modifier = Modifier.height(metrics.row))
                }
            }
        }
    }
}

// ==================== AI 接入方式（可折叠）====================

private data class AccessSummary(val title: String, val detail: String)

/** 折叠态要显示的「当前方式概要」——只回答"现在用的是什么"，不堆参数。 */
@Composable
private fun accessSummary(
    mode: AiAccessMode,
    config: AiEndpointConfig,
    freeQuota: com.hmp.domain.agent.runtime.TokenSnapshot,
    freeAvailable: Boolean,
): AccessSummary = when (mode) {
    AiAccessMode.FREE -> AccessSummary(
        stringResource(Res.string.agent_ai_free_tier),
        if (!freeAvailable) {
            stringResource(Res.string.ai_free_unavailable)
        } else {
            stringResource(Res.string.agent_ai_free_remaining, fmtTokens(freeQuota.remaining))
        },
    )

    AiAccessMode.CUSTOM -> {
        val detail = when {
            config.selectedModel.isNotBlank() -> config.selectedModel
            config.endpoint.isNotBlank() -> config.endpoint
            else -> stringResource(Res.string.agent_ai_not_configured)
        }
        AccessSummary(stringResource(Res.string.agent_ai_custom_endpoint), detail)
    }

    AiAccessMode.PAID -> AccessSummary(stringResource(Res.string.agent_ai_paid_mode), stringResource(Res.string.agent_ai_coming_soon))
}

/**
 * AI 接入方式：默认**折叠**，只显示当前方式的概要；点击展开才是 tab 切换 + 详细配置。
 * 这样做的理由：端点/Key/模型属于"配一次就不管"的设置，默认铺开会让设置页首屏变得很长，
 * 把下面的 Agent 入口挤到折叠线以下。
 */
@Composable
private fun AiAccessSection(
    aiAccessMode: AiAccessMode,
    freeQuota: com.hmp.domain.agent.runtime.TokenSnapshot,
    freeAvailable: Boolean,
    customConfig: AiEndpointConfig,
    availableModels: List<String>,
    isTestingApi: Boolean,
    form: AIScreenFormState,
    onModeChange: (AiAccessMode) -> Unit,
    onSaveCustomConfigClicked: () -> Unit,
    onFetchModelsClicked: () -> Unit,
    onTestConnectionClicked: () -> Unit,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onPresetSelected: (String, String) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    var expanded by remember { mutableStateOf(false) }
    val summary = accessSummary(aiAccessMode, customConfig, freeQuota, freeAvailable)

    Column(verticalArrangement = Arrangement.spacedBy(metrics.base)) {
        // 概要行：始终可见，点击切换展开/折叠
        HMPCard(
            modifier = Modifier.clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.title,
                        style = typo.body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        summary.detail,
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(metrics.compact))
                Text(
                    if (expanded) stringResource(Res.string.agent_ai_collapse) else stringResource(Res.string.agent_ai_expand),
                    style = typo.footnote.medium(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (expanded) {
            val tabs = listOf(AiAccessMode.FREE, AiAccessMode.CUSTOM, AiAccessMode.PAID)
            SegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                options = tabs.map { mode ->
                    SegmentedOption(
                        id = mode.name,
                        label = stringResource(
                            when (mode) {
                                AiAccessMode.FREE -> Res.string.ai_tab_free
                                AiAccessMode.CUSTOM -> Res.string.ai_tab_custom
                                AiAccessMode.PAID -> Res.string.ai_tab_paid
                            },
                        ),
                    )
                },
                selectedOption = aiAccessMode.name,
                onOptionSelected = { id -> onModeChange(AiAccessMode.valueOf(id)) },
            )
            when (aiAccessMode) {
                AiAccessMode.FREE -> FreeTrialContent(
                    freeQuota = freeQuota,
                    freeAvailable = freeAvailable,
                )

                AiAccessMode.CUSTOM -> CustomConfigContent(
                    form = form,
                    customConfig = customConfig,
                    availableModels = availableModels,
                    isTestingApi = isTestingApi,
                    onSaveCustomConfigClicked = onSaveCustomConfigClicked,
                    onFetchModelsClicked = onFetchModelsClicked,
                    onTestConnectionClicked = onTestConnectionClicked,
                    onEndpointChange = onEndpointChange,
                    onApiKeyChange = onApiKeyChange,
                    onModelChange = onModelChange,
                    onPresetSelected = onPresetSelected,
                )

                AiAccessMode.PAID -> PaidModeContent()
            }
        }
    }
}

// ==================== 免费体验 Tab ====================

@Composable
private fun FreeTrialContent(
    freeQuota: com.hmp.domain.agent.runtime.TokenSnapshot,
    freeAvailable: Boolean,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    TitleWidget(title = stringResource(Res.string.ai_tab_free)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.cozy),
        ) {
            // 免费入口的显示条件是「有没有可用的内置端点」，不是平台
            if (!freeAvailable) {
                Text(
                    text = stringResource(Res.string.ai_free_unavailable),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else {
                val remaining = freeQuota.remaining
                Text(
                    text = stringResource(Res.string.ai_free_remaining, fmtTokens(remaining)),
                    style = typo.headline,
                    color = if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                LinearProgressIndicator(
                    progress = { freeQuota.rate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.micro)
                        .clip(RoundedCornerShape(metrics.hair)),
                    color = if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.ai_free_used, fmtTokens(freeQuota.used), fmtTokens(freeQuota.quota)),
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (remaining <= 0) {
                    Text(
                        text = stringResource(Res.string.ai_free_exhausted),
                        style = typo.body,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.ai_free_hint),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ==================== 自定义配置 Tab ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomConfigContent(
    form: AIScreenFormState,
    customConfig: AiEndpointConfig,
    availableModels: List<String>,
    isTestingApi: Boolean,
    onSaveCustomConfigClicked: () -> Unit,
    onFetchModelsClicked: () -> Unit,
    onTestConnectionClicked: () -> Unit,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onPresetSelected: (String, String) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val endpointValue = form.endpoint
    // D7-04：密钥不能走 rememberSaveable —— 它会把值写进 SavedStateHandle / Bundle，
    // Android 上等于把明文密钥交给系统保存态（dumpsys 可读、生命周期比进程更长），
    // 把 SecureStorageHelper 那套密文存储整套抵消掉。代价是进程被回收后输入框清空，
    // 可接受：保存本来就要显式点按钮。endpoint / model 不是密钥，保持 rememberSaveable。
    val apiKeyValue = form.apiKey
    val modelValue = form.model
    var showPassword by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    // 当配置加载后更新模型
    TitleWidget(title = stringResource(Res.string.ai_tab_custom)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            verticalArrangement = Arrangement.spacedBy(metrics.base),
        ) {
            // D7-01：Android 换机 / 云备份恢复后，DataStore 里的密文回来了、KeyStore 密钥没回来，
            // 解密必然失败。以前这条路会直接抛到 viewModelScope 上（打开 AI 页即崩溃），
            // 现在降级为空值 + 一句明确提示 —— 不然用户只看到"我明明填过，怎么又空了"。
            if (customConfig.keyNeedsRefill) {
                Text(
                    text = stringResource(Res.string.ai_key_needs_refill),
                    style = typo.body,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // 预设快捷按钮
            Text(
                text = stringResource(Res.string.ai_preset_quick_fill),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(metrics.compact),
            ) {
                AiPresetEndpoints.ALL.forEach { preset ->
                    Button(
                        onClick = {
                            onPresetSelected(preset.endpoint, preset.defaultModel)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(preset.displayName, style = typo.footnote.medium())
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Endpoint 输入
            OutlinedTextField(
                value = endpointValue,
                onValueChange = onEndpointChange,
                label = { Text(stringResource(Res.string.ai_endpoint)) },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(metrics.base),
            )

            // F12-T3：端点窗口前提声明（无法配置时探测，故显式告知用户前提是 ≥64K）
            Text(
                stringResource(Res.string.agent_ai_endpoint_prereq),
                style = typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // API Key 输入
            TextField(
                value = apiKeyValue,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(Res.string.api_key), color = MaterialTheme.colorScheme.onBackground) },
                placeholder = { Text(stringResource(Res.string.enter_api_key_placeholder, ""), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Transparent,
                    unfocusedIndicatorColor = Transparent,
                    disabledIndicatorColor = Transparent,
                ),
                shape = RoundedCornerShape(LayoutConstraints.ShadowElevation),
                modifier = Modifier.fillMaxWidth(),
            )

            // 获取模型按钮
            Button(
                onClick = onFetchModelsClicked,
                enabled = endpointValue.isNotBlank() && apiKeyValue.isNotBlank() && !isTestingApi,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTestingApi) {
                    CircularProgressIndicator(modifier = Modifier.size(metrics.cozy), strokeWidth = metrics.hair, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(metrics.compact))
                }
                Text(stringResource(Res.string.ai_fetch_models), color = MaterialTheme.colorScheme.onPrimary)
            }

            // 模型下拉选择
            if (availableModels.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded },
                ) {
                    TextField(
                        value = modelValue.ifBlank { availableModels.firstOrNull() ?: "" },
                        onValueChange = onModelChange,
                        readOnly = true,
                        label = { Text(stringResource(Res.string.model_name)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Transparent,
                            unfocusedIndicatorColor = Transparent,
                            disabledIndicatorColor = Transparent,
                        ),
                        shape = RoundedCornerShape(metrics.base),
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false },
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    onModelChange(model)
                                    modelExpanded = false
                                },
                            )
                        }
                    }
                }
            } else if (modelValue.isNotBlank()) {
                OutlinedTextField(
                    value = modelValue,
                    onValueChange = onModelChange,
                    label = { Text(stringResource(Res.string.model_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(metrics.base),
                )
            }

            // 测试 + 保存按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
            ) {
                Button(
                    onClick = onTestConnectionClicked,
                    enabled = endpointValue.isNotBlank() && apiKeyValue.isNotBlank() && !isTestingApi,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isTestingApi) {
                        CircularProgressIndicator(modifier = Modifier.size(metrics.cozy), strokeWidth = metrics.hair, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(Res.string.test), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Button(
                    onClick = onSaveCustomConfigClicked,
                    enabled = endpointValue.isNotBlank() && apiKeyValue.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.save), color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // 配置状态
            if (customConfig.isConfigured) {
                Text(
                    text = stringResource(Res.string.configured),
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 自定义模式的用量限额（T6）。语义与 `TokenQuotaGate` 对齐：
 * - 关掉开关 = 完全不限；
 * - 全局 / 每个 Agent 留空 = 该项不限；
 * - 窗口是**每日**（免费模式才是总额 100M，两者不混）。
 */
@Composable
private fun TokenLimitsCard(
    form: AIScreenFormState,
    onTokenLimitsEnabledChange: (Boolean) -> Unit,
    onGlobalDailyLimitChange: (String) -> Unit,
    onPerAgentLimitChange: (String, String) -> Unit,
    onSaveTokenLimits: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val enabled = form.tokenLimitsEnabled
    val globalLimit = form.globalDailyLimit
    val perAgent = form.perAgentLimits
    val saved = form.tokenLimitsSaved

    Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(Res.string.agent_token_limits_title),
            style = typo.subtitle,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.agent_token_limits_enable),
                style = typo.body,
            )
            Switch(checked = enabled, onCheckedChange = onTokenLimitsEnabledChange)
        }

        if (enabled) {
            OutlinedTextField(
                value = globalLimit,
                onValueChange = onGlobalDailyLimitChange,
                label = { Text(stringResource(Res.string.agent_token_limits_global)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            listOf("master", "radio", "enrich", "hello").forEach { key ->
                OutlinedTextField(
                    value = perAgent[key].orEmpty(),
                    onValueChange = { value -> onPerAgentLimitChange(key, value) },
                    label = { Text("${key.replaceFirstChar { it.uppercase() }} · ${stringResource(Res.string.agent_token_limits_per_agent)}") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = onSaveTokenLimits,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.agent_token_limits_save), color = MaterialTheme.colorScheme.onPrimary)
        }
        if (saved) {
            Text(
                text = stringResource(Res.string.config_saved),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ==================== 付费模式 Tab ====================

@Composable
private fun PaidModeContent() {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    TitleWidget(title = stringResource(Res.string.ai_tab_paid)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.block),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.cozy),
        ) {
            Text(
                text = stringResource(Res.string.ai_paid_coming_soon),
                style = typo.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.ai_paid_description),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class AgentEntry(val role: String, val label: String)

// ==================== 全局 Agent 参数 ====================

/**
 * 回复语言（真实可配，**全局默认值**）。
 * 读/写 GlobalAgentConfig（DataStore key "agent_global"）。
 * 子页面 AgentConfigScreen 的语言选择器有「跟随全局」选项，取值即来自这里。
 */
@Composable
private fun ReplyLanguageSection(
    replyLanguage: String,
    onReplyLanguageChange: (String) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current

    Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentedControl(
                modifier = Modifier.weight(1f),
                options = listOf(
                    SegmentedOption("zh", stringResource(Res.string.agent_ai_lang_zh)),
                    SegmentedOption("en", stringResource(Res.string.agent_ai_lang_en)),
                    SegmentedOption("auto", stringResource(Res.string.agent_ai_lang_auto)),
                ),
                selectedOption = replyLanguage,
                onOptionSelected = onReplyLanguageChange,
            )
        }
        Text(
            stringResource(Res.string.agent_ai_lang_follow_global_desc),
            style = typo.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ==================== 从 HEAD 恢复（误删函数，已还原）====================

@Composable
private fun VoiceSection() {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    HMPCard(contentPadding = Modifier.padding(metrics.cozy)) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
            Text(
                text = stringResource(Res.string.agent_ai_voice_title),
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.agent_ai_voice_desc),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.agent_ai_voice_locked),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * 分区 5：记忆与信任 —— TrustLedger 档位回拨 UI。
 * 三档 SegmentedControl + alwaysAllow 重置按钮。
 */
/** 来源分档 → 友好标签（契约 §2.5 五档）。 */

/**
 * 分区 6：记忆管理 —— 把"伙伴对你的认识"透明化：
 * ① 概览（已有几条侧写）② 每条侧写可单独"忘记"
 * ③ 重置认识（带二次确认）④ 伙伴操作日志入口。
 */
@Composable
private fun MemoryManagementSection(
    form: AIScreenFormState,
    navController: NavBackStack<NavKey>,
    onMemoryEnabledChange: (Boolean) -> Unit,
    onForgetPortrait: (String) -> Unit,
    onClearMemoryRequested: () -> Unit,
    onClearMemoryDismissed: () -> Unit,
    onClearAllMemory: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val portraits = form.portraits
    val showConfirmClear = form.showConfirmClear
    val memoryEnabled = form.memoryEnabled

    HMPCard(contentPadding = Modifier.padding(metrics.cozy)) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.base)) {
            // 总开关：记忆功能（关闭后伙伴不再记住你，已记住内容被清空）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_feature),
                        style = typo.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_off_desc),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = memoryEnabled,
                    onCheckedChange = onMemoryEnabledChange,
                )
            }

            if (memoryEnabled) {
                // ① 概览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_about_you),
                        style = typo.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_count, portraits.size),
                        style = typo.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (portraits.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_empty),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // ② 每条认识 + 可单独"忘记"
                    portraits.take(6).forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.type.displayName + if (p.type.weakEvidence) stringResource(Res.string.agent_ai_memory_inferred) else "",
                                    style = typo.footnote,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val detail = p.slots.values.filter { it.isNotBlank() }.joinToString("、")
                                if (detail.isNotBlank()) {
                                    Text(
                                        text = detail,
                                        style = typo.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(Res.string.agent_ai_memory_forget),
                                style = typo.footnote.medium(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onForgetPortrait(p.type.id) }
                                    .padding(metrics.compact),
                            )
                        }
                    }
                    if (portraits.size > 6) {
                        Text(
                            text = stringResource(Res.string.agent_ai_memory_more, portraits.size - 6),
                            style = typo.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ③ 重置认识（按钮与描述同一行，带二次确认）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.agent_ai_memory_reset),
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.agent_ai_memory_reset_desc),
                            style = typo.footnote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onClearMemoryRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(Res.string.agent_ai_memory_reset_btn), color = MaterialTheme.colorScheme.onError)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ④ 伙伴操作日志入口（移到最后）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(metrics.compact))
                        .clickable { navController.add(Routes.Settings.AuditLog) }
                        .padding(vertical = metrics.compact, horizontal = metrics.micro),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.agent_ai_memory_log_title),
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.agent_ai_memory_log_desc),
                            style = typo.footnote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_log_view),
                        style = typo.footnote.medium(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.agent_ai_memory_disabled_note),
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = onClearMemoryDismissed,
            confirmButton = {
                TextButton(
                    onClick = onClearAllMemory,
                ) { Text(stringResource(Res.string.agent_ai_reset_confirm_btn), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = onClearMemoryDismissed) { Text(stringResource(Res.string.cancel)) }
            },
            title = { Text(stringResource(Res.string.agent_ai_reset_dialog_title)) },
            text = { Text(stringResource(Res.string.agent_ai_reset_dialog_text)) },
        )
    }
}

@Composable
private fun AgentQuickEntries(
    onAgentClick: (String) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val agents = listOf(
        AgentEntry("master", "Master"),
        AgentEntry("radio", "Radio"),
        AgentEntry("enrich", "Enrich"),
        AgentEntry("hello", "Hello"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.compact),
    ) {
        agents.forEach { entry ->
            HMPCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAgentClick(entry.role) },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(metrics.micro),
                ) {
                    val icon = agentIcon(entry.role)
                    if (icon != null) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(metrics.block),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        entry.label,
                        style = typo.caption,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
