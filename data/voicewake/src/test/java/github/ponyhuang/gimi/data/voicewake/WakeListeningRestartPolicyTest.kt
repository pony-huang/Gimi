package github.ponyhuang.gimi.data.voicewake

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeListeningRestartPolicyTest {
    @Test
    fun sameRouteWithActiveDetectorResumesCaptureWithoutRecreatingWakeSession() {
        val action = WakeListeningRestartPolicy.decide(
            currentRouteId = "bluetooth:12",
            availableRouteId = "bluetooth:12",
            hasActiveDetector = true,
        )

        assertEquals(WakeListeningRestartAction.ResumeCapture, action)
    }

    @Test
    fun changedRouteRecreatesWakeSession() {
        val action = WakeListeningRestartPolicy.decide(
            currentRouteId = "bluetooth:12",
            availableRouteId = "bluetooth:42",
            hasActiveDetector = true,
        )

        assertEquals(WakeListeningRestartAction.RecreateSession, action)
    }
}
