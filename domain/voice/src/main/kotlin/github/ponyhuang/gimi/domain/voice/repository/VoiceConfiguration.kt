package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 语音对话方式：与语音处理链路正交的用户意图维度。
 */
enum class VoiceConversationStyle {
    /** Chatbot：绑定当前 Chatbot 会话，共用文字与语音角色预设。 */
    CHATBOT,

    /** 助手：接入完整 agent、工具授权与对话持久化。 */
    ASSISTANT,
}

/**
 * 语音处理链路：与对话方式正正交的引擎维度。
 */
enum class VoicePipeline {
    /** 级联：识别 → 对话生成 → 合成。 */
    CASCADED,

    /**
     * 半级联：音频直接进入理解模型、回答文本交给合成。
     * 暂未接入具体业务（尚无已核验的音频输入适配器），界面置灰展示，
     * 运行配置显式拒绝该链路；待候选能力核验通过后再放开。
     */
    SEMI_CASCADED,

    /** 原生实时：厂商全双工会话。 */
    NATIVE_REALTIME,
}

/**
 * 语音配置的单字段覆盖：继承标记与明确值分开表示。
 *
 * [Inherit] 表示该字段未在对话内覆盖，有效值随全局默认实时解析；
 * [Value] 表示对话内明确选择；恢复默认即移除覆盖回到 [Inherit]。
 */
sealed interface VoiceFieldOverride<out T> {
    /** 继承全局默认。 */
    data object Inherit : VoiceFieldOverride<Nothing>

    /** 对话内明确覆盖值。 */
    data class Value<T>(val value: T) : VoiceFieldOverride<T>
}

/** 覆盖值存在时返回它，否则回退到 [default]（可为 null 表示无默认）。 */
fun <T> VoiceFieldOverride<T>.orElse(default: T?): T? = when (this) {
    is VoiceFieldOverride.Value -> value
    VoiceFieldOverride.Inherit -> default
}

/** 该字段是否为对话内明确覆盖。 */
val <T> VoiceFieldOverride<T>.isOverridden: Boolean
    get() = this is VoiceFieldOverride.Value

/**
 * 可在对话内覆盖、也可恢复默认（回到继承）的语音配置字段。
 * 每条链路各自保留配置，字段按链路前缀分组。
 */
enum class VoiceConfigField {
    /** 语音处理链路。 */
    PIPELINE,

    /** 级联识别模型。 */
    CASCADED_RECOGNITION_MODEL,

    /** 级联合成模型。 */
    CASCADED_SYNTHESIS_MODEL,

    /** 级联合成音色。 */
    CASCADED_VOICE,

    /** 半级联音频理解模型。 */
    SEMI_CASCADED_UNDERSTANDING_MODEL,

    /** 半级联合成模型。 */
    SEMI_CASCADED_SYNTHESIS_MODEL,

    /** 半级联合成音色。 */
    SEMI_CASCADED_VOICE,

    /** 原生实时模型。 */
    REALTIME_MODEL,

    /** 原生实时会话音色。 */
    REALTIME_VOICE,
}

/**
 * 语音专属全局偏好：[VoiceGlobalDefaultsRepository] 唯一可写的字段集合。
 *
 * 模型目录维护的识别/合成/Realtime 默认模型不在其中，避免调用方误以为
 * 修改聚合视图里的模型字段就能写入默认值。
 *
 * @property pipeline 默认语音处理链路。
 * @property semiCascadedUnderstandingModel 半级联音频理解默认模型。
 * @property semiCascadedSynthesisModel 半级联合成默认模型。
 */
data class VoiceGlobalPreference(
    val pipeline: VoicePipeline = VoicePipeline.CASCADED,
    val semiCascadedUnderstandingModel: ModelSelection? = null,
    val semiCascadedSynthesisModel: ModelSelection? = null,
)

