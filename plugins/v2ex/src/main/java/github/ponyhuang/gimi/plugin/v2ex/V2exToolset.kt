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
    - All V2EX tools call the v2 API with the configured Personal Access Token. If a tool reports a missing or
      invalid token, tell the user to configure the plugin; never retry with invented credentials.
    - Node names are the URL slugs from v2ex.com/go/<slug> (e.g. "python", "job", "v2ex"); resolve an unfamiliar
      node with `v2ex_node` before querying `v2ex_node_topics`. Both accept a `page` param.
    - `v2ex_topic` returns the full raw body of one topic; `v2ex_topic_replies` returns at most the configured number
      of replies for one page with a `total` count — if a thread has more pages, say so instead of implying
      completeness.
    - `v2ex_notifications` lists the account's notifications; `v2ex_notification_delete` removes one by id.
    - Write actions have real effects and cost resources: `v2ex_topic_set_sticky` pins the user's own topic,
      `v2ex_topic_boost` promotes it to the homepage and charges coins, `v2ex_token_create` issues a new token
      (max 10). Only run them when the user explicitly asks, and never fabricate confirmation of success.
    - Preserve titles, authors, node names, reply counts, timestamps, and raw content returned by tools. Never
      fabricate V2EX content, authors, node names, or URLs.
    - The API is rate-limited to 600 requests/hour per IP; avoid rapid-fire repeated calls to the same endpoint.
    </v2ex>
""".trimIndent()
