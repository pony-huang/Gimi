package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.a2ui.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*

/** 只生成界面的隔离子 Agent；最多一次纠错，不执行主 Agent 的业务工具。 */
internal class A2uiGeneration(private val model: Model, private val protocol: A2uiProtocol) {
    suspend fun generate(
        surfaceId: String,
        capabilities: A2uiCapabilities,
        instruction: String,
        requireWeatherComponent: Boolean = false,
        requestedComponents: Set<String> = emptySet(),
    ): A2uiDocument {
        var correction = ""
        val forcedWeather = if (requireWeatherComponent && weatherComponentsAvailable(capabilities)) WeatherComponentTypes else emptySet()
        val scoped = generationCatalog(capabilities, requestedComponents + forcedWeather)
        val allowedTypes = Json.parseToJsonElement(scoped.schemaJson).jsonObject["components"]?.jsonObject?.keys
        val weatherRouting = weatherRoutingInstructions(scoped)
        val musicPlayerRouting = musicPlayerRoutingInstructions(scoped)
        val optionDeckRouting = optionDeckRoutingInstructions(scoped)
        val inputRouting = inputRoutingInstructions(scoped)
        val containerRouting = containerRoutingInstructions(scoped)
        val tableRouting = tableRoutingInstructions(scoped)
        repeat(2) { attempt ->
            val request = LlmRequest(
                model = model,
                contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = instruction + correction)))),
                config = GenerateContentConfig(
                    systemInstruction = Content(parts = listOf(Part(text = """
                        Generate an A2UI 0.9.1 surface using ONLY the supplied catalog.
                        Call render_a2ui exactly once. components and data must be JSON strings.
                        Return a complete component tree, root id 'root', unique component ids.
                        Use the user's language for all visible labels. Bind editable values to data paths.
                        Buttons use event actions with meaningful names and context. Never execute business operations.
                        Button variants are primary, default, and borderless; never use a variant named secondary.
                        Do not include id or component inside a nested properties object: components are flat objects.

                        Design for a compact card inside a mobile chat, with roughly 280-360 dp of available width.
                        Prefer a simple vertical hierarchy: short title, optional supporting text, content, then actions.
                        Use one outer Card at most; avoid nested cards, repeated section headings and decorative dividers.
                        Use a modest heading variant supported by the catalog, body text for content, and caption for metadata.
                        Avoid h1/h2-sized headings in chat cards. Keep source, format and duration on one concise metadata line.
                        Follow the host theme; do not invent colors, fonts, spacing or sizing properties absent from the catalog.
                        Give each card one primary action. Use secondary actions sparingly and label them with short, clear verbs.
                        Put one or two short footer actions in a Row with justify="end" and align="center".
                        Keep the containing Column aligned with align="stretch"; keep body content start-aligned.
                        Stack longer actions vertically; never squeeze three or more text buttons across the card.
                        Keep button labels at normal reading size; never insert line breaks or split words to make them fit.
                        Use the user's language consistently, including action labels, status text and field descriptions.
                        Show each fact once; avoid repeating playback state, timestamps or instructions in multiple places.
                        Prefer catalog Video or AudioPlayer components for media instead of recreating their controls with buttons.
                        Only show controls and status supported by actual supplied capabilities and facts.
                        Never invent playing/paused state, progress, volume controls or success messages from a URL or button click.
                        For an external-player flow, show concise media details and an open/play entry instead of simulated controls.
                        $inputRouting
                        Before calling render_a2ui, review the tree for narrow-screen readability and remove redundant elements.
                        $weatherRouting
                        $musicPlayerRouting
                        $optionDeckRouting
                        $containerRouting
                        $tableRouting
                        Catalog: ${scoped.schemaJson}
                    """.trimIndent()))),
                    tools = listOf(Tool(functionDeclarations = listOf(renderDeclaration))),
                    toolConfig = ToolConfig(functionCallingConfig = FunctionCallingConfig(
                        mode = FunctionCallingConfigMode.ANY,
                        allowedFunctionNames = listOf("render_a2ui"),
                    )),
                ),
            )
            // 网络或 SDK 错误不进入格式纠错循环，避免失败时额外付费调用。
            val responses = model.generateContent(request, stream = false).toList()
            responses.firstOrNull { it.errorCode != null || !it.errorMessage.isNullOrBlank() }?.let {
                error(it.errorMessage ?: it.errorCode.orEmpty())
            }
            try {
                val call = responses.flatMap { it.content?.parts.orEmpty() }
                    .mapNotNull { it.functionCall }.singleOrNull { it.name == "render_a2ui" }
                    ?: throw IllegalArgumentException("Call render_a2ui exactly once")
                val components = Json.parseToJsonElement(call.args["components"] as? String
                    ?: throw IllegalArgumentException("components must be a JSON array string")).jsonArray
                if (requireWeatherComponent && weatherComponentsAvailable(capabilities) &&
                    components.none { it.componentType() in WeatherComponentTypes }
                ) {
                    throw IllegalArgumentException(
                        "Weather UI requests must use WeatherToday instead of " +
                            "a generic Card/Text/List layout",
                    )
                }
                require(allowedTypes == null || components.all { it.componentType() in allowedTypes }) {
                    "Use only component types from the selected catalog"
                }
                val data = Json.parseToJsonElement(call.args["data"] as? String ?: "{}").jsonObject
                val document = A2uiDocument(surfaceId, listOf(
                    message("createSurface", buildJsonObject {
                        put("surfaceId", surfaceId); put("catalogId", capabilities.catalogId); put("sendDataModel", true)
                    }),
                    message("updateDataModel", buildJsonObject {
                        put("surfaceId", surfaceId); put("path", "/"); put("value", data)
                    }),
                    message("updateComponents", buildJsonObject {
                        put("surfaceId", surfaceId); put("components", components)
                    }),
                ))
                protocol.validate(document, capabilities)
                return document
            } catch (invalid: IllegalArgumentException) {
                if (attempt == 1) throw invalid
                correction = "\nPrevious output was invalid: ${invalid.message}. Generate a corrected complete surface."
            }
        }
        error("Unreachable")
    }

    private val renderDeclaration = FunctionDeclaration(
        name = "render_a2ui",
        description = "Return a complete native A2UI surface.",
        parameters = Schema(type = Type.OBJECT, properties = mapOf(
            "components" to Schema(type = Type.STRING, description = "JSON array of flat A2UI components."),
            "data" to Schema(type = Type.STRING, description = "JSON object containing initial bound field values."),
        ), required = listOf("components", "data")),
    )
}