/**
 * 语音全局默认：按链路分别保存配置；对话未覆盖字段继承于此。
 *
 * 级联识别/合成与原生实时模型沿用模型目录的既有默认选择（识别默认同时服务
 * 按住说话转写等非通话场景，不在语音偏好内重复存储）；链路与
 * 半级联模型属于语音专属偏好。
 *
 * @property pipeline 默认语音处理链路。
 * @property cascadedRecognitionModel 级联识别默认模型（模型目录 STT 默认）。
 * @property cascadedSynthesisModel 级联合成默认模型（模型目录 TTS 默认）。
 * @property semiCascadedUnderstandingModel 半级联音频理解默认模型。
 * @property semiCascadedSynthesisModel 半级联合成默认模型。
 * @property realtimeModel 原生实时默认模型（模型目录 Realtime 默认）。
 */
data class VoiceGlobalDefaults(
    val pipeline: VoicePipeline = VoicePipeline.CASCADED,
    val cascadedRecognitionModel: ModelSelection? = null,
    val cascadedSynthesisModel: ModelSelection? = null,
    val semiCascadedUnderstandingModel: ModelSelection? = null,
    val semiCascadedSynthesisModel: ModelSelection? = null,
    val realtimeModel: ModelSelection? = null,
)

/**
 * 对话级语音配置覆盖：只记录对话内明确选择的字段，其余继承全局默认。
 * 对话内修改仅作用于该对话；重新进入对话恢复其覆盖。
 *
 * @property conversationId 覆盖所属对话。
 */
data class ConversationVoiceOverrides(
    val conversationId: String,
    val pipeline: VoiceFieldOverride<VoicePipeline> = VoiceFieldOverride.Inherit,
    val cascadedRecognitionModel: VoiceFieldOverride<ModelSelection> = VoiceFieldOverride.Inherit,
    val cascadedSynthesisModel: VoiceFieldOverride<ModelSelection> = VoiceFieldOverride.Inherit,
    val cascadedVoice: VoiceFieldOverride<String> = VoiceFieldOverride.Inherit,
    val semiCascadedUnderstandingModel: VoiceFieldOverride<ModelSelection> = VoiceFieldOverride.Inherit,
    val semiCascadedSynthesisModel: VoiceFieldOverride<ModelSelection> = VoiceFieldOverride.Inherit,
    val semiCascadedVoice: VoiceFieldOverride<String> = VoiceFieldOverride.Inherit,
    val realtimeModel: VoiceFieldOverride<ModelSelection> = VoiceFieldOverride.Inherit,
    val realtimeVoice: VoiceFieldOverride<String> = VoiceFieldOverride.Inherit,
) {
    /** 当前明确覆盖的字段集合。 */
    val overriddenFields: Set<VoiceConfigField>
        get() = buildSet {
            if (pipeline.isOverridden) add(VoiceConfigField.PIPELINE)
            if (cascadedRecognitionModel.isOverridden) add(VoiceConfigField.CASCADED_RECOGNITION_MODEL)
            if (cascadedSynthesisModel.isOverridden) add(VoiceConfigField.CASCADED_SYNTHESIS_MODEL)
            if (cascadedVoice.isOverridden) add(VoiceConfigField.CASCADED_VOICE)
            if (semiCascadedUnderstandingModel.isOverridden) add(VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL)
            if (semiCascadedSynthesisModel.isOverridden) add(VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL)
            if (semiCascadedVoice.isOverridden) add(VoiceConfigField.SEMI_CASCADED_VOICE)
            if (realtimeModel.isOverridden) add(VoiceConfigField.REALTIME_MODEL)
            if (realtimeVoice.isOverridden) add(VoiceConfigField.REALTIME_VOICE)
        }

    /** 是否存在任一明确覆盖。 */
    val hasAnyOverride: Boolean get() = overriddenFields.isNotEmpty()
}

/**
 * 对话语音配置编辑草稿：以编辑开始时的全局默认快照为基线，应用时检测冲突。
 *
 * @property overrides 草稿覆盖内容。
 * @property baseline 编辑开始时的全局默认快照；应用时与当前快照比较，
 *   全局默认在编辑期间变化即视为冲突，草稿保留并要求重新确认。
 */
data class VoiceConfigDraft(
    val overrides: ConversationVoiceOverrides,
    val baseline: VoiceGlobalDefaults,
)

/** 草稿应用结果；除 [Applied] 外草稿一律保留。 */
sealed interface VoiceApplyResult {
    /** 应用成功。 */
    data object Applied : VoiceApplyResult

    /** 与已提交内容一致：未产生新提交（同一内容重复应用只提交一次）。 */
    data object Unchanged : VoiceApplyResult

    /** 全局默认在编辑期间变化：需重新确认继承状态后再应用。 */
    data object Conflict : VoiceApplyResult

    /** 激活链路必需项缺失或失效；缺项集合供界面定位。 */
    data class Invalid(val missing: Set<VoiceConfigField>) : VoiceApplyResult
}

/**
 * 音色解析结果。
 *
 * @property voiceId 生效音色 ID；null 表示采用所选模型的厂商默认音色。
 * @property substituted 记忆或覆盖音色失效后发生替代；界面据此说明替代原因。
 */
data class VoiceResolution(
    val voiceId: String? = null,
    val substituted: Boolean = false,
)

/**
 * 有效语音配置：全局默认与对话覆盖按字段合并后的结果。
 * 各链路字段独立解析；[missingRequired] 只针对当前激活链路，其他链路草稿无需补齐。
 *
 * @property pipeline 生效语音处理链路。
 * @property cascadedRecognitionModel 级联识别模型。
 * @property cascadedSynthesisModel 级联合成模型。
 * @property cascadedVoice 级联合成音色解析。
 * @property semiCascadedUnderstandingModel 半级联音频理解模型。
 * @property semiCascadedSynthesisModel 半级联合成模型。
 * @property semiCascadedVoice 半级联合成音色解析。
 * @property realtimeModel 原生实时模型。
 * @property realtimeVoice 原生实时会话音色解析。
 * @property missingRequired 激活链路必需但缺失或已失效的字段。
 */
data class EffectiveVoiceConfig(
    val pipeline: VoicePipeline,
    val cascadedRecognitionModel: ModelSelection?,
    val cascadedSynthesisModel: ModelSelection?,
    val cascadedVoice: VoiceResolution,
    val semiCascadedUnderstandingModel: ModelSelection?,
    val semiCascadedSynthesisModel: ModelSelection?,
    val semiCascadedVoice: VoiceResolution,
    val realtimeModel: ModelSelection?,
    val realtimeVoice: VoiceResolution,
    val missingRequired: Set<VoiceConfigField>,
)

/**
 * 有效语音配置解析所需的外部查询；由数据层实现，领域层保持纯 Kotlin。
 */
interface VoiceConfigLookup {
    /**
     * 模型选择是否仍可用：实例、连接与模型存在且凭据可用（由目录连接解析统一收敛）。
     */
    fun isModelValid(selection: ModelSelection): Boolean

    /**
     * 音色对模型是否仍然合法。null 表示暂时无法核验（目录未就绪等），
     * 按合法处理，避免把可核验前的正常状态误报为失效替代。
     */
    fun isVoiceValid(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String): Boolean?

    /**
     * 该链路下该完整模型选择记忆的音色；无记忆返回 null（采用厂商默认音色）。
     */
    fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String?
}

internal val VoiceGlobalDefaults.effectiveSemiCascadedSynthesisModel: ModelSelection?
    get() = semiCascadedSynthesisModel ?: cascadedSynthesisModel

/**
 * 语音配置解析器：把全局默认与对话覆盖按字段合并为有效配置。
 *
 * 继承语义：覆盖值存在取覆盖值，否则取全局默认；音色先解析覆盖值再解析
 * 按模型记忆，均失效时采用厂商默认并标记替代，供界面说明原因。
 */
object VoiceConfigResolver {

    /** 合并全局默认与对话覆盖，解析有效配置。 */
    fun resolve(
        defaults: VoiceGlobalDefaults,
        overrides: ConversationVoiceOverrides,
        lookup: VoiceConfigLookup,
    ): EffectiveVoiceConfig {
        // 链路默认值非空，覆盖缺失时直接取默认，不需要不可达的兜底分支。
        val pipeline = when (val override = overrides.pipeline) {
            is VoiceFieldOverride.Value -> override.value
            VoiceFieldOverride.Inherit -> defaults.pipeline
        }

        val cascadedRecognition = overrides.cascadedRecognitionModel.orElse(defaults.cascadedRecognitionModel)
        val cascadedSynthesis = overrides.cascadedSynthesisModel.orElse(defaults.cascadedSynthesisModel)
        val semiUnderstanding = overrides.semiCascadedUnderstandingModel.orElse(defaults.semiCascadedUnderstandingModel)
        val semiSynthesis = overrides.semiCascadedSynthesisModel.orElse(defaults.effectiveSemiCascadedSynthesisModel)
        val realtime = overrides.realtimeModel.orElse(defaults.realtimeModel)

        val missing = when (pipeline) {
            VoicePipeline.CASCADED -> missingModels(
                lookup,
                VoiceConfigField.CASCADED_RECOGNITION_MODEL to cascadedRecognition,
                VoiceConfigField.CASCADED_SYNTHESIS_MODEL to cascadedSynthesis,
            )
            VoicePipeline.SEMI_CASCADED -> missingModels(
                lookup,
                VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL to semiSynthesis,
            )
            VoicePipeline.NATIVE_REALTIME -> missingModels(
                lookup,
                VoiceConfigField.REALTIME_MODEL to realtime,
            )
        }

        return EffectiveVoiceConfig(
            pipeline = pipeline,
            cascadedRecognitionModel = cascadedRecognition,
            cascadedSynthesisModel = cascadedSynthesis,
            cascadedVoice = resolveVoice(VoicePipeline.CASCADED, cascadedSynthesis, overrides.cascadedVoice, lookup),
            semiCascadedUnderstandingModel = semiUnderstanding,
            semiCascadedSynthesisModel = semiSynthesis,
            semiCascadedVoice = resolveVoice(VoicePipeline.SEMI_CASCADED, semiSynthesis, overrides.semiCascadedVoice, lookup),
            realtimeModel = realtime,
            realtimeVoice = resolveVoice(VoicePipeline.NATIVE_REALTIME, realtime, overrides.realtimeVoice, lookup),
            missingRequired = missing,
        )
    }

    /**
     * 校验草稿：激活链路必需项全部有效才能应用；返回缺失字段，空集合表示可应用。
     */
    fun validateDraft(
        defaults: VoiceGlobalDefaults,
        draft: VoiceConfigDraft,
        lookup: VoiceConfigLookup,
    ): Set<VoiceConfigField> = resolve(defaults, draft.overrides, lookup).missingRequired

    /** 覆盖值优先于按模型记忆；模型缺失或失效时不再解析音色（缺项已单列）。 */
    private fun resolveVoice(
        pipeline: VoicePipeline,
        selection: ModelSelection?,
        override: VoiceFieldOverride<String>,
        lookup: VoiceConfigLookup,
    ): VoiceResolution {
        if (selection == null || !lookup.isModelValid(selection)) return VoiceResolution()
        var substituted = false
        if (override is VoiceFieldOverride.Value) {
            when (lookup.isVoiceValid(pipeline, selection, override.value)) {
                true, null -> return VoiceResolution(override.value)
                // 覆盖音色失效：继续尝试按模型记忆的音色，而不是直接丢回厂商默认。
                false -> substituted = true
            }
        }
        val remembered = lookup.rememberedVoice(pipeline, selection)
            ?: return VoiceResolution(substituted = substituted)
        return when (lookup.isVoiceValid(pipeline, selection, remembered)) {
            false -> VoiceResolution(substituted = true)
            else -> VoiceResolution(remembered, substituted = substituted)
        }
    }

    private fun missingModels(
        lookup: VoiceConfigLookup,
        vararg fields: Pair<VoiceConfigField, ModelSelection?>,
    ): Set<VoiceConfigField> = fields.mapNotNullTo(mutableSetOf()) { (field, selection) ->
        field.takeIf { selection == null || !lookup.isModelValid(selection) }
    }
}
