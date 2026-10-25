# Lohe

**Ua lohe ʻoe? — Have you heard?**

Lohe is an Android podcast player for listening on your own terms. Find the stories, conversations, news, and curious detours you want to hear. Keep your shows and playlists together, shape Up Next, and pick up where you left off.

**[Visit the Lohe landing page](https://dave-patrick.github.io/Lohe/)** · **[Download the latest preview](https://github.com/dave-patrick/Lohe/releases/tag/v0.10.1)** · **[Read the getting started guide](docs/guide.md)**

The current public build is **0.10.1 (build 2026.25.10.15)**. It is a debug prerelease for testing, not a production-signed Play Store release.

## What Lohe does

- Discover and subscribe to podcasts, then download episodes for offline listening.
- Build and reorder Up Next; configure individual shows to automatically add new episodes at the beginning or end.
- Create playlists and browse smart lists for played, in-progress, unplayed, downloaded, and new episodes.
- Resume playback, set skip intervals and playback speed, adjust volume boost and voice enhancement, and use silence controls and a sleep timer.
- Mark episodes played when they finish. Completed episodes leave explicit playlists and Up Next, remain in the Played smart playlist, and follow the configured archive delay for downloaded audio.
- Use Android Auto for media browsing and playback. Android Automotive OS dashboard support still needs head-unit verification.
- Optionally show floating playback controls over other apps.

See the [getting started guide](docs/guide.md) for listening, organization, playback settings, and installation details. Read [docs/MCP.md](docs/MCP.md) for the debug control surface.

## Build from source

Requires JDK 17 and an Android SDK with platform 37. Create `local.properties` in the repository root and set the path to your Android SDK:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Then build the debug APK:

```cmd
gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug            # macOS / Linux
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To run the JVM unit tests:

```cmd
./gradlew testDebugUnitTest
```

Gradle 9.6.0 is provided by the wrapper. The app uses Jetpack Compose, Room, and AndroidX Media3.

## Project structure

- `app/src/main` — Android app, UI, storage, playback, and debug tooling
- `docs/guide.md` — user guide
- `docs/MCP.md` — debug API and MCP setup
- `docs/TODO.md` — known gaps and planned work
- `tools/` — project utilities

Lohe means “to hear, to listen, to take something to heart.” Kani, the dragonfly in the app, keeps an eye on your library so your next listen is ready when you are.
