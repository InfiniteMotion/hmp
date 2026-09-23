package com.hearablemusic.player.ui.library.pages
import com.hearablemusic.player.ui.generated.resources.home_explore
import com.hearablemusic.player.ui.generated.resources.home_private_reco
import com.hearablemusic.player.ui.generated.resources.home_recommended
import com.hearablemusic.player.ui.generated.resources.home_today_reco
import com.hearablemusic.player.ui.generated.resources.play_all

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
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.AnniversaryContent
import com.hmp.domain.agent.card.DiscoverContent
import com.hmp.domain.agent.card.ForgottenContent
import com.hmp.domain.agent.card.RadioStatusContent
import com.hmp.domain.agent.card.RecommendContent
import com.hmp.domain.agent.card.RecommendListPayload
import com.hmp.domain.agent.card.RecommendSource
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.MusicRepository
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.components.base.HMPTextField
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.TabScreen
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.magnifyingglass
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.search_placeholder
import com.hearablemusic.player.ui.library.pages.components.FeatureEntryRow
import com.hearablemusic.player.ui.agent.cards.CardStackTuning
import com.hearablemusic.player.ui.agent.cards.HelloCardCoverflow
import com.hearablemusic.player.ui.agent.cards.HelloSlideCardStack
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
    val windowSize = LocalWindowSizeInfo.current
    val isLandscape = windowSize.isLandscape
    // 宽窗右栏"组间间距"分档依据：直接用窗口宽度数值（横屏两栏布局下宽普遍 900–1600dp，
    // 三档 WindowWidthSizeClass 区分度不够，故按 px 无关的 dp 数值分档）。
    val widthDp = windowSize.widthDp

    // 两个推荐入口数据：payload==null → 生成中（置灰）；空 items → 无数据（隐藏）；否则正常。
    val masterAgent: MasterAgent = koinInject()
    val dailyPayload by masterAgent.dailyRecommendList.collectAsState()
    val privatePayload by masterAgent.privateRecommendList.collectAsState()

    // 堆叠卡短按 → 按卡型分流（能播的播、能跳的跳、纯文案卡不响应）
    val musicRepository: MusicRepository = koinInject()
    val cardScope = rememberCoroutineScope()

    // G6：播放某个推荐列表（整组**追加到队列尾部**，播其中第一首）
    fun playRecommend(source: RecommendSource) {
        val payload = when (source) {
            RecommendSource.DAILY -> dailyPayload
            RecommendSource.PRIVATE -> privatePayload
        }
        val ids = payload?.items?.map { it.trackId }.orEmpty()
        if (ids.isEmpty()) return
        cardScope.launch {
            val infos = runCatching { musicRepository.getMusicInfoByIds(ids) }.getOrNull().orEmpty()
            if (infos.isNotEmpty()) {
                // 追加到现有队列尾部（addToPlaylist 内含整队列去重，已存在的不会重复入队），
                // 再从首曲播起 —— 不清空队列。见下方 playIds 的说明。
                infos.forEach { playlistQueueViewModel.addToPlaylist(it) }
                playlistQueueViewModel.playAt(infos.first())
                navController.add(NavRoutes.Player.Player)
            }
        }
    }

    val onSlideCardClick: (SlideCard) -> Unit = { card ->
        // 卡片点播只"追加进队列"、不替换：逐个 addToPlaylist（尾部追加 + 整队列去重）
        // 再用 playAt 定位到目标曲。不可用 addAllToPlaylistInOrder —— 它实际是整条替换并从首曲播起。
        fun playIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            cardScope.launch {
                val infos = runCatching { musicRepository.getMusicInfoByIds(ids) }.getOrNull().orEmpty()
                if (infos.isNotEmpty()) {
                    infos.forEach { playlistQueueViewModel.addToPlaylist(it) }
                    playlistQueueViewModel.playAt(infos.first())
                    navController.add(NavRoutes.Player.Player)
                }
            }
        }
        when (val content = card.content) {
            is RecommendContent -> playIds(listOf(content.trackId))
            is ForgottenContent -> playIds(listOf(content.trackId))
            // 纪念日：PLAYLIST_CREATE 无单曲（trackId=0），其余直接播
            is AnniversaryContent ->
                if (content.trackId > 0L) playIds(listOf(content.trackId))
            // 探索卡：整组入队，从第一首播起
            is DiscoverContent -> playIds(content.trackIds)
            // 正在听卡 / 电台卡：进播放页
            is AnchorContent ->
                navController.add(NavRoutes.Player.Player)
            is RadioStatusContent ->
                navController.add(NavRoutes.Player.Player)
            // GREETING / ENRICH_TRACKING / NARRATIVE：暂不响应
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabScreen(showHeader = false) {
                if (isLandscape) {
                    // Expanded 横向：左栏 Hello 卡堆叠，右栏搜索 + 推荐 + 探索
                    Row(
                        modifier = Modifier.fillMaxSize()
                            // bottom 88dp 为悬浮播放条（MiniPlayerBar）让位，项目既有约定。
                            .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(44.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // 左栏：横屏调参的卡片堆叠（露邻卡的 3D Coverflow 观感）
                        HelloCardCoverflow(
                            modifier = Modifier.weight(0.8f).fillMaxHeight(),
                            tuning = CardStackTuning.Landscape,
                            onCardClick = onSlideCardClick,
                        )

                        // ── 右栏：搜索框 + 推荐 + 探索 ──
                        //
                        // 垂直居中要靠外层 Box：内层 Column 带 verticalScroll，高度约束无界，
                        // 直接加 verticalArrangement = Center 是空操作。
                        //
                        // 组间间距按窗口宽度分档。探索卡改回 1:1 后，其高度 = 卡宽
                        // （900 窗口下 ≈133dp、1104 下 ≈171dp），右栏固定项 344dp，
                        // 故窄窗（900×600 可用 496dp）留给两个组间的余量只有 ~19dp。
                        // 溢出会触发 verticalScroll，而滚动一出现垂直居中即失效，故必须留安全余量。
                        val groupGap = when {
                            widthDp < 1100f -> 6.dp
                            widthDp < 1300f -> 26.dp
                            else -> 46.dp
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                // 搜索框
                                HomeSearchBar(onClick = { navController.add(NavRoutes.Library.Search) })

                                // 组间
                                Spacer(modifier = Modifier.height(groupGap))

                                // 为你推荐
                                Text(
                                    text = stringResource(Res.string.home_recommended),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                // 组内
                                Spacer(modifier = Modifier.height(16.dp))
                                // Row 高度直接决定 RadioCard 的边长（正方 + fillMaxHeight）
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    RadioCard(
                                        modifier = Modifier.fillMaxHeight(),
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxHeight().weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                    ) {
                                        RecommendEntryCard(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            source = RecommendSource.DAILY,
                                            payload = dailyPayload,
                                            onOpen = { navController.add(NavRoutes.Recommend.Daily) },
                                            onPlay = { playRecommend(RecommendSource.DAILY) },
                                        )
                                        RecommendEntryCard(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            source = RecommendSource.PRIVATE,
                                            payload = privatePayload,
                                            onOpen = { navController.add(NavRoutes.Recommend.Private) },
                                            onPlay = { playRecommend(RecommendSource.PRIVATE) },
                                        )
                                    }
                                }

                                // 组间
                                Spacer(modifier = Modifier.height(groupGap))

                                // 探索
                                Text(
                                    text = stringResource(Res.string.home_explore),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                FeatureEntryRow(navController = navController)

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                } else {
                    // Compact / Medium 纵向布局
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        HomeSearchBar(onClick = { navController.add(NavRoutes.Library.Search) })

                        // 竖屏：卡纵向内缩 16dp（横向撑满，与上方搜索栏对齐），卡本身 10:9。
                        // padding 必须在 aspectRatio 之前，否则比例会漂成 1.123 而非 1.111。
                        // 不露邻卡但能无限循环；下方不再补 Spacer，避免与这里的 bottom 16dp 叠加成 32dp。
                        HelloSlideCardStack(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .aspectRatio(10f / 9f),
                            onCardClick = onSlideCardClick,
                        )
                        Text(
                            text = stringResource(Res.string.home_recommended),
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
                                    source = RecommendSource.DAILY,
                                    payload = dailyPayload,
                                    onOpen = { navController.add(NavRoutes.Recommend.Daily) },
                                    onPlay = { playRecommend(RecommendSource.DAILY) },
                                )
                                RecommendEntryCard(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    source = RecommendSource.PRIVATE,
                                    payload = privatePayload,
                                    onOpen = { navController.add(NavRoutes.Recommend.Private) },
                                    onPlay = { playRecommend(RecommendSource.PRIVATE) },
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
    HMPTextField(
        onClick = onClick,
        placeholder = stringResource(Res.string.search_placeholder),
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                painter = painterResource(Res.drawable.magnifyingglass),
                contentDescription = null,
                modifier = Modifier.padding(end = 10.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
    )
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
    source: RecommendSource,
    payload: RecommendListPayload?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    // 无数据（已生成但为空）→ 入口不显示
    if (payload != null && payload.items.isEmpty()) return

    val ready = payload != null && payload.items.isNotEmpty()
    val title = when (source) {
        RecommendSource.DAILY -> stringResource(Res.string.home_today_reco)
        RecommendSource.PRIVATE -> stringResource(Res.string.home_private_reco)
    }

    // 容器对齐 TitleWidget / User·Setting 页卡片：透明底 + outlineVariant 50% 描边 + dimens.corner.md
    HMPCard(
        modifier = modifier
            .clickable(enabled = ready) { onOpen() },
        contentPadding = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // 撑满卡片可用区并垂直居中：卡高由调用方决定（宽窗 70dp / Compact 70dp），
        // 内容只有一行标题 + 圆形按钮，居中后上下留白对称。
        Row(
            modifier = Modifier.fillMaxSize(),
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
                    contentDescription = stringResource(Res.string.play_all),
                )
            }
        }
    }
}