/**
 * 当客户端目录包含当天天气组件时，向生成器注入强制路由。
 *
 * 天气数据通常已经由业务工具取得；如果让模型自由发挥，它会倾向于用 Card/Text/List
 * 拼一张“看起来像天气”的通用卡片，绕过宿主提供的原生组件。这里用组件名、绑定路径
 * 和最小数据示例把选择收紧，避免模型因 schema 较大而忽略自定义组件。
 */
private fun weatherRoutingInstructions(capabilities: A2uiCapabilities): String {
    if (!weatherComponentsAvailable(capabilities)) return ""
    return """
        WEATHER COMPONENT ROUTING (HIGHEST PRIORITY):
        Apply this only when the requested UI itself is a weather card, current-conditions view, or
        forecast. Do NOT choose a weather component merely because a news article, digest, or another
        requested UI happens to mention weather, rain, or temperature.
        For an actual weather UI request, do NOT build the layout from Card, Text, List, Row, or Button.
        Use exactly one native WeatherToday component as the root for today's weather only.
        Future-day and multi-day forecasts have no native component; answer them in Markdown,
        never relabel a future day as today or embed future days inside WeatherToday.
        Use the required weather property, preferably a data binding:
        {"id":"root","component":"WeatherToday","weather":{"path":"/weather"}}
        Put only today's facts under data.weather:
        {"weather":{"location":"广州","updatedAt":"11:26",
        "current":{"conditionCode":"cloudy","temperatureC":28,"humidityPercent":68,
        "windSpeedKph":12,"precipitationPercent":20},
        "daily":[{"date":"2026-10-25","highC":33,"lowC":23}]}}
        Requirements:
        - daily contains exactly one item: today's actual highC and lowC. Do not use a later day.
        - Do not send hourly forecasts, extra daily items, sun paths, UV or air-quality fields.
        - Omit unavailable humidity, wind speed, precipitation probability and update time.
        - conditionCode must be one of: clear, partly_cloudy, cloudy, rain, thunderstorm, snow, fog, wind.
        - Do not wrap the weather component in a generic Card and do not add duplicate action buttons.
        - Preserve facts returned by business tools; never invent missing measurements.
    """.trimIndent()
}

