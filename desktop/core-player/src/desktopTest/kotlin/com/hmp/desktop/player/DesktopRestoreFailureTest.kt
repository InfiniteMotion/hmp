package com.hmp.desktop.player

import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D1-08：启动恢复失败不再被空 catch 吞掉 —— 失败计数必须可见。
 *
 * 以前两处都是 `catch (_: Exception) {}`，DB/DataStore 异常时用户只看到
 * 「播放胶囊闪一下消失」，日志里什么都没有。
 */
class DesktopRestoreFailureTest {

    @Test
    fun restoreFailures_areCounted() = runTestWithMain {
        val settings = stubInterface<SettingsRepository> { name ->
            when (name) {
                "getCurrentPlaylistId" -> MutableStateFlow<Long?>(1L)

                "getLikedPlaylistId" -> MutableStateFlow<Long?>(2L)

                "getRecentPlaylistId" -> MutableStateFlow<Long?>(3L)

                // 两个恢复入口各抛一次，模拟 DB/DataStore 启动期异常
                "getCurrentMusicId" -> flow<Long?> { error("boom: currentMusicId") }

                "getCurrentPosition" -> flow<Long> { error("boom: currentPosition") }

                else -> null
            }
        }

        val controller = DesktopMusicController(
            audioEngine = FakeAudioEngine(),
            currentPlaybackUseCase = CurrentPlaybackUseCase(
                stubInterface<MusicRepository>(),
                stubInterface<PlaylistRepository>(),
                settings,
            ),
            playbackHistoryUseCase = PlaybackHistoryUseCase(stubInterface<MusicRepository>()),
            timerUseCase = TimerUseCase(settings),
            managePlaylistUseCase = ManagePlaylistUseCase(stubInterface<PlaylistRepository>(), settings),
            settingsRepository = settings,
        )

        runCurrent()

        assertEquals(2, controller.restoreFailures.value, "playlist 与 position 两次恢复失败都应被计数")
    }
}
