package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Episodes for one podcast, plus the podcast row itself. */
class EpisodesViewModel(
    private val repository: SubscriptionRepository,
    private val podcastUuid: String,
    private val viewStore: EpisodeViewStore? = null,
) : ViewModel() {

    val podcast: StateFlow<PodcastEntity?> = repository
        .observePodcasts()
        .map { list -> list.firstOrNull { it.uuid == podcastUuid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The view chosen in this podcast last time, so it opens the way it was left.
    private val saved = viewStore?.load(podcastUuid) ?: EpisodeView()
    private val _filter = MutableStateFlow(saved.filter)
    private val _order = MutableStateFlow(saved.order)
    private val _hidePlayed = MutableStateFlow(saved.hidePlayed)
    private val _query = MutableStateFlow("")

    val filter: StateFlow<EpisodeFilter.Filter> = _filter.asStateFlow()
    val order: StateFlow<EpisodeFilter.Order> = _order.asStateFlow()
    val hidePlayed: StateFlow<Boolean> = _hidePlayed.asStateFlow()

    private fun saveView() {
        viewStore?.save(podcastUuid, EpisodeView(_filter.value, _order.value, _hidePlayed.value))
    }

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
            _filter,
            _order,
            _query,
            _hidePlayed,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val all = values[0] as List<EpisodeEntity>
            val show = values[1] as PodcastEntity?
            val filter = values[2] as EpisodeFilter.Filter
            val order = values[3] as EpisodeFilter.Order
            val query = values[4] as String
            val hidePlayed = values[5] as Boolean
            val fallbackArt = show?.artworkUrl?.takeIf { it.isNotBlank() }
            val resolved = if (fallbackArt != null) {
                all.map { ep ->
                    if (ep.artworkUrl.isNullOrBlank()) ep.copy(artworkUrl = fallbackArt) else ep
                }
            } else {
                all
            }
            EpisodeFilter.apply(resolved, filter, order, query, hidePlayed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(value: EpisodeFilter.Filter) {
        _filter.value = value
        saveView()
    }

    fun setOrder(value: EpisodeFilter.Order) {
        _order.value = value
        saveView()
    }

    fun setHidePlayed(value: Boolean) {
        _hidePlayed.value = value
        saveView()
    }

    fun setQuery(value: String) {
        _query.value = value
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
        EpisodesViewModel(
            ServiceLocator.subscriptionRepository(),
            podcastUuid,
            ServiceLocator.episodeViewStore(),
        ) as T
}
