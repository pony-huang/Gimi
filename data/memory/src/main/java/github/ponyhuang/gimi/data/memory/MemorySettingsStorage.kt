package github.ponyhuang.gimi.data.memory

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 安全存储中的记忆配置载荷。
 *
 * @property memoryEnabled 是否启用记忆（总开关）。
 * @property mem0Enabled 是否启用 Mem0。
 * @property apiKey Mem0 API Key。
 */
@Serializable
data class StoredMemorySettings(
    val memoryEnabled: Boolean = true,
    val mem0Enabled: Boolean = false,
    val apiKey: String = "",
)

interface MemorySettingsStorage {
    fun read(): StoredMemorySettings?

    fun write(value: StoredMemorySettings)
}

/** 使用 Android Keystore AES/GCM 加密整个 Mem0 配置，不提供明文降级路径。 */
@Singleton
class KeystoreMemorySettingsStorage @Inject constructor(
    @ApplicationContext context: Context,
) : MemorySettingsStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)

    override fun read(): StoredMemorySettings? = preferences.getString(SETTINGS_KEY, null)
        ?.let { payload ->
            cipher.decryptHealing(payload)
                ?.let { runCatching { json.decodeFromString(StoredMemorySettings.serializer(), it) }.getOrNull() }
        }

    override fun write(value: StoredMemorySettings) {
        val encrypted = cipher.encryptOrThrow(json.encodeToString(StoredMemorySettings.serializer(), value))
        preferences.edit(commit = true) { putString(SETTINGS_KEY, encrypted) }
    }

    private companion object {
        const val PREFERENCES_NAME = "memory_settings_secure_v1"
        const val SETTINGS_KEY = "encrypted_settings"
        const val KEY_ALIAS = "memory_settings_key_v1"
    }
}
