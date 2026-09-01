package com.hmp.domain.agent.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * SchedulerStopSignal 门闩语义测试。
 *
 * 重点回归：旧版 waitResume() 用 tryLock 成功后不释放，
 * Agent 第二个批次就自锁在自己持有的锁上（Scheduler 从未 pause 也死锁）。
 */
class StopSignalTest {

    @Test
    fun waitResume_returnsImmediately_whenNeverPaused() = runBlocking {
        val signal = SchedulerStopSignal()
        // 旧版在此自锁：第 1 次 tryLock 成功不释放 → 第 2 次挂起等 resume（永远不会来）
        withTimeout(1_000) {
            signal.waitResume()
            signal.waitResume()
            signal.waitResume()
        }
    }

    @Test
    fun waitResume_suspendsWhilePaused_andResumes() = runBlocking {
        val signal = SchedulerStopSignal()
        signal.onSchedulerPaused()

        val waiter = async { signal.waitResume(); true }
        yield()  // 让 waiter 启动并挂起在门闩上
        assertTrue(!waiter.isCompleted, "pause 期间 waitResume 必须挂起")

        signal.onSchedulerResumed()
        assertTrue(withTimeout(1_000) { waiter.await() }, "resume 后必须立即恢复")
    }

    @Test
    fun onSchedulerPaused_isIdempotent() = runBlocking {
        val signal = SchedulerStopSignal()
        signal.onSchedulerPaused()
        signal.onSchedulerPaused()  // 重复 pause 不得死锁/不得二次持锁

        val waiter = async { signal.waitResume(); true }
        yield()
        assertTrue(!waiter.isCompleted)
        signal.onSchedulerResumed()
        assertTrue(withTimeout(1_000) { waiter.await() })
    }

    @Test
    fun onSchedulerResumed_withoutPause_isNoop() {
        val signal = SchedulerStopSignal()
        // 未 pause 直接 resume：不得抛 IllegalStateException
        signal.onSchedulerResumed()
    }

    @Test
    fun waitResume_passesImmediately_afterResumeBeforeWait() = runBlocking {
        val signal = SchedulerStopSignal()
        signal.onSchedulerPaused()
        signal.onSchedulerResumed()  // Agent 尚未调 waitResume，门闩已提前释放

        // 必须立即通过（pauseRequested=false 快速路径）
        withTimeout(1_000) { signal.waitResume() }

        // 再来一轮完整 pause/resume 也正常（门闩恢复 unlocked 基态）
        signal.onSchedulerPaused()
        signal.onSchedulerResumed()
        withTimeout(1_000) { signal.waitResume() }
    }

    @Test
    fun pauseResume_multipleCycles() = runBlocking {
        val signal = SchedulerStopSignal()
        repeat(3) {
            signal.onSchedulerPaused()
            val waiter = async { signal.waitResume(); true }
            yield()
            assertTrue(!waiter.isCompleted, "第 ${it + 1} 轮 pause 期间必须挂起")
            signal.onSchedulerResumed()
            assertTrue(withTimeout(1_000) { waiter.await() }, "第 ${it + 1} 轮 resume 后必须恢复")
        }
    }
}
