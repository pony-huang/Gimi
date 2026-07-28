package github.ponyhuang.asssistantai.domain.modelcatalog.model

/**
 * Domain interface for listing the user-selectable functions of an official
 * tool. Implementations may fetch the manifest over the network (Kimi) or
 * return a static list (web search).
 */
interface OfficialToolFunctionCatalog {
    /** Returns the functions currently available for [toolId], or empty on failure. */
    suspend fun listFunctions(toolId: String): List<OfficialToolFunction>
}