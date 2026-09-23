package com.hearablemusic.player.ui.agent.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.agent.runtime.sub.shared.RadioTrack
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberPlatformHaptics
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.radiowaves
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.platform.HapticEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * 节目单在控制台里**只列前 N 首**。
 *
 * 面板是"看一眼这一档在播什么"的地方，不是队列管理器（完整队列在播放页的播放列表里）。
 * 列满一屏会把它变成第二个队列页，反而看不清；5 首足够看出编排走向。
 */
private const val UPCOMING_PREVIEW = 5

/**
 * 电台控制台 —— 长按伙伴胶囊唤起的**全屏弹窗**
 * （设计依据 `docs/7_x/B agent-build/design/agent-radio-console.md`）。
 *
 * **为什么存在**：关闭电台是终态动作，不该由一个长按直接完成 —— 用户需要先看清"我要关掉的是什么"。
 * 于是长按改为打开本面板，关闭动作移入面板并更名**「结束这一档」**。
 * 面板两个职责：**看见**（主播为这段收听排的节目单 + 每首按语）与**收档**。
 *
 * **式样与 `MusicDetailDialog` 对齐**（用户决议 2026-09-19）：同一套弹窗基座与语汇 ——
 * `ScrimDialog`（真 `Dialog` + 0.5 黑遮罩 + 点遮罩关闭 + 系统返回键天然可用）+ 圆角 **28dp**
 * + 外边距 **24dp** + **haze 效果**与 `hazeTintAlpha()` 容器色 + elevation 0
 * + `headlineMedium` 标题 + `onBackground` 文本令牌。
 * 体量上仍按设计占满（`fillMaxSize` 后留 24dp 缝）= **几乎占满的弹窗**。
 *
 * **不打断电台**：面板是只读观察窗，打开期间电台照常运行、照常续歌。
 *
 * **数据源**：全部读 `MasterAgent` 已暴露的状态（`radioState` / `radioCardState` / `radioPlaylist`），
 * **零后端改动**。「本档台账」「多轮编排思路」需等 `RadioArchiveState` 转发（v2）。
 *
 * **边界**（刻意不做）：不做对话输入（不是第二个对话页）；不做"换一批 / 换主题"；
 * 不接管播放·暂停·切歌（播放控制不归电台管）；不显示模型自由文案（只渲染结构化字段 `why` / `reason`）。
 *
 * @param visible 是否展开
 * @param onDismiss 收起（电台继续跑，**不断流**）
 * @param onEndSession 结束这一档 → 调用方转 `masterAgent.stopRadio()`
 * @param onPlayTrack 点节目单某首 → 跳到那首（调用方负责 id→MusicInfo 解析与播放；
 *   **不在这里直接播** —— 面板不持有播放依赖，且"跳转"必须走既有队列而不是新建队列）
 * @param nowCoverUri 在播曲封面（电台镜像头与播放器当前曲一致；调用方传入，null = 用占位图标）
 */
