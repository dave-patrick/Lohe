# Handoff Prompt — Lohe Google Play Compliance

Use this prompt with another AI agent to continue the Google Play compliance work
for the Lohe Android podcast app.

## Context

- Repo: `C:\repos\Lohe` (remote: github.com/dave-patrick/Lohe)
- App: Android podcast player, Kotlin + Jetpack Compose + Room + Media3,
  minSdk 26, targetSdk 37, compileSdk 37.
- A prior pass already made these code changes (do not redo):
  - `app/src/main/AndroidManifest.xml`: removed `usesCleartextTraffic="true"`,
    added `android:networkSecurityConfig="@xml/network_security_config"`.
  - New `app/src/main/res/xml/network_security_config.xml` (cleartext permitted
    by default, documented justification).
  - `.github/workflows/ci.yml`: added `./gradlew bundleRelease` step.
  - New docs: `docs/privacy-policy.md` (draft, placeholder contact email) and
    `docs/google-play-compliance.md` (full checklist).

## Your task

Continue the Google Play compliance work. Prioritize the items in
`docs/google-play-compliance.md` under "Remaining Play Console steps". Specifically:

1. Replace the placeholder contact email in `docs/privacy-policy.md` with a real
   address (ask the user if unknown).
2. Host the privacy policy (suggest GitHub Pages using the existing
   `.github/workflows/pages.yml`) and confirm the URL.
3. Add in-context runtime permission requests for `POST_NOTIFICATIONS`
   (Android 13+) and `SYSTEM_ALERT_WINDOW` (overlay) with rationale and graceful
   denial handling. The onboarding wizard in `docs/TODO.md` is the intended
   home but is not implemented; implement the minimal permission-request flow in
   Settings as an interim, or wire it into a new first-run screen.
4. Decide on Android Auto / Automotive support: either verify on a head unit /
   automotive emulator, or remove the car-app meta-data (`com.google.android.gms.car.application`,
   `com.android.automotive`, `CarAppActivity`, `CarPlaybackService`) to avoid
   Play review rejection. Document the decision.
5. Verify the release AAB builds locally (`./gradlew bundleRelease`) and that
   R8 minification does not crash at runtime (smoke test).
6. Produce a Play Console submission checklist document listing every field the
   user must fill (Data Safety, content rating, target audience, permissions
   declarations with video, signing/upload key).

## Constraints

- Do not restart the Hermes gateway.
- Do not push to any remote; the user's GitHub account `dave-patrick` is
  read-only on upstreams. Commit locally only after `git fetch` + merge
  origin/main.
- Preserve existing code style and the brand token decisions recorded in
  `docs/TODO.md`.
- Run unit tests (`./gradlew testDebugUnitTest`) after any code change.

## Output

A markdown report at `docs/play-console-submission-checklist.md` plus any code
changes, with a summary of what was done and what still needs the user.
