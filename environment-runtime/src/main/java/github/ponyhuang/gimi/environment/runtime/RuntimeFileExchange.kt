package github.ponyhuang.gimi.environment.runtime

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** 文件先完整写入暂存区，验证后原子发布；半成品不会成为可执行资源。 */
internal class RuntimeFileExchange(private val workspace: File) {
    fun importFile(path: String, input: InputStream): String {
        val target = RuntimeEngine.contained(workspace, path)
        require(target != workspace.canonicalFile)
        target.parentFile!!.mkdirs()
        val staging = File(target.parentFile, ".transfer-${UUID.randomUUID()}")
        try {
            staging.outputStream().use { copyBounded(input, it) }
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { staging.delete() }
        return target.relativeTo(workspace).invariantSeparatorsPath
    }

    fun prepareSkill(input: InputStream): String {
        val cache = File(workspace, ".skills").apply { mkdirs() }
        val archive = File.createTempFile("skill-", ".zip", cache)
        val staging = File(cache, ".prepare-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            archive.outputStream().use { copyBounded(input, it) }
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { source ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(cache, hash)
            if (!target.isDirectory) {
                ZipInputStream(archive.inputStream()).use { zip ->
                    var count = 0
                    var total = 0L
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(++count <= 2000)
                        require(!entry.name.startsWith("/") && !entry.name.contains('\\'))
                        val file = RuntimeEngine.contained(staging, entry.name)
                        require(file != staging.canonicalFile)
                        if (entry.isDirectory) file.mkdirs() else {
                            file.parentFile!!.mkdirs()
                            file.outputStream().use { total += copyBounded(zip, it, LIMIT - total) }
                        }
                    }
                }
                require(File(staging, "SKILL.md").isFile)
                synchronized(RuntimeFileExchange::class.java) {
                    if (!target.exists()) Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
            }
            return ".skills/$hash"
        } finally { archive.delete(); staging.deleteRecursively() }
    }

    companion object {
        private const val LIMIT = 100L * 1024 * 1024
        private fun copyBounded(input: InputStream, output: java.io.OutputStream, limit: Long = LIMIT): Long {
            var total = 0L
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return total
                total += count
                require(total <= limit) { "File exceeds transfer limit" }
                output.write(buffer, 0, count)
            }
        }
    }
}
