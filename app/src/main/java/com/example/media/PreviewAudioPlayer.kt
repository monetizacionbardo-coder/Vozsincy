package com.example.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "PreviewAudioPlayer"

class PreviewAudioPlayer(private val scope: CoroutineScope) {
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    fun playPcm24k(samples: ShortArray, onCompleted: () -> Unit = {}) {
        stop()
        playJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    WavAudioProcessor.TARGET_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(WavAudioProcessor.TARGET_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                var offset = 0
                val chunk = 2048
                while (isActive && offset < samples.size) {
                    val count = minOf(chunk, samples.size - offset)
                    val written = track.write(samples, offset, count)
                    if (written <= 0) break
                    offset += written
                }

                // Wait briefly for hardware buffer drain if not cancelled
                if (isActive) {
                    runCatching { track.stop() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio preview playback failed", e)
            } finally {
                runCatching { track?.release() }
                if (audioTrack === track) audioTrack = null
                onCompleted()
            }
        }
    }

    fun playWavFile(wavPath: String, onCompleted: () -> Unit = {}) {
        stop()
        playJob = scope.launch(Dispatchers.IO) {
            try {
                val file = File(wavPath)
                if (!file.exists()) {
                    onCompleted()
                    return@launch
                }
                val parsed = WavAudioProcessor.parseWav(file.readBytes())
                val mono = WavAudioProcessor.downmixToMono(parsed.samples, parsed.channels)
                val pcm24k = WavAudioProcessor.resampleLinear(
                    mono,
                    parsed.sampleRate,
                    WavAudioProcessor.TARGET_SAMPLE_RATE
                )
                playPcm24k(pcm24k, onCompleted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play WAV file $wavPath", e)
                onCompleted()
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }
}
