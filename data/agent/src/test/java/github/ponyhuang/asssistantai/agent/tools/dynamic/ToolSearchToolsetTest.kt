package github.ponyhuang.asssistantai.agent.tools.dynamic

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSearchToolsetTest {

    @Test
    fun onDemandStartsWithOnlyToolSearch() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )

        assertEquals(listOf(TOOL_SEARCH_NAME), toolset.getTools(context()).map(BaseTool::name))
    }

    @Test
    fun alwaysAvailableExposesEveryUnambiguousToolWithoutSearch() = runTest {
        val toolset = ToolSearchToolset(
            mode = ToolAccessMode.ALWAYS_AVAILABLE,
            sources = listOf(
                source("local", tool("set_alarm"), tool("get_location")),
                source("mcp", tool("create_issue")),
            ),
        )

        assertEquals(
            listOf("set_alarm", "get_location", "create_issue"),
            toolset.getTools(context()).map(BaseTool::name),
        )
    }

    @Test
    fun automaticModeLoadsSmallCatalogAndSearchesLargeCatalog() = runTest {
        val budget = ToolAccessBudget(maxTools = 2, maxSchemaBytes = 16 * 1024)
        val small = ToolSearchToolset(
            mode = ToolAccessMode.AUTO,
            sources = listOf(source("local", tool("one"), tool("two"))),
            budget = budget,
        )
        val large = ToolSearchToolset(
            mode = ToolAccessMode.AUTO,
            sources = listOf(source("local", tool("one"), tool("two"), tool("three"))),
            budget = budget,
        )

        assertEquals(listOf("one", "two"), small.getTools(context()).map(BaseTool::name))
        assertEquals(listOf(TOOL_SEARCH_NAME), large.getTools(context()).map(BaseTool::name))
    }

    @Test
    fun automaticModeFallsBackToSearchWhenAnySourceFails() = runTest {
        val toolset = ToolSearchToolset(
            mode = ToolAccessMode.AUTO,
            sources = listOf(
                source("local", tool("set_alarm")),
                failingSource("offline-mcp"),
            ),
        )

        assertEquals(listOf(TOOL_SEARCH_NAME), toolset.getTools(context()).map(BaseTool::name))
    }

    @Test
    fun searchRanksNamesBeforeDescriptionsAndAppliesTheBudget() = runTest {
        val toolset = ToolSearchToolset(
            mode = ToolAccessMode.ON_DEMAND,
            sources = listOf(
                source(
                    "local",
                    tool("calendar_search", "Find calendar entries"),
                    tool("create_calendar_event", "Create a calendar entry"),
                    tool("unrelated", "Search calendar backups"),
                ),
            ),
            budget = ToolAccessBudget(maxTools = 2, maxSchemaBytes = 16 * 1024),
        )

        val result = toolset.search("calendar", toolContext(context()))
        val loaded = result.loadedToolNames()

        assertEquals(listOf("calendar_search", "create_calendar_event"), loaded)
        assertEquals(1, result["omitted_match_count"])
        assertEquals(true, result["selection_changed"])
        assertFalse(result.toString().contains("parameters"))
    }

    @Test
    fun successfulSearchPersistsTheSelectionIntoSessionState() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )
        val actions = EventActions()

        toolset.search("alarm", toolContext(context(), actions))

        assertEquals(
            listOf("set_alarm"),
            actions.stateDelta[ToolSearchToolset.STATE_KEY_LOADED_TOOLS],
        )
    }

    @Test
    fun persistedStateSelectionSurvivesIntoANewInvocation() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )
        // 模拟新一轮用户请求：无任何当前 invocation 事件，只有持久化的 session state。
        val newTurnContext = context(
            state = mapOf(
                ToolSearchToolset.STATE_KEY_LOADED_TOOLS to listOf("set_alarm"),
            ),
        )

        assertEquals(
            listOf(TOOL_SEARCH_NAME, "set_alarm"),
            toolset.getTools(newTurnContext).map(BaseTool::name),
        )
    }

    @Test
    fun bestOversizedMatchCanUseTheBudgetAlone() = runTest {
        val oversized = tool(
            name = "large_tool",
            description = "large ".repeat(200),
        )
        val toolset = ToolSearchToolset(
            mode = ToolAccessMode.ON_DEMAND,
            sources = listOf(source("local", oversized, tool("large_backup"))),
            budget = ToolAccessBudget(maxTools = 8, maxSchemaBytes = 100),
        )

        val result = toolset.search("large_tool", toolContext(context()))

        assertEquals(listOf("large_tool"), result.loadedToolNames())
        assertEquals(1, result["omitted_match_count"])
    }

    @Test
    fun latestSuccessfulSearchEventReplacesThePreviousSelection() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )
        val context = context(
            searchEvent("set_alarm"),
            searchEvent(selectionChanged = false),
            searchEvent("get_location"),
        )

        assertEquals(
            listOf(TOOL_SEARCH_NAME, "get_location"),
            toolset.getTools(context).map(BaseTool::name),
        )
    }

    @Test
    fun unsuccessfulSearchDoesNotClearThePreviousSelection() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )
        val context = context(
            searchEvent("set_alarm"),
            searchEvent(selectionChanged = false),
        )

        assertEquals(
            listOf(TOOL_SEARCH_NAME, "set_alarm"),
            toolset.getTools(context).map(BaseTool::name),
        )
    }

    @Test
    fun persistedSearchSelectionSurvivesToolsetRecreationForConfirmationResume() = runTest {
        val persistedContext = context(searchEvent("set_alarm"))
        val resumedToolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )

        assertEquals(
            listOf(TOOL_SEARCH_NAME, "set_alarm"),
            resumedToolset.getTools(persistedContext).map(BaseTool::name),
        )
    }

    @Test
    fun aNewInvocationDoesNotInheritThePreviousSelectionWithoutPersistedState() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )

        assertEquals(
            listOf(TOOL_SEARCH_NAME, "set_alarm"),
            toolset.getTools(context(searchEvent("set_alarm"))).map(BaseTool::name),
        )
        // 没有持久化 state 时（兼容旧会话），新 invocation 不继承上一轮的选择。
        assertEquals(
            listOf(TOOL_SEARCH_NAME),
            toolset.getTools(context()).map(BaseTool::name),
        )
    }

    @Test
    fun parallelConversationContextsRestoreIndependentSelections() = runTest {
        val toolset = toolset(
            mode = ToolAccessMode.ON_DEMAND,
            tools = listOf(tool("set_alarm"), tool("get_location")),
        )

        assertEquals(
            listOf(TOOL_SEARCH_NAME, "set_alarm"),
            toolset.getTools(context(searchEvent("set_alarm"))).map(BaseTool::name),
        )
        assertEquals(
            listOf(TOOL_SEARCH_NAME, "get_location"),
            toolset.getTools(context(searchEvent("get_location"))).map(BaseTool::name),
        )
    }

    @Test
    fun duplicateCallableNamesAreNeverLoaded() = runTest {
        val toolset = ToolSearchToolset(
            mode = ToolAccessMode.ON_DEMAND,
            sources = listOf(
                source("local", tool("duplicate"), tool("unique")),
                source("mcp", tool("duplicate")),
            ),
        )

        val result = toolset.search("duplicate", toolContext(context()))

        assertTrue(result.loadedToolNames().isEmpty())
        assertTrue((result["ambiguous_tools"] as List<*>).isNotEmpty())
    }

    @Test
    fun sourceFailuresAreSanitizedInSearchResults() = runTest {
        val toolset = ToolSearchToolset(
            mode = ToolAccessMode.ON_DEMAND,
            sources = listOf(failingSource("private-server")),
        )

        val result = toolset.search("files", toolContext(context()))
        val errors = result["source_errors"] as List<*>

        assertEquals(1, errors.size)
        assertFalse(errors.single().toString().contains("secret.example.com"))
        assertFalse(errors.single().toString().contains("token"))
    }

    private fun toolset(
        mode: ToolAccessMode,
        tools: List<BaseTool>,
    ): ToolSearchToolset = ToolSearchToolset(
        mode = mode,
        sources = listOf(source("local", *tools.toTypedArray())),
    )

    private fun source(
        id: String,
        vararg tools: BaseTool,
    ): DynamicToolCandidateSource = object : DynamicToolCandidateSource {
        override val id: String = id
        override val displayName: String = id

        override suspend fun loadTools(
            readonlyContext: ReadonlyContext?,
        ): List<BaseTool> = tools.toList()
    }

    private fun failingSource(id: String): DynamicToolCandidateSource =
        object : DynamicToolCandidateSource {
            override val id: String = id
            override val displayName: String = id

            override suspend fun loadTools(
                readonlyContext: ReadonlyContext?,
            ): List<BaseTool> = error(
                "https://secret.example.com?token=do-not-leak",
            )
        }

    private fun tool(
        name: String,
        description: String = name,
    ): BaseTool = DeclarationTool(name, description)

    private fun context(
        vararg events: Event,
        state: Map<String, Any> = emptyMap(),
    ): ReadonlyContext = FakeReadonlyContext(events.toList(), state)

    private fun toolContext(
        readonlyContext: ReadonlyContext,
        actions: EventActions = EventActions(),
    ): ToolContext {
        val toolContext = mockk<ToolContext>()
        every { toolContext.context } returns readonlyContext
        every { toolContext.actions } returns actions
        return toolContext
    }

    private fun searchEvent(
        vararg names: String,
        selectionChanged: Boolean = true,
    ): Event = Event(
        invocationId = INVOCATION_ID,
        author = "Assistant",
        content = Content(
            role = Role.USER,
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = TOOL_SEARCH_NAME,
                        response = mapOf(
                            "loaded_tools" to names.map { name ->
                                mapOf(
                                    "name" to name,
                                    "description" to name,
                                    "source" to "test",
                                )
                            },
                            "selection_changed" to selectionChanged,
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun Map<String, Any>.loadedToolNames(): List<String> =
        (getValue("loaded_tools") as List<*>).map { item ->
            (item as Map<*, *>)["name"] as String
        }

    private class DeclarationTool(
        name: String,
        description: String,
    ) : BaseTool(name, description) {
        override fun declaration(): FunctionDeclaration = FunctionDeclaration(
            name = name,
            description = description,
            parameters = Schema(
                type = Type.OBJECT,
                properties = emptyMap(),
            ),
        )

        override suspend fun run(
            context: ToolContext,
            args: Map<String, Any>,
        ): Any = emptyMap<String, Any>()
    }

    private class FakeReadonlyContext(
        private val events: List<Event>,
        override val state: Map<String, Any> = emptyMap(),
    ) : ReadonlyContext {
        override val session: Session = Session(
            SessionKey("app", "user", "session"),
            events = events.toMutableList(),
        )
        override val runConfig: RunConfig? = null
        override val invocationId: String = INVOCATION_ID
        override val agentName: String = "Assistant"
        override val userId: String = "user"
        override val userContent: Content? = null
        override val branch: String? = null
        override val artifactService: ArtifactService? = null
        override val memoryService: MemoryService? = null

        override suspend fun getEvents(
            currentInvocation: Boolean,
            currentBranch: Boolean,
        ): List<Event> = events
    }

    private companion object {
        const val INVOCATION_ID = "invocation"
    }
}
