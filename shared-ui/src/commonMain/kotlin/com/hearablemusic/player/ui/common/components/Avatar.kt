package com.hearablemusic.player.ui.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.generated.resources.*
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun Avatar(
    aSize: Int,
    imageUri: String?,
) {
    val avatarDesc = stringResource(Res.string.user_avatar_desc)
    if (!imageUri.isNullOrEmpty()) {
        AsyncImage(
            model = imageUri,
            contentDescription = avatarDesc,
            modifier = Modifier
                .size(aSize.dp)
                .clip(CircleShape),
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.none),
            contentDescription = avatarDesc,
            modifier = Modifier
                .size(aSize.dp)
                .clip(CircleShape),
        )
    }
}
