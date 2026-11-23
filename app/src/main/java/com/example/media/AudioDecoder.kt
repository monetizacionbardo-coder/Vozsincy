package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "AudioDecoder"

object AudioDecoder {

    /**
     * Decodes any Android-supported audio file (WAV, MP3, M4A, AAC, OGG) into 24 kHz mono 16-bit PCM.
     */
    suspend fun decodeTo24kMonoPcm(context: Context, uri: Uri): ShortArray = withContext(Dispatchers.IO) {
        // Fast path for direct WAV files
        val rawHead = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(12)
            val read = stream.read(buf)
            if (read == 12) buf else null
        }
        if (rawHead != null && rawHead[0] == 'R'.code.toByte() && rawHead[8] == 'W'.code.toByte()) {
            val allBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (allBytes != null && WavAudioProcessor.isWavHeader(allBytes)) {
                val parsed = WavAudioProcessor.parseWav(allBytes)
                val mono = WavAudioProcessor.downmixToMono(parsed.samples, parsed.channels)
                return@withContext WavAudioProcessor.resampleLinear(
                    mono,
                    parsed.sampleRate,
                    WavAudioProcessor.TARGET_SAMPLE_RATE
                )
            }
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) {
                throw IllegalArgumentException("No audio track found in background music file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            // Limit decoding to 15 minutes of music max to safeguard memory
            val maxBytes = 15 * 60 * 48_000 * 2 * 2

            while (!sawOutputEos && pcmOut.size() < maxBytes) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outBuffer = codec.getOutputBuffer(outIndex)!!
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuffer.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }

            val decodedShorts = WavAudioProcessor.pcm16LeBytesToShorts(pcmOut.toByteArray())
            val mono = WavAudioProcessor.downmixToMono(decodedShorts, channels)
            WavAudioProcessor.resampleLinear(mono, sampleRate, WavAudioProcessor.TARGET_SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode background audio", e)
            throw e
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
