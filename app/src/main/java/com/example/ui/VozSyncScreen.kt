package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i18n.AppStringsProvider
import com.example.ui.components.ApiCallsConfirmDialog
import com.example.ui.components.GenerationProgressMonitor
import com.example.ui.components.ProjectInputsDeck
import com.example.ui.components.QuotaErrorBanner
import com.example.ui.components.SceneTableSection
import com.example.ui.components.StudioHeaderBanner
import com.example.ui.components.StudioOutputPlayerSection
import com.example.ui.components.VoiceConfigSection
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldContainer
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioErrorContainer
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextPrimary
import com.example.viewmodel.VozSyncViewModel
import java.io.File

@Composable
fun VozSyncScreen(
    viewModel: VozSyncViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = AppStringsProvider.get(state.appLanguage)
    val context = LocalContext.current

    // Zero-permission SAF file pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onVideoSelected(uri)
        }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onJsonSelected(uri)
    }

    val txtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onTxtSelected(uri)
    }

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onBackgroundMusicSelected(uri)
        }
    }

    // Zero-permission SAF download savers
    val saveMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val path = state.exportResult?.mp4FilePath
        if (uri != null && path != null) {
            viewModel.exportArtifactToUri(path, uri, "VozSync_Final.mp4")
        }
    }

    val saveWavLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val path = state.exportResult?.wavFilePath
        if (uri != null && path != null) {
            viewModel.exportArtifactToUri(path, uri, "VozSync_Voice.wav")
        }
    }

    fun shareFile(filePath: String, mimeType: String, title: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }

    if (state.showApiConfirmDialog) {
        ApiCallsConfirmDialog(
            strings = strings,
            initialCalls = state.remainingScenesCount.coerceAtLeast(1),
            voiceName = state.selectedVoice.displayName,
            onConfirm = { viewModel.confirmAndStartGeneration() },
            onDismiss = { viewModel.dismissApiConfirmDialog() }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StudioObsidian,
                            StudioSurface.copy(alpha = 0.7f),
                            StudioObsidian
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWide = maxWidth >= 860.dp

                val leftColumnContent: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        ProjectInputsDeck(
                            strings = strings,
                            videoInfo = state.videoInfo,
                            jsonFileName = state.jsonFileName,
                            txtFileName = state.txtFileName,
                            musicFileInfo = state.musicFileInfo,
                            includeBackgroundMusic = state.includeBackgroundMusic,
                            isDetectingCuts = state.isDetectingCuts,
                            cutDetectionProgress = state.cutDetectionProgress,
                            onPickVideo = {
                                videoPickerLauncher.launch(arrayOf("video/mp4", "video/webm", "video/*"))
                            },
                            onPickJson = {
                                jsonPickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            onPickTxt = {
                                txtPickerLauncher.launch(arrayOf("text/plain", "*/*"))
                            },
                            onPickMusic = {
                                musicPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            onRemoveMusic = { viewModel.removeBackgroundMusic() },
                            onToggleMusic = { viewModel.toggleBackgroundMusic(it) },
                            onDetectCuts = { viewModel.runManualCutDetection() }
                        )

                        VoiceConfigSection(
                            strings = strings,
                            appLanguage = state.appLanguage,
                            selectedVoice = state.selectedVoice,
                            narrationLanguage = state.narrationLanguage,
                            voiceStyle = state.voiceStyle,
                            isPreviewingVoice = state.isPreviewingVoice,
                            previewError = state.previewError,
                            onSelectVoice = { viewModel.selectVoice(it) },
                            onSelectNarrationLanguage = { viewModel.selectNarrationLanguage(it) },
                            onSelectStyle = { viewModel.selectVoiceStyle(it) },
                            onPreviewVoice = { viewModel.previewSelectedVoice() }
                        )

                        // Primary Generate / Sync CTA
                        if (!state.progress.isRunning) {
                            Button(
                                onClick = { viewModel.requestStartGeneration(0) },
                                enabled = state.validScenesCount > 0 && !state.isDetectingCuts,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("generate_sync_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioCyan,
                                    contentColor = StudioObsidian,
                                    disabledContainerColor = StudioCyan.copy(alpha = 0.25f),
                                    disabledContentColor = StudioTextPrimary.copy(alpha = 0.45f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = strings.generateButton,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                val rightColumnContent: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        AnimatedVisibility(visible = state.progress.isRunning) {
                            GenerationProgressMonitor(
                                strings = strings,
                                progress = state.progress,
                                onCancel = { viewModel.cancelGeneration() }
                            )
                        }

                        state.quotaError?.let { qErr ->
                            QuotaErrorBanner(
                                strings = strings,
                                quotaError = qErr,
                                onRetryFromFailedScene = {
                                    viewModel.requestStartGeneration(qErr.failedSceneIndex)
                                }
                            )
                        }

                        state.exportResult?.let { result ->
                            StudioOutputPlayerSection(
                                strings = strings,
                                exportResult = result,
                                onDownloadMp4 = { saveMp4Launcher.launch("VozSync_Final.mp4") },
                                onDownloadWav = { saveWavLauncher.launch("VozSync_Voice.wav") },
                                onShareMp4 = {
                                    result.mp4FilePath?.let { shareFile(it, "video/mp4", strings.shareMp4Button) }
                                },
                                onShareWav = {
                                    shareFile(result.wavFilePath, "audio/wav", strings.shareWavButton)
                                }
                            )
                        }

                        SceneTableSection(
                            strings = strings,
                            scenes = state.scenes,
                            durationSourceNote = state.durationSourceNote,
                            totalTimelineSec = state.totalTimelineSec,
                            playingSceneId = state.playingSceneId,
                            isGenerating = state.progress.isRunning,
                            onAddScene = { viewModel.addManualScene() },
                            onUpdateNarration = { id, text -> viewModel.updateSceneNarration(id, text) },
                            onUpdateDuration = { id, dur -> viewModel.updateSceneDuration(id, dur) },
                            onDeleteScene = { id -> viewModel.deleteScene(id) },
                            onToggleScenePlay = { scene -> viewModel.toggleSceneAudioPreview(scene) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 1240.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StudioHeaderBanner(
                        strings = strings,
                        currentLanguage = state.appLanguage,
                        onLanguageChange = { viewModel.setAppLanguage(it) }
                    )

                    // Status or error toast banner
                    AnimatedVisibility(visible = !state.statusBanner.isNullOrBlank() || !state.generalError.isNullOrBlank()) {
                        val isError = !state.generalError.isNullOrBlank()
                        val msg = state.generalError ?: state.statusBanner.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isError) StudioErrorContainer else StudioEmeraldContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isError) StudioError.copy(alpha = 0.6f) else StudioEmerald.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isError) StudioError else StudioEmerald
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.dismissBanner() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss",
                                        tint = StudioTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(0.45f)) {
                                leftColumnContent()
                            }
                            Box(modifier = Modifier.weight(0.55f)) {
                                rightColumnContent()
                            }
                        }
                    } else {
                        leftColumnContent()
                        rightColumnContent()
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
