package com.example.model

import java.util.UUID

enum class AppLanguage(val code: String) {
    ES("es"),
    EN("en")
}

enum class NarrationLanguage(
    val bcp47: String,
    val labelEs: String,
    val labelEn: String,
    val previewSample: String
) {
    ES_LATAM(
        bcp47 = "es-419",
        labelEs = "Español (Latinoamérica)",
        labelEn = "Spanish (Latin America)",
        previewSample = "Hola, esta es una prueba de narración con VozSync. Cada pausa fluye de forma cálida y natural."
    ),
    ES_ES(
        bcp47 = "es-ES",
        labelEs = "Español (España)",
        labelEn = "Spanish (Spain)",
        previewSample = "Hola, esta es una muestra de voz para tu vídeo en VozSync, con un ritmo cercano, claro y natural."
    ),
    EN_US(
        bcp47 = "en-US",
        labelEs = "Inglés (EE. UU.)",
        labelEn = "English (US)",
        previewSample = "Hi there, this is a VozSync voice preview. Notice the warm, effortless pacing and natural breathing."
    ),
    EN_GB(
        bcp47 = "en-GB",
        labelEs = "Inglés (Reino Unido)",
        labelEn = "English (UK)",
        previewSample = "Hello, this is a VozSync narration sample, crafted for smooth phrasing and crystal-clear timing."
    );

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.ES) labelEs else labelEn
}

enum class VoiceStyle(
    val labelEs: String,
    val labelEn: String,
    val directiveEs: String,
    val directiveEn: String
) {
    NATURAL_WARM(
        labelEs = "Natural y cálido",
        labelEn = "Natural & Warm",
        directiveEs = "Tono natural, cálido, humano y fluido, con pausas respiratorias suaves y expresividad genuina, nada robótico.",
        directiveEn = "Natural, warm, human, and fluid tone with gentle natural pauses and genuine phrasing, never robotic."
    ),
    DOCUMENTARY(
        labelEs = "Documental sereno",
        labelEn = "Calm Documentary",
        directiveEs = "Narración documental serena, cálida y envolvente, dicción nítida, pausas orgánicas y ritmo equilibrado, nunca monótono.",
        directiveEn = "Calm, warm, immersive documentary narration with crisp articulation, organic pauses, and poised pacing."
    ),
    STORYTELLING(
        labelEs = "Narrativo cinematográfico",
        labelEn = "Cinematic Storytelling",
        directiveEs = "Voz narrativa cercana y cinematográfica, con calidez emocional, cadencia fluida y pausas expresivas naturales.",
        directiveEn = "Intimate cinematic storytelling voice with emotional warmth, fluid cadence, and natural expressive pauses."
    ),
    CONVERSATIONAL(
        labelEs = "Conversacional cercano",
        labelEn = "Friendly Conversational",
        directiveEs = "Estilo conversacional amable y espontáneo, como hablando con un amigo, cálido, natural y sin rigidez.",
        directiveEn = "Friendly, spontaneous conversational style, as if speaking warmly to a friend, effortless and never stiff."
    ),
    EDUCATIONAL(
        labelEs = "Explicativo claro",
        labelEn = "Clear Explainer",
        directiveEs = "Estilo explicativo claro, cálido y ameno, articulación precisa con pausas naturales para facilitar la comprensión.",
        directiveEn = "Clear, warm, engaging explainer delivery with crisp articulation and natural pauses for easy comprehension."
    );

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.ES) labelEs else labelEn
    fun directive(lang: NarrationLanguage): String =
        if (lang.bcp47.startsWith("es")) directiveEs else directiveEn
}

data class GeminiVoice(
    val id: String,
    val displayName: String,
    val profileEs: String,
    val profileEn: String,
    val registerEs: String,
    val registerEn: String
) {
    fun profile(lang: AppLanguage): String = if (lang == AppLanguage.ES) profileEs else profileEn
    fun register(lang: AppLanguage): String = if (lang == AppLanguage.ES) registerEs else registerEn
}

