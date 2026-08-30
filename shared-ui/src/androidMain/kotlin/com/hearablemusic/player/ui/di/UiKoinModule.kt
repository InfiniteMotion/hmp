package com.hearablemusic.player.ui.di

import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel
import com.hearablemusic.player.ui.platform.AlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.CoilAlbumArtPixelsLoader
import com.hearablemusic.player.ui.platform.MusicControllerPlaybackAdapter
import com.hearablemusic.player.ui.platform.PlaybackController
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
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    includes(chatGatewayModule)

    single { DialogManager() }
    single<PlaybackController> { MusicControllerPlaybackAdapter(get()) }
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
    single<AlbumArtPixelsLoader> { CoilAlbumArtPixelsLoader(androidContext()) }
    viewModel { ThemeViewModel(get(), get()) }
}
