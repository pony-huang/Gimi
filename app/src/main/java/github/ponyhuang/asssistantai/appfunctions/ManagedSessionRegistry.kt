package github.ponyhuang.asssistantai.appfunctions

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

internal interface ManagedSessionStore {
    fun read(): Map<String, Long>

    fun write(sessions: Map<String, Long>)
}

internal data class ManagedSession(
    val sessionId: String,
    val isNew: Boolean,
)

class ManagedSessionRegistry internal constructor(
    private val store: ManagedSessionStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newHandle: () -> String = { UUID.randomUUID().toString() },
) {
    @Synchronized
    internal fun resolve(requestedHandle: String): ManagedSession? {
        val now = nowMillis()
        val activeSessions = store.read()
            .filterValues { lastUsedAt -> now - lastUsedAt <= TTL_MILLIS }
            .toMutableMap()
        val isNew = requestedHandle.isBlank()
        val handle = if (isNew) newHandle() else requestedHandle
        if (!isNew && handle !in activeSessions) {
            store.write(activeSessions)
            return null
        }
        activeSessions[handle] = now
        while (activeSessions.size > MAX_SESSIONS) {
            val oldest = activeSessions.minBy { it.value }.key
            activeSessions.remove(oldest)
        }
        store.write(activeSessions)
        return ManagedSession(sessionId = handle, isNew = isNew)
    }

    @Synchronized
    internal fun revoke(handle: String) {
        store.write(store.read() - handle)
    }

    companion object {
        const val TTL_MILLIS: Long = 7L * 24L * 60L * 60L * 1_000L
        private const val MAX_SESSIONS = 50
    }
}

private class SharedPreferencesManagedSessionStore(
    context: Context,
) : ManagedSessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): Map<String, Long> = preferences
        .getStringSet(KEY_SESSIONS, emptySet())
        .orEmpty()
        .mapNotNull { encoded ->
            val separator = encoded.lastIndexOf(SEPARATOR)
            if (separator <= 0) return@mapNotNull null
            val handle = encoded.substring(0, separator)
            val timestamp = encoded.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            handle to timestamp
        }
        .toMap()

    override fun write(sessions: Map<String, Long>) {
        preferences.edit()
            .putStringSet(
                KEY_SESSIONS,
                sessions.mapTo(mutableSetOf()) { (handle, timestamp) ->
                    "$handle$SEPARATOR$timestamp"
                },
            )
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_function_sessions"
        const val KEY_SESSIONS = "managed_sessions_v1"
        const val SEPARATOR = '|'
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object ManagedSessionModule {
    @Provides
    @Singleton
    fun provideManagedSessionRegistry(
        @ApplicationContext context: Context,
    ): ManagedSessionRegistry = ManagedSessionRegistry(
        store = SharedPreferencesManagedSessionStore(context),
    )
}
