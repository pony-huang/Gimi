package github.ponyhuang.gimi.environment.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 文件交换边界，覆盖大文件、相对资源、版本复用与非法归档。 */
class RuntimeFileExchangeTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test fun importsFilesLargerThanBinderByteLimit() {
        val root = temporary.newFolder()
        val bytes = ByteArray(600_000) { (it % 127).toByte() }
        val path = RuntimeFileExchange(root).importFile("inputs/example.bin", ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, File(root, path).readBytes())
        assertFalse(root.walkTopDown().any { it.name.startsWith(".transfer-") })
    }

    @Test fun preservesResourcesAndReusesIdenticalSnapshot() {
        val root = temporary.newFolder()
        val exchange = RuntimeFileExchange(root)
        val bytes = archive("SKILL.md" to "instructions", "scripts/main.py" to "print(1)", "assets/template.txt" to "template")
        val first = exchange.prepareSkill(ByteArrayInputStream(bytes))
        assertEquals(first, exchange.prepareSkill(ByteArrayInputStream(bytes)))
        assertEquals("template", File(root, "$first/assets/template.txt").readText())
        assertFalse(File(root, ".skills").listFiles()!!.any { it.name.startsWith(".") || it.extension == "zip" })
    }

    @Test fun rejectsTraversalAndLeavesNoPartialSnapshot() {
        val root = temporary.newFolder()
        try {
            RuntimeFileExchange(root).prepareSkill(ByteArrayInputStream(archive("../escape" to "bad")))
            fail("Expected unsafe path rejection")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, File(root, ".skills").listFiles()!!.size)
    }

    @Test fun missingManifestCannotBePublished() {
        val root = temporary.newFolder()
        try {
            RuntimeFileExchange(root).prepareSkill(ByteArrayInputStream(archive("script.py" to "pass")))
            fail("Expected manifest rejection")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, File(root, ".skills").listFiles()!!.size)
    }

    @Test fun rejectsImportOutsideWorkspace() {
        val root = temporary.newFolder()
        try {
            RuntimeFileExchange(root).importFile("../escape", ByteArrayInputStream(byteArrayOf(1)))
            fail("Expected path rejection")
        } catch (_: IllegalArgumentException) { }
    }
}
