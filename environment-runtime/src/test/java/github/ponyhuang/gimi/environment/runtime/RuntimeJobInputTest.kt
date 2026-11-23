package github.ponyhuang.gimi.environment.runtime

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeJobInputTest {
    @Test fun preservesQueuedInputOrderAndClosesAfterWriteFailure() {
        val input = RuntimeJobInput()
        val received = ByteArrayOutputStream()
        input.enqueue("first")
        input.enqueue("\u4e8c\n")
        input.enqueue("fail")
        val output = object : OutputStream() {
            override fun write(value: Int) = error("Expected byte array writes")
            override fun write(bytes: ByteArray) {
                if (bytes.decodeToString() == "fail") throw IOException("Pipe closed")
                received.write(bytes)
            }
        }

        input.writeTo(output)

        assertEquals("first\u4e8c\n", received.toString("UTF-8"))
        assertThrows(IllegalStateException::class.java) { input.enqueue("retry") }
    }

    @Test fun rejectsOverflowAndOversizedUtf8Input() {
        val input = RuntimeJobInput()
        assertThrows(IllegalArgumentException::class.java) { input.enqueue("\u4e8c".repeat(1366)) }
        repeat(16) { input.enqueue("input") }
        assertThrows(IllegalStateException::class.java) { input.enqueue("overflow") }
        input.close()
        assertThrows(IllegalStateException::class.java) { input.enqueue("closed") }
    }
}
