package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 组件预览共用的协议宿主，只负责 fixture 渲染和本地示例事件展示。 */
@Composable
internal fun ChatA2uiSurfacePreview(
    dark: Boolean,
    title: String,
    fixtures: (catalogId: String) -> List<String>,
) {
    val catalog = remember { containerPreviewCatalog() }
    val processor = remember(catalog) { A2uiMessageProcessor(listOf(catalog)) }
    val surfaces by processor.activeSurfaces.collectAsState()
    var lastEvent by remember { mutableStateOf("可滑动浏览、展开列表，或填写并提交下方示例。") }
    var protocolError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(processor) {
        val parser = A2uiMessageParser()
        launch { processor.collectMessages() }
        launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect { event ->
                when (event) {
                    is A2uiClientErrorMessage -> protocolError = "${event.code}: ${event.message}"
                    is A2uiClientEventMessage -> {
                        // 示例只展示组件原有事件和提交值，不执行工具或伪装成业务操作成功。
                        lastEvent = "事件：${event.type}；内容：${event.context}"
                    }
                }
            }
        }
        fixtures(catalog.id).forEach { processor.processInput(parser, it) }
        awaitCancellation()
    }
    AsssistantaiTheme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("真实 A2UI 渲染 · 交互预览", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                surfaces.forEach { surface ->
                    key(surface.id) { ChatA2uiContent(listOf(surface), failed = false) }
                }
                Text(protocolError ?: lastEvent, style = MaterialTheme.typography.bodySmall,
                    color = if (protocolError == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 只替换正式目录的图片加载器，预览使用离线素材及相同的组件契约。 */
private fun containerPreviewCatalog(): A2uiCatalog {
    val base = chatA2uiCatalog {}
    val image = MaterialA2uiBasicCatalogV1Defaults.image { url, description, _, modifier, _ ->
        ContainerFixtureImage(url.substringAfterLast('/').toIntOrNull() ?: 0, description, modifier)
    }
    return A2uiCatalog(
        catalogId = "https://gimi.app/a2ui/catalogs/preview/v1",
        components = base.components.filterNot { it.name == "Image" } + image,
        functions = base.functions,
        isInline = true,
    )
}

/** 离线图片加载 fixture；Image 本身仍使用基础组件的 schema、属性绑定和渲染入口。 */
@Composable
private fun ContainerFixtureImage(index: Int, description: String?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.semantics { description?.let { contentDescription = it } }) {
        drawRect(colors.primaryContainer)
        drawCircle(colors.surface.copy(alpha = 0.8f), size.minDimension * 0.095f,
            Offset(size.width * (0.7f - index % 3 * 0.12f), size.height * 0.24f))
        val ridge = Path().apply {
            moveTo(0f, size.height * 0.58f)
            lineTo(size.width * 0.22f, size.height * (0.25f + index % 2 * 0.1f))
            lineTo(size.width * 0.46f, size.height * 0.55f)
            lineTo(size.width * 0.72f, size.height * 0.32f)
            lineTo(size.width, size.height * 0.57f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(ridge, colors.primary.copy(alpha = 0.35f))
        drawRect(colors.surfaceContainerHigh.copy(alpha = 0.72f),
            topLeft = Offset(0f, size.height * 0.7f), size = Size(size.width, size.height * 0.3f))
    }
}

internal fun containerPreviewEnvelope(operation: String, payload: JsonObject): String = buildJsonObject {
    put("version", "v0.9.1"); put(operation, payload)
}.toString()
