package com.hmp.desktop.player.di

import com.hmp.desktop.player.AudioEngine
import com.hmp.desktop.player.DesktopMusicController
import com.hmp.desktop.player.FFmpegAudioEngine
import org.koin.dsl.module

val desktopPlayerModule = module {
    single<AudioEngine> { FFmpegAudioEngine() }
    single {
        DesktopMusicController(
            audioEngine = get(),
            currentPlaybackUseCase = get(),
            playbackHistoryUseCase = get(),
            timerUseCase = get(),
            managePlaylistUseCase = get(),
            settingsRepository = get(),
        )
    }
}
