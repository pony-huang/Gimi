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
    ): A2uiDocument {
        var correction = ""
        val weatherRouting = weatherRoutingInstructions(capabilities)
        val musicPlayerRouting = musicPlayerRoutingInstructions(capabilities)
        val newsRouting = newsRoutingInstructions(capabilities)
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
                        Do not include id or component inside a nested properties object: components are flat objects.

                        Design for a compact card inside a mobile chat, with roughly 280-360 dp of available width.
                        Prefer a simple vertical hierarchy: short title, optional supporting text, content, then actions.
                        Use one outer Card at most; avoid nested cards, repeated section headings and decorative dividers.
                        Use a modest heading variant supported by the catalog, body text for content, and caption for metadata.
                        Avoid h1/h2-sized headings in chat cards. Keep source, format and duration on one concise metadata line.
                        Follow the host theme; do not invent colors, fonts, spacing or sizing properties absent from the catalog.
                        Give each card one primary action. Use secondary actions sparingly and label them with short, clear verbs.
                        Put one or two short footer actions in a Row with justify="center" and align="center".
                        Keep the containing Column aligned with align="stretch" so the footer Row can center across the card width.
                        Center the action group as a whole; keep headings, descriptions and other body content start-aligned.
                        Stack longer actions vertically; never squeeze three or more text buttons across the card.
                        Keep button labels at normal reading size; never insert line breaks or split words to make them fit.
                        Use the user's language consistently, including action labels, status text and field descriptions.
                        Show each fact once; avoid repeating playback state, timestamps or instructions in multiple places.
                        Prefer catalog Video or AudioPlayer components for media instead of recreating their controls with buttons.
                        Only show controls and status supported by actual supplied capabilities and facts.
                        Never invent playing/paused state, progress, volume controls or success messages from a URL or button click.
                        For an external-player flow, show concise media details and an open/play entry instead of simulated controls.
                        For forms, place labels with their fields, initialize bound values, and end with a specific primary action.
                        Minimize typing by choosing input components that match the meaning of each field.
                        REQUIRED INPUT SELECTION: when the catalog contains DateTimeInput, use it for calendar dates and clock times.
                        This applies to alarms, reminders, appointments, reservations, birthdays, and start/end dates or times.
                        Do NOT use TextField for these values, including number fields labeled hour/minute or separate date parts.
                        Do NOT require users to type HH:mm, YYYY-MM-DD, or ISO strings; the picker handles those values.
                        Clock time: enableTime=true, enableDate=false. Calendar date: enableDate=true, enableTime=false.
                        A scheduled date and time: enableDate=true, enableTime=true. Never leave both flags false.
                        Bind value to a writable path and initialize that path in data with a valid supported ISO value or an empty string.
                        Example clock-time component (adapt the visible label to the user's language):
                        {"id":"alarm-time","component":"DateTimeInput","label":"时间","value":{"path":"/alarmTime"},"enableDate":false,"enableTime":true}
                        Example initial data when the time is unknown: {"alarmTime":""}.
                        Relative durations such as a number of minutes are not clock times; use an appropriate duration input.
                        Use a TextField for a calendar date/clock time only if the user explicitly requests manual text entry,
                        or DateTimeInput is absent from the supplied catalog. A previous text-based layout is not an exception.
                        For a finite set of choices, prefer ChoicePicker; for boolean settings, prefer CheckBox.
                        For a boolean setting or on/off control, when the catalog provides CheckBox, bind it to the supplied
                        current state and add a primary Button with an event action that applies the displayed choice
                        (for example enable or disable). A CheckBox only edits local data, so the action Button is mandatory;
                        never imply the toggle already changed the device. Label the checkbox with the setting name and
                        the primary action with its concrete effect, such as enable, disable, or apply.
                        For an alarm, reminder, timer, or schedule control, show the concrete date/time with DateTimeInput,
                        bind a CheckBox to the enabled state when available, and provide enable/disable/apply event actions.
                        When the current state is unknown, say so explicitly and offer a check/refresh action instead of
                        guessing. Keep the control available for the reverse action after an update.
                        For bounded numeric adjustments, prefer Slider when appropriate; use TextField when precise entry is necessary.
                        Use supported initial values from the supplied context to reduce repeated input, without guessing missing facts.
                        Bind selections to writable data paths and use only components and settings present in the supplied catalog.
                        On update, preserve the existing layout and submitted values unless the requested change requires otherwise.
                        On update, replace existing date/time TextFields with DateTimeInput while preserving the known selected value.
                        FINAL CHECK: inspect every date/time field before render_a2ui. Replace forbidden TextFields with DateTimeInput,
                        verify enableDate/enableTime, and verify each value binding has an initialized data entry.
                        Before calling render_a2ui, review the tree for narrow-screen readability and remove redundant elements.
                        $weatherRouting
                        $musicPlayerRouting
                        $newsRouting
                        When the catalog provides DataTable and the request is a comparison, metrics table, or structured
                        query result, prefer DataTable over manually aligning Text/Row/List components. Keep specialized
                        weather, news and music requests on their matching native components. DataTable is read-only:
                        do not use it for editable forms or controls. Use one required table property, preferably
                        {"id":"root","component":"DataTable","table":{"path":"/table"}}.
                        Example data: {"table":{"heading":"用量","columns":[{"label":"模型"},
                        {"label":"请求数","align":"end"}],"rows":[["Model A","128"],["Model B","64"]]}}.
                        Supply exactly one formatted string per column in every row. Localize labels, numbers, units,
                        dates and missing-value labels. Use rows:[] for empty results. Do not invent values or truncate
                        silently: at most 8 columns and 100 rows; narrow the query explicitly for larger results.
                        Catalog: ${capabilities.schemaJson}
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
                        "Weather UI requests must use WeatherToday or WeatherForecast instead of " +
                            "a generic Card/Text/List layout",
                    )
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
 * 当客户端目录包含两个天气组件时，向生成器注入强制路由。
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
        Use exactly one native weather component as the root:
        - WeatherForecast for any multi-day forecast, including when only 2-15 days are available.
        - WeatherToday for a detailed current-day view when the user explicitly asks for today.
        Both components share the same required property named `weather`.
        Prefer a data binding:
        {"id":"root","component":"WeatherForecast","weather":{"path":"/weather"}}
        Put the complete unified payload under data.weather. Minimal shape:
        {"weather":{"location":"广州","updatedAt":"11:26",
        "current":{"conditionCode":"cloudy","temperatureC":28,"feelsLikeC":30,
        "humidityPercent":68,"windDirection":"北风","windSpeedKph":12,
        "precipitationPercent":20,"uvIndex":4,"airQuality":"良"},
        "hourly":[{"timeLabel":"12:00","conditionCode":"cloudy","temperatureC":29}],
        "daily":[{"date":"2026-10-18","conditionCode":"cloudy","highC":33,"lowC":23,
        "precipitationPercent":20,"sunrise":"06:20","sunset":"18:00"}]}}
        Requirements:
        - daily is required and ordered from today; include at most 15 items.
        - Each daily item requires date, conditionCode, highC, and lowC.
        - conditionCode must be one of: clear, partly_cloudy, cloudy, rain, thunderstorm, snow, fog, wind.
        - A four-day result still uses WeatherForecast with four daily items.
        - Do not wrap the weather component in a generic Card and do not add duplicate action buttons.
        - Preserve facts returned by business tools; never invent missing measurements.
    """.trimIndent()
}

private fun weatherComponentsAvailable(capabilities: A2uiCapabilities): Boolean = runCatching {
    val components = Json.parseToJsonElement(capabilities.schemaJson)
        .jsonObject["components"]
        ?.jsonObject
    components?.containsKey("WeatherToday") == true &&
        components?.containsKey("WeatherForecast") == true
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
          player_next actions.
    """.trimIndent()
}

