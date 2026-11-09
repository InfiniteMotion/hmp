package com.hearablemusic.player.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.library.pages.GalleryScreen
import com.hearablemusic.player.ui.library.pages.HomeScreen
import com.hearablemusic.player.ui.library.pages.ListScreen
import com.hearablemusic.player.ui.settings.pages.UserScreen

/**
 * Tab 壳。
 *
 * 形态：HorizontalPager 4 页（Home/Gallery/List/User 真实页面），
 * 切页触觉反馈；TabPageIndicator 浮层与 BottomFusionBar 由 AppRoot 持有
 * （pagerState 由 AppRoot 传入共用）。tabHeader 经 LocalTabHeaderContent 下发。
 */
@Composable
fun MainShell(
    navController: NavBackStack<NavKey>,
    pagerState: PagerState,
) {
    val haptic = rememberHapticFeedback()
    var previousPage by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != previousPage) {
            haptic.performClick()
            previousPage = pagerState.currentPage
        }
    }

    // 四页常驻是设计前提（保留各 tab 的组合状态与滚动位置），不靠减少
    // beyondViewportPageCount 来释放订阅；订阅生命周期改由 LocalTabVisible 表达。
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 3,
    ) { page ->
        when (page) {
            0 -> HomeScreen(
                navController = navController,
            )

            1 -> GalleryScreen(
                navController = navController,
            )

            2 -> ListScreen(
                navController = navController,
            )

            3 -> UserScreen(
                navController = navController,
            )
        }
    }
}
