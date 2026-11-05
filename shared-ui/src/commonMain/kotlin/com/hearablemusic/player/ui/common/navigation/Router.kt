package com.hearablemusic.player.ui.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * 统一的路由导航接口
 * 提供类型安全的页面跳转 API，分离导航逻辑和 UI 组件
 *
 * 该接口封装了 Navigation3 的导航操作，提供了更高层次的抽象，
 * 使得 ViewModel 和 Composable 可以通过依赖注入或 rememberRouter 获取导航能力，
 * 而不需要直接依赖 NavController。
 *
 * 所有导航操作都是同步的，且必须在主线程调用。
 */
interface RouteNavigator {
    /**
     * 导航到指定路由
     * @param route 目标路由键
     */
    fun navigateTo(route: NavKey)

    /**
     * 导航到指定路由，并替换当前页面
     * @param route 目标路由键
     */
    fun navigateReplace(route: NavKey)

    /**
     * 导航到指定路由，并清空返回栈（除了新路由）
     * @param route 目标路由键
     */
    fun navigateSingleTop(route: NavKey)

    /**
     * 返回上一个页面
     * @return 是否成功返回（如果已经是第一个页面则返回 false）
     */
    fun popBackStack(): Boolean

    /**
     * 返回到指定路由
     * @param route 目标路由键
     * @param inclusive 是否包含目标路由本身（true 表示弹出目标路由）
     */
    fun popBackStackTo(route: NavKey, inclusive: Boolean = false): Boolean

    /**
     * 获取当前路由
     * @return 当前路由键，如果返回栈为空则返回 null
     */
    fun currentRoute(): NavKey?

    /**
     * 检查指定路由是否在返回栈中
     * @param route 要检查的路由键
     * @return 如果路由在返回栈中则返回 true
     */
    fun containsRoute(route: NavKey): Boolean

    /**
     * 清空返回栈（只保留第一个路由）
     */
    fun clearBackStack()
}

/**
 * Router 实现类
 * 封装 NavBackStack 提供具体的导航操作
 *
 * 该类实现了 RouteNavigator 接口，将高层导航操作映射到底层 NavBackStack 的堆栈管理。
 * 注意：该类不处理动画，动画由 NavigationGraph 配置。
 */
class Router(
    private val navBackStack: NavBackStack<NavKey>,
) : RouteNavigator {
    override fun navigateTo(route: NavKey) {
        navBackStack.add(route)
    }

    override fun navigateReplace(route: NavKey) {
        if (navBackStack.size > 0) {
            navBackStack.removeLastOrNull()
        }
        navBackStack.add(route)
    }

    override fun navigateSingleTop(route: NavKey) {
        // 检查是否已经在栈顶
        if (navBackStack.lastOrNull() == route) {
            return
        }
        // 移除栈中已有的相同路由（如果存在）
        val index = navBackStack.indexOfFirst { key -> key == route }
        if (index >= 0) {
            // 移除该位置之后的所有路由
            while (navBackStack.size > index + 1) {
                navBackStack.removeLastOrNull()
            }
            // 现在栈顶就是目标路由，无需添加
            return
        }
        // 添加新路由
        navBackStack.add(route)
    }

    override fun popBackStack(): Boolean = navBackStack.removeLastOrNull() != null

    override fun popBackStackTo(route: NavKey, inclusive: Boolean): Boolean {
        val index = navBackStack.indexOfFirst { key -> key == route }
        if (index < 0) {
            return false
        }
        val targetIndex = if (inclusive) index else index + 1
        while (navBackStack.size > targetIndex) {
            navBackStack.removeLastOrNull()
        }
        return true
    }

    override fun currentRoute(): NavKey? = navBackStack.lastOrNull()

    override fun containsRoute(route: NavKey): Boolean = navBackStack.any { key -> key == route }

    override fun clearBackStack() {
        while (navBackStack.size > 1) {
            navBackStack.removeLastOrNull()
        }
    }
}

/**
 * 创建 Router 的扩展函数，便于在可组合函数中获取
 *
 * 使用 remember 和 navBackStack 作为 key，确保在同一个 NavBackStack 实例中 Router 是稳定的，
 * 避免在重组时重新创建 Router 实例。
 *
 * @param navBackStack Navigation3 的导航堆栈实例
 * @return 与 navBackStack 关联的 Router 实例
 */
@Composable
fun rememberRouter(navBackStack: NavBackStack<NavKey>): Router = remember(navBackStack) { Router(navBackStack) }
