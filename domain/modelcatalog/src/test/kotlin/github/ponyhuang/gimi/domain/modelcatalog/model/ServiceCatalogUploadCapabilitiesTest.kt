package github.ponyhuang.gimi.domain.modelcatalog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 实例级"文件上传"开关对文档能力的推导规则。 */
class ServiceCatalogUploadCapabilitiesTest {
    private val model = Model("model", "Model")

    @Test
    fun switchDisabledDerivesPathTextDocumentCapability() {
        val instance = uploadInstance(isFileUploadEnabled = false)
        val derived = instance.withDerivedUploadCapabilities()
        for (connection in derived.connections) {
            for (candidate in connection.models) {
                val document = candidate.multimodal.documentInput
                assertEquals(FileIngestMode.PATH_TEXT, document?.ingestMode)
                assertEquals(null, document?.maxInlineBytes)
            }
        }
    }

    @Test
    fun switchEnabledDerivesVendorFileReferenceCapability() {
        val derived = uploadInstance(isFileUploadEnabled = true).withDerivedUploadCapabilities()
        for (connection in derived.connections) {
            for (candidate in connection.models) {
                val document = candidate.multimodal.documentInput
                assertEquals(FileIngestMode.FILE_REFERENCE, document?.ingestMode)
                assertEquals(100L * 1024 * 1024, document?.maxUploadBytes)
            }
        }
    }

    @Test
    fun nonUploadTemplatesKeepTheirDeclaredCapabilities() {
        val inlineDocumentModel = model.copy(
            multimodal = MultimodalCapabilities(documentInput = DocumentInputCapability()),
        )
        val instance = uploadInstance(
            templateId = "kimi",
            isFileUploadEnabled = false,
            baseModel = inlineDocumentModel,
        )
        assertSame(instance, instance.withDerivedUploadCapabilities())
        assertTrue(
            instance.connections.first().models.first().multimodal.documentInput!!.ingestMode ==
                FileIngestMode.INLINE,
        )
    }

    @Test
    fun alreadyDerivedInstancesAreReturnedUnchanged() {
        val disabled = uploadInstance(isFileUploadEnabled = false).withDerivedUploadCapabilities()
        assertSame(disabled, disabled.withDerivedUploadCapabilities())
        val enabled = uploadInstance(isFileUploadEnabled = true).withDerivedUploadCapabilities()
        assertSame(enabled, enabled.withDerivedUploadCapabilities())
    }

    @Test
    fun nonConversationConnectionsDoNotDeriveDocumentCapability() {
        val instance = ModelServiceInstance(
            id = "instance-deepseek",
            name = "DeepSeek",
            templateId = "deepseek",
            isEnabled = true,
            isOfficialToolsEnabled = false,
            isFileUploadEnabled = true,
            sharedApiKey = "key",
            connections = listOf(
                ServiceConnection(
                    id = "conn-conversation-1",
                    capability = ServiceCapability.Conversation,
                    protocol = ApiProtocol.Anthropic,
                    endpoint = "https://api.deepseek.com/anthropic",
                    models = listOf(model),
                ),
                ServiceConnection(
                    id = "conn-recognition-1",
                    capability = ServiceCapability.Recognition,
                    protocol = ApiProtocol.OpenaiChatcompletions,
                    endpoint = "https://api.deepseek.com",
                    models = listOf(Model("deepseek-asr", "deepseek-asr", capabilities = setOf(ModelCapability.STT))),
                ),
            ),
        ).withDerivedUploadCapabilities()

        assertEquals(
            FileIngestMode.FILE_REFERENCE,
            instance.connection(ServiceCapability.Conversation)!!.models.single().multimodal.documentInput?.ingestMode,
        )
        assertEquals(
            null,
            instance.connection(ServiceCapability.Recognition)!!.models.single().multimodal.documentInput,
        )
    }

    private fun uploadInstance(
        templateId: String = "deepseek",
        isFileUploadEnabled: Boolean,
        baseModel: Model = model,
    ) = ModelServiceInstance(
        id = "instance-$templateId",
        name = templateId,
        templateId = templateId,
        isEnabled = true,
        isOfficialToolsEnabled = false,
        isFileUploadEnabled = isFileUploadEnabled,
        sharedApiKey = "key",
        connections = listOf(
            ServiceConnection(
                id = "conn-conversation-1",
                capability = ServiceCapability.Conversation,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://example.com",
                models = listOf(baseModel, baseModel.copy(id = "model-2")),
            ),
        ),
    )
}
