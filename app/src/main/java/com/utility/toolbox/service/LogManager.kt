package com.utility.toolbox.service

import android.content.Context
import android.util.Log
import com.utility.toolbox.data.local.dao.LogDao
import com.utility.toolbox.data.local.entity.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.StringWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized in-app logging manager.
 *
 * Writes logs to both:
 *   1. Android logcat (via android.util.Log) — so developers can still use ADB
 *   2. Room database (LogDao) — so the in-app Log Viewer can display them
 *
 * Also installs a global uncaught exception handler to capture crash
 * stack traces before the app terminates.
 *
 * USAGE:
 *   LogManager.d(TAG, "message")   // debug
 *   LogManager.i(TAG, "message")   // info
 *   LogManager.w(TAG, "message")   // warn
 *   LogManager.e(TAG, "message")   // error with optional Throwable
 *   LogManager.crash(TAG, "msg", throwable)  // crash-level
 */
class LogManager private constructor(
    private val logDao: LogDao,
    private val appContext: Context
) {
    companion object {
        private const val TAG = "LogManager"
        private const val MAX_STORED_LOGS = 5000

        @Volatile
        private var INSTANCE: LogManager? = null

        @Volatile
        private var sIsEnabled = true

        /** Background scope for DB writes (don't block callers). */
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // ── Public API ─────────────────────────────────────────────────

        /** Get or create the singleton instance. */
        fun getInstance(context: Context, dao: LogDao): LogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LogManager(dao, context.applicationContext)
                    .also { INSTANCE = it }
            }
        }

        /** Enable/disable database logging (logcat is unaffected). */
        fun setEnabled(enabled: Boolean) { sIsEnabled = enabled }

        fun isEnabled(): Boolean = sIsEnabled

        // ── Log levels ─────────────────────────────────────────────────

        fun v(tag: String, msg: String) {
            Log.v(tag, msg)
            if (sIsEnabled) writeAsync("V", tag, msg, null)
        }

        fun d(tag: String, msg: String) {
            Log.d(tag, msg)
            if (sIsEnabled) writeAsync("D", tag, msg, null)
        }

        fun i(tag: String, msg: String) {
            Log.i(tag, msg)
            if (sIsEnabled) writeAsync("I", tag, msg, null)
        }

        fun w(tag: String, msg: String) {
            Log.w(tag, msg)
            if (sIsEnabled) writeAsync("W", tag, msg, null)
        }

        fun w(tag: String, msg: String, tr: Throwable?) {
            Log.w(tag, msg, tr)
            if (sIsEnabled) writeAsync("W", tag, msg, tr?.let { stackTraceToString(it) })
        }

        fun e(tag: String, msg: String) {
            Log.e(tag, msg)
            if (sIsEnabled) writeAsync("E", tag, msg, null)
        }

        fun e(tag: String, msg: String, tr: Throwable?) {
            Log.e(tag, msg, tr)
            if (sIsEnabled) writeAsync("E", tag, msg, tr?.let { stackTraceToString(it) })
        }

        /** Crash-level entry — for uncaught exceptions. */
        fun crash(tag: String, msg: String, tr: Throwable) {
            Log.e(tag, msg, tr)
            if (sIsEnabled) writeAsync("C", tag, msg, stackTraceToString(tr))
        }

        // ── Query API (for the Log Viewer) ─────────────────────────────

        fun getAllLogs(): Flow<List<LogEntry>> = safeDao()?.getAllLogs()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

        fun getLogsByLevel(levels: List<String>): Flow<List<LogEntry>> =
            safeDao()?.getLogsByLevel(levels)
                ?: kotlinx.coroutines.flow.flowOf(emptyList())

        fun searchLogs(query: String): Flow<List<LogEntry>> =
            safeDao()?.searchLogs(query)
                ?: kotlinx.coroutines.flow.flowOf(emptyList())

        fun searchLogsFiltered(query: String, levels: List<String>): Flow<List<LogEntry>> =
            safeDao()?.searchLogsFiltered(query, levels)
                ?: kotlinx.coroutines.flow.flowOf(emptyList())

        fun getAllTags(): Flow<List<String>> =
            safeDao()?.getAllTags()
                ?: kotlinx.coroutines.flow.flowOf(emptyList())

        // ── Management ─────────────────────────────────────────────────

        /** Clear all stored logs from the database. */
        fun clearAllLogs() {
            ioScope.launch {
                safeDao()?.deleteAll()
                Log.d(TAG, "All logs cleared")
            }
        }

        /** Delete logs older than the specified age in milliseconds. */
        fun clearLogsOlderThan(ageMs: Long) {
            ioScope.launch {
                val cutoff = System.currentTimeMillis() - ageMs
                safeDao()?.deleteOlderThan(cutoff)
                Log.d(TAG, "Logs older than ${ageMs / 1000}s cleared")
            }
        }

        /** Keep only the most recent N logs. */
        fun trimLogs() {
            ioScope.launch {
                safeDao()?.trimToCount(MAX_STORED_LOGS)
            }
        }

        // ── Export ─────────────────────────────────────────────────────

        /**
         * Format all logs as a plain-text string suitable for sharing.
         */
        fun formatLogsForExport(logs: List<LogEntry>): String {
            val dateFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val sb = StringBuilder()
            sb.appendLine("═══════════════════════════════════════════════")
            sb.appendLine("  DualSpace — In-App Log Export")
            sb.appendLine("  Exported: ${dateFmt.format(Date())}")
            sb.appendLine("  Total entries: ${logs.size}")
            sb.appendLine("═══════════════════════════════════════════════")
            sb.appendLine()
            sb.appendLine(" Time          Level Tag                           Message")
            sb.appendLine(" ──────────── ───── ────────────────────────────── ───────────────────────")

            logs.sortedBy { it.timestamp }.forEach { entry ->
                val time = dateFmt.format(Date(entry.timestamp))
                val tagPadded = entry.tag.padEnd(32).take(32)
                val msgPreview = entry.message.take(150)
                sb.appendLine(" $time  ${entry.level}    $tagPadded $msgPreview")
                if (!entry.stackTrace.isNullOrBlank()) {
                    entry.stackTrace.lines().forEach { line ->
                        sb.appendLine("  │ $line")
                    }
                }
            }
            sb.appendLine()
            sb.appendLine("── End of export ──")
            return sb.toString()
        }

        // ── Internal ───────────────────────────────────────────────────

        private fun writeAsync(level: String, tag: String, msg: String, stackTrace: String?) {
            ioScope.launch {
                try {
                    val dao = safeDao() ?: return@launch
                    dao.insert(
                        LogEntry(
                            level = level,
                            tag = tag.take(100),
                            message = msg.take(1000),
                            stackTrace = stackTrace?.take(10000),
                            timestamp = System.currentTimeMillis(),
                            source = "host"
                        )
                    )
                    // Trim if we exceed the limit (roughly every ~100 inserts)
                    dao.trimToCount(MAX_STORED_LOGS)
                } catch (_: Exception) {
                    // Silently ignore DB write failures — don't recurse
                }
            }
        }

        private fun safeDao(): LogDao? {
            return INSTANCE?.logDao
        }

        private fun stackTraceToString(tr: Throwable): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            tr.printStackTrace(pw)
            pw.flush()
            return sw.toString()
        }

        /**
         * Install an uncaught exception handler to capture crashes.
         * Chains with any existing handler so the app still terminates normally.
         */
        fun installCrashHandler() {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    crash("CRASH", "Uncaught exception in ${thread.name}", throwable)
                    // Give the DB write a moment before the app dies
                    Thread.sleep(100)
                } catch (_: Exception) { /* ignore */ }
                // Forward to previous handler (app will terminate)
                previousHandler?.uncaughtException(thread, throwable)
                    ?: runCatching { System.exit(2) }
            }
        }
    }
}
