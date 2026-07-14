package github.ponyhuang.asssistantai.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * 给 AppFunction 返回/入参使用的轻量"模型服务摘要"数据类 — 与 UI 层的
 * [github.ponyhuang.asssistantai.data.LLMModelProvider] 完全解耦，避免把内部
 * `apiKey` / `apiBaseUrl` 等敏感字段意外暴露给 system agent。
 *
 * KSP 约束（参考 `~/.claude/skills/appfunctions/SKILL.md` 与 reference
 * `E:/workplace/appfunctions/ChatApp`）: `@AppFunctionSerializable` 数据类**只能**用
 * property 级 inline `/** */` 写描述，不能用 class-level `@param`/`@property` 标签 —
 * 否则 KSP 不会进 schema。
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ModelServiceSummary(
    /** 平台唯一 ID（与 `ModelProvider.serviceId` 对应，例如 `"deepseek"`）。 */
    val serviceId: String,
    /** 平台展示名（中文品牌名，例如 `"深度求索"`）。 */
    val serviceName: String,
    /** 该服务下全部 modelId 的扁平列表（跨所有 modelGroup，顺序与 store 一致）。 */
    val availableModels: List<String>,
)
