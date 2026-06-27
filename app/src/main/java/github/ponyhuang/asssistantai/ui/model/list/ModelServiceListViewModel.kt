package github.ponyhuang.asssistantai.ui.model.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.ModelProvider
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
 * Store 的种子注入统一在 `AsssistantaiApp.onCreate` 完成，这里不再调用 `seedIfEmpty`，
 * 保证 `AgentFactory` 冷启动拿到的是同一份种子。
 */
@HiltViewModel
class ModelServiceListViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
) : ViewModel() {

    /** 当前搜索词。 */
    val query: MutableStateFlow<String> = MutableStateFlow("")

    /** 过滤后的供应商列表（按种子数据原始顺序）。 */
    val filtered: StateFlow<List<ModelProvider>> = combine(
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
