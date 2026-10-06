package github.ponyhuang.gimi.domain.modelcatalog.model

import kotlinx.serialization.json.JsonObject

/**
 * Kimi 动态目录中的一个函数，供设置展示和 Agent 适配共用。
 *
 * @property name 发给模型的函数名称。
 * @property description 函数能力描述。
 * @property parameters 厂商返回的原始 JSON Schema，不绑定任何 Agent SDK。
 * @property formulaUri 执行时使用的 Moonshot formula 标识。
 */
data class FormulaDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject?,
    val formulaUri: String,
)
