package github.ponyhuang.gimi.feature.chat.realtime

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRolePreset
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRoleRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 预设管理页的行为：新建落库必须带稳定 id，官方预设只读、查看不进入编辑流程。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimpleChatPromptViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun creatingAPresetStoresItWithAGeneratedId() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())

        viewModel.onAction(SimpleChatPromptAction.StartCreate)
        viewModel.onAction(SimpleChatPromptAction.Save("简洁速答", "直接给结论。"))

        val stored = repository.presets.value.single()
        assertTrue("新建预设必须带非空 id", stored.id.isNotBlank())
        assertEquals("简洁速答", stored.name)
        assertEquals("直接给结论。", stored.content)
    }

    @Test
    fun blankInputIsNotPersisted() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())

        viewModel.onAction(SimpleChatPromptAction.StartCreate)
        viewModel.onAction(SimpleChatPromptAction.Save("  ", "内容"))

        assertTrue(repository.presets.value.isEmpty())
    }

    @Test
    fun editingAPresetKeepsItsId() = runTest {
        val repository = FakePromptRepository()
        repository.upsertPreset(ChatbotRolePreset("p1", "旧名", "旧内容。"))
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())

        viewModel.onAction(SimpleChatPromptAction.StartEdit("p1"))
        viewModel.onAction(SimpleChatPromptAction.Save("新名", "新内容。"))

        val stored = repository.presets.value.single()
        assertEquals("p1", stored.id)
        assertEquals("新名", stored.name)
    }

    @Test
    fun builtInPresetsAreListedAndViewedWithoutEnteringTheEditor() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())

        viewModel.uiState.test {
            assertEquals(listOf("builtin.demo"), awaitItem().builtInPresets.map { it.id })

            viewModel.onAction(SimpleChatPromptAction.ViewPrompt("builtin.demo"))

            val state = awaitItem()
            assertEquals("builtin.demo", state.viewing?.id)
            assertNull(state.editing)
        }
    }


    @Test
    fun selectingRoleKeepsConversationSelectionsIndependent() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())
        viewModel.bind("first")
        viewModel.onAction(SimpleChatPromptAction.Select("builtin.demo"))
        viewModel.bind("second")
        viewModel.onAction(SimpleChatPromptAction.Select(null))
        assertEquals("builtin.demo", repository.selectedPresetIds.value["first"])
        assertNull(repository.selectedPresetIds.value["second"])
    }

    @Test
    fun unboundManagementDoesNotChangeConversationRoles() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())
        viewModel.onAction(SimpleChatPromptAction.Select("builtin.demo"))
        assertTrue(repository.selectedPresetIds.value.isEmpty())
    }

    @Test
    fun copyingBuiltInRoleCreatesEditableIndependentPreset() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController())
        viewModel.onAction(SimpleChatPromptAction.ViewPrompt("builtin.demo"))
        viewModel.onAction(SimpleChatPromptAction.CopyViewedPreset)
        viewModel.onAction(SimpleChatPromptAction.Save("我的角色", "自定义正文"))
        assertEquals("我的角色", repository.presets.value.single().name)
        assertTrue(repository.presets.value.single().id != "builtin.demo")
        assertEquals("官方人设", repository.builtInPresets.single().name)
    }

    @Test
    fun roleRemainsLockedFromConnectionThroughCallCleanup() = runTest {
        val repository = FakePromptRepository()
        val call = MutableStateFlow(RealtimeVoiceCallState(active = true))
        val viewModel = SimpleChatPromptViewModel(repository, voiceCallController(call))
        viewModel.bind("conversation")

        viewModel.onAction(SimpleChatPromptAction.Select("builtin.demo"))
        assertNull(repository.selectedPresetIds.value["conversation"])
        call.value = RealtimeVoiceCallState(ending = true)
        viewModel.onAction(SimpleChatPromptAction.Select("builtin.demo"))
        assertNull(repository.selectedPresetIds.value["conversation"])
        call.value = RealtimeVoiceCallState()
        viewModel.onAction(SimpleChatPromptAction.Select("builtin.demo"))
        assertEquals("builtin.demo", repository.selectedPresetIds.value["conversation"])
    }

    private fun voiceCallController(call: MutableStateFlow<RealtimeVoiceCallState> = MutableStateFlow(RealtimeVoiceCallState())) =
        mockk<RealtimeVoiceCallController> { every { state } returns call }

    /** 内存假仓库：只保留行为，不落盘。 */
    private class FakePromptRepository(
        override val builtInPresets: List<ChatbotRolePreset> = listOf(
            ChatbotRolePreset("builtin.demo", "官方人设", "# Role and Objective\n正文", "官方说明"),
        ),
    ) : ChatbotRoleRepository {
        override val presets = MutableStateFlow(emptyList<ChatbotRolePreset>())
        override val selectedPresetIds = MutableStateFlow<Map<String, String>>(emptyMap())

        override fun upsertPreset(preset: ChatbotRolePreset) {
            presets.value = presets.value.filterNot { it.id == preset.id } + preset
        }

        override fun deletePreset(presetId: String) {
            presets.value = presets.value.filterNot { it.id == presetId }
        }

        override fun selectPreset(conversationId: String, presetId: String?) {
            selectedPresetIds.value = if (presetId == null) selectedPresetIds.value - conversationId else selectedPresetIds.value + (conversationId to presetId)
        }
    }
}
