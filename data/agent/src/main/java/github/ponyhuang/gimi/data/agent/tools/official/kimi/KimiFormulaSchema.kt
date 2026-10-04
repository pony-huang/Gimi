package github.ponyhuang.gimi.data.agent.tools.official.kimi

import com.google.adk.kt.types.Schema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 将目录保留的 JSON Schema 转换为当前 Agent SDK 的参数声明。 */
internal fun JsonObject.toAdkSchema(): Schema = Schema(
    description = this["description"]?.jsonPrimitive?.content,
    properties = this["properties"]?.jsonObject?.mapValues { (_, value) ->
        value.jsonObject.toAdkSchema()
    },
    items = this["items"]?.jsonObject?.toAdkSchema(),
    required = this["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
)
