package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 语音全局默认仓库：对话方式、链路与各链路默认配置的读取入口。
 *
 * 级联识别/合成与原生实时模型的持久化沿用模型目录仓库；本仓库把它们与
 * 语音专属偏好（对话方式、链路、半级联模型）聚合为一份 [VoiceGlobalDefaults]。
 */
interface VoiceGlobalDefaultsRepository {
    /** 当前全局默认；全局默认变化时对话继承状态随之实时变化。 */
    val defaults: StateFlow<VoiceGlobalDefaults>

    /**
     * 更新语音专属偏好字段（对话方式、链路、半级联模型）。
     *
     * 参数只暴露本仓库真正持久化的字段：级联识别/合成与原生实时模型由模型目录
     * 仓库维护，传入 [VoiceGlobalDefaults] 会让这些字段的修改被静默丢弃。
     */
    suspend fun update(transform: (VoiceGlobalPreference) -> VoiceGlobalPreference)
}

/**
 * 对话语音覆盖仓库：按对话持久化字段覆盖，删除对话时清理。
 *
 * 应用以编辑基线做冲突检测，同一内容重复应用只提交一次；恢复默认移除
 * 覆盖回到继承状态，重复恢复幂等。
 */
interface VoiceOverrideRepository {
    /** 对话覆盖流；无覆盖时返回全继承的空覆盖。 */
    fun observe(conversationId: String): Flow<ConversationVoiceOverrides>

    /** 当前覆盖快照。 */
    fun current(conversationId: String): ConversationVoiceOverrides

    /**
     * 无法解析的已存配置被重置的次数；界面据此提示「语音配置已重建」，
     * 可读取的明确选择在部分解析时继续保留。
     */
    val rebuildCount: StateFlow<Int>

    /** 界面提示过重建后清零计数，避免重进页面重复提示。 */
    suspend fun acknowledgeRebuilds()

    /**
     * 原子应用草稿（整批修改一次提交，避免半写配置）。
     * - 与已提交内容一致 → [VoiceApplyResult.Unchanged]（重复应用同一内容只提交一次）。
     * - 否则持久化并返回 [VoiceApplyResult.Applied]。
     *
     * 激活链路必需项校验与全局基线冲突检测由 [ApplyVoiceConfigUseCase] 在调用前完成。
     */
    suspend fun apply(draft: VoiceConfigDraft): VoiceApplyResult

    /** 恢复默认：移除指定字段覆盖；重复恢复保持继承状态。 */
    suspend fun resetFields(conversationId: String, fields: Set<VoiceConfigField>)

    /** 删除对话时清理该对话的全部覆盖。 */
    suspend fun clear(conversationId: String)
}

/**
 * 模型音色记忆仓库：按链路与完整模型选择记忆音色。
 * 从模型 A 切到 B 再切回 A 时恢复 A 的合法音色；记忆失效时由解析器采用
 * 厂商默认并标记替代。
 */
interface VoiceMemoryRepository {
    /** 记忆音色；同一 (链路, 模型选择) 重复记忆以最后一次为准。 */
    suspend fun rememberVoice(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String)

    /** 查询记忆音色；无记忆返回 null（采用厂商默认音色）。 */
    fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String?
}

/**
 * 对话语音配置应用流程：必需项校验、基线冲突检测与提交的领域编排。
 *
 * 应用顺序：先比较全局基线（编辑期间草稿所继承的默认值变化即冲突），
 * 再用编辑基线校验激活链路必需项，最后原子提交；提交幂等由覆盖仓库保证。
 * 冲突先于校验：全局默认已经变了，缺项结论针对的是旧默认值，不能用来覆盖冲突提示。
 */
class ApplyVoiceConfigUseCase @Inject constructor(
    private val defaultsRepository: VoiceGlobalDefaultsRepository,
    private val overridesRepository: VoiceOverrideRepository,
    private val lookup: VoiceConfigLookup,
) {
    suspend operator fun invoke(draft: VoiceConfigDraft): VoiceApplyResult {
        val current = defaultsRepository.defaults.value
        // 只比较草稿实际继承、且激活链路真正用到的字段：被覆盖字段与其他链路
        // 的默认模型变化都不改变有效配置，不该判为冲突。
        if (relevantDefaults(current, draft.overrides) !=
            relevantDefaults(draft.baseline, draft.overrides)
        ) return VoiceApplyResult.Conflict
        val missing = VoiceConfigResolver.validateDraft(draft.baseline, draft, lookup)
        if (missing.isNotEmpty()) return VoiceApplyResult.Invalid(missing)
        return overridesRepository.apply(draft)
    }

    /** 草稿激活链路依赖的全局字段值；被覆盖字段以 null 占位，不参与冲突判定。 */
    private fun relevantDefaults(
        defaults: VoiceGlobalDefaults,
        overrides: ConversationVoiceOverrides,
    ): List<Any?> {
        val pipeline = when (val override = overrides.pipeline) {
            is VoiceFieldOverride.Value -> override.value
            VoiceFieldOverride.Inherit -> defaults.pipeline
        }
        val common = listOf(
            defaults.pipeline.takeIf { !overrides.pipeline.isOverridden },
        )
        val models = when (pipeline) {
            VoicePipeline.CASCADED -> listOf(
                defaults.cascadedRecognitionModel.takeIf { !overrides.cascadedRecognitionModel.isOverridden },
                defaults.cascadedSynthesisModel.takeIf { !overrides.cascadedSynthesisModel.isOverridden },
            )
            VoicePipeline.SEMI_CASCADED -> listOf(
                defaults.semiCascadedUnderstandingModel.takeIf { !overrides.semiCascadedUnderstandingModel.isOverridden },
                defaults.effectiveSemiCascadedSynthesisModel.takeIf { !overrides.semiCascadedSynthesisModel.isOverridden },
            )
            VoicePipeline.NATIVE_REALTIME -> listOf(
                defaults.realtimeModel.takeIf { !overrides.realtimeModel.isOverridden },
            )
        }
        return common + models
    }
}