/**
 * 目录包含 NewsList 时，要求新闻/资讯摘要使用原生列表组件。
 *
 * 组件固定显示 5 条并在卡片内滚动，因此模型只需提供完整的编辑顺序和原文链接，
 * 不要再用多个 Card/Text 组件重复搭建列表。
 */
private fun newsRoutingInstructions(capabilities: A2uiCapabilities): String {
    val available = runCatching {
        Json.parseToJsonElement(capabilities.schemaJson)
            .jsonObject["components"]
            ?.jsonObject
            ?.containsKey("NewsList") == true
    }.getOrDefault(false)
    if (!available) return ""
    return """
        NEWS LIST ROUTING (HIGHEST PRIORITY):
        When the requested UI concerns news, latest headlines, current events, or an information
        digest, do NOT build the layout from Card, Text, List, Row, or Button.
        Use exactly one native NewsList component as the root.
        It has one required property named `news`. Prefer a data binding:
        {"id":"root","component":"NewsList","news":{"path":"/news"}}
        Put the digest under data.news:
        {"news":{"heading":"今日要闻","updatedAt":"12:11","icon":"newspaper","items":[
        {"title":"Headline from the source","source":"Reuters","timeLabel":"12:08",
        "category":"Technology","imageUrl":"https://...","url":"https://..."}]}}
        Requirements:
        - Use only articles and URLs returned by tools; never invent headlines, sources, or links.
        - Preserve editorial order; include at most 20 items.
        - Each item requires a title. source, timeLabel, category, imageUrl, and url are optional.
        - icon is optional and accepts newspaper, article, globe, trending, campaign, or bolt.
        - The component shows five items and scrolls internally for the rest.
        - NewsList takes precedence over weather keywords contained inside the articles.
        - Do not duplicate the news list in Markdown after rendering the component.
    """.trimIndent()
}

private val WeatherComponentTypes = setOf("WeatherToday", "WeatherForecast")

private fun JsonElement.componentType(): String? =
    (this as? JsonObject)?.get("component")?.jsonPrimitive?.contentOrNull

internal fun message(operation: String, payload: JsonObject): String = buildJsonObject {
    put("version", "v0.9.1")
    put(operation, payload)
}.toString()
