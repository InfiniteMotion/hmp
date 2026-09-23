package com.hearablemusic.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.hearablemusic.player.player.controller.MusicController
import com.hearablemusic.player.ui.AppRoot
import com.hearablemusic.player.ui.common.design.theme.HearableMusicPlayerTheme
import com.hearablemusic.player.ui.common.pages.IntroScreen
import com.hearablemusic.player.ui.common.util.LocalAppViewModelStoreOwner
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.platform.AndroidPlatformServices
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

@UnstableApi
class MainActivity : ComponentActivity() {

    private val musicController: MusicController by inject()
    private val userSettingsUseCase: UserSettingsUseCase by inject()

    private val settingsViewModel: SettingsViewModel by viewModel()
    private val libraryViewModel: LibraryViewModel by viewModel()
    private val recommendationViewModel: RecommendationViewModel by viewModel()


    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        musicController.setTargetActivityClass(MainActivity::class.java)
        musicController.bindService()
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        super.onDestroy()
        musicController.release()
        musicController.unbindService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 平台服务注册（分享/文件选择/悬浮窗权限/标签编辑桥/触觉/悬浮歌词）。
        // 构造需宿主 Activity（launcher 挂其 registry），故在 Activity 侧注册而非 UiKoinModule。
        val platformServices = AndroidPlatformServices(applicationContext, this)
        loadKoinModules(
            module {
                single<PlatformServices> { platformServices }
            }
        )

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalAppViewModelStoreOwner provides this
            ) {
                val customMode by settingsViewModel.customMode.collectAsState("default")
                val darkTheme = when (customMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                // 首启引导（旧版 MainActivity 行为恢复）：
                // 初始值给 true——避免冷启动第一帧因默认 false 误判为非首次启动而跳过 Intro；
                // DataStore 异步加载后更新为真实值（老用户为 false，新用户为 true）
                val isFirstLaunch by settingsViewModel.isFirstLaunch.collectAsState(true)
                val autoBatchProcess by userSettingsUseCase.autoBatchProcess.collectAsState(false)
                val context = LocalContext.current

                // 权限授予后的初始化副作用（旧版行为恢复）：
                // 头像已由 SettingsViewModel.init 承接；此处保留自动批处理
                // （每日推荐生成的调度已由 MasterAgent → HelloSubAgent 的 dailyRefreshLoop 接管）
                LaunchedEffect(Unit) {
                    val musicReadGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (musicReadGranted) {
                        if (autoBatchProcess) {
                            delay(2000)
                            recommendationViewModel.startAutoProcessWithCurrentProvider()
                        }
                    }
                }

                if (isFirstLaunch) {
                    HearableMusicPlayerTheme(darkTheme = darkTheme) {
                        IntroScreen(
                            settingsViewModel = settingsViewModel,
                            libraryViewModel = libraryViewModel,
                            recommendationViewModel = recommendationViewModel,
                            onFinished = {
                                settingsViewModel.saveIsFirstLaunchStatus(false)
                            }
                        )
                    }
                } else {
                    AppRoot(darkTheme = darkTheme)
                }
            }
        }
    }
}
