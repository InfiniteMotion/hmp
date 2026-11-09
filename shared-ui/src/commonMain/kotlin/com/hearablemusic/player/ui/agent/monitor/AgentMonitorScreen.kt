package com.hearablemusic.player.ui.agent.monitor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Severity
import com.hearablemusic.player.ui.agent.agentDetailOf
import com.hearablemusic.player.ui.agent.agentIcon
import com.hearablemusic.player.ui.agent.agentStatusColor
import com.hearablemusic.player.ui.agent.agentStatusLabel
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.colors.AgentStatusColors
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.fmtTokens
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_monitor_active
import com.hearablemusic.player.ui.generated.resources.agent_monitor_log_count
import com.hearablemusic.player.ui.generated.resources.agent_monitor_log_dialog_title
import com.hearablemusic.player.ui.generated.resources.agent_monitor_log_meta
import com.hearablemusic.player.ui.generated.resources.agent_monitor_no_data
import com.hearablemusic.player.ui.generated.resources.agent_monitor_no_logs
import com.hearablemusic.player.ui.generated.resources.agent_monitor_quota_line
import com.hearablemusic.player.ui.generated.resources.agent_monitor_run_logs
import com.hearablemusic.player.ui.generated.resources.agent_monitor_title
import com.hearablemusic.player.ui.generated.resources.agent_monitor_token_clear_confirm
import com.hearablemusic.player.ui.generated.resources.agent_monitor_token_line
import com.hearablemusic.player.ui.generated.resources.agent_monitor_token_usage
import com.hearablemusic.player.ui.generated.resources.agent_monitor_total_usage
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.clear
import com.hearablemusic.player.ui.generated.resources.confirm
import com.hmp.data.util.localDateString
import com.hmp.domain.agent.port.CapabilityState
import com.hmp.domain.agent.quota.TokenLedgerSnapshot
import com.hmp.log.LogEntry
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    val metrics = LocalHmpMetrics.current
    val viewModel: AgentMonitorViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshCapabilities() }
    // 日志弹窗的毛玻璃要采样本页内容 → 本页建 HazeState 并把内容标为 hazeSource。
    val hazeState = rememberHazeState()

    // 账本快照：总用量与按 Agent 分账共用同一个 30 天窗口（D6-05）。
    // 快照由屏级 VM 持有，这里只把分账行格式化成文案（纯渲染）。
    @Composable
    fun tokenTextFor(agentId: String): String? {
        val row = uiState.snapshot?.agentRows?.firstOrNull { it.label.equals(agentId, ignoreCase = true) } ?: return null
        return stringResource(Res.string.agent_monitor_token_line, fmtTokens(row.totalTokens), row.calls)
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.agent_monitor_title),
    ) {
        // F14-T1 自适应：Expanded（桌面宽窗 / 平板横屏）下六卡改「三列两行」一次尽收，
        // 同时内容整体限宽居中，避免宽窗下拉满导致的超长行；Medium 与 Compact 保持两列。
        // 横屏（含手机横屏，宽 800–840 也落在 Medium）：三列 + 限宽 —— 横向有余量时两列会拉出超长卡。
        val window = LocalWindowSizeInfo.current
        val isExpanded = window.isExpanded
        val columns = if (isExpanded || window.isLandscape) 3 else 2
        val maxBoardWidth = if (isExpanded || window.isLandscape) LayoutConstraints.BoardMaxWidth else Dp.Unspecified

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxBoardWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .hazeSource(state = hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.cozy, vertical = metrics.cozy),
                verticalArrangement = Arrangement.spacedBy(metrics.base),
            ) {
                TokenMonitorCard(
                    snap = uiState.tokenUsage,
                    mode = uiState.quotaMode,
                    bucketUsage = uiState.bucketUsage,
                    exhaustedBuckets = uiState.exhaustedBuckets,
                    snapshot = uiState.snapshot,
                    showClearConfirm = uiState.showClearConfirm,
                    onReload = viewModel::onReloadRequested,
                    onClearRequested = viewModel::onClearRequested,
                    onClearDismissed = viewModel::onClearDismissed,
                    onClearConfirmed = viewModel::onClearConfirmed,
                )

                // 网格：每行 [columns] 张，同行等高。
                // 等高靠 Row 的 IntrinsicSize.Max 取同行最高内容作为行高（约束的是**纵轴**），
                // 卡宽则用横轴的 weight(1f) 均分 —— 两个方向互不干扰，故可同时使用。
                // 末尾行不足一列时用 Spacer(weight) 补位，避免最后一张被拉宽。
                val cards: List<@Composable (Modifier) -> Unit> = listOf(
                    { m ->
                        MasterMonitorCard(
                            radioState = uiState.radioState,
                            enrichState = uiState.enrichState,
                            helloState = uiState.helloState,
                            tokenText = tokenTextFor("master"),
                            logEntries = uiState.logsByBucket["Master"].orEmpty(),
                            hazeState = hazeState,
                            onClick = { navController.add(Routes.AI.AgentConfig("master")) },
                            modifier = m,
                        )
                    },
                    { m ->
                        RadioMonitorCard(
                            state = uiState.radioState,
                            tokenText = tokenTextFor("radio"),
                            logEntries = uiState.logsByBucket["Radio"].orEmpty(),
                            hazeState = hazeState,
                            onClick = { navController.add(Routes.AI.AgentConfig("radio")) },
                            modifier = m,
                        )
                    },
                    { m ->
                        EnrichMonitorCard(
                            state = uiState.enrichState,
                            tokenText = tokenTextFor("enrich"),
                            logEntries = uiState.logsByBucket["Enrich"].orEmpty(),
                            hazeState = hazeState,
                            onClick = { navController.add(Routes.AI.AgentConfig("enrich")) },
                            modifier = m,
                        )
                    },
                    { m ->
                        HelloMonitorCard(
                            state = uiState.helloState,
                            tokenText = tokenTextFor("hello"),
                            logEntries = uiState.logsByBucket["Hello"].orEmpty(),
                            hazeState = hazeState,
                            onClick = { navController.add(Routes.AI.AgentConfig("hello")) },
                            modifier = m,
                        )
                    },
                )
                val groups = cards.chunked(columns)

                Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(metrics.compact),
                        ) {
                            group.forEach { card ->
                                card(Modifier.weight(1f))
                            }
                            // 末行不足一列时补位
                            repeat(columns - group.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 全局 Token 用量（明细账本分账，F12-T2）──
@Composable
private fun TokenMonitorCard(
    snap: com.hmp.domain.agent.runtime.TokenSnapshot,
    mode: com.hmp.domain.setting.model.AiAccessMode,
    bucketUsage: Map<com.hmp.domain.agent.quota.QuotaBucket, Long>,
    exhaustedBuckets: Set<com.hmp.domain.agent.quota.QuotaBucket>,
    snapshot: TokenLedgerSnapshot?,
    showClearConfirm: Boolean,
    onReload: () -> Unit,
    onClearRequested: () -> Unit,
    onClearDismissed: () -> Unit,
    onClearConfirmed: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current

    val total = snapshot?.totalTokens ?: 0L
    val totalRows = snapshot?.totalRows ?: 0L
    val earliestText = snapshot?.earliestMs?.let { localDateString(it) } ?: "—"

    HMPCard {
        Column(modifier = Modifier.padding(metrics.cozy)) {
            // 标题 + 清空（仅在有记录时可用）+ 刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(Res.string.agent_monitor_token_usage))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.clear),
                        style = typo.caption,
                        color = if (totalRows > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(metrics.micro))
                            .clickable(enabled = totalRows > 0) { onClearRequested() }
                            .padding(horizontal = metrics.compact, vertical = metrics.micro),
                    )
                    IconButton(onClick = onReload, modifier = Modifier.size(metrics.section)) {
                        Text(
                            "↻",
                            style = typo.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(metrics.micro))
            // 窗口内总用量（近 30 天）——数字是这张卡的视觉焦点
            Text(
                fmtTokens(total),
                style = typo.subtitle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(metrics.hair))
            Text(
                stringResource(Res.string.agent_monitor_total_usage),
                style = typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 配额进度：只有真的存在上限时才画。
            // CUSTOM 且用户没开限额时 quota=0 —— 画一条 0% 的进度条 +「剩余 0」是在骗人。
            if (snap.quota > 0L) {
                Spacer(Modifier.height(metrics.base))
                SlimProgress(snap.rate)
                Spacer(Modifier.height(metrics.micro))
                Text(
                    stringResource(Res.string.agent_monitor_quota_line, snap.used, snap.remaining, (snap.rate * 100).toInt()),
                    style = typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 免费模式：四桶明细（哪桶用完了标红）。用户要能看出是谁把额度吃掉的。
            if (mode == com.hmp.domain.setting.model.AiAccessMode.FREE) {
                Spacer(Modifier.height(metrics.base))
                com.hmp.domain.agent.quota.QuotaBucket.entries.forEach { bucket ->
                    val used = bucketUsage[bucket] ?: 0L
                    val cap = com.hmp.domain.agent.quota.TokenQuotaGate.FREE_BUCKET_CAPS[bucket] ?: 0L
                    val drained = bucket in exhaustedBuckets
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = metrics.hair),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = bucket.bucketId.replaceFirstChar { it.uppercase() },
                            style = typo.caption,
                            color = if (drained) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "${fmtTokens(used)} / ${fmtTokens(cap)}",
                            style = typo.caption,
                            color = if (drained) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }

    // 清空确认：正文带「将删除多少条、自哪天起」，避免用户点一下就抹掉全部诊断数据。
    if (showClearConfirm) {
        ScrimDialog(onDismissRequest = onClearDismissed) {
            HMPCard(modifier = Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(metrics.section)) {
                Column(modifier = Modifier.padding(metrics.cozy)) {
                    Text(
                        stringResource(Res.string.agent_monitor_token_clear_confirm, totalRows.toString(), earliestText),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(metrics.base))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClearDismissed) {
                            Text(stringResource(Res.string.cancel))
                        }
                        TextButton(onClick = onClearConfirmed) {
                            Text(stringResource(Res.string.confirm))
                        }
                    }
                }
            }
        }
    }
}

// ── 各 Agent 卡片内嵌运行日志（按 Agent 分桶，F11/F12 收尾）──
// buckets：该卡片归属的 Agent 桶（见 MemLogWriter.TAG_TO_AGENT）。
// 只显示四个 Agent 自身（Master/Radio/Enrich/Hello）；「框架」等非 Agent 日志一律不过滤进看板。
// 卡片内只显示条数；点击条数弹出 ScrimDialog 查看具体条目。
@Composable
private fun CardLogSection(
    title: String,
    entries: List<LogEntry>,
    hazeState: HazeState,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    var showDetail by remember { mutableStateOf(false) }

    Spacer(Modifier.height(metrics.compact))
    // 紧凑一行：左「📝 运行日志」/ 右「N 条 ›」，整体可点（› 暗示可展开）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.compact))
            .clickable { showDetail = true }
            .padding(horizontal = metrics.compact, vertical = metrics.micro),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.agent_monitor_run_logs),
                style = typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(Res.string.agent_monitor_log_count, entries.size),
            style = typo.caption,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (showDetail) {
        LogDetailDialog(
            title = stringResource(Res.string.agent_monitor_log_dialog_title, title),
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
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    ScrimDialog(onDismissRequest = onDismiss) {
        // 式样对齐 MusicDetailDialog：hazeEffect + surface 半透明底色 + 圆角 28dp + 无描边 + elevation 0。
        // HMPCard 是 Box+background（无 elevation 概念），天然满足 elevation 0；
        // hazeEffect 必须放在容器 modifier 上、半透明底色之下才能看到模糊。
        HMPCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .hazeEffect(state = hazeState, style = hazeStyleForIntensity()),
            shape = RoundedCornerShape(metrics.section),
            borderColor = Color.Transparent,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha()),
        ) {
            Column(modifier = Modifier.heightIn(max = LayoutConstraints.HeightBreakpointMedium)) {
                Text(
                    title,
                    style = typo.body.copy(letterSpacing = 0.4.sp), // design-token-exception: 字距，非字号
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(metrics.hair))
                Text(
                    stringResource(Res.string.agent_monitor_log_meta, entries.size),
                    style = typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(metrics.compact))
                // 极浅分隔线：弹窗内只留这一条，起锚定作用
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.stroke.thin)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(metrics.compact))
                if (entries.isEmpty()) {
                    Text(
                        stringResource(Res.string.agent_monitor_no_logs),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(metrics.micro),
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
    val typo = LocalHmpTypo.current
    val color = when (entry.severity) {
        Severity.Verbose, Severity.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
        Severity.Info -> MaterialTheme.colorScheme.onSurface
        Severity.Warn -> AgentStatusColors.Warn
        Severity.Error, Severity.Assert -> MaterialTheme.colorScheme.error
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "[${entry.tag}] ",
            style = typo.caption,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            entry.message,
            style = typo.footnote,
            color = color,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── MasterAgent（编排中枢：自身无 capability，状态行改为「活跃子 Agent 数」） ──
@Composable
private fun MasterMonitorCard(
    radioState: CapabilityState?,
    enrichState: CapabilityState?,
    helloState: CapabilityState?,
    tokenText: String?,
    logEntries: List<LogEntry>,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = listOf(radioState, enrichState, helloState).count { s ->
        s?.status == CapabilityState.Status.RUNNING || s?.status == CapabilityState.Status.BUILDING
    }

    AgentStatusCard(
        icon = agentIcon("master"),
        title = "MasterAgent",
        status = if (active > 0) CapabilityState.Status.RUNNING else CapabilityState.Status.IDLE,
        statusText = stringResource(Res.string.agent_monitor_active, active),
        // 编排中枢没有自己的 capability，无 detail → 占位
        detailText = null,
        tokenText = tokenText,
        hazeState = hazeState,
        logTitle = "Master",
        logEntries = logEntries,
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Radio ──
@Composable
private fun RadioMonitorCard(
    state: CapabilityState?,
    tokenText: String?,
    logEntries: List<LogEntry>,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AgentStatusCard(
        icon = agentIcon("radio"),
        title = "RadioSubAgent",
        status = state?.status,
        statusText = agentStatusLabel(state),
        detailText = agentDetailOf(state),
        tokenText = tokenText,
        hazeState = hazeState,
        logTitle = "Radio",
        logEntries = logEntries,
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Enrich ──
@Composable
private fun EnrichMonitorCard(
    state: CapabilityState?,
    tokenText: String?,
    logEntries: List<LogEntry>,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AgentStatusCard(
        icon = agentIcon("enrich"),
        title = "EnrichSubAgent",
        status = state?.status,
        statusText = agentStatusLabel(state),
        detailText = agentDetailOf(state),
        tokenText = tokenText,
        hazeState = hazeState,
        logTitle = "Enrich",
        logEntries = logEntries,
        onClick = onClick,
        modifier = modifier,
    )
}

// ── Hello ──
@Composable
private fun HelloMonitorCard(
    state: CapabilityState?,
    tokenText: String?,
    logEntries: List<LogEntry>,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AgentStatusCard(
        icon = agentIcon("hello"),
        title = "HelloSubAgent",
        status = state?.status,
        statusText = agentStatusLabel(state),
        detailText = agentDetailOf(state),
        tokenText = tokenText,
        hazeState = hazeState,
        logTitle = "Hello",
        logEntries = logEntries,
        onClick = onClick,
        modifier = modifier,
    )
}

/** 小标题：小字号 + 宽字距 + 次级灰，靠留白分区（不加竖色条 / 下划线）。 */
@Composable
private fun SectionLabel(text: String) {
    val typo = LocalHmpTypo.current
    Text(
        text,
        style = typo.caption.copy(letterSpacing = 0.6.sp), // design-token-exception: 字距，非字号
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 卡片标题：字距 + SemiBold（避开 Bold 的厚重感；纯英文标题可安全加字距），水平居中。 */
@Composable
private fun CardTitle(text: String) {
    val typo = LocalHmpTypo.current
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = typo.body.copy(letterSpacing = 0.4.sp), // design-token-exception: 字距，非字号
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 卡片字段行：无数据时显示**空提示语**「暂无」而不是留白，保证各卡行数与行高严格相等 → 等高。
 * 不要改成"为空就不渲染"——那会让卡片高度随数据有无而抖动；
 * 也不要渲染空格——那看起来像"没加载出来"，用提示语才能区分「无数据」和「加载失败/渲染缺失」。
 */
@Composable
private fun CardRow(text: String?) {
    val typo = LocalHmpTypo.current
    Text(
        text = text?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.agent_monitor_no_data),
        style = typo.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
    val metrics = LocalHmpMetrics.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(metrics.bar),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Spacer(Modifier.size(metrics.bar))
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
    logTitle: String,
    logEntries: List<LogEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    HMPCard(modifier.then(Modifier.clickable { onClick() })) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Spacer(Modifier.height(metrics.cozy))
            CardIcon(icon)
            Spacer(Modifier.height(metrics.cozy))
            CardTitle(title)
            Spacer(Modifier.height(metrics.cozy))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status)
                Spacer(Modifier.width(metrics.micro))
                Text(
                    statusText,
                    style = typo.caption,
                    color = if (status == CapabilityState.Status.ERROR) {
                        AgentStatusColors.Error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(metrics.micro))
            CardRow(detailText)
            Spacer(Modifier.height(metrics.micro))
            CardRow(tokenText)
            CardLogSection(title = logTitle, entries = logEntries, hazeState = hazeState)
        }
    }
}

/** 细进度条：4dp + 圆角，比默认方角更收敛。 */
@Composable
private fun SlimProgress(progress: Float) {
    val metrics = LocalHmpMetrics.current
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.micro)
            .clip(RoundedCornerShape(metrics.hair)),
    )
}

// ── 通用：状态色点（色值取自共享令牌 AgentStatusColors）──
@Composable
private fun StatusDot(status: CapabilityState.Status?) {
    Box(
        modifier = Modifier
            .size(LayoutConstraints.StatusDotSize)
            .clip(CircleShape)
            .background(agentStatusColor(status)),
    )
}
