package github.ponyhuang.asssistantai.core.database

import androidx.room.RoomDatabase

/** Exploration builds deliberately recreate all owned tables instead of carrying migrations. */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.destructiveForPrototype(): RoomDatabase.Builder<T> =
    fallbackToDestructiveMigration(dropAllTables = true)
