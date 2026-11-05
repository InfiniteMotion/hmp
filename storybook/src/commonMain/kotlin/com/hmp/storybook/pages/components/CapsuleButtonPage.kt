package com.hmp.storybook.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.LocalAppLanguage

/**
 * 标签分类枚举
 */
private enum class TagCategory(val key: String) {
    GENRE("GENRE"), // 曲风
    MOOD("MOOD"), // 情绪
    SCENARIO("SCENARIO"), // 场景
    LANGUAGE("LANGUAGE"), // 语言
    ERA("ERA"), // 年代
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapsuleButtonPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.capsuleButton(lang),
        description = if (lang == AppLanguage.ZH) {
            "胶囊标签组件 (Capsule)，FlowRow 布局，展示真实标签分类"
        } else {
            "Capsule tag component with FlowRow layout, showing real tag categories"
        },
        onBack = onBack,
    ) {
        // 基础样式
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "基础样式" else "Basic Styles",
            description = if (lang == AppLanguage.ZH) {
                "形状 RoundedCornerShape(16.dp)，内边距 水平 12.dp / 垂直 6.dp，文字颜色 onPrimary"
            } else {
                "Shape RoundedCornerShape(16.dp), padding H=12.dp / V=6.dp, text color onPrimary"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CapsuleButtonPreview(
                    text = if (lang == AppLanguage.ZH) "已选中" else "Selected",
                    selected = true,
                )
                CapsuleButtonPreview(
                    text = if (lang == AppLanguage.ZH) "未选中" else "Unselected",
                    selected = false,
                )
                CapsuleButtonPreview(
                    text = if (lang == AppLanguage.ZH) "描边" else "Outlined",
                    selected = false,
                    outlined = true,
                )
            }
        }

        // LabelsCapsule 展示：真实标签分类
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "LabelsCapsule 标签分类" else "LabelsCapsule Tag Categories",
            description = if (lang == AppLanguage.ZH) {
                "技术信息使用 tertiary + labelMedium，标签使用 primary + labelSmall，FlowRow 布局，每行最多 4 个，间距 8.dp"
            } else {
                "Tech info uses tertiary + labelMedium, tags use primary + labelSmall, FlowRow layout, max 4 per row, spacing 8.dp"
            },
        ) {
            // 曲风 GENRE
            LabelsCapsuleGroup(
                category = TagCategory.GENRE,
                lang = lang,
                tags = if (lang == AppLanguage.ZH) {
                    listOf("流行", "摇滚", "古典", "爵士", "电子", "民谣", "R&B", "嘻哈")
                } else {
                    listOf("Pop", "Rock", "Classical", "Jazz", "Electronic", "Folk", "R&B", "Hip-Hop")
                },
            )

            // 情绪 MOOD
            LabelsCapsuleGroup(
                category = TagCategory.MOOD,
                lang = lang,
                tags = if (lang == AppLanguage.ZH) {
                    listOf("开心", "伤感", "放松", "激昂", "浪漫", "孤独")
                } else {
                    listOf("Happy", "Sad", "Relaxed", "Energetic", "Romantic", "Lonely")
                },
            )

            // 场景 SCENARIO
            LabelsCapsuleGroup(
                category = TagCategory.SCENARIO,
                lang = lang,
                tags = if (lang == AppLanguage.ZH) {
                    listOf("工作", "运动", "学习", "通勤", "派对", "睡前")
                } else {
                    listOf("Work", "Exercise", "Study", "Commute", "Party", "Bedtime")
                },
            )

            // 语言 LANGUAGE
            LabelsCapsuleGroup(
                category = TagCategory.LANGUAGE,
                lang = lang,
                tags = if (lang == AppLanguage.ZH) {
                    listOf("中文", "英文", "日文", "韩文", "法文", "西班牙文")
                } else {
                    listOf("Chinese", "English", "Japanese", "Korean", "French", "Spanish")
                },
            )

            // 年代 ERA
            LabelsCapsuleGroup(
                category = TagCategory.ERA,
                lang = lang,
                tags = if (lang == AppLanguage.ZH) {
                    listOf("70s", "80s", "90s", "00s", "10s", "20s")
                } else {
                    listOf("70s", "80s", "90s", "00s", "10s", "20s")
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsCapsuleGroup(
    category: TagCategory,
    lang: AppLanguage,
    tags: List<String>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 分类标题：tertiary 颜色 + labelMedium
        val categoryLabel = when (category) {
            TagCategory.GENRE -> if (lang == AppLanguage.ZH) "曲风 GENRE" else "GENRE"
            TagCategory.MOOD -> if (lang == AppLanguage.ZH) "情绪 MOOD" else "MOOD"
            TagCategory.SCENARIO -> if (lang == AppLanguage.ZH) "场景 SCENARIO" else "SCENARIO"
            TagCategory.LANGUAGE -> if (lang == AppLanguage.ZH) "语言 LANGUAGE" else "LANGUAGE"
            TagCategory.ERA -> if (lang == AppLanguage.ZH) "年代 ERA" else "ERA"
        }
        Text(
            text = categoryLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // FlowRow 布局，每行最多 4 个，间距 8.dp
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tags.forEachIndexed { index, tag ->
                CapsuleButtonPreview(
                    text = tag,
                    selected = index == 0,
                )
            }
        }
    }
}

@Composable
private fun CapsuleButtonPreview(
    text: String,
    selected: Boolean,
    outlined: Boolean = false,
) {
    val bgColor = when {
        selected && !outlined -> MaterialTheme.colorScheme.primary
        outlined -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        selected && !outlined -> MaterialTheme.colorScheme.onPrimary
        outlined -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}
