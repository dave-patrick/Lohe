package com.example.edition

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LoheBillingState(
    val isConnected: Boolean = false,
    val isPlayStoreAvailable: Boolean = true,
    val isBusy: Boolean = false,
    val formattedPrice: String = "$9.99/year",
    val statusMessage: String = "Google Play Billing ready"
)

sealed class DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome()
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome()
    data class Unavailable(val reason: String) : DriveAuthOutcome()
}

interface ProFeatures {
    val billingState: StateFlow<LoheBillingState>

    fun startBillingConnection()
    fun endBillingConnection()
    fun purchasePro(activity: Activity)
    fun restorePurchases(silent: Boolean = false)

    suspend fun requestDriveAuthorization(): DriveAuthOutcome
    fun extractDriveTokenFromResult(resultCode: Int, data: Intent?): String?
    suspend fun uploadBackupToAppData(accessToken: String, jsonPayload: String): Result<String>
    suspend fun downloadBackupFromAppData(accessToken: String): Result<String?>

    fun schedulePeriodicSync(
        context: Context,
        intervalHours: Int = 6,
        wifiOnly: Boolean = false,
        enabled: Boolean = true
    )

    fun enqueueImmediateSync(context: Context)

    fun startOrUpdateFloatingOverlay(
        context: Context,
        sizeDp: Int,
        transparencyPct: Int,
        skipBackSec: Int,
        skipForwardSec: Int,
        episodeTitle: String,
        podcastTitle: String,
        isPlaying: Boolean
    )

    fun stopFloatingOverlay(context: Context)

    @Composable
    fun UpgradeDialog(
        isProUnlocked: Boolean,
        billingState: LoheBillingState,
        onDismiss: () -> Unit,
        onPurchasePro: () -> Unit,
        onRestorePurchases: () -> Unit
    )
}

object NoOpProFeatures : ProFeatures {
    private val _billingState = MutableStateFlow(
        LoheBillingState(
            isConnected = false,
            isPlayStoreAvailable = false,
            statusMessage = "FOSS Edition"
        )
    )
    override val billingState: StateFlow<LoheBillingState> = _billingState.asStateFlow()

    override fun startBillingConnection() = Unit
    override fun endBillingConnection() = Unit
    override fun purchasePro(activity: Activity) = Unit
    override fun restorePurchases(silent: Boolean) = Unit

    override suspend fun requestDriveAuthorization(): DriveAuthOutcome =
        DriveAuthOutcome.Unavailable("Not available in FOSS build")

    override fun extractDriveTokenFromResult(resultCode: Int, data: Intent?): String? = null

    override suspend fun uploadBackupToAppData(accessToken: String, jsonPayload: String): Result<String> =
        Result.failure(UnsupportedOperationException("Not available in FOSS build"))

    override suspend fun downloadBackupFromAppData(accessToken: String): Result<String?> =
        Result.failure(UnsupportedOperationException("Not available in FOSS build"))

    override fun schedulePeriodicSync(
        context: Context,
        intervalHours: Int,
        wifiOnly: Boolean,
        enabled: Boolean
    ) = Unit

    override fun enqueueImmediateSync(context: Context) = Unit

    override fun startOrUpdateFloatingOverlay(
        context: Context,
        sizeDp: Int,
        transparencyPct: Int,
        skipBackSec: Int,
        skipForwardSec: Int,
        episodeTitle: String,
        podcastTitle: String,
        isPlaying: Boolean
    ) = Unit

    override fun stopFloatingOverlay(context: Context) = Unit

    @Composable
    override fun UpgradeDialog(
        isProUnlocked: Boolean,
        billingState: LoheBillingState,
        onDismiss: () -> Unit,
        onPurchasePro: () -> Unit,
        onRestorePurchases: () -> Unit
    ) = Unit
}

object ProFeaturesRegistry {
    fun create(
        context: Context,
        scope: CoroutineScope,
        onEntitlementChanged: (Boolean) -> Unit,
        onStatusNotice: (String) -> Unit
    ): ProFeatures {
        if (!Edition.HAS_PRO) return NoOpProFeatures
        return runCatching {
            val clazz = Class.forName("com.example.pro.ProFeaturesImpl")
            val ctor = clazz.getConstructor(
                Context::class.java,
                CoroutineScope::class.java,
                Function1::class.java,
                Function1::class.java
            )
            ctor.newInstance(context, scope, onEntitlementChanged, onStatusNotice) as ProFeatures
        }.getOrDefault(NoOpProFeatures)
    }
}
