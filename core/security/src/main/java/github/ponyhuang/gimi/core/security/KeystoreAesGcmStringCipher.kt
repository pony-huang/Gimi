package github.ponyhuang.gimi.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore AES/GCM 字符串加解密，密钥按 [keyAlias] 独立管理。
 *
 * 由 McpServerStorage / MemorySettingsStorage / ModelServiceRepository 等安全存储共享，
 * 统一 `Base64(iv) + ":" + Base64(ciphertext)` 载荷格式与失效密钥的恢复策略。
 */
class KeystoreAesGcmStringCipher(private val keyAlias: String) {

    /**
     * 加密；密钥失效时删除并重建后重试一次，其余异常继续抛出。
     */
    fun encryptOrThrow(plainText: String): String =
        retryAfterInvalidKey(resetKey = ::deleteKey) { encrypt(plainText) }

    /**
     * 解密；密钥失效时删除失效密钥（自愈，下次写入生成新 key）并告警，返回 null。
     * 其它解密失败也返回 null，但仅密钥失效会触发自愈。
     */
    fun decryptHealing(payload: String): String? = try {
        decrypt(payload)
    } catch (invalidKey: InvalidKeyException) {
        Log.w(TAG, "Keystore key for '$keyAlias' is invalidated; discarding it so the next write regenerates a fresh key", invalidKey)
        deleteKey()
        null
    } catch (failure: Exception) {
        Log.w(TAG, "Unable to decrypt payload for key alias '$keyAlias'", failure)
        null
    }

    fun deleteKey() {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
            if (containsAlias(keyAlias)) deleteEntry(keyAlias)
        }
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
        require(parts.size == 2) { "Invalid encrypted payload." }
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
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
    }

    private companion object {
        const val TAG = "core.security.cipher"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
