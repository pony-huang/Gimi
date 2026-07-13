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
class ModelServiceDatabaseTest {
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
    fun seedOnlyWritesAnEmptyCatalog() = runBlocking {
        seedModelCatalogIfEmpty(database)
        val seeded = database.modelServiceDao().getAll()
        assertEquals(DefaultModelServices.services.map { it.serviceId }, seeded.map { it.serviceId })

        val renamed = seeded.first().copy(serviceName = "Edited")
        database.modelServiceDao().upsert(renamed)
        seedModelCatalogIfEmpty(database)

        assertEquals("Edited", database.modelServiceDao().get(renamed.serviceId)?.serviceName)
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
