package github.ponyhuang.gimi.domain.mobileuse

import org.junit.Assert.*
import org.junit.Test

class MobileDisplaySessionsTest {
    private val sessions = MobileDisplaySessions()

    private fun open(owner: String = "turn1", chat: String = "chat1", id: String = "screen1") {
        sessions.register(owner, chat)
        assertEquals(MobileExecutionAccess.ALLOWED, sessions.claim(owner))
        sessions.open(owner, id, 10, 1080, 2400)
    }

    @Test fun finishingRetainsTheDisplayAndTheNextTurnInSameChatReusesIt() {
        open()
        val screen = sessions.session.value
        assertTrue(sessions.finish("turn1"))
        assertEquals(screen?.copy(completionVersion = 1), sessions.session.value)
        sessions.register("turn2", "chat1")
        assertEquals(MobileExecutionAccess.ALLOWED, sessions.claim("turn2"))
        assertEquals("screen1", sessions.session.value?.id)
    }

    @Test fun anotherChatMustCloseRetainedDisplayEvenAfterExecutionFinishes() {
        open()
        sessions.finish("turn1")
        sessions.register("turn2", "chat2")
        assertEquals(MobileExecutionAccess.OTHER_CHAT, sessions.claim("turn2"))
    }

    @Test fun closingBlocksCurrentOwnerIncludingResumeButAllowsNewTurn() {
        open()
        assertTrue(sessions.close("screen1"))
        assertNull(sessions.session.value)
        assertEquals(MobileExecutionAccess.CLOSED, sessions.claim("turn1"))
        sessions.finish("turn1")
        sessions.register("turn1", "chat1")
        assertEquals(MobileExecutionAccess.CLOSED, sessions.claim("turn1"))
        open("turn2", id = "screen2")
    }

    @Test fun staleCloseFinishAndGeometryCallbacksCannotAffectReplacementSession() {
        open()
        sessions.close("screen1")
        open("turn2", id = "screen2")
        assertFalse(sessions.close("screen1"))
        assertFalse(sessions.finish("turn1"))
        sessions.update("screen1", 1, 1)
        assertEquals(1080, sessions.session.value?.width)
        sessions.register("turn3", "chat1")
        assertEquals(MobileExecutionAccess.BUSY, sessions.claim("turn3"))
    }

    @Test fun connectionLossClearsDisplayAndBlocksAutomaticRecreationByCurrentOwner() {
        open()
        sessions.clear(blockOwner = true)
        assertNull(sessions.session.value)
        assertEquals(MobileExecutionAccess.CLOSED, sessions.claim("turn1"))
    }

    @Test fun closingAlsoBlocksAStartedTurnThatHasNotUsedMobileToolsYet() {
        open()
        sessions.finish("turn1")
        sessions.register("turn2", "chat1")
        sessions.close("screen1")
        assertEquals(MobileExecutionAccess.CLOSED, sessions.claim("turn2"))
        sessions.finish("turn2")
        sessions.register("turn3", "chat1")
        assertEquals(MobileExecutionAccess.ALLOWED, sessions.claim("turn3"))
    }

    @Test fun closingAfterFlowCompletionStillBlocksResumingTheOldExecution() {
        open()
        sessions.finish("turn1")
        sessions.close("screen1")
        sessions.register("turn1", "chat1")
        assertEquals(MobileExecutionAccess.CLOSED, sessions.claim("turn1"))
        sessions.register("turn2", "chat1")
        assertEquals(MobileExecutionAccess.ALLOWED, sessions.claim("turn2"))
    }
    @Test fun onlyActiveExecutionCompletionRequestsWindowDismissal() {
        open()
        sessions.register("other", "chat2")
        assertFalse(sessions.finish("other"))
        assertEquals(0L, sessions.session.value?.completionVersion)
        assertTrue(sessions.finish("turn1"))
        assertEquals(1L, sessions.session.value?.completionVersion)
        assertFalse(sessions.finish("turn1"))
        assertEquals(1L, sessions.session.value?.completionVersion)
        sessions.update("screen1", 2400, 1080)
        assertEquals(1L, sessions.session.value?.completionVersion)
        sessions.register("turn2", "chat1")
        assertEquals(MobileExecutionAccess.ALLOWED, sessions.claim("turn2"))
        assertTrue(sessions.finish("turn2"))
        assertEquals(2L, sessions.session.value?.completionVersion)
    }

}
