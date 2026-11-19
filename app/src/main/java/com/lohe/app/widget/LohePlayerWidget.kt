package com.lohe.app.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lohe.app.MainActivity
import com.lohe.app.R
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.LohePreferencesRepository
import com.lohe.app.playback.LohePlaybackService
import kotlinx.coroutines.flow.first

private object WidgetKeys {
    val EPISODE_ID = stringPreferencesKey("widget_episode_id")
    val EPISODE_TITLE = stringPreferencesKey("widget_episode_title")
    val PODCAST_TITLE = stringPreferencesKey("widget_podcast_title")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val POSITION_MS = longPreferencesKey("widget_position_ms")
    val DURATION_MS = longPreferencesKey("widget_duration_ms")
}

class LohePlayerWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        suspend fun syncSnapshotAndUpdate(
            context: Context,
            episodeId: String,
            episodeTitle: String,
            podcastTitle: String,
            isPlaying: Boolean,
            positionMs: Long,
            durationMs: Long
        ) {
            if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return
            runCatching {
                val appCtx = context.applicationContext
                val manager = GlanceAppWidgetManager(appCtx)
                val ids = manager.getGlanceIds(LohePlayerWidget::class.java)
                val widget = LohePlayerWidget()
                ids.forEach { glanceId ->
                    updateAppWidgetState(appCtx, glanceId) { prefs ->
                        prefs[WidgetKeys.EPISODE_ID] = episodeId
                        prefs[WidgetKeys.EPISODE_TITLE] = episodeTitle
                        prefs[WidgetKeys.PODCAST_TITLE] = podcastTitle
                        prefs[WidgetKeys.IS_PLAYING] = isPlaying
                        prefs[WidgetKeys.POSITION_MS] = positionMs
                        prefs[WidgetKeys.DURATION_MS] = durationMs
                    }
                    widget.update(appCtx, glanceId)
                }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = LohePreferencesRepository(context.applicationContext)
        val settings = runCatching { prefsRepo.settingsFlow.first() }.getOrNull()
        val dao = LoheDatabase.getInstance(context.applicationContext).loheDao()
        val fallbackEp = runCatching {
            val lastId = settings?.lastPlayingEpisodeId.orEmpty()
            (if (lastId.isNotBlank()) dao.getEpisodeById(lastId) else null)
                ?: dao.getInProgressEpisodes().first().firstOrNull()
                ?: dao.getUpNextEpisodes().first().firstOrNull()
        }.getOrNull()

        provideContent {
            GlanceTheme {
                val widgetPrefs = currentState<Preferences>()
                val epTitle = widgetPrefs[WidgetKeys.EPISODE_TITLE]
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackEp?.title
                    ?: "Open Lohe to pick an episode"
                val podTitle = widgetPrefs[WidgetKeys.PODCAST_TITLE]
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackEp?.podcastTitle
                    ?: "Lohe Podcast Player"
                val isPlaying = widgetPrefs[WidgetKeys.IS_PLAYING] ?: false
                val posMs = widgetPrefs[WidgetKeys.POSITION_MS] ?: fallbackEp?.resumePositionMs ?: 0L
                val durMs = (widgetPrefs[WidgetKeys.DURATION_MS] ?: fallbackEp?.durationMs ?: 60_000L)
                    .coerceAtLeast(1L)
                val progress = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)

                LoheWidgetContent(
                    episodeTitle = epTitle,
                    podcastTitle = podTitle,
                    isPlaying = isPlaying,
                    progress = progress,
                    skipBackSec = settings?.skipBackSeconds ?: 15,
                    skipForwardSec = settings?.skipForwardSeconds ?: 30
                )
            }
        }
    }
}

@Composable
private fun LoheWidgetContent(
    episodeTitle: String,
    podcastTitle: String,
    isPlaying: Boolean,
    progress: Float,
    skipBackSec: Int,
    skipForwardSec: Int
) {
    val bgColor = ColorProvider(Color(0xFF0A0F1A))
    val borderTint = ColorProvider(Color(0xFF1E293B))
    val blueAccent = ColorProvider(Color(0xFF008EFF))
    val cyanAccent = ColorProvider(Color(0xFF00D2FF))
    val white = ColorProvider(Color.White)
    val muted = ColorProvider(Color(0xFF94A3B8))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .background(borderTint)
                    .cornerRadius(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_signal),
                    contentDescription = "Lohe Player",
                    modifier = GlanceModifier.size(22.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = podcastTitle,
                    style = TextStyle(
                        color = cyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = episodeTitle,
                    style = TextStyle(
                        color = white,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(34.dp)
                        .cornerRadius(17.dp)
                        .background(borderTint)
                        .clickable(actionRunCallback<WidgetSkipBackAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_media_rew),
                        contentDescription = "Skip back $skipBackSec seconds",
                        colorFilter = ColorFilter.tint(muted),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .cornerRadius(20.dp)
                        .background(blueAccent)
                        .clickable(actionRunCallback<WidgetTogglePlayPauseAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        colorFilter = ColorFilter.tint(white),
                        modifier = GlanceModifier.size(22.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                    modifier = GlanceModifier
                        .size(34.dp)
                        .cornerRadius(17.dp)
                        .background(borderTint)
                        .clickable(actionRunCallback<WidgetSkipForwardAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_media_ff),
                        contentDescription = "Skip forward $skipForwardSec seconds",
                        colorFilter = ColorFilter.tint(muted),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(3.dp)
                .cornerRadius(2.dp),
            color = cyanAccent,
            backgroundColor = borderTint
        )
    }
}

class WidgetTogglePlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendWidgetServiceAction(context, LohePlaybackService.ACTION_WIDGET_TOGGLE_PLAY_PAUSE)
    }
}

class WidgetSkipBackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendWidgetServiceAction(context, LohePlaybackService.ACTION_WIDGET_SKIP_BACK)
    }
}

class WidgetSkipForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendWidgetServiceAction(context, LohePlaybackService.ACTION_WIDGET_SKIP_FORWARD)
    }
}

private fun sendWidgetServiceAction(context: Context, action: String) {
    val intent = Intent(context, LohePlaybackService::class.java).setAction(action)
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }.onFailure {
        runCatching { context.startService(intent) }
    }
}

class LohePlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LohePlayerWidget()
}
