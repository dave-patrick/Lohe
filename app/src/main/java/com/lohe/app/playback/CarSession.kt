package com.lohe.app.playback

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
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

    /**
     * The system media card starts the car activity with
     * [SHOW_MEDIA_PLAYBACK] when the user taps it. The activity forwards the
     * intent to this session, so the playback screen is what has to be on top
     * when it arrives.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != SHOW_MEDIA_PLAYBACK) {
            return
        }
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        if (screenManager.top is CarHomeScreen) {
            return
        }
        screenManager.push(CarHomeScreen(carContext))
    }

    private companion object {
        /**
         * The action the system media card uses to open the playback surface.
         *
         * The library only published the constant as
         * `MediaConstants.ACTION_SHOW_MEDIA_PLAYBACK` in car-app 1.9, and this
         * app is on 1.7, so the action is spelled out here.
         */
        const val SHOW_MEDIA_PLAYBACK = "androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK"
    }
}
