package github.ponyhuang.gimi.data.conversation.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRolePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ChatbotRolePreferencesTest {
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

    private fun newRepository() = ChatbotRolePreferences(context)

    @Test
    fun upsertSelectAndRoundTripAcrossInstances() {
        val first = newRepository()
        first.upsertPreset(ChatbotRolePreset("p1", "简洁速答", "直接给结论。"))
        first.upsertPreset(ChatbotRolePreset("p2", "口语陪练", "全程用英语对话。"))
        first.selectPreset("conversation", "p2")

        val second = newRepository()
        assertEquals(listOf(
            ChatbotRolePreset("p1", "简洁速答", "直接给结论。"),
            ChatbotRolePreset("p2", "口语陪练", "全程用英语对话。"),
        ), second.presets.value)
        assertEquals("p2", second.selectedPresetIds.value["conversation"])
    }

    @Test
    fun upsertOverwritesExistingIdInPlace() {
        val repository = newRepository()
        repository.upsertPreset(ChatbotRolePreset("p1", "旧名", "旧内容。"))
        repository.upsertPreset(ChatbotRolePreset("p2", "第二个", "内容。"))
        repository.upsertPreset(ChatbotRolePreset("p1", "新名", "新内容。"))

        assertEquals(listOf("p1", "p2"), repository.presets.value.map { it.id })
        assertEquals("新名", repository.presets.value.first { it.id == "p1" }.name)
    }

    @Test
    fun deletingSelectedPresetFallsBackToNoneWhileOtherSelectionKept() {
        val repository = newRepository()
        repository.upsertPreset(ChatbotRolePreset("p1", "一", "内容一。"))
        repository.upsertPreset(ChatbotRolePreset("p2", "二", "内容二。"))

        repository.selectPreset("conversation", "p1")
        repository.deletePreset("p1")
        assertNull(repository.selectedPresetIds.value["conversation"])

        repository.selectPreset("conversation", "p2")
        repository.deletePreset("p1")
        assertEquals("p2", repository.selectedPresetIds.value["conversation"])
    }

    @Test
    fun unknownSelectionIsNormalizedToNone() {
        val repository = newRepository()
        repository.selectPreset("conversation", "missing")

        assertNull(repository.selectedPresetIds.value["conversation"])
        assertNull(newRepository().selectedPresetIds.value["conversation"])
    }

    @Test
    fun corruptedOrIncompletePersistedEntriesAreDropped() {
        values["presets_json"] = "[{\"id\":\"p1\",\"name\":\"有效\",\"content\":\"内容。\"}," +
            "{\"id\":\"p2\",\"content\":\"缺名称。\"},{\"name\":\"缺id\",\"content\":\"内容。\"}]"
        values["selected_preset_id"] = "p2"
        // 损坏数据不影响其他语音偏好文件：只丢弃非法条目。
        values["unrelated"] = "kept"

        val repository = newRepository()
        assertEquals(listOf(ChatbotRolePreset("p1", "有效", "内容。")), repository.presets.value)
        assertNull(repository.selectedPresetIds.value["conversation"])
    }

    @Test
    fun corruptJsonStartsEmpty() {
        values["presets_json"] = "{not json"
        val repository = newRepository()

        assertEquals(emptyList<ChatbotRolePreset>(), repository.presets.value)
        assertNull(repository.selectedPresetIds.value["conversation"])
    }

    @Test
    fun builtInPresetsAreExposedAndCanBeSelected() {
        val repository = newRepository()
        val builtIn = repository.builtInPresets.first()

        repository.selectPreset("conversation", builtIn.id)

        assertEquals(builtIn.id, repository.selectedPresetIds.value["conversation"])
        assertEquals(builtIn.id, newRepository().selectedPresetIds.value["conversation"])
    }

    @Test
    fun upsertingABuiltInPresetIdIsRejected() {
        val repository = newRepository()
        val builtIn = repository.builtInPresets.first()
        try {
            repository.upsertPreset(builtIn.copy(name = "篡改"))
            fail("Expected built-in preset ids to be read-only")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(emptyList<ChatbotRolePreset>(), repository.presets.value)
    }

    @Test
    fun deletingABuiltInPresetIsIgnored() {
        val repository = newRepository()
        val builtIn = repository.builtInPresets.first()
        repository.selectPreset("conversation", builtIn.id)

        repository.deletePreset(builtIn.id)

        assertEquals(builtIn.id, repository.selectedPresetIds.value["conversation"])
        assertEquals(emptyList<ChatbotRolePreset>(), repository.presets.value)
    }

    @Test
    fun presetDescriptionSurvivesRoundTrip() {
        val repository = newRepository()
        repository.upsertPreset(
            ChatbotRolePreset("p1", "简洁速答", "直接给结论。", "一句话说完"),
        )

        assertEquals("一句话说完", newRepository().presets.value.first { it.id == "p1" }.description)
    }
    @Test
    fun selectionsAreIsolatedAndNewConversationsUseDefaultRole() {
        val repository = newRepository()
        val roleId = repository.builtInPresets.first().id
        repository.selectPreset("first", roleId)
        assertNull(repository.selectedPresetIds.value["second"])
        assertEquals(roleId, newRepository().selectedPresetIds.value["first"])
        repository.selectPreset("second", roleId)
        repository.selectPreset("first", null)
        assertNull(repository.selectedPresetIds.value["first"])
        assertEquals(roleId, repository.selectedPresetIds.value["second"])
    }

    @Test
    fun deletingCustomRoleClearsAllAffectedConversationSelections() {
        val repository = newRepository()
        repository.upsertPreset(ChatbotRolePreset("custom", "角色", "自定义角色正文"))
        repository.selectPreset("first", "custom")
        repository.selectPreset("second", "custom")
        repository.deletePreset("custom")
        assertEquals(emptyMap<String, String>(), repository.selectedPresetIds.value)
        assertEquals(emptyMap<String, String>(), newRepository().selectedPresetIds.value)
    }

}
