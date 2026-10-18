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

**Now:** `tokens.css` wins. It self-declares as "platform-agnostic source of
truth" and `BRAND.md` §2 independently corroborates `#0052FF → #00D2FF`, so
two written sources outweigh one reading of a picture. `Color.kt` is back to
`#0052FF` / `#00A8FF` with a comment recording why, and the dead
`lohe_accent_700` is gone — `colors.xml` now carries only the window background
it is actually read for.

*If the artwork hex is the one you want, say so and this reverts — but then
`tokens.css` and `BRAND.md` need updating first, not just `Color.kt`.*

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

## Housekeeping

Clean: `.gitignore` excludes `build/`, `.gradle/` and `local.properties`; the
legacy `rea/work/Lohe` duplicate has been deleted; `C:\Users\davem\repos\Lohe`
is the only working copy.