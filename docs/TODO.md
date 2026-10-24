# Lohe — Outstanding tasks

Audited against the code at `749df07`. Items are ordered by consequence, not by
ease. Each one names the files it touches so it can be picked up cold.

---

## Onboarding wizard — planned

- [ ] Add a skippable first-run wizard, with a way to replay setup from Settings.
- [ ] Explain library import/subscriptions, playlists, Up Next, and offline downloads without changing existing library state.
- [ ] Offer the optional cross-app floating player, then explain and request Display over other apps only if enabled. Do not request accessibility-service access for playback controls.
- [ ] Request notification permission in context; explain the ongoing floating-player notification and its Hide controls action.
- [ ] Offer optional battery optimization setup with an honest battery-use tradeoff, detect current status, and allow Not now without blocking playback.
- [ ] Introduce drag-to-place, hold-to-expand, collapse-to-play/pause, size and transparency controls, and configured rewind/forward intervals.
- [ ] Let listeners choose played-episode archive delay, including the 24-hour option and downloaded-file deletion on archive.
- [ ] Persist completion and declined optional steps; make permission denial, revocation, and interrupted setup safe and resumable.

The wizard itself is not implemented. Floating-player and battery setup are currently available in Settings.

---

## 0b. The in-car app is built but has never run on a head unit

**Severity: medium. It compiles, installs and leaves the phone app untouched,
but no head unit has rendered it yet.**

Android Auto on a phone reaches the app over the media browser service: the
car browses the library and asks for episodes to be played. That half is
built and covered by `MediaLibraryBrowseTest`, which walks the tree the way a
car does and checks that a tap resolves to something playable.

Android Automotive OS runs the app itself. `CarPlaybackService` now declares
a media-category `CarAppService`, `CarSession` returns `CarHomeScreen`, and
that screen renders a `MediaPlaybackTemplate` after handing the host the live
Media3 platform token:

- `PlaybackService` publishes its player and platform token on its companion
- `CarHomeScreen` binds `PlaybackService`, converts the token with
  `MediaSessionCompat.Token.fromToken(...)` and calls
  `MediaPlaybackManager.registerMediaPlaybackToken(...)`
- the manifest carries `androidx.car.app.MEDIA_TEMPLATES`, the automotive and
  Android Auto declarations, and `minCarApiLevel` 8
- `res/xml/automotive_app_desc.xml` declares `media` and `template`

**Not verified:** the token handshake, the template render, and whether the
head unit lists Lohe under media apps or under car apps. All of it needs the
truck, or an automotive emulator.

**Known gap:** there is no launchable activity, so the app has no icon in the
AAOS app grid and does not answer
`androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK` from the system media
card. Adding `androidx.car.app:app-automotive` fixes that but raises its
minSdk to 29 (the app is 26), so it needs `tools:overrideLibrary` and a
`android:enabled="false"` activity; do it only with something to test on.

**To do it:** install the debug APK in the car, open the media app list, and
check that browsing and playback work; then check whether the templated
playback screen appears at all.

---

**Was:** the worst bug in the app, and it had been there the whole time.

`PodcastDao.upsert` was `@Insert(onConflict = OnConflictStrategy.REPLACE)`.
Room implements REPLACE as delete-then-insert, and `episodes` has a foreign
key onto `podcasts` with `ON DELETE CASCADE`. Every feed refresh saved the
podcast row, which deleted the row, which cascaded away every episode of
that show — and the feed's copy was then written back in with default state.

Played status, resume positions, downloads, stars, queue order and
accumulated listening time all vanished on every refresh. The merge in
`RssPodcastFetcher.importFeed`, added earlier to preserve exactly those
fields, never got a chance to run: the rows it was meant to preserve had
already been deleted.

**Now:** the upsert inserts with `IGNORE` and updates only the feed-derived
columns, so the row survives a refresh and the listener's own settings
(auto-download, subscribe date, unread count) are left alone.

Pinned by `PodcastUpsertCascadeTest` (instrumented). The cascade only
reproduces against a real foreign-key-enforcing SQLite, so the test runs on
a device rather than on the JVM.

**Run it with `:app:deviceTest`, not `connectedAndroidTest`.** The Gradle
task of that name uninstalls the app and the test package when it finishes,
which throws away everything in the app's data directory — the library, the
playlists, the listening history. `deviceTest` installs over the top with
`adb install -r` and drives the runner directly, so a test run costs nothing
but time.

