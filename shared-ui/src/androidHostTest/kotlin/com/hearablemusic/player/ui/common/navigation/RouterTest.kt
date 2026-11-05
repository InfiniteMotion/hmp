package com.hearablemusic.player.ui.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.*
import org.junit.Test

/**
 * Router 单元测试（基于真实 NavBackStack，避免 mockk 对 Compose 快照类的挂起问题）
 */
class RouterTest {

    private fun createRouter(vararg initial: NavKey): Pair<Router, NavBackStack<NavKey>> {
        val stack = NavBackStack<NavKey>()
        initial.forEach { stack.add(it) }
        return Router(stack) to stack
    }

    @Test
    fun `navigateTo should add route`() {
        val (router, stack) = createRouter(Routes.Main.Tabs)
        router.navigateTo(Routes.Library.Search)
        assertEquals(Routes.Library.Search, stack.last())
    }

    @Test
    fun `navigateReplace should remove last and add when stack not empty`() {
        val (router, stack) = createRouter(Routes.Main.Tabs, Routes.Library.Search)
        router.navigateReplace(Routes.Player.Player)
        assertEquals(listOf<NavKey>(Routes.Main.Tabs, Routes.Player.Player), stack.toList())
    }

    @Test
    fun `navigateReplace should only add when stack empty`() {
        val (router, stack) = createRouter()
        router.navigateReplace(Routes.Main.Tabs)
        assertEquals(listOf<NavKey>(Routes.Main.Tabs), stack.toList())
    }

    @Test
    fun `navigateSingleTop should not add if already on top`() {
        val (router, stack) = createRouter(Routes.Main.Tabs)
        router.navigateSingleTop(Routes.Main.Tabs)
        assertEquals(listOf<NavKey>(Routes.Main.Tabs), stack.toList())
    }

    @Test
    fun `navigateSingleTop should remove routes above existing target`() {
        val (router, stack) = createRouter(Routes.Main.Tabs, Routes.Library.Search, Routes.Library.Album("Album"))
        router.navigateSingleTop(Routes.Library.Search)
        assertEquals(listOf<NavKey>(Routes.Main.Tabs, Routes.Library.Search), stack.toList())
    }

    @Test
    fun `navigateSingleTop should add new route if not in stack`() {
        val (router, stack) = createRouter(Routes.Main.Tabs)
        router.navigateSingleTop(Routes.Library.Search)
        assertEquals(listOf<NavKey>(Routes.Main.Tabs, Routes.Library.Search), stack.toList())
    }

    @Test
    fun `popBackStack should return true and remove last when successful`() {
        val (router, stack) = createRouter(Routes.Main.Tabs, Routes.Library.Search)
        assertTrue(router.popBackStack())
        assertEquals(listOf<NavKey>(Routes.Main.Tabs), stack.toList())
    }

    @Test
    fun `popBackStack should return false when stack empty`() {
        val (router, _) = createRouter()
        assertFalse(router.popBackStack())
    }

    @Test
    fun `popBackStackTo should return false when route not found`() {
        val (router, _) = createRouter(Routes.Main.Tabs)
        assertFalse(router.popBackStackTo(Routes.Library.Search))
    }

    @Test
    fun `popBackStackTo should remove routes above target when inclusive false`() {
        val (router, stack) = createRouter(
            Routes.Main.Tabs,
            Routes.Library.Search,
            Routes.Library.Album("Album"),
            Routes.Library.Artist("Artist"),
        )
        assertTrue(router.popBackStackTo(Routes.Library.Search))
        assertEquals(listOf<NavKey>(Routes.Main.Tabs, Routes.Library.Search), stack.toList())
    }

    @Test
    fun `popBackStackTo should remove routes including target when inclusive true`() {
        val (router, stack) = createRouter(
            Routes.Main.Tabs,
            Routes.Library.Search,
            Routes.Library.Album("Album"),
            Routes.Library.Artist("Artist"),
        )
        assertTrue(router.popBackStackTo(Routes.Library.Search, inclusive = true))
        assertEquals(listOf<NavKey>(Routes.Main.Tabs), stack.toList())
    }

    @Test
    fun `currentRoute should return last element`() {
        val (router, _) = createRouter(Routes.Main.Tabs, Routes.Library.Search)
        assertEquals(Routes.Library.Search, router.currentRoute())
    }

    @Test
    fun `currentRoute should return null when stack empty`() {
        val (router, _) = createRouter()
        assertNull(router.currentRoute())
    }

    @Test
    fun `containsRoute should return true when route exists`() {
        val (router, _) = createRouter(Routes.Main.Tabs, Routes.Library.Search)
        assertTrue(router.containsRoute(Routes.Library.Search))
    }

    @Test
    fun `containsRoute should return false when route not exists`() {
        val (router, _) = createRouter(Routes.Main.Tabs)
        assertFalse(router.containsRoute(Routes.Library.Search))
    }

    @Test
    fun `clearBackStack should remove all except first`() {
        val (router, stack) = createRouter(
            Routes.Main.Tabs,
            Routes.Library.Search,
            Routes.Library.Album("Album"),
            Routes.Library.Artist("Artist"),
        )
        router.clearBackStack()
        assertEquals(listOf<NavKey>(Routes.Main.Tabs), stack.toList())
    }

    @Test
    fun `clearBackStack should do nothing when size 0 or 1`() {
        val (router, stack) = createRouter(Routes.Main.Tabs)
        router.clearBackStack()
        assertEquals(listOf<NavKey>(Routes.Main.Tabs), stack.toList())
    }
}
