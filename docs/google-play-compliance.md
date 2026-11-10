# Google Play Store Readiness & Compliance — Lohe

This document provides the definitive audit of Google Play Store readiness for Lohe, distinguishing what is fully implemented and configured within the codebase from the manual tasks required by the account owner in Google Play Console.

Detailed step-by-step submission tracking is maintained in [play-console-submission-checklist.md](play-console-submission-checklist.md). Store copy is drafted in [play-store-listing.md](play-store-listing.md).

---

## 1. Executive Summary

| Category | Status | Details |
|---|---|---|
| **Codebase & Architecture** | **Ready** | Target SDK 37, compile SDK 37, min SDK 26, versionCode 1101, versionName 0.11.1 |
| **Phone/Tablet Manifest Profile** | **Ready** | `-PlohePlayStore=true` strips unverified Android Auto/Automotive car declarations |
| **Foreground Services (FGS)** | **Ready** | `mediaPlayback` and `specialUse` (with manifest subtype property) configured |
| **Permissions & Rationale** | **Ready** | Contextual runtime rationale for `POST_NOTIFICATIONS` and `SYSTEM_ALERT_WINDOW` |
| **Network Security Config** | **Ready** | Cleartext HTTP permitted for legacy third-party podcast RSS/audio; disclosed in policy |
| **Privacy Policy** | **Ready** | In-app offline policy in About; public HTML hosted on GitHub Pages |
| **Build Optimization & R8** | **Ready** | R8 minification, resource shrinking, and 16 KB page alignment checks in place |
| **Store Listing Copy & Graphics** | **Partial** | Copy, 512×512 store icon, and 1024×500 feature graphic ready; phone screenshots pending |
| **Google Play Console Actions** | **Owner Required** | Signing keystore, account verification, Data Safety form, FGS demo videos, closed testing |

---

## 2. Codebase & Technical Readiness (Configured in Repo)

### 2.1 SDK & Package Configuration
- **Application ID**: `com.lohe.app` (configured in `app/build.gradle.kts`).
- **Target SDK**: `37` (Android 16 preview / Android 15+ compatible; exceeds Google Play's annual minimum requirement).
- **Compile SDK**: `37`.
- **Minimum SDK**: `26` (Android 8.0 Oreo).
- **Version Code**: `1101` (monotonically computed: `major * 10000 + minor * 100 + patch` for release `0.11.1`).
- **Version Name**: `0.11.1`.
- **Build ID**: `2026.10.11.90` (visible in Settings → About; decoupled from release code).

### 2.2 Play Store Manifest Profile (`-PlohePlayStore=true`)
- Google Play requires separate manual review and specialized hardware verification for Android Auto / Automotive OS distribution.
- To prevent review rejection for uncertified automotive environments during phone/tablet submission, Lohe maintains a dedicated manifest override at `app/src/play/AndroidManifest.xml`.
- When passing `-PlohePlayStore=true`, Gradle swaps in this manifest to remove:
  - `CarAppActivity` and `CarPlaybackService` declarations.
  - `com.google.android.gms.car.application` metadata.
  - Android Auto XML declarations (`automotive_app_desc.xml`).
- Normal `release` and private `debug` builds retain full car projection capabilities for direct/GitHub releases.

### 2.3 Foreground Service (FGS) Declarations (API 34+)
Google Play strictly gates background services on Android 14+; Lohe declares only two essential foreground service types:
1. **`mediaPlayback`** (`PlaybackService`):
   - Service: `com.lohe.app.playback.PlaybackService`.
   - Purpose: Continuous, user-requested podcast audio playback and platform `MediaLibrarySession` controls when the app is backgrounded.
   - Justification: Pausing audio when backgrounded would break primary podcast player functionality.
2. **`specialUse`** (`FloatingPlaybackService`):
   - Service: `com.lohe.app.playback.floating.FloatingPlaybackService`.
   - Manifest Subtype: Declared with `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`:
     `"Persistent floating playback control overlay over other apps"`.
   - Purpose: Provides user-opted draggable mini-player controls over other applications while listening or navigating outside Lohe.
   - Justification: A dedicated overlay window requires foreground priority to prevent OS process termination while paused over another app; dismissed by the user at will.

### 2.4 Contextual Runtime Permissions
Lohe requests no broad, invasive, or dangerous device permissions (no camera, microphone, contacts, location, broad storage, or advertising ID).
- **`android.permission.POST_NOTIFICATIONS`** (Android 13+):
  - In-app contextual rationale flow implemented via `FloatingPlayerPermissionFlow.kt`.
  - Media session playback notifications benefit from system-level playback exemptions.
  - Floating player notifications are optional; declining leaves regular playback and in-app controls fully operational.
  - Persistent handled flags prevent repeated OS permission nagging.
- **`android.permission.SYSTEM_ALERT_WINDOW`**:
  - Special app access required only when user toggles "Floating player" in Settings.
  - Shows an educational explanatory dialog before opening Android's "Display over other apps" system settings screen.
  - Never requested automatically on app launch or during onboarding.
- **`android.permission.RECEIVE_BOOT_COMPLETED`**:
  - Used exclusively by WorkManager to reschedule background feed refresh and completed episode maintenance. Does not auto-start audio at boot.

### 2.5 Network Security Configuration
- Configured in `app/src/main/res/xml/network_security_config.xml`.
- Search queries (iTunes / Podcast Index) and metadata retrieval use encrypted HTTPS.
- Cleartext HTTP traffic is intentionally permitted (`cleartextTrafficPermitted="true"`) to accommodate legacy third-party podcast RSS feeds and media servers hosted on unencrypted HTTP.
- This behavior is transparently disclosed in both the in-app privacy policy and public documentation.

### 2.6 Privacy Infrastructure
- **In-App Offline Policy**: Bundled in `app/src/main/assets/privacy-policy.txt`, accessible offline from Settings → About → Privacy Policy.
- **Hosted Public Policy**: Generated at `docs/privacy/index.html` and served live via GitHub Pages at `https://dave-patrick.github.io/Lohe/privacy/`.
- **Policy Sync Script**: `tools/prepare-privacy-policy.ps1` synchronizes `docs/privacy-policy.md`, the app asset, and the website HTML; verified with `-Check`.
- **Contact Disclosure**: `atomic22industries@gmail.com` disclosed as the developer contact.
- **Data Scope**: App performs on-device storage (Room database, DataStore). No third-party analytics, crash reporting SDKs, or ad trackers are embedded.

### 2.7 Build & Optimization
- **R8 Minification**: `isMinifyEnabled = true` in `buildTypes.release`.
- **Resource Shrinking**: `isShrinkResources = true` removes unreferenced drawables and layouts.
- **Proguard Rules**: `app/proguard-rules.pro` configures official keep rules for Room, Media3, DataStore, and Kotlin Coroutines.
- **16 KB Page Alignment**: Native libraries and APK packaging verified for Android 15 16 KB page-size compatibility using `tools/verify-play-bundle.ps1`.
- **CI Signing Isolation**: CI builds generate explicitly **unsigned** AABs (`bundleRelease`) without leaking signing keystores; release R8 mapping files are archived with build artifacts.

---

## 3. Store Listing & Graphical Assets

| Asset | Location / Specification | Status |
|---|---|---|
| **App Title** | Max 30 characters: `Lohe Podcast Player` (21 chars) | **Ready** (`docs/play-store-listing.md`) |
| **Short Description** | Max 80 characters: `Fast, private podcast player with playlists, sleep timer, and floating controls.` (80 chars) | **Ready** (`docs/play-store-listing.md`) |
| **Full Description** | Max 4,000 characters (detailed feature overview, private by design, no tracking) | **Ready** (`docs/play-store-listing.md`) |
| **Store Icon** | 512 × 512 PNG, 32-bit color, no transparency: `docs/assets/lohe-mark.png` | **Ready** |
| **Feature Graphic** | 1024 × 500 PNG, centered branding: `docs/assets/feature-graphic.png` | **Ready** |
| **Screenshots** | Minimum 2 phone screenshots (16:9 or 9:16 aspect ratio, min 1080px) | **Pending owner capture** |

---

## 4. Remaining Owner Actions in Google Play Console

The following items cannot be performed through repository code and require manual execution by the account owner:

### 1. Release Keystore & Local Environment
- Create or locate the production release keystore outside Git.
- Configure `local.properties` (ignored by Git) on your local build machine:
  ```properties
  keystore.path=/path/to/release.keystore
  keystore.password=your_keystore_password
  key.alias=your_key_alias
  key.password=your_key_password
  ```
- *Note*: If upgrading existing GitHub Release installs to Play Store builds, enroll in **Play App Signing** by exporting and uploading your existing release key so certificate fingerprints match.

### 2. Google Play Developer Account Verification
- Complete personal or organizational identity verification (D-U-N-S number if organization).
- Confirm public developer profile and trader disclosures.
- Verify contact email (`atomic22industries@gmail.com`) and phone number.

### 3. Data Safety Form Submission
In Play Console under *App content → Data safety*:
- **Data Collection**: Declare that the app does not collect personal data into developer-owned cloud servers.
- **Third-Party Transmissions**: Disclose that search queries contact Apple / Podcast Index APIs, and audio/feed streaming directly connects to third-party podcast hosting servers (which receive client IP addresses).
- **Security Practices**: Disclose that data is transferred over HTTPS (for catalog queries) and HTTP/HTTPS (for public RSS feeds).
- **Account Deletion**: Not applicable (no user accounts or user profiles exist).

### 4. Foreground Service Declaration & Demo Videos
Submit the FGS declaration in Play Console with links to publicly viewable video demonstrations:
- **Video A (`mediaPlayback`)**:
  - Demonstration: Launch Lohe → select podcast episode → press Play → background app / return to home screen → show persistent playback notification and lock screen controls → pause and stop audio.
- **Video B (`specialUse`)**:
  - Demonstration: Open Settings → Floating player → toggle enabled → show rationale dialog → grant "Display over other apps" permission in Android settings → return to Lohe → show draggable overlay over another application → demonstrate playback controls and dismiss overlay.

### 5. Content Rating (IARC) & App Content
- Complete the IARC questionnaire: Music & Audio / Podcast player accessing general Internet content.
- Target audience: Adults / General audience (do not target children under 13 to avoid Families policy requirements).
- Ads declaration: Declare "No" (app contains no third-party ad networks or advertising SDKs; commercial ads in podcast audio are distributor content).

### 6. Screenshots & Visual Submission
- Capture at least two high-resolution phone screenshots (e.g. Home/Player screen and Playlist/Up Next screen) without mockups or unverified features.
- Upload screenshots, 512×512 icon, and 1024×500 feature graphic in Play Console.

### 7. Testing & Production Release
- For personal developer accounts created after November 13, 2023:
  - Upload signed production AAB to the **Closed testing** track.
  - Recruit 12 opt-in testers for 14 continuous days.
  - Apply for production track access after completing closed testing.
- For established accounts: Upload signed production AAB directly to Internal / Production track and submit for review.

---

## 5. Build & Verification Commands

To generate and verify a production-ready Play Store bundle locally:

```powershell
# 1. Build release bundle and smoke release APK with car declarations stripped
./gradlew -PlohePlayStore=true :app:bundleRelease :app:assembleSmokeRelease :app:lintRelease :app:testDebugUnitTest

# 2. Verify privacy policy consistency across app and web assets
pwsh tools/prepare-privacy-policy.ps1 -Check

# 3. Verify bundle integrity, manifest exclusions, signatures, and 16 KB page alignment
pwsh tools/verify-play-bundle.ps1 `
  -BundlePath app/build/outputs/bundle/release/app-release.aab `
  -RequireSigned `
  -ApkPath app/build/outputs/apk/smokeRelease/app-smokeRelease.apk
```

---

## 6. Document Integrity & References

- **Submission Checklist**: [play-console-submission-checklist.md](play-console-submission-checklist.md)
- **Store Listing Text**: [play-store-listing.md](play-store-listing.md)
- **Privacy Policy**: [privacy-policy.md](privacy-policy.md)
- **Feature Graphic Generator**: `tools/generate-feature-graphic.ps1`
- **Bundle Validator**: `tools/verify-play-bundle.ps1`
