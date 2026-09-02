package com.hmp.data.di

import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.network.OpenAiLlmTransport
import com.hmp.data.network.createHttpClient
import com.hmp.data.network.createJson
import com.hmp.domain.agent.port.LlmTransport
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDailyMusicRecommendationUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.music.usecase.LoadMusicFromDeviceUseCase
import com.hmp.domain.music.usecase.EditMusicTagsUseCase
import com.hmp.domain.music.usecase.MusicLabelUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.music.usecase.SearchMusicUseCase
import com.hmp.domain.music.usecase.SyncMusicFromDeviceIncrementalUseCase
import com.hmp.domain.playlist.usecase.GeneratePlaylistUseCase
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.GetUserUsageDataUseCase
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hmp.domain.backup.usecase.DeleteBackupUseCase
import com.hmp.domain.backup.usecase.ExportUserDataBackupUseCase
import com.hmp.domain.backup.usecase.GetBackupsUseCase
import com.hmp.domain.backup.usecase.ImportUserDataBackupUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * 跨平台共享 Koin 模块 — 包含所有平台通用的依赖。
 * 平台特定模块（Database、Repository）在 androidMain/iosMain 分别提供。
 */
val sharedModule = module {
    single { createJson() }

    single { createHttpClient(get()) }

    singleOf(::OpenAiCompatibleAdapter)
    single<LlmTransport> { OpenAiLlmTransport(adapter = get(), json = get()) }

    // Use Cases
    single { GetAllMusicUseCase(get()) }
    single { SearchMusicUseCase(get()) }
    single { LoadMusicFromDeviceUseCase(get()) }
    single { SyncMusicFromDeviceIncrementalUseCase(get()) }
    single { RemoveFromLibraryUseCase(get()) }
    single { RestoreToLibraryUseCase(get()) }
    single { GetDeletedMusicIdsGroupedByFolderUseCase(get()) }
    single { MusicLabelUseCase(get(), get()) }
    single { EditMusicTagsUseCase(get()) }
    single { GetDailyMusicRecommendationUseCase(get(), get()) }
    single { ManagePlaylistUseCase(get(), get()) }
    single { GeneratePlaylistUseCase(get(), get()) }
    single { UserSettingsUseCase(get()) }
    single { LyricsSettingsUseCase(get()) }
    single { CurrentPlaybackUseCase(get(), get(), get()) }
    single { PlaybackHistoryUseCase(get()) }
    single { GetUserUsageDataUseCase(get()) }
    single { TimerUseCase(get()) }
    single { ExportUserDataBackupUseCase(get(), get(), get(), get()) }
    single { ImportUserDataBackupUseCase(get(), get(), get(), get()) }
    single { GetBackupsUseCase(get()) }
    single { DeleteBackupUseCase(get()) }
}
