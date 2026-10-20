package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.model.processor.A2uiJsonMessageParser
import androidx.a2ui.model.processor.A2uiJsonReader
import androidx.a2ui.model.protocol.A2uiDataPath
import github.ponyhuang.gimi.data.a2ui.AndroidxA2uiProtocol
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import github.ponyhuang.gimi.domain.a2ui.A2uiInteraction
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

/** 提交重放的生命周期边界：仍在显示的组件不能失去必填绑定值。 */
class ChatA2uiSubmissionTest {
    @Test
    fun submissionKeepsDisplayedSurfaceAndRequiredBindingsAlive() = runBlocking {
        // JVM 没有 Android JsonReader；只替换 reader，保留官方解析器与真实运行时。
        mockkStatic("androidx.a2ui.compose.runtime.A2uiMessageParserKt")
        every { A2uiMessageParser() } returns A2uiJsonMessageParser { raw ->
            Class.forName("github.ponyhuang.gimi.data.a2ui.ProtocolJsonReader")
                .getDeclaredConstructor(String::class.java).apply { isAccessible = true }
                .newInstance(raw) as A2uiJsonReader
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val visible = AtomicReference<A2uiSurfaceModel>()
        val coordinator = ChatA2uiCoordinator(scope, AndroidxA2uiProtocol(), { true }, { _, _ -> }, {},
            { _, surfaces, _ -> surfaces["surface"]?.let(visible::set) })
        val document = A2uiDocument("surface", listOf(
            """{"version":"v0.9.1","createSurface":{"surfaceId":"surface","catalogId":"${coordinator.capabilities.catalogId}","sendDataModel":true}}""",
            """{"version":"v0.9.1","updateDataModel":{"surfaceId":"surface","path":"/","value":{"checked":false}}}""",
            """{"version":"v0.9.1","updateComponents":{"surfaceId":"surface","components":[{"id":"root","component":"CheckBox","label":"确认","value":{"path":"/checked"}}]}}""",
        ))
        val original = Message(id = "generated", author = "agent", role = MessageRole.Assistant,
            functionResponses = listOf(FunctionResponseView("tool", "generate_a2ui", a2uiDocument = document)))
        try {
            coordinator.sync("session", listOf(original))
            withTimeout(5_000) { while (visible.get()?.let(::rootData) == null) delay(1) }
            val displayed = visible.get()
            assertEquals(false, rootData(displayed)?.get("checked"))
            val submission = Message(id = "submitted", author = "user", role = MessageRole.User,
                a2uiInteraction = A2uiInteraction("surface", "submit", "confirm", 1,
                    Json.parseToJsonElement("{}").jsonObject,
                    Json.parseToJsonElement("""{"surface":{"checked":true}}""").jsonObject))
            coordinator.sync("session", listOf(original, submission))
            withTimeout(5_000) {
                while (rootData(displayed)?.get("checked") == false) delay(1)
            }
            // 与官方 CheckBox.Content 一样，旧组件仍持有 surface 时必须能解析 value。
            val value = checkNotNull(rootData(displayed)?.get("checked")) { "Required property 'value' is missing." }
            assertEquals(true, value)
            assertSame(displayed, visible.get())
            coordinator.sync("session", listOf(original, submission))
            assertSame(displayed, visible.get())
        } finally {
            scope.cancel()
            unmockkStatic("androidx.a2ui.compose.runtime.A2uiMessageParserKt")
        }
    }

    // 官方接口不暴露原始数据；仅在此生命周期回归测试中读取真实 Compose 数据模型。
    private fun rootData(surface: A2uiSurfaceModel): Map<*, *>? {
        val field = surface.javaClass.getDeclaredField("dataModel").apply { isAccessible = true }
        val data = field.get(surface)
        val get = data.javaClass.getMethod("get", A2uiDataPath::class.java).apply { isAccessible = true }
        return get.invoke(data, A2uiDataPath("/")) as? Map<*, *>
    }
}
