package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelItem
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.LLMModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * 服务详情页 ViewModel。
 *
 * 设计：
 * - [serviceId] 通过 `loadService` 注入；找不到对应服务时为 `null`。
 * - [service] 派生自模型服务仓库，跟随更新自动重组。
 * - [rows] 把 `modelGroups` 拍扁为 [LLMModelRow] 列表，按 [expandedGroupIds] 折叠。
 * - [expandedGroupIds] 是详情页本地状态（不写入 Store），让用户切走再回来时折叠状态不丢。
 */
@HiltViewModel
class ModelServiceDetailViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
) : ViewModel() {

    private val serviceIdFlow = MutableStateFlow<String?>(null)
    // null 表示尚未初始化，空集合则表示用户已收起全部模型组。
    private val expandedFlow = MutableStateFlow<Set<String>?>(null)
    private val httpClient = OkHttpClient()

    /** 当前详情页对应的服务 ID；找不到对应记录时为 `null`。 */
    val serviceId: StateFlow<String?> = serviceIdFlow

    /** 当前服务（找不到时为 `null`）。 */
    val service: StateFlow<LLMModelProvider?> = combine(
        modelServices.services,
        serviceIdFlow,
    ) { all, id ->
        id?.let { sid -> all.firstOrNull { it.serviceId == sid } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /**
     * 扁平化的模型行（组头 + 子项）。折叠的组不渲染子项。
     */
    val rows: StateFlow<List<LLMModelRow>> = combine(
        service,
        expandedFlow,
    ) { svc, expanded ->
        if (svc == null) {
            emptyList()
        } else {
            buildList {
                svc.LLMModelGroups.forEach { group ->
                    add(
                        LLMModelRow.GroupHeader(
                            groupId = group.groupId,
                            groupName = group.groupName,
                            isExpanded = group.groupId in expanded.orEmpty(),
                        )
                    )
                    if (group.groupId in expanded.orEmpty()) {
                        group.models.forEach { item ->
                            add(LLMModelRow.LLMModelItemRow(groupId = group.groupId, item = item))
                        }
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * 装载一个服务。返回 `false` 表示 Store 中找不到对应 ID（UI 应 Toast + popBackStack）。
     */
    suspend fun loadService(id: String): Boolean {
        modelServices.awaitReady()
        serviceIdFlow.value = id
        val svc = modelServices.getService(id) ?: return false
        // 默认全部展开；保留用户折叠状态（如果该 ID 之前存在）。
        if (expandedFlow.value == null && svc.LLMModelGroups.isNotEmpty()) {
            expandedFlow.value = svc.LLMModelGroups.map { it.groupId }.toSet()
        }
        return true
    }

    fun onApiKeyChange(value: String) {
        val id = serviceIdFlow.value ?: return
        modelServices.updateService(id) { it.copy(apiKey = value) }
    }

    fun onApiBaseUrlChange(value: String) {
        val id = serviceIdFlow.value ?: return
        modelServices.updateService(id) {
            it.copy(
                apiBaseUrl = if (it.baseType == ApiBaseType.Standard) value else it.apiBaseUrl,
                anthropicBaseUrl = if (it.baseType == ApiBaseType.Anthropic) value else it.anthropicBaseUrl,
            )
        }
    }

    fun onBaseTypeChange(type: ApiBaseType) {
        val id = serviceIdFlow.value ?: return
        modelServices.updateService(id) { service ->
            if (service.baseType == type) return@updateService service
            // 两种协议分别保存地址；切换时只切协议，让 UI 从目标字段读取既有地址。
            service.copy(baseType = type)
        }
    }

    fun onToggleEnabled(enabled: Boolean) {
        val id = serviceIdFlow.value ?: return
        modelServices.setEnabled(id, enabled)
    }

    fun toggleGroupExpanded(groupId: String) {
        val current = expandedFlow.value
        // 第一次没显式记录过 → 以"全展开"为基线；空集合代表已收起全部。
        val baseline = current
            ?: (service.value?.LLMModelGroups?.map { it.groupId }?.toSet() ?: emptySet())
        expandedFlow.value = if (groupId in baseline) {
            baseline - groupId
        } else {
            baseline + groupId
        }
    }

    fun removeModel(groupId: String, modelId: String) {
        val id = serviceIdFlow.value ?: return
        viewModelScope.launch {
            modelServices.removeModel(serviceId = id, groupId = groupId, modelId = modelId)
        }
    }

    fun appendModel(modelId: String, isStt: Boolean) {
        val id = serviceIdFlow.value ?: return
        viewModelScope.launch {
            modelServices.appendModel(
                serviceId = id,
                model = LLMModelItem(modelId = modelId, modelName = modelId, isStt = isStt),
            )
        }
    }

    /** 使用供应商的 models 端点验证密钥；仅 HTTP 200 视为成功。 */
    suspend fun testApiKey(): ApiKeyTestResult = withContext(Dispatchers.IO) {
        val current = service.value ?: return@withContext ApiKeyTestResult.Failure
        if (current.apiKey.isBlank()) return@withContext ApiKeyTestResult.Failure

        runCatching {
            val request = Request.Builder()
                .url(current.modelsEndpointUrl())
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${current.apiKey.trim()}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 200) {
                    ApiKeyTestResult.Success
                } else {
                    ApiKeyTestResult.Failure
                }
            }
        }.getOrElse { ApiKeyTestResult.Failure }
    }

    /**
     * 远端同步固定使用 OpenAI 兼容的 models 端点。
     *
     * Anthropic 类型仍保留给实际聊天请求；部分服务不支持 Anthropic 的 models API，
     * 因此不能用其 SDK 来同步模型。
     */
    suspend fun refreshRemoteModels(): LLMModelRefreshResult {
        val current = service.value ?: return LLMModelRefreshResult.Failure
        val id = serviceIdFlow.value ?: return LLMModelRefreshResult.Failure
        if (current.apiKey.isBlank()) return LLMModelRefreshResult.Failure

        return runCatching {
            val models = fetchOpenAIModels(current.openAiCompatibleBaseUrl(), current.apiKey)
            modelServices.syncRemoteModels(serviceId = id, models = models)
            LLMModelRefreshResult.Success(models.map { it.modelId })
        }.getOrElse { LLMModelRefreshResult.Failure }
    }

    private suspend fun fetchOpenAIModels(baseUrl: String, apiKey: String): List<LLMModelItem> = withContext(Dispatchers.IO) {
        val client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build()
        client.models().list().data()
            .map { LLMModelItem(modelId = it.id(), modelName = it.id()) }
    }

}

/** API 密钥检测结果。 */
sealed interface ApiKeyTestResult {
    data object Success : ApiKeyTestResult
    data object Failure : ApiKeyTestResult
}

private fun LLMModelProvider.modelsEndpointUrl(): String {
    return "${openAiCompatibleBaseUrl()}/models"
}

/** 将 Anthropic 兼容入口还原为同一供应商的 OpenAI 兼容 API 根地址。 */
private fun LLMModelProvider.openAiCompatibleBaseUrl(): String {
    return apiBaseUrl.trim().trimEnd('/')
}

sealed interface LLMModelRefreshResult {
    data class Success(val modelIds: List<String>) : LLMModelRefreshResult
    data object Failure : LLMModelRefreshResult
}

/**
 * 详情页 LazyColumn 的扁平行。
 */
sealed interface LLMModelRow {
    data class GroupHeader(
        val groupId: String,
        val groupName: String,
        val isExpanded: Boolean,
    ) : LLMModelRow

    data class LLMModelItemRow(
        val groupId: String,
        val item: LLMModelItem,
    ) : LLMModelRow
}
