package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.ConversationVoiceOverrides
import github.ponyhuang.gimi.domain.voice.repository.VoiceApplyResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigDraft
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoiceFieldOverride
import github.ponyhuang.gimi.domain.voice.repository.VoiceOverrideRepository
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 对话语音覆盖的持久化：每个对话一条 JSON 记录，整批应用一次写入保证原子性。
 *
 * 读取时逐字段容忍损坏：无法解析的枚举/模型字段按继承处理，可读取的明确选择
 * 继续保留；损坏条目会立即修复或删盘，避免每次冷启动重复递增 [rebuildCount]。
 * 空白对话 ID 不参与持久化：无会话绑定时不能把覆盖写到共享 key 上。
 */
@Singleton
class VoiceOverridePreferences(
    context: Context,
    scope: CoroutineScope,
    private val repairDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VoiceOverrideRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), Dispatchers.IO)

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val writeMutex = Mutex()

    private val loaded = readAll()
    private val mutableOverrides = MutableStateFlow(loaded.overrides)
    private val mutableRebuildCount = MutableStateFlow(loaded.rebuilds)

    init {
        // 冷启动修复写盘是磁盘 IO，切到 IO 执行，不阻塞注入线程。
        // 修复基于构造时的快照：必须走 writeMutex 并在写前复核原值仍是待修复内容，
        // 否则用户随后的 apply/clear 会被旧快照复活或覆盖（内存新值、磁盘旧值）。
        if (loaded.repairs.isNotEmpty()) {
            scope.launch(repairDispatcher) {
                writeMutex.withLock {
                    loaded.repairs.forEach { repair ->
                        if (preferences.getString(repair.key, null) != repair.expectedRaw) return@forEach
                        commit {
                            if (repair.replacement == null) remove(repair.key)
                            else putString(repair.key, repair.replacement)
                        }
                    }
                }
            }
        }
    }

    override val rebuildCount: StateFlow<Int> = mutableRebuildCount.asStateFlow()

    override fun observe(conversationId: String): Flow<ConversationVoiceOverrides> =
        mutableOverrides.map { stored(conversationId) }.distinctUntilChanged()

    override fun current(conversationId: String): ConversationVoiceOverrides = stored(conversationId)

    private fun stored(conversationId: String): ConversationVoiceOverrides =
        if (conversationId.isBlank()) empty(conversationId)
        else mutableOverrides.value[conversationId] ?: empty(conversationId)

    override suspend fun apply(draft: VoiceConfigDraft): VoiceApplyResult = writeMutex.withLock {
        val conversationId = draft.overrides.conversationId
        // 无会话绑定时不落盘：空白 key 会让所有未绑定会话共享同一份覆盖。
        if (conversationId.isBlank()) return@withLock VoiceApplyResult.Unchanged
        val existing = current(conversationId)
        // 重复应用同一内容只提交一次；比较的是整份覆盖，部分修改仍会提交。
        if (existing == draft.overrides) return@withLock VoiceApplyResult.Unchanged
        write(draft.overrides)
        VoiceApplyResult.Applied
    }

    override suspend fun resetFields(conversationId: String, fields: Set<VoiceConfigField>) = writeMutex.withLock {
        if (conversationId.isBlank()) return@withLock
        val existing = current(conversationId)
        val reset = existing.without(fields)
        // 重复恢复幂等：没有可移除的覆盖时不产生写入。
        if (reset == existing) return@withLock
        write(reset)
    }

    override suspend fun clear(conversationId: String) = writeMutex.withLock {
        if (conversationId.isBlank() || conversationId !in mutableOverrides.value) return@withLock
        // 同步提交：删盘失败必须上抛，不能让内存看起来已清理而下次冷启动又恢复覆盖。
        if (!withContext(Dispatchers.IO) { commit { remove(conversationId) } }) {
            throw IOException("Failed to clear voice overrides")
        }
        mutableOverrides.value = mutableOverrides.value - conversationId
    }

    override suspend fun acknowledgeRebuilds() = writeMutex.withLock {
        mutableRebuildCount.value = 0
    }

    private fun empty(conversationId: String) = ConversationVoiceOverrides(conversationId)

    /** 移除指定字段覆盖；未列出的字段保持不变。 */
    private fun ConversationVoiceOverrides.without(fields: Set<VoiceConfigField>) = copy(
        pipeline = pipeline.overrideless(fields, VoiceConfigField.PIPELINE),
        cascadedRecognitionModel = cascadedRecognitionModel.overrideless(fields, VoiceConfigField.CASCADED_RECOGNITION_MODEL),
        cascadedSynthesisModel = cascadedSynthesisModel.overrideless(fields, VoiceConfigField.CASCADED_SYNTHESIS_MODEL),
        cascadedVoice = cascadedVoice.overrideless(fields, VoiceConfigField.CASCADED_VOICE),
        semiCascadedUnderstandingModel = semiCascadedUnderstandingModel.overrideless(
            fields, VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL,
        ),
        semiCascadedSynthesisModel = semiCascadedSynthesisModel.overrideless(
            fields, VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL,
        ),
        semiCascadedVoice = semiCascadedVoice.overrideless(fields, VoiceConfigField.SEMI_CASCADED_VOICE),
        realtimeModel = realtimeModel.overrideless(fields, VoiceConfigField.REALTIME_MODEL),
        realtimeVoice = realtimeVoice.overrideless(fields, VoiceConfigField.REALTIME_VOICE),
    )

    private fun <T> VoiceFieldOverride<T>.overrideless(
        fields: Set<VoiceConfigField>,
        field: VoiceConfigField,
    ): VoiceFieldOverride<T> = if (field in fields) VoiceFieldOverride.Inherit else this

    /** 同步落盘：默认 apply 是异步且失败不抛，草稿会被误当成已保存。 */
    private fun commit(block: SharedPreferences.Editor.() -> Unit): Boolean {
        val editor = preferences.edit()
        block(editor)
        return editor.commit()
    }

    private suspend fun write(overrides: ConversationVoiceOverrides) {
        // 先落盘再更新内存流：写入失败时异常上抛（草稿保留），内存与存储保持一致。
        // commit 是磁盘 IO，切到 IO 调度器执行，避免调用方（通常是 Main）被阻塞。
        val persisted = withContext(Dispatchers.IO) {
            commit { putString(overrides.conversationId, gson.toJson(toDto(overrides))) }
        }
        if (!persisted) throw IOException("Failed to persist voice overrides")
        mutableOverrides.value = mutableOverrides.value + (overrides.conversationId to overrides)
    }

    /**
     * 一次冷启动读取的快照：内存覆盖表、累计修复数，以及待异步落盘的修复写操作。
     * 修复写只在磁盘损坏时出现，延迟到 IO 调度器执行不影响内存状态的一致性。
     * [expectedRaw] 是读快照时的原值，写前复核可避免覆盖用户在此期间的修改；
     * [replacement] 为 null 表示删盘。
     */
    private data class LoadedState(
        val overrides: Map<String, ConversationVoiceOverrides>,
        val rebuilds: Int,
        val repairs: List<PendingRepair> = emptyList(),
    )

    private data class PendingRepair(
        val key: String,
        val expectedRaw: String,
        val replacement: String?,
    )

    private fun readAll(): LoadedState {
        var rebuilds = 0
        val repairs = mutableListOf<PendingRepair>()
        val overrides = buildMap {
            // preferences.all 是快照：遍历期间不再写盘，修复操作统一延后到 IO 调度器。
            for ((key, raw) in preferences.all) {
                if (raw !is String) continue
                val parsed = parseEntry(raw, key)
                if (parsed == null) {
                    // 整条 JSON 无法解析：删盘并计一次，冷启动不再重复提示。
                    repairs += PendingRepair(key, raw, null)
                    rebuilds++
                    continue
                }
                put(key, parsed.overrides)
                if (parsed.repairedFields > 0) {
                    // 字段级损坏也计数，并把降级结果写回，避免静默丢失用户可见信息。
                    val json = gson.toJson(toDto(parsed.overrides))
                    repairs += PendingRepair(key, raw, json)
                    rebuilds += parsed.repairedFields
                }
            }
        }
        return LoadedState(overrides, rebuilds, repairs)
    }

    /** 单条记录的解析结果；repairedFields 为按继承降级的字段数。 */
    private data class ParsedEntry(
        val overrides: ConversationVoiceOverrides,
        val repairedFields: Int,
    )

    /** 逐字段解析；可读取的明确选择保留，损坏字段按继承处理并计入修复数。 */
    private fun parseEntry(raw: String, conversationId: String): ParsedEntry? {
        val dto = try {
            gson.fromJson(raw, VoiceOverridesDto::class.java) ?: return null
        } catch (_: Exception) {
            return null
        }
        val pipeline = dto.pipeline.toEnum(VoicePipeline::class.java)
        val cascadedRecognition = dto.cascadedRecognitionModel.toModelOverride()
        val cascadedSynthesis = dto.cascadedSynthesisModel.toModelOverride()
        val cascadedVoice = dto.cascadedVoice.toTextOverride()
        val semiUnderstanding = dto.semiCascadedUnderstandingModel.toModelOverride()
        val semiSynthesis = dto.semiCascadedSynthesisModel.toModelOverride()
        val semiVoice = dto.semiCascadedVoice.toTextOverride()
        val realtimeModel = dto.realtimeModel.toModelOverride()
        val realtimeVoice = dto.realtimeVoice.toTextOverride()
        val repaired = listOf(
            pipeline.repaired, cascadedRecognition.repaired,
            cascadedSynthesis.repaired, cascadedVoice.repaired,
            semiUnderstanding.repaired, semiSynthesis.repaired, semiVoice.repaired,
            realtimeModel.repaired, realtimeVoice.repaired,
        ).count { it }
        return ParsedEntry(
            ConversationVoiceOverrides(
                conversationId = conversationId,
                pipeline = pipeline.value,
                cascadedRecognitionModel = cascadedRecognition.value,
                cascadedSynthesisModel = cascadedSynthesis.value,
                cascadedVoice = cascadedVoice.value,
                semiCascadedUnderstandingModel = semiUnderstanding.value,
                semiCascadedSynthesisModel = semiSynthesis.value,
                semiCascadedVoice = semiVoice.value,
                realtimeModel = realtimeModel.value,
                realtimeVoice = realtimeVoice.value,
            ),
            repaired,
        )
    }

    /** 持久化 DTO；null 表示继承（Inherit），非 null 表示对话内明确覆盖。 */
    private data class VoiceOverridesDto(
        val pipeline: String? = null,
        val cascadedRecognitionModel: StoredModelSelection? = null,
        val cascadedSynthesisModel: StoredModelSelection? = null,
        val cascadedVoice: String? = null,
        val semiCascadedUnderstandingModel: StoredModelSelection? = null,
        val semiCascadedSynthesisModel: StoredModelSelection? = null,
        val semiCascadedVoice: String? = null,
        val realtimeModel: StoredModelSelection? = null,
        val realtimeVoice: String? = null,
    )

    /** 可空字段承载 Gson 对缺失字段的静默 null，避免非空 Kotlin 字段在读取时 NPE。 */
    private data class StoredModelSelection(
        val instanceId: String? = null,
        val connectionId: String? = null,
        val modelId: String? = null,
    ) {
        fun toSelection(): ModelSelection? = modelId?.takeIf(String::isNotBlank)?.let {
            ModelSelection(instanceId.orEmpty(), connectionId.orEmpty(), it)
        }
    }

    private fun toDto(overrides: ConversationVoiceOverrides) = VoiceOverridesDto(
        pipeline = (overrides.pipeline as? VoiceFieldOverride.Value)?.value?.name,
        cascadedRecognitionModel = (overrides.cascadedRecognitionModel as? VoiceFieldOverride.Value)?.value.toStored(),
        cascadedSynthesisModel = (overrides.cascadedSynthesisModel as? VoiceFieldOverride.Value)?.value.toStored(),
        cascadedVoice = (overrides.cascadedVoice as? VoiceFieldOverride.Value)?.value,
        semiCascadedUnderstandingModel = (overrides.semiCascadedUnderstandingModel as? VoiceFieldOverride.Value)?.value.toStored(),
        semiCascadedSynthesisModel = (overrides.semiCascadedSynthesisModel as? VoiceFieldOverride.Value)?.value.toStored(),
        semiCascadedVoice = (overrides.semiCascadedVoice as? VoiceFieldOverride.Value)?.value,
        realtimeModel = (overrides.realtimeModel as? VoiceFieldOverride.Value)?.value.toStored(),
        realtimeVoice = (overrides.realtimeVoice as? VoiceFieldOverride.Value)?.value,
    )

    private fun ModelSelection?.toStored(): StoredModelSelection? = this?.let {
        StoredModelSelection(it.instanceId, it.connectionId, it.modelId)
    }

    /** 单个字段的解析结果：值（Inherit 或 Value）与是否按继承降级修复。 */
    private data class ParsedField<T>(
        val value: VoiceFieldOverride<T>,
        val repaired: Boolean,
    )

    /** 枚举名无法解析时按继承处理并计入修复，不阻塞其他字段。 */
    private fun <T : Enum<T>> String?.toEnum(type: Class<T>): ParsedField<T> {
        if (this == null) return ParsedField(VoiceFieldOverride.Inherit, false)
        val value = runCatching { java.lang.Enum.valueOf(type, this) }.getOrNull()
            ?: return ParsedField(VoiceFieldOverride.Inherit, true)
        return ParsedField(VoiceFieldOverride.Value(value), false)
    }

    /** 缺少 modelId 的模型 JSON 视为损坏，而不是把无效选择写回有效配置。 */
    private fun StoredModelSelection?.toModelOverride(): ParsedField<ModelSelection> = when {
        this == null -> ParsedField(VoiceFieldOverride.Inherit, false)
        else -> toSelection()?.let { ParsedField(VoiceFieldOverride.Value(it), false) }
            ?: ParsedField(VoiceFieldOverride.Inherit, true)
    }

    /** 空白音色 ID 视为损坏，回落继承（由记忆或厂商默认解析）。 */
    private fun String?.toTextOverride(): ParsedField<String> = when {
        this == null -> ParsedField(VoiceFieldOverride.Inherit, false)
        isBlank() -> ParsedField(VoiceFieldOverride.Inherit, true)
        else -> ParsedField(VoiceFieldOverride.Value(this), false)
    }

    private companion object {
        const val PREFERENCES_NAME = "voice_conversation_overrides"
    }
}
