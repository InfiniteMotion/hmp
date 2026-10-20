package com.hearablemusic.player.player.controller

import android.content.Context
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 一-3 C3 / **D5-04** 的 Android 侧判据：自然播完时传给 `completePlaybackSession` 的
 * 时长必须来自**本次会话累计实听**，而不是 `music.duration`（元数据时长）。
 *
 * 为什么这条必须单独钉：三端曾各写一种口径（Android 写元数据时长、Desktop 写引擎位置、
 * iOS 写"距上次 tick 的残值"），同一首歌在三个平台上统计出三个 `playDuration`，
 * 总收听时长与完播率因此互不可比。修口径是行为改变，没有断言就会被人"顺手改回去"。
 *
 * 白盒说明：会话状态（`sessionTrack` / `currentPlaybackHistoryId` / `playStartTime`）是私有的，
 * 而真实起播要 Media3 的 `PlayControl`（Robolectric 下拿不到）。这里用反射把它们摆到
 * 「播了 5 秒的一首歌自然播完」这个形状上，断言的仍然是**对外部协作者的调用参数** ——
 * 也就是仓库真正收到的那个数。反射只读不写业务逻辑，字段改名时本用例会红（而不是静默失效）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MusicControllerPlaybackSettlementTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var historyUseCase: PlaybackHistoryUseCase
    private lateinit var controller: MusicController
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()

        historyUseCase = mockk(relaxed = true)
        controller = MusicController(
            context = context,
            currentPlaybackUseCase = mockk(relaxed = true),
            playbackHistoryUseCase = historyUseCase,
            timerUseCase = mockk(relaxed = true),
            managePlaylistUseCase = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun naturalCompletionSettlesWithCumulativeListenedMs_notMetadataDuration() {
        val track = track(id = 1L, durationMs = 200_000L)
        controller.addToPlaylist(track)

        // 摆成"已经在播、已听 5 秒"的会话形状
        setField("sessionTrack", track)
        setField("currentPlaybackHistoryId", 7L)
        setField("playStartTime", System.currentTimeMillis() - 5_000L)
        setField("totalPlayedDurationInSession", 0L)
        controller.onPlayStateChanged(true)

        val durationSlot = slot<Long>()
        coEvery { historyUseCase.completePlaybackSession(any(), any(), capture(durationSlot)) } returns Unit

        controller.onPlaybackEnded()

        coVerify { historyUseCase.completePlaybackSession(7L, 1L, any()) }
        val written = durationSlot.captured
        // 元数据是 200 秒：写进去的必须明显不是它
        assertTrue(
            "playDuration=$written 不是会话累计实听（元数据时长是 200000）",
            written in 4_000L..30_000L,
        )
    }    private fun track(id: Long, durationMs: Long) = MusicInfo(
        music = Music(
            id = id,
            title = "song-$id",
            artist = "artist",
            album = "album",
            duration = durationMs,
            path = "/a/$id.mp3",
            albumArtUri = "",
        ),
        extra = null,
        userInfo = null,
    )

    private fun setField(name: String, value: Any?) {
        val field = MusicController::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(controller, value)
    }
}
