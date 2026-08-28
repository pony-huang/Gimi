package github.ponyhuang.gimi.data.recommendation

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecommendationPreferencesTest {
    @Test
    fun settingsAndSuccessfulSnapshotPersistAcrossRecreation() {
        val preferences = FakePreferences()
        val scheduler = mockk<RecommendationWorkScheduler>(relaxed = true)
        val repository = repository(preferences, scheduler)
        val snapshot = snapshot()

        repository.setIntervalHours(6)
        repository.saveSnapshot(snapshot)

        val restored = repository(preferences, scheduler).state.value
        assertEquals(6, restored.settings.intervalHours)
        assertEquals(snapshot, restored.snapshot)
    }

    @Test
    fun disablingCancelsWorkButKeepsCachedSnapshot() {
        val preferences = FakePreferences()
        val scheduler = mockk<RecommendationWorkScheduler>(relaxed = true)
        val repository = repository(preferences, scheduler)
        repository.saveSnapshot(snapshot())

        repository.setEnabled(false)

        assertFalse(repository.state.value.settings.enabled)
        assertEquals(6, repository.state.value.snapshot?.items?.size)
        verify { scheduler.cancel() }
    }

    @Test
    fun refreshRequestUsesUniqueSchedulerEntryPoint() {
        val scheduler = mockk<RecommendationWorkScheduler>(relaxed = true)
        val repository = repository(FakePreferences(), scheduler)

        repository.requestRefresh()

        verify(exactly = 1) { scheduler.enqueueImmediate() }
    }

    private fun repository(
        preferences: FakePreferences,
        scheduler: RecommendationWorkScheduler,
    ): RecommendationPreferences {
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences.delegate
        }
        return RecommendationPreferences(context, scheduler)
    }

    private fun snapshot() = RecommendationSnapshot(
        items = (1..6).map { index ->
            AgentRecommendation("id-$index", "task-$index", RecommendationCategory.GENERAL)
        },
        generatedAtEpochMillis = 123L,
    )

    private class FakePreferences {
        private val values = mutableMapOf<String, Any?>()
        private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val delegate = mockk<SharedPreferences>()

        init {
            every { delegate.getBoolean(any(), any()) } answers {
                values[firstArg()] as? Boolean ?: secondArg()
            }
            every { delegate.getInt(any(), any()) } answers {
                values[firstArg()] as? Int ?: secondArg()
            }
            every { delegate.getString(any(), any()) } answers {
                values[firstArg()] as? String ?: secondArg<String?>()
            }
            every { delegate.edit() } returns editor
            every { editor.putBoolean(any(), any()) } answers {
                values[firstArg()] = secondArg<Boolean>(); editor
            }
            every { editor.putInt(any(), any()) } answers {
                values[firstArg()] = secondArg<Int>(); editor
            }
            every { editor.putString(any(), any()) } answers {
                values[firstArg()] = secondArg<String?>(); editor
            }
            every { editor.apply() } returns Unit
        }
    }
}
