package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI

/**
 * The library: subscriptions, the search box over them, and the network
 * operations that change them.
 *
 * Long-running operations (import, refresh) expose a one-shot result via a
 * [StateFlow] that the UI consumes and then clears, rather than a callback, so
 * a rotation mid-request does not lose the outcome.
 */
class SubscriptionsViewModel(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val podcasts: StateFlow<List<PodcastEntity>> =
        searchQuery
            .flatMapLatest { query -> repository.observeSearchResults(query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQueryState: StateFlow<String> = searchQuery.asStateFlow()

    /**
     * Every subscription, before the search box narrows it.
     *
     * The header shows how much the library holds, which is not the same as
     * how many rows happen to be on screen.
     */
    val totalPodcastCount: StateFlow<Int> = repository.observePodcasts()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val allEpisodes: StateFlow<List<EpisodeSummary>> =
        repository.observeAllEpisodes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localFilesStorageSize: StateFlow<Long> =
        repository.getLocalFilesStorageSize()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun unsubscribe(uuid: String) = viewModelScope.launch {
        repository.unsubscribe(uuid)
    }

    fun unsubscribeBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.unsubscribe(it) }
    }

    fun markPodcastsPlayed(podcastUuids: Set<String>) = viewModelScope.launch {
        val targetEpisodes = allEpisodes.value.filter { it.podcastUuid in podcastUuids }
        targetEpisodes.forEach { repository.markPlayed(it.uuid) }
    }

    /**
     * Reopens a show's episodes so they count as unlistened again.
     *
     * The counterpart to [markPodcastsPlayed]. Marking a show used to be a
     * one-way door: the action was named "toggle" but only ever marked, and a
     * feed refresh deliberately preserves listener state, so a show marked by
     * accident stayed marked with no way back.
     */
    fun unmarkPodcastsPlayed(podcastUuids: Set<String>) = viewModelScope.launch {
        val targetEpisodes = allEpisodes.value.filter { it.podcastUuid in podcastUuids }
        targetEpisodes.forEach { repository.reopenEpisode(it.uuid) }
    }

    /** Delete every episode backed by a local content:// URI. */
    fun deleteLocalFiles() = viewModelScope.launch {
        repository.deleteLocalFiles()
    }

    // --- Single feed --------------------------------------------------------

    private val _feedImport = MutableStateFlow<FeedImportState>(FeedImportState.Idle)
    val feedImport: StateFlow<FeedImportState> = _feedImport.asStateFlow()

    fun consumeFeedImport() {
        _feedImport.value = FeedImportState.Idle
    }

    fun importFeed(rawUrl: String) = viewModelScope.launch {
        val url = normalizeFeedUrl(rawUrl)
        if (url == null) {
            _feedImport.value = FeedImportState.Failed(INVALID_FEED_URL)
            return@launch
        }

        _feedImport.value = FeedImportState.Loading
        repository.importFeed(url)
            .onSuccess { podcast ->
                _feedImport.value = FeedImportState.Succeeded(podcast.title)
            }
            .onFailure { error ->
                _feedImport.value = FeedImportState.Failed(
                    error.message ?: "Couldn't read that feed.",
                )
            }
    }

    /**
     * Accepts a bare host and assumes https.
     *
     * Users paste whatever their podcast website showed them, which is rarely a
     * scheme-prefixed URL.
     */
    private fun normalizeFeedUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val parsed = runCatching { URI(withScheme) }.getOrNull() ?: return null
        return if (parsed.host.isNullOrBlank()) null else withScheme
    }

    // --- OPML ---------------------------------------------------------------

    private val _opmlImport = MutableStateFlow<OpmlImportState>(OpmlImportState.Idle)
    val opmlImport: StateFlow<OpmlImportState> = _opmlImport.asStateFlow()

    fun consumeOpmlImport() {
        _opmlImport.value = OpmlImportState.Idle
    }

    fun importOpml(document: String) = viewModelScope.launch {
        _opmlImport.value = OpmlImportState.Running(0, 0)
        try {
            val result = repository.importOpml(document) { completed, total ->
                _opmlImport.value = OpmlImportState.Running(completed, total)
            }
            _opmlImport.value = OpmlImportState.Done(result)
        } catch (e: IllegalArgumentException) {
            _opmlImport.value = OpmlImportState.Failed(
                e.message ?: "That file isn't an OPML subscription list.",
            )
        } catch (e: Exception) {
            _opmlImport.value = OpmlImportState.Failed(
                e.message ?: "Couldn't import that OPML file.",
            )
        }
    }

    /** Builds an OPML document for the caller to write out. */
    suspend fun buildOpml(): String = repository.exportOpml()

    // --- Finding a replacement for a dead feed -------------------------------

    private val _replacement = MutableStateFlow<FeedReplacementState>(FeedReplacementState.Idle)
    val replacement: StateFlow<FeedReplacementState> = _replacement.asStateFlow()

    fun consumeReplacement() {
        _replacement.value = FeedReplacementState.Idle
    }

    /**
     * Looks a show up in the catalog to find its current feed address.
     *
     * A feed that returns 404 has usually moved, and the address in the OPML
     * is the stale one. The catalog knows where the show lives now, so the
     * listener can be offered the working address instead of a dead end.
     */
    fun findReplacementFeed(title: String) = viewModelScope.launch {
        _replacement.value = FeedReplacementState.Searching(title)
        ServiceLocator.catalogSearcher().findFeedForTitle(title)
            .onSuccess { match ->
                _replacement.value = if (match == null) {
                    FeedReplacementState.NotFound(title)
                } else {
                    FeedReplacementState.Found(
                        wantedTitle = title,
                        showTitle = match.title,
                        feedUrl = match.feedUrl,
                    )
                }
            }
            .onFailure { error ->
                _replacement.value = FeedReplacementState.Failed(
                    title,
                    error.message ?: "Couldn't search the catalog.",
                )
            }
    }

    /** Subscribes to a feed found by [findReplacementFeed]. */
    fun importReplacement(feedUrl: String) = viewModelScope.launch {
        _replacement.value = FeedReplacementState.Importing
        repository.importFeed(feedUrl)
            .onSuccess { _replacement.value = FeedReplacementState.Imported }
            .onFailure { error ->
                _replacement.value = FeedReplacementState.Failed(
                    "",
                    error.message ?: "Couldn't import that feed.",
                )
            }
    }

    // --- Refresh ------------------------------------------------------------

    private val _refresh = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refresh: StateFlow<RefreshState> = _refresh.asStateFlow()

    fun consumeRefresh() {
        _refresh.value = RefreshState.Idle
    }

    /** Re-fetches every subscription. Re-entrant calls are ignored. */
    fun refreshFeeds() = viewModelScope.launch {
        if (_refresh.value is RefreshState.Running) return@launch

        _refresh.value = RefreshState.Running
        try {
            _refresh.value = RefreshState.Done(repository.refreshAllFeeds())
        } catch (e: Exception) {
            _refresh.value = RefreshState.Failed(e.message ?: "Couldn't refresh your shows.")
        }
    }

    private companion object {
        const val INVALID_FEED_URL = "That doesn't look like an RSS feed URL."
    }
}

