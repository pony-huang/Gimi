package github.ponyhuang.gimi.data.plugin.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogParserTest {

    private val parser = PluginCatalogParser()

    @Test
    fun parsesEntriesAndSortsBySortOrder() {
        val entries = parser.parse(
            """
            {
              "schemaVersion": 1,
              "name": "Test feed",
              "entries": [
                {
                  "key": "later",
                  "packageName": "github.ponyhuang.gimi.plugin.later",
                  "title": "Later",
                  "summary": {"zh": "稍后", "en": "Later"},
                  "description": {"zh": "描述", "en": "Description"},
                  "highlights": [{"zh": "能力", "en": "Capability"}],
                  "sortOrder": 20,
                  "pluginVersion": 1,
                  "releaseAssetPrefix": "Gimi-plugin-later-v",
                  "repoUrl": "https://example.com",
                  "iconKey": "later",
                  "tags": ["tag"]
                },
                {
                  "key": "first",
                  "packageName": "github.ponyhuang.gimi.plugin.first",
                  "title": "First",
                  "summary": {"zh": "第一", "en": "First"},
                  "description": {"zh": "描述", "en": "Description"},
                  "sortOrder": 10,
                  "pluginVersion": 2,
                  "releaseAssetPrefix": "Gimi-plugin-first-v",
                  "iconKey": "first"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("first", "later"), entries.map { it.key })
        assertEquals(2, entries[0].pluginVersion)
        assertEquals("https://example.com", entries[1].repoUrl)
        assertEquals(listOf("tag"), entries[1].tags)
        assertNull(entries[0].apkUrl)
    }

    @Test
    fun directApkUrlIsPropagated() {
        val entries = parser.parse(
            """
            {
              "entries": [
                {
                  "key": "custom",
                  "packageName": "com.example.plugin.custom",
                  "title": "Custom",
                  "summary": {"zh": "自定义", "en": "Custom"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 3,
                  "apkUrl": "https://example.com/custom.apk",
                  "sha256": "${"c".repeat(64)}",
                  "sizeBytes": 12345,
                  "versionCode": 5000000000,
                  "versionName": " 1.2.0 "
                }
              ]
            }
            """.trimIndent(),
        )

        val entry = entries.single()
        assertEquals("https://example.com/custom.apk", entry.apkUrl)
        assertEquals("c".repeat(64), entry.sha256)
        assertEquals(12345L, entry.sizeBytes)
        assertEquals(5000000000L, entry.versionCode)
        assertEquals("1.2.0", entry.versionName)
        assertTrue(entry.releaseAssetPrefix.isEmpty())
    }

    @Test
    fun absentInstallVersionIsUnknownAndNonPositiveVersionIsDropped() {
        val valid = """{"entries":[{"key":"test","packageName":"plugin.test","title":"Test",
            "summary":{"zh":"测试"},"description":{"zh":"测试"},"apkUrl":"https://example.com/test.apk"}]}"""
        assertNull(parser.parse(valid).single().versionCode)
        assertNull(parser.parse(valid).single().versionName)
        for (code in listOf(0, -1)) {
            assertTrue(parser.parse(valid.replace("\"key\":", "\"versionCode\":$code,\"key\":" )).isEmpty())
        }
    }

    @Test
    fun invalidDirectUrlsAreDropped() {
        val entries = parser.parse(
            """
            {
              "entries": [
                {
                  "key": "http",
                  "packageName": "com.example.plugin.http",
                  "title": "HTTP",
                  "summary": {"zh": "明文", "en": "Plain"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 1,
                  "apkUrl": "http://example.com/custom.apk"
                },
                {
                  "key": "invalid-sha",
                  "packageName": "com.example.plugin.sha",
                  "title": "Bad sha",
                  "summary": {"zh": "坏", "en": "Bad"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 1,
                  "apkUrl": "https://example.com/custom.apk",
                  "sha256": "short"
                }
              ]
            }
            """.trimIndent(),
        )

        // 非 HTTPS 直链整条丢弃；无效 sha 被忽略但条目保留。
        assertEquals(listOf("invalid-sha"), entries.map { it.key })
        assertNull(entries.single().sha256)
    }

    @Test
    fun skipsInvalidAndDuplicateEntries() {
        val entries = parser.parse(
            """
            {
              "entries": [
                {
                  "key": "ok",
                  "packageName": "github.ponyhuang.gimi.plugin.ok",
                  "title": "OK",
                  "summary": {"zh": "好", "en": "OK"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 1,
                  "releaseAssetPrefix": "Gimi-plugin-ok-v"
                },
                {
                  "key": "ok",
                  "packageName": "github.ponyhuang.gimi.plugin.duplicate",
                  "title": "Duplicate",
                  "summary": {"zh": "重复", "en": "Duplicate"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 1,
                  "releaseAssetPrefix": "Gimi-plugin-duplicate-v"
                },
                {
                  "key": "bad-package",
                  "packageName": "../escape",
                  "title": "Bad",
                  "summary": {"zh": "坏", "en": "Bad"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 1,
                  "releaseAssetPrefix": "Gimi-plugin-bad-v"
                },
                {
                  "key": "bad-prefix",
                  "packageName": "github.ponyhuang.gimi.plugin.bad",
                  "title": "Bad",
                  "summary": {"zh": "坏", "en": "Bad"},
                  "description": {"zh": "描述", "en": "Description"},
                  "pluginVersion": 0,
                  "releaseAssetPrefix": "Plugin-bad-v"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("ok"), entries.map { it.key })
        assertTrue(entries.single().highlights.isEmpty())
    }

    @Test
    fun bundledCatalogIsValidAndCoversFirstPartyPlugins() {
        val file = listOf(
            File("src/main/assets/plugin_catalog.json"),
            File("data/plugin/src/main/assets/plugin_catalog.json"),
        ).firstOrNull(File::isFile)
            ?: error("plugin_catalog.json not found from ${File(".").absolutePath}")

        val entries = parser.parse(file.readText())
        assertEquals(
            setOf("spotify", "zhihu", "v2ex", "weibo", "xiaohongshu", "netease"),
            entries.map { it.key }.toSet(),
        )
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
        entries.forEach { entry ->
            assertTrue(entry.packageName.startsWith("github.ponyhuang.gimi.plugin."))
            assertTrue(entry.releaseAssetPrefix.startsWith("Gimi-plugin-"))
            assertTrue(entry.releaseAssetPrefix.endsWith("-v"))
            assertEquals("Gimi-plugin-${entry.key}-v", entry.releaseAssetPrefix)
            assertTrue(entry.summary.zh.isNotBlank())
            assertTrue(entry.summary.en.isNotBlank())
            assertTrue(entry.description.zh.isNotBlank())
            assertFalse(entry.tags.isEmpty())
            assertTrue(entry.pluginVersion > 0)
            assertNull(entry.apkUrl)
        }
    }
}
