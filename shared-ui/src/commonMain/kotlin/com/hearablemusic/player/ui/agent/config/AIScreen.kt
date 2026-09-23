package com.hearablemusic.player.ui.agent.config
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.hearablemusic.player.ui.agent.agentIcon
import com.hearablemusic.player.ui.common.components.SectionHeader
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.enum.AiPresetEndpoints
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.agent.profile.PortraitDraft
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.ai_endpoint
import com.hearablemusic.player.ui.generated.resources.ai_fetch_models
import com.hearablemusic.player.ui.generated.resources.ai_free_exhausted
import com.hearablemusic.player.ui.generated.resources.ai_free_hint
import com.hearablemusic.player.ui.generated.resources.ai_free_remaining
import com.hearablemusic.player.ui.generated.resources.ai_free_used
import com.hearablemusic.player.ui.generated.resources.ai_paid_coming_soon
import com.hearablemusic.player.ui.generated.resources.ai_paid_description
import com.hearablemusic.player.ui.generated.resources.ai_preset_quick_fill
import com.hearablemusic.player.ui.generated.resources.ai_tab_custom
import com.hearablemusic.player.ui.generated.resources.ai_tab_free
import com.hearablemusic.player.ui.generated.resources.ai_tab_paid
import com.hearablemusic.player.ui.generated.resources.api_key
import com.hearablemusic.player.ui.generated.resources.auto_background_completion
import com.hearablemusic.player.ui.generated.resources.auto_background_completion_desc
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.config_saved
import com.hearablemusic.player.ui.generated.resources.configured
import com.hearablemusic.player.ui.generated.resources.enter_api_key_placeholder
import com.hearablemusic.player.ui.generated.resources.model_name
import com.hearablemusic.player.ui.generated.resources.pause
import com.hearablemusic.player.ui.generated.resources.paused
import com.hearablemusic.player.ui.generated.resources.please_config_provider
import com.hearablemusic.player.ui.generated.resources.please_enter_api_key
import com.hearablemusic.player.ui.generated.resources.resume
import com.hearablemusic.player.ui.generated.resources.save
import com.hearablemusic.player.ui.generated.resources.test
import com.hearablemusic.player.ui.generated.resources.title_ai
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.agent.config.AiSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AIScreen(
    aiSettingsViewModel: AiSettingsViewModel = koinViewModel(),
    recommendationViewModel: RecommendationViewModel = activityViewModel(),
    libraryViewModel: LibraryViewModel = activityViewModel(),
    masterAgent: MasterAgent = koinInject(),
    navController: NavBackStack<NavKey>
) {
    val dialogManager = activityViewModel<DialogManagerViewModel>().dialogManager
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        aiSettingsViewModel.loadCustomAiConfig()
    }

    val musicWithExtraCount by libraryViewModel.musicWithExtraCount.collectAsState(initial = 0)
    val pendingCount by recommendationViewModel.pendingMusicCount.collectAsState(initial = 0)
    val aiAccessMode by aiSettingsViewModel.aiAccessMode.collectAsState()
    val freeTrialRemaining by aiSettingsViewModel.aiFreeTrialRemainingCount.collectAsState()
    val customConfig by aiSettingsViewModel.customAiConfig.collectAsState()
    val availableModels by aiSettingsViewModel.availableModels.collectAsState()
    val isTestingApi by aiSettingsViewModel.isTestingApi.collectAsState()
    val apiTestResult by aiSettingsViewModel.apiTestResult.collectAsState()
    val progress by recommendationViewModel.processingProgress.collectAsState()
    val autoBatchProcess by aiSettingsViewModel.autoBatchProcess.collectAsState()

    AIScreenContent(
        aiAccessMode = aiAccessMode,
        freeTrialRemaining = freeTrialRemaining,
        customConfig = customConfig,
        availableModels = availableModels,
        isTestingApi = isTestingApi,
        apiTestResult = apiTestResult,
        musicWithExtraCount = musicWithExtraCount,
        pendingCount = pendingCount,
        progress = progress,
        autoBatchProcess = autoBatchProcess,
        onClearAllMemory = { scope.launch { masterAgent.clearAllMemory() } },
        onModeChange = aiSettingsViewModel::switchAiAccessMode,
        onSaveCustomConfig = aiSettingsViewModel::saveCustomAiConfig,
        onFetchModels = aiSettingsViewModel::fetchAvailableModels,
        onTestConnection = aiSettingsViewModel::testAiConnection,
        onClearTestResult = aiSettingsViewModel::clearApiTestResult,
        onAutoBatchProcessChange = aiSettingsViewModel::saveAutoBatchProcess,
        startAutoProcessExtraInfo = recommendationViewModel::startAutoProcessWithCurrentProvider,
        pauseProcess = recommendationViewModel::pauseProcessing,
        resumeProcess = recommendationViewModel::resumeProcessing,
        cancelProcess = recommendationViewModel::cancelProcessing,
        dialogManager = dialogManager,
        navController = navController,
        masterAgent = masterAgent,
        onBackClick = { navController.removeLastOrNull() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIScreenContent(
    aiAccessMode: AiAccessMode,
    freeTrialRemaining: Int,
    customConfig: AiEndpointConfig,
    availableModels: List<String>,
    isTestingApi: Boolean,
    apiTestResult: AiSettingsViewModel.ApiTestResult?,
    musicWithExtraCount: Int,
    pendingCount: Int,
    progress: RecommendationViewModel.BatchProcessingProgress,
    autoBatchProcess: Boolean,
    onClearAllMemory: () -> Unit,
    onModeChange: (AiAccessMode) -> Unit,
    onSaveCustomConfig: (String, String, String) -> Unit,
    onFetchModels: (String, String) -> Unit,
    onTestConnection: (String, String) -> Unit,
    onClearTestResult: () -> Unit,
    onAutoBatchProcessChange: (Boolean) -> Unit,
    startAutoProcessExtraInfo: () -> Unit,
    pauseProcess: () -> Unit,
    resumeProcess: () -> Unit,
    cancelProcess: () -> Unit,
    dialogManager: DialogManager,
    navController: NavBackStack<NavKey>,
    masterAgent: MasterAgent,
    onBackClick: () -> Unit
) {
    // 显示测试结果 Toast
    LaunchedEffect(apiTestResult) {
        apiTestResult?.let { result ->
            val message = when (result) {
                is AiSettingsViewModel.ApiTestResult.Success -> result.message
                is AiSettingsViewModel.ApiTestResult.Error -> result.message
            }
            dialogManager.showMessage(message)
            onClearTestResult()
        }
    }

    SubScreen(
        onBackClick = onBackClick,
        title = stringResource(Res.string.title_ai)
    ) {
        // F14-T1 自适应：竖屏/窄窗单栏限宽居中（~600dp）；横屏（手机横屏 / 平板、桌面宽窗）
        // 改双栏 —— 左栏 = 接入方式 + 语言语音，右栏 = Agent 管理 + 记忆管理。
        // Compact maxWidth = Dp.Unspecified → 单栏不加约束，与改造前逐像素一致。
        val window = LocalWindowSizeInfo.current
        val isWide = window.isExpanded || window.isMedium
        val isLandscape = window.isLandscape
        val dimens = LocalHMPDimens.current
        val formMaxWidth = if (isWide) 600.dp else Dp.Unspecified
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        if (isLandscape) {
        // ── 横屏双栏 ──
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.spacing.xl, vertical = dimens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xl),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
            ) {
                // 分区 1：AI 接入方式
                SectionHeader(stringResource(Res.string.agent_ai_access_header))
                AiAccessSection(
                    aiAccessMode = aiAccessMode,
                    freeTrialRemaining = freeTrialRemaining,
                    customConfig = customConfig,
                    availableModels = availableModels,
                    isTestingApi = isTestingApi,
                    pendingCount = pendingCount,
                    onModeChange = onModeChange,
                    onSaveConfig = onSaveCustomConfig,
                    onFetchModels = onFetchModels,
                    onTestConnection = onTestConnection,
                    dialogManager = dialogManager,
                )
                // 分区 3：语言和语音
                SectionHeader(stringResource(Res.string.agent_ai_lang_voice_header))
                ReplyLanguageSection(masterAgent = masterAgent)
                VoiceSection()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg),
            ) {
                // 分区 2：Agent 管理
                SectionHeader(stringResource(Res.string.agent_ai_agent_mgmt_header))
                AgentQuickEntries(
                    masterAgent = masterAgent,
                    onAgentClick = { role -> navController.add(Routes.AI.AgentConfig(role)) }
                )
                // 分区 4：记忆管理
                SectionHeader(stringResource(Res.string.agent_ai_memory_header))
                MemoryManagementSection(
                    masterAgent = masterAgent,
                    navController = navController,
                    onClearAllMemory = onClearAllMemory
                )
            }
        }
        } else {
        Column(
            modifier = Modifier
                .widthIn(max = formMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(dimens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.lg)
        ) {
            // ═══ 分区 1：AI 接入方式（折叠态只显示概要，展开才配细节）═══
            SectionHeader(stringResource(Res.string.agent_ai_access_header))
            AiAccessSection(
                aiAccessMode = aiAccessMode,
                freeTrialRemaining = freeTrialRemaining,
                customConfig = customConfig,
                availableModels = availableModels,
                isTestingApi = isTestingApi,
                pendingCount = pendingCount,
                onModeChange = onModeChange,
                onSaveConfig = onSaveCustomConfig,
                onFetchModels = onFetchModels,
                onTestConnection = onTestConnection,
                dialogManager = dialogManager,
            )

            // ═══ 分区 2：Agent 管理 —— 监控看板 + 配置入口 ═══
            SectionHeader(stringResource(Res.string.agent_ai_agent_mgmt_header))
            AgentQuickEntries(
                masterAgent = masterAgent,
                onAgentClick = { role -> navController.add(Routes.AI.AgentConfig(role)) }
            )

            // ═══ 分区 3：语言和语音 —— 回复语言（可配）+ 语音对话（M7 gate 占位）═══
            SectionHeader(stringResource(Res.string.agent_ai_lang_voice_header))
            ReplyLanguageSection(masterAgent = masterAgent)
            VoiceSection()

            // ═══ 分区 4：记忆管理 —— 侧写展示 / 重置 / 操作日志 ═══
            SectionHeader(stringResource(Res.string.agent_ai_memory_header))
            MemoryManagementSection(
                masterAgent = masterAgent,
                navController = navController,
                onClearAllMemory = onClearAllMemory
            )

            Spacer(modifier = Modifier.height(64.dp))
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
    freeTrialRemaining: Int,
): AccessSummary = when (mode) {
    AiAccessMode.FREE -> AccessSummary(stringResource(Res.string.agent_ai_free_tier), stringResource(Res.string.agent_ai_free_remaining, freeTrialRemaining))
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
    freeTrialRemaining: Int,
    customConfig: AiEndpointConfig,
    availableModels: List<String>,
    isTestingApi: Boolean,
    pendingCount: Int,
    onModeChange: (AiAccessMode) -> Unit,
    onSaveConfig: (String, String, String) -> Unit,
    onFetchModels: (String, String) -> Unit,
    onTestConnection: (String, String) -> Unit,
    dialogManager: DialogManager,
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = accessSummary(aiAccessMode, customConfig, freeTrialRemaining)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 概要行：始终可见，点击切换展开/折叠
        HMPCard(
            modifier = Modifier.clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        summary.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (expanded) stringResource(Res.string.agent_ai_collapse) else stringResource(Res.string.agent_ai_expand),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
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
                            }
                        )
                    )
                },
                selectedOption = aiAccessMode.name,
                onOptionSelected = { id -> onModeChange(AiAccessMode.valueOf(id)) }
            )
            when (aiAccessMode) {
                AiAccessMode.FREE -> FreeTrialContent(
                    freeTrialRemaining = freeTrialRemaining,
                    pendingCount = pendingCount
                )
                AiAccessMode.CUSTOM -> CustomConfigContent(
                    customConfig = customConfig,
                    availableModels = availableModels,
                    isTestingApi = isTestingApi,
                    onSaveConfig = onSaveConfig,
                    onFetchModels = onFetchModels,
                    onTestConnection = onTestConnection,
                    dialogManager = dialogManager
                )
                AiAccessMode.PAID -> PaidModeContent()
            }
        }
    }
}

