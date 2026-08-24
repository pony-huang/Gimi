package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.data.voicewake.notification.WakeModelNotifier
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WakeModelRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var repository: WakeModelRepository

    @Before
    fun setUp() {
        val context = mockk<Context> {
            every { filesDir } returns File(temporaryFolder.root, "files").apply { mkdirs() }
            every { cacheDir } returns File(temporaryFolder.root, "cache").apply { mkdirs() }
            every { getString(any()) } returns "Downloading wake model"
        }
        val pendingCall = mockk<Call>(relaxed = true)
        val callback = slot<Callback>()
        every { pendingCall.enqueue(capture(callback)) } returns Unit
        val client = mockk<OkHttpClient> {
            every { newCall(any<Request>()) } returns pendingCall
        }
        repository = WakeModelRepository(
            context,
            HttpFileDownloader(client),
            NoOpWakeModelNotifier,
        )
    }

    @After
    fun tearDown() {
        repository.cancelInstall(WakeModelCatalog.Chinese.id)
    }

    @Test
    fun `install marks model downloading before network work is scheduled`() {
        repository.install(WakeModelCatalog.Chinese.id)

        val state = repository.states.value.getValue(WakeModelCatalog.Chinese.id)
        assertEquals(WakeModelStatus.Downloading, state.status)
        assertEquals(0f, state.progress)
    }

    private object NoOpWakeModelNotifier : WakeModelNotifier {
        override fun showDownloading(
            info: WakeModelInfo,
            progress: Float,
            totalBytes: Long,
        ) = Unit

        override fun showInstalling(info: WakeModelInfo) = Unit

        override fun showReady(info: WakeModelInfo) = Unit

        override fun showFailed(info: WakeModelInfo) = Unit

        override fun cancel(modelId: String) = Unit
    }
}
