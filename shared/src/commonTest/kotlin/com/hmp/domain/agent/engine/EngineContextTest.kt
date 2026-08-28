package com.hmp.domain.agent.engine

import com.hmp.domain.agent.port.LlmMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextBudgetTest {
    private fun budget(now: () -> Long, maxList: Int = 1200, maxResults: Int = 6, quota: Int = 100) =
        ContextBudget(now, maxLibraryListChars = maxList, maxToolResultsKept = maxResults, dailyCloudQuota = quota)

    @Test
    fun `truncates and switches library list to overview when oversized`() {
        val cb = budget({ 0L }, maxList = 10)
        val small = cb.assemble("任务", "简短清单", "概览", null)
        assertEquals("简短清单", small.library) // 清单未超预算 → 用清单

        val bigList = "曲目".repeat(50)
        val switched = cb.assemble("任务", bigList, "按流派聚合 50 首", null)
        assertEquals("按流派聚合 50 首", switched.library) // 清单超预算 → 降级概览
    }

    @Test
    fun `rolls out old tool results keeping newest`() {
        val cb = budget({ 0L }, maxResults = 2)
        var a = cb.assemble(null, null, null, "r1")
        a = cb.assemble(null, null, null, "r2")
        a = cb.assemble(null, null, null, "r3")
        assertEquals(listOf("r3", "r2"), a.toolResults)
    }

    @Test
    fun `cloud quota resets per day`() {
        var day = 0L
        val cb = budget({ day }, quota = 2)
        assertTrue(cb.spendCloudCall()); assertTrue(cb.spendCloudCall())
        assertFalse(cb.spendCloudCall(), "额度耗尽")
        day += 86_400_000L // 第二天
        assertTrue(cb.spendCloudCall(), "跨天重置额度")
    }
}

class SessionStoreTest {
    @Test
    fun `session lifecycle`() {
        var now = 0L
        val store = SessionStore({ now })
        assertFalse(store.isActive())
        val id = store.startNewSession()
        assertEquals(1L, id)
        assertTrue(store.isActive())
        store.append(LlmMessage(role = "user", content = "hi"))
        store.append(LlmMessage(role = "assistant", content = "hello"))
        assertEquals(2, store.history().size)
        store.setPendingConfirm(PendingConfirm("createPlaylist", "args", "propose"))
        assertEquals("createPlaylist", store.takePendingConfirm()?.toolName)
        assertNull(store.takePendingConfirm())
    }
}

class PresenceBusTest {
    @Test
    fun `badge state updates via emit`() = kotlinx.coroutines.runBlocking {
        val bus = PresenceBus()
        assertFalse(bus.badgeState.value.visible)
        bus.emit(PresenceEvent.CompanionBadge(visible = true, label = "电台进行中"))
        assertTrue(bus.badgeState.value.visible)
        assertEquals("电台进行中", bus.badgeState.value.label)
    }

    @Test
    fun `events broadcast to collector`() = kotlinx.coroutines.runBlocking {
        val bus = PresenceBus()
        val got = mutableListOf<PresenceEvent>()
        // Unconfined：collect 同步建立订阅（不排队），保证 emit 前已订阅
        val job = launch(Dispatchers.Unconfined) { bus.events.collect { got += it } }
        bus.emit(PresenceEvent.DjBlank)
        bus.emit(PresenceEvent.CloudQuotaExhausted)
        job.cancel()
        assertEquals(2, got.size)
        assertTrue(got.any { it is PresenceEvent.DjBlank })
    }
}