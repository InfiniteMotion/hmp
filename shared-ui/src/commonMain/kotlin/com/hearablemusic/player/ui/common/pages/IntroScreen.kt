package com.hearablemusic.player.ui.common.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.dialogs.MusicScanDialog
import com.hearablemusic.player.ui.common.layout.LocalTitleBarInset
import com.hearablemusic.player.ui.common.layout.rememberAppWindowSizeInfo
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_BLUR_RADIUS
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_INTENSITY
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_MATERIAL_PRESET
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_MODE
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_NOISE_FACTOR
import com.hearablemusic.player.ui.common.util.DEFAULT_HAZE_TINT_ALPHA
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.grant_permission
import com.hearablemusic.player.ui.generated.resources.ic_launcher_foreground
import com.hearablemusic.player.ui.generated.resources.intro_skip_permission
import com.hearablemusic.player.ui.generated.resources.intro_step_1
import com.hearablemusic.player.ui.generated.resources.intro_step_1_desc
import com.hearablemusic.player.ui.generated.resources.intro_step_2
import com.hearablemusic.player.ui.generated.resources.intro_step_2_desc
import com.hearablemusic.player.ui.generated.resources.intro_step_3
import com.hearablemusic.player.ui.generated.resources.intro_step_3_desc
import com.hearablemusic.player.ui.generated.resources.permission_granted
import com.hearablemusic.player.ui.generated.resources.scan
import com.hearablemusic.player.ui.generated.resources.scan_finished
import com.hearablemusic.player.ui.generated.resources.start_experience
import com.hearablemusic.player.ui.generated.resources.welcome_to
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun IntroScreen(
    settingsViewModel: SettingsViewModel,
    recommendationViewModel: RecommendationViewModel,
    onFinished: () -> Unit,
    /**
     * 是否跳过首个「权限」步（桌面：无运行时权限体系，该步无意义）。
     *
     * 依据：旧桌面 UI 层曾有一份同构实现，内中即写明
     * `// Desktop: skip permission step, start at scan` 且 `isPermissionGiven = true`。
     * 默认 false —— Android / iOS 行为不变。
     */
    skipPermissionStep: Boolean = false,
    viewModel: IntroViewModel = koinViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    // 7.5 P1：引导阶段态收进 IntroViewModel 的单一 uiState
    val introState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.onStart(skipPermissionStep) }
    // 扫描进度与总量在统一 uiState 里；扫描动作也在 VM 内发起，经组合点转发的只剩 AI 补全。
    val isScanning = introState.isScanning
    val musicCount = introState.musicCount
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                IntroEffect.StartAiAutoProcess -> recommendationViewModel.startAutoProcessWithCurrentProvider()
            }
        }
    }
    val hazeMode by settingsViewModel.hazeMode.collectAsState(DEFAULT_HAZE_MODE)
    val hazeMaterialPreset by settingsViewModel.hazeMaterialPreset.collectAsState(DEFAULT_HAZE_MATERIAL_PRESET)
    val hazeBlurRadius by settingsViewModel.hazeBlurRadius.collectAsState(DEFAULT_HAZE_BLUR_RADIUS)
    val hazeNoiseFactor by settingsViewModel.hazeNoiseFactor.collectAsState(DEFAULT_HAZE_NOISE_FACTOR)
    val hazeTintAlpha by settingsViewModel.hazeTintAlpha.collectAsState(DEFAULT_HAZE_TINT_ALPHA)
    val hazeIntensity by settingsViewModel.hazeIntensity.collectAsState(DEFAULT_HAZE_INTENSITY)

    val hazeState = rememberHazeState()

    // ── F14 横屏 / 宽窗适配 ──
    // ① 限宽居中：单列卡片在桌面、平板横屏下拉满会得到超长行。手机竖屏（Compact）不加约束，
    //    与改造前逐像素一致。
    // ② 手机横屏（isPhoneLandscape = 横屏 + 紧凑高度）：可用高度 < 480dp，而单列卡片的固有
    //    高度约 580dp（Logo 88 + 品牌区 + 间隔 + 240dp 步骤区 + 内边距），必然溢出滚动；
    //    卡内改左右分栏（左品牌 / 右步骤）后，高度需求降到约 220dp。
    // ③ 横屏一律收紧内外边距，为分栏或单列再让出一截垂直空间。
    //
    // 注意：这里必须用 rememberAppWindowSizeInfo() 自算，而不是消费 LocalWindowSizeInfo ——
    // 该 CompositionLocal 由 AppRoot 在其内部 provides，而首启引导在三个平台的宿主中
    // 都挂在 AppRoot 之前（Android MainActivity / Desktop Main.kt / iOS IosAppRootShell），
    // 消费本地必然抛「AppWindowSizeInfo not provided」并崩溃（真机已复现）。
    // 本函数只依赖 Compose 运行时恒有的 LocalWindowInfo / LocalDensity，与 AppRoot
    // 提供的值同源同算，行为一致。
    val window = rememberAppWindowSizeInfo()
    val isLandscape = window.isLandscape
    val maxCardWidth = when {
        window.isExpanded -> LayoutConstraints.ContentMaxWidthWide
        window.isMedium || isLandscape -> LayoutConstraints.ContentMaxWidthMedium
        else -> Dp.Unspecified
    }
    val useLandscapeLayout = window.isPhoneLandscape
    val rootVPadding = if (isLandscape) metrics.cozy else metrics.block
    val cardHPadding = if (isLandscape) metrics.section else metrics.block
    val cardVPadding = if (isLandscape) metrics.section else metrics.block

    // 权限请求走平台桥：Android 拉系统多权限（音频/通知）；iOS 媒体库权限已在
    // AppDelegate 引导（IosPermissionService.requestIntroPermissions 恒授权）
    val platformServices: PlatformServices = koinInject()
    val requestIntroPermissions: () -> Unit = {
        platformServices.permission.requestIntroPermissions { allGranted ->
            viewModel.onPermissionResult(allGranted)
        }
    }

    // 步骤内容区：竖屏整宽单列、手机横屏放右栏 —— 抽成 lambda 供两种布局共用，避免正文重复两份。
    val stepArea: @Composable (Modifier) -> Unit = { areaModifier ->
        Box(
            modifier = areaModifier,
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = introState.step,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = HmpMotion.enter(),
                    ) + fadeIn(
                        animationSpec = HmpMotion.enter(),
                    ) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = HmpMotion.exit(),
                        ) + fadeOut(
                            animationSpec = HmpMotion.exit(),
                        )
                },
                label = "step_transition",
            ) { step ->
                when (step) {
                    0 -> PermissionStep(
                        isPermissionGiven = introState.permissionGiven,
                        onRequestPermission = requestIntroPermissions,
                        onSkip = viewModel::onSkipPermission,
                    )

                    1 -> ScanMusicStep(
                        isScanCompleted = introState.scanCompleted,
                        onStartScan = viewModel::onScanRequested,
                        onScanComplete = viewModel::onScanCompleted,
                        showScanDialog = introState.showScanDialog,
                        isScanning = isScanning,
                        musicCount = musicCount,
                        hazeState = hazeState,
                    )

                    2 -> AiExperienceStep(
                        onFinished = onFinished,
                    )
                }
            }
        }
    }

    ProvideHazeRenderSettings(
        settings = HazeRenderSettings(
            mode = hazeMode,
            preset = hazeMaterialPreset,
            intensity = hazeIntensity,
            blurRadius = hazeBlurRadius,
            noiseFactor = hazeNoiseFactor,
            tintAlpha = hazeTintAlpha,
        ),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .hazeSource(state = hazeState)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 桌面：CustomTitleBar 悬浮叠加在内容之上，需为其让位。
                // 其余端 LocalTitleBarInset 默认为 0.dp，等价于无变化。
                .padding(top = LocalTitleBarInset.current)
                .padding(vertical = rootVPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── 统一容器：所有内容并入单一卡片 ──
            Surface(
                modifier = Modifier
                    // 宽窗限宽居中（F14 惯例）。手机竖屏 maxCardWidth = Dp.Unspecified → 不加约束。
                    .then(
                        if (maxCardWidth != Dp.Unspecified) {
                            Modifier.widthIn(max = maxCardWidth)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .padding(horizontal = metrics.cozy),
                shape = RoundedCornerShape(metrics.section),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = metrics.hair,
            ) {
                if (useLandscapeLayout) {
                    // ── 手机横屏：左右分栏 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = cardHPadding, vertical = cardVPadding),
                        horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ── 左栏：Logo + 品牌区 + 步骤指示器 ──
                        BrandBlock(
                            currentStep = introState.step,
                            totalSteps = 3,
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )

                        // ── 竖直细分隔线 ──
                        Box(
                            modifier = Modifier
                                .width(metrics.stroke.thin)
                                .height(LayoutConstraints.CoverHeightSmall)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                        )

                        // ── 右栏：步骤内容 ──
                        stepArea(
                            Modifier
                                .weight(1f)
                                .height(LayoutConstraints.PanelHeightLarge),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = cardHPadding)
                            .padding(top = cardVPadding, bottom = cardVPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ── 卡片顶部：Logo + 品牌区 ──
                        BrandBlock(
                            currentStep = introState.step,
                            totalSteps = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(metrics.section))

                        // ── 细分隔线 ──
                        HorizontalDivider(
                            modifier = Modifier.widthIn(max = LayoutConstraints.DialogMaxWidth),
                            thickness = metrics.stroke.thin,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        Spacer(modifier = Modifier.height(metrics.block))

                        // ── 步骤内容区（固定高度，保证三个步骤大小一致）──
                        stepArea(
                            Modifier
                                .fillMaxWidth()
                                .height(LayoutConstraints.DialogMaxWidth),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 胶囊式步骤指示器
 * - 已完成：实心圆点 + 主色
 * - 当前：拉伸为胶囊 + 主色
 * - 未到达：小圆点 + onSurface 低透明
 */
@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.micro, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isCurrent = index == currentStep
            val isDone = index < currentStep
            val color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            }
            Box(
                modifier = Modifier
                    .height(metrics.micro)
                    .then(
                        if (isCurrent) Modifier.width(metrics.section) else Modifier.size(metrics.micro),
                    )
                    .clip(RoundedCornerShape(metrics.hair))
                    .background(color),
            )
        }
    }
}

@Composable
fun PermissionStep(
    isPermissionGiven: Boolean,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.cozy, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.intro_step_1),
            style = typo.headline,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.intro_step_1_desc),
            style = typo.body,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        if (!isPermissionGiven) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.width(metrics.card),
            ) {
                Text(stringResource(Res.string.grant_permission))
            }
            // 逃生口：权限被永久拒绝后系统不再弹框（且本步原本只有「授权」一条路径），
            // 没有这个入口用户会永久卡在引导页。跳过后的「无权限 → 空库」由
            // LibrarySettingsScreen 的权限提示条兜住。
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(Res.string.intro_skip_permission),
                    style = typo.body.medium(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Button(
                onClick = { },
                colors = ButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.width(metrics.card),
            ) {
                Text(stringResource(Res.string.permission_granted))
            }
        }
    }
}

@Composable
fun ScanMusicStep(
    isScanCompleted: Boolean,
    showScanDialog: Boolean,
    onStartScan: () -> Unit,
    onScanComplete: () -> Unit,
    isScanning: Boolean,
    musicCount: Int,
    hazeState: HazeState,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.cozy, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.intro_step_2),
            style = typo.headline,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.intro_step_2_desc),
            style = typo.body,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        if (isScanCompleted) {
            Button(
                onClick = { },
                colors = ButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.width(metrics.card),
            ) {
                Text(stringResource(Res.string.scan_finished))
            }
        } else {
            Button(
                onClick = onStartScan,
                modifier = Modifier.width(metrics.card),
            ) {
                Text(stringResource(Res.string.scan))
            }
        }
    }

    if (showScanDialog) {
        MusicScanDialog(
            isLoading = isScanning,
            musicCount = musicCount,
            onDismiss = onScanComplete,
            hazeState = hazeState,
        )
    }
}

