package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** 实际官方渲染器的交互式 fixture；可由 PreviewActivity 离线验证，不接线到业务导航。 */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun A2uiGalleryPreview() {
    var revision by remember { mutableStateOf(0) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("上海") }
    val catalog = remember { chatA2uiCatalog {} }
    val processor = remember(revision) { A2uiMessageProcessor(listOf(catalog)) }
    val surfaces by processor.activeSurfaces.collectAsState()
    LaunchedEffect(processor) {
        val parser = A2uiMessageParser()
        fun paint() { fixtureMessages(catalog.id, name, submitted).forEach { processor.processInput(parser, it) } }
        launch { processor.collectMessages() }
        launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect { event ->
                if (event is A2uiClientEventMessage) {
                    val data = event.clientDataModel?.surfaces?.get("fixture") as? Map<*, *>
                    name = data?.get("name") as? String ?: name
                    submitted = true
                    processor.processInput(parser, """{"version":"v0.9.1","deleteSurface":{"surfaceId":"fixture"}}""")
                    paint()
                }
            }
        }
        paint()
        awaitCancellation()
    }
    AsssistantaiTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("A2UI 原生交互预览", style = MaterialTheme.typography.titleLarge)
                ChatA2uiContent(surfaces, failed = false)
                Button(onClick = { revision += 1 }) { Text("从历史恢复") }
            }
        }
    }
}

private fun fixtureMessages(catalogId: String, name: String, submitted: Boolean): List<String> {
    val components = Json.parseToJsonElement("""[
        {"id":"root","component":"Card","child":"body"},
        {"id":"body","component":"Column","children":["heading","description","name","submit"]},
        {"id":"heading","component":"Text","text":"周末安排"},
        {"id":"description","component":"Text","text":"修改目的地后提交，卡片会原位更新。"},
        {"id":"name","component":"TextField","label":"目的地","value":{"path":"/name"}},
        {"id":"submit","component":"Button","child":"submit-label","action":{"event":{"name":"submit","context":{}}}},
        {"id":"submit-label","component":"Text","text":"提交"}
    ]""").jsonArray.toMutableList()
    if (submitted) components[3] = buildJsonObject {
        put("id", "description"); put("component", "Text"); put("text", "已更新：$name")
    }
    fun envelope(operation: String, payload: JsonObject) = buildJsonObject {
        put("version", "v0.9.1"); put(operation, payload)
    }.toString()
    return listOf(
        envelope("createSurface", buildJsonObject {
            put("surfaceId", "fixture"); put("catalogId", catalogId); put("sendDataModel", true)
        }),
        envelope("updateDataModel", buildJsonObject {
            put("surfaceId", "fixture"); put("path", "/"); put("value", buildJsonObject { put("name", name) })
        }),
        envelope("updateComponents", buildJsonObject {
            put("surfaceId", "fixture"); put("components", JsonArray(components))
        }),
    )
}
