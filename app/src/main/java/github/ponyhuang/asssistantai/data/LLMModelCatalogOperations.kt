package github.ponyhuang.asssistantai.data

internal fun removeStoredModel(
    groups: List<StoredModelGroup>,
    groupId: String,
    modelId: String,
): List<StoredModelGroup> = groups.map { group ->
    if (group.groupId == groupId) {
        group.copy(models = group.models.filterNot { it.modelId == modelId })
    } else {
        group
    }
}

internal fun appendUserModel(
    groups: List<StoredModelGroup>,
    serviceId: String,
    serviceName: String,
    model: LLMModelItem,
): List<StoredModelGroup> {
    val stored = StoredModel(model.modelId, model.modelName, StoredModelSource.USER)
    if (groups.isEmpty()) {
        return listOf(
            StoredModelGroup(
                groupId = "$serviceId-default",
                groupName = "$serviceName 默认组",
                models = listOf(stored),
            ),
        )
    }
    return groups.mapIndexed { index, group ->
        if (index == 0) {
            group.copy(models = group.models.filterNot { it.modelId == model.modelId } + stored)
        } else {
            group.copy(models = group.models.filterNot { it.modelId == model.modelId })
        }
    }
}

internal fun syncStoredRemoteModels(
    existingGroups: List<StoredModelGroup>,
    serviceId: String,
    serviceName: String,
    models: List<LLMModelItem>,
): List<StoredModelGroup> {
    val groups = existingGroups.ifEmpty {
        listOf(
            StoredModelGroup(
                groupId = "$serviceId-default",
                groupName = "$serviceName 默认组",
            ),
        )
    }
    val userModelIds = groups
        .flatMap { it.models }
        .filter { it.source == StoredModelSource.USER }
        .mapTo(mutableSetOf()) { it.modelId }
    val remoteModels = models
        .distinctBy { it.modelId }
        .filterNot { it.modelId in userModelIds }
        .map { StoredModel(it.modelId, it.modelName, StoredModelSource.REMOTE) }

    return groups.mapIndexed { index, group ->
        val userModels = group.models.filter { it.source == StoredModelSource.USER }
        if (index == 0) {
            group.copy(models = remoteModels + userModels)
        } else {
            group.copy(models = userModels)
        }
    }
}
