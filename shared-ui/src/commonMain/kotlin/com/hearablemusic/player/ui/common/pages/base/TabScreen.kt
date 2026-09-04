package com.hearablemusic.player.ui.common.pages.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.SearchButton
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass


val LocalTabHeaderContent = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun TabScreen(
    title: String? = null,
    hasSearchBotton: Boolean = false,
    navController: NavBackStack<NavKey>? = null,
    trailing: @Composable (() -> Unit)? = null,
    showHeader: Boolean = true,
    content: @Composable () -> Unit
) {
    val isLandscape = LocalWindowSizeInfo.current.isLandscape
    val horizontalPadding = when (LocalWindowSizeInfo.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 32.dp
        WindowWidthSizeClass.Medium -> 24.dp
        WindowWidthSizeClass.Compact -> 16.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            if (showHeader && !isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = horizontalPadding, end = horizontalPadding, top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (trailing != null) trailing()
                        if (hasSearchBotton && navController != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            SearchButton(navController)
                        }
                    }
                } else {
                    // 无 title 但有 trailing/search → 只渲染右侧操作区
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (trailing != null) trailing()
                        if (hasSearchBotton && navController != null) {
                            if (trailing != null) Spacer(modifier = Modifier.width(8.dp))
                            SearchButton(navController)
                        }
                    }
                }
            }
            }
            content()
        }
    }
}
