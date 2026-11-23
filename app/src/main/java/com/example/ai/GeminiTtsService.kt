package com.example.ai

import android.util.Log
import com.example.media.WavAudioProcessor
import com.example.model.GeminiVoice
import com.example.model.NarrationLanguage
import com.example.model.VoiceStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.Locale

private const val TAG = "GeminiTtsService"
private const val TTS_MODEL_NAME = "gemini-3.1-flash-tts-preview"
private const val TTS_TIMEOUT_MS = 60_000L

class TtsQuotaException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class TtsAudioResult(
    val pcm24kMono16Bit: ShortArray,
    val sampleRate: Int = WavAudioProcessor.TARGET_SAMPLE_RATE,
    val durationSec: Double
)

class GeminiTtsService {

    private val modelCache = mutableMapOf<Pair<String, String>, GenerativeModel>()

    @OptIn(PublicPreviewAPI::class)
    private fun getModel(voice: GeminiVoice, language: NarrationLanguage): GenerativeModel {
        val key = voice.id to language.bcp47
        return modelCache.getOrPut(key) {
            Firebase.ai.generativeModel(
                modelName = TTS_MODEL_NAME,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(
                        voice = Voice(voice.id),
                        languageCode = language.bcp47
                    )
                }
            )
        }
    }

    suspend fun synthesizeSpeech(
        text: String,
        voice: GeminiVoice,
        narrationLanguage: NarrationLanguage,
        style: VoiceStyle,
        targetDurationSec: Double? = null,
        speedUpAttempt: Int = 0
    ): TtsAudioResult {
        val prompt = buildTtsPrompt(
            text = text,
            narrationLanguage = narrationLanguage,
            style = style,
            targetDurationSec = targetDurationSec,
            speedUpAttempt = speedUpAttempt
        )

        val model = getModel(voice, narrationLanguage)

        val response = try {
            withTimeout(TTS_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini TTS request timed out after ${TTS_TIMEOUT_MS}ms", e)
            throw Exception("Gemini TTS request timed out. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS call failed", e)
            if (isQuotaOrRateLimitError(e)) {
                throw TtsQuotaException(
                    e.localizedMessage ?: "Gemini TTS quota or rate limit exceeded (429).",
                    e
                )
            }
            throw e
        }

        val audioPart = response.candidates
            .asSequence()
            .flatMap { it.content.parts.asSequence() }
            .filterIsInstance<InlineDataPart>()
            .firstOrNull { part ->
                part.inlineData.isNotEmpty() &&
                    (part.mimeType.startsWith("audio/", ignoreCase = true) ||
                        part.mimeType.contains("pcm", ignoreCase = true) ||
                        part.mimeType.contains("L16", ignoreCase = true))
            }
            ?: response.candidates
                .asSequence()
                .flatMap { it.content.parts.asSequence() }
                .filterIsInstance<InlineDataPart>()
                .firstOrNull { it.inlineData.isNotEmpty() }

        if (audioPart == null) {
            val fallbackText = response.text
            Log.e(TAG, "No audio InlineDataPart returned. Text=$fallbackText")
            throw IllegalStateException(
                "Gemini TTS returned no audio payload." +
                    if (!fallbackText.isNullOrBlank()) " ($fallbackText)" else ""
            )
        }

        val normalizedPcm = decodeTo24kMono16Bit(audioPart.inlineData, audioPart.mimeType)
        if (normalizedPcm.isEmpty()) {
            throw IllegalStateException("Decoded Gemini TTS audio was empty.")
        }

        val durationSec = normalizedPcm.size.toDouble() / WavAudioProcessor.TARGET_SAMPLE_RATE
        return TtsAudioResult(
            pcm24kMono16Bit = normalizedPcm,
            sampleRate = WavAudioProcessor.TARGET_SAMPLE_RATE,
            durationSec = durationSec
        )
    }

    private fun buildTtsPrompt(
        text: String,
        narrationLanguage: NarrationLanguage,
        style: VoiceStyle,
        targetDurationSec: Double?,
        speedUpAttempt: Int
    ): String {
        val isSpanish = narrationLanguage.bcp47.startsWith("es")
        val styleDirective = style.directive(narrationLanguage)
        val formattedTarget = targetDurationSec?.let { String.format(Locale.US, "%.1f", it) }

        return if (isSpanish) {
            val paceInstruction = when (speedUpAttempt) {
                1 -> "IMPORTANTE: Habla ligeramente más rápido y ágil (aproximadamente un 10% más rápido) para encajar cómodamente en ${formattedTarget ?: "el tiempo"} segundos, manteniendo un tono cálido, natural y fluido, sin sonar apresurado ni robótico."
                2 -> "IMPORTANTE: Habla de forma claramente más ágil y concisa (aproximadamente un 18% más rápido) con pausas breves para caber dentro de ${formattedTarget ?: "el tiempo"} segundos, conservando calidez, claridad y naturalidad."
                else -> "Habla con un ritmo natural, pausas orgánicas y calidez humana, nunca robótico."
            }
            """
            Lee en voz alta el siguiente texto de narración exactamente como está escrito, sin añadir introducciones, comentarios ni palabras extra.
            Idioma: ${narrationLanguage.labelEs}.
            Dirección de voz: $styleDirective
            $paceInstruction

            Texto a narrar:
            $text
            """.trimIndent()
        } else {
            val paceInstruction = when (speedUpAttempt) {
                1 -> "IMPORTANT: Speak slightly faster and more briskly (about 10% brisker) so the read fits comfortably within ${formattedTarget ?: "the target"} seconds, while staying warm, natural, and fluid—never rushed or robotic."
                2 -> "IMPORTANT: Speak noticeably brisker with crisp, brief pauses (about 18% faster) so the narration fits within ${formattedTarget ?: "the target"} seconds while keeping warmth, clarity, and natural phrasing."
                else -> "Use natural breathing pauses, warm human inflection, and fluid delivery—never robotic."
            }
            """
            Read the following narration script aloud verbatim. Do not add intros, commentary, or extra words.
            Language: ${narrationLanguage.labelEn}.
            Voice direction: $styleDirective
            $paceInstruction

            Narration text:
            $text
            """.trimIndent()
        }
    }

    private fun decodeTo24kMono16Bit(bytes: ByteArray, mimeType: String): ShortArray {
        if (WavAudioProcessor.isWavHeader(bytes)) {
            val wav = WavAudioProcessor.parseWav(bytes)
            val mono = WavAudioProcessor.downmixToMono(wav.samples, wav.channels)
            return WavAudioProcessor.resampleLinear(
                samples = mono,
                fromRate = wav.sampleRate,
                toRate = WavAudioProcessor.TARGET_SAMPLE_RATE
            )
        }

        val sampleRate = parseSampleRateFromMime(mimeType) ?: WavAudioProcessor.TARGET_SAMPLE_RATE
        val channels = parseChannelsFromMime(mimeType) ?: 1
        val pcmSamples = WavAudioProcessor.pcm16LeBytesToShorts(bytes)
        val mono = WavAudioProcessor.downmixToMono(pcmSamples, channels)
        return WavAudioProcessor.resampleLinear(
            samples = mono,
            fromRate = sampleRate,
            toRate = WavAudioProcessor.TARGET_SAMPLE_RATE
        )
    }

    private fun parseSampleRateFromMime(mimeType: String): Int? {
        val match = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseChannelsFromMime(mimeType: String): Int? {
        val match = Regex("channels=(\\d+)", RegexOption.IGNORE_CASE).find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun isQuotaOrRateLimitError(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val msg = (current.message ?: "").lowercase(Locale.US)
            val className = current.javaClass.simpleName.lowercase(Locale.US)
            if (
                msg.contains("429") ||
                msg.contains("quota") ||
                msg.contains("resource_exhausted") ||
                msg.contains("resource exhausted") ||
                msg.contains("rate limit") ||
                msg.contains("too many requests") ||
                className.contains("quota")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
