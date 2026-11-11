package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleChatPromptPreferencesTest {
    private val values = mutableMapOf<String, String>()

    private val context: Context = run {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences> {
            every { getString(any(), any<String>()) } answers { values[firstArg()] ?: secondArg() }
            every { getString(any(), isNull()) } answers { values[firstArg()] }
            every { edit() } returns editor
        }
        every { editor.putString(any(), any<String>()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.putString(any(), isNull()) } answers {
            values.remove(firstArg())
            editor
        }
        mockk { every { getSharedPreferences(any(), any()) } returns preferences }
    }

    private fun newRepository() = SimpleChatPromptPreferences(context)

    @Test
    fun upsertSelectAndRoundTripAcrossInstances() {
        val first = newRepository()
        first.upsertPreset(SimpleChatPromptPreset("p1", "简洁速答", "直接给结论。"))
        first.upsertPreset(SimpleChatPromptPreset("p2", "口语陪练", "全程用英语对话。"))
        first.selectPreset("p2")

        val second = newRepository()
        assertEquals(listOf(
            SimpleChatPromptPreset("p1", "简洁速答", "直接给结论。"),
            SimpleChatPromptPreset("p2", "口语陪练", "全程用英语对话。"),
        ), second.presets.value)
        assertEquals("p2", second.selectedPresetId.value)
    }

    @Test
    fun upsertOverwritesExistingIdInPlace() {
        val repository = newRepository()
        repository.upsertPreset(SimpleChatPromptPreset("p1", "旧名", "旧内容。"))
        repository.upsertPreset(SimpleChatPromptPreset("p2", "第二个", "内容。"))
        repository.upsertPreset(SimpleChatPromptPreset("p1", "新名", "新内容。"))

        assertEquals(listOf("p1", "p2"), repository.presets.value.map { it.id })
        assertEquals("新名", repository.presets.value.first { it.id == "p1" }.name)
    }

    @Test
    fun deletingSelectedPresetFallsBackToNoneWhileOtherSelectionKept() {
        val repository = newRepository()
        repository.upsertPreset(SimpleChatPromptPreset("p1", "一", "内容一。"))
        repository.upsertPreset(SimpleChatPromptPreset("p2", "二", "内容二。"))

        repository.selectPreset("p1")
        repository.deletePreset("p1")
        assertNull(repository.selectedPresetId.value)

        repository.selectPreset("p2")
        repository.deletePreset("p1")
        assertEquals("p2", repository.selectedPresetId.value)
    }

    @Test
    fun unknownSelectionIsNormalizedToNone() {
        val repository = newRepository()
        repository.selectPreset("missing")

        assertNull(repository.selectedPresetId.value)
        assertNull(newRepository().selectedPresetId.value)
    }

    @Test
    fun corruptedOrIncompletePersistedEntriesAreDropped() {
        values["presets_json"] = "[{\"id\":\"p1\",\"name\":\"有效\",\"content\":\"内容。\"}," +
            "{\"id\":\"p2\",\"content\":\"缺名称。\"},{\"name\":\"缺id\",\"content\":\"内容。\"}]"
        values["selected_preset_id"] = "p2"
        // 损坏数据不影响其他语音偏好文件：只丢弃非法条目。
        values["unrelated"] = "kept"

        val repository = newRepository()
        assertEquals(listOf(SimpleChatPromptPreset("p1", "有效", "内容。")), repository.presets.value)
        assertNull(repository.selectedPresetId.value)
    }

    @Test
    fun corruptJsonStartsEmpty() {
        values["presets_json"] = "{not json"
        val repository = newRepository()

        assertEquals(emptyList<SimpleChatPromptPreset>(), repository.presets.value)
        assertNull(repository.selectedPresetId.value)
    }

    @Test
    fun builtInPresetsAreExposedAndCanBeSelected() {
        val repository = newRepository()
        val builtIn = repository.builtInPresets.first()

        repository.selectPreset(builtIn.id)

        assertEquals(builtIn.id, repository.selectedPresetId.value)
        assertEquals(builtIn.id, newRepository().selectedPresetId.value)
    }

    @Test
    fun deletingABuiltInPresetIsIgnored() {
        val repository = newRepository()
        val builtIn = repository.builtInPresets.first()
        repository.selectPreset(builtIn.id)

        repository.deletePreset(builtIn.id)

        assertEquals(builtIn.id, repository.selectedPresetId.value)
        assertEquals(emptyList<SimpleChatPromptPreset>(), repository.presets.value)
    }

    @Test
    fun presetDescriptionSurvivesRoundTrip() {
        val repository = newRepository()
        repository.upsertPreset(
            SimpleChatPromptPreset("p1", "简洁速答", "直接给结论。", "一句话说完"),
        )

        assertEquals("一句话说完", newRepository().presets.value.first { it.id == "p1" }.description)
    }
}
