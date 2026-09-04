package github.ponyhuang.gimi.domain.assistant.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSessionStateTest {
    @Test
    fun activeTaskDependsOnExecutionOwnershipNotOverlayVisibility() {
        assertTrue(
            AssistantSessionState(
                taskActive = true,
                presentationVisible = false,
            ).hasActiveTask,
        )
        assertFalse(
            AssistantSessionState(
                taskActive = false,
                presentationVisible = true,
            ).hasActiveTask,
        )
    }
}
