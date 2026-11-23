package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.i18n.VozSyncStrings
import com.example.model.ExportResult
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberContainer
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanContainer
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldContainer
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceBright
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarning
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioOutputPlayerSection(
    strings: VozSyncStrings,
    exportResult: ExportResult,
    onDownloadMp4: () -> Unit,
    onDownloadWav: () -> Unit,
    onShareMp4: () -> Unit,
    onShareWav: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("output_player_section"),
        shape = RoundedCornerShape(20.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioEmerald.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = strings.stepOutputTitle,
                subtitle = strings.stepOutputSubtitle
            )

            // Format badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutputFormatPill(text = strings.pcmFormatBadge, bg = StudioCyanContainer, fg = StudioCyan)
                if (exportResult.mp4FilePath != null) {
                    OutputFormatPill(text = strings.videoCopyBadge, bg = StudioEmeraldContainer, fg = StudioEmerald)
                }
                if (exportResult.hasBackgroundMusic) {
                    OutputFormatPill(text = strings.musicLevelBadge, bg = StudioAmberContainer, fg = StudioAmber)
                }
            }

            if (exportResult.tightFitScenes.isNotEmpty()) {
                TightFitWarningCard(
                    strings = strings,
                    tightFitScenes = exportResult.tightFitScenes
                )
            }

            if (!exportResult.videoMuxNote.isNullOrBlank()) {
                Text(
                    text = exportResult.videoMuxNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioWarning
                )
            }

            // Embedded video player (when MP4 exists) OR audio-only studio monitor
            if (exportResult.mp4FilePath != null && File(exportResult.mp4FilePath).exists()) {
                EmbeddedMp4StudioMonitor(mp4Path = exportResult.mp4FilePath)
            } else {
                EmbeddedWavStudioMonitor(
                    wavPath = exportResult.wavFilePath,
                    note = strings.audioOnlyPlayerNote
                )
            }

            // Primary download actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (exportResult.mp4FilePath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDownloadMp4,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("download_mp4_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioCyan,
                                contentColor = StudioObsidian
                            )
                        ) {
                            Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.downloadMp4Button,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onShareMp4,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("share_mp4_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = strings.shareMp4Button,
                                tint = StudioCyan
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownloadWav,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("download_wav_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioAmberContainer,
                            contentColor = StudioAmber
                        ),
                        border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.downloadWavButton,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onShareWav,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("share_wav_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = strings.shareWavButton,
                            tint = StudioAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputFormatPill(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun EmbeddedMp4StudioMonitor(mp4Path: String) {
    var videoViewRef by remember(mp4Path) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(mp4Path) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(mp4Path) { mutableStateOf(false) }
    var isMuted by remember(mp4Path) { mutableStateOf(false) }
    var positionMs by remember(mp4Path) { mutableLongStateOf(0L) }
    var durationMs by remember(mp4Path) { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, videoViewRef) {
        while (isPlaying) {
            val vv = videoViewRef
            if (vv != null) {
                runCatching {
                    positionMs = vv.currentPosition.toLong().coerceAtLeast(0L)
                    if (vv.duration > 0) durationMs = vv.duration.toLong()
                    if (!vv.isPlaying && durationMs > 0 && positionMs >= durationMs - 200) {
                        isPlaying = false
                    }
                }
            }
            delay(120L)
        }
    }

    DisposableEffect(mp4Path) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
            mediaPlayerRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioObsidian)
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(File(mp4Path)))
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            durationMs = mp.duration.toLong().coerceAtLeast(1L)
                            mp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                            seekTo(1)
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            positionMs = durationMs
                        }
                        videoViewRef = this
                    }
                }
            )
        }

        TransportControlsBar(
            isPlaying = isPlaying,
            isMuted = isMuted,
            positionMs = positionMs,
            durationMs = durationMs,
            onTogglePlay = {
                val vv = videoViewRef ?: return@TransportControlsBar
                if (isPlaying) {
                    vv.pause()
                    isPlaying = false
                } else {
                    if (durationMs > 0 && positionMs >= durationMs - 150) {
                        vv.seekTo(0)
                    }
                    vv.start()
                    isPlaying = true
                }
            },
            onSeek = { targetMs ->
                videoViewRef?.seekTo(targetMs.toInt())
                positionMs = targetMs
            },
            onToggleMute = {
                isMuted = !isMuted
                val vol = if (isMuted) 0f else 1f
                runCatching { mediaPlayerRef?.setVolume(vol, vol) }
            }
        )
    }
}

