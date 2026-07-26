package github.ponyhuang.asssistantai.appfunctions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedSessionRegistryTest {
    @Test
    fun `unknown caller supplied handle is rejected`() {
        val registry = ManagedSessionRegistry(
            store = InMemoryManagedSessionStore(),
            nowMillis = { 1_000L },
            newHandle = { "generated" },
        )

        assertNull(registry.resolve("caller-controlled"))
    }

    @Test
    fun `blank handle creates a managed session that can be resumed`() {
        val store = InMemoryManagedSessionStore()
        val registry = ManagedSessionRegistry(store, { 1_000L }, { "generated" })

        val created = registry.resolve("")
        val resumed = registry.resolve("generated")

        assertEquals("generated", created?.sessionId)
        assertTrue(created?.isNew == true)
        assertFalse(resumed?.isNew == true)
    }

    @Test
    fun `expired handle is rejected and removed`() {
        val store = InMemoryManagedSessionStore(
            mutableMapOf("expired" to 1L),
        )
        val registry = ManagedSessionRegistry(
            store = store,
            nowMillis = { ManagedSessionRegistry.TTL_MILLIS + 2L },
            newHandle = { "generated" },
        )

        assertNull(registry.resolve("expired"))
        assertFalse("expired" in store.read())
    }

    @Test
    fun `failed new session can be revoked`() {
        val store = InMemoryManagedSessionStore()
        val registry = ManagedSessionRegistry(store, { 1_000L }, { "generated" })
        registry.resolve("")

        registry.revoke("generated")

        assertNull(registry.resolve("generated"))
    }
}

private class InMemoryManagedSessionStore(
    private var sessions: MutableMap<String, Long> = mutableMapOf(),
) : ManagedSessionStore {
    override fun read(): Map<String, Long> = sessions.toMap()

    override fun write(sessions: Map<String, Long>) {
        this.sessions = sessions.toMutableMap()
    }
}
