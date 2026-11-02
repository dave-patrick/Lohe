package com.lohe.app.data

import com.lohe.app.data.db.entity.EpisodeSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaylistEpisodeSortTest {
    private fun entry(
        id: String, title: String = id, show: String = "Show", value: Long? = null,
    ) = QueueEntry(
        EpisodeSummary(
            uuid = id, podcastUuid = "show", title = title, audioUrl = "https://example.com/$id",
            published = value ?: 0, durationSeconds = value?.toInt(), sizeBytes = value,
        ), show,
    )

    private fun ids(entries: List<QueueEntry>) = entries.map { it.episode.uuid }

    @Test
    fun `numeric sorts keep unknown values last in either direction and ties ascending`() {
        val input = listOf(
            entry("null", value = null), entry("large", value = Long.MAX_VALUE),
            entry("z", title = " SAME ", value = 5), entry("negative", value = -1),
            entry("small", value = 1), entry("a", title = "same", value = 5),
            entry("zero", value = 0),
        )
        for (field in listOf(PlaylistEpisodeSortField.FILE_SIZE, PlaylistEpisodeSortField.PUBLISH_DATE)) {
            assertEquals(listOf("small", "a", "z", "large", "negative", "null", "zero"),
                ids(sortPlaylistEpisodes(input, PlaylistEpisodeSort(field))))
            assertEquals(listOf("large", "a", "z", "small", "negative", "null", "zero"),
                ids(sortPlaylistEpisodes(input, PlaylistEpisodeSort(field, PlaylistEpisodeSortDirection.DESCENDING))))
        }
        // Duration is an Int; use values that are valid for this field.
        val durations = input.map { if (it.episode.uuid == "large") it.copy(episode = it.episode.copy(durationSeconds = Int.MAX_VALUE)) else it }
        for (direction in PlaylistEpisodeSortDirection.entries) {
            val expected = if (direction == PlaylistEpisodeSortDirection.ASCENDING)
                listOf("small", "a", "z", "large", "negative", "null", "zero")
            else listOf("large", "a", "z", "small", "negative", "null", "zero")
            assertEquals(expected, ids(sortPlaylistEpisodes(durations, PlaylistEpisodeSort(PlaylistEpisodeSortField.LENGTH, direction))))
        }
    }

    @Test
    fun `podcast title ignores whitespace and case with blank titles last`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val input = listOf(
                entry("blank", show = "  "), entry("z", title = " SAME ", show = " india "),
                entry("a", title = "same", show = "INDIA"), entry("alpha", show = "alpha"),
                entry("zulu", show = "Zulu"), entry("empty", show = ""),
            )
            assertEquals(listOf("alpha", "a", "z", "zulu", "blank", "empty"),
                ids(sortPlaylistEpisodes(input, PlaylistEpisodeSort(PlaylistEpisodeSortField.PODCAST_TITLE))))
            assertEquals(listOf("zulu", "a", "z", "alpha", "blank", "empty"),
                ids(sortPlaylistEpisodes(input, PlaylistEpisodeSort(PlaylistEpisodeSortField.PODCAST_TITLE, PlaylistEpisodeSortDirection.DESCENDING))))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `manual preserves input and sorting never mutates membership order`() {
        val input = mutableListOf(entry("z", value = 9), entry("a", value = 1))
        val original = input.toList()
        for (direction in PlaylistEpisodeSortDirection.entries) {
            assertEquals(original, sortPlaylistEpisodes(input, PlaylistEpisodeSort(PlaylistEpisodeSortField.MANUAL, direction)))
        }
        sortPlaylistEpisodes(input, PlaylistEpisodeSort(PlaylistEpisodeSortField.FILE_SIZE))
        assertEquals(original, input)
    }

    @Test
    fun `all sort choices round trip and malformed preferences fall back to manual`() {
        for (field in PlaylistEpisodeSortField.entries) {
            for (direction in PlaylistEpisodeSortDirection.entries) {
                val sort = PlaylistEpisodeSort(field, direction)
                assertEquals("${field.name}:${direction.name}", sort.encode())
                assertEquals(sort, PlaylistEpisodeSort.decode(sort.encode()))
            }
        }
        for (value in listOf(null, "", "FILE_SIZE", "FILE_SIZE:UNKNOWN", "UNKNOWN:ASCENDING", "FILE_SIZE:ASCENDING:extra", "file_size:ASCENDING", ":")) {
            assertEquals(PlaylistEpisodeSort(), PlaylistEpisodeSort.decode(value))
        }
    }
}
