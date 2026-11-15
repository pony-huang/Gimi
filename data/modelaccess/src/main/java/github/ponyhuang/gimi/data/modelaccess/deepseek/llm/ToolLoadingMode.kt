package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import github.ponyhuang.gimi.data.modelaccess.ModelRuntimeMetadata

/** 工具搜索的协议加载方式；未适配端点始终返回普通搜索结果。 */
enum class ToolLoadingMode { RESULT, DEEPSEEK_REFERENCE }

fun toolLoadingMode(runtime: ModelRuntimeMetadata?): ToolLoadingMode {
    if (!isOfficialDeepSeekAnthropicEndpoint(runtime)) return ToolLoadingMode.RESULT
    // 工具引用（tool_reference/defer_loading）目前仅 deepseek-flash 声明支持。
    return if (runtime?.modelId == "deepseek-flash") {
        ToolLoadingMode.DEEPSEEK_REFERENCE
    } else ToolLoadingMode.RESULT
}

const val DEFERRED_CATALOG = "gimi.deferred_tool_catalog"

/** 内联工具定义写入 Event.partMetadata 的键；转换器据此加载声明，工具集据此写入。 */
const val INLINE_DEFINITIONS = "gimi.inline_tool_definitions"

/** 内联工具搜索工具的协议名；转换器与工具集共用。 */
const val TOOL_SEARCH_NAME: String = "tool_search"
