package dev.e2b.sdk

import com.google.gson.JsonObject
import java.io.Closeable
import java.net.URLEncoder

/** Reusable, thread-safe client. Closing it releases local HTTP resources, not remote sandboxes. */
class E2BClient @JvmOverloads constructor(val config: E2BConfig = E2BConfig.builder().build()) : Closeable {
    private val http = HttpTransport(config)

    @JvmOverloads
    fun create(options: CreateOptions = CreateOptions()): Sandbox {
        val body = mutableMapOf<String, Any>(
            "templateID" to options.template,
            "timeout" to options.timeoutSeconds,
            "metadata" to options.metadata.toMap(),
            "envVars" to options.envs.toMap(),
            "autoPause" to options.autoPause,
        )
        options.allowInternetAccess?.let { body["allow_internet_access"] = it }
        return http.api("POST", http.url(config.apiUrl, "v2", "sandboxes"), body).use {
            sandbox(Json.obj(http.readJson(it)))
        }
    }

    fun create(template: String): Sandbox = create(CreateOptions(template))

    /** Connect also resumes paused sandboxes. Omitted timeout preserves the service default. */
    @JvmOverloads
    fun connect(sandboxId: String, timeoutSeconds: Int? = null): Sandbox {
        require(sandboxId.isNotBlank())
        require(timeoutSeconds == null || timeoutSeconds > 0)
        val body = mutableMapOf<String, Any>()
        timeoutSeconds?.let { body["timeout"] = it }
        return http.api("POST", http.url(config.apiUrl, "v2", "sandboxes", sandboxId, "connect"), body).use {
            sandbox(Json.obj(http.readJson(it)))
        }
    }

    fun getInfo(sandboxId: String): SandboxInfo = http.api("GET", http.url(config.apiUrl, "sandboxes", sandboxId)).use {
        info(Json.obj(http.readJson(it)))
    }

    @JvmOverloads
    fun list(options: ListOptions = ListOptions()): SandboxPage {
        val url = http.url(config.apiUrl, "v2", "sandboxes").newBuilder()
            .addQueryParameter("limit", options.limit.toString()).apply {
                options.nextToken?.let { addQueryParameter("nextToken", it) }
                if (options.states.isNotEmpty()) addQueryParameter("state", options.states.joinToString(","))
                options.template?.let { addQueryParameter("template", it) }
                if (options.metadata.isNotEmpty()) {
                    // 与官方 SDK 一致：键和值先 quote，再 urlencode，最后作为 query 值编码。
                    fun quote(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                    addQueryParameter("metadata", options.metadata.entries.joinToString("&") {
                        URLEncoder.encode(quote(it.key), "UTF-8") + "=" + URLEncoder.encode(quote(it.value), "UTF-8")
                    })
                }
            }.build()
        return http.api("GET", url).use {
            val json = http.readJson(it)
            if (!json.isJsonArray) throw ProtocolException("Expected a sandbox list")
            SandboxPage(json.asJsonArray.map { item -> info(Json.obj(item)) }, it.header("X-Next-Token")?.takeIf(String::isNotBlank))
        }
    }

    fun setTimeout(sandboxId: String, timeoutSeconds: Int) {
        require(timeoutSeconds > 0)
        http.api("POST", http.url(config.apiUrl, "sandboxes", sandboxId, "timeout"), mapOf("timeout" to timeoutSeconds)).use {
            http.check(it)
        }
    }

    /** Lists one page of templates belonging to the API key's project. Does not create a sandbox. */
    @JvmOverloads
    fun listTemplates(limit: Int = 100, nextToken: String? = null): TemplatePage {
        require(limit in 1..100)
        val url = http.url(config.apiUrl, "v2", "templates").newBuilder()
            .addQueryParameter("limit", limit.toString()).apply {
                nextToken?.let { addQueryParameter("nextToken", it) }
            }.build()
        return http.api("GET", url).use { response ->
            val json = http.readJson(response)
            if (!json.isJsonArray) throw ProtocolException("Expected a template list")
            val items = json.asJsonArray.map { item ->
                val value = Json.obj(item)
                val id = value.get("templateID")
                if (id == null || !id.isJsonPrimitive || !id.asJsonPrimitive.isString || id.asString.isBlank()) {
                    throw ProtocolException("Missing or invalid template ID")
                }
                val names = value.get("names")?.takeUnless { it.isJsonNull }?.let { names ->
                    if (!names.isJsonArray) throw ProtocolException("Invalid template names")
                    names.asJsonArray.map { name ->
                        if (!name.isJsonPrimitive || !name.asJsonPrimitive.isString) throw ProtocolException("Invalid template name")
                        name.asString
                    }.filter(String::isNotBlank)
                } ?: emptyList()
                val status = value.get("buildStatus")?.takeUnless { it.isJsonNull }?.let {
                    if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) throw ProtocolException("Invalid template status")
                    it.asString
                }
                TemplateInfo(id.asString, names, status)
            }
            TemplatePage(items, response.header("X-Next-Token")?.takeIf(String::isNotBlank))
        }
    }

    /** Returns false when already paused; a missing sandbox is an error. */
    @JvmOverloads
    fun pause(sandboxId: String, keepMemory: Boolean = true): Boolean =
        http.api("POST", http.url(config.apiUrl, "sandboxes", sandboxId, "pause"), mapOf("memory" to keepMemory)).use {
            if (it.code == 409) false else { http.check(it); true }
        }

    /** Returns false when the sandbox no longer exists. */
    fun kill(sandboxId: String): Boolean = http.api("DELETE", http.url(config.apiUrl, "sandboxes", sandboxId)).use {
        if (it.code == 404) false else { http.check(it); true }
    }

    internal fun sandbox(json: JsonObject): Sandbox {
        val id = json.requiredText("sandboxID")
        val domain = json.text("domain") ?: config.domain
        require(domain.matches(Regex("[a-zA-Z0-9.-]+"))) { "Invalid sandbox domain" }
        val version = json.requiredText("envdVersion")
        val token = json.requiredText("envdAccessToken")
        return Sandbox(this, id, domain, Envd(http, config, id, domain, token, version))
    }

    /** Cancel in-flight local requests without closing this reusable client. */
    fun cancelRequests() = http.cancelRequests()

    override fun close() = http.close()

    companion object {
        internal fun info(json: JsonObject) = SandboxInfo(
            json.requiredText("sandboxID"), json.requiredText("templateID"), json.requiredText("state"),
            json.text("startedAt"), json.text("endAt"), json.number("cpuCount").toInt(),
            json.number("memoryMB").toInt(), json.number("diskSizeMB"), json.strings("metadata"), json.text("envdVersion"),
        )
    }
}
