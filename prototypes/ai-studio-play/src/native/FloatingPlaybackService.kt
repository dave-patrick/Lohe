package com.lohe.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lohe.app.R

/**
 * Native Android Floating Playback Overlay Service.
 *
 * Implements a draggable, system-level picture-in-picture audio controller using
 * WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY on Android 8.0+ (API 26+).
 *
 * Security & Entitlement Architecture:
 * - Gated strictly under Lohe Pro ($9.99/year subscription).
 * - FOSS edition compiles without this service (build flavors isolate com.lohe.app.foss).
 * - When subscription expires or enters ON_HOLD, the overlay window is removed immediately
 *   while user preferences (floatingSizeDp, floatingTransparencyPct, floatingPlayerEnabled)
 *   are preserved for restoration upon renewal.
 */
class FloatingPlaybackService : MediaSessionService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mediaSession: MediaSession? = null

    // Draggable touch state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return super.onStartCommand(intent, flags, startId)

        when (action) {
            ACTION_SHOW_OVERLAY -> {
                // Verified Pro check before displaying system overlay
                if (isProEntitled()) {
                    showOverlayWindow()
                } else {
                    stopSelf()
                }
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlayWindow()
            }
            ACTION_ENTITLEMENT_REVOKED_OR_EXPIRED -> {
                // Subscription expired, revoked, or account hold: dismiss overlay immediately
                // User preferences remain saved in SharedPreferences
                hideOverlayWindow()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showOverlayWindow() {
        if (overlayView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Retrieve user preferences (saved size and transparency)
        val prefs = getSharedPreferences("lohe_player_settings", Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt("floatingSizeDp", 56)
        val transparencyPct = prefs.getInt("floatingTransparencyPct", 0)
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("overlay_x", 100)
            y = prefs.getInt("overlay_y", 200)
            alpha = 1.0f - (transparencyPct / 100f)
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_floating_player, null)

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Save last position
                    prefs.edit()
                        .putInt("overlay_x", params.x)
                        .putInt("overlay_y", params.y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
    }

    private fun hideOverlayWindow() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    private fun isProEntitled(): Boolean {
        val prefs = getSharedPreferences("lohe_entitlements", Context.MODE_PRIVATE)
        val status = prefs.getString("subscription_status", "NONE")
        val expiryTime = prefs.getLong("subscription_expiry_time", 0L)
        val now = System.currentTimeMillis()

        // Confirmed active or within grace period / offline tolerance
        return (status == "ACTIVE" || status == "CANCELLED" || status == "IN_GRACE_PERIOD") && expiryTime > now
    }

    private fun createNotification(): Notification {
        val channelId = "lohe_floating_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating Player Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lohe Floating Controller")
            .setContentText("Playback overlay active")
            .setSmallIcon(R.drawable.ic_lohe_mini)
            .setOngoing(true)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        hideOverlayWindow()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4099
        const val ACTION_SHOW_OVERLAY = "com.lohe.app.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.lohe.app.action.HIDE_OVERLAY"
        const val ACTION_ENTITLEMENT_REVOKED_OR_EXPIRED = "com.lohe.app.action.ENTITLEMENT_REVOKED"
    }
}
