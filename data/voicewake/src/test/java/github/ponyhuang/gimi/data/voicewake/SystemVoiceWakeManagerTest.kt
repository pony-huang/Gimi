package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.speech.model.WakePhraseMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemVoiceWakeManagerTest {
    @Test
    fun startsOnlyWhenEnabledForegroundAvailableAndPermitted() = runTest {
        val recognizer = FakeVoiceWakeRecognizer(isAvailable = true)
        val manager = manager(recognizer = recognizer, permission = { true })

        manager.setEnabled(true)
        assertEquals(0, recognizer.starts)

        manager.setForeground(true)

        assertEquals(1, recognizer.starts)
        assertTrue(manager.isListening.value)
    }

    @Test
    fun unavailableOrUnpermittedRecognizerNeverStarts() = runTest {
        val unavailable = FakeVoiceWakeRecognizer(isAvailable = false)
        manager(recognizer = unavailable).apply {
            setEnabled(true)
            setForeground(true)
        }
        val unpermitted = FakeVoiceWakeRecognizer(isAvailable = true)
        manager(recognizer = unpermitted, permission = { false }).apply {
            setEnabled(true)
            setForeground(true)
        }

        assertEquals(0, unavailable.starts)
        assertEquals(0, unpermitted.starts)
    }

    @Test
    fun finalTranscriptDispatchesStrippedCommand() = runTest {
        val recognizer = FakeVoiceWakeRecognizer(isAvailable = true)
        val commands = mutableListOf<WakePhraseMatch>()
        val manager = manager(recognizer = recognizer, onCommand = { commands += it })
        manager.setEnabled(true)
        manager.setForeground(true)

        recognizer.emit(VoiceWakeRecognitionEvent.Transcript("吉米，打开地图", isFinal = false))
        recognizer.emit(VoiceWakeRecognitionEvent.Transcript("吉米，打开地图", isFinal = true))
        advanceUntilIdle()

        assertEquals(listOf(WakePhraseMatch("吉米", "打开地图")), commands)
    }

    @Test
    fun callbackFromStoppedSessionCannotDispatch() = runTest {
        val recognizer = FakeVoiceWakeRecognizer(isAvailable = true)
        val commands = mutableListOf<WakePhraseMatch>()
        val manager = manager(recognizer = recognizer, onCommand = { commands += it })
        manager.setEnabled(true)
        manager.setForeground(true)
        val retired = recognizer.listener

        manager.setForeground(false)
        retired?.invoke(VoiceWakeRecognitionEvent.Transcript("吉米，打开地图", isFinal = true))
        advanceUntilIdle()

        assertTrue(commands.isEmpty())
        assertFalse(manager.isListening.value)
        assertEquals(1, recognizer.stops)
    }

    @Test
    fun recoverableRecognitionErrorStartsAFreshSession() = runTest {
        val recognizer = FakeVoiceWakeRecognizer(isAvailable = true)
        val manager = manager(recognizer = recognizer)
        manager.setEnabled(true)
        manager.setForeground(true)

        recognizer.emit(VoiceWakeRecognitionEvent.Error(retryable = true))
        advanceUntilIdle()

        assertEquals(2, recognizer.starts)
        assertTrue(manager.isListening.value)
    }

    private fun TestScope.manager(
        recognizer: FakeVoiceWakeRecognizer,
        permission: () -> Boolean = { true },
        onCommand: suspend (WakePhraseMatch) -> Unit = {},
    ) = SystemVoiceWakeManager(
        scope = TestScope(StandardTestDispatcher(testScheduler)),
        recognizer = recognizer,
        initialTriggerWords = listOf("吉米"),
        hasRecordAudioPermission = permission,
        restartDelayMs = 0,
        onCommand = onCommand,
    )

    private class FakeVoiceWakeRecognizer(
        override val isAvailable: Boolean,
    ) : VoiceWakeRecognizer {
        var starts = 0
        var stops = 0
        var listener: ((VoiceWakeRecognitionEvent) -> Unit)? = null

        override fun start(onEvent: (VoiceWakeRecognitionEvent) -> Unit) {
            starts += 1
            listener = onEvent
            onEvent(VoiceWakeRecognitionEvent.Ready)
        }

        override fun stop() {
            stops += 1
            listener = null
        }

        fun emit(event: VoiceWakeRecognitionEvent) {
            listener?.invoke(event)
        }
    }
}
