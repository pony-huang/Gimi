package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.ui.toJsonSchemaString
import github.ponyhuang.gimi.data.a2ui.AndroidxA2uiProtocol
import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * 锁定宿主导出目录与独立校验器之间的真实契约。
 * 尤其覆盖 Material 目录引用的公共类型，防止运行时才出现 ChildList 等缺失。
 */
class ChatA2uiCatalogValidationTest {
    @Test
    fun exportedContainersValidateComposedTemplatesInputsTabsAndDetailReferences() = runTest {
        val catalog = chatA2uiCatalog {}
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        for (empty in listOf(false, true)) {
            for (revision in containerPreviewMessages(catalog.id, !empty, false, empty).chunked(3)) {
                val surfaceId = Json.parseToJsonElement(revision.first()).jsonObject
                    .getValue("createSurface").jsonObject.getValue("surfaceId").jsonPrimitive.content
                AndroidxA2uiProtocol().validate(A2uiDocument(surfaceId, revision), capabilities)
            }
        }
    }

    @Test
    fun exportedCatalogValidatesInlineAndBoundDataTablesIncludingEmptyRows() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val table = """{"heading":"用量","columns":[{"label":"模型"},{"label":"费用","align":"end"}],"rows":[["A","¥ 1.20"]]}"""
        for (property in listOf(table, """{"path":"/table"}""", table.replace("[[\"A\",\"¥ 1.20\"]]", "[]"))) {
            AndroidxA2uiProtocol().validate(
                A2uiDocument(
                    surfaceId = "table-surface",
                    messages = listOf(
                        """{"version":"v0.9.1","createSurface":{"surfaceId":"table-surface","catalogId":"${catalog.id}"}}""",
                        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"table-surface","path":"/","value":{"table":$table}}}""",
                        """{"version":"v0.9.1","updateComponents":{"surfaceId":"table-surface","components":[{"id":"root","component":"DataTable","table":$property}]}}""",
                    ),
                ),
                capabilities,
            )
        }
    }

    @Test
    fun exportedInteractiveCardCatalogValidatesRepresentativeForm() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val document = A2uiDocument(
            surfaceId = "surface",
            messages = listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"surface","catalogId":"${catalog.id}","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"surface","path":"/","value":{"destination":"上海"}}}""",
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"surface","components":[
                  {"id":"root","component":"Card","child":"body"},
                  {"id":"body","component":"Column","children":["title","description","destination","list","video","audio","submit"]},
                  {"id":"title","component":"Text","text":"周末行程"},
                  {"id":"description","component":"Text","text":"修改目的地后提交。"},
                  {"id":"destination","component":"TextField","label":"目的地","value":{"path":"/destination"},
                   "checks":[{"condition":{"call":"regex","args":{"value":{"path":"/destination"},"pattern":"^.+$"}},
                              "message":"目的地不能为空"}]},
                  {"id":"list","component":"List","children":["note"]},
                  {"id":"note","component":"Text","text":"列表项"},
                  {"id":"video","component":"Video","url":"https://example.com/video.mp4"},
                  {"id":"audio","component":"AudioPlayer","url":"https://example.com/audio.mp3","description":"测试音频"},
                  {"id":"submit","component":"Button","child":"submit-label","action":{"event":{"name":"submit","context":{}}}},
                  {"id":"submit-label","component":"Text","text":"提交"}
                ]}}""",
            ),
        )

        AndroidxA2uiProtocol().validate(document, capabilities)
    }

    @Test
    fun exportedCatalogValidatesTodayOnlyWeatherPayload() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        org.junit.Assert.assertFalse(capabilities.schemaJson.contains("WeatherForecast"))
        val document = A2uiDocument(
            surfaceId = "weather-surface",
            messages = listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"weather-surface","catalogId":"${catalog.id}","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"weather-surface","path":"/","value":{
                  "weather":{
                    "location":"上海",
                    "updatedAt":"14:20",
                    "current":{"conditionCode":"clear","temperatureC":28,"humidityPercent":68},
                    "daily":[{"date":"2026-10-25","highC":31,"lowC":24}]
                  }
                }}}""",
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"weather-surface","components":[
                  {"id":"root","component":"WeatherToday","weather":{"path":"/weather"}}
                ]}}""",
            ),
        )

        AndroidxA2uiProtocol().validate(document, capabilities)
    }

    @Test
    fun exportedCatalogValidatesMusicPlayerPayload() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val document = A2uiDocument(
            surfaceId = "player-surface",
            messages = listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"player-surface","catalogId":"${catalog.id}","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"player-surface","path":"/","value":{
                  "player":{
                    "provider":"网易云音乐",
                    "trackUri":"netease:track:one",
                    "title":"Night Flight",
                    "artist":"Artist",
                    "album":"Album",
                    "durationMs":258000,
                    "progressMs":72000,
                    "isPlaying":true,
                    "fetchedAtEpochMs":1760000000000,
                    "deviceId":"device",
                    "deviceName":"Living room",
                    "playbackContext":"now_playing"
                  }
                }}}""",
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"player-surface","components":[
                  {"id":"root","component":"MusicPlayer","player":{"path":"/player"}}
                ]}}""",
            ),
        )

        AndroidxA2uiProtocol().validate(document, capabilities)
    }

    @Test
    fun exportedCatalogValidatesOptionDeckWithContextualActionsAndLinks() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val deck = """
            {
              "title":"发布会视觉方向",
              "acceptLabel":"赞同",
              "rejectLabel":"不赞同",
              "items":[
                {
                  "id":"calm-blue",
                  "title":"克制的深蓝视觉",
                  "description":"适合强调技术感和可信度。",
                  "tags":["视觉设计","发布会"],
                  "link":{"label":"查看设计稿","url":"https://example.com/design/calm-blue"}
                },
                {
                  "id":"warm-paper",
                  "title":"纸张质感的暖色视觉",
                  "description":"突出内容和人本身的温度。"
                }
              ]
            }
        """.trimIndent()
        for (property in listOf(deck, """{"path":"/deck"}""")) {
            AndroidxA2uiProtocol().validate(
                A2uiDocument(
                    surfaceId = "option-deck-surface",
                    messages = listOf(
                        """{"version":"v0.9.1","createSurface":{"surfaceId":"option-deck-surface","catalogId":"${catalog.id}","sendDataModel":true}}""",
                        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"option-deck-surface","path":"/","value":{"deck":$deck}}}""",
                        """{"version":"v0.9.1","updateComponents":{"surfaceId":"option-deck-surface","components":[{"id":"root","component":"OptionDeck","deck":$property}]}}""",
                    ),
                ),
                capabilities,
            )
        }
    }
}
