package github.ponyhuang.gimi.data.agent.a2a

import java.net.URI
import org.a2aproject.sdk.client.http.A2AHttpClient

/** 仅允许向配置的同源端点发送静态凭据；所有链式调用保留地址校验包装。 */
internal class StaticHeaderA2AHttpClient(
    private val delegate: A2AHttpClient,
    private val headerName: String,
    headerValue: String,
    endpointUrl: String,
) : A2AHttpClient {
    private val headerValue = headerValue.trim().also {
        require(it.none { char -> char == '\r' || char == '\n' }) { "Invalid credential header" }
    }
    private val origin = URI(endpointUrl)

    private fun checkOrigin(url: String) {
        val target = URI(url)
        fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        require(
            target.scheme?.lowercase() in setOf("http", "https") &&
            target.scheme.equals(origin.scheme, true) && target.host != null &&
            target.host.equals(origin.host, true) && port(target) == port(origin) && target.userInfo == null
        ) { "Remote agent interface must use the configured origin" }
    }

    override fun createGet(): A2AHttpClient.GetBuilder = HeaderGetBuilder(delegate.createGet())

    private inner class HeaderGetBuilder(
        private val builder: A2AHttpClient.GetBuilder,
    ) : A2AHttpClient.GetBuilder by builder {
        init {
            builder.addHeader(headerName, headerValue)
        }
        override fun url(url: String): A2AHttpClient.GetBuilder = apply {
            checkOrigin(url)
            builder.url(url)
        }
        override fun addHeader(name: String, value: String): A2AHttpClient.GetBuilder = apply {
            builder.addHeader(name, value)
        }
        override fun addHeaders(headers: Map<String, String>): A2AHttpClient.GetBuilder = apply {
            builder.addHeaders(headers)
        }
    }

    override fun createPost(): A2AHttpClient.PostBuilder = HeaderPostBuilder(delegate.createPost())

    private inner class HeaderPostBuilder(
        private val builder: A2AHttpClient.PostBuilder,
    ) : A2AHttpClient.PostBuilder by builder {
        init {
            builder.addHeader(headerName, headerValue)
        }
        override fun url(url: String): A2AHttpClient.PostBuilder = apply {
            checkOrigin(url)
            builder.url(url)
        }
        override fun addHeader(name: String, value: String): A2AHttpClient.PostBuilder = apply {
            builder.addHeader(name, value)
        }
        override fun addHeaders(headers: Map<String, String>): A2AHttpClient.PostBuilder = apply {
            builder.addHeaders(headers)
        }
        override fun body(body: String): A2AHttpClient.PostBuilder = apply { builder.body(body) }
        override fun followRedirects(follow: Boolean): A2AHttpClient.PostBuilder = apply {
            require(!follow) { "Remote agent redirects are disabled" }
            builder.followRedirects(false)
        }
    }

    override fun createDelete(): A2AHttpClient.DeleteBuilder = HeaderDeleteBuilder(delegate.createDelete())

    private inner class HeaderDeleteBuilder(
        private val builder: A2AHttpClient.DeleteBuilder,
    ) : A2AHttpClient.DeleteBuilder by builder {
        init {
            builder.addHeader(headerName, headerValue)
        }
        override fun url(url: String): A2AHttpClient.DeleteBuilder = apply {
            checkOrigin(url)
            builder.url(url)
        }
        override fun addHeader(name: String, value: String): A2AHttpClient.DeleteBuilder = apply {
            builder.addHeader(name, value)
        }
        override fun addHeaders(headers: Map<String, String>): A2AHttpClient.DeleteBuilder = apply {
            builder.addHeaders(headers)
        }
    }
}
