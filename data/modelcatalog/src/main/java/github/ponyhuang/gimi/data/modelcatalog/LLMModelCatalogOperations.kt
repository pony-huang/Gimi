package github.ponyhuang.gimi.data.modelcatalog

internal fun mergeDefaultModelMetadata(
    existingGroups: List<StoredModelGroup>,
    defaultGroups: List<StoredModelGroup>,
): List<StoredModelGroup> {
    val defaultsByGroup = defaultGroups.associateBy { it.groupId }
    val existingGroupIds = existingGroups.mapTo(mutableSetOf()) { it.groupId }
    val mergedExisting = existingGroups.map { group ->
        val defaults = defaultsByGroup[group.groupId] ?: return@map group
        val defaultsByModel = defaults.models.associateBy { it.modelId }
        val existingModelIds = group.models.mapTo(mutableSetOf()) { it.modelId }
        group.copy(
            models = group.models.map { model ->
                defaultsByModel[model.modelId]?.let { default ->
                    model.copy(
                        capabilities = default.capabilities,
                        multimodal = default.multimodal,
                    )
                } ?: model
            } + defaults.models.filterNot { it.modelId in existingModelIds },
        )
    }
    return mergedExisting + defaultGroups.filterNot { it.groupId in existingGroupIds }
}
