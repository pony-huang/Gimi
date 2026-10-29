package github.ponyhuang.gimi.data.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

/** 可组合容器的必要风险边界：所有声明的引用都检查缺失与循环，业务字符串不参与图遍历。 */
class A2uiComponentGraphTest {
    private val id = """{"${'$'}ref":"common_types.json#/${'$'}defs/ComponentId"}"""
    private val capabilities = A2uiCapabilities("graph", """{
      "components":{
        "Container":{"type":"object","properties":{
          "header":{"allOf":[$id]},"footer":{"${'$'}ref":"#/${'$'}defs/slot"},
          "summary":$id,"trigger":$id,"content":$id,
          "children":{"${'$'}ref":"common_types.json#/${'$'}defs/ChildList"},
          "tabs":{"type":"array","items":{"type":"object","properties":{"child":$id}}},
          "payload":{"type":"object","properties":{"child":{"type":"string"}}}
        }},
        "Text":{"type":"object","properties":{"text":{"type":"string"}}}
      },
      "${'$'}defs":{"slot":$id}
    }""")

    @Test
    fun rejectsMissingAndCyclicReferencesInEveryDeclaredSlot() = runTest {
        val protocol = AndroidxA2uiProtocol()
        for (target in listOf("missing", "root")) {
            val values = listOf(
                "\"header\":\"$target\"", "\"footer\":\"$target\"", "\"summary\":\"$target\"",
                "\"trigger\":\"$target\"", "\"content\":\"$target\"", "\"children\":[\"$target\"]",
                "\"children\":{\"componentId\":\"$target\",\"path\":\"/items\"}",
                "\"tabs\":[{\"child\":\"$target\"}]",
            )
            for (properties in values) {
                try {
                    protocol.validate(document(properties), capabilities)
                    fail("Expected invalid component graph: $properties")
                } catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun resolvesLocalSchemaReferencesAndDoesNotInterpretPlainStringsAsComponentIds() = runTest {
        AndroidxA2uiProtocol().validate(document(
            """"header":"label","footer":"label","children":["label"],"payload":{"child":"missing"}""",
            withLabel = true,
        ), capabilities)
    }

    private fun document(properties: String, withLabel: Boolean = false) = A2uiDocument("surface", listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"surface","catalogId":"graph"}}""",
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"surface","path":"/","value":{}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"surface","components":[
          {"id":"root","component":"Container",$properties}
          ${if (withLabel) """,{"id":"label","component":"Text","text":"root"}""" else ""}
        ]}}""",
    ))
}
