package github.ponyhuang.gimi.data.appfunction

import androidx.appfunctions.metadata.*
import org.junit.Assert.*
import org.junit.Test

/** 验证平台声明映射，尤其不能使用 SDK 硬编码的静态 isEnabled。 */
class AppFunctionMetadataTest {
    @Test fun mapsDescriptionsParametersAndRuntimeState() {
        val metadata = AppFunctionMetadata(
            name = AppFunctionName("notes", "Notes#create"), schema = null,
            parameters = listOf(AppFunctionParameterMetadata("title", true, AppFunctionStringTypeMetadata(false), "Title")),
            response = AppFunctionResponseMetadata(AppFunctionArrayTypeMetadata(AppFunctionLongTypeMetadata(false), true), "Ids"),
            packageMetadata = AppFunctionPackageMetadata("notes", emptyList(), AppFunctionComponentsMetadata()),
            description = "Create a note",
        )
        val info = metadata.toInfo(true)
        assertEquals("Notes#create", info.id)
        assertEquals("Create a note", info.description)
        assertEquals("string", info.parameters.single().type)
        assertTrue(info.parameters.single().required)
        assertEquals("Title", info.parameters.single().description)
        assertEquals("array<long>?", info.returnType)
        assertEquals("Ids", info.returnDescription)
        assertEquals(true, info.isEnabled)
        assertNull(metadata.toInfo(null).isEnabled)
    }
}
