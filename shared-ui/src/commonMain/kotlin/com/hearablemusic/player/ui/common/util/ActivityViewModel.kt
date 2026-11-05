package com.hearablemusic.player.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel

/**
 * 应用级（Activity scope）共享 ViewModel 入口（commonMain 契约）。
 *
 * Navigation3 的 entry 通过 rememberViewModelStoreNavEntryDecorator() 提供
 * entry 级 ViewModelStoreOwner；跨页面共享的 ViewModel（播放状态、弹窗、主题）
 * 必须绑定应用级 owner，否则会在每个 entry 产生独立实例导致状态分裂。
 *
 * 平台壳（MainActivity / Desktop 主窗口）负责 CompositionLocalProvider
 * provides 应用级 ViewModelStoreOwner。
 */
val LocalAppViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("LocalAppViewModelStoreOwner not provided")
}

@Composable
inline fun <reified T : ViewModel> activityViewModel(): T = koinViewModel(viewModelStoreOwner = LocalAppViewModelStoreOwner.current)
