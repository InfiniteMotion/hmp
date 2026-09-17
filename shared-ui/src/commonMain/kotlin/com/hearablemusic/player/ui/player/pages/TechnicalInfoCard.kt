package com.hearablemusic.player.ui.player.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.unit.dp
import com.hmp.domain.music.MusicExtra
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.bitrate
import com.hearablemusic.player.ui.generated.resources.file_size
import com.hearablemusic.player.ui.generated.resources.format
import com.hearablemusic.player.ui.generated.resources.sample_rate
import org.jetbrains.compose.resources.stringResource

@Composable
fun TechnicalInfoCard(
    extra: MusicExtra?,
    modifier: Modifier = Modifier
) {
    HMPCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalHMPDimens.current.corner.sm),
        contentPadding = Modifier.padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 第一行：比特率和采样率
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                extra?.bitRate?.let {
                    TechnicalInfoItem(stringResource(Res.string.bitrate), "$it kbps")
                }
                extra?.sampleRate?.let {
                    TechnicalInfoItem(stringResource(Res.string.sample_rate), "$it Hz")
                }
            }

            // 第二行：文件大小和格式
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                extra?.fileSize?.let {
                    TechnicalInfoItem(stringResource(Res.string.file_size), formatFileSize(it))
                }
                extra?.format?.let {
                    TechnicalInfoItem(stringResource(Res.string.format), it)
                }
            }
        }
    }
}

@Composable
private fun TechnicalInfoItem(
    label: String,
    value: String
) {
    Row (
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// commonMain 无 java.lang.String.format，一位小数手动四舍五入（与旧 %.1f 输出一致）
private fun formatFileSize(bytes: Long): String {
    val tenths = when {
        bytes >= 1024 * 1024 -> (bytes / (1024.0 * 1024.0) * 10).toLong()
        bytes >= 1024 -> (bytes / 1024.0 * 10).toLong()
        else -> return "$bytes B"
    }
    val value = tenths / 10.0
    val unit = if (bytes >= 1024 * 1024) "MB" else "KB"
    return "$value $unit"
}