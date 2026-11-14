package github.ponyhuang.gimi.environment.runtime

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 将当前 App 网络的 DNS 原子写入 Linux，空网络状态保留最近一次配置。 */
internal object RuntimeDnsConfig {
    fun write(root: File, servers: List<InetAddress>) {
        if (servers.isEmpty()) return
        val content = servers.mapNotNull { it.hostAddress }.distinct().take(3)
            .joinToString("", transform = { "nameserver $it\n" })
        if (content.isEmpty()) return
        val file = RuntimeEngine.contained(root, "etc/resolv.conf")
        if (file.isFile && file.readText() == content) return
        val staging = File.createTempFile(".resolv-", ".tmp", file.parentFile)
        try {
            staging.writeText(content)
            Files.move(staging.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { staging.delete() }
    }
}
