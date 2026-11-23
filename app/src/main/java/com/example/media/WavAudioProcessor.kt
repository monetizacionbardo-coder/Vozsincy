package com.example.media

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh

data class ParsedWav(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val samples: ShortArray
)

data class TimelineAudioClip(
    val startSec: Double,
    val targetDurationSec: Double,
    val pcmSamples: ShortArray
)

object WavAudioProcessor {
    const val TARGET_SAMPLE_RATE = 24_000
    const val TARGET_CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val MAX_ATEMPO_SPEED = 1.15
    const val BACKGROUND_MUSIC_GAIN = 0.12f

    fun isWavHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 44 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() &&
            bytes[11] == 'E'.code.toByte()
    }

    fun parseWav(bytes: ByteArray): ParsedWav {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var channels = 1
        var sampleRate = TARGET_SAMPLE_RATE
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(offset + 4)
            val contentStart = offset + 8

            if (chunkId == "fmt " && contentStart + 16 <= bytes.size) {
                channels = bb.getShort(contentStart + 2).toInt().coerceAtLeast(1)
                sampleRate = bb.getInt(contentStart + 4).coerceAtLeast(8000)
                bitsPerSample = bb.getShort(contentStart + 14).toInt().coerceAtLeast(8)
            } else if (chunkId == "data") {
                dataOffset = contentStart
                dataSize = min(chunkSize, bytes.size - contentStart).coerceAtLeast(0)
                break
            }

            val paddedSize = chunkSize + (chunkSize and 1)
            offset = contentStart + paddedSize
        }

        if (dataOffset < 0 || dataSize <= 0) {
            return ParsedWav(sampleRate, channels, bitsPerSample, ShortArray(0))
        }

        val dataBytes = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
        val samples = pcm16LeBytesToShorts(dataBytes)
        return ParsedWav(sampleRate, channels, bitsPerSample, samples)
    }

    fun pcm16LeBytesToShorts(bytes: ByteArray): ShortArray {
        val count = bytes.size / 2
        val result = ShortArray(count)
        val bb = ByteBuffer.wrap(bytes, 0, count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            result[i] = bb.short
        }
        return result
    }

    fun shortsToPcm16LeBytes(samples: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            bb.putShort(s)
        }
        return bb.array()
    }

    fun downmixToMono(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1 || samples.isEmpty()) return samples
        val frames = samples.size / channels
        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            val base = f * channels
            for (c in 0 until channels) {
                sum += samples[base + c].toInt()
            }
            mono[f] = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return mono
    }

    fun resampleLinear(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (samples.isEmpty() || fromRate <= 0 || toRate <= 0 || fromRate == toRate) return samples
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = max(1, (samples.size / ratio).roundToInt())
        val out = ShortArray(outLen)
        val lastIdx = samples.size - 1

        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx0 = floor(srcPos).toInt().coerceIn(0, lastIdx)
            val idx1 = min(idx0 + 1, lastIdx)
            val frac = srcPos - idx0
            val interp = samples[idx0] * (1.0 - frac) + samples[idx1] * frac
            out[i] = interp.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun trimTrailingSilence(samples: ShortArray, sampleRate: Int = TARGET_SAMPLE_RATE): ShortArray {
        if (samples.isEmpty()) return samples
        val threshold = 220 // ~ -43 dBFS
        var lastNonSilent = samples.size - 1
        while (lastNonSilent > 0 && abs(samples[lastNonSilent].toInt()) < threshold) {
            lastNonSilent--
        }
        // Keep 60ms natural tail room
        val tailSamples = (sampleRate * 0.06).roundToInt()
        val endIndex = min(samples.size, lastNonSilent + tailSamples)
        return if (endIndex < samples.size) samples.copyOf(endIndex) else samples
    }

    fun padWithSilence(samples: ShortArray, targetSamples: Int): ShortArray {
        if (targetSamples <= samples.size) return samples
        val padded = ShortArray(targetSamples)
        System.arraycopy(samples, 0, padded, 0, samples.size)
        return padded
    }

    /**
     * Pitch-preserving WSOLA (Waveform Similarity Overlap-Add) time-stretcher.
     * Speeds up speech (speedFactor > 1.0, e.g., up to 1.15x) without raising pitch.
     */
    fun timeStretchWsola(
        input: ShortArray,
        speedFactor: Double,
        sampleRate: Int = TARGET_SAMPLE_RATE
    ): ShortArray {
        if (input.isEmpty() || speedFactor <= 1.005) return input

        val windowSize = (sampleRate * 0.040).roundToInt() and 0xFFFFFFFE.toInt() // 40ms even
        val halfWin = windowSize / 2
        val maxSearch = (sampleRate * 0.008).roundToInt() // +/- 8ms search
        if (input.size < windowSize * 2) return input

        val outLength = max(windowSize, (input.size / speedFactor).roundToInt())
        val outAcc = FloatArray(outLength + windowSize)
        val weightAcc = FloatArray(outLength + windowSize)

        val hann = FloatArray(windowSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (windowSize - 1)))).toFloat()
        }

        // First frame directly copied with window
        for (i in 0 until windowSize) {
            outAcc[i] += input[i] * hann[i]
            weightAcc[i] += hann[i]
        }

        var outPos = halfWin
        var naturalPrevPos = halfWin

        while (outPos + windowSize < outLength + halfWin) {
            val targetInPos = (outPos * speedFactor).roundToInt()
            if (targetInPos + windowSize >= input.size) break

            val searchStart = max(0, targetInPos - maxSearch)
            val searchEnd = min(input.size - windowSize - 1, targetInPos + maxSearch)

            var bestPos = targetInPos
            if (naturalPrevPos + halfWin < input.size && searchEnd > searchStart) {
                var bestScore = Double.NEGATIVE_INFINITY
                val step = 2
                var candidate = searchStart
                while (candidate <= searchEnd) {
                    var dot = 0.0
                    var normA = 1.0
                    var normB = 1.0
                    var k = 0
                    while (k < halfWin) {
                        val a = input[naturalPrevPos + k].toDouble()
                        val b = input[candidate + k].toDouble()
                        dot += a * b
                        normA += a * a
                        normB += b * b
                        k += 3
                    }
                    val corr = dot / kotlin.math.sqrt(normA * normB)
                    if (corr > bestScore) {
                        bestScore = corr
                        bestPos = candidate
                    }
                    candidate += step
                }
            }

            for (i in 0 until windowSize) {
                val w = hann[i]
                outAcc[outPos + i] += input[bestPos + i] * w
                weightAcc[outPos + i] += w
            }

            naturalPrevPos = bestPos + halfWin
            outPos += halfWin
        }

        val finalOut = ShortArray(outLength)
        for (i in 0 until outLength) {
            val w = weightAcc[i]
            val value = if (w > 1e-4f) outAcc[i] / w else 0f
            finalOut[i] = value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return finalOut
    }

    /**
     * Places each synchronized clip at its exact cumulative timeline start offset.
     */
    fun assembleTimeline(
        clips: List<TimelineAudioClip>,
        totalDurationSec: Double,
        sampleRate: Int = TARGET_SAMPLE_RATE
    ): ShortArray {
        val computedEndSamples = clips.maxOfOrNull {
            ((it.startSec * sampleRate).roundToInt() + it.pcmSamples.size)
        } ?: 0
        val declaredTotalSamples = (totalDurationSec * sampleRate).roundToInt()
        val totalSamples = max(computedEndSamples, declaredTotalSamples).coerceAtLeast(sampleRate / 2)

        val master = FloatArray(totalSamples)
        for (clip in clips) {
            val startSample = (clip.startSec * sampleRate).roundToInt().coerceAtLeast(0)
            val pcm = clip.pcmSamples
            val fadeLen = min(120, pcm.size / 4) // 5ms click-free fade in/out
            for (i in pcm.indices) {
                val dst = startSample + i
                if (dst >= totalSamples) break
                var gain = 1.0f
                if (fadeLen > 0) {
                    if (i < fadeLen) gain = i.toFloat() / fadeLen
                    else if (i > pcm.size - fadeLen) gain = (pcm.size - i).toFloat() / fadeLen
                }
                master[dst] += pcm[i] * gain
            }
        }

        return ShortArray(totalSamples) { idx ->
            softLimitToShort(master[idx])
        }
    }

    /**
     * Mixes background music seamlessly at 12% volume (0.12f) with fade-in/out and soft limiting.
     */
    fun mixBackgroundMusic(
        voicePcm: ShortArray,
        musicPcm: ShortArray,
        musicGain: Float = BACKGROUND_MUSIC_GAIN,
        sampleRate: Int = TARGET_SAMPLE_RATE
    ): ShortArray {
        if (musicPcm.isEmpty() || voicePcm.isEmpty()) return voicePcm
        val total = voicePcm.size
        val fadeInSamples = min((sampleRate * 0.5).roundToInt(), total / 4)
        val fadeOutSamples = min((sampleRate * 1.2).roundToInt(), total / 3)
        val out = ShortArray(total)

        for (i in 0 until total) {
            val musicSample = musicPcm[i % musicPcm.size].toFloat()
            var env = 1.0f
            if (fadeInSamples > 0 && i < fadeInSamples) {
                env = i.toFloat() / fadeInSamples
            } else if (fadeOutSamples > 0 && i > total - fadeOutSamples) {
                env = (total - i).toFloat() / fadeOutSamples
            }
            val mixed = voicePcm[i].toFloat() + (musicSample * musicGain * env)
            out[i] = softLimitToShort(mixed)
        }
        return out
    }

    fun encodeWav(
        samples: ShortArray,
        sampleRate: Int = TARGET_SAMPLE_RATE,
        channels: Int = TARGET_CHANNELS
    ): ByteArray {
        val pcmBytes = shortsToPcm16LeBytes(samples)
        val byteRate = sampleRate * channels * (BITS_PER_SAMPLE / 8)
        val blockAlign = channels * (BITS_PER_SAMPLE / 8)
        val dataSize = pcmBytes.size
        val chunkSize = 36 + dataSize

        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(chunkSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // PCM audio format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        out.write(header.array())
        out.write(pcmBytes)
        return out.toByteArray()
    }

    fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int = TARGET_SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            fos.write(encodeWav(samples, sampleRate))
        }
    }

    private fun softLimitToShort(value: Float): Short {
        val normalized = value / 32767f
        val limited = if (abs(normalized) > 0.92f) {
            tanh(normalized.toDouble()).toFloat()
        } else {
            normalized
        }
        return (limited * 32767f).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
