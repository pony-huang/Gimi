package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.withDefaultAttachmentCapabilities

fun LLMModelProvider.toDomain(): LLMModelSetting = LLMModelSetting(
    id = serviceId,
    name = serviceName,
    isAdded = isAdded,
    isEnabled = isEnabled,
    isOfficialToolsEnabled = isOfficialToolsEnabled,
    apiKey = apiKey,
    apiBaseUrl = apiBaseUrl,
    apiProtocol = baseType.toDomain(),
    supportedProtocols = supportedBaseTypes.map { it.toDomain() },
    anthropicBaseUrl = anthropicBaseUrl,
    groups = lLMModelGroups.map(LLMModelGroup::toDomain),
    iconRes = iconRes,
    homepageUrl = homepageUrl,
    keyHelpUrl = keyHelpUrl,
    kind = kind,
    endpointSchemes = endpointSchemes,
    workspaceId = workspaceId,
)

fun Model.toData(): LLMModelItem = LLMModelItem(
    modelId = id,
    modelName = name,
    capabilities = capabilities,
    multimodal = multimodal,
)

fun ApiProtocol.toData(): ApiBaseType = when (this) {
    ApiProtocol.OpenaiChatcompletions -> ApiBaseType.OpenaiChatcompletions
    ApiProtocol.Anthropic -> ApiBaseType.Anthropic
    ApiProtocol.Gemini -> ApiBaseType.Gemini
}

fun ApiBaseType.toDomain(): ApiProtocol = when (this) {
    ApiBaseType.OpenaiChatcompletions -> ApiProtocol.OpenaiChatcompletions
    ApiBaseType.Anthropic -> ApiProtocol.Anthropic
    ApiBaseType.Gemini -> ApiProtocol.Gemini
}

private fun LLMModelGroup.toDomain(): ModelGroup = ModelGroup(
    id = groupId,
    name = groupName,
    models = models.map { item ->
        Model(
            id = item.modelId,
            name = item.modelName,
            capabilities = item.capabilities,
            multimodal = item.multimodal.withDefaultAttachmentCapabilities(),
        )
    },
)
