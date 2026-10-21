package com.lohe.app.playback

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * The session Android Automotive OS creates when it connects to
 * [CarPlaybackService].
 *
 * It returns [CarHomeScreen], which owns the playback registration (that needs
 * a [androidx.car.app.CarContext], which only [Screen] has).
 */
class CarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return CarHomeScreen(carContext)
    }
}
