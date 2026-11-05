package com.hmp.storybook.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun DailyHeroCardPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.dailyHeroCard(lang),
        description = if (lang == AppLanguage.ZH) {
            "每日推荐卡片 (DailyHeroCard)，正方形比例，底部渐变遮罩，播放按钮"
        } else {
            "Daily recommendation card (DailyHeroCard) with square aspect ratio, bottom gradient overlay, and play button"
        },
        onBack = onBack,
    ) {
        // 标准卡片
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "每日推荐" else "Daily Recommendation",
            description = if (lang == AppLanguage.ZH) "AI 驱动的每日音乐推荐卡片" else "AI-powered daily music recommendation card",
        ) {
            DailyHeroCardPreview(
                title = "Clair de Lune",
                artist = "Claude Debussy",
                gradientColors = listOf(HDBlue, Color(0xFF6C63FF)),
            )
        }

        // 暖色调风格
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "暖色调风格" else "Warm Style",
            description = if (lang == AppLanguage.ZH) "暖色渐变背景的推荐卡片" else "Recommendation card with warm gradient",
        ) {
            DailyHeroCardPreview(
                title = "Here Comes the Sun",
                artist = "The Beatles",
                gradientColors = listOf(Color(0xFFFF6B35), Color(0xFFF7C59F)),
            )
        }

        // 长标题截断
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "长标题截断" else "Long Title Truncation",
            description = if (lang == AppLanguage.ZH) "标题最多 2 行，艺术家最多 1 行" else "Title max 2 lines, artist max 1 line",
        ) {
            DailyHeroCardPreview(
                title = "This Is A Very Long Song Title That Should Be Truncated After Two Lines Are Reached",
                artist = "An Artist With A Very Long Name That Also Needs Truncation",
                gradientColors = listOf(Color(0xFF2ECC71), Color(0xFF27AE60)),
            )
        }
    }
}

@Composable
private fun DailyHeroCardPreview(
    title: String,
    artist: String,
    gradientColors: List<Color> = listOf(HDBlue, Color(0xFF6C63FF)),
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(colors = gradientColors),
                ),
        ) {
            // 内容区域：水平 32.dp, 垂直 16.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 底部渐变遮罩 + 文字信息
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // 标题：headlineLarge, ExtraBold, White, 最多 2 行
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // 艺术家：headlineMedium, White(0.8f), 最多 1 行
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // 播放按钮：FilledIconButton, 48.dp, primary 背景 + onPrimary 图标, 图标 28.dp
                        FilledIconButton(
                            onClick = {},
                            modifier = Modifier
                                .align(Alignment.Start)
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(24.dp),
                                ),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }

            // 底部渐变遮罩：从透明到 Color.Black.copy(alpha = 0.8f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )
        }
    }
}
