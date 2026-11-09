package com.hearablemusic.player.ui.player.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.bitrate
import com.hearablemusic.player.ui.generated.resources.file_size
import com.hearablemusic.player.ui.generated.resources.format
import com.hearablemusic.player.ui.generated.resources.sample_rate
import com.hmp.domain.music.MusicExtra
import org.jetbrains.compose.resources.stringResource

@Composable
fun TechnicalInfoCard(
    extra: MusicExtra?,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    HMPCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(metrics.base),
        contentPadding = Modifier.padding(metrics.cozy),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
            // 第一行：比特率和采样率
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                horizontalArrangement = Arrangement.SpaceBetween,
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
    value: String,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typo.footnote.medium(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(metrics.compact))
        Text(
            text = value,
            style = typo.caption,
            color = MaterialTheme.colorScheme.onSurface,
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
