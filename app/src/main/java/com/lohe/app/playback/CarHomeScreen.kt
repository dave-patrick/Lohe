package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.media.MediaPlaybackManager
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.Template

/**
 * The root screen for the Android Automotive OS app.
 *
 * The head unit renders the actual playback UI itself from our media session,
 * so this screen only has to hand the host a token. Browsing and playback keep
 * running through [PlaybackService], the same [androidx.media3.session.MediaLibraryService]
 * that Android Auto projection already uses, which means the car and the phone
 * share one queue, one position and one history.
 */
class CarHomeScreen(carContext: CarContext) : Screen(carContext) {

    private var registered = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            registerSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            registered = false
        }
    }

    init {
        // The service may already be up (the car also browses it directly), in
        // which case the token is available immediately.
        registerSession()

        // Otherwise create it and register once it connects.
        if (!registered) {
            val intent = Intent(carContext, PlaybackService::class.java)
            runCatching { carContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
        }
    }

    /**
     * Hands the host the platform token of the live Media3 session.
     *
     * Media3 exposes an `android.media.session.MediaSession.Token`, while the
     * car library expects a [MediaSessionCompat.Token]; `fromToken` wraps it
     * without creating a second session, so both surfaces stay in sync.
     */
    private fun registerSession() {
        val token = PlaybackService.platformToken ?: return
        val manager = carContext.getCarService(CarContext.MEDIA_PLAYBACK_SERVICE)
            as? MediaPlaybackManager ?: return
        manager.registerMediaPlaybackToken(MediaSessionCompat.Token.fromToken(token))
        registered = true
    }

    override fun onGetTemplate(): Template {
        return MediaPlaybackTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Lohe")
                    .build()
            )
            .build()
    }
}
