package com.hmp.domain.agent.health

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * 健康门控的两条不变量（2026-11-13）：
 * 「应用回到前台」靠 evaluate() 补启服务，「四个入口」靠 inspect() 只查不动。
 * 若有人把 onAppForeground() 误改成 inspect()，第一条会红。
 */
class AgentHealthGateTest {

    private fun checker() = AgentHealthChecker(null, null, null, { 0L })

    @Test
    fun `evaluate 每次都触发回调 —— 回前台能补启被跳过的服务`() = runTest {
        var calls = 0
        val gate = AgentHealthGate(checker()) { _ -> calls++ }

        gate.evaluate()
        gate.evaluate()

        assertEquals(2, calls, "连续两次回前台都要重检；applyHealth 幂等，重复调用无害")
    }

    @Test
    fun `inspect 只刷新快照、不触发回调 —— 入口不会递归拉起服务`() = runTest {
        var calls = 0
        val gate = AgentHealthGate(checker()) { _ -> calls++ }

        val first = gate.inspect()
        val second = gate.inspect()

        assertEquals(0, calls, "入口只读检查，绝不能回调 applyHealth")
        assertNotNull(gate.last)
        assertSame(second, gate.last)
        assertNotNull(first)
    }
}
