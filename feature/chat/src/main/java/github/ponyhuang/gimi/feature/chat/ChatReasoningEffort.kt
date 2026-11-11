package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort

/**
 * 思考强度档位的展示名。原独立推理抽屉已并入模型选择抽屉二级页
 * （见 [ChatModelPickerSheet]），这里保留档位命名供模型 chip 与滑杆复用。
 */
@Composable
internal fun reasoningEffortLabel(effort: ReasoningEffort?): String = stringResource(
    when (effort) {
        null -> R.string.chat_reasoning_effort_off
        ReasoningEffort.MINIMAL -> R.string.chat_reasoning_effort_minimal
        ReasoningEffort.LOW -> R.string.chat_reasoning_effort_low
        ReasoningEffort.MEDIUM -> R.string.chat_reasoning_effort_medium
        ReasoningEffort.HIGH -> R.string.chat_reasoning_effort_high
    },
)
