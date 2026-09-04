package github.ponyhuang.gimi.data.voicewake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTriggerPolicyTest {

    @Test
    fun currentChatBlocksWakeTrigger() {
        assertFalse(
            WakeTriggerPolicy.canTrigger(
                status = BluetoothVoiceStatus.Listening,
                currentChatVisible = true,
                cooldownElapsed = true,
            ),
        )
    }

    @Test
    fun backgroundListeningAllowsWakeTriggerAfterCooldown() {
        assertTrue(
            WakeTriggerPolicy.canTrigger(
                status = BluetoothVoiceStatus.Listening,
                currentChatVisible = false,
                cooldownElapsed = true,
            ),
        )
    }
}
