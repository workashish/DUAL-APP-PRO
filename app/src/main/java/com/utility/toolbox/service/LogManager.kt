package com.utility.toolbox.service

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.utility.toolbox.data.local.dao.LogDao
import com.utility.toolbox.data.local.entity.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogManager private constructor(
    private val logDao: LogDao,
    private val appContext: Context
) {
    companion object {
        private const val TAG = "LogManager"
        private const val DB_NAME = "dualspace_logs_db"
        private const val MAX_STORED_LOGS = 5000

        @Volatile private var INSTANCE: LogManager? = null
        @Volatile private var sIsEnabled = true
        private val pendingLogs = mutableListOf<LogEntry>()
        private val pendingLock = Any()
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Database(entities = [LogEntry::class], version = 1, exportSchema = false)
        abstract class LogOnlyDb : RoomDatabase() { abstract fun logDao(): LogDao }

        fun init(context: Context) {
            if (INSTANCE != null) return
            synchronized(this) {
                if (INSTANCE != null) return
                val appCtx = context.applicationContext
                val db = Room.databaseBuilder(appCtx, LogOnlyDb::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                val dao = db.logDao()
                INSTANCE = LogManager(dao, appCtx)
                synchronized(pendingLock) {
                    val buffered = pendingLogs.toList()
                    pendingLogs.clear()
                    if (buffered.isNotEmpty()) {
                        ioScope.launch {
                            try { buffered.forEach { dao.insert(it) } } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        fun setEnabled(enabled: Boolean) { sIsEnabled = enabled }
        fun isEnabled(): Boolean = sIsEnabled

        fun v(tag: String, msg: String) { Log.v(tag, msg); if (sIsEnabled) writeAsync("V", tag, msg, null) }
        fun d(tag: String, msg: String) { Log.d(tag, msg); if (sIsEnabled) writeAsync("D", tag, msg, null) }
        fun i(tag: String, msg: String) { Log.i(tag, msg); if (sIsEnabled) writeAsync("I", tag, msg, null) }
        fun w(tag: String, msg: String) { Log.w(tag, msg); if (sIsEnabled) writeAsync("W", tag, msg, null) }
        fun w(tag: String, msg: String, tr: Throwable?) { Log.w(tag, msg, tr); if (sIsEnabled) writeAsync("W", tag, msg, tr?.let { stackTraceToString(it) }) }
        fun e(tag: String, msg: String) { Log.e(tag, msg); if (sIsEnabled) writeAsync("E", tag, msg, null) }
        fun e(tag: String, msg: String, tr: Throwable?) { Log.e(tag, msg, tr); if (sIsEnabled) writeAsync("E", tag, msg, tr?.let { stackTraceToString(it) }) }
        fun crash(tag: String, msg: String, tr: Throwable) { Log.e(tag, msg, tr); if (sIsEnabled) writeAsync("C", tag, msg, stackTraceToString(tr)) }

        fun getAllLogs(): Flow<List<LogEntry>> = INSTANCE?.logDao?.getAllLogs() ?: flowOf(emptyList())
        fun getLogsByLevel(levels: List<String>): Flow<List<LogEntry>> = INSTANCE?.logDao?.getLogsByLevel(levels) ?: flowOf(emptyList())
        fun searchLogsFiltered(query: String, levels: List<String>): Flow<List<LogEntry>> = INSTANCE?.logDao?.searchLogsFiltered(query, levels) ?: flowOf(emptyList())
        fun getAllTags(): Flow<List<String>> = INSTANCE?.logDao?.getAllTags() ?: flowOf(emptyList())
        fun clearAllLogs() { INSTANCE?.logDao?.let { dao -> ioScope.launch { try { dao.deleteAll() } catch (_: Exception) {} } } }

        fun formatLogsForExport(logs: List<LogEntry>): String {
            val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val sb = StringBuilder()
            sb.appendLine("═══ DualSpace Log Export ═══")
            sb.appendLine("Exported: ${fmt.format(Date())} | Entries: ${logs.size}")
            sb.appendLine()
            logs.sortedBy { it.timestamp }.forEach { e ->
                sb.appendLine("${fmt.format(Date(e.timestamp))} ${e.level} [${e.tag}] ${e.message}")
                if (!e.stackTrace.isNullOrBlank()) sb.appendLine("  └─ ${e.stackTrace.take(200)}")
            }
            return sb.toString()
        }

        private fun writeAsync(level: String, tag: String, msg: String, stackTrace: String?) {
            val entry = LogEntry(level = level, tag = tag.take(100), message = msg.take(1000),
                stackTrace = stackTrace?.take(10000), timestamp = System.currentTimeMillis(), source = "host")
            val dao = INSTANCE?.logDao
            if (dao != null) {
                ioScope.launch { try { dao.insert(entry); dao.trimToCount(MAX_STORED_LOGS) } catch (_: Exception) {} }
            } else {
                synchronized(pendingLock) { if (pendingLogs.size < 500) pendingLogs.add(entry) }
            }
        }

        private fun stackTraceToString(tr: Throwable): String { val sw = StringWriter(); tr.printStackTrace(PrintWriter(sw)); return sw.toString() }

        fun installCrashHandler() {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try { crash("CRASH", "Uncaught in ${thread.name}", throwable); Thread.sleep(100) } catch (_: Exception) {}
                prev?.uncaughtException(thread, throwable) ?: System.exit(2)
            }
        }
    }
}
