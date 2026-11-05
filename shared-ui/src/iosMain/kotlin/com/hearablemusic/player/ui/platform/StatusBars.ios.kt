package com.hearablemusic.player.ui.platform

import androidx.compose.runtime.Composable

/**
 * iOS actual：状态栏控制器。
 *
 * 歌词页沉浸式交互（隐藏/恢复状态栏）。Compose UIViewController 层级中
 * 通过 UIApplication 的 keyWindow 根控制器切换 prefersStatusBarHidden；
 * Phase 1（A1）先给空实现（沉浸式交互在 A3 桥接完善后走 Swift 壳代理）。
 */
@Composable
actual fun rememberStatusBarsController(): StatusBarsController? = null
