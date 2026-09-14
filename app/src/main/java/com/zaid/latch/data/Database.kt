package com.zaid.latch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey val id: String,
    val mediaKey: String,
    val url: String,
    val title: String,
    val platform: String,
    val thumbnail: String,
    val kind: String,
    val choiceLabel: String,
    val selector: String,
    val audioFormat: String,
    val audioBitrate: Int,
    val estimatedSize: Long,
    val status: String = QUEUED,
    val progress: Float = -1f,
    val etaSeconds: Long = -1,
    val savedUri: String = "",
    val mimeType: String = "",
    val actualSize: Long = 0,
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val QUEUED = "Queued"
        const val RUNNING = "Downloading"
        const val PROCESSING = "Processing"
        const val SAVING = "Saving"
        const val COMPLETED = "Completed"
        const val FAILED = "Failed"
        const val CANCELLED = "Cancelled"
        const val INTERRUPTED = "Interrupted"
    }
    val isActive get() = status in setOf(QUEUED, RUNNING, PROCESSING, SAVING)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadRecord>>
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadRecord?
    @Query("SELECT * FROM downloads WHERE mediaKey = :key AND status IN ('Queued', 'Downloading', 'Processing', 'Saving', 'Completed') LIMIT 1")
    suspend fun duplicate(key: String): DownloadRecord?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DownloadRecord)
    @Query("UPDATE downloads SET status = :status, progress = :progress, etaSeconds = :eta, message = :message WHERE id = :id AND status NOT IN ('Cancelled', 'Completed')")
    suspend fun progress(id: String, status: String, progress: Float, eta: Long = -1, message: String = "")
    @Query("UPDATE downloads SET status = 'Completed', progress = 100, savedUri = :uri, mimeType = :mime, actualSize = :size, etaSeconds = 0, message = '' WHERE id = :id AND status != 'Cancelled'")
    suspend fun complete(id: String, uri: String, mime: String, size: Long): Int
    @Query("UPDATE downloads SET status = 'Cancelled', message = 'Cancelled by you', progress = -1 WHERE id = :id AND status != 'Completed'")
    suspend fun cancel(id: String)
    @Query("UPDATE downloads SET status = 'Queued', progress = -1, message = '', etaSeconds = -1 WHERE id = :id AND status IN ('Failed', 'Cancelled', 'Interrupted')")
    suspend fun retry(id: String)
    @Query("SELECT * FROM downloads WHERE status = 'Queued' ORDER BY createdAt ASC LIMIT 1")
    suspend fun next(): DownloadRecord?
    @Query("UPDATE downloads SET status = 'Interrupted', message = 'The previous session ended. Tap retry to continue.' WHERE status IN ('Queued', 'Downloading', 'Processing', 'Saving')")
    suspend fun recoverInterrupted()
    @Query("UPDATE downloads SET status = 'Interrupted', message = :message WHERE status IN ('Queued', 'Downloading', 'Processing', 'Saving')")
    suspend fun interruptActive(message: String)
    @Query("SELECT * FROM downloads WHERE status != 'Completed' AND savedUri != ''")
    suspend fun unfinishedWithFiles(): List<DownloadRecord>
    @Query("UPDATE downloads SET savedUri = :uri, mimeType = :mime WHERE id = :id")
    suspend fun reserveUri(id: String, uri: String, mime: String)
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun remove(id: String)
    @Query("DELETE FROM downloads WHERE status NOT IN ('Queued', 'Downloading', 'Processing', 'Saving')")
    suspend fun clearHistory()
}
@Database(entities = [DownloadRecord::class], version = 1, exportSchema = true)
abstract class LatchDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}
