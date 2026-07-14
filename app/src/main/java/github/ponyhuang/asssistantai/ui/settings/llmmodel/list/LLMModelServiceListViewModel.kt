package github.ponyhuang.asssistantai.ui.settings.llmmodel.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.LLMModelProvider
import github.ponyhuang.asssistantai.data.ModelCatalogLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 模型服务列表页的 ViewModel。
 *
 * - [query] 当前搜索词，客户端实时过滤（不发起网络请求）。
 * - [filtered] 按 `serviceId` 或 `serviceName`（不区分大小写）包含 [query] 的子集。
 *
 * Room 目录由 [ModelServiceRepository] 异步初始化；页面通过 [loadState] 区分加载、成功与失败。
 */
@HiltViewModel
class LLMModelServiceListViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
) : ViewModel() {

    val loadState: StateFlow<ModelCatalogLoadState> = modelServices.loadState

    /** 当前搜索词。 */
    val query: MutableStateFlow<String> = MutableStateFlow("")

    /** 过滤后的供应商列表（按种子数据原始顺序）。 */
    val filtered: StateFlow<List<LLMModelProvider>> = combine(
        modelServices.services,
        query,
    ) { all, q ->
        if (q.isBlank()) {
            all
        } else {
            all.filter { svc ->
                svc.serviceId.contains(q, ignoreCase = true) ||
                    svc.serviceName.contains(q, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun onQueryChange(text: String) {
        query.value = text
    }

    /**
     * 切换某个服务的总开关。数据落模型服务仓库，订阅 [filtered] 的 UI 会自动重组。
     */
    fun onToggleEnabled(serviceId: String, enabled: Boolean) {
        modelServices.setEnabled(serviceId, enabled)
    }
}
