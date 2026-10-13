package github.ponyhuang.gimi.domain.sandbox

import org.junit.Assert.*
import org.junit.Test

class SandboxConfigurationTest {
    @Test fun defaultSettingsAreDisabledAndUnconfigured() {
        val settings = SandboxConfiguration()
        assertFalse(settings.enabled)
        assertFalse(settings.isConfigured)
        assertFalse(settings.copy(enabled = true).isReady)
        assertEquals(setOf(SandboxSettingField.API_KEY), validateSandboxConnection(settings.connection))
    }
    @Test fun onlyEnabledValidConnectionsAreReady() {
        val settings = SandboxConfiguration(connection = SandboxConnection(apiKey = "key"))
        assertTrue(settings.isConfigured)
        assertFalse(settings.isReady)
        assertTrue(settings.copy(enabled = true).isReady)
    }
    @Test fun rejectsUrlsPortsInvalidLabelsAndNonpositiveIdleTimeout() {
        listOf("https://e2b.app", "e2b.app/api", "e2b.app:443", "bad_domain.app", "-bad.app", "e2b..app").forEach {
            assertTrue(validateSandboxConnection(SandboxConnection(domain = it, apiKey = "key")).contains(SandboxSettingField.DOMAIN))
        }
        assertTrue(validateSandboxConnection(SandboxConnection(apiKey = "key", idleTimeoutSeconds = 0)).contains(SandboxSettingField.IDLE_TIMEOUT))
    }
    @Test fun emptyEnvironmentValuesAreAllowedButInvalidNamesAreNot() {
        assertTrue(validateSandboxConnection(SandboxConnection(apiKey = "key", envs = mapOf("EMPTY" to ""))).isEmpty())
        assertFalse(isSandboxEnvironmentName(""))
        assertFalse(isSandboxEnvironmentName("A=B"))
        assertTrue(isSandboxEnvironmentName("LANG"))
    }
    @Test fun connectionDebugStringDoesNotExposeCredentials() {
        val settings = SandboxConnection(apiKey = "secret-key", envs = mapOf("TOKEN" to "secret-value"))
        assertFalse(settings.toString().contains("secret"))
    }
}
