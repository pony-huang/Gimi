package github.ponyhuang.gimi.data.mcp.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import javax.inject.Inject
import javax.inject.Singleton

interface McpServerStorage {
    fun read(): String?

    fun write(value: String)
}

/** 使用 Android Keystore AES/GCM 加密整个 MCP 配置，不提供明文降级路径。 */
@Singleton
class KeystoreMcpServerStorage @Inject constructor(
    @ApplicationContext context: Context,
) : McpServerStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)

    override fun read(): String? = preferences.getString(SERVERS_KEY, null)?.let(cipher::decryptHealing)

    override fun write(value: String) {
        val encrypted = cipher.encryptOrThrow(value)
        preferences.edit(commit = true) { putString(SERVERS_KEY, encrypted) }
    }

    private companion object {
        const val PREFERENCES_NAME = "mcp_servers_secure_v2"
        const val SERVERS_KEY = "encrypted_servers"
        const val KEY_ALIAS = "mcp_server_settings_key_v2"
    }
}
