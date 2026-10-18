package com.lohe.app.data.fetch

import com.lohe.app.data.db.entity.PodcastEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlCodecTest {

    private fun podcasts(vararg rows: Pair<String, String>) = rows.map { (title, feed) ->
        PodcastEntity(
            uuid = title,
            title = title,
            description = null,
            feedUrl = feed,
            artworkUrl = null,
            lastEpisodePublished = 0L,
        )
    }

    // --- Writing -------------------------------------------------------------

    @Test
    fun `writes a valid opml envelope`() {
        val xml = OpmlCodec.write(listOf(OpmlSubscription("Show", "https://e.com/f.xml")))

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""<opml version="2.0">"""))
        assertTrue(xml.contains("<head>"))
        assertTrue(xml.contains("<body>"))
        assertTrue(xml.trimEnd().endsWith("</opml>"))
    }

    @Test
    fun `escapes characters that would break the document`() {
        val xml = OpmlCodec.write(
            listOf(
                OpmlSubscription(
                    title = """Ben & Jerry's <Great> "Show"""",
                    feedUrl = "https://e.com/f.xml?a=1&b=2",
                ),
            ),
        )

        assertTrue(xml.contains("&amp;"))
        assertTrue(xml.contains("&lt;Great&gt;"))
        assertTrue(xml.contains("&quot;"))
        assertTrue(xml.contains("&apos;"))

        // And it must survive a round trip, which is the real assertion.
        val parsed = OpmlCodec.read(xml).single()
        assertEquals("""Ben & Jerry's <Great> "Show"""", parsed.title)
        assertEquals("https://e.com/f.xml?a=1&b=2", parsed.feedUrl)
    }

    @Test
    fun `write then read round trips every subscription`() {
        val original = listOf(
            OpmlSubscription("Alpha", "https://a.com/feed.xml"),
            OpmlSubscription("Beta", "https://b.com/feed.xml", "https://b.com/"),
            OpmlSubscription("Gamma & Co", "https://c.com/f.xml?x=1&y=2"),
        )

        assertEquals(original, OpmlCodec.read(OpmlCodec.write(original)))
    }

    @Test
    fun `categories nest and survive a round trip`() {
        val xml = OpmlCodec.write(
            subscriptions = listOf(OpmlSubscription("Loose", "https://loose.com/f.xml")),
            categories = mapOf("Tech" to listOf(OpmlSubscription("Syntax", "https://s.com/f.xml"))),
        )

        assertTrue(xml.contains("""<outline text="Tech" title="Tech">"""))

        val parsed = OpmlCodec.read(xml)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.title == "Loose" })
        assertTrue(parsed.any { it.title == "Syntax" })
    }

    @Test
    fun `fromPodcasts drops device-local shows with no feed url`() {
        val result = OpmlCodec.fromPodcasts(
            podcasts(
                "Remote" to "https://remote.com/f.xml",
                "Local Folder" to "",
                "Also Local" to "   ",
            ),
        )

        assertEquals(1, result.size)
        assertEquals("Remote", result.single().title)
    }

    // --- Reading -------------------------------------------------------------

    @Test
    fun `reads outlines nested under category folders`() {
        val subscriptions = OpmlCodec.read(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Subs</title></head>
              <body>
                <outline text="Tech" title="Tech">
                  <outline text="Syntax" title="Syntax" type="rss"
                           xmlUrl="https://s.com/f.xml"/>
                  <outline text="Builds" title="Builds" type="rss"
                           xmlUrl="https://b.com/f.xml"/>
                </outline>
                <outline text="Loose" type="rss" xmlUrl="https://loose.com/f.xml"/>
              </body>
            </opml>
            """.trimIndent(),
        )

        assertEquals(
            listOf("Syntax", "Builds", "Loose"),
            subscriptions.map { it.title },
        )
    }

    @Test
    fun `falls back to text when title is absent`() {
        val subscriptions = OpmlCodec.read(
            """<opml version="1.0"><body>
               <outline text="Only Text" xmlUrl="https://e.com/f.xml"/>
               </body></opml>""",
        )
        assertEquals("Only Text", subscriptions.single().title)
    }

    @Test
    fun `falls back to the feed url when there is no title at all`() {
        val subscriptions = OpmlCodec.read(
            """<opml version="1.0"><body>
               <outline xmlUrl="https://e.com/f.xml"/>
               </body></opml>""",
        )
        assertEquals("https://e.com/f.xml", subscriptions.single().title)
    }

    @Test
    fun `reads htmlUrl when present`() {
        val subscription = OpmlCodec.read(
            """<opml version="1.0"><body>
               <outline title="S" xmlUrl="https://e.com/f.xml" htmlUrl="https://e.com/"/>
               </body></opml>""",
        ).single()
        assertEquals("https://e.com/", subscription.htmlUrl)
    }

    @Test
    fun `tolerates an xml declaration and a doctype`() {
        val subscriptions = OpmlCodec.read(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE opml>
            <opml version="2.0"><body>
              <outline title="S" xmlUrl="https://e.com/f.xml"/>
            </body></opml>
            """.trimIndent(),
        )
        assertEquals(1, subscriptions.size)
    }

    @Test
    fun `rejects a document that is not opml`() {
        assertThrows(IllegalArgumentException::class.java) {
            OpmlCodec.read("""<?xml version="1.0"?><rss><channel/></rss>""")
        }
    }

    @Test
    fun `an opml document with no subscriptions reads as empty`() {
        val subscriptions = OpmlCodec.read(
            """<opml version="2.0"><head/><body/></opml>""",
        )
        assertTrue(subscriptions.isEmpty())
    }

    @Test
    fun `deeply nested folders are still found`() {
        val subscriptions = OpmlCodec.read(
            """
            <opml version="2.0"><body>
              <outline text="A">
                <outline text="B">
                  <outline text="C">
                    <outline title="Deep" xmlUrl="https://d.com/f.xml"/>
                  </outline>
                </outline>
              </outline>
            </body></opml>
            """.trimIndent(),
        )
        assertEquals("Deep", subscriptions.single().title)
    }

    @Test
    fun `blank xmlUrl entries are skipped rather than imported`() {
        val subscriptions = OpmlCodec.read(
            """
            <opml version="1.0"><body>
              <outline title="Good" xmlUrl="https://good.com/f.xml"/>
              <outline title="Blank" xmlUrl="   "/>
              <outline title="Folder"/>
            </body></opml>
            """.trimIndent(),
        )
        assertEquals(1, subscriptions.size)
        assertEquals("Good", subscriptions.single().title)
    }
}
