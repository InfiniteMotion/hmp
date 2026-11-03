package com.hearablemusic.player.ui.agent.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 面板开关的契约：默认关闭、open/close 幂等。 */
class RadioConsoleControllerTest {

    @Test
    fun openAndClose_toggleVisibility() {
        val controller = RadioConsoleController()

        assertFalse(controller.visible.value, "默认收起")

        controller.open()
        assertTrue(controller.visible.value)

        controller.open()
        assertTrue(controller.visible.value, "重复 open 仍为开")

        controller.close()
        assertFalse(controller.visible.value)
    }
}
