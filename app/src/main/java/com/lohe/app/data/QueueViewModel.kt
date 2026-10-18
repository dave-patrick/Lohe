package com.lohe.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One queue entry, paired with its show so the row can name the source. */
data class QueueEntry(val episode: EpisodeEntity, val showTitle: String)

/** One in-progress episode, paired with its show. */
data class ContinueEntry(val episode: EpisodeEntity, val showTitle: String)

/**
 * Backs the queue and the home screen's "continue listening".
 *
 * Both screens need episodes joined to their show title, and both need that
 * join to stay live as the library changes — so the join happens once here
 * rather than twice in the UI.
 */
class QueueViewModel(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val podcasts: StateFlow<List<PodcastEntity>> = repository
        .observePodcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queue: StateFlow<List<QueueEntry>> = combine(
        repository.observeQueue(),
        podcasts,
    ) { episodes, shows ->
        val titles = shows.associate { it.uuid to it.title }
        episodes.map { QueueEntry(it, titles[it.podcastUuid].orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueListening: StateFlow<List<ContinueEntry>> = combine(
        repository.observeInProgress(),
        podcasts,
    ) { episodes, shows ->
        val titles = shows.associate { it.uuid to it.title }
        episodes.map { ContinueEntry(it, titles[it.podcastUuid].orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dequeue(uuid: String) = viewModelScope.launch { repository.dequeue(uuid) }

    fun promote(uuid: String) = viewModelScope.launch { repository.promote(uuid) }

    /** "Played up to here": drops this entry and everything after it. */
    fun clearFrom(uuid: String) = viewModelScope.launch { repository.clearQueueFrom(uuid) }

    fun enqueue(uuid: String) = viewModelScope.launch { repository.enqueue(uuid) }

    /** Total remaining time across the queue, in seconds; unknown durations ignored. */
    val queuedSeconds: StateFlow<Long> = queue
        .map { entries ->
            entries.sumOf { entry ->
                val duration = entry.episode.durationSeconds ?: 0
                (duration - entry.episode.playedUpToSeconds).coerceAtLeast(0L)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
}

class QueueViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        QueueViewModel(ServiceLocator.subscriptionRepository()) as T
}
