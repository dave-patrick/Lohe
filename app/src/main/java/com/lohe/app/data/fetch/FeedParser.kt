package com.lohe.app.data.fetch

import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Pure bytes/XML -> model mapping for podcast feeds.
 *
 * Deliberately free of Android, network, and database types so it can be unit
 * tested directly: the caller supplies the raw feed bytes.
 *
 * Feeds in the wild are inconsistent — RSS 2.0 and Atom both occur, namespaces
 * vary by publisher, and dates appear in several formats. Parsing is therefore
 * defensive: anything unreadable degrades to null instead of throwing, and only
 * a structurally broken document aborts the parse.
 */
class FeedParser(
    private val parserFactory: () -> XmlPullParser = {
        XmlPullParserFactory.newInstance().newPullParser()
    },
) {

    data class ParsedFeed(val podcast: PodcastEntity, val episodes: List<EpisodeEntity>)

    /**
     * Parses a raw feed document.
     *
     * Encoding and the `<?xml ?>` prolog are resolved here rather than handed to
     * the pull parser. Two reasons: implementations differ on whether they
     * accept a prolog at all (upstream kxml2 rejects it outright), and some
     * ignore the encoding argument and decode UTF-8 as Latin-1 — which silently
     * mangles every non-ASCII character in a title. Owning both steps makes
     * behaviour identical on-device and under test.
     *
     * @throws org.xmlpull.v1.XmlPullParserException if the body is not well-formed.
     */
    fun parse(feedUrl: String, bytes: ByteArray): ParsedFeed {
        val text = stripProlog(decode(bytes))

        val parser = parserFactory().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(text))
        }

        // Keyed on the URL, not the body: re-subscribing to a feed whose
        // contents changed must update the show, not fork a second row.
        val podcastUuid = stableUuid("feed", feedUrl)

        var title: String? = null
        var author: String? = null
        var summary: String? = null
        var artwork: String? = null
        val episodes = mutableListOf<EpisodeEntity>()

        // Depth of <channel>/<feed>; its direct children sit one level below.
        var rootDepth = -1

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                // iTunes puts artwork on a namespaced, attribute-only element;
                // `localName` would strip the prefix and collide it with RSS's
                // <image>, so the qualified name is checked first.
                if (parser.name == "itunes:image") {
                    artwork = parser.getAttributeValue(null, "href") ?: artwork
                } else {
                    when (parser.localName) {
                        "channel", "feed" -> if (rootDepth < 0) rootDepth = parser.depth

                        "item", "entry" -> {
                            episodes += parseItem(parser, podcastUuid)
                            // parseItem stops on the item's END_TAG; step past it
                            // so the outer loop does not revisit the element.
                            ev = parser.next()
                            continue
                        }

                        "title" -> title = title ?: parser.nextTextOrNull()
                        "author", "creator", "managingEditor" ->
                            author = author ?: parser.nextTextOrNull()
                        "description", "summary", "subtitle" ->
                            summary = summary ?: parser.nextMarkupTextOrNull()

                        "image" -> if (parser.depth == rootDepth + 1) {
                            artwork = artwork ?: channelImageUrl(parser)
                        }
                    }
                }
            }
            ev = parser.next()
        }

        val resolvedEpisodes = if (artwork != null) {
            episodes.map { ep ->
                if (ep.artworkUrl.isNullOrBlank()) ep.copy(artworkUrl = artwork) else ep
            }
        } else {
            episodes
        }

        return ParsedFeed(
            podcast = PodcastEntity(
                uuid = podcastUuid,
                title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled show",
                author = author?.trim()?.takeIf { it.isNotEmpty() },
                description = summary?.let { plainText(it) }?.takeIf { it.isNotEmpty() },
                feedUrl = feedUrl,
                artworkUrl = artwork,
                lastEpisodePublished = resolvedEpisodes.maxOfOrNull { it.published } ?: 0L,
            ),
            episodes = resolvedEpisodes,
        )
    }

    /** Convenience overload for callers and tests that already hold text. */
    fun parse(feedUrl: String, xml: String): ParsedFeed =
        parse(feedUrl, xml.toByteArray(Charsets.UTF_8))

    /**
     * Drops the XML prolog and any DOCTYPE, returning the document from its
     * root element.
     *
     * kxml2 rejects a leading prolog outright, and a DOCTYPE with an internal
     * subset is legal but not always handled, so both are removed here rather
     * than left for the pull parser to trip over.
     */
    internal fun stripProlog(text: String): String {
        var i = 0
        while (i < text.length) {
            when {
                text[i].isWhitespace() -> i++

                text.startsWith("<?", i) -> {
                    val end = text.indexOf("?>", i)
                    // An unterminated prolog is malformed; treating the rest as
                    // the body beats throwing away the whole feed.
                    i = if (end < 0) text.length else end + 2
                }

                text.startsWith("<!--", i) -> {
                    val end = text.indexOf("-->", i)
                    i = if (end < 0) text.length else end + 3
                }

                text.startsWith("<!DOCTYPE", i, ignoreCase = true) ->
                    i = skipDoctype(text, i)

                text[i] == '<' -> return text.substring(i)

                else -> return text.substring(i)
            }
        }
        return text
    }

    /** Advances past a DOCTYPE, honouring an internal `[ ... ]` subset. */
    private fun skipDoctype(text: String, start: Int): Int {
        var i = start + "<!DOCTYPE".length
        var depth = 0
        while (i < text.length) {
            when (text[i]) {
                '[' -> depth++
                ']' -> depth--
                '>' -> if (depth <= 0) return i + 1
            }
            i++
        }
        return text.length
    }

    /** Reads `<image><url>` without mistaking the sibling `<image><title>` for it. */
    private fun channelImageUrl(parser: XmlPullParser): String? {
        val endDepth = parser.depth
        var url: String? = null

        var ev = parser.next()
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && parser.depth == endDepth) break
            if (ev == XmlPullParser.START_TAG && parser.localName == "url") {
                url = parser.nextTextOrNull()
            }
            ev = parser.next()
        }
        return url
    }

    /**
     * Consumes the current item/entry element and returns the episode.
     *
     * Assumes the parser is parked on the item's START_TAG; leaves it on the
     * matching END_TAG.
     */
    private fun parseItem(parser: XmlPullParser, podcastUuid: String): EpisodeEntity {
        val endName = parser.localName
        val endDepth = parser.depth

        var title: String? = null
        var audioUrl: String? = null
        var mimeType: String? = null
        var sizeBytes: Long? = null
        var published = 0L
        var durationSeconds: Int? = null
        var guid: String? = null
        var author: String? = null
        var itemArtwork: String? = null
        var itemDescription: String? = null

        var ev = parser.next()
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG &&
                parser.depth == endDepth &&
                parser.localName == endName
            ) {
                break
            }

            if (ev == XmlPullParser.START_TAG) {
                if (parser.name == "itunes:image") {
                    itemArtwork = parser.getAttributeValue(null, "href") ?: itemArtwork
                } else {
                    when (parser.localName) {
                        "title" -> title = title ?: parser.nextTextOrNull()
                        "guid", "id" -> guid = guid ?: parser.nextTextOrNull()
                        "pubDate", "published", "updated", "date" ->
                            published = parseDate(parser.nextTextOrNull()) ?: published
                        "duration" ->
                            durationSeconds = parseDuration(parser.nextTextOrNull()) ?: durationSeconds
                        "author", "creator" -> author = author ?: parser.nextTextOrNull()
                        "description", "summary", "subtitle", "encoded", "content" ->
                            itemDescription = itemDescription ?: parser.nextMarkupTextOrNull()

                        "image", "thumbnail" -> {
                            itemArtwork = parser.getAttributeValue(null, "href")
                                ?: parser.getAttributeValue(null, "url")
                                ?: itemArtwork
                        }

                        "enclosure" -> {
                            audioUrl = parser.getAttributeValue(null, "url") ?: audioUrl
                            mimeType = parser.getAttributeValue(null, "type") ?: mimeType
                            sizeBytes = parser.getAttributeValue(null, "length")?.toLongOrNull()
                                ?: sizeBytes
                        }

                        // Atom carries audio as <link rel="enclosure" href="..."/>
                        "link" -> if (parser.getAttributeValue(null, "rel") == "enclosure") {
                            audioUrl = parser.getAttributeValue(null, "href") ?: audioUrl
                            mimeType = parser.getAttributeValue(null, "type") ?: mimeType
                        }
                    }
                }
            }
            ev = parser.next()
        }

        val media = audioUrl.orEmpty()
        return EpisodeEntity(
            // Keying on the publisher's own id (falling back to the media URL)
            // keeps a refresh from piling up duplicates.
            uuid = stableUuid(podcastUuid, guid ?: media.takeIf { it.isNotBlank() }),
            podcastUuid = podcastUuid,
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled episode",
            audioUrl = media,
            published = published,
            durationSeconds = durationSeconds,
            sizeBytes = sizeBytes,
            fileType = mimeType,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            artworkUrl = itemArtwork?.trim()?.takeIf { it.isNotEmpty() },
            description = itemDescription?.let { plainText(it, limit = 2_000) }?.takeIf { it.isNotEmpty() },
        )
    }

    /* ---------- Companion object: pure helpers with no parser state ---------- */

    internal companion object {
        /**
         * Reduces a feed's HTML-ish blurb to readable plain text.
         *
         * Podcast descriptions are CDATA-wrapped HTML, so storing them raw
         * shows the listener literal `<p>` tags and unescaped entities. Block
         * boundaries become spaces rather than newlines because the result
         * renders inside a single paragraph; the result is then clipped on a
         * word boundary, since cutting mid-word reads as corruption.
         */
        fun plainText(raw: String, limit: Int = 400): String {
            if (raw.isEmpty()) return ""
            val withBreaks = BLOCK_BREAK.replace(raw, " ")
            val noTags = TAG.replace(withBreaks, "")
            val decoded = ENTITIES.replace(noTags) { match ->
                // Malformed references degrade to a space rather than being
                // dropped, so odd input never welds two words together.
                val body = match.groupValues[1]
                val numeric = NUMERIC_ENTITY.matchEntire(body)
                val code = numeric?.let {
                    val digits = it.groupValues[1]
                    if (digits.startsWith("x", ignoreCase = true)) {
                        digits.drop(1).toIntOrNull(16)
                    } else {
                        digits.toIntOrNull()
                    }
                }
                when {
                    numeric != null ->
                        code?.takeIf { it in 1..0x10FFFF }
                            ?.let { Character.toChars(it).concatToString() }
                            ?: " "
                    else -> NAMED_ENTITIES[body.lowercase()] ?: " "
                }
            }
            val collapsed = decoded.replace(WHITESPACE, " ").trim()
            if (collapsed.length <= limit) return collapsed
            val clipped = collapsed.take(limit)
            val lastSpace = clipped.lastIndexOf(' ')
            // A long unbroken run (a URL, say) has no space to break on;
            // keeping it beats returning nothing.
            val body = if (lastSpace > limit / 2) clipped.take(lastSpace) else clipped
            return "$body…"
        }

        private val BLOCK_BREAK = Regex("""(?i)</?(?:p|div|br|li|ul|ol|h[1-6]|tr)\b[^>]*>""")
        private val TAG = Regex("<[^>]*>")
        private val WHITESPACE = Regex("\\s+")
        private val NUMERIC_ENTITY = Regex("""#(x[0-9a-fA-F]+|\d+)""")
        private val ENTITIES = Regex("""&([^;\s]+);""")

        // A handful of entities that show up in podcast feeds; everything else
        // falls through the numeric branch above.
        private val NAMED_ENTITIES = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "hellip" to "...",
            "mdash" to "—",
            "ndash" to "–",
            "rsquo" to "'",
            "lsquo" to "'",
            "ldquo" to "\"",
            "rdquo" to "\"",
        )

        private val ENCODING_DECL = Regex("""<\?xml[^>]*\bencoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val RFC_1123: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
        private val RFC_822: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US)

        /**
         * Decodes feed bytes, honouring a UTF-8/UTF-16 BOM and then the encoding
         * named in the XML declaration. Falls back to UTF-8, which is what an
         * undeclared or unreadable declaration almost always means.
         */
        internal fun decode(bytes: ByteArray): String {
            bomCharset(bytes)?.let { (charset, offset) ->
                return String(bytes, offset, bytes.size - offset, charset)
            }

            // Read a short prefix as ASCII to sniff the declaration; the body itself
            // is decoded with whatever the declaration says.
            val probe = String(bytes, 0, minOf(bytes.size, 200), Charsets.US_ASCII)
            val declared = ENCODING_DECL.find(probe)
                ?.groupValues?.get(1)
                ?.trim()
                ?.let { name ->
                    runCatching { Charset.forName(name) }.getOrNull()
                        ?: runCatching { Charset.forName(name.replace("-", "")) }.getOrNull()
                }

            return String(bytes, declared ?: Charsets.UTF_8)
        }

        private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                Charsets.UTF_16BE to 2
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                Charsets.UTF_16LE to 2
            else -> null
        }

        /** Stable id for a feed or episode, derived from its URL/guid. */
        internal fun stableUuid(vararg parts: String?): String {
            // Null and blank strings must be distinguishable: a feed without a
            // guid should not share an episode uuid with one whose guid is " ".
            val seed = parts.joinToString("/") { p ->
                when {
                    p == null -> "null"
                    p.isBlank() -> p  // whitespace is significant to distinguish from null
                    else -> p
                }
            }
            return UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
        }

        /**
         * Parses a `pubDate`/`published` timestamp, trying ISO-8601 first and
         * falling back to RFC-1123 and a loose RFC-822 form. Returns the
         * epoch second, the same representation stored in the database.
         */
        internal fun parseDate(raw: String?): Long? = raw?.let { s ->
            listOf(
                { ZonedDateTime.parse(s) },
                { ZonedDateTime.parse(s, RFC_822) },
                { ZonedDateTime.parse(s, RFC_1123) },
            ).firstNotNullOfOrNull { runCatching(it).getOrNull() }
                ?.toEpochSecond()
        }

        /** Parses an iTunes `duration` value (seconds, `mm:ss`, or `hh:mm:ss`). */
        internal fun parseDuration(raw: String?): Int? = raw?.let { s ->
            val trimmed = s.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.all { c -> c.isDigit() }) return trimmed.toIntOrNull()
            val parts = trimmed.split(":")
            if (parts.size == 3) {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val sec = parts[2].toIntOrNull() ?: return null
                h * 3600 + m * 60 + sec
            } else if (parts.size == 2) {
                val m = parts[0].toIntOrNull() ?: return null
                val sec = parts[1].toIntOrNull() ?: return null
                m * 60 + sec
            } else {
                null
            }
        }
    }
}

/* ---------- Top-level extension properties ---------- */

private val XmlPullParser.localName: String
    get() = name?.substringAfterLast(':').orEmpty()

/**
 * Reads this element's text, leaving the parser on its END_TAG. Returns null
 * rather than throwing so one malformed element cannot abort the whole feed.
 */
internal fun XmlPullParser.nextTextOrNull(): String? = runCatching { nextText() }.getOrNull()

internal fun XmlPullParser.nextMarkupTextOrNull(): String? = runCatching {
    val startDepth = depth
    val content = StringBuilder()
    var event = next()
    while (event != XmlPullParser.END_DOCUMENT &&
        !(event == XmlPullParser.END_TAG && depth == startDepth)
    ) {
        when (event) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF ->
                content.append(this.text ?: this.name.orEmpty())
            XmlPullParser.START_TAG, XmlPullParser.END_TAG -> content.append(' ')
        }
        event = next()
    }
    content.toString()
}.getOrNull()
