package github.ponyhuang.gimi.data.logging

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingLogStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private var now = 2 * RollingLogStore.RETENTION_MILLIS
    private fun store(directory: File = temporary.newFolder()) = RollingLogStore(
        directory, { now }, maxBytes = 4096, chunkBytes = 1024, previewBytes = 1024,
    )

    @Test fun `retains recent logs and expires at exactly 24 hours`() {
        val store = store()
        store.append("first")
        now += RollingLogStore.RETENTION_MILLIS - 1
        store.append("second")
        assertTrue(store.read().content.contains("first"))
        now++
        assertEquals("second\n", store.read().content)
    }

    @Test fun `cleanup works without new writes and after restart`() {
        val directory = temporary.newFolder()
        store(directory).append("expired")
        now += RollingLogStore.RETENTION_MILLIS
        val restarted = store(directory)
        restarted.cleanup()
        assertEquals(0, directory.listFiles()!!.size)
        assertEquals("", restarted.read().content)
    }

    @Test fun `drops old and future events from logcat buffers`() {
        val store = store()
        store.append("old", now - RollingLogStore.RETENTION_MILLIS)
        store.append("future", now + 1)
        store.append("current", now)
        assertEquals("current\n", store.read().content)
    }

    @Test fun `mixed chunk removes only expired entries`() {
        val store = store()
        store.append("old")
        now += 100
        store.append("recent")
        now += RollingLogStore.RETENTION_MILLIS - 100
        assertEquals("recent\n", store.read().content)
    }

    @Test fun `rotates oldest chunks and always bounds disk usage`() {
        val directory = temporary.newFolder()
        val store = store(directory)
        repeat(100) {
            store.append("record-$it " + "x".repeat(150))
            assertTrue(directory.listFiles()!!.sumOf { file -> file.length() } <= 4096)
            assertTrue(directory.listFiles()!!.all { file -> file.length() <= 1024 })
        }
        assertTrue(store.read().content.contains("record-99"))
        val exported = StringBuilder()
        store.exportTo { exported.append(it) }
        assertFalse(exported.contains("record-0 "))
    }

    @Test fun `bounds oversized unicode messages and preserves record boundaries`() {
        val directory = temporary.newFolder()
        val store = store(directory)
        store.append("界\n".repeat(10000))
        assertTrue(directory.listFiles()!!.all { it.length() <= 1024 })
        assertEquals(1, store.read().content.lines().filter { it.isNotEmpty() }.size)
    }

    @Test fun `preview is bounded while export includes earlier retained logs`() {
        val store = store()
        repeat(15) { store.append("record-$it " + "x".repeat(150)) }
        val preview = store.read()
        val export = StringBuilder()
        store.exportTo { export.append(it) }
        assertTrue(preview.truncated)
        assertTrue(preview.content.toByteArray().size <= 1024)
        assertFalse(preview.content.contains("record-0 "))
        assertTrue(export.contains("record-0 "))
        assertTrue(export.endsWith(preview.content))
    }

    @Test fun `export filters expired records`() {
        val store = store()
        store.append("expired")
        now += RollingLogStore.RETENTION_MILLIS
        store.append("retained")
        val exported = StringBuilder()
        store.exportTo { exported.append(it) }
        assertEquals("retained\n", exported.toString())
    }

    @Test fun `reopening storage appends without replacing retained chunks`() {
        val directory = temporary.newFolder()
        val first = store(directory)
        repeat(8) { first.append("first-$it " + "x".repeat(150)) }
        store(directory).append("after-restart")
        val exported = StringBuilder()
        store(directory).exportTo { exported.append(it) }
        assertTrue(exported.contains("first-0"))
        assertTrue(exported.contains("after-restart"))
    }

    @Test fun `redacts common credentials before persistence and export`() {
        val store = store()
        store.append("Authorization: Bearer super-secret api_key=abc access_token=def password=xyz")
        store.append("{\"apiKey\":\"json-secret\"}")
        val exported = StringBuilder()
        store.exportTo { exported.append(it) }
        listOf("super-secret", "abc", "def", "xyz", "json-secret").forEach {
            assertFalse(exported.contains(it))
        }
        assertTrue(exported.contains("[REDACTED]"))
    }

    @Test fun `removes interrupted cleanup files`() {
        val directory = temporary.newFolder()
        File(directory, "00001.log.tmp").writeText("stale")
        store(directory).cleanup()
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `unwritable directory surfaces IO failure`() {
        val file = temporary.newFile()
        try {
            store(file).read()
            fail("Expected IOException")
        } catch (_: IOException) { }
    }

    @Test fun `export write failure propagates without deleting retained logs`() {
        val store = store()
        store.append("retained")
        try {
            store.exportTo { throw IOException("destination unavailable") }
            fail("Expected IOException")
        } catch (_: IOException) { }
        assertEquals("retained\n", store.read().content)
    }
}
