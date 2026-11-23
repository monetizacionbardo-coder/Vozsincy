package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.VozSyncStrings
import com.example.model.SceneItem
import com.example.model.SceneSyncStatus
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberContainer
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanContainer
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldContainer
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioErrorContainer
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceBright
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarning
import com.example.ui.theme.StudioWarningContainer
import java.util.Locale

@Composable
fun SceneTableSection(
    strings: VozSyncStrings,
    scenes: List<SceneItem>,
    durationSourceNote: String?,
    totalTimelineSec: Double,
    playingSceneId: String?,
    isGenerating: Boolean,
    onAddScene: () -> Unit,
    onUpdateNarration: (String, String) -> Unit,
    onUpdateDuration: (String, Double) -> Unit,
    onDeleteScene: (String) -> Unit,
    onToggleScenePlay: (SceneItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = strings.stepScenesTitle,
            subtitle = strings.stepScenesSubtitle,
            trailing = {
                OutlinedButton(
                    onClick = onAddScene,
                    enabled = !isGenerating,
                    modifier = Modifier.testTag("add_scene_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = StudioCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = strings.addSceneButton,
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioCyan
                    )
                }
            }
        )

        if (!durationSourceNote.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioCyanContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = StudioCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = durationSourceNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioTextPrimary
                    )
                }
            }
        }

        if (scenes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = StudioTextMuted,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = strings.emptyScenesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioTextPrimary
                    )
                    Text(
                        text = strings.emptyScenesBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioTextSecondary
                    )
                }
            }
        } else {
            // Timeline total summary bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${scenes.size} ${strings.colScene.lowercase()}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioTextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.totalDurationLabel}: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioTextSecondary
                        )
                        Text(
                            text = formatTimecode(totalTimelineSec),
                            style = StudioMonoStyle,
                            color = StudioCyan
                        )
                    }
                }
            }

            var cumulativeStart = 0.0
            scenes.forEachIndexed { index, scene ->
                val startSec = cumulativeStart
                val endSec = cumulativeStart + scene.durationSec
                cumulativeStart = endSec

                EditableSceneRowCard(
                    strings = strings,
                    scene = scene,
                    sceneNumber = index + 1,
                    startSec = startSec,
                    endSec = endSec,
                    isPlaying = playingSceneId == scene.id,
                    enabled = !isGenerating,
                    onUpdateNarration = { onUpdateNarration(scene.id, it) },
                    onUpdateDuration = { onUpdateDuration(scene.id, it) },
                    onDelete = { onDeleteScene(scene.id) },
                    onTogglePlay = { onToggleScenePlay(scene) }
                )
            }
        }
    }
}

