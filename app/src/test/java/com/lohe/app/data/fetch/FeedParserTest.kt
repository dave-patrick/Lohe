package com.lohe.app.data.fetch

import com.lohe.app.data.db.entity.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedParser] is exercised directly on the JVM with kxml2 standing in for
 * Android's pull parser. Fixtures are real-shaped feeds — the dialects and
 * defects below are all things actual publishers ship.
 */
class FeedParserTest {

    private val parser = FeedParser()
    private val feedUrl = "https://example.com/feed.xml"

    private fun parse(xml: String) = parser.parse(feedUrl, xml)

    // --- RSS 2.0 -------------------------------------------------------------

    @Test
    fun `parses a well-formed rss feed`() {
        val feed = parse(
            """
            <?xml version="1.0"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Field Notes</title>
                <description>Long-form interviews.</description>
                <itunes:author>Desk</itunes:author>
                <itunes:image href="https://example.com/art.jpg"/>
                <item>
                  <title>Small Things</title>
                  <guid isPermaLink="false">ep-001</guid>
                  <pubDate>Mon, 11 Mar 2024 09:00:00 GMT</pubDate>
                  <itunes:duration>1:02:03</itunes:duration>
                  <enclosure url="https://example.com/1.mp3"
                             type="audio/mpeg"
                             length="12345678"/>
                </item>
                <item>
                  <title>Second Episode</title>
                  <guid>ep-002</guid>
                  <pubDate>Tue, 12 Mar 2024 09:00:00 GMT</pubDate>
                  <enclosure url="https://example.com/2.mp3" type="audio/mpeg"/>
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )

        assertEquals("Field Notes", feed.podcast.title)
        assertEquals("Desk", feed.podcast.author)
        assertEquals("Long-form interviews.", feed.podcast.description)
        assertEquals("https://example.com/art.jpg", feed.podcast.artworkUrl)
        assertEquals(feedUrl, feed.podcast.feedUrl)

        assertEquals(2, feed.episodes.size)

        val first = feed.episodes[0]
        assertEquals("Small Things", first.title)
        assertEquals("https://example.com/1.mp3", first.audioUrl)
        assertEquals("audio/mpeg", first.fileType)
        assertEquals(12_345_678L, first.sizeBytes)
        assertEquals(3723, first.durationSeconds)
        assertEquals(
            FeedParser.parseDate("Mon, 11 Mar 2024 09:00:00 GMT"),
            first.published,
        )

        // lastEpisodePublished must reflect the newest item, not the oldest.
        assertEquals(
            FeedParser.parseDate("Tue, 12 Mar 2024 09:00:00 GMT"),
            feed.podcast.lastEpisodePublished,
        )
    }

    @Test
    fun `episode uuids are stable across re-parses of the same feed`() {
        val xml = """
            <rss version="2.0"><channel><title>S</title>
            <item><guid>abc</guid><enclosure url="https://e.com/1.mp3"/></item>
            </channel></rss>
        """.trimIndent()

        val first = parse(xml).episodes.single().uuid
        val second = parse(xml).episodes.single().uuid

        assertEquals(first, second)
    }

    @Test
    fun `podcast uuid is keyed on the url not the body`() {
        val before = parse(
            "<rss><channel><title>Old Name</title></channel></rss>",
        ).podcast.uuid

        val after = parse(
            "<rss><channel><title>New Name</title><item><guid>x</guid></item></channel></rss>",
        ).podcast.uuid

        assertEquals("uuid must not fork when feed contents change", before, after)
    }

    @Test
    fun `different feeds get different uuids`() {
        val a = parser.parse("https://a.com/f.xml", "<rss><channel><title>A</title></channel></rss>")
        val b = parser.parse("https://b.com/f.xml", "<rss><channel><title>B</title></channel></rss>")
        assertNotEquals(a.podcast.uuid, b.podcast.uuid)
    }

    @Test
    fun `episodes without a guid fall back to the media url`() {
        val feed = parse(
            """
            <rss><channel><title>S</title>
            <item><title>No guid</title><enclosure url="https://e.com/only.mp3"/></item>
            </channel></rss>
            """.trimIndent(),
        )
        val episode = feed.episodes.single()
        assertTrue(episode.uuid.isNotBlank())
        // Re-parsing the same body must still collide on the same row.
        assertEquals(episode.uuid, parse(
            """
            <rss><channel><title>S</title>
            <item><title>No guid</title><enclosure url="https://e.com/only.mp3"/></item>
            </channel></rss>
            """.trimIndent(),
        ).episodes.single().uuid)
    }

    // --- Atom ----------------------------------------------------------------

    @Test
    fun `parses an atom feed with enclosure links`() {
        val feed = parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Cast</title>
              <subtitle>Atom flavoured</subtitle>
              <entry>
                <title>Atom Episode</title>
                <id>tag:example.com,2024:1</id>
                <updated>2024-03-11T09:00:00Z</updated>
                <link rel="enclosure" type="audio/mpeg" href="https://e.com/a.mp3"/>
              </entry>
            </feed>
            """.trimIndent(),
        )

        assertEquals("Atom Cast", feed.podcast.title)
        assertEquals("Atom flavoured", feed.podcast.description)

        val episode = feed.episodes.single()
        assertEquals("Atom Episode", episode.title)
        assertEquals("https://e.com/a.mp3", episode.audioUrl)
        assertEquals("audio/mpeg", episode.fileType)
        assertEquals(FeedParser.parseDate("2024-03-11T09:00:00Z"), episode.published)
    }

