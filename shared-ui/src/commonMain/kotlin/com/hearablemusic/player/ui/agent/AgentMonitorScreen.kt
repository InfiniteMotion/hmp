package com.hearablemusic.player.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Severity
import com.hmp.domain.agent.policy.TrustLevel
import com.hmp.domain.agent.runtime.CapabilityState
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.sub.EnrichSubAgent
import com.hmp.domain.agent.sub.RadioTrack
import com.hmp.log.LogEntry
import com.hmp.log.MemLogWriter
import com.hmp.memLogWriter
import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hmp.data.database.currentTimeMillis
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Agent 看板 v2 — 有运行态、有控制、有进度。
 */
@Composable
fun AgentMonitorScreen(
    navController: NavBackStack<NavKey>,
) {
    val masterAgent: MasterAgent = koinInject()
    val scope = rememberCoroutineScope()

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = "Agent 看板",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopBar(
                onGotoMasterConfig = { navController.add(Routes.AI.AgentConfig("master")) },
                onGotoAudit = { navController.add(Routes.Settings.AuditLog) },
            )

            TokenMonitorCard(masterAgent = masterAgent)

            MasterMonitorCard(
                masterAgent = masterAgent,
                onGotoConfig = { navController.add(Routes.AI.AgentConfig("master")) },
            )

            RadioMonitorCard(
                masterAgent = masterAgent,
                onPause = { scope.launch { masterAgent.pauseRadio() } },
                onResume = { scope.launch { masterAgent.resumeRadio() } },
                onStop = { scope.launch { masterAgent.stopRadio() } },
                onGotoConfig = { navController.add(Routes.AI.AgentConfig("radio")) },
            )

            EnrichMonitorCard(
                masterAgent = masterAgent,
                onGotoConfig = { navController.add(Routes.AI.AgentConfig("enrich")) },
            )

            HelloMonitorCard(
                masterAgent = masterAgent,
                onGotoConfig = { navController.add(Routes.AI.AgentConfig("hello")) },
            )

            LogPanel()

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── 全局 Token 用量（明细账本分账，F12-T2）──
private enum class TokenWindow(val label: String) {
    TODAY("今天"),
    LAST_7("近 7 天"),
    LAST_30("近 30 天"),
    ALL("全部");

    /** 该窗口的起始时间（epoch ms）。今天 = UTC 当日零点。 */
    fun sinceMs(): Long {
        val now = currentTimeMillis()
        return when (this) {
            TODAY -> now - (now % 86_400_000L)
            LAST_7 -> now - 7 * 86_400_000L
            LAST_30 -> now - 30 * 86_400_000L
            ALL -> 0L
        }
    }
}

@Composable
private fun TokenMonitorCard(
    masterAgent: MasterAgent,
) {
    val snap by masterAgent.tokenCounter.snapshot.collectAsState()
    val ledgerDao: TokenLedgerDao = koinInject()

    var window by remember { mutableStateOf(TokenWindow.TODAY) }
    var agentRows by remember { mutableStateOf<List<TokenAggregateRow>>(emptyList()) }
    var endpointRows by remember { mutableStateOf<List<TokenAggregateRow>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            runCatching {
                val since = window.sinceMs()
                agentRows = ledgerDao.sumByAgent(since)
                endpointRows = ledgerDao.sumByEndpoint(since)
            }
        }
    }

    // 切换窗口即重查；首次进入也会触发
    LaunchedEffect(window) { reload() }

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题 + 刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🪙 Token 用量分账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${snap.used} / ${snap.quota}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { reload() }, modifier = Modifier.size(28.dp)) {
                        Text("↻", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { snap.rate }, modifier = Modifier.fillMaxWidth().height(6.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "全局累计 ${snap.used} · 剩余 ${snap.remaining} · ${(snap.rate * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // 时间窗口选择
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                enumValues<TokenWindow>().forEach { w ->
                    FilterChip(
                        selected = window == w,
                        onClick = { window = w },
                        label = { Text(w.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 按 Agent 分账（F12-T2 最终目标：各 Agent × 各端点 × 分时）
            Text("按 Agent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (agentRows.isEmpty()) {
                Text("该时段暂无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                agentRows.forEach { row ->
                    TokenBreakdownRow(row.label, row)
                    Spacer(Modifier.height(2.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // 按端点·模型 分账（换端点后会出现两行）
            Text("按端点 · 模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (endpointRows.isEmpty()) {
                Text("该时段暂无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                endpointRows.forEach { row ->
                    TokenBreakdownRow(row.label, row)
                    Spacer(Modifier.height(2.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "📐 真值来自端点 usage；未返回时按长度估算（标注「估算」）。账本仅存主机名，不含密钥。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TokenBreakdownRow(label: String, row: TokenAggregateRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${row.calls} 次 · ${if (row.estimatedRows == 0L) "全部实测" else "估算 ${row.estimatedRows} 次"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            fmtTokens(row.totalTokens),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun fmtTokens(n: Long): String {
    if (n >= 1_000_000L) return "${(n / 10_000L) / 100.0}M"
    if (n >= 1000L) return "${(n / 100L) / 10.0}K"
    return "$n"
}

// ── Agent 运行日志面板 ──
@Composable
private fun LogPanel() {
    val writer: MemLogWriter = memLogWriter
    val allLogs by writer.logs.collectAsState()
    var onlyAbnormal by remember { mutableStateOf(false) }

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📝 运行日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = onlyAbnormal,
                    onClick = { onlyAbnormal = !onlyAbnormal },
                    label = { Text("只看异常") }
                )
            }
            Spacer(Modifier.height(8.dp))

            val visible = if (onlyAbnormal) {
                // 只看 WARN/ERROR/ASSERT
                allLogs.filter { it.severity.ordinal >= Severity.Warn.ordinal }
            } else {
                allLogs
            }

            if (visible.isEmpty()) {
                Text(
                    if (onlyAbnormal) "无异常日志 🎉" else "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // 固定高度滚动列表（最外层已 verticalScroll，这里用固定 height）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visible.takeLast(50), key = { it.sequence }) { entry -> LogLine(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val color = when (entry.severity) {
        Severity.Verbose, Severity.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
        Severity.Info -> MaterialTheme.colorScheme.onSurface
        Severity.Warn -> Color(0xFFB26A00)
        Severity.Error, Severity.Assert -> MaterialTheme.colorScheme.error
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "[${entry.tag}] ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── 顶部控制条 ──
@Composable
private fun TopBar(
    onGotoMasterConfig: () -> Unit,
    onGotoAudit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onGotoMasterConfig,
            modifier = Modifier.weight(1f)
        ) { Text("🤖 配置中心") }
        OutlinedButton(
            onClick = onGotoAudit,
            modifier = Modifier.weight(1f)
        ) { Text("📝 审计日志") }
    }
}

// ── MasterAgent（信任档位快调） ──
@Composable
private fun MasterMonitorCard(
    masterAgent: MasterAgent,
    onGotoConfig: () -> Unit,
) {
    val cfg = remember { masterAgent.getAgentPolicyConfig("master") }
    val resolved = remember { cfg.resolvedFor("master") }
    val persona = resolved.persona
    var trustLevel by remember { mutableStateOf(cfg.trustLevel) }
    val scope = rememberCoroutineScope()

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🤖 MasterAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onGotoConfig, modifier = Modifier.size(32.dp)) {
                    Text("⚙️", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("人格: ${persona?.name ?: "默认"}（${persona?.personaName ?: "-"}）")
            Text("步数预算: ${resolved.runtimeParams.stepBudget} · 温度: ${resolved.temperature}")
            Spacer(Modifier.height(12.dp))
            Text("信任档位（快捷调节）", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    TrustLevel.SUGGEST to "谨慎",
                    TrustLevel.ACT to "代劳",
                    TrustLevel.SILENT to "静默",
                ).forEach { (level, label) ->
                    FilterChip(
                        selected = trustLevel == level,
                        onClick = {
                            trustLevel = level
                            scope.launch { masterAgent.setMasterTrustLevel(level) }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

// ── Radio（正在播 + 队列 + 控制） ──
@Composable
private fun RadioMonitorCard(
    masterAgent: MasterAgent,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onGotoConfig: () -> Unit,
) {
    val cap = remember { masterAgent.capability("radio") }
    val stateFlow = cap?.stateFlow
    // nullable StateFlow → 安全 collect
    val state by stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }
    val playlist by masterAgent.radioPlaylist.collectAsState()
    val cfg = remember { masterAgent.getAgentPolicyConfig("radio") }
    val resolved = remember { cfg.resolvedFor("radio") }
    val targetCount = resolved.runtimeParams.targetCount
    val autoRenew = resolved.runtimeParams.autoRenew

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📻 RadioSubAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(state?.status)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stateLabel(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state?.status == CapabilityState.Status.ERROR)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onGotoConfig, modifier = Modifier.size(32.dp)) {
                        Text("⚙️", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // 正在播放
            if (playlist.isNotEmpty()) {
                val nowPlaying = playlist.first()
                Spacer(Modifier.height(8.dp))
                Text("▶ 正在播放", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${nowPlaying.title} — ${nowPlaying.artist.ifBlank { "未知" }}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (nowPlaying.why.isNotBlank()) {
                    Text(
                        nowPlaying.why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("（未启动 — 打开首页收音机卡）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 队列预览（最多 5 首）
            if (playlist.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text("📋 接下来 ${playlist.size - 1} 首", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(playlist.drop(1).take(5)) { track -> QueueChip(track) }
                    if (playlist.size > 6) {
                        item {
                            Text(
                                "+${playlist.size - 6}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 进度条 + 控制
            Spacer(Modifier.height(8.dp))
            val progress = if (targetCount > 0) (playlist.size.toFloat() / targetCount).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${playlist.size} / $targetCount 首${if (autoRenew) " · 自动续歌" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val isRunning = state?.status == CapabilityState.Status.RUNNING
                    val isPaused = state?.status == CapabilityState.Status.PAUSED
                    IconButton(onClick = onPause, enabled = isRunning, modifier = Modifier.size(36.dp)) {
                        Text("⏸", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onResume, enabled = isPaused, modifier = Modifier.size(36.dp)) {
                        Text("▶", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Text("⏹", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueChip(track: RadioTrack) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                track.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(140.dp)
            )
            Text(
                track.artist.ifBlank { "未知" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

// ── Enrich（进度条 + 已富化/总数） ──
@Composable
private fun EnrichMonitorCard(
    masterAgent: MasterAgent,
    onGotoConfig: () -> Unit,
) {
    val cap = remember { masterAgent.capability("enrich") }
    val stateFlow = cap?.stateFlow
    val state by stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }

    val progressFlow = remember { masterAgent.enrichProgressState() }
    val progress by progressFlow?.collectAsState()
        ?: remember { mutableStateOf<EnrichSubAgent.EnrichProgress?>(null) }

    val cfg = remember { masterAgent.getAgentPolicyConfig("enrich") }
    val resolved = remember { cfg.resolvedFor("enrich") }
    val targetCoverage = resolved.runtimeParams.targetCoverage

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📚 EnrichSubAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(state?.status)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stateLabel(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state?.status == CapabilityState.Status.ERROR)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onGotoConfig, modifier = Modifier.size(32.dp)) {
                        Text("⚙️", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (progress != null && progress!!.chunkTotal > 0) {
                Spacer(Modifier.height(8.dp))
                val pct = progress!!.chunkIndex.toFloat() / progress!!.chunkTotal
                LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${progress!!.chunkIndex}/${progress!!.chunkTotal} 块 · 富化 ${progress!!.success} 首 / 失败 ${progress!!.failed}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$((pct * 100).toInt())%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (progress!!.currentArtist != null && progress!!.phase.isNotBlank()) {
                    Text(
                        "当前: ${progress!!.phase} · ${progress!!.currentArtist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    state?.detail ?: "未运行（需要主动触发富化）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "目标覆盖率: ${(targetCoverage * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Hello（卡池 + 今日推荐） ──
@Composable
private fun HelloMonitorCard(
    masterAgent: MasterAgent,
    onGotoConfig: () -> Unit,
) {
    val cap = remember { masterAgent.capability("hello") }
    val stateFlow = cap?.stateFlow
    val state by stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }
    val cfg = remember { masterAgent.getAgentPolicyConfig("hello") }
    val resolved = remember { cfg.resolvedFor("hello") }
    val dailyCount = resolved.runtimeParams.dailyRecommendCount
    val listSize = resolved.runtimeParams.recommendListSize

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👋 HelloSubAgent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(state?.status)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stateLabel(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state?.status == CapabilityState.Status.ERROR)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onGotoConfig, modifier = Modifier.size(32.dp)) {
                        Text("⚙️", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("每日推荐: $dailyCount 张 · 歌单长度: $listSize 首")
            if (state?.detail?.isNotBlank() == true) {
                Text(
                    state!!.detail!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── 通用：状态色点 + helper ──
@Composable
private fun StatusDot(status: CapabilityState.Status?) {
    val color = when (status) {
        CapabilityState.Status.IDLE -> Color.Gray
        CapabilityState.Status.BUILDING -> Color(0xFFFF9800)
        CapabilityState.Status.RUNNING -> Color(0xFF4CAF50)
        CapabilityState.Status.PAUSED -> Color(0xFF2196F3)
        CapabilityState.Status.COMPLETED -> Color(0xFF9C27B0)
        CapabilityState.Status.ERROR -> Color(0xFFF44336)
        null -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun stateLabel(state: CapabilityState?): String = state?.let { s ->
    when (s.status) {
        CapabilityState.Status.IDLE -> "💤 空闲"
        CapabilityState.Status.BUILDING -> "🔨 启动中"
        CapabilityState.Status.RUNNING -> "▶ 运行中"
        CapabilityState.Status.PAUSED -> "⏸ 已暂停"
        CapabilityState.Status.COMPLETED -> "🏁 完成"
        CapabilityState.Status.ERROR -> "❌ 错误"
    }
} ?: "—"
