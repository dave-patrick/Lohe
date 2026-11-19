package com.lohe.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lohe.app.data.download.PodcastStorageUsage
import com.lohe.app.data.importer.ImportProgressState
import com.lohe.app.data.importer.ImportResultSummary
import com.lohe.app.data.local.LoheUserSettings
import com.lohe.app.data.local.QueueIndicatorSize
import com.lohe.app.data.local.QueueIndicatorStyle
import com.lohe.app.edition.DevTools
import com.lohe.app.edition.Edition
import com.lohe.app.edition.LoheBillingState
import com.lohe.app.ui.SettingsSubScreen
import com.lohe.app.ui.components.QueueIndicatorBadge
import com.lohe.app.ui.theme.LoheBackgroundDark
import com.lohe.app.ui.theme.LoheBrightBlueText
import com.lohe.app.ui.theme.LoheCyanAccent
import com.lohe.app.ui.theme.LoheElectricBlue
import com.lohe.app.ui.theme.LoheTextMutedDark
import com.lohe.app.ui.theme.LoheTextPrimaryDark
import com.lohe.app.ui.theme.QueuePresetColor
import com.lohe.app.ui.theme.ThemePreference
import com.lohe.app.ui.theme.parseHexColorOrDefault
import java.util.Locale

@Composable
fun SettingsContainerScreen(
    currentSubScreen: SettingsSubScreen,
    settings: LoheUserSettings,
    billingState: LoheBillingState,
    isCloudSyncBusy: Boolean,
    downloadedEpisodesCount: Int,
    storageBreakdown: Pair<Long, List<PodcastStorageUsage>> = 0L to emptyList(),
    importProgressState: ImportProgressState = ImportProgressState(),
    lastImportSummary: ImportResultSummary? = null,
    onNavigateSubScreen: (SettingsSubScreen) -> Unit,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onUpNextIndicatorEnabledChange: (Boolean) -> Unit,
    onIndicatorStyleChange: (QueueIndicatorStyle) -> Unit,
    onIndicatorSizeChange: (QueueIndicatorSize) -> Unit,
    onPresetColorChange: (QueuePresetColor) -> Unit,
    onCustomHexColorChange: (String, Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSkipDurationsChange: (Int, Int) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onContinuousPlaybackChange: (Boolean) -> Unit,
    onPlaybackExtraOptionsChange: (Boolean, Boolean, Int) -> Unit,
    onSleepShakeExtendChange: (Boolean) -> Unit = {},
    onFloatingPlayerChange: (Boolean) -> Unit,
    onFloatingAppearanceChange: (Int, Int) -> Unit,
    onAutoArchivePolicyChange: (String) -> Unit,
    onKeepStarredChange: (Boolean) -> Unit,
    onEpisodeLimitChange: (Int) -> Unit,
    onArchivePlayedNow: () -> Unit,
    onAutoDownloadChange: (Boolean, Boolean, Int) -> Unit,
    onNewEpisodeNotificationsChange: (Boolean) -> Unit = {},
    onBackgroundSyncPolicyChange: (Boolean, Int) -> Unit = { _, _ -> },
    onStorageCleanupPolicyChange: (String, Int) -> Unit = { _, _ -> },
    onClearDownloadsForPodcast: (String) -> Unit = {},
    onClearDownloads: () -> Unit,
    onRefreshFeedsNow: () -> Unit,
    onImportOpmlOrJson: () -> Unit,
    onExportOpml: () -> Unit,
    onExportJsonBackup: () -> Unit,
    onSyncToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onToggleCloudAutoBackup: (Boolean) -> Unit,
    onOpenProUpgradeModal: () -> Unit,
    onRestoreProPurchases: () -> Unit
) {
    BackHandler {
        onBack()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp
        val effectiveDetail = if (isTwoPane && currentSubScreen == SettingsSubScreen.OVERVIEW) {
            SettingsSubScreen.APPEARANCE
        } else {
            currentSubScreen
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (!isTwoPane && currentSubScreen == SettingsSubScreen.ABOUT) LoheBackgroundDark
                    else MaterialTheme.colorScheme.background
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .testTag("settings_screen_${currentSubScreen.name.lowercase()}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (!isTwoPane && currentSubScreen == SettingsSubScreen.ABOUT) 8.dp else 12.dp,
                        bottom = if (!isTwoPane && currentSubScreen == SettingsSubScreen.ABOUT) 0.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (!isTwoPane && currentSubScreen == SettingsSubScreen.ABOUT) LoheTextPrimaryDark
                        else MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTwoPane) "Settings • ${effectiveDetail.title}" else currentSubScreen.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!isTwoPane && currentSubScreen == SettingsSubScreen.ABOUT) LoheTextPrimaryDark
                    else MaterialTheme.colorScheme.onBackground
                )
            }

            if (isTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("settings_two_pane_layout"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(0.38f)) {
                        SettingsOverviewList(
                            settings = settings,
                            billingState = billingState,
                            selectedSubScreen = effectiveDetail,
                            onSelect = onNavigateSubScreen,
                            onOpenProModal = onOpenProUpgradeModal
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Box(
                        modifier = Modifier
                            .weight(0.62f)
                            .background(
                                if (effectiveDetail == SettingsSubScreen.ABOUT) LoheBackgroundDark
                                else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = if (effectiveDetail == SettingsSubScreen.ABOUT) 12.dp else 0.dp)
                    ) {
                        SettingsDetailContent(
                            subScreen = effectiveDetail,
                            settings = settings,
                            billingState = billingState,
                            isCloudSyncBusy = isCloudSyncBusy,
                            downloadedEpisodesCount = downloadedEpisodesCount,
                            storageBreakdown = storageBreakdown,
                            importProgressState = importProgressState,
                            lastImportSummary = lastImportSummary,
                            onThemeChange = onThemeChange,
                            onReducedMotionChange = onReducedMotionChange,
                            onUpNextIndicatorEnabledChange = onUpNextIndicatorEnabledChange,
                            onIndicatorStyleChange = onIndicatorStyleChange,
                            onIndicatorSizeChange = onIndicatorSizeChange,
                            onPresetColorChange = onPresetColorChange,
                            onCustomHexColorChange = onCustomHexColorChange,
                            onSpeedChange = onSpeedChange,
                            onSkipDurationsChange = onSkipDurationsChange,
                            onSkipSilenceChange = onSkipSilenceChange,
                            onVolumeBoostChange = onVolumeBoostChange,
                            onContinuousPlaybackChange = onContinuousPlaybackChange,
                            onPlaybackExtraOptionsChange = onPlaybackExtraOptionsChange,
                            onSleepShakeExtendChange = onSleepShakeExtendChange,
                            onFloatingPlayerChange = onFloatingPlayerChange,
                            onFloatingAppearanceChange = onFloatingAppearanceChange,
                            onAutoArchivePolicyChange = onAutoArchivePolicyChange,
                            onKeepStarredChange = onKeepStarredChange,
                            onEpisodeLimitChange = onEpisodeLimitChange,
                            onArchivePlayedNow = onArchivePlayedNow,
                            onAutoDownloadChange = onAutoDownloadChange,
                            onNewEpisodeNotificationsChange = onNewEpisodeNotificationsChange,
                            onBackgroundSyncPolicyChange = onBackgroundSyncPolicyChange,
                            onStorageCleanupPolicyChange = onStorageCleanupPolicyChange,
                            onClearDownloadsForPodcast = onClearDownloadsForPodcast,
                            onClearDownloads = onClearDownloads,
                            onRefreshFeedsNow = onRefreshFeedsNow,
                            onImportOpmlOrJson = onImportOpmlOrJson,
                            onExportOpml = onExportOpml,
                            onExportJsonBackup = onExportJsonBackup,
                            onSyncToGoogleDrive = onSyncToGoogleDrive,
                            onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                            onToggleCloudAutoBackup = onToggleCloudAutoBackup,
                            onOpenProUpgradeModal = onOpenProUpgradeModal,
                            onRestoreProPurchases = onRestoreProPurchases
                        )
                    }
                }
            } else {
                if (currentSubScreen == SettingsSubScreen.OVERVIEW) {
                    SettingsOverviewList(
                        settings = settings,
                        billingState = billingState,
                        selectedSubScreen = null,
                        onSelect = onNavigateSubScreen,
                        onOpenProModal = onOpenProUpgradeModal
                    )
                } else {
                    SettingsDetailContent(
                        subScreen = currentSubScreen,
                        settings = settings,
                        billingState = billingState,
                        isCloudSyncBusy = isCloudSyncBusy,
                        downloadedEpisodesCount = downloadedEpisodesCount,
                        storageBreakdown = storageBreakdown,
                        importProgressState = importProgressState,
                        lastImportSummary = lastImportSummary,
                        onThemeChange = onThemeChange,
                        onReducedMotionChange = onReducedMotionChange,
                        onUpNextIndicatorEnabledChange = onUpNextIndicatorEnabledChange,
                        onIndicatorStyleChange = onIndicatorStyleChange,
                        onIndicatorSizeChange = onIndicatorSizeChange,
                        onPresetColorChange = onPresetColorChange,
                        onCustomHexColorChange = onCustomHexColorChange,
                        onSpeedChange = onSpeedChange,
                        onSkipDurationsChange = onSkipDurationsChange,
                        onSkipSilenceChange = onSkipSilenceChange,
                        onVolumeBoostChange = onVolumeBoostChange,
                        onContinuousPlaybackChange = onContinuousPlaybackChange,
                        onPlaybackExtraOptionsChange = onPlaybackExtraOptionsChange,
                        onSleepShakeExtendChange = onSleepShakeExtendChange,
                        onFloatingPlayerChange = onFloatingPlayerChange,
                        onFloatingAppearanceChange = onFloatingAppearanceChange,
                        onAutoArchivePolicyChange = onAutoArchivePolicyChange,
                        onKeepStarredChange = onKeepStarredChange,
                        onEpisodeLimitChange = onEpisodeLimitChange,
                        onArchivePlayedNow = onArchivePlayedNow,
                        onAutoDownloadChange = onAutoDownloadChange,
                        onNewEpisodeNotificationsChange = onNewEpisodeNotificationsChange,
                        onBackgroundSyncPolicyChange = onBackgroundSyncPolicyChange,
                        onStorageCleanupPolicyChange = onStorageCleanupPolicyChange,
                        onClearDownloadsForPodcast = onClearDownloadsForPodcast,
                        onClearDownloads = onClearDownloads,
                        onRefreshFeedsNow = onRefreshFeedsNow,
                        onImportOpmlOrJson = onImportOpmlOrJson,
                        onExportOpml = onExportOpml,
                        onExportJsonBackup = onExportJsonBackup,
                        onSyncToGoogleDrive = onSyncToGoogleDrive,
                        onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                        onToggleCloudAutoBackup = onToggleCloudAutoBackup,
                        onOpenProUpgradeModal = onOpenProUpgradeModal,
                        onRestoreProPurchases = onRestoreProPurchases
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailContent(
    subScreen: SettingsSubScreen,
    settings: LoheUserSettings,
    billingState: LoheBillingState,
    isCloudSyncBusy: Boolean,
    downloadedEpisodesCount: Int,
    storageBreakdown: Pair<Long, List<PodcastStorageUsage>>,
    importProgressState: ImportProgressState,
    lastImportSummary: ImportResultSummary?,
    onThemeChange: (ThemePreference) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onUpNextIndicatorEnabledChange: (Boolean) -> Unit,
    onIndicatorStyleChange: (QueueIndicatorStyle) -> Unit,
    onIndicatorSizeChange: (QueueIndicatorSize) -> Unit,
    onPresetColorChange: (QueuePresetColor) -> Unit,
    onCustomHexColorChange: (String, Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSkipDurationsChange: (Int, Int) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onContinuousPlaybackChange: (Boolean) -> Unit,
    onPlaybackExtraOptionsChange: (Boolean, Boolean, Int) -> Unit,
    onSleepShakeExtendChange: (Boolean) -> Unit,
    onFloatingPlayerChange: (Boolean) -> Unit,
    onFloatingAppearanceChange: (Int, Int) -> Unit,
    onAutoArchivePolicyChange: (String) -> Unit,
    onKeepStarredChange: (Boolean) -> Unit,
    onEpisodeLimitChange: (Int) -> Unit,
    onArchivePlayedNow: () -> Unit,
    onAutoDownloadChange: (Boolean, Boolean, Int) -> Unit,
    onNewEpisodeNotificationsChange: (Boolean) -> Unit,
    onBackgroundSyncPolicyChange: (Boolean, Int) -> Unit,
    onStorageCleanupPolicyChange: (String, Int) -> Unit,
    onClearDownloadsForPodcast: (String) -> Unit,
    onClearDownloads: () -> Unit,
    onRefreshFeedsNow: () -> Unit,
    onImportOpmlOrJson: () -> Unit,
    onExportOpml: () -> Unit,
    onExportJsonBackup: () -> Unit,
    onSyncToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onToggleCloudAutoBackup: (Boolean) -> Unit,
    onOpenProUpgradeModal: () -> Unit,
    onRestoreProPurchases: () -> Unit
) {
    when (subScreen) {
        SettingsSubScreen.OVERVIEW -> Unit

        SettingsSubScreen.APPEARANCE -> AppearanceSettingsSubScreen(
            settings = settings,
            onThemeChange = onThemeChange,
            onReducedMotionChange = onReducedMotionChange,
            onUpNextIndicatorEnabledChange = onUpNextIndicatorEnabledChange,
            onIndicatorStyleChange = onIndicatorStyleChange,
            onIndicatorSizeChange = onIndicatorSizeChange,
            onPresetColorChange = onPresetColorChange,
            onCustomHexColorChange = onCustomHexColorChange,
            onOpenProModal = onOpenProUpgradeModal
        )

        SettingsSubScreen.PLAYBACK -> PlaybackSettingsSubScreen(
            settings = settings,
            onSpeedChange = onSpeedChange,
            onSkipDurationsChange = onSkipDurationsChange,
            onSkipSilenceChange = onSkipSilenceChange,
            onVolumeBoostChange = onVolumeBoostChange,
            onContinuousPlaybackChange = onContinuousPlaybackChange,
            onPlaybackExtraOptionsChange = onPlaybackExtraOptionsChange,
            onSleepShakeExtendChange = onSleepShakeExtendChange
        )

        SettingsSubScreen.FLOATING_PLAYER -> {
            if (Edition.HAS_PRO) {
                FloatingPlayerSettingsSubScreen(
                    settings = settings,
                    onFloatingPlayerChange = onFloatingPlayerChange,
                    onFloatingAppearanceChange = onFloatingAppearanceChange,
                    onOpenProModal = onOpenProUpgradeModal
                )
            }
        }

        SettingsSubScreen.ARCHIVING -> ArchivingSettingsSubScreen(
            settings = settings,
            onAutoArchivePolicyChange = onAutoArchivePolicyChange,
            onKeepStarredChange = onKeepStarredChange,
            onEpisodeLimitChange = onEpisodeLimitChange,
            onArchivePlayedNow = onArchivePlayedNow
        )

        SettingsSubScreen.DOWNLOADS -> DownloadsSettingsSubScreen(
            settings = settings,
            downloadedCount = downloadedEpisodesCount,
            storageBreakdown = storageBreakdown,
            onAutoDownloadChange = onAutoDownloadChange,
            onNewEpisodeNotificationsChange = onNewEpisodeNotificationsChange,
            onBackgroundSyncPolicyChange = onBackgroundSyncPolicyChange,
            onStorageCleanupPolicyChange = onStorageCleanupPolicyChange,
            onClearDownloadsForPodcast = onClearDownloadsForPodcast,
            onClearDownloads = onClearDownloads,
            onRefreshFeedsNow = onRefreshFeedsNow,
            onImportOpmlOrJson = onImportOpmlOrJson,
            onExportOpml = onExportOpml,
            onExportJsonBackup = onExportJsonBackup
        )

        SettingsSubScreen.IMPORT_FROM_APP -> ImportFromAnotherAppSettingsSubScreen(
            importProgressState = importProgressState,
            lastImportSummary = lastImportSummary,
            onPickImportFile = onImportOpmlOrJson
        )

        SettingsSubScreen.CLOUD_SYNC -> {
            if (Edition.HAS_PRO) {
                CloudSyncSettingsSubScreen(
                    settings = settings,
                    isCloudSyncBusy = isCloudSyncBusy,
                    onSyncToGoogleDrive = onSyncToGoogleDrive,
                    onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                    onToggleCloudAutoBackup = onToggleCloudAutoBackup,
                    onImportFromDriveSaf = onImportOpmlOrJson,
                    onExportToDriveSaf = onExportJsonBackup,
                    onOpenProModal = onOpenProUpgradeModal
                )
            }
        }

        SettingsSubScreen.ABOUT -> AboutSettingsSubScreen(
            settings = settings,
            billingState = billingState,
            onOpenProModal = onOpenProUpgradeModal,
            onRestorePurchases = onRestoreProPurchases
        )

        SettingsSubScreen.DIAGNOSTICS -> {
            if (DevTools.current.isAvailable) {
                DevTools.current.DiagnosticsSettingsScreen()
            }
        }
    }
}

@Composable
private fun SettingsOverviewList(
    settings: LoheUserSettings,
    billingState: LoheBillingState,
    selectedSubScreen: SettingsSubScreen?,
    onSelect: (SettingsSubScreen) -> Unit,
    onOpenProModal: () -> Unit
) {
    val entries: List<Pair<SettingsSubScreen, ImageVector>> = buildList {
        add(SettingsSubScreen.APPEARANCE to Icons.Filled.Palette)
        add(SettingsSubScreen.PLAYBACK to Icons.Filled.PlayCircleFilled)
        add(SettingsSubScreen.FLOATING_PLAYER to Icons.Filled.PictureInPictureAlt)
        add(SettingsSubScreen.ARCHIVING to Icons.Filled.Archive)
        add(SettingsSubScreen.DOWNLOADS to Icons.Filled.Download)
        add(SettingsSubScreen.IMPORT_FROM_APP to Icons.Filled.FileOpen)
        add(SettingsSubScreen.CLOUD_SYNC to Icons.Filled.CloudSync)
        add(SettingsSubScreen.ABOUT to Icons.Filled.Info)
        if (DevTools.current.isAvailable) {
            add(SettingsSubScreen.DIAGNOSTICS to Icons.Filled.BugReport)
        }
    }.filter { (sub, _) -> Edition.HAS_PRO || !sub.isProFeatured }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("settings_overview_list")
    ) {
        if (Edition.HAS_PRO) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LoheElectricBlue, RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenProModal)
                        .testTag("settings_pro_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = LoheCyanAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (settings.isProUnlocked) "Lohe Pro Bundle Active"
                                    else "Lohe Pro Bundle (${billingState.formattedPrice})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.isProUnlocked) "Floating player, extra indicators, custom hex & Google Drive Sync unlocked"
                                    else "Unlock Floating Player, custom indicator styles, hex colors & Google Drive Sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = LoheCyanAccent
                        )
                    }
                }
            }
        }

        items(entries.size) { idx ->
            val (sub, icon) = entries[idx]
            val isRowSelected = selectedSubScreen == sub
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (isRowSelected) 1.5.dp else 1.dp,
                        if (isRowSelected) LoheElectricBlue else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(sub) }
                    .testTag("settings_item_${sub.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = LoheElectricBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sub.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (Edition.HAS_PRO && sub.isProFeatured) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ProBadgePill(unlocked = settings.isProUnlocked)
                                }
                            }
                            Text(
                                text = sub.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProBadgePill(unlocked: Boolean) {
    if (!Edition.HAS_PRO) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (unlocked) LoheCyanAccent.copy(alpha = 0.2f) else LoheElectricBlue.copy(alpha = 0.2f))
            .border(1.dp, if (unlocked) LoheCyanAccent else LoheElectricBlue, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = if (unlocked) "PRO ACTIVE" else "PRO",
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) LoheCyanAccent else LoheElectricBlue
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsSubScreen(
    settings: LoheUserSettings,
    onThemeChange: (ThemePreference) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onUpNextIndicatorEnabledChange: (Boolean) -> Unit,
    onIndicatorStyleChange: (QueueIndicatorStyle) -> Unit,
    onIndicatorSizeChange: (QueueIndicatorSize) -> Unit,
    onPresetColorChange: (QueuePresetColor) -> Unit,
    onCustomHexColorChange: (String, Boolean) -> Unit,
    onOpenProModal: () -> Unit
) {
    var hexInput by remember(settings.customHexColor) { mutableStateOf(settings.customHexColor) }

    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val pillBgUnselected = if (isDark) com.lohe.app.ui.theme.LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val pillBorder = if (isDark) com.lohe.app.ui.theme.LoheBorderDark else MaterialTheme.colorScheme.outline

    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.testTag("appearance_settings_list")
    ) {
        // Dark & Light Full-Width Radio Rows (matches indicator-appearance68_a37c0831.png)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                ThemePreference.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(mode) }
                            .padding(vertical = 10.dp)
                            .testTag("theme_mode_${mode.name.lowercase()}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        RadioButton(
                            selected = settings.themePreference == mode,
                            onClick = { onThemeChange(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = LoheElectricBlue,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = pillBorder)
            }
        }

        // Up Next indicator section (flat layout matching indicator-appearance68_a37c0831.png)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Up Next indicator",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Mark episodes that are currently in Up Next.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.upNextIndicatorEnabled,
                        onCheckedChange = onUpNextIndicatorEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LoheElectricBlue
                        ),
                        modifier = Modifier.testTag("up_next_indicator_switch")
                    )
                }

                // Bare inline preview row matching screenshot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QueueIndicatorBadge(settings = settings, queuePosition = 1)
                    Text(
                        text = "Episode title",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Style",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueueIndicatorStyle.entries
                            .filter { Edition.HAS_PRO || !it.isPro }
                            .forEach { style ->
                                val selected = settings.queueIndicatorStyle == style
                                LoheSegmentPillButton(
                                    text = style.label,
                                    selected = selected,
                                    locked = Edition.HAS_PRO && style.isPro && !settings.isProUnlocked,
                                    unselectedBg = pillBgUnselected,
                                    borderColor = pillBorder,
                                    onClick = { onIndicatorStyleChange(style) },
                                    modifier = Modifier.testTag("indicator_style_${style.name.lowercase()}")
                                )
                            }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueuePresetColor.entries.forEach { preset ->
                            val isSelected = !settings.useCustomHexColor && settings.queuePresetColor == preset
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) LoheElectricBlue else pillBgUnselected)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) LoheElectricBlue else pillBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onPresetColorChange(preset) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                    .testTag("preset_color_${preset.name.lowercase()}")
                            ) {
                                if (preset != QueuePresetColor.BLUE &&
                                    preset != QueuePresetColor.TEAL &&
                                    preset != QueuePresetColor.AMBER
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(preset.color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QueueIndicatorSize.entries.forEach { sz ->
                            val selected = settings.queueIndicatorSize == sz
                            LoheSegmentPillButton(
                                text = sz.label,
                                selected = selected,
                                locked = false,
                                unselectedBg = pillBgUnselected,
                                borderColor = pillBorder,
                                onClick = { onIndicatorSizeChange(sz) },
                                modifier = Modifier.testTag("indicator_size_${sz.name.lowercase()}")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = pillBorder)
            }
        }

        // Custom Hex Color Picker (Pro)
        if (Edition.HAS_PRO) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom Hex Color", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        ProBadgePill(unlocked = settings.isProUnlocked)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseHexColorOrDefault(hexInput))
                                .border(1.dp, pillBorder, CircleShape)
                        )
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it },
                            label = { Text("#RRGGBB") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_hex_input")
                        )
                        Button(
                            onClick = {
                                if (settings.isProUnlocked) onCustomHexColorChange(hexInput, true)
                                else onOpenProModal()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(if (settings.isProUnlocked) "Apply" else "Pro", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = pillBorder)
                }
            }
        }

        // Reduced Motion toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reduced Motion",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.reducedMotion,
                    onCheckedChange = onReducedMotionChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LoheElectricBlue
                    ),
                    modifier = Modifier.testTag("reduced_motion_switch")
                )
            }
        }
    }
}

@Composable
private fun LoheSegmentPillButton(
    text: String,
    selected: Boolean,
    locked: Boolean,
    unselectedBg: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LoheElectricBlue else unselectedBg)
            .border(
                width = 1.dp,
                color = if (selected) LoheElectricBlue else borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Pro",
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSettingsSubScreen(
    settings: LoheUserSettings,
    onSpeedChange: (Float) -> Unit,
    onSkipDurationsChange: (Int, Int) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onVolumeBoostChange: (Int) -> Unit,
    onContinuousPlaybackChange: (Boolean) -> Unit,
    onPlaybackExtraOptionsChange: (Boolean, Boolean, Int) -> Unit,
    onSleepShakeExtendChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard(title = "Default Playback Speed") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f).forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.playbackSpeed - speed) < 0.03f,
                            onClick = { onSpeedChange(speed) },
                            label = { Text("${speed}x") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LoheElectricBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Skip Back, Forward & Smart Rewind") {
                Text("Skip Back:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30).forEach { sec ->
                        FilterChip(
                            selected = settings.skipBackSeconds == sec,
                            onClick = { onSkipDurationsChange(sec, settings.skipForwardSeconds) },
                            label = { Text("-${sec}s") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Skip Forward:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { sec ->
                        FilterChip(
                            selected = settings.skipForwardSeconds == sec,
                            onClick = { onSkipDurationsChange(settings.skipBackSeconds, sec) },
                            label = { Text("+${sec}s") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Auto-Rewind on Resume:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 5, 10).forEach { sec ->
                        FilterChip(
                            selected = settings.autoRewindSeconds == sec,
                            onClick = {
                                onPlaybackExtraOptionsChange(
                                    settings.markPlayedWhenFinished,
                                    settings.resumeOnHeadsetConnect,
                                    sec
                                )
                            },
                            label = { Text(if (sec == 0) "Off" else "${sec}s") }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Volume Boost & Skip Silence (Free)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip Silence", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Trims dead air in spoken-word audio via ExoPlayer DSP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.skipSilenceEnabled,
                        onCheckedChange = onSkipSilenceChange,
                        modifier = Modifier.testTag("playback_skip_silence_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Volume Boost (Android LoudnessEnhancer):", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 3 to "Mild (+3dB)", 6 to "Voice (+6dB)", 10 to "Max (+10dB)").forEach { (db, label) ->
                        FilterChip(
                            selected = settings.volumeBoostDb == db,
                            onClick = { onVolumeBoostChange(db) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LoheElectricBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Queue Automation & Battery Setup") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Continuous Queue Playback", style = MaterialTheme.typography.titleMedium)
                        Text("Auto-advance to next episode in Up Next", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.continuousPlayback, onCheckedChange = onContinuousPlaybackChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mark played when finished", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = settings.markPlayedWhenFinished,
                        onCheckedChange = {
                            onPlaybackExtraOptionsChange(it, settings.resumeOnHeadsetConnect, settings.autoRewindSeconds)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resume on headset connect", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = settings.resumeOnHeadsetConnect,
                        onCheckedChange = {
                            onPlaybackExtraOptionsChange(settings.markPlayedWhenFinished, it, settings.autoRewindSeconds)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shake Sleep Timer (+5m)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Shake phone when a sleep timer is counting down to add 5 min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.sleepShakeToExtendEnabled,
                        onCheckedChange = onSleepShakeExtendChange,
                        modifier = Modifier.testTag("playback_sleep_shake_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("battery_setup_button")
                ) {
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = LoheCyanAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Unrestricted Background Audio / Battery Setup")
                }
            }
        }

        item {
            val totalMins14d = settings.dailyListeningMinutes.sum()
            val hrs = totalMins14d / 60
            val remMins = totalMins14d % 60
            val savedMins = settings.totalTimeSavedMs / 60_000L
            var streak = 0
            for (i in settings.dailyListeningMinutes.lastIndex downTo 0) {
                if (settings.dailyListeningMinutes[i] > 0) streak++ else break
            }
            SettingsCard(title = "Listening Stats & DSP Savings") {
                Column(modifier = Modifier.testTag("listening_stats_summary_card")) {
                    Text(
                        text = "${hrs}h ${remMins}m listened in last 14 days • $streak-day streak",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LoheCyanAccent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${savedMins} min reclaimed using Skip Silence & Variable Speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingPlayerSettingsSubScreen(
    settings: LoheUserSettings,
    onFloatingPlayerChange: (Boolean) -> Unit,
    onFloatingAppearanceChange: (Int, Int) -> Unit,
    onOpenProModal: () -> Unit
) {
    val context = LocalContext.current
    val hasOverlayPermission = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard(title = "Floating & Cross-App Overlay Player (Pro Bundle)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable Floating Mini-Player", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            ProBadgePill(unlocked = settings.isProUnlocked)
                        }
                        Text(
                            "Floats a draggable mini-player pill inside Lohe and across other apps when System Overlay permission is granted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.floatingMiniPlayerEnabled,
                        onCheckedChange = { enabled ->
                            if (!settings.isProUnlocked && enabled) onOpenProModal()
                            else onFloatingPlayerChange(enabled)
                        },
                        modifier = Modifier.testTag("floating_player_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Button Size:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(48, 56, 72, 88).forEach { dp ->
                        FilterChip(
                            selected = settings.floatingButtonSizeDp == dp,
                            onClick = { onFloatingAppearanceChange(dp, settings.floatingTransparencyPct) },
                            label = { Text("${dp}dp") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Transparency:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 25, 50, 75).forEach { pct ->
                        FilterChip(
                            selected = settings.floatingTransparencyPct == pct,
                            onClick = { onFloatingAppearanceChange(settings.floatingButtonSizeDp, pct) },
                            label = { Text("$pct%") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (hasOverlayPermission) {
                        "System Overlay Permission: Granted • Cross-app overlay activates automatically when leaving Lohe."
                    } else {
                        "System Overlay Permission: Not granted • Tap below to allow Lohe to display above other apps."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasOverlayPermission) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("floating_overlay_permission_button")
                ) {
                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = LoheCyanAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasOverlayPermission) "Manage System Overlay Permission" else "Grant System Overlay Permission")
                }

                if (!settings.isProUnlocked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenProModal,
                        colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Unlock Lohe Pro Bundle", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivingSettingsSubScreen(
    settings: LoheUserSettings,
    onAutoArchivePolicyChange: (String) -> Unit,
    onKeepStarredChange: (Boolean) -> Unit,
    onEpisodeLimitChange: (Int) -> Unit,
    onArchivePlayedNow: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard(title = "Auto-Archive Played Episodes") {
                val policies = listOf(
                    "IMMEDIATELY" to "Immediately after completion",
                    "AFTER_24H" to "After 24 hours",
                    "AFTER_7D" to "After 7 days",
                    "NEVER" to "Never auto-archive"
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    policies.forEach { (key, label) ->
                        FilterChip(
                            selected = settings.autoArchivePolicy == key,
                            onClick = { onAutoArchivePolicyChange(key) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LoheElectricBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Retention & Starred Protection") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Protect Bookmarked / Starred Episodes", style = MaterialTheme.typography.titleMedium)
                        Text("Never auto-archive episodes you have bookmarked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.keepStarredExcluded, onCheckedChange = onKeepStarredChange)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Episode Limit Per Podcast:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 50, 100).forEach { limit ->
                        FilterChip(
                            selected = settings.episodeLimitPerShow == limit,
                            onClick = { onEpisodeLimitChange(limit) },
                            label = { Text("$limit eps") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onArchivePlayedNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Archive, contentDescription = null, tint = LoheCyanAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Archive All Played Non-Starred Now")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsSettingsSubScreen(
    settings: LoheUserSettings,
    downloadedCount: Int,
    storageBreakdown: Pair<Long, List<PodcastStorageUsage>>,
    onAutoDownloadChange: (Boolean, Boolean, Int) -> Unit,
    onNewEpisodeNotificationsChange: (Boolean) -> Unit,
    onBackgroundSyncPolicyChange: (Boolean, Int) -> Unit,
    onStorageCleanupPolicyChange: (String, Int) -> Unit,
    onClearDownloadsForPodcast: (String) -> Unit,
    onClearDownloads: () -> Unit,
    onRefreshFeedsNow: () -> Unit,
    onImportOpmlOrJson: () -> Unit,
    onExportOpml: () -> Unit,
    onExportJsonBackup: () -> Unit
) {
    val totalMbFormatted = remember(storageBreakdown.first) {
        String.format(Locale.US, "%.1f MB", storageBreakdown.first / (1024f * 1024f))
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("downloads_settings_list")
    ) {
        item {
            SettingsCard(title = "On-Device Library Import / Export (SAF)") {
                Text(
                    text = "Personal subscriptions are never bundled in APK assets. Import your OPML (.opml/.xml) or Lohe JSON backup directly from device storage or Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onImportOpmlOrJson,
                    colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("downloads_import_opml_json_button")
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Import OPML (.opml/.xml) or JSON Backup", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportOpml,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("downloads_export_opml_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = LoheCyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export OPML")
                    }
                    OutlinedButton(
                        onClick = onExportJsonBackup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("downloads_export_json_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = LoheElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export JSON")
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Background Sync & New Episode Alerts") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = LoheCyanAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Episode Notifications", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "Notify when subscribed podcasts publish new episodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.newEpisodeNotificationsEnabled,
                        onCheckedChange = onNewEpisodeNotificationsChange,
                        modifier = Modifier.testTag("new_episode_notifications_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background RSS Sync", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Battery-aware WorkManager feed refresh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.backgroundSyncEnabled,
                        onCheckedChange = { onBackgroundSyncPolicyChange(it, settings.backgroundSyncIntervalHours) },
                        modifier = Modifier.testTag("background_sync_switch")
                    )
                }

                if (settings.backgroundSyncEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sync Interval:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 12, 24).forEach { hrs ->
                            FilterChip(
                                selected = settings.backgroundSyncIntervalHours == hrs,
                                onClick = { onBackgroundSyncPolicyChange(true, hrs) },
                                label = { Text("Every ${hrs}h") },
                                modifier = Modifier.testTag("sync_interval_$hrs")
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Automatic Offline Caching ($downloadedCount episodes cached)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-download on unmetered Wi-Fi", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = settings.autoDownloadWifi,
                        onCheckedChange = { onAutoDownloadChange(it, settings.autoDownloadUpNext, settings.autoDownloadQueueDepth) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-download episodes added to Up Next", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = settings.autoDownloadUpNext,
                        onCheckedChange = { onAutoDownloadChange(settings.autoDownloadWifi, it, settings.autoDownloadQueueDepth) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Queue Download Depth:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10, 20).forEach { depth ->
                        FilterChip(
                            selected = settings.autoDownloadQueueDepth == depth,
                            onClick = { onAutoDownloadChange(settings.autoDownloadWifi, settings.autoDownloadUpNext, depth) },
                            label = { Text("Top $depth") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Auto-Delete Played Downloads:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "NEVER" to "Never",
                        "IMMEDIATELY" to "Immediately",
                        "AFTER_24H" to "After 24h",
                        "AFTER_7D" to "After 7d"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = settings.autoDeletePlayedPolicy == key,
                            onClick = { onStorageCleanupPolicyChange(key, settings.maxCacheSizeMb) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Offline Storage Cache Cap:", style = MaterialTheme.typography.labelMedium, color = LoheCyanAccent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Unlimited", 500 to "500 MB", 1024 to "1 GB", 2048 to "2 GB").forEach { (mb, label) ->
                        FilterChip(
                            selected = settings.maxCacheSizeMb == mb,
                            onClick = { onStorageCleanupPolicyChange(settings.autoDeletePlayedPolicy, mb) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRefreshFeedsNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("downloads_refresh_feeds_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = LoheCyanAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh Subscribed Feeds Now")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onClearDownloads,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Clear All Cached Audio Downloads")
                }
            }
        }

        item {
            SettingsCard(title = "Storage Breakdown • $totalMbFormatted") {
                Column(modifier = Modifier.testTag("storage_breakdown_card")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = LoheCyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (storageBreakdown.second.isEmpty()) "0 episodes cached on device"
                            else "${storageBreakdown.second.sumOf { it.episodeCount }} offline files ($totalMbFormatted)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    storageBreakdown.second.forEach { usage ->
                        val showMb = String.format(Locale.US, "%.1f MB", usage.totalBytes / (1024f * 1024f))
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(usage.podcastTitle, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${usage.episodeCount} episode(s) • $showMb",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onClearDownloadsForPodcast(usage.podcastId) }) {
                                Text("Clear", color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportFromAnotherAppSettingsSubScreen(
    importProgressState: ImportProgressState,
    lastImportSummary: ImportResultSummary?,
    onPickImportFile: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("import_from_app_settings_list")
    ) {
        if (importProgressState.isImporting) {
            item {
                SettingsCard(title = "Importing Library Data...") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_progress_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = LoheCyanAccent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = importProgressState.stageMessage.ifBlank { "Running database transaction..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        LinearProgressIndicator(
                            progress = { importProgressState.progressFraction.coerceIn(0f, 1f) },
                            color = LoheElectricBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .testTag("import_progress_bar")
                        )
                    }
                }
            }
        }

        if (lastImportSummary != null) {
            item {
                SettingsCard(title = "Last Import Summary • ${lastImportSummary.format.label}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_summary_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Shows added",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${lastImportSummary.showsAdded}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LoheCyanAccent,
                                modifier = Modifier.testTag("import_summary_shows_added")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Episodes updated",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${lastImportSummary.episodesUpdated}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LoheElectricBlue,
                                modifier = Modifier.testTag("import_summary_episodes_updated")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Items skipped",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${lastImportSummary.itemsSkipped}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("import_summary_items_skipped")
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Import via System File Picker (SAF)") {
                Text(
                    text = "Select an export file from your device or cloud drive using Android's Storage Access Framework. No broad storage permissions are required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onPickImportFile,
                    enabled = !importProgressState.isImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("import_from_app_pick_file_button")
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Backup or OPML File", color = Color.White)
                }
            }
        }

        item {
            SettingsCard(title = "Supported Import Formats") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImportFormatOptionRow(
                        title = "1. OPML Subscriptions (.opml / .xml)",
                        description = "Standard OPML subscription lists from any podcast app.",
                        buttonLabel = "Import OPML",
                        testTag = "import_format_opml_button",
                        enabled = !importProgressState.isImporting,
                        onClick = onPickImportFile
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ImportFormatOptionRow(
                        title = "2. AntennaPod Database Export (.db)",
                        description = "SQLite database export including subscriptions, played state, in-progress positions, and played dates.",
                        buttonLabel = "Import .db",
                        testTag = "import_format_antennapod_button",
                        enabled = !importProgressState.isImporting,
                        onClick = onPickImportFile
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ImportFormatOptionRow(
                        title = "3. Podcast Addict Backup (.backup)",
                        description = "Podcast Addict .backup ZIP archive containing its SQLite database with subscriptions, played state, and resume positions.",
                        buttonLabel = "Import .backup",
                        testTag = "import_format_podcast_addict_button",
                        enabled = !importProgressState.isImporting,
                        onClick = onPickImportFile
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ImportFormatOptionRow(
                        title = "4. Lohe JSON Backup (.json)",
                        description = "Complete Lohe library backup with subscriptions, played state, positions, and custom playlists.",
                        buttonLabel = "Import .json",
                        testTag = "import_format_lohe_json_button",
                        enabled = !importProgressState.isImporting,
                        onClick = onPickImportFile
                    )
                }
            }
        }

        item {
            SettingsCard(title = "Episode Matching & Forward-Only Merge") {
                Text(
                    text = "• Episodes are matched by GUID first, then by enclosure URL, and finally by title plus publish date.\n" +
                        "• Imports only move playback state forward: Lohe never marks a played episode unplayed and never lowers a saved resume position.\n" +
                        "• Positions within the last 10 seconds or past 95% of episode duration automatically count as played.\n" +
                        "• All changes execute atomically inside a single database transaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("import_matching_rules_text")
                )
            }
        }

        item {
            SettingsCard(title = "Pocket Casts, Apple Podcasts & Spotify") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_opml_only_help_card"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Pocket Casts, Apple Podcasts and Spotify can only export subscriptions via OPML. They do not include episode listening history or playback positions in their exports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("import_opml_only_help_text")
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportFormatOptionRow(
    title: String,
    description: String,
    buttonLabel: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .height(48.dp)
                .testTag(testTag)
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun CloudSyncSettingsSubScreen(
    settings: LoheUserSettings,
    isCloudSyncBusy: Boolean,
    onSyncToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onToggleCloudAutoBackup: (Boolean) -> Unit,
    onImportFromDriveSaf: () -> Unit,
    onExportToDriveSaf: () -> Unit,
    onOpenProModal: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard(title = "Google Drive & Cloud Sync (Pro Bundle)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProBadgePill(unlocked = settings.isProUnlocked)
                    Spacer(Modifier.width(8.dp))
                    Text("App-Private Google Drive AppData Sync", style = MaterialTheme.typography.titleSmall, color = LoheCyanAccent)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Backs up your subscriptions, Up Next queue, playlists, and playback progress directly to your hidden Google Drive AppData folder using OAuth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Status: ${settings.lastCloudSyncSummary}", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Background Backup", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Backs up library changes periodically via WorkManager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.cloudSyncAutoBackup,
                        onCheckedChange = onToggleCloudAutoBackup,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LoheElectricBlue
                        ),
                        modifier = Modifier.testTag("cloud_sync_auto_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!settings.isProUnlocked) {
                    Button(
                        onClick = onOpenProModal,
                        colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cloud_sync_unlock_pro_button")
                    ) {
                        Text("Unlock Google Drive Cloud Sync", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onSyncToGoogleDrive,
                        enabled = !isCloudSyncBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cloud_sync_upload_button")
                    ) {
                        if (isCloudSyncBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing with Google Drive...", color = Color.White)
                        } else {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync to Google Drive Now", color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRestoreFromGoogleDrive,
                        enabled = !isCloudSyncBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cloud_sync_restore_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = LoheCyanAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Restore from Google Drive")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Manual File Backup (Always Free via Android SAF):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onImportFromDriveSaf, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("SAF Drive Import")
                    }
                    OutlinedButton(onClick = onExportToDriveSaf, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("SAF Drive Export")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
