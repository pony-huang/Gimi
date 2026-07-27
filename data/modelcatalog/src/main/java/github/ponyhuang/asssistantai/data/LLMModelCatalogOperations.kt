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
 * 用最新的远端语言模型快照更新本地目录，同时保留本地不可由该快照完整描述的数据。
 *
 * 核心约束：
 * 1. 用户手动添加的模型优先于同 ID 的远端模型，且必须保留原分组。
 * 2. `/models` 通常只覆盖语言模型，因此未返回的既有 STT/TTS 模型不能被当作已下架模型删除。
 * 3. 新远端模型按 ID 自动分组；已有语音模型保留原分组，避免内置语音分组被拆散。
 * 4. 普通模型格式为 `{名字}-{版本}-{类型}`，MiMo 格式为
 *    `{名字}-{版本}-{版本或类型}-{具体类型}`。
 */
internal fun syncStoredRemoteModels(
    existingGroups: List<StoredModelGroup>,
    serviceId: String,
    serviceName: String,
    models: List<LLMModelItem>,
): List<StoredModelGroup> {
    // 用户模型不参与远端快照替换。同 ID 冲突时跳过远端项，确保用户配置不被刷新覆盖。
    val userModelIds = existingGroups
        .flatMap { it.models }
        .filter { it.source == StoredModelSource.USER }
        .mapTo(mutableSetOf()) { it.modelId }
    val existingById = existingGroups.flatMap { it.models }.associateBy { it.modelId }
    val fetchedRemoteModels = models
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
    val fetchedRemoteModelIds = fetchedRemoteModels.mapTo(mutableSetOf()) { it.modelId }

    // 模型列表接口并非语音目录的权威来源。保留接口未返回的既有语音模型，
    // 否则刷新 MiniMax 等服务的语言模型时会误删内置 TTS/STT 模型。
    val preservedSpeechModels = existingGroups
        .flatMap { it.models }
        .filter { model ->
            model.source == StoredModelSource.REMOTE &&
                (model.isStt || model.isTts) &&
                model.modelId !in fetchedRemoteModelIds
        }
    val remoteModels = fetchedRemoteModels + preservedSpeechModels
    val userGroups = existingGroups.mapNotNull { group ->
        val userModels = group.models.filter { it.source == StoredModelSource.USER }
        group.takeIf { userModels.isNotEmpty() }?.copy(models = userModels)
    }

    // 建立旧模型到旧分组的索引：已有语音模型优先沿用原分组；
    // 只有普通远端模型才根据 ID 重新计算分组，以支持厂商新增版本而无需维护静态清单。
    val existingGroupIdByModelId = existingGroups.flatMap { group ->
        group.models.map { model -> model.modelId to group.groupId }
    }.toMap()
    val remoteGroups = remoteModels
        .groupBy { model ->
            val existingGroupId = existingGroupIdByModelId[model.modelId]
            if ((model.isStt || model.isTts) && existingGroupId != null) {
                existingGroupId
            } else {
                model.modelId.modelGroupId(
                    serviceId = serviceId,
                    fallbackGroupId = existingGroupId ?: existingGroups.firstOrNull()?.groupId,
                )
            }
        }
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

    // 同一分组同时包含远端与用户模型时合并两者；用户模型追加在后且不会被远端快照删除。
    val remoteByGroupId = remoteGroups.associateBy { it.groupId }
    return userGroups.map { userGroup ->
        remoteByGroupId[userGroup.groupId]?.let { remoteGroup ->
            remoteGroup.copy(models = remoteGroup.models + userGroup.models)
        } ?: userGroup
    } + remoteGroups.filterNot { it.groupId in userGroups.map(StoredModelGroup::groupId) }
}

private fun String.modelGroupId(serviceId: String, fallbackGroupId: String?): String {
    val segments = split('-').filter(String::isNotBlank)
    return when {
        serviceId.equals("mimo", ignoreCase = true) && segments.size >= 3 ->
            segments.take(3).joinToString("-")
        segments.size > 2 -> segments.take(2).joinToString("-")
        segments.size == 2 -> segments.first()
        else -> fallbackGroupId ?: this
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
