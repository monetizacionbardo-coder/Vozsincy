package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProjectParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VozSync", appName)
    }

    @Test
    fun `parse json project and fill missing narration from txt`() {
        val json = """
            {
              "escenas": [
                { "escena": 1, "duracion": 4.5, "narracion": "" },
                { "escena": 2, "start": 4.5, "end": 10.0 }
              ]
            }
        """.trimIndent()
        val txt = """
            Escena 1: Bienvenidos a este recorrido visual.
            Escena 2: Cada corte se sincroniza con precisión.
        """.trimIndent()

        val parsedJson = ProjectParser.parseJsonProject(json)
        assertTrue(parsedJson.hasValidDurations)
        assertTrue(parsedJson.hasMissingNarrations)

        val txtLines = ProjectParser.parseTxtScript(txt)
        assertEquals(2, txtLines.size)

        val merged = ProjectParser.buildSceneItems(
            jsonScenes = parsedJson.scenes,
            txtLines = txtLines,
            detectedDurations = null,
            videoTotalDurationSec = null
        )
        assertEquals(2, merged.size)
        assertEquals(4.5, merged[0].durationSec, 0.01)
        assertEquals(5.5, merged[1].durationSec, 0.01)
        assertEquals("Bienvenidos a este recorrido visual.", merged[0].narration)
        assertEquals("Cada corte se sincroniza con precisión.", merged[1].narration)
    }

    @Test
    fun `parse json without durations flags missing durations`() {
        val json = """[ { "text": "Hello world" }, { "text": "Second scene" } ]"""
        val parsed = ProjectParser.parseJsonProject(json)
        assertFalse(parsed.hasValidDurations)
        assertFalse(parsed.hasMissingNarrations)
        assertEquals(2, parsed.scenes.size)
    }
}
