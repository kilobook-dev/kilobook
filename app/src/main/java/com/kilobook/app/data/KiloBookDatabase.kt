package com.kilobook.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTime DESC, id DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(): Trip?

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): Trip?

    @Insert
    suspend fun insert(trip: Trip): Long

    @Insert
    suspend fun insertAll(trips: List<Trip>)

    @Update
    suspend fun update(trip: Trip)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()

    /** Removes only the example trips (5-8); recorded trips stay. Returns how many were deleted. */
    @Query("DELETE FROM trips WHERE isSample = 1")
    suspend fun deleteSamples(): Int
}

@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE id = 0")
    suspend fun get(): AppState?

    @Upsert
    suspend fun upsert(state: AppState)

    @Query("DELETE FROM app_state")
    suspend fun clear()
}

@Database(entities = [Trip::class, AppState::class], version = 2, exportSchema = false)
abstract class KiloBookDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        /** Version 2 (Phase 5): example-trip flag. Existing trips keep their data and become isSample = 0. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN isSample INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): KiloBookDatabase =
            Room.databaseBuilder(context.applicationContext, KiloBookDatabase::class.java, "kilobook.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
