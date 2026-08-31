package github.ponyhuang.gimi.plugin.xiaohongshu

import github.ponyhuang.gimi.pluginapi.PluginJson
import java.net.URLEncoder
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.time.Duration.Companion.milliseconds

/** 隔离 Android WebView 细节的最小浏览器执行契约，便于验证页面行为。 */
internal interface XiaohongshuBrowserGateway {
    suspend fun navigate(url: String)
    suspend fun waitUntil(script: String, timeoutMillis: Long): Boolean
    suspend fun evaluate(script: String): String?
    suspend fun clearCookies()
}

/**
 * 直接在小红书网页中执行操作的服务，不经过 MCP 或中转 HTTP 服务。
 *
 * 页面数据优先读取参考项目使用的 `window.__INITIAL_STATE__`，交互能力则通过 DOM 事件完成。
 */
internal class DirectXiaohongshuService(
    private val browser: XiaohongshuBrowserGateway,
) : XiaohongshuService {
    private val operationMutex = Mutex()

    override suspend fun invoke(operation: String, args: Map<String, Any?>): Any =
        operationMutex.withLock { invokeSerially(operation, args) }

    private suspend fun invokeSerially(operation: String, args: Map<String, Any?>): Any = when (operation) {
        "check_login_status" -> checkLoginStatus()
        "get_login_qrcode" -> getLoginQrcode()
        "delete_cookies" -> deleteCookies()
        "list_feeds" -> listFeeds()
        "search_feeds" -> searchFeeds(args)
        "get_feed_detail" -> getFeedDetail(args)
        "user_profile" -> userProfile(args)
        "get_my_profile" -> myProfile(args)
        "like_feed" -> toggleFeed(args, kind = "liked", undoKey = "unlike", selector = LIKE_SELECTOR)
        "favorite_feed" -> toggleFeed(
            args,
            kind = "collected",
            undoKey = "unfavorite",
            selector = FAVORITE_SELECTOR,
        )
        "get_unread_count" -> unreadCount()
        "post_comment_to_feed" -> postComment(args)
        "reply_comment_in_feed" -> replyComment(args)
        "list_notifications" -> listNotifications(args)
        "reply_notification" -> replyNotification(args)
        "like_notification" -> likeNotification(args)
        else -> throw IllegalArgumentException("尚未实现的小红书操作：$operation")
    }

    private suspend fun checkLoginStatus(): Map<String, Any?> {
        browser.navigate(EXPLORE_URL)
        val loggedIn = browser.waitUntil(LOGIN_SELECTOR_SCRIPT, QUICK_TIMEOUT_MS)
        return mapOf("is_logged_in" to loggedIn)
    }

    private suspend fun getLoginQrcode(): Map<String, Any?> {
        browser.navigate(EXPLORE_URL)
        if (browser.waitUntil(LOGIN_SELECTOR_SCRIPT, PAGE_SETTLE_TIMEOUT_MS)) {
            return mapOf("is_logged_in" to true, "img" to "")
        }
        val image = browser.evaluate(
            "document.querySelector('.login-container .qrcode-img')?.getAttribute('src') || ''",
        ).orEmpty()
        require(image.isNotBlank()) { "未找到小红书登录二维码" }
        return mapOf("is_logged_in" to false, "img" to image, "timeout" to 300)
    }

    private suspend fun deleteCookies(): Map<String, Any?> {
        browser.clearCookies()
        return mapOf("success" to true, "message" to "小红书登录状态已清除")
    }

    private suspend fun listFeeds(): Map<String, Any?> {
        browser.navigate(HOME_URL)
        // 等 feed.feeds 数据节点注水就绪，而非只等 __INITIAL_STATE__ 根对象存在——
        // 根对象在首屏即存在，但 feeds 数据要稍后才填充，只等根对象会读到空列表。
        // 参考实现轮询 8s 后即报错，避免把 60s 等满造成超时。
        require(browser.waitUntil(FEEDS_READY_SCRIPT, DATA_READY_TIMEOUT_MS)) { "小红书首页加载超时" }
        return feedsResult(browser.evaluate(FEED_LIST_SCRIPT))
    }

    private suspend fun searchFeeds(args: Map<String, Any?>): Map<String, Any?> {
        val keyword = args.string("keyword")
        @Suppress("UNCHECKED_CAST")
        val nestedFilters = args["filters"] as? Map<String, Any?> ?: emptyMap()
        fun filter(key: String): String? = nestedFilters.optionalString(key) ?: args.optionalString(key)
        val filters = SearchFilters(
            sortBy = filter("sort_by"),
            noteType = filter("note_type"),
            publishTime = filter("publish_time"),
            searchScope = filter("search_scope"),
            location = filter("location"),
        ).also(SearchFilters::validate)
        val url = "$SEARCH_URL?keyword=${encode(keyword)}&source=web_explore_feed"
        browser.navigate(url)
        // 根状态在首屏就存在，搜索结果稍后才注水；给结果一个短等待窗口，
        // 但不把真正的零结果误报成超时。
        browser.waitUntil(SEARCH_FEEDS_READY_SCRIPT, DATA_READY_TIMEOUT_MS)
        if (filters.hasAny) {
            // 记下筛选前的结果 ID；点完筛选项后站点会先清空再灌入新数据，
            // 需等到结果集变化后再读，否则读到的是空或筛选前的旧数据（对齐参考实现）。
            val before = browser.evaluate(SEARCH_FEED_IDS_SCRIPT).orEmpty()
            applyFilters(filters)
            waitFeedsChanged(before)
        }
        return feedsResult(browser.evaluate(SEARCH_FEEDS_SCRIPT))
    }

    private suspend fun getFeedDetail(args: Map<String, Any?>): Map<String, Any?> {
        val feedId = args.string("feed_id")
        val token = args.string("xsec_token")
        browser.navigate("$EXPLORE_URL/${encodePath(feedId)}?xsec_token=${encode(token)}&xsec_source=pc_feed")
        // 连续跳转时旧页面也可能已有空的 noteDetailMap；必须等目标笔记本身出现。
        browser.waitUntil(noteDetailReadyScript(feedId), DATA_READY_TIMEOUT_MS)
        if (args["load_all_comments"] == true) loadComments(args)
        val map = jsonObject(browser.evaluate(FEED_DETAIL_SCRIPT))
        val detail = map[feedId] ?: throw IllegalStateException("详情中未找到笔记 $feedId")
        return mapOf("feed_id" to feedId, "data" to detail)
    }

    private suspend fun loadComments(args: Map<String, Any?>) {
        val limit = ((args["limit"] as? Number)?.toInt() ?: 20).coerceAtLeast(1)
        val clickMore = args["click_more_replies"] == true
        val replyLimit = ((args["reply_limit"] as? Number)?.toInt() ?: 10).coerceAtLeast(0)
        val speed = when (args.optionalString("scroll_speed")) {
            "slow" -> 800L
            "fast" -> 200L
            else -> 400L
        }
        repeat(MAX_COMMENT_SCROLL_ROUNDS) {
            val state = jsonObject(browser.evaluate(commentScrollScript(clickMore, replyLimit)))
            val count = (state["count"] as? Number)?.toInt() ?: 0
            if (count >= limit || state["end"] == true) return
            delay(speed)
        }
    }

    private fun commentScrollScript(clickMore: Boolean, replyLimit: Int): String = """
        (() => {
          if ($clickMore) {
            const more = [...document.querySelectorAll('.show-more, .more-replies, button, span')]
              .filter(e => /展开|more/i.test(e.textContent || ''));
            for (const button of more) {
              const count = Number((button.textContent || '').match(/\d+/)?.[0] || 0);
              if ($replyLimit === 0 || count <= $replyLimit) button.click();
            }
          }
          const comments = document.querySelectorAll('.parent-comment');
          const end = [...document.querySelectorAll('.end-container')]
            .some(e => /THE\s*END/i.test(e.textContent || ''));
          const selectors = ['.interaction-container', '.note-scroller', '.comments-container'];
          const scroller = selectors.map(s => document.querySelector(s))
            .find(e => e && e.scrollHeight > e.clientHeight);
          if (scroller) scroller.scrollTop += Math.max(600, scroller.clientHeight * 0.8);
          else window.scrollBy(0, 800);
          return JSON.stringify({count: comments.length, end});
        })()
    """

    private suspend fun userProfile(args: Map<String, Any?>): Map<String, Any?> {
        val userId = args.string("user_id")
        val token = args.string("xsec_token")
        val tab = ProfileTab.parse(args.optionalString("tab"))
        return loadProfile(userId, token, tab)
    }

    private suspend fun myProfile(args: Map<String, Any?>): Map<String, Any?> {
        val tab = ProfileTab.parse(args.optionalString("tab"))
        browser.navigate(EXPLORE_URL)
        require(browser.waitUntil(INITIAL_STATE_SCRIPT, PAGE_TIMEOUT_MS)) { "小红书首页加载超时" }
        val raw = browser.evaluate(CURRENT_USER_SCRIPT)
        val current = if (raw.isNullOrBlank()) emptyMap() else jsonObject(raw)
        val userId = current["userId"]?.toString().orEmpty()
        require(userId.isNotBlank()) { "当前未登录小红书，请先登录" }
        return loadProfile(userId, "", tab)
    }

    private suspend fun loadProfile(userId: String, token: String, tab: ProfileTab): Map<String, Any?> {
        val query = buildList {
            if (token.isNotBlank()) add("xsec_token=${encode(token)}")
            add("xsec_source=pc_note")
            if (tab != ProfileTab.NOTE) {
                add("tab=${tab.wireValue}")
                add("subTab=note")
            }
        }.joinToString("&")
        browser.navigate("$PROFILE_URL/${encodePath(userId)}?$query")
        require(browser.waitUntil(USER_PROFILE_SCRIPT, PAGE_TIMEOUT_MS)) { "小红书用户主页加载超时" }
        return jsonObject(browser.evaluate(PROFILE_DATA_SCRIPT))
    }

    private suspend fun toggleFeed(
        args: Map<String, Any?>,
        kind: String,
        undoKey: String,
        selector: String,
    ): Map<String, Any?> {
        val feedId = args.string("feed_id")
        navigateFeed(feedId, args.string("xsec_token"))
        val want = args[undoKey] != true

        // 幂等：已处于目标状态则跳过（避免无谓点击）。
        val state = readInteractState(feedId)
        if (state[kind] == want) {
            return mapOf("feed_id" to feedId, "success" to true, kind to want)
        }

        // 对齐参考实现：最多点击 2 次，每次点击后轮询确认状态真正到位，
        // 消除"点了但状态没变"的假阳性（页面点击偶发不生效）。
        repeat(MAX_INTERACT_ATTEMPTS) {
            humanizeBeforeClick()
            require(clickButton(selector)) { "未找到小红书交互按钮" }
            if (browser.waitUntil(interactStateCondition(feedId, kind, want), INTERACTION_TIMEOUT_MS)) {
                humanizeAfterInteract()
                return mapOf("feed_id" to feedId, "success" to true, kind to want)
            }
        }
        error("点击后未确认小红书交互状态")
    }

    /** 读目标笔记的点赞/收藏状态，按 feed_id 精读（页面详情 map 可能含多条笔记，不能取第一条）。 */
    private suspend fun readInteractState(feedId: String): Map<String, Any?> =
        jsonObject(
            browser.evaluate(
                "(() => { const m = window.__INITIAL_STATE__?.note?.noteDetailMap || {}; " +
                    "const d = m[${JSONObject.quote(feedId)}]?.note?.interactInfo || {}; " +
                    "return JSON.stringify({liked: Boolean(d.liked), collected: Boolean(d.collected)}); })()",
            ),
        )

    /** 等待条件：目标笔记的指定交互字段已变为 [want]。 */
    private fun interactStateCondition(feedId: String, kind: String, want: Boolean): String =
        "(() => { const d = (window.__INITIAL_STATE__?.note?.noteDetailMap || {})" +
            "[${JSONObject.quote(feedId)}]?.note?.interactInfo; return d?.$kind === $want; })()"

    private suspend fun clickButton(selector: String): Boolean =
        browser.evaluate(
            "(() => { const e = document.querySelector(${JSONObject.quote(selector)}); " +
                "if (!e) return false; e.click(); return true; })()",
        ) == "true"

    private suspend fun unreadCount(): Map<String, Any?> {
        browser.navigate(EXPLORE_URL)
        require(browser.waitUntil(INITIAL_STATE_SCRIPT, PAGE_TIMEOUT_MS)) { "小红书首页加载超时" }
        val raw = jsonObject(browser.evaluate(UNREAD_COUNT_SCRIPT))
        return mapOf(
            "mentions" to (raw["mentions"] ?: 0),
            "likes" to (raw["likes"] ?: 0),
            "connections" to (raw["connections"] ?: 0),
            "unread" to (raw["unreadCount"] ?: 0),
        )
    }

    private suspend fun postComment(args: Map<String, Any?>): Map<String, Any?> {
        val feedId = args.string("feed_id")
        val content = args.string("content")
        navigateFeed(feedId, args.string("xsec_token"))
        require(browser.evaluate(commentSubmitScript(content, null, null)) == "true") {
            "未找到小红书评论输入框或提交按钮"
        }
        require(browser.waitUntil(commentRenderedScript(content), COMMENT_CONFIRM_TIMEOUT_MS)) {
            "评论未确认成功：提交后未在评论区出现"
        }
        return mapOf("feed_id" to feedId, "success" to true, "message" to "评论发布成功")
    }

    private suspend fun replyComment(args: Map<String, Any?>): Map<String, Any?> {
        val feedId = args.string("feed_id")
        val content = args.string("content")
        val commentId = args.optionalString("comment_id")
        val userId = args.optionalString("user_id")
        require(commentId != null || userId != null) { "缺少 comment_id 或 user_id" }
        navigateFeed(feedId, args.string("xsec_token"))
        require(browser.evaluate(commentSubmitScript(content, commentId, userId)) == "true") {
            "未找到目标评论或回复输入框"
        }
        require(browser.waitUntil(commentRenderedScript(content), COMMENT_CONFIRM_TIMEOUT_MS)) {
            "回复未确认成功：提交后未在评论区出现"
        }
        return mapOf("feed_id" to feedId, "success" to true, "message" to "评论回复成功")
    }

    private suspend fun listNotifications(args: Map<String, Any?>): Map<String, Any?> {
        val tab = notificationTab(args.optionalString("tab"))
        val limit = ((args["limit"] as? Number)?.toInt() ?: 20).coerceIn(1, 100)
        browser.navigate(NOTIFICATION_URL)
        require(browser.waitUntil(INITIAL_STATE_SCRIPT, PAGE_TIMEOUT_MS)) { "小红书通知页加载超时" }
        if (tab != "mentions") {
            val label = if (tab == "likes") "赞和收藏" else "新增关注"
            require(browser.evaluate(notificationTabScript(label)) == "true") { "未找到通知分区 $label" }
            require(browser.waitUntil(notificationStateScript(tab), PAGE_TIMEOUT_MS)) { "通知分区加载超时" }
        }
        return jsonObject(browser.evaluate(notificationListScript(tab, limit)))
    }

    private suspend fun replyNotification(args: Map<String, Any?>): Map<String, Any?> {
        val commentId = args.string("comment_id")
        val content = args.string("content")
        browser.navigate(NOTIFICATION_URL)
        require(browser.waitUntil(notificationStateScript("mentions"), PAGE_TIMEOUT_MS)) {
            "小红书通知页加载超时"
        }
        require(browser.evaluate(replyNotificationScript(commentId, content)) == "true") {
            "未找到通知评论或回复控件"
        }
        return mapOf("comment_id" to commentId, "content" to content, "success" to true)
    }

    private suspend fun likeNotification(args: Map<String, Any?>): Map<String, Any?> {
        val commentId = args.string("comment_id")
        val want = args["unlike"] != true
        browser.navigate(NOTIFICATION_URL)
        require(browser.waitUntil(notificationStateScript("mentions"), PAGE_TIMEOUT_MS)) {
            "小红书通知页加载超时"
        }
        val result = jsonObject(browser.evaluate(likeNotificationScript(commentId, want)))
        require(result["success"] == true) { result["error"]?.toString() ?: "通知点赞失败" }
        if (result["changed"] == true) {
            require(browser.waitUntil(notificationLikedScript(commentId, want), INTERACTION_TIMEOUT_MS)) {
                "点击后未确认通知评论点赞状态"
            }
        }
        return mapOf("comment_id" to commentId, "success" to true, "liked" to want)
    }

    private fun commentSubmitScript(content: String, commentId: String?, userId: String?): String {
        val target = if (commentId == null && userId == null) {
            "document.querySelector('div.input-box div.content-edit span')?.click();"
        } else {
            """
            const comments = [...document.querySelectorAll('.parent-comment, .comment-item')];
            const target = comments.find(e =>
              (${JSONObject.quote(commentId.orEmpty())} &&
                (e.dataset.id === ${JSONObject.quote(commentId.orEmpty())} ||
                 e.getAttribute('data-comment-id') === ${JSONObject.quote(commentId.orEmpty())})) ||
              (${JSONObject.quote(userId.orEmpty())} &&
                (e.dataset.userId === ${JSONObject.quote(userId.orEmpty())} ||
                 e.querySelector(`[data-user-id=${JSONObject.quote(userId.orEmpty())}]`)))
            );
            if (!target) return false;
            target.querySelector('.right .interactions .reply, .reply')?.click();
            """
        }
        return """
            (() => {
              $target
              const input = document.querySelector('div.input-box div.content-edit p.content-input');
              const submit = document.querySelector('div.bottom button.submit');
              if (!input || !submit) return false;
              input.focus();
              input.textContent = ${JSONObject.quote(content)};
              input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: ${JSONObject.quote(content)}}));
              submit.click();
              return true;
            })()
        """
    }

    private fun commentRenderedScript(content: String): String =
        "document.querySelector('.comments-container')?.innerText.includes(${JSONObject.quote(content)}) === true"

    private fun notificationTab(value: String?): String = when (value.orEmpty().lowercase()) {
        "", "mentions" -> "mentions"
        "likes" -> "likes"
        "connections" -> "connections"
        else -> throw IllegalArgumentException("未知的通知分区：$value")
    }

    private fun notificationTabScript(label: String): String = """
        (() => {
          const tab = [...document.querySelectorAll('.reds-tab-item')]
            .find(e => e.textContent?.trim() === ${JSONObject.quote(label)});
          if (!tab) return false;
          tab.click();
          return true;
        })()
    """

    private fun notificationStateScript(tab: String): String =
        "window.__INITIAL_STATE__?.notification?.notificationMap?.${tab} !== undefined"

    private fun notificationListScript(tab: String, limit: Int): String = """
        (() => {
          const raw = window.__INITIAL_STATE__?.notification?.notificationMap?.${tab};
          const list = raw?.messageList || [];
          let filtered = 0;
          const items = [];
          for (const r of list) {
            if (items.length >= $limit) break;
            const comment = r.commentInfo || {};
            const note = r.itemInfo || {};
            const cs = comment.illegalInfo?.illegalStatus;
            const ns = note.illegalInfo?.illegalStatus;
            if ((cs && cs !== 'NORMAL') || (ns && ns !== 'NORMAL')) { filtered++; continue; }
            const user = r.userInfo?.userid ? r.userInfo : (r.user || {});
            items.push({
              id: r.id || '', type: r.type || '', title: r.title || '', time: r.time || 0,
              from: {user_id: user.userid || '', nickname: user.nickname || '', xsec_token: user.xsecToken || ''},
              comment_id: comment.id || '', comment_text: comment.content || '', liked: Boolean(comment.liked),
              feed_id: note.type === 'note_info' ? (note.id || '') : '',
              feed_xsec_token: note.type === 'note_info' ? (note.xsecToken || '') : '',
              feed_title: note.content || ''
            });
          }
          return JSON.stringify({tab: ${JSONObject.quote(tab)}, filtered, items});
        })()
    """

    private fun replyNotificationScript(commentId: String, content: String): String = """
        (() => {
          const list = window.__INITIAL_STATE__?.notification?.notificationMap?.mentions?.messageList || [];
          const index = list.findIndex(r => r.commentInfo?.id === ${JSONObject.quote(commentId)});
          const item = document.querySelectorAll('.tabs-content-container > .container')[index];
          if (index < 0 || !item) return false;
          item.querySelector('.action-reply')?.click();
          const input = item.querySelector('textarea.comment-input');
          const submit = item.querySelector('button.submit');
          if (!input || !submit) return false;
          const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
          setter?.call(input, ${JSONObject.quote(content)});
          input.dispatchEvent(new Event('input', {bubbles: true}));
          submit.click();
          return true;
        })()
    """

    private fun likeNotificationScript(commentId: String, want: Boolean): String = """
        (() => {
          const list = window.__INITIAL_STATE__?.notification?.notificationMap?.mentions?.messageList || [];
          const index = list.findIndex(r => r.commentInfo?.id === ${JSONObject.quote(commentId)});
          if (index < 0) return JSON.stringify({success: false, error: '未找到评论'});
          const current = Boolean(list[index].commentInfo?.liked);
          if (current === $want) return JSON.stringify({success: true, changed: false, liked: current});
          const item = document.querySelectorAll('.tabs-content-container > .container')[index];
          const button = item?.querySelector('.action-like .like-wrapper');
          if (!button) return JSON.stringify({success: false, error: '未找到点赞按钮'});
          button.click();
          return JSON.stringify({success: true, changed: true, liked: $want});
        })()
    """

    private fun notificationLikedScript(commentId: String, want: Boolean): String = """
        (() => {
          const list = window.__INITIAL_STATE__?.notification?.notificationMap?.mentions?.messageList || [];
          const target = list.find(r => r.commentInfo?.id === ${JSONObject.quote(commentId)});
          return Boolean(target?.commentInfo?.liked) === $want;
        })()
    """

    private suspend fun navigateFeed(feedId: String, token: String) {
        browser.navigate("$EXPLORE_URL/${encodePath(feedId)}?xsec_token=${encode(token)}&xsec_source=pc_feed")
        require(browser.waitUntil(NOTE_DETAIL_SCRIPT, PAGE_TIMEOUT_MS)) { "小红书笔记详情加载超时" }
    }

    private suspend fun applyFilters(filters: SearchFilters) {
        val values = listOfNotNull(
            filters.sortBy?.let { "排序依据" to it },
            filters.noteType?.let { "笔记类型" to it },
            filters.publishTime?.let { "发布时间" to it },
            filters.searchScope?.let { "搜索范围" to it },
            filters.location?.let { "位置距离" to it },
        )
        if (values.isEmpty()) return
        val json = JSONArray(values.map { (group, value) -> JSONArray(listOf(group, value)) }).toString()
        val result = browser.evaluate(APPLY_FILTERS_SCRIPT.replace("__FILTERS__", json))
        require(result == "true") { "无法应用小红书搜索筛选条件" }
    }

    /** 点完筛选项后轮询，直到结果集 ID 变化（站点先清空再灌新数据）。超时不报错：筛选已点上去，宁可返回偏旧数据。 */
    private suspend fun waitFeedsChanged(before: String) {
        val deadline = System.currentTimeMillis() + FILTER_REFRESH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val now = browser.evaluate(SEARCH_FEED_IDS_SCRIPT).orEmpty()
            if (now.isNotBlank() && now != before) return
            delay(FILTER_REFRESH_POLL_MS.milliseconds)
        }
    }

    // 人化延迟（对齐参考实现的 humanize）：点击前留出人眼定位/移鼠的间隔，
    // 交互成功后停留片刻再继续，降低被风控识别的概率。读路径不加，保持 Agent 响应速度。
    private suspend fun humanizeBeforeClick() = delay(Random.nextLong(BEFORE_CLICK_MIN_MS, BEFORE_CLICK_MAX_MS).milliseconds)
    private suspend fun humanizeAfterInteract() = delay(Random.nextLong(AFTER_INTERACT_MIN_MS, AFTER_INTERACT_MAX_MS).milliseconds)

    private fun feedsResult(raw: String?): Map<String, Any?> {
        val feeds = jsonArray(raw)
        return mapOf("feeds" to feeds, "count" to feeds.size)
    }

    private fun Map<String, Any?>.string(key: String): String =
        (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("缺少参数 $key")

    private fun Map<String, Any?>.optionalString(key: String): String? =
        (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)

    private fun jsonArray(raw: String?): List<Any?> = when (val value = parseJson(raw)) {
        is List<*> -> value
        null -> emptyList()
        else -> throw IllegalStateException("小红书页面返回的列表格式无效")
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonObject(raw: String?): Map<String, Any?> = parseJson(raw) as? Map<String, Any?>
        ?: throw IllegalStateException("小红书页面返回的对象格式无效")

    private fun parseJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return PluginJson.toNative(JSONTokener(raw).nextValue())
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encodePath(value: String): String = encode(value).replace("+", "%20")

    private fun noteDetailReadyScript(feedId: String): String =
        "Object.prototype.hasOwnProperty.call(" +
            "window.__INITIAL_STATE__?.note?.noteDetailMap || {}, ${JSONObject.quote(feedId)})"

    companion object {
        private const val HOME_URL = "https://www.xiaohongshu.com"
        private const val EXPLORE_URL = "$HOME_URL/explore"
        private const val SEARCH_URL = "$HOME_URL/search_result"
        private const val PROFILE_URL = "$HOME_URL/user/profile"
        private const val NOTIFICATION_URL = "$HOME_URL/notification"
        private const val QUICK_TIMEOUT_MS = 30_000L
        private const val PAGE_SETTLE_TIMEOUT_MS = 2_000L
        private const val PAGE_TIMEOUT_MS = 60_000L
        /** 首页 feed 数据注水的等待上限（对齐参考实现 8s 轮询）；超时快速失败而非挂满 60s。 */
        private const val DATA_READY_TIMEOUT_MS = 8_000L
        private const val INTERACTION_TIMEOUT_MS = 8_000L
        private const val MAX_INTERACT_ATTEMPTS = 2
        private const val FILTER_REFRESH_TIMEOUT_MS = 15_000L
        private const val FILTER_REFRESH_POLL_MS = 300L
        private const val BEFORE_CLICK_MIN_MS = 80L
        private const val BEFORE_CLICK_MAX_MS = 1_000L
        private const val AFTER_INTERACT_MIN_MS = 500L
        private const val AFTER_INTERACT_MAX_MS = 1_500L
        private const val COMMENT_CONFIRM_TIMEOUT_MS = 8_000L
        private const val MAX_COMMENT_SCROLL_ROUNDS = 40
        private const val LIKE_SELECTOR = ".interact-container .left .like-lottie"
        private const val FAVORITE_SELECTOR = ".interact-container .left .reds-icon.collect-icon"

        private const val LOGIN_SELECTOR_SCRIPT =
            "document.querySelector('.main-container .user .link-wrapper .channel') !== null"
        private const val INITIAL_STATE_SCRIPT = "window.__INITIAL_STATE__ !== undefined"
        private const val NOTE_DETAIL_SCRIPT =
            "window.__INITIAL_STATE__?.note?.noteDetailMap !== undefined"
        private const val USER_PROFILE_SCRIPT =
            "window.__INITIAL_STATE__?.user?.userPageData !== undefined"
        private const val FEED_LIST_SCRIPT = """
            (() => {
              const f = window.__INITIAL_STATE__?.feed?.feeds;
              return JSON.stringify(f ? (f.value !== undefined ? f.value : f._value) : []);
            })()
        """
        private const val SEARCH_FEEDS_SCRIPT = """
            (() => {
              const f = window.__INITIAL_STATE__ && window.__INITIAL_STATE__.search &&
                window.__INITIAL_STATE__.search.feeds;
              return JSON.stringify(f ? (f.value !== undefined ? f.value : f._value) : []);
            })()
        """
        private const val SEARCH_FEEDS_READY_SCRIPT =
            "(() => { const f = window.__INITIAL_STATE__?.search?.feeds; " +
                "const v = f ? (f.value !== undefined ? f.value : f._value) : null; " +
                "return Array.isArray(v) && v.length > 0; })()"
        private const val FEED_DETAIL_SCRIPT =
            "JSON.stringify(window.__INITIAL_STATE__?.note?.noteDetailMap || {})"
        private const val FEEDS_READY_SCRIPT =
            "window.__INITIAL_STATE__?.feed?.feeds && " +
                "(Array.isArray(window.__INITIAL_STATE__.feed.feeds.value) || " +
                "Array.isArray(window.__INITIAL_STATE__.feed.feeds._value))"
        private const val SEARCH_FEED_IDS_SCRIPT = """
            (() => {
              const f = window.__INITIAL_STATE__?.search?.feeds;
              const v = f ? (f.value !== undefined ? f.value : f._value) : null;
              return v ? v.map(x => x.id).join(",") : "";
            })()
        """
        private const val UNREAD_COUNT_SCRIPT = """
            (() => {
              const count = window.__INITIAL_STATE__?.notification?.notificationCount;
              return JSON.stringify(count || {});
            })()
        """
        private const val CURRENT_USER_SCRIPT = """
            (() => {
              const u = window.__INITIAL_STATE__?.user;
              const v = u?.userInfo?.value !== undefined ? u.userInfo.value : u?.userInfo;
              if (!v || v.guest) return "";
              return JSON.stringify(v);
            })()
        """
        private const val PROFILE_DATA_SCRIPT = """
            (() => {
              const u = window.__INITIAL_STATE__?.user;
              const unwrap = o => o?.value !== undefined ? o.value : o?._value;
              const page = unwrap(u?.userPageData) || {};
              const notes = unwrap(u?.notes) || [];
              const active = unwrap(u?.activeTab) || {};
              return JSON.stringify({
                userBasicInfo: page.basicInfo || {},
                interactions: page.interactions || [],
                feeds: notes[active.index || 0] || []
              });
            })()
        """
        private const val APPLY_FILTERS_SCRIPT = """
            (() => {
              const filters = __FILTERS__;
              const button = document.querySelector('div.filter');
              if (!button) return false;
              button.dispatchEvent(new MouseEvent('mouseenter', {bubbles: true}));
              const groups = [...document.querySelectorAll('div.filter-panel div.filters')];
              for (const [label, value] of filters) {
                const group = groups.find(g => g.querySelector(':scope > span')?.textContent?.trim() === label);
                const option = group && [...group.querySelectorAll('div.tags')]
                  .find(o => o.textContent?.trim() === value);
                if (!option) return false;
                option.click();
              }
              return true;
            })()
        """
    }
}
