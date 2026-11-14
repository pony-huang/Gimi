package github.ponyhuang.gimi.environment.runtime

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject

/** 固定官方 Debian OCI 层，校验后在 staging 中解包并原子提交，取消不提交半成品。 */
internal class RootfsInstaller(private val context: Context) {
    fun install(active: () -> Boolean, progress: (Int) -> Unit) {
        val archive = File(context.cacheDir, "debian-rootfs.tar.gz")
        if (!archive.isFile || RuntimeEngine.digest(archive.readBytes()) != DIGEST) {
            Log.w(TAG, "rootfs download starting (archive missing or digest mismatch)")
            val token = connection("https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/debian:pull").let { request ->
                try { JSONObject(request.inputStream.bufferedReader().use { it.readText() }).getString("token") } finally { request.disconnect() }
            }
            val request = connection("https://registry-1.docker.io/v2/library/debian/blobs/sha256:$DIGEST")
            request.setRequestProperty("Authorization", "Bearer $token")
            try {
                request.inputStream.use { input -> archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        if (!active()) throw IOException("Installation canceled")
                        val read = input.read(buffer); if (read < 0) break
                        total += read; if (total > SIZE) throw IOException("Image size mismatch")
                        output.write(buffer, 0, read); progress((total * 80 / SIZE).toInt())
                    }
                } }
            } finally { request.disconnect() }
            if (RuntimeEngine.digest(archive.readBytes()) != DIGEST) throw IOException("Image checksum mismatch")
        } else { Log.w(TAG, "rootfs archive reused (digest verified)") }
        if (context.filesDir.usableSpace < 600L * 1024 * 1024) throw IOException("Insufficient space")
        val staging = File(context.filesDir, "rootfs-staging")
        remove(staging); staging.mkdirs()
        try {
            val links = mutableListOf<Pair<File, String>>()
            TarArchiveInputStream(GZIPInputStream(archive.inputStream())).use { tar ->
                while (true) {
                    if (!active()) throw IOException("Installation canceled")
                    val entry = tar.nextEntry ?: break
                    val target = RuntimeEngine.contained(staging, entry.name)
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink -> links += target to entry.linkName
                        entry.isLink -> {
                            target.parentFile?.mkdirs()
                            RuntimeEngine.contained(staging, entry.linkName).copyTo(target, overwrite = true)
                        }
                        entry.isFile -> {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { tar.copyTo(it) }
                            Os.chmod(target.path, entry.mode and 511)
                        }
                    }
                }
            }
            // 将绝对符号链接改写为 rootfs 内的相对链接，防止宿主文件 API 跳出环境。
            Log.w(TAG, "tar extracted, rewriting ${links.size} symlinks")
            // /data/user/0 是指向 /data/data 的符号链接：canonicalFile 会改写路径前缀，
            // 目标与 staging 必须同为规范形态比较，否则 containment 检查必然误判。
            val stagingPath = staging.canonicalFile.toPath()
            links.forEach { (file, link) ->
                file.parentFile?.mkdirs()
                val target = if (link.startsWith('/')) RuntimeEngine.contained(staging, link).toPath() else file.parentFile.toPath().resolve(link).normalize()
                if (!target.startsWith(stagingPath)) throw IOException("Unsafe archive link: $file -> $link")
                Files.createSymbolicLink(file.toPath(), file.parentFile.toPath().relativize(target))
            }
            File(staging, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            if (!active()) throw IOException("Installation canceled")
            Log.w(TAG, "committing rootfs (removing old, atomic move)")
            val root = File(context.filesDir, "rootfs")
            if (File(context.filesDir, "installed").exists()) throw IOException("Environment already installed")
            remove(root)
            Files.move(staging.toPath(), root.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Log.w(TAG, "rootfs installed")
            progress(100)
        } finally { remove(staging) }
    }
    private fun connection(url: String) = (URI(url).toURL().openConnection() as HttpURLConnection).apply { connectTimeout = 30_000; readTimeout = 30_000 }
    private fun remove(file: File) {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(file.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
    companion object {
        private const val TAG = "RuntimeInstall"
        const val DIGEST = "acd43e9bebc37bc4ba7a5f47bd7598913337803e53d468f37d940f11f5d5f1ef"
        const val SIZE = 30214037L
    }
}
