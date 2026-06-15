package com.example.scoutface

import android.content.Context
import androidx.room.*

@Dao
interface PersonDao {
    @Query("SELECT * FROM people_memory")
    suspend fun getAllPeople(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePerson(person: PersonEntity)

    @Query("SELECT * FROM people_memory WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): PersonEntity?
}

@Database(entities = [PersonEntity::class], version = 1)
abstract class ScoutDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao

    companion object {
        @Volatile
        private var INSTANCE: ScoutDatabase? = null

        fun getDatabase(context: Context): ScoutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScoutDatabase::class.java,
                    "scout_brain.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}