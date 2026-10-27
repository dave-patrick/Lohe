# Security and performance fixes — 26 October 2026

The two supplied reviews were identical. Fixes were applied to the existing working tree without reverting its pending feature work. Release version remains 0.10.1; the final source/build set is 2026.26.10.27.

| Review finding | Resolution |
| --- | --- |
| S1 | Release car hosts use AndroidX signature validation and the standard host allow-list. Debug builds retain development-host compatibility. |
| S2, P2 | Release diagnostics are disabled by default and enabled from Settings → Diagnostics. Event logging uses a bounded channel and buffered background writer, redacts entire URLs, rotates logs around 1 MiB, limits event length, and retains five report folders. Screenshots are reduced to a maximum 1280-pixel edge. Release builds omit logcat. Reports stay local. |
| S3 | Legacy backup rules and Android 12+ cloud/device-transfer rules exclude reports and re-downloadable audio. Library and preferences remain eligible for restoration. |
| S4 | Actual response-stream limits: feeds/Libsyn fallback 16 MiB, catalog 4 MiB, artwork 8 MiB. Limits apply even without a reliable Content-Length and after HTTP decompression. |
| S5, P1 | Debug API uses a random installation-specific token outside backup. Authentication precedes body allocation. Header lines, total headers, body size, connections, idle reads, and total read time are limited. Bodies are read as UTF-8 bytes. Idle SSE is replaced with the optional MCP GET 405 response. Private token setup is documented in MCP.md. |
| S6 | HTTP compatibility is retained for arbitrary third-party podcast/feed/media hosts. A domain-based Android security config cannot distinguish request purpose. A static universal allow-list would break legitimate shows. |
| S7 | Media3 1.11.1 already distinguishes trusted controllers and read-only untrusted controllers. Its compatibility defaults remain in place; the asserted unrestricted playback-control flaw was not confirmed. |
| S8 | Removed broad media-storage permissions; imports use SAF. Kept RECEIVE_BOOT_COMPLETED because the persisted archive job requires it. |
| S9 | Database searches escape LIKE wildcard characters, including the escape character itself, so user input is literal. |
| S10 | User-Agent and MCP versions derive from BuildConfig.VERSION_NAME. |
| S11 | Pinned CI/Pages actions to verified revisions, restricted CI permissions, added release lint and PR dependency review, and configured weekly Dependabot. Removed blanket Room/DAO/ViewModel/ExoPlayer keeps. CI artifact names include release/build metadata. |
| P3 | Production imports use a Room transaction, batch episode/history reads and history deletions in groups of 400, preserve listener state, and skip unchanged episode writes. |
| P4 | Feed/catalog/artwork/download clients derive from a shared OkHttp base client. Short operations have total call deadlines; downloads retain a longer idle timeout without a short total-download deadline. |
| P5 | Archive maintenance selects only due IDs in keyset batches of 200 and then reads those episodes. Transport state and floating play/pause controls use player events while idle. The sleep timer waits on its deadline. Active-playback position sampling and periodic resume persistence remain intentional. |
| P6 | Artwork disk cache has a 64 MiB LRU budget, 8 MiB entry limit, temporary-file writes, startup cleanup, and four concurrent loads. Cache filesystem access runs on IO. Bounds decoding and RGB_565 remain intentional. |
| P7 | Show-card statistics use SQL aggregates rather than grouping all episode summaries on the composition thread. Major list keys were already stable. No unsupported claim of measured frame-rate or battery improvement is made. |

Enabling lint also exposed existing errors in car API opt-ins, media-search declarations/result constants, and configuration-sensitive Compose resources/locales; these were corrected. Nine stale duration-test expectations were updated to match the existing compact duration formatter without changing its UI behavior.

## Verification

- The first full JVM pass after expectation repair passed 286 tests, with zero failures or skips. Regression coverage includes actual streamed body limits, unauthorized oversized requests, request framing, UTF-8 lengths, URL redaction, cache eviction, and feed-import state preservation/unchanged-write avoidance.
- OSV query of 171 resolved release Maven dependencies returned zero known vulnerability matches. This covers those coordinates against OSV at the time of the check, not every possible vulnerability or build-tool dependency.
- New Android database checks cover literal wildcard searches, due-ID pagination, aggregate counts, and transaction rollback.
- Final `testDebugUnitTest`, `assembleRelease`, and `lintRelease` passed. Lint has zero errors, 87 warnings, and five hints; no lint baseline was introduced. Warnings include existing style/deprecation notices, application-context singleton heuristics, and the intentionally exported media/car services.
- All 15 instrumentation tests passed on the connected SM_S948U in the isolated `com.lohe.app.audit` package. The test runner was invoked directly through ADB because the optional UTP Gradle runner was not cached locally. Existing browse tests now seed and remove their own fixtures, release controllers, and check URI resolution on the service-side player; they no longer depend on a populated personal library. The archive test now expects the existing marks-played behavior.
- APK signature verification passed with v2 signing. Package/versionCode remain `com.lohe.app`/1001. The release is not debuggable, has neither broad storage permission, retains the persisted-job permission, and contains both backup rule references. The build ID is present in DEX; the legacy public token and debug-token-file code are absent.
- Signed artifact: `app/build/outputs/apk/release/Lohe-0.10.1-build-2026.26.10.27-release.apk` (9,182,855 bytes).
- SHA-256: `67916176ea35ea20c2cd2c63325e165654bd8c93fb4b08ae39d23d12047d35e7`.
- Machine-readable proof and device-run output are saved under `app/build/reports/security/`. CI YAML and shell syntax, artifact metadata extraction, Herald validation, and `git diff --check` also passed.

Device checks use `'-PloheDebugApplicationIdSuffix=.audit'` (quote this argument in PowerShell) to target a separate test application. The temporary test packages were removed after verification. The user's installed `com.lohe.app` and its library were not replaced or cleared. The signed release APK was built but was not installed over that app.

No source was pushed and no release was published as part of this work. An automotive head-unit test, full backup/restore round trip, and device profiling of the large Compose screens remain separate verification tasks.
