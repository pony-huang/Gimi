package github.ponyhuang.gimi.agent.tools.search

import io.objectbox.BoxStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ObjectBoxToolVectorSearchTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: BoxStore
    private lateinit var embeddingModel: FakeEmbeddingModel
    private lateinit var vectorSearch: ObjectBoxToolVectorSearch

    @Before
    fun setUp() {
        store = MyObjectBox.builder()
            .directory(temporaryFolder.newFolder("objectbox"))
            .build()
        embeddingModel = FakeEmbeddingModel()
        vectorSearch = ObjectBoxToolVectorSearch(
            box = store.boxFor(ToolVectorEntity::class.java),
            embeddingModel = embeddingModel,
        )
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun searchPersistsEveryDocumentAndReturnsNearestFirst() = runTest {
        val documents = listOf(
            ToolVectorDocument("local:set_alarm", "alarm clock"),
            ToolVectorDocument("local:get_location", "device location"),
        )

        val matches = vectorSearch.search(
            scopeKey = "test",
            documents = documents,
            query = "alarm request",
            maxResultCount = documents.size,
        )

        assertEquals(
            listOf("local:set_alarm", "local:get_location"),
            matches.map(ToolVectorMatch::key),
        )
        assertEquals(2, store.boxFor(ToolVectorEntity::class.java).count())
    }

    @Test
    fun unchangedDocumentsReuseStoredEmbeddingsAndRemovedDocumentsAreDeleted() = runTest {
        val initial = listOf(
            ToolVectorDocument("local:set_alarm", "alarm clock"),
            ToolVectorDocument("local:get_location", "device location"),
        )
        vectorSearch.search("test", initial, "alarm request", initial.size)
        vectorSearch.search("test", initial, "alarm request", initial.size)

        assertEquals(
            "two documents are embedded once; each query is embedded independently",
            4,
            embeddingModel.encodedTexts.size,
        )

        vectorSearch.search(
            scopeKey = "test",
            documents = initial.drop(1),
            query = "location request",
            maxResultCount = 1,
        )

        assertEquals(1, store.boxFor(ToolVectorEntity::class.java).count())
    }

    private class FakeEmbeddingModel : ToolEmbeddingModel {
        override val dimensions: Int = ToolEmbeddingDimensions.MINI_LM.toInt()
        override val version: String = "fake-v1"
        val encodedTexts = mutableListOf<String>()

        override suspend fun encode(text: String): FloatArray {
            encodedTexts += text
            return FloatArray(dimensions).apply {
                when {
                    "alarm" in text -> this[0] = 1f
                    "location" in text -> this[1] = 1f
                }
            }
        }
    }
}
