package github.ponyhuang.gimi.data.agent.phone

import com.google.adk.kt.agents.BaseAgent
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneController
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 直控手机贡献方：把 [DirectPhoneAgent] 以 sub-agent 形式挂入 agent 树，由模型经
 * `transfer_to_agent` 委派。不贡献工具，也不进入工具目录。
 *
 * 后端尚不可用时（占位实现恒为 [MobileUseAvailability.UNSUPPORTED]）不挂载该委派目标，
 * 避免每次请求给模型一个只会回报 not_implemented 的诱饵；[revision] 跟踪这一可用性，
 * 后端就绪后 Agent 运行时缓存自动失效并重新装配。
 */
@Singleton
class DirectPhoneContribution @Inject constructor(
    private val controller: DirectPhoneController,
) : AgentContribution {

    override val id: String = ID

    /** 可用性变化即重建 Agent；用布尔值而非原始诊断，避免 BUSY 等瞬态反复失效缓存。 */
    override fun revision(): Any = isAvailable()

    override suspend fun subAgents(spec: AgentBuildSpec): List<BaseAgent> =
        if (!isAvailable()) {
            emptyList()
        } else {
            // BaseAgent 挂树时写入 parentAgent，同一实例二次挂载会抛异常；每次装配新建。
            listOf(DirectPhoneAgent(controller))
        }

    private fun isAvailable(): Boolean = controller.availability() != MobileUseAvailability.UNSUPPORTED

    private companion object {
        const val ID: String = "direct_phone"
    }
}
