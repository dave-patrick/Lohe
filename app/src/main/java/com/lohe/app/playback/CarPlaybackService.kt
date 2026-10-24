package com.lohe.app.playback

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import androidx.car.app.Session

/**
 * The Android Automotive OS entry point.
 *
 * A head unit running Android Automotive OS launches this service instead of
 * the phone's [PlaybackService]. It returns a [CarSession], which builds the
 * browse tree the car renders.
 *
 * The phone side of Android Auto (projection) continues to use
 * [PlaybackService] ([MediaLibraryService]) — that is unchanged. This service
 * is only for cars that run Android Automotive OS natively.
 */
class CarPlaybackService : CarAppService() {

    override fun onCreateSession(): Session = CarSession()

    override fun createHostValidator(): HostValidator {
        // Allow the app to connect to any host for now. In production, this
        // should be restricted to the head unit's certificate digest.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
}
