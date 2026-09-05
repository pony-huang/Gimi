package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 官方工具目录 ID — 厂商唯一，不跨厂商复用。
 *
 * 这些 ID 会持久化到 `ConversationToolConfiguration` 并透传到 agent 层过滤真实工具集，
 * 属于域级稳定标识：data 层注册表（OfficialToolRegistry）与 UI 展示层共用本文件，
 * 避免多处字面量各说各话。
 */
public object OfficialToolIds {
    public const val OPENAI_WEB_SEARCH: String = "openai_web_search"
    public const val ANTHROPIC_WEB_SEARCH: String = "anthropic_web_search"
    public const val MINIMAX_WEB_SEARCH: String = "minimax_web_search"
    public const val MIMO_WEB_SEARCH: String = "mimo_web_search"
    public const val GEMINI_WEB_SEARCH: String = "gemini_web_search"
    public const val GEMINI_URL_CONTEXT: String = "gemini_url_context"
    public const val GEMINI_GOOGLE_MAPS: String = "gemini_google_maps"
    public const val GLM_WEB_SEARCH: String = "glm_web_search"
    public const val KIMI_FORMULAS: String = "kimi_formulas"

    /**
     * GLM 在一个工具目录下展开两个本地执行函数，函数 ID 即厂商协议函数名
     * （对应 agent 层 GlmWebSearchTool / GlmReaderTool 的 NAME）。
     */
    public const val GLM_WEB_SEARCH_FUNCTION: String = "web_search"
    public const val GLM_WEB_READER_FUNCTION: String = "web_reader"
}
