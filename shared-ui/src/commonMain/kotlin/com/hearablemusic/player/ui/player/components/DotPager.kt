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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun DotPager(
    pageContent: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0
) {
    val metrics = LocalHmpMetrics.current
    val pageCount = pageContent.size
    // 基础滑动页
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )
    Column (modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                pageContent[page]()
            }
        }

        // 极简圆点指示器（仅多页时显示）
        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = metrics.compact),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier,
                    horizontalArrangement = Arrangement.spacedBy(metrics.micro)
                ) {
                    repeat(pageCount) { index ->
                        val size = metrics.micro
                        val color = if (pagerState.currentPage == index)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

                        Box(
                            modifier = Modifier
                                .size(size)
                                .background(color, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