@Composable
private fun EditableSceneRowCard(
    strings: VozSyncStrings,
    scene: SceneItem,
    sceneNumber: Int,
    startSec: Double,
    endSec: Double,
    isPlaying: Boolean,
    enabled: Boolean,
    onUpdateNarration: (String) -> Unit,
    onUpdateDuration: (Double) -> Unit,
    onDelete: () -> Unit,
    onTogglePlay: () -> Unit
) {
    var durationText by remember(scene.id) {
        mutableStateOf(String.format(Locale.US, "%.2f", scene.durationSec))
    }

    LaunchedEffect(scene.durationSec) {
        val parsedCurrent = durationText.replace(",", ".").toDoubleOrNull()
        if (parsedCurrent == null || kotlin.math.abs(parsedCurrent - scene.durationSec) > 0.005) {
            durationText = String.format(Locale.US, "%.2f", scene.durationSec)
        }
    }

    val borderColor = when (scene.status) {
        is SceneSyncStatus.Generating, is SceneSyncStatus.Retrying -> StudioCyan
        is SceneSyncStatus.Synced -> StudioEmerald.copy(alpha = 0.45f)
        is SceneSyncStatus.SpedUp -> if (scene.status.isTightFit) StudioWarning.copy(alpha = 0.6f) else StudioCyan.copy(alpha = 0.4f)
        is SceneSyncStatus.Error -> StudioError.copy(alpha = 0.65f)
        SceneSyncStatus.Pending -> StudioOutline
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_card_$sceneNumber"),
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar: Scene badge, timecode window, play & delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioCyanContainer
                    ) {
                        Text(
                            text = String.format(Locale.US, "#%02d", sceneNumber),
                            style = StudioMonoStyle,
                            color = StudioCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${formatTimecode(startSec)} → ${formatTimecode(endSec)}",
                        style = StudioMonoStyle,
                        color = StudioTextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scene.clipWavPath != null) {
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier.testTag("play_scene_btn_$sceneNumber")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) strings.stopSceneDesc else strings.playSceneDesc,
                                tint = if (isPlaying) StudioAmber else StudioCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        enabled = enabled,
                        modifier = Modifier.testTag("delete_scene_btn_$sceneNumber")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = strings.deleteSceneDesc,
                            tint = StudioTextMuted
                        )
                    }
                }
            }

            // Narration text field
            OutlinedTextField(
                value = scene.narration,
                onValueChange = onUpdateNarration,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scene_narration_input_$sceneNumber"),
                placeholder = {
                    Text(
                        text = strings.narrationPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextMuted
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = StudioTextPrimary),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioCyan,
                    unfocusedBorderColor = StudioOutline,
                    focusedContainerColor = StudioSurfaceElevated,
                    unfocusedContainerColor = StudioSurfaceElevated,
                    cursorColor = StudioCyan
                )
            )

            // Bottom bar: Duration controls + Live sync status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { onUpdateDuration((scene.durationSec - 0.5).coerceAtLeast(0.5)) },
                        enabled = enabled,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurfaceBright)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "-0.5s",
                            tint = StudioTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { raw ->
                            durationText = raw
                            val parsed = raw.replace(",", ".").toDoubleOrNull()
                            if (parsed != null && parsed > 0.0) {
                                onUpdateDuration(parsed)
                            }
                        },
                        enabled = enabled,
                        singleLine = true,
                        modifier = Modifier
                            .width(96.dp)
                            .testTag("scene_duration_input_$sceneNumber"),
                        textStyle = StudioMonoStyle.copy(color = StudioTextPrimary),
                        suffix = { Text(strings.secondsShort, style = StudioMonoStyle, color = StudioTextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StudioAmber,
                            unfocusedBorderColor = StudioOutline,
                            focusedContainerColor = StudioSurfaceElevated,
                            unfocusedContainerColor = StudioSurfaceElevated,
                            cursorColor = StudioAmber
                        )
                    )

                    IconButton(
                        onClick = { onUpdateDuration(scene.durationSec + 0.5) },
                        enabled = enabled,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurfaceBright)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "+0.5s",
                            tint = StudioTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                SceneStatusBadge(strings = strings, status = scene.status)
            }
        }
    }
}

@Composable
private fun SceneStatusBadge(
    strings: VozSyncStrings,
    status: SceneSyncStatus
) {
    val (bgColor, textColor, label) = when (status) {
        SceneSyncStatus.Pending -> Triple(StudioSurfaceBright, StudioTextMuted, strings.statusPending)
        SceneSyncStatus.Generating -> Triple(StudioCyanContainer, StudioCyan, strings.statusGenerating)
        is SceneSyncStatus.Retrying -> Triple(
            StudioAmberContainer,
            StudioAmber,
            strings.statusRetrying(status.attempt, status.maxRetries, status.previousAudioSec)
        )
        is SceneSyncStatus.Synced -> Triple(
            StudioEmeraldContainer,
            StudioEmerald,
            strings.statusSynced(status.rawAudioSec, status.paddedSilenceSec)
        )
        is SceneSyncStatus.SpedUp -> Triple(
            if (status.isTightFit) StudioWarningContainer else StudioCyanContainer,
            if (status.isTightFit) StudioWarning else StudioCyan,
            strings.statusSpedUp(status.speedFactor, status.isTightFit)
        )
        is SceneSyncStatus.Error -> Triple(StudioErrorContainer, StudioError, strings.statusError)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (status) {
                SceneSyncStatus.Generating, is SceneSyncStatus.Retrying -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = textColor
                    )
                }
                is SceneSyncStatus.Synced -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                }
                is SceneSyncStatus.SpedUp -> {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                }
                is SceneSyncStatus.Error -> {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                }
                SceneSyncStatus.Pending -> Unit
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}
