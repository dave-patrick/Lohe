# Lohe — Outstanding tasks

Audited against the code at `d6ef0da`. Items are ordered by consequence, not by
ease. Each one names the files it touches so it can be picked up cold.

---

## 1. Per-show auto-download does nothing

**Severity: high — a visible setting that is silently inert.**

`podcasts.autoDownload` is fully plumbed for *storage* and *display*:

- column + migration — `data/db/AppDatabase.kt:71` (schema v4)
- setter — `data/db/dao/PodcastDao.kt:46`
- repository — `data/SubscriptionRepository.kt:121`
- entity field — `data/db/entity/PodcastEntity.kt:30`
- toggle in the UI — `ui/navigation/PodcastDetailSheet.kt:179`

But nothing ever *reads* it to start a download. A grep for `autoDownload`
across `data/` returns only the definition and the setter — no consumer. The
only path that reaches `DownloadManager.start()` is the explicit per-episode
button in `LoheNavHost.kt:320`.

So the toggle persists across restarts and renders correctly, and then does
nothing. A user who turns it on will reasonably conclude downloads are broken.

**To fix:** consult the flag in `SubscriptionRepository.refreshAllFeeds()`
(`SubscriptionRepository.kt:182`) and on the subscribe path, kicking
`downloadManager().start(episode)` for *newly seen* episodes only — a refresh
that re-downloads the whole back catalogue would be hostile and expensive.
Test it in `SubscriptionRepositoryTest` alongside the existing
`auto-download is per show` and `auto-download defaults to off` cases.

---

## 2. Brand tokens have three disagreeing sources

**Severity: medium — every future brand edit has to guess which file is right.**

| Source | Electric blue | Mid | Cyan |
|---|---|---|---|
| `brand/tokens.css` | `#0052FF` | `#00A8FF` | `#00D2FF` |
| `ui/theme/Color.kt:22-24` | `#0080FF` | `#0052CC` | `#00D2FF` |
| `res/values/colors.xml:4` | `#FF0052FF` | — | — |

`Color.kt` currently holds the values read off the brand identity artwork
(`#0080FF` / `#0052CC`), while its own header comment still claims *"Mirrors
brand/tokens.css — keep the two in sync"* — which is no longer true.

`colors.xml` agrees with neither, and `lohe_accent_700` is **referenced
nowhere**: `themes.xml` only consumes `lohe_bg_900`. It is dead and drifted.

**To fix:** pick one canonical accent (artwork or `tokens.css`), write it into
`tokens.css` first, then make `Color.kt` and `colors.xml` agree. Either delete
`lohe_accent_700` or point it at the single source of truth — right now it is
a third answer that nothing reads.

---

## 3. Three committed docs assert things that are no longer true

**Severity: medium — these are the first thing a reader opens.**

`FINAL_SUMMARY.md`

- Says the background is `#070D14` "Midnight Void". It is `#121214`.
- Lists *"Known Limitation: `seekTo()` method has reference issues"*. This is
  **stale** — `PlayerConnection.seekToFraction` / `seekTo` / `seekBack` all
  exist (`playback/PlayerConnection.kt:88-103`) and `PlayerScreen.kt:191` wires
  `onSeek`. Scrubbing works; the note should go.
- Describes the player as built around manual state tracking and "post-build
  method calls" instead of a `Player.Listener`. That predates `PlaybackService`.

`STATUS_UPDATE.md`

- *"Pending — Wire Playback (Media3/ExoPlayer)"* and *"Resolve Media3
  dependency issue"*. Both resolved: `playback/PlaybackService.kt` exists with
  a MediaSession.

`PROGRESS_SUMMARY.md`

- Repeats the `#070D14` background.

**To fix:** rewrite or delete these three. They are worse than absent, because
each one confidently describes a codebase that no longer exists.

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

## 6. Release build is not hardened

**Severity: low, but blocks any real distribution.**

- `build.gradle.kts:21` — `isMinifyEnabled = false`, so R8 never runs
- `proguard-rules.pro` exists but is never applied to anything
- no signing config; debug-signed only
- no CI — the 195 tests currently run because someone remembered to run them

**To fix:** enable R8 and fix whatever it breaks (Media3 and Room both need
keep rules), then add a workflow that runs `testDebugUnitTest` on push.

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

## Housekeeping

Clean: `.gitignore` excludes `build/`, `.gradle/` and `local.properties`; the
legacy `rea/work/Lohe` duplicate has been deleted; `C:\Users\davem\repos\Lohe`
is the only working copy.