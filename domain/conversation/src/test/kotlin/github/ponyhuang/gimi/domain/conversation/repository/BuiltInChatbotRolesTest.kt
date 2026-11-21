package github.ponyhuang.gimi.domain.conversation.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 官方预设目录的结构约束：条目完整、id 唯一、正文覆盖实时语音提示词的固定段落结构。 */
class BuiltInChatbotRolesTest {
    @Test
    fun catalogShipsUniqueReadOnlyPresets() {
        val presets = BuiltInChatbotRoles.all
        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertTrue(presets.isNotEmpty())
        assertTrue(presets.all { it.id.startsWith(BUILT_IN_ID_PREFIX) })
        assertTrue(presets.all { it.name.isNotBlank() })
        assertTrue(presets.all { it.description.isNotBlank() })
    }

    @Test
    fun everyPresetCarriesTheFullRealtimeSectionSkeleton() {
        BuiltInChatbotRoles.all.forEach { preset ->
            REQUIRED_SECTIONS.forEach { section ->
                assertTrue(
                    "${preset.name} 缺少段落 $section",
                    preset.content.contains(section),
                )
            }
        }
    }

    @Test
    fun findResolvesByIdAndReturnsNullForUnknown() {
        val first = BuiltInChatbotRoles.all.first()
        assertNotNull(BuiltInChatbotRoles.find(first.id))
        assertEquals(first, BuiltInChatbotRoles.find(first.id))
        assertNull(BuiltInChatbotRoles.find("missing"))
    }

    private companion object {
        const val BUILT_IN_ID_PREFIX = "builtin."
        val REQUIRED_SECTIONS = listOf(
            "# Role and Objective",
            "# Personality and Tone",
            "# Language",
            "# Reasoning",
            "# Message Channels",
            "# Preambles",
            "# Verbosity",
            "# Tools",
            "# Unclear Audio",
            "# Entity Capture",
            "# Long Context Behavior",
            "# Escalation",
        )
    }
}
