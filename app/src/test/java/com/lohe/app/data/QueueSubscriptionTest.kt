package com.lohe.app.data

import androidx.lifecycle.ViewModelStore
import com.lohe.app.data.db.dao.EpisodeDao
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.dao.ListeningHistoryDao
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.data.fetch.PodcastFetcher
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QueueSubscriptionTest {
    @Test fun `background model does not retain whole library observers`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val active = AtomicInteger()
        val episodes = proxy<EpisodeDao> { name ->
            if (name == "observeAllSummaries") flow {
                active.incrementAndGet()
                try {
                    emit(emptyList<EpisodeSummary>())
                    awaitCancellation()
                } finally { active.decrementAndGet() }
            } else flowOf(emptyList<Any>())
        }
        val repository = SubscriptionRepository(
            proxy<PodcastDao> { flowOf(emptyList<Any>()) }, episodes,
            proxy<ListeningHistoryDao> { flowOf(emptyList<Any>()) },
            proxy<PodcastFetcher> { error("No network expected") },
        )
        val store = ViewModelStore()
        try {
            val model = QueueViewModel(repository)
            store.put("queue", model)
            withContext(Dispatchers.Default) { delay(200) }
            assertEquals("No screen subscribes, so no full-library observer may remain", 0, active.get())
            val screen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                model.userPlaylists.collect()
            }
            withContext(Dispatchers.Default) { delay(200) }
            assertEquals("A visible playlist should observe the library", 1, active.get())
            screen.cancelAndJoin()
            // flowOn(Default) is fused into stateIn's sharing context, so its
            // grace-period delay uses real time rather than the test scheduler.
            withContext(Dispatchers.Default) { delay(5_500) }
            assertEquals("Leaving the screen must release the full-library observer", 0, active.get())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private inline fun <reified T> proxy(crossinline value: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            value(method.name)
        } as T
}
