package com.hearablemusic.player.ui.settings.pages

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hearablemusic.player.ui.common.components.base.TitleWidget
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.align_center
import com.hearablemusic.player.ui.generated.resources.align_left
import com.hearablemusic.player.ui.generated.resources.align_right
import com.hearablemusic.player.ui.generated.resources.alignment
import com.hearablemusic.player.ui.generated.resources.bilingual
import com.hearablemusic.player.ui.generated.resources.current_line_size
import com.hearablemusic.player.ui.generated.resources.display_mode
import com.hearablemusic.player.ui.generated.resources.enable_floating_lyrics
import com.hearablemusic.player.ui.generated.resources.floating_lyrics
import com.hearablemusic.player.ui.generated.resources.follow_component
import com.hearablemusic.player.ui.generated.resources.fullscreen
import com.hearablemusic.player.ui.generated.resources.independent_config
import com.hearablemusic.player.ui.generated.resources.karaoke_lyrics
import com.hearablemusic.player.ui.generated.resources.line_spacing
import com.hearablemusic.player.ui.generated.resources.lyrics_component
import com.hearablemusic.player.ui.generated.resources.lyrics_settings
import com.hearablemusic.player.ui.generated.resources.original_text
import com.hearablemusic.player.ui.generated.resources.original_text_size
import com.hearablemusic.player.ui.generated.resources.player_page
import com.hearablemusic.player.ui.generated.resources.reset_component_to_default
import com.hearablemusic.player.ui.generated.resources.shared_settings
import com.hearablemusic.player.ui.generated.resources.text_settings
import com.hearablemusic.player.ui.generated.resources.translated_text
import com.hearablemusic.player.ui.generated.resources.translated_text_size
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hearablemusic.player.ui.settings.viewmodel.LyricsSettingsPageViewModel
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.lyrics.LyricsComponent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 设置页可见的歌词组件。
 *
 * Desktop/iOS 的悬浮歌词是空壳（`FloatingLyricsController.isSupported=false`），
 * 直接隐藏这个 tab，避免"看起来能开、实际无效"（D4-04）。
 */
internal fun supportedLyricsComponents(floatingSupported: Boolean): List<LyricsComponent> = LyricsComponent.entries.filter { it != LyricsComponent.FLOATING || floatingSupported }

