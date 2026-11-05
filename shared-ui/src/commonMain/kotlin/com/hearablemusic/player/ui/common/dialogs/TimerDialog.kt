package com.hearablemusic.player.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.custom_minutes
import com.hearablemusic.player.ui.generated.resources.ok
import com.hearablemusic.player.ui.generated.resources.sleep_timer
import com.hearablemusic.player.ui.generated.resources.timer_off
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null,
) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    val typo = LocalHmpTypo.current

    // State
    var selectedOption by remember { mutableStateOf("0") }
    var customMinutesInput by remember { mutableStateOf("") }

    // Segmented control options
    val options = listOf(
        SegmentedOption("0", stringResource(Res.string.timer_off)),
        SegmentedOption("15", "15 min"),
        SegmentedOption("30", "30 min"),
        SegmentedOption("60", "60 min"),
    )

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(onDismissRequest = onDismiss) {
            val dialogShape = RoundedCornerShape(metrics.section)
            Card(
                modifier = Modifier
                    .padding(metrics.section)
                    .clip(dialogShape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = hazeStyleForIntensity(),
                            )
                        } else {
                            Modifier
                        },
                    ),
                shape = dialogShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (hazeState != null) {
                        MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(metrics.section)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.sleep_timer),
                        style = typo.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = metrics.section),
                    )

                    // Segmented Control
                    SegmentedControl(
                        modifier = Modifier.fillMaxWidth(),
                        options = options,
                        selectedOption = selectedOption,
                        onOptionSelected = { optionId ->
                            haptic.performClick()
                            selectedOption = optionId
                            if (optionId != "custom") {
                                customMinutesInput = ""
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(metrics.section))

                    // Custom Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.cozy),
                    ) {
                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { input ->
                                // Allow digits only
                                if (input.all { it.isDigit() }) {
                                    customMinutesInput = input
                                    if (input.isNotEmpty()) {
                                        selectedOption = "custom"
                                    }
                                }
                            },
                            label = { Text(stringResource(Res.string.custom_minutes)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(metrics.base),
                        )
                    }

                    Spacer(modifier = Modifier.height(metrics.block))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                haptic.performClick()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(metrics.base),
                        ) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                style = typo.body.medium(),
                            )
                        }
                        Spacer(modifier = Modifier.width(metrics.compact))
                        TextButton(
                            onClick = {
                                haptic.performConfirm()
                                val minutes = when {
                                    selectedOption == "custom" -> customMinutesInput.toIntOrNull()
                                        ?: 0

                                    else -> selectedOption.toIntOrNull() ?: 0
                                }
                                onConfirm(minutes)
                            },
                            shape = RoundedCornerShape(metrics.base),
                        ) {
                            Text(
                                text = stringResource(Res.string.ok),
                                style = typo.body.medium(),
                            )
                        }
                    }
                }
            }
        }
    }
}
