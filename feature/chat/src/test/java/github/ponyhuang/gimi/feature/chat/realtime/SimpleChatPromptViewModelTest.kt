package github.ponyhuang.gimi.feature.chat.realtime

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
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
        val viewModel = SimpleChatPromptViewModel(repository)

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
        val viewModel = SimpleChatPromptViewModel(repository)

        viewModel.onAction(SimpleChatPromptAction.StartCreate)
        viewModel.onAction(SimpleChatPromptAction.Save("  ", "内容"))

        assertTrue(repository.presets.value.isEmpty())
    }

    @Test
    fun editingAPresetKeepsItsId() = runTest {
        val repository = FakePromptRepository()
        repository.upsertPreset(SimpleChatPromptPreset("p1", "旧名", "旧内容。"))
        val viewModel = SimpleChatPromptViewModel(repository)

        viewModel.onAction(SimpleChatPromptAction.StartEdit("p1"))
        viewModel.onAction(SimpleChatPromptAction.Save("新名", "新内容。"))

        val stored = repository.presets.value.single()
        assertEquals("p1", stored.id)
        assertEquals("新名", stored.name)
    }

    @Test
    fun builtInPresetsAreListedAndViewedWithoutEnteringTheEditor() = runTest {
        val repository = FakePromptRepository()
        val viewModel = SimpleChatPromptViewModel(repository)

        viewModel.uiState.test {
            assertEquals(listOf("builtin.demo"), awaitItem().builtInPresets.map { it.id })

            viewModel.onAction(SimpleChatPromptAction.ViewPrompt("builtin.demo"))

            val state = awaitItem()
            assertEquals("builtin.demo", state.viewing?.id)
            assertNull(state.editing)
        }
    }

    /** 内存假仓库：只保留行为，不落盘。 */
    private class FakePromptRepository(
        override val builtInPresets: List<SimpleChatPromptPreset> = listOf(
            SimpleChatPromptPreset("builtin.demo", "官方人设", "# Role and Objective\n正文", "官方说明"),
        ),
    ) : SimpleChatPromptRepository {
        override val presets = MutableStateFlow(emptyList<SimpleChatPromptPreset>())
        override val selectedPresetId = MutableStateFlow<String?>(null)

        override fun upsertPreset(preset: SimpleChatPromptPreset) {
            presets.value = presets.value.filterNot { it.id == preset.id } + preset
        }

        override fun deletePreset(presetId: String) {
            presets.value = presets.value.filterNot { it.id == presetId }
        }

        override fun selectPreset(presetId: String?) {
            selectedPresetId.value = presetId
        }
    }
}
