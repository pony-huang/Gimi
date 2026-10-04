package github.ponyhuang.gimi.domain.modelcatalog.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.FormulaDeclaration

/** 设置目录和 Agent 共用的 Kimi 动态声明源；实现负责网络与凭据隔离缓存。 */
interface KimiFormulaSource {
    /** 按来源服务与凭据加载声明，失败允许后续重试，取消必须传播。 */
    suspend fun fetch(serviceId: String, apiKey: String): List<FormulaDeclaration>
}
