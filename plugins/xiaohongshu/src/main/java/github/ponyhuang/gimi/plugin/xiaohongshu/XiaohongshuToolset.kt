package github.ponyhuang.gimi.plugin.xiaohongshu

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part

/** 在请求期暴露小红书工具，并补充网页会话与跨工具参数约束。 */
internal class XiaohongshuToolset(
    private val tools: () -> List<BaseTool>,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools()

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest.appendInstructions(
        Content(parts = listOf(Part(text = XIAOHONGSHU_INSTRUCTIONS))),
    )

    override fun close() = Unit
}

private val XIAOHONGSHU_INSTRUCTIONS: String = """
    <xiaohongshu>
    - Tools act through the user's web session. Check `check_login_status` when login state is unknown; if signed
      out, ask the user to complete plugin login instead of guessing.
    - `list_feeds` for recommendations, `search_feeds` for a keyword. Pass the exact `feed_id` + `xsec_token` they
      return to every detail, comment, reply, like, and favorite operation.
    - Call `get_feed_detail` before citing a note's content or comments, and reuse its returned identifiers. Never
      fabricate IDs, tokens, profiles, notifications, or engagement results.
    - Reply in Markdown and embed key images inline as `![cover](image-url)` using tool-returned HTTPS URLs (e.g.
      `urlDefault` in `get_feed_detail`'s `imageList`); never invent image URLs.
    - `get_my_profile` for the signed-in account, `user_profile` for others; notifications only via identifiers
      from `list_notifications`. Mutating actions (comment/reply/like/favorite) count as done only when the tool
      succeeds — surface page/login errors instead of claiming completion.
    </xiaohongshu>
""".trimIndent()
