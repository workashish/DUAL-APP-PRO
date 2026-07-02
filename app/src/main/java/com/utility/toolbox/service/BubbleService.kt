package com.utility.toolbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.utility.toolbox.DualAppsApp
import com.utility.toolbox.MainActivity
import com.utility.toolbox.R
import com.utility.toolbox.data.local.AppDatabase
import com.utility.toolbox.data.local.entity.ClonedAppEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import com.utility.toolbox.service.BlackBoxEngine

/**
 * Floating bubble overlay service that provides quick access to cloned apps.
 * Similar to Facebook Messenger's chat heads.
 * Shows running cloned apps as bubbles that users can tap to switch.
 */
@AndroidEntryPoint
class BubbleService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "bubble_service"
        private const val TAG = "BubbleService"

        private var _isRunning = false

        fun isRunning(context: Context): Boolean {
            return _isRunning
        }

        fun start(context: Context) {
            _isRunning = true
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            _isRunning = false
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var blackBoxEngine: BlackBoxEngine

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubbleView != null) return

        // Inflate the bubble
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.bubble_view, null)

        // Bubble icon
        val bubbleIcon = bubbleView?.findViewById<ImageView>(R.id.bubble_icon)
        bubbleIcon?.setImageResource(R.drawable.ic_launcher_foreground)

        // Bubble touch listener for dragging and tap detection
        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (event.rawX - bubbleView!!.x).toInt()
                    initialY = (event.rawY - bubbleView!!.y).toInt()
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        bubbleView?.x = event.rawX - initialX
                        bubbleView?.y = event.rawY - initialY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleExpanded()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        // Add to window
        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun showExpandedView() {
        if (expandedView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        expandedView = inflater.inflate(R.layout.bubble_expanded, null)

        val appListContainer = expandedView?.findViewById<LinearLayout>(R.id.app_list)
        val closeButton = expandedView?.findViewById<ImageView>(R.id.close_bubble)
        val headerTitle = expandedView?.findViewById<TextView>(R.id.bubble_title)

        headerTitle?.text = "Quick Switch"

        // Load running cloned apps
        serviceScope.launch {
            try {
                val apps = database.clonedAppDao().getRunningApps()
                launch(Dispatchers.Main) {
                    appListContainer?.removeAllViews()
                    if (apps.isEmpty()) {
                        val emptyView = inflater.inflate(
                            android.R.layout.simple_list_item_1,
                            appListContainer,
                            false
                        ) as TextView
                        emptyView.text = "No running apps"
                        appListContainer?.addView(emptyView)
                    } else {
                        apps.forEach { app ->
                            val appView = inflater.inflate(
                                android.R.layout.simple_list_item_2,
                                appListContainer,
                                false
                            )
                            val text1 = appView.findViewById<TextView>(android.R.id.text1)
                            val text2 = appView.findViewById<TextView>(android.R.id.text2)
                            text1.text = app.appName
                            text2.text = app.clonePackage
                            appView.setOnClickListener {
                                // Launch via BlackBoxEngine for proper isolation
                                if (blackBoxEngine.isInitialized()) {
                                    blackBoxEngine.launchClone(app.clonePackage, app.userId)
                                } else {
                                    val launchIntent = packageManager
                                        .getLaunchIntentForPackage(app.clonePackage)
                                    if (launchIntent != null) {
                                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        startActivity(launchIntent)
                                    }
                                }
                                hideExpandedView()
                            }
                            appListContainer?.addView(appView)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }

        closeButton?.setOnClickListener { hideExpandedView() }

        val expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(expandedView, expandedParams)
        isExpanded = true
    }

    private fun hideExpandedView() {
        expandedView?.let { windowManager.removeView(it) }
        expandedView = null
        isExpanded = false
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            hideExpandedView()
        } else {
            showExpandedView()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Switch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating bubble for quick app switching"
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quick Switch")
            .setContentText("Tap to switch between cloned apps")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        hideExpandedView()
        try {
            bubbleView?.let {
                if (it.isAttachedToWindow || it.windowToken != null) {
                    windowManager.removeView(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing bubble view", e)
        }
        bubbleView = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