@Composable
fun AiExperienceStep(
    onFinished: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.cozy, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.intro_step_3),
            style = typo.headline,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.intro_step_3_desc),
            style = typo.body,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onFinished,
            modifier = Modifier.width(metrics.card),
        ) {
            Text(stringResource(Res.string.start_experience))
        }
    }
}

/**
 * 卡片顶部品牌区：Logo + 欢迎语 + 产品名 + 步骤指示器。
 *
 * - [compact] = false（竖屏单列）：Logo 88dp、产品名 headlineMedium、末间隔 28dp —— 与改造前一致。
 * - [compact] = true（手机横屏左栏，栏宽约 280dp）：Logo 收至 64dp、产品名降为 titleLarge，
 *   否则产品名单行宽度（约 260dp）在该栏宽下会折行；末间隔一并收紧以省垂直空间。
 */
@Composable
private fun BrandBlock(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = "Logo",
            modifier = Modifier.size(metrics.row),
        )
        Spacer(modifier = Modifier.height(if (compact) metrics.base else metrics.cozy))
        Text(
            text = stringResource(Res.string.welcome_to),
            style = typo.body.medium(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(metrics.micro))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Hearable",
                style = typo.headline,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " Music Player",
                style = typo.headline,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(if (compact) metrics.cozy else metrics.section))

        // ── 胶囊式步骤指示器 ──
        StepIndicator(
            currentStep = currentStep,
            totalSteps = totalSteps,
        )
    }
}
