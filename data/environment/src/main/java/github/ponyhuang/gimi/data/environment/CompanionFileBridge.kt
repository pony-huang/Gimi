package github.ponyhuang.gimi.data.environment

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.WorkspaceStorageIds
import github.ponyhuang.gimi.domain.environment.EnvironmentFileBridge
import github.ponyhuang.gimi.domain.environment.EnvironmentRepository
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/** 大文件经描述符传输，控制消息只携带会话、路径与状态。 */
class CompanionFileBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: EnvironmentRepository,
    private val documents: DocumentSearchRepository,
    private val storage: StorageRegistry,
) : EnvironmentFileBridge {
    private fun call(method: String, args: Bundle): Bundle {
        val result = context.contentResolver.call("content://$RUNTIME_PACKAGE".toUri(), method, null, args)
            ?: throw IOException("Runtime unavailable")
        if (result.getBoolean("failed")) throw IOException("File transfer failed")
        return result
    }

    private suspend fun checkReady() {
        check(environment.status().let { it.enabled && it.runtimeUsable }) { "Runtime unavailable" }
    }

    override suspend fun importFile(session: String, source: String, name: String): String = withContext(Dispatchers.IO) {
        checkReady()
        require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' })
        val uri = source.toUri()
        val descriptor = if (uri.scheme == "content") {
            require(uri.authority == android.provider.MediaStore.AUTHORITY || documents.isAuthorized(source))
            context.contentResolver.openFileDescriptor(uri, "r")
        } else {
            val file = File(source).canonicalFile
            val root = storage.resolve(WorkspaceStorageIds.WORKSPACE).canonicalFile
            require(file.parentFile == root && file.isFile && !file.name.endsWith(".tmp"))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        descriptor.use { descriptor ->
            requireNotNull(descriptor)
            call("importFile", Bundle().apply {
                putString("session", session); putString("path", "inputs/${UUID.randomUUID()}/$name")
                putParcelable("descriptor", descriptor)
            }).getString("path") ?: throw IOException("Missing path")
        }
    }

    override suspend fun prepareSkill(session: String, archivePath: String): String = withContext(Dispatchers.IO) {
        checkReady()
        ParcelFileDescriptor.open(File(archivePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            call("prepareSkill", Bundle().apply {
                putString("session", session); putParcelable("descriptor", descriptor)
            }).getString("path") ?: throw IOException("Missing path")
        }
    }

    override suspend fun publishFile(session: String, path: String): String = withContext(Dispatchers.IO) {
        checkReady()
        val root = storage.resolve(WorkspaceStorageIds.WORKSPACE, create = true)
        val name = path.substringAfterLast('/').also { require(it.isNotBlank() && it != "." && it != "..") }
        val target = File(root, "${UUID.randomUUID()}-$name")
        val staging = File(root, "${UUID.randomUUID()}.tmp")
        try {
            val uri = "content://$RUNTIME_PACKAGE/export".toUri().buildUpon()
                .appendQueryParameter("session", session).appendQueryParameter("path", path).build()
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                staging.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= 100L * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                }
            }
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            target.absolutePath
        } finally { staging.delete() }
    }
}
