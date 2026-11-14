@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess.shared

import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Role
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 所有支持应用文字输入/音频输出模式的适配器共用的官方连接契约。
 * 子类应使用本地假传输，让每个完整文字轮返回音频并结束；禁止调用真实模型。
 */
abstract class LiveConnectionContractTest {
    protected abstract suspend fun openConnection(): LiveConnection

    @Test
    fun repeatedTurnCollectionsKeepOneConnectionOpenUntilExplicitClose() = runTest {
        val connection = openConnection()
        try {
            connection.sendHistory(emptyList())
            repeat(2) { index ->
                connection.sendContent(Content.fromText(Role.USER, "第"), partial = true)
                connection.sendContent(Content.fromText(Role.USER, "$index 轮"))
                val responses = connection.receive().toList()
                assertTrue(responses.last().turnComplete == true)
                assertTrue(responses.any { response -> response.content?.parts.orEmpty().any { it.inlineData != null } })
            }
        } finally {
            connection.closeSession()
        }
        assertTrue(connection.receive().toList().isEmpty())
        connection.closeSession()
    }

    @Test
    fun closeUnblocksWaitingReceiverAndConcurrentReceiversAreRejected() = runTest {
        val connection = openConnection()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { connection.receive().toList() }
        try {
            connection.receive().toList()
            fail("Expected single receiver enforcement")
        } catch (_: IllegalStateException) {
            // 并发接收必须失败，不能让两名消费者分走同一条连接的帧。
        } finally {
            connection.closeSession()
        }
        assertTrue(waiting.await().isEmpty())
    }
}
