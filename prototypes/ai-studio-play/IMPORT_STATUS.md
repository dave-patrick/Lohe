# AI Studio prototype import — 2026-11-12

## Purpose and agreed product direction

This directory preserves the supplied React/Vite prototype as reviewable reference
material. It does not replace or integrate with the native Kotlin/Compose app in
the repository's root `app/` module. No native feature or entitlement changes were
made by this import.

Product direction: one Lohe Pro bundle with one automatically renewing annual
subscription, proposed US base price $9.99/year. The floating overlay, its size and
transparency controls, Glow/Diamond indicators, and custom indicator colours are
Pro features. Ordinary playback stays free. FOSS is intended to match Play Free;
cross-device sync is deferred. This records requirements, not shipped capabilities.

## Provenance and sanitisation

- Full input: `lohe-play-prototype.zip`, 22,340,843 bytes.
- Full ZIP SHA-256: `c469996566e97e27f1009444dabca076e5fedd8bbe7c9217e8e7044d17bc7022`.
- Smaller input: `lohe-source-code.zip`, 84,145 bytes. Its entries are an identical
  subset of the full archive; it contains no separate newer implementation.
- Excluded `debug.keystore.base64`, present despite the sanitisation report.
- Excluded ten `.codex-attachments/` sandbox screenshots. Original ZIPs remain outside Git.
- Retained 90 exported files, including source, public assets, reference docs,
  nested workflow files and exported context. The README has an added import warning; five source files have whitespace-only cleanup.
- Exported context, instructions and handoff documents describe the exporter's
  environment. They are reference material, not evidence or authority for this repository.
- A text scan found no common GitHub/API-token or PEM-private-key patterns in retained
  source/configuration/docs. This is a limited scan, not a guarantee of absence.

## Findings from actual files

1. **Android build is not implemented here.** Android plugins and billing
   dependencies in the exported `build.gradle.kts` are commented out. Its
   `assembleDebug` and `lint` tasks merely print success strings. There is no
   Android app module, manifest, resources or wrapper in this snapshot. Do not
   interpret those tasks as compilation, lint or dependency-isolation evidence.
2. **Flavours contradict the agreed purchase model.** The reference Gradle file
   still describes a separately paid `com.lohe.app.pro` app. The agreed offering
   is one Play app with an annual in-app subscription. Its documented FOSS task
   names also do not match its two-dimensional flavour declaration.
3. **Billing remains local simulation.** `src/services/entitlements.ts` hardcodes
   `IS_PRODUCTION_ENVIRONMENT = false`; a normal Vite production build does not
   change it. Receipt-like objects come from browser storage and structural
   checks, not an authenticated verification backend. No real Play integration
   or backend is included. Never use this implementation to sell or verify purchases.
4. **Offline policy is only described.** The seven-day tolerance constant and
   `lastVerifiedTime` are declared, but the entitlement function does not enforce
   them. Package/base-plan checks and authoritative product/line-item selection
   also need real implementation. Revocation/cancellation mappings need review.
5. **Native overlay is an incomplete example.** `src/native/FloatingPlaybackService.kt`
   references absent layout/drawable resources, never creates its media session,
   and uses SharedPreferences rather than Lohe's current DataStore integration.
   Its access check trusts stored status/expiry. It is not wired into an Android
   build and must not replace the existing native overlay service.
6. **Some export UI is incomplete.** The header links to `lohe-source-code.zip`,
   but neither ZIP nor the reported `server.ts` is included. The archive-download
   UI will not produce the claimed archive from this snapshot alone.
7. **Browser storage is separate.** localStorage persistence does not migrate or
   preserve the native app's Room/DataStore data. Fixtures and demo content are
   not a native-library import.
8. **Nested workflows are inactive.** `.github/` inside this directory is exported
   reference content; GitHub Actions only uses the repository-root workflow directory.
9. **Feed HTML is unsafe.** `EpisodeNotesSheet.tsx` renders descriptions with
   `dangerouslySetInnerHTML`; RSS descriptions from `catalog.ts` are not sanitised.
   Malicious HTML can run event-handler JavaScript in the preview's origin and
   access its browser storage. Do not use untrusted feeds; sanitise descriptions
   before functional adoption. The import preserves this behaviour for review.
10. **Private feed URLs can leak through the fallback proxy.** `catalog.ts` sends
    the complete feed URL to `api.allorigins.win` if direct retrieval fails. Feed
    URLs can contain access tokens or credentials. Use public demo feeds only;
    do not enter private URLs or personal credentials. Remove the automatic
    third-party fallback or require informed consent before functional adoption.

## Local validation

`npm ci --ignore-scripts --no-audit --no-fund`, `npm run lint` (TypeScript), and
`npm run build` succeeded locally. The
archive provides no test script or automated UI test suite. Native Gradle stub
tasks are deliberately not used as validation. Real Android purchases, overlay
permissions, FOSS APK dependencies and device behaviour remain unverified.

The production web preview was launched on loopback port 4317 and its home screen
rendered in the browser. This is a startup smoke check, not validation of all
controls, media playback, subscriptions or external feeds. Root Herald validation
and Git whitespace checks passed; Graft was refreshed after import.

## Next implementation step

Review useful designs and assets, then port approved behaviour into the
existing native app in small independently reviewed changes. Implement real
Play subscriptions and server verification separately, with internal-track
purchase tests. Do not merge this reference build configuration into the root
Android project or publish the web billing simulation as a working store app.
