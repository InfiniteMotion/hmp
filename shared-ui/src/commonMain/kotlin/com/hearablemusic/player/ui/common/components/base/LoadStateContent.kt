package com.hearablemusic.player.ui.common.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hearablemusic.player.ui.common.util.LoadState

@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    modifier: Modifier = Modifier,
    onLoading: @Composable () -> Unit = { DefaultLoading() },
    onError: @Composable (String) -> Unit = { message -> DefaultError(message) },
    onEmpty: @Composable () -> Unit = { DefaultEmpty() },
    onSuccess: @Composable (T) -> Unit,
) {
    when (state) {
        is LoadState.Idle -> onLoading()
        is LoadState.Loading -> onLoading()
        is LoadState.Error -> onError(state.message)
        is LoadState.Empty -> onEmpty()
        is LoadState.Success -> onSuccess(state.data)
    }
}
