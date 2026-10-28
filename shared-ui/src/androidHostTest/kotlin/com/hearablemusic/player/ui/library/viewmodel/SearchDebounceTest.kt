package com.hearablemusic.player.ui.library.viewmodel

import com.hearablemusic.player.ui.common.util.UiState
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.music.usecase.SearchMusicUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * D2-18 判据：一次按键只该打一次仓库，而且**旧的那次不能把新的结果盖掉**。
 *
 * 修前的两处调用点（`SearchScreen` 的 `LaunchedEffect` + `onSearchQueryChange`）各起一个
 * `viewModelScope.launch`，两者写完 `_searchState` 的先后与 query 新旧无关；
 * 下面的第三条用例把这一点钉住 —— 慢的旧查询后完成时，界面必须是新查询的结果。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchDebounceTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val repository = mockk<MusicRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun music(title: String) = MusicInfo(
        music = Music(
            id = title.hashCode().toLong(), title = title, artist = "A", album = "B",
            duration = 1L, path = "/$title.mp3", albumArtUri = ""
        ),
        extra = null,
        userInfo = null,
    )

    private fun viewModel() = SearchViewModel(SearchMusicUseCase(repository))

    @Test
    fun rapidKeystrokes_hitRepositoryOnce_withTheLatestQuery() = runTest(dispatcher) {
        coEvery { repository.searchMusic(any()) } returns listOf(music("ab"))

        val vm = viewModel()
        vm.searchMusic("a")
        runCurrent()
        vm.searchMusic("ab")

        // 防抖窗口内不该有任何查询打出去
        coVerify(exactly = 0) { repository.searchMusic(any()) }

        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()

        coVerify(exactly = 1) { repository.searchMusic("ab") }
        coVerify(exactly = 0) { repository.searchMusic("a") }
    }

    @Test
    fun slowOldQuery_cannotOverwriteTheNewerResult() = runTest(dispatcher) {
        coEvery { repository.searchMusic("a") } coAnswers {
            delay(5_000)
            listOf(music("a"))
        }
        coEvery { repository.searchMusic("ab") } returns listOf(music("ab"))

        val vm = viewModel()
        vm.searchMusic("a")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        // 此刻 "a" 正在飞；紧接着按出 "ab"
        vm.searchMusic("ab")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(UiState.Success(listOf(music("ab"))), vm.searchState.value)
    }

    @Test
    fun cancelledQuery_isNotReportedAsFailure() = runTest(dispatcher) {
        coEvery { repository.searchMusic("a") } coAnswers {
            delay(5_000)
            listOf(music("a"))
        }
        coEvery { repository.searchMusic("ab") } returns listOf(music("ab"))

        val vm = viewModel()
        vm.searchMusic("a")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        vm.searchMusic("ab")
        runCurrent()

        // 被取消的那一拍既不写 Error，也不把界面停在错误态
        assertFalse(vm.searchState.value is UiState.Error)
    }

    @Test
    fun clearingTheQuery_resetsToIdle_withoutHittingTheRepository() = runTest(dispatcher) {
        coEvery { repository.searchMusic(any()) } returns listOf(music("a"))

        val vm = viewModel()
        vm.searchMusic("a")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(UiState.Success(listOf(music("a"))), vm.searchState.value)

        vm.searchMusic("")
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(UiState.Idle, vm.searchState.value)
        coVerify(exactly = 1) { repository.searchMusic("a") }
    }
}
