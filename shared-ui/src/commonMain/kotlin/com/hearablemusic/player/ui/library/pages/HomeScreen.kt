package com.hearablemusic.player.ui.library.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hmp.domain.agent.sub.SlideCard
import com.hmp.domain.music.MusicRepository
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.TabScreen
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.search_placeholder
import com.hearablemusic.player.ui.library.pages.components.FeatureEntryRow
import com.hearablemusic.player.ui.library.pages.components.HelloSlideCardStack
import com.hearablemusic.player.ui.library.pages.components.RadioCard
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.navigation.Routes as NavRoutes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>
) {
    val isLandscape = LocalWindowSizeInfo.current.isLandscape

    // ── G6：两个推荐入口数据（agent 生成的每日 / 私人推荐列表） ──
    // payload==null → 生成中（入口置灰）；payload 空 items → 无数据（入口隐藏）；否则正常。
    val masterAgent: com.hmp.domain.agent.runtime.MasterAgent = koinInject()
    // MasterAgent 持有的固定转发流（内部镜像 Hello 子代理）——首帧即可拿到，
    // 不会像透传子代理那样缓存到 null / 旧实例。
    val dailyPayload by masterAgent.dailyRecommendList.collectAsState()
    val privatePayload by masterAgent.privateRecommendList.collectAsState()

    // ── G2：堆叠卡短按 → 直接接入播放 ──
    // 按卡型分流：能播的播、能跳的跳、纯文案卡不响应。
    // 长按事件本次不做（骨架保留）。叙事卡（NARRATIVE）点击落点暂空，待 P5 报告页。
    // 卡片 content 只带 trackId，需按 id 查回 MusicInfo 才能入队播放（playWith 要 MusicInfo）。
    val musicRepository: MusicRepository = koinInject()
    val cardScope = rememberCoroutineScope()

    // G6：播放某个推荐列表（整组入队，从第一首播起）
    fun playRecommend(source: com.hmp.domain.agent.sub.RecommendSource) {
        val payload = when (source) {
            com.hmp.domain.agent.sub.RecommendSource.DAILY -> dailyPayload
            com.hmp.domain.agent.sub.RecommendSource.PRIVATE -> privatePayload
        }
        val ids = payload?.items?.map { it.trackId }.orEmpty()
        if (ids.isEmpty()) return
        cardScope.launch {
            val infos = runCatching { musicRepository.getMusicInfoByIds(ids) }.getOrNull().orEmpty()
            if (infos.isNotEmpty()) {
                playlistQueueViewModel.clearPlaylist()
                playlistQueueViewModel.addAllToPlaylistInOrder(infos)
                playlistQueueViewModel.playWith(infos.first())
                navController.add(NavRoutes.Player.Player)
            }
        }
    }

    val onSlideCardClick: (SlideCard) -> Unit = { card ->
        // 抽出「按 id 集合播放」的公共路径：查 MusicInfo → 清队 → 全量入队 → 从首曲播起 → 进播放页
        fun playIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            cardScope.launch {
                val infos = runCatching { musicRepository.getMusicInfoByIds(ids) }.getOrNull().orEmpty()
                if (infos.isNotEmpty()) {
                    playlistQueueViewModel.clearPlaylist()
                    playlistQueueViewModel.addAllToPlaylistInOrder(infos)
                    playlistQueueViewModel.playWith(infos.first())
                    navController.add(NavRoutes.Player.Player)
                }
            }
        }
        when (val content = card.content) {
            is com.hmp.domain.agent.sub.RecommendContent -> playIds(listOf(content.trackId))
            is com.hmp.domain.agent.sub.ForgottenContent -> playIds(listOf(content.trackId))
            // 纪念日：PLAYLIST_CREATE 无单曲（trackId=0），其余直接播
            is com.hmp.domain.agent.sub.AnniversaryContent ->
                if (content.trackId > 0L) playIds(listOf(content.trackId))
            // 探索卡：整组入队，从第一首播起
            is com.hmp.domain.agent.sub.DiscoverContent -> playIds(content.trackIds)
            // 正在听卡 / 电台卡：进播放页
            is com.hmp.domain.agent.sub.AnchorContent ->
                navController.add(NavRoutes.Player.Player)
            is com.hmp.domain.agent.sub.RadioStatusContent ->
                navController.add(NavRoutes.Player.Player)
            // GREETING / ENRICH_TRACKING / NARRATIVE：暂不响应
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabScreen(showHeader = false) {
                if (isLandscape) {
                    // ═══════════════════════════════════════
                    // Expanded 横向布局 — 左右两栏
                    // 左栏：HelloSlideCardStack（独立展示区）
                    // 右栏：搜索框 + 区域② + 区域③
                    // ═══════════════════════════════════════
                    Row(
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // ── 左栏：HelloSlideCardStack ──
                        HelloSlideCardStack(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxHeight().padding(bottom = 80.dp),
                            onCardClick = onSlideCardClick,
                        )

                        // ── 右栏：搜索框 + 推荐 + 探索 ──
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // 搜索框
                            HomeSearchBar(onClick = { navController.add(NavRoutes.Library.Search) })
                            Spacer(modifier = Modifier.height(32.dp))

                            // 区域② 为你推荐
                            Text(
                                text = "为你推荐",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(320.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                RadioCard(
                                    modifier = Modifier.fillMaxHeight(),
                                )
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    RecommendEntryCard(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        source = com.hmp.domain.agent.sub.RecommendSource.DAILY,
                                        payload = dailyPayload,
                                        onOpen = { navController.add(NavRoutes.Recommend.Daily) },
                                        onPlay = { playRecommend(com.hmp.domain.agent.sub.RecommendSource.DAILY) },
                                    )
                                    RecommendEntryCard(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        source = com.hmp.domain.agent.sub.RecommendSource.PRIVATE,
                                        payload = privatePayload,
                                        onOpen = { navController.add(NavRoutes.Recommend.Private) },
                                        onPlay = { playRecommend(com.hmp.domain.agent.sub.RecommendSource.PRIVATE) },
                                    )
                                }
                            }

                            // 区域③ 探索
                            Text(
                                text = "探索",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            FeatureEntryRow(navController = navController)
                        }
                    }
                } else {
                    // ═══════════════════════════════════════
                    // Compact / Medium 纵向布局
                    // ① HelloSlideCardStack (16:9)
                    // ② Row { RadioCard(1:1); Column { 今日推荐; 最近收藏 } }
                    // ③ FeatureEntryRow
                    // ═══════════════════════════════════════
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        // 搜索框
                        HomeSearchBar(onClick = { navController.add(NavRoutes.Library.Search) })

                        // 区域①
                        HelloSlideCardStack(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(10f / 9f),
                            onCardClick = onSlideCardClick,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // 区域② 快速播放
                        Text(
                            text = "为你推荐",
                            modifier = Modifier,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            RadioCard(
                                modifier = Modifier.fillMaxHeight(),
                            )
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                RecommendEntryCard(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    source = com.hmp.domain.agent.sub.RecommendSource.DAILY,
                                    payload = dailyPayload,
                                    onOpen = { navController.add(NavRoutes.Recommend.Daily) },
                                    onPlay = { playRecommend(com.hmp.domain.agent.sub.RecommendSource.DAILY) },
                                )
                                RecommendEntryCard(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    source = com.hmp.domain.agent.sub.RecommendSource.PRIVATE,
                                    payload = privatePayload,
                                    onOpen = { navController.add(NavRoutes.Recommend.Private) },
                                    onPlay = { playRecommend(com.hmp.domain.agent.sub.RecommendSource.PRIVATE) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        FeatureEntryRow(navController = navController)
                    }
                }
            }
        }
    }
}

/**
 * 首页搜索框：surfaceVariant 圆角容器 + 🔍 图标 + placeholder 文字，点击跳转 SearchScreen。
 * 样式与 ChatScreen 输入框保持一致（同 surfaceVariant 背景 + RoundedCornerShape）。
 */
@Composable
private fun HomeSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.magnifyingglass),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.search_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * G6：区域②右侧推荐入口卡（每日 / 私人）。
 *
 * 容器对齐 TitleWidget / User·Setting 页卡片：透明底 + `outlineVariant` 50% 描边 + `dimens.corner.md` 圆角。
 * 内容 = 标题 + 尾部播放按钮（无前置图标、无副文案、无指示箭头 —— 整卡可点，箭头冗余）。
 *
 * 三态：payload==null → 生成中（标题与播放按钮置灰，不可点）；已生成但空 → 不显示；否则正常。
 * 语义：点卡片 = 进二级页；播放按钮 = 直接播放整组。
 */
@Composable
private fun RecommendEntryCard(
    modifier: Modifier,
    source: com.hmp.domain.agent.sub.RecommendSource,
    payload: com.hmp.domain.agent.sub.RecommendListPayload?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    // 无数据（已生成但为空）→ 入口不显示
    if (payload != null && payload.items.isEmpty()) return

    val ready = payload != null && payload.items.isNotEmpty()
    val title = when (source) {
        com.hmp.domain.agent.sub.RecommendSource.DAILY -> "今日推荐"
        com.hmp.domain.agent.sub.RecommendSource.PRIVATE -> "私人推荐"
    }

    // 容器对齐 TitleWidget / User·Setting 页卡片：透明底 + outlineVariant 50% 描边 + dimens.corner.md
    val dimens = LocalHMPDimens.current
    val corner = RoundedCornerShape(dimens.corner.md)

    Card(
        // 入口卡语义：点卡片 = 进二级页；右侧播放按钮 = 直接播放
        modifier = modifier
            .clip(corner)
            .clickable(enabled = ready) { onOpen() },
        shape = corner,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (ready) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = { if (ready) onPlay() },
                enabled = ready,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.play_fill),
                    contentDescription = "播放全部",
                )
            }
        }
    }
}