**Recovery note.** The bug had already run against a real library before it
was found, so the episode state on that device was reconstructed from the
SQLite file's freed pages and write-ahead log: 4365 played episodes, a
3-episode queue in its original order, 3 downloads and 22 s of accumulated
listening time were recovered and written back. The reconstruction scripts
live in `D:\My Drive\Dev Projects\Lohe\tools\recover-episodes.py` and
`build-restore.py`.

*(One thing did not survive that second loss: the per-show folder
assignments in `files/podcast-folders.tsv`. They were never read before the
uninstall and cannot be reconstructed. The playlists themselves were, from
the file format and the values captured beforehand.)*

---

## 1. ~~Per-show auto-download does nothing~~ — **done**

**Was:** `podcasts.autoDownload` was fully plumbed for *storage* and *display*
(column + migration, DAO setter, repository method, entity field, UI toggle)
but nothing ever *read* it to start a download. The toggle persisted across
restarts, rendered correctly, and did nothing.

**Now:** `SubscriptionRepository` takes an `autoDownload: (EpisodeEntity) -> Unit`
seam and `refreshAllFeeds()` diffs episode UUIDs across the fetch, handing over
only what it has not seen before. `ServiceLocator` resolves the real
`DownloadManager` lazily through that seam — resolving it in the constructor
would recurse, since the manager itself takes a repository.

Two deliberate exclusions, both covered by tests:

- **The first fetch of a show is exempt.** A new subscription arrives with its
  entire back catalogue attached; downloading all of it is not what "save new
  episodes" asks for.
- **Already-finished episodes are skipped.** They cost bandwidth and will never
  be played again.

Pinned by four cases in `SubscriptionRepositoryTest`:
`refresh does not auto-download when the setting is off`,
`refresh auto-downloads only episodes it has not seen before`,
`the first fetch of a show does not pull its back catalogue`,
`a newly seen episode that is already finished is not downloaded`.

Still worth confirming on a device (see item 4): that the refresh button
actually pulls bytes.

---

## 2. ~~Brand tokens had three disagreeing sources~~ — **done**

**Was:** the accent existed in three places at once and no two agreed.

| Source | Was |
|---|---|
| `brand/tokens.css` | `#0052FF` / `#00A8FF` |
| `ui/theme/Color.kt` | `#0080FF` / `#0052CC` |
| `res/values/colors.xml` | `#FF0052FF`, referenced nowhere |

`Color.kt` held values read off the brand identity artwork, while its own
header still claimed it mirrored `tokens.css`. That was a misread of a colour
circle in a rendered JPEG.

**Then:** `tokens.css` won. It self-declares as "platform-agnostic source of
truth" and `BRAND.md` §2 independently corroborated `#0052FF → #00D2FF`, so
two written sources outweighed one reading of a picture. `Color.kt` went back
to `#0052FF` / `#00A8FF`, and the dead `lohe_accent_700` was removed.

**Now:** the artwork won, on request. The accent is read off the wordmark
itself rather than off a colour circle in a rendered JPEG, and measured
rather than eyeballed: the letters are a slab whose most common colour is
`#008EFF`, lifting into a `#07C6FC` highlight. The launcher icon agrees,
at `#05A4FA`. `#0052FF` was more violet than anything in either artwork.

So the ramp is `#008EFF → #00A8FF → #00D2FF`, and `tokens.css`, `BRAND.md`
and `Color.kt` all say the same thing. The artwork is the source of truth
here, which is the opposite of the earlier ruling and is recorded so the
next reader does not assume the tokens file is the authority.

---

## 3. ~~Committed docs described a codebase that no longer exists~~ — **done**

`FINAL_SUMMARY.md`, `STATUS_UPDATE.md`, `PROGRESS_SUMMARY.md`,
`INSTALL_INSTRUCTIONS.md` and `WIRELESS_INSTALL.md` were all progress snapshots
from an earlier build, and all five had rotted:

- `#070D14` background (actual: `#121214`)
- *"Known Limitation: `seekTo()` has reference issues"* — resolved long ago;
  `PlayerConnection.seekToFraction` / `seekTo` / `seekBack` all work and
  `PlayerScreen` wires `onSeek`
- *"Pending: wire playback"* — `PlaybackService` exists with a MediaSession
- install docs pointing at `rea\work\Lohe`, a directory that no longer exists
- wrong APK size, wrong adb path, wrong bottom-bar tabs, an emoji

