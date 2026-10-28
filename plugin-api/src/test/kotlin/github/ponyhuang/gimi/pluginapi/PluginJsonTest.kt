package github.ponyhuang.gimi.pluginapi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginJsonTest {

    @Test
    fun recursivelyConvertsToJsonNative() {
        val json = JSONObject()
            .put("id", 1)
            .put("name", "track")
            .put("nested", JSONObject().put("active", true))
            .put("tags", JSONArray().put("a").put("b"))
            .put("empty", JSONObject.NULL)

        val native = PluginJson.toNative(json) as Map<String, Any?>

        assertEquals(1, native["id"])
        assertEquals("track", native["name"])
        assertEquals(mapOf("active" to true), native["nested"])
        assertEquals(listOf("a", "b"), native["tags"])
        assertNull(native["empty"])
    }

}
