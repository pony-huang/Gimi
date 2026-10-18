package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.ui.toJsonSchemaString
import github.ponyhuang.gimi.data.a2ui.AndroidxA2uiProtocol
import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 锁定宿主导出目录与独立校验器之间的真实契约。
 * 尤其覆盖 Material 目录引用的公共类型，防止运行时才出现 ChildList 等缺失。
 */
class ChatA2uiCatalogValidationTest {
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
    fun exportedCatalogValidatesSharedWeatherComponentPayloads() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val document = A2uiDocument(
            surfaceId = "weather-surface",
            messages = listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"weather-surface","catalogId":"${catalog.id}","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"weather-surface","path":"/","value":{
                  "weather":{
                    "location":"上海",
                    "updatedAt":"14:20",
                    "current":{"conditionCode":"clear","temperatureC":28,"humidityPercent":68},
                    "hourly":[{"timeLabel":"15:00","conditionCode":"clear","temperatureC":29}],
                    "daily":[{"date":"2026-10-18","conditionCode":"clear","highC":31,"lowC":24,
                              "sunrise":"05:58","sunset":"17:24"}]
                  }
                }}}""",
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"weather-surface","components":[
                  {"id":"root","component":"Column","children":["today","forecast"]},
                  {"id":"today","component":"WeatherToday","weather":{"path":"/weather"}},
                  {"id":"forecast","component":"WeatherForecast","weather":{"path":"/weather"}}
                ]}}""",
            ),
        )

        AndroidxA2uiProtocol().validate(document, capabilities)
    }

    @Test
    fun exportedCatalogValidatesSpotifyPlayerPayload() = runTest {
        val catalog = chatA2uiCatalog(openUrl = {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val document = A2uiDocument(
            surfaceId = "spotify-surface",
            messages = listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"spotify-surface","catalogId":"${catalog.id}","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"spotify-surface","path":"/","value":{
                  "spotify":{
                    "trackUri":"spotify:track:one",
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
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"spotify-surface","components":[
                  {"id":"root","component":"SpotifyPlayer","spotify":{"path":"/spotify"}}
                ]}}""",
            ),
        )

        AndroidxA2uiProtocol().validate(document, capabilities)
    }
}
