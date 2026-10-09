package com.hearablemusic.player.ui.settings.viewmodel

import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.WindowedUsageAnalytics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 一-3 / D5-14 + D5-09 判据：窗口查询**失败**不能再长得像"这个窗口内没听歌"，
 * 而 UserScreen 那三个统计数字所属的窗口要可考。
 *
 * `UserUsageDataViewModel` 的依赖是接口 + 一个具体类，用 mockk 打桩即可；
 * 这里刻意断言"标记"而不是"文案"——文案由 Compose 渲染，本模块的测试源集没有配 UI 测试基建。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserUsageDataWindowTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val repository = mockk<MusicRepository>()
    private val masterAgent = mockk<MasterAgent>()

    private val emptyAnalytics = WindowedUsageAnalytics(
        totalListeningMinutes = 0,
        completionRate = 0f,
        skipRate = 0f,
        totalPlayCount = 0,
        totalSkipCount = 0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { masterAgent.userMemory } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubOverviewOk() {
        coEvery { repository.getWindowedAnalytics(any()) } returns emptyAnalytics
        coEvery { repository.getWindowedSourceBreakdown(any()) } returns emptyMap()
    }

    @Test
    fun defaultWindowIsThirtyDays_andThatIsWhatTheCardMeans() = runTest(dispatcher) {
        stubOverviewOk()

        UserUsageDataViewModel(repository, masterAgent)
        runCurrent()

        // D5-09：卡片写「总听歌时长」，取数却是这条 30 天窗口 —— 先钉住"确实是 30"，
        // 范围标签随之成为必须显示的字段（UserScreen 现在把 rangeLabel 一起渲染）
        io.mockk.coVerify(exactly = 1) { repository.getWindowedAnalytics(30) }
    }

    @Test
    fun queryFailure_isMarkedAsFailure_notSilentEmpty() = runTest(dispatcher) {
        coEvery { repository.getWindowedAnalytics(any()) } throws IllegalStateException("迁移后缺列")
        coEvery { repository.getWindowedSourceBreakdown(any()) } returns emptyMap()

        val vm = UserUsageDataViewModel(repository, masterAgent)
        runCurrent()

        assertEquals(Dimension.OVERVIEW, vm.windowed.value.failedDimension)
        assertNull(vm.windowed.value.analytics)
        // 读完了（哪怕是失败）就不该还停在加载态
        assertFalse(vm.windowed.value.isLoading)
    }

    @Test
    fun genuinelyEmptyWindow_isNotReportedAsFailure() = runTest(dispatcher) {
        stubOverviewOk()

        val vm = UserUsageDataViewModel(repository, masterAgent)
        runCurrent()

        // 这条与上一条是成对的：只断言"失败会标记"，很容易顺手把空结果也标成失败
        assertNull(vm.windowed.value.failedDimension)
        assertEquals(0L, vm.windowed.value.analytics?.totalListeningMinutes)
    }

    @Test
    fun firstFrameIsLoading_notEmptyHint() = runTest(dispatcher) {
        stubOverviewOk()

        val vm = UserUsageDataViewModel(repository, masterAgent)
        // init 里的协程还没跑：首帧必须是加载态，而不是先闪一次"暂无数据"再跳变
        assertTrue(vm.windowed.value.isLoading)

        runCurrent()
        assertTrue(vm.windowed.value.isLoading.not())
    }
}
