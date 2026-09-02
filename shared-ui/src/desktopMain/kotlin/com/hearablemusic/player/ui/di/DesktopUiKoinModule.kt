package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.DesktopMusicControllerPlaybackAdapter
import com.hearablemusic.player.ui.platform.DesktopPlatformServices
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.platform.SkiaAlbumArtPixelsLoader
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import com.hearablemusic.player.ui.library.viewmodel.EditMusicTagsViewModel
import com.hearablemusic.player.ui.library.viewmodel.SearchViewModel
import com.hearablemusic.player.ui.library.viewmodel.SongDetailViewModel
import com.hearablemusic.player.ui.chat.ChatViewModel
import com.hearablemusic.player.ui.chat.chatGatewayModule
import com.hearablemusic.player.ui.player.viewmodel.PlaybackViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlaylistQueueViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.ArtistAlbumViewModel
import com.hearablemusic.player.ui.settings.viewmodel.AudioEffectViewModel
import com.hearablemusic.player.ui.settings.viewmodel.AiSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.BackupViewModel
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.UserUsageDataViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * desktopMain �?UI Koin 模块�? *
 * 注册面镜�?androidMain UiKoinModule（commonMain �?VM 类完全相同，构造参数一致）�? * 差异仅平台桥三件�? * - PlaybackController �?FFmpeg 引擎适配器（DesktopMusicController �? *   :desktop:core-player �?desktopPlayerModule 注册�? * - AlbumArtPixelsLoader �?skiko 实现（无 Context 依赖�? * - PlatformServices �?桌面聚合实现（无宿主 Activity，可直接注册；Android 侧因
 *   launcher 需�?Activity registry 而在 MainActivity 动态注册）
 *
 * 装配时机：desktop/app 壳连�?desktopPlayerModule 等既有模块一起加载�? */
val desktopUiModule = module {
    includes(chatGatewayModule)

    single { DialogManager() }

    // 平台桥三�?    single<PlaybackController> { DesktopMusicControllerPlaybackAdapter(get()) }
    single<AlbumArtPixelsLoader> { SkiaAlbumArtPixelsLoader() }
    single<PlatformServices> { DesktopPlatformServices() }

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
    viewModel { RecommendationViewModel(get(), get(), get(), get(), get()) }
    viewModel { UserUsageDataViewModel(get()) }
    viewModel { ChatViewModel(get(), get(), get(), get()) }
    viewModel { ThemeViewModel(get(), get()) }
}
