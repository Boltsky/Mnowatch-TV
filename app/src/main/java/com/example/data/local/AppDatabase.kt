package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val posterUrl: String,
    val isSeries: Boolean,
    val episodeId: String,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val year: String,
    val genre: String,
    val vj: String,
    val isSeries: Boolean,
    val addedTimestamp: Long
)

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT 30")
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE contentId = :contentId LIMIT 1")
    suspend fun getWatchHistoryById(contentId: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun hasFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Database(
    entities = [WatchHistoryEntity::class, FavoriteEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "munowatch_tv_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
