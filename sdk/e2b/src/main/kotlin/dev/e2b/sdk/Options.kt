package dev.e2b.sdk

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Connection settings. API credentials are used only with the control-plane URL. */
class E2BConfig private constructor(
    internal val apiKey: String,
    val apiUrl: String,
    val domain: String,
    val requestTimeoutSeconds: Int,
    internal val sandboxUrl: String?,
    internal val codeInterpreterUrl: String?,
) {
    class Builder {
        private var apiKey = System.getenv("E2B_API_KEY") ?: ""
        private var domain = System.getenv("E2B_DOMAIN") ?: "e2b.app"
        private var apiUrl: String? = null
        private var sandboxUrl: String? = null
        private var codeInterpreterUrl: String? = null
        private var requestTimeoutSeconds = 60

        fun apiKey(value: String) = apply { apiKey = value }
        fun domain(value: String) = apply { domain = value }
        fun apiUrl(value: String) = apply { apiUrl = value }
        /** Override envd URL for local tests or self-hosting; never use for application ports. */
        fun sandboxUrl(value: String) = apply { sandboxUrl = value }
        /** Override the interpreter endpoint for tests or self-hosting; independent from envd. */
        fun codeInterpreterUrl(value: String) = apply { codeInterpreterUrl = value }
        fun requestTimeoutSeconds(value: Int) = apply { requestTimeoutSeconds = value }
        fun build(): E2BConfig {
            require(apiKey.isNotBlank()) { "Set E2B_API_KEY or provide an API key" }
            require(requestTimeoutSeconds > 0) { "Request timeout must be positive" }
            require(domain.matches(Regex("[a-zA-Z0-9.-]+"))) { "Invalid domain" }
            val url = (apiUrl ?: "https://api.$domain").toHttpUrl()
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
            sandboxUrl?.toHttpUrl()?.let {
                require(it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
            }
            codeInterpreterUrl?.toHttpUrl()?.let {
                require(it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
            }
            return E2BConfig(apiKey, url.toString(), domain, requestTimeoutSeconds, sandboxUrl, codeInterpreterUrl)
        }
    }

    companion object {
        @JvmStatic fun builder() = Builder()
    }
}

/** Sandbox creation settings; all timeouts are seconds. Maps are copied before sending. */
data class CreateOptions @JvmOverloads constructor(
    val template: String = "base",
    val timeoutSeconds: Int = 300,
    val metadata: Map<String, String> = emptyMap(),
    val envs: Map<String, String> = emptyMap(),
    val allowInternetAccess: Boolean? = null,
    val autoPause: Boolean = false,
) {
    init {
        require(template.isNotBlank())
        require(timeoutSeconds > 0)
    }
}

/** List filters and opaque continuation token; each call fetches one page. */
data class ListOptions @JvmOverloads constructor(
    val limit: Int = 100,
    val nextToken: String? = null,
    val states: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val template: String? = null,
) {
    init {
        require(limit in 1..100)
        require(states.all { it == "running" || it == "paused" })
    }
}

/** Command execution settings. A zero timeout disables the total stream deadline.
 * @property maxOutputBytes Maximum combined stdout/stderr retained by a handle.
 */
data class CommandOptions @JvmOverloads constructor(
    val cwd: String? = null,
    val envs: Map<String, String> = emptyMap(),
    val user: String? = null,
    val timeoutSeconds: Int = 60,
    val stdin: Boolean = false,
    val maxOutputBytes: Int = 4 * 1024 * 1024,
) {
    init {
        require(timeoutSeconds >= 0)
        require(maxOutputBytes > 0)
    }
}
