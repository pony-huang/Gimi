package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiCapabilities
import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 生成器只获知本次需要的组件；本地定义取引用闭包，函数保留实际宿主契约，不另起分类模型。 */
internal fun generationCatalog(capabilities: A2uiCapabilities, requested: Set<String>): A2uiCapabilities {
    val source = Json.parseToJsonElement(capabilities.schemaJson) as? JsonObject
        ?: throw IllegalArgumentException("Invalid A2UI catalog")
    val all = source["components"] as? JsonObject ?: return capabilities
    require(requested.all { it in all }) { "Requested A2UI component is not supported by this client" }
    val selected = linkedMapOf<String, JsonElement>()
    val definitions = linkedMapOf<String, MutableMap<String, JsonElement>>()
    val visited = mutableSetOf<String>()

    fun includeReferences(value: JsonElement) {
        when (value) {
            is JsonArray -> value.forEach(::includeReferences)
            is JsonObject -> {
                (value["\$ref"] as? JsonPrimitive)?.contentOrNull?.let { ref ->
                    if (!ref.substringBefore('#').endsWith("common_types.json") && visited.add(ref)) {
                        val path = ref.substringAfter('#', "").removePrefix("/").split('/')
                            .map { it.replace("~1", "/").replace("~0", "~") }
                        require(path.size >= 2) { "Unsupported catalog reference: $ref" }
                        val group = source[path[0]] as? JsonObject
                        val target = group?.get(path[1])
                            ?: throw IllegalArgumentException("Missing catalog reference: $ref")
                        if (path[0] == "components") selected[path[1]] = target
                        else if (path[0] != "functions") definitions.getOrPut(path[0]) { linkedMapOf() }[path[1]] = target
                        includeReferences(target)
                    }
                }
                value.values.forEach(::includeReferences)
            }
            else -> Unit
        }
    }
    (BaseGenerationComponents + requested).forEach { name -> all[name]?.let { selected[name] = it } }
    selected.values.toList().forEach(::includeReferences)
    source["functions"]?.let(::includeReferences)
    source["theme"]?.let(::includeReferences)
    // common_types 的 FunctionCall 间接引用 catalog.json#/$defs/anyFunction，不能只看内联的引用。
    ((source["\$defs"] as? JsonObject)?.get("anyFunction"))?.let { definition ->
        definitions.getOrPut("\$defs") { linkedMapOf() }["anyFunction"] = definition
        includeReferences(definition)
    }
    val scoped = source.toMutableMap()
    scoped["components"] = JsonObject(selected)
    // 不携带未引用定义，尤其避免组件已裁剪但 anyComponent 又引用整个旧目录。
    listOf("\$defs", "definitions").forEach(scoped::remove)
    definitions.forEach { (key, value) -> scoped[key] = JsonObject(value) }
    return capabilities.copy(schemaJson = JsonObject(scoped).toString())
}

/** 更新已有 surface 时保留其组件能力，避免裁剪掉仍在使用的输入或原生控件。 */
internal fun previousComponentNames(document: A2uiDocument?): Set<String> = document?.messages.orEmpty()
    .flatMap { raw ->
        val update = (Json.parseToJsonElement(raw) as? JsonObject)?.get("updateComponents") as? JsonObject
        (update?.get("components") as? JsonArray).orEmpty()
    }
    .mapNotNull { component -> ((component as? JsonObject)?.get("component") as? JsonPrimitive)?.contentOrNull }
    .toSet()

private val BaseGenerationComponents = setOf("Text", "Icon", "Row", "Column", "Card", "Button", "Divider")
