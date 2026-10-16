package github.ponyhuang.gimi.data.sandbox

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 配置存储边界，测试使用内存实现；真实实现不得明文降级。 */
interface SandboxSettingsStorage {
    fun read(): SandboxConfiguration?
    fun write(configuration: SandboxConfiguration)
}

/** 加密载荷的完整配置；apiKey 和 envs 均不单独存入明文 Preferences。 */
@Serializable
private data class StoredSandboxSettings(
    val enabled: Boolean = false,
    val apiUrl: String = "https://api.e2b.app",
    val apiKey: String = "",
    val template: String = "code-interpreter-v1",
    val idleTimeoutSeconds: Int = 300,
    val envs: Map<String, String> = emptyMap(),
    val sandboxUrl: String = "",
)

/** 使用独立 Keystore alias 加密整个沙盒配置，损坏时回到关闭且未配置状态。 */
@Singleton
class KeystoreSandboxSettingsStorage @Inject constructor(@ApplicationContext context: Context) : SandboxSettingsStorage {
    private val preferences = context.getSharedPreferences("sandbox_settings_secure_v1", Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher("sandbox_settings_key_v1")
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): SandboxConfiguration? {
        val payload = preferences.getString("encrypted_settings", null) ?: return null
        val plain = cipher.decryptHealing(payload) ?: return null
        val stored = try {
            json.decodeFromString<StoredSandboxSettings>(plain)
        } catch (_: SerializationException) {
            return null
        }
        return SandboxConfiguration(stored.enabled, SandboxConnection(stored.apiUrl, stored.apiKey, stored.template, stored.idleTimeoutSeconds, stored.envs.toMap(), stored.sandboxUrl))
    }

    override fun write(configuration: SandboxConfiguration) {
        val connection = configuration.connection
        val stored = StoredSandboxSettings(configuration.enabled, connection.apiUrl, connection.apiKey, connection.template, connection.idleTimeoutSeconds, connection.envs.toMap(), connection.sandboxUrl)
        val encrypted = cipher.encryptOrThrow(json.encodeToString(StoredSandboxSettings.serializer(), stored))
        if (!preferences.edit().putString("encrypted_settings", encrypted).commit()) {
            throw IOException("Unable to save sandbox settings")
        }
    }
}
