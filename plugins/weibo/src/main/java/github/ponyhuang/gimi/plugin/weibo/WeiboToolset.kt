package github.ponyhuang.gimi.plugin.weibo

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part

/**
 * 把使用约束一次性灌进 LLM 上下文。
 *
 * 模型面对的是中文用户、却不一定熟悉微博接口细节；把凭据来源、配额上限、id 语义、
 * 智搜返回的是 AI 摘要而非原始微博等关键约束集中放在这里，避免在每个工具描述里
 * 反复重申。
 */
internal const val WEIBO_INSTRUCTIONS: String = """
<weibo>
你正在使用微博插件，所有微博工具都通过同一组 App ID / App Secret 换短期 token 调用。

凭据：插件不会回传 token 本体；App ID / App Secret 在插件设置中配置，缺失时所有工具会返回「微博插件未配置 App ID 与 App Secret」。
申请方式：在微博向「@微博龙虾助手」发私信并发送「连接龙虾」，按提示完成接入即可拿到 App ID 与 App Secret。插件设置里填入后会立即生效，无需重启宿主。

凭据自检：调用 weibo_credential_status 确认凭据是否已配置并能换到 token；其他工具只在报凭据错误时才调用它。

热搜榜：可选「主榜、文娱榜、社会榜、生活榜、acg榜、科技榜、体育榜」，count 范围 1-50，默认 50。

智搜（weibo_search）返回的是官方 AI 摘要，不是原始微博列表。要特别注意：
 - completed=false 或 analyzing=true 表示仍在生成、结果不完整，需向用户说明。
 - noContent=true / refused=true 表示拒答或无内容，不要自行编造结论。
 - content_format 描述了返回正文的格式（如 markdown），按它原样转达。

授权账号微博（weibo_status）只能列出当前 App 授权账号自己发布的微博，不能查他人账号。

超话（crowd）：
 - 先调用 weibo_crowd_topics 拿到当前账号可互动的超话名称，其它超话工具的 topic_name 必须取自该列表。
 - 超话发帖（weibo_crowd_post）每日上限 10 条，是真实公开发布；只在用户明确要求发帖时调用，调用前必须复述内容让用户确认。
 - 评论与回复每日共 1000 条配额，超话评论（weibo_crowd_comment）、回复评论（weibo_crowd_comment_reply）同样只按用户明确指令执行，调用前复述内容让用户确认。
 - 回复评论（weibo_crowd_comment_reply）必须同时提供 cid 与 id，缺 cid 时接口会把回复降级成普通评论。

微博 id / 评论 id 是 64 位整数（雪花算法），远超 2^53，必须以字符串形式传入与传回，模型与工具之间不要把它当作数字处理，否则会丢精度。

所有微博接口的成功判定都看返回里的 code，外层 code=0 不代表内层写操作成功（写接口在 data 里又嵌了一层 code），按工具返回的 success 字段如实转达，不要默认「已发布/已评论」。

不要编造不存在的微博 id、链接或内容；接口报错就把错误原文转给用户。
</weibo>
"""

internal class WeiboToolset(internal val api: WeiboApi) : Toolset {

    private val tools: List<WeiboTool> by lazy {
        listOf(
            WeiboCredentialStatusTool(api),
            WeiboHotSearchTool(api),
            WeiboSearchTool(api),
            WeiboStatusTool(api),
            WeiboCrowdTopicsTool(api),
            WeiboCrowdTimelineTool(api),
            WeiboCrowdPostTool(api),
            WeiboCrowdCommentTool(api),
            WeiboCrowdCommentReplyTool(api),
            WeiboCrowdCommentsTool(api),
            WeiboCrowdChildCommentsTool(api),
        )
    }

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools

    /**
     * 每次 LLM 请求时把约束说明追加到 system instructions，让模型在调用工具前
     * 已经看到上述凭据来源、配额与 id 语义说明，避免凭据错误信息让用户绕一大圈。
     */
    override suspend fun processLlmRequest(toolContext: ToolContext, llmRequest: LlmRequest): LlmRequest =
        llmRequest.appendInstructions(Content(parts = listOf(Part(text = WEIBO_INSTRUCTIONS))))

    override fun close() = Unit
}