package github.ponyhuang.gimi.data.agent.tools.official

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 官方工具目录的 domain 接口实现 — 全部委托 [OfficialToolRegistry]。
 *
 * 工具的适用范围(服务/协议/模型家族)与函数列表(静态 ID 或动态获取,如 Kimi
 * formulas)都由注册表统一维护;这里不持有任何厂商知识或展示文案,展示文案由
 * UI 层 string 资源承担。
 */
@Singleton
class DefaultOfficialToolFunctionCatalog @Inject constructor(
    private val registry: OfficialToolRegistry,
) : OfficialToolFunctionCatalog {

    override fun supportedToolIds(serviceId: String, protocol: ApiProtocol): Set<String> =
        registry.supportedToolIds(serviceId, protocol)

    override suspend fun listFunctions(toolId: String): List<OfficialToolFunction> =
        registry.listFunctions(toolId)
}
