package github.ponyhuang.gimi.data.sandbox

import dev.e2b.sdk.AuthenticationException
import dev.e2b.sdk.E2BClient
import dev.e2b.sdk.E2BConfig
import dev.e2b.sdk.E2BException
import dev.e2b.sdk.ProtocolException
import dev.e2b.sdk.RateLimitException
import dev.e2b.sdk.TransportException
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplate
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateException
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateFailure
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** 每次查询独占客户端，翻完分页才返回；取消不会影响聊天沙盒中的请求。 */
@Singleton
class SdkSandboxTemplateRepository internal constructor(
    private val scope: CoroutineScope,
    private val clientFactory: (String, String) -> E2BClient,
) : SandboxTemplateRepository {
    @Inject constructor(@SandboxRuntimeScope scope: CoroutineScope) : this(scope, { apiUrl, apiKey ->
        E2BClient(E2BConfig.builder().apiUrl(apiUrl).apiKey(apiKey).build())
    })

    override suspend fun listTemplates(apiUrl: String, apiKey: String): List<SandboxTemplate> = suspendCancellableCoroutine { continuation ->
        val client = clientFactory(apiUrl, apiKey)
        val worker = scope.launch {
            client.use {
                try {
                    val templates = linkedMapOf<String, SandboxTemplate>()
                    val seenTokens = mutableSetOf<String>()
                    var token: String? = null
                    do {
                        ensureActive()
                        if (!continuation.isActive) return@launch
                        val page = client.listTemplates(nextToken = token)
                        page.items.forEach { item -> templates[item.templateId] = SandboxTemplate(item.templateId, item.names, item.buildStatus) }
                        token = page.nextToken
                        // 重复游标意味着服务端分页异常，不能无限请求或把不完整列表当成全部模板。
                        if (token != null && !seenTokens.add(token)) throw SandboxTemplateException(SandboxTemplateFailure.PROTOCOL)
                    } while (token != null)
                    continuation.resume(templates.values.toList()) { _, _, _ -> }
                } catch (error: E2BException) {
                    continuation.resumeWithException(SandboxTemplateException(when (error) {
                        is AuthenticationException -> SandboxTemplateFailure.AUTHENTICATION
                        is RateLimitException -> SandboxTemplateFailure.RATE_LIMIT
                        is TransportException -> SandboxTemplateFailure.NETWORK
                        is ProtocolException -> SandboxTemplateFailure.PROTOCOL
                        else -> SandboxTemplateFailure.UNAVAILABLE
                    }))
                } catch (error: Throwable) {
                    // 阻塞 SDK 桥接需要把异常回送调用方，不将取消或编程错误吞成可恢复失败。
                    continuation.resumeWithException(error)
                }
            }
        }
        continuation.invokeOnCancellation { client.cancelRequests(); worker.cancel(); client.close() }
    }
}
