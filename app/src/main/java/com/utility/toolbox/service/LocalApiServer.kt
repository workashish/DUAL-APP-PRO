package com.utility.toolbox.service

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalApiServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val gson = Gson()
    private val startTime = System.currentTimeMillis()

    data class ApiResponse(val success: Boolean, val data: Any? = null, val error: String? = null)
    data class CloneInfo(val id: Long, val name: String, val clonePackage: String, val userId: Int, val deviceModel: String, val deviceBrand: String, val androidId: String, val gmsInstalled: Boolean, val isRunning: Boolean)
    data class ServerInfo(val port: Int, val uptime: Long, val version: String, val deviceInfo: String)

    fun start(port: Int = 8888) {
        if (isRunning) return
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                LogManager.i("ApiServer", "Started on port $port")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: continue
                        handleRequest(client)
                    } catch (e: Exception) {
                        if (isRunning) LogManager.w("ApiServer", "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) { LogManager.e("ApiServer", "Failed to start: ${e.message}") }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    fun isRunning() = isRunning

    private fun handleRequest(client: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)
                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@launch
                val method = parts[0]; val path = parts[1]
                val headers = mutableMapOf<String, String>()
                var line = reader.readLine()
                while (line != null && line.isNotEmpty()) {
                    val ci = line.indexOf(':')
                    if (ci > 0) headers[line.substring(0, ci).trim().lowercase()] = line.substring(ci + 1).trim()
                    line = reader.readLine()
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) { val buf = CharArray(contentLength); reader.read(buf, 0, contentLength); String(buf) } else null
                val response = route(method, path, body)
                val json = gson.toJson(response)
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: application/json")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("Content-Length: ${json.toByteArray().size}")
                writer.println()
                writer.println(json)
                client.close()
            } catch (e: Exception) { try { client.close() } catch (_: Exception) {} }
        }
    }

    private fun route(method: String, path: String, body: String?): ApiResponse {
        val db = com.utility.toolbox.data.local.AppDatabase.getInstance(context)
        val clonedDao = db.clonedAppDao()
        val logDao = db.logDao()
        return when {
            path == "/" || path == "/api" || path == "/api/info" -> ApiResponse(true, ServerInfo(serverSocket?.localPort ?: 0, System.currentTimeMillis() - startTime, "1.0.0", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))
            path == "/api/health" -> ApiResponse(true, mapOf("status" to "ok"))
            path == "/api/clones" -> {
                val apps = runBlocking { clonedDao.getAllAppsSync() }
                ApiResponse(true, apps.map { CloneInfo(it.id, it.appName, it.clonePackage, it.userId, it.deviceModel, it.deviceBrand, it.androidId, it.gmsInstalled, it.isRunning) })
            }
            path.startsWith("/api/clones/") -> {
                val id = path.removePrefix("/api/clones/").toLongOrNull() ?: return ApiResponse(false, error = "Invalid ID")
                val app = runBlocking { clonedDao.getAppById(id) } ?: return ApiResponse(false, error = "Clone not found")
                ApiResponse(true, CloneInfo(app.id, app.appName, app.clonePackage, app.userId, app.deviceModel, app.deviceBrand, app.androidId, app.gmsInstalled, app.isRunning))
            }
            path == "/api/launch" && method == "POST" -> {
                val req = gson.fromJson(body, Map::class.java); val id = (req["id"] as? Double)?.toLong() ?: return ApiResponse(false, error = "Missing id")
                val app = runBlocking { clonedDao.getAppById(id) } ?: return ApiResponse(false, error = "Clone not found")
                val engine = BlackBoxEngine.getInstance(context); val ok = engine.launchClone(app.clonePackage, app.userId); ApiResponse(ok, if (ok) "Launched" else "Failed")
            }
            path == "/api/stop" && method == "POST" -> {
                val req = gson.fromJson(body, Map::class.java); val id = (req["id"] as? Double)?.toLong() ?: return ApiResponse(false, error = "Missing id")
                val app = runBlocking { clonedDao.getAppById(id) } ?: return ApiResponse(false, error = "Clone not found")
                val engine = BlackBoxEngine.getInstance(context); val ok = engine.stopClone(app.clonePackage, app.userId); ApiResponse(ok, if (ok) "Stopped" else "Failed")
            }
            path == "/api/delete" && method == "POST" -> {
                val req = gson.fromJson(body, Map::class.java); val id = (req["id"] as? Double)?.toLong() ?: return ApiResponse(false, error = "Missing id")
                val app = runBlocking { clonedDao.getAppById(id) } ?: return ApiResponse(false, error = "Clone not found")
                BlackBoxEngine.getInstance(context).uninstallClone(app.clonePackage, app.userId)
                runBlocking { clonedDao.deleteById(id) }; ApiResponse(true, "Deleted")
            }
            path == "/api/gms/install" && method == "POST" -> {
                val req = gson.fromJson(body, Map::class.java); val id = (req["id"] as? Double)?.toLong() ?: return ApiResponse(false, error = "Missing id")
                val app = runBlocking { clonedDao.getAppById(id) } ?: return ApiResponse(false, error = "Clone not found")
                val ok = BlackBoxEngine.getInstance(context).installGms(app.userId)
                if (ok) runBlocking { clonedDao.updateGmsStatus(app.id, true) }; ApiResponse(ok, if (ok) "GMS installed" else "Failed")
            }
            path == "/api/kill-all" && method == "POST" -> { BlackBoxEngine.getInstance(context).killAllCloneProcesses(); ApiResponse(true, "All killed") }
            path == "/api/logs" -> {
                val logs = runBlocking { logDao.getAllLogsSync().takeLast(100) }
                ApiResponse(true, logs.map { mapOf("time" to it.timestamp, "level" to it.level, "tag" to it.tag, "msg" to it.message) })
            }
            method == "OPTIONS" -> ApiResponse(true, "ok")
            else -> ApiResponse(false, error = "Unknown: $path")
        }
    }
}