@Composable
private fun EmbeddedWavStudioMonitor(wavPath: String, note: String) {
    var mediaPlayer by remember(wavPath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(wavPath) { mutableStateOf(false) }
    var isMuted by remember(wavPath) { mutableStateOf(false) }
    var positionMs by remember(wavPath) { mutableLongStateOf(0L) }
    var durationMs by remember(wavPath) { mutableLongStateOf(0L) }

    DisposableEffect(wavPath) {
        val mp = MediaPlayer().apply {
            runCatching {
                setDataSource(wavPath)
                prepare()
                durationMs = duration.toLong().coerceAtLeast(1L)
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = durationMs
                }
            }
        }
        mediaPlayer = mp
        onDispose {
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null) {
                runCatching {
                    positionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                }
            }
            delay(100L)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedAudioWaveformBar(isPlaying = isPlaying)
            Spacer(Modifier.height(8.dp))
            TransportControlsBar(
                isPlaying = isPlaying,
                isMuted = isMuted,
                positionMs = positionMs,
                durationMs = durationMs,
                onTogglePlay = {
                    val mp = mediaPlayer ?: return@TransportControlsBar
                    if (isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        if (durationMs > 0 && positionMs >= durationMs - 150) {
                            mp.seekTo(0)
                        }
                        mp.start()
                        isPlaying = true
                    }
                },
                onSeek = { targetMs ->
                    runCatching { mediaPlayer?.seekTo(targetMs.toInt()) }
                    positionMs = targetMs
                },
                onToggleMute = {
                    isMuted = !isMuted
                    val vol = if (isMuted) 0f else 1f
                    runCatching { mediaPlayer?.setVolume(vol, vol) }
                }
            )
        }
    }
}

@Composable
private fun AnimatedAudioWaveformBar(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave_anim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StudioObsidian)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val barCount = 44
        val gap = size.width / barCount
        val barWidth = (gap * 0.56f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normX = i.toFloat() / barCount
            val amp = if (isPlaying) {
                val w1 = abs(sin(phase + i * 0.45f))
                val w2 = abs(sin(phase * 1.4f - i * 0.25f))
                (0.22f + 0.72f * (w1 * 0.6f + w2 * 0.4f)).coerceIn(0.18f, 0.96f)
            } else {
                0.20f + 0.35f * abs(sin(i * 0.55f))
            }
            val barHeight = size.height * amp
            val color = if (normX < 0.5f) StudioCyan else StudioAmber
            drawRoundRect(
                color = color.copy(alpha = if (isPlaying) 0.92f else 0.45f),
                topLeft = Offset(i * gap + (gap - barWidth) / 2f, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun TransportControlsBar(
    isPlaying: Boolean,
    isMuted: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var sliderVal by remember(positionMs, durationMs) {
        mutableFloatStateOf((positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(StudioCyan)
                .testTag("player_play_pause_button")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = StudioObsidian
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = sliderVal,
                onValueChange = { frac ->
                    sliderVal = frac
                    onSeek((frac * safeDuration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = StudioCyan,
                    activeTrackColor = StudioCyan,
                    inactiveTrackColor = StudioSurfaceBright
                ),
                modifier = Modifier.height(28.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimecode(positionMs / 1000.0),
                    style = StudioMonoStyle,
                    color = StudioTextPrimary
                )
                Text(
                    text = formatTimecode(durationMs / 1000.0),
                    style = StudioMonoStyle,
                    color = StudioTextSecondary
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.testTag("player_mute_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) StudioWarning else StudioTextPrimary
            )
        }
    }
}
