package com.hearablemusic.player.ui.agent.chat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_chat_always_allow
import com.hearablemusic.player.ui.generated.resources.agent_chat_always_allow_mark
import com.hearablemusic.player.ui.generated.resources.agent_chat_confirm_prompt
import com.hearablemusic.player.ui.generated.resources.agent_chat_do_it
import com.hearablemusic.player.ui.generated.resources.agent_chat_my_plan
import com.hearablemusic.player.ui.generated.resources.agent_chat_play_all
import com.hearablemusic.player.ui.generated.resources.agent_chat_skip
import com.hearablemusic.player.ui.generated.resources.chevron_down
import com.hearablemusic.player.ui.generated.resources.lightbulb
import com.hearablemusic.player.ui.generated.resources.more
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FixedMusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 气泡内交互回调（由 ChatScreen 透穿，默认空实现便于快照渲染）。 */
data class CompanionCallbacks(
    val onSongClick: (MusicInfo) -> Unit = {},
    val onSongMenu: (MusicInfo) -> Unit = {},
    val onPlaylistPlayAll: (List<MusicInfo>) -> Unit = {},
    val onToggleConfirm: (String) -> Unit = {},
    /** v7.1 新增：切换某项"总是允许"（写 AgentPolicyConfig.alwaysAllow） */
    val onToggleAlwaysAllow: (String) -> Unit = {},
    val onSubmitConfirm: () -> Unit = {},
    val onSkipConfirm: () -> Unit = {},
)

/**
 * M5-T2 五类气泡按 [CompanionRenderHint] 分发渲染。
 * 交互经 [CompanionCallbacks] 回调上层；纯展示时全部回调为空实现（快照/单测友好）。
 */
@Composable
fun CompanionBubble(
    message: CompanionMessage,
    callbacks: CompanionCallbacks = CompanionCallbacks(),
    modifier: Modifier = Modifier,
) {
    if (message.fromUser) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            BubbleContainer(message, callbacks, user = true)
        }
    } else {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            BubbleContainer(message, callbacks, user = false)
        }
    }
}

/** 气泡容器：用户=primary 半透明底+primary 边框(右对齐尾)；伙伴=surfaceContainer 半透明底+outlineVariant 边框(左对齐尾)。 */
@Composable
private fun BubbleContainer(message: CompanionMessage, callbacks: CompanionCallbacks, user: Boolean) {
    val metrics = LocalHmpMetrics.current
    val shape = if (user) {
        RoundedCornerShape(metrics.cozy, metrics.micro, metrics.cozy, metrics.cozy)
    } else {
        RoundedCornerShape(metrics.micro, metrics.cozy, metrics.cozy, metrics.cozy)
    }
    val bg = if (user) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.15f)
    }
    val border = if (user) {
        BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else {
        BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    val content = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .widthIn(max = HMPMetricTokens.Compact.hero)
            .border(border, shape)
            .background(bg, shape)
            .padding(horizontal = metrics.cozy, vertical = metrics.base),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            when (message.renderHint) {
                CompanionRenderHint.TEXT -> TextBubbleContent(message)

                CompanionRenderHint.SONG -> SongBubbleContent(message, callbacks)

                CompanionRenderHint.SONGLIST -> SongListBubbleContent(message, callbacks)

                CompanionRenderHint.EXPLAIN -> ExplainBubbleContent(message)

                CompanionRenderHint.CONFIRM -> ConfirmMatrixCard(
                    items = message.confirmItems,
                    submitted = true,
                    receipt = message.receipt,
                    callbacks = callbacks,
                )
            }
        }
    }
}

