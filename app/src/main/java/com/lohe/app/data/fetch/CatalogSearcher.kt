package com.lohe.app.data.fetch

import com.lohe.app.LOHE_USER_AGENT
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** A podcast returned by the catalog, before it is subscribed to. */
data class CatalogPodcast(
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val genre: String?,
    /** Show blurb, for the detail view. Absent for sparse catalog entries. */
    val description: String? = null,
    /** Episodes the catalog knows about; a rough size, not an exact count. */
    val episodeCount: Int? = null,
)

/**
 * Searches the public iTunes podcast catalog.
 *
 * Chosen because it needs no API key and returns feed URLs directly, which is
 * the one thing discovery actually requires — a ranking API without a feed URL
 * would still need a second round trip per result.
 */
class CatalogSearcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun search(term: String, limit: Int = 25): Result<List<CatalogPodcast>> =
        withContext(io) {
            val query = term.trim()
            if (query.isEmpty()) return@withContext Result.success(emptyList())

            val url = ITUNES.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("media", "podcast")
                ?.addQueryParameter("entity", "podcast")
                ?.addQueryParameter("limit", limit.coerceIn(1, 200).toString())
                ?.addQueryParameter("term", query)
                ?.build()
                ?.toString()
                ?: return@withContext Result.failure(IllegalArgumentException("bad search url"))

            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", LOHE_USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Search failed (HTTP ${response.code})")
                    }
                    val body = response.body?.string()
                        ?: throw IllegalStateException("Search returned an empty body")
                    parseResults(body)
                }
            }
        }

    companion object {
        private const val ITUNES = "https://itunes.apple.com/search"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Maps the iTunes payload to catalog entries.
         *
         * Results without a `feedUrl` are dropped: they cannot be subscribed to,
         * and the iTunes API does return such rows for defunct shows.
         */
        internal fun parseResults(body: String): List<CatalogPodcast> {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return emptyList()

            // Cast rather than the `.jsonArray` accessor: that accessor throws on
            // a non-array, so a hostile or malformed response would crash the
            // search instead of coming back empty.
            val results = (root["results"] as? kotlinx.serialization.json.JsonArray)
                ?: return emptyList()

            return results.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val feedUrl = obj.string("feedUrl")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                CatalogPodcast(
                    title = obj.string("collectionName")?.takeIf { it.isNotBlank() }
                        ?: obj.string("trackName")
                        ?: "Untitled show",
                    author = obj.string("artistName").orEmpty(),
                    feedUrl = feedUrl,
                    artworkUrl = obj.string("artworkUrl600") ?: obj.string("artworkUrl100"),
                    genre = obj.string("primaryGenreName"),
                    // iTunes offers two blurb lengths; the long one is what a
                    // listener expects when opening a show.
                    description = obj.string("longDescriptionText")
                        ?: obj.string("shortDescriptionText"),
                    episodeCount = (obj["trackCount"] as? JsonPrimitive)
                        ?.intOrNull
                        ?.takeIf { it > 0 },
                )
            }
                // iTunes returns one row per album, so a single show routinely
                // arrives several times under the same feedUrl. Presenting the
                // same podcast repeatedly reads as a broken search, and the
                // duplicates also collide as Compose list keys. Keep the first
                // occurrence, which carries the richest metadata.
                .distinctBy { it.feedUrl }
        }

        /** Upgrades the 100px artwork URLs to the 600px variant. */
        internal fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.doubleOrNull != null }
                ?.let { it.contentOrNull ?: it.jsonPrimitive.content }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
