package com.example.data

import com.example.model.SceneItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.round

data class RawSceneData(
    val label: String,
    val narration: String,
    val durationSec: Double?
)

data class ParsedJsonProject(
    val scenes: List<RawSceneData>,
    val hasValidDurations: Boolean,
    val hasMissingNarrations: Boolean
)

object ProjectParser {

    private val arrayContainerKeys = listOf(
        "scenes", "escenas", "clips", "segments", "segmentos",
        "items", "storyboard", "shots", "timeline", "script"
    )

    private val narrationKeys = listOf(
        "narration", "narracion", "narración",
        "text", "texto",
        "voiceover", "voice_over", "voiceText", "voice_text",
        "audioText", "audio_text",
        "script", "guion", "guión",
        "dialogue", "dialogo", "diálogo",
        "caption", "subtitle", "subtitulo", "subtítulo",
        "line", "linea", "línea", "speech", "voz"
    )

    private val durationSecKeys = listOf(
        "duration", "duracion", "duración",
        "durationSec", "duration_sec", "durationSeconds", "duration_seconds",
        "seconds", "segundos", "length", "dur", "time", "tiempo"
    )

    private val durationMsKeys = listOf(
        "durationMs", "duration_ms", "lengthMs", "length_ms", "ms"
    )

    private val startKeys = listOf("start", "startTime", "start_time", "startSec", "start_sec", "inicio", "from")
    private val endKeys = listOf("end", "endTime", "end_time", "endSec", "end_sec", "fin", "to")

    private val labelKeys = listOf("label", "title", "titulo", "título", "name", "nombre", "scene", "escena", "id")