/** 供 ChatScreen 复用：会话内非模态确认矩阵卡（勾选/照做/跳过），未提交时可交互。 */
@Composable
fun ConfirmMatrixCard(
    items: List<ConfirmItem>,
    submitted: Boolean,
    receipt: UiText? = null,
    callbacks: CompanionCallbacks = CompanionCallbacks(),
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(metrics.cozy),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(metrics.base)) {
            // 标题 + 全选提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(metrics.compact)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(metrics.micro)),
                )
                Spacer(Modifier.width(metrics.compact))
                Text(
                    text = stringResource(Res.string.agent_chat_confirm_prompt),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(metrics.compact))
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = metrics.micro),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧：确认勾选框 + 工具名（可点击区域）
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (submitted) {
                                    Modifier
                                } else {
                                    Modifier.clickable { callbacks.onToggleConfirm(item.id) }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(metrics.cozy)
                                .background(
                                    if (item.selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(metrics.micro),
                                ),
                        ) {
                            if (item.selected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center),
                                    style = typo.footnote.medium(),
                                )
                            }
                        }
                        Spacer(Modifier.width(metrics.compact))
                        Text(
                            text = "${item.toolName} · ${item.argsSummary}",
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 右侧："总是允许" checkbox（仅未提交时可操作；不可逆项不提供永久放行）
                    if (!submitted && !item.permanentAllowForbidden) {
                        val labelColor = if (item.alwaysAllow) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { callbacks.onToggleAlwaysAllow(item.id) },
                        ) {
                            Checkbox(
                                checked = item.alwaysAllow,
                                onCheckedChange = null, // 整个 Row clickable，Checkbox 自身不消费点击
                                modifier = Modifier.size(metrics.section),
                            )
                            Text(
                                text = stringResource(Res.string.agent_chat_always_allow),
                                style = typo.caption,
                                color = labelColor,
                            )
                        }
                    } else if (item.alwaysAllow) {
                        // 已提交回执：如果勾了"总是允许"显示标记
                        Text(
                            text = stringResource(Res.string.agent_chat_always_allow_mark),
                            style = typo.caption,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(metrics.base))
            if (submitted) {
                receipt?.let {
                    Text(
                        text = it.asString(),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = callbacks.onSkipConfirm) { Text(stringResource(Res.string.agent_chat_skip)) }
                    Spacer(Modifier.width(metrics.compact))
                    OutlinedButton(onClick = callbacks.onSubmitConfirm) {
                        Text(stringResource(Res.string.agent_chat_do_it), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBubbleContent(message: CompanionMessage) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Text(
        // 资源型文案在组合期才解析（ViewModel 侧只带引用），随语言切换自动跟随
        text = message.textRes?.asString() ?: message.text,
        style = typo.body,
        color = LocalContentColor.current,
    )
    if (message.note.isNotBlank()) {
        Spacer(Modifier.height(metrics.micro))
        Text(
            text = message.note,
            style = typo.caption,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun SongBubbleContent(message: CompanionMessage, callbacks: CompanionCallbacks) {
    val metrics = LocalHmpMetrics.current
    val song = message.song ?: return
    val typo = LocalHmpTypo.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AlbumCover(
            uri = song.music.albumArtUri,
            size = metrics.bar,
            corner = metrics.compact,
            shadow = metrics.micro,
        )
        Spacer(Modifier.width(metrics.base))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.music.title,
                style = typo.body.medium(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.music.artist,
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { callbacks.onSongMenu(song) }) {
            Icon(
                painter = painterResource(Res.drawable.more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SongListBubbleContent(message: CompanionMessage, callbacks: CompanionCallbacks) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(modifier = Modifier.fillMaxWidth()) {
        val config = defaultMusicListConfig(
            callbacks = object : MusicListCallbacksAdapter() {
                override fun onItemClick(musicInfo: MusicInfo, index: Int) = callbacks.onSongClick(musicInfo)
                override fun onMenuClick(musicInfo: MusicInfo) = callbacks.onSongMenu(musicInfo)
            },
        ).copy(
            header = HeaderConfig.None,
            item = ItemConfig(
                variant = ItemVariant.Full,
                showIndex = true,
                fullOptions = FullItemOptions(
                    showPinButton = false,
                    showRemoveButton = false,
                    showMenuButton = true,
                ),
            ),
            edit = EditConfig(enabled = false),
            currentPlaying = CurrentPlayingConfig(index = null, autoScrollToCurrent = false),
        )
        FixedMusicList(
            musicInfoList = message.songs,
            config = config,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(metrics.compact))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { callbacks.onPlaylistPlayAll(message.songs) }
                .padding(vertical = metrics.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.play_fill),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(metrics.cozy),
            )
            Spacer(Modifier.width(metrics.micro))
            Text(
                text = stringResource(Res.string.agent_chat_play_all),
                style = typo.body,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ExplainBubbleContent(message: CompanionMessage) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.lightbulb),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(metrics.cozy),
            )
            Spacer(Modifier.width(metrics.compact))
            Text(
                text = stringResource(Res.string.agent_chat_my_plan),
                style = typo.body.medium(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(Res.drawable.chevron_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(metrics.cozy),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(HmpMotion.enter()) + expandIn(HmpMotion.enter()),
            exit = fadeOut(HmpMotion.exit()) + shrinkOut(HmpMotion.exit()),
        ) {
            Column(modifier = Modifier.padding(top = metrics.compact)) {
                message.trail.forEachIndexed { i, step ->
                    Text(
                        text = "${i + 1}. $step",
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = metrics.hair),
                    )
                }
            }
        }
    }
}

private fun Modifier.widthInMax(): Modifier = this.then(Modifier.width(LayoutConstraints.PanelMaxWidth))
