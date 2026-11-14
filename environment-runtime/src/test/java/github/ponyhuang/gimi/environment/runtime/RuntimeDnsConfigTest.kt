package github.ponyhuang.gimi.environment.runtime

import java.io.File
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DNS 配置使用当前网络并原子更新，离线时保留已有可用配置。 */
class RuntimeDnsConfigTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun writesCurrentNetworkDnsAndRemovesDuplicates() {
        val root = temporary.newFolder()
        File(root, "etc").mkdirs()
        val address = InetAddress.getByName("192.0.2.1")
        RuntimeDnsConfig.write(root, listOf(address, address))
        assertEquals("nameserver 192.0.2.1\n", File(root, "etc/resolv.conf").readText())
        assertEquals(listOf("resolv.conf"), File(root, "etc").listFiles()!!.map { it.name })
    }

    @Test fun offlineStatePreservesPreviousDns() {
        val root = temporary.newFolder()
        File(root, "etc").mkdirs()
        val file = File(root, "etc/resolv.conf").apply { writeText("previous") }
        RuntimeDnsConfig.write(root, emptyList())
        assertEquals("previous", file.readText())
    }
}
