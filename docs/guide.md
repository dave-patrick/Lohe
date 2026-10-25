# Getting started with Lohe

Lohe is an Android podcast player built around listening on your own terms. This guide covers the main listening flows in the current app.

## Install the current release

1. Open the [Lohe 0.10.1 GitHub release](https://github.com/dave-patrick/Lohe/releases/tag/v0.10.1-build-2026.25.10.17) and download `app-release.apk`.
2. Open the downloaded APK on your Android device. If Android asks, allow your browser or file manager to install apps.
3. Launch Lohe and add a podcast from **Discover**, or import an OPML subscription file from your podcast library.

This is a signed Android release distributed through GitHub. It is not listed on Google Play.

## Find and play episodes

- **My Podcasts** contains subscribed shows and their episodes.
- **Discover** searches the podcast catalog so you can find and subscribe to shows.
- Tap an episode to play it, or add it to **Up Next** to build a listening queue.
- In Up Next, drag an episode by its grip to change the order. Podcast settings can add newly released episodes to the beginning or end of the queue.
- Playback continues in the background. The notification and Android media controls provide basic transport actions.

When an episode finishes, Lohe marks it played and removes it from explicit playlists and Up Next. It remains in the Played smart playlist. Its downloaded audio follows the archive delay selected in **Settings → Archiving** (24 hours by default).

## Tune playback

Open **Settings → Playback** to set the skip-back and skip-forward intervals, default playback speed, volume boost, voice enhancement, silence handling, and other playback defaults. The player also has episode-level controls. Use the sleep timer from the player when you want playback to stop after a chosen period.

The **Floating player** settings enable optional playback controls over other apps. Android requires the separate Display over other apps permission; the floating player is off until enabled.

## Organize your listening

Create playlists in **Playlists**, then add episodes and arrange them in the order you want. Grid and list views are available. Smart playlists collect played, in-progress, unplayed, downloaded, and recently released episodes automatically. Their visibility can be adjusted from the Up Next playlist controls.

## Android Auto

Lohe exposes its podcast library and playback through Android's media browser, which Android Auto uses to browse and play audio. Android Automotive OS is a separate system; its in-car screen integration has not yet been verified on a head unit.

## Help and source

- [Project landing page](index.html)
- [Report a bug or request a feature](https://github.com/dave-patrick/Lohe/issues)
- [Source code](https://github.com/dave-patrick/Lohe)
- [Debug control surface documentation](MCP.md)
