package com.hearablemusic.player.ui.library.pages.components.musiclist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.generated.resources.*
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 与 MusicListHeader 一致，保证编辑栏与头部高度一致（总高度 48.dp） */
private val EditToolbarHorizontalPadding = HMPMetricTokens.Compact.base
private val EditToolbarTopPadding = HMPMetricTokens.Compact.base
private val EditToolbarBottomPadding = HMPMetricTokens.Compact.micro
private val EditToolbarTotalHeight = HMPMetricTokens.Compact.bar
private val EditToolbarButtonSize = HMPMetricTokens.Compact.block
private val EditToolbarIconSize = HMPMetricTokens.Compact.section

/** 数字 label 固定宽度，与头部一致 */
private val EditToolbarCountLabelWidth = HMPMetricTokens.Compact.section

/**
 * 编辑栏：左侧显示选中数量，右侧四个按钮（全选、添加、删除、确认）。高度与头部一致。
 */
@Composable
internal fun MusicListEditToolbar(
    config: EditConfig,
    state: MusicListState,
    allIds: Set<Long>,
    callbacks: MusicListCallbacks,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typo = LocalHmpTypo.current
    val allSelected = state.selectedIds.size == allIds.size && allIds.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EditToolbarTotalHeight)
            .padding(horizontal = EditToolbarHorizontalPadding)
            .padding(top = EditToolbarTopPadding, bottom = EditToolbarBottomPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Box(modifier = Modifier.width(EditToolbarCountLabelWidth)) {
            Text(
                text = config.selectedCountFormat(state.selectedIds.size),
                style = typo.footnote,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        IconButton(
            onClick = {
                if (allSelected) {
                    state.clearSelection()
                } else {
                    state.selectAll(allIds)
                }
                callbacks.onSelectionChange(state.selectedIds)
            },
            modifier = Modifier.size(EditToolbarButtonSize),
        ) {
            Icon(
                painter = painterResource(
                    if (allSelected) {
                        Res.drawable.checkmark_square_on_square_fill
                    } else {
                        Res.drawable.checkmark_square_on_square
                    },
                ),
                contentDescription = if (allSelected) "Deselect all" else "Select all",
                modifier = Modifier.size(EditToolbarIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = { callbacks.onBatchAddToPlaylist(state.selectedIds) },
            modifier = Modifier.size(EditToolbarButtonSize),
        ) {
            Icon(
                painter = painterResource(Res.drawable.plus),
                contentDescription = stringResource(Res.string.add_to_playlist_desc),
                modifier = Modifier.size(EditToolbarIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = { callbacks.onBatchDelete(state.selectedIds) },
            modifier = Modifier.size(EditToolbarButtonSize),
        ) {
            Icon(
                painter = painterResource(Res.drawable.trash),
                contentDescription = stringResource(Res.string.delete_selected_desc),
                modifier = Modifier.size(EditToolbarIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = onExitEditMode,
            modifier = Modifier.size(EditToolbarButtonSize),
        ) {
            Icon(
                painter = painterResource(Res.drawable.checkmark_circle),
                contentDescription = stringResource(Res.string.confirm_desc),
                modifier = Modifier.size(EditToolbarIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
