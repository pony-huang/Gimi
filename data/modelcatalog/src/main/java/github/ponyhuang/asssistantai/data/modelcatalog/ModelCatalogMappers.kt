package github.ponyhuang.asssistantai.data.modelcatalog

import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelGroup
import github.ponyhuang.asssistantai.data.LLMModelItem
import github.ponyhuang.asssistantai.data.LLMModelProvider
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection

fun LLMModelProvider.toDomain(): ModelService = ModelService(
    id = serviceId,
    name = serviceName,
    isEnabled = isEnabled,
    apiKey = apiKey,
    apiBaseUrl = apiBaseUrl,
    apiProtocol = baseType.toDomain(),
    anthropicBaseUrl = anthropicBaseUrl,
    groups = LLMModelGroups.map(LLMModelGroup::toDomain),
    iconRes = iconRes,
    homepageUrl = homepageUrl,
    keyHelpUrl = keyHelpUrl,
    docsUrl = docsUrl,
    modelsUrl = modelsUrl,
)

fun Model.toData(): LLMModelItem = LLMModelItem(
    modelId = id,
    modelName = name,
    isStt = isStt,
    isTts = isTts,
)

fun LLMModelSelection.toDomain(): ModelSelection = ModelSelection(
    serviceId = serviceId,
    groupId = groupId,
    modelId = modelId,
)

fun ModelSelection.toData(): LLMModelSelection = LLMModelSelection(
    serviceId = serviceId,
    groupId = groupId,
    modelId = modelId,
)

fun ApiProtocol.toData(): ApiBaseType = when (this) {
    ApiProtocol.Standard -> ApiBaseType.Standard
    ApiProtocol.Anthropic -> ApiBaseType.Anthropic
}

private fun ApiBaseType.toDomain(): ApiProtocol = when (this) {
    ApiBaseType.Standard -> ApiProtocol.Standard
    ApiBaseType.Anthropic -> ApiProtocol.Anthropic
}

private fun LLMModelGroup.toDomain(): ModelGroup = ModelGroup(
    id = groupId,
    name = groupName,
    models = models.map { item ->
        Model(
            id = item.modelId,
            name = item.modelName,
            isStt = item.isStt,
            isTts = item.isTts,
        )
    },
)
