package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 官方工具目录 ID — 厂商唯一，不跨厂商复用。
 *
 * 这些 ID 会持久化到 `ConversationToolConfiguration` 并透传到 agent 层过滤真实工具集，
 * 属于域级稳定标识：官方工具支持矩阵与 Agent 工厂与 UI 展示层共用本文件，
 * 避免多处字面量各说各话。
 */
public object OfficialToolIds {
    public const val ANTHROPIC_WEB_SEARCH: String = "anthropic_web_search"
    public const val MINIMAX_WEB_SEARCH: String = "minimax_web_search"
    public const val MINIMAX_IMAGE_GENERATION: String = "minimax_image_generation"
    public const val MINIMAX_TEXT_TO_IMAGE: String = "minimax_text_to_image"
    public const val MINIMAX_IMAGE_TO_IMAGE: String = "minimax_image_to_image"
    public const val GEMINI_WEB_SEARCH: String = "gemini_web_search"
    public const val GEMINI_URL_CONTEXT: String = "gemini_url_context"
    public const val GEMINI_GOOGLE_MAPS: String = "gemini_google_maps"
    public const val GLM_WEB_SEARCH: String = "glm_web_search"
    public const val KIMI_FORMULAS: String = "kimi_formulas"

    /**
     * 厂商远端执行的联网搜索在本地使用的声明名。
     *
     * 这些声明由厂商远端完成检索，本地没有可执行实现；若与本地真实函数同名（GLM 也提供
     * 名为 `web_search` 的本地函数，MCP 工具同样如此），协议适配器就无法判断该不该转成
     * 厂商原生形态。因此远端执行的声明统一使用本地专用名字，由各协议适配器翻译成厂商
     * wire 形态（Anthropic 协议为 `web_search_20250305`）。
     */
    public const val PROVIDER_WEB_SEARCH: String = "provider_web_search"

    /**
     * GLM 在一个工具目录下展开两个本地执行函数，函数 ID 即厂商协议函数名
     * （对应 agent 层 GlmWebSearchTool / GlmReaderTool 的 NAME）。
     */
    public const val GLM_WEB_SEARCH_FUNCTION: String = "web_search"
    public const val GLM_WEB_READER_FUNCTION: String = "web_reader"
}
