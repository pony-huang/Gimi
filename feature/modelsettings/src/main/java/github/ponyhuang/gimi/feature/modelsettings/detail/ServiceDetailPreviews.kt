package github.ponyhuang.gimi.feature.modelsettings.detail

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection

/** 详情页预览 fixture；仅用于 @Preview，不参与生产逻辑。 */
internal object PreviewDrafts {

    fun builtInTemplate(): ModelServiceTemplate = ModelServiceTemplate(
        id = "openai",
        name = "OpenAI",
        connections = listOf(
            TemplateConnection(
                capability = ServiceCapability.Conversation,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
            ),
            TemplateConnection(
                capability = ServiceCapability.Recognition,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
            ),
            TemplateConnection(
                capability = ServiceCapability.Synthesis,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "https://api.openai.com/v1"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("http", "https"),
            ),
            TemplateConnection(
                capability = ServiceCapability.Realtime,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions),
                defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to "wss://api.openai.com/v1/realtime"),
                endpointPolicy = EndpointPolicy.VendorManaged,
                endpointSchemes = setOf("wss"),
            ),
        ),
    )

    fun minimaxTemplate(): ModelServiceTemplate = ModelServiceTemplate(
        id = "minimax",
        name = "MiniMax",
        connections = listOf(
            TemplateConnection(
                capability = ServiceCapability.Conversation,
                protocols = listOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic),
                defaultEndpoints = mapOf(
                    ApiProtocol.OpenaiChatcompletions to "https://api.minimaxi.com/v1",
                    ApiProtocol.Anthropic to "https://api.minimaxi.com/anthropic",
                ),
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
            ),
        ),
    )

    fun builtIn(): ModelServiceDraft {
        val template = builtInTemplate()
        return ModelServiceDraft(
            instanceId = "instance-openai",
            templateId = "openai",
            name = "OpenAI 个人",
            isOfficialToolsEnabled = true,
            sharedApiKey = "sk-preview-only",
            connections = template.connections.mapIndexed { index, connection ->
                ServiceConnection(
                    id = "conn-${index}",
                    capability = connection.capability,
                    protocol = connection.protocols.first(),
                    endpoint = connection.defaultEndpoint(connection.protocols.first()),
                    credential = if (index == 1) CredentialSource.Independent("sk-independent") else CredentialSource.Shared,
                    models = listOf(Model("gpt-4.1", "gpt-4.1")),
                )
            },
        )
    }

    fun minimaxDraft(): ModelServiceDraft = ModelServiceDraft(
        instanceId = "instance-minimax",
        templateId = "minimax",
        name = "MiniMax",
        isOfficialToolsEnabled = true,
        sharedApiKey = "sk-preview-only",
        connections = minimaxTemplate().connections.map { connection ->
            ServiceConnection(
                id = "conn-${connection.capability.name.lowercase()}",
                capability = connection.capability,
                protocol = connection.protocols.first(),
                endpoint = connection.defaultEndpoint(connection.protocols.first()),
                models = listOf(Model("MiniMax-Text-01", "MiniMax-Text-01")),
            )
        },
    )

    fun connectionEditors(): List<ConnectionEditor> {
        val template = builtInTemplate()
        return builtIn().connections.map { connection ->
            val templateConnection = template.connection(connection.capability) ?: return@map ConnectionEditor(
                connection = connection,
                endpointPolicy = EndpointPolicy.UserConfigured,
                endpointSchemes = setOf("http", "https"),
                supportsWorkspace = false,
                protocols = listOf(connection.protocol),
                test = ConnectionTestState(draftVersion = 1, result = ServiceConnectionResult.Succeeded),
            )
            ConnectionEditor(
                connection = connection,
                endpointPolicy = templateConnection.endpointPolicy,
                endpointSchemes = templateConnection.endpointSchemes,
                supportsWorkspace = templateConnection.supportsWorkspace,
                protocols = templateConnection.protocols,
                test = ConnectionTestState(draftVersion = 1, result = ServiceConnectionResult.Succeeded),
            )
        }
    }

    fun toolRows(): List<OfficialToolRow> {
        val support = github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport()
        return support.all.filter { it.templateId == "minimax" }.map { spec ->
            OfficialToolRow(spec = spec, isSupportedByProtocol = spec.protocols.contains(ApiProtocol.Anthropic))
        }
    }

    fun suggestions(): List<DefaultModelSuggestion> = listOf(
        DefaultModelSuggestion(
            role = DefaultModelRole.SpeechRecognition,
            roleLabelRes = github.ponyhuang.gimi.feature.modelsettings.R.string.modelsettings_role_recognition,
            selection = ModelSelection("instance-openai", "conn-1", "whisper-1"),
            modelLabel = "whisper-1 · OpenAI 个人",
        ),
        DefaultModelSuggestion(
            role = DefaultModelRole.SpeechSynthesis,
            roleLabelRes = github.ponyhuang.gimi.feature.modelsettings.R.string.modelsettings_role_synthesis,
            selection = ModelSelection("instance-openai", "conn-2", "gpt-4o-mini-tts"),
            modelLabel = "gpt-4o-mini-tts · OpenAI 个人",
        ),
    )
}

@Suppress("unused")
private val unusedModelCapability = ModelCapability.LLM
