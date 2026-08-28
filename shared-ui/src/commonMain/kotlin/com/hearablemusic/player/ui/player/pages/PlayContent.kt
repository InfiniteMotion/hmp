package com.hearablemusic.player.ui.player.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.components.AgentQuickSheet
import com.hearablemusic.player.ui.common.components.base.GeneratePlaylistComboButtons
import com.hearablemusic.player.ui.common.dialogs.TimerDialog
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.library.pages.components.musiclist.CurrentPlayingConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hearablemusic.player.ui.library.viewmodel.SongDetailData
import com.hearablemusic.player.ui.library.viewmodel.SongDetailViewModel
import com.hearablemusic.player.ui.player.components.DotPager
import com.hearablemusic.player.ui.common.viewmodel.PaletteColors
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.album_label
import com.hearablemusic.player.ui.generated.resources.artist_introduction
import com.hearablemusic.player.ui.generated.resources.artist_label
import com.hearablemusic.player.ui.generated.resources.backward_end_fill
import com.hearablemusic.player.ui.generated.resources.creative_background
import com.hearablemusic.player.ui.generated.resources.favorite
import com.hearablemusic.player.ui.generated.resources.forward_end_fill
import com.hearablemusic.player.ui.generated.resources.agent_capsule_desc
import com.hearablemusic.player.ui.generated.resources.heart
import com.hearablemusic.player.ui.generated.resources.heart_fill
import com.hearablemusic.player.ui.generated.resources.identify_song
import com.hearablemusic.player.ui.generated.resources.labels
import com.hearablemusic.player.ui.generated.resources.liked_status
import com.hearablemusic.player.ui.generated.resources.lyrics
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.music_title_placeholder
import com.hearablemusic.player.ui.generated.resources.next
import com.hearablemusic.player.ui.generated.resources.pause
import com.hearablemusic.player.ui.generated.resources.personal_stats
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.play_pause
import com.hearablemusic.player.ui.generated.resources.playback_mode
import com.hearablemusic.player.ui.generated.resources.playlist
import com.hearablemusic.player.ui.generated.resources.playlist_count
import com.hearablemusic.player.ui.generated.resources.recommendation_mode
import com.hearablemusic.player.ui.generated.resources.scope
import com.hearablemusic.player.ui.generated.resources.more
import com.hearablemusic.player.ui.generated.resources.previous
import com.hearablemusic.player.ui.generated.resources.related_info
import com.hearablemusic.player.ui.generated.resources.repeat
import com.hearablemusic.player.ui.generated.resources.repeat_1
import com.hearablemusic.player.ui.generated.resources.shuffle
import com.hearablemusic.player.ui.generated.resources.skipped_count
import com.hearablemusic.player.ui.generated.resources.sleep_timer
import com.hearablemusic.player.ui.generated.resources.song_achievements
import com.hearablemusic.player.ui.generated.resources.song_description
import com.hearablemusic.player.ui.generated.resources.song_detail_tab_intro
import com.hearablemusic.player.ui.generated.resources.sort_play_count
import com.hearablemusic.player.ui.generated.resources.chevron_up_circle
import com.hearablemusic.player.ui.generated.resources.timer
import com.hearablemusic.player.ui.generated.resources.user_rating
import com.hearablemusic.player.ui.player.viewmodel.LyricsSettingsState
import com.hearablemusic.player.ui.player.viewmodel.PlayerCallbacks
import com.hearablemusic.player.ui.player.viewmodel.PlayerUiState
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.AlgorithmType
import com.hmp.domain.playlist.ExtensionConfig
import com.hmp.domain.playlist.WeightTemplate
import kotlin.math.abs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// 格式化时间为 mm:ss（commonMain 无 String.format，用 padStart 等价实现）
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun PlayContent(
    playerUiState: PlayerUiState,
    lyricsSettingsState: LyricsSettingsState,
    paletteColors: PaletteColors? = null,
    callbacks: PlayerCallbacks,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()

    var showTimerDialog by remember { mutableStateOf(false) }
    var playlistExpanded by remember { mutableStateOf(false) }
    // M1 播放页锚点：轻量浮层（无底栏页面贴屏底，设计总纲 3.3）
    var quickSheetVisible by remember { mutableStateOf(false) }

    // 解构状态以便在代码中使用
    val musicInfo = playerUiState.musicInfo
    val isPlaying = playerUiState.isPlaying
    val currentPosition = playerUiState.currentPosition
    val duration = playerUiState.duration
    val playbackMode = playerUiState.playbackMode
    val remainingTime = playerUiState.remainingTime
    val isLiked = playerUiState.isLiked
    val lyrics = playerUiState.lyrics
    val playlist = playerUiState.playlist
    val currentIndex = playerUiState.currentIndex
    val defaultAlgorithmType = playerUiState.defaultAlgorithmType
    val defaultTemplate = playerUiState.defaultTemplate

    val lyricsOriginalTextSize = lyricsSettingsState.lyricsOriginalTextSize
    val lyricsTranslatedTextSize = lyricsSettingsState.lyricsTranslatedTextSize
    val lyricsCurrentTimeTextSize = lyricsSettingsState.lyricsCurrentTimeTextSize
    val lyricsLineSpacing = lyricsSettingsState.lyricsLineSpacing
    val lyricsDisplayMode = lyricsSettingsState.lyricsDisplayMode
    val lyricsAlignment = lyricsSettingsState.lyricsAlignment
    val lyricsKaraokeEnabled = lyricsSettingsState.lyricsKaraokeEnabled

    val songDetailViewModel: SongDetailViewModel = koinViewModel()
    val songDetailState by songDetailViewModel.uiState.collectAsState()
    LaunchedEffect(musicInfo?.music?.id) {
        musicInfo?.music?.id?.let { songDetailViewModel.loadSongDetail(it) }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val sizeClass = LocalWindowSizeInfo.current.widthSizeClass
        val windowSizeInfo = LocalWindowSizeInfo.current
        val isPhoneLandscape = windowSizeInfo.isPhoneLandscape
        val isLandscape = windowSizeInfo.isLandscape
        val screenHeight = maxHeight
        val containerWidth = maxWidth
        val scrollState = rememberScrollState()
        val coverSize = when (sizeClass) {
            WindowWidthSizeClass.Compact -> minOf(containerWidth * 0.65f, 280.dp)
            WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded ->
                minOf(containerWidth * 0.35f, 260.dp.coerceAtMost(maxHeight * 0.5f))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
        ) {
            AgentQuickSheet(
                visible = quickSheetVisible,
                onSubmit = { input ->
                    // M5 接会话（浮层与对话页同 session_id）；M1 为锚点骨架占位
                    println("[M1] player quick sheet: $input")
                    quickSheetVisible = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            if (isPhoneLandscape) {
                // 手机横屏：左栏封面，右栏 tabs
                var selectedTab by remember { mutableStateOf("controls") }
                var isProgrammaticScroll by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxSize().displayCutoutPadding()) {
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    Box(Modifier.weight(0.35f).fillMaxHeight()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragOffset > 100f) callbacks.onPrevious()
                                    else if (dragOffset < -100f) callbacks.onNext()
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                                onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                            )
                        }, contentAlignment = Alignment.Center) {
                        AlbumCover(musicInfo?.music?.albumArtUri, minOf(containerWidth * 0.5f, 240.dp), 16.dp, 8.dp,
                            modifier = Modifier.graphicsLayer { translationX = dragOffset })
                    }
                    Column(Modifier.weight(0.65f).fillMaxHeight().padding(start = 16.dp, end = 24.dp).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val tabs = listOf("controls", "lyrics", "info", "generate", "playlist")
                        val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
                        LaunchedEffect(selectedTab) {
                            val idx = tabs.indexOf(selectedTab)
                            if (idx >= 0 && idx != pagerState.currentPage) {
                                isProgrammaticScroll = true
                                pagerState.animateScrollToPage(idx)
                                isProgrammaticScroll = false
                            }
                        }
                        LaunchedEffect(pagerState.currentPage) {
                            if (!isProgrammaticScroll) selectedTab = tabs[pagerState.currentPage]
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            HorizontalPager(state = pagerState) { page ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when (tabs[page]) {
                                    "controls" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        MusicInfo(musicInfo?.music, { callbacks.onArtistClick(it) }, centerAlign = true)
                                        Spacer(Modifier.height(24.dp))
                                        SeekBar(currentPosition, duration) { callbacks.onSeek(it) }
                                        PlaybackControlsButtons(
                                            isPlaying = isPlaying,
                                            playbackMode = playbackMode,
                                            isLike = isLiked,
                                            remainingTime = remainingTime,
                                            playlistExpanded = selectedTab == "playlist",
                                            onPlayPause = { haptic.performClick(); callbacks.onPlayPause() },
                                            onNext = { haptic.performClick(); callbacks.onNext() },
                                            onPrevious = { haptic.performClick(); callbacks.onPrevious() },
                                            onPlaybackModeChange = { haptic.performContextClick(); callbacks.onPlaybackModeChange() },
                                            onFavorite = { haptic.performConfirm(); callbacks.onFavorite() },
                                            onChatClick = { quickSheetVisible = true },
                                            onPlaylistToggle = { selectedTab = if (selectedTab == "playlist") "controls" else "playlist" },
                                            onTimerClick = { callbacks.onShowTimerDialog() },
                                            onHeartMode = { haptic.performConfirm(); callbacks.onHeartMode() },
                                        )
                                    }
                                    "playlist" -> PlaylistTabContent(playlist, currentIndex ?: 0, onPlayItem = { callbacks.onPlayItem(it) }, onMoveToTop = { callbacks.onMoveToTop(it) }, onRemoveFromPlaylist = { callbacks.onRemoveFromPlaylist(it) }, onClearPlaylist = { callbacks.onClearPlaylist() })
                                    "info" -> SongDetailInfoTab(songDetailState, musicInfo?.extra, musicInfo?.userInfo)
                                    "generate" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        GeneratePlaylistComboButtons(musicInfo?.music?.id ?: 0L, defaultAlgorithmType, defaultTemplate, onGeneratePlaylist = { callbacks.onGeneratePlaylist(it) }, onSaveDefaultConfig = { a, b, c -> callbacks.onSaveDefaultConfig(a, b, c) })
                                    }
                                    else -> AdvancedLyrics(Modifier.fillMaxSize().padding(vertical = 16.dp), lyrics, currentPosition, { callbacks.onSeek(it) }, lyricsOriginalTextSize, lyricsTranslatedTextSize, lyricsCurrentTimeTextSize, lyricsLineSpacing, totalDurationMs = duration, karaokeEnabled = lyricsKaraokeEnabled, isPlaying = isPlaying, paletteColors = paletteColors, displayMode = lyricsDisplayMode, alignment = lyricsAlignment)
                                }
                                } // center Box
                            }
                        }
                    }
                }
            } else if (isLandscape) {
                // 平板横屏：左控制 + 右 3tab
                var selectedTab by remember { mutableStateOf("lyrics") }
                var isProgrammaticScroll by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.weight(0.4f).fillMaxHeight()) {
                        Spacer(Modifier.height(12.dp))
                        PlayerHeader({ callbacks.onBackClick() })
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            MusicInfo(musicInfo?.music, { callbacks.onArtistClick(it) }, centerAlign = true)
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AlbumCover(musicInfo?.music?.albumArtUri, 280.dp, 16.dp, 8.dp) }
                            Spacer(Modifier.height(16.dp))
                            SeekBar(currentPosition, duration) { callbacks.onSeek(it) }
                            PlaybackControlsButtons(
                                            isPlaying = isPlaying,
                                            playbackMode = playbackMode,
                                            isLike = isLiked,
                                            remainingTime = remainingTime,
                                            playlistExpanded = selectedTab == "playlist",
                                            onPlayPause = { haptic.performClick(); callbacks.onPlayPause() },
                                            onNext = { haptic.performClick(); callbacks.onNext() },
                                            onPrevious = { haptic.performClick(); callbacks.onPrevious() },
                                            onPlaybackModeChange = { haptic.performContextClick(); callbacks.onPlaybackModeChange() },
                                            onFavorite = { haptic.performConfirm(); callbacks.onFavorite() },
                                            onChatClick = { quickSheetVisible = true },
                                            onPlaylistToggle = { selectedTab = if (selectedTab == "playlist") "lyrics" else "playlist" },
                                            onTimerClick = { callbacks.onShowTimerDialog() },
                                            onHeartMode = { haptic.performConfirm(); callbacks.onHeartMode() },
                                        )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Column(Modifier.weight(0.6f).fillMaxHeight().padding(start = 16.dp, end = 24.dp).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val tabs = listOf("lyrics", "info", "playlist")
                        val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
                        LaunchedEffect(selectedTab) {
                            val idx = tabs.indexOf(selectedTab)
                            if (idx >= 0 && idx != pagerState.currentPage) {
                                isProgrammaticScroll = true
                                pagerState.animateScrollToPage(idx)
                                isProgrammaticScroll = false
                            }
                        }
                        LaunchedEffect(pagerState.currentPage) {
                            if (!isProgrammaticScroll) selectedTab = tabs[pagerState.currentPage]
                        }
                        Box(Modifier.weight(1f)) {
                            HorizontalPager(state = pagerState) { page ->
                                when (tabs[page]) {
                                    "playlist" -> PlaylistTabContent(playlist, currentIndex ?: 0, musicInfo?.music?.id, defaultAlgorithmType, defaultTemplate, { callbacks.onGeneratePlaylist(it) }, { a, b, c -> callbacks.onSaveDefaultConfig(a, b, c) }, { callbacks.onPlayItem(it) }, { callbacks.onMoveToTop(it) }, { callbacks.onRemoveFromPlaylist(it) }, { callbacks.onClearPlaylist() })
                                    "info" -> SongDetailInfoTab(songDetailState, musicInfo?.extra, musicInfo?.userInfo)
                                    else -> AdvancedLyrics(Modifier.fillMaxSize().padding(vertical = 16.dp), lyrics, currentPosition, { callbacks.onSeek(it) }, lyricsOriginalTextSize, lyricsTranslatedTextSize, lyricsCurrentTimeTextSize, lyricsLineSpacing, totalDurationMs = duration, karaokeEnabled = lyricsKaraokeEnabled, isPlaying = isPlaying, paletteColors = paletteColors, displayMode = lyricsDisplayMode, alignment = lyricsAlignment)
                                }
                            }
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { PlayerTabBar(selectedTab, { selectedTab = it }) }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 播放器主界面容器：强制填满一屏高度
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .weight(1f) // 使顶部区域占据剩余空间
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerHeader({ callbacks.onBackClick() })
                        Spacer(modifier = Modifier.height(24.dp))
                        MusicInfo(musicInfo?.music, { callbacks.onArtistClick(it) })
                        Spacer(modifier = Modifier.height(16.dp))
                        // 封面区域：使用 weight(1f) 实现弹性缩放
                        MusicInfoExtra(
                            musicInfo = musicInfo,
                            lyrics = lyrics,
                            currentPosition = currentPosition,
                            defaultAlgorithmType = defaultAlgorithmType,
                            defaultTemplate = defaultTemplate,
                            originalTextSize = lyricsOriginalTextSize,
                            translatedTextSize = lyricsTranslatedTextSize,
                            currentTimeTextSize = lyricsCurrentTimeTextSize,
                              lineSpacing = lyricsLineSpacing,
                              displayMode = lyricsDisplayMode,
                              alignment = lyricsAlignment,
                              totalDurationMs = duration,
                              karaokeEnabled = lyricsKaraokeEnabled,
                              paletteColors = paletteColors,
                              isPlaying = isPlaying,
                              onSeek = { callbacks.onSeek(it) },
                            onGeneratePlaylist = { callbacks.onGeneratePlaylist(it) },
                            onSaveDefaultConfig = { a, b, c -> callbacks.onSaveDefaultConfig(a, b, c) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SeekBar(
                            currentPosition = currentPosition,
                            duration = duration,
                            onSeek = { callbacks.onSeek(it) }
                        )
                        // 播放控制按钮区
                        PlaybackControlsButtons(
                            isPlaying = isPlaying,
                            playbackMode = playbackMode,
                            isLike = isLiked,
                            remainingTime = remainingTime,
                            playlistExpanded = playlistExpanded,
                            onPlayPause = {
                                haptic.performClick()
                                callbacks.onPlayPause()
                            },
                            onNext = {
                                haptic.performClick()
                                callbacks.onNext()
                            },
                            onPrevious = {
                                haptic.performClick()
                                callbacks.onPrevious()
                            },
                            onPlaybackModeChange = {
                                haptic.performContextClick()
                                callbacks.onPlaybackModeChange()
                            },
                            onFavorite = {
                                haptic.performConfirm()
                                callbacks.onFavorite()
                            },
                            onChatClick = { quickSheetVisible = true },
                            onTimerClick = { callbacks.onShowTimerDialog() },
                            onHeartMode = {
                                haptic.performConfirm()
                                callbacks.onHeartMode()
                            },
                            onPlaylistToggle = {
                                playlistExpanded = !playlistExpanded
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 播放列表区域：位于主界面下方
                PlaylistArea(
                    expanded = playlistExpanded,
                    playlist = playlist,
                    currentIndex = currentIndex,
                    scrollState = scrollState,
                    onClearPlaylist = { callbacks.onClearPlaylist() },
                    onPlayItem = { callbacks.onPlayItem(it) },
                    onMoveToTop = { callbacks.onMoveToTop(it) },
                    onRemoveFromPlaylist = { callbacks.onRemoveFromPlaylist(it) }
                )
            }
            } // else
        }
        if (showTimerDialog) {
            TimerDialog(
                onDismiss = { showTimerDialog = false },
                onConfirm = { minutes: Int ->
                    if (minutes == 0) {
                        callbacks.onCancelTimer()
                    } else {
                        callbacks.onTimerClick(minutes)
                    }
                    showTimerDialog = false
                },
                hazeState = hazeState
            )
        }
    }
}

// 歌曲标题、艺术家、专辑信息
@Composable
fun MusicInfo(
    music: Music?,
    onArtistClick: (String) -> Unit,
    centerAlign: Boolean = false
) {
    Column(
        horizontalAlignment = if (centerAlign) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        val artistLabel = stringResource(Res.string.artist_label)
        Text(
            text = music?.title ?: stringResource(Res.string.music_title_placeholder),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = music?.artist ?: artistLabel,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable {
                onArtistClick(music?.artist ?: artistLabel)
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = music?.album ?: stringResource(Res.string.album_label),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 标签，专辑封面，歌词
@Composable
fun MusicInfoExtra(
    modifier: Modifier = Modifier,
    musicInfo: MusicInfo?,
    lyrics: String?,
    currentPosition: Long,
    defaultAlgorithmType: AlgorithmType?,
    defaultTemplate: WeightTemplate?,
    originalTextSize: Int = 14,
    translatedTextSize: Int = 14,
    currentTimeTextSize: Int = 16,
    lineSpacing: Int = 6,
    displayMode: DisplayMode = DisplayMode.DUAL,
    alignment: LyricsAlignment = LyricsAlignment.CENTER,
    totalDurationMs: Long = 0L,
    karaokeEnabled: Boolean = true,
    paletteColors: PaletteColors? = null,
    isPlaying: Boolean = true,
    onSeek: (Long) -> Unit,
    onGeneratePlaylist: (Long) -> Unit,
    onSaveDefaultConfig: (AlgorithmType, WeightTemplate, ExtensionConfig) -> Unit
) {
    val contents = listOf<@Composable () -> Unit>(
        {
            Column(
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (musicInfo?.extra != null) {
                    TechnicalInfoCard(musicInfo.extra, modifier = Modifier.padding(16.dp))
                }
                if (musicInfo?.extra?.isGetExtraInfo == true) {
                    GeneratePlaylistComboButtons(
                        musicInfo.music.id,
                        defaultAlgorithmType,
                        defaultTemplate,
                        onGeneratePlaylist,
                        onSaveDefaultConfig
                    )
                }
            }
        },
        {
            AlbumCover(
                musicInfo?.music?.albumArtUri,
                300.dp,
                20.dp,
                10.dp
            )
        },
        {
            AdvancedLyrics(
                modifier = Modifier.padding(vertical = 16.dp),
                lyrics = lyrics,
                currentPosition = currentPosition,
                onSeek = onSeek,
                originalTextSize = originalTextSize,
                translatedTextSize = translatedTextSize,
                currentTimeTextSize = currentTimeTextSize,
                lineSpacing = lineSpacing,
                totalDurationMs = totalDurationMs,
                karaokeEnabled = karaokeEnabled,
                paletteColors = paletteColors,
                isPlaying = isPlaying,
                displayMode = displayMode,
                alignment = alignment
            )
        }
    )
    DotPager(
        modifier = modifier.fillMaxWidth(),
        pageContent = contents,
        initialPage = 1
    )
}

// 音乐进度条和时间显示
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    // 监听位置变化
    LaunchedEffect(currentPosition) {
        if (!isSeeking && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration
        }
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                isSeeking = true // 用户开始拖动，设置为true
                haptic.performDragStart()
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                haptic.performGestureEnd()
                val seekPosition = (sliderPosition * duration).toLong()
                onSeek(seekPosition)
                isSeeking = false // 拖动结束，设置为false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            enabled = true,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                    drawStopIndicator = null
                )
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = Color.Transparent,
                        )
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 播放控制按钮（上一首、播放/暂停、下一首 + 副行五键）
// 设计总纲 2.5 重排：副行 = 播放模式 · 收藏 · 对话 · 播放列表 · 更多；
// 心动模式与睡眠定时收入「更多」菜单（定时激活时菜单项显示倒计时、更多按钮带角标）
@Composable
fun PlaybackControlsButtons(
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    isLike: Boolean,
    remainingTime: Long?,
    playlistExpanded: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlaybackModeChange: () -> Unit,
    onFavorite: () -> Unit,
    onChatClick: () -> Unit,
    onPlaylistToggle: () -> Unit,
    onTimerClick: () -> Unit,
    onHeartMode: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 播放控制按钮区域
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.backward_end_fill),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.previous),
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) Res.drawable.pause else Res.drawable.play_fill),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.play_pause),
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.forward_end_fill),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.next),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 副行五键：播放模式 · 收藏 · 对话 · 播放列表 · 更多
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlaybackModeChange) {
                Icon(
                    painter = painterResource(
                        when (playbackMode) {
                            PlaybackMode.SHUFFLE -> Res.drawable.shuffle
                            PlaybackMode.REPEAT_ONE -> Res.drawable.repeat_1
                            PlaybackMode.SEQUENTIAL -> Res.drawable.repeat
                        }
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.playback_mode),
                )
            }

            // 收藏态由外部参数单源驱动（review 2026-08-28：移除本地乐观翻转双源真值，状态回传即时反映）
            IconButton(onClick = onFavorite) {
                Icon(
                    painter = painterResource(if (isLike) Res.drawable.heart_fill else Res.drawable.heart),
                    contentDescription = stringResource(Res.string.favorite),
                    tint = if (isLike) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }

            // 对话：唤起轻量浮层（M1 播放页锚点；浮层实例由 PlayerScreen 层持有）
            IconButton(
                onClick = {
                    haptic.performClick()
                    onChatClick()
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.scope),
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.agent_capsule_desc),
                )
            }

            // 播放列表按钮
            IconButton(
                onClick = {
                    haptic.performClick()
                    onPlaylistToggle()
                }
            ) {
                Icon(
                    tint = if (playlistExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    painter = painterResource(
                        if (playlistExpanded) Res.drawable.chevron_up_circle else Res.drawable.music_note_list
                    ),
                    contentDescription = stringResource(Res.string.playlist),
                )
            }

            // 更多：心动模式 + 睡眠定时（定时激活时菜单项显示倒计时、按钮带角标）
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        painter = painterResource(Res.drawable.more),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(Res.string.recommendation_mode),
                    )
                }
                if (remainingTime != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.recommendation_mode)) },
                        onClick = {
                            showMoreMenu = false
                            onHeartMode()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.identify_song),
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (remainingTime != null) {
                                    formatTime(remainingTime)
                                } else {
                                    stringResource(Res.string.sleep_timer)
                                }
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onTimerClick()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.timer),
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTabContent(
    playlist: List<MusicInfo>, currentIndex: Int,
    seedMusicId: Long? = null, defaultAlgorithmType: AlgorithmType? = null, defaultTemplate: WeightTemplate? = null,
    onGeneratePlaylist: ((Long) -> Unit)? = null,
    onSaveDefaultConfig: ((AlgorithmType, WeightTemplate, ExtensionConfig) -> Unit)? = null,
    onPlayItem: (MusicInfo) -> Unit, onMoveToTop: (MusicInfo) -> Unit,
    onRemoveFromPlaylist: (MusicInfo) -> Unit, onClearPlaylist: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        if (seedMusicId != null && onGeneratePlaylist != null) {
            GeneratePlaylistComboButtons(seedMusicId, defaultAlgorithmType, defaultTemplate, onGeneratePlaylist, onSaveDefaultConfig ?: { _, _, _ -> })
            Spacer(Modifier.height(16.dp))
        }
        Surface(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)), color = Transparent) {
            if (playlist.isNotEmpty()) {
                val callbacks = object : MusicListCallbacksAdapter() {
                    override fun onItemClick(musicInfo: MusicInfo, index: Int) { haptic.performClick(); onPlayItem(musicInfo) }
                    override fun onRemoveFromPlaylist(musicInfo: MusicInfo) { onRemoveFromPlaylist(musicInfo) }
                }
                MusicList(musicInfoList = playlist, config = defaultMusicListConfig(callbacks).copy(header = HeaderConfig.None, item = ItemConfig(showIndex = true, variant = ItemVariant.Full, fullOptions = FullItemOptions(showPinButton = true, showRemoveButton = true, showMenuButton = false)), edit = EditConfig(enabled = false), currentPlaying = CurrentPlayingConfig(index = currentIndex, autoScrollToCurrent = true)), modifier = Modifier.fillMaxSize(), isPlaying = false)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongDetailInfoTab(songDetailState: UiState<SongDetailData>, musicExtra: com.hmp.domain.music.MusicExtra?, userInfo: com.hmp.domain.music.UserInfo?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TechnicalInfoCard(extra = musicExtra, modifier = Modifier.fillMaxWidth())
        if (songDetailState is UiState.Success) {
            val data = songDetailState.data; val dailyInfo = data.dailyMusicInfo
            if (dailyInfo != null && (dailyInfo.backgroundIntroduce.isNotBlank() && dailyInfo.backgroundIntroduce != "None" || dailyInfo.description.isNotBlank() && dailyInfo.description != "None" || dailyInfo.singerIntroduce.isNotBlank() && dailyInfo.singerIntroduce != "None" || dailyInfo.rewards.isNotBlank() && dailyInfo.rewards != "None")) {
                InfoCard(stringResource(Res.string.related_info)) {
                    if (dailyInfo.backgroundIntroduce.isNotBlank() && dailyInfo.backgroundIntroduce != "None") InfoRow(stringResource(Res.string.creative_background), dailyInfo.backgroundIntroduce)
                    if (dailyInfo.description.isNotBlank() && dailyInfo.description != "None") InfoRow(stringResource(Res.string.song_description), dailyInfo.description)
                    if (dailyInfo.singerIntroduce.isNotBlank() && dailyInfo.singerIntroduce != "None") InfoRow(stringResource(Res.string.artist_introduction), dailyInfo.singerIntroduce)
                    if (dailyInfo.rewards.isNotBlank() && dailyInfo.rewards != "None") InfoRow(stringResource(Res.string.song_achievements), dailyInfo.rewards)
                }
            }
            if (userInfo != null) {
                InfoCard(title = stringResource(Res.string.personal_stats)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCell(stringResource(Res.string.sort_play_count), (userInfo.playCount ?: 0).toString(), Modifier.weight(1f)); StatCell(stringResource(Res.string.skipped_count), (userInfo.skippedCount ?: 0).toString(), Modifier.weight(1f)); StatCell(stringResource(Res.string.playlist_count), (userInfo.inCustomPlaylistCount ?: 0).toString(), Modifier.weight(1f)) }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCell(stringResource(Res.string.user_rating), (userInfo.userRating ?: 0).toString(), Modifier.weight(1f)); StatCell(stringResource(Res.string.liked_status), if (userInfo.liked) "♥" else "♡", Modifier.weight(1f)) }
                }
            }
            val validLabels = data.labels.filterNotNull().filter { it.label.name.isNotBlank() }
            if (validLabels.isNotEmpty()) {
                InfoCard(title = stringResource(Res.string.labels)) { FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { validLabels.forEach { label -> AssistChip(onClick = {}, label = { Text(label.label.name, style = MaterialTheme.typography.labelSmall) }, border = null) } } }
            }
        } else if (songDetailState is UiState.Loading) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)), color = Transparent) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); content() } }
}

@Composable
private fun InfoRow(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(2.dp)); Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), tonalElevation = 0.dp) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun PlayerTabBar(
    selectedTab: String, onTabSelected: (String) -> Unit,
    tabs: List<Pair<String, String>> = listOf("lyrics" to stringResource(Res.string.lyrics), "info" to stringResource(Res.string.song_detail_tab_intro), "playlist" to stringResource(Res.string.playlist)),
) {
    val haptic = rememberHapticFeedback()
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)) {
        Row(Modifier.padding(4.dp)) { tabs.forEach { (id, label) -> Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (selectedTab == id) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent).clickable { haptic.performClick(); onTabSelected(id) }.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (selectedTab == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } } }
    }
}
