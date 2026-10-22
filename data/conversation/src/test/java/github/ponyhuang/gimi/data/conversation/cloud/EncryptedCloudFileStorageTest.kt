package github.ponyhuang.gimi.data.conversation.cloud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 只替换密码器，验证真实磁盘提交和解密失败边界，不使用设备 Keystore。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class EncryptedCloudFileStorageTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private var failWrites = false
    private val record = CloudFile("file-shared", "scope-digest", "report.txt", "text/plain", 10,
        "content-digest", 1_700_000_000_000L, 1_700_604_800_000L, null, "/managed/report.txt")

    @Before fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        preferences = application.getSharedPreferences("cloud-files-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        context = object : ContextWrapper(application) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = preferences.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                delegate.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = if (failWrites) false else delegate.commit()
                        }
                    }
                }
        }
        mockkConstructor(KeystoreAesGcmStringCipher::class)
        every { anyConstructed<KeystoreAesGcmStringCipher>().encryptOrThrow(any()) } answers {
            firstArg<String>().reversed()
        }
        every { anyConstructed<KeystoreAesGcmStringCipher>().decryptHealing(any()) } answers {
            firstArg<String>().reversed()
        }
    }

    @After fun tearDown() { unmockkConstructor(KeystoreAesGcmStringCipher::class) }

    @Test fun recordsGoThroughTheCipherAndSurviveNewStorageInstances() {
        EncryptedCloudFileStorage(context).write(listOf(record))
        val payload = preferences.getString("records", null)!!
        assertFalse(payload.contains("file-shared"))
        assertFalse(payload.contains("report.txt"))
        assertEquals(listOf(record), EncryptedCloudFileStorage(context).read())
    }

    @Test fun failedCommitKeepsPreviousRecordsAndReportsFailure() {
        EncryptedCloudFileStorage(context).write(listOf(record))
        failWrites = true
        assertStorageFailure { EncryptedCloudFileStorage(context).write(emptyList()) }
        assertEquals(listOf(record), EncryptedCloudFileStorage(context).read())
    }

    @Test fun undecryptableOrMalformedRecordsAreNotTreatedAsAnEmptySuccessfulLibrary() {
        preferences.edit().putString("records", "invalid").commit()
        assertStorageFailure { EncryptedCloudFileStorage(context).read() }
        every { anyConstructed<KeystoreAesGcmStringCipher>().decryptHealing(any()) } returns null
        assertStorageFailure { EncryptedCloudFileStorage(context).read() }
    }

    private fun assertStorageFailure(block: () -> Unit) {
        try { block() } catch (failure: CloudFileException) {
            assertEquals(CloudFileFailure.StorageUnavailable, failure.reason)
            return
        }
        throw AssertionError("Expected storage failure")
    }
}