**Now:** replaced with a single `README.md` describing the app as it actually
is, and the five stale files are deleted. They were worse than absent — each
one asserted confidently about code that had changed.

---

## 4. Three flows have never been confirmed by hand

**Severity: medium — all are covered by unit tests, none by a person looking
at a phone.**

The device has been PIN-locked, so these were verified only at the JVM level:

- Podcast detail renders an HTML/entity-stripped blurb from a real feed
  (`FeedParser.plainText`)
- Per-show auto-download survives an app restart *(blocked behind #1 — the
  flag persists but never acts)*
- Player shows the feed's duration before ExoPlayer reports one
  (`NowPlaying.knownDurationSeconds`)

**To fix:** unlock the device and walk Settings → About → back, a show detail
sheet, and a fresh play. Capture screenshots alongside the task.

---

## 5. No typeface

**Severity: low.**

`ui/theme/Type.kt:16` is still `FontFamily.Default`, with a comment noting
*"swap `LoheFontFamily` in when a face is chosen."* The brand identity sheet
specifies a heavy, tightly-tracked bold for headlines and a regular weight for
body/UI. The current scale approximates this by letter-spacing alone.

**To fix:** bundle the chosen family, or delete the placeholder comment if the
default is the intended answer. `LoheTypography` already centralises the scale,
so this is a one-file change plus the font asset.

---

## 6. Release build is not hardened — **CI done, R8 outstanding**

**CI (done).** Tests and a debug APK now run on every push and pull request,
with the APK attached to each run and test reports uploaded on failure. Before
this, the 199 tests only ran when someone remembered.

Getting there surfaced three things that were broken for anyone but this
machine:

- **There was no `gradlew` at all.** Every build had been driven by a hardcoded
  absolute path to a local Gradle install, so a clean clone could not be built
  by anyone else. The wrapper is now generated and committed.
- **The wrapper contradicted itself.** `gradle-wrapper.gradle.kts` pinned
  Gradle 9.3.1 while `gradle-wrapper.properties` said 9.6.0. In Gradle 9 the
  declarative file wins, so only one of them was ever consulted. Aligned on
  9.6.0, the version actually verified.
- **`gradlew` was not executable in git**, so it exited 126 on the Linux
  runner. Fixed in the index rather than with a `chmod` in the workflow.

**Still to do:**

- `build.gradle.kts:21` — `isMinifyEnabled = false`, so R8 never runs
- `proguard-rules.pro` exists but is never applied to anything
- no signing config; debug-signed only

Enabling R8 needs care and is deliberately not done blind: a minified build
can pass assembly and still crash at runtime, and the release path here is
unsigned so it cannot be installed to check. Media3 and Room both ship
consumer rules, so the likely result is that it works — but "likely" is not
the standard. Do it alongside a signing config, then smoke-test the artifact
before trusting it.

---

## 7. Light mode has not been reviewed

**Severity: low.**

The palette is defined dark-first; `Appearance.LIGHT` is an explicit user
choice, not a derived variant. Two screens already special-case the white/cyan
brand marks so they don't vanish on paper white — `HomeScreen` and
`AboutScreen` both gate on background luminance. The rest of the app has not
been looked at in light mode, and `Glass.kt` uses hand-tuned light fills
(`glassFillLight` at ~30% white) that have never been tuned against real
artwork.

---

## 8. Podrolls — AI knowledge extraction from listening

**Severity: low. New feature, not yet started.**

Built around capturing notes while listening hands-free. Tapping your earbud
creates an AI "snip" that generates a real-time transcript, title, and summary
of the segment, then exports directly to Notion, Obsidian, or Readwise. It also
breaks long episodes into auto-generated, searchable AI chapters.

Files it touches:
- `app/src/main/java/com/lohe/app/ui/player/` — new sheet/screen for capture
- `app/src/main/java/com/lohe/app/data/` — new entity + DAO for snips/chapters
- `app/src/main/java/com/lohe/app/playback/` — earbud tap integration (media
  button / headset hook)
- `app/src/main/java/com/lohe/app/ui/navigation/` — export targets (Notion,
  Obsidian, Readwise)

**Not started.** No code exists yet; this is a greenfield feature.

---

## 9. Natural-Language Segment Querying

**Severity: low. New feature, not yet started.**

