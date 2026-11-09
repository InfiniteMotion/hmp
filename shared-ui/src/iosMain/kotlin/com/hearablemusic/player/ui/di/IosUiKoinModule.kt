package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.IosAlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.IosPlatformServices
import com.hearablemusic.player.ui.platform.IosPlaybackController
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.data.database.AppDatabaseGuard
import com.hmp.data.database.hmpDatabasePath
import org.koin.dsl.module

/**
 * iosMain 侧 UI 装配（A3）：跨端注册与 android / desktop 共用 commonMain 的 [uiViewModelModule]，
 * 本模块只有平台桥三件：
 * - PlaybackController → IosPlaybackController（Swift 引擎双桥：状态汇总 + 命令闭包）
 * - AlbumArtPixelsLoader → skiko 实现（IosAlbumArtPixelsLoader，与 Desktop 同构）
 * - PlatformServices → IosPlatformServices（Swift 闭包 + Taptic Engine 触觉）
 *
 * 装配时机：ios 壳（AppDelegate）在 KoinInitializer 之后调 `installKoinIosWithSharedUi()`，
 * 一并注册 shared 侧的 sharedModule + iosPlatformModule。
 */
val iosUiModule = module {
    includes(uiViewModelModule)

    // 平台桥三件（PlaybackController 为单例 object，Swift 桥与 Koin 共用同一实例）。
    // ⚠️ 这一行曾因文件编码损坏被并进上一行的 `//` 注释里 —— 编译不报错（注释合法），
    // 但 `single<PlaybackController>` 实际从未注册，commonMain 的 `koinInject<PlaybackController>()`
    // 在 iOS 上会抛 NoDefinitionFound。改动这段注释时别把代码行粘进注释。
    single<PlaybackController> { IosPlaybackController }
    single<AlbumArtPixelsLoader> { IosAlbumArtPixelsLoader() }
    single<PlatformServices> { IosPlatformServices() }
}

/** iOS 壳一次性装配入口：shared（业务 + 平台）+ shared-ui 的全部 Koin 模块。 */
fun installKoinIosWithSharedUi() {
    // 降级守卫（D7-10）：Swift 侧不必知道这件事 —— AppRoot 的分流读的是同一个状态
    AppDatabaseGuard.probe(hmpDatabasePath())
    com.hmp.di.initKoinIos(iosUiModule)
}
