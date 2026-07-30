package github.ponyhuang.asssistantai.domain.modelcatalog.model

/**
 * A single user-selectable function exposed by an official-tool category.
 *
 * `id` is the stable identifier persisted in `ConversationToolConfiguration` and
 * passed through to the agent layer to filter the actual toolset. For providers
 * whose declarations are dynamic (e.g. Moonshot formulas fetched from a remote
 * manifest), `id` matches the tool name reported by the vendor.
 */
data class OfficialToolFunction(
    val id: String,
    val name: String,
    val description: String,
)
