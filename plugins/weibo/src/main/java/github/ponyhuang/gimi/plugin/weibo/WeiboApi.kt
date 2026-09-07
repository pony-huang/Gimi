package github.ponyhuang.gimi.plugin.weibo

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * 微博开放接口（`open-im.api.weibo.com` 合作接口）客户端。
 *
 * 鉴权是两段式：先用 App ID / App Secret 换短期 token（`/open/auth/ws_token`，默认 2 小时），
 * 再把 token 作为 query 参数带到业务接口上——微博这套接口不走 Authorization 头，也不需要
 * Cookie 或浏览器指纹，因此插件不做任何 UA 伪装。
 *
 * token 由本类内部缓存复用（[TOKEN_REFRESH_BUFFER_MS] 安全余量内提前换新），业务接口若仍
 * 返回 [CODE_TOKEN_INVALID] 则清缓存换新 token 重试一次，避免把「重新登录」暴露给模型。
 *
 * HTTP 用 JDK 自带 HttpURLConnection，JSON 用 Android 自带 org.json，插件零第三方依赖。
 *
 * @property clock 取当前时间；注入便于单测断言 token 过期行为。
 * @property sleeper 退避休眠；注入便于单测跳过真实等待。
 */
internal class WeiboApi(
    baseUrl: String = DEFAULT_BASE_URL,
    appId: String = "",
    appSecret: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {

    /** 配置后由 [WeiboPlugin.configure] 经 [configure] 写入；工具每次执行读取当前值。 */
    @Volatile
    var baseUrl: String = baseUrl
        private set

    @Volatile
    var appId: String = appId
        private set

    @Volatile
    var appSecret: String = appSecret
        private set

    private val tokenLock = Any()

    private var cachedToken: CachedToken? = null

    /**
     * 回填宿主持久化的配置。
     *
     * token 与 App 凭据、服务基址绑定，凭据或基址一变旧 token 立即失效，否则会拿着上一个
     * 账号的 token 继续调用。`ai_modelName` 走常量（[AI_MODEL_NAME_DECLARATION]），不再
     * 由插件设置控制——服务端对取值完全开放，curl 实测任意字符串（包括 random-xyz）都
     * 接受，没必要把内部细节漏给用户填。
     */
    fun configure(baseUrl: String, appId: String, appSecret: String) {
        val credentialsChanged = baseUrl != this.baseUrl || appId != this.appId || appSecret != this.appSecret
        this.baseUrl = baseUrl
        this.appId = appId
        this.appSecret = appSecret
        if (credentialsChanged) invalidateToken()
    }

    /** 是否已配置可换 token 的凭据；工具据此在调用前快速失败。 */
    fun hasCredentials(): Boolean = appId.isNotBlank() && appSecret.isNotBlank()

    /** 丢弃已缓存 token，下次调用重新换取。 */
    fun invalidateToken() {
        synchronized(tokenLock) { cachedToken = null }
    }

    /**
     * 凭据自检：确认能换到 token，只回传状态与掩码。
     *
     * token 本身是凭据，不进模型上下文——模型只需知道「通不通、还有多久过期」。
     */
    fun tokenStatus(): Map<String, Any?> {
        validToken(forceRefresh = false)
        val token = synchronized(tokenLock) { cachedToken }
            ?: throw IllegalStateException("微博 token 获取失败")
        return mapOf(
            "configured" to true,
            "token_preview" to maskToken(token.value),
            "acquired_at" to Instant.ofEpochMilli(token.acquiredAtMs).toString(),
            "expires_at" to Instant.ofEpochMilli(token.expiresAtMs).toString(),
            "expires_in_seconds" to token.expiresInSeconds,
            "valid_for_seconds" to ((token.expiresAtMs - clock()) / 1_000).coerceAtLeast(0),
        )
    }

    /** 热搜榜；[category] 是中文榜名本体（与 [HOT_SEARCH_CATEGORIES] 的键相同）。 */
    fun hotSearch(category: String, count: Int?): JSONObject = dataObject(
        get(
            path = "/open/weibo/hot_search",
            query = buildMap {
                put("category", category)
                if (count != null) put("count", count.toString())
            },
        ),
    )

    /** 微博智搜：返回对全站内容的 AI 摘要，而非原始微博列表。 */
    fun search(query: String): JSONObject =
        dataObject(get(path = "/open/wis/search_query", query = mapOf("query" to query)))

    /** 授权账号自己发布的微博。 */
    fun userStatus(count: Int?, page: Int?): JSONObject = dataObject(
        get(
            path = "/open/weibo/user_status",
            query = buildMap {
                if (count != null) put("count", count.toString())
                if (page != null) put("page", page.toString())
            },
        ),
    )

    /** 当前应用可互动的超话社区名列表。 */
    fun crowdTopicNames(): JSONArray = dataArray(get(path = "/open/crowd/topic_names", query = emptyMap()))

    /** 超话帖子流。 */
    fun crowdTimeline(
        topicName: String,
        page: Int?,
        count: Int?,
        sinceId: String?,
        maxId: String?,
        sortType: Int?,
    ): JSONObject = dataObject(
        get(
            path = "/open/crowd/timeline",
            query = buildMap {
                put("topic_name", topicName)
                if (page != null) put("page", page.toString())
                if (count != null) put("count", count.toString())
                if (sinceId != null) put("since_id", sinceId)
                if (maxId != null) put("max_id", maxId)
                if (sortType != null) put("sort_type", sortType.toString())
            },
        ),
    )

    /** 在超话中发帖（消耗每日发帖配额）。 */
    fun crowdPost(topicName: String, status: String): JSONObject = dataObject(
        post(
            path = "/open/crowd/post",
            body = buildMap {
                put("topic_name", topicName)
                put("status", status)
                putAiModelName()
            },
        ),
    )

    /** 评论指定微博（消耗每日评论配额）。 */
    fun crowdComment(id: String, comment: String, commentOri: Int?, isRepost: Int?): JSONObject = dataObject(
        post(
            path = "/open/crowd/comment",
            body = buildMap {
                put("id", id)
                put("comment", comment)
                putAiModelName()
                if (commentOri != null) put("comment_ori", commentOri)
                if (isRepost != null) put("is_repost", isRepost)
            },
        ),
    )

    /** 回复指定评论；[cid] 缺失会退化成普通评论，故由调用方保证非空。 */
    fun crowdReply(
        cid: String,
        id: String,
        comment: String,
        withoutMention: Int?,
        commentOri: Int?,
        isRepost: Int?,
    ): JSONObject = dataObject(
        post(
            path = "/open/crowd/comment/reply",
            body = buildMap {
                put("cid", cid)
                put("id", id)
                put("comment", comment)
                putAiModelName()
                if (withoutMention != null) put("without_mention", withoutMention)
                if (commentOri != null) put("comment_ori", commentOri)
                if (isRepost != null) put("is_repost", isRepost)
            },
        ),
    )

    /** 指定微博的一级评论（可带子评论）。 */
    fun crowdComments(
        id: String,
        page: Int?,
        count: Int?,
        childCount: Int?,
        fetchChild: Int?,
        isAsc: Int?,
        trimUser: Int?,
        sinceId: String?,
        maxId: String?,
    ): JSONObject = dataObject(
        get(
            path = "/open/crowd/comment/tree/root_child",
            query = buildMap {
                put("id", id)
                if (page != null) put("page", page.toString())
                if (count != null) put("count", count.toString())
                if (childCount != null) put("child_count", childCount.toString())
                if (fetchChild != null) put("fetch_child", fetchChild.toString())
                if (isAsc != null) put("is_asc", isAsc.toString())
                if (trimUser != null) put("trim_user", trimUser.toString())
                if (sinceId != null) put("since_id", sinceId)
                if (maxId != null) put("max_id", maxId)
            },
        ),
    )

    /** 指定一级评论下的子评论。 */
    fun crowdChildComments(
        id: String,
        page: Int?,
        count: Int?,
        needRootComment: Int?,
        isAsc: Int?,
        trimUser: Int?,
        sinceId: String?,
        maxId: String?,
    ): JSONObject = dataObject(
        get(
            path = "/open/crowd/comment/tree/child",
            query = buildMap {
                put("id", id)
                if (page != null) put("page", page.toString())
                if (count != null) put("count", count.toString())
                if (needRootComment != null) put("need_root_comment", needRootComment.toString())
                if (isAsc != null) put("is_asc", isAsc.toString())
                if (trimUser != null) put("trim_user", trimUser.toString())
                if (sinceId != null) put("since_id", sinceId)
                if (maxId != null) put("max_id", maxId)
            },
        ),
    )

    /**
     * 写接口带上 AI 模型声明。
     *
     * `ai_model_name` 在服务端是"内容由哪个 AI 生成"的审计字段，对取值完全开放——curl
     * 实测任意字符串都接受。本地直接写常量 [AI_MODEL_NAME_DECLARATION]，不再让用户
     * 在插件设置里填这个服务端实现细节。
     */
    private fun MutableMap<String, Any?>.putAiModelName() {
        put("ai_model_name", AI_MODEL_NAME_DECLARATION)
    }

    private fun get(path: String, query: Map<String, String>): Any? =
        call(path = path, method = "GET", query = query, body = null)

    private fun post(path: String, body: Map<String, Any?>): Any? =
        call(path = path, method = "POST", query = emptyMap(), body = body)

    /**
     * 带 token 的业务调用。
     *
     * token 走 query 参数（微博 open 接口约定，POST 也一样）。命中 [CODE_TOKEN_INVALID]
     * 说明服务端已判 token 失效（可能被提前吊销），清缓存换新 token 重试一次即止，
     * 避免凭据本身失效时无限重试。
     */
    private fun call(path: String, method: String, query: Map<String, String>, body: Map<String, Any?>?): Any? {
        var attempt = 0
        while (true) {
            val token = validToken(forceRefresh = attempt > 0)
            try {
                return parseEnvelope(execute(path, method, query + (PARAM_TOKEN to token), body))
            } catch (error: WeiboApiException) {
                if (error.code != CODE_TOKEN_INVALID || attempt >= 1) throw error
                invalidateToken()
                attempt++
            }
        }
    }

    /**
     * 取当前可用 token，必要时换新。
     *
     * 整段持锁（含网络 IO）：并发首调时只放一个请求出去，避免同一凭据被重复换 token；
     * 插件工具本身是串行调用，序列化代价可忽略。
     */
    private fun validToken(forceRefresh: Boolean): String = synchronized(tokenLock) {
        val fingerprint = credentialFingerprint()
        val current = cachedToken
        if (!forceRefresh && current != null && current.fingerprint == fingerprint && current.isFresh(clock())) {
            return@synchronized current.value
        }
        fetchToken(fingerprint).also { cachedToken = it }.value
    }

    private fun fetchToken(fingerprint: String): CachedToken {
        if (!hasCredentials()) throw IllegalStateException(MISSING_CREDENTIALS_MESSAGE)
        val response = executeWithRetry(
            path = PATH_TOKEN,
            method = "POST",
            query = emptyMap(),
            body = mapOf("app_id" to appId, "app_secret" to appSecret),
        )
        val data = parseEnvelope(response) as? JSONObject
            ?: throw IllegalStateException("微博 token 接口未返回数据")
        val token = data.optString("token")
        if (token.isBlank()) throw IllegalStateException("微博 token 接口未返回 token")
        return CachedToken(
            fingerprint = fingerprint,
            value = token,
            acquiredAtMs = clock(),
            expiresInSeconds = data.optLong("expire_in").takeIf { it > 0 } ?: DEFAULT_TOKEN_EXPIRE_SECONDS,
        )
    }

    /** 换 token 是所有调用的前置步骤，对瞬时故障做有限退避重试，避免整轮对话直接失败。 */
    private fun executeWithRetry(
        path: String,
        method: String,
        query: Map<String, String>,
        body: Map<String, Any?>?,
    ): String {
        var attempt = 0
        var delayMs = TOKEN_FETCH_BASE_DELAY_MS
        while (true) {
            try {
                return execute(path, method, query, body)
            } catch (error: WeiboHttpException) {
                if (attempt >= TOKEN_FETCH_MAX_RETRIES || error.status !in RETRYABLE_STATUSES) throw error
                sleeper(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(TOKEN_FETCH_MAX_DELAY_MS)
                attempt++
            }
        }
    }

    private fun execute(path: String, method: String, query: Map<String, String>, body: Map<String, Any?>?): String {
        val url = buildString {
            append(baseUrl.trimEnd('/')).append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
            }
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            // 用 JSONObject(Map) 构造器而非逐字段 put：R8 内联 lambda 时会把 put 误配成不存在的重载。
            val payload = JSONObject(body).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) throw WeiboHttpException(status, responseBody)
        return responseBody
    }

    private fun credentialFingerprint(): String = "$appId|$appSecret|$baseUrl"

    private fun dataObject(data: Any?): JSONObject = data as? JSONObject ?: JSONObject()

    private fun dataArray(data: Any?): JSONArray = when (data) {
        is JSONArray -> data
        is JSONObject -> JSONArray().put(data)
        else -> JSONArray()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun maskToken(token: String): String =
        if (token.length <= TOKEN_PREVIEW_LENGTH) "*".repeat(token.length)
        else token.take(TOKEN_PREVIEW_LENGTH) + "***"

    /**
     * 已缓存的 token。
     *
     * @property fingerprint 换取该 token 的凭据与基址指纹，配置变更后据此判定作废。
     * @property value token 本体。
     * @property acquiredAtMs 换取时刻（毫秒）。
     * @property expiresInSeconds 服务端声明的有效期秒数。
     */
    private data class CachedToken(
        val fingerprint: String,
        val value: String,
        val acquiredAtMs: Long,
        val expiresInSeconds: Long,
    ) {
        val expiresAtMs: Long get() = acquiredAtMs + expiresInSeconds * 1_000

        /** 留出 [TOKEN_REFRESH_BUFFER_MS] 余量，避免请求在途中过期。 */
        fun isFresh(nowMs: Long): Boolean = nowMs < expiresAtMs - TOKEN_REFRESH_BUFFER_MS
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://open-im.api.weibo.com"

        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时；智搜是同步生成摘要，可能较慢。 */
        const val READ_TIMEOUT_MS: Int = 30_000

        /** 服务端未声明有效期时的兜底：微博 token 名义有效期 2 小时。 */
        const val DEFAULT_TOKEN_EXPIRE_SECONDS: Long = 7_200

        /** token 过期前的安全余量：提前换新，避免边界请求命中 token 失效。 */
        const val TOKEN_REFRESH_BUFFER_MS: Long = 60_000

        const val MISSING_CREDENTIALS_MESSAGE: String = "微博插件未配置 App ID 与 App Secret"

        /**
         * 写接口向服务端声明的 AI 模型名。固定常量，不在插件设置里暴露——服务端对取值完全
         * 开放（curl 实测任意字符串都接受），没必要把服务端审计字段漏给用户填。
         */
        const val AI_MODEL_NAME_DECLARATION: String = "gimi-agent"

        private const val PATH_TOKEN: String = "/open/auth/ws_token"
        private const val PARAM_TOKEN: String = "token"
        private const val TOKEN_PREVIEW_LENGTH: Int = 6
        private const val TOKEN_FETCH_MAX_RETRIES: Int = 2
        private const val TOKEN_FETCH_BASE_DELAY_MS: Long = 1_000
        private const val TOKEN_FETCH_MAX_DELAY_MS: Long = 8_000

        /** 换 token 时值得重试的瞬时 HTTP 状态。 */
        private val RETRYABLE_STATUSES: Set<Int> = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
