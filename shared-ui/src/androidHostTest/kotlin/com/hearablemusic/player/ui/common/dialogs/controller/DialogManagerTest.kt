package com.hearablemusic.player.ui.common.dialogs.controller

import com.hmp.domain.agent.port.ConfirmDecision
import com.hmp.domain.agent.port.ConfirmStep
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * D6-08 判据：确认链的 id 必须唯一。
 *
 * 旧实现 `val id = nowEpochMillis()` —— 同一毫秒内并发两条确认，后写的覆盖前一条，
 * 前一条的 continuation 永不 resume，对应工具调用挂死。
 *
 * 并发断言直接压 [DialogManager.allocateConfirmId]：`dialogEvent` 是 replay=0 +
 * `DROP_OLDEST` 的 SharedFlow，按"收到几条事件"计数会因丢事件而不稳。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DialogManagerTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun step(title: String) = ConfirmStep(title = title, message = "msg-$title")

    @Test
    fun concurrentAllocations_neverShareAnId() = runTest {
        val manager = DialogManager()

        // 50 个并发取号（真实线程，不是虚拟时间）—— 时间戳方案在这里会大面积撞号
        val ids = (1..50).map {
            async(Dispatchers.Default) { manager.allocateConfirmId() }
        }.awaitAll()

        assertEquals("确认链 id 必须两两不同", 50, ids.distinct().size)
        assertEquals("从 1 开始连续取号", (1L..50L).toList(), ids.sorted())
    }

    @Test
    fun emptySteps_approvedWithoutConsumingAnId() = runTest(dispatcher) {
        val manager = DialogManager()

        assertEquals(ConfirmDecision.Approved, manager.requestConfirm(emptyList()))
        assertEquals(1L, manager.allocateConfirmId())
    }

    @Test
    fun requestConfirm_emitsTheAllocatedId() = runTest(dispatcher) {
        val manager = DialogManager()
        var emittedId: Long? = null
        val collector = launch {
            manager.dialogEvent.collect { event ->
                if (event is DialogEvent.ConfirmChain) emittedId = event.id
            }
        }
        advanceUntilIdle() // 让收集器订阅上

        val request = launch { manager.requestConfirm(listOf(step("s"))) }
        advanceUntilIdle()

        assertEquals("第一条确认链的 id 应是 1（顺序取号的第一号）", 1L, emittedId ?: -1L)

        request.cancel()
        collector.cancel()
    }
}
