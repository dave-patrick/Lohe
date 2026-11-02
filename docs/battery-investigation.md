# Background battery investigation — November 1, 2026

## Reproduction and diagnosis

The installed Debug build 2026.01.11.50 showed sustained database CPU during background playback. `dumpsys cpuinfo` reported 106% CPU (72% user, 34% kernel); a three-sample `top` capture included 90.2% and 86.4% interval samples. Thread sampling placed most CPU on `arch_disk_io` threads, rather than the audio decoder. Android reported MainActivity paused while playback was active at 2x.

`dumpsys dbinfo com.lohe.app` showed repeated whole-library summary queries over **38,412 episodes** after five-second progress updates. Cursor windows repeatedly reloaded the sorted result. Raw diagnostic captures are ignored under `app/build/battery-audit/`.

Three subscription owners kept this work alive: QueueViewModel's permanent completion-cleanup collector, eagerly shared playlist resolution, and Compose screen collectors that did not stop with the Activity lifecycle. Independent diagnosis confirmed the source path. The playback service itself has no permanent whole-library observer.

## Fix in build 2026.01.11.51

- Reconcile legacy completed playlist membership once. Subsequent completions still remove membership through the existing central repository callback.
- Resolve playlists only while subscribed, releasing the source after a five-second grace period. Explicit batch queue actions resolve a fresh one-shot snapshot, including current shows, rather than reading an inactive cached value.
- Collect screen state with the lifecycle-aware Compose API so stopped screens release database subscriptions. Add the explicit runtime-compose dependency at the existing Lifecycle version.
- Keep playback in the service and preserve five-second position persistence and immediate pause saves.

## Validation

The new QueueSubscriptionTest failed before the fix with **two active whole-library observers and no screen subscriber**. It checks idle subscription ownership, visible playlist collection, and release after the last screen subscriber leaves. Independent review found no issues.

The isolated phone passed **26 tests**: a separate fresh-process cold-start test, nine completion tests, four browse tests, two seek tests, and ten podcast settings/auto-queue tests. The main app's data was backed up in a validated 25-entry compressed archive outside the repository before installation.

The final JVM suite passed **307 tests, zero failures/errors/skips**. Release lint passed with **zero errors, 96 warnings and four hints**. The subscription-release test uses a real-time wait because upstream `flowOn(Default)` is fused into `stateIn`'s sharing context, so its grace-period timer is outside the test scheduler; a virtual-time-only version initially failed for that test-harness reason.

Private Debug build 51 installed and launched on the main phone. Its installed APK checksum matches `5849aad837ab694136f8bb2c25caa8944d1e9c49ab777b5ec619149dc4d4307a`. Main app data was preserved. No commit, push, Play upload or release publication was performed; the existing build 50 Play bundle does not contain this fix.

With the updated app backgrounded and no active Lohe audio, CPU interval samples were **2.5% and 0.2%** (initial sample 0.0%). The latest whole-library query timestamps in `after-idle-dbinfo.txt` remained at initial load (19:49:51), rather than repeating continuously. This idle sample is not an equivalent playback comparison: the user was asked to start an episode for that measurement. These short samples do not measure a full-day battery percentage or prove every possible power issue is resolved.
