package github.ponyhuang.asssistantai.data.skills

import github.ponyhuang.asssistantai.domain.skills.model.SkillImportFailure
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportSource
import java.io.IOException
import java.io.InputStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal class SkillArchiveReader(
    private val okHttpClient: OkHttpClient,
    private val localDocumentOpener: (String) -> InputStream?,
) {
    fun open(source: SkillImportSource): InputStream = when (source) {
        is SkillImportSource.LocalDocument -> openLocal(source.uri)
        is SkillImportSource.Url -> openUrl(source.value)
    }

    private fun openLocal(uri: String): InputStream {
        if (uri.isBlank()) invalidSource("The selected skill archive is unavailable.")
        return try {
            localDocumentOpener(uri)
                ?: invalidSource("The selected skill archive is unavailable.")
        } catch (failure: SkillImportFailure) {
            throw failure
        } catch (error: Exception) {
            throw SkillImportFailure(
                SkillImportFailure.Reason.InvalidSource,
                "The selected skill archive could not be opened.",
                error,
            )
        }
    }

    private fun openUrl(value: String): InputStream {
        val url = value.toHttpUrlOrNull()
            ?: invalidSource("Enter a valid HTTPS URL.")
        if (!url.isHttps) invalidSource("Only HTTPS skill URLs are supported.")
        val response = try {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute()
        } catch (error: IOException) {
            throw SkillImportFailure(
                SkillImportFailure.Reason.DownloadFailed,
                "The skill archive could not be downloaded.",
                error,
            )
        }
        if (!response.isSuccessful || !response.request.url.isHttps) {
            response.close()
            throw SkillImportFailure(
                SkillImportFailure.Reason.DownloadFailed,
                "The skill archive could not be downloaded securely.",
            )
        }
        val body = response.body ?: run {
            response.close()
            throw SkillImportFailure(
                SkillImportFailure.Reason.DownloadFailed,
                "The skill archive response was empty.",
            )
        }
        if (body.contentLength() > MAX_ARCHIVE_BYTES) {
            response.close()
            throw SkillImportFailure(
                SkillImportFailure.Reason.ArchiveTooLarge,
                "The ZIP archive is too large.",
            )
        }
        return body.byteStream()
    }

    private fun invalidSource(message: String): Nothing =
        throw SkillImportFailure(SkillImportFailure.Reason.InvalidSource, message)

    private companion object {
        const val MAX_ARCHIVE_BYTES = 20L * 1024L * 1024L
    }
}
