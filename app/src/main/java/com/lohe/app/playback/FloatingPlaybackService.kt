package com.lohe.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.lohe.app.MainActivity
import com.lohe.app.R
import com.lohe.app.data.ServiceLocator
import com.lohe.app.ui.theme.ElectricBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var connection: PlayerConnection
    private lateinit var windows: WindowManager
    private lateinit var controls: DragControls
    private lateinit var playButton: ImageButton
    private val preferences by lazy { getSharedPreferences("floating-player-position", MODE_PRIVATE) }
    private var expanded = false
    private var attached = false
    private var backSeconds = 10
    private var forwardSeconds = 30
    private var diameter = 56
    private var horizontal = 0.9f
    private var vertical = 0.6f
    private val layout = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.LEFT }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
        windows = getSystemService(WindowManager::class.java)
        connection = PlayerConnection.create(this)
        connection.attach()
        horizontal = preferences.getFloat("horizontal", 0.9f)
        vertical = preferences.getFloat("vertical", 0.6f)
        expanded = preferences.getBoolean("expanded", false)
        controls = DragControls(this)
        controls.background = GradientDrawable().apply {
            setColor(ElectricBlue.toArgb())
            cornerRadius = dp(32).toFloat()
        }
        controls.elevation = dp(6).toFloat()
        controls.clipToOutline = true
        render()
        scope.launch {
            ServiceLocator.settingsStore().settings.collect {
                backSeconds = it.skipBackSeconds
                forwardSeconds = it.skipForwardSeconds
                if (!it.floatingPlayerEnabled) {
                    stopSelf()
                    return@collect
                }
                val sizeChanged = diameter != it.floatingPlayerSizeDp
                diameter = it.floatingPlayerSizeDp
                layout.alpha = it.floatingPlayerOpacity
                if (sizeChanged) {
                    render()
                    position()
                }
                if (attached) windows.updateViewLayout(controls, layout)
            }
        }
        scope.launch {
            connection.isPlaying.collect { playing -> updatePlaying(playing) }
        }
        scope.launch {
            while (isActive) {
                if (!Settings.canDrawOverlays(this@FloatingPlaybackService)) {
                    ServiceLocator.settingsStore().setFloatingPlayerEnabled(false)
                    stopSelf()
                    break
                }
                delay(2_000)
            }
        }
    }

    private fun updatePlaying(playing: Boolean) {
        playButton.setImageResource(if (playing) R.drawable.ic_float_pause else R.drawable.ic_float_play)
        playButton.contentDescription = if (playing) {
            "Pause; double-tap to expand controls; hold to close"
        } else {
            "Play; double-tap to expand controls; hold to close"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            scope.launch {
                ServiceLocator.settingsStore().setFloatingPlayerEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Floating player", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val hide = PendingIntent.getService(this, 1, Intent(this, FloatingPlaybackService::class.java).setAction(ACTION_HIDE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_float_play)
                .setContentTitle("Lohe floating player")
                .setContentText("Drag to move. Double-tap to expand; hold to close.")
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, "Hide controls", hide).build())
                .setOngoing(true)
                .build()
            startForeground(4820, notification)
            if (!attached) {
                position()
                windows.addView(controls, layout)
                attached = true
            }
        } catch (failure: RuntimeException) {
            android.util.Log.e("FloatingPlayer", "Could not show overlay", failure)
            Toast.makeText(this, "Could not show floating player. Check Display over other apps permission.", Toast.LENGTH_LONG).show()
            scope.launch { ServiceLocator.settingsStore().setFloatingPlayerEnabled(false); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun render() {
        controls.removeAllViews()
        val buttonSize = floatingPlayerButtonSize(diameter)
        (controls.background as GradientDrawable).cornerRadius = dp(diameter / 2).toFloat()
        val padding = dp((diameter - buttonSize) / 2)
        controls.setPadding(padding, padding, padding, padding)
        if (expanded) button(R.drawable.ic_float_rewind, "Rewind", buttonSize) { connection.skipBackMs(backSeconds * 1000L) }
        playButton = button(R.drawable.ic_float_play, "Play or pause", buttonSize) {
            if (connection.controller?.mediaItemCount == 0 || connection.controller == null) {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else connection.togglePlayPause()
        }
        updatePlaying(connection.isPlaying.value)
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                playButton.performClick()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                toggleExpanded()
                return true
            }

            override fun onLongPress(event: MotionEvent) {
                closeControls()
            }
        })
        playButton.setOnTouchListener { _, event ->
            gestures.onTouchEvent(event)
            true
        }
        if (expanded) {
            button(R.drawable.ic_float_forward, "Forward", buttonSize) { connection.skipForwardMs(forwardSeconds * 1000L) }
            button(R.drawable.ic_float_collapse, "Collapse controls", buttonSize) { toggleExpanded() }
        }
        controls.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    }

    private fun toggleExpanded() {
        expanded = !expanded
        preferences.edit().putBoolean("expanded", expanded).apply()
        render()
        position()
        if (attached) windows.updateViewLayout(controls, layout)
        controls.announceForAccessibility(
            if (expanded) "Playback controls expanded"
            else "Playback controls collapsed. Double-tap play or pause to expand.",
        )
    }

    private fun closeControls() {
        scope.launch {
            ServiceLocator.settingsStore().setFloatingPlayerEnabled(false)
            stopSelf()
        }
    }

    private fun button(icon: Int, description: String, size: Int, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            background = getDrawable(android.R.drawable.list_selector_background)
            val padding = dp(((size - 24f * diameter / 56f) / 2f).roundToInt().coerceAtLeast(0))
            setPadding(padding, padding, padding, padding)
            setOnClickListener { action() }
            controls.addView(this, LinearLayout.LayoutParams(dp(size), dp(size)))
        }

    private fun available(): Pair<Int, Int> {
        val size = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds else {
            android.graphics.Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }
        return (size.width() - controls.measuredWidth).coerceAtLeast(0) to
            (size.height() - statusAndNavigationHeight() - controls.measuredHeight).coerceAtLeast(0)
    }

    private fun statusAndNavigationHeight(): Int = if (Build.VERSION.SDK_INT >= 30) {
        val insets = windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        insets.top + insets.bottom
    } else dp(72)

    private fun position() {
        val (width, height) = available()
        layout.x = floatingPlayerPosition(horizontal, width)
        layout.y = floatingPlayerPosition(vertical, height)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        position()
        if (attached) windows.updateViewLayout(controls, layout)
    }

    override fun onDestroy() {
        if (attached) runCatching { windows.removeView(controls) }
        connection.detach()
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private inner class DragControls(context: Context) : LinearLayout(context) {
        private var startX = 0f
        private var startY = 0f
        private var originalX = 0
        private var originalY = 0
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    originalX = layout.x
                    originalY = layout.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startX) > slop || abs(event.rawY - startY) > slop) dragging = true
                }
            }
            return dragging
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!dragging) return super.onTouchEvent(event)
            val (width, height) = available()
            layout.x = (originalX + event.rawX - startX).roundToInt().coerceIn(0, width)
            layout.y = (originalY + event.rawY - startY).roundToInt().coerceIn(0, height)
            horizontal = floatingPlayerFraction(layout.x, width)
            vertical = floatingPlayerFraction(layout.y, height)
            if (attached) windows.updateViewLayout(controls, layout)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                preferences.edit().putFloat("horizontal", horizontal).putFloat("vertical", vertical).apply()
                dragging = false
            }
            return true
        }
    }

    companion object {
        private const val CHANNEL = "lohe-floating-player"
        private const val ACTION_HIDE = "com.lohe.app.HIDE_FLOATING_PLAYER"

        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, FloatingPlaybackService::class.java)
            if (enabled && Settings.canDrawOverlays(context)) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure { android.util.Log.e("FloatingPlayer", "Start rejected", it) }
            } else context.stopService(intent)
        }
    }
}
