package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSettings
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiToolContributionTest {
    @Test
    fun disablingA2uiRemovesTheToolAndChangesTheBuildRevision() {
        val state = MutableStateFlow(
            RecommendationState(settings = RecommendationSettings(a2uiEnabled = false)),
        )
        val settings = object : RecommendationRepository {
            override val state: StateFlow<RecommendationState> = state

            override fun setEnabled(enabled: Boolean) = Unit
            override fun setIntervalHours(intervalHours: Int) = Unit
            override fun requestRefresh() = Unit

            override fun setA2uiEnabled(enabled: Boolean) {
                state.update { it.copy(settings = it.settings.copy(a2uiEnabled = enabled)) }
            }
        }
        val contribution = A2uiToolContribution(
            models = mockk(relaxed = true),
            protocol = mockk(relaxed = true),
            settings = settings,
        )
        val spec = AgentBuildSpec(pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()))

        assertTrue(contribution.tools(spec).isEmpty())
        val disabledRevision = contribution.revision()

        settings.setA2uiEnabled(true)

        assertEquals(1, contribution.tools(spec).size)
        assertNotEquals(disabledRevision, contribution.revision())
    }

}
