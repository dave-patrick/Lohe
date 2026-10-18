package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

    val allEpisodes: StateFlow<List<EpisodeEntity>> =
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
        _opmlImport.value = OpmlImportState.Running
        try {
            _opmlImport.value = OpmlImportState.Done(repository.importOpml(document))
        } catch (e: IllegalArgumentException) {
            _opmlImport.value = OpmlImportState.Failed(
                e.message ?: "That file isn't an OPML subscription list.",
            )
        }
    }

    /** Builds an OPML document for the caller to write out. */
    suspend fun buildOpml(): String = repository.exportOpml()

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
    data object Running : OpmlImportState
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

class SubscriptionsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SubscriptionsViewModel(ServiceLocator.subscriptionRepository()) as T
}
