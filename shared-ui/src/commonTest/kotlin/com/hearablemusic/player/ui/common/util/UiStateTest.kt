package com.hearablemusic.player.ui.common.util

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class UiStateTest {

    @Test
    fun idle_isIdle() {
        val state: UiState<Nothing> = UiState.Idle
        assertTrue(state.isIdle)
        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertFalse(state.isError)
        assertFalse(state.isEmpty)
    }

    @Test
    fun loading_isLoading() {
        val state: UiState<Nothing> = UiState.Loading
        assertTrue(state.isLoading)
        assertFalse(state.isIdle)
    }

    @Test
    fun success_isSuccess() {
        val state = UiState.Success("data")
        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals("data", state.data)
    }

    @Test
    fun error_isError() {
        val state = UiState.Error("something failed")
        assertTrue(state.isError)
        assertEquals("something failed", state.message)
    }

    @Test
    fun empty_isEmpty() {
        val state: UiState<Nothing> = UiState.Empty
        assertTrue(state.isEmpty)
    }

    @Test
    fun dataOrNull_success_returnsData() {
        val state = UiState.Success(42)
        assertEquals(42, state.dataOrNull)
    }

    @Test
    fun dataOrNull_loading_returnsNull() {
        val state: UiState<Int> = UiState.Loading
        assertNull(state.dataOrNull)
    }

    @Test
    fun dataOrNull_error_returnsNull() {
        val state: UiState<Int> = UiState.Error("err")
        assertNull(state.dataOrNull)
    }

    @Test
    fun map_success_transforms() {
        val state = UiState.Success(5)
        val mapped = state.map { it * 2 }
        assertTrue(mapped is UiState.Success)
        assertEquals(10, (mapped as UiState.Success).data)
    }

    @Test
    fun map_idle_staysIdle() {
        val state: UiState<Int> = UiState.Idle
        val mapped = state.map { it * 2 }
        assertTrue(mapped is UiState.Idle)
    }

    @Test
    fun map_loading_staysLoading() {
        val state: UiState<Int> = UiState.Loading
        val mapped = state.map { it * 2 }
        assertTrue(mapped is UiState.Loading)
    }

    @Test
    fun map_error_preservesMessage() {
        val state: UiState<Int> = UiState.Error("fail")
        val mapped = state.map { it * 2 }
        assertTrue(mapped is UiState.Error)
        assertEquals("fail", (mapped as UiState.Error).message)
    }

    @Test
    fun map_empty_staysEmpty() {
        val state: UiState<Int> = UiState.Empty
        val mapped = state.map { it * 2 }
        assertTrue(mapped is UiState.Empty)
    }

    @Test
    fun onSuccess_callsActionOnSuccess() {
        var called = false
        UiState.Success("x").onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun onSuccess_doesNotCallOnError() {
        var called = false
        UiState.Error("x").onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun onError_callsActionOnError() {
        var msg = ""
        UiState.Error("oops").onError { msg = it }
        assertEquals("oops", msg)
    }

    @Test
    fun onError_doesNotCallOnSuccess() {
        var called = false
        UiState.Success("x").onError { called = true }
        assertFalse(called)
    }

    @Test
    fun onLoading_callsActionOnLoading() {
        var called = false
        UiState.Loading.onLoading { called = true }
        assertTrue(called)
    }

    @Test
    fun onEmpty_callsActionOnEmpty() {
        var called = false
        UiState.Empty.onEmpty { called = true }
        assertTrue(called)
    }

    @Test
    fun asUiState_emitsLoadingThenSuccess() = runTest {
        val results = flowOf(42).asUiState().toList()
        assertEquals(2, results.size)
        assertTrue(results[0] is UiState.Loading)
        assertTrue(results[1] is UiState.Success)
        assertEquals(42, (results[1] as UiState.Success).data)
    }

    @Test
    fun runCatchingToUiState_success_emitsLoadingThenSuccess() = runTest {
        val results = flowOf("ok").runCatchingToUiState().toList()
        assertEquals(2, results.size)
        assertTrue(results[0] is UiState.Loading)
        assertEquals("ok", (results[1] as UiState.Success).data)
    }

    @Test
    fun runCatchingToUiState_error_emitsLoadingThenError() = runTest {
        val errorFlow = flow<String> { throw RuntimeException("boom") }
        val results = errorFlow.runCatchingToUiState().toList()
        assertEquals(2, results.size)
        assertTrue(results[0] is UiState.Loading)
        assertTrue(results[1] is UiState.Error)
        assertEquals("boom", (results[1] as UiState.Error).message)
    }
}
