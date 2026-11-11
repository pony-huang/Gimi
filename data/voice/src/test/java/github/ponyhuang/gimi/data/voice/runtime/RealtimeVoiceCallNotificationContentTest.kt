package github.ponyhuang.gimi.data.voice.runtime

import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保护通知文案优先级、切后台提醒时机，以及高频语音状态不会持续刷新常驻通知。 */
class RealtimeVoiceCallNotificationContentTest {
    @Test
    fun endingAndFailureTakePrecedenceOverMuteAndPhase() {
        val ending = call(
            ending = true,
            voice = RealtimeVoiceState(muted = true, phase = RealtimeVoicePhase.Speaking),
        )
        assertEquals(VoiceCallNotificationText.Ending, ending.toNotificationContent(false).text)
        val failed = call(
            voice = RealtimeVoiceState(
                muted = true,
                phase = RealtimeVoicePhase.Listening,
                failure = RealtimeVoiceFailure.Recognition,
            ),
        )
        assertEquals(VoiceCallNotificationText.Failed, failed.toNotificationContent(false).text)
    }

    @Test
    fun phaseAndMuteMapToTheirOwnCopy() {
        assertEquals(VoiceCallNotificationText.Idle, text(RealtimeVoicePhase.Idle))
        assertEquals(VoiceCallNotificationText.Listening, text(RealtimeVoicePhase.Listening))
        assertEquals(VoiceCallNotificationText.Transcribing, text(RealtimeVoicePhase.Transcribing))
        assertEquals(VoiceCallNotificationText.Thinking, text(RealtimeVoicePhase.Thinking))
        assertEquals(VoiceCallNotificationText.Speaking, text(RealtimeVoicePhase.Speaking))
        assertEquals(VoiceCallNotificationText.Failed, text(RealtimeVoicePhase.Failed))
        val muted = call(voice = RealtimeVoiceState(muted = true, phase = RealtimeVoicePhase.Speaking))
        assertEquals(VoiceCallNotificationText.Muted, muted.toNotificationContent(false).text)
        assertTrue(muted.toNotificationContent(false).muted)
    }

    @Test
    fun highFrequencyVoiceUpdatesDoNotChangeTheNotificationSnapshot() {
        val first = call(
            conversationTitle = "当前聊天",
            voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, inputLevel = 0.1f),
        )
        val second = first.copy(
            voice = first.voice.copy(inputLevel = 0.9f, outputLevel = 0.4f, inputText = "你好", responseText = "回答"),
        )
        assertEquals(first.toNotificationContent(false), second.toNotificationContent(false))
        assertEquals("当前聊天", first.toNotificationContent(false).conversationTitle)
        assertFalse(first.toNotificationContent(false).backgrounded)
    }

    @Test
    fun alertFiresOnlyOnTheFirstBackgroundTransitionOfAnActiveCall() {
        val foreground = call().toNotificationContent(false)
        val background = call().toNotificationContent(true)
        assertFalse(shouldAlertOnBackground(null, foreground))
        assertTrue(shouldAlertOnBackground(foreground, background))
        assertFalse(shouldAlertOnBackground(background, background))
        assertFalse(shouldAlertOnBackground(background, foreground))
        assertFalse(shouldAlertOnBackground(foreground, call(ending = true).toNotificationContent(true)))
    }

    @Test
    fun resetCallStateStillDrivesServiceShutdown() {
        val content = RealtimeVoiceCallState().toNotificationContent(true)
        assertFalse(content.active)
        assertFalse(content.ending)
    }

    private fun call(
        ending: Boolean = false,
        conversationTitle: String = "",
        voice: RealtimeVoiceState = RealtimeVoiceState(),
    ) = RealtimeVoiceCallState(
        active = true,
        ending = ending,
        conversationTitle = conversationTitle,
        voice = voice,
    )

    private fun text(phase: RealtimeVoicePhase) =
        call(voice = RealtimeVoiceState(phase = phase)).toNotificationContent(false).text
}
