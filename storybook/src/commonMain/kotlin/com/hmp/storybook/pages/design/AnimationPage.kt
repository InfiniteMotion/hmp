package com.hmp.storybook.pages.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Spring.DampingRatioHighBouncy
import androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
import androidx.compose.animation.core.Spring.StiffnessLow
import androidx.compose.animation.core.Spring.StiffnessMedium
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun AnimationPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.animation(lang),
        description = Strings.animationDescription(lang),
        onBack = onBack,
    ) {
        // 持续时间
        ComponentShowcase(
            title = Strings.duration(lang),
            description = if (lang == AppLanguage.ZH) {
                "四档动画持续时间，对应不同交互场景"
            } else {
                "Four animation duration tiers for different interaction scenarios"
            },
        ) {
            val durations = listOf(
                "MICRO_INTERACTION" to 200,
                "TRANSITION" to 400,
                "COMPLEX" to 650,
                "BACKGROUND" to 3000,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                durations.forEach { (name, ms) ->
                    val infiniteTransition = rememberInfiniteTransition(label = name)
                    val progress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = ms, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = name,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "$name",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(120.dp),
                        )
                        Text(
                            text = "${ms}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HDBlue),
                            )
                        }
                    }
                }
            }
        }

        // 缓动函数
        ComponentShowcase(
            title = Strings.easing(lang),
            description = if (lang == AppLanguage.ZH) {
                "三组缓动函数，控制动画的加速和减速节奏"
            } else {
                "Three easing curves controlling animation acceleration and deceleration"
            },
        ) {
            val easings = listOf(
                "EASE_IN_OUT" to CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f),
                "EASE_OUT" to CubicBezierEasing(0.2f, 0.0f, 0.1f, 1.0f),
                "EASE_IN" to CubicBezierEasing(0.6f, 0.0f, 0.8f, 1.0f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                easings.forEach { (name, easing) ->
                    val infiniteTransition = rememberInfiniteTransition(label = name)
                    val offset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = easing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = name,
                    )
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        translationX = offset * 300f
                                    }
                                    .background(HDBlue, CircleShape),
                            )
                        }
                    }
                }
            }
        }

        // Spring 配置
        ComponentShowcase(
            title = Strings.springConfig(lang),
            description = if (lang == AppLanguage.ZH) {
                "Spring 弹簧动画配置，用于物理感交互反馈"
            } else {
                "Spring animation configs for physics-based interaction feedback"
            },
        ) {
            val springs = listOf(
                "SPRING_MEDIUM" to spring<Float>(dampingRatio = DampingRatioMediumBouncy, stiffness = StiffnessMediumLow),
                "SPRING_BOUNCY" to spring<Float>(dampingRatio = DampingRatioHighBouncy, stiffness = StiffnessMedium),
                "SPRING_GENTLE" to spring<Float>(dampingRatio = DampingRatioHighBouncy, stiffness = StiffnessLow),
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                springs.forEach { (name, springSpec) ->
                    var targetValue by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            targetValue = 1f
                            kotlinx.coroutines.delay(1500)
                            targetValue = 0f
                            kotlinx.coroutines.delay(500)
                        }
                    }
                    val animatable = remember { Animatable(0f) }
                    LaunchedEffect(targetValue) {
                        animatable.animateTo(
                            targetValue = targetValue,
                            animationSpec = springSpec,
                        )
                    }
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        translationX = animatable.value * 300f
                                    }
                                    .background(HDBlue, CircleShape),
                            )
                        }
                    }
                }
            }
        }

        // 动态背景说明
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "动态背景" else "Dynamic Background",
            description = if (lang == AppLanguage.ZH) {
                "三种动态背景风格，用于沉浸式页面体验"
            } else {
                "Three dynamic background styles for immersive page experiences"
            },
        ) {
            val bgStyles = listOf(
                if (lang == AppLanguage.ZH) "FLUID" to "流体渐变，平滑色彩过渡" else "FLUID" to "Fluid gradient with smooth color transitions",
                if (lang == AppLanguage.ZH) "SPOTS" to "光斑浮动，柔和光晕效果" else "SPOTS" to "Floating light spots with soft halo effects",
                if (lang == AppLanguage.ZH) "BLUR" to "模糊层次，景深虚化背景" else "BLUR" to "Blurred layers with depth-of-field background",
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                bgStyles.forEach { (name, desc) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(140.dp),
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
