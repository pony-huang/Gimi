package github.ponyhuang.gimi.data.agent.a2ui

import kotlinx.serialization.json.*

/** 固定设备模板及主 Agent 自由组合共用的创建输入。 */
internal object A2uiDirectComposition {
    const val DEVICE_CONTROLS = "device_controls"
    val deviceTypes = setOf("Column", "MediaVolumeSlider", "FlashlightSwitch")

    fun components(template: String?, raw: Any?): JsonArray {
        if (template == null) return parse(raw, "components") as? JsonArray
            ?: throw IllegalArgumentException("components must be a JSON array of flat components")
        require(raw == null && template == DEVICE_CONTROLS) {
            "Use template_id=device_controls alone or supply components for a custom composition"
        }
        return buildJsonArray {
            add(buildJsonObject {
                put("id", "root")
                put("component", "Column")
                putJsonArray("children") { add("volume"); add("flashlight") }
            })
            add(component("volume", "MediaVolumeSlider"))
            add(component("flashlight", "FlashlightSwitch"))
        }
    }

    fun data(raw: Any?): JsonObject = if (raw == null) JsonObject(emptyMap()) else {
        parse(raw, "data") as? JsonObject ?: throw IllegalArgumentException("data must be a JSON object")
    }

    fun catalogTypes(schema: String): Set<String> =
        (Json.parseToJsonElement(schema).jsonObject["components"] as? JsonObject)?.keys.orEmpty()

    private fun parse(raw: Any?, field: String): JsonElement {
        val text = raw as? String ?: throw IllegalArgumentException("$field must be a JSON string")
        require(text.length <= 256 * 1024) { "$field is too large" }
        return Json.parseToJsonElement(text)
    }

    private fun component(id: String, type: String) = buildJsonObject {
        put("id", id)
        put("component", type)
    }
}
