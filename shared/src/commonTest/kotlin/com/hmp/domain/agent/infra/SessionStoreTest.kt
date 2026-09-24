package com.hmp.domain.agent.infra

import com.hmp.domain.agent.port.LlmMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SessionStore（M4-T3）会话状态测试：消息历史 / 会话 id / 挂起确认的生命周期。
 */
class SessionStoreTest {

    private var now = 1_000L
    private fun newStore(): SessionStore = SessionStore { now }

    private fun userMsg(content: String) = LlmMessage(role = "user", content = content)
    private fun botMsg(content: String) = LlmMessage(role = "assistant", content = content)

    @Test
    fun inactive_beforeFirstStart() {
        val store = newStore()

        assertFalse(store.isActive(), "从未 startNewSession 时不活跃")
        assertNull(store.currentSessionId())
        assertTrue(store.history().isEmpty())
    }

    @Test
    fun startNewSession_returnsIncrementingIds() {
        val store = newStore()

        assertEquals(1L, store.startNewSession(), "首个会话 id 从 1 起")
        assertTrue(store.isActive())
        assertEquals(2L, store.startNewSession(), "再次开会话 id 自增")
    }

    @Test
    fun append_buildsHistory() {
        val store = newStore()
        store.startNewSession()

        store.append(userMsg("你好"), botMsg("在的"))
        store.append(userMsg("放点歌"))

        assertEquals(3, store.history().size)
        assertEquals("你好", store.history()[0].content)
        assertEquals("assistant", store.history()[1].role)
        assertEquals("放点歌", store.history()[2].content)
    }

    @Test
    fun history_isDefensiveCopy() {
        val store = newStore()
        store.startNewSession()
        store.append(userMsg("第一条"))
        val snapshot = store.history()

        store.append(userMsg("第二条"))

        assertEquals(1, snapshot.size, "历史快照不受后续追加影响")
        assertEquals(2, store.history().size)
    }

    @Test
    fun startNewSession_clearsHistoryAndPendingConfirm() {
        val store = newStore()
        store.startNewSession()
        store.append(userMsg("上一段对话"))
        store.setPendingConfirm(PendingConfirm("playlist_create", "{\"name\":\"通勤\"}", "创建歌单「通勤」？"))

        store.startNewSession()

        assertTrue(store.history().isEmpty(), "新会话不得携带旧消息")
        assertNull(store.takePendingConfirm(), "新会话不得携带旧挂起确认")
    }

    @Test
    fun pendingConfirm_consumedOnce() {
        val store = newStore()
        store.startNewSession()

        store.setPendingConfirm(PendingConfirm("playlist_rename", "{\"id\":3}", "重命名？"))
        val taken = store.takePendingConfirm()

        assertNotNull(taken)
        assertEquals("playlist_rename", taken.toolName)
        assertEquals("{\"id\":3}", taken.argsSummary)
        assertNull(store.takePendingConfirm(), "取走一次后即清空（逐项确认语义）")
    }

    @Test
    fun clear_resetsEverything() {
        val store = newStore()
        store.startNewSession()
        store.append(userMsg("hi"))
        store.setPendingConfirm(PendingConfirm("playlist_delete", "{}", "删除？"))

        store.clear()

        assertFalse(store.isActive())
        assertNull(store.currentSessionId())
        assertTrue(store.history().isEmpty())
        assertNull(store.takePendingConfirm())
    }
}
