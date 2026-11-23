package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.conversation.model.WebCitation
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import github.ponyhuang.gimi.domain.conversation.model.WebSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class EventMapperWebCitationTest {
    @Test fun showsStoppedReplyUntilTheSameInvocationHasAFinalReply() {
        val citation = WebCitation(WebSource("https://example.org/story", "Story"), 4)
        val stopped = Event(
            id = "stopped", author = "agent",
            content = Content(role = Role.MODEL, parts = listOf(Part(text = "Read"))),
            customMetadata = mapOf(
                WebCitationMetadata.STOPPED_REPLY_KEY to "turn",
                WebCitationMetadata.KEY to WebCitationMetadata.encode(listOf(citation)),
            ),
        )
        val final = Event(
            id = "final", invocationId = "turn", author = "agent",
            content = Content(role = Role.MODEL, parts = listOf(Part(text = "Final"))),
        )
        val session = mockk<Session>()
        every { session.events } returns mutableListOf(stopped)
        val message = EventMapper.fromSession(session).single()
        assertEquals("turn", message.invocationId)
        assertEquals(listOf(citation), message.webCitations)
        every { session.events } returns mutableListOf(stopped, final)
        assertEquals(listOf("final"), EventMapper.fromSession(session).map { it.id })
    }
}