    @Test
    fun `ignores atom links that are not enclosures`() {
        val feed = parse(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>A</title>
              <entry>
                <title>E</title>
                <link rel="alternate" href="https://example.com/page"/>
                <link rel="enclosure" href="https://example.com/real.mp3"/>
              </entry>
            </feed>
            """.trimIndent(),
        )
        assertEquals("https://example.com/real.mp3", feed.episodes.single().audioUrl)
    }

    // --- Hostile input -------------------------------------------------------

    @Test
    fun `channel title wins over a nested image title`() {
        val feed = parse(
            """
            <rss><channel>
              <title>Real Show</title>
              <image><url>https://e.com/i.png</url><title>Artwork Title</title></image>
            </channel></rss>
            """.trimIndent(),
        )
        assertEquals("Real Show", feed.podcast.title)
    }

    @Test
    fun `cdata in descriptions does not break the parse`() {
        val feed = parse(
            """
            <rss><channel>
              <title>CDATA Cast</title>
              <description><![CDATA[<p>Rich <b>HTML</b> description</p>]]></description>
              <item><title><![CDATA[Ep & One]]></title>
              <enclosure url="https://e.com/1.mp3"/></item>
            </channel></rss>
            """.trimIndent(),
        )
        assertEquals("CDATA Cast", feed.podcast.title)
        assertEquals(1, feed.episodes.size)
        assertTrue(feed.podcast.description!!.contains("HTML"))
    }

    @Test
    fun `unparseable date falls back to zero without dropping the episode`() {
        val feed = parse(
            """
            <rss><channel><title>S</title>
            <item><title>E</title><pubDate>not a date at all</pubDate>
            <enclosure url="https://e.com/1.mp3"/></item>
            </channel></rss>
            """.trimIndent(),
        )
        val episode = feed.episodes.single()
        assertEquals(0L, episode.published)
    }

    @Test
    fun `missing duration and enclosure do not crash the parse`() {
        val feed = parse(
            """
            <rss><channel><title>S</title>
            <item><title>Bare episode</title></item>
            </channel></rss>
            """.trimIndent(),
        )
        val episode = feed.episodes.single()
        assertEquals("Bare episode", episode.title)
        assertEquals("", episode.audioUrl)
        assertNull(episode.durationSeconds)
        assertNull(episode.fileType)
    }

    @Test
    fun `empty channel yields a podcast and no episodes`() {
        val feed = parse("<rss><channel><title>Quiet</title></channel></rss>")
        assertEquals("Quiet", feed.podcast.title)
        assertTrue(feed.episodes.isEmpty())
        assertEquals(0L, feed.podcast.lastEpisodePublished)
    }

    @Test
    fun `feed with no title still yields a usable podcast`() {
        val feed = parse("<rss><channel></channel></rss>")
        assertEquals("Untitled show", feed.podcast.title)
    }

    @Test
    fun `description is truncated to a sane length`() {
        val long = "x".repeat(5_000)
        val feed = parse("<rss><channel><title>S</title><description>$long</description></channel></rss>")
        assertEquals(401, feed.podcast.description!!.length)
    }

    @Test
    fun `an xml declaration and leading whitespace are tolerated`() {
        val feed = parse(
            """

            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>Spaced</title></channel></rss>
            """.trimIndent(),
        )
        assertEquals("Spaced", feed.podcast.title)
    }

    @Test
    fun `a feed whose body is utf-8 bytes parses correctly`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss><channel><title>Café Notes</title></channel></rss>""".trimIndent()

        val feed = parser.parse(feedUrl, xml.toByteArray(Charsets.UTF_8))

        assertEquals("Café Notes", feed.podcast.title)
    }

    @Test
    fun `a feed declaring iso-8859-1 is decoded with that charset`() {
        val expected = "Café Notes"
        val prologue = """<?xml version="1.0" encoding="ISO-8859-1"?>"""
            .plus("<rss><channel><title>")
        val epilogue = "</title></channel></rss>"

        val bytes = prologue.toByteArray(Charsets.ISO_8859_1) +
            expected.toByteArray(Charsets.ISO_8859_1) +
            epilogue.toByteArray(Charsets.ISO_8859_1)

        assertEquals(expected, parser.parse(feedUrl, bytes).podcast.title)
    }

    @Test
    fun `an undeclared encoding falls back to utf-8`() {
        val expected = "Café Notes"
        val bytes = ("<rss><channel><title>$expected</title></channel></rss>")
            .toByteArray(Charsets.UTF_8)

        assertEquals(expected, parser.parse(feedUrl, bytes).podcast.title)
    }

    @Test
    fun `a utf-8 bom is consumed rather than becoming part of the title`() {
        val xml = "<?xml version=\"1.0\"?><rss><channel><title>Bommed</title></channel></rss>"
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            xml.toByteArray(Charsets.UTF_8)

        assertEquals("Bommed", parser.parse(feedUrl, withBom).podcast.title)
    }

    @Test
    fun `a doctype with an internal subset does not derail the parse`() {
        val feed = parse(
            """
            <?xml version="1.0"?>
            <!DOCTYPE rss [ <!ENTITY brand "Lohe"> ]>
            <rss version="2.0"><channel><title>Doc Typed</title></channel></rss>
            """.trimIndent(),
        )
        assertEquals("Doc Typed", feed.podcast.title)
    }

    @Test
    fun `stripProlog removes the declaration and leading whitespace`() {
        assertEquals(
            "<rss/>",
            FeedParser().stripProlog("""  <?xml version="1.0" encoding="UTF-8"?><rss/>"""),
        )
        assertEquals(
            "<rss/>",
            FeedParser().stripProlog("""<!-- a comment --><?xml version="1.0"?><rss/>"""),
        )
        assertEquals(
            "<rss/>",
            FeedParser().stripProlog("""<!DOCTYPE rss [ <!ENTITY a "b"> ]><rss/>"""),
        )
        assertEquals("<rss/>", FeedParser().stripProlog("<rss/>"))
    }

    @Test
    fun `rss image child supplies artwork when itunes is absent`() {
        val feed = parse(
            """
            <rss><channel><title>S</title>
              <image><url>https://e.com/cover.png</url><title>Cover</title></image>
            </channel></rss>
            """.trimIndent(),
        )
        assertEquals("https://e.com/cover.png", feed.podcast.artworkUrl)
        // The nested <image><title> must not overwrite the channel title.
        assertEquals("S", feed.podcast.title)
    }

    // --- Helpers -------------------------------------------------------------

    @Test
    fun `parseDuration handles colon and bare forms`() {
        assertEquals(3723, FeedParser.parseDuration("1:02:03"))
        assertEquals(123, FeedParser.parseDuration("2:03"))
        assertEquals(45, FeedParser.parseDuration("45"))
        assertNull(FeedParser.parseDuration(""))
        assertNull(FeedParser.parseDuration(null))
        assertNull(FeedParser.parseDuration("1:xx:03"))
    }

    @Test
    fun `parseDate accepts the three common formats and rejects the rest`() {
        assertNotNull(FeedParser.parseDate("Mon, 11 Mar 2024 09:00:00 GMT"))
        assertNotNull(FeedParser.parseDate("2024-03-11T09:00:00Z"))
        assertNull(FeedParser.parseDate("11/03/2024"))
        assertNull(FeedParser.parseDate(null))
    }

    @Test
    fun `parseDate yields identical instants for equivalent notations`() {
        val rfc = FeedParser.parseDate("Mon, 11 Mar 2024 09:00:00 GMT")
        val iso = FeedParser.parseDate("2024-03-11T09:00:00Z")
        assertEquals(rfc, iso)
    }

    @Test
    fun `stableUuid returns a fresh uuid for a blank seed`() {
        assertNotEquals(
            FeedParser.stableUuid("feed", null),
            FeedParser.stableUuid("feed", "   "),
        )
    }

    @Test
    fun `stableUuid namespaces its seeds`() {
        assertNotEquals(
            FeedParser.stableUuid("feed", "same"),
            FeedParser.stableUuid("other", "same"),
        )
    }

    @Test
    fun `episode uuid does not collide across different podcasts`() {
        val a = parser.parse(
            "https://a.com/f.xml",
            """<rss><channel><title>A</title><item><guid>shared</guid>
               <enclosure url="https://a.com/1.mp3"/></item></channel></rss>""",
        ).episodes.single()
        val b = parser.parse(
            "https://b.com/f.xml",
            """<rss><channel><title>B</title><item><guid>shared</guid>
               <enclosure url="https://b.com/1.mp3"/></item></channel></rss>""",
        ).episodes.single()

        // Both publishers reused the guid "shared"; the namespace keeps them apart.
        assertNotEquals(a.uuid, b.uuid)
        assertNotEquals(a.podcastUuid, b.podcastUuid)
    }

    @Test
    fun `parser instances are reusable`() {
        val first = parse("<rss><channel><title>One</title></channel></rss>")
        val second = parse("<rss><channel><title>Two</title></channel></rss>")
        assertEquals("One", first.podcast.title)
        assertEquals("Two", second.podcast.title)
    }

    @Suppress("unused")
    private fun EpisodeEntity.assertMedia(url: String) {
        assertEquals(url, audioUrl)
    }

    // --- Blurb cleaning ----------------------------------------------------
    //
    // Descriptions arrive as CDATA-wrapped HTML. Storing them raw put literal
    // `<p>` tags in front of the listener.

    @Test
    fun `tags are stripped from a description`() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <title>Show</title>
              <description><![CDATA[<p>Follow along as we build a homestead.</p>]]></description>
            </channel></rss>
        """.trimIndent()

        val parsed = FeedParser().parse("https://x.com/f.xml", xml)
        assertEquals("Follow along as we build a homestead.", parsed.podcast.description)
    }

    @Test
    fun `nested inline markup is stripped`() {
        assertEquals(
            "Bold and a link",
            FeedParser.plainText("<b>Bold</b> and <a href='x'>a link</a>"),
        )
    }

    @Test
    fun `block tags become spaces, not run-together words`() {
        assertEquals(
            "One Two Three",
            FeedParser.plainText("<p>One</p><p>Two</p><br/>Three"),
        )
    }

    @Test
    fun `named entities are decoded`() {
        assertEquals(
            "Tom & Jerry's \"best\"",
            FeedParser.plainText("Tom &amp; Jerry&apos;s &quot;best&quot;"),
        )
    }

    @Test
    fun `numeric entities are decoded`() {
        assertEquals("Aé—", FeedParser.plainText("A&#233;&#x2014;"))
    }

    @Test
    fun `an unknown entity degrades to a space`() {
        // Dropping it silently would weld two words together.
        assertEquals("a b", FeedParser.plainText("a &notarealentity; b"))
    }

    @Test
    fun `runs of whitespace collapse`() {
        assertEquals("one two", FeedParser.plainText("one  \n\n  two"))
    }

    @Test
    fun `a long blurb is clipped on a word boundary`() {
        val text = FeedParser.plainText("word ".repeat(200), limit = 40)
        assertTrue(text.endsWith("…"))
        assertTrue("must not cut mid-word", text.dropLast(1).endsWith("word"))
    }

    @Test
    fun `an unbreakable run is still returned rather than dropped`() {
        val text = FeedParser.plainText("x".repeat(500), limit = 40)
        assertEquals(41, text.length)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun `empty input yields an empty blurb`() {
        assertEquals("", FeedParser.plainText(""))
    }

    @Test
    fun `a description of only markup yields an empty blurb`() {
        assertEquals("", FeedParser.plainText("<p><br/></p>   "))
    }
}
