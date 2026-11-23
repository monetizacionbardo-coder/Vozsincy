package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.VozSyncStrings
import com.example.model.AppLanguage
import com.example.model.AudioFileInfo
import com.example.model.GeminiVoice
import com.example.model.GeminiVoicesCatalog
import com.example.model.GenerationProgress
import com.example.model.NarrationLanguage
import com.example.model.QuotaErrorState
import com.example.model.VideoInfo
import com.example.model.VoiceStyle
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberContainer
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanContainer
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldContainer
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioErrorContainer
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioObsidian
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

fun formatTimecode(seconds: Double): String {
    val safe = seconds.coerceAtLeast(0.0)
    val mins = (safe / 60.0).toInt()
    val secs = safe - mins * 60.0
    return String.format(Locale.US, "%02d:%05.2f", mins, secs)
}

fun formatShortDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val mins = safe / 60
    val rem = safe % 60
    return if (mins > 0) "${mins}m ${rem}s" else "${rem}s"
}

@Composable
fun StudioHeaderBanner(
    strings: VozSyncStrings,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_wave_banner_1795421974287),
                contentDescription = strings.appTagline,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                StudioObsidian.copy(alpha = 0.93f),
                                StudioObsidian.copy(alpha = 0.78f),
                                StudioObsidian.copy(alpha = 0.62f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(StudioCyanContainer)
                                .border(1.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = StudioCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = strings.firebaseReadyBadge,
                                style = StudioMonoStyle,
                                color = StudioCyan
                            )
                        }
                    }

                    LanguageTogglePill(
                        currentLanguage = currentLanguage,
                        onLanguageChange = onLanguageChange
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.appTagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary
                )
            }
        }
    }
}

