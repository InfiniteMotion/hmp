package com.hearablemusic.player.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hmp.data.database.AgentAuditLog
import com.hmp.data.database.AgentAuditLogDao
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.formatEpochMillis
import org.koin.compose.koinInject

/**
 * M6-T4：Agent 操作审计日志页面。
 * 每行一条审计记录（时间戳 + tool + outcome + reason）。
 * 支持筛选：全部 / 工具调用 / 电台操作 / 跳过重排 / LLM 生成。
 * 点击行展开详情。
 */
@Composable
fun AuditLogScreen(
    navController: NavBackStack<NavKey>,
) {
    val dao: AgentAuditLogDao = koinInject()
    val viewModel = remember { AuditLogViewModel(dao) }

    val logs by viewModel.logs.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = "操作审计日志",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // 筛选 Tab
            FilterRow(
                currentFilter = currentFilter,
                onFilterSelected = { viewModel.applyFilter(it) }
            )

            Spacer(Modifier.height(8.dp))

            if (logs.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        AuditLogRow(log)
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 筛选 Tab
// ═══════════════════════════════════════════════════════════════

private data class FilterTab(val label: String, val tool: String? = null)

private val filterTabs = listOf(
    FilterTab("全部"),
    FilterTab("电台操作", "radio.start"),
    FilterTab("跳过重排", "radio.reorder"),
    FilterTab("LLM 生成", "dj.segue"),
)

@Composable
private fun FilterRow(
    currentFilter: AuditLogViewModel.Filter,
    onFilterSelected: (AuditLogViewModel.Filter) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filterTabs.forEach { tab ->
            val selected = when (currentFilter) {
                is AuditLogViewModel.Filter.All -> tab.tool == null
                is AuditLogViewModel.Filter.ByTool -> tab.tool == currentFilter.tool
            }
            FilterChip(
                selected = selected,
                onClick = {
                    if (tab.tool == null) {
                        onFilterSelected(AuditLogViewModel.Filter.All)
                    } else {
                        onFilterSelected(AuditLogViewModel.Filter.ByTool(tab.tool))
                    }
                },
                label = { Text(tab.label) },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 空态
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "还没有审计记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "开始使用 AI 功能后这里会自动记录所有操作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 单条记录行
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AuditLogRow(log: AgentAuditLog) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = log.tool,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        OutcomeBadge(outcome = log.outcome)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(log.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = log.reason ?: "(无详情)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (log.argsHash != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "参数哈希: ${log.argsHash}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (log.taskId != null) {
                    Text(
                        text = "任务 ID: ${log.taskId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "记录 ID: ${log.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: String) {
    val (bg, fg) = when (outcome) {
        "success", "allowed_silent", "allowed_notify" ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        "failed" ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
        "denied", "refused", "rejected" ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
        "pending_confirm" ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.tertiary
        else ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = outcome,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

private fun formatTimestamp(ts: Long): String =
    runCatching { formatEpochMillis(ts, "yyyy-MM-dd HH:mm:ss") }.getOrDefault("timestamp=$ts")
