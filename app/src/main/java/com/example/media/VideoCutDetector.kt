package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.AudioFileInfo
import com.example.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

private const val TAG = "VideoCutDetector"

object VideoCutDetector {

    fun queryDisplayName(context: Context, uri: Uri, fallback: String): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun inspectVideo(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mime = context.contentResolver.getType(uri)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: "video/mp4"
            val name = queryDisplayName(context, uri, "video.mp4")

            VideoInfo(
                uriString = uri.toString(),
                displayName = name,
                durationSec = round(durationMs / 10.0) / 100.0,
                width = width,
                height = height,
                mimeType = mime
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun inspectAudio(context: Context, uri: Uri): AudioFileInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val name = queryDisplayName(context, uri, "music.mp3")
            AudioFileInfo(
                uriString = uri.toString(),
                displayName = name,
                durationSec = round(durationMs / 10.0) / 100.0
            )
        } catch (_: Exception) {
            AudioFileInfo(
                uriString = uri.toString(),
                displayName = queryDisplayName(context, uri, "music.mp3"),
                durationSec = 0.0
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Compares downscaled video frames to detect scene cut timestamps and compute per-scene durations.
     * Returns null if cuts cannot be reliably detected (caller falls back to editable table).
     */
    suspend fun detectSceneDurations(
        context: Context,
        uri: Uri,
        expectedSceneCount: Int?,
        onProgress: (Float) -> Unit
    ): List<Double>? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@withContext null
            val totalSec = durationMs / 1000.0
            if (totalSec < 1.5) return@withContext null

            // Sample up to 90 frames evenly across the video (0.3s to 1.0s step depending on length)
            val targetSamples = min(90, max(16, (totalSec * 2.0).toInt()))
            val stepMs = (durationMs / targetSamples).coerceAtLeast(250L)

            val timestampsSec = mutableListOf<Double>()
            val diffs = mutableListOf<Double>()

            var prevSignature: IntArray? = null
            var prevHist: FloatArray? = null

            for (i in 0 until targetSamples) {
                coroutineContext.ensureActive()
                val timeUs = (i * stepMs) * 1000L
                val frame = extractSmallFrame(retriever, timeUs)
                if (frame != null) {
                    val (sig, hist) = computeFrameSignature(frame)
                    frame.recycle()

                    if (prevSignature != null && prevHist != null) {
                        val madDiff = computeMad(prevSignature, sig)
                        val histDiff = computeHistDiff(prevHist, hist)
                        val combinedScore = madDiff * 0.65 + histDiff * 0.35
                        timestampsSec.add((i * stepMs) / 1000.0)
                        diffs.add(combinedScore)
                    }
                    prevSignature = sig
                    prevHist = hist
                }
                onProgress((i + 1).toFloat() / targetSamples.toFloat())
            }

            if (diffs.size < 4) return@withContext null

            val mean = diffs.average()
            val variance = diffs.map { (it - mean) * (it - mean) }.average()
            val stdDev = sqrt(variance)
            val adaptiveThreshold = max(0.18, mean + 1.65 * stdDev)
            val minGapSec = 1.0

            // Find local maxima above floor
            val candidates = mutableListOf<Pair<Double, Double>>() // (timeSec, score)
            for (i in diffs.indices) {
                val score = diffs[i]
                val left = if (i > 0) diffs[i - 1] else 0.0
                val right = if (i < diffs.lastIndex) diffs[i + 1] else 0.0
                if (score >= left && score >= right && score >= 0.14) {
                    candidates.add(timestampsSec[i] to score)
                }
            }

            val selectedCuts: List<Double> = if (expectedSceneCount != null && expectedSceneCount > 1) {
                val neededCuts = expectedSceneCount - 1
                val strong = mutableListOf<Double>()
                for ((t, score) in candidates.sortedByDescending { it.second }) {
                    if (strong.size >= neededCuts) break
                    if (t > 0.6 && t < totalSec - 0.6 && strong.none { abs(it - t) < minGapSec }) {
                        if (score >= max(0.16, mean + 0.8 * stdDev)) {
                            strong.add(t)
                        }
                    }
                }
                if (strong.size == neededCuts) {
                    strong.sorted()
                } else {
                    // Not enough reliable cuts to match scene count cleanly
                    return@withContext null
                }
            } else {
                val strong = mutableListOf<Double>()
                for ((t, score) in candidates.sortedByDescending { it.second }) {
                    if (score >= adaptiveThreshold && t > 0.6 && t < totalSec - 0.6) {
                        if (strong.none { abs(it - t) < minGapSec }) {
                            strong.add(t)
                        }
                    }
                }
                if (strong.isEmpty()) return@withContext null
                strong.sorted()
            }

            val boundaries = listOf(0.0) + selectedCuts + listOf(totalSec)
            val durations = (0 until boundaries.lastIndex).map { idx ->
                val dur = (boundaries[idx + 1] - boundaries[idx]).coerceAtLeast(0.5)
                round(dur * 100.0) / 100.0
            }

            durations.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Frame-based scene cut detection failed", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSmallFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    48,
                    27
                )
            } else {
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val scaled = Bitmap.createScaledBitmap(raw, 48, 27, false)
                    if (scaled !== raw) raw.recycle()
                    scaled
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeFrameSignature(bitmap: Bitmap): Pair<IntArray, FloatArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = IntArray(pixels.size)
        val hist = FloatArray(16)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val y = (r * 30 + g * 59 + b * 11) / 100
            luma[i] = y
            val bin = (y shr 4).coerceIn(0, 15)
            hist[bin] += 1f
        }

        val inv = 1f / max(1, pixels.size)
        for (b in hist.indices) hist[b] *= inv
        return luma to hist
    }

    private fun computeMad(a: IntArray, b: IntArray): Double {
        val len = min(a.size, b.size)
        if (len == 0) return 0.0
        var sum = 0L
        for (i in 0 until len) {
            sum += abs(a[i] - b[i])
        }
        return sum.toDouble() / (len * 255.0)
    }

    private fun computeHistDiff(a: FloatArray, b: FloatArray): Double {
        var diff = 0.0
        for (i in a.indices) {
            diff += abs(a[i] - b[i])
        }
        return (diff / 2.0).coerceIn(0.0, 1.0)
    }
}
