package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiTtsService
import com.example.ai.TtsQuotaException
import com.example.data.ProjectParser
import com.example.data.RawSceneData
import com.example.i18n.AppStringsProvider
import com.example.media.AudioDecoder
import com.example.media.PreviewAudioPlayer
import com.example.media.TimelineAudioClip
import com.example.media.VideoAudioMuxer
import com.example.media.VideoCutDetector
import com.example.media.WavAudioProcessor
import com.example.model.AppLanguage
import com.example.model.ExportResult
import com.example.model.GeminiVoice
import com.example.model.GenerationProgress
import com.example.model.NarrationLanguage
import com.example.model.QuotaErrorState
import com.example.model.SceneItem
import com.example.model.SceneSyncStatus
import com.example.model.VoiceStyle
import com.example.model.VozSyncUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

private const val TAG = "VozSyncViewModel"
private const val PREFS_NAME = "vozsync_prefs"
private const val KEY_APP_LANG = "app_lang"
private const val MAX_TTS_RETRIES = 2

class VozSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ttsService = GeminiTtsService()
    private val previewPlayer = PreviewAudioPlayer(viewModelScope)

    private var cachedJsonScenes: List<RawSceneData> = emptyList()
    private var jsonHasDurations: Boolean = false
    private var cachedTxtLines: List<String> = emptyList()
    private val generatedScenePcm = mutableMapOf<String, ShortArray>()

    private var cutDetectionJob: Job? = null
    private var generationJob: Job? = null
    private var previewJob: Job? = null

    private val _uiState = MutableStateFlow(
        VozSyncUiState(
            appLanguage = loadSavedLanguage()
        )
    )
    val uiState: StateFlow<VozSyncUiState> = _uiState.asStateFlow()

    private fun loadSavedLanguage(): AppLanguage {
        val code = prefs.getString(KEY_APP_LANG, AppLanguage.ES.code)
        return if (code == AppLanguage.EN.code) AppLanguage.EN else AppLanguage.ES
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANG, language.code).apply()
        _uiState.update { state ->
            val suggestedNarration = when {
                language == AppLanguage.EN && state.narrationLanguage.bcp47.startsWith("es") -> NarrationLanguage.EN_US
                language == AppLanguage.ES && state.narrationLanguage.bcp47.startsWith("en") -> NarrationLanguage.ES_LATAM
                else -> state.narrationLanguage
            }
            state.copy(appLanguage = language, narrationLanguage = suggestedNarration)
        }
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(generalError = null, statusBanner = null) }
            try {
                val info = VideoCutDetector.inspectVideo(appContext, uri)
                _uiState.update { it.copy(videoInfo = info, exportResult = null) }

                // If scenes exist without JSON durations, run cut detection automatically
                val currentScenes = _uiState.value.scenes
                if (!jsonHasDurations && (currentScenes.isNotEmpty() || cachedJsonScenes.isNotEmpty() || cachedTxtLines.isNotEmpty())) {
                    detectVideoCutsInternal(uri, expectedCount = currentScenes.size.takeIf { it > 1 })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect video", e)
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                _uiState.update { it.copy(generalError = strings.errorParseFile(e.localizedMessage ?: "Video")) }
            }
        }
    }

    fun onJsonSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(generalError = null, statusBanner = null) }
            try {
                val fileName = VideoCutDetector.queryDisplayName(appContext, uri, "project.json")
                val rawText = readTextFromUri(uri)
                val parsed = ProjectParser.parseJsonProject(rawText)

                cachedJsonScenes = parsed.scenes
                jsonHasDurations = parsed.hasValidDurations
                generatedScenePcm.clear()

                val video = _uiState.value.videoInfo
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)

                val rebuilt = ProjectParser.buildSceneItems(
                    jsonScenes = cachedJsonScenes,
                    txtLines = cachedTxtLines,
                    detectedDurations = null,
                    videoTotalDurationSec = video?.durationSec
                )

                val note = when {
                    parsed.hasValidDurations && parsed.hasMissingNarrations && cachedTxtLines.isNotEmpty() ->
                        "${strings.durationFromJsonNote} ${strings.narrationFromTxtNote}"
                    parsed.hasValidDurations -> strings.durationFromJsonNote
                    else -> null
                }

                _uiState.update {
                    it.copy(
                        jsonFileName = fileName,
                        scenes = rebuilt,
                        durationSourceNote = note,
                        exportResult = null,
                        quotaError = null
                    )
                }

                // Step 1 rule: if JSON lacks durations and video is loaded, detect cuts automatically
                if (!parsed.hasValidDurations && video != null) {
                    detectVideoCutsInternal(Uri.parse(video.uriString), expectedCount = parsed.scenes.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON", e)
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                _uiState.update { it.copy(generalError = strings.errorParseFile(e.localizedMessage ?: "JSON")) }
            }
        }
    }

    fun onTxtSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(generalError = null, statusBanner = null) }
            try {
                val fileName = VideoCutDetector.queryDisplayName(appContext, uri, "script.txt")
                val rawText = readTextFromUri(uri)
                val lines = ProjectParser.parseTxtScript(rawText)
                require(lines.isNotEmpty()) { "TXT file contains no narration lines." }

                cachedTxtLines = lines
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                val current = _uiState.value

                val updatedScenes = if (current.scenes.isNotEmpty()) {
                    // Fill missing narrations from TXT without overwriting user-edited durations
                    current.scenes.mapIndexed { idx, scene ->
                        val fallbackTxt = lines.getOrNull(idx).orEmpty()
                        if (scene.narration.isBlank() && fallbackTxt.isNotBlank()) {
                            scene.copy(narration = fallbackTxt, status = SceneSyncStatus.Pending)
                        } else {
                            scene
                        }
                    }
                } else {
                    ProjectParser.buildSceneItems(
                        jsonScenes = emptyList(),
                        txtLines = lines,
                        detectedDurations = null,
                        videoTotalDurationSec = current.videoInfo?.durationSec
                    )
                }

                _uiState.update {
                    it.copy(
                        txtFileName = fileName,
                        scenes = updatedScenes,
                        durationSourceNote = strings.narrationFromTxtNote,
                        exportResult = null
                    )
                }

                // If no JSON durations exist and video is loaded, try frame cut detection for TXT scenes
                val video = _uiState.value.videoInfo
                if (!jsonHasDurations && video != null) {
                    detectVideoCutsInternal(Uri.parse(video.uriString), expectedCount = updatedScenes.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse TXT", e)
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                _uiState.update { it.copy(generalError = strings.errorParseFile(e.localizedMessage ?: "TXT")) }
            }
        }
    }

    fun onBackgroundMusicSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(generalError = null) }
            val info = VideoCutDetector.inspectAudio(appContext, uri)
            _uiState.update {
                it.copy(
                    musicFileInfo = info,
                    includeBackgroundMusic = true
                )
            }
        }
    }

    fun removeBackgroundMusic() {
        _uiState.update { it.copy(musicFileInfo = null, includeBackgroundMusic = false) }
    }

    fun toggleBackgroundMusic(enabled: Boolean) {
        _uiState.update { it.copy(includeBackgroundMusic = enabled) }
    }

    fun runManualCutDetection() {
        val video = _uiState.value.videoInfo ?: return
        val expectedCount = _uiState.value.scenes.size.takeIf { it > 1 }
        detectVideoCutsInternal(Uri.parse(video.uriString), expectedCount)
    }

    private fun detectVideoCutsInternal(videoUri: Uri, expectedCount: Int?) {
        cutDetectionJob?.cancel()
        cutDetectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isDetectingCuts = true, cutDetectionProgress = 0f, generalError = null) }
            try {
                val detected = VideoCutDetector.detectSceneDurations(
                    context = appContext,
                    uri = videoUri,
                    expectedSceneCount = expectedCount,
                    onProgress = { p ->
                        _uiState.update { it.copy(cutDetectionProgress = p) }
                    }
                )
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                val state = _uiState.value

                if (detected != null && detected.isNotEmpty()) {
                    val updatedScenes = if (state.scenes.isEmpty()) {
                        detected.mapIndexed { idx, dur ->
                            SceneItem(
                                index = idx,
                                label = "#${idx + 1}",
                                narration = cachedTxtLines.getOrNull(idx).orEmpty(),
                                durationSec = dur
                            )
                        }
                    } else {
                        state.scenes.mapIndexed { idx, scene ->
                            val dur = detected.getOrNull(idx) ?: scene.durationSec
                            scene.copy(durationSec = dur)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            scenes = updatedScenes,
                            isDetectingCuts = false,
                            cutDetectionProgress = 1f,
                            durationSourceNote = strings.durationFromCutsNote(detected.size)
                        )
                    }
                } else {
                    // Fallback: divide video evenly so user can edit in table
                    val total = state.videoInfo?.durationSec ?: 0.0
                    val updated = if (state.scenes.isNotEmpty() && total > 0.5) {
                        val even = round((total / state.scenes.size) * 100.0) / 100.0
                        state.scenes.map { it.copy(durationSec = even.coerceAtLeast(1.0)) }
                    } else {
                        state.scenes
                    }
                    _uiState.update {
                        it.copy(
                            scenes = updated,
                            isDetectingCuts = false,
                            cutDetectionProgress = 0f,
                            durationSourceNote = strings.durationFallbackNote
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cut detection error", e)
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                _uiState.update {
                    it.copy(
                        isDetectingCuts = false,
                        durationSourceNote = strings.durationFallbackNote
                    )
                }
            }
        }
    }

    fun updateSceneNarration(sceneId: String, newNarration: String) {
        generatedScenePcm.remove(sceneId)
        _uiState.update { state ->
            state.copy(
                scenes = state.scenes.map { scene ->
                    if (scene.id == sceneId) {
                        scene.copy(narration = newNarration, status = SceneSyncStatus.Pending, clipWavPath = null)
                    } else scene
                }
            )
        }
    }

    fun updateSceneDuration(sceneId: String, newDurationSec: Double) {
        val clamped = (round(newDurationSec.coerceIn(0.5, 600.0) * 100.0) / 100.0)
        _uiState.update { state ->
            state.copy(
                scenes = state.scenes.map { scene ->
                    if (scene.id == sceneId) scene.copy(durationSec = clamped) else scene
                }
            )
        }
    }

    fun addManualScene() {
        _uiState.update { state ->
            val nextIdx = state.scenes.size
            val newScene = SceneItem(
                index = nextIdx,
                label = "#${nextIdx + 1}",
                narration = "",
                durationSec = 5.0
            )
            state.copy(scenes = state.scenes + newScene)
        }
    }

    fun deleteScene(sceneId: String) {
        generatedScenePcm.remove(sceneId)
        _uiState.update { state ->
            val filtered = state.scenes.filterNot { it.id == sceneId }
                .mapIndexed { idx, s -> s.copy(index = idx) }
            state.copy(scenes = filtered)
        }
    }

    fun selectVoice(voice: GeminiVoice) {
        _uiState.update { it.copy(selectedVoice = voice, previewError = null) }
    }

    fun selectNarrationLanguage(language: NarrationLanguage) {
        _uiState.update { it.copy(narrationLanguage = language, previewError = null) }
    }

    fun selectVoiceStyle(style: VoiceStyle) {
        _uiState.update { it.copy(voiceStyle = style, previewError = null) }
    }

    fun previewSelectedVoice() {
        previewJob?.cancel()
        previewPlayer.stop()
        val state = _uiState.value
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isPreviewingVoice = true, previewError = null, playingSceneId = null) }
            try {
                val result = ttsService.synthesizeSpeech(
                    text = state.narrationLanguage.previewSample,
                    voice = state.selectedVoice,
                    narrationLanguage = state.narrationLanguage,
                    style = state.voiceStyle
                )
                _uiState.update { it.copy(isPreviewingVoice = false) }
                previewPlayer.playPcm24k(result.pcm24kMono16Bit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TtsQuotaException) {
                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                _uiState.update {
                    it.copy(
                        isPreviewingVoice = false,
                        previewError = "${strings.quotaErrorTitle}: ${e.localizedMessage ?: "429"}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice preview failed", e)
                _uiState.update {
                    it.copy(
                        isPreviewingVoice = false,
                        previewError = e.localizedMessage ?: "Voice preview failed."
                    )
                }
            }
        }
    }

    fun toggleSceneAudioPreview(scene: SceneItem) {
        val currentPlaying = _uiState.value.playingSceneId
        if (currentPlaying == scene.id) {
            previewPlayer.stop()
            _uiState.update { it.copy(playingSceneId = null) }
            return
        }
        val path = scene.clipWavPath ?: return
        _uiState.update { it.copy(playingSceneId = scene.id) }
        previewPlayer.playWavFile(path) {
            _uiState.update { st ->
                if (st.playingSceneId == scene.id) st.copy(playingSceneId = null) else st
            }
        }
    }

    fun requestStartGeneration(startFromSceneIndex: Int = 0) {
        val state = _uiState.value
        val strings = AppStringsProvider.get(state.appLanguage)
        if (state.validScenesCount == 0) {
            _uiState.update { it.copy(generalError = strings.errorNoScenes) }
            return
        }
        _uiState.update {
            it.copy(
                generalError = null,
                pendingStartFromSceneIndex = startFromSceneIndex.coerceIn(0, max(0, state.scenes.lastIndex)),
                showApiConfirmDialog = true
            )
        }
    }

    fun dismissApiConfirmDialog() {
        _uiState.update { it.copy(showApiConfirmDialog = false) }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                progress = it.progress.copy(isRunning = false, etaSec = null)
            )
        }
    }

    fun confirmAndStartGeneration() {
        val startIndex = _uiState.value.pendingStartFromSceneIndex
        _uiState.update {
            it.copy(
                showApiConfirmDialog = false,
                quotaError = null,
                generalError = null,
                statusBanner = null,
                exportResult = null
            )
        }

        previewPlayer.stop()
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            runSynchronizationPipeline(startFromSceneIndex = startIndex)
        }
    }

    private suspend fun runSynchronizationPipeline(startFromSceneIndex: Int) {
        val snapshot = _uiState.value
        val strings = AppStringsProvider.get(snapshot.appLanguage)
        val scenes = snapshot.scenes
        val totalScenes = scenes.size
        if (totalScenes == 0) return

        val startTimeMs = System.currentTimeMillis()
        var apiCallsUsed = 0
        val perSceneTimingsMs = mutableListOf<Long>()

        val exportsDir = File(appContext.cacheDir, "exports").apply { mkdirs() }

        // Keep elapsed time fresh every second
        val timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000L
                _uiState.update { st ->
                    if (st.progress.isRunning) {
                        st.copy(progress = st.progress.copy(elapsedSec = elapsed))
                    } else st
                }
            }
        }

        try {
            // Reset pending state for scenes we are about to generate
            _uiState.update { st ->
                st.copy(
                    scenes = st.scenes.mapIndexed { idx, s ->
                        if (idx >= startFromSceneIndex) {
                            s.copy(status = SceneSyncStatus.Pending, isTightFit = false)
                        } else s
                    },
                    progress = GenerationProgress(
                        isRunning = true,
                        stageText = strings.stageGeneratingScene(startFromSceneIndex + 1, totalScenes),
                        currentScene = startFromSceneIndex + 1,
                        totalScenes = totalScenes,
                        fraction = (startFromSceneIndex.toFloat() / totalScenes.toFloat()) * 0.80f,
                        elapsedSec = 0L,
                        etaSec = ((totalScenes - startFromSceneIndex) * 4L),
                        apiCallsUsed = 0
                    )
                )
            }

            val tightFitSceneNumbers = mutableListOf<Int>()

            for (i in scenes.indices) {
                val scene = _uiState.value.scenes[i]
                val sceneNum = i + 1

                if (i < startFromSceneIndex && generatedScenePcm.containsKey(scene.id)) {
                    if (scene.isTightFit) tightFitSceneNumbers.add(sceneNum)
                    continue
                }

                if (scene.narration.isBlank()) {
                    val silenceSamples = (scene.durationSec * WavAudioProcessor.TARGET_SAMPLE_RATE).roundToInt().coerceAtLeast(0)
                    generatedScenePcm[scene.id] = ShortArray(silenceSamples)
                    updateSceneAt(i) {
                        it.copy(
                            status = SceneSyncStatus.Synced(0.0, scene.durationSec),
                            rawAudioSec = 0.0,
                            speedFactor = 1.0,
                            isTightFit = false
                        )
                    }
                    continue
                }

                val sceneStartMs = System.currentTimeMillis()

                updateSceneAt(i) { it.copy(status = SceneSyncStatus.Generating) }
                updateProgress(
                    stageText = strings.stageGeneratingScene(sceneNum, totalScenes),
                    currentScene = sceneNum,
                    totalScenes = totalScenes,
                    fraction = (i.toFloat() / totalScenes.toFloat()) * 0.80f,
                    startTimeMs = startTimeMs,
                    remainingScenes = totalScenes - i,
                    avgSceneMs = perSceneTimingsMs.average().takeIf { !it.isNaN() }?.toLong() ?: 4500L,
                    apiCallsUsed = apiCallsUsed
                )

                var bestPcm: ShortArray? = null
                var bestDurationSec = Double.MAX_VALUE
                var attempt = 0

                while (attempt <= MAX_TTS_RETRIES) {
                    if (attempt > 0) {
                        updateSceneAt(i) {
                            it.copy(
                                status = SceneSyncStatus.Retrying(
                                    attempt = attempt,
                                    maxRetries = MAX_TTS_RETRIES,
                                    previousAudioSec = bestDurationSec
                                )
                            )
                        }
                        updateProgress(
                            stageText = strings.stageRetryingScene(sceneNum, totalScenes, attempt),
                            currentScene = sceneNum,
                            totalScenes = totalScenes,
                            fraction = ((i + 0.35f * attempt / MAX_TTS_RETRIES) / totalScenes.toFloat()) * 0.80f,
                            startTimeMs = startTimeMs,
                            remainingScenes = totalScenes - i,
                            avgSceneMs = perSceneTimingsMs.average().takeIf { !it.isNaN() }?.toLong() ?: 4500L,
                            apiCallsUsed = apiCallsUsed
                        )
                    }

                    apiCallsUsed++
                    val ttsResult = try {
                        ttsService.synthesizeSpeech(
                            text = scene.narration,
                            voice = snapshot.selectedVoice,
                            narrationLanguage = snapshot.narrationLanguage,
                            style = snapshot.voiceStyle,
                            targetDurationSec = scene.durationSec,
                            speedUpAttempt = attempt
                        )
                    } catch (q: TtsQuotaException) {
                        Log.w(TAG, "Quota error on scene $sceneNum", q)
                        val quotaMsg = strings.errorQuotaMessage(sceneNum)
                        updateSceneAt(i) {
                            it.copy(status = SceneSyncStatus.Error(quotaMsg, isQuotaError = true))
                        }
                        _uiState.update {
                            it.copy(
                                quotaError = QuotaErrorState(message = quotaMsg, failedSceneIndex = i),
                                progress = it.progress.copy(isRunning = false, apiCallsUsed = apiCallsUsed, etaSec = null)
                            )
                        }
                        return
                    }

                    val trimmed = WavAudioProcessor.trimTrailingSilence(ttsResult.pcm24kMono16Bit)
                    val trimmedSec = trimmed.size.toDouble() / WavAudioProcessor.TARGET_SAMPLE_RATE

                    if (trimmedSec < bestDurationSec) {
                        bestPcm = trimmed
                        bestDurationSec = trimmedSec
                    }

                    // Fits comfortably inside scene duration -> no need for faster retries
                    if (bestDurationSec <= scene.durationSec + 0.05) {
                        break
                    }
                    attempt++
                }

                // Synchronize best audio clip to scene duration (silence pad OR WSOLA atempo <= 1.15x)
                updateProgress(
                    stageText = strings.stageSyncingScene(sceneNum, totalScenes),
                    currentScene = sceneNum,
                    totalScenes = totalScenes,
                    fraction = ((i + 0.85f) / totalScenes.toFloat()) * 0.80f,
                    startTimeMs = startTimeMs,
                    remainingScenes = totalScenes - i - 1,
                    avgSceneMs = perSceneTimingsMs.average().takeIf { !it.isNaN() }?.toLong() ?: 4500L,
                    apiCallsUsed = apiCallsUsed
                )

                val rawClip = bestPcm ?: ShortArray(0)
                val targetSamples = (scene.durationSec * WavAudioProcessor.TARGET_SAMPLE_RATE).roundToInt().coerceAtLeast(1)

                val finalClipPcm: ShortArray
                val appliedSpeed: Double
                val isTightFit: Boolean
                val finalStatus: SceneSyncStatus

                if (bestDurationSec <= scene.durationSec) {
                    finalClipPcm = WavAudioProcessor.padWithSilence(rawClip, targetSamples)
                    appliedSpeed = 1.0
                    isTightFit = false
                    val silenceAdded = max(0.0, scene.durationSec - bestDurationSec)
                    finalStatus = SceneSyncStatus.Synced(
                        rawAudioSec = bestDurationSec,
                        paddedSilenceSec = silenceAdded
                    )
                } else {
                    val requiredSpeed = bestDurationSec / scene.durationSec
                    appliedSpeed = requiredSpeed.coerceAtMost(WavAudioProcessor.MAX_ATEMPO_SPEED)
                    val stretched = withContext(Dispatchers.Default) {
                        WavAudioProcessor.timeStretchWsola(rawClip, appliedSpeed)
                    }
                    val stretchedSec = stretched.size.toDouble() / WavAudioProcessor.TARGET_SAMPLE_RATE
                    val overflowSec = max(0.0, stretchedSec - scene.durationSec)
                    isTightFit = requiredSpeed > 1.05 || overflowSec > 0.05
                    if (isTightFit) {
                        tightFitSceneNumbers.add(sceneNum)
                    }

                    finalClipPcm = if (stretched.size < targetSamples) {
                        WavAudioProcessor.padWithSilence(stretched, targetSamples)
                    } else {
                        stretched
                    }

                    finalStatus = SceneSyncStatus.SpedUp(
                        rawAudioSec = bestDurationSec,
                        speedFactor = appliedSpeed,
                        isTightFit = isTightFit,
                        overflowSec = overflowSec
                    )
                }

                // Save per-scene WAV clip for instant in-table auditioning
                val sceneWavFile = File(exportsDir, "scene_${sceneNum}.wav")
                withContext(Dispatchers.IO) {
                    WavAudioProcessor.writeWavFile(sceneWavFile, finalClipPcm)
                }
                generatedScenePcm[scene.id] = finalClipPcm

                updateSceneAt(i) {
                    it.copy(
                        status = finalStatus,
                        clipWavPath = sceneWavFile.absolutePath,
                        rawAudioSec = bestDurationSec,
                        speedFactor = appliedSpeed,
                        isTightFit = isTightFit
                    )
                }

                perSceneTimingsMs.add(System.currentTimeMillis() - sceneStartMs)
            }

            // Step 5: Place every scene audio at its exact start timestamp and join into master track
            updateProgress(
                stageText = strings.stageAssemblingTrack,
                currentScene = totalScenes,
                totalScenes = totalScenes,
                fraction = 0.84f,
                startTimeMs = startTimeMs,
                remainingScenes = 0,
                avgSceneMs = 2000L,
                apiCallsUsed = apiCallsUsed
            )

            val latestScenes = _uiState.value.scenes
            var cumulativeStart = 0.0
            val timelineClips = latestScenes.map { sc ->
                val clip = TimelineAudioClip(
                    startSec = cumulativeStart,
                    targetDurationSec = sc.durationSec,
                    pcmSamples = generatedScenePcm[sc.id] ?: ShortArray(0)
                )
                cumulativeStart += sc.durationSec
                clip
            }
            val totalTimelineSec = max(cumulativeStart, _uiState.value.videoInfo?.durationSec ?: 0.0)

            var masterPcm = withContext(Dispatchers.Default) {
                WavAudioProcessor.assembleTimeline(timelineClips, totalTimelineSec)
            }

            // Optional background music mixed softly at 12%
            val musicInfo = _uiState.value.musicFileInfo
            val shouldMixMusic = musicInfo != null && _uiState.value.includeBackgroundMusic
            var actuallyMixedMusic = false

            if (shouldMixMusic && musicInfo != null) {
                updateProgress(
                    stageText = strings.stageMixingMusic,
                    currentScene = totalScenes,
                    totalScenes = totalScenes,
                    fraction = 0.89f,
                    startTimeMs = startTimeMs,
                    remainingScenes = 0,
                    avgSceneMs = 1500L,
                    apiCallsUsed = apiCallsUsed
                )
                try {
                    val musicPcm = AudioDecoder.decodeTo24kMonoPcm(appContext, Uri.parse(musicInfo.uriString))
                    if (musicPcm.isNotEmpty()) {
                        masterPcm = withContext(Dispatchers.Default) {
                            WavAudioProcessor.mixBackgroundMusic(
                                voicePcm = masterPcm,
                                musicPcm = musicPcm,
                                musicGain = WavAudioProcessor.BACKGROUND_MUSIC_GAIN
                            )
                        }
                        actuallyMixedMusic = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background music decode/mix failed, proceeding with voice only", e)
                }
            }

            // Write master WAV (24 kHz mono 16-bit)
            val masterWavFile = File(exportsDir, "vozsync_master_${System.currentTimeMillis()}.wav")
            withContext(Dispatchers.IO) {
                WavAudioProcessor.writeWavFile(masterWavFile, masterPcm)
            }

            // Step 6: Mux with muted video without re-encoding (-c:v copy)
            val videoInfo = _uiState.value.videoInfo
            var finalMp4Path: String? = null
            var videoMuxNote: String? = null

            if (videoInfo != null) {
                updateProgress(
                    stageText = strings.stageMuxingVideo,
                    currentScene = totalScenes,
                    totalScenes = totalScenes,
                    fraction = 0.94f,
                    startTimeMs = startTimeMs,
                    remainingScenes = 0,
                    avgSceneMs = 1500L,
                    apiCallsUsed = apiCallsUsed
                )
                val mp4File = File(exportsDir, "vozsync_final_${System.currentTimeMillis()}.mp4")
                val muxSuccess = VideoAudioMuxer.muxVideoWithMasterPcm(
                    context = appContext,
                    videoUri = Uri.parse(videoInfo.uriString),
                    masterPcm24kMono = masterPcm,
                    outputMp4File = mp4File
                )
                if (muxSuccess && mp4File.exists() && mp4File.length() > 0) {
                    finalMp4Path = mp4File.absolutePath
                } else {
                    videoMuxNote = strings.errorVideoMuxFallback
                }
            }

            val totalAudioSec = masterPcm.size.toDouble() / WavAudioProcessor.TARGET_SAMPLE_RATE
            val elapsedTotal = (System.currentTimeMillis() - startTimeMs) / 1000L

            _uiState.update {
                it.copy(
                    progress = GenerationProgress(
                        isRunning = false,
                        stageText = strings.stageComplete,
                        currentScene = totalScenes,
                        totalScenes = totalScenes,
                        fraction = 1f,
                        elapsedSec = elapsedTotal,
                        etaSec = 0L,
                        apiCallsUsed = apiCallsUsed
                    ),
                    exportResult = ExportResult(
                        wavFilePath = masterWavFile.absolutePath,
                        mp4FilePath = finalMp4Path,
                        totalDurationSec = totalAudioSec,
                        tightFitScenes = tightFitSceneNumbers.distinct().sorted(),
                        hasBackgroundMusic = actuallyMixedMusic,
                        videoMuxNote = videoMuxNote
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Synchronization pipeline failed", e)
            _uiState.update {
                it.copy(
                    generalError = e.localizedMessage ?: "Synchronization failed.",
                    progress = it.progress.copy(isRunning = false, etaSec = null, apiCallsUsed = apiCallsUsed)
                )
            }
        } finally {
            timerJob.cancel()
        }
    }

    fun exportArtifactToUri(sourcePath: String, destinationUri: Uri, suggestedName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val srcFile = File(sourcePath)
                require(srcFile.exists()) { "Source file no longer exists." }
                appContext.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Could not open destination output stream.")

                val strings = AppStringsProvider.get(_uiState.value.appLanguage)
                _uiState.update { it.copy(statusBanner = strings.savedFileMessage(suggestedName)) }
            } catch (e: Exception) {
                Log.e(TAG, "Export to Uri failed", e)
                _uiState.update { it.copy(generalError = e.localizedMessage ?: "Failed to save file.") }
            }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null, generalError = null) }
    }

    private fun updateSceneAt(index: Int, transform: (SceneItem) -> SceneItem) {
        _uiState.update { st ->
            if (index !in st.scenes.indices) return@update st
            val mutable = st.scenes.toMutableList()
            mutable[index] = transform(mutable[index])
            st.copy(scenes = mutable)
        }
    }

    private fun updateProgress(
        stageText: String,
        currentScene: Int,
        totalScenes: Int,
        fraction: Float,
        startTimeMs: Long,
        remainingScenes: Int,
        avgSceneMs: Long,
        apiCallsUsed: Int
    ) {
        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000L
        val etaSec = ((remainingScenes * avgSceneMs) / 1000L) + if (remainingScenes > 0) 2L else 1L
        _uiState.update {
            it.copy(
                progress = GenerationProgress(
                    isRunning = true,
                    stageText = stageText,
                    currentScene = currentScene,
                    totalScenes = totalScenes,
                    fraction = fraction.coerceIn(0f, 1f),
                    elapsedSec = elapsedSec,
                    etaSec = etaSec,
                    apiCallsUsed = apiCallsUsed
                )
            )
        }
    }

    private suspend fun readTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: throw IllegalStateException("Unable to read selected file.")
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer.stop()
        generationJob?.cancel()
        cutDetectionJob?.cancel()
        previewJob?.cancel()
    }
}
