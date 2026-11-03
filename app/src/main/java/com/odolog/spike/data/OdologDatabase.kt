package com.odolog.spike.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
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

    @Update
    suspend fun update(trip: Trip)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
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

@Database(entities = [Trip::class, AppState::class], version = 1, exportSchema = false)
abstract class OdologDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        fun create(context: Context): OdologDatabase =
            Room.databaseBuilder(context.applicationContext, OdologDatabase::class.java, "odolog.db").build()
    }
}
