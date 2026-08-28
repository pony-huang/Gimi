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
    - All tools need the configured Personal Access Token; on missing/invalid, tell the user to configure it —
      never invent credentials.
    - Node slugs come from v2ex.com/go/<slug>; common ones (alias in parens): qna·问与答, all4all(flea)·二手交易,
      programmer(developer)·程序员, jobs·酷工作, share·分享发现, create·分享创造, apple, career·职场话题,
      macos(macosx,osx), pointless·无要点, bb(wifi)·宽带症候群, python, flamewar·水深火热, flood·水,
      promotions(promotion)·推广, libido·情感问题. For others, resolve with `v2ex_node`.
    - `v2ex_topic_replies` returns one page up to `max` plus a `total`; say when a thread has more pages.
    - `v2ex_notifications` lists them; `v2ex_notification_delete` removes one by id.
    - set_sticky/boost/token_create change real state and cost resources: run only when asked, never claim success
      you didn't receive.
    - Preserve returned titles/authors/counts/timestamps/content; never fabricate V2EX data.
    </v2ex>
""".trimIndent()
