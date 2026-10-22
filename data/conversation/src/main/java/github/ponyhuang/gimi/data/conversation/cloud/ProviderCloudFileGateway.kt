package github.ponyhuang.gimi.data.conversation.cloud

import com.openai.client.okhttp.OpenAIOkHttpClientAsync
import com.openai.core.MultipartField
import com.openai.errors.OpenAIException
import com.openai.models.files.FileCreateParams
import com.openai.models.files.FilePurpose
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Files 上传适配，不负责更改会话协议；DeepSeek 官方上传路径独立于聊天端点。 */
internal class ProviderCloudFileGateway @Inject constructor() : CloudFileRemoteGateway {
    private val http = OkHttpClient.Builder()
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .callTimeout(Duration.ofSeconds(120)).build()

    override suspend fun download(service: LLMModelSetting, file: CloudFile, target: File) {
        if (service.id != "openai") throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        if (service.apiProtocol != ApiProtocol.OpenaiChatcompletions) {
            throw CloudFileException(CloudFileFailure.InvalidConfiguration)
        }
        val client = OpenAIOkHttpClientAsync.builder().apiKey(service.apiKey.trim())
            .baseUrl(service.openAiCompatibleBaseUrl).maxRetries(0)
            .timeout(Duration.ofSeconds(120)).build()
        try {
            client.files().content(file.id).downloadTo(target, file.sizeBytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OpenAIException) {
            throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        } catch (_: IOException) {
            throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        } finally {
            client.close()
        }
    }

    override suspend fun upload(
        service: LLMModelSetting,
        attachment: DraftAttachment,
        source: File,
    ): UploadedCloudFile {
        if (service.apiProtocol != ApiProtocol.OpenaiChatcompletions ||
            service.fileUploadRetentionDays !in setOf(1, 7, 30)) {
            throw CloudFileException(CloudFileFailure.InvalidConfiguration)
        }
        return when (service.id) {
            "openai" -> uploadOpenAi(service, attachment, source)
            "deepseek" -> uploadDeepSeek(service, attachment, source)
            else -> throw CloudFileException(CloudFileFailure.UnsupportedProvider)
        }
    }

    private suspend fun uploadOpenAi(
        service: LLMModelSetting,
        attachment: DraftAttachment,
        source: File,
    ): UploadedCloudFile {
        // SDK 4.41.0 的 OkHttp builder 已关闭连接重试；这里再关闭 SDK 的 HTTP 状态重试。
        val client = OpenAIOkHttpClientAsync.builder().apiKey(service.apiKey.trim())
            .baseUrl(service.openAiCompatibleBaseUrl).maxRetries(0)
            .timeout(Duration.ofSeconds(120)).build()
        try {
            return source.inputStream().use { input ->
                val params = FileCreateParams.builder()
                    .file(MultipartField.builder<InputStream>().value(input)
                        .filename(attachment.displayName).contentType(attachment.mimeType).build())
                    .purpose(FilePurpose.USER_DATA)
                    .expiresAfter(FileCreateParams.ExpiresAfter.builder()
                        .seconds(service.fileUploadRetentionDays * 86_400L).build()).build()
                val file = client.files().create(params).awaitUpload()
                UploadedCloudFile(file.id(), Math.multiplyExact(file.createdAt(), 1000L),
                    file.expiresAt().orElse(null)?.let { Math.multiplyExact(it, 1000L) })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OpenAIException) {
            throw CloudFileException(CloudFileFailure.UploadRejected)
        } catch (_: IOException) {
            throw CloudFileException(CloudFileFailure.UploadRejected)
        } catch (_: ArithmeticException) {
            throw CloudFileException(CloudFileFailure.InvalidResponse)
        } finally {
            client.close()
        }
    }

    private suspend fun uploadDeepSeek(
        service: LLMModelSetting,
        attachment: DraftAttachment,
        source: File,
    ): UploadedCloudFile {
        // 官方 harness 的 Files 接口不是 OpenAI Files 方言：没有 purpose，认证和日期格式也不同。
        // 这里只上传，不借此启用 Anthropic Messages 聊天。
        val root = service.openAiCompatibleBaseUrl.removeSuffix("/v1")
        val request = Request.Builder().url("$root/anthropic/v1/files")
            .header("x-api-key", service.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", "files-api-2025-04-14")
            .post(MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", attachment.displayName, source.asRequestBody(attachment.mimeType.toMediaType()))
                .addFormDataPart("expires_after[anchor]", "created_at")
                .addFormDataPart("expires_after[seconds]", (service.fileUploadRetentionDays * 86_400L).toString())
                .build()).build()
        try {
            return http.newCall(request).awaitDeepSeekMetadata()
        } catch (_: IOException) {
            throw CloudFileException(CloudFileFailure.UploadRejected)
        }
    }
}

private suspend fun <T> CompletableFuture<T>.awaitUpload(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel(true) }
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value)
        else continuation.resumeWithException(if (failure is CompletionException) failure.cause ?: failure else failure)
    }
}

