package github.ponyhuang.gimi.data.agent.tools.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolActivityResultStateTest {

    @Test
    fun `successful pick exposes primary data uri`() {
        val state = toolResultState(
            ToolActivityResult(
                resultCode = RESULT_OK,
                dataUri = "content://contacts/people/7",
                uris = listOf("content://contacts/people/7"),
            ),
        )

        assertEquals(true, state["success"])
        assertEquals(false, state["cancelled"])
        assertEquals("content://contacts/people/7", state["data"])
        assertNull(state["uris"])
    }

    @Test
    fun `multi selection keeps every uri while data stays primary`() {
        val state = toolResultState(
            ToolActivityResult(
                resultCode = RESULT_OK,
                dataUri = "content://a/1",
                uris = listOf("content://a/1", "content://a/2"),
            ),
        )

        assertEquals("content://a/1", state["data"])
        assertEquals(listOf("content://a/1", "content://a/2"), state["uris"])
    }

    @Test
    fun `user abort is a successful cancelled response`() {
        val state = toolResultState(ToolActivityResult(resultCode = RESULT_CANCELED, dataUri = null, uris = emptyList()))

        assertEquals(true, state["success"])
        assertEquals(true, state["cancelled"])
        assertNull(state["data"])
    }

    @Test
    fun `timeout is reported as cancelled so the model can continue`() {
        val state = toolResultState(null)

        assertEquals(true, state["success"])
        assertEquals(true, state["cancelled"])
        assertEquals(true, state["timedOut"])
        assertFalse(state.containsKey("data"))
    }

    @Test
    fun `ok result without data stays successful`() {
        val state = toolResultState(ToolActivityResult(resultCode = RESULT_OK, dataUri = null, uris = emptyList()))

        assertEquals(true, state["success"])
        assertEquals(false, state["cancelled"])
        assertNull(state["data"])
    }
}

private const val RESULT_OK = -1

private const val RESULT_CANCELED = 0
