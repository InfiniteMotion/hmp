package com.hearablemusic.player.ui.common.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

fun <T, R> LoadState<T>.map(transform: (T) -> R): LoadState<R> = when (this) {
    is LoadState.Idle -> LoadState.Idle
    is LoadState.Loading -> LoadState.Loading
    is LoadState.Success -> LoadState.Success(transform(data))
    is LoadState.Error -> LoadState.Error(message)
    is LoadState.Empty -> LoadState.Empty
}

fun <T> LoadState<T>.onSuccess(action: (T) -> Unit): LoadState<T> {
    if (this is LoadState.Success) {
        action(data)
    }
    return this
}

fun <T> LoadState<T>.onError(action: (String) -> Unit): LoadState<T> {
    if (this is LoadState.Error) {
        action(message)
    }
    return this
}

fun <T> LoadState<T>.onLoading(action: () -> Unit): LoadState<T> {
    if (this is LoadState.Loading) {
        action()
    }
    return this
}

fun <T> LoadState<T>.onEmpty(action: () -> Unit): LoadState<T> {
    if (this is LoadState.Empty) {
        action()
    }
    return this
}

val <T> LoadState<T>.isLoading: Boolean
    get() = this is LoadState.Loading

val <T> LoadState<T>.isSuccess: Boolean
    get() = this is LoadState.Success

val <T> LoadState<T>.isError: Boolean
    get() = this is LoadState.Error

val <T> LoadState<T>.isEmpty: Boolean
    get() = this is LoadState.Empty

val <T> LoadState<T>.isIdle: Boolean
    get() = this is LoadState.Idle

val <T> LoadState<T>.dataOrNull: T?
    get() = (this as? LoadState.Success)?.data

fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> = this.map<T, LoadState<T>> { LoadState.Success(it) }
    .onStart { emit(LoadState.Loading) }

fun <T> Flow<T>.runCatchingToLoadState(): Flow<LoadState<T>> = this.map<T, LoadState<T>> { LoadState.Success(it) }
    .onStart { emit(LoadState.Loading) }
    .catch { emit(LoadState.Error(it.message ?: "Unknown error")) }
