package com.hearablemusic.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hearablemusic.player.ui.common.components.AgentQuickSheet
import com.hearablemusic.player.ui.common.components.BottomFusionBar
import com.hearablemusic.player.ui.common.components.FusionSidebar
import com.hearablemusic.player.ui.common.components.TabPageIndicator
import com.hearablemusic.player.ui.common.design.animation.AnimationTokens
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.design.dimens.rememberHMPDimens
import com.hearablemusic.player.ui.common.design.theme.ThemeExtensionManager
import com.hearablemusic.player.ui.common.design.typography.TypographyTokens
import com.hearablemusic.player.ui.common.dialogs.CreatePlaylistDialog
import com.hearablemusic.player.ui.common.dialogs.MusicDetailDialog
import com.hearablemusic.player.ui.common.dialogs.MusicPickerDialog
import com.hearablemusic.player.ui.common.dialogs.PlaylistPickerDialog
import com.hearablemusic.player.ui.common.dialogs.TimerDialog
import com.hearablemusic.player.ui.common.dialogs.base.MessageToast
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogEvent
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.layout.LocalTitleBarInset
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.layout.rememberAppWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.navigation.navigationGraph
import com.hearablemusic.player.ui.common.navigation.rememberHmpNavBackStack
import com.hearablemusic.player.ui.common.navigation.rememberRouter
import com.hearablemusic.player.ui.common.pages.base.BackgroundStyle
import com.hearablemusic.player.ui.common.pages.base.DynamicBackground
import com.hearablemusic.player.ui.common.pages.base.LocalTabHeaderContent
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_BLUR_RADIUS
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_INTENSITY
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_MATERIAL_PRESET
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_MODE
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_NOISE_FACTOR
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_TINT_ALPHA
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 新 UI 共享层应用壳。
 *
 * 责任：完整导航宿主——NavDisplay（转场动画）+ Tabs（MainShell）+ 全部二级页；
 * 播放态动态主题与动态背景；TabPageIndicator / BottomFusionBar / FusionSidebar 自适应布局；
 * 全局 DialogHost 与 Toast；分享/悬浮歌词等平台能力经 PlatformServices。
 *
 * @param darkTheme 由 app 壳（MainActivity）按用户主题偏好计算后传入
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(darkTheme: Boolean) {
    val settingsViewModel: SettingsViewModel = activityViewModel()
    val playbackViewModel: PlaybackViewModel = activityViewModel()
    val playlistQueueViewModel: PlaylistQueueViewModel = activityViewModel()
    val themeViewModel: ThemeViewModel = activityViewModel()
    val dialogManagerViewModel: DialogManagerViewModel = activityViewModel()
    val dialogViewModel: DialogViewModel = activityViewModel()
    val platformServices = koinInject<com.hearablemusic.player.ui.platform.PlatformServices>()

    val dialogManager = dialogManagerViewModel.dialogManager
    // 订阅调色板、当前曲目与播放状态
    val currentMusic by playlistQueueViewModel.currentPlayingMusic.collectAsState()
    val paletteColors by themeViewModel.paletteColors.collectAsState()
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val currentPosition by playbackViewModel.currentPosition.collectAsState()
    val duration by playbackViewModel.duration.collectAsState()

    // 背景样式与 haze 渲染设置
    val backgroundStyleString by settingsViewModel.backgroundStyle.collectAsState("FLUID")
    val hazeMode by settingsViewModel.hazeMode.collectAsState(DEFAULT_HAZE_MODE)
    val hazeMaterialPreset by settingsViewModel.hazeMaterialPreset.collectAsState(DEFAULT_HAZE_MATERIAL_PRESET)
    val hazeBlurRadius by settingsViewModel.hazeBlurRadius.collectAsState(DEFAULT_HAZE_BLUR_RADIUS)
    val hazeNoiseFactor by settingsViewModel.hazeNoiseFactor.collectAsState(DEFAULT_HAZE_NOISE_FACTOR)
    val hazeTintAlpha by settingsViewModel.hazeTintAlpha.collectAsState(DEFAULT_HAZE_TINT_ALPHA)
    val hazeIntensity by settingsViewModel.hazeIntensity.collectAsState(DEFAULT_HAZE_INTENSITY)
    val hazeRenderSettings = HazeRenderSettings(
        mode = hazeMode,
        preset = hazeMaterialPreset,
        intensity = hazeIntensity,
        blurRadius = hazeBlurRadius,
        noiseFactor = hazeNoiseFactor,
        tintAlpha = hazeTintAlpha
    )
    val backgroundStyle = try {
        BackgroundStyle.valueOf(backgroundStyleString)
    } catch (e: Exception) {
        BackgroundStyle.FLUID
    }

    // 根据播放状态选择主题: 播放时使用动态主题,暂停时使用预置主题
    val colorScheme = if (isPlaying) {
        ThemeExtensionManager.generateDynamicColorScheme(paletteColors, darkTheme)
    } else {
        ThemeExtensionManager.getColorScheme(darkTheme)
    }

    // DialogHost状态
    val dialogEvent by dialogManager.dialogEvent.collectAsState(null)
    val hazeState = rememberHazeState()
    val messageToShowState = remember { mutableStateOf<DialogEvent.Message?>(null) }

    // 启动时按配置启动悬浮歌词（平台能力经 PlatformServices）
    val lyricsSettingsUseCase: LyricsSettingsUseCase = koinInject()
    LaunchedEffect(Unit) {
        try {
            if (lyricsSettingsUseCase.floatingLyricsEnabled.first() && platformServices.permission.hasOverlayPermission()) {
                platformServices.floatingLyrics.start()
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(dialogEvent) {
        when (dialogEvent) {
            is DialogEvent.Message -> {
                messageToShowState.value = dialogEvent as DialogEvent.Message
            }
            is DialogEvent.DismissTimerDialog -> {
                dialogViewModel.dismissTimerDialog()
            }
            is DialogEvent.ShareMusic -> {
                val shareEvent = dialogEvent as DialogEvent.ShareMusic
                platformServices.share.shareMusic(
                    com.hearablemusic.player.ui.platform.ShareMusicRequest(
                        filePath = shareEvent.filePath,
                        title = shareEvent.title,
                        artist = shareEvent.artist,
                        album = shareEvent.album
                    )
                )
            }
            else -> {
                // 无操作
            }
        }
    }

    val navController = rememberHmpNavBackStack(Routes.Main.Tabs)
    val router = rememberRouter(navController)
    // 消费 ViewModel 发起的导航请求（ViewModel 不持有导航器引用）
    LaunchedEffect(dialogEvent) {
        if (dialogEvent is DialogEvent.NavRequest) {
            navController.add((dialogEvent as DialogEvent.NavRequest).route)
        }
    }

    val tabCount = 4
    val savedTabIndex = rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedTabIndex.intValue
    ) { tabCount }
    val haptic = rememberHapticFeedback()

    // ── M1 锚点系统状态（Fake 驱动；M4 接 PresenceBus）──
    // M1-T6 存根：轻量浮层开关（伙伴徽标已于 2026-08-27 移除——门面页高亮代替红点提示；
    // 未读/待确认等真实徽标在 M5 会话接入时按需恢复）
    var companionQuickSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        savedTabIndex.intValue = pagerState.currentPage
    }
    val tabHeader: @Composable () -> Unit = {
        TabPageIndicator(
            currentPage = pagerState.currentPage,
            totalPages = tabCount,
            modifier = Modifier
                .fillMaxWidth()
        )
    }

    val windowSizeInfo = rememberAppWindowSizeInfo()

    // 应用主题(根据播放状态切换)；typography 必须显式传自定义字体配置
    // （HarmonyOS Sans 家族 + 自定义字重字号），缺省会回退 M3 默认字体
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TypographyTokens.Typography
    ) {
        ProvideHazeRenderSettings(
            settings = hazeRenderSettings
        ) {
            CompositionLocalProvider(
                LocalWindowSizeInfo provides windowSizeInfo
            ) {
                val dimens = rememberHMPDimens()
                CompositionLocalProvider(
                    LocalHMPDimens provides dimens
                ) {
                    CompositionLocalProvider(
                        LocalTabHeaderContent provides tabHeader
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // M1-T5 键盘锚点：C 唤起轻量浮层、Esc 收起。
                                    // 用 onKeyEvent（冒泡阶段）而非 onPreviewKeyEvent（捕获阶段）：
                                    // 冒泡阶段子节点先消费按键——文本输入聚焦时字母 C/Esc 由输入框先行处理，
                                    // 根节点只收到未被消费的按键，不会吞掉用户正在输入的字符（review 修复 2026-08-28）
                                    .onKeyEvent { event ->
                                        when {
                                            event.type == KeyEventType.KeyDown &&
                                                event.key == Key.C &&
                                                !companionQuickSheetVisible -> {
                                                haptic.performClick()
                                                companionQuickSheetVisible = true
                                                true
                                            }
                                            event.type == KeyEventType.KeyDown &&
                                                event.key == Key.Escape &&
                                                companionQuickSheetVisible -> {
                                                companionQuickSheetVisible = false
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                                    .hazeSource(state = hazeState)
                            ) {
                                // 1. 静态背景层 (始终存在，确保无黑屏)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                )

                                // 2. 全局动态背景层 (仅在播放时覆盖在静态背景之上，带过渡动画)
                                AnimatedVisibility(
                                    visible = isPlaying && currentMusic != null,
                                    enter = fadeIn(
                                        animationSpec = tween(durationMillis = 800, easing = AnimationTokens.EASE_IN_OUT)
                                    ),
                                    exit = fadeOut(
                                        animationSpec = tween(durationMillis = 800, easing = AnimationTokens.EASE_IN_OUT)
                                    )
                                ) {
                                    DynamicBackground(
                                        albumArtUri = currentMusic?.music?.albumArtUri,
                                        paletteColors = paletteColors,
                                        isDarkTheme = darkTheme,
                                        style = backgroundStyle,
                                        modifier = Modifier
                                    )
                                }

                                // 前景页面内容
                                Scaffold(
                                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                    containerColor = Transparent,
                                    bottomBar = {}
                                ) {
                                    val contentModifier = Modifier
                                        .padding(it)
                                        .statusBarsPadding()
                                        // 5d：平台壳悬浮标题栏让位（Desktop 40dp / Android 0）
                                        .padding(top = LocalTitleBarInset.current)

                                    val coroutineScope = rememberCoroutineScope()

                                    val navContent: @Composable () -> Unit = {
                                        NavDisplay(
                                            backStack = navController,
                                            onBack = { navController.removeLastOrNull() },
                                            modifier = Modifier.fillMaxSize(),
                                            entryDecorators = listOf(
                                                rememberSaveableStateHolderNavEntryDecorator(),
                                                rememberViewModelStoreNavEntryDecorator()
                                            ),
                                            transitionSpec = {
                                                fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) + scaleIn(
                                                    initialScale = 0.95f,
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) togetherWith fadeOut(
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) + scaleOut(
                                                    targetScale = 1.05f,
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                )
                                            },
                                            popTransitionSpec = {
                                                fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) + scaleIn(
                                                    initialScale = 0.95f,
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) togetherWith fadeOut(
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) + scaleOut(
                                                    targetScale = 1.05f,
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                )
                                            },
                                            predictivePopTransitionSpec = {
                                                fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) + scaleIn(
                                                    initialScale = 0.95f,
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) togetherWith fadeOut(
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                ) + scaleOut(
                                                    targetScale = 1.05f,
                                                    animationSpec = tween(
                                                        durationMillis = AnimationTokens.TRANSITION,
                                                        easing = AnimationTokens.EASE_IN_OUT
                                                    )
                                                )
                                            },
                                            entryProvider = navigationGraph(
                                                navController = navController,
                                                pagerState = pagerState
                                            )
                                        )

                                        // TabPageIndicator
                                        val isInTabs = navController.size == 1 && navController.lastOrNull() is Routes.Main.Tabs
                                        AnimatedVisibility(
                                            visible = isInTabs && !windowSizeInfo.useFusionSidebar && !windowSizeInfo.isLandscape,
                                            enter = fadeIn(
                                                animationSpec = tween(300, easing = AnimationTokens.EASE_OUT)
                                            ) + scaleIn(
                                                initialScale = 0.8f,
                                                animationSpec = tween(300, easing = AnimationTokens.EASE_OUT)
                                            ) + slideInVertically(
                                                initialOffsetY = { -it },
                                                animationSpec = tween(300, easing = AnimationTokens.EASE_OUT)
                                            ),
                                            exit = fadeOut(
                                                animationSpec = tween(250, easing = AnimationTokens.EASE_IN)
                                            ) + scaleOut(
                                                targetScale = 0.9f,
                                                animationSpec = tween(250, easing = AnimationTokens.EASE_IN)
                                            ) + slideOutVertically(
                                                targetOffsetY = { -it / 2 },
                                                animationSpec = tween(250, easing = AnimationTokens.EASE_IN)
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                tabHeader()
                                            }
                                        }
                                    }
                                    val isOnTabPage = navController.size == 1 && navController.lastOrNull() is Routes.Main.Tabs

                                    if (windowSizeInfo.useFusionSidebar) {
                                        // Medium 手机横屏：左侧融合侧边栏 + 右侧内容（侧边栏仅在Tab页面显示）
                                        Row(modifier = contentModifier.displayCutoutPadding()) {
                                            if (isOnTabPage) {
                                                FusionSidebar(
                                                    selectedTabIndex = pagerState.currentPage,
                                                    currentMusic = currentMusic,
                                                    isPlaying = isPlaying,
                                                    onTabSelected = { index ->
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    },
                                                    onOpenPlayer = { navController.add(Routes.Player.Player) }
                                                )
                                            }
                                            Box(Modifier.weight(1f).fillMaxSize()) {
                                                navContent()
                                            }
                                        }
                                    } else {
                                        // Compact / Expanded：单列布局
                                        Box(modifier = contentModifier.then(
                                            if (windowSizeInfo.isLandscape) Modifier.displayCutoutPadding() else Modifier
                                        )) {
                                            navContent()
                                        }
                                    }
                                }
                            }

                            // BottomFusionBar 底部融合栏（导航Tab + 播放控制）
                            // 非 Medium 模式始终渲染，Medium 模式仅在子页面渲染（Tab 页面由 FusionSidebar 处理）
                            val bfbIsOnTabPage = navController.size == 1 && navController.lastOrNull() is Routes.Main.Tabs
                            if (!windowSizeInfo.useFusionSidebar || !bfbIsOnTabPage) {
                                val bfbScope = rememberCoroutineScope()
                                // 底部融合栏 = Tab 导航 + 迷你播放器：Tab 页常驻显示（无歌曲时仍提供
                                // Tab 导航，迷你播放器区显示空态；子页面由进入播放/歌词页才隐藏）
                                AnimatedVisibility(
                                    visible = navController.none { it is Routes.Player.Player || it is Routes.Player.Lyrics },
                                    enter = slideInVertically(
                                        initialOffsetY = { it },
                                        animationSpec = tween(durationMillis = AnimationTokens.TRANSITION, easing = AnimationTokens.EASE_IN_OUT)
                                    ),
                                    exit = slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = tween(durationMillis = AnimationTokens.TRANSITION, easing = AnimationTokens.EASE_IN_OUT)
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                ) {
                                    // 浮层锚定在底栏上方（设计总纲 3.3：有底栏贴底栏上方、无底栏贴屏底）
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AgentQuickSheet(
                                            visible = companionQuickSheetVisible,
                                            onSubmit = { input ->
                                                // M5 起接线会话（浮层与对话页同 session_id）；M1 为锚点骨架占位
                                                println("[M1] agent quick sheet submit: $input")
                                                companionQuickSheetVisible = false
                                            },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        )
                                        Box {
                                            BottomFusionBar(
                                                musicInfo = currentMusic,
                                                isPlaying = isPlaying,
                                                progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                                selectedTabIndex = pagerState.currentPage,
                                                onTabSelected = { index ->
                                                    bfbScope.launch {
                                                        // tab N ↔ 页 N+1（门面页 0 无对应 Tab，设计总纲 2.2）
                                                        pagerState.animateScrollToPage(index + 1)
                                                    }
                                                },
                                                onCompanionClick = {
                                                    // 设计总纲 2.2：子页面态点按伙伴胶囊 = pop 回 Tabs + 滚到门面（第 0 页）
                                                    bfbScope.launch {
                                                        while (navController.size > 1) {
                                                            navController.removeLastOrNull()
                                                        }
                                                        pagerState.animateScrollToPage(0)
                                                    }
                                                },
                                                onCompanionLongPress = {
                                                    companionQuickSheetVisible = true
                                                },
                                                hazeState = hazeState,
                                            showNavText = windowSizeInfo.isLandscape,
                                            showNavCapsule = bfbIsOnTabPage,
                                            maxWidth = when (windowSizeInfo.widthSizeClass) {
                                                WindowWidthSizeClass.Compact -> 480.dp
                                                WindowWidthSizeClass.Medium -> 640.dp
                                                WindowWidthSizeClass.Expanded -> null
                                            },
                                            onPlayPause = {
                                                if (isPlaying) {
                                                    playbackViewModel.pauseMusic()
                                                } else {
                                                    playbackViewModel.playOrResume()
                                                }
                                            },
                                            onNext = { playbackViewModel.playNext() },
                                            onPrev = { playbackViewModel.playPrevious() },
                                            onOpenPlayer = { navController.add(Routes.Player.Player) }
                                        )
                                    }
                                }
                            }
                        }

                            val activeDialogState by dialogViewModel.activeDialog.collectAsState()
                            when (val state = activeDialogState) {
                                is DialogViewModel.DialogUiState.MusicDetail -> {
                                    MusicDetailDialog(
                                        dialogViewModel = dialogViewModel,
                                        onDismiss = {
                                            dialogViewModel.dismissMusicDetailDialog()
                                        },
                                        router = router,
                                        hazeState = hazeState,
                                        hazeRenderSettings = hazeRenderSettings
                                    )
                                }
                                is DialogViewModel.DialogUiState.CreatePlaylist -> {
                                    CreatePlaylistDialog(
                                        dialogViewModel = dialogViewModel,
                                        hazeState = hazeState,
                                        hazeRenderSettings = hazeRenderSettings
                                    )
                                }
                                is DialogViewModel.DialogUiState.MusicPicker -> {
                                    MusicPickerDialog(
                                        allMusic = state.state.allMusic,
                                        selectedIds = state.state.selectedIds,
                                        title = state.state.title,
                                        onConfirm = dialogViewModel::confirmMusicPickerDialog,
                                        onDismiss = dialogViewModel::dismissMusicPickerDialog,
                                        hazeState = hazeState,
                                        hazeRenderSettings = hazeRenderSettings
                                    )
                                }
                                is DialogViewModel.DialogUiState.PlaylistPicker -> {
                                    PlaylistPickerDialog(
                                        playlists = state.state.playlists,
                                        title = state.state.title,
                                        onDismiss = dialogViewModel::dismissPlaylistPickerDialog,
                                        onSelectPlaylist = dialogViewModel::confirmPlaylistPickerDialog,
                                        hazeState = hazeState,
                                        hazeRenderSettings = hazeRenderSettings
                                    )
                                }
                                is DialogViewModel.DialogUiState.Timer -> {
                                    TimerDialog(
                                        onDismiss = {
                                            state.state.onDismiss()
                                            dialogViewModel.dismissTimerDialog()
                                        },
                                        onConfirm = { minutes: Int ->
                                            state.state.onConfirm(minutes)
                                            dialogViewModel.dismissTimerDialog()
                                        },
                                        hazeState = hazeState,
                                        hazeRenderSettings = hazeRenderSettings
                                    )
                                }
                                null -> Unit
                            }

                            messageToShowState.value?.let { message ->
                                MessageToast(
                                    message = message.message,
                                    duration = message.duration,
                                    id = message.id,
                                    hazeState = hazeState,
                                    hazeRenderSettings = hazeRenderSettings,
                                    onDismiss = { messageToShowState.value = null }
                                )
                            }
                        }
                    } // CompositionLocalProvider LocalTabHeaderContent
                } // CompositionLocalProvider HMPDimens
            } // CompositionLocalProvider
        }
    }
}
