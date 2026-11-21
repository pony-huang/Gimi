package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaults
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaultsRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalPreference
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 语音全局默认的持久化与聚合。
 *
 * 链路与半级联模型属于语音专属偏好，保存在应用私有偏好；
 * 级联识别/合成与原生实时模型沿用模型目录的既有默认（识别默认同时服务
 * 按住说话等非通话场景），这里只做聚合视图，不重复存储。
 * 读取容忍损坏数据：枚举名或模型 JSON 无法解析时回落默认值，不阻塞语音功能。
 *
 * [defaults] 由语音偏好与模型目录流直接派生，避免内存快照与聚合视图两份状态；
 * 目录流收集所用的作用域由构造参数提供，测试可注入可控作用域。
 */
@Singleton
class VoiceGlobalDefaultsPreferences(
    context: Context,
    private val catalog: ModelCatalogRepository,
    private val scope: CoroutineScope,
) : VoiceGlobalDefaultsRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        catalog: ModelCatalogRepository,
    ) : this(context, catalog, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val writeMutex = Mutex()

    /** 语音专属偏好快照；模型字段由目录流聚合，不在此处。 */
    private data class VoicePreferences(
        val pipeline: VoicePipeline = VoicePipeline.CASCADED,
        val semiCascadedUnderstandingModel: ModelSelection? = null,
        val semiCascadedSynthesisModel: ModelSelection? = null,
    )

    private val mutablePreferences = MutableStateFlow(readPreferences())

    override val defaults: StateFlow<VoiceGlobalDefaults> = combine(
        mutablePreferences,
        catalog.observeSpeechSelection(),
        catalog.observeTtsSelection(),
        catalog.observeRealtimeSelection(),
    ) { prefs, speech, tts, realtime ->
        composeDefaults(prefs, speech, tts, realtime)
    }.stateIn(
        scope = scope,
        // 单例生命周期等同于应用：立即收集目录流，模型默认加载完成后自动收敛。
        started = SharingStarted.Eagerly,
        initialValue = composeDefaults(mutablePreferences.value),
    )

    override suspend fun update(transform: (VoiceGlobalPreference) -> VoiceGlobalPreference) = writeMutex.withLock {
        val current = mutablePreferences.value
        // 只暴露语音专属字段，transform 无法误改由模型目录维护的模型默认值。
        val updated = transform(
            VoiceGlobalPreference(
                pipeline = current.pipeline,
                semiCascadedUnderstandingModel = current.semiCascadedUnderstandingModel,
                semiCascadedSynthesisModel = current.semiCascadedSynthesisModel,
            ),
        )
        val next = VoicePreferences(
            pipeline = updated.pipeline,
            semiCascadedUnderstandingModel = updated.semiCascadedUnderstandingModel,
            semiCascadedSynthesisModel = updated.semiCascadedSynthesisModel,
        )
        if (current == next) return@withLock
        // 同步落盘后再更新内存：写入失败上抛，不会让界面误以为已保存。
        // commit 是磁盘 IO，切到 IO 调度器执行，避免调用方（通常是 Main）被阻塞。
        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            editor.putString(PIPELINE_KEY, next.pipeline.name)
            editor.putString(SEMI_UNDERSTANDING_KEY, next.semiCascadedUnderstandingModel.toJson())
            editor.putString(SEMI_SYNTHESIS_KEY, next.semiCascadedSynthesisModel.toJson())
            if (!editor.commit()) throw IOException("Failed to persist voice global defaults")
        }
        mutablePreferences.value = next
    }

    private fun composeDefaults(
        prefs: VoicePreferences,
        speech: ModelSelection? = catalog.currentSpeechSelection(),
        tts: ModelSelection? = catalog.currentTtsSelection(),
        realtime: ModelSelection? = catalog.currentRealtimeSelection(),
    ) = VoiceGlobalDefaults(
        pipeline = prefs.pipeline,
        cascadedRecognitionModel = speech,
        cascadedSynthesisModel = tts,
        semiCascadedUnderstandingModel = prefs.semiCascadedUnderstandingModel,
        semiCascadedSynthesisModel = prefs.semiCascadedSynthesisModel,
        realtimeModel = realtime,
    )

    private fun readPreferences(): VoicePreferences = VoicePreferences(
        pipeline = preferences.getString(PIPELINE_KEY, null)
            ?.let { raw -> runCatching { VoicePipeline.valueOf(raw) }.getOrNull() }
            ?: VoicePipeline.CASCADED,
        semiCascadedUnderstandingModel = preferences.getString(SEMI_UNDERSTANDING_KEY, null).toSelection(),
        semiCascadedSynthesisModel = preferences.getString(SEMI_SYNTHESIS_KEY, null).toSelection(),
    )

    private fun ModelSelection?.toJson(): String? = this?.let { gson.toJson(it) }

    private fun String?.toSelection(): ModelSelection? = this?.let { raw ->
        // Gson 对缺失字段会留 null；字段校验放在 runCatching 内，损坏记录回落默认。
        runCatching {
            gson.fromJson(raw, ModelSelection::class.java)?.takeIf { it.modelId.isNotBlank() }
        }.getOrNull()
    }

    private companion object {
        const val PREFERENCES_NAME = "voice_global_preferences"
        const val PIPELINE_KEY = "pipeline"
        const val SEMI_UNDERSTANDING_KEY = "semi_cascaded_understanding_model"
        const val SEMI_SYNTHESIS_KEY = "semi_cascaded_synthesis_model"
    }
}
