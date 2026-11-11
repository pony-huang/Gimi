package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneResult
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 占位后端只回报能力缺失；实装替换后本测试应随真实行为一起重写。 */
class PlaceholderDirectPhoneControllerTest {
    private val controller = PlaceholderDirectPhoneController()

    @Test
    fun reportsUnsupportedAvailability() {
        assertEquals(MobileUseAvailability.UNSUPPORTED, controller.availability())
    }

    @Test
    fun executeAlwaysReturnsTerminalNotImplementedRegardlessOfInput() = runBlocking {
        val first = controller.execute("owner-a", "chat-1", "Open the camera")
        val second = controller.execute("owner-b", "chat-2", "")

        assertEquals(DirectPhoneResult.STATUS_NOT_IMPLEMENTED, first.status)
        // 占位不持有状态：不同 owner / 会话 / 指令都得到同一终局结果。
        assertEquals(first, second)
    }

    @Test
    fun finishExecutionKeepsBackendUnsupported() = runBlocking {
        controller.finishExecution("owner")

        assertEquals(MobileUseAvailability.UNSUPPORTED, controller.availability())
        assertEquals(
            DirectPhoneResult.STATUS_NOT_IMPLEMENTED,
            controller.execute("owner", "chat-1", "Open the camera").status,
        )
    }
}
