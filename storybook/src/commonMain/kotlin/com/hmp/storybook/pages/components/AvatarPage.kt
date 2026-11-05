package com.hmp.storybook.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hmp.storybook.i18n.Strings
import com.hmp.storybook.layout.ComponentShowcase
import com.hmp.storybook.layout.StorybookPage
import com.hmp.storybook.theme.AppLanguage
import com.hmp.storybook.theme.HDBlue
import com.hmp.storybook.theme.LocalAppLanguage

@Composable
fun AvatarPage(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current

    StorybookPage(
        title = Strings.avatar(lang),
        description = if (lang == AppLanguage.ZH) {
            "头像组件 (Avatar)，CircleShape，参数 aSize (Int, dp值)，典型调用 UserScreen 中 aSize=100"
        } else {
            "Avatar component with CircleShape, parameter aSize (Int, dp value), typical call in UserScreen with aSize=100"
        },
        onBack = onBack,
    ) {
        // 多种尺寸展示：32, 48, 64, 100 dp
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "尺寸变体" else "Size Variants",
            description = if (lang == AppLanguage.ZH) {
                "展示 32 / 48 / 64 / 100 dp 四种典型尺寸"
            } else {
                "Showing 4 typical sizes: 32 / 48 / 64 / 100 dp"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarPreview(aSize = 32, label = "32 dp")
                AvatarPreview(aSize = 48, label = "48 dp")
                AvatarPreview(aSize = 64, label = "64 dp")
                AvatarPreview(aSize = 100, label = "100 dp")
            }
        }

        // 有图片状态 (Coil AsyncImage)
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "有图片状态" else "With Image",
            description = if (lang == AppLanguage.ZH) {
                "使用 Coil AsyncImage 加载网络图片，CircleShape 裁剪"
            } else {
                "Using Coil AsyncImage to load network image, clipped with CircleShape"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarWithImagePreview(aSize = 48, label = "48 dp")
                AvatarWithImagePreview(aSize = 64, label = "64 dp")
                AvatarWithImagePreview(aSize = 100, label = "100 dp (UserScreen)")
            }
        }

        // 无图片状态 (占位图)
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "无图片状态" else "Without Image",
            description = if (lang == AppLanguage.ZH) {
                "无图片时显示渐变占位图 + Person 图标"
            } else {
                "Showing gradient placeholder with Person icon when no image is available"
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarPlaceholderPreview(aSize = 32, label = "32 dp")
                AvatarPlaceholderPreview(aSize = 48, label = "48 dp")
                AvatarPlaceholderPreview(aSize = 64, label = "64 dp")
                AvatarPlaceholderPreview(aSize = 100, label = "100 dp")
            }
        }

        // UserScreen 典型调用
        ComponentShowcase(
            title = if (lang == AppLanguage.ZH) "UserScreen 典型调用" else "UserScreen Typical Usage",
            description = if (lang == AppLanguage.ZH) {
                "UserScreen 中 Avatar(aSize = 100)，居中显示"
            } else {
                "Avatar(aSize = 100) in UserScreen, centered display"
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarPlaceholderPreview(aSize = 100, label = "")
                Text(
                    text = if (lang == AppLanguage.ZH) "用户名" else "Username",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (lang == AppLanguage.ZH) "user@example.com" else "user@example.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Avatar 预览：模拟真实 Avatar 组件
 * 参数 aSize: Int (dp 值)
 * 形状: CircleShape
 */
@Composable
private fun AvatarPreview(
    aSize: Int,
    label: String = "",
) {
    val sizeDp = aSize.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(HDBlue, Color(0xFF6C63FF)),
                    ),
                ),
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 有图片状态的 Avatar：使用 Coil AsyncImage
 * Storybook 中用渐变模拟，真实实现使用 Coil AsyncImage
 */
@Composable
private fun AvatarWithImagePreview(
    aSize: Int,
    label: String = "",
) {
    val sizeDp = aSize.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2ECC71), Color(0xFF27AE60)),
                    ),
                ),
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 无图片状态的 Avatar：占位图 + Person 图标
 */
@Composable
private fun AvatarPlaceholderPreview(
    aSize: Int,
    label: String = "",
) {
    val sizeDp = aSize.dp
    val iconSize = (aSize * 0.45f).dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(iconSize),
            )
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
