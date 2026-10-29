package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * A single user-selectable function exposed by an official-tool category.
 *
 * `id` is the stable identifier persisted in `ConversationToolConfiguration` and
 * passed through to the agent layer to filter the actual toolset. `id` matches the locally declared function name used by the agent.
 */
data class OfficialToolFunction(
    val id: String,
    val name: String,
    val description: String,
)
