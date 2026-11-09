package com.hearablemusic.player.ui.common.pages.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hearablemusic.player.ui.common.components.base.BackButton
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.layout.WindowWidthSizeClass

@Composable
fun SubScreen(
    onBackClick: () -> Unit,
    title: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val horizontalPadding = when (LocalWindowSizeInfo.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> metrics.bar
        WindowWidthSizeClass.Medium -> metrics.block
        WindowWidthSizeClass.Compact -> metrics.cozy
    }
    var visible by rememberSaveable { mutableStateOf(false) }

    val currentContent by rememberUpdatedState(content)
    val currentTitle by rememberUpdatedState(title)

    // 启动动画
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = HmpMotion.enter(),
        ),
        exit = fadeOut(
            animationSpec = HmpMotion.exit(),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = metrics.cozy),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(),
            ) {
                // 返回按钮（与右侧按钮垂直居中对齐）
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    BackButton(onClick = onBackClick)
                }

                // 标题（如果提供）
                currentTitle?.let {
                    Text(
                        text = it,
                        style = typo.headline,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sizeIn(maxWidth = LayoutConstraints.PanelMaxWidth)
                            .align(Alignment.Center),
                    )
                }
                // 右侧与返回按钮对称的操作按钮（可选）
                trailingContent?.let { content ->
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) { content() }
                }
            }

            // 主内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    currentContent()
                }
            }
        }
    }
}
