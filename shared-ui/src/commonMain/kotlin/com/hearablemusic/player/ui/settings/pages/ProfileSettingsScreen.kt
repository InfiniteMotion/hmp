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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.components.Avatar
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
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
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hearablemusic.player.ui.settings.viewmodel.ProfileSettingsEffect
import com.hearablemusic.player.ui.settings.viewmodel.ProfileSettingsViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileSettingsScreen(
    navController: NavBackStack<NavKey>,
    viewModel: ProfileSettingsViewModel = koinViewModel(),
    dialogManagerViewModel: DialogManagerViewModel = activityViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    val dialogManager = dialogManagerViewModel.dialogManager
    // 唯一状态出口：当前值、待确认预览、输入框草稿都在这里
    val uiState by viewModel.uiState.collectAsState()

    // 一次性效果：VM 不持有 DialogManager，toast 由组合点消费
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProfileSettingsEffect.AvatarChanged -> {
                    dialogManager.showMessage(getString(Res.string.avatar_changed))
                }

                is ProfileSettingsEffect.AvatarChangeCancelled -> {
                    dialogManager.showMessage(getString(Res.string.avatar_change_cancelled))
                }
            }
        }
    }

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
                            avatarUri = uiState.avatarUri,
                            pendingAvatarUri = uiState.pendingAvatarUri,
                            onPickImage = viewModel::onPickAvatar,
                            onConfirm = viewModel::onConfirmAvatar,
                            onCancel = viewModel::onCancelAvatar,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        UpdateUserName(
                            userName = uiState.userName,
                            nameInput = uiState.nameInput,
                            onNameChange = viewModel::onNameChange,
                            onConfirm = viewModel::onConfirmName,
                        )
                    }
                }
            } else {
                UpdateAvatar(
                    avatarUri = uiState.avatarUri,
                    pendingAvatarUri = uiState.pendingAvatarUri,
                    onPickImage = viewModel::onPickAvatar,
                    onConfirm = viewModel::onConfirmAvatar,
                    onCancel = viewModel::onCancelAvatar,
                )
                UpdateUserName(
                    userName = uiState.userName,
                    nameInput = uiState.nameInput,
                    onNameChange = viewModel::onNameChange,
                    onConfirm = viewModel::onConfirmName,
                )
            }
            MiniPlayerSafeSpacer(height = metrics.bar)
        }
    }
}

@Composable
private fun UpdateAvatar(
    avatarUri: String,
    pendingAvatarUri: String,
    onPickImage: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.height(metrics.cozy))
                // 有待确认预览时优先显示预览，否则显示当前头像
                val displayUri = if (pendingAvatarUri.isNotEmpty()) pendingAvatarUri else avatarUri
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
                if (pendingAvatarUri.isEmpty()) {
                    Button(
                        modifier = Modifier.width(LayoutConstraints.PanelHeight),
                        onClick = onPickImage,
                    ) {
                        Text(text = stringResource(Res.string.change_avatar), color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    AsyncImage(
                        model = pendingAvatarUri,
                        contentDescription = stringResource(Res.string.user_avatar_desc),
                        modifier = Modifier
                            .size(metrics.bar)
                            .clip(RoundedCornerShape(metrics.base)),
                    )
                    Spacer(modifier = Modifier.width(metrics.block))
                    Button(
                        onClick = onConfirm,
                    ) {
                        Text(text = stringResource(Res.string.change), color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Button(
                        onClick = onCancel,
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
    userName: String,
    nameInput: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
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
            Text(
                text = userName,
                style = typo.displayL,
                modifier = Modifier.padding(metrics.cozy),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = nameInput,
                    onValueChange = onNameChange,
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
                onClick = onConfirm,
            ) {
                Text(text = stringResource(Res.string.change_user_name), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