private suspend fun Call.awaitDeepSeekMetadata(): UploadedCloudFile = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) = continuation.resumeWithException(error)
        override fun onResponse(call: Call, response: okhttp3.Response) {
            // 响应体也在可取消阶段读取：只等到响应头就恢复协程，会遗漏下载响应体时的取消。
            try {
                val metadata = response.use {
                    if (!it.isSuccessful) throw CloudFileException(CloudFileFailure.UploadRejected)
                    val source = it.body?.source() ?: throw CloudFileException(CloudFileFailure.InvalidResponse)
                    source.request(65_537)
                    if (source.buffer.size > 65_536) throw CloudFileException(CloudFileFailure.InvalidResponse)
                    val body = Json.parseToJsonElement(source.readUtf8()).jsonObject
                    val id = body.getValue("id").jsonPrimitive.content
                    val createdAt = Instant.parse(body.getValue("created_at").jsonPrimitive.content).toEpochMilli()
                    // 当前官方返回契约未承诺 expires_at，缺失时仅计算本地复用期限。
                    val expiresAt = body["expires_at"]?.jsonPrimitive?.long?.let { Math.multiplyExact(it, 1000L) }
                    UploadedCloudFile(id, createdAt, expiresAt)
                }
                continuation.resume(metadata)
            } catch (failure: CloudFileException) {
                continuation.resumeWithException(failure)
            } catch (failure: IOException) {
                continuation.resumeWithException(failure)
            } catch (_: Exception) {
                continuation.resumeWithException(CloudFileException(CloudFileFailure.InvalidResponse))
            }
        }
    })
}

/** SDK 返回响应头后仍需保持取消监听；取消时关闭响应体，中断正在进行的原文件下载。 */
private suspend fun CompletableFuture<com.openai.core.http.HttpResponse>.downloadTo(target: File, expectedSize: Long): Unit =
    suspendCancellableCoroutine { continuation ->
        val responseRef = java.util.concurrent.atomic.AtomicReference<com.openai.core.http.HttpResponse?>()
        continuation.invokeOnCancellation {
            cancel(true)
            responseRef.getAndSet(null)?.close()
        }
        whenComplete { response, failure ->
            if (failure != null) {
                continuation.resumeWithException(if (failure is CompletionException) failure.cause ?: failure else failure)
            } else {
                responseRef.set(response)
                try {
                    response.use {
                        if (!continuation.isActive) return@use
                        response.body().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (continuation.isActive) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    total += read
                                    if (total > expectedSize) throw CloudFileException(CloudFileFailure.InvalidResponse)
                                    output.write(buffer, 0, read)
                                }
                                if (continuation.isActive && total != expectedSize) throw CloudFileException(CloudFileFailure.InvalidResponse)
                            }
                        }
                    }
                    continuation.resume(Unit)
                } catch (error: IOException) {
                    continuation.resumeWithException(error)
                } catch (error: CloudFileException) {
                    continuation.resumeWithException(error)
                } finally {
                    responseRef.set(null)
                }
            }
        }
    }
