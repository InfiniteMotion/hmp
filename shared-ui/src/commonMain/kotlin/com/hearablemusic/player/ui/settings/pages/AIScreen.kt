package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hmp.domain.agent.runtime.CapabilityState
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.enum.AiPresetEndpoints
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
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
import com.hearablemusic.player.ui.generated.resources.completed_music_count
import com.hearablemusic.player.ui.generated.resources.config_saved
import com.hearablemusic.player.ui.generated.resources.configured
import com.hearablemusic.player.ui.generated.resources.current_setting_startup
import com.hearablemusic.player.ui.generated.resources.current_setting_time
import com.hearablemusic.player.ui.generated.resources.daily_recommendation_strategy
import com.hearablemusic.player.ui.generated.resources.enter_api_key_placeholder
import com.hearablemusic.player.ui.generated.resources.model_name
import com.hearablemusic.player.ui.generated.resources.music_info_completion
import com.hearablemusic.player.ui.generated.resources.no_pending_music
import com.hearablemusic.player.ui.generated.resources.pause
import com.hearablemusic.player.ui.generated.resources.paused
import com.hearablemusic.player.ui.generated.resources.pending_music_count
import com.hearablemusic.player.ui.generated.resources.please_config_provider
import com.hearablemusic.player.ui.generated.resources.please_enter_api_key
import com.hearablemusic.player.ui.generated.resources.processing_music
import com.hearablemusic.player.ui.generated.resources.refresh_by_startup
import com.hearablemusic.player.ui.generated.resources.refresh_by_time
import com.hearablemusic.player.ui.generated.resources.refresh_interval_hours
import com.hearablemusic.player.ui.generated.resources.refresh_mode_label
import com.hearablemusic.player.ui.generated.resources.refresh_smart
import com.hearablemusic.player.ui.generated.resources.resume
import com.hearablemusic.player.ui.generated.resources.save
import com.hearablemusic.player.ui.generated.resources.select_refresh_strategy
import com.hearablemusic.player.ui.generated.resources.smart_refresh_desc
import com.hearablemusic.player.ui.generated.resources.start_batch_completion
import com.hearablemusic.player.ui.generated.resources.startup_count_label
import com.hearablemusic.player.ui.generated.resources.switched_to
import com.hearablemusic.player.ui.generated.resources.test
import com.hearablemusic.player.ui.generated.resources.title_ai
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.settings.viewmodel.AiSettingsViewModel
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
    val refreshMode by aiSettingsViewModel.dailyRefreshMode.collectAsState()
    val refreshHours by aiSettingsViewModel.dailyRefreshHours.collectAsState()
    val startupCount by aiSettingsViewModel.dailyRefreshStartupCount.collectAsState()

    // 信任档位（从 MasterAgent 读；MasterAgent 实例创建后就有值）
    var trustLevel by remember { mutableStateOf(masterAgent.getMasterTrustLevel()) }
    var alwaysAllowCount by remember { mutableStateOf(masterAgent.getMasterAlwaysAllow().size) }

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
        refreshMode = refreshMode,
        refreshHours = refreshHours,
        startupCount = startupCount,
        trustLevel = trustLevel,
        alwaysAllowCount = alwaysAllowCount,
        onTrustLevelChange = { newLevel ->
            masterAgent.setMasterTrustLevel(newLevel)
            trustLevel = newLevel
        },
        onResetAlwaysAllow = {
            masterAgent.resetMasterAlwaysAllow()
            alwaysAllowCount = 0
        },
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
        onSaveDailyRefreshMode = aiSettingsViewModel::saveDailyRefreshMode,
        onSaveDailyRefreshHours = aiSettingsViewModel::saveDailyRefreshHours,
        onSaveDailyRefreshStartupCount = aiSettingsViewModel::saveDailyRefreshStartupCount,
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
    refreshMode: String,
    refreshHours: Int,
    startupCount: Int,
    trustLevel: Int,
    alwaysAllowCount: Int,
    onTrustLevelChange: (Int) -> Unit,
    onResetAlwaysAllow: () -> Unit,
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
    onSaveDailyRefreshMode: (String) -> Unit,
    onSaveDailyRefreshHours: (Int) -> Unit,
    onSaveDailyRefreshStartupCount: (Int) -> Unit,
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
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        val tabs = listOf(AiAccessMode.FREE, AiAccessMode.CUSTOM, AiAccessMode.PAID)
        val selectedModeId = aiAccessMode.name

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ═══ 分区 1：身体素质 —— AI 接入方式（现有三 tab）═══
            SectionHeader("身体素质")
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
                selectedOption = selectedModeId,
                onOptionSelected = { id ->
                    onModeChange(AiAccessMode.valueOf(id))
                }
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
                    onSaveConfig = onSaveCustomConfig,
                    onFetchModels = onFetchModels,
                    onTestConnection = onTestConnection,
                    dialogManager = dialogManager
                )
                AiAccessMode.PAID -> PaidModeContent()
            }

            // ═══ 全局参数：Token 配额 + 回复语言 ═══
            GlobalConfigSection(masterAgent = masterAgent)

            // ═══ 分区 2：Agent 管理 —— 监控看板 + 配置入口 ═══
            SectionHeader("Agent 管理")
            AgentSummaryCards(
                masterAgent = masterAgent,
                onAgentClick = { role -> navController.add(com.hearablemusic.player.ui.common.navigation.Routes.AI.AgentConfig(role)) }
            )

            // ═══ 分区 3：嗓音与耳朵 —— M7 gate（占位）═══
            SectionHeader("嗓音与耳朵")
            VoiceSection()

            // ═══ 分区 4：记忆管理 —— 清除画像 + 审计页入口 ═══
            SectionHeader("记忆管理")
            MemoryManagementSection(
                navController = navController,
                onClearAllMemory = onClearAllMemory
            )

            Spacer(modifier = Modifier.height(64.dp))
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
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
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

// ==================== 每日推荐刷新策略（复用原有实现）====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRefreshSettings(
    refreshMode: String,
    refreshHours: Int,
    startupCount: Int,
    onSaveRefreshMode: (String) -> Unit,
    onSaveRefreshHours: (Int) -> Unit,
    onSaveStartupCount: (Int) -> Unit,
    dialogManager: DialogManager
) {
    val scope = rememberCoroutineScope()

    TitleWidget(
        title = stringResource(Res.string.daily_recommendation_strategy),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.select_refresh_strategy),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            var expanded by remember { mutableStateOf(false) }
            val refreshModes = listOf(
                "time" to stringResource(Res.string.refresh_by_time),
                "startup" to stringResource(Res.string.refresh_by_startup),
                "smart" to stringResource(Res.string.refresh_smart)
            )
            val currentModeLabel = refreshModes.find { it.first == refreshMode }?.second ?: stringResource(Res.string.refresh_by_time)

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = currentModeLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.refresh_mode_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryEditable,
                            enabled = true
                        ),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Transparent,
                        unfocusedIndicatorColor = Transparent,
                        disabledIndicatorColor = Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    refreshModes.forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSaveRefreshMode(mode)
                                expanded = false
                                scope.launch { dialogManager.showMessage(getString(Res.string.switched_to, label)) }
                            }
                        )
                    }
                }
            }

            when (refreshMode) {
                "time" -> {
                    var hoursText by remember(refreshHours) { mutableStateOf(refreshHours.toString()) }

                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = {
                            hoursText = it
                            it.toIntOrNull()?.let { hours ->
                                if (hours > 0) {
                                    onSaveRefreshHours(hours)
                                }
                            }
                        },
                        label = { Text(stringResource(Res.string.refresh_interval_hours)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = stringResource(Res.string.current_setting_time, refreshHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "startup" -> {
                    var countText by remember(startupCount) { mutableStateOf(startupCount.toString()) }

                    OutlinedTextField(
                        value = countText,
                        onValueChange = {
                            countText = it
                            it.toIntOrNull()?.let { count ->
                                if (count > 0) {
                                    onSaveStartupCount(count)
                                }
                            }
                        },
                        label = { Text(stringResource(Res.string.startup_count_label)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = stringResource(Res.string.current_setting_startup, startupCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "smart" -> {
                    Text(
                        text = stringResource(Res.string.smart_refresh_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==================== 批量补全（复用原有实现）====================

@Composable
fun LoadMusicExtraInfo(
    pendingCount: Int,
    musicWithExtraCount: Int,
    progress: RecommendationViewModel.BatchProcessingProgress,
    isConfigured: Boolean = true,
    autoBatchProcess: Boolean = false,
    onAutoBatchProcessChange: (Boolean) -> Unit = {},
    startAutoProcessExtraInfo: () -> Unit,
    pauseProcess: () -> Unit,
    resumeProcess: () -> Unit,
    cancelProcess: () -> Unit,
    dialogManager: DialogManager
) {
    val scope = rememberCoroutineScope()

    TitleWidget(
        title = stringResource(Res.string.music_info_completion),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 自动后台处理开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.auto_background_completion),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(Res.string.auto_background_completion_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = autoBatchProcess,
                    onCheckedChange = onAutoBatchProcessChange
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (!progress.isProcessing) {
                Text(
                    text = stringResource(Res.string.pending_music_count, pendingCount),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(Res.string.completed_music_count, musicWithExtraCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            if (progress.isProcessing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.processing_music, progress.currentMusicTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress.progressPercent },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${progress.processedCount} / ${progress.totalCount}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )

                    if (progress.isPaused) {
                        Text(
                            text = stringResource(Res.string.paused),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (progress.isPaused) {
                            Button(
                                onClick = resumeProcess,
                                modifier = Modifier.width(100.dp)
                            ) {
                                Text(stringResource(Res.string.resume), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            Button(
                                onClick = pauseProcess,
                                modifier = Modifier.width(100.dp)
                            ) {
                                Text(stringResource(Res.string.pause), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        Button(
                            onClick = cancelProcess,
                            modifier = Modifier.width(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(Res.string.cancel), color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                if (!isConfigured) {
                    Text(
                        text = stringResource(Res.string.please_config_provider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    modifier = Modifier.width(300.dp),
                    onClick = {
                        if (pendingCount <= 0) {
                            scope.launch { dialogManager.showMessage(getString(Res.string.no_pending_music)) }
                            return@Button
                        }
                        startAutoProcessExtraInfo()
                    },
                    enabled = true
                ) {
                    Text(text = stringResource(Res.string.start_batch_completion), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// F9-T2 新增分区组件
// ═══════════════════════════════════════════════════════════════

/** 分区标题 —— 粗体小字 + 上边距，把六分区视觉分开 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * 分区 2：人格 —— 人设预设下拉。
 * 知音（默认，理解者）/ DJ（活跃推荐者）/ 馆长（幕后整理者）。
 * 实际切换暂未接 DataStore（人设持久化是后续 T0b 增量），
 * 这里先做占位 UI，让六分区骨架完整可见。
 */
@Composable
private fun PersonaSection() {
    HMPCard(contentPadding = Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "伙伴的说话方式和回应风格",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val personas = listOf("知音", "DJ", "馆长")
                personas.forEach { name ->
                    Button(
                        onClick = { /* TODO: 切换人设 — 接 DataStore 持久化 */ },
                        colors = ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(name, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(
                text = "设置后伙伴会以不同的口吻与你交流，并调整推荐与问候的偏向",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** 分区 3：嗓音与耳朵 —— M7 gate，暂占位说明 */
@Composable
private fun VoiceSection() {
    HMPCard(contentPadding = Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "实时语音对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "伙伴可以用语音回应你（beta）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "此项功能需要后续语音端点支持，暂未开放",
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
@Composable
private fun TrustLevelSection(
    trustLevel: Int,
    alwaysAllowCount: Int,
    onTrustLevelChange: (Int) -> Unit,
    onResetAlwaysAllow: () -> Unit
) {
    HMPCard(contentPadding = Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "你对伙伴的信任程度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 三档选择
            SegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                options = listOf(
                    SegmentedOption("0", "谨慎"),
                    SegmentedOption("1", "代劳"),
                    SegmentedOption("2", "静默"),
                ),
                selectedOption = trustLevel.toString(),
                onOptionSelected = { id -> onTrustLevelChange(id.toInt()) }
            )

            // 档位说明
            val description = when (trustLevel) {
                0 -> "每次执行可能影响音乐库的操作前，伙伴都会先征求你的确认"
                1 -> "伙伴可以代劳大多数操作，并在完成后通知你"
                2 -> "伙伴可以在后台静默执行操作，不打扰你"
                else -> ""
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // AlwaysAllow 状态 + 重置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "总是允许白名单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (alwaysAllowCount > 0) "$alwaysAllowCount 个工具" else "暂无",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (alwaysAllowCount > 0) {
                    Button(
                        onClick = onResetAlwaysAllow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("全部清除", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
    }
}

/**
 * 分区 6：记忆管理 —— 清除画像 + 审计页入口。
 */
@Composable
private fun MemoryManagementSection(
    navController: NavBackStack<NavKey>,
    onClearAllMemory: () -> Unit
) {
    HMPCard(contentPadding = Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 审计页入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navController.add(com.hearablemusic.player.ui.common.navigation.Routes.Settings.AuditLog) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "伙伴操作日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "查看伙伴做了什么、凭什么做",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "查看 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 清除画像
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "清除伙伴对你的全部认识",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "清除后，伙伴需要重新了解你。此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onClearAllMemory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清除画像并重置个性化", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}

// ==================== Agent 管理看板 ====================

/**
 * 四张 Agent 摘要卡片——每张显示三行：名称 + 关键状态 + 当前配置值。
 * 点按整张卡片跳对应 AgentConfigScreen。
 */
@Composable
private fun AgentSummaryCards(
    masterAgent: MasterAgent,
    onAgentClick: (String) -> Unit,
) {
    val agents = listOf(
        AgentEntry("master", "MasterAgent", "🤖"),
        AgentEntry("hello", "HelloSubAgent", "👋"),
        AgentEntry("enrich", "EnrichSubAgent", "📚"),
        AgentEntry("radio", "RadioSubAgent", "📻"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        agents.forEach { entry ->
            AgentCard(
                entry = entry,
                masterAgent = masterAgent,
                onClick = { onAgentClick(entry.role) }
            )
        }
    }
}

private data class AgentEntry(val role: String, val label: String, val icon: String)

@Composable
private fun AgentCard(
    entry: AgentEntry,
    masterAgent: MasterAgent,
    onClick: () -> Unit,
) {
    val cfg = remember { masterAgent.getAgentPolicyConfig(entry.role) }
    val resolved = remember { cfg.resolvedFor(entry.role) }

    // 实时能力状态（Radio/Enrich/Hello 有，Master 没有 Capability；SubAgent 可能异步 start，不用 remember）
    val liveState = masterAgent.capability(entry.role)?.stateFlow?.collectAsState()?.value
    val statusLabel = liveState?.let { stateStatusLabel(it) }

    HMPCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // 第一行：实时状态 + 配置摘要
                val staticSummary = when (entry.role) {
                    "master" -> "信任: ${trustLabel(cfg.trustLevel)} · 步数: ${resolved.runtimeParams.stepBudget}"
                    "enrich" -> "覆盖率: ${(resolved.runtimeParams.targetCoverage * 100).toInt()}%"
                    "radio" -> "目标: ${resolved.runtimeParams.targetCount} 首${if (resolved.runtimeParams.autoRenew) " · 自动续歌" else ""}"
                    "hello" -> "每日: ${resolved.runtimeParams.dailyRecommendCount} 张 · 歌单: ${resolved.runtimeParams.recommendListSize} 首"
                    else -> ""
                }
                val firstLine = if (statusLabel != null) "$statusLabel · $staticSummary" else staticSummary
                Text(firstLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 第二行：温度 + prompt 语言
                val langLabel = when (cfg.preferredLang) {
                    "zh" -> "🇨🇳 中文"
                    "en" -> "🇺🇸 English"
                    "auto" -> "🗺️ 跟随系统"
                    else -> "🌐 跟随全局"
                }
                Text(
                    "温度: ${resolved.temperature} · prompt: $langLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** CapabilityState → 可读状态标签（带 emoji 颜色提示）。 */
private fun stateStatusLabel(s: CapabilityState): String = when (s.status) {
    CapabilityState.Status.IDLE -> "💤 空闲"
    CapabilityState.Status.BUILDING -> "🔨 启动中"
    CapabilityState.Status.RUNNING -> "✅ 运行中"
    CapabilityState.Status.PAUSED -> "⏸️ 已暂停"
    CapabilityState.Status.COMPLETED -> "🏁 已完成"
    CapabilityState.Status.ERROR -> "❌ 错误"
} + if (s.detail.isNotBlank()) " (${s.detail})" else ""

private fun trustLabel(level: Int) = when (level) {
    0 -> "谨慎"
    1 -> "代劳"
    2 -> "静默"
    else -> "?"
}

// ==================== 全局 Agent 参数 ====================

/**
 * 全局参数：日 Token 配额 + 回复语言。
 * 读/写 GlobalAgentConfig（DataStore key "agent_global"）。
 */
@Composable
private fun GlobalConfigSection(
    masterAgent: MasterAgent,
) {
    val scope = rememberCoroutineScope()
    val globalConfig = remember { masterAgent.getGlobalAgentConfig() }
    var tokenQuota by remember { mutableIntStateOf(globalConfig.dailyTokenQuota) }
    var replyLanguage by remember { mutableStateOf(globalConfig.replyLanguage) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 日 Token 配额
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("日 Token 配额", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
            Slider(
                value = tokenQuota.toFloat(),
                onValueChange = { tokenQuota = it.toInt() },
                onValueChangeFinished = {
                    scope.launch {
                        masterAgent.saveGlobalAgentConfig(
                            globalConfig.copy(dailyTokenQuota = tokenQuota)
                        )
                    }
                },
                valueRange = 100_000f..2_000_000f,
                steps = 18,  // 100K..2M，每步 100K
                modifier = Modifier.weight(1f),
            )
            Text("${tokenQuota / 1000}K", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
        }

        // 回复语言
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("回复语言", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
            val languageOptions = listOf("zh" to "中文", "en" to "英文", "auto" to "自动")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                languageOptions.forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (replyLanguage == key) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            replyLanguage = key
                            scope.launch {
                                masterAgent.saveGlobalAgentConfig(
                                    globalConfig.copy(replyLanguage = key)
                                )
                            }
                        }
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (replyLanguage == key) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Text(
            "「跟随全局」Agent 的 prompt 语言从此处取值；每个 Agent 可单独覆盖。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
