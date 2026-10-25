package github.ponyhuang.gimi.data.a2a.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import javax.inject.Inject
import javax.inject.Singleton

interface RemoteAgentStorage {
    fun read(): String?

    fun write(value: String)
}

/**
 * 使用 Android Keystore AES/GCM 加密整个远程 A2A agent 配置，不提供明文降级路径。
 * 配置含 Bearer 凭据，敏感级别与 MCP 服务器配置一致。
 */
@Singleton
class KeystoreRemoteAgentStorage @Inject constructor(
    @ApplicationContext context: Context,
) : RemoteAgentStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)

    override fun read(): String? = preferences.getString(AGENTS_KEY, null)?.let(cipher::decryptHealing)

    override fun write(value: String) {
        val encrypted = cipher.encryptOrThrow(value)
        preferences.edit(commit = true) { putString(AGENTS_KEY, encrypted) }
    }

    private companion object {
        const val PREFERENCES_NAME = "remote_agents_secure_v1"
        const val AGENTS_KEY = "encrypted_remote_agents"
        const val KEY_ALIAS = "remote_agent_settings_key_v1"
    }
}
