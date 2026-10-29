package github.ponyhuang.gimi.data.agent.a2a

import org.a2aproject.sdk.client.http.A2AHttpClient

/**
 * 为全部请求注入单个静态请求头的 [A2AHttpClient] 装饰器。
 *
 * a2a-java 传输层只在构建 builder 时经 [A2AHttpClient.createGet]/[createPost]/[createDelete]
 * 拿到请求 builder，调用方无法从外部追加鉴权头；本类在 builder 创建时即写入
 * [headerName]/[headerValue]，后续链式调用原样透传给被装饰实现。
 */
class StaticHeaderA2AHttpClient(
    private val delegate: A2AHttpClient,
    private val headerName: String,
    private val headerValue: String,
) : A2AHttpClient {

    override fun createGet(): A2AHttpClient.GetBuilder = HeaderGetBuilder(delegate.createGet(), headerName, headerValue)

    override fun createPost(): A2AHttpClient.PostBuilder = HeaderPostBuilder(delegate.createPost(), headerName, headerValue)

    override fun createDelete(): A2AHttpClient.DeleteBuilder =
        HeaderDeleteBuilder(delegate.createDelete(), headerName, headerValue)

    private class HeaderGetBuilder(
        private val delegate: A2AHttpClient.GetBuilder,
        headerName: String,
        headerValue: String,
    ) : A2AHttpClient.GetBuilder by delegate {
        init {
            delegate.addHeader(headerName, headerValue)
        }
    }

    private class HeaderPostBuilder(
        private val delegate: A2AHttpClient.PostBuilder,
        headerName: String,
        headerValue: String,
    ) : A2AHttpClient.PostBuilder by delegate {
        init {
            delegate.addHeader(headerName, headerValue)
        }
    }

    private class HeaderDeleteBuilder(
        private val delegate: A2AHttpClient.DeleteBuilder,
        headerName: String,
        headerValue: String,
    ) : A2AHttpClient.DeleteBuilder by delegate {
        init {
            delegate.addHeader(headerName, headerValue)
        }
    }
}
