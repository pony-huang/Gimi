@file:OptIn(com.google.adk.kt.annotations.FrameworkInternalApi::class)

package github.ponyhuang.gimi.data.modelaccess.shared

import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.tools.toJsonSchema
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.ponyhuang.gimi.data.modelaccess.shared.openAiFunctionToolArray
import github.ponyhuang.gimi.data.modelaccess.shared.qwenFunctionToolArray

/**
 * Realtime 会话工具声明的共享转换。
 *
 * 两条厂商链路（GA OpenAI / Qwen-Audio）的工具条目外层结构不同
 * （GA 扁平并列 vs Qwen 嵌套在 `function` 键下），但参数都是 ADK [FunctionDeclaration]
 * 的 parameters 经 `toJsonSchema` 转出的 JSON Schema。这里只提供共享的转换件，
 * 外层形状由各协议自行组装。
 */

/**
 * 工具参数的 JSON Schema。
 *
 * 服务端按整批 `tools` 校验声明：只要有一条缺 `type`/`properties` 或带 `description: null`，
 * 就可能被判为非法而**整批丢弃**（表现就是模型「没有工具」）。因此这里把所有声明
 * 归一成合法的 object schema，并给缺省描述补非空占位。
 */
internal fun FunctionDeclaration.toolParametersJson(): JsonObject {
    // toJsonSchema 返回 kotlinx.serialization 的 JsonObject；协议层统一 Gson，经文本换域。
    val schema = parameters?.toJsonSchema()?.toString()
        ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
        ?: return emptyObjectSchema()
    if (!schema.has("type") || schema.get("type")?.isJsonNull == true) {
        schema.addProperty("type", "object")
    }
    if (schema.get("type")?.asString == "object" && !schema.has("properties")) {
        schema.add("properties", JsonObject())
    }
    return schema
}

/** 无参数工具也要给出完整的 object schema，避免 `{}` 被服务端判为缺字段。 */
private fun emptyObjectSchema(): JsonObject = JsonObject().apply {
    addProperty("type", "object")
    add("properties", JsonObject())
}

/** description 缺省时给非空占位，避免序列化成 `"description": null` 连累整批 tools。 */
private fun FunctionDeclaration.toolDescription(): String =
    description.takeIf { it.isNotBlank() } ?: "Call ${name}."

/** GA OpenAI 的扁平 function 工具条目数组。 */
internal fun openAiFunctionToolArray(tools: List<FunctionDeclaration>): JsonArray = JsonArray().apply {
    for (tool in tools) {
        add(JsonObject().apply {
            addProperty("type", "function")
            addProperty("name", tool.name)
            addProperty("description", tool.toolDescription())
            add("parameters", tool.toolParametersJson())
        })
    }
}

/** Qwen 的嵌套 function 工具条目数组（声明对象包在 `function` 键下）。 */
internal fun qwenFunctionToolArray(tools: List<FunctionDeclaration>): JsonArray = JsonArray().apply {
    for (tool in tools) {
        add(JsonObject().apply {
            addProperty("type", "function")
            add("function", JsonObject().apply {
                addProperty("name", tool.name)
                addProperty("description", tool.toolDescription())
                add("parameters", tool.toolParametersJson())
            })
        })
    }
}
