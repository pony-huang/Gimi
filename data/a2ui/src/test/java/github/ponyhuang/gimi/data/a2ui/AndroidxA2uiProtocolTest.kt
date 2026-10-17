package github.ponyhuang.gimi.data.a2ui

import github.ponyhuang.gimi.domain.a2ui.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 独立协议能力的必要风险边界：目录校验、原子修订与历史重放。 */
class AndroidxA2uiProtocolTest {
    private val protocol = AndroidxA2uiProtocol()
    private val capabilities = A2uiCapabilities("test", """{
      "components":{"Text":{"type":"object","properties":{
        "component":{"type":"string","const":"Text"},"text":{"type":"string"}
      },"required":["component","text"]}}
    }""")

    @Test
    fun validatesUsingExportedCatalogAndRejectsUnknownOrMalformedComponents() = runTest {
        protocol.validate(document("Text", "\"Hello\""), capabilities)
        for (invalid in listOf(document("Unknown", "\"Hello\""), document("Text", "42"))) {
            try {
                protocol.validate(invalid, capabilities)
                fail("Expected catalog validation failure")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun validatesCommonTypeReferencesFromTheExportedChatCatalog() = runTest {
        val chatCatalog = A2uiCapabilities(
            "common-types-probe",
            """
            {
              "components": {
                "Row": {
                  "type": "object",
                  "properties": {
                    "component": {"type": "string", "const": "Row"},
                    "children": {"${'$'}ref": "common_types.json#/${'$'}defs/ChildList"}
                  },
                  "required": ["component", "children"]
                },
                "Text": {
                  "type": "object",
                  "properties": {
                    "component": {"type": "string", "const": "Text"},
                    "text": {"type": "string"}
                  },
                  "required": ["component", "text"]
                }
              }
            }
            """.trimIndent(),
        )
        val row = A2uiDocument(
            "surface",
            listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"surface","catalogId":"common-types-probe","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"surface","path":"/","value":{}}}""",
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"surface","components":[
                    {"id":"root","component":"Row","children":["label"]},
                    {"id":"label","component":"Text","text":"Hello"}
                ]}}""",
            ),
        )

        protocol.validate(row, chatCatalog)
    }

    @Test
    fun rejectsUnsupportedVersionAndMixedSurfaces() = runTest {
        for (invalid in listOf(
            document().copy(messages = document().messages.map { it.replace("v0.9.1", "v1.0") }),
            document().copy(surfaceId = "other"),
        )) {
            try { protocol.validate(invalid, capabilities); fail("Expected rejection") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun replayDeduplicatesRevisionsAndHonorsDelete() {
        val first = document()
        val updated = document(text = "\"Updated\"")
        assertEquals(listOf(updated), protocol.replay(listOf(first, first, updated).map(A2uiRecord::Revision)))
        val delete = A2uiDocument("surface", listOf("""{"version":"v0.9.1","deleteSurface":{"surfaceId":"surface"}}"""), true)
        assertTrue(protocol.replay(listOf(first, updated, delete).map(A2uiRecord::Revision)).isEmpty())
        assertEquals(listOf(first), protocol.replay(listOf(A2uiRecord.Revision(first))))
    }

    @Test
    fun structuredInteractionAndToolEnvelopeRoundTrip() {
        val interaction = A2uiInteractionCodec.decode("""{"a2ui_interaction":{
            "surfaceId":"surface","componentId":"submit","action":"confirm","timestamp":1,"context":{},"data":{"surface":{"name":"Alice"}}
        }}""")!!
        assertEquals(interaction, A2uiInteractionCodec.decode(A2uiInteractionCodec.encode(interaction)))
        assertNull(A2uiInteractionCodec.decode("ordinary text"))
        assertEquals(document(), A2uiToolResult.extract(A2uiToolResult.TOOL_NAME,
            mapOf("result" to mapOf(A2uiToolResult.DOCUMENT_KEY to A2uiToolResult.encode(document())))))
        assertNull(A2uiToolResult.extract("another_tool", mapOf(A2uiToolResult.DOCUMENT_KEY to "bad")))
    }

    @Test
    fun replayKeepsSubmittedValuesUntilANewerAgentRevision() {
        val interaction = A2uiInteractionCodec.decode("""{"a2ui_interaction":{
            "surfaceId":"surface","componentId":"submit","action":"confirm","timestamp":1,"context":{},"data":{"surface":{"name":"Alice"}}
        }}""")!!
        val records = listOf(A2uiRecord.Revision(document()), A2uiRecord.Submission(interaction))
        val restored = protocol.replay(records).single()
        val value = Json.parseToJsonElement(restored.messages[1]).jsonObject.getValue("updateDataModel").jsonObject.getValue("value")
        assertEquals("""{"name":"Alice"}""", value.toString())
        assertEquals(listOf(document()), protocol.replay(records + A2uiRecord.Revision(document())))
    }

    private fun document(type: String = "Text", text: String = "\"Hello\"") = A2uiDocument("surface", listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"surface","catalogId":"test","sendDataModel":true}}""",
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"surface","path":"/","value":{}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"surface","components":[{"id":"root","component":"$type","text":$text}]}}""",
    ))
}
