package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 只注入本次目录所需的交互契约，避免集合请求也携带完整表单和各类原生组件的说明。 */
internal fun catalogHas(capabilities: A2uiCapabilities, vararg names: String): Boolean {
    val components = Json.parseToJsonElement(capabilities.schemaJson).jsonObject["components"]?.jsonObject
    return names.any { components?.containsKey(it) == true }
}

/** 容器只描述布局，业务内容仍由普通组件与标准 children 模板表达。 */
internal fun containerRoutingInstructions(capabilities: A2uiCapabilities): String = buildString {
    if (catalogHas(capabilities, "Disclosure")) appendLine(
        "Disclosure: summary is a component ID; children uses the standard child list/template. " +
            "initiallyExpanded is optional. Use for details/sources; expand/collapse is local.",
    )
    if (catalogHas(capabilities, "Tabs")) appendLine(
        "Tabs: tabs:[{\"title\":\"Label\",\"child\":\"component-id\"}]. Each child is an existing subtree. " +
            "Switching is local; preserve bound input values.",
    )
    if (catalogHas(capabilities, "DetailSheet")) appendLine(
        "DetailSheet: trigger and content are component IDs. The trigger Button must emit open_detail; " +
            "a close Button inside content emits close_detail. These scoped events are local only. " +
            "Business buttons inside content keep their normal event names and authorization.",
    )
}.trimEnd()

internal fun inputRoutingInstructions(capabilities: A2uiCapabilities): String {
    if (!catalogHas(capabilities, "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput")) return ""
    return """
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
    """.trimIndent()
}

internal fun tableRoutingInstructions(capabilities: A2uiCapabilities): String {
    if (!catalogHas(capabilities, "DataTable")) return ""
    return """
        When the catalog provides DataTable and the request is a comparison, metrics table, or structured
        query result, prefer DataTable over manually aligning Text/Row/List components. Keep specialized
        weather and music requests on their matching native components. DataTable is read-only:
        do not use it for editable forms or controls. Use one required table property, preferably
        {"id":"root","component":"DataTable","table":{"path":"/table"}}.
        Example data: {"table":{"heading":"用量","columns":[{"label":"模型"},
        {"label":"请求数","align":"end"}],"rows":[["Model A","128"],["Model B","64"]]}}.
        Supply exactly one formatted string per column in every row. Localize labels, numbers, units,
        dates and missing-value labels. Use rows:[] for empty results. Do not invent values or truncate
        silently: at most 8 columns and 100 rows; narrow the query explicitly for larger results.
    """.trimIndent()
}
