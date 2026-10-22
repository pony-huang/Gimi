package github.ponyhuang.gimi.data.conversation.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface CloudFileStorage {
    fun read(): List<CloudFile>
    fun write(files: List<CloudFile>)
}

/** 文件 ID 和本地来源一起加密保存；不随单个会话删除，也不存储连接凭据。 */
@Singleton
internal class EncryptedCloudFileStorage @Inject constructor(
    @ApplicationContext context: Context,
) : CloudFileStorage {
    private val preferences = context.getSharedPreferences("cloud-files", Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher("gimi-cloud-files")
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): List<CloudFile> {
        val encrypted = preferences.getString("records", null) ?: return emptyList()
        return try {
            val plainText = cipher.decryptHealing(encrypted)
                ?: throw CloudFileException(CloudFileFailure.StorageUnavailable)
            json.decodeFromString<List<CloudFile>>(plainText)
        } catch (failure: CloudFileException) {
            throw failure
        } catch (_: Exception) {
            throw CloudFileException(CloudFileFailure.StorageUnavailable)
        }
    }

    override fun write(files: List<CloudFile>) {
        try {
            val encrypted = cipher.encryptOrThrow(json.encodeToString(files))
            if (!preferences.edit().putString("records", encrypted).commit()) {
                throw CloudFileException(CloudFileFailure.StorageUnavailable)
            }
        } catch (failure: CloudFileException) {
            throw failure
        } catch (_: Exception) {
            throw CloudFileException(CloudFileFailure.StorageUnavailable)
        }
    }
}
