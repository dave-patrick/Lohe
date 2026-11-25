# Lohe: project context

## How this repository is produced

- The main Lohe codebase is the **private Play Edition** (`dave-patrick/Lohe-Play-Edition`), developed in Google AI Studio. This public repository is the free, open-source (FOSS) edition generated from it.
- `tools/foss-export/export.py` copies the Play Edition here and removes: the Pro package (`app/src/main/java/com/example/pro`), debug-only owner tools (`app/src/debug`), AI Studio/Firebase configuration, Google Play Billing, Google sign-in and the secrets/google-services Gradle plugins. It sets `Edition.HAS_PRO = false` and the application ID `com.lohe.app.foss`, then fails if any private material remains.
- `.github/workflows/foss-export.yml` runs the export daily or on demand, builds and tests the result, and opens a pull request. It needs the `PLAY_EDITION_TOKEN` secret (read-only access to the Play Edition repository).
- **Do not edit app code here.** Changes to `app/`, Gradle files or `gradle/` are overwritten by the next export. Make app changes in the Play Edition (via AI Studio prompts) instead.
- Files owned by this repository and never overwritten by the export are listed in `KEEP` in `tools/foss-export/export.py` (CI, docs, README, Herald context, Gradle wrapper, tools).

## Editions

- **Play free** and **FOSS** (`com.lohe.app.foss`, labeled **"Lohe (FOSS)"**) have identical core features, and FOSS additionally unlocks direct Cloud Sync via Nextcloud/WebDAV (with automatic `MKCOL` folder creation) and Android SAF file sync (OneDrive, Dropbox, Box, pCloud, Syncthing) so libraries sync seamlessly between Play and FOSS without proprietary dependencies.
- **Play Pro** (`lohe_pro_yearly` subscription) adds the floating cross-app player, extra Up Next indicator styles (Pill, Diamond, Glow), custom hex colours, and Google Drive AppData OAuth sync.
- Pro code lives only in `com.example.pro`; everything else reaches it through `com.example.edition.ProFeatures`, loaded by reflection, with `NoOpProFeatures` when absent. All Pro UI is gated by `Edition.HAS_PRO` and hidden (not shown locked) when it is false.
- Diagnostics and the local MCP/REST debug API are owner-only and belong in the Play Edition's debug source set, so they ship in neither Play nor FOSS releases.

## History

- The original hand-built FOSS codebase (Room schema 14, Android Auto, Pocket Casts tools, MCP) is preserved at the `legacy-v0.11.1` tag / commit `2d123a0`. Use it as a reference when porting features into the Play Edition.

## Imported design reference

- `prototypes/ai-studio-play/` is a separate React/Vite AI Studio reference snapshot. Read its `IMPORT_STATUS.md` before using it. Its billing, Android build files, native overlay example and exported context do not establish implemented native capabilities. Build the actual Android app from the repository root; keep the prototype's Gradle/configuration files isolated.
