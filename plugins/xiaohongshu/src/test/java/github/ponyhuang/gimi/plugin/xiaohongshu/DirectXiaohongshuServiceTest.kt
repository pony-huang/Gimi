package github.ponyhuang.gimi.plugin.xiaohongshu

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectXiaohongshuServiceTest {

    @Test
    fun checkLoginReadsWebsiteDomDirectly() = runTest {
        val browser = FakeBrowserGateway(waitResult = true)
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke("check_login_status", emptyMap()) as Map<*, *>

        assertEquals(listOf("https://www.xiaohongshu.com/explore"), browser.navigations)
        assertTrue(browser.waitScripts.single().contains(".main-container .user"))
        assertEquals(true, result["is_logged_in"])
    }

    @Test
    fun searchEncodesKeywordAndReturnsInitialStateFeeds() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(listOf("""[{"id":"feed-1","xsecToken":"token"}]""")),
        )
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke("search_feeds", mapOf("keyword" to "上海 咖啡")) as Map<*, *>

        assertEquals(
            "https://www.xiaohongshu.com/search_result?keyword=%E4%B8%8A%E6%B5%B7+%E5%92%96%E5%95%A1&source=web_explore_feed",
            browser.navigations.single(),
        )
        assertTrue(browser.evaluationScripts.single().contains("__INITIAL_STATE__.search.feeds"))
        assertEquals(1, result["count"])
        assertEquals("feed-1", ((result["feeds"] as List<*>).single() as Map<*, *>)["id"])
    }

    @Test
    fun invalidSearchFilterFailsBeforeNavigation() = runTest {
        val browser = FakeBrowserGateway()
        val service = DirectXiaohongshuService(browser)

        val result = runCatching {
            service.invoke(
                "search_feeds",
                mapOf("keyword" to "测试", "sort_by" to "随机"),
            )
        }

        assertTrue(result.isFailure)
        assertFalse(browser.navigations.isNotEmpty())
    }

    @Test
    fun likeFeedSkipsClickWhenPageAlreadyHasTargetState() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(listOf("""{"liked":true,"collected":false}""")),
        )
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke(
            "like_feed",
            mapOf("feed_id" to "feed-1", "xsec_token" to "token"),
        ) as Map<*, *>

        assertEquals(true, result["success"])
        assertEquals(true, result["liked"])
        assertEquals(1, browser.evaluationScripts.size)
    }

    @Test
    fun unreadCountComesFromWebsiteInitialState() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(
                listOf("""{"mentions":2,"likes":3,"connections":1,"unreadCount":6}"""),
            ),
        )
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke("get_unread_count", emptyMap()) as Map<*, *>

        assertEquals(6, result["unread"])
        assertTrue(browser.evaluationScripts.single().contains("notificationCount"))
    }

    @Test
    fun postCommentUsesFeedPageDomAndConfirmsRenderedContent() = runTest {
        val browser = FakeBrowserGateway(evaluations = ArrayDeque(listOf("true")))
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke(
            "post_comment_to_feed",
            mapOf("feed_id" to "feed-1", "xsec_token" to "token", "content" to "好内容"),
        ) as Map<*, *>

        assertEquals(true, result["success"])
        assertTrue(browser.evaluationScripts.single().contains("p.content-input"))
        assertTrue(browser.evaluationScripts.single().contains("button.submit"))
        assertTrue(browser.waitScripts.last().contains("好内容"))
    }

    @Test
    fun replyCommentRequiresCommentOrUserIdBeforeNavigation() = runTest {
        val browser = FakeBrowserGateway()
        val service = DirectXiaohongshuService(browser)

        val result = runCatching {
            service.invoke(
                "reply_comment_in_feed",
                mapOf("feed_id" to "feed-1", "xsec_token" to "token", "content" to "回复"),
            )
        }

        assertTrue(result.isFailure)
        assertTrue(browser.navigations.isEmpty())
    }

    @Test
    fun notificationsAreReadFromWebsiteNotificationState() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(
                listOf("""{"tab":"mentions","filtered":0,"items":[{"comment_id":"c1"}]}"""),
            ),
        )
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke("list_notifications", emptyMap()) as Map<*, *>

        assertEquals("mentions", result["tab"])
        assertEquals("c1", ((result["items"] as List<*>).single() as Map<*, *>)["comment_id"])
        assertTrue(browser.evaluationScripts.single().contains("notificationMap"))
    }

    @Test
    fun replyNotificationUsesNotificationItemDom() = runTest {
        val browser = FakeBrowserGateway(evaluations = ArrayDeque(listOf("true")))
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke(
            "reply_notification",
            mapOf("comment_id" to "c1", "content" to "谢谢"),
        ) as Map<*, *>

        assertEquals(true, result["success"])
        assertTrue(browser.evaluationScripts.single().contains(".action-reply"))
        assertTrue(browser.evaluationScripts.single().contains("textarea.comment-input"))
    }

    @Test
    fun likeNotificationUsesTargetStateInsteadOfBlindToggle() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(listOf("""{"success":true,"liked":true}""")),
        )
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke("like_notification", mapOf("comment_id" to "c1")) as Map<*, *>

        assertEquals(true, result["liked"])
        assertTrue(browser.evaluationScripts.single().contains(".action-like .like-wrapper"))
    }

    @Test
    fun publishContentUploadsFilesAndSubmitsOnCreatorWebsite() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(listOf("true", "true")),
            fileSelectionResult = true,
        )
        val service = DirectXiaohongshuService(browser)

        val result = service.invoke(
            "publish_content",
            mapOf(
                "title" to "上海咖啡",
                "content" to "探店记录",
                "images" to listOf("https://example.com/coffee.jpg"),
                "tags" to listOf("咖啡", "上海"),
            ),
        ) as Map<*, *>

        assertEquals(true, result["success"])
        assertEquals(listOf("https://example.com/coffee.jpg"), browser.selectedFiles.single())
        assertEquals(
            "https://creator.xiaohongshu.com/publish/publish?source=official",
            browser.navigations.single(),
        )
        assertTrue(browser.evaluationScripts.last().contains("上海咖啡"))
        assertTrue(browser.evaluationScripts.last().contains("#咖啡"))
    }

    @Test
    fun publishContentBindsRequestedProductsInsteadOfSilentlyIgnoringThem() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(listOf("true", "true", "true", "true", "true", "true")),
            fileSelectionResult = true,
        )
        val service = DirectXiaohongshuService(browser)

        service.invoke(
            "publish_content",
            mapOf(
                "title" to "防晒",
                "content" to "实测",
                "images" to listOf("https://example.com/a.jpg"),
                "products" to listOf("防晒霜 SPF50"),
            ),
        )

        assertTrue(browser.evaluationScripts.any { it.contains("multi-goods-selector-modal") })
        assertTrue(browser.evaluationScripts.any { it.contains("防晒霜 SPF50") })
        assertFalse(browser.evaluationScripts.any { it.contains("products.length) return false") })
    }

    @Test
    fun originalPublishConfirmsDeclarationBeforeSubmitting() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(listOf("true", "true", "true", "true")),
            fileSelectionResult = true,
        )
        val service = DirectXiaohongshuService(browser)

        service.invoke(
            "publish_content",
            mapOf(
                "title" to "原创内容",
                "content" to "正文",
                "images" to listOf("https://example.com/a.jpg"),
                "is_original" to true,
            ),
        )

        assertTrue(browser.evaluationScripts.any { it.contains("原创声明须知") })
        assertTrue(browser.evaluationScripts.any { it.contains("button.custom-button") })
    }

    @Test
    fun feedDetailScrollsCommentsWhenRequested() = runTest {
        val browser = FakeBrowserGateway(
            evaluations = ArrayDeque(
                listOf(
                    """{"count":10,"end":false}""",
                    """{"count":20,"end":true}""",
                    """{"feed-1":{"note":{"noteId":"feed-1"},"comments":{"list":[]}}}""",
                ),
            ),
        )
        val service = DirectXiaohongshuService(browser)

        service.invoke(
            "get_feed_detail",
            mapOf(
                "feed_id" to "feed-1",
                "xsec_token" to "token",
                "load_all_comments" to true,
                "limit" to 20,
                "click_more_replies" to true,
            ),
        )

        assertTrue(browser.evaluationScripts.first().contains(".parent-comment"))
        assertTrue(browser.evaluationScripts.first().contains("more"))
        assertEquals(3, browser.evaluationScripts.size)
    }
}

private class FakeBrowserGateway(
    private val waitResult: Boolean = true,
    private val evaluations: ArrayDeque<String?> = ArrayDeque(),
    private val fileSelectionResult: Boolean = false,
) : XiaohongshuBrowserGateway {
    val navigations = mutableListOf<String>()
    val waitScripts = mutableListOf<String>()
    val evaluationScripts = mutableListOf<String>()
    val selectedFiles = mutableListOf<List<String>>()

    override suspend fun navigate(url: String) {
        navigations += url
    }

    override suspend fun waitUntil(script: String, timeoutMillis: Long): Boolean {
        waitScripts += script
        return waitResult
    }

    override suspend fun evaluate(script: String): String? {
        evaluationScripts += script
        return evaluations.removeFirstOrNull()
    }

    override suspend fun clearCookies() = Unit

    override suspend fun selectFiles(selector: String, sources: List<String>): Boolean {
        selectedFiles += sources
        return fileSelectionResult
    }
}
