package github.ponyhuang.gimi.core.security

import java.security.InvalidKeyException

/**
 * 密钥失效恢复：Android 系统升级或锁屏凭据变更可能使既有 Keystore 密钥失效，
 * 此时仅删除并重建密钥后重试一次；其余异常继续向上传播，避免把凭据降级为明文。
 */
fun <T> retryAfterInvalidKey(
    resetKey: () -> Unit,
    operation: () -> T,
): T = try {
    operation()
} catch (_: InvalidKeyException) {
    resetKey()
    operation()
}
