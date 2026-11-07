package com.hearablemusic.player.ui.common.util

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadStateTest {

    @Test
    fun idle_isIdle() {
        val state: LoadState<Nothing> = LoadState.Idle
        assertTrue(state.isIdle)
        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertFalse(state.isError)
        assertFalse(state.isEmpty)
    }

    @Test
    fun loading_isLoading() {
        val state: LoadState<Nothing> = LoadState.Loading
        assertTrue(state.isLoading)
        assertFalse(state.isIdle)
    }

    @Test
    fun success_isSuccess() {
        val state = LoadState.Success("data")
        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals("data", state.data)
    }

    @Test
    fun error_isError() {
        val state = LoadState.Error("something failed")
        assertTrue(state.isError)
        assertEquals("something failed", state.message)
    }

    @Test
    fun empty_isEmpty() {
        val state: LoadState<Nothing> = LoadState.Empty
        assertTrue(state.isEmpty)
    }

    @Test
    fun dataOrNull_success_returnsData() {
        val state = LoadState.Success(42)
        assertEquals(42, state.dataOrNull)
    }

    @Test
    fun dataOrNull_loading_returnsNull() {
        val state: LoadState<Int> = LoadState.Loading
        assertNull(state.dataOrNull)
    }

    @Test
    fun dataOrNull_error_returnsNull() {
        val state: LoadState<Int> = LoadState.Error("err")
        assertNull(state.dataOrNull)
    }

    @Test
    fun map_success_transforms() {
        val state = LoadState.Success(5)
        val mapped = state.map { it * 2 }
        assertTrue(mapped is LoadState.Success)
        assertEquals(10, (mapped as LoadState.Success).data)
    }

    @Test
    fun map_idle_staysIdle() {
        val state: LoadState<Int> = LoadState.Idle
        val mapped = state.map { it * 2 }
        assertTrue(mapped is LoadState.Idle)
    }

    @Test
    fun map_loading_staysLoading() {
        val state: LoadState<Int> = LoadState.Loading
        val mapped = state.map { it * 2 }
        assertTrue(mapped is LoadState.Loading)
    }

    @Test
    fun map_error_preservesMessage() {
        val state: LoadState<Int> = LoadState.Error("fail")
        val mapped = state.map { it * 2 }
        assertTrue(mapped is LoadState.Error)
        assertEquals("fail", (mapped as LoadState.Error).message)
    }

    @Test
    fun map_empty_staysEmpty() {
        val state: LoadState<Int> = LoadState.Empty
        val mapped = state.map { it * 2 }
        assertTrue(mapped is LoadState.Empty)
    }

    @Test
    fun onSuccess_callsActionOnSuccess() {
        var called = false
        LoadState.Success("x").onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun onSuccess_doesNotCallOnError() {
        var called = false
        LoadState.Error("x").onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun onError_callsActionOnError() {
        var msg = ""
        LoadState.Error("oops").onError { msg = it }
        assertEquals("oops", msg)
    }

    @Test
    fun onError_doesNotCallOnSuccess() {
        var called = false
        LoadState.Success("x").onError { called = true }
        assertFalse(called)
    }

    @Test
    fun onLoading_callsActionOnLoading() {
        var called = false
        LoadState.Loading.onLoading { called = true }
        assertTrue(called)
    }

    @Test
    fun onEmpty_callsActionOnEmpty() {
        var called = false
        LoadState.Empty.onEmpty { called = true }
        assertTrue(called)
    }

    @Test
    fun asLoadState_emitsLoadingThenSuccess() = runTest {
        val results = flowOf(42).asLoadState().toList()
        assertEquals(2, results.size)
        assertTrue(results[0] is LoadState.Loading)
        assertTrue(results[1] is LoadState.Success)
        assertEquals(42, (results[1] as LoadState.Success).data)
    }

    @Test
    fun runCatchingToLoadState_success_emitsLoadingThenSuccess() = runTest {
        val results = flowOf("ok").runCatchingToLoadState().toList()
        assertEquals(2, results.size)
        assertTrue(results[0] is LoadState.Loading)
        assertEquals("ok", (results[1] as LoadState.Success).data)
    }

    @Test
    fun runCatchingToLoadState_error_emitsLoadingThenError() = runTest {
        val errorFlow = flow<String> { throw RuntimeException("boom") }
        val results = errorFlow.runCatchingToLoadState().toList()
        assertEquals(2, results.size)
        assertTrue(results[0] is LoadState.Loading)
        assertTrue(results[1] is LoadState.Error)
        assertEquals("boom", (results[1] as LoadState.Error).message)
    }
}