// ==================== 免费体验 Tab ====================

@Composable
private fun FreeTrialContent(
    freeTrialRemaining: Int,
    pendingCount: Int
) {
    TitleWidget(title = stringResource(Res.string.ai_tab_free)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 剩余次数显示
            Text(
                text = stringResource(Res.string.ai_free_remaining, freeTrialRemaining),
                style = MaterialTheme.typography.headlineMedium,
                color = if (freeTrialRemaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // 进度条
            val usedPercent = ((100 - freeTrialRemaining).coerceAtLeast(0) / 100f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { usedPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (freeTrialRemaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = stringResource(Res.string.ai_free_used, 100 - freeTrialRemaining, 100),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (freeTrialRemaining <= 0) {
                Text(
                    text = stringResource(Res.string.ai_free_exhausted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(Res.string.ai_free_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==================== 自定义配置 Tab ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomConfigContent(
    customConfig: AiEndpointConfig,
    availableModels: List<String>,
    isTestingApi: Boolean,
    onSaveConfig: (String, String, String) -> Unit,
    onFetchModels: (String, String) -> Unit,
    onTestConnection: (String, String) -> Unit,
    dialogManager: DialogManager
) {
    val scope = rememberCoroutineScope()
    var endpointValue by rememberSaveable { mutableStateOf(customConfig.endpoint) }
    var apiKeyValue by rememberSaveable { mutableStateOf("") }
    var modelValue by rememberSaveable { mutableStateOf(customConfig.selectedModel) }
    var showPassword by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    // 当配置加载后更新模型
    LaunchedEffect(customConfig) {
        if (customConfig.selectedModel.isNotBlank()) {
            modelValue = customConfig.selectedModel
        }
    }

    TitleWidget(title = stringResource(Res.string.ai_tab_custom)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 预设快捷按钮
            Text(
                text = stringResource(Res.string.ai_preset_quick_fill),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiPresetEndpoints.ALL.forEach { preset ->
                    Button(
                        onClick = {
                            endpointValue = preset.endpoint
                            modelValue = preset.defaultModel
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(preset.displayName, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Endpoint 输入
            OutlinedTextField(
                value = endpointValue,
                onValueChange = { endpointValue = it },
                label = { Text(stringResource(Res.string.ai_endpoint)) },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // F12-T3：端点窗口前提声明（无法配置时探测，故显式告知用户前提是 ≥64K）
            Text(
                stringResource(Res.string.agent_ai_endpoint_prereq),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // API Key 输入
            TextField(
                value = apiKeyValue,
                onValueChange = { apiKeyValue = it },
                label = { Text(stringResource(Res.string.api_key), color = MaterialTheme.colorScheme.onBackground) },
                placeholder = { Text(stringResource(Res.string.enter_api_key_placeholder, ""), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Transparent,
                    unfocusedIndicatorColor = Transparent,
                    disabledIndicatorColor = Transparent
                ),
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 获取模型按钮
            Button(
                onClick = {
                    if (endpointValue.isNotBlank() && apiKeyValue.isNotBlank()) {
                        onFetchModels(endpointValue, apiKeyValue)
                    } else {
                        scope.launch { dialogManager.showMessage(getString(Res.string.please_enter_api_key)) }
                    }
                },
                enabled = endpointValue.isNotBlank() && apiKeyValue.isNotBlank() && !isTestingApi,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTestingApi) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.ai_fetch_models), color = MaterialTheme.colorScheme.onPrimary)
            }

            // 模型下拉选择
            if (availableModels.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    TextField(
                        value = modelValue.ifBlank { availableModels.firstOrNull() ?: "" },
                        onValueChange = { modelValue = it },
                        readOnly = true,
                        label = { Text(stringResource(Res.string.model_name)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Transparent,
                            unfocusedIndicatorColor = Transparent,
                            disabledIndicatorColor = Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    modelValue = model
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            } else if (modelValue.isNotBlank()) {
                OutlinedTextField(
                    value = modelValue,
                    onValueChange = { modelValue = it },
                    label = { Text(stringResource(Res.string.model_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 测试 + 保存按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (endpointValue.isNotBlank() && apiKeyValue.isNotBlank()) {
                            onTestConnection(endpointValue, apiKeyValue)
                        }
                    },
                    enabled = endpointValue.isNotBlank() && apiKeyValue.isNotBlank() && !isTestingApi,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTestingApi) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(Res.string.test), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Button(
                    onClick = {
                        if (endpointValue.isNotBlank() && apiKeyValue.isNotBlank()) {
                            onSaveConfig(endpointValue, apiKeyValue, modelValue)
                            scope.launch { dialogManager.showMessage(getString(Res.string.config_saved)) }
                        } else {
                            scope.launch { dialogManager.showMessage(getString(Res.string.please_enter_api_key)) }
                        }
                    },
                    enabled = endpointValue.isNotBlank() && apiKeyValue.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.save), color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // 配置状态
            if (customConfig.isConfigured) {
                Text(
                    text = stringResource(Res.string.configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==================== 付费模式 Tab ====================

@Composable
private fun PaidModeContent() {
    TitleWidget(title = stringResource(Res.string.ai_tab_paid)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.ai_paid_coming_soon),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.ai_paid_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
    masterAgent: MasterAgent,
) {
    val scope = rememberCoroutineScope()
    val globalConfig = remember { masterAgent.getGlobalAgentConfig() }
    var replyLanguage by remember { mutableStateOf(globalConfig.replyLanguage) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedControl(
                modifier = Modifier.weight(1f),
                options = listOf(
                    SegmentedOption("zh", stringResource(Res.string.agent_ai_lang_zh)),
                    SegmentedOption("en", stringResource(Res.string.agent_ai_lang_en)),
                    SegmentedOption("auto", stringResource(Res.string.agent_ai_lang_auto)),
                ),
                selectedOption = replyLanguage,
                onOptionSelected = { key ->
                    replyLanguage = key
                    scope.launch {
                        masterAgent.saveGlobalAgentConfig(globalConfig.copy(replyLanguage = key))
                    }
                }
            )
        }
        Text(
            stringResource(Res.string.agent_ai_lang_follow_global_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

}

// ==================== 从 HEAD 恢复（误删函数，已还原）====================

@Composable
private fun VoiceSection() {
    HMPCard(contentPadding = Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.agent_ai_voice_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.agent_ai_voice_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Res.string.agent_ai_voice_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
    masterAgent: MasterAgent,
    navController: NavBackStack<NavKey>,
    onClearAllMemory: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var portraits by remember { mutableStateOf<List<PortraitDraft>>(emptyList()) }
    var showConfirmClear by remember { mutableStateOf(false) }
    var memoryEnabled by remember { mutableStateOf(masterAgent.getGlobalAgentConfig().memoryEnabled) }

    suspend fun load() {
        val mem = masterAgent.userMemory ?: return
        portraits = mem.currentPortraits()
    }
    LaunchedEffect(Unit) { load() }

    HMPCard(contentPadding = Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 总开关：记忆功能（关闭后伙伴不再记住你，已记住内容被清空）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_feature),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_off_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = memoryEnabled,
                    onCheckedChange = { enabled ->
                        memoryEnabled = enabled
                        scope.launch {
                            masterAgent.saveGlobalAgentConfig(
                                masterAgent.getGlobalAgentConfig().copy(memoryEnabled = enabled)
                            )
                            load()
                        }
                    }
                )
            }

            if (memoryEnabled) {
            // ① 概览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.agent_ai_memory_about_you),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.agent_ai_memory_count, portraits.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (portraits.isEmpty()) {
                Text(
                    text = stringResource(Res.string.agent_ai_memory_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // ② 每条认识 + 可单独"忘记"
                portraits.take(6).forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p.type.displayName + if (p.type.weakEvidence) stringResource(Res.string.agent_ai_memory_inferred) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val detail = p.slots.values.filter { it.isNotBlank() }.joinToString("、")
                            if (detail.isNotBlank()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = stringResource(Res.string.agent_ai_memory_forget),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { scope.launch { masterAgent.forgetPortrait(p.type.id); load() } }
                                .padding(8.dp)
                        )
                    }
                }
                if (portraits.size > 6) {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_more, portraits.size - 6),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ③ 重置认识（按钮与描述同一行，带二次确认）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_reset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_reset_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showConfirmClear = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.agent_ai_memory_reset_btn), color = MaterialTheme.colorScheme.onError)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ④ 伙伴操作日志入口（移到最后）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navController.add(Routes.Settings.AuditLog) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_log_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Res.string.agent_ai_memory_log_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(Res.string.agent_ai_memory_log_view),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            } else {
                Text(
                    text = stringResource(Res.string.agent_ai_memory_disabled_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmClear = false
                        onClearAllMemory()
                        portraits = emptyList()
                    }
                ) { Text(stringResource(Res.string.agent_ai_reset_confirm_btn), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) { Text(stringResource(Res.string.cancel)) }
            },
            title = { Text(stringResource(Res.string.agent_ai_reset_dialog_title)) },
            text = { Text(stringResource(Res.string.agent_ai_reset_dialog_text)) }
        )
    }
}

@Composable
private fun AgentQuickEntries(
    masterAgent: MasterAgent,
    onAgentClick: (String) -> Unit,
) {
    val agents = listOf(
        AgentEntry("master", "Master"),
        AgentEntry("radio", "Radio"),
        AgentEntry("enrich", "Enrich"),
        AgentEntry("hello", "Hello"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val icon = agentIcon(entry.role)
                    if (icon != null) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

