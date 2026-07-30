package github.ponyhuang.gimi.agent.tools.appfunctions

import com.google.adk.kt.types.Type
import com.google.adk.kt.tools.FunctionTool
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionParameter
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionSelection
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionValueType
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFunctionToolsetTest {
    @Test
    fun `tool names are stable bounded and distinguish colliding readable names`() {
        val first = AppFunctionToolName.from(
            AppFunctionKey("com.example.notes", "create-note"),
        )
        val repeated = AppFunctionToolName.from(
            AppFunctionKey("com.example.notes", "create-note"),
        )
        val readableCollision = AppFunctionToolName.from(
            AppFunctionKey("com.example.notes", "create_note"),
        )

        assertEquals(first, repeated)
        assertNotEquals(first, readableCollision)
        assertTrue(first.length <= 64)
        assertTrue(first.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")))
    }

    @Test
    fun `toolset only exposes selected supported and provider enabled functions`() = runTest {
        val enabled = descriptor("enabled")
        val unsupported = descriptor("unsupported", supported = false)
        val providerDisabled = descriptor("provider_disabled", providerEnabled = false)
        val repository = FakeRepository(
            AppFunctionCatalogState(
                support = AppFunctionsSupport.AVAILABLE,
                selection = AppFunctionSelection(
                    featureEnabled = true,
                    enabledPackageNames = setOf(enabled.key.packageName),
                    enabledFunctionKeys = setOf(
                        enabled.key,
                        unsupported.key,
                        providerDisabled.key,
                    ),
                ),
                functions = listOf(enabled, unsupported, providerDisabled),
            ),
        )

        val tools = AppFunctionToolset(repository).getTools(null)

        assertEquals(listOf(AppFunctionToolName.from(enabled.key)), tools.map { it.name })
    }

    @Test
    fun `tool declaration converts nested json compatible parameters`() = runTest {
        val descriptor = descriptor("create").copy(
            parameters = listOf(
                AppFunctionParameter(
                    name = "title",
                    description = "Note title",
                    required = true,
                    type = AppFunctionValueType.StringType(
                        nullable = false,
                        enumValues = listOf("work", "personal"),
                    ),
                ),
                AppFunctionParameter(
                    name = "metadata",
                    description = "Nested metadata",
                    required = false,
                    type = AppFunctionValueType.ObjectType(
                        properties = mapOf(
                            "tags" to AppFunctionValueType.ArrayType(
                                itemType = AppFunctionValueType.StringType(nullable = false),
                                nullable = false,
                            ),
                        ),
                        required = listOf("tags"),
                        nullable = true,
                    ),
                ),
            ),
        )
        val repository = FakeRepository(
            AppFunctionCatalogState(
                support = AppFunctionsSupport.AVAILABLE,
                selection = AppFunctionSelection(
                    featureEnabled = true,
                    enabledPackageNames = setOf(descriptor.key.packageName),
                    enabledFunctionKeys = setOf(descriptor.key),
                ),
                functions = listOf(descriptor),
            ),
        )

        val declaration = AppFunctionToolset(repository).getTools(null).single().declaration()!!

        assertEquals(Type.OBJECT, declaration.parameters?.type)
        assertEquals(listOf("title"), declaration.parameters?.required)
        assertEquals(listOf("work", "personal"), declaration.parameters?.properties?.get("title")?.enum)
        assertEquals(
            Type.ARRAY,
            declaration.parameters?.properties?.get("metadata")?.properties?.get("tags")?.type,
        )
    }

    @Test
    fun `every external app function invocation requires confirmation`() = runTest {
        val descriptor = descriptor("create")
        val repository = FakeRepository(
            AppFunctionCatalogState(
                support = AppFunctionsSupport.AVAILABLE,
                selection = AppFunctionSelection(
                    featureEnabled = true,
                    enabledPackageNames = setOf(descriptor.key.packageName),
                    enabledFunctionKeys = setOf(descriptor.key),
                ),
                functions = listOf(descriptor),
            ),
        )
        val tool = AppFunctionToolset(repository).getTools(null).single()
        val getter = FunctionTool::class.java
            .getDeclaredMethod("getRequiresConfirmation")
            .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val predicate = getter.invoke(tool) as (Map<String, Any>) -> Boolean

        assertTrue(predicate(emptyMap()))
    }

    private fun descriptor(
        functionId: String,
        supported: Boolean = true,
        providerEnabled: Boolean = true,
    ) = AppFunctionDescriptor(
        key = AppFunctionKey("com.example", functionId),
        appLabel = "Example",
        appDescription = null,
        description = "Execute $functionId",
        parameters = emptyList(),
        providerEnabled = providerEnabled,
        supported = supported,
        unsupportedReason = if (supported) null else "Unsupported type",
    )
}

private class FakeRepository(
    initialState: AppFunctionCatalogState,
) : AppFunctionRepository {
    override val state = MutableStateFlow(initialState)
    override val revision = MutableStateFlow(0L)

    override suspend fun setFeatureEnabled(enabled: Boolean): Boolean = true

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) = Unit

    override suspend fun setFunctionEnabled(key: AppFunctionKey, enabled: Boolean) = Unit

    override suspend fun execute(
        key: AppFunctionKey,
        arguments: Map<String, Any>,
    ): AppFunctionExecutionResult = AppFunctionExecutionResult.Success(emptyMap<String, Any>())
}
