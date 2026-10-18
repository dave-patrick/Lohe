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
gradle assembleDebug
gradle testDebugUnitTest
```

Gradle 9.6.0 is resolved through `gradle/wrapper/gradle-wrapper.gradle.kts`;
there is no `gradle-wrapper.jar` in the repo, which is the expected shape for
the declarative wrapper.

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

## Tests

199 JVM unit tests, no instrumentation. They cover the parts worth pinning:
feed parsing across RSS/Atom, entity decoding, date and duration formats,
queue and filter ordering, artwork caching and sampling, OPML round-trips,
download naming and path-traversal safety, sleep-timer deadlines, and the
repository's failure isolation.

```cmd
gradle testDebugUnitTest
```

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