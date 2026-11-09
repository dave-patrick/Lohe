# Handoff: Lohe debug build (Material 3 adherence pass)

> Historical handoff for commit `855ee63`. The paths and verification status below describe Claude's original environment. Subsequent fixes reserve full 48 dp button slots, wrap crowded actions, and show the full Podcasts navigation label. Current build metadata is in `app/build.gradle.kts`; public distribution uses the signed Release APK, while the owner's main app remains on the private Debug channel.

APK: /workspace/bright-darwin/app/build/outputs/apk/debug/app-debug.apk
SHA-256: 70151274ba21a94dead4516fdbb0bb078cae03106f322816b06b17b0d516ad3f
applicationId: com.lohe.app.debug  (versionName 0.11.1, versionCode 1101)
Built: 2026-11-09 with
  ./gradlew :app:assembleDebug -PloheDebugApplicationIdSuffix=.debug --no-daemon --no-build-cache \
    -Dorg.gradle.jvmargs=-Xmx3g -Pkotlin.compiler.execution.strategy=in-process
(Gradle daemons kept dying on this device; no-daemon + in-process Kotlin worked.)

## Install notes
- Suffix ".debug" makes this a SEPARATE app from the user's real Lohe (com.lohe.app).
  Installing it does not replace or touch the real app's data. Do NOT uninstall com.lohe.app.
- The new install starts with empty data (no playlists/settings/episode statuses).
- Nothing was installed by Claude: no working adb here (SDK adb is a 0-byte stub; Ubuntu adb 1.0.39 has no `adb pair`).
  Suggested: adb install -r app-debug.apk   (from a client that can reach the phone)
- Phone: Galaxy S26 Ultra, wireless debugging at 192.168.0.118 (ports change; pairing code was never used).

## What changed (uncommitted, 24 files in git diff; new file ui/theme/Shapes.kt)
1. Contrast: dark onPrimary #121214 (5.63:1 on #008EFF); light primary #0077D6 (4.56:1);
   secondaryContainer now slate (#1E3B57 dark / #D3E9F7 light) instead of brand blue; stale comment in Color.kt replaced.
2. Color scheme: filled missing surface/inverse/scrim/tertiary/errorContainer roles (light+dark).
3. Typography: all 15 M3 styles defined; pre-existing 5 styles keep original sizes; spec tracking; labelSmall 11sp.
4. Shapes.kt: M3 4/8/12/16/28dp scale wired into the theme; ~45 inline RoundedCornerShape -> MaterialTheme.shapes
   (pills, artwork clips, partial-corner shapes left alone). GlassCard default corner 20->16dp.
5. Sub-11sp hardcoded text (~20) -> labelSmall (Home chart labels 8/9sp -> 11sp, may overflow at 3 digits).
6. LoheBottomBar: ripple, focus border, selectable Role.Tab, 48dp min, labelMedium (12sp).
7. Touch targets: ~40 buttons raised to 48dp. Dense rows (Episodes, Podcasts, Playlists) use
   TouchTargetIconButton / CompactIconButton (requiredSize 48dp inside a smaller box) -- hit area outside
   the parent's bounds may NOT receive taps. Verify on device.
8. PodcastDetailSheet download icon got a contentDescription.

## Verification status
- :app:compileDebugKotlin and :app:assembleDebug pass.
- Unit tests: could not be run offline (deps not cached); an online attempt printed no failures but was not confirmed complete.
- Device/UI tests NOT run (no adb). App never launched.

## Known gaps / follow-ups
- PrivacyPolicyDialog and BugReportOverlay dialog still stock AlertDialog.
- Brand blue #008EFF still used as text outside HomeScreen.
- QueueRow play button contentDescription is "Now playing" (pre-existing bug).
- Dynamic color / follow-system theme and WindowSizeClass adaptivity not started.
