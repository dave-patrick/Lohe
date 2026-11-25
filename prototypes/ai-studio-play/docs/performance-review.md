# Performance Review — Lohe

Scope: `app/src/main/java` + build config. Review only; no code changed.

## Summary
Lohe is generally well-structured (Room indexes, LazyColumn keys, Compose state hoisting in places). Findings below focus on remaining bottlenecks across DB, Compose, playback, network, image loading, memory, coroutines, and build.

## Findings

### Database / Room
1. **HIGH** `EpisodeDao.kt:16-21` `observePodcastStats()` groups over entire `episodes` table; existing single-column index on `podcastUuid` (EpisodeEntity.kt:26-27) helps, but a composite index on `(podcastUuid, isDeleted, playingStatus, isArchived)` would avoid a full scan on every podcast list recomposition. Add a composite index.
2. **MED** `EpisodeDao.kt:84-88` `observeAllEntities()` loads full entities (incl. `description`, `chapters`) as a Flow; combined with `observeAllSummaries()` this doubles memory. Ensure only one is collected per screen.
3. **MED** `EpisodeDao.kt:136-143` `promoteBatchInQueue` does per-row `renumber` updates inside a `@Transaction`; for large queues this is O(n) writes. Consider single bulk UPDATE with CASE.
4. **LOW** `AppDatabase.kt` migrations add indexes only via `MIGRATION_2_3`/`MIGRATION_1_3`; fresh installs rely on entity `@Index` (verify `EpisodeEntity` declares them). Confirm `@Index` annotations exist.

### Compose recomposition
5. **MED** `EpisodesScreen.kt:465` `items(episodes, key = { it.uuid })` — verify `episodes` is a `derivedStateOf`/remembered list; if recomputed each frame, list diffing re-runs. Hoist filtering/sorting.
6. **MED** `PodcastsScreen.kt:846/914` duplicate `items(visiblePodcasts,...)` blocks suggest two LazyColumns; ensure only one is composed per tab to avoid double work.
7. **LOW** `RemoteImage.kt:50` `remember(url)` for bitmap state is correct (line 49 `remember { ServiceLocator.artworkLoader() }`), but `animateFloatAsState` runs for every image in lists; consider disabling animation for list thumbnails.

### Media playback
8. **HIGH (verification-only)** `FloatingPlaybackService.kt` / `CarPlaybackService.kt` — ExoPlayer reuse and `setHandleAudioBecomingNoisy` already present (PlaybackService.kt:42,59,83); verify no per-episode player creation remains. (Inspect `PlayerConnection.kt`.)
9. **MED** `InterruptedPlayback.kt` — confirm resume position saved on `onTaskRemoved` and audio focus loss; missing saves cause replays.
10. **LOW** Buffering: ensure `DefaultLoadControl` min buffer tuned for podcasts (long-form) to reduce stalls.

### Image loading
(No open findings — image decode already uses `withContext(Dispatchers.IO)` and `inSampleSize` via `sampleSizeFor` in ArtworkLoader.kt:44,72-82; downloads bounded by `Semaphore(4)`.)

### Network / RSS
13. **MED** `FeedParser.kt` / `PodcastFetcher.kt` — ensure OkHttp response bodies closed (`use {}`) and a single shared `OkHttpClient`. Check `Http.kt`.
14. **MED** `FeedRefreshWorker.kt` — batch refresh concurrency; unbounded parallel fetches can spike memory. Bound with semaphore.

### List rendering / paging
15. **LOW** No Paging 3 usage found; full lists loaded into memory. For very large libraries consider paging, but current Flow-based is acceptable for typical sizes.

### Memory
16. **LOW** `AndroidManifest.xml:42` `largeHeap="true"` — less urgent now that image decode downsamples (ArtworkLoader.kt:72-82); still remove once caching verified.
17. **MED** `EpisodeEntity` holds `description`/`chapters` TEXT; loading full entities into lists retains large strings. Summary projection already mitigates; ensure screens use summaries.

### Coroutines / threading
18. **MED** Search for `Dispatchers.Main` blocking calls; `remember { ServiceLocator... }` fine. Verify DB writes use `Dispatchers.IO` (Room handles internally).
(GlobalScope claim removed — no `GlobalScope` usage found in `app/src`.)

### Build
20. **LOW** R8 enabled in release; verify `proguard-rules.pro` keeps Room/Media3. Add baseline profiles for startup if not present.

## Suggested next steps
- Add composite DB index migration.
- Move image decode to IO + downsample.
- Bound feed refresh concurrency.
- Replace GlobalScope with lifecycle scopes.
- Remove largeHeap after fixes.

## 2026-11-07 review — fixed

- Downloads: a row left "downloading" by a closed app is reset to failed on start (it used to spin forever and hide the Download button); tapping the spinner cancels; at most 3 transfers run at once; a failed or interrupted download keeps its `.part` file and resumes with an HTTP Range request (falls back to a full download on 200/416).
- Battery rounds 1 and 2 (unmerged since 2026-11-01) merged: one shared whole-library query, progress saved every 30 s, Wi-Fi-only automatic downloads through WorkManager, conditional feed refresh (ETag / Last-Modified, schema 15).
- Home dashboard maths and playlist resolution run off the main thread and skip identical results; the playlist archive clock ticks every 5 minutes instead of every minute.
- Feed refresh downloads 4 feeds at a time; queue bookkeeping for new episodes stays one show at a time.
- Skip-outro check sleeps until the cut is near (was every 0.5 s for the whole episode); the floating player listens for the overlay permission being switched off instead of polling every 2 s.
- Room now exports each schema version to `app/schemas/` (from 15 on) so future migrations can be tested against the real previous layout.
- Removed unused DAO queries (`observeAllEntities`, `observeLatest`), 26 unused strings and an unused splash image; moved two loose scripts into `tools/`.
- Libraries: Kotlin 2.4.21, OkHttp 5.5.1, WorkManager 2.12, Navigation 2.10.2, Core 1.19.1, DataStore 1.3.0, serialization 1.12.0, androidx.media 1.8.0, test ext 1.3.0 / runner 1.7.0.
- Later the same day: `QueueScreen.kt` split into `QueueScreen.kt`, `PlaylistsScreen.kt` and `QueueRow.kt`. targetSdk 37 after an on-device background-audio check (docs/TODO.md). Still open: R8 shrinking for release.
