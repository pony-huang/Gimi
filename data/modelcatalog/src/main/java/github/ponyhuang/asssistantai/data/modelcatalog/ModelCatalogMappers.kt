package github.ponyhuang.asssistantai.data.modelcatalog

import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.model.withDefaultAttachmentCapabilities

fun LLMModelProvider.toDomain(): LLMModelSetting = LLMModelSetting(
    id = serviceId,
    name = serviceName,
    isEnabled = isEnabled,
    apiKey = apiKey,
    apiBaseUrl = apiBaseUrl,
    apiProtocol = baseType.toDomain(),
    supportedProtocols = supportedBaseTypes.map { it.toDomain() },
    anthropicBaseUrl = anthropicBaseUrl,
    groups = lLMModelGroups.map(LLMModelGroup::toDomain),
    iconRes = iconRes,
    homepageUrl = homepageUrl,
    keyHelpUrl = keyHelpUrl,
    docsUrl = docsUrl,
    modelsUrl = modelsUrl
)

fun Model.toData(): LLMModelItem = LLMModelItem(
    modelId = id,
    modelName = name,
    isStt = isStt,
    isTts = isTts,
    capabilities = capabilities,
)

fun ApiProtocol.toData(): ApiBaseType = when (this) {
    ApiProtocol.Standard -> ApiBaseType.Standard
    ApiProtocol.Anthropic -> ApiBaseType.Anthropic
}

fun ApiBaseType.toDomain(): ApiProtocol = when (this) {
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
            capabilities = item.capabilities.withDefaultAttachmentCapabilities(),
        )
    },
)
