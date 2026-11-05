package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.components.Avatar
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.controller.DialogManager
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogManagerViewModel
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.avatar
import com.hearablemusic.player.ui.generated.resources.avatar_change_cancelled
import com.hearablemusic.player.ui.generated.resources.avatar_changed
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.change
import com.hearablemusic.player.ui.generated.resources.change_avatar
import com.hearablemusic.player.ui.generated.resources.change_user_name
import com.hearablemusic.player.ui.generated.resources.enter_new_user_name
import com.hearablemusic.player.ui.generated.resources.profile_settings
import com.hearablemusic.player.ui.generated.resources.user_avatar_desc
import com.hearablemusic.player.ui.generated.resources.user_name
import com.hearablemusic.player.ui.platform.PlatformServices
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hearablemusic.player.ui.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun ProfileSettingsScreen(
    navController: NavBackStack<NavKey>,
    settingsViewModel: SettingsViewModel = activityViewModel(),
    dialogManagerViewModel: DialogManagerViewModel = activityViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    val dialogManager = dialogManagerViewModel.dialogManager
    val avatarUri by settingsViewModel.avatarUri.collectAsState("")
    val userName by settingsViewModel.userName.collectAsState("")

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.profile_settings),
    ) {
        val isLandscape = LocalWindowSizeInfo.current.isLandscape
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.section),
                ) {
                    Box(Modifier.weight(1f)) {
                        UpdateAvatar(
                            avatarUri = avatarUri,
                            updateAvatar = settingsViewModel::saveAvatarUri,
                            dialogManager = dialogManager,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        UpdateUserName(
                            userName = userName,
                            updateUserName = settingsViewModel::saveUserName,
                        )
                    }
                }
            } else {
                UpdateAvatar(
                    avatarUri = avatarUri,
                    updateAvatar = settingsViewModel::saveAvatarUri,
                    dialogManager = dialogManager,
                )
                UpdateUserName(
                    userName = userName,
                    updateUserName = settingsViewModel::saveUserName,
                )
            }
            MiniPlayerSafeSpacer(height = metrics.bar)
        }
    }
}

@Composable
private fun UpdateAvatar(
    avatarUri: String,
    updateAvatar: (String) -> Unit,
    dialogManager: DialogManager,
) {
    val metrics = LocalHmpMetrics.current
    val scope = rememberCoroutineScope()
    val platformServices: PlatformServices = koinInject()
    TitleWidget(
        title = stringResource(Res.string.avatar),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val uriImg = remember { mutableStateOf("") }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.height(metrics.cozy))
                // 如果 uriImg 有值（用户刚选了新图），优先显示预览
                // 否则显示当前的 avatarUri
                val displayUri = if (uriImg.value.isNotEmpty()) uriImg.value else avatarUri
                Avatar(metrics.card.value.toInt(), displayUri)
                Spacer(modifier = Modifier.height(metrics.cozy))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(metrics.cozy),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (uriImg.value == "") {
                    Button(
                        modifier = Modifier.width(LayoutConstraints.PanelHeight),
                        onClick = {
                            // 打开图片选择器（复制到 filesDir/user_avatar.jpg 由平台实现完成）
                            platformServices.filePicker.pickImage { path ->
                                if (path != null) uriImg.value = path
                            }
                        },
                    ) {
                        Text(text = stringResource(Res.string.change_avatar), color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    AsyncImage(
                        model = uriImg.value,
                        contentDescription = stringResource(Res.string.user_avatar_desc),
                        modifier = Modifier
                            .size(metrics.bar)
                            .clip(RoundedCornerShape(metrics.base)),
                    )
                    Spacer(modifier = Modifier.width(metrics.block))
                    Button(
                        onClick = {
                            updateAvatar(uriImg.value)
                            scope.launch { dialogManager.showMessage(getString(Res.string.avatar_changed)) }
                        },
                    ) {
                        Text(text = stringResource(Res.string.change), color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Button(
                        onClick = {
                            uriImg.value = ""
                            scope.launch { dialogManager.showMessage(getString(Res.string.avatar_change_cancelled)) }
                        },
                    ) {
                        Text(text = stringResource(Res.string.cancel), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateUserName(
    userName: String?,
    updateUserName: (String) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    TitleWidget(
        title = stringResource(Res.string.user_name),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cozy),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var name by rememberSaveable { mutableStateOf("") }
            Text(
                text = userName ?: stringResource(Res.string.user_name),
                style = typo.displayL,
                modifier = Modifier.padding(metrics.cozy),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = { Text(stringResource(Res.string.enter_new_user_name)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Transparent, // 聚焦时下划线颜色
                        unfocusedIndicatorColor = Transparent, // 未聚焦时下划线颜色
                        disabledIndicatorColor = Transparent, // 禁用时下划线颜色
                    ),
                    shape = RoundedCornerShape(metrics.section),
                    modifier = Modifier.width(LayoutConstraints.PanelHeight)
                        .padding(vertical = metrics.cozy),
                )
            }

            Spacer(modifier = Modifier.height(metrics.cozy))

            Button(
                modifier = Modifier.width(metrics.card),
                onClick = {
                    updateUserName(name)
                },
            ) {
                Text(text = stringResource(Res.string.change_user_name), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
