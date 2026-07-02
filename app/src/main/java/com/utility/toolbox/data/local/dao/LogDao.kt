package com.utility.toolbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.utility.toolbox.data.local.entity.LogEntry
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing persisted log entries.
 *
 * Supports:
 *   - Inserting new log entries
 *   - Querying logs by level, tag, or search text (with Flow for reactive UI)
 *   - Deleting old logs (by age or all)
 *   - Getting log count and size info
 */
@Dao
interface LogDao {

    /** Insert a single log entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: LogEntry): Long

    /** Insert multiple log entries at once (for bulk insert at app start). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<LogEntry>)

    /** Get all logs ordered by most recent first. */
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    /** Get logs filtered by minimum level (e.g. "W" = WARN and above). */
    @Query("SELECT * FROM logs WHERE level IN (:levels) ORDER BY timestamp DESC")
    fun getLogsByLevel(levels: List<String>): Flow<List<LogEntry>>

    /** Search logs by text in tag or message. */
    @Query("SELECT * FROM logs WHERE tag LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchLogs(query: String): Flow<List<LogEntry>>

    /** Search logs filtered by level. */
    @Query("SELECT * FROM logs WHERE (tag LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%') AND level IN (:levels) ORDER BY timestamp DESC")
    fun searchLogsFiltered(query: String, levels: List<String>): Flow<List<LogEntry>>

    /** Get all distinct tags for the filter dropdown. */
    @Query("SELECT DISTINCT tag FROM logs ORDER BY tag ASC")
    fun getAllTags(): Flow<List<String>>

    /** Get total count of stored logs. */
    @Query("SELECT COUNT(*) FROM logs")
    suspend fun getLogCount(): Int

    /** Delete all logs. */
    @Query("DELETE FROM logs")
    suspend fun deleteAll()

    /** Delete logs older than the given timestamp. */
    @Query("DELETE FROM logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    /** Delete logs by level (e.g. delete all VERBOSE logs). */
    @Query("DELETE FROM logs WHERE level = :level")
    suspend fun deleteByLevel(level: String)

    /** Keep only the most recent N logs, delete the rest. */
    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimToCount(keepCount: Int)
}
