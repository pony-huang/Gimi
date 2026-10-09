package github.ponyhuang.gimi.data.logging

import org.junit.Assert.*
import org.junit.Test

class LogcatCursorTest {
    @Test fun `preserves multiple records and identical messages at same timestamp`() {
        val cursor = LogcatCursor(1000)
        cursor.beginSession()
        assertEquals(1001L, cursor.timestamp("1.001 12 12 I tag: first"))
        assertEquals(1001L, cursor.timestamp("1.001 12 12 I tag: second"))
        assertEquals(1001L, cursor.timestamp("1.001 12 12 I tag: second"))
    }

    @Test fun `skips already collected replay but keeps new records after restart`() {
        val cursor = LogcatCursor(1000)
        cursor.beginSession()
        val first = "1.001 12 12 I tag: first"
        cursor.timestamp(first)
        cursor.timestamp(first)
        cursor.beginSession()
        assertNull(cursor.timestamp(first))
        assertNull(cursor.timestamp(first))
        assertEquals(1001L, cursor.timestamp(first))
        assertEquals(1002L, cursor.timestamp("1.002 12 12 I tag: next"))
        assertEquals("1.002", cursor.since())
    }

    @Test fun `rejects buffer headers invalid numbers and old events`() {
        val cursor = LogcatCursor(1000)
        assertNull(cursor.timestamp("--------- beginning of main"))
        assertNull(cursor.timestamp("logcat: unavailable"))
        assertNull(cursor.timestamp("NaN invalid"))
        assertNull(cursor.timestamp("Infinity invalid"))
        assertNull(cursor.timestamp("0.999 12 12 I tag: old"))
    }
}
