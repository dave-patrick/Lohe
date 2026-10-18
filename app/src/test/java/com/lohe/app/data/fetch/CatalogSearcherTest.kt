package com.lohe.app.data.fetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are trimmed from real iTunes Search responses, including the shapes
 * that trip parsers: numeric-looking ids, absent optional fields, and rows for
 * defunct shows with no feed.
 */
class CatalogSearcherTest {

    private val resultsWrapper = { entries: String ->
        """{"resultCount":${entries.count { it == '{' }},"results":[$entries]}"""
    }

    @Test
    fun `maps a complete result`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"wrapperType":"collection","kind":"podcast","collectionId":1,
                 "collectionName":"Field Notes","artistName":"The Desk",
                 "feedUrl":"https://feeds.example.com/field-notes.xml",
                 "artworkUrl600":"https://art.example.com/600.jpg",
                 "primaryGenreName":"Society & Culture"}
                """.trimIndent(),
            ),
        )

        val result = parsed.single()
        assertEquals("Field Notes", result.title)
        assertEquals("The Desk", result.author)
        assertEquals("https://feeds.example.com/field-notes.xml", result.feedUrl)
        assertEquals("https://art.example.com/600.jpg", result.artworkUrl)
        assertEquals("Society & Culture", result.genre)
    }

    @Test
    fun `drops results with no feed url`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"Alive","feedUrl":"https://a.com/f.xml"},
                {"collectionName":"Defunct","feedUrl":""},
                {"collectionName":"NoFeedAtAll"}
                """.trimIndent(),
            ),
        )

        assertEquals(1, parsed.size)
        assertEquals("Alive", parsed.single().title)
    }

    @Test
    fun `falls back through the title fields`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"","trackName":"Fallback Title","feedUrl":"https://a.com/f.xml"},
                {"trackName":"Only Track","feedUrl":"https://b.com/f.xml"},
                {"feedUrl":"https://c.com/f.xml"}
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf("Fallback Title", "Only Track", "Untitled show"),
            parsed.map { it.title },
        )
    }

    @Test
    fun `falls back to the smaller artwork`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """{"collectionName":"S","feedUrl":"https://a.com/f.xml",
                    "artworkUrl100":"https://art.example.com/100.jpg"}""",
            ),
        )
        assertEquals("https://art.example.com/100.jpg", parsed.single().artworkUrl)
    }

    @Test
    fun `missing optional fields become null rather than the string null`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper("""{"collectionName":"S","feedUrl":"https://a.com/f.xml"}"""),
        )

        val result = parsed.single()
        assertNull(result.artworkUrl)
        assertNull(result.genre)
        assertEquals("", result.author)
    }

    @Test
    fun `numeric ids do not disturb the mapping`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """{"collectionId":1234567890,"trackId":987654321,"collectionName":"Numeric",
                    "feedUrl":"https://a.com/f.xml"}""",
            ),
        )
        assertEquals("Numeric", parsed.single().title)
    }

    @Test
    fun `handles an empty result set`() {
        assertTrue(CatalogSearcher.parseResults("""{"resultCount":0,"results":[]}""").isEmpty())
    }

    @Test
    fun `malformed json yields empty rather than throwing`() {
        assertTrue(CatalogSearcher.parseResults("not json at all").isEmpty())
        assertTrue(CatalogSearcher.parseResults("").isEmpty())
        assertTrue(CatalogSearcher.parseResults("""{"results":"nope"}""").isEmpty())
    }

    @Test
    fun `unknown fields are ignored`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"Forward Compatible","feedUrl":"https://a.com/f.xml",
                 "someBrandNewField":{"nested":[1,2,3]},"anotherOne":null}
                """.trimIndent(),
            ),
        )
        assertEquals("Forward Compatible", parsed.single().title)
    }

    @Test
    fun `whitespace-only fields are treated as absent`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """{"collectionName":"   ","artistName":"  ","feedUrl":"https://a.com/f.xml"}""",
            ),
        )

        val result = parsed.single()
        assertEquals("Untitled show", result.title)
        assertEquals("", result.author)
    }

    @Test
    fun `preserves order across multiple results`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"One","feedUrl":"https://1.com/f.xml"},
                {"collectionName":"Two","feedUrl":"https://2.com/f.xml"},
                {"collectionName":"Three","feedUrl":"https://3.com/f.xml"}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("One", "Two", "Three"), parsed.map { it.title })
    }

    @Test
    fun `non-object entries are skipped without failing the parse`() {
        val parsed = CatalogSearcher.parseResults(
            """{"results":[42,"junk",{"collectionName":"Good","feedUrl":"https://a.com/f.xml"}]}""",
        )
        assertEquals(1, parsed.size)
        assertEquals("Good", parsed.single().title)
    }

    @Test
    fun `repeats of one feed collapse to a single row`() {
        // iTunes returns a row per album, so one show arrives many times over.
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"Planet Money","feedUrl":"https://feeds.npr.org/510289/podcast.xml"},
                {"collectionName":"Planet Money","feedUrl":"https://feeds.npr.org/510289/podcast.xml"},
                {"collectionName":"Planet Money","feedUrl":"https://feeds.npr.org/510289/podcast.xml"}
                """.trimIndent(),
            ),
        )

        assertEquals(1, parsed.size)
    }

    @Test
    fun `the first occurrence is the one that survives`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"Full","feedUrl":"https://a.com/f.xml","artistName":"Real Host"},
                {"collectionName":"Later","feedUrl":"https://a.com/f.xml","artistName":"Someone Else"}
                """.trimIndent(),
            ),
        )

        assertEquals("Full", parsed.single().title)
        assertEquals("Real Host", parsed.single().author)
    }

    @Test
    fun `different shows sharing a title are both kept`() {
        // Deduping is by feed, not by name: two distinct shows may be called
        // the same thing.
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"Same Name","feedUrl":"https://a.com/f.xml"},
                {"collectionName":"Same Name","feedUrl":"https://b.com/f.xml"}
                """.trimIndent(),
            ),
        )

        assertEquals(2, parsed.size)
    }

    @Test
    fun `the long blurb is preferred for the detail view`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"S","feedUrl":"https://a.com/f.xml",
                 "longDescriptionText":"The long one","shortDescriptionText":"The short one"}
                """.trimIndent(),
            ),
        )

        assertEquals("The long one", parsed.single().description)
    }

    @Test
    fun `the short blurb stands in when there is no long one`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """{"collectionName":"S","feedUrl":"https://a.com/f.xml","shortDescriptionText":"Short"}""",
            ),
        )

        assertEquals("Short", parsed.single().description)
    }

    @Test
    fun `a missing blurb is null rather than blank`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper("""{"collectionName":"S","feedUrl":"https://a.com/f.xml"}"""),
        )

        assertEquals(null, parsed.single().description)
    }

    @Test
    fun `track count becomes the episode count`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """{"collectionName":"S","feedUrl":"https://a.com/f.xml","trackCount":"1200"}""",
            ),
        )

        assertEquals(1200, parsed.single().episodeCount)
    }

    @Test
    fun `a missing or zero track count is null`() {
        val parsed = CatalogSearcher.parseResults(
            resultsWrapper(
                """
                {"collectionName":"A","feedUrl":"https://a.com/f.xml"},
                {"collectionName":"B","feedUrl":"https://b.com/f.xml","trackCount":"0"}
                """.trimIndent(),
            ),
        )

        assertTrue(parsed.all { it.episodeCount == null })
    }
}
