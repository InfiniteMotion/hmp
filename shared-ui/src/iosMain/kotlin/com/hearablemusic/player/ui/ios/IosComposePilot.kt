package com.hearablemusic.player.ui.ios

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.theme.ThemeManager
import com.hearablemusic.player.ui.common.design.typography.HMPTypoTokens
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.m3ComponentTypography
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.rememberAppWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.navigation.rememberHmpNavBackStack
import com.hearablemusic.player.ui.common.util.LocalAppViewModelStoreOwner
import com.hearablemusic.player.ui.settings.pages.BackupSettingsScreen
import com.hearablemusic.player.ui.settings.pages.LibrarySettingsScreen
import com.hearablemusic.player.ui.settings.pages.LyricsSettingsPage
import com.hearablemusic.player.ui.settings.pages.ProfileSettingsScreen
import com.hearablemusic.player.ui.settings.pages.SettingScreen
import platform.UIKit.UIViewController

/**
 * iOS 应用级 ViewModelStoreOwner（A5 试点）。
 * 对应 MainActivity / Desktop 主窗口的 LocalAppViewModelStoreOwner 提供者：
 * activityViewModel() 绑定此 owner，保证页面间共享 VM 单例。
 */
class IosAppViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * A2/A5 spike：设置中心 + 设置子页的独立导航图。
 *
 * 直接使用 navigation3 的 entryProvider 声明式 API（与 AppRoot 的 navigationGraph 同模式），
 * 验证 iOS 上 NavDisplay 转场 / 返回手势 / 保存状态（Saveable + ViewModel entry decorator）。
 */
@Composable
fun settingsPilotGraph(navController: NavBackStack<NavKey>) = entryProvider<NavKey> {
    entry<Routes.Settings.Setting> {
        SettingScreen(navController)
    }
    entry<Routes.Settings.ProfileSettings> {
        ProfileSettingsScreen(navController = navController)
    }
    entry<Routes.Settings.BackupSettings> {
        BackupSettingsScreen(navController = navController)
    }
    entry<Routes.Settings.LibrarySettings> {
        LibrarySettingsScreen(navController = navController)
    }
    entry<Routes.Settings.LyricsSettings> {
        LyricsSettingsPage(
            onBack = { navController.removeLastOrNull() },
        )
    }
}

/**
 * 试点根组合（设置中心 + 子页）。
 *
 * 壳职责（对齐 AppRoot 的裁剪版）：
 * 1. CompositionLocalProvider 提供应用级 ViewModelStoreOwner
 * 2. MaterialTheme + 字阶内部投影（HarmonyOS Sans 字体族由 composeResources 注入，业务排版走 LocalHmpTypo）
 * 3. NavDisplay 承载设置模块导航
 */
@Composable
fun IosSettingsPilotRoot() {
    val storeOwner = remember { IosAppViewModelStoreOwner() }
    val windowSizeInfo = rememberAppWindowSizeInfo()
    // 字阶 9 角色 + 统一尺寸刻度 12 档（共用同一档位判据，业务走两个 CompositionLocal）
    val typo = HMPTypoTokens.rememberHmpTypo()
    val metrics = HMPMetricTokens.rememberHmpMetrics()
    CompositionLocalProvider(LocalAppViewModelStoreOwner provides storeOwner) {
        MaterialTheme(
            colorScheme = ThemeManager.getLightColorScheme(),
            typography = m3ComponentTypography(typo),
        ) {
            CompositionLocalProvider(
                LocalWindowSizeInfo provides windowSizeInfo,
                LocalHmpTypo provides typo,
                LocalHmpMetrics provides metrics,
            ) {
                val navController = rememberHmpNavBackStack(Routes.Settings.Setting)
                NavDisplay(
                    backStack = navController,
                    onBack = { navController.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = settingsPilotGraph(navController),
                )
            }
        }
    }
}

/**
 * Swift 壳调用入口（A5）：创建设置中心试点 ViewController。
 *
 * Swift 侧示例：
 * ```swift
 * let vc = IosComposePilotKt.createSettingsPilotViewController()
 * present(vc, animated: true)
 * ```
 */
fun createSettingsPilotViewController(): UIViewController = ComposeUIViewController { IosSettingsPilotRoot() }
