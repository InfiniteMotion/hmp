package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.DesktopMusicControllerPlaybackAdapter
import com.hearablemusic.player.ui.platform.DesktopPlatformServices
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.platform.SkiaAlbumArtPixelsLoader
import org.koin.dsl.module

/**
 * desktopMain 专属 UI 装配：跨端注册来自 [uiViewModelModule]，本模块只有平台桥三件套。
 *
 * - `PlaybackController` → FFmpeg 引擎适配器（DesktopMusicController 由 :desktop:core-player 的 desktopPlayerModule 注册）
 * - `AlbumArtPixelsLoader` → skiko 实现（无 Context 依赖）
 * - `PlatformServices` → 桌面聚合实现（无宿主 Activity，可直接注册；Android 侧因 launcher 需 Activity registry 而在 MainActivity 动态注册）
 *
 * 装配时机：desktop/app 壳连同 desktopPlayerModule 等既有模块一起加载。
 */
val desktopUiModule = module {
    includes(uiViewModelModule)

    single<PlaybackController> { DesktopMusicControllerPlaybackAdapter(get()) }
    single<AlbumArtPixelsLoader> { SkiaAlbumArtPixelsLoader() }
    single<PlatformServices> { DesktopPlatformServices() }
}
