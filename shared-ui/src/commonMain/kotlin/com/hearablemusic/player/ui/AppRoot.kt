package com.hearablemusic.player.ui
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hearablemusic.player.ui.agent.shell.AgentNoticeBar
import com.hearablemusic.player.ui.agent.shell.BottomFusionBar
import com.hearablemusic.player.ui.agent.shell.FusionSidebar
import com.hearablemusic.player.ui.agent.shell.RadioConsole
import com.hearablemusic.player.ui.agent.shell.RadioConsoleController
import com.hearablemusic.player.ui.common.components.TabPageIndicator
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.theme.ThemeExtensionManager
import com.hearablemusic.player.ui.common.design.typography.HMPTypoTokens
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.m3ComponentTypography
import com.hearablemusic.player.ui.common.dialogs.MusicDetailDialog
import com.hearablemusic.player.ui.common.dialogs.PlaylistPickerDialog
import com.hearablemusic.player.ui.common.dialogs.TimerDialog
import com.hearablemusic.player.ui.common.dialogs.base.MessageToast
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogEvent
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.layout.LocalTitleBarInset
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass
import com.hearablemusic.player.ui.common.layout.rememberAppWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.navigation.navigationGraph
import com.hearablemusic.player.ui.common.navigation.rememberHmpNavBackStack
import com.hearablemusic.player.ui.common.pages.DatabaseTooNewScreen
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
import com.hearablemusic.player.ui.common.util.LocalAppHazeState
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.platform.togglePlayPause
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hmp.data.database.AppDatabaseGuard
import com.hmp.domain.agent.runtime.sub.radio.isActive
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    // 降级守卫（D7-10）：库比代码新时必须在这里就分流 —— 下面任何一次 koinInject / activityViewModel
    // 都会首次解析 AppDatabase，而那会撞上 DatabaseTooNewException（三端都没挂 destructive 兜底，这是刻意的）。
    // 状态由各端启动路径上的 AppDatabaseGuard.probe 挂上来，本页不碰数据库。
    AppDatabaseGuard.tooNew?.let { blocked ->
        DatabaseTooNewScreen(blocked)
        return
    }

    val settingsViewModel: SettingsViewModel = activityViewModel()
    val themeViewModel: ThemeViewModel = activityViewModel()
    val dialogManagerViewModel: DialogManagerViewModel = activityViewModel()
    val dialogViewModel: DialogViewModel = activityViewModel()
    val platformServices = koinInject<com.hearablemusic.player.ui.platform.PlatformServices>()
    // 7.5 P1：播放态投影与动作直接走平台门面（旧 VM 是 controller 的零逻辑包装）
    val playbackController: PlaybackController = koinInject()
    val appRootViewModel: AppRootViewModel = activityViewModel()
    val appRootState by appRootViewModel.uiState.collectAsState()

    // 启动不变量：系统歌单存在性校验 + 自愈（每次启动一次，详见 DefaultPlaylistGuard KDoc）
    LaunchedEffect(Unit) { appRootViewModel.ensureDefaultPlaylists() }

    // 应用回到前台 → 引擎重跑一次健康自检。覆盖「跨天 / 额度重置」这类没有配置、
    // 也没有曲库事件的状态变化；LocalLifecycleOwner 在 commonMain 可用，三端共用一个
    // 组合根，不必逐端去接 Activity / Window / AppDelegate 的生命周期入口。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appRootViewModel.onAppForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dialogManager = dialogManagerViewModel.dialogManager
    // 订阅调色板、当前曲目与播放状态
    val currentMusic by playbackController.currentPlayingMusic.collectAsState()
    val paletteColors by themeViewModel.paletteColors.collectAsState()
    val isPlaying by playbackController.isPlaying.collectAsState()

    // 电台「已开启」= PLAYING / PAUSED / BUILDING（外部不区分停止与暂停，与 RadioCard 同一口径）：
    // 暂停态由电台自管且可恢复，对它显示"已关闭"会误导。
    val radioState = appRootState.radioState
    val radioActive = radioState.isActive()

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
        tintAlpha = hazeTintAlpha,
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

    // DialogHost 状态：一次性事件由单个 collector 分发，SharedFlow 不做状态化 ——
    // 状态化会让后到事件覆盖前一件（toast 被新事件顶掉），同值事件也会被吞。
    val hazeState = rememberHazeState()
    // toast 队列：连续事件按序展示；原单槽会在前一条未显示完时被后一条覆盖。
    var messageQueue by remember { mutableStateOf<List<DialogEvent.Message>>(emptyList()) }

    // 启动时按配置启动悬浮歌词：配置读取与异常处理在 AppRootViewModel，平台启动留在组合点
    LaunchedEffect(Unit) {
        if (appRootViewModel.shouldStartFloatingLyrics() && platformServices.permission.hasOverlayPermission()) {
            platformServices.floatingLyrics.start()
        }
    }

    val navController = rememberHmpNavBackStack(Routes.Main.Tabs)

    // 全局对话框事件的唯一消费点：toast / 导航 / 分享都在这里直达，
    // 不再把 SharedFlow 变成 StateFlow 后让两个 LaunchedEffect 分头读同一份状态。
    LaunchedEffect(Unit) {
        dialogManager.dialogEvent.collect { event ->
            when (event) {
                is DialogEvent.Message -> messageQueue = (messageQueue + event).takeLast(5)

                is DialogEvent.NavRequest -> navController.add(event.route)

                is DialogEvent.ShareMusic -> platformServices.share.shareMusic(
                    com.hearablemusic.player.ui.platform.ShareMusicRequest(
                        filePath = event.filePath,
                        title = event.title,
                        artist = event.artist,
                        album = event.album,
                    ),
                )
            }
        }
    }

    val tabCount = 4
    val savedTabIndex = rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedTabIndex.intValue,
    ) { tabCount }
    val haptic = rememberHapticFeedback()

    // ── M1 锚点系统状态（Fake 驱动；M4 接 PresenceBus）──
    // 「找伙伴」统一出口：**直接进对话页**（轻量浮层已于 2026-09-19 彻底移除）。
    // 长按伙伴胶囊（电台未开启时）/ C 键（Desktop）都走这里；
    // 电台开启时长按让位给电台控制台（见下方 BottomFusionBar 的分流）。
    val openCompanionChat: () -> Unit = {
        if (navController.none { it is Routes.Companion.Chat }) {
            navController.add(Routes.Companion.Chat)
        }
    }

    // 长按伙伴胶囊（电台开启时）唤起的电台控制台 —— 弹窗
    val radioConsoleController: RadioConsoleController = koinInject()
    val radioConsoleVisible by radioConsoleController.visible.collectAsState()
    // 电台一旦不在（用户收档 / 对话说停电台 / 看板停止），面板自动收起；
    // 滑动收档 → stopRadio → 这里触发 → 面板淡出，正是"收档"的收尾动作。
    LaunchedEffect(radioActive) {
        if (!radioActive) radioConsoleController.close()
    }

    // ── M6-T2c/M6-T3：PresenceBus → AgentNoticeBar 侧条（事件投影在组合根 VM）──

    LaunchedEffect(pagerState.currentPage) {
        savedTabIndex.intValue = pagerState.currentPage
    }
    // TabPageIndicator：3 条（音乐库/歌单/个人），门面页(index 0)不参与
    val indicatorTotal = 3
    val indicatorPage = (pagerState.currentPage - 1).coerceIn(0, indicatorTotal - 1)
    val tabHeader: @Composable () -> Unit = {
        TabPageIndicator(
            currentPage = indicatorPage,
            totalPages = indicatorTotal,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }

    val windowSizeInfo = rememberAppWindowSizeInfo()

    // 应用主题(根据播放状态切换)：typography = 字阶 9 角色（TypeScale，随窗口档缩放）的
    // internal 投影，仅喂 M3 组件内部文字（Button/TextField/Dialog 等）；业务排版走 LocalHmpTypo。
    // 投影零字重覆盖（槽位字重全随角色默认），见 hmp-design.md §3.2 / taskbook D8-03 阶段 2。
    val typo = HMPTypoTokens.rememberHmpTypo()
    val metrics = HMPMetricTokens.rememberHmpMetrics()
    MaterialTheme(
        colorScheme = colorScheme,
        typography = m3ComponentTypography(typo),
    ) {
        ProvideHazeRenderSettings(
            settings = hazeRenderSettings,
        ) {
            CompositionLocalProvider(
                LocalWindowSizeInfo provides windowSizeInfo,
                LocalHmpTypo provides typo,
                LocalHmpMetrics provides metrics,
                LocalAppHazeState provides hazeState,
            ) {
                CompositionLocalProvider(
                    LocalTabHeaderContent provides tabHeader,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // M1-T5 键盘锚点：C = 直达对话页（原为唤起轻量浮层，该层已移除）、
                                // Esc = 关闭电台控制台。
                                // 用 onKeyEvent（冒泡阶段）而非 onPreviewKeyEvent（捕获阶段）：
                                // 冒泡阶段子节点先消费按键——文本输入聚焦时字母 C/Esc 由输入框先行处理，
                                // 根节点只收到未被消费的按键，不会吞掉用户正在输入的字符（review 修复 2026-08-28）
                                .onKeyEvent { event ->
                                    when {
                                        // C 键（Desktop）：直达对话页（原为唤起轻量浮层）
                                        event.type == KeyEventType.KeyDown &&
                                            event.key == Key.C &&
                                            !radioConsoleVisible -> {
                                            haptic.performClick()
                                            openCompanionChat()
                                            true
                                        }

                                        event.type == KeyEventType.KeyDown &&
                                            event.key == Key.Escape &&
                                            radioConsoleVisible -> {
                                            radioConsoleController.close()
                                            true
                                        }

                                        else -> false
                                    }
                                }
                                .hazeSource(state = hazeState),
                        ) {
                            // 1. 静态背景层 (始终存在，确保无黑屏)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            )

                            // 2. 全局动态背景层 (仅在播放时覆盖在静态背景之上，带过渡动画)
                            AnimatedVisibility(
                                visible = isPlaying && currentMusic != null,
                                enter = fadeIn(
                                    animationSpec = HmpMotion.page(),
                                ),
                                exit = fadeOut(
                                    animationSpec = HmpMotion.page(),
                                ),
                            ) {
                                DynamicBackground(
                                    albumArtUri = currentMusic?.music?.albumArtUri,
                                    paletteColors = paletteColors,
                                    isDarkTheme = darkTheme,
                                    style = backgroundStyle,
                                    modifier = Modifier,
                                )
                            }

                            // 前景页面内容
                            Scaffold(
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                containerColor = Transparent,
                                bottomBar = {},
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
                                            rememberViewModelStoreNavEntryDecorator(),
                                        ),
                                        transitionSpec = {
                                            fadeIn(
                                                animationSpec = HmpMotion.emphasis(),
                                            ) + scaleIn(
                                                initialScale = 0.95f,
                                                animationSpec = HmpMotion.emphasis(),
                                            ) togetherWith fadeOut(
                                                animationSpec = HmpMotion.emphasis(),
                                            ) + scaleOut(
                                                targetScale = 1.05f,
                                                animationSpec = HmpMotion.emphasis(),
                                            )
                                        },
                                        popTransitionSpec = {
                                            fadeIn(
                                                animationSpec = HmpMotion.emphasis(),
                                            ) + scaleIn(
                                                initialScale = 0.95f,
                                                animationSpec = HmpMotion.emphasis(),
                                            ) togetherWith fadeOut(
                                                animationSpec = HmpMotion.emphasis(),
                                            ) + scaleOut(
                                                targetScale = 1.05f,
                                                animationSpec = HmpMotion.emphasis(),
                                            )
                                        },
                                        predictivePopTransitionSpec = {
                                            fadeIn(
                                                animationSpec = HmpMotion.emphasis(),
                                            ) + scaleIn(
                                                initialScale = 0.95f,
                                                animationSpec = HmpMotion.emphasis(),
                                            ) togetherWith fadeOut(
                                                animationSpec = HmpMotion.emphasis(),
                                            ) + scaleOut(
                                                targetScale = 1.05f,
                                                animationSpec = HmpMotion.emphasis(),
                                            )
                                        },
                                        entryProvider = navigationGraph(
                                            navController = navController,
                                            pagerState = pagerState,
                                        ),
                                    )

                                    // TabPageIndicator
                                    val isInTabs = navController.size == 1 && navController.lastOrNull() is Routes.Main.Tabs
                                    AnimatedVisibility(
                                        visible = isInTabs && !windowSizeInfo.useFusionSidebar && !windowSizeInfo.isLandscape && pagerState.currentPage > 0,
                                        enter = fadeIn(
                                            animationSpec = HmpMotion.enter(),
                                        ) + scaleIn(
                                            initialScale = 0.8f,
                                            animationSpec = HmpMotion.enter(),
                                        ) + slideInVertically(
                                            initialOffsetY = { -it },
                                            animationSpec = HmpMotion.enter(),
                                        ),
                                        exit = fadeOut(
                                            animationSpec = HmpMotion.exit(),
                                        ) + scaleOut(
                                            targetScale = 0.9f,
                                            animationSpec = HmpMotion.exit(),
                                        ) + slideOutVertically(
                                            targetOffsetY = { -it / 2 },
                                            animationSpec = HmpMotion.exit(),
                                        ),
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.TopCenter,
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
                                                onOpenPlayer = { navController.add(Routes.Player.Player) },
                                            )
                                        }
                                        Box(Modifier.weight(1f).fillMaxSize()) {
                                            navContent()
                                        }
                                    }
                                } else {
                                    // Compact / Expanded：单列布局
                                    Box(
                                        modifier = contentModifier.then(
                                            if (windowSizeInfo.isLandscape) Modifier.displayCutoutPadding() else Modifier,
                                        ),
                                    ) {
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
                                visible = navController.none { it is Routes.Player.Player || it is Routes.Player.Lyrics || it is Routes.Companion.Chat },
                                enter = slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = HmpMotion.emphasis(),
                                ),
                                exit = slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = HmpMotion.emphasis(),
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter),
                            ) {
                                // 侧条锚定在底栏上方（设计总纲 3.3：有底栏贴底栏上方、无底栏贴屏底）
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // M6-T2c/M6-T3：AgentNoticeBar 侧条（PresenceBus 事件 → 4s 自动退场）
                                    AgentNoticeBar(
                                        notice = appRootState.notice,
                                        modifier = Modifier.padding(bottom = metrics.micro),
                                    )
                                    Box {
                                        BottomFusionBar(
                                            musicInfo = currentMusic,
                                            isPlaying = isPlaying,
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
                                                // 电台未开启时长按 = 直接进对话页（轻量浮层已移除）
                                                openCompanionChat()
                                            },
                                            radioActive = radioActive,
                                            onOpenRadioConsole = { radioConsoleController.open() },
                                            hazeState = hazeState,
                                            showNavText = windowSizeInfo.isLandscape,
                                            showNavCapsule = bfbIsOnTabPage,
                                            maxWidth = when (windowSizeInfo.widthSizeClass) {
                                                WindowWidthSizeClass.Compact -> LayoutConstraints.HeightBreakpointMedium
                                                WindowWidthSizeClass.Medium -> LayoutConstraints.ContentMaxWidthMedium
                                                WindowWidthSizeClass.Expanded -> null
                                            },
                                            onPlayPause = { playbackController.togglePlayPause(isPlaying) },
                                            onNext = { playbackController.playNext() },
                                            onPrev = { playbackController.playPrevious() },
                                            onOpenPlayer = { navController.add(Routes.Player.Player) },
                                        )
                                    }
                                }
                            }
                        }

                        val activeDialogState by dialogViewModel.activeDialog.collectAsState()
                        when (val state = activeDialogState) {
                            is DialogViewModel.DialogUiState.MusicDetail -> {
                                MusicDetailDialog(
                                    state = state,
                                    dialogViewModel = dialogViewModel,
                                    hazeState = hazeState,
                                    hazeRenderSettings = hazeRenderSettings,
                                )
                            }

                            is DialogViewModel.DialogUiState.PlaylistPicker -> {
                                PlaylistPickerDialog(
                                    playlists = state.state.playlists,
                                    title = state.state.title,
                                    onDismiss = dialogViewModel::dismissPlaylistPickerDialog,
                                    onSelectPlaylist = dialogViewModel::confirmPlaylistPickerDialog,
                                    hazeState = hazeState,
                                    hazeRenderSettings = hazeRenderSettings,
                                )
                            }

                            is DialogViewModel.DialogUiState.Timer -> {
                                TimerDialog(
                                    onDismiss = dialogViewModel::dismissTimerDialog,
                                    onConfirm = dialogViewModel::confirmTimerDialog,
                                    hazeState = hazeState,
                                    hazeRenderSettings = hazeRenderSettings,
                                )
                            }

                            null -> Unit
                        }

                        messageQueue.firstOrNull()?.let { message ->
                            MessageToast(
                                message = message.message,
                                duration = message.duration,
                                id = message.id,
                                hazeState = hazeState,
                                hazeRenderSettings = hazeRenderSettings,
                                onDismiss = { messageQueue = messageQueue.drop(1) },
                            )
                        }

                        // ── 电台控制台：长按伙伴胶囊（电台开启时）唤起的全屏浮层弹窗 ──
                        // 覆盖在底栏与所有内容之上；面板打开期间电台照常运行（只读观察窗）。
                        RadioConsole(
                            visible = radioConsoleVisible,
                            onDismiss = { radioConsoleController.close() },
                            onEndSession = {
                                radioConsoleController.close()
                                appRootViewModel.onEndRadioSession()
                            },
                            onPlayTrack = { musicId ->
                                // 跳到那首：**在既有队列里定位并播**，不新建队列 ——
                                // 新建会把主播刚排的队冲掉。`playWith` = `addToPlaylist`
                                // （尾部追加，整队列去重 ⇒ 已在队列中则原地不动、不重复入队）
                                // + `playAt`（在既有队列里定位播放，**不重建队列**）。
                                // **不收起面板**（用户决议）：跳一首不代表"用完了控制台"——
                                // 留着才能接着看编排、继续跳下一首；面板只读，开着不干扰播放。
                                appRootViewModel.onPlayRadioTrack(musicId)
                            },
                            nowCoverUri = currentMusic?.music?.albumArtUri,
                            hazeState = hazeState,
                            hazeRenderSettings = hazeRenderSettings,
                        )
                    }
                } // CompositionLocalProvider LocalTabHeaderContent
            } // CompositionLocalProvider
        }
    }
}
