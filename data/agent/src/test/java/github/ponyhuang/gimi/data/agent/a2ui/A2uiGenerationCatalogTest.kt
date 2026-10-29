package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** 目录裁剪是付费调用前的必要边界：保留引用闭包，拒绝宿主不支持的能力。 */
class A2uiGenerationCatalogTest {
    @Test
    fun keepsOnlySelectedComponentsAndReachableDefinitionsIncludingIndirectFunctionContract() {
        val capabilities = A2uiCapabilities("catalog", """{
          "catalogId":"catalog",
          "components":{
            "Column":{"properties":{"body":{"${'$'}ref":"#/${'$'}defs/body"}}},
            "Text":{},"Unused":{"description":"unused-native-schema"}
          },
          "functions":{"format":{"properties":{"args":{"${'$'}ref":"#/${'$'}defs/args"}}}},
          "${'$'}defs":{
            "body":{"${'$'}ref":"#/components/Text"},
            "args":{"type":"object"},
            "anyFunction":{"${'$'}ref":"#/functions/format"},
            "unused":{"description":"unneeded-definition"}
          }
        }""")
        val scoped = generationCatalog(capabilities, setOf("Column"))
        val schema = Json.parseToJsonElement(scoped.schemaJson).jsonObject
        assertEquals("catalog", scoped.catalogId)
        assertEquals(setOf("Column", "Text"), schema.getValue("components").jsonObject.keys)
        assertEquals(setOf("body", "args", "anyFunction"), schema.getValue("\$defs").jsonObject.keys)
        assertTrue(schema.getValue("functions").jsonObject.containsKey("format"))
        assertFalse(scoped.schemaJson.contains("unused-native-schema"))
        assertFalse(scoped.schemaJson.contains("unneeded-definition"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUnknownComponentBeforeModelInvocation() {
        generationCatalog(A2uiCapabilities("catalog", """{"components":{"Text":{}}}"""), setOf("Missing"))
    }

    @Test
    fun keepsComponentsFromThePreviousTargetWhenSelectingAnUpdateCatalog() {
        val previous = A2uiDocument("surface", listOf("""{
          "updateComponents":{"components":[{"id":"time","component":"DateTimeInput"}]}
        }"""))
        val capabilities = A2uiCapabilities("catalog", """{
          "components":{"Text":{},"Column":{},"DateTimeInput":{},"WeatherToday":{}}
        }""")
        val scoped = generationCatalog(capabilities, setOf("Column") + previousComponentNames(previous))
        val names = Json.parseToJsonElement(scoped.schemaJson).jsonObject.getValue("components").jsonObject.keys
        assertTrue("DateTimeInput" in names)
        assertFalse("WeatherToday" in names)
    }
}
