package github.ponyhuang.asssistantai.domain.modelcatalog.repository

import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ResolvedAgentModel
import kotlinx.coroutines.flow.StateFlow

/** Agent 运行时读取模型配置的只读契约，由 :data:modelcatalog 实现。 */
interface AgentModelConfigurationSource {
    /** 进程内运行时选择（聊天 TopAppBar 显式选择），非持久化默认值。 */
    val runtimeSelection: StateFlow<ModelSelection?>
    val fastSelection: StateFlow<ModelSelection?>
    val configurationRevision: StateFlow<Long>

    suspend fun awaitReady()

    /** 默认助手模型，缺失/失效时回退到第一个可用模型。 */
    fun defaultSelection(): ModelSelection?

    /** 解析普通聊天模型；服务被禁用/key 为空/模型不存在时返回 null。 */
    fun resolveChatModel(selection: ModelSelection?): ResolvedAgentModel?

    fun currentServices(): List<LLMModelSetting>
}
