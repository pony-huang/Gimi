package github.ponyhuang.gimi.data.conversation.local

import androidx.room.Room
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity.CHATBOT_USER_ID
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity.DEFAULT_USER_ID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 分区隔离是 Chatbot/助手共用元数据库的前提：激活与单行操作都不得跨分区。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationMetadataDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        ConversationMetadataDatabase::class.java,
    ).build()
    private val dao = database.conversationMetadataDao()

    @After fun close() = database.close()

    @Test fun `activation in one partition keeps the other partition's pointer`() = runBlocking {
        dao.activate(DEFAULT_USER_ID, "assistant-1", defaultModel = "")
        dao.activate(CHATBOT_USER_ID, "chatbot-1", defaultModel = "")

        assertEquals("assistant-1", dao.getLast(DEFAULT_USER_ID)!!.sessionId)
        assertEquals("chatbot-1", dao.getLast(CHATBOT_USER_ID)!!.sessionId)
    }

    @Test fun `re-activation clears only its own partition`() = runBlocking {
        dao.activate(DEFAULT_USER_ID, "assistant-1", defaultModel = "")
        dao.activate(CHATBOT_USER_ID, "chatbot-1", defaultModel = "")

        dao.activate(DEFAULT_USER_ID, "assistant-2", defaultModel = "")

        assertEquals("assistant-2", dao.getLast(DEFAULT_USER_ID)!!.sessionId)
        assertEquals("chatbot-1", dao.getLast(CHATBOT_USER_ID)!!.sessionId)
    }

    @Test fun `single row operations cannot cross partitions`() = runBlocking {
        dao.activate(DEFAULT_USER_ID, "assistant-1", defaultModel = "model-a")

        assertNull(dao.get(CHATBOT_USER_ID, "assistant-1"))
        assertEquals(emptyList<String>(), dao.getAll(CHATBOT_USER_ID).map { it.sessionId })

        dao.delete(CHATBOT_USER_ID, "assistant-1")
        assertEquals("assistant-1", dao.getLast(DEFAULT_USER_ID)!!.sessionId)
    }
}