@kotlin.OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun RadioConsole(
    visible: Boolean,
    onDismiss: () -> Unit,
    onEndSession: () -> Unit,
    onPlayTrack: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    nowCoverUri: String? = null,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null,
    masterAgent: MasterAgent = koinInject(),
) {
    if (!visible) return

    val haptic = rememberPlatformHaptics()
    val radioState by masterAgent.radioState.collectAsState()
    val card by masterAgent.radioCardState.collectAsState()
    val playlist by masterAgent.radioPlaylist.collectAsState()

    // 队列约定：index 0 = 在播（电台镜像头），其余为后续 —— 与 RadioSubAgent.currentPlaylist 同构
    val now: RadioTrack? = playlist.firstOrNull()
    val upcoming: List<RadioTrack> = remember(playlist) { playlist.drop(1) }
    val preview: List<RadioTrack> = remember(upcoming) { upcoming.take(UPCOMING_PREVIEW) }

    val stateText = when (radioState) {
        is RadioState.BUILDING -> "正在编排…"
        is RadioState.PAUSED -> "已暂停"
        is RadioState.PLAYING -> "运行中"
        else -> "已停止"
    }

    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(onDismissRequest = onDismiss) {
            // 大小自适应：**不固定尺寸**。宽度沿用项目弹窗惯例（24dp 边距内铺满）；
            // 高度随内容伸缩，封顶到"可用高度"之后由内部列表滚动（封顶值取自实测可用空间，不是写死数）。
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxPanelHeight = (maxHeight - 48.dp).coerceAtLeast(240.dp)
                val dialogShape = RoundedCornerShape(28.dp)
                Card(
                    modifier = modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxPanelHeight)
                        .clip(dialogShape)
                        // 吃掉面板内的点击，避免落到遮罩上误关闭（本面板含拖拽滑块，尤其重要）
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        .then(
                            if (hazeState != null) {
                                Modifier.hazeEffect(
                                    state = hazeState,
                                    style = hazeStyleForIntensity()
                                )
                            } else Modifier
                        ),
                    shape = dialogShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (hazeState != null) {
                            MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    // 高度自适应：列自身随内容收缩；仅中间列表在"封顶后"才吃剩余空间滚动
                    Column(modifier = Modifier.fillMaxWidth()) {

                    // ① 头部：电台标识 + 主题 + 状态 + 收起
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.radiowaves),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.theme?.takeIf { it.isNotBlank() } ?: "自动电台",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$stateText · 待播 ${card.upcomingCount} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        // 唯一刻意保留的偏离：满屏面板下，24dp 外缝太薄，单靠点外关闭不好按
                        Text(
                            text = "收起",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.perform(HapticEffect.TICK)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }

                    // ②③④ 主体（可滚动）
                    // fill = false：内容不多时只占自身高度（弹窗因此收缩），内容超高才吃满剩余空间并滚动
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    ) {
                        // ② 在播（ANCHOR）
                        now?.let { track ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (nowCoverUri != null) {
                                    AlbumCover(uri = nowCoverUri, size = 48.dp, corner = 12.dp, shadow = 6.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.radiowaves),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (track.artist.isNotBlank()) {
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (track.why.isNotBlank()) {
                                        Text(
                                            text = track.why,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ③ 节目单（主角：主播为这段收听排的东西；只列前 5 首，点任一可跳到那首）
                        Text(
                            text = when {
                                upcoming.isEmpty() -> "接下来 · 暂无待播"
                                upcoming.size > UPCOMING_PREVIEW ->
                                    "接下来 · 主播排的 ${upcoming.size} 首（此处列前 $UPCOMING_PREVIEW）"
                                else -> "接下来 · 主播排的 ${upcoming.size} 首"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        preview.forEachIndexed { index, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onPlayTrack(track.musicId) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (track.why.isNotBlank()) {
                                        Text(
                                            text = track.why,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // ④ 主播思路（最近一轮）
                        card.lastAdjust?.takeIf { it.isNotBlank() }?.let { intent ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "主播思路 · 最近一轮",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = intent,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // ⑤ 吸底：结束这一档（滑动收档）
                    EndSessionSlider(
                        enabled = radioState != null,
                        onDragStart = { haptic.perform(HapticEffect.DRAG_START) },
                        onEnd = {
                            haptic.perform(HapticEffect.CONFIRM)
                            onEndSession()
                        },
                    )
                    }   // Card 的内容列
                }       // Card
            }           // BoxWithConstraints（自适应尺寸）
        }               // ScrimDialog
    }                   // ProvideHazeRenderSettings
}                       // fun RadioConsole

/**
 * 滑动收档 —— 「结束这一档」的确认手势。
 *
 * **为什么不用单击**：结束这一档会**暂停音乐**（队列保留、可续档 —— 可逆，但有可见副作用），
 * 需要一个"确定感"足够强、又不可能误触的动作。横滑到底 = 自解释的"我确定"，
 * 且不引入第二层弹窗（弹窗套弹窗）。
 * 手势与项目既有横滑切歌（`BottomFusionBar` 播放胶囊）同源，学习成本为零。
 *
 * 触发时机：**位移达行程 92% 才触发**；未达阈值松手回弹，一切照旧。
 */
@Composable
private fun EndSessionSlider(
    enabled: Boolean,
    onDragStart: () -> Unit,
    onEnd: () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableIntStateOf(0) }
    val animatedOffset by animateFloatAsState(
        dragOffset,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
    )
    val knobSize = 48.dp
    val maxTravel = (trackWidth - 48).coerceAtLeast(1).toFloat()
    val fireThreshold = maxTravel * 0.92f
    val dangerColor = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(knobSize)
                .clip(RoundedCornerShape(24.dp))
                .background(dangerColor.copy(alpha = 0.08f))
                .onSizeChanged { trackWidth = it.width }
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(maxTravel) {
                        detectHorizontalDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = {
                                if (dragOffset >= fireThreshold) onEnd()
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                            onHorizontalDrag = { _, amount ->
                                dragOffset = (dragOffset + amount).coerceIn(0f, maxTravel)
                            },
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "滑动结束这一档",
                style = MaterialTheme.typography.labelMedium,
                color = dangerColor.copy(alpha = 0.75f),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(dangerColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "音乐将暂停，队列保留；情境连续时再次开启可续上这一档",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
