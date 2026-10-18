package com.lohe.app.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lohe.app.R
import com.lohe.app.data.DiscoveryViewModel
import com.lohe.app.data.DiscoveryViewModelFactory
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.EpisodesViewModelFactory
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.QueueViewModelFactory
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.SubscriptionsViewModel
import com.lohe.app.data.SubscriptionsViewModelFactory
import com.lohe.app.playback.rememberPlayerConnection
import com.lohe.app.playback.rememberTransportState
import com.lohe.app.ui.player.NowPlayingBar
import com.lohe.app.ui.player.PlayerScreen
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.PlaybackSettings
import com.lohe.app.ui.theme.SettingsStore
import com.lohe.app.utils.FileScanner
import com.lohe.app.utils.LocalFileManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    PODCASTS("podcasts", "Podcasts", Icons.Filled.LibraryMusic),
    DISCOVER("discover", "Discover", Icons.Filled.Explore),
    QUEUE("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
    PLAYER("player", "Now Playing", Icons.Filled.PlayArrow),
    STORAGE("storage", "Storage", Icons.Filled.Storage),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/** Detail routes carry arguments and never appear in the bottom bar. */
private object Routes {
    const val EPISODES = "podcast/{uuid}/episodes"
    const val ABOUT = "about"
    fun episodes(uuid: String) = "podcast/$uuid/episodes"
}

/** What the player should be showing right now. */
private data class NowPlaying(
    val title: String,
    val uri: String,
    val showTitle: String,
    val episodeId: String,
    val artworkUrl: String?,
    /**
     * Length from the feed, in seconds.
     *
     * ExoPlayer reports [androidx.media3.common.C.TIME_UNSET] until the media
     * is prepared, so the scrubber would otherwise read 00:00 for the first
     * second or two of every episode even though the feed told us the length.
     */
    val knownDurationSeconds: Int? = null,
)

/** Destinations reachable from the bottom bar; the rest are push-only. */
private val BAR_DESTINATIONS = Dest.entries.filter {
    it != Dest.PLAYER && it != Dest.STORAGE && it != Dest.SETTINGS
}

@Composable
fun LoheNavHost(
    appearance: Appearance,
    onToggleAppearance: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Picks and exports flow through state rather than being handled inside the
    // activity-result callbacks: the ViewModel owns the import, and suspend
    // work belongs in a lifecycle-owned effect rather than in a callback that
    // may fire after the composable has moved on.
    var pendingTree by remember { mutableStateOf<Uri?>(null) }
    var pendingOpml by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var nowPlaying by remember { mutableStateOf<NowPlaying?>(null) }

    val podcastsViewModel: SubscriptionsViewModel = viewModel(
        factory = SubscriptionsViewModelFactory(),
    )

    // Shared by Home, Episodes, and Queue so a "queue" action from any of them
    // is reflected in all of them immediately.
    val queueViewModel: QueueViewModel = viewModel(factory = QueueViewModelFactory())
    val discoveryViewModel: DiscoveryViewModel = viewModel(
        factory = DiscoveryViewModelFactory(),
    )

    // One connection for the whole nav host: the bar and the player screen must
    // observe the same controller, or the bar would sit idle while the player
    // screen plays.
    val playerConnection = rememberPlayerConnection()
    val transport = rememberTransportState(playerConnection)

    // One settings stream for the whole nav host so the player and the settings
    // screen cannot disagree about a preference.
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val playbackSettings by remember(settingsStore) {
        settingsStore.settings
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                PlaybackSettings(appearance = appearance),
            )
    }.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        pendingTree = uri
    }

    val opmlPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                importMessage = "Couldn't read that file."
            } else {
                pendingOpml = bytes
            }
        }
    }

    LaunchedEffect(pendingOpml) {
        val bytes = pendingOpml ?: return@LaunchedEffect
        pendingOpml = null
        podcastsViewModel.importOpml(String(bytes, Charsets.UTF_8))
    }

    val exportWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        val document = pendingExport
        pendingExport = null
        if (uri == null || document == null) return@rememberLauncherForActivityResult
        importMessage = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(document.toByteArray(Charsets.UTF_8))
            } ?: error("no output stream")
        }.fold(
            onSuccess = { "Exported subscriptions." },
            onFailure = { "Couldn't write the export file." },
        )
    }

    LaunchedEffect(pendingTree) {
        val tree = pendingTree ?: return@LaunchedEffect
        pendingTree = null

        val name = FileScanner.displayNameOf(context, tree) ?: "Local Files"
        val files = FileScanner.scanFolderForAudio(context, tree)
        if (files.isEmpty()) {
            importMessage = "No audio found in $name"
        } else {
            val imported = LocalFileManager(
                context.applicationContext,
                ServiceLocator.subscriptionRepository(),
            ).importFolder(name, files)
            importMessage = "Added $imported file${if (imported == 1) "" else "s"} from $name"
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                NowPlayingBar(
                    connection = playerConnection,
                    transport = transport.value,
                    artworkUrl = nowPlaying?.artworkUrl,
                    onOpenPlayer = { navController.navigate(Dest.PLAYER.route) },
                )

                LoheBottomBar(
                    destinations = remember {
                        BAR_DESTINATIONS.map { dest ->
                            BottomDestination(
                                route = dest.route,
                                label = dest.label,
                                icon = dest.icon,
                            )
                        }
                    },
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.HOME.route,
            // Scaffold's padding carries the status-bar and bottom-bar insets.
            // Without it every screen draws underneath the status bar and the
            // header titles clip.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Dest.HOME.route) {
                HomeScreen(
                    queueViewModel = queueViewModel,
                    onOpenPodcast = { navController.navigate(Routes.episodes(it)) },
                    onOpenLibrary = { navController.navigate(Dest.PODCASTS.route) },
                    onOpenDiscover = { navController.navigate(Dest.DISCOVER.route) },
                    onOpenSettings = { navController.navigate(Dest.SETTINGS.route) },
                    onPlay = { entry ->
                        nowPlaying = NowPlaying(
                            title = entry.episode.title,
                            uri = entry.episode.playbackUri,
                            showTitle = entry.showTitle,
                            episodeId = entry.episode.uuid,
                            artworkUrl = entry.episode.artworkUrl,
                        )
                        navController.navigate(Dest.PLAYER.route)
                    },
                    appearance = appearance,
                )
            }

            composable(Dest.PODCASTS.route) {
                PodcastsScreen(
                    viewModel = podcastsViewModel,
                    onOpenPodcast = { navController.navigate(Routes.episodes(it)) },
                    onAddLocalFolder = { folderPicker.launch(null) },
                    onManageStorage = { navController.navigate(Dest.STORAGE.route) },
                    onPickOpmlToImport = { opmlPicker.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                    onPickExportDestination = { document ->
                        pendingExport = document
                        exportWriter.launch(context.getString(R.string.opml_file_name))
                    },
                    message = importMessage,
                    onMessageShown = { importMessage = null },
                )
            }

            composable(
                route = Routes.EPISODES,
                arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
            ) { entry ->
                val uuid = entry.arguments?.getString("uuid").orEmpty()
                val vm: EpisodesViewModel = viewModel(
                    factory = EpisodesViewModelFactory(uuid),
                    key = "episodes-$uuid",
                )
                val show by vm.podcast.collectAsState()
                val queueTitle = show?.title.orEmpty()
                EpisodesScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onQueue = { episodeId ->
                        queueViewModel.enqueue(episodeId)
                        importMessage = "Added to queue"
                    },
                    onDownload = { episode ->
                        ServiceLocator.downloadManager().start(episode)
                        importMessage = "Downloading"
                    },
                    onPlay = { episode ->
                        // A downloaded copy plays offline; playbackUri prefers it.
                        nowPlaying = NowPlaying(
                            title = episode.title,
                            uri = episode.playbackUri,
                            showTitle = queueTitle,
                            episodeId = episode.uuid,
                            artworkUrl = episode.artworkUrl ?: show?.artworkUrl,
                            knownDurationSeconds = episode.durationSeconds,
                        )
                        navController.navigate(Dest.PLAYER.route)
                    },
                )
            }

            composable(Dest.DISCOVER.route) {
                DiscoveryScreen(
                    viewModel = discoveryViewModel,
                    onOpenSubscribed = { uuid ->
                        navController.navigate(Routes.episodes(uuid))
                    },
                )
            }

            composable(Dest.QUEUE.route) {
                QueueScreen(
                    viewModel = queueViewModel,
                    onPlay = { entry ->
                        nowPlaying = NowPlaying(
                            title = entry.episode.title,
                            uri = entry.episode.playbackUri,
                            showTitle = entry.showTitle,
                            episodeId = entry.episode.uuid,
                            artworkUrl = entry.episode.artworkUrl,
                        )
                        navController.navigate(Dest.PLAYER.route)
                    },
                )
            }

            composable(Dest.PLAYER.route) {
                PlayerScreen(
                    connection = playerConnection,
                    appearance = appearance,
                    onToggleAppearance = onToggleAppearance,
                    mediaUri = nowPlaying?.uri,
                    mediaEpisodeId = nowPlaying?.episodeId,
                    mediaTitle = nowPlaying?.title.orEmpty(),
                    mediaShowTitle = nowPlaying?.showTitle.orEmpty(),
                    mediaArtworkUrl = nowPlaying?.artworkUrl,
                    knownDurationMs = nowPlaying?.knownDurationSeconds?.times(1_000L),
                    skipBackMs = playbackSettings.skipBackSeconds * 1_000L,
                    skipForwardMs = playbackSettings.skipForwardSeconds * 1_000L,
                    volumeBoostDefault = playbackSettings.volumeBoostByDefault,
                )
            }

            composable(Dest.STORAGE.route) {
                StorageManagementScreen(
                    viewModel = viewModel(factory = SubscriptionsViewModelFactory()),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Dest.SETTINGS.route) {
                SettingsScreen(
                    appearance = appearance,
                    onToggleAppearance = onToggleAppearance,
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    settingsStore = settingsStore,
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
