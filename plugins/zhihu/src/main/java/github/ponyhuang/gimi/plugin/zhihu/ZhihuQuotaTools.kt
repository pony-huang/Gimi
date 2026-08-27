package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.pluginapi.PluginJson

/** 额度查询工具 — 查当前 access_secret 账号当日各能力的限免额度与用量，不消耗业务额度。 */
internal class ZhihuQuotaTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "api_ids" to stringParam("Comma-separated API IDs to query; omit to query all."),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            mapOf(RESULT_KEY to PluginJson.toNative(api.quota(secret, strArg(args, "api_ids"))))
        }

    companion object {
        const val NAME: String = "zhihu_quota"
        private const val DESCRIPTION: String =
            "Query Zhihu Open Platform API quota usage (total, used, remaining) per tool. " +
                "Use when the user asks about API limits or when a tool reports quota exhaustion. " +
                "Known API IDs: global_search, zhihu_search, hot_list, user_data, zhida_openai, knowledge, tools."
    }
}
