package com.hearablemusic.player.ui.playlist

import androidx.compose.runtime.Composable
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.playlist_name_empty
import com.hearablemusic.player.ui.generated.resources.playlist_name_exists
import com.hearablemusic.player.ui.generated.resources.playlist_name_too_long
import com.hmp.domain.playlist.PlaylistNameError
import org.jetbrains.compose.resources.stringResource

/** 名称错误码 → 本地化文案。规则在 domain，文案在 UI（D3-05④ / D3-12）。 */
@Composable
internal fun playlistNameErrorText(error: PlaylistNameError): String = when (error) {
    PlaylistNameError.EMPTY -> stringResource(Res.string.playlist_name_empty)
    PlaylistNameError.TOO_LONG -> stringResource(Res.string.playlist_name_too_long)
    PlaylistNameError.DUPLICATE -> stringResource(Res.string.playlist_name_exists)
}
