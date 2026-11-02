# Lohe — Google Play submission checklist

Updated November 1, 2026. This is a phone/tablet submission plan, not a claim of Play approval. Unchecked Console and publication items must be completed before submission. Do not upload a Debug or smoke APK.

## Build and distribution decision

- Production package `com.lohe.app`, release `0.10.3`, release-only versionCode `1003`; build ID is separate in artifact names and About.
- Use `-PlohePlayStore=true`. That profile omits unverified car declarations. Ordinary Release and private Debug retain the car implementation. Do not opt the first Play submission into Android Auto or Automotive.
- `smokeRelease` is minified and non-debuggable, signed with the release key, with isolated package `com.lohe.app.releaseaudit`. It is for local device testing only.
- CI builds an explicitly **unsigned** Play AAB without signing credentials and retains its R8 mapping. No private Debug APK is uploaded. An unsigned CI artifact is not upload-ready.
- Local artifact and test evidence is recorded at the end of this document after validation.

## 1. Account and app identity

- [ ] Create/verify the developer account, accept agreements, complete identity/contact/device verification and any organization/D-U-N-S requirements Console presents. Check Android developer verification and app registration status.
- [ ] Confirm legal developer identity, public developer name and region-specific trader disclosures. The app/policy identify Lohe and Atomic 22 Industries.
- [ ] Create the app: default language, app rather than game, name and free/paid choice. Music & Audio is the proposed category; owner confirms pricing, countries and tags.
- [ ] Confirm `com.lohe.app` is available or belongs to the account. Package identity becomes permanent.
- [ ] Check highest versionCode uploaded in every track: `1003` must exceed it. Another source set cannot reuse this code after upload; request the next semantic release under this repo's versioning convention.

## 2. Store listing

- [ ] Name (30 characters maximum), short description (80), full description (4,000), category/tags, languages/translations. Draft copy: `play-store-listing.md`.
- [ ] Support email **atomic22industries@gmail.com** (owner supplied), website/phone where appropriate or required.
- [x] Privacy URL: `https://dave-patrick.github.io/Lohe/privacy/`. Published with the user's GitHub publication request and verified November 1, 2026: public HTTPS 200, no login or editing access, HTML policy with the supplied contact address.
- [ ] Upload an actual **512 × 512** store icon, **1024 × 500** feature graphic and at least two genuine phone screenshots meeting current Console image/format requirements. Launcher density icons are not automatically store icons. Do not expose personal library data or claim car functionality; add tablet assets only for form factors claimed. Confirm promotional image/content rights.
- [ ] Release name and localized release notes; no Google endorsement or unverified feature claims.

Source: [preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

## 3. Privacy and Data Safety

- [ ] Approve `privacy-policy.md`. Offline text is in Settings → About → Privacy policy. `pwsh tools/prepare-privacy-policy.ps1` generates the app asset and website HTML; `-Check` verifies consistency.
- [ ] Complete Data Safety for the shipped bundle and dependencies. Do not copy the initial draft's blanket “app activity collected / no data shared / all encrypted” answers.

| Verified data flow | Assessment needed |
| --- | --- |
| Library/history/preferences/playlists processed on device | On-device-only processing is outside the collection definition. Local history is not collected merely because it exists. |
| Apple receives search words/show-title lookups and network request information | Assess search history and applicable identifiers, functional purpose and optionality. Recipient retention must not be assumed ephemeral. |
| Publisher/feed/artwork/audio/CDN requests expose resources and an IP address | Assess third-party collection categories and practices. User-initiated transfers may qualify for a sharing exception; that does not automatically remove collection disclosures or cover scheduled refresh. |
| Optional local diagnostics; user-selected export/sharing/support messages | No automatic crash upload. Assess actual user-directed transfers separately; files/email may contain information the user includes. |
| Android-managed backup/restore and selected external storage | Policy discloses platform/provider behavior. Assess current platform-service exclusions. There is no developer-operated library-sync backend. |

- [ ] Answer collected/shared/optional/required/purpose/deletion questions for each applicable category. No advertising ID or ad/analytics SDK is intentionally included. “No collection” requires assessment of **all** off-device requests, not just absence of a developer server.
- [ ] Encryption: catalog access is HTTPS; arbitrary public HTTP feeds/audio remain supported. Do not attest every transfer is encrypted solely because search uses HTTPS. Apply user-directed-transfer exceptions only when their criteria are met.
- [ ] Lohe has no account creation, so the current account-deletion URL requirement is not applicable. Android clear-storage/uninstall removes app-private data; exports/shared copies/OS backups require separate deletion. Reassess if accounts/sync are added.
- [ ] Keep policy, store answers and actual behavior aligned on every update.

Sources: [Data Safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [privacy and account deletion policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

## 4. App content and audience

- [ ] App access: no login required. Explain search → subscribe → play, Settings, optional permissions and any actual region/network limitations to reviewers.
- [ ] Advertising: Lohe injects no ads/includes no ad SDK. Publishers may include ads in podcast audio; review Console wording against the actual content distributed.
- [ ] Complete IARC for an unrestricted podcast player and third-party content access. Do not infer a rating from UI appearance.
- [ ] Choose actual target age groups; app is not specifically child-directed. Do not select children without satisfying Families requirements. Owner confirms audience and countries.
- [ ] Complete all App content questionnaires Console presents: government affiliation, financial features, health, news designation, advertising ID and others. Current app is a general podcast player, with no payments/account/ad SDK or contact/location/camera/microphone access; podcast topics alone do not establish those app features.
- [ ] Confirm rights to Lohe branding/art/promotional assets and comply with intellectual-property/content/applicable-law requirements. Do not imply ownership of third-party podcasts.

## 5. Foreground services and permissions

Use the **merged Play manifest** as the inventory. Playback uses `mediaPlayback`; optional persistent floating controls use `specialUse` with a manifest subtype. `POST_NOTIFICATIONS` is offered contextually on Android 13+. Media-session notifications have Android exemptions; ordinary listening does not require the optional floating-control notification. Overlay access is Android special access, not a dangerous runtime permission.

- [ ] Declare **mediaPlayback**: user starts an episode; the service maintains audio/media controls in the background. Deferral/interruption would prevent or interrupt the requested listening session.
- [ ] Declare **specialUse**: user explicitly enables a visible draggable playback overlay over other apps, including while paused. It can be dismissed in Lohe or by notification when notifications are allowed. Deferral/interruption removes requested controls; audio uses a separate media service.
- [ ] Provide accessible video URL(s) for both FGS types, recorded from the actual minified Play build.
- [ ] Complete any additional permission form Console presents. Do not assume `SYSTEM_ALERT_WINDOW` always has a universal separate video declaration; demonstrate it as part of special-use setup and answer the actual forms.
- [ ] Inspect transitive permissions for unexpected advertising, all-files, package-install, location, microphone, camera, accessibility or full-screen-intent access. `RECEIVE_BOOT_COMPLETED` supports persisted background maintenance, not automatic audio at boot.

Video A: launch → choose episode → Play → background app → show continuing audio/media controls → pause/stop.

Video B: Settings → Floating player → enable → rationale → Android Display over other apps grant → return → notification rationale → allow/decline → show draggable controls over another app → hide via notification if allowed or disable in Lohe. Demonstrate denial leaves ordinary playback usable.

Source: [FGS declaration, impact and video requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en).

## 6. Signing and artifact verification

- [ ] Enroll in Play App Signing; record app-signing and upload certificate fingerprints. For existing GitHub Release installations to update to Play, preserve the existing **app-signing certificate** through the appropriate enrollment/import path. A new Google-generated app-signing key can break update compatibility. Debug intentionally has its own key.
- [ ] Back up signing material securely outside Git; never put private keys/passwords in docs/logs/chat. `local.properties` stays private. A signed AAB alone does not establish the certificate Play will use for delivered APKs.
- [ ] Build/validate with JDK 17 and configured Android SDK:

```powershell
./gradlew.bat -PlohePlayStore=true :app:bundleRelease :app:assembleSmokeRelease :app:lintRelease :app:testDebugUnitTest
pwsh tools/prepare-privacy-policy.ps1 -Check
pwsh tools/verify-play-bundle.ps1 -BundlePath app/build/outputs/bundle/release/app-release.aab -BundletoolPath app/build/tools/bundletool-all-1.18.3.jar -RequireSigned -ApkPath app/build/outputs/apk/smokeRelease/app-smokeRelease.apk -ZipalignPath C:/Users/davem/AppData/Local/Android/Sdk/build-tools/36.0.0/zipalign.exe
```

The tool inspects bundle structure, package/SDK/debug state, permissions/car exclusion, signatures and applicable native 64-bit ELF / APK ZIP 16 KB alignment. It does not certify Console answers or test an actual 16 KB device.

- [ ] Save AAB, matching R8 mapping, build ID and SHA-256 together. Upload only the signed production AAB; keep smoke and private Debug local.
- [ ] Inspect Play-generated APKs/device exclusions after internal-track upload. Target API 36 meets the currently published phone requirement; recheck at submission. Exercise native libraries on a 16 KB environment where available.

Sources: [target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [16 KB](https://developer.android.com/guide/practices/page-sizes), [car distribution](https://developer.android.com/training/cars/distribute).

## 7. Testing and submission

- [ ] Internal-track signed AAB first: inspect App Bundle Explorer, device exclusions, pre-review/pre-launch reports, crashes/ANRs, accessibility and policy warnings.
- [ ] Verify fresh install, upgrade/library preservation, background audio/media notification, resume/queue/playlists, offline audio, optional permission grant/denial/revocation, privacy text and file import/export on representative Android versions/form factors. Sanitize listing screenshots.
- [ ] If a personal account was created after November 13, 2023, run the required closed test: **12 continuously opted-in testers for 14 days**, then apply for production access and answer testing questions. Account eligibility may differ; Console is authoritative.
- [ ] Complete countries/pricing, release name/notes, policy URL, support, app-content/signing/audience/rating/review attestations and rollout settings. Resolve Publishing overview blockers before Send for review.
- [ ] No car opt-in until separate car validation. Local tests are not Play approval.

Source: [new personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

## Latest GitHub release — build 51

- Source, private Debug and signed public Release build 2026.01.11.51 include the background library-subscription fix described in `battery-investigation.md`. Release remains 0.10.3. The build 50 Play bundle below does not include this newer fix; rebuild and validate a matching Play bundle before submission. No Play bundle was published.
- Published source commit `b909a0b683e60554ef5218bd04a590eb8ec953f7` and [GitHub release 0.10.3, build 51](https://github.com/dave-patrick/Lohe/releases/tag/v0.10.3-build-2026.01.11.51). Public assets contain the signed ordinary Release APK and SHA-256 file only, not private Debug or smoke APKs. APK SHA-256: `197f113b06c7171fd1ca6fe89c48e08a83c9e66a2ee9030875dfd5c006ca801c`; downloaded public bytes match. Signing certificate matches the preceding public release.
- Minified public APK upgraded successfully from the prior published 0.10.1 release in an isolated API 36 emulator (4 KB pages), retained its saved Light appearance, displayed release/build 0.10.3 / 2026.01.11.51 and opened the offline privacy dialog with its contact address. No app crash was recorded. This does not establish actual 16 KB runtime, car head-unit behavior or long-term battery savings.
- The public site and privacy page deployed successfully through GitHub Pages and both returned HTTP 200. The landing page shows the new build. Newly attached screenshots and local handoff/performance drafts were excluded from the source commit. The phone remains on private Debug.

## Background startup crash fix — November 1, 2026, build 50

- Samsung's crash warning was confirmed by main-app crash records: `ServiceLocator not initialized` in `PlaybackService.startSeekIncrementObserver`, including repeated background starts at 08:47 and 12:17 on November 1. These predate the build 49 installation; the same missing initialization remained in source. Do not submit the older bundles below with this known defect.
- `PlaybackService.onCreate` now initializes its dependencies before creating the player/session or launching observers, matching the other background entry points. Independent diagnosis agreed; independent review found no issues.
- New `PlaybackColdStartTest` connects through a MediaBrowser in a fresh isolated `.audit` process without opening an activity or fixture initialization. Before the fix it crashed with the exact reported stack. After the fix it passed (one test, no skip). Run this class alone; it skips when another fixture has already initialized the process.
- The full JVM suite passed **306 tests, zero failures/errors/skips**. Ordinary release lint passed. The initial broader phone run had one queue-advancement timeout; that test passed alone and the complete rerun passed **15/15**. Together with the separate cold-start run, **16 phone tests passed**. This is not a claim that long-term battery usage has been resolved.
- Private Debug **0.10.3, build 2026.01.11.50** installed and launched on the main phone, preserving data after a validated 25-entry compressed backup outside the repo. Installed APK SHA-256 matches `5e09e58017e0a3f53ce9ce50252185f52bbeab2605133305937d019e9315b3cf`.
- Signed Play bundle for build 50: `app/build/outputs/play/Lohe-0.10.3-build-2026.01.11.50-play-release.aab`, SHA-256 `57d398f7d6f78f3106d1da351c6877a60e43ada5b450b05a7df8acc1232d1c41`. Matching R8 mapping, verification JSON and checksum are beside it. Bundletool, signature, package/SDK/debug/car/permissions and native 64-bit 16 KB alignment checks passed. No new minified runtime walkthrough or actual 16 KB device test is claimed. Nothing committed, pushed or published.

## Historical artifact and queue-completion fix — November 1, 2026, build 49 (superseded)

- Historical signed AAB: `app/build/outputs/play/Lohe-0.10.3-build-2026.01.11.49-play-release.aab`; matching mapping, verification JSON and checksum are beside it. Release stays 0.10.3 / versionCode 1003. Check Console's highest uploaded code before uploading, as described above.
- Historical AAB SHA-256: `c279e46a0a0a8bbc8b41f1570d8b50605a21ecd1b2430db538462bd532b4dc86`.
- Fixed two reproduced replay paths: repeat ONE/ALL kept the service's one-item player looping without a terminal ended event; a stale reorder payload could reinsert an episode already removed on completion. Independent diagnosis/review agreed with these paths.
- `PlaybackService` now filters only repeat-setting from the controller-aware `onConnectAsync` result and defensively resets non-off repeat modes. It preserves default trust restrictions, playback controls and library access. `EpisodeDao.renumber` updates current queue members only; explicit enqueue can still request a replay. Debug transport snapshots include repeatMode for diagnosis.
- The three behavior regressions failed on the old app. Final **15/15 phone tests passed**, including repeat ONE/ALL through two complete episodes to an empty queue, stale reorder versus intentional replay, session controls/library access, natural completion, sleep timer, interrupted resume, browsing and seeking. Test file: `PlaybackCompletionIntegrationTest.kt`, plus the existing browse/seek suites.
- Full JVM suite: **306 tests, zero failures/errors/skips**. Play `lintRelease`: **zero errors, 96 warnings, 4 hints**. Independent final review: no findings. A trial implementation's legacy connection-callback regression was caught by the broader phone suite and corrected before main-app installation.
- Main private Debug **0.10.3 build 2026.01.11.49 installed and launched**; installed APK SHA-256 matched the named build artifact. App data was backed up in a validated 25-entry compressed archive outside the repo and preserved.
- Updated signed/minified Play AAB passed bundle structure, signature, production package/SDK/debug-state/car-opt-in/permission checks. All four 64-bit native libraries retain 16 KB load alignment; bundle config specifies PAGE_ALIGNMENT_16K. No new minified UI walkthrough or actual 16 KB runtime/head-unit test is claimed; the older smoke APK evidence below is historical.
- Privacy URL was rechecked November 1 and still returns HTTP 404. Deployment, store assets/videos and Console/account/review steps remain outstanding. Nothing was pushed, published or committed.

## Historical evidence — October 31, 2026, build 47 (superseded)

- Built signed production AAB: `app/build/outputs/play/Lohe-0.10.3-build-2026.31.10.47-play-release.aab` (13,853,073 bytes). Matching R8 mapping, verification JSON and checksum are beside it.
- AAB SHA-256: `07d434700a264788bdcee4256c0b624571c9d13a01daa3a6df8a5f0908cc831d`.
- Bundletool 1.18.3 validation, JAR signature verification and merged manifest checks passed: production package, target 36, versionCode 1003, non-debuggable, no car opt-in declarations or unexpected restricted/advertising permissions.
- Release certificate SHA-256: `d48ae0963817107e96ddeb1da4ed3b4456b2087c410e1539834576795cb6b1df`. It matches the existing signed Release APK. Play App Signing enrollment still needs the owner's compatibility decision.
- All four arm64/x86_64 native libraries have 16,384-byte load alignment; bundle config specifies `PAGE_ALIGNMENT_16K`. Minified smoke APK passed `zipalign -c -P 16 4` and APK signature verification.
- Full JVM suite: **306 tests, zero failures/errors/skips**. Play `lintRelease`: **zero errors, 96 warnings, 4 hints**. Warnings are not a clean-lint claim; the new notification constant is offered only through the Android 13+ permission path, and remaining style/API advisories are in the lint report.
- Real phone instrumentation: **4/4 passed** — overlay rationale cancellation, optional notification Not now/repeated enable, actual Android notification denial, and offline About policy open/close with the supplied contact.
- Minified, signed, non-debuggable `com.lohe.app.releaseaudit` installed and its process started on the phone (API 37, 4 KB pages). Interactive release UI/playback walkthrough is **pending phone unlock**; a running process alone is not a completed UI smoke test. No actual 16 KB runtime or head-unit test is claimed.
- Generated policy consistency check, local privacy-page HTTP 200/contact check, listing text character limits (21/77/1,634), public Pages staging/link checks, Herald validation, and independent review passed. One review finding (omitted public guide) was fixed; the second round had no findings.
- Intended public privacy URL currently returns **HTTP 404**. Deployment, store assets/videos, developer-account/Console declarations, internal-track/closed testing where applicable and Play review remain outstanding. The handoff forbids remote pushes; nothing is committed or published. The user's main Debug app and library were not replaced.