object GeminiVoicesCatalog {
    val all: List<GeminiVoice> = listOf(
        GeminiVoice("Kore", "Kore", "Firme, cálida y muy clara", "Firm, warm & crystal clear", "Femenina · Media", "Female · Mid"),
        GeminiVoice("Aoede", "Aoede", "Suave, cercana y expresiva", "Breezy, warm & expressive", "Femenina · Cálida", "Female · Warm"),
        GeminiVoice("Leda", "Leda", "Joven, natural y luminosa", "Youthful, natural & bright", "Femenina · Ligera", "Female · Light"),
        GeminiVoice("Sulafat", "Sulafat", "Cálida, serena y narrativa", "Warm, poised & narrative", "Femenina · Suave", "Female · Smooth"),
        GeminiVoice("Vindemiatrix", "Vindemiatrix", "Gentil, pausada y documental", "Gentle, calm & documentary", "Femenina · Serena", "Female · Calm"),
        GeminiVoice("Puck", "Puck", "Animada, ágil y conversacional", "Upbeat, agile & conversational", "Masculina · Ágil", "Male · Agile"),
        GeminiVoice("Charon", "Charon", "Informativa, sobria y clara", "Informative, grounded & clear", "Masculina · Media", "Male · Mid"),
        GeminiVoice("Fenrir", "Fenrir", "Dinámica, cálida y con presencia", "Excitable, warm & resonant", "Masculina · Grave", "Male · Deep"),
        GeminiVoice("Orus", "Orus", "Firme, profunda y documental", "Firm, deep & documentary", "Masculina · Grave", "Male · Deep"),
        GeminiVoice("Zephyr", "Zephyr", "Brillante, amable y fluida", "Bright, friendly & fluid", "Neutra · Clara", "Neutral · Bright")
    )
}

sealed interface SceneSyncStatus {
    data object Pending : SceneSyncStatus
    data object Generating : SceneSyncStatus
    data class Retrying(
        val attempt: Int,
        val maxRetries: Int,
        val previousAudioSec: Double
    ) : SceneSyncStatus
    data class Synced(
        val rawAudioSec: Double,
        val paddedSilenceSec: Double
    ) : SceneSyncStatus
    data class SpedUp(
        val rawAudioSec: Double,
        val speedFactor: Double,
        val isTightFit: Boolean,
        val overflowSec: Double
    ) : SceneSyncStatus
    data class Error(
        val message: String,
        val isQuotaError: Boolean
    ) : SceneSyncStatus
}

data class SceneItem(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val label: String,
    val narration: String,
    val durationSec: Double,
    val status: SceneSyncStatus = SceneSyncStatus.Pending,
    val clipWavPath: String? = null,
    val rawAudioSec: Double? = null,
    val speedFactor: Double = 1.0,
    val isTightFit: Boolean = false
)

data class VideoInfo(
    val uriString: String,
    val displayName: String,
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val mimeType: String
)

data class AudioFileInfo(
    val uriString: String,
    val displayName: String,
    val durationSec: Double
)

data class GenerationProgress(
    val isRunning: Boolean = false,
    val stageText: String = "",
    val currentScene: Int = 0,
    val totalScenes: Int = 0,
    val fraction: Float = 0f,
    val elapsedSec: Long = 0L,
    val etaSec: Long? = null,
    val apiCallsUsed: Int = 0
)

data class ExportResult(
    val wavFilePath: String,
    val mp4FilePath: String?,
    val totalDurationSec: Double,
    val tightFitScenes: List<Int>,
    val hasBackgroundMusic: Boolean,
    val videoMuxNote: String? = null
)

data class QuotaErrorState(
    val message: String,
    val failedSceneIndex: Int
)

data class VozSyncUiState(
    val appLanguage: AppLanguage = AppLanguage.ES,
    val videoInfo: VideoInfo? = null,
    val jsonFileName: String? = null,
    val txtFileName: String? = null,
    val musicFileInfo: AudioFileInfo? = null,
    val includeBackgroundMusic: Boolean = true,
    val scenes: List<SceneItem> = emptyList(),
    val durationSourceNote: String? = null,
    val isDetectingCuts: Boolean = false,
    val cutDetectionProgress: Float = 0f,
    val selectedVoice: GeminiVoice = GeminiVoicesCatalog.all.first(),
    val narrationLanguage: NarrationLanguage = NarrationLanguage.ES_LATAM,
    val voiceStyle: VoiceStyle = VoiceStyle.NATURAL_WARM,
    val isPreviewingVoice: Boolean = false,
    val previewError: String? = null,
    val playingSceneId: String? = null,
    val showApiConfirmDialog: Boolean = false,
    val pendingStartFromSceneIndex: Int = 0,
    val progress: GenerationProgress = GenerationProgress(),
    val quotaError: QuotaErrorState? = null,
    val generalError: String? = null,
    val statusBanner: String? = null,
    val exportResult: ExportResult? = null
) {
    val totalTimelineSec: Double
        get() = scenes.sumOf { it.durationSec.coerceAtLeast(0.0) }

    val validScenesCount: Int
        get() = scenes.count { it.narration.isNotBlank() && it.durationSec > 0.0 }

    val remainingScenesCount: Int
        get() = scenes.drop(pendingStartFromSceneIndex).count { it.narration.isNotBlank() && it.durationSec > 0.0 }
}
