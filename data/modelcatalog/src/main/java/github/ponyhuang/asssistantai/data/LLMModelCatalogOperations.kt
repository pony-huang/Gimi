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
        isTts = model.isTts,
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

/**
 * 自动进行分组。目前模型名字格式：{名字}-{版本}-{版本类型}; 目前mimo格式：：{名字}-{版本}-{版本/类型}-{具体类型}
 */
internal fun syncStoredRemoteModels(
    existingGroups: List<StoredModelGroup>,
    serviceId: String,
    serviceName: String,
    models: List<LLMModelItem>,
): List<StoredModelGroup> {
    val userModelIds = existingGroups
        .flatMap { it.models }
        .filter { it.source == StoredModelSource.USER }
        .mapTo(mutableSetOf()) { it.modelId }
    val existingById = existingGroups.flatMap { it.models }.associateBy { it.modelId }
    val remoteModels = models
        .distinctBy { it.modelId }
        .filterNot { it.modelId in userModelIds }
        .map { model ->
            val existing = existingById[model.modelId]
            StoredModel(
                modelId = model.modelId,
                modelName = model.modelName,
                source = StoredModelSource.REMOTE,
                isStt = existing?.isStt ?: model.isStt || model.modelId.hasModelType("asr"),
                isTts = existing?.isTts ?: model.isTts || model.modelId.hasModelType("tts"),
            )
        }
    val userGroups = existingGroups.mapNotNull { group ->
        val userModels = group.models.filter { it.source == StoredModelSource.USER }
        group.takeIf { userModels.isNotEmpty() }?.copy(models = userModels)
    }
    val remoteGroups = remoteModels
        .groupBy { it.modelId.modelGroupId(serviceId, existingGroups.firstOrNull()?.groupId) }
        .map { (groupId, groupedModels) ->
            val existing =
                existingGroups.firstOrNull { it.groupId.equals(groupId, ignoreCase = true) }
            StoredModelGroup(
                groupId = existing?.groupId ?: groupId,
                groupName = existing?.groupName ?: groupId,
                isExpanded = existing?.isExpanded ?: true,
                models = groupedModels,
            )
        }
    if (remoteGroups.isEmpty() && userGroups.isEmpty()) {
        return listOf(StoredModelGroup("$serviceId-default", "$serviceName 默认组"))
    }
    val remoteByGroupId = remoteGroups.associateBy { it.groupId }
    return userGroups.map { userGroup ->
        remoteByGroupId[userGroup.groupId]?.let { remoteGroup ->
            remoteGroup.copy(models = remoteGroup.models + userGroup.models)
        } ?: userGroup
    } + remoteGroups.filterNot { it.groupId in userGroups.map(StoredModelGroup::groupId) }
}

private fun String.modelGroupId(serviceId: String, fallbackGroupId: String?): String {
    val segments = split('-').filter(String::isNotBlank)
    val groupSize = if (serviceId.equals("mimo", ignoreCase = true)) 3 else 2
    return if (segments.size >= groupSize) {
        segments.take(groupSize).joinToString("-")
    } else {
        fallbackGroupId ?: this
    }
}

private fun String.hasModelType(type: String): Boolean =
    split('-').any { it.equals(type, ignoreCase = true) }

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
                    model.copy(isStt = default.isStt, isTts = default.isTts)
                } ?: model
            } + defaults.models.filterNot { it.modelId in existingModelIds },
        )
    }
    return mergedExisting + defaultGroups.filterNot { it.groupId in existingGroupIds }
}