@Composable
fun LanguageTogglePill(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = StudioSurfaceElevated.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, StudioOutline)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(AppLanguage.ES to "ES", AppLanguage.EN to "EN").forEach { (lang, label) ->
                val selected = currentLanguage == lang
                Box(
                    modifier = Modifier
                        .testTag("lang_toggle_${lang.code}")
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) StudioCyan else Color.Transparent)
                        .clickable { onLanguageChange(lang) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) StudioObsidian else StudioTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = StudioTextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun FileUploadCard(
    title: String,
    hint: String,
    icon: ImageVector,
    accentColor: Color,
    loadedFileName: String?,
    loadedMeta: String?,
    actionLabel: String,
    testTag: String,
    onSelectClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null
) {
    val isLoaded = !loadedFileName.isNullOrBlank()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelectClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isLoaded) StudioSurfaceElevated else StudioSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLoaded) accentColor.copy(alpha = 0.55f) else StudioOutline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = if (isLoaded) 0.20f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLoaded) Icons.Filled.CheckCircle else icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoaded) {
                    Text(
                        text = loadedFileName!!,
                        style = StudioMonoStyle,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!loadedMeta.isNullOrBlank()) {
                        Text(
                            text = loadedMeta,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary
                        )
                    }
                } else {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (isLoaded && onRemoveClick != null) {
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = StudioTextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isLoaded) StudioSurfaceBright else accentColor.copy(alpha = 0.16f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = null,
                        tint = if (isLoaded) StudioTextPrimary else accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLoaded) StudioTextPrimary else accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectInputsDeck(
    strings: VozSyncStrings,
    videoInfo: VideoInfo?,
    jsonFileName: String?,
    txtFileName: String?,
    musicFileInfo: AudioFileInfo?,
    includeBackgroundMusic: Boolean,
    isDetectingCuts: Boolean,
    cutDetectionProgress: Float,
    onPickVideo: () -> Unit,
    onPickJson: () -> Unit,
    onPickTxt: () -> Unit,
    onPickMusic: () -> Unit,
    onRemoveMusic: () -> Unit,
    onToggleMusic: (Boolean) -> Unit,
    onDetectCuts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = strings.stepInputsTitle,
            subtitle = strings.stepInputsSubtitle
        )

        val videoMeta = videoInfo?.let {
            val res = if (it.width > 0 && it.height > 0) "${it.width}×${it.height} · " else ""
            "${res}${formatTimecode(it.durationSec)}"
        }

        FileUploadCard(
            title = strings.uploadVideoTitle,
            hint = strings.uploadVideoHint,
            icon = Icons.Filled.Movie,
            accentColor = StudioCyan,
            loadedFileName = videoInfo?.displayName,
            loadedMeta = videoMeta,
            actionLabel = if (videoInfo != null) strings.changeFile else strings.selectFile,
            testTag = "upload_video_card",
            onSelectClick = onPickVideo
        )

        FileUploadCard(
            title = strings.uploadJsonTitle,
            hint = strings.uploadJsonHint,
            icon = Icons.Filled.Code,
            accentColor = StudioAmber,
            loadedFileName = jsonFileName,
            loadedMeta = null,
            actionLabel = if (jsonFileName != null) strings.changeFile else strings.selectFile,
            testTag = "upload_json_card",
            onSelectClick = onPickJson
        )

        FileUploadCard(
            title = strings.uploadTxtTitle,
            hint = strings.uploadTxtHint,
            icon = Icons.Filled.Description,
            accentColor = StudioEmerald,
            loadedFileName = txtFileName,
            loadedMeta = null,
            actionLabel = if (txtFileName != null) strings.changeFile else strings.selectFile,
            testTag = "upload_txt_card",
            onSelectClick = onPickTxt
        )

        FileUploadCard(
            title = strings.uploadMusicTitle,
            hint = strings.uploadMusicHint,
            icon = Icons.Filled.MusicNote,
            accentColor = Color(0xFFC084FC),
            loadedFileName = musicFileInfo?.displayName,
            loadedMeta = musicFileInfo?.let { "${strings.musicLevelBadge} · ${formatTimecode(it.durationSec)}" },
            actionLabel = if (musicFileInfo != null) strings.changeFile else strings.selectFile,
            testTag = "upload_music_card",
            onSelectClick = onPickMusic,
            onRemoveClick = if (musicFileInfo != null) onRemoveMusic else null
        )

        AnimatedVisibility(visible = musicFileInfo != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.musicEnabledLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextPrimary
                        )
                    }
                    Switch(
                        checked = includeBackgroundMusic,
                        onCheckedChange = onToggleMusic,
                        modifier = Modifier.testTag("music_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioObsidian,
                            checkedTrackColor = StudioCyan
                        )
                    )
                }
            }
        }

        AnimatedVisibility(visible = videoInfo != null) {
            if (isDetectingCuts) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioCyanContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.detectingCutsLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioCyan
                            )
                            Text(
                                text = "${(cutDetectionProgress * 100).toInt()}%",
                                style = StudioMonoStyle,
                                color = StudioCyan
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { cutDetectionProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = StudioCyan,
                            trackColor = StudioSurfaceBright,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onDetectCuts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detect_cuts_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = StudioCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.detectCutsButton,
                        color = StudioCyan,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceConfigSection(
    strings: VozSyncStrings,
    appLanguage: AppLanguage,
    selectedVoice: GeminiVoice,
    narrationLanguage: NarrationLanguage,
    voiceStyle: VoiceStyle,
    isPreviewingVoice: Boolean,
    previewError: String?,
    onSelectVoice: (GeminiVoice) -> Unit,
    onSelectNarrationLanguage: (NarrationLanguage) -> Unit,
    onSelectStyle: (VoiceStyle) -> Unit,
    onPreviewVoice: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = strings.stepVoiceTitle,
                subtitle = strings.stepVoiceSubtitle
            )

            // Voice selector dropdown + Preview button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var voiceMenuExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_selector_button")
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { voiceMenuExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                tint = StudioCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedVoice.displayName} · ${selectedVoice.register(appLanguage)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioTextPrimary
                                )
                                Text(
                                    text = selectedVoice.profile(appLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false },
                        modifier = Modifier.background(StudioSurfaceElevated)
                    ) {
                        GeminiVoicesCatalog.all.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${voice.displayName}  (${voice.register(appLanguage)})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (voice.id == selectedVoice.id) StudioCyan else StudioTextPrimary
                                        )
                                        Text(
                                            text = voice.profile(appLanguage),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StudioTextSecondary
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectVoice(voice)
                                    voiceMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = onPreviewVoice,
                    enabled = !isPreviewingVoice,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("preview_voice_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioAmberContainer,
                        contentColor = StudioAmber
                    ),
                    border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    if (isPreviewingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = StudioAmber
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.previewingVoiceLabel, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = strings.previewVoiceButton,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(strings.previewVoiceButton, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = strings.previewCallHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioAmber.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            if (!previewError.isNullOrBlank()) {
                Text(
                    text = previewError,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioError
                )
            }

            // Narration Language Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.narrationLangLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NarrationLanguage.entries.forEach { lang ->
                        val selected = lang == narrationLanguage
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectNarrationLanguage(lang) },
                            label = { Text(lang.label(appLanguage), style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioCyanContainer,
                                selectedLabelColor = StudioCyan,
                                containerColor = StudioSurfaceElevated,
                                labelColor = StudioTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = StudioOutline,
                                selectedBorderColor = StudioCyan
                            )
                        )
                    }
                }
            }

            // Style Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.styleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoiceStyle.entries.forEach { style ->
                        val selected = style == voiceStyle
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectStyle(style) },
                            label = { Text(style.label(appLanguage), style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioAmberContainer,
                                selectedLabelColor = StudioAmber,
                                containerColor = StudioSurfaceElevated,
                                labelColor = StudioTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = StudioOutline,
                                selectedBorderColor = StudioAmber
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenerationProgressMonitor(
    strings: VozSyncStrings,
    progress: GenerationProgress,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        label = "sync_progress"
    )
    val percent = (animatedProgress * 100).toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("generation_progress_card"),
        shape = RoundedCornerShape(18.dp),
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp,
                        color = StudioCyan
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.progressTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioTextPrimary
                    )
                }
                Text(
                    text = "$percent%",
                    style = StudioMonoStyle.copy(fontWeight = FontWeight.Bold),
                    color = StudioCyan
                )
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = StudioCyan,
                trackColor = StudioSurfaceBright,
                strokeCap = StrokeCap.Round
            )

            Text(
                text = progress.stageText,
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = strings.elapsedLabel(formatShortDuration(progress.elapsedSec)),
                        style = StudioMonoStyle,
                        color = StudioTextSecondary
                    )
                    progress.etaSec?.let { eta ->
                        Text(
                            text = strings.etaLabel(formatShortDuration(eta)),
                            style = StudioMonoStyle,
                            color = StudioAmber
                        )
                    }
                }

                Text(
                    text = strings.apiCallsCounter(progress.apiCallsUsed),
                    style = StudioMonoStyle,
                    color = StudioTextMuted
                )
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End).testTag("cancel_generation_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioError.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null,
                    tint = StudioError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(strings.cancelGenerationButton, color = StudioError)
            }
        }
    }
}