/** Progress and outcome of adding a feed by URL. */
sealed interface FeedImportState {
    data object Idle : FeedImportState
    data object Loading : FeedImportState
    data class Succeeded(val showTitle: String) : FeedImportState
    data class Failed(val reason: String) : FeedImportState
}

/** Progress and outcome of an OPML import. */
sealed interface OpmlImportState {
    data object Idle : OpmlImportState
    /** [completed] feeds processed out of [total] found in the document. */
    data class Running(val completed: Int = 0, val total: Int = 0) : OpmlImportState
    data class Done(val result: OpmlImportResult) : OpmlImportState
    data class Failed(val reason: String) : OpmlImportState
}

/** Progress and outcome of a feed refresh. */
sealed interface RefreshState {
    data object Idle : RefreshState
    data object Running : RefreshState
    data class Done(val summary: FeedRefreshSummary) : RefreshState
    data class Failed(val reason: String) : RefreshState
}

/**
 * Progress and outcome of looking up a replacement feed for a dead one.
 */
sealed interface FeedReplacementState {
    data object Idle : FeedReplacementState
    /** Looking up [title] in the catalog. */
    data class Searching(val title: String) : FeedReplacementState
    /** The catalog knows this show, at [feedUrl]. */
    data class Found(
        val wantedTitle: String,
        val showTitle: String,
        val feedUrl: String,
    ) : FeedReplacementState
    /** No catalog entry matched [title]. */
    data class NotFound(val title: String) : FeedReplacementState
    /** Importing a found feed. */
    data object Importing : FeedReplacementState
    /** The replacement feed was imported. */
    data object Imported : FeedReplacementState
    /** The lookup or the import failed. */
    data class Failed(val title: String, val reason: String) : FeedReplacementState
}

class SubscriptionsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SubscriptionsViewModel(ServiceLocator.subscriptionRepository()) as T
}
