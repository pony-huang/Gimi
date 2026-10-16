package github.ponyhuang.gimi.domain.sandbox

import org.junit.Assert.*
import org.junit.Test

class SandboxConfigurationTest {
    @Test fun acceptsCompleteHttpAndHttpsEndpoints() {
        listOf("http://192.168.1.10:3000", "http://localhost:3000", "https://api.e2b.app", "https://example.com/E2B/api").forEach {
            assertTrue("Expected endpoint to be accepted: $it", validateSandboxConnection(SandboxConnection(apiUrl = it, apiKey = "key")).isEmpty())
        }
    }
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
    @Test fun rejectsInvalidEndpointsAndNonpositiveIdleTimeout() {
        listOf("e2b.app", "e2b.app:443", "ftp://example.com", "http://", "http://bad_domain.app", "https://e2b..app", "http://host:0", "http://host:65536", "https://user:secret@host", "https://host?key=secret", "https://host#fragment", "https://host?", "https://host#").forEach {
            assertTrue(validateSandboxConnection(SandboxConnection(apiUrl = it, apiKey = "key")).contains(SandboxSettingField.API_URL))
        }
        assertTrue(validateSandboxConnection(SandboxConnection(apiKey = "key", idleTimeoutSeconds = 0)).contains(SandboxSettingField.IDLE_TIMEOUT))
    }
    @Test fun proxyIsOptionalButMustBeACompleteEndpointWhenPresent() {
        assertTrue(validateSandboxConnection(SandboxConnection(apiKey = "key", sandboxUrl = "http://[::1]:3002/Proxy")).isEmpty())
        assertTrue(validateSandboxConnection(SandboxConnection(apiKey = "key", sandboxUrl = "host:3002")).contains(SandboxSettingField.SANDBOX_URL))
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
