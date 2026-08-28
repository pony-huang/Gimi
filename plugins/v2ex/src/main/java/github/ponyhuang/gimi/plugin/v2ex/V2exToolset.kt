package github.ponyhuang.gimi.plugin.v2ex

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part

/** 在请求期暴露 V2EX 工具，并向模型说明各工具的能力边界与数据口径。 */
internal class V2exToolset(
    private val tools: () -> List<BaseTool>,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools()

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest.appendInstructions(
        Content(parts = listOf(Part(text = V2EX_INSTRUCTIONS))),
    )

    override fun close() = Unit
}

private val V2EX_INSTRUCTIONS: String = """
    <v2ex>
    - V2EX public API is anonymous and read-only: hot/latest/node topics, topic detail, replies, node and member
      info need no credentials. An optional `base_url` config overrides the API base (default
      https://www.v2ex.com/api) for mirror usage; if a tool reports a network error, mention this config to the user.
    - Use `v2ex_hot_topics` for current hot discussions and `v2ex_latest_topics` for newly created topics; never
      present one as the other.
    - Node names are the URL slugs from v2ex.com/go/<slug> (e.g. "python", "job", "v2ex"). Resolve an unfamiliar
      node with `v2ex_node_info` before querying `v2ex_node_topics`.
    - `v2ex_topic` returns the full raw body of one topic; `v2ex_topic_replies` returns at most the configured number
      of replies with a `total` count — if a thread has more, say so instead of implying completeness.
    - Preserve authors, node names, reply counts, timestamps, and raw content returned by tools. Never fabricate
      V2EX content, authors, node names, or URLs.
    - The public API throttles by IP; avoid rapid-fire repeated calls to the same endpoint, and expect some endpoints
      to be slow.
    </v2ex>
""".trimIndent()
