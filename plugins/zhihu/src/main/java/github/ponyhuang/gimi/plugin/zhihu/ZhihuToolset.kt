package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part

/** 在请求期暴露知乎工具，并向模型说明四类能力的选择边界。 */
internal class ZhihuToolset(
    private val tools: () -> List<BaseTool>,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools()

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest.appendInstructions(
        Content(parts = listOf(Part(text = ZHIHU_INSTRUCTIONS))),
    )

    override fun close() = Unit
}

private val ZHIHU_INSTRUCTIONS: String = """
    <zhihu>
    - Zhihu tools require the plugin's configured `access_secret`. If a tool reports that it is missing or invalid,
      tell the user to configure the plugin; do not retry with invented credentials or claim current results.
    - Use `zhihu_search` for questions, answers, and articles inside Zhihu. Use `zhihu_global_search` only when the
      user needs wider web sources, its source metadata, or an explicit host/time/database filter.
    - Use `zhihu_hot_list` only for the current Zhihu hot ranking. Do not present search results, personal relevance,
      or model-generated answers as the current hot list.
    - Use `zhihu_ask` when the user wants a synthesized answer grounded in Zhihu content. Choose `fast` for latency,
      `thinking` for deeper reasoning, and `agent` only when an agent-style answer is useful; default to `thinking`.
    - Preserve titles, authors, summaries, source labels, authority metadata, vote counts, and links returned by tools.
      Distinguish retrieved facts from synthesis, and never fabricate current content or URLs.
    </zhihu>
""".trimIndent()
