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
import com.hearablemusic.player.ui.common.components.base.SearchButton
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass

val LocalTabHeaderContent = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun TabScreen(
    title: String? = null,
    /** 传则渲染搜索按钮；导航由调用方决定（组件不再持有 NavBackStack / 硬编码路由）。 */
    onSearchClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    showHeader: Boolean = true,
    content: @Composable () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val isLandscape = LocalWindowSizeInfo.current.isLandscape
    val horizontalPadding = when (LocalWindowSizeInfo.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> metrics.block
        WindowWidthSizeClass.Medium -> metrics.section
        WindowWidthSizeClass.Compact -> metrics.cozy
    }
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showHeader && !isLandscape) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = horizontalPadding, end = horizontalPadding, top = metrics.cozy, bottom = metrics.cozy),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (title != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = metrics.cozy),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = typo.title,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (trailing != null) trailing()
                            if (onSearchClick != null) {
                                Spacer(modifier = Modifier.width(metrics.compact))
                                SearchButton(onClick = onSearchClick)
                            }
                        }
                    } else {
                        // 无 title 但有 trailing/search → 只渲染右侧操作区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (trailing != null) trailing()
                            if (onSearchClick != null) {
                                if (trailing != null) Spacer(modifier = Modifier.width(metrics.compact))
                                SearchButton(onClick = onSearchClick)
                            }
                        }
                    }
                }
            }
            content()
        }
    }
}
