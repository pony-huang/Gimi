package github.ponyhuang.asssistantai.agent.tools.official

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelConfig

/**
 * One **protocol-scoped** official toolset — a single class per protocol
 * uniformly modeled after `KimiFormulaToolset`.
 *
 * - For agent-side tools (e.g. Moonshot formulas) this class owns the
 *   remote manifest, per-call [BaseTool] instantiation, and user-level
 *   function filtering — see `KimiFormulaToolset`.
 * - For protocol-native tools (e.g. OpenAI/Anthropic built-ins) this class
 *   owns the list of supported native tool ids and the vendor SDK spec
 *   per id — see `OpenaiOfficialToolset` / `AnthropicOfficialToolset`.
 *
 * One toolset per protocol keeps registration overhead constant as new
 * native tools are adopted; see `SUPPORTED_NATIVE_IDS` in each toolset.
 */
interface OfficialToolset {
    /** Stable identifier (e.g. `"openai"`, `"anthropic"`, `"kimi"`). Diagnostic. */
    val protocolId: String

    /** True iff this toolset contributes anything for [config] (service + protocol + enabled ids). */
    fun isApplicable(config: ModelConfig): Boolean

    /**
     * Local [BaseTool] implementations owned by this toolset. Empty for
     * native-only toolsets (e.g. `OpenaiOfficialToolset`).
     */
    suspend fun getTools(config: ModelConfig): List<BaseTool>

    /**
     * Native tool specs contributed by this toolset for the
     * OpenAI-Compatible protocol (OpenAI, Mimo). Empty when unsupported.
     */
    fun openAiNativeSpecs(config: ModelConfig): List<NativeToolSpec>

    /**
     * Native tool specs contributed by this toolset for the Anthropic
     * protocol. Empty when unsupported.
     */
    fun anthropicNativeSpecs(config: ModelConfig): List<NativeToolSpec>
}
