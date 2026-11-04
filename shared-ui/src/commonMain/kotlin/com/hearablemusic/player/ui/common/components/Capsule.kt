package com.hearablemusic.player.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.hmp.domain.music.MusicExtra
import com.hmp.domain.music.MusicLabel
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.*
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun Capsule(
    text: String,
    style: TextStyle = LocalHmpTypo.current.body,
    color: Color = MaterialTheme.colorScheme.primary,
){
    val metrics = LocalHmpMetrics.current
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(metrics.cozy)
            )
            .padding(horizontal = metrics.base, vertical = metrics.micro)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelsCapsule(
    extra: MusicExtra?,
    labels: List<MusicLabel?>
){
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(vertical = metrics.cozy, horizontal = metrics.cozy),
        verticalArrangement = Arrangement.Center
    ) {
        val color = MaterialTheme.colorScheme.tertiary
        var style = typo.footnote.medium()

        Column(
            verticalArrangement = Arrangement.spacedBy(metrics.compact)
        ) {
            extra?.let { it1 ->
                listOf(
                    Triple(stringResource(Res.string.bitrate) , it1.bitRate.toString(),"kbps"),
                    Triple(stringResource(Res.string.sample_rate) , it1.sampleRate.toString(),"Hz"),
                    Triple(stringResource(Res.string.file_size) , it1.fileSize.toString(),"Byte"),
                    Triple(stringResource(Res.string.format) , it1.format.toString(),"")
                ).forEach {
                    Capsule(text = "${it.first}: ${it.second} ${it.third}",color=color,style=style)
                }
            }
        }
        Spacer(modifier = Modifier.height(metrics.cozy))
        style = typo.caption
        FlowRow(
            maxItemsInEachRow = 4,
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.compact),
            horizontalArrangement = Arrangement.spacedBy(metrics.compact)
        ){
            labels.forEach {
                it?.let {
                    Capsule(text = it.label.toString(),style=style)
                }
            }
        }
    }

}