private fun weatherComponentsAvailable(capabilities: A2uiCapabilities): Boolean = runCatching {
    val components = Json.parseToJsonElement(capabilities.schemaJson)
        .jsonObject["components"]
        ?.jsonObject
    components?.containsKey("WeatherToday") == true
}.getOrDefault(false)

/**
 * 目录包含 MusicPlayer 时，强制任意播放器的当前播放/曲目卡使用原生组件。
 *
 * 进度必须在 UI 侧根据进度快照和设备时间外推，因此这里要求模型原样传递
 * `progress_ms`、`is_playing` 与 `fetched_at_ms`，不能自行计算或编造。
 */
private fun musicPlayerRoutingInstructions(capabilities: A2uiCapabilities): String {
    val available = runCatching {
        Json.parseToJsonElement(capabilities.schemaJson)
            .jsonObject["components"]
            ?.jsonObject
            ?.containsKey("MusicPlayer") == true
    }.getOrDefault(false)
    if (!available) return ""
    return """
        MUSIC PLAYER ROUTING (HIGHEST PRIORITY):
        When the requested UI concerns now-playing information, playback control, or a concrete
        track result for ANY music provider (Spotify, NetEase Cloud Music, QQ Music, Apple Music,
        or another player), do NOT build the layout from Card, Text, List, Row, or Button.
        Use exactly one native MusicPlayer component as the root.
        It has one required property named `player`. Prefer a data binding:
        {"id":"root","component":"MusicPlayer","player":{"path":"/player"}}
        Put the snapshot under data.player:
        {"player":{"provider":"Spotify","providerIconUrl":"https://example.com/icon.png",
        "trackUri":"spotify:track:...","title":"Track title",
        "artist":"Artist one, Artist two","album":"Album name","artworkUrl":"https://i.scdn.co/...",
        "durationMs":258000,"progressMs":72000,"isPlaying":true,
        "fetchedAtEpochMs":1760000000000,"deviceId":"device-id",
        "deviceName":"Living room speaker","trackUrl":"https://open.spotify.com/track/...",
        "playbackContext":"now_playing"}}
        Mapping requirements:
        - provider = the service's human-readable display name, such as Spotify, 网易云音乐,
          QQ音乐, or Apple Music.
        - trackUri and trackUrl are optional; include whichever identifier and web URL the
          provider or supplied tool returns.
        - title = track name; artist = joined artist names; album and artworkUrl when available.
        - durationMs, progressMs, fetchedAtEpochMs, deviceId, and deviceName are optional.
        - For Spotify: trackUri = item.uri; artist = item.artists[].name joined with commas;
          album = item.album.name; artworkUrl = the preferred item.album.images[] URL;
          durationMs = item.duration_ms; trackUrl = item.external_urls.spotify.
        - For spotify_now_playing: progressMs = progress_ms, isPlaying = is_playing,
          fetchedAtEpochMs = fetched_at_ms, deviceId/deviceName from device, playbackContext = now_playing.
        - For a Spotify search/get-track result: progressMs = 0, isPlaying = false,
          fetchedAtEpochMs may be omitted, playbackContext = track_result.
        - For other providers, map the equivalent fields from the supplied tool result and omit
          unknown values.
        - Never invent progress, duration, device, artwork, title, track identity, or provider.
        - Do not add generic play/pause buttons; MusicPlayer emits player_toggle_playback and
          player_next, player_previous, player_seek(positionMs), player_set_shuffle(enabled),
          player_set_repeat(mode: off/context/track), player_set_saved(saved) actions.
        - Set canPrevious/canSeek/canShuffle/canRepeat/canSave only when the supplied tools support
          the operation and the current device/playback restrictions permit it. Omit otherwise.
          For Spotify now-playing, inspect actions.disallows and device.is_restricted; do not
          enable transport controls for track search results or restricted devices.
        - Map Spotify shuffle_state to shuffleEnabled and repeat_state to repeatMode only when
          returned. Map library membership to isSaved only after spotify_check_library.
          Unknown states must be omitted, never assumed false.
        - Route player_previous to spotify_previous, player_seek positionMs to spotify_seek
          position_ms, player_set_shuffle enabled to spotify_set_shuffle state, player_set_repeat
          mode to spotify_set_repeat state. Pass deviceId as device_id where supported.
          Route player_set_saved to spotify_save_to_library or spotify_remove_from_library using
          trackUri as a one-element ids array. Use only tools available for that provider; never substitute Spotify
          tools for another service. After mutations fetch fresh playback/library state and update
          the same surface. Tool acceptance alone is not proof playback changed.
    """.trimIndent()
}

