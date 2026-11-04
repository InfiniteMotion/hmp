package com.hearablemusic.player.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.domain.agent.quota.TokenQuotaGate
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_radio_anchor_thought
import com.hearablemusic.player.ui.generated.resources.agent_radio_auto_theme
import com.hearablemusic.player.ui.generated.resources.agent_radio_status_building
import com.hearablemusic.player.ui.generated.resources.agent_radio_status_line
import com.hearablemusic.player.ui.generated.resources.agent_radio_status_paused
import com.hearablemusic.player.ui.generated.resources.agent_radio_status_playing
import com.hearablemusic.player.ui.generated.resources.agent_radio_status_stopped
import com.hearablemusic.player.ui.generated.resources.ai_quota_exhausted_hint
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.home_reco_unavailable
import com.hearablemusic.player.ui.generated.resources.radio_card_building
import com.hearablemusic.player.ui.generated.resources.radio_card_enable_hint
import com.hearablemusic.player.ui.generated.resources.radio_card_end_session
import com.hearablemusic.player.ui.generated.resources.radio_card_idle
import com.hearablemusic.player.ui.generated.resources.radio_card_open
import com.hearablemusic.player.ui.generated.resources.radio_card_open_console
import com.hearablemusic.player.ui.generated.resources.radio_entry_seed_desc
import com.hearablemusic.player.ui.generated.resources.radio_entry_seed_title
import com.hearablemusic.player.ui.generated.resources.radiowaves
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

/**
 * 播放页的电台入口（替换原「生成推荐列表」）。
 *
 * 定位是**状态卡 + 打开控制面板**，不是把 RadioConsole 搬进来：
 * - 播放页已有主播放键，暂停/继续由它承担（用户暂停会经 `pauseByUser` 同步到电台）
 * - 「结束这一档」不在卡上直接 stop，而是打开 Console 走滑动收档，保留防误触
 *
 * 无在播曲目时不渲染 —— 播放页本身也进不来（用户 2026-11-03 口径）。
 */
@Composable
fun RadioEntryCard(
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val masterAgent: MasterAgent = koinInject()
    val quotaGate: TokenQuotaGate = koinInject()
    val dialogManager: DialogManager = koinInject()
    val scope = rememberCoroutineScope()
    val typo = LocalHmpTypo.current

    val radioState by masterAgent.radioState.collectAsState()
    val card by masterAgent.radioCardState.collectAsState()

    val isBuilding = radioState is RadioState.BUILDING
    val isPaused = radioState is RadioState.PAUSED
    val isActive = isBuilding || isPaused || radioState is RadioState.PLAYING
    val quotaExhausted = quotaGate.isBucketExhausted("radio")
    val quotaText = stringResource(Res.string.ai_quota_exhausted_hint)

    val statusText = when {
        isBuilding -> stringResource(Res.string.agent_radio_status_building)
        isPaused -> stringResource(Res.string.agent_radio_status_paused)
        radioState is RadioState.PLAYING -> stringResource(Res.string.agent_radio_status_playing)
        else -> stringResource(Res.string.agent_radio_status_stopped)
    }

    fun start() {
        if (quotaExhausted) {
            scope.launch { dialogManager.showMessage(quotaText) }
            return
        }
        scope.launch { masterAgent.startRadio() }
    }

    HMPCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        contentPadding = Modifier.padding(metrics.cozy),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.radiowaves),
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(metrics.cozy),
                )
                Spacer(Modifier.size(metrics.compact))
                Text(
                    text = stringResource(Res.string.radio_card_idle),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (isActive) {
                    Text(
                        text = statusText,
                        style = typo.caption,
                        color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(metrics.base))

            when {
                isBuilding -> {
                    Text(
                        text = stringResource(Res.string.radio_card_building),
                        style = typo.subtitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(metrics.compact))
                    val building = radioState as? RadioState.BUILDING
                    LinearProgressIndicator(
                        progress = { ((building?.progressPercent ?: 0) / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(metrics.micro),
                    )
                    Spacer(Modifier.height(metrics.compact))
                    Text(
                        text = building?.actionText ?: stringResource(Res.string.radio_card_enable_hint),
                        style = typo.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                isActive -> {
                    Text(
                        text = card.theme?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.agent_radio_auto_theme),
                        style = typo.subtitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(metrics.micro))
                    Text(
                        text = stringResource(
                            Res.string.agent_radio_status_line,
                            statusText,
                            card.upcomingCount,
                        ),
                        style = typo.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    card.lastAdjust?.takeIf { it.isNotBlank() }?.let { thought ->
                        Spacer(Modifier.height(metrics.compact))
                        Text(
                            text = stringResource(Res.string.agent_radio_anchor_thought),
                            style = typo.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = thought,
                            style = typo.footnote,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(Res.string.radio_entry_seed_title),
                        style = typo.subtitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(metrics.micro))
                    Text(
                        text = stringResource(Res.string.radio_entry_seed_desc),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(metrics.cozy))

            when {
                isBuilding -> {
                    OutlinedButton(
                        onClick = { scope.launch { masterAgent.stopRadio() } },
                        modifier = Modifier.fillMaxWidth().height(metrics.bar),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                }

                isActive -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.compact),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onOpenConsole,
                            modifier = Modifier.weight(1f).height(metrics.bar),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(stringResource(Res.string.radio_card_open_console))
                        }
                        TextButton(
                            onClick = onOpenConsole,
                            modifier = Modifier.height(metrics.bar),
                        ) {
                            Text(
                                text = stringResource(Res.string.radio_card_end_session),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = ::start,
                        enabled = !quotaExhausted,
                        modifier = Modifier.fillMaxWidth().height(metrics.bar),
                    ) {
                        Text(
                            text = if (quotaExhausted) {
                                stringResource(Res.string.home_reco_unavailable)
                            } else {
                                stringResource(Res.string.radio_card_open)
                            },
                        )
                    }
                }
            }
        }
    }
}
