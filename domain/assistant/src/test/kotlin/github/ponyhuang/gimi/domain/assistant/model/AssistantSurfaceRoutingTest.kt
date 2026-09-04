package github.ponyhuang.gimi.domain.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSurfaceRoutingTest {
    @Test
    fun `chat page keeps wake feedback in composer`() {
        assertEquals(
            AssistantSurfaceTarget.CHAT_COMPOSER,
            routeAssistantSurface(
                AssistantSurfaceEnvironment(
                    presentationVisible = true,
                    appForeground = true,
                    chatVisible = true,
                    deviceLocked = false,
                    overlayPermissionGranted = false,
                    lockScreenLaunchAllowed = false,
                ),
            ),
        )
    }

    @Test
    fun `foreground non chat page uses in app sheet`() {
        assertEquals(
            AssistantSurfaceTarget.IN_APP_SHEET,
            routeAssistantSurface(
                AssistantSurfaceEnvironment(
                    presentationVisible = true,
                    appForeground = true,
                    chatVisible = false,
                    deviceLocked = false,
                    overlayPermissionGranted = false,
                    lockScreenLaunchAllowed = false,
                ),
            ),
        )
    }

    @Test
    fun `background uses overlay only when permission is granted`() {
        val base = AssistantSurfaceEnvironment(
            presentationVisible = true,
            appForeground = false,
            chatVisible = false,
            deviceLocked = false,
            overlayPermissionGranted = false,
            lockScreenLaunchAllowed = false,
        )

        assertEquals(AssistantSurfaceTarget.NOTIFICATION_ONLY, routeAssistantSurface(base))
        assertEquals(
            AssistantSurfaceTarget.SYSTEM_OVERLAY,
            routeAssistantSurface(base.copy(overlayPermissionGranted = true)),
        )
    }

    @Test
    fun `lock screen activity requires an allowed background launch`() {
        val base = AssistantSurfaceEnvironment(
            presentationVisible = true,
            appForeground = false,
            chatVisible = false,
            deviceLocked = true,
            overlayPermissionGranted = true,
            lockScreenLaunchAllowed = false,
        )

        assertEquals(AssistantSurfaceTarget.NOTIFICATION_ONLY, routeAssistantSurface(base))
        assertEquals(
            AssistantSurfaceTarget.LOCK_SCREEN_ACTIVITY,
            routeAssistantSurface(base.copy(lockScreenLaunchAllowed = true)),
        )
    }

    @Test
    fun `hidden presentation has no surface`() {
        assertEquals(
            AssistantSurfaceTarget.NONE,
            routeAssistantSurface(
                AssistantSurfaceEnvironment(
                    presentationVisible = false,
                    appForeground = true,
                    chatVisible = false,
                    deviceLocked = false,
                    overlayPermissionGranted = true,
                    lockScreenLaunchAllowed = true,
                ),
            ),
        )
    }
}