Apps like CastFox and Snipd run vector embeddings over the raw transcript,
allowing you to ask: *"What was the second paper the guest mentioned about
solid-state cooling?"* The app synthesizes an answer and returns an exact
timestamp link to jump directly to that point in the audio.

Files it touches:
- `app/src/main/java/com/lohe/app/ui/player/` — query UI + timestamp jump
- `app/src/main/java/com/lohe/app/data/` — transcript + embedding storage
- `app/src/main/java/com/lohe/app/data/db/` — vector-capable storage or local
  vector search index

**Not started.** Depends on the Podrolls transcript pipeline being in place
first.

---

## 10. Pre-Listen Executive Synthesis

**Severity: low. New feature, not yet started.**

Instead of reading vague show notes, AI generates a 3-bullet core takeaway
list and a structured outline before you commit to a 2-hour recording.

Files it touches:
- `app/src/main/java/com/lohe/app/ui/player/` — pre-listen summary sheet
- `app/src/main/java/com/lohe/app/data/` — per-episode synthesis cache
- `app/src/main/java/com/lohe/app/ui/navigation/` — trigger on show detail /
  episode detail

**Not started.** Depends on the Podrolls transcript pipeline being in place
first.

---

## 11. Beyond Fixed Timers — Ad Detection

**Severity: low. New feature, not yet started.**

Rather than relying on manual "skip 30 seconds" buttons, experimental builds
detect the acoustic shifts between host speech and dynamic ad insertion (DAI).
Transcript semantic boundaries identify sponsor cues (e.g., promo codes,
standard transition phrases like "we'll be right back after this") in the
auto-generated transcript and autonomously skip to the exact second the
interview resumes.

Files it touches:
- `app/src/main/java/com/lohe/app/data/` — ad-marker storage (timestamp +
  confidence)
- `app/src/main/java/com/lohe/app/playback/` — skip-to-boundary logic
- `app/src/main/java/com/lohe/app/ui/player/` — skip-to-boundary UI control

**Not started.** Depends on the Podrolls transcript pipeline being in place
first.

---

## 12. Podcast-Engine Processing for Non-Podcast Audio

**Severity: low. New feature, not yet started.**

Using the system share sheet, apps like Castro and Podcast Republic let you
send YouTube audio rips, DRM-free audiobooks, or voice memos directly into
the player. The audio inherits all app benefits: silence trimming, voice
boost, bookmarking, and variable playback speeds.

Files it touches:
- `app/src/main/java/com/lohe/app/ui/navigation/` — share-sheet target activity
- `app/src/main/java/com/lohe/app/data/` — non-podcast audio entity + library
- `app/src/main/java/com/lohe/app/data/db/` — non-podcast audio table
- `app/src/main/java/com/lohe/app/playback/` — non-podcast source playback path

**Not started.** No code exists yet.

---

## 13. Synchronized Data & Visual Overlays

**Severity: low. New feature, not yet started.**

Feeds can push dynamic JSON payloads alongside audio timestamps. When a host
mentions a specific schematic, financial chart, or map, the player surfaces
interactive 2D data or links on your display without switching to a video file
or draining high-bandwidth video streams.

Files it touches:
- `app/src/main/java/com/lohe/app/data/` — feed-side JSON payload ingestion +
  episode overlay schema
- `app/src/main/java/com/lohe/app/ui/player/` — overlay surface + timestamp
  sync
- `app/src/main/java/com/lohe/app/playback/` — timestamped overlay dispatch

**Not started.** No code exists yet.

---

## 14. Auto-Mute on Speech Detection

**Severity: low. New feature, not yet started.**

Using earbud transparency microphones and bone-conduction sensors, the player
detects when you start talking in the real world (ordering coffee, answering a
coworker) and automatically pauses the podcast and resumes speech-boosted
ambient audio — then rewinds 5 seconds once you stop talking.

Files it touches:
- `app/src/main/java/com/lohe/app/playback/` — audio pause/resume hook +
  5-second rewind
- `app/src/main/java/com/lohe/app/ui/player/` — auto-mute UI toggle

**Not started.** No code exists yet; depends on microphone access permission
and earbud transparency-microphone API support.

---

## Housekeeping

Clean: `.gitignore` excludes `build/`, `.gradle/` and `local.properties`; the
legacy `rea/work/Lohe` duplicate has been deleted; `C:\Users\davem\repos\Lohe`
is the only working copy.
