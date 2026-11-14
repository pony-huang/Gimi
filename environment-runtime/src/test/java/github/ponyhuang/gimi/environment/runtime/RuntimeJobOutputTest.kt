package github.ponyhuang.gimi.environment.runtime

import org.junit.Assert.*
import org.junit.Test

/** 后台任务的输出在完成前即可查询，且持续消费输出时保持内存有界。 */
class RuntimeJobOutputTest {
    @Test fun exposesPartialOutputBeforeTaskCompletes() {
        val output = RuntimeJobOutput()
        output.append("login link\n".toCharArray(), 11)
        assertEquals("login link\n", output.snapshot())
        output.append("done".toCharArray(), 4)
        assertEquals("login link\ndone", output.snapshot())
    }

    @Test fun outputRemainsBoundedAndPreservesEarlierLoginLink() {
        val output = RuntimeJobOutput()
        output.append("link".toCharArray(), 4)
        val data = "x".repeat(40_000).toCharArray()
        output.append(data, data.size)
        assertEquals(30_000, output.snapshot().length)
        assertTrue(output.snapshot().startsWith("link"))
    }
}