@Composable
fun QuotaErrorBanner(
    strings: VozSyncStrings,
    quotaError: QuotaErrorState,
    onRetryFromFailedScene: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quota_error_banner"),
        shape = RoundedCornerShape(16.dp),
        color = StudioErrorContainer,
        border = BorderStroke(1.dp, StudioError.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = StudioError
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.quotaErrorTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioError
                )
            }
            Text(
                text = quotaError.message,
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextPrimary
            )
            Button(
                onClick = onRetryFromFailedScene,
                modifier = Modifier.testTag("quota_retry_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioError,
                    contentColor = StudioObsidian
                )
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.quotaRetryButton(quotaError.failedSceneIndex + 1))
            }
        }
    }
}

@Composable
fun TightFitWarningCard(
    strings: VozSyncStrings,
    tightFitScenes: List<Int>
) {
    if (tightFitScenes.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tight_fit_warning_card"),
        shape = RoundedCornerShape(16.dp),
        color = StudioWarningContainer,
        border = BorderStroke(1.dp, StudioWarning.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = StudioWarning,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.tightFitWarningTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioWarning
                )
                Text(
                    text = strings.tightFitWarningBody(tightFitScenes.joinToString(", ") { "#$it" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextPrimary
                )
            }
        }
    }
}

@Composable
fun ApiCallsConfirmDialog(
    strings: VozSyncStrings,
    initialCalls: Int,
    voiceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxCalls = initialCalls * 3
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        titleContentColor = StudioTextPrimary,
        textContentColor = StudioTextSecondary,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StudioCyanContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = StudioCyan
                )
            }
        },
        title = {
            Text(
                text = strings.confirmDialogTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.confirmDialogBody(initialCalls, maxCalls, voiceName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioOutline)
                ) {
                    Text(
                        text = strings.confirmDialogNote,
                        style = StudioMonoStyle,
                        color = StudioCyan,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_api_calls_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioCyan,
                    contentColor = StudioObsidian
                )
            ) {
                Text(strings.confirmStartButton)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_api_calls_button")
            ) {
                Text(strings.cancelButton, color = StudioTextSecondary)
            }
        }
    )
}
