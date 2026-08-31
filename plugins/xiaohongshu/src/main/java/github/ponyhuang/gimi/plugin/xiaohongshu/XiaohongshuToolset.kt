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
    - Xiaohongshu tools act through the user's current web session. Check `check_login_status` before authenticated
      work when login state is unknown; if signed out, ask the user to complete plugin login instead of guessing.
    - Use `list_feeds` for recommendations and `search_feeds` for a specific keyword. Preserve the exact `feed_id`
      and `xsec_token` returned by those tools and pass both to detail, comment, reply, like, and favorite operations.
    - Read a feed with `get_feed_detail` before referring to its content or comments. Reuse returned comment and user
      identifiers for replies; never fabricate IDs, tokens, profiles, notification items, or engagement results.
    - Use `get_my_profile` for the signed-in account and `user_profile` for another user. Use notification tools only
      with identifiers returned by `list_notifications`.
    - Commenting, replying, liking, favoriting, and notification actions mutate the user's account. Report
      success only after the tool succeeds, and surface page/login errors rather than claiming completion.
    </xiaohongshu>
""".trimIndent()
