package github.ponyhuang.gimi.data.mcp.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface McpServerStorage {
    fun read(): String?

    fun write(value: String)
}

@Singleton
class KeystoreMcpServerStorage @Inject constructor(
    @ApplicationContext context: Context,
) : McpServerStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(SERVERS_KEY, null)?.let { payload ->
        runCatching { decrypt(payload) }.getOrNull()
    }

    override fun write(value: String) {
        val encrypted = retryWithFreshKeyAfterInvalidKey(
            resetKey = ::deleteSecretKey,
        ) {
            encrypt(value)
        }
        preferences.edit(commit = true) { putString(SERVERS_KEY, encrypted) }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2) { "Invalid encrypted MCP settings." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            .toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun deleteSecretKey() {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
            if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "mcp_servers_secure_v2"
        const val SERVERS_KEY = "encrypted_servers"
        const val KEY_ALIAS = "mcp_server_settings_key_v2"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

/**
 * Android 系统升级或锁屏凭据变更可能使已有 Keystore 密钥失效。
 *
 * 仅针对该可恢复情形重建密钥并重试一次；其余存储故障必须继续抛出，避免把凭据降级为明文。
 */
internal inline fun <T> retryWithFreshKeyAfterInvalidKey(
    resetKey: () -> Unit,
    operation: () -> T,
): T = try {
    operation()
} catch (_: InvalidKeyException) {
    resetKey()
    operation()
}
