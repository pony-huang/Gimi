package dev.e2b.sdk

/** Sandbox filesystem. Binary transfers use HTTP; metadata operations use Connect RPC. */
class Filesystem internal constructor(private val envd: Envd) {
    @JvmOverloads fun read(path: String, user: String? = null): String = readBytes(path, user).toString(Charsets.UTF_8)
    @JvmOverloads fun readBytes(path: String, user: String? = null): ByteArray = envd.readFile(path, user)
    @JvmOverloads fun write(path: String, text: String, user: String? = null): WriteInfo = write(path, text.toByteArray(Charsets.UTF_8), user)
    @JvmOverloads fun write(path: String, bytes: ByteArray, user: String? = null): WriteInfo {
        val json = envd.writeFile(path, bytes, user)
        if (!json.isJsonArray || json.asJsonArray.size() != 1) throw ProtocolException("Expected one file-upload acknowledgement")
        val entry = Json.obj(json.asJsonArray[0])
        return WriteInfo(entry.requiredText("name"), entry.requiredText("path"), entry.requiredText("type"))
    }

    @JvmOverloads fun list(path: String, depth: Int = 1, user: String? = null): List<EntryInfo> {
        require(depth > 0)
        val json = envd.unary("filesystem.Filesystem", "ListDir", mapOf("path" to path, "depth" to depth), user)
        return json.getAsJsonArray("entries")?.map { entry(Json.obj(it)) } ?: emptyList()
    }

    @JvmOverloads fun getInfo(path: String, user: String? = null): EntryInfo = entry(
        Json.obj(envd.unary("filesystem.Filesystem", "Stat", mapOf("path" to path), user).get("entry")
            ?: throw ProtocolException("Missing file entry")),
    )

    @JvmOverloads fun exists(path: String, user: String? = null): Boolean = try {
        getInfo(path, user); true
    } catch (_: NotFoundException) { false }

    @JvmOverloads fun makeDir(path: String, user: String? = null): Boolean = try {
        envd.unary("filesystem.Filesystem", "MakeDir", mapOf("path" to path), user); true
    } catch (error: E2BException) {
        if (error.rpcCode == "already_exists" || error.statusCode == 409) false else throw error
    }

    @JvmOverloads fun remove(path: String, user: String? = null) {
        envd.unary("filesystem.Filesystem", "Remove", mapOf("path" to path), user)
    }

    @JvmOverloads fun rename(source: String, destination: String, user: String? = null): EntryInfo = entry(
        Json.obj(envd.unary("filesystem.Filesystem", "Move", mapOf("source" to source, "destination" to destination), user).get("entry")
            ?: throw ProtocolException("Missing moved file entry")),
    )

    private fun entry(json: com.google.gson.JsonObject) = EntryInfo(
        json.requiredText("name"), json.requiredText("path"), json.text("type") ?: "FILE_TYPE_UNSPECIFIED",
        json.number("size"), json.number("mode").toInt(), json.text("permissions"), json.text("owner"), json.text("group"),
        json.text("modifiedTime"), json.text("symlinkTarget"), json.get("isSymlink")?.asBoolean ?: false, json.strings("metadata"),
    )
}
