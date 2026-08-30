package com.hearablemusic.player.ui.chat

import com.hmp.domain.agent.engine.ConfirmRequest
import com.hmp.domain.agent.engine.TerminationReason
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.FakePlaybackCommandPort
import com.hmp.domain.agent.port.StoredAgentMessage
import com.hmp.domain.agent.tool.ToolPermissionLevel
import com.hmp.domain.setting.usecase.UserSettingsUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** R-T5 假会话存储：无历史、append 空实现，不干扰对话流测试。 */
    private val fakeMessageStore = object : AgentMessageStore {
        override suspend fun currentOrNewSessionId(): String = "test"
        override suspend fun append(message: StoredAgentMessage) {}
        override suspend fun loadSession(sessionId: String, limit: Int): List<StoredAgentMessage> = emptyList()
    }

    private fun vm(gateway: FakeChatAgentGateway) =
        ChatViewModel(gateway, UserSettingsUseCase(MinimalSettingsRepository()), FakePlaybackCommandPort, fakeMessageStore)

    private fun needConfirm() = ChatAgentEvent.NeedConfirm(
        turnId = "t1",
        requests = listOf(
            ConfirmRequest("create_playlist", "创建「驾车精选」歌单并加 9 首", ToolPermissionLevel.CONFIRM),
            ConfirmRequest("load_tag_list", "整理「工作专注」标签 12 首", ToolPermissionLevel.CONFIRM),
        ),
    )

    @Test
    fun send_appendsUserBubbleAndRunsWithHint() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)

        assertEquals(1, vm.state.value.messages.size) // 问候区
        vm.onInputChange("帮我建个歌单")
        vm.send()
        runCurrent()

        val state = vm.state.value
        assertTrue(state.running)
        assertTrue(state.runningHint.isNotBlank())
        assertEquals(2, state.messages.size)
        val userBubble = state.messages.last()
        assertTrue(userBubble.fromUser)
        assertEquals("帮我建个歌单", userBubble.text)
    }

    @Test
    fun send_ignoresBlankInput() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)

        vm.onInputChange("   ")
        vm.send()
        runCurrent()

        assertFalse(vm.state.value.running)
        assertEquals(1, vm.state.value.messages.size)
    }

    @Test
    fun sendPreloaded_appendsUserBubbleAndRuns_withoutInputField() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)

        vm.sendPreloaded("你好") // 外部入口带入（M1 锚点 → M5 对话）
        runCurrent()

        val state = vm.state.value
        assertTrue(state.running)
        assertEquals(2, state.messages.size)
        val userBubble = state.messages.last()
        assertTrue(userBubble.fromUser)
        assertEquals("你好", userBubble.text)
        // 输入框未被写入：仍为空
        assertEquals("", state.input)
    }

    @Test
    fun needConfirm_showsPendingCard_withDefaultSelection_andToggle() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)
        vm.onInputChange("整理一下")
        vm.send()
        runCurrent()

        gateway.emitEvent(needConfirm())
        runCurrent()

        val pending = vm.state.value.pendingConfirm
        assertTrue(pending != null)
        assertEquals(2, pending!!.items.size)
        assertTrue(pending.items.all { it.selected })

        vm.toggleConfirmItem(pending.items[0].id)
        assertFalse(vm.state.value.pendingConfirm!!.items.first().selected)
        assertTrue(vm.state.value.pendingConfirm!!.items[1].selected)
    }

    @Test
    fun toggle_ignoredAfterSubmit() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)
        vm.onInputChange("整理一下")
        vm.send()
        runCurrent()

        gateway.emitEvent(needConfirm())
        runCurrent()
        val id = vm.state.value.pendingConfirm!!.items.first().id

        vm.submitConfirm()
        runCurrent()
        vm.toggleConfirmItem(id)

        assertEquals(true, vm.state.value.pendingConfirm!!.items.first().selected)
    }

    @Test
    fun submitConfirm_thenFinished_appendsConfirmCardAndTextBubble() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)
        vm.onInputChange("整理一下")
        vm.send()
        runCurrent()

        gateway.emitEvent(needConfirm())
        runCurrent()
        // 勾掉第二项再照做
        val secondId = vm.state.value.pendingConfirm!!.items[1].id
        vm.toggleConfirmItem(secondId)
        vm.submitConfirm()
        assertTrue(vm.state.value.pendingConfirm!!.submitted)

        gateway.emitEvent(ChatAgentEvent.Finished("已整理到歌单", emptyList(), TerminationReason.ANSWERED))
        runCurrent()

        val state = vm.state.value
        assertFalse(state.running)
        assertNull(state.pendingConfirm)
        val confirmBubble = state.messages[state.messages.lastIndex - 1]
        val textBubble = state.messages.last()
        assertEquals(CompanionRenderHint.CONFIRM, confirmBubble.renderHint)
        assertEquals(2, confirmBubble.confirmItems.size)
        assertEquals(1, confirmBubble.confirmItems.count { it.selected })
        assertTrue(confirmBubble.receipt.contains("执行 1 项"))
        assertEquals(CompanionRenderHint.TEXT, textBubble.renderHint)
        assertEquals("已整理到歌单", textBubble.text)
    }

    @Test
    fun skipConfirm_marksAllUnselected_andReceiptShowsSkipped() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)
        vm.onInputChange("整理一下")
        vm.send()
        runCurrent()

        gateway.emitEvent(needConfirm())
        runCurrent()

        vm.skipConfirm()
        assertTrue(vm.state.value.pendingConfirm!!.submitted)
        assertTrue(vm.state.value.pendingConfirm!!.items.none { it.selected })

        gateway.emitEvent(ChatAgentEvent.Finished("明白了", emptyList(), TerminationReason.ANSWERED))
        runCurrent()

        val confirmBubble = vm.state.value.messages[vm.state.value.messages.lastIndex - 1]
        assertTrue(confirmBubble.confirmItems.none { it.selected })
        assertTrue(confirmBubble.receipt.contains("全部跳过"))
    }

    @Test
    fun send_blockedWhileConfirmPendingAndUnsubmitted() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)
        vm.onInputChange("第一次")
        vm.send()
        runCurrent()
        gateway.emitEvent(needConfirm())
        runCurrent()

        vm.onInputChange("第二次")
        vm.send()
        runCurrent()

        // pending 未提交时不派新一轮：仍在 running，消息数不再增加
        assertTrue(vm.state.value.running)
        assertEquals(2, vm.state.value.messages.size)
    }

    @Test
    fun failed_appendsErrorBubbleAndResetsState() = runTest(dispatcher.scheduler) {
        val gateway = FakeChatAgentGateway()
        val vm = vm(gateway)
        vm.onInputChange("测试")
        vm.send()
        runCurrent()

        gateway.emitEvent(ChatAgentEvent.Failed("network down"))
        runCurrent()

        val state = vm.state.value
        assertFalse(state.running)
        assertNull(state.pendingConfirm)
        val last = state.messages.last()
        assertFalse(last.fromUser)
        assertTrue(last.text.contains("没连上"))
    }
}