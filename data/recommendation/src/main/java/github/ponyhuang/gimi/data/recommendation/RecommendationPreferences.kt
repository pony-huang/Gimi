package github.ponyhuang.gimi.data.recommendation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSettings
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** 推荐后台工作的最小调度边界，便于设置存储保持平台实现可替换。 */
interface RecommendationWorkScheduler {
    fun reconcile(settings: RecommendationSettings, hasSnapshot: Boolean)
    fun schedulePeriodic(intervalHours: Int)
    fun enqueueImmediate()
    fun cancel()
}

/** 使用应用私有偏好保存全局推荐设置和最后成功快照。 */
@Singleton
class RecommendationPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val scheduler: RecommendationWorkScheduler,
) : RecommendationRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readState())

    override val state: StateFlow<RecommendationState> = mutableState.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        val settings = mutableState.value.settings
        if (settings.enabled == enabled) return
        val updated = settings.copy(enabled = enabled)
        persistSettings(updated)
        mutableState.update { current -> current.copy(settings = updated, lastError = null) }
        if (enabled) {
            scheduler.schedulePeriodic(updated.intervalHours)
            scheduler.enqueueImmediate()
        } else {
            scheduler.cancel()
        }
    }

    override fun setIntervalHours(intervalHours: Int) {
        val updated = mutableState.value.settings.copy(intervalHours = intervalHours)
        if (updated == mutableState.value.settings) return
        persistSettings(updated)
        mutableState.update { current -> current.copy(settings = updated) }
        if (updated.enabled) scheduler.schedulePeriodic(updated.intervalHours)
    }

    override fun requestRefresh() {
        if (!mutableState.value.settings.enabled) return
        mutableState.update {
            it.copy(refreshStatus = RecommendationRefreshStatus.Scheduled, lastError = null)
        }
        scheduler.enqueueImmediate()
    }

    /** 提交一次完整成功结果；验证由 [RecommendationSnapshot] 保证。 */
    fun saveSnapshot(snapshot: RecommendationSnapshot) {
        preferences.edit { putString(SNAPSHOT_KEY, encodeSnapshot(snapshot)) }
        mutableState.update {
            it.copy(
                snapshot = snapshot,
                refreshStatus = RecommendationRefreshStatus.Idle,
                lastError = null,
            )
        }
    }

    /** 标记刷新已实际进入模型调用。 */
    fun markRefreshing() {
        mutableState.update {
            it.copy(refreshStatus = RecommendationRefreshStatus.Refreshing, lastError = null)
        }
    }

    /** 记录安全错误文案，同时保留最后成功快照。 */
    fun markFailed(message: String) {
        mutableState.update {
            it.copy(refreshStatus = RecommendationRefreshStatus.Idle, lastError = message)
        }
    }

    /** 在进程启动时恢复唯一调度。 */
    fun reconcileWork() {
        val current = mutableState.value
        scheduler.reconcile(current.settings, current.snapshot != null)
    }

    private fun persistSettings(settings: RecommendationSettings) {
        preferences.edit {
            putBoolean(ENABLED_KEY, settings.enabled)
            putInt(INTERVAL_HOURS_KEY, settings.intervalHours)
        }
    }

    private fun readState(): RecommendationState {
        val rawInterval = preferences.getInt(
            INTERVAL_HOURS_KEY,
            RecommendationSettings.DEFAULT_INTERVAL_HOURS,
        )
        val interval = rawInterval.takeIf { it in RecommendationSettings.SUPPORTED_INTERVAL_HOURS }
            ?: RecommendationSettings.DEFAULT_INTERVAL_HOURS
        val settings = RecommendationSettings(
            enabled = preferences.getBoolean(ENABLED_KEY, true),
            intervalHours = interval,
        )
        val snapshot = preferences.getString(SNAPSHOT_KEY, null)
            ?.let(::decodeSnapshot)
        return RecommendationState(settings = settings, snapshot = snapshot)
    }

    private fun encodeSnapshot(snapshot: RecommendationSnapshot): String = Json.encodeToString(
        JsonObject(
            mapOf(
                "generatedAtEpochMillis" to JsonPrimitive(snapshot.generatedAtEpochMillis),
                "items" to JsonArray(
                    snapshot.items.map { item ->
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(item.id),
                                "prompt" to JsonPrimitive(item.prompt),
                                "category" to JsonPrimitive(item.category.name),
                            ),
                        )
                    },
                ),
            ),
        ),
    )

    private fun decodeSnapshot(raw: String): RecommendationSnapshot? = runCatching {
        val json = Json.parseToJsonElement(raw).jsonObject
        val array = json["items"]?.jsonArray ?: error("Missing recommendation items.")
        val items = buildList {
            array.forEach { element ->
                val item = element.jsonObject
                add(
                    AgentRecommendation(
                        id = item["id"]?.jsonPrimitive?.content ?: error("Missing recommendation id."),
                        prompt = item["prompt"]?.jsonPrimitive?.content ?: error("Missing recommendation prompt."),
                        category = RecommendationCategory.valueOf(
                            item["category"]?.jsonPrimitive?.content
                                ?: error("Missing recommendation category."),
                        ),
                    ),
                )
            }
        }
        RecommendationSnapshot(
            items,
            json["generatedAtEpochMillis"]?.jsonPrimitive?.long
                ?: error("Missing recommendation generation time."),
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "agent_recommendations"
        const val ENABLED_KEY = "enabled"
        const val INTERVAL_HOURS_KEY = "interval_hours"
        const val SNAPSHOT_KEY = "snapshot_json"
    }
}
