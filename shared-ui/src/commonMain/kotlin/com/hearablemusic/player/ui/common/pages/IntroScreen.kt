package com.hearablemusic.player.ui.common.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.dialogs.MusicScanDialog
import com.hearablemusic.player.ui.common.design.animation.AnimationTokens
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
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject

@Composable
fun IntroScreen(
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    recommendationViewModel: RecommendationViewModel,
    onFinished: ()-> Unit,
    /**
     * 是否跳过首个「权限」步（桌面：无运行时权限体系，该步无意义）。
     *
     * 依据：旧桌面 UI 层曾有一份同构实现，内中即写明
     * `// Desktop: skip permission step, start at scan` 且 `isPermissionGiven = true`。
     * 默认 false —— Android / iOS 行为不变。
     */
    skipPermissionStep: Boolean = false
) {
    val currentStep = remember { mutableIntStateOf(if (skipPermissionStep) 1 else 0) }
    val isPermissionGiven = remember { mutableStateOf(skipPermissionStep) }
    val showScanDialog = remember { mutableStateOf(false) }
    val isScanCompleted = remember { mutableStateOf(false) }
    val userSettingsUseCase: UserSettingsUseCase = koinInject()
    val aiAccessMode by userSettingsUseCase.aiAccessMode.collectAsState(AiAccessMode.FREE)
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
        window.isExpanded -> 720.dp
        window.isMedium || isLandscape -> 640.dp
        else -> Dp.Unspecified
    }
    val useLandscapeLayout = window.isPhoneLandscape
    val rootVPadding = if (isLandscape) 16.dp else 32.dp
    val cardHPadding = if (isLandscape) 24.dp else 32.dp
    val cardVPadding = if (isLandscape) 24.dp else 40.dp
    
    // 权限授予后自动进入下一步
    LaunchedEffect(isPermissionGiven.value) {
        if (isPermissionGiven.value && currentStep.intValue == 0) {
            currentStep.intValue = 1
        }
    }

    // 权限请求走平台桥：Android 拉系统多权限（音频/通知）；iOS 媒体库权限已在
    // AppDelegate 引导（IosPermissionService.requestIntroPermissions 恒授权）
    val platformServices: PlatformServices = koinInject()
    val requestIntroPermissions: () -> Unit = {
        platformServices.permission.requestIntroPermissions { allGranted ->
            isPermissionGiven.value = allGranted
        }
    }

    // 步骤内容区：竖屏整宽单列、手机横屏放右栏 —— 抽成 lambda 供两种布局共用，避免正文重复两份。
    val stepArea: @Composable (Modifier) -> Unit = { areaModifier ->
        Box(
            modifier = areaModifier,
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep.intValue,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = AnimationTokens.EASE_OUT
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = AnimationTokens.EASE_OUT
                        )
                    ) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = AnimationTokens.EASE_IN
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = AnimationTokens.EASE_IN
                        )
                    )
                },
                label = "step_transition"
            ) { step ->
                when (step) {
                    0 -> PermissionStep(
                        isPermissionGiven = isPermissionGiven.value,
                        onRequestPermission = requestIntroPermissions,
                        onSkip = { currentStep.intValue = 1 }
                    )
                    1 -> ScanMusicStep(
                        isScanCompleted = isScanCompleted.value,
                        onStartScan = {
                            libraryViewModel.refreshMusicList()
                            showScanDialog.value = true
                        },
                        onScanComplete = {
                            showScanDialog.value = false
                            isScanCompleted.value = true
                            currentStep.intValue = 2
                            // 首次扫描完成后，若为免费体验模式则自动触发 AI 批量补全
                            // isLoadMusic 已在 LibraryViewModel 扫描成功后持久化
                            if (aiAccessMode == AiAccessMode.FREE) {
                                recommendationViewModel.startAutoProcessWithCurrentProvider()
                            }
                        },
                        showScanDialog = showScanDialog.value,
                        libraryViewModel = libraryViewModel,
                        hazeState = hazeState
                    )
                    2 -> AiExperienceStep(
                        onFinished = onFinished
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
            tintAlpha = hazeTintAlpha
        )
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 统一容器：所有内容并入单一卡片 ──
            Surface(
                modifier = Modifier
                    // 宽窗限宽居中（F14 惯例）。手机竖屏 maxCardWidth = Dp.Unspecified → 不加约束。
                    .then(
                        if (maxCardWidth != Dp.Unspecified) Modifier.widthIn(max = maxCardWidth)
                        else Modifier
                    )
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                if (useLandscapeLayout) {
                    // ── 手机横屏：左右分栏 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = cardHPadding, vertical = cardVPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── 左栏：Logo + 品牌区 + 步骤指示器 ──
                        BrandBlock(
                            currentStep = currentStep.intValue,
                            totalSteps = 3,
                            modifier = Modifier.weight(1f),
                            compact = true
                        )

                        // ── 竖直细分隔线 ──
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(160.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                        )

                        // ── 右栏：步骤内容 ──
                        stepArea(
                            Modifier
                                .weight(1f)
                                .height(220.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = cardHPadding)
                            .padding(top = cardVPadding, bottom = cardVPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ── 卡片顶部：Logo + 品牌区 ──
                        BrandBlock(
                            currentStep = currentStep.intValue,
                            totalSteps = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // ── 细分隔线 ──
                        HorizontalDivider(
                            modifier = Modifier.widthIn(max = 240.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // ── 步骤内容区（固定高度，保证三个步骤大小一致）──
                        stepArea(
                            Modifier
                                .fillMaxWidth()
                                .height(240.dp)
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
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
                    .height(6.dp)
                    .then(
                        if (isCurrent) Modifier.width(28.dp) else Modifier.size(6.dp)
                    )
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun PermissionStep(
    isPermissionGiven: Boolean,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(Res.string.intro_step_1),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(Res.string.intro_step_1_desc),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        if (!isPermissionGiven) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.width(200.dp)
            ) {
                Text(stringResource(Res.string.grant_permission))
            }
            // 逃生口：权限被永久拒绝后系统不再弹框（且本步原本只有「授权」一条路径），
            // 没有这个入口用户会永久卡在引导页。跳过后的「无权限 → 空库」由
            // LibrarySettingsScreen 的权限提示条兜住。
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(Res.string.intro_skip_permission),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.width(200.dp)
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
    libraryViewModel: LibraryViewModel?,
    hazeState: HazeState
) {
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(Res.string.intro_step_2),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(Res.string.intro_step_2_desc),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
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
                modifier = Modifier.width(200.dp)
            ) {
                Text(stringResource(Res.string.scan_finished))
            }
        } else {
            Button(
                onClick = onStartScan,
                modifier = Modifier.width(200.dp)
            ) {
                Text(stringResource(Res.string.scan))
            }
        }
    }

    if (showScanDialog && libraryViewModel != null) {
        MusicScanDialog(
            libraryViewModel = libraryViewModel,
            onDismiss = onScanComplete,
            hazeState = hazeState
        )
    }
}

@Composable
fun AiExperienceStep(
    onFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(Res.string.intro_step_3),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(Res.string.intro_step_3_desc),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onFinished,
            modifier = Modifier.width(200.dp)
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = "Logo",
            modifier = Modifier.size(if (compact) 64.dp else 88.dp),
        )
        Spacer(modifier = Modifier.height(if (compact) 12.dp else 16.dp))
        Text(
            text = stringResource(Res.string.welcome_to),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        val nameStyle =
            if (compact) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.headlineMedium
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Hearable",
                style = nameStyle,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " Music Player",
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(if (compact) 20.dp else 28.dp))

        // ── 胶囊式步骤指示器 ──
        StepIndicator(
            currentStep = currentStep,
            totalSteps = totalSteps
        )
    }
}
