package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.library.viewmodel.EditMusicTagsViewModel
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.library.viewmodel.SearchViewModel
import com.hearablemusic.player.ui.library.viewmodel.SongDetailViewModel
import com.hearablemusic.player.ui.chat.ChatViewModel
import com.hearablemusic.player.ui.chat.chatGatewayModule
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.ArtistAlbumViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.IosAlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.IosPlaybackController
import com.hearablemusic.player.ui.platform.IosPlatformServices
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.settings.viewmodel.AiSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.AudioEffectViewModel
import com.hearablemusic.player.ui.settings.viewmodel.BackupViewModel
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.UserUsageDataViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * iosMain 的 UI Koin 模块（A3，镜像 desktopMain DesktopUiKoinModule）。
 *
 * 注册面与 android/desktop 完全一致（commonMain VM 类相同、构造参数相同），
 * 差异仅平台桥三件：
 * - PlaybackController → IosPlaybackController（Swift 引擎双桥：状态汇聚 + 命令闭包）
 * - AlbumArtPixelsLoader → skiko 实现（IosAlbumArtPixelsLoader，与 Desktop 同构）
 * - PlatformServices → IosPlatformServices（Swift 闭包桥 + Taptic Engine 触觉）
 *
 * 装配时机：ios 壳（AppDelegate）在 KoinInitializer 之后以
 * `installKoinIosWithSharedUi()` 一并注册（含 shared 的 sharedModule + iosPlatformModule）。
 */
val iosUiModule = module {
    includes(chatGatewayModule)

    single { DialogManager() }

    // 平台桥三件（PlaybackController 为单例 object，Swift 桥与 Koin 共用同一实例）
    single<PlaybackController> { IosPlaybackController }
    single<AlbumArtPixelsLoader> { IosAlbumArtPixelsLoader() }
    single<PlatformServices> { IosPlatformServices() }

    viewModel { DialogManagerViewModel(get()) }
    viewModel { DialogViewModel(get(), get(), get(), get(), get(), get()) }

    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { SongDetailViewModel(get(), get()) }
    viewModel { EditMusicTagsViewModel(get(), get(), get()) }
    viewModel { PlaybackViewModel(get()) }
    viewModel { PlaylistQueueViewModel(get(), get(), get(), get()) }
    viewModel { PlaylistViewModel(get(), get(), get(), get()) }
    viewModel { ArtistAlbumViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { AudioEffectViewModel(get()) }
    viewModel { BackupViewModel(get(), get(), get(), get()) }
    viewModel { AiSettingsViewModel(get(), get()) }
    viewModel { LyricsSettingsViewModel(get()) }
    viewModel { RecommendationViewModel(get(), get(), get(), get()) }
    viewModel { UserUsageDataViewModel(get()) }
    viewModel { ChatViewModel(get(), get(), get(), get()) }
    viewModel { ThemeViewModel(get(), get()) }
}

/** iOS 壳一次性装配入口：shared（业务+平台） + shared-ui 全部 Koin 模块。 */
fun installKoinIosWithSharedUi() {
    com.hmp.di.initKoinIos(iosUiModule)
}