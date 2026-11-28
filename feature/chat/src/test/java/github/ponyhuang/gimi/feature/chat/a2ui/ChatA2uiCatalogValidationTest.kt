package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.toJsonSchemaString
import github.ponyhuang.gimi.data.a2ui.AndroidxA2uiProtocol
import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import github.ponyhuang.gimi.feature.chat.a2ui.components.NeteaseRedAmbientPalette
import github.ponyhuang.gimi.feature.chat.a2ui.components.fallbackPaletteFor
import github.ponyhuang.gimi.feature.chat.a2ui.components.resolveMusicClientJumpUrl
import github.ponyhuang.gimi.feature.chat.a2ui.components.resolveNeteaseJumpUrl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 锁定宿主导出目录与独立校验器之间的真实契约。
 * 尤其覆盖 Material 目录引用的公共类型，防止运行时才出现 ChildList 等缺失。
 */
class ChatA2uiCatalogValidationTest {
    @Test
    fun entityCardListAcceptsTextBadgesAlongsideObjectBadges() = runTest {
        val catalog = extendedChatA2uiCatalog(chatA2uiCatalog {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val document = A2uiDocument("entities", listOf(
            """{"version":"v0.9.1","createSurface":{"surfaceId":"entities","catalogId":"${catalog.id}"}}""",
            """{"version":"v0.9.1","updateDataModel":{"surfaceId":"entities","path":"/","value":{"items":[{"title":"First","badge":"Featured"},{"title":"Second","badge":{"label":"Open","color":"success"}}]}}}""",
            """{"version":"v0.9.1","updateComponents":{"surfaceId":"entities","components":[
              {"id":"root","component":"Column","children":["list","direct"]},
              {"id":"list","component":"List","children":{"componentId":"item","path":"/items"}},
              {"id":"item","component":"EntityCard","title":{"path":"title"},"badge":{"path":"badge"}},
              {"id":"direct","component":"EntityCard","title":"Third","badge":"New"}
            ]}}""",
        ))

        AndroidxA2uiProtocol().validate(document, capabilities)
    }

    @Test
    fun extendedCatalogExportsCustomComponentsAlongsideTheBasicCatalog() {
        val basic = chatA2uiCatalog {}
        val extended = extendedChatA2uiCatalog(basic)
        fun componentNames(catalog: A2uiCatalog) =
            Json.parseToJsonElement(catalog.toJsonSchemaString()).jsonObject
                .getValue("components").jsonObject.keys

        assertEquals(
            setOf("BarChart", "PieChart", "CandlestickChart", "WeatherCard", "ReceiptCard", "StockTicker",
                "DataTable", "KeyValueList", "Timeline", "Agenda", "MusicPlayerCard", "Switch", "ChipSelect", "EntityCard", "MediaVolumeSlider", "FlashlightSwitch"),
            componentNames(extended) - componentNames(basic),
        )
        assertNotEquals(basic.id, extended.id)
    }

    @Test
    fun extendedCatalogValidatesCustomCardsAndWritableSwitchWithBasicContainer() = runTest {
        val catalog = extendedChatA2uiCatalog(chatA2uiCatalog {})
        val capabilities = A2uiCapabilities(catalog.id, catalog.toJsonSchemaString())
        val document = A2uiDocument(
            surfaceId = "extended",
            messages = listOf(
                """{"version":"v0.9.1","createSurface":{"surfaceId":"extended","catalogId":"${catalog.id}","sendDataModel":true}}""",
                """{"version":"v0.9.1","updateDataModel":{"surfaceId":"extended","path":"/","value":{"reminder":true,"liked":true,"topics":["deploys"],"slices":[{"label":"Food","value":20}]}}}""",
                """{"version":"v0.9.1","updateComponents":{"surfaceId":"extended","components":[
                  {"id":"root","component":"Column","children":["bar","pie","candlestick","weather","receipt","stock","table","pairs","timeline","agenda","music","switch","chips","volume","torch","entity"]},
                  {"id":"bar","component":"BarChart","format":"currency","data":[{"x":"Food","y":20,"series":"Budget"}]},
                  {"id":"pie","component":"PieChart","format":"compact","data":{"path":"/slices"},"donut":true,"centerLabel":"Total"},
                  {"id":"candlestick","component":"CandlestickChart","title":"HLVR","format":"currency","data":[{"date":"Sep 1","open":182.4,"high":182.48,"low":178.71,"close":179.46}]},
                  {"id":"weather","component":"WeatherCard","location":"Hangzhou","temp":23,"condition":"Cloudy"},
                  {"id":"receipt","component":"ReceiptCard","merchant":"Cafe","items":[{"name":"Coffee","price":12}]},
                  {"id":"stock","component":"StockTicker","symbol":"0700.HK","price":420.8,"sample":true,"series":[410,420.8]},
                  {"id":"table","component":"DataTable","title":"Accounts","pageSize":5,"columns":[{"key":"price","label":"Price","format":"currency"}],"rows":[{"price":12}]},
                  {"id":"pairs","component":"KeyValueList","title":"Order","columns":2,"items":[{"label":"Store","value":"West Lake"},{"label":"Items","value":3,"hint":"Express"}]},
                  {"id":"timeline","component":"Timeline","items":[{"title":"Packed","status":"done"},{"title":"In transit","status":"current","icon":"truck"}]},
                  {"id":"agenda","component":"Agenda","title":"This week","days":[{"date":"2026-11-28","events":[{"title":"Walk","time":"9:30 AM","tone":"accent"}]}]},
                  {"id":"music","component":"MusicPlayerCard","title":"Goon Gumpas","artist":"Aphex Twin","durationSeconds":153,"progressSeconds":51,"isPlaying":true,"provider":"spotify","deviceId":"speaker-1","externalUrl":"https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC","songId":"8f3c9a12b4e567890123456789abcdef","originalId":"139774","liked":{"path":"/liked"},"tracks":[{"title":"Goon Gumpas","artist":"Aphex Twin","durationSeconds":153},{"title":"Avril 14th","artist":"Aphex Twin","externalUrl":"spotify:track:1a2b3c","isVip":true}]},
                  {"id":"switch","component":"Switch","label":"Reminder","value":{"path":"/reminder"}},
                  {"id":"chips","component":"ChipSelect","label":"Topics","value":{"path":"/topics"},"options":[{"value":"deploys","label":"Deploys"},{"value":"security","label":"Security"}]},
                  {"id":"volume","component":"MediaVolumeSlider"},
                  {"id":"torch","component":"FlashlightSwitch"},
                  {"id":"entity","component":"EntityCard","title":"Café Central","subtitle":"Coffee house","meta":"Open until 22:00","badge":{"label":"Popular","color":"success"},"rating":4.5,"ratingCount":12840,"actions":[{"label":"Directions","href":"https://maps.google.com"},{"label":"Pick","variant":"solid"}]}
                ]}}""",
            ),
        )

        AndroidxA2uiProtocol().validate(document, capabilities)
    }

    @Test
    fun exportedCatalogContainsOnlyBasicCatalogComponents() {
        val catalog = chatA2uiCatalog {}
        val names = Json.parseToJsonElement(catalog.toJsonSchemaString()).jsonObject
            .getValue("components").jsonObject.keys
        assertEquals(
            setOf(
                "Text", "Image", "Icon", "Video", "AudioPlayer", "Row", "Column", "List",
                "Card", "Tabs", "Divider", "Modal", "Button", "TextField", "CheckBox",
                "ChoicePicker", "Slider", "DateTimeInput",
            ),
            names,
        )
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
    fun neteaseJumpUrlPrefersNumericOriginalIdsAndNeverEmbedsEncryptedHexIds() {
        assertEquals("orpheus://song/139774", resolveNeteaseJumpUrl("139774", "999888"))
        assertEquals("orpheus://playlist/999888", resolveNeteaseJumpUrl("", "999888"))
        // 32 位十六进制加密 songId 不能拼进客户端路由，必须回退到 orpheus:// 首页
        assertEquals("orpheus://", resolveNeteaseJumpUrl("8f3c9a12b4e567890123456789abcdef", ""))
    }

    @Test
    fun musicClientJumpSupportsSpotifyUrisAndNetEaseRedBaseTheme() {
        assertEquals(
            "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            resolveMusicClientJumpUrl(
                originalSongId = "",
                songId = "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            ),
        )
        assertEquals(
            "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            resolveMusicClientJumpUrl(
                originalSongId = "",
                externalUrl = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=123",
            ),
        )
        assertEquals(
            "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            resolveMusicClientJumpUrl(
                originalSongId = "",
                songId = "4uLU6hMCjMI75M1A2tKUQC",
                provider = "spotify",
            ),
        )
        assertEquals(NeteaseRedAmbientPalette, fallbackPaletteFor("Goon Gumpas", 0))
    }
}
