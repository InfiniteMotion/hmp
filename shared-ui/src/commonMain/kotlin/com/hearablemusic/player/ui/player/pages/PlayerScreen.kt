package com.hearablemusic.player.ui.player.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.hearablemusic.player.ui.common.util.activityViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.player.viewmodel.LyricsSettingsState
import com.hearablemusic.player.ui.player.viewmodel.PlayerCallbacks
import com.hearablemusic.player.ui.player.viewmodel.PlayerUiState
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsViewModel
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.AlgorithmType
import com.hmp.domain.playlist.ExtensionConfig
import com.hmp.domain.playlist.WeightTemplate
import dev.chrisbanes.haze.rememberHazeState

// 播放器主界面
@Composable
fun PlayerScreen(
    playbackViewModel: PlaybackViewModel = activityViewModel(),
    playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel(),
    playlistViewModel: PlaylistViewModel = activityViewModel(),
    lyricsSettingsViewModel: LyricsSettingsViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>
) {
    val density = LocalDensity.current
    val chatEntryBroker = koinInject<com.hearablemusic.player.ui.chat.ChatEntryBroker>()
    val dismissThreshold = with(density) { 220.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val hazeState = rememberHazeState()

    // 预加载当前播放音乐信息
    LaunchedEffect(Unit) {
    }

    // 开启播放进度监督
    DisposableEffect(Unit) {
        playbackViewModel.startProgressTracking()
        onDispose {

        }
    }

    val musicInfo by playlistQueueViewModel.currentPlayingMusic.collectAsState()
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val currentPosition by playbackViewModel.currentPosition.collectAsState()
    val duration by playbackViewModel.duration.collectAsState()
    val playbackMode by playbackViewModel.playbackMode.collectAsState()
    val remainingTime by playbackViewModel.timerRemaining.collectAsState()
    val isLiked by playlistQueueViewModel.likeStatus.collectAsState()
    val lyrics by playlistQueueViewModel.currentMusicLyrics.collectAsState()
    val playlist by playlistQueueViewModel.currentPlaylist.collectAsState()
    val currentIndex by playlistQueueViewModel.currentIndex.collectAsState()
    val defaultAlgorithmType by playlistQueueViewModel.defaultAlgorithmType.collectAsState()
    val defaultTemplate by playlistQueueViewModel.defaultWeightTemplate.collectAsState()

    // 歌词配置
    val lyricsOriginalTextSize by lyricsSettingsViewModel.lyricsOriginalTextSize.collectAsState()
    val lyricsTranslatedTextSize by lyricsSettingsViewModel.lyricsTranslatedTextSize.collectAsState()
    val lyricsCurrentTimeTextSize by lyricsSettingsViewModel.lyricsCurrentTimeTextSize.collectAsState()
    val lyricsLineSpacing by lyricsSettingsViewModel.lyricsLineSpacing.collectAsState()
    val lyricsDisplayMode by lyricsSettingsViewModel.lyricsDisplayMode.collectAsState()
    val lyricsAlignment by lyricsSettingsViewModel.lyricsAlignment.collectAsState()
    val lyricsKaraokeEnabled by lyricsSettingsViewModel.lyricsKaraokeEnabled.collectAsState()
    val paletteColors by themeViewModel.paletteColors.collectAsState()

    val playerUiState = PlayerUiState(
        musicInfo = musicInfo,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        playbackMode = playbackMode,
        remainingTime = remainingTime,
        isLiked = isLiked,
        lyrics = lyrics,
        playlist = playlist,
        currentIndex = currentIndex,
        defaultAlgorithmType = defaultAlgorithmType,
        defaultTemplate = defaultTemplate
    )

    val lyricsSettingsState = LyricsSettingsState(
        lyricsOriginalTextSize = lyricsOriginalTextSize,
        lyricsTranslatedTextSize = lyricsTranslatedTextSize,
        lyricsCurrentTimeTextSize = lyricsCurrentTimeTextSize,
        lyricsLineSpacing = lyricsLineSpacing,
        lyricsDisplayMode = lyricsDisplayMode,
        lyricsAlignment = lyricsAlignment,
        lyricsKaraokeEnabled = lyricsKaraokeEnabled
    )

    val playerCallbacks = object : PlayerCallbacks {
        override fun onSeek(position: Long) { playbackViewModel.seekTo(position) }
        override fun onPlayPause() { if (isPlaying) playbackViewModel.pauseMusic() else playbackViewModel.playOrResume() }
        override fun onNext() { playbackViewModel.playNext() }
        override fun onPrevious() { playbackViewModel.playPrevious() }
        override fun onPlaybackModeChange() { playbackViewModel.togglePlaybackModeByOrder() }
        override fun onFavorite() { musicInfo?.let { playlistQueueViewModel.updateMusicLikedStatus(it, !isLiked) } }
        override fun onTimerClick(minutes: Int) { playbackViewModel.startTimer(minutes) }
        override fun onShowTimerDialog() {
            dialogViewModel.showTimerDialog(
                onConfirm = { minutes ->
                    playbackViewModel.startTimer(minutes)
                },
                onDismiss = {
                    // 用户取消
                }
            )
        }
        override fun onCancelTimer() { playbackViewModel.cancelTimer() }
        override fun onHeartMode() { navController.add(Routes.Player.Lyrics) }
        override fun onGeneratePlaylist(seedMusicId: Long) { playlistQueueViewModel.generatePlaylist(seedMusicId) }
        override fun onSaveDefaultConfig(algorithmType: AlgorithmType, weightTemplate: WeightTemplate, extensionConfig: ExtensionConfig) { playlistQueueViewModel.saveAlgorithmConfig(algorithmType, weightTemplate, extensionConfig) }
        override fun onArtistClick(artistName: String) { navController.add(Routes.Library.Artist(artistName)) }
        override fun onClearPlaylist() { playlistQueueViewModel.clearPlaylist() }
        override fun onPlayItem(musicInfo: MusicInfo) { playlistQueueViewModel.playAt(musicInfo) }
        override fun onMoveToTop(musicInfo: MusicInfo) { playlistQueueViewModel.moveToTop(musicInfo) }
        override fun onRemoveFromPlaylist(musicInfo: MusicInfo) { playlistQueueViewModel.removeFromPlaylist(musicInfo) }
        override fun onBackClick() { navController.removeLastOrNull() }
    }

    // 监听音乐变化并加载相关信息
    LaunchedEffect(musicInfo?.music?.id) {
        musicInfo?.music?.id?.let { id ->
            playlistQueueViewModel.getLikedStatus(id)
            playlistQueueViewModel.getMusicLabels(id)
            playlistQueueViewModel.getMusicLyrics(id)
        }
    }

    // 嵌套滚动处理
    val nestedScrollConnection = rememberPlayerScreenNestedScroll(
        dismissThreshold = dismissThreshold,
        offsetY = offsetY,
        scope = scope,
        haptic = { haptic.performLightClick() },
        onDismiss = { 
            navController.removeLastOrNull()
            haptic.performGestureEnd()
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 键盘快捷键（对齐旧桌面版行为；KMP API，Android 外接键盘同样生效）：
            // Space 播放/暂停、←/→ 上一首/下一首、L 心动模式（进歌词页）
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Spacebar -> { playerCallbacks.onPlayPause(); true }
                        Key.DirectionLeft -> { playerCallbacks.onPrevious(); true }
                        Key.DirectionRight -> { playerCallbacks.onNext(); true }
                        Key.L -> { playerCallbacks.onHeartMode(); true }
                        else -> false
                    }
                } else false
            }
            .offset { IntOffset(0, offsetY.value.toInt()) }
            .graphicsLayer {
                alpha = 1f - (offsetY.value / (2 * dismissThreshold)).coerceIn(0f, 1f)
            }
            .nestedScroll(nestedScrollConnection) // 添加嵌套滚动支持
    ) {
        PlayContent(
            playerUiState = playerUiState,
            lyricsSettingsState = lyricsSettingsState,
            paletteColors = paletteColors,
            callbacks = playerCallbacks,
            hazeState = hazeState,
            onOpenChat = { input ->
                // M1 锚点 → M5 对话：带话进对话页（同 session_id 语义）
                chatEntryBroker.pendingInput.value = input
                navController.add(Routes.Companion.Chat)
            },
        )
    }
}

