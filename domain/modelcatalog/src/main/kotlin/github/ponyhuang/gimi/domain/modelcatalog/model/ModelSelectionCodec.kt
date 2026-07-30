package github.ponyhuang.gimi.domain.modelcatalog.model

/** App-owned payload; historical compatibility is intentionally not required in this phase. */
object ModelSelectionCodec {
    private const val SEPARATOR = '\u001f'

    fun encode(selection: ModelSelection): String = listOf(
        selection.serviceId,
        selection.groupId,
        selection.modelId,
    ).joinToString(SEPARATOR.toString())

    fun decode(value: String): ModelSelection? {
        val parts = value.split(SEPARATOR)
        if (parts.size != 3 || parts.any(String::isBlank)) return null
        return ModelSelection(parts[0], parts[1], parts[2])
    }
}
