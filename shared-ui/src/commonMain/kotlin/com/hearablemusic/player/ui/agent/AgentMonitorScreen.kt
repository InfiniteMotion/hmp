package com.hearablemusic.player.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.enrich
import com.hearablemusic.player.ui.generated.resources.hello
import com.hearablemusic.player.ui.generated.resources.master
import com.hearablemusic.player.ui.generated.resources.radiowaves
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hmp.domain.agent.runtime.CapabilityState
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.log.LogEntry
import com.hmp.log.MemLogWriter
import com.hmp.memLogWriter
import com.hmp.data.database.TokenAggregateRow
import com.hmp.data.database.TokenLedgerDao
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import co.touchlab.kermit.Severity
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Agent 看板 — 顶部 Token 总用量；下方每行两个 Agent 卡片、同行等高。
 *
 * 卡片宽度约束：半屏宽（内容区约 128dp）只够放下一个 Agent 名字，
 * 因此卡片内只保留「名字 / 状态 / Token / 日志条数」四行竖排，不放任何并排元素。
 */
@Composable
fun AgentMonitorScreen(
    navController: NavBackStack<NavKey>,
) {
    val masterAgent: MasterAgent = koinInject()
    val ledgerDao: TokenLedgerDao = koinInject()
    val scope = rememberCoroutineScope()
    // 日志弹窗的毛玻璃要采样本页内容 → 本页建 HazeState 并把内容标为 hazeSource。
    val hazeState = rememberHazeState()

    // 各 Agent 的 Token 分账（账本按 agent_id 聚合；label 即 agent_id）
    var agentRows by remember { mutableStateOf<List<TokenAggregateRow>>(emptyList()) }

    fun reloadAgentTokens() {
        scope.launch { runCatching { agentRows = ledgerDao.sumByAgent(0L) } }
    }
    LaunchedEffect(Unit) { reloadAgentTokens() }

    fun tokenTextFor(agentId: String): String? {
        val row = agentRows.firstOrNull { it.label.equals(agentId, ignoreCase = true) } ?: return null
        return "Token ${fmtTokens(row.totalTokens)} · ${row.calls} 次"
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = "Agent 看板",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TokenMonitorCard(
                masterAgent = masterAgent,
                onReloaded = { reloadAgentTokens() },
            )

            // 2×2 网格：每行两个，同行两张卡片高度一致。
            // Row 用 IntrinsicSize.Max 取同行最高内容作为行高，两卡 fillMaxHeight 撑满 → 等高。
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasterMonitorCard(
                        masterAgent = masterAgent,
                        tokenText = tokenTextFor("master"),
                        hazeState = hazeState,
                        onClick = { navController.add(Routes.AI.AgentConfig("master")) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    RadioMonitorCard(
                        masterAgent = masterAgent,
                        tokenText = tokenTextFor("radio"),
                        hazeState = hazeState,
                        onClick = { navController.add(Routes.AI.AgentConfig("radio")) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EnrichMonitorCard(
                        masterAgent = masterAgent,
                        tokenText = tokenTextFor("enrich"),
                        hazeState = hazeState,
                        onClick = { navController.add(Routes.AI.AgentConfig("enrich")) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    HelloMonitorCard(
                        masterAgent = masterAgent,
                        tokenText = tokenTextFor("hello"),
                        hazeState = hazeState,
                        onClick = { navController.add(Routes.AI.AgentConfig("hello")) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ── 全局 Token 用量（明细账本分账，F12-T2）──
@Composable
private fun TokenMonitorCard(
    masterAgent: MasterAgent,
    onReloaded: () -> Unit,
) {
    val snap by masterAgent.tokenCounter.snapshot.collectAsState()
    val ledgerDao: TokenLedgerDao = koinInject()
    var total by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            runCatching {
                total = ledgerDao.sumTotal()
                onReloaded()
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    HMPCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题 + 刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Token 用量")
                IconButton(onClick = { reload() }, modifier = Modifier.size(28.dp)) {
                    Text(
                        "↻",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // 累计总用量（账本全量）——数字是这张卡的视觉焦点
            Text(
                fmtTokens(total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "累计总用量 · 账本全量",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            // 实时配额进度（按日滚动计数）
            SlimProgress(snap.rate)
            Spacer(Modifier.height(6.dp))
            Text(
                "今日 ${snap.used} · 剩余 ${snap.remaining} · ${(snap.rate * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun fmtTokens(n: Long): String {
    if (n >= 1_000_000L) return "${(n / 10_000L) / 100.0}M"
    if (n >= 1000L) return "${(n / 100L) / 10.0}K"
    return "$n"
}

// ── 各 Agent 卡片内嵌运行日志（按 Agent 分桶，F11/F12 收尾）──
// buckets：该卡片归属的 Agent 桶（见 MemLogWriter.TAG_TO_AGENT）。
// 只显示四个 Agent 自身（Master/Radio/Enrich/Hello）；「框架」等非 Agent 日志一律不过滤进看板。
// 卡片内只显示条数；点击条数弹出 ScrimDialog 查看具体条目。
@Composable
private fun CardLogSection(
    buckets: List<String>,
    hazeState: HazeState,
) {
    val writer: MemLogWriter = memLogWriter
    val allLogs by writer.logs.collectAsState()
    val entries = allLogs.filter { it.agent in buckets }
    var showDetail by remember { mutableStateOf(false) }

    Spacer(Modifier.height(10.dp))
    // 紧凑一行：左「📝 运行日志」/ 右「N 条 ›」，整体可点（› 暗示可展开）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { showDetail = true }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                "运行日志",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${entries.size} 条 ›",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDetail) {
        LogDetailDialog(
            title = "运行日志 · ${buckets.firstOrNull() ?: ""}",
            entries = entries,
            hazeState = hazeState,
            onDismiss = { showDetail = false },
        )
    }
}

@Composable
private fun LogDetailDialog(
    title: String,
    entries: List<LogEntry>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    ScrimDialog(onDismissRequest = onDismiss) {
        // 式样对齐 MusicDetailDialog：hazeEffect + surface 半透明底色 + 圆角 28dp + 无描边 + elevation 0。
        // HMPCard 是 Box+background（无 elevation 概念），天然满足 elevation 0；
        // hazeEffect 必须放在容器 modifier 上、半透明底色之下才能看到模糊。
        HMPCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .hazeEffect(state = hazeState, style = hazeStyleForIntensity()),
            shape = RoundedCornerShape(28.dp),
            borderColor = Color.Transparent,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha()),
        ) {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.4.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "进程内易失 · 共 ${entries.size} 条 · 最新在上",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                // 极浅分隔线：弹窗内只留这一条，起锚定作用
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Text(
                        "暂无日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 最新在上
                        items(entries.reversed()) { LogLine(it) }
                    }
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

// ── MasterAgent（编排中枢：自身无 capability，状态行改为「活跃子 Agent 数」） ──
@Composable
private fun MasterMonitorCard(
    masterAgent: MasterAgent,
    tokenText: String?,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radioCap = remember { masterAgent.capability("radio") }
    val enrichCap = remember { masterAgent.capability("enrich") }
    val helloCap = remember { masterAgent.capability("hello") }
    val radioState by radioCap?.stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }
    val enrichState by enrichCap?.stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }
    val helloState by helloCap?.stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }

    val active = listOf(radioState, enrichState, helloState).count { s ->
        s?.status == CapabilityState.Status.RUNNING || s?.status == CapabilityState.Status.BUILDING
    }

    AgentStatusCard(
        icon = agentIcon("master"),
        title = "MasterAgent",
        status = if (active > 0) CapabilityState.Status.RUNNING else CapabilityState.Status.IDLE,
        statusText = "活跃 $active / 3",
        // 编排中枢没有自己的 capability，无 detail → 占位
        detailText = null,
        tokenText = tokenText,
        hazeState = hazeState,
        logBuckets = listOf("Master"),
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Radio ──
@Composable
private fun RadioMonitorCard(
    masterAgent: MasterAgent,
    tokenText: String?,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cap = remember { masterAgent.capability("radio") }
    val stateFlow = cap?.stateFlow
    // nullable StateFlow → 安全 collect
    val state by stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }

    AgentStatusCard(
        icon = agentIcon("radio"),
        title = "RadioSubAgent",
        status = state?.status,
        statusText = agentStatusLabel(state),
        detailText = agentDetailOf(state),
        tokenText = tokenText,
        hazeState = hazeState,
        logBuckets = listOf("Radio"),
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Enrich ──
@Composable
private fun EnrichMonitorCard(
    masterAgent: MasterAgent,
    tokenText: String?,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cap = remember { masterAgent.capability("enrich") }
    val stateFlow = cap?.stateFlow
    val state by stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }

    AgentStatusCard(
        icon = agentIcon("enrich"),
        title = "EnrichSubAgent",
        status = state?.status,
        statusText = agentStatusLabel(state),
        detailText = agentDetailOf(state),
        tokenText = tokenText,
        hazeState = hazeState,
        logBuckets = listOf("Enrich"),
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Hello ──
@Composable
private fun HelloMonitorCard(
    masterAgent: MasterAgent,
    tokenText: String?,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cap = remember { masterAgent.capability("hello") }
    val stateFlow = cap?.stateFlow
    val state by stateFlow?.collectAsState()
        ?: remember { mutableStateOf<CapabilityState?>(null) }

    AgentStatusCard(
        icon = agentIcon("hello"),
        title = "HelloSubAgent",
        status = state?.status,
        statusText = agentStatusLabel(state),
        detailText = agentDetailOf(state),
        tokenText = tokenText,
        hazeState = hazeState,
        logBuckets = listOf("Hello"),
        onClick = onClick,
        modifier = modifier,
    )
}

/** 小标题：小字号 + 宽字距 + 次级灰，靠留白分区（不加竖色条 / 下划线）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 卡片标题：字距 + SemiBold（避开 Bold 的厚重感；纯英文标题可安全加字距），水平居中。 */
@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.4.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

/**
 * 卡片字段行：无数据时显示**空提示语**「暂无」而不是留白，保证各卡行数与行高严格相等 → 等高。
 * 不要改成"为空就不渲染"——那会让卡片高度随数据有无而抖动；
 * 也不要渲染空格——那看起来像"没加载出来"，用提示语才能区分「无数据」和「加载失败/渲染缺失」。
 */
@Composable
private fun CardRow(text: String?) {
    Text(
        text = text?.takeIf { it.isNotBlank() } ?: "暂无",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

/**
 * 图标行：48dp、水平居中；资源缺失时用等高空白占位。
 * 只让这一行居中（用 Box(fillMaxWidth) 包），不给外层 Column 设 CenterHorizontally——
 * 否则名字/状态/Token/日志也会跟着居中，变成"居中排版"而不是"图标居中"。
 * tint 用中性色（不抢色）——颜色只由状态点承担"区分状态"的职责，图标保持中性以免四个卡五颜六色。
 */
@Composable
private fun CardIcon(icon: DrawableResource?) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

/**
 * Agent 卡片统一骨架。
 *
 * 约束一（宽度）：卡片只有半屏宽（内容区约 128dp，刚好放下一个 Agent 名字），
 * 所以严格竖排、每行一条信息，**不出现任何并排元素**——并排必然挤爆。
 * 约束二（高度）：**六行固定**，任何一行无数据都必须占位，四张卡行数相等 → 高度一致。
 * 六行：图标 / 名字 / 状态 / 当前动作 / Token / 日志条数。
 * 整卡可点 → 进该 Agent 配置页（省掉「设置」文字按钮）；日志行内层可点 → 弹窗明细。
 */
@Composable
private fun AgentStatusCard(
    icon: DrawableResource?,
    title: String,
    status: CapabilityState.Status?,
    statusText: String,
    detailText: String?,
    tokenText: String?,
    hazeState: HazeState,
    logBuckets: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HMPCard(modifier.then(Modifier.clickable { onClick() })) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Spacer(Modifier.height(16.dp))
            CardIcon(icon)
            Spacer(Modifier.height(16.dp))
            CardTitle(title)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status)
                Spacer(Modifier.width(6.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == CapabilityState.Status.ERROR) AgentStatusColors.Error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            CardRow(detailText)
            Spacer(Modifier.height(4.dp))
            CardRow(tokenText)
            CardLogSection(logBuckets, hazeState)
        }
    }
}

/** 细进度条：4dp + 圆角，比默认方角更收敛。 */
@Composable
private fun SlimProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
    )
}

// ── 通用：状态色点（色值取自共享令牌 AgentStatusColors）──
@Composable
private fun StatusDot(status: CapabilityState.Status?) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(agentStatusColor(status))
    )
}
