package com.hearablemusic.player.ui.common.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Idle -> UiState.Idle
    is UiState.Loading -> UiState.Loading
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Error -> UiState.Error(message)
    is UiState.Empty -> UiState.Empty
}

fun <T> UiState<T>.onSuccess(action: (T) -> Unit): UiState<T> {
    if (this is UiState.Success) {
        action(data)
    }
    return this
}

fun <T> UiState<T>.onError(action: (String) -> Unit): UiState<T> {
    if (this is UiState.Error) {
        action(message)
    }
    return this
}

fun <T> UiState<T>.onLoading(action: () -> Unit): UiState<T> {
    if (this is UiState.Loading) {
        action()
    }
    return this
}

fun <T> UiState<T>.onEmpty(action: () -> Unit): UiState<T> {
    if (this is UiState.Empty) {
        action()
    }
    return this
}

val <T> UiState<T>.isLoading: Boolean
    get() = this is UiState.Loading

val <T> UiState<T>.isSuccess: Boolean
    get() = this is UiState.Success

val <T> UiState<T>.isError: Boolean
    get() = this is UiState.Error

val <T> UiState<T>.isEmpty: Boolean
    get() = this is UiState.Empty

val <T> UiState<T>.isIdle: Boolean
    get() = this is UiState.Idle

val <T> UiState<T>.dataOrNull: T?
    get() = (this as? UiState.Success)?.data

fun <T> Flow<T>.asUiState(): Flow<UiState<T>> = this.map<T, UiState<T>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading) }

fun <T> Flow<T>.runCatchingToUiState(): Flow<UiState<T>> = this.map<T, UiState<T>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading) }
    .catch { emit(UiState.Error(it.message ?: "Unknown error")) }
