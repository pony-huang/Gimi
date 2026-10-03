package github.ponyhuang.gimi.data.modelcatalog.local

import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelFailure
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载边界的可本地化错误，保留底层原因用于诊断。 */
internal class LocalModelDownloadException(val reason: LocalModelFailure, cause: Throwable? = null) : IOException(reason.name, cause)

/** 把完整、哈希匹配的文件原子发布；中断或失败绝不留下可用模型。 */
internal class LocalModelFileDownloader(private val client: OkHttpClient) {
    suspend fun download(
        spec: LocalModelDownloadSpec,
        target: File,
        onProgress: (Float) -> Unit,
        onVerifying: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, target.name + ".part")
        if (target.parentFile?.mkdirs() == false && !target.parentFile!!.isDirectory) {
            throw LocalModelDownloadException(LocalModelFailure.Storage)
        }
        if (target.parentFile!!.usableSpace < spec.variant.bytes + 64L * 1024 * 1024) {
            throw LocalModelDownloadException(LocalModelFailure.Storage)
        }
        val call = client.newCall(Request.Builder().url(spec.url).build())
        try {
            coroutineScope {
                // 取消不等待阻塞 read 的超时，立即关闭连接释放下载任务。
                val abort = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try {
                    call.execute().use { response ->
                        if (response.code == 401 || response.code == 403) throw LocalModelDownloadException(LocalModelFailure.AccessDenied)
                        if (!response.isSuccessful) throw LocalModelDownloadException(LocalModelFailure.Network)
                        val body = response.body ?: throw LocalModelDownloadException(LocalModelFailure.Network)
                        if (body.contentLength() > 0 && body.contentLength() != spec.variant.bytes) {
                            throw LocalModelDownloadException(LocalModelFailure.Integrity)
                        }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        var reported = -1
                        body.byteStream().use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    copied += read
                                    if (copied > spec.variant.bytes) throw LocalModelDownloadException(LocalModelFailure.Integrity)
                                    try { output.write(buffer, 0, read) } catch (error: IOException) {
                                        throw LocalModelDownloadException(LocalModelFailure.Storage, error)
                                    }
                                    digest.update(buffer, 0, read)
                                    val step = (copied * 200 / spec.variant.bytes).toInt()
                                    if (step != reported) {
                                        reported = step
                                        onProgress(copied.toFloat() / spec.variant.bytes)
                                    }
                                }
                            }
                        }
                        ensureActive()
                        onVerifying()
                        val hash = digest.digest().joinToString("") { "%02x".format(it) }
                        if (copied != spec.variant.bytes || hash != spec.sha256) throw LocalModelDownloadException(LocalModelFailure.Integrity)
                        if (!partial.renameTo(target)) throw LocalModelDownloadException(LocalModelFailure.Storage)
                    }
                } finally { abort.cancel() }
            }
        } catch (error: IOException) {
            // OkHttp 取消阻塞连接会抛 SocketException，协程取消必须仍按取消传播。
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            call.cancel()
            partial.delete()
        }
    }
}