/**
 * 目录包含 OptionDeck 时，把需要逐项比较的候选方案路由到原生横向卡组。
 *
 * 模型会倾向于用 Card/Text/List 拼静态列表；组件的接受/拒绝需要由宿主发出
 * server event 再回到主 Agent，因此这里固定数据形状、场景动作和事件契约。
 */
private fun optionDeckRoutingInstructions(capabilities: A2uiCapabilities): String {
    val available = runCatching {
        Json.parseToJsonElement(capabilities.schemaJson)
            .jsonObject["components"]
            ?.jsonObject
            ?.containsKey("OptionDeck") == true
    }.getOrDefault(false)
    if (!available) return ""
    return """
        OPTION DECK ROUTING (HIGHEST PRIORITY):
        When the requested UI compares plans one by one and asks the user to react to each option,
        such as travel itineraries, design directions, social recommendations, nearby stores or places,
        restaurants, courses, gifts or proposals,
        do NOT build the layout from Card, Text, List, Row, or Button.
        Use exactly one native OptionDeck component as root.
        It has one required property named `deck`. Prefer a data binding:
        {"id":"root","component":"OptionDeck","deck":{"path":"/deck"}}
        Put the candidates under data.deck:
        {"deck":{"title":"杭州周末路线","acceptLabel":"想去","rejectLabel":"换个地方",
        "items":[{"id":"west-lake","title":"西湖两日慢游","meta":"2 天 1 夜",
        "description":"避开拥挤时段，预留雨天替代路线。","tags":["慢节奏","西湖周边"],
        "emoji":"🧭","link":{"label":"查看完整路线","url":"https://..."}}]}}
        Requirements:
        - items needs at least 2 and at most 12 options; every id must be unique and stable.
        - title, meta, description, tags (at most 3) and emoji or imageUrl are optional per item;
          fill them from real tool results or well-known facts, never invent businesses or places.
        - acceptLabel and rejectLabel are required and must fit the content domain. Examples:
          travel: 想去 / 换个地方; design review: 赞同 / 不赞同; dining: 喜欢 / 不喜欢;
          social posts: 收藏 / 略过; nearby stores: 想去 / 换一家.
          Do not use generic like/dislike wording when a more natural domain action is clear.
        - Each optional link object needs a domain-specific label and an absolute http/https URL.
          Examples: 查看完整路线, 打开设计稿, 查看菜单, 查看笔记, 打开地图. Do not invent destinations.
        - Use the user's language for all visible labels and copy.
        - The component renders horizontal browsing, progress and the exhausted state; do not add
          duplicate decision buttons and do not wrap it in a generic Card.
        - It emits option_accept / option_reject (context: itemId, index, title, actionLabel)
          for every decision and option_exhausted (context: accepted, rejected, total) when
          the deck is finished. Run the matching business tools and update the same surface.
        - To offer another round, update the deck property in place with a new candidate list.
    """.trimIndent()
}

private val WeatherComponentTypes = setOf("WeatherToday")

private fun JsonElement.componentType(): String? =
    (this as? JsonObject)?.get("component")?.jsonPrimitive?.contentOrNull

internal fun message(operation: String, payload: JsonObject): String = buildJsonObject {
    put("version", "v0.9.1")
    put(operation, payload)
}.toString()
