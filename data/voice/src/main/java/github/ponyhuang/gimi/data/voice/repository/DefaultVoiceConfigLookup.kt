package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigLookup
import github.ponyhuang.gimi.domain.voice.repository.VoiceMemoryRepository
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 有效语音配置解析的数据侧查询：模型合法性收敛到目录连接解析（启用状态、
 * 凭据与存在性），原生实时音色目录用于确认已知音色，音色记忆交由记忆仓库。
 */
@Singleton
class DefaultVoiceConfigLookup @Inject constructor(
    private val catalog: ModelCatalogRepository,
    private val capabilities: VoiceCapabilityResolver,
    private val memory: VoiceMemoryRepository,
    private val voiceCatalog: VoiceCatalogResolver,
) : VoiceConfigLookup {
    override fun isModelValid(selection: ModelSelection): Boolean =
        catalog.resolveConnection(selection) != null

    override fun isVoiceValid(
        pipeline: VoicePipeline,
        selection: ModelSelection,
        voiceId: String,
    ): Boolean? = when (pipeline) {
        VoicePipeline.NATIVE_REALTIME -> {
            // 内置目录只确认已知音色，目录之外的字符串 ID 由厂商端校验。
            catalog.resolveConnection(selection)?.templateId
                ?.let(capabilities::realtimeSupport)
                ?.voicesFor(selection.modelId)
                ?.any { it.id == voiceId }
                ?.takeIf { it }
        }
        // 级联/半级联合成音色的权威目录可能来自厂商列表接口，同步路径整体不核验；
        // 但跨厂商残留可精确判定：音色命中其他厂商的封闭内置表即为失效，触发
        // 领域层替代回落（厂商默认 + substituted 标记），不再把必败请求发给厂商。
        // 复刻音色等开放集 ID 与目录未就绪时仍返回 null，按合法交由厂商端校验。
        else -> {
            val templateId = catalog.resolveConnection(selection)?.templateId
            val owner = voiceCatalog.builtInVoiceOwner(voiceId)
            if (templateId == null || owner == null) null else owner == templateId
        }
    }

    override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? =
        memory.rememberedVoice(pipeline, selection)
}
