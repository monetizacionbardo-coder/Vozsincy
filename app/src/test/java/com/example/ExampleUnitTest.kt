package com.example

import com.example.media.TimelineAudioClip
import com.example.media.WavAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun wavEncodeAndDecodeRoundTripPreservesPcm24kMono() {
        val sampleRate = WavAudioProcessor.TARGET_SAMPLE_RATE
        val pcm = ShortArray(2400) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 16000.0).toInt().toShort()
        }
        val wavBytes = WavAudioProcessor.encodeWav(pcm, sampleRate)
        assertTrue(WavAudioProcessor.isWavHeader(wavBytes))
        val parsed = WavAudioProcessor.parseWav(wavBytes)
        assertEquals(sampleRate, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
        assertEquals(pcm.size, parsed.samples.size)
        assertEquals(pcm[500], parsed.samples[500])
    }

    @Test
    fun wsolaTimeStretchShortensClipBySpeedFactor() {
        val sampleRate = WavAudioProcessor.TARGET_SAMPLE_RATE
        // 2 seconds of 220 Hz sine wave
        val input = ShortArray(sampleRate * 2) { i ->
            (sin(2.0 * PI * 220.0 * i / sampleRate) * 14000.0).toInt().toShort()
        }
        val stretched = WavAudioProcessor.timeStretchWsola(input, 1.15, sampleRate)
        val expectedSamples = (input.size / 1.15).toInt()
        assertTrue(abs(stretched.size - expectedSamples) < 100)
    }

    @Test
    fun silencePaddingAndTimelineAssemblyPlaceClipsAtExactOffsets() {
        val sr = WavAudioProcessor.TARGET_SAMPLE_RATE
        val clip1 = WavAudioProcessor.padWithSilence(ShortArray(sr) { 4000 }, sr * 2)
        val clip2 = ShortArray(sr) { -5000 }
        val master = WavAudioProcessor.assembleTimeline(
            clips = listOf(
                TimelineAudioClip(startSec = 0.0, targetDurationSec = 2.0, pcmSamples = clip1),
                TimelineAudioClip(startSec = 2.0, targetDurationSec = 1.0, pcmSamples = clip2)
            ),
            totalDurationSec = 3.0
        )
        assertEquals(sr * 3, master.size)
        assertTrue(master[sr / 2] > 3000) // Active in clip 1
        assertEquals(0.toShort(), master[sr + sr / 2]) // Padded silence in clip 1
        assertTrue(master[sr * 2 + sr / 2] < -4000) // Active in clip 2 at exact 2.0s offset
    }

    @Test
    fun backgroundMusicMixesAtTwelvePercentWithoutClipping() {
        val voice = ShortArray(24_000) { 10_000 }
        val music = ShortArray(12_000) { 10_000 }
        val mixed = WavAudioProcessor.mixBackgroundMusic(voice, music, 0.12f)
        assertEquals(voice.size, mixed.size)
        // Middle sample (past fade-in, before fade-out) should be ~10000 + 1200 = 11200
        assertTrue(abs(mixed[10_000] - 11_200) <= 5)
    }
}
