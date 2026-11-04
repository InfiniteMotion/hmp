package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.backup_desc
import com.hearablemusic.player.ui.generated.resources.backup_settings
import com.hearablemusic.player.ui.generated.resources.chevron_right
import com.hearablemusic.player.ui.generated.resources.externaldrive
import com.hearablemusic.player.ui.generated.resources.library_desc
import com.hearablemusic.player.ui.generated.resources.library_settings
import com.hearablemusic.player.ui.generated.resources.music
import com.hearablemusic.player.ui.generated.resources.person
import com.hearablemusic.player.ui.generated.resources.profile_desc
import com.hearablemusic.player.ui.generated.resources.profile_settings
import com.hearablemusic.player.ui.generated.resources.title_settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun SettingScreen(
    navController: NavBackStack<NavKey>
) {
    val metrics = LocalHmpMetrics.current
    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.title_settings)
    ) {
        val haptic = rememberHapticFeedback()
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.cozy)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cozy)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.cozy)) {
                        SettingItem(
                            title = stringResource(Res.string.profile_settings),
                            description = stringResource(Res.string.profile_desc),
                            icon = Res.drawable.person,
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Settings.ProfileSettings)
                            }
                        )
                        SettingItem(
                            title = stringResource(Res.string.backup_settings),
                            description = stringResource(Res.string.backup_desc),
                            icon = Res.drawable.externaldrive,
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Settings.BackupSettings)
                            }
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.cozy)) {
                        SettingItem(
                            title = stringResource(Res.string.library_settings),
                            description = stringResource(Res.string.library_desc),
                            icon = Res.drawable.music,
                            onClick = {
                                haptic.performClick()
                                navController.add(Routes.Settings.LibrarySettings)
                            }
                        )
                    }
                }
            } else {
                SettingItem(
                    title = stringResource(Res.string.profile_settings),
                    description = stringResource(Res.string.profile_desc),
                    icon = Res.drawable.person,
                    onClick = {
                        haptic.performClick()
                        navController.add(Routes.Settings.ProfileSettings)
                    }
                )
                SettingItem(
                    title = stringResource(Res.string.backup_settings),
                    description = stringResource(Res.string.backup_desc),
                    icon = Res.drawable.externaldrive,
                    onClick = {
                        haptic.performClick()
                        navController.add(Routes.Settings.BackupSettings)
                    }
                )
                SettingItem(
                    title = stringResource(Res.string.library_settings),
                    description = stringResource(Res.string.library_desc),
                    icon = Res.drawable.music,
                    onClick = {
                        haptic.performClick()
                        navController.add(Routes.Settings.LibrarySettings)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    description: String,
    icon: DrawableResource,
    onClick: () -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.base))
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(metrics.base),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(metrics.cozy)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(metrics.block),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(metrics.cozy))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typo.subtitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = typo.footnote.medium(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
