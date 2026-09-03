package nl.ikomex.karaokey.data.queue

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

enum class QueueItemStatus {
    PENDING,
    PLAYING,
    PLAYED,
    SKIPPED
}

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotifyUri: String,
    val trackName: String,
    val artistName: String,
    val albumArtUrl: String? = null,
    val durationMs: Long = 0,
    val addedBy: String = "Guest",
    val position: Int = 0,
    val status: String = QueueItemStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items WHERE status IN ('PENDING', 'PLAYING') ORDER BY position ASC, createdAt ASC")
    fun observeActiveQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE status = 'PENDING' ORDER BY position ASC, createdAt ASC LIMIT 1")
    suspend fun getNextPending(): QueueItemEntity?

    @Query("SELECT * FROM queue_items WHERE status = 'PLAYING' LIMIT 1")
    suspend fun getCurrentlyPlaying(): QueueItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue_items WHERE status = 'PENDING'")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(item: QueueItemEntity): Long

    @Query("UPDATE queue_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE queue_items SET status = 'SKIPPED' WHERE status = 'PLAYING'")
    suspend fun clearPlaying()

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM queue_items ORDER BY position ASC, createdAt ASC")
    suspend fun getAll(): List<QueueItemEntity>
}

@Database(entities = [QueueItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "karaokey.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
