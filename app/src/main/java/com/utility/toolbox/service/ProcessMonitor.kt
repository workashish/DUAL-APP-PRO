package com.utility.toolbox.service

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class CloneStatus(val packageName: String, val userId: Int, val wasRunning: Boolean, val isRunning: Boolean, val crashed: Boolean)

    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null
    private val runningStates = mutableMapOf<String, Boolean>()
    private val listeners = mutableListOf<(List<CloneStatus>) -> Unit>()

    fun startMonitoring(intervalMs: Long = 5000) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                checkProcesses()
            }
        }
        LogManager.i("ProcessMonitor", "Started (interval=${intervalMs}ms)")
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        LogManager.i("ProcessMonitor", "Stopped")
    }

    fun addListener(listener: (List<CloneStatus>) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (List<CloneStatus>) -> Unit) { listeners.remove(listener) }

    private fun checkProcesses() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = am.runningAppProcesses?.map { it.processName }?.toSet() ?: emptySet()
            val changes = mutableListOf<CloneStatus>()

            for ((key, wasRunning) in runningStates.toList()) {
                val isNowRunning = runningProcesses.any { it.contains(key) }
                if (wasRunning && !isNowRunning) {
                    LogManager.w("ProcessMonitor", "Process stopped: $key")
                    changes.add(CloneStatus(key, 0, true, false, crashed = true))
                }
                runningStates[key] = isNowRunning
            }

            if (changes.isNotEmpty()) listeners.forEach { it(changes) }
        } catch (e: Exception) {
            LogManager.w("ProcessMonitor", "Error: ${e.message}")
        }
    }

    fun trackProcess(processName: String) {
        runningStates[processName] = true
    }

    fun untrackProcess(processName: String) {
        runningStates.remove(processName)
    }

    fun isProcessRunning(processName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.any { it.processName.contains(processName) } ?: false
    }
}
