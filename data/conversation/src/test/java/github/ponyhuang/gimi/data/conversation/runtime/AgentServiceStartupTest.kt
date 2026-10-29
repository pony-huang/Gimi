package github.ponyhuang.gimi.data.conversation.runtime

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 验证快速结束的任务不会在服务收到启动回调前撤销前台服务请求。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AgentServiceStartupTest {
    @Test
    fun releasingBeforeServiceCallbackDoesNotStopPendingForegroundStart() = runTest {
        var starts = 0
        var stops = 0
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startForegroundService(service: Intent): ComponentName {
                starts++
                return ComponentName(this, AgentExecutionService::class.java)
            }

            override fun stopService(service: Intent): Boolean {
                stops++
                return true
            }
        }
        val gate = InMemoryAgentRuntimeGate(context)

        repeat(2) {
            gate.acquire(AgentTaskSource.CHAT).release()
        }

        assertEquals(2, starts)
        assertEquals("Service must acknowledge pending foreground starts before stopping itself", 0, stops)
    }
}
