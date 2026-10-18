package com.lohe.app.data.fetch

import com.lohe.app.data.db.entity.PodcastEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One subscription as represented in an OPML document. */
data class OpmlSubscription(
    val title: String,
    val feedUrl: String,
    val htmlUrl: String? = null,
)

/**
 * OPML 2.0 reader/writer for podcast subscriptions.
 *
 * Pure and Android-free so it can be unit tested directly.
 *
 * OPML in the wild is inconsistent: most writers nest outlines under category
 * folders, some omit `title` in favour of `text`, and `xmlUrl` is the only
 * attribute we can rely on. Parsing therefore walks the whole tree looking for
 * any outline that carries an `xmlUrl`, at any depth, and ignores everything
 * it does not understand.
 */
object OpmlCodec {

    private val RFC_1123: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    /**
     * Serialises subscriptions to an OPML 2.0 document.
     *
     * Categories are preserved as parent outlines so a round trip through
     * another podcast app keeps the user's grouping.
     */
    fun write(
        subscriptions: List<OpmlSubscription>,
        categories: Map<String, List<OpmlSubscription>> = emptyMap(),
        title: String = "Lohe subscriptions",
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<opml version="2.0">""")
        appendLine("""  <head>""")
        appendLine("""    <title>${escape(title)}</title>""")
        appendLine(
            """    <dateCreated>${RFC_1123.format(ZonedDateTime.now(java.time.ZoneOffset.UTC))}""" +
                "</dateCreated>",
        )
        appendLine("""  </head>""")
        appendLine("""  <body>""")

        subscriptions.forEach { appendOutline(it, indent = "    ") }

        categories.forEach { (category, items) ->
            appendLine("""    <outline text="${escape(category)}" title="${escape(category)}">""")
            items.forEach { appendOutline(it, indent = "      ") }
            appendLine("""    </outline>""")
        }

        appendLine("""  </body>""")
        append("</opml>")
    }

    private fun StringBuilder.appendOutline(item: OpmlSubscription, indent: String) {
        append(indent)
        append("""<outline text="${escape(item.title)}"""")
        append(""" title="${escape(item.title)}"""")
        append(""" type="rss"""")
        append(""" xmlUrl="${escape(item.feedUrl)}"""")
        item.htmlUrl?.let { append(""" htmlUrl="${escape(it)}"""") }
        appendLine(" />")
    }

    /**
     * Reads every subscription in an OPML document, at any nesting depth.
     *
     * Outlines without an `xmlUrl` are treated as folders and descended into
     * rather than rejected — that is how virtually every real OPML file is
     * structured.
     */
    fun read(opml: String): List<OpmlSubscription> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(opml))
        }

        val out = mutableListOf<OpmlSubscription>()
        var sawOpmlRoot = false

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                if (parser.name.equals("opml", ignoreCase = true)) sawOpmlRoot = true
                if (parser.name.equals("outline", ignoreCase = true)) {
                    val feedUrl = parser.attr("xmlUrl")
                    if (feedUrl.isNullOrBlank()) {
                        // A folder: keep walking, its children are the real rows.
                    } else {
                        val title = parser.attr("title")
                            ?: parser.attr("text")
                            ?: feedUrl
                        out += OpmlSubscription(
                            title = title.trim().ifEmpty { feedUrl },
                            feedUrl = feedUrl.trim(),
                            htmlUrl = parser.attr("htmlUrl"),
                        )
                    }
                }
            }
            ev = parser.next()
        }

        // Some tools export a bare outline list with no <opml> wrapper; that is
        // still importable, so only a total absence of any content is an error.
        if (!sawOpmlRoot && out.isEmpty()) {
            throw IllegalArgumentException("Not an OPML document: no <opml> root element")
        }
        return out
    }

    private fun XmlPullParser.attr(name: String): String? =
        runCatching { getAttributeValue(null, name) }.getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun escape(raw: String): String = buildString(raw.length) {
        raw.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /** Projects the library into the flat shape OPML wants. */
    fun fromPodcasts(podcasts: List<PodcastEntity>): List<OpmlSubscription> =
        podcasts.mapNotNull { podcast ->
            if (podcast.feedUrl.isBlank()) {
                null // device-local imports have no feed to hand to another app
            } else {
                OpmlSubscription(title = podcast.title, feedUrl = podcast.feedUrl)
            }
        }
}
