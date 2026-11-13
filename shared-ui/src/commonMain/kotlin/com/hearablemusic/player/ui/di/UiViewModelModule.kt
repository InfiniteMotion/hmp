package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.AppRootViewModel
import com.hearablemusic.player.ui.agent.cards.HelloCardsViewModel
import com.hearablemusic.player.ui.agent.chat.ChatViewModel
import com.hearablemusic.player.ui.agent.chat.chatGatewayModule
import com.hearablemusic.player.ui.agent.config.AgentConfigViewModel
import com.hearablemusic.player.ui.agent.config.AiSettingsViewModel
import com.hearablemusic.player.ui.agent.monitor.AgentMonitorViewModel
import com.hearablemusic.player.ui.agent.monitor.AuditLogViewModel
import com.hearablemusic.player.ui.agent.shell.RadioConsoleController
import com.hearablemusic.player.ui.agent.shell.RadioConsoleViewModel
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.pages.IntroViewModel
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.library.viewmodel.EditMusicTagsViewModel
import com.hearablemusic.player.ui.library.viewmodel.GalleryViewModel
import com.hearablemusic.player.ui.library.viewmodel.HomeViewModel
import com.hearablemusic.player.ui.library.viewmodel.LibrarySettingsViewModel
import com.hearablemusic.player.ui.library.viewmodel.RecommendListViewModel
import com.hearablemusic.player.ui.library.viewmodel.SearchViewModel
import com.hearablemusic.player.ui.library.viewmodel.SongDetailViewModel
import com.hearablemusic.player.ui.player.viewmodel.PlayerScreenViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.ArtistAlbumViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistEditorViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistManageViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistScreenViewModel
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import com.hearablemusic.player.ui.settings.viewmodel.AudioEffectViewModel
import com.hearablemusic.player.ui.settings.viewmodel.BackupViewModel
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsPageViewModel
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.ProfileSettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.RecommendationViewModel
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import com.hearablemusic.player.ui.settings.viewmodel.UserUsageDataViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * UI 层跨端注册的单一真源（7.5 收口）。
 *
 * 原先同一份 `viewModel {}` 在 Android / Desktop / iOS 三个模块各抄一份：改一个构造参数要同改三处，
 * 而 iOS 那份在本机既不编译、也进不了图校验，漏一端的后果是运行期 `NoDefinitionFound`。
 * VM 类与它们的构造依赖都在 commonMain，所以注册也只有在 commonMain 一份才合理。
 *
 * 平台模块只保留真正分叉的绑定：
 * - `PlaybackController`（Media3 / FFmpeg / AVPlayer 三套适配）
 * - `AlbumArtPixelsLoader`（Android 走 Coil + `androidContext()`）
 * - `PlatformServices`（三端的分享 / 选目录 / 权限 / 触觉门面）
 *
 * 新增 VM 时只改这里；`get()` 仍按构造参数的期望类型解析，缺定义由三端图校验 + 启动兜底
 * （GraphVerificationTest 不实例化定义，见 AGENTS §八.4）。
 */
val uiViewModelModule = module {
    includes(chatGatewayModule)

    // 跨端单例（无平台依赖）
    single { DialogManager() }
    single { RadioConsoleController() }

    viewModel { DialogManagerViewModel(get()) }
    viewModel { DialogViewModel(get(), get(), get(), get(), get()) }

    viewModel { GalleryViewModel(get(), get(), get(), get(), get()) }
    viewModel { LibrarySettingsViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { SongDetailViewModel(get(), get()) }
    viewModel { RecommendListViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { PlayerScreenViewModel(get(), get(), get(), get(), get()) }
    viewModel { HelloCardsViewModel(getOrNull(), get(), get(), getOrNull()) }
    viewModel { EditMusicTagsViewModel(get(), get(), get()) }
    viewModel { PlaylistViewModel(get(), get(), get()) }
    viewModel { PlaylistScreenViewModel(get(), get(), get(), get(), get()) }
    viewModel { PlaylistManageViewModel(get()) }
    viewModel { PlaylistEditorViewModel(get(), get()) }
    viewModel { ArtistAlbumViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { ProfileSettingsViewModel(get(), get()) }
    viewModel { AudioEffectViewModel(get()) }
    viewModel { BackupViewModel(get(), get(), get(), get(), get()) }
    viewModel { AiSettingsViewModel(get(), get(), get(), get()) }
    viewModel { AgentConfigViewModel(get(), get()) }
    viewModel { LyricsSettingsViewModel(get()) }
    viewModel { LyricsSettingsPageViewModel(get(), get()) }
    viewModel { RecommendationViewModel(get(), get()) }
    viewModel { UserUsageDataViewModel(get<com.hmp.domain.music.MusicRepository>(), get<com.hmp.domain.agent.runtime.MasterAgent>()) }
    viewModel { ChatViewModel(get(), get(), get(), get(), get()) }
    viewModel { AuditLogViewModel(get()) }
    viewModel { AgentMonitorViewModel(get(), get(), get()) }
    viewModel { RadioConsoleViewModel(get()) }
    viewModel { ThemeViewModel(get(), get()) }
    viewModel { AppRootViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { IntroViewModel(get(), get(), get()) }
}
