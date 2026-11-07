package com.hearablemusic.player.ui.common.util

/**
 * 一次异步操作的结果（7.5 P1 命名归位：由旧通用异步态类型改名而来）。
 * 只作 `XxxUiState` 里的字段类型；整屏状态用 `XxxUiState`，写法约定见 `UdfConventions.md`。
 */
sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
    data object Empty : LoadState<Nothing>
}