@Composable
fun LyricsSettingsPage(
    onBack: () -> Unit,
    viewModel: LyricsSettingsPageViewModel = koinViewModel(),
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current

    // 唯一状态出口：选中的组件、三份配置、悬浮开关与平台能力位
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.currentConfig

    val tabLabels = mapOf(
        LyricsComponent.PLAYER to stringResource(Res.string.player_page),
        LyricsComponent.FULLSCREEN to stringResource(Res.string.fullscreen),
        LyricsComponent.FLOATING to stringResource(Res.string.floating_lyrics),
    )
    val components = supportedLyricsComponents(uiState.floatingSupported)

    SubScreen(
        onBackClick = onBack,
        title = stringResource(Res.string.lyrics_settings),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(metrics.section),
            verticalArrangement = Arrangement.spacedBy(metrics.section),
        ) {
            // 组件切换
            TitleWidget(title = stringResource(Res.string.lyrics_component)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    components.forEachIndexed { idx, comp ->
                        SegmentedButton(
                            selected = uiState.selectedTab == comp,
                            onClick = { viewModel.onSelectTab(comp) },
                            shape = SegmentedButtonDefaults.itemShape(idx, components.size),
                        ) { Text(tabLabels[comp] ?: comp.key) }
                    }
                }
            }

            // 悬浮歌词开关 — 仅 floating tab 时显示
            if (uiState.selectedTab == LyricsComponent.FLOATING) {
                TitleWidget(title = stringResource(Res.string.floating_lyrics)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(metrics.base),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.cozy, vertical = metrics.base),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(Res.string.enable_floating_lyrics),
                                style = typo.body,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = uiState.floatingEnabled,
                                onCheckedChange = { viewModel.onFloatingLyricsToggle(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }
                }
            }

            // 显示模式
            TitleWidget(title = stringResource(Res.string.display_mode)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("DUAL" to stringResource(Res.string.bilingual), "LANG1" to stringResource(Res.string.original_text), "LANG2" to stringResource(Res.string.translated_text)).forEachIndexed { idx, (key, label) ->
                        SegmentedButton(
                            selected = config.displayMode.name == key,
                            onClick = { viewModel.onDisplayModeChange(DisplayMode.valueOf(key)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, 3),
                        ) { Text(label, style = typo.footnote) }
                    }
                }
            }

            // 对齐方式
            TitleWidget(title = stringResource(Res.string.alignment)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("LEFT" to stringResource(Res.string.align_left), "CENTER" to stringResource(Res.string.align_center), "RIGHT" to stringResource(Res.string.align_right)).forEachIndexed { idx, (key, label) ->
                        SegmentedButton(
                            selected = config.alignment.name == key,
                            onClick = { viewModel.onAlignmentChange(LyricsAlignment.valueOf(key)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, 3),
                        ) { Text(label, style = typo.footnote) }
                    }
                }
            }

            // 文本设置
            TitleWidget(title = stringResource(Res.string.text_settings)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.cozy),
                    verticalArrangement = Arrangement.spacedBy(metrics.compact),
                ) {
                    NumericRow(stringResource(Res.string.original_text_size), config.originalTextSize, 10..28) {
                        viewModel.onOriginalTextSizeChange(it)
                    }
                    NumericRow(stringResource(Res.string.translated_text_size), config.translatedTextSize, 10..28) {
                        viewModel.onTranslatedTextSizeChange(it)
                    }
                    NumericRow(stringResource(Res.string.current_line_size), config.currentTimeTextSize, 10..28) {
                        viewModel.onCurrentTimeTextSizeChange(it)
                    }
                    NumericRow(stringResource(Res.string.line_spacing), config.lineSpacing, 0..20) {
                        viewModel.onLineSpacingChange(it)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(metrics.base),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.cozy, vertical = metrics.base),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(Res.string.karaoke_lyrics),
                                style = typo.body,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = config.karaokeEnabled,
                                onCheckedChange = { viewModel.onKaraokeChange(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }
                }
            }

            // 共享设置
            TitleWidget(title = stringResource(Res.string.shared_settings)) {
                val linkOptions = listOf(null to stringResource(Res.string.independent_config)) +
                    LyricsComponent.entries.filter { it != uiState.selectedTab }.map { it.key to stringResource(Res.string.follow_component, tabLabels[it]!!) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.compact),
                    verticalArrangement = Arrangement.spacedBy(metrics.hair),
                ) {
                    linkOptions.forEach { (key, label) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onLinkedToChange(key) },
                            shape = RoundedCornerShape(metrics.base),
                            color = if (config.linkedTo == key) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                Color.Transparent
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = metrics.compact, horizontal = metrics.micro),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = config.linkedTo == key,
                                    onClick = { viewModel.onLinkedToChange(key) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Spacer(Modifier.width(metrics.compact))
                                Text(
                                    label,
                                    style = typo.body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // 重置
            TextButton(
                onClick = { viewModel.onResetCurrentComponent() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    stringResource(Res.string.reset_component_to_default, tabLabels[uiState.selectedTab] ?: ""),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            MiniPlayerSafeSpacer(height = metrics.bar)
        }
    }
}

@Composable
private fun NumericRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(metrics.base),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(metrics.stroke.thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = metrics.micro, horizontal = metrics.cozy),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = typo.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (value > range.first) onChange(value - 1) },
                    modifier = Modifier.size(metrics.block),
                ) {
                    Text("−", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface) // design-token-exception: 加减号字形尺寸，属图形符号非文字排版
                }
                Text(
                    "$value",
                    style = typo.subtitle,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = metrics.compact),
                )
                TextButton(
                    onClick = { if (value < range.last) onChange(value + 1) },
                    modifier = Modifier.size(metrics.block),
                ) {
                    Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface) // design-token-exception: 加减号字形尺寸，属图形符号非文字排版
                }
            }
        }
    }
}
