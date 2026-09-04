package github.ponyhuang.gimi.data.voicewake

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDrainPolicyTest {
    @Test
    fun drainDeadlineIncludesAudioDurationAndRecoveryMargin() {
        assertEquals(
            12_000L,
            playbackDrainTimeoutMs(frameCount = 240_000L, sampleRate = 24_000),
        )
    }

    @Test
    fun drainDeadlineKeepsMinimumRecoveryWindowForShortAudio() {
        assertEquals(
            3_000L,
            playbackDrainTimeoutMs(frameCount = 2_400L, sampleRate = 24_000),
        )
    }

    @Test
    fun playbackDeadlineIsBoundedForShortAndLongResponses() {
        assertEquals(30_000L, speechPlaybackTimeoutMs(textLength = 10))
        assertEquals(60_000L, speechPlaybackTimeoutMs(textLength = 100))
        assertEquals(300_000L, speechPlaybackTimeoutMs(textLength = 10_000))
    }
}
