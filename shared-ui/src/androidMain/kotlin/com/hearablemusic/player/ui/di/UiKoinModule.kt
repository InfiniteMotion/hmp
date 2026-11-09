package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.CoilAlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.MusicControllerPlaybackAdapter
import com.hearablemusic.player.ui.platform.PlaybackController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android 侧 UI 装配：跨端注册全部来自 [uiViewModelModule]，本模块只保留平台分叉绑定。
 *
 * - `PlaybackController` → Media3 ExoPlayer 适配器
 * - `AlbumArtPixelsLoader` → Coil（需要 `androidContext()`）
 * - `PlatformServices` 不在这里：它依赖 Activity 的 launcher registry，由 MainActivity 动态注册
 *
 * 新增 / 拆分 VM 只改 `commonMain/.../di/UiViewModelModule.kt`，不要再往三端模块各抄一份。
 */
val uiModule = module {
    includes(uiViewModelModule)

    single<PlaybackController> { MusicControllerPlaybackAdapter(get()) }
    single<AlbumArtPixelsLoader> { CoilAlbumArtPixelsLoader(androidContext()) }
}
