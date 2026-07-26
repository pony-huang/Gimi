package github.ponyhuang.asssistantai.core.database

import androidx.room.RoomDatabase

/** Development builds recreate owned tables instead of carrying migration compatibility. */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.destructiveForPrototype(): RoomDatabase.Builder<T> =
    fallbackToDestructiveMigration(dropAllTables = true)
