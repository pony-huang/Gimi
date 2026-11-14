package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** 用应用拦截器构造离线响应，覆盖 HTTP 与连接失败分类，避免真实网络依赖。 */
class SkillArchiveReaderTest {
    private fun reader(interceptor: Interceptor) = SkillArchiveReader(
        OkHttpClient.Builder().addInterceptor(interceptor).build(),
        localDocumentOpener = { null },
    )

    @Test fun preservesHttpStatusWithoutIncludingServerBodyOrUrl() {
        val reader = reader { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(404).message("Not Found").body("private server response".toResponseBody()).build()
        }
        val failure = runCatching { reader.open(SkillImportSource.Url("https://example.com/archive.zip")) }
            .exceptionOrNull() as SkillImportFailure
        assertEquals(SkillImportFailure.DownloadIssue.HttpError, failure.downloadIssue)
        assertEquals(404, failure.httpStatus)
        assertFalse(failure.message!!.contains("private server response"))
        assertFalse(failure.message!!.contains("example.com"))
    }

    @Test fun distinguishesTimeoutTlsAndConnectionFailures() {
        val cases = listOf(
            SocketTimeoutException() to SkillImportFailure.DownloadIssue.TimedOut,
            SSLHandshakeException("bad certificate") to SkillImportFailure.DownloadIssue.TlsFailed,
            UnknownHostException() to SkillImportFailure.DownloadIssue.ConnectionFailed,
        )
        for ((error, issue) in cases) {
            val reader = reader { throw error }
            val failure = runCatching { reader.open(SkillImportSource.Url("https://example.com/archive.zip")) }
                .exceptionOrNull() as SkillImportFailure
            assertEquals(issue, failure.downloadIssue)
            assertNull(failure.httpStatus)
        }
    }

    @Test fun rejectsAnInsecureFinalResponse() {
        val reader = reader { chain ->
            Response.Builder().request(chain.request().newBuilder().url("http://example.com/archive.zip").build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("zip".toResponseBody()).build()
        }
        val failure = runCatching { reader.open(SkillImportSource.Url("https://example.com/archive.zip")) }
            .exceptionOrNull() as SkillImportFailure
        assertEquals(SkillImportFailure.DownloadIssue.InsecureRedirect, failure.downloadIssue)
    }

    @Test fun successfulDownloadReturnsOriginalBytes() {
        val reader = reader { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("archive bytes".toResponseBody()).build()
        }
        reader.open(SkillImportSource.Url("https://example.com/archive.zip")).use {
            assertEquals("archive bytes", it.readBytes().toString(Charsets.UTF_8))
        }
    }
}
