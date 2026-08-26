package github.ponyhuang.gimi.pluginapi

import org.json.JSONArray
import org.json.JSONObject

/**
 * 插件工具结果序列化工具。
 *
 * ADK 工具返回给 Agent 的结果必须是 JSON-native（Map/List/String/Number/Boolean/null），
 * 否则事件持久化时对非 JSON 类型会抛错。插件作者把 org.json 的解析结果
 * （[JSONObject]/[JSONArray]/[JSONObject.NULL]）经 [toNative] 递归转成 JSON-native 后再返回。
 */
object PluginJson {

    /** 把 org.json 值递归转成 ADK 工具可返回的 JSON-native 类型；普通值原样返回。 */
    fun toNative(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associate { key -> key to toNative(value.opt(key)) }
        is JSONArray -> (0 until value.length()).map { toNative(value.opt(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
