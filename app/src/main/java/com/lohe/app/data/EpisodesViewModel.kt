package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

enum class PodcastUnsubscribeState { IDLE, WORKING, SUCCESS, ERROR }

/** Episodes for one podcast, plus the podcast row itself. */
class EpisodesViewModel(
    private val repository: SubscriptionRepository,
    private val podcastUuid: String,
    private val settingsStore: SettingsStore? = null,
) : ViewModel() {

    private val _unsubscribeState = MutableStateFlow(PodcastUnsubscribeState.IDLE)
    val unsubscribeState: StateFlow<PodcastUnsubscribeState> = _unsubscribeState.asStateFlow()

    fun unsubscribe() {
        if (!_unsubscribeState.compareAndSet(PodcastUnsubscribeState.IDLE, PodcastUnsubscribeState.WORKING)) return
        viewModelScope.launch {
            try {
                repository.unsubscribe(podcastUuid)
                _unsubscribeState.value = PodcastUnsubscribeState.SUCCESS
            } catch (cancelled: CancellationException) {
                _unsubscribeState.value = PodcastUnsubscribeState.IDLE
                throw cancelled
            } catch (_: Exception) {
                _unsubscribeState.value = PodcastUnsubscribeState.ERROR
            }
        }
    }

    fun acknowledgeUnsubscribeError() {
        _unsubscribeState.compareAndSet(PodcastUnsubscribeState.ERROR, PodcastUnsubscribeState.IDLE)
    }

    val podcast: StateFlow<PodcastEntity?> = repository
        .observePodcasts()
        .map { list -> list.firstOrNull { it.uuid == podcastUuid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _filter = MutableStateFlow(EpisodeFilter.Filter.ALL)
    private val _order = MutableStateFlow(EpisodeFilter.Order.NEWEST_FIRST)
    private val _query = MutableStateFlow("")

    val filter: StateFlow<EpisodeFilter.Filter> =
        (settingsStore?.podcastEpisodeFilter(podcastUuid) ?: _filter)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeFilter.Filter.ALL)
    val order: StateFlow<EpisodeFilter.Order> =
        (settingsStore?.podcastEpisodeOrder(podcastUuid) ?: _order)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeFilter.Order.NEWEST_FIRST)

    /**
     * Episodes for this show, filtered and ordered.
     *
     * Filtering happens here rather than in SQL so the list stays reactive to
     * download and progress writes while the user is looking at it.
     */
    val episodes: StateFlow<List<EpisodeEntity>> =
        combine(
            repository.observeEpisodesFor(podcastUuid),
            podcast,
            filter,
            order,
            _query,
        ) { all, show, filter, order, query ->
            val fallbackArt = show?.artworkUrl?.takeIf { it.isNotBlank() }
            val resolved = if (fallbackArt != null) {
                all.map { ep ->
                    if (ep.artworkUrl.isNullOrBlank()) ep.copy(artworkUrl = fallbackArt) else ep
                }
            } else {
                all
            }
            EpisodeFilter.apply(resolved, filter, order, query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(value: EpisodeFilter.Filter) {
        if (settingsStore == null) _filter.value = value
        else viewModelScope.launch { settingsStore.setPodcastEpisodeFilter(podcastUuid, value) }
    }

    fun setOrder(value: EpisodeFilter.Order) {
        if (settingsStore == null) _order.value = value
        else viewModelScope.launch { settingsStore.setPodcastEpisodeOrder(podcastUuid, value) }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setAutoAddNewEpisodesToUpNext(enabled: Boolean) = viewModelScope.launch {
        repository.setPodcastAutoQueueWithExisting(podcastUuid, enabled)
    }

    fun setAutoAddNewEpisodesAtTop(atTop: Boolean) = viewModelScope.launch {
        repository.setAutoAddNewEpisodesAtTop(podcastUuid, atTop)
    }

    fun setStarred(uuid: String, starred: Boolean) = viewModelScope.launch {
        repository.setStarred(uuid, starred)
    }

    /**
     * Files an episode away, or brings it back.
     *
     * An archived episode leaves the lists and stops counting towards the
     * listening figures, but is kept so it can be brought back.
     */
    fun toggleArchived(uuid: String, currentlyArchived: Boolean) = viewModelScope.launch {
        repository.setArchived(uuid, !currentlyArchived)
    }

    /**
     * Toggles played state.
     *
     * Un-marking restores a zero resume position, so the episode restarts from
     * the beginning rather than jumping to where playback stopped.
     */
    fun togglePlayed(uuid: String, currentlyPlayed: Boolean) = viewModelScope.launch {
        if (currentlyPlayed) {
            repository.reopenEpisode(uuid)
        } else {
            repository.markPlayed(uuid)
        }
    }

    fun markPlayedBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.markPlayed(it) }
    }

    fun markUnplayedBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.reopenEpisode(it) }
    }

    fun deleteBatch(uuids: Collection<String>) = viewModelScope.launch {
        uuids.forEach { repository.deleteEpisode(it) }
    }
}

class EpisodesViewModelFactory(
    private val podcastUuid: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EpisodesViewModel(ServiceLocator.subscriptionRepository(), podcastUuid, ServiceLocator.settingsStore()) as T
}
