package github.ponyhuang.gimi.data.appupdate.apk

import github.ponyhuang.gimi.domain.appupdate.model.ApkAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkAssetSelectorTest {

    private val arm64 = ApkAsset("Gimi-v0.2.0-arm64-v8a.apk", "https://x/a.apk", 1, null)
    private val universal = ApkAsset("Gimi-v0.2.0-universal.apk", "https://x/u.apk", 2, null)

    @Test
    fun `prefers exact abi match`() {
        val selected = ApkAssetSelector.select(listOf(arm64, universal), listOf("arm64-v8a"))
        assertEquals(arm64, selected)
    }

    @Test
    fun `honors device abi priority order`() {
        val x86 = ApkAsset("Gimi-v0.2.0-x86_64.apk", "https://x/x.apk", 3, null)
        val selected = ApkAssetSelector.select(
            listOf(x86, arm64),
            listOf("arm64-v8a", "x86_64"),
        )
        assertEquals(arm64, selected)
    }

    @Test
    fun `falls back to universal when abi missing`() {
        val selected = ApkAssetSelector.select(listOf(arm64, universal), listOf("x86_64"))
        assertEquals(universal, selected)
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(ApkAssetSelector.select(listOf(arm64), listOf("x86_64")))
        assertNull(ApkAssetSelector.select(emptyList(), listOf("arm64-v8a")))
    }
}
