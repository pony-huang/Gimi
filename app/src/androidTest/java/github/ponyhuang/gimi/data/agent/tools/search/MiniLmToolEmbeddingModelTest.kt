package github.ponyhuang.gimi.data.agent.tools.search

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniLmToolEmbeddingModelTest {
    @Test
    fun bundledModelProducesSemanticToolEmbeddings() = runBlocking {
        val model = MiniLmToolEmbeddingModel(ApplicationProvider.getApplicationContext())

        val query = model.encode("wake me up tomorrow morning")
        val alarm = model.encode("Creates an alarm in the system clock.")
        val location = model.encode("Gets the current device location.")

        assertEquals(384, query.size)
        assertEquals(384, alarm.size)
        assertEquals(384, location.size)
        assertTrue(
            "Alarm capability should be closer to the wake-up request than location.",
            cosine(query, alarm) > cosine(query, location),
        )
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        return dot / (sqrt(leftNorm) * sqrt(rightNorm))
    }
}
