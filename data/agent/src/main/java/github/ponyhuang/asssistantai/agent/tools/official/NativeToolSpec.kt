package github.ponyhuang.asssistantai.agent.tools.official

import com.anthropic.models.messages.ToolUnion
import com.openai.models.chat.completions.ChatCompletionTool

/**
 * A vendor-native tool spec contributed by an [OfficialToolset],
 * tagged with its [github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds]
 * key for merge/dedupe in the adapter.
 */
sealed interface NativeToolSpec {
    val toolId: String

    data class OpenAi(
        override val toolId: String,
        val tool: ChatCompletionTool,
    ) : NativeToolSpec

    data class Anthropic(
        override val toolId: String,
        val tool: ToolUnion,
    ) : NativeToolSpec
}
