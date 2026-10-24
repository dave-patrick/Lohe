# Lohe

A podcast client for Android. *Lohe* (*lōhe*) is a Hawaiian transitive verb —
to hear, to listen to, to mind. The name marks the move from background noise
to deliberate listening.

Subscriptions, catalog discovery, a playback queue, background playback and
offline downloads, built with Jetpack Compose and Media3.

---

## Build

Requires JDK 17+ and an Android SDK with platform 37. Point Gradle at it with
`local.properties` (gitignored):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Then:

```cmd
gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug            # macOS / Linux
gradlew.bat testDebugUnitTest
```

Gradle 9.6.0 arrives via the wrapper — no local install needed. It is pinned in
`gradle/wrapper/gradle-wrapper.gradle.kts`, which in Gradle 9 is the source of
truth; `gradle-wrapper.properties` is generated from it.

| | |
|---|---|
| Gradle | 9.6.0 |
| AGP | 9.4.1 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Media3 | 1.11.1 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk` (~27 MB).

## Install on a device

```cmd
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.lohe.app/.MainActivity
```

Over wireless debugging, `adb connect <host>:<port>` first — the session drops
when the phone sleeps or moves off the network.

---

## Architecture

The Settings gear has a shared top-right anchor across tabs. Settings opens a clickable category index: Appearance, Playback, Floating player, Archiving, Downloads, and About. Each category opens its own page with a fixed Back/title header; Back returns to the index without changing preferences.

Settings → Floating player offers optional blue playback controls over other apps. Enable Display over other apps when prompted; no accessibility service is needed. Drag any button to reposition, hold play/pause to expand, and tap the chevron to collapse to play/pause only. Choose Small, Medium, Large, or Extra large controls and 0–75% transparency; both apply live and persist. Position and collapsed state persist; rewind/forward follow playback settings. The ongoing notification can hide controls without stopping audio. Battery optimization setup is optional, and the overlay is off by default. It resumes when Lohe is opened with the option enabled; it does not auto-enable at boot or restart itself after a force-stop. The onboarding wizard is tracked in `docs/TODO.md`.

In Playlists grid view, choose Manual sorting and drag the grip on a card's artwork to change its saved order. Dragging near the top or bottom scrolls the grid. Folder-filtered reordering preserves the positions of other playlists. Episode-count numerals beneath grid artwork are bold electric blue.

Settings → Archiving controls automatic archiving after marking played: Off, Immediately, 1 hour, 24 hours (default), or 7 days. Played episodes leave explicit playlists immediately. A dedicated completion timestamp starts the delay; repeated marks and feed refreshes do not reset it, and reopening an episode cancels its eligibility. Archiving removes the downloaded copy and cancels any in-flight download while retaining episode metadata and listening history. Cleanup retries failures and runs through a persisted Android background job; battery restrictions can delay execution beyond the selected time, never shorten it. Migration gives existing played-but-unarchived episodes a fresh delay rather than guessing their completion date.

```
MainActivity ── appearance (DataStore) ──► LoheTheme
                                            │
                                            └─► LoheNavHost (bottom bar + push routes)
                                                 ├── Home        continue listening / empty state
                                                 ├── Podcasts    library, OPML import/export, local import
                                                 ├── Discover    catalog search, subscribe, per-show settings
                                                 ├── Queue        ordered, re-orderable, advanceable
                                                 ├── Player       Media3, artwork bleed, sleep timer
                                                 ├── Storage      downloads + imported audio
                                                 ├── Settings     appearance, playback, about
                                                 └── About        hero mark, wordmark, studio credit

data/          SubscriptionRepository (front door), DiscoveryViewModel,
               EpisodesViewModel, QueueViewModel, SubscriptionsViewModel
data/db/       Room — podcasts, episodes. Schema v4, real migrations
data/fetch/    FeedParser (RSS + Atom), CatalogSearcher (iTunes), OpmlCodec
playback/      PlaybackService (MediaSession), PlayerConnection, SleepTimer
download/      DownloadManager — resumable, per-episode
dev/           DevApi — the debug control surface: MCP and REST over a socket
```

`SubscriptionRepository` takes DAOs and a fetcher, never a `Context`, so the
fan-out logic over many feeds survives individual failures and is testable
without a network. Anything needing Android resolves through `ServiceLocator`.

### Design notes

- **Dark is the product default** and deliberately does *not* follow the system
  setting. Light is an explicit choice in Settings. The palette is designed
  dark-first.
- **The glass recipe**: translucent fill, backdrop blur, and a hairline
  *top* edge only — episode artwork sits behind the surface and bleeds through.
  `Modifier.blur` is a no-op below API 31, so it degrades to a translucent panel
  rather than disappearing.
- **Accent is rationed.** Electric blue marks things that are tappable or
  currently active, and nothing else. That restraint is what separates
  "premium" from "loud".
- **Palette source of truth is `brand/tokens.css`**, mirrored in
  `ui/theme/Color.kt`. `res/values/colors.xml` exists only because the window
  background is read before Compose starts.

## The dev surface

The debug build carries a control surface so an agent can drive the app
without tapping through the UI: it speaks the Model Context Protocol, so the
library, the playlists, the queue and the player are all reachable as
tools. It binds the loopback interface inside the app and is reached
through `adb forward`.

See [`docs/MCP.md`](docs/MCP.md) for the tool list, the setup and the
OpenCode configuration.

It also reads a Pocket Casts account and brings the library into line with
it — the same episodes, in the same state, in the same playlists — through
`tools/pocketcasts-export.py`.

A debug build also wears a staging mark: the version, beside the wordmark on
the home screen and on the About page. A release build has no mark and no
BUG button.

One-time library mirrors can use snapshot playlists. These retain their
explicit order while excluding archived episodes and honoring the played-state
filter. Completed episodes are archived and removed from playlists; reopening
an episode returns it to unplayed and removes its archive flag. The optional eleventh playlist TSV column enables
snapshot behavior; older playlist files remain supported. Room schema 10 also
allows a show to be retained as an unsubscribed archive without deleting its
episodes or listening history. No recurring synchronization is enabled.

## Tests

245 JVM unit tests, no instrumentation. They cover the parts worth pinning:
feed parsing across RSS/Atom, entity decoding, date and duration formats,
queue and filter ordering, artwork caching and sampling, OPML round-trips,
download naming and path-traversal safety, sleep-timer deadlines, the
MCP wire format, and the repository's failure isolation.

```cmd
./gradlew testDebugUnitTest
```

Tests also run in CI on every push and pull request, and the debug APK is
attached to each run.

`FeedParser` runs against kxml2 on the JVM because Android's `android.util.Xml`
is unavailable in plain unit tests.

## Known gaps

See [`docs/TODO.md`](docs/TODO.md) — open work, ordered by consequence. The
short version: several flows have never been checked by hand on a device, the
release build is not hardened, and no typeface is bundled yet.

## Brand

`brand/tokens.css` (palette, motion, spacing, radius, glass) and `BRAND.md`
(identity, voice, taglines) sit outside the app module. Kani the dragonfly is
the character; the geometric hero mark is the logo. They are different assets
and are used in different places — see the launcher icon versus the splash.
