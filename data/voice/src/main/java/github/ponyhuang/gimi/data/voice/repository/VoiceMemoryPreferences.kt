package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.VoiceMemoryRepository
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 模型音色记忆的持久化：按链路与完整模型选择记忆音色。
 * 从模型 A 切到 B 再切回 A 时恢复 A 的记忆音色；同一 (链路, 模型) 重复记忆以最后一次为准。
 * 读取容忍损坏数据：JSON 无法解析、数组含 null 元素或字段缺失时跳过该条记忆，
 * 回落厂商默认音色，绝不在单例构造期抛异常导致语音功能整体不可用。
 */
@Singleton
class VoiceMemoryPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : VoiceMemoryRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val writeMutex = Mutex()

    private val mutableMemory = MutableStateFlow(readAll())

    override suspend fun rememberVoice(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String) = writeMutex.withLock {
        val key = pipeline to selection
        if (mutableMemory.value[key] == voiceId) return@withLock
        // 写互斥下的读-改-写：并发记忆同一链路时后写不丢前写。
        val next = mutableMemory.value + (key to voiceId)
        // 先同步落盘再更新内存：写入失败异常上抛，调用方据此保留原状态并提示重试。
        // commit 是磁盘 IO，切到 IO 调度器执行，避免调用方（通常是 Main）被阻塞。
        val persisted = withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            editor.putString(storageKey(pipeline), gson.toJson(entriesOf(next, pipeline)))
            editor.commit()
        }
        if (!persisted) throw IOException("Failed to persist voice memory")
        mutableMemory.value = next
    }

    override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? =
        mutableMemory.value[pipeline to selection]

    /** 单条记忆；键为完整模型选择（实例、连接与模型 ID），音色因此与模型强绑定。 */
    private data class VoiceMemoryEntry(
        val selection: StoredSelection? = null,
        val voiceId: String? = null,
    )

    /** 可空字段承载 Gson 对缺失字段的静默 null，避免非空 Kotlin 字段读取时 NPE。 */
    private data class StoredSelection(
        val instanceId: String? = null,
        val connectionId: String? = null,
        val modelId: String? = null,
    ) {
        fun toSelection(): ModelSelection? = modelId?.takeIf(String::isNotBlank)?.let {
            ModelSelection(instanceId.orEmpty(), connectionId.orEmpty(), it)
        }
    }

    private fun ModelSelection.toStored() = StoredSelection(instanceId, connectionId, modelId)

    private fun entriesOf(
        memory: Map<Pair<VoicePipeline, ModelSelection>, String>,
        pipeline: VoicePipeline,
    ): List<VoiceMemoryEntry> = memory.entries.filter { it.key.first == pipeline }
        .map { VoiceMemoryEntry(it.key.second.toStored(), it.value) }

    private fun storageKey(pipeline: VoicePipeline): String = when (pipeline) {
        VoicePipeline.CASCADED -> CASCADED_KEY
        VoicePipeline.SEMI_CASCADED -> SEMI_CASCADED_KEY
        VoicePipeline.NATIVE_REALTIME -> REALTIME_KEY
    }

    private fun readAll(): Map<Pair<VoicePipeline, ModelSelection>, String> {
        val type = object : TypeToken<List<VoiceMemoryEntry?>>() {}.type
        return VoicePipeline.entries.flatMap { pipeline ->
            val raw = preferences.getString(storageKey(pipeline), null) ?: return@flatMap emptyList()
            // 整段解析与逐条字段访问都在 runCatching 内：损坏条目只跳过，不影响其他记忆。
            runCatching {
                val parsed: List<VoiceMemoryEntry?>? = gson.fromJson(raw, type)
                parsed.orEmpty().mapNotNull { entry ->
                    val selection = entry?.selection?.toSelection() ?: return@mapNotNull null
                    val voiceId = entry.voiceId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    (pipeline to selection) to voiceId
                }
            }.getOrDefault(emptyList())
        }.toMap()
    }

    private companion object {
        const val PREFERENCES_NAME = "voice_model_memory"
        const val CASCADED_KEY = "cascaded_voices"
        const val SEMI_CASCADED_KEY = "semi_cascaded_voices"
        const val REALTIME_KEY = "realtime_voices"
    }
}
