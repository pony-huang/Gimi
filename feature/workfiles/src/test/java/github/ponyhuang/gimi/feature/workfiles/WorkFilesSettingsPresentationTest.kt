package github.ponyhuang.gimi.feature.workfiles

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkFilesSettingsPresentationTest {
    @Test
    fun storageBytesUseCompactBinaryUnits() {
        assertEquals("0 B", formatStorageBytes(0L))
        assertEquals("1 KB", formatStorageBytes(1024L))
        assertEquals("1.5 MB", formatStorageBytes(1572864L))
        assertEquals("2 GB", formatStorageBytes(2147483648L))
    }
}
