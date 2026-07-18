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
    val stored = StoredModel(
        modelId = model.modelId,
        modelName = model.modelName,
        source = StoredModelSource.USER,
        isStt = model.isStt,
    )
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
    val existingById = groups.flatMap { it.models }.associateBy { it.modelId }
    val remoteModels = models
        .distinctBy { it.modelId }
        .filterNot { it.modelId in userModelIds }
        .map { model ->
            StoredModel(
                modelId = model.modelId,
                modelName = model.modelName,
                source = StoredModelSource.REMOTE,
                isStt = existingById[model.modelId]?.isStt ?: model.isStt,
            )
        }
    val remoteById = remoteModels.associateBy { it.modelId }
    val existingRemoteSpeechIds = groups.flatMap { group ->
        group.models.filter { it.source == StoredModelSource.REMOTE && it.isStt }
    }.mapTo(mutableSetOf()) { it.modelId }
    val newRemoteSpeechModels = remoteModels.filter {
        it.isStt && it.modelId !in existingRemoteSpeechIds
    }
    val remoteChatModels = remoteModels.filterNot { it.isStt }

    return groups.mapIndexed { index, group ->
        val userModels = group.models.filter { it.source == StoredModelSource.USER }
        val speechModels = group.models
            .filter { it.source == StoredModelSource.REMOTE && it.isStt }
            .map { remoteById[it.modelId] ?: it }
        if (index == 0) {
            group.copy(models = remoteChatModels + speechModels + newRemoteSpeechModels + userModels)
        } else {
            group.copy(models = speechModels + userModels)
        }
    }
}

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
                    model.copy(isStt = default.isStt)
                } ?: model
            } + defaults.models.filterNot { it.modelId in existingModelIds },
        )
    }
    return mergedExisting + defaultGroups.filterNot { it.groupId in existingGroupIds }
}
