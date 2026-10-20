package github.ponyhuang.gimi.data.a2ui

import androidx.a2ui.model.processor.A2uiJsonReader
import androidx.a2ui.model.processor.A2uiJsonToken
import com.google.gson.stream.JsonReader
import java.io.StringReader

/** 官方协议解析器的 JVM 可测试 JSON reader，不依赖 Android JsonReader。 */
internal class ProtocolJsonReader(raw: String) : A2uiJsonReader {
    private val reader = JsonReader(StringReader(raw))
    override fun peek() = A2uiJsonToken.valueOf(reader.peek().name)
    override fun beginObject() = reader.beginObject()
    override fun endObject() = reader.endObject()
    override fun beginArray() = reader.beginArray()
    override fun endArray() = reader.endArray()
    override fun hasNext() = reader.hasNext()
    override fun nextName(): String = reader.nextName()
    override fun nextString(): String = reader.nextString()
    override fun nextBoolean() = reader.nextBoolean()
    override fun nextDouble() = reader.nextDouble()
    override fun nextInt() = reader.nextInt()
    override fun nextLong() = reader.nextLong()
    override fun nextNull() = reader.nextNull()
    override fun skipValue() = reader.skipValue()
    override fun close() = reader.close()
}
