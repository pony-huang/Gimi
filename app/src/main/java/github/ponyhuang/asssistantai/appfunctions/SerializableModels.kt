package github.ponyhuang.asssistantai.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * `sendMessage` 的返回结构：把本轮实际使用的会话 id 与 LLM 回答一起交给外部 agent，
 * 使其能在后续调用中回传同一 `sessionId` 以延续对话。
 *
 * KSP 约束（参考 `~/.claude/skills/appfunctions`）：`@AppFunctionSerializable` 数据类
 * **只能**用 property 级 inline `/** */` 写描述，不能用 class-level `@param`/`@property`
 * 标签 — 否则 KSP 不会进 schema。
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantReply(
    /** 本轮实际使用的会话 id：入参 sessionId 为空时是新建的 id，否则回显传入值。下一轮传回此值即可延续对话。 */
    val sessionId: String,
    /** LLM 本轮的最终回答文本（已屏蔽 streaming 细节，单字符串）。 */
    val reply: String,
)
