package com.hearablemusic.player.ui.ios

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.hearablemusic.player.ui.AppRoot
import com.hearablemusic.player.ui.common.design.theme.HearableMusicPlayerTheme
import com.hearablemusic.player.ui.common.pages.IntroScreen
import com.hearablemusic.player.ui.common.util.LocalAppViewModelStoreOwner
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import platform.UIKit.UIViewController

/**
 * iOS 应用根组合（A6：「全面替换」入口）。
 *
 * 对应 Android MainActivity 的 Compose 宿主职责（v7.1 iOS 同步首启引导，IntroScreen
 * 已迁 commonMain 三端共享）：
 * 1. CompositionLocalProvider 提供应用级 ViewModelStoreOwner（activityViewModel 绑定）
 * 2. 主题模式：读 SettingsViewModel.customMode（"light"/"dark"/"default"）→ darkTheme
 * 3. 首启分流：isFirstLaunch=true 时显示 IntroScreen（三步：权限→扫描→AI 体验），
 *    完成回调写 isFirstLaunch=false 后进入 AppRoot
 * 4. 渲染共享层完整应用壳 AppRoot（MainShell 4 Tab + 全部二级页 + BottomFusionBar/
 *    TabPageIndicator/动态背景/全局对话框）
 *
 * Swift 壳（ContentView）承载 `createAppRootViewController()` 即完成入口。
 */
@Composable
fun IosAppRootShell() {
    val storeOwner = remember { IosAppViewModelStoreOwner() }
    CompositionLocalProvider(LocalAppViewModelStoreOwner provides storeOwner) {
        val settingsViewModel: SettingsViewModel = activityViewModel()
        val isFirstLaunch by settingsViewModel.isFirstLaunch.collectAsState(true)
        val customMode by settingsViewModel.customMode.collectAsState("default")
        val darkTheme = when (customMode) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
        HearableMusicPlayerTheme(darkTheme = darkTheme) {
            if (isFirstLaunch) {
                val recommendationViewModel: RecommendationViewModel = activityViewModel()
                IntroScreen(
                    settingsViewModel = settingsViewModel,
                    recommendationViewModel = recommendationViewModel,
                    onFinished = {
                        settingsViewModel.saveIsFirstLaunchStatus(false)
                    },
                )
            } else {
                AppRoot(darkTheme = darkTheme)
            }
        }
    }
}

/** Swift 壳调用入口：创建完整 Compose 应用根 ViewController。 */
fun createAppRootViewController(): UIViewController = ComposeUIViewController { IosAppRootShell() }
