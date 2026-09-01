package com.hearablemusic.player.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.chevron_down
import com.hearablemusic.player.ui.generated.resources.lightbulb
import com.hearablemusic.player.ui.generated.resources.more
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.player_d
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FixedMusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 气泡内交互回调（由 ChatScreen 透穿，默认空实现便于快照渲染）。 */
data class CompanionCallbacks(
    val onSongClick: (MusicInfo) -> Unit = {},
    val onSongMenu: (MusicInfo) -> Unit = {},
    val onPlaylistPlayAll: () -> Unit = {},
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

/** 气泡容器：用户=品牌实底+白字(右对齐，右上小圆角尾)；伙伴=浅底+深字(左对齐，左上小圆角尾)。 */
@Composable
private fun BubbleContainer(message: CompanionMessage, callbacks: CompanionCallbacks, user: Boolean) {
    val shape = if (user) RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp)
    else RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)
    val bg = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(bg, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
    receipt: String = "",
    callbacks: CompanionCallbacks = CompanionCallbacks(),
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 标题 + 全选提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "我想这么做，请勾选：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧：确认勾选框 + 工具名（可点击区域）
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (submitted) Modifier
                                else Modifier.clickable { callbacks.onToggleConfirm(item.id) }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    when {
                                        submitted && item.selected -> MaterialTheme.colorScheme.primary
                                        item.selected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                        ) {
                            if (item.selected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${item.toolName} · ${item.argsSummary}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 右侧："总是允许" checkbox（仅未提交时可操作）
                    if (!submitted) {
                        val labelColor = if (item.alwaysAllow)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { callbacks.onToggleAlwaysAllow(item.id) },
                        ) {
                            Checkbox(
                                checked = item.alwaysAllow,
                                onCheckedChange = null,  // 整个 Row clickable，Checkbox 自身不消费点击
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = "总是允许",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                            )
                        }
                    } else if (item.alwaysAllow) {
                        // 已提交回执：如果勾了"总是允许"显示标记
                        Text(
                            text = "✦ 总是允许",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (submitted) {
                if (receipt.isNotBlank()) {
                    Text(
                        text = receipt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = callbacks.onSkipConfirm) { Text("跳过") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = callbacks.onSubmitConfirm) {
                        Text("照做", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBubbleContent(message: CompanionMessage) {
    Text(
        text = message.text,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalContentColor.current,
    )
    if (message.note.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = message.note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun SongBubbleContent(message: CompanionMessage, callbacks: CompanionCallbacks) {
    val song = message.song ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AlbumCover(
            uri = song.music.albumArtUri,
            size = 48.dp,
            corner = 10.dp,
            shadow = 4.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.music.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.music.artist,
                style = MaterialTheme.typography.bodySmall,
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
    Column(modifier = Modifier.fillMaxWidth()) {
        val config = defaultMusicListConfig(
            callbacks = object : MusicListCallbacksAdapter() {
                override fun onItemClick(musicInfo: MusicInfo, index: Int) = callbacks.onSongClick(musicInfo)
                override fun onMenuClick(musicInfo: MusicInfo) = callbacks.onSongMenu(musicInfo)
            }
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
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = callbacks.onPlaylistPlayAll)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.play_fill),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "播放全部",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ExplainBubbleContent(message: CompanionMessage) {
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
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "我的打算",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(Res.drawable.chevron_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                message.trail.forEachIndexed { i, step ->
                    Text(
                        text = "${i + 1}. $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.widthInMax(): Modifier = this.then(Modifier.width(280.dp))