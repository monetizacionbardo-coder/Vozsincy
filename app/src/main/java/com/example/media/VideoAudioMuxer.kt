package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

private const val TAG = "VideoAudioMuxer"

object VideoAudioMuxer {

    /**
     * Combines the original video stream WITHOUT re-encoding (packet stream copy, -c:v copy)
     * with the synchronized 24 kHz mono 16-bit PCM track encoded to AAC into an MP4 file.
     */
    suspend fun muxVideoWithMasterPcm(
        context: Context,
        videoUri: Uri,
        masterPcm24kMono: ShortArray,
        outputMp4File: File
    ): Boolean = withContext(Dispatchers.IO) {
        outputMp4File.parentFile?.mkdirs()
        if (outputMp4File.exists()) outputMp4File.delete()

        val videoExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var audioEncoder: MediaCodec? = null
        var muxerStarted = false

        try {
            videoExtractor.setDataSource(context, videoUri, null)
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = fmt
                    break
                }
            }
            if (videoTrackIndex < 0 || videoFormat == null) {
                Log.e(TAG, "No video track found in input file.")
                return@withContext false
            }

            videoExtractor.selectTrack(videoTrackIndex)

            // Configure AAC encoder for master audio (24 kHz mono -> 128 kbps AAC-LC)
            val audioSampleRate = WavAudioProcessor.TARGET_SAMPLE_RATE
            val audioChannels = WavAudioProcessor.TARGET_CHANNELS
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                audioSampleRate,
                audioChannels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // First encode all audio frames in memory (small & fast: ~16 KB/sec) so we have final AAC outputFormat
            val pcmBytes = WavAudioProcessor.shortsToPcm16LeBytes(masterPcm24kMono)
            val encodedAudioPackets = mutableListOf<EncodedPacket>()
            var encodedAudioFormat: MediaFormat? = null

            val bufferInfo = MediaCodec.BufferInfo()
            var byteOffset = 0
            var totalSamplesFed = 0L
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = audioEncoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (byteOffset >= pcmBytes.size) {
                            val ptsUs = (totalSamplesFed * 1_000_000L) / audioSampleRate
                            audioEncoder.queueInputBuffer(
                                inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val chunkLen = minOf(inBuf.remaining(), pcmBytes.size - byteOffset)
                            inBuf.put(pcmBytes, byteOffset, chunkLen)
                            val ptsUs = (totalSamplesFed * 1_000_000L) / audioSampleRate
                            audioEncoder.queueInputBuffer(inIdx, 0, chunkLen, ptsUs, 0)
                            byteOffset += chunkLen
                            totalSamplesFed += (chunkLen / 2)
                        }
                    }
                }

                val outIdx = audioEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        encodedAudioFormat = audioEncoder.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = audioEncoder.getOutputBuffer(outIdx)!!
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val data = ByteArray(bufferInfo.size)
                            outBuf.get(data)
                            encodedAudioPackets.add(
                                EncodedPacket(
                                    data = data,
                                    ptsUs = bufferInfo.presentationTimeUs,
                                    flags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                                )
                            )
                        }
                        audioEncoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (encodedAudioFormat == null) {
                Log.e(TAG, "AAC encoder did not produce an output format.")
                return@withContext false
            }

            muxer = MediaMuxer(outputMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Preserve source video rotation if present
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            }

            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = muxer.addTrack(encodedAudioFormat)
            muxer.start()
            muxerStarted = true

            // Stream-copy original video samples verbatim (-c:v copy)
            val maxVideoBufSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(512 * 1024)
            } else {
                2 * 1024 * 1024
            }
            val videoBuf = ByteBuffer.allocate(maxVideoBufSize)
            val videoInfo = MediaCodec.BufferInfo()
            var lastVideoPts = -1L

            while (true) {
                videoBuf.clear()
                val sampleSize = videoExtractor.readSampleData(videoBuf, 0)
                if (sampleSize < 0) break

                val sampleTime = videoExtractor.sampleTime.coerceAtLeast(0L)
                val monotonicPts = if (sampleTime <= lastVideoPts) lastVideoPts + 1L else sampleTime
                lastVideoPts = monotonicPts

                videoInfo.offset = 0
                videoInfo.size = sampleSize
                videoInfo.presentationTimeUs = monotonicPts
                videoInfo.flags = extractorFlagsToCodecFlags(videoExtractor.sampleFlags)
                muxer.writeSampleData(muxVideoTrack, videoBuf, videoInfo)
                videoExtractor.advance()
            }

            // Write synchronized AAC audio samples
            val audioInfo = MediaCodec.BufferInfo()
            var lastAudioPts = -1L
            for (pkt in encodedAudioPackets) {
                val pts = if (pkt.ptsUs <= lastAudioPts) lastAudioPts + 1L else pkt.ptsUs
                lastAudioPts = pts
                audioInfo.offset = 0
                audioInfo.size = pkt.data.size
                audioInfo.presentationTimeUs = pts
                audioInfo.flags = pkt.flags
                muxer.writeSampleData(muxAudioTrack, ByteBuffer.wrap(pkt.data), audioInfo)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "MP4 stream-copy muxing failed", e)
            runCatching { if (outputMp4File.exists()) outputMp4File.delete() }
            false
        } finally {
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            runCatching {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            }
            runCatching { videoExtractor.release() }
        }
    }

    private fun extractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var flags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return flags
    }

    private data class EncodedPacket(
        val data: ByteArray,
        val ptsUs: Long,
        val flags: Int
    )
}
