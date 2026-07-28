package github.ponyhuang.asssistantai.data.modelcatalog

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
    private lateinit var database: LLMModelRoomDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LLMModelRoomDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedSkipsExistingServiceIds() = runBlocking {
        seedMissingModelCatalog(database)
        val seeded = database.lLMModelConfigDao().getAll()
        assertEquals(LLMModelConfigs.services.map { it.serviceId }, seeded.map { it.serviceId })

        val renamed = seeded.first().copy(serviceName = "Edited")
        database.lLMModelConfigDao().upsert(renamed)
        seedMissingModelCatalog(database)

        assertEquals("Edited", database.lLMModelConfigDao().get(renamed.serviceId)?.serviceName)
    }

    @Test
    fun seedInsertsProvidersMissingFromExistingCatalog() = runBlocking {
        val first = LLMModelConfigs.services.first()
        database.lLMModelConfigDao().upsert(
            lLMModelConfigEntities().first { it.serviceId == first.serviceId },
        )
        assertEquals(1, database.lLMModelConfigDao().count())

        seedMissingModelCatalog(database)

        val ids = database.lLMModelConfigDao().getAll().map { it.serviceId }
        assertEquals(LLMModelConfigs.services.map { it.serviceId }.toSet(), ids.toSet())
        assertEquals(LLMModelConfigs.services.size, ids.size)
    }

    @Test
    fun observableQueryEmitsAfterInsert() = runBlocking {
        val emission = async(start = CoroutineStart.UNDISPATCHED) {
            database.lLMModelConfigDao().observeAll().first { it.isNotEmpty() }
        }

        database.lLMModelConfigDao().upsert(lLMModelConfigEntities().first())

        assertEquals(1, emission.await().size)
    }
}
