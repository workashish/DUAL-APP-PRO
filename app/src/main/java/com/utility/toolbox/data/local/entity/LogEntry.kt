package com.utility.toolbox.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single log entry captured by the in-app logger.
 *
 * Stores the same data you'd see in logcat but persists it locally so users
 * can browse, filter, and share logs without needing ADB access.
 */
@Entity(
    tableName = "logs",
    indices = [
        Index("level"),
        Index("timestamp"),
        Index("tag")
    ]
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Log level: V=VERBOSE, D=DEBUG, I=INFO, W=WARN, E=ERROR, C=CRASH */
    @ColumnInfo(name = "level")
    val level: String,

    /** Log tag (usually the class or component name) */
    @ColumnInfo(name = "tag")
    val tag: String,

    /** The log message content */
    @ColumnInfo(name = "message")
    val message: String,

    /** Optional stack trace for errors / crashes */
    @ColumnInfo(name = "stack_trace")
    val stackTrace: String? = null,

    /** Unix timestamp in milliseconds when this log was captured */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /** Package name or process identifier that generated the log */
    @ColumnInfo(name = "source")
    val source: String = "host"
)
