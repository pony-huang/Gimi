package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * Domain interface for official (vendor built-in) tools. The implementation
 * lives in the agent layer and owns the vendor support matrix; UI and
 * conversation configuration query it through this contract.
 *
 * Implementations may fetch the manifest over the network (Kimi) or return a
 * static list (web search).
 */
interface OfficialToolFunctionCatalog {

    /**
     * Returns the tool ids the given service supports under [protocol],
     * regardless of the concrete model. Tool ids are vendor-unique; model
     * family narrowing (if any) is applied later by the agent runtime.
     */
    suspend fun supportedToolIds(serviceId: String, protocol: ApiProtocol): Set<String>

    /** Returns the functions currently available for [toolId], or empty on failure. */
    suspend fun listFunctions(toolId: String): List<OfficialToolFunction>
}