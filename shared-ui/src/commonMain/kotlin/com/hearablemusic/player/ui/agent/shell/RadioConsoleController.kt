package com.hearablemusic.player.ui.agent.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 电台控制面板的共享开关。
 *
 * 面板本身仍由 `AppRoot` 渲染（它是全屏 ScrimDialog），但打开它的入口现在有三个：
 * 底栏伙伴胶囊长按、播放页电台卡、以及未来的其他入口。把可见性放在 UI 层单例里，
 * 避免把 `mutableStateOf` 从 `AppRoot` 一层层传到播放页。
 *
 * 只做开关，不持有电台状态 —— 状态仍以 `MasterAgent.radioState` 为唯一真源。
 */
class RadioConsoleController {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun open() {
        _visible.value = true
    }

    fun close() {
        _visible.value = false
    }
}
