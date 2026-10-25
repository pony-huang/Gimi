package github.ponyhuang.gimi.domain.modelcatalog.usecase

import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 观察快速模型是否已选择且当前可解析（服务启用、凭据齐全、模型仍存在）。
 *
 * 解析语义与 Agent 运行时的 [AgentModelConfigurationSource.resolveChatModel] 同源，
 * 避免"界面认为可用、运行时却解析失败"的判定分叉。
 * 服务配置变化（禁用/删除/清空凭据）不改变 selection 本身，
 * 因此必须让 [AgentModelConfigurationSource.configurationRevision] 参与组合来触发重算。
 */
class ObserveFastModelAvailabilityUseCase @Inject constructor(
    private val configurationSource: AgentModelConfigurationSource,
) {
    operator fun invoke(): Flow<Boolean> = combine(
        configurationSource.fastSelection,
        configurationSource.configurationRevision,
    ) { selection, _ -> configurationSource.resolveChatModel(selection) != null }
}
