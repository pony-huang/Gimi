package github.ponyhuang.asssistantai.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LLMModelSettingDatabaseTest {
    private lateinit var database: ModelServiceDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ModelServiceDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedSkipsExistingServiceIds() = runBlocking {
        seedMissingModelCatalog(database)
        val seeded = database.modelServiceDao().getAll()
        assertEquals(LLMModelConfigs.services.map { it.serviceId }, seeded.map { it.serviceId })

        val renamed = seeded.first().copy(serviceName = "Edited")
        database.modelServiceDao().upsert(renamed)
        seedMissingModelCatalog(database)

        assertEquals("Edited", database.modelServiceDao().get(renamed.serviceId)?.serviceName)
    }

    @Test
    fun seedInsertsProvidersMissingFromExistingCatalog() = runBlocking {
        val first = LLMModelConfigs.services.first()
        database.modelServiceDao().upsert(
            defaultModelServiceEntities().first { it.serviceId == first.serviceId },
        )
        assertEquals(1, database.modelServiceDao().count())

        seedMissingModelCatalog(database)

        val ids = database.modelServiceDao().getAll().map { it.serviceId }
        assertEquals(LLMModelConfigs.services.map { it.serviceId }.toSet(), ids.toSet())
        assertEquals(LLMModelConfigs.services.size, ids.size)
    }

    @Test
    fun observableQueryEmitsAfterInsert() = runBlocking {
        val emission = async(start = CoroutineStart.UNDISPATCHED) {
            database.modelServiceDao().observeAll().first { it.isNotEmpty() }
        }

        database.modelServiceDao().upsert(defaultModelServiceEntities().first())

        assertEquals(1, emission.await().size)
    }
}
