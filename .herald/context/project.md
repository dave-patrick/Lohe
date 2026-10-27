# Lohe: project context

## Overview
Native Android podcast client using Kotlin, Jetpack Compose, Room, and a debug MCP/REST surface.

## Library mirrors
- Android Auto projection uses `PlaybackService`'s `MediaLibrarySession` and `MediaLibrary` browse tree for Continue listening, Up Next, Downloads, Shows, Playlists, and episode/show search. AAOS additionally uses `CarPlaybackService` and `CarHomeScreen`, registering the same platform media-session token so car and phone share playback state. The media service declares the launcher icon, and the car media card uses a monochrome tintable attribution vector.
- The Playlists tab has six derived smart playlists: Played, In Progress, Unplayed, Not Downloaded, Downloaded, and New Releases (last seven days). They update from episode state, default to visible, and each visibility preference persists in DataStore.
- Room schema 12 adds per-podcast auto-queue preferences and whether newly discovered episodes go at the head or tail of Up Next. Schema 11 adds a separate `playedAt` completion timestamp, preserved through refresh. Schema 10 retained unsubscribed podcast rows using `isSubscribed`; normal subscription lists, search, counts, and feed refresh exclude those rows without deleting their episodes or history.
- Playlist TSV column 11 stores the optional snapshot flag and column 12 stores the optional pinned flag. Missing values preserve legacy behavior. A snapshot resolves its explicit ordered IDs while honoring the played and archived filters; completed episodes are removed from it. Marking played removes explicit playlist membership centrally; archiving follows the configurable delay (24 hours by default). Reopening clears both completion time and archive state.
- EpisodeMaintenance checks due completion times in process and through a persisted 15-minute Android job. Android may defer background execution. Archiving cancels active downloads and removes local copies; failed cleanup is retried. Archived episodes with actual listened time remain in Home's listening signal, while archives with no recorded listening do not inflate it. Original shared-storage audio sources are not deleted.
- One-time Pocket Casts mirrors are explicit snapshots, not recurring synchronization. Keep credentials and operational exports outside the repository and back up device data before replacement or APK installation.

## Current feature additions

- Manual in-app playback switches pause the interrupted item, persist it at the head of Up Next, and then replace media on the shared player. Queue auto-download checks read only the requested episodes and do not block the switch. Media-session play requests resolve only the selected episode and its show, rather than reading every show.
- Resume positions are supplied by `onSetMediaItems` before preparation, and by the service when advancing Up Next. Never restore position with a delayed seek after `STATE_READY`; that plays the beginning and can overwrite stored progress. Explicit pauses save progress immediately in addition to the periodic saves.
- Room schema 13 adds `archivedAt` so completed episodes can remain in Played for a configurable retention window before their downloaded copy is removed. Manual archive marks the episode played and clears its queue position. Older archived rows without this timestamp are preserved by cleanup.
- Podcast Updates uses WorkManager for configurable periodic feed refresh (default six hours; Never cancels it). Custom playlists can follow selected shows and exclude individual episodes; opted-in auto-download playlists download newly discovered matching episodes.

## Existing guidance

- Swipe action trays across podcast cards, episodes, playlists, and Up Next share `SwipeActionsHost`: Cancel, an outside tap, or Back closes the tray without applying the action. Outside taps are consumed so they cannot also play another episode. Only one tray stays open. Episode swipes reveal controls and require tapping an action to commit.
- Android 12+ launch uses `values-v31/Theme.Lohe` with a transparent system splash icon and black background. The full branded splash is rendered by Compose; do not let Android fall back to the masked launcher icon before it.
- A single settings button is anchored by LoheNavHost at the top-right of primary tabs and episode screens; local headers reserve a 48 dp slot instead of positioning their own gears. Settings is a category index with separate Appearance, Playback, Floating player, Archiving, and Downloads routes, plus the existing About destination. Subpage Back returns to the index. Keep the debug BUG affordance below the shared header.
- Floating player size choices are 48, 56 (default), 72, and 88 dp, preserving 48 dp minimum touch targets. Transparency is 0–75% (window alpha 1–0.25). Changes apply live and persist in DataStore; size changes re-clamp the normalized saved position.
- Cross-app floating controls are optional and off by default. FloatingPlaybackService owns a small TYPE_APPLICATION_OVERLAY window and a dismissible specialUse foreground-service notification. Start it only from a resumed activity after user-granted overlay permission; no accessibility service or automatic permission grants. Positions and collapsed state persist, seek amounts follow playback settings, and hiding the overlay does not stop audio. Battery setup is optional and opens system settings rather than requesting a direct power exemption. The onboarding wizard is planned in docs/TODO.md, not implemented.
- Playlist cards keep pinned custom playlists at the top across sort modes. Grid cards expose a drag grip in the space below the artwork; list cards place the grip at the far left in Manual sort. Reordering saves through PlaylistRepository and preserves hidden folder slots. Grid episode totals use bold electric blue numerals.
- Cross-app floating playback uses only FloatingPlaybackService; do not show a second in-app floating control when the overlay is disabled. The mini-player stays above the bottom bar with a visible accent glow.
- Read `README.md` for existing project guidance; follow directory-specific instructions where present.
- Read `graft/INDEX.md` for existing project guidance; follow directory-specific instructions where present.

## Stack evidence
- `build.gradle.kts` is present; use it to confirm dependencies and supported commands.

## Conventions
- Preserve existing project instructions, configuration, credentials, and application data.
- Use Graft for code context when available, following the global and project Graft guidance.
- Keep this context accurate as project architecture and conventions are established.
- Herald compiler output replaces vendor files. Preserve and merge existing guidance before compiling.

## Security and performance boundaries

- Release CarAppService connections use AndroidX host signature validation and its standard allow-list; permissive hosts are restricted to debug builds. Media3's trusted/read-only controller defaults remain in use.
- Feed bodies are limited to 16 MiB, catalog responses to 4 MiB, and artwork to 8 MiB of actual decoded response data. Feed, catalog, artwork, and download clients share a base OkHttp pool; feeds and short requests have total call deadlines while downloads retain a longer idle read timeout.
- Artwork uses a 64 MiB disk LRU with atomic cache writes and at most four simultaneous loads. Bounds decoding remains intentional.
- Local diagnostics are off by default in release settings. Enabling Diagnostics exposes the BUG button and permits local recording. URLs are redacted, event writes use a bounded background channel and buffered rotating writer, at most five report folders are retained, and release reports never capture logcat. Nothing is uploaded by reporting.
- Both backup formats exclude diagnostics and re-downloadable episode files while preserving library/preferences backups. Keep RECEIVE_BOOT_COMPLETED for persisted archive jobs; SAF imports require no broad storage permission.
- The debug REST/MCP API authenticates headers before body reads using an installation-specific random token in noBackupFilesDir/debug-api-token. See docs/MCP.md for private local retrieval and request limits. MCP GET returns 405 because server notifications are unsupported.
- Feed persistence runs inside a database transaction, batches history and episode reads below SQLite bind limits, and skips unchanged episode writes. Maintenance pages due IDs; show-card counts use a database aggregate. Search treats LIKE wildcards literally.
- Transport controls update from player events while idle; position updates remain periodic during playback. The sleep timer waits for its deadline; the overlay updates play state from events and checks permission separately.
- For isolated device testing, pass -PloheDebugApplicationIdSuffix=.audit. This installs a separate debug package and must not replace or clear the user's com.lohe.app data.
- CI pins action revisions, runs lint, reviews dependency changes, and configures weekly Dependabot updates. Release shrinking relies on upstream Room/Media3 rules rather than blanket keeps.
