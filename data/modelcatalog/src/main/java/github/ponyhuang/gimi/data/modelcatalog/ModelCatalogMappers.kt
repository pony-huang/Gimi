package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.withDefaultAttachmentCapabilities

fun LLMModelProvider.toDomain(): LLMModelSetting = LLMModelSetting(
    id = serviceId,
    name = serviceName,
    isAdded = isAdded,
    isEnabled = isEnabled,
    isOfficialToolsEnabled = isOfficialToolsEnabled,
    isFileUploadEnabled = isFileUploadEnabled,
    fileUploadRetentionDays = fileUploadRetentionDays,
    apiKey = apiKey,
    apiBaseUrl = apiBaseUrl,
    apiProtocol = baseType.toDomain(),
    supportedProtocols = supportedBaseTypes.map { it.toDomain() },
    anthropicBaseUrl = anthropicBaseUrl,
    groups = lLMModelGroups.map(LLMModelGroup::toDomain),
    iconRes = iconRes,
    homepageUrl = homepageUrl,
    keyHelpUrl = keyHelpUrl,
)

fun Model.toData(): LLMModelItem = LLMModelItem(
    modelId = id,
    modelName = name,
    isStt = isStt,
    isTts = isTts,
    capabilities = capabilities,
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
            isStt = item.isStt,
            isTts = item.isTts,
            capabilities = item.capabilities.withDefaultAttachmentCapabilities(),
        )
    },
)
