# Lohe — Outstanding tasks

Audited against the code at `d6ef0da`. Items are ordered by consequence, not by
ease. Each one names the files it touches so it can be picked up cold.

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