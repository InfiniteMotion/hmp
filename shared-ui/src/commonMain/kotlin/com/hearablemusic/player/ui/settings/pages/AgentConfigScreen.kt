package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hmp.domain.agent.persona.CompanionProfile
import com.hmp.domain.agent.persona.DefaultCompanionProfiles
import com.hmp.domain.agent.policy.AgentPolicyConfig
import com.hmp.domain.agent.runtime.L10N_PROMPTS
import com.hmp.domain.agent.runtime.Lang
import com.hmp.domain.agent.runtime.EngineDefaults
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlinx.coroutines.runBlocking

/**
 * 通用 Agent 配置页——按 agentRole 参数决定渲染哪个 Agent 的配置表单。
 *
 * agentRole 取值："master" / "hello" / "enrich" / "radio"
 * 每个 role 渲染不同参数组 + 可选 prompt 编辑 + 可选状态只读卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigScreen(
    agentRole: String,
    navController: NavBackStack<NavKey>,
    masterAgent: MasterAgent = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val settingsRepo: SettingsRepository = koinInject()
    val policyConfig = remember { masterAgent.getAgentPolicyConfig(agentRole) }
    val resolved = remember { policyConfig.resolvedFor(agentRole) }

    // ── AI 端点配置（per-Agent 独立 endpoint / null = 跟随全局）──
    val initialEndpoint = remember { runBlocking { settingsRepo.getAgentEndpointConfig(agentRole) } }
    var useCustomEndpoint by remember { mutableStateOf(initialEndpoint != null) }
    var customEndpoint by remember { mutableStateOf(initialEndpoint?.endpoint ?: "") }
    var customApiKey by remember { mutableStateOf(initialEndpoint?.apiKey ?: "") }
    var customModel by remember { mutableStateOf(initialEndpoint?.selectedModel ?: "") }
    // 全局默认 endpoint 显示只读预览
    val globalEndpoint = remember { runBlocking { settingsRepo.getActiveAiConfig() } }

    // ── 本地状态（从 policyConfig 读初始值）──
    var temperature by remember { mutableFloatStateOf(resolved.temperature) }
    var stepBudget by remember { mutableIntStateOf(resolved.runtimeParams.stepBudget) }
    var dailyRecommendCount by remember { mutableIntStateOf(resolved.runtimeParams.dailyRecommendCount) }
    var recommendListSize by remember { mutableIntStateOf(resolved.runtimeParams.recommendListSize) }
    var targetCoverage by remember { mutableFloatStateOf(resolved.runtimeParams.targetCoverage) }
    var targetCount by remember { mutableIntStateOf(resolved.runtimeParams.targetCount) }
    var autoRenew by remember { mutableStateOf(resolved.runtimeParams.autoRenew) }
    var systemPrompt by remember { mutableStateOf(resolved.promptOverrides[defaultPromptKeyFor(agentRole)] ?: "") }
    var preferredLang by remember { mutableStateOf(resolved.preferredLang) }

    // Master 专属：人格预设 + 三滑杆（当前 persona —— 来自 resolved.persona）
    val currentPersona = resolved.persona ?: DefaultCompanionProfiles.DEFAULT
    var personaId by remember { mutableStateOf(currentPersona.id) }
    var talkativeness by remember { mutableFloatStateOf(currentPersona.talkativeness) }
    var proactiveness by remember { mutableFloatStateOf(currentPersona.proactiveness) }
    var topicBreadth by remember { mutableFloatStateOf(currentPersona.topicBreadth) }

    val save = {
        val cfg = masterAgent.getAgentPolicyConfig(agentRole)
        cfg.temperature = temperature
        cfg.preferredLang = preferredLang
        when (agentRole) {
            "master" -> {
                cfg.runtimeParams["stepBudget"] = stepBudget.toString()
                // 人格覆盖：选预设 + 三滑杆合成新 persona
                val preset = DefaultCompanionProfiles.all().firstOrNull { it.id == personaId }
                    ?: DefaultCompanionProfiles.DEFAULT
                cfg.personaOverride = preset.copy(
                    talkativeness = talkativeness,
                    proactiveness = proactiveness,
                    topicBreadth = topicBreadth,
                )
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
        // Prompt：空 = 移除覆盖（引擎回落出厂默认），非空 = 完全替换
        val promptKey = defaultPromptKeyFor(agentRole)
        val template = L10N_PROMPTS[promptKey]?.get(Lang.ZH)
            ?: L10N_PROMPTS[promptKey]?.get(Lang.EN)
        val missing = template?.extractPlaceholders()?.filter { !systemPrompt.contains("{{$it}}") } ?: emptyList()
        val effectivePrompt = if (systemPrompt.isNotBlank() && missing.isEmpty()) systemPrompt else ""
        if (effectivePrompt.isNotBlank()) cfg.promptOverrides[promptKey] = effectivePrompt
        else cfg.promptOverrides.remove(promptKey)  // 空或占位符缺失 → 回落出厂默认
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
        }; Unit
    }

    val reset = {
        val cfg = AgentPolicyConfig()
        cfg.temperature = null  // 回落默认
        cfg.runtimeParams.clear()
        cfg.promptOverrides.clear()
        cfg.personaOverride = null
        cfg.preferredLang = "global"
        // 保持 trustLevel / alwaysAllow 不变（那是权限组，不属于"行为参数"）
        val original = masterAgent.getAgentPolicyConfig(agentRole)
        cfg.trustLevel = original.trustLevel
        cfg.alwaysAllow.addAll(original.alwaysAllow)
        scope.launch {
            masterAgent.saveAgentPolicyConfig(agentRole, cfg)
            settingsRepo.saveAgentEndpointConfig(agentRole, null)  // 清除 per-Agent endpoint → 跟随全局
            masterAgent.updateAiConfig()
        }; Unit
        // 本地状态也重置
        temperature = EngineDefaults.defaultTemperatureFor(agentRole)
        stepBudget = EngineDefaults.STEP_BUDGET
        dailyRecommendCount = 1
        recommendListSize = 8
        targetCoverage = EngineDefaults.ENRICH_TARGET_COVERAGE
        targetCount = EngineDefaults.RADIO_TARGET_COUNT
        autoRenew = EngineDefaults.RADIO_AUTO_RENEW
        systemPrompt = ""
        preferredLang = "global"
        useCustomEndpoint = false
        customEndpoint = ""
        customApiKey = ""
        customModel = ""
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("${agentLabelFor(agentRole)} · 配置") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Language Selector（所有 Agent 都有）
            HMPCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Prompt 语言", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SegmentedControl(
                        modifier = Modifier.fillMaxWidth(),
                        options = listOf(
                            SegmentedOption("global", "跟随全局"),
                            SegmentedOption("zh", "中文"),
                            SegmentedOption("en", "English"),
                            SegmentedOption("auto", "Auto"),
                        ),
                        selectedOption = preferredLang,
                        onOptionSelected = { preferredLang = it },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "决定此 Agent 的 system prompt 语言。「跟随全局」= 使用全局设置里的回复语言。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // AI 端点配置（所有 Agent 都有 —— per-Agent 独立 endpoint）
            HMPCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI 端点配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SegmentedControl(
                        modifier = Modifier.fillMaxWidth(),
                        options = listOf(
                            SegmentedOption("global", "跟随全局默认"),
                            SegmentedOption("custom", "独立端点"),
                        ),
                        selectedOption = if (useCustomEndpoint) "custom" else "global",
                        onOptionSelected = { useCustomEndpoint = it == "custom" },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (useCustomEndpoint) {
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            label = { Text("Endpoint URL") },
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
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("deepseek-chat / gpt-4o-mini / ...") },
                        )
                    } else {
                        // 全局默认只读预览
                        Text(
                            "当前跟随全局 AI 设置",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("Endpoint: ${if (globalEndpoint.endpoint.isNotBlank()) globalEndpoint.endpoint else "(内置)"}")
                                if (globalEndpoint.selectedModel.isNotBlank()) append("  ·  Model: ${globalEndpoint.selectedModel}")
                                append("  ·  Key: ")
                                append(if (globalEndpoint.apiKey.isNotBlank()) "已配置 (${globalEndpoint.apiKey.take(6)}...)" else "未配置")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "改全局默认 → 前往 AI 设置首页。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Temperature（所有 Agent 都有）
            HMPCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Temperature 采样温度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            }

            // Master 专属：stepBudget
            if (agentRole == "master") {
                HMPCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("步数预算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            Text("$stepBudget 步", modifier = Modifier.width(48.dp))
                        }
                    }
                }
            }

            // Master 专属：人格预设 + 三滑杆
            if (agentRole == "master") {
                HMPCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("伙伴人格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        SegmentedControl(
                            modifier = Modifier.fillMaxWidth(),
                            options = DefaultCompanionProfiles.all().map { p ->
                                SegmentedOption(p.id, "${p.name} · ${p.personaName}")
                            },
                            selectedOption = personaId,
                            onOptionSelected = { newId ->
                                personaId = newId
                                // 切换预设时，把三滑杆重置为该预设的默认值
                                val preset = DefaultCompanionProfiles.all().firstOrNull { it.id == newId }
                                    ?: DefaultCompanionProfiles.DEFAULT
                                talkativeness = preset.talkativeness
                                proactiveness = preset.proactiveness
                                topicBreadth = preset.topicBreadth
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "当前: ${currentPersona.name} — ${currentPersona.greeting}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        // 三滑杆
                        Text("健谈度 (${(talkativeness * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(value = talkativeness, onValueChange = { talkativeness = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("主动度 (${(proactiveness * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(value = proactiveness, onValueChange = { proactiveness = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("话题宽度 (${(topicBreadth * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(value = topicBreadth, onValueChange = { topicBreadth = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }

            // Hello 专属
            if (agentRole == "hello") {
                HMPCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("推荐卡参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("每日推荐卡数量")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = dailyRecommendCount.toFloat(),
                                onValueChange = { dailyRecommendCount = it.toInt() },
                                valueRange = 1f..5f,
                                steps = 3,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$dailyRecommendCount 张")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("推荐歌单长度")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = recommendListSize.toFloat(),
                                onValueChange = { recommendListSize = it.toInt() },
                                valueRange = 5f..20f,
                                steps = 14,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$recommendListSize 首")
                        }
                    }
                }
            }

            // Enrich 专属
            if (agentRole == "enrich") {
                HMPCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("目标覆盖率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            }

            // Radio 专属
            if (agentRole == "radio") {
                HMPCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("电台参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("目标曲目数")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = targetCount.toFloat(),
                                onValueChange = { targetCount = it.toInt() },
                                valueRange = 8f..30f,
                                steps = 21,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$targetCount 首")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("自动续歌")
                            Switch(checked = autoRenew, onCheckedChange = { autoRenew = it })
                        }
                    }
                }
            }

            // Prompt 编辑（替换模式）
            HMPCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("完全替换模式：你写的内容会作为最终 system prompt 发送给 LLM。留空 = 使用出厂默认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    if (systemPrompt.isBlank()) {
                        // 当前生效语言提示（global 时取全局 replyLanguage）
                        val globalReplyLang = masterAgent.getGlobalAgentConfig().replyLanguage
                        val effectiveLang = when (preferredLang) {
                            "zh" -> "🇨🇳 中文"
                            "en" -> "🇺🇸 English"
                            "auto" -> "🗺️ 跟随系统"
                            else -> when (globalReplyLang) {  // "global"
                                "en" -> "🌐 跟随全局（English）"
                                "auto" -> "🌐 跟随全局（Auto）"
                                else -> "🌐 跟随全局（中文）"
                            }
                        }
                        Text(
                            "当前为空 · 生效语言: $effectiveLang · 引擎将使用对应语言的出厂默认 prompt。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        placeholder = { Text("自定义 system prompt（替换出厂默认）") },
                    )

                    // 占位符护栏（编辑时实时显示）
                    if (systemPrompt.isNotBlank()) {
                        val promptKey = defaultPromptKeyFor(agentRole)
                        val template = L10N_PROMPTS[promptKey]?.get(Lang.ZH)
                            ?: L10N_PROMPTS[promptKey]?.get(Lang.EN)
                        val requiredPlaceholders = template?.extractPlaceholders() ?: emptySet()
                        if (requiredPlaceholders.isNotEmpty()) {
                            val userHas = requiredPlaceholders.filter { systemPrompt.contains("{{$it}}") }
                            val missing = requiredPlaceholders.filter { !systemPrompt.contains("{{$it}}") }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "需要的占位符 (${userHas.size}/${requiredPlaceholders.size})：",
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
                                    "⚠️ 缺失 ${{ missing.joinToString(", ") { "{{$it}}" } }} — 引擎运行时注入可能失败，保存后将自动回落出厂默认。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // 加载出厂默认按钮
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.compose.material3.TextButton(onClick = {
                        val key = defaultPromptKeyFor(agentRole)
                        val lang = when (preferredLang) {
                            "en" -> Lang.EN
                            else -> Lang.ZH  // global / auto / zh → 默认中文（用户可切 Lang Selector 再点一次）
                        }
                        systemPrompt = L10N_PROMPTS[key]?.get(lang)
                            ?: L10N_PROMPTS[key]?.get(Lang.ZH)
                            ?: ""
                    }) {
                        val key = defaultPromptKeyFor(agentRole)
                        val templateLang = when (preferredLang) {
                            "en" -> "English"
                            else -> "中文"
                        }
                        Text("加载出厂默认模板 ($templateLang)")
                    }
                    }
                }
            }

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = reset, modifier = Modifier.weight(1f)) { Text("恢复默认") }
                Button(onClick = save, modifier = Modifier.weight(1f)) { Text("保存") }
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

private fun defaultPromptKeyFor(role: String) = when (role) {
    "master" -> "chat.system"
    "enrich" -> "enrich.system"
    "radio" -> "radio.dj_prompt"
    "hello" -> "hello.greeting.quote"
    else -> "${role}.system"
}

/** 从 prompt 模板中提取所有 {{placeholder}} 占位符（不含括号）。 */
private fun String.extractPlaceholders(): Set<String> {
    val regex = Regex("""\{\{\s*([a-zA-Z_][a-zA-Z0-9_\.]*)\s*\}\}""")
    return regex.findAll(this).map { it.groupValues[1] }.toSet()
}