    fun parseJsonProject(rawJson: String): ParsedJsonProject {
        val trimmed = rawJson.trim().removePrefix("\uFEFF")
        require(trimmed.isNotEmpty()) { "JSON file is empty." }

        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                findSceneArray(root)
                    ?: throw IllegalArgumentException(
                        "No scenes array found (expected keys like 'scenes', 'escenas', 'clips', or a root JSON array)."
                    )
            }
            else -> throw IllegalArgumentException("Invalid JSON format.")
        }

        val parsed = mutableListOf<RawSceneData>()
        var validDurationsCount = 0
        var missingNarrationsCount = 0

        for (i in 0 until array.length()) {
            val element = array.opt(i)
            when (element) {
                is JSONObject -> {
                    val label = extractLabel(element, i + 1)
                    val narration = extractString(element, narrationKeys).trim()
                    val duration = extractDurationSec(element)

                    if (duration != null && duration > 0.0) {
                        validDurationsCount++
                    }
                    if (narration.isBlank()) {
                        missingNarrationsCount++
                    }

                    parsed.add(
                        RawSceneData(
                            label = label,
                            narration = narration,
                            durationSec = duration?.let { roundToTwoDecimals(it) }
                        )
                    )
                }
                is String -> {
                    val text = element.trim()
                    if (text.isBlank()) missingNarrationsCount++
                    parsed.add(
                        RawSceneData(
                            label = "Scene ${i + 1}",
                            narration = text,
                            durationSec = null
                        )
                    )
                }
            }
        }

        require(parsed.isNotEmpty()) { "No scenes were found inside the JSON file." }

        return ParsedJsonProject(
            scenes = parsed,
            hasValidDurations = validDurationsCount == parsed.size,
            hasMissingNarrations = missingNarrationsCount > 0
        )
    }

    fun parseTxtScript(rawTxt: String): List<String> {
        val normalized = rawTxt
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        if (normalized.isEmpty()) return emptyList()

        // Strategy 1: Explicit scene headers like "Escena 1:", "Scene 2 -", "[1]", "#1", "1."
        val headerRegex = Regex(
            pattern = """^(?:(?:escena|scene|clip|toma|shot)\s*\d+\s*[:.\-–)]*|\[?\s*\d+\s*\]\s*[:.\-–)]*|#\d+\s*[:.\-–)]*|\d+\s*[.)\-–]\s+)""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        val matches = headerRegex.findAll(normalized).toList()
        if (matches.size >= 2) {
            val sections = mutableListOf<String>()
            for (idx in matches.indices) {
                val start = matches[idx].range.last + 1
                val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else normalized.length
                val body = normalized.substring(start, end).trim()
                if (body.isNotEmpty()) {
                    sections.add(body.replace(Regex("\\s+"), " "))
                }
            }
            if (sections.isNotEmpty()) return sections
        }

        // Strategy 2: Blank-line separated paragraphs
        val paragraphs = normalized
            .split(Regex("\\n\\s*\\n+"))
            .map { block ->
                block.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .replace(headerRegex, "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
        if (paragraphs.size >= 2) return paragraphs

        // Strategy 3: Non-empty lines
        return normalized
            .lines()
            .map { it.trim().replace(headerRegex, "").trim() }
            .filter { it.isNotEmpty() }
    }

    fun buildSceneItems(
        jsonScenes: List<RawSceneData>,
        txtLines: List<String>,
        detectedDurations: List<Double>?,
        videoTotalDurationSec: Double?
    ): List<SceneItem> {
        val count = max(jsonScenes.size, if (jsonScenes.isEmpty()) txtLines.size else jsonScenes.size)
        if (count == 0) return emptyList()

        val fallbackPerScene = if (videoTotalDurationSec != null && videoTotalDurationSec > 0.5) {
            roundToTwoDecimals((videoTotalDurationSec / count).coerceAtLeast(1.0))
        } else {
            5.0
        }

        return (0 until count).map { idx ->
            val raw = jsonScenes.getOrNull(idx)
            val txt = txtLines.getOrNull(idx).orEmpty()
            val narration = when {
                !raw?.narration.isNullOrBlank() -> raw!!.narration
                txt.isNotBlank() -> txt
                else -> ""
            }
            val duration = when {
                raw?.durationSec != null && raw.durationSec > 0.0 -> raw.durationSec
                detectedDurations != null && idx < detectedDurations.size && detectedDurations[idx] > 0.0 ->
                    roundToTwoDecimals(detectedDurations[idx])
                else -> fallbackPerScene
            }
            val label = raw?.label?.takeIf { it.isNotBlank() } ?: "#${idx + 1}"
            SceneItem(
                index = idx,
                label = label,
                narration = narration,
                durationSec = duration
            )
        }
    }

    private fun findSceneArray(root: JSONObject): JSONArray? {
        for (key in arrayContainerKeys) {
            val candidate = root.optJSONArray(key)
            if (candidate != null && candidate.length() > 0) return candidate
        }
        // Fallback: search nested objects 1 level deep or first JSONArray property
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = root.optJSONArray(key)
            if (arr != null && arr.length() > 0) return arr
            val obj = root.optJSONObject(key)
            if (obj != null) {
                for (nestedKey in arrayContainerKeys) {
                    val nestedArr = obj.optJSONArray(nestedKey)
                    if (nestedArr != null && nestedArr.length() > 0) return nestedArr
                }
            }
        }
        return null
    }

    private fun extractLabel(obj: JSONObject, oneBasedIndex: Int): String {
        for (key in labelKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val raw = obj.opt(key)?.toString()?.trim().orEmpty()
                if (raw.isNotEmpty()) {
                    return if (raw.all { it.isDigit() }) "#$raw" else raw
                }
            }
        }
        return "#$oneBasedIndex"
    }

    private fun extractString(obj: JSONObject, keys: List<String>): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.optString(key, "").trim()
                if (value.isNotEmpty()) return value
            }
        }
        // Also check nested "audio" or "voice" object
        for (nestedName in listOf("audio", "voice", "narration", "content")) {
            val nested = obj.optJSONObject(nestedName)
            if (nested != null) {
                for (key in keys) {
                    val value = nested.optString(key, "").trim()
                    if (value.isNotEmpty()) return value
                }
            }
        }
        return ""
    }

    private fun extractDurationSec(obj: JSONObject): Double? {
        for (key in durationSecKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val v = parseFlexibleDouble(obj.opt(key))
                if (v != null && v > 0.0) return v
            }
        }
        for (key in durationMsKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val v = parseFlexibleDouble(obj.opt(key))
                if (v != null && v > 0.0) return v / 1000.0
            }
        }
        val start = startKeys.firstNotNullOfOrNull { k ->
            if (obj.has(k) && !obj.isNull(k)) parseFlexibleDouble(obj.opt(k)) else null
        }
        val end = endKeys.firstNotNullOfOrNull { k ->
            if (obj.has(k) && !obj.isNull(k)) parseFlexibleDouble(obj.opt(k)) else null
        }
        if (start != null && end != null && end > start) {
            return end - start
        }
        return null
    }

    private fun parseFlexibleDouble(value: Any?): Double? {
        if (value == null) return null
        if (value is Number) return value.toDouble()
        val str = value.toString().trim().lowercase()
        if (str.isEmpty()) return null
        // Handle timecode mm:ss.ms or hh:mm:ss.ms
        if (str.contains(":")) {
            val parts = str.split(":").mapNotNull { it.toDoubleOrNull() }
            return when (parts.size) {
                2 -> parts[0] * 60.0 + parts[1]
                3 -> parts[0] * 3600.0 + parts[1] * 60.0 + parts[2]
                else -> null
            }
        }
        val cleaned = str.removeSuffix("ms").removeSuffix("sec").removeSuffix("s").replace(",", ".").trim()
        val num = cleaned.toDoubleOrNull() ?: return null
        return if (str.endsWith("ms")) num / 1000.0 else num
    }

    private fun roundToTwoDecimals(value: Double): Double = round(value * 100.0) / 100.0
}
