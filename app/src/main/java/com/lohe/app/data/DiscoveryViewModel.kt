package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.CatalogPodcast
import com.lohe.app.data.fetch.CatalogSearcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the discovery screen is currently showing. */
sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data object Empty : CatalogState
    data class Results(val items: List<CatalogPodcast>) : CatalogState
    data class Failed(val reason: String) : CatalogState
}

/**
 * Backs catalog search.
 *
 * The query is held here as [StateFlow] and is the single source of truth: the
 * text field renders from it rather than keeping a local copy. A screen that
 * keeps its own copy and only writes to the ViewModel renders a permanently
 * empty field and the IME's characters are discarded on every keystroke.
 *
 * The debounce lives next to the search call it protects, so a six-letter word
 * costs one request rather than six.
 */
class DiscoveryViewModel(
    private val searcher: CatalogSearcher,
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _subscribed = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val subscribed: SharedFlow<String> = _subscribed

    /**
     * What happened when the listener asked for something from the catalog.
     *
     * Subscribing fetches the feed, so it can fail for reasons that have
     * nothing to do with the tap. Without this the button simply did nothing
     * when the feed was unreachable, which reads as a broken button.
     */
    private val _action = MutableStateFlow<CatalogActionState>(CatalogActionState.Idle)
    val action: StateFlow<CatalogActionState> = _action.asStateFlow()

    /**
     * Followed shows keyed by feed URL, so the detail sheet knows immediately
     * whether a catalog result is already in the library and can show that
     * show's own settings.
     */
    val subscriptions: StateFlow<Map<String, PodcastEntity>> =
        repository.observePodcasts()
            .map { list -> list.associateBy { it.feedUrl } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Episodes fetched for previewing a show, keyed by feed URL.
     *
     * Populated when the user opens a search result's detail sheet; lets them
     * browse the episode list and download individual episodes before
     * deciding whether to follow the show.
     */
    private val _previewEpisodes = MutableStateFlow<Map<String, List<EpisodeEntity>>>(emptyMap())
    val previewEpisodes: StateFlow<Map<String, List<EpisodeEntity>>> = _previewEpisodes.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * Fetches a show's episode list for preview, without subscribing.
     */
    fun fetchFeedPreview(feedUrl: String) {
        viewModelScope.launch {
            repository.fetchFeedPreview(feedUrl)
                .onSuccess { episodes ->
                    _previewEpisodes.value += feedUrl to episodes
                }
        }
    }

    fun search(term: String) {
        if (term.isBlank()) {
            _state.value = CatalogState.Idle
            return
        }

        _state.value = CatalogState.Loading
        viewModelScope.launch {
            searcher.search(term)
                .onSuccess { results ->
                    _state.value = if (results.isEmpty()) {
                        CatalogState.Empty
                    } else {
                        CatalogState.Results(results)
                    }
                }
                .onFailure { error ->
                    _state.value = CatalogState.Failed(
                        error.message ?: "Search is unavailable right now.",
                    )
                }
        }
    }

    fun subscribe(podcast: CatalogPodcast) {
        viewModelScope.launch {
            _action.value = CatalogActionState.Working
            repository.importFeed(podcast.feedUrl)
                .onSuccess {
                    _subscribed.tryEmit(podcast.feedUrl)
                    _action.value = CatalogActionState.Done("Subscribed to ${podcast.title}")
                }
                .onFailure { error ->
                    // Swallowing this left the button looking dead: the feed
                    // fetch is what fails, not the tap, and nothing said so.
                    _action.value = CatalogActionState.Failed(
                        error.message ?: "Couldn't subscribe to that feed.",
                    )
                }
        }
    }

    /**
     * Subscribes to a show (importing its feed) and enqueues one of its
     * episodes for download, so a listener can grab a single episode from
     * search results without committing to the whole show first.
     */
    fun downloadEpisode(podcast: CatalogPodcast, episodeUuid: String) {
        viewModelScope.launch {
            _action.value = CatalogActionState.Working
            repository.importFeed(podcast.feedUrl)
                .onSuccess {
                    repository.enqueue(episodeUuid)
                    _action.value = CatalogActionState.Done("Downloading that episode")
                }
                .onFailure { error ->
                    _action.value = CatalogActionState.Failed(
                        error.message ?: "Couldn't download that episode.",
                    )
                }
        }
    }

    fun consumeAction() {
        _action.value = CatalogActionState.Idle
    }

    /** The library row behind a catalog result, or null when not followed yet. */
    /**
 * Blurbs pulled from feeds while the detail sheet is open, keyed by feed URL.
 *
 * The catalog has no description, so this fills the gap without subscribing.
 */
private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())
    val previews: StateFlow<Map<String, String>> = _previews.asStateFlow()

    private var previewJob: Job? = null

    /**
     * Loads a show's blurb for the detail sheet.
     *
     * Already-followed shows use the copy parsed at subscribe time; only a show
     * that has never been fetched needs a network round trip.
     */
    fun loadPreview(podcast: CatalogPodcast, knownDescription: String?) {
        if (!knownDescription.isNullOrBlank()) return
        if (_previews.value.containsKey(podcast.feedUrl)) return

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            repository.previewFeed(podcast.feedUrl)
                .onSuccess { fetched ->
                    val blurb = fetched.description
                    if (!blurb.isNullOrBlank()) {
                        _previews.value += podcast.feedUrl to blurb
                    }
                }
        }
    }

    fun unsubscribe(podcastUuid: String) {
        viewModelScope.launch { repository.unsubscribe(podcastUuid) }
    }

    fun setAutoDownload(podcastUuid: String, enabled: Boolean) {
        viewModelScope.launch { repository.setAutoDownload(podcastUuid, enabled) }
    }

    fun setAutoAddNewEpisodesToUpNext(podcastUuid: String, enabled: Boolean) {
        viewModelScope.launch { repository.setAutoAddNewEpisodesToUpNext(podcastUuid, enabled) }
    }

    fun setAutoAddNewEpisodesAtTop(podcastUuid: String, atTop: Boolean) {
        viewModelScope.launch { repository.setAutoAddNewEpisodesAtTop(podcastUuid, atTop) }
    }
}

/** What happened when the listener asked for something from the catalog. */
sealed interface CatalogActionState {
    data object Idle : CatalogActionState
    data object Working : CatalogActionState
    /** It worked; [message] says what was done. */
    data class Done(val message: String) : CatalogActionState
    /** It did not work; [message] says why. */
    data class Failed(val message: String) : CatalogActionState
}

class DiscoveryViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DiscoveryViewModel(
            searcher = ServiceLocator.catalogSearcher(),
            repository = ServiceLocator.subscriptionRepository(),
        ) as T
}
