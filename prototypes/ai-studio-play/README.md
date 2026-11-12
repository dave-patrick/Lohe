# Lohe — Web Audio & Play Store Subscription Prototype

> Imported AI Studio reference prototype, not the native Android application.
> Read [IMPORT_STATUS.md](IMPORT_STATUS.md) first: the exported descriptions below
> include unverified claims, nonfunctional Android build stubs, and simulated billing.
> Run web commands from this directory. Build the real Android app from the repository root.
> Use public demo feeds only: feed HTML is not sanitised, and a third-party proxy
> fallback receives complete feed URLs. Do not enter private feeds or credentials.

<p align="center"><img src="/assets/kani.png" alt="Kani, Lohe's blue dragonfly mascot" width="180"></p>

<p align="center">
  <strong>ua lohe ʻoe? — Have you heard?</strong><br>
  <em>Clean, private, tracker-free audio player prototype for Android and Web.</em>
</p>

---

## ⚠️ Prototype Functionality & Simulated Billing Notice

This repository contains the interactive **React / Vite companion prototype** for the Lohe podcast player, along with the multi-flavor Android build configuration (`build.gradle.kts`, `settings.gradle.kts`) and integration specifications.

### Explicit Prototype Boundaries (What is Simulated vs Native):
1. **Google Play Billing Simulation**:
   - The in-app purchase flow and subscription sheet in this web build operate on **local prototype test fixtures** (`isTestFixture: true`).
   - Mock order IDs (`GPA.3312-...`), simulated token strings (`test_fixture_token_...`), and test RSA digests are client-side fixtures for evaluating UI states and entitlement gates in the browser.
   - **In production release builds, test fixtures are strictly rejected** (`IS_PRODUCTION_ENVIRONMENT = true`). Real production entitlement requires server-side validation against Google Play Developer API (`purchases.subscriptionsv2.get`).
2. **Floating Player Overlay Simulation**:
   - The in-app floating player preview in this web build renders within the DOM container.
   - In production native Android builds, the floating player is executed by `FloatingPlaybackService`, which uses Android's `SYSTEM_ALERT_WINDOW` permission and `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` to render draggable playback controls on top of third-party applications and the home launcher.
3. **FOSS Standalone Edition**:
   - The FOSS edition is built with **zero proprietary Google Play Billing or Google Play Services libraries**.
   - Pro features (Floating Player overlay, Glow & Diamond Up Next indicator styles, precision custom HEX color engine) are **strictly unavailable in FOSS builds**. An edition switcher in the prototype cannot bypass entitlement checks.

---

## Subscription & Monetization Architecture

* **Product SKU**: `lohe_pro_annual` (Type: `BillingClient.ProductType.SUBS`)
* **Base Plan**: `lohe-pro-annual-base`
* **Cadence & Pricing**: Single annually renewing subscription at **US$9.99/year** ($0.83/month billed annually).
* **Billing Library**: Compatible with **Google Play Billing Library 8.0.0+** (meeting Google Play's submission requirements as of November 2026).
* **RTDN & Verification Architecture**:
  - Real-Time Developer Notifications (RTDN via Google Cloud Pub/Sub) act as an **asynchronous event trigger**, not receipt verification.
  - When an RTDN event occurs, the backend service queries the authoritative Google Play Developer API:
    `GET https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}`
  - The resulting `SubscriptionPurchaseV2` resource is the single authoritative source of truth.
* **Lifecycle State Coverage**:
  - `ACTIVE`: Active auto-renewing subscription.
  - `CANCELLED`: Auto-renewal cancelled in Play Store; retains Pro access until the end of the paid billing period (`lineItems[0].expiryTime`).
  - `IN_GRACE_PERIOD`: Payment failed; Google Play retrying; access continues dynamically until the Play-configured line-item expiry (`lineItems[0].expiryTime`).
  - `ON_HOLD`: Account hold after grace period lapses; access is suspended.
  - `PAUSED`: Subscription paused by user in Google Play; access is suspended.
  - `REVOKED`: Internal derived state representing developer/system cancellations with refunds mapped from `canceledStateContext`.
  - `PENDING`: Payment deferred / awaiting confirmation; access held until authorized.
  - `EXPIRED`: Cycle ended; floating player is paused while user preferences (`floatingSizeDp`, `floatingTransparencyPct`, `floatingPlayerEnabled`) are preserved.
* **Non-Destructive Acknowledgement Retry**:
  - The client retries `BillingClient.acknowledgePurchase()` and reconciles with Google Play's backend rather than declaring purchases refunded locally due to a client-side timer expiration.
* **Client Offline Tolerance Policy**:
  - If the client device is offline, previously verified active entitlement is cached locally for up to 7 days (`CLIENT_OFFLINE_VERIFICATION_TOLERANCE_MS`), provided the subscription has not exceeded its known `expiryTime`.

---

## Free Baseline Features (100% Free in Both Play Free & FOSS)

The following core features remain unconditionally free across both Google Play Free and FOSS Standalone editions:
* Standard in-app mini-player (`NowPlayingBar`), background audio, and lock-screen/notification playback controls
* Volume Boost (audio pre-amp gain) and Skip Silence
* Unlimited playlists, folders, and smart playlists (Played, In Progress, Unplayed, Downloaded, New Releases)
* Per-show automatic queueing and head/tail placement
* Local folder and offline playback
* Battery optimization setup and guides

---

## Getting Started (Web Prototype)

### Prerequisites
* Node.js 18+ or 20+
* npm or bun

### Installation & Execution

```bash
# 1. Install dependencies
npm install

# 2. Start development server (Port 3000)
npm run dev

# 3. Build production bundle
npm run build

# 4. Run type check and linter
npm run lint
```

The application will be served at `http://localhost:3000`.

---

## Multi-Flavor Android Build Configuration

The native project configuration is defined in `build.gradle.kts`:

```bash
# Build Google Play Free APK
./gradlew :app:assemblePlayFreeRelease

# Build Google Play Paid / Pro APK
./gradlew :app:assemblePlayPaidRelease

# Build Libre FOSS APK (Zero Google dependencies)
./gradlew :app:assembleFossRelease

# Verify FOSS runtime classpath has zero billing dependencies
./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath
```

---

## License

* **FOSS Edition**: 100% Free and Open Source under the project's libre license.
* **Branding & Assets**: Copyright © 2026 Atomic 22 Industries. All rights reserved.
