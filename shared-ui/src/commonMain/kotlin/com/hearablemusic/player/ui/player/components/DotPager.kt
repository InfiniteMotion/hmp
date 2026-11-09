package com.hearablemusic.player.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun DotPager(
    pageContent: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val metrics = LocalHmpMetrics.current
    val pageCount = pageContent.size
    // 页数变化时重建 pager：rememberPagerState 的 pageCount lambda 不会驱动状态重置，
    // 旧 currentPage 可能越界；同时给 page 内容加 getOrNull 兜底。
    key(pageCount) {
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            pageCount = { pageCount },
        )
        Column(modifier = modifier) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    pageContent.getOrNull(page)?.invoke()
                }
            }

            // 极简圆点指示器（仅多页时显示）
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = metrics.compact),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(metrics.micro),
                    ) {
                        repeat(pageCount) { index ->
                            val size = metrics.micro
                            val color = if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            }

                            Box(
                                modifier = Modifier
                                    .size(size)
                                    .background(color, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
