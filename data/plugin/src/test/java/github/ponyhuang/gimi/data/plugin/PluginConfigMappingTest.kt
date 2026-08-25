package github.ponyhuang.gimi.data.plugin

import github.ponyhuang.gimi.domain.plugin.model.PluginActionDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import github.ponyhuang.gimi.pluginapi.PluginConfigField
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginConfigMappingTest {

    @Test
    fun textFieldMapsKindSecretAndDefault() {
        val field = PluginConfigField.Text("token", "API Token", defaultValue = "abc", secret = true)

        val descriptor = field.toDescriptor()

        assertEquals(PluginConfigFieldDescriptor.Kind.TEXT, descriptor.kind)
        assertEquals("token", descriptor.key)
        assertEquals("API Token", descriptor.label)
        assertEquals(true, descriptor.secret)
        assertEquals("abc", descriptor.defaultValue)
    }

    @Test
    fun toggleFieldMapsBooleanDefaultToString() {
        val field = PluginConfigField.Toggle("enabled", "启用", defaultValue = true)

        val descriptor = field.toDescriptor()

        assertEquals(PluginConfigFieldDescriptor.Kind.TOGGLE, descriptor.kind)
        assertEquals("true", descriptor.defaultValue)
        assertEquals(false, descriptor.secret)
    }

    @Test
    fun selectFieldMapsOptionsAndDefault() {
        val field = PluginConfigField.Select(
            key = "model",
            label = "模型",
            options = listOf("fast", "thinking"),
            defaultValue = "thinking",
        )

        val descriptor = field.toDescriptor()

        assertEquals(PluginConfigFieldDescriptor.Kind.SELECT, descriptor.kind)
        assertEquals(listOf("fast", "thinking"), descriptor.options)
        assertEquals("thinking", descriptor.defaultValue)
    }

    @Test
    fun actionMapsIdAndLabel() {
        val descriptor = PluginConfigAction(id = "login", label = "授权登录").toActionDescriptor()

        assertEquals(PluginActionDescriptor(id = "login", label = "授权登录"), descriptor)
    }
}
