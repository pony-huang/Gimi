package github.ponyhuang.gimi.data.agent.model

import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.ThinkingConfigEnabled
import com.anthropic.models.messages.ThinkingConfigParam
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel

/**
 * 一次请求解析出的 Claude 思考参数。
 *
 * @property thinking 明确的开关、固定预算或 adaptive 模式；null 保留模型默认行为。
 * @property effort adaptive 模式的推理强度；与 JSON 输出格式合并后写入 output_config。
 */
internal data class ClaudeThinkingParameters(
    val thinking: ThinkingConfigParam? = null,
    val effort: OutputConfig.Effort? = null,
) {
    val isEnabled: Boolean get() = effort != null || thinking?.isDisabled() == false
}

/** 产品四档到协议参数的唯一映射；显式预算优先，未知兼容模型不推断 adaptive 能力。 */
internal object ClaudeThinkingPolicy {
    private const val MIN_BUDGET = 1024
    // 限制版本段长度并检查分隔符，避免把 4-20250514 的日期当成次版本号。
    private val nativeVersion = Regex(
        """claude-(?:[a-z]+-)?(\d{1,2})(?:[-.](\d{1,2}))?(?=[-.]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun resolve(model: String, config: ThinkingConfig?, maxTokens: Int): ClaudeThinkingParameters {
        if (config == null) return ClaudeThinkingParameters()
        if (config.includeThoughts == false || config.thinkingBudget == 0) {
            return ClaudeThinkingParameters(ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build()))
        }
        config.thinkingBudget?.let { budget ->
            if (budget < 0) return adaptive(config.thinkingLevel)
            // 显式预算是调用方的约束，非法值直接暴露；只对产品档位推导的预算做裁剪。
            require(budget >= MIN_BUDGET && budget < maxTokens) {
                "Claude thinkingBudget must be at least $MIN_BUDGET and less than maxOutputTokens ($maxTokens)"
            }
            return enabled(budget)
        }
        val level = config.thinkingLevel ?: return ClaudeThinkingParameters()
        if (supportsAdaptive(model)) return adaptive(level)
        val budget = minOf(level.manualBudget(), maxTokens - 1)
        return if (budget >= MIN_BUDGET) enabled(budget) else ClaudeThinkingParameters()
    }

    private fun supportsAdaptive(model: String): Boolean {
        val match = nativeVersion.find(model) ?: return false
        val major = match.groupValues[1].toIntOrNull() ?: return false
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        // Claude 4.5 仍使用固定预算，4.6 起支持 adaptive；兼容端点可用负预算显式选择。
        return major > 4 || (major == 4 && minor >= 6)
    }

    private fun enabled(budget: Int) = ClaudeThinkingParameters(
        thinking = ThinkingConfigParam.ofEnabled(ThinkingConfigEnabled.builder().budgetTokens(budget.toLong()).build()),
    )

    private fun adaptive(level: ThinkingLevel?) = ClaudeThinkingParameters(
        thinking = ThinkingConfigParam.ofAdaptive(ThinkingConfigAdaptive.builder()
            .display(ThinkingConfigAdaptive.Display.SUMMARIZED).build()),
        effort = when (level) {
            null -> null
            // adaptive 没有 minimal；不使用部分模型不支持的 max 来凑四档。
            ThinkingLevel.MINIMAL, ThinkingLevel.LOW -> OutputConfig.Effort.LOW
            ThinkingLevel.MEDIUM -> OutputConfig.Effort.MEDIUM
            ThinkingLevel.HIGH, ThinkingLevel.THINKING_LEVEL_UNSPECIFIED -> OutputConfig.Effort.HIGH
        },
    )

    private fun ThinkingLevel.manualBudget(): Int = when (this) {
        ThinkingLevel.MINIMAL -> 1024
        ThinkingLevel.LOW -> 2048
        ThinkingLevel.MEDIUM -> 4096
        ThinkingLevel.HIGH, ThinkingLevel.THINKING_LEVEL_UNSPECIFIED -> 6144
    }
}
