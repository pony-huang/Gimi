package github.ponyhuang.gimi.domain.sandbox

import org.junit.Assert.*
import org.junit.Test

class SandboxOutputTextTest {
    @Test fun preservesExactBoundaryAndChunkNewlines() {
        assertEquals("你好", truncateSandboxOutput("你好", 6))
        assertEquals("one\ntwo\n", truncateSandboxOutput("one\ntwo\n"))
    }
    @Test fun neverSplitsChineseOrEmojiAndReportsExactOmittedBytes() {
        assertEquals("你…[truncated 3 bytes]", truncateSandboxOutput("你好", 4))
        assertEquals("…[truncated 4 bytes]", truncateSandboxOutput("😀", 3))
        assertEquals("…[truncated 3 bytes]", truncateSandboxOutput("你", 0))
    }
    @Test fun normalizesUnpairedSurrogates() {
        assertEquals("a\uFFFDb", truncateSandboxOutput("a\uD800b"))
    }
}
