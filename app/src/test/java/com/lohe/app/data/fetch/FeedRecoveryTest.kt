package com.lohe.app.data.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Matching a failed feed against the catalog, and recovering a Libsyn feed
 * that its own vanity address will not serve.
 *
 * The cases here are the ones that actually occurred: a show whose feed
 * moved, a show that has been deleted, and a Libsyn show whose feed is
 * still published but whose old address points at a private object.
 */
class FeedRecoveryTest {

    private fun podcast(title: String, feed: String = "https://a.com/f.xml") =
        CatalogPodcast(
            title = title,
            author = "Someone",
            feedUrl = feed,
            artworkUrl = null,
            genre = null,
        )

    @Test
    fun `an exact title wins`() {
        val results = listOf(
            podcast("Sounding Off with Rick Beato"),
            podcast("Sounding Off"),
        )
        val match = CatalogSearcher.bestTitleMatch("Sounding Off with Rick Beato", results)
        assertEquals("Sounding Off with Rick Beato", match?.title)
    }

    @Test
    fun `punctuation and case do not stop a match`() {
        val results = listOf(podcast("The Show: A Talk"))
        val match = CatalogSearcher.bestTitleMatch("the show a talk", results)
        assertEquals("The Show: A Talk", match?.title)
    }

    @Test
    fun `a show is not matched with a longer show that starts the same way`() {
        // "The Triple R" and "The Triple R Mommy Show" are different podcasts.
        // Importing the wrong one would put the wrong episodes in the library.
        val results = listOf(podcast("The Triple R Mommy Show"))
        assertNull(CatalogSearcher.bestTitleMatch("The Triple R", results))
    }

    @Test
    fun `a short suffix is still the same show`() {
        val results = listOf(podcast("Heavy Metal News"))
        val match = CatalogSearcher.bestTitleMatch("Heavy Metal News HD", results)
        assertEquals("Heavy Metal News", match?.title)
    }

    @Test
    fun `no match is better than a wrong one`() {
        val results = listOf(
            podcast("Our Mind On Music"),
            podcast("The Grunge Bible Podcast"),
        )
        assertNull(CatalogSearcher.bestTitleMatch("Sounding Off with Rick Beato", results))
    }

    @Test
    fun `the libsyn show id is read from the address a feed redirects to`() {
        // The vanity address carries no id, so it is read from the redirect.
        assertEquals(
            "96977",
            RssPodcastFetcher.libsynShowId(
                "https://rss.libsyn.com/shows/96977/destinations/500968.xml",
            ),
        )
    }

    @Test
    fun `the libsyn show id is read from the per-show address`() {
        assertEquals("96977", RssPodcastFetcher.libsynShowId("https://feeds.libsyn.com/96977"))
    }

    @Test
    fun `an address with no libsyn show id yields nothing`() {
        assertNull(RssPodcastFetcher.libsynShowId("https://soundingoff.libsyn.com/rss"))
        assertNull(RssPodcastFetcher.libsynShowId("https://feeds.example.com/show.xml"))
    }
}
