package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.card.RecommendItem
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.card.TimePhase
import com.hmp.domain.music.MusicRepository
import com.hearablemusic.player.ui.common.components.base.BackButton
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.platform.rememberStatusBarsController
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * G6 推荐列表二级页（每日推荐 / 私人推荐，同构）。
 *
 * 沉浸式版：进入即隐藏状态栏，种子曲封面全出血英雄区（时段大标题 + AI 总述 + 播放全部）
 * → **整页深色**（跟随封面基调，避免英雄区深、列表白的割裂）→ 无卡片底色的歌曲列表。
 *
 * 数据源 = MasterAgent 的固定转发流 `dailyRecommendList` / `privateRecommendList`（内部镜像 Hello 子代理）。
 * 三态：payload==null → 生成中；items 空 → 无数据；否则列表。
 */
@Composable
fun RecommendListScreen(
    source: RecommendSource,
    navController: NavBackStack<NavKey>,
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
) {
    val masterAgent: MasterAgent = koinInject()
    val musicRepository: MusicRepository = koinInject()
    val themeViewModel: ThemeViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    // 沉浸式：进入本页隐藏状态栏（AppRoot 的 statusBarsPadding 随之归零，英雄区顶到屏幕最上沿）；
    // 退出恢复。Desktop 无系统状态栏 → 控制器为 null，自动跳过。
    val statusBars = rememberStatusBarsController()
    DisposableEffect(Unit) {
        statusBars?.hide()
        onDispose { statusBars?.show() }
    }

    // MasterAgent 持有的固定转发流（首帧即可拿到，不会缓存到 null / 旧实例）
    val payload by (
        if (source == RecommendSource.DAILY) masterAgent.dailyRecommendList
        else masterAgent.privateRecommendList
        ).collectAsState()

    // hydrate：trackId → MusicInfo（列表只持久化 id + 按语）
    val items by produceState(initialValue = emptyList<RecommendItem>(), payload) {
        val p = payload
        value = if (p == null || p.items.isEmpty()) {
            emptyList()
        } else {
            val infos = runCatching { musicRepository.getMusicInfoByIds(p.items.map { it.trackId }) }
                .getOrNull().orEmpty().associateBy { it.music.id }
            p.items.mapNotNull { rec ->
                infos[rec.trackId]?.let { RecommendItem(it, rec.reason, p.source, p.phase) }
            }
        }
    }

    // 种子曲封面取色 → 整页深色基调
    val seedUri = items.firstOrNull()?.musicInfo?.music?.albumArtUri?.takeIf { it.isNotBlank() }
    val palette by produceState(initialValue = PaletteColors(), seedUri) {
        value = runCatching { themeViewModel.paletteFor(seedUri) }.getOrDefault(PaletteColors())
    }

    // 整页底色：封面主色压暗（跟随封面基调，且保证是深色）
    val pageBg = lerp(palette.background, Color.Black, 0.5f)
    val ink = Color.White
    val inkSecondary = Color.White.copy(alpha = 0.72f)
    val inkTertiary = Color.White.copy(alpha = 0.52f)
    val dividerColor = Color.White.copy(alpha = 0.12f)
    val thumbBg = Color.White.copy(alpha = 0.08f)

    fun playFrom(index: Int) {
        if (items.isEmpty()) return
        scope.launch {
            val infos = items.map { it.musicInfo }
            // 追加到现有队列尾部（**不清空** —— 点推荐不该丢掉用户已有的待播列表），
            // 再定位到点中的那首播起。
            // 注：原来这里用 `clearPlaylist() + addAllToPlaylistInOrder()`，而后者名不副实 ——
            // 它做的是「整条替换 + 索引归零 + 从第一首播」，既清掉了旧队列，
            // 也让 `playFrom(index)` 的 index 彻底失效（永远从 0 播）。
            // `addToPlaylist` 内含整队列去重，已在队列中的曲目不会重复入队。
            infos.forEach { playlistQueueViewModel.addToPlaylist(it) }
            playlistQueueViewModel.playAt(infos[index.coerceIn(0, infos.lastIndex)])
            navController.add(NavRoutes.Player.Player)
        }
    }

    val label = if (source == RecommendSource.DAILY) {
        "每日推荐 · 共 ${items.size} 首"
    } else {
        "私人推荐 · 共 ${items.size} 首"
    }
    val heroTitle = if (source == RecommendSource.DAILY) {
        phaseTitle(payload?.phase)
    } else {
        "为你而选"
    }

    Box(Modifier.fillMaxSize().background(pageBg)) {
        when {
            payload == null -> LoadingState(ink)
            items.isEmpty() -> EmptyState(source, inkSecondary, inkTertiary)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    RecommendHero(
                        modifier = Modifier.fillParentMaxHeight(0.58f),
                        coverUri = seedUri,
                        label = label,
                        title = heroTitle,
                        overview = payload?.overview.orEmpty(),
                        scrim = pageBg,
                        ink = ink,
                        onBack = { navController.removeLastOrNull() },
                        onPlayAll = { playFrom(0) },
                    )
                }
                itemsIndexed(items) { index, item ->
                    RecommendRow(
                        item = item,
                        titleColor = ink,
                        secondaryColor = inkSecondary,
                        tertiaryColor = inkTertiary,
                        thumbBg = thumbBg,
                        onClick = { playFrom(index) },
                    )
                    if (index != items.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 84.dp, end = 16.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(dividerColor)
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RecommendHero(
    modifier: Modifier,
    coverUri: String?,
    label: String,
    title: String,
    overview: String,
    scrim: Color,
    ink: Color,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
) {
    // 与 SubScreen 头部一致：Compact 16 / Medium 32 / Expanded 48
    val horizontalPadding = when (LocalWindowSizeInfo.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 48.dp
        WindowWidthSizeClass.Medium -> 32.dp
        WindowWidthSizeClass.Compact -> 16.dp
    }

    Box(modifier.fillMaxWidth()) {
        // ① 封面全出血
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(scrim))
        }

        // ② 同色系压暗渐变：顶部透出封面，底部实底（= 页面底色）保证文字可读且与列表无缝衔接
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to scrim.copy(alpha = 0.45f),
                        0.72f to scrim.copy(alpha = 0.85f),
                        1f to scrim,
                    )
                )
        )

        // ③ 返回按钮：与其它二级页（SubScreen 头部）同款 —— BackButton + 相同内边距
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = horizontalPadding, top = 16.dp),
        ) {
            BackButton(onClick = onBack)
        }

        // ④ 文案 + 播放全部
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ink)
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scrim,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(ink: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ink)
    }
}

@Composable
private fun EmptyState(source: RecommendSource, textColor: Color, iconColor: Color) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(Res.drawable.music_note_list),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (source == RecommendSource.DAILY) {
                    "今天的推荐还没准备好"
                } else {
                    "还没有足够的数据生成私人推荐"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}

@Composable
private fun RecommendRow(
    item: RecommendItem,
    titleColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    thumbBg: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(thumbBg),
        ) {
            if (item.musicInfo.music.albumArtUri.isNotBlank()) {
                AsyncImage(
                    model = item.musicInfo.music.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.musicInfo.music.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.musicInfo.music.artist,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = tertiaryColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 时段 → 英雄区大标题（表现层映射，放在 shared-ui，避免改动 shared 触发大模块重编译） */
private fun phaseTitle(phase: TimePhase?): String = when (phase) {
    TimePhase.NIGHT -> "深夜"
    TimePhase.MORNING_COMMUTE -> "早高峰"
    TimePhase.WORK -> "工作时段"
    TimePhase.LUNCH -> "午休"
    TimePhase.EVENING_COMMUTE -> "晚高峰"
    TimePhase.EVENING_LEISURE -> "晚间休闲"
    TimePhase.UNKNOWN, null -> "今日精选"
}
