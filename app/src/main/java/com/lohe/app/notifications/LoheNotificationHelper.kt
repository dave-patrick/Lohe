package com.lohe.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lohe.app.MainActivity
import com.lohe.app.R

data class NewEpisodeAlert(
    val podcastId: String,
    val podcastTitle: String,
    val episodeTitle: String,
    val episodeId: String
)

object LoheNotificationHelper {

    const val CHANNEL_NEW_EPISODES = "lohe_new_episodes"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_NEW_EPISODES) == null) {
            val channel = NotificationChannel(
                CHANNEL_NEW_EPISODES,
                "New Podcast Episodes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when followed podcasts publish new episodes"
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun hasPostNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun postNewEpisodesSummary(context: Context, alerts: List<NewEpisodeAlert>) {
        if (alerts.isEmpty()) return
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return
        if (!hasPostNotificationPermission(context)) return

        ensureChannels(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1201,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val first = alerts.first()
        val title = if (alerts.size == 1) {
            first.podcastTitle
        } else {
            "${alerts.size} New Podcast Episodes"
        }
        val body = if (alerts.size == 1) {
            first.episodeTitle
        } else {
            alerts.take(3).joinToString(" • ") { "${it.podcastTitle}: ${it.episodeTitle}" }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES)
            .setSmallIcon(R.drawable.ic_widget_signal)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(12001, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked between check and notify.
        }
    }
}
