# MusicSM

A dark, glassmorphic music‑streaming app for Android. Real audio streamed from YouTube via NewPipeExtractor and played through AndroidX Media3, wrapped in a Jetpack Compose UI inspired by an aurora‑glass design language (coral accent, Plus Jakarta Sans).

> **Not shippable to Google Play.** MusicSM streams from YouTube and depends on GPLv3 `NewPipeExtractor`; it is a personal / educational project, not a distributable product.

---

## Features

- **Home** — personalized shelves built from your liked songs (*Recommended for you*, *Because you liked …*), real **Trending now** from YouTube's `trending_music` kiosk, plus curated genre/mood shelves. Pull‑to‑refresh and shimmer skeleton loading.
- **Search** — songs, albums, and artists with a debounced query and artwork‑tinted header.
- **Album page** — cover art, metadata, Play / Shuffle, full tracklist with the current track highlighted; Add (saves as a playlist) and Favorite (likes every track).
- **Artist page** — circular avatar, top songs, and albums shelf. Opens a detail page instead of playing immediately.
- **Library** — liked songs and user‑created playlists, persisted locally.
- **Now Playing** — blurred artwork backdrop, hue‑cycling frosted‑glass play/pause button, an animated multi‑hue seek bar, live synced lyrics, queue, and volume.
- **Playback** — background playback via a Media3 foreground service; the media notification / lockscreen controls reopen the app.
- **Smooth position** — frame‑interpolated playback position so the seek bar and lyric highlighting stay fluid and accurate between the player's coarse state ticks.

## Screenshots

> Drop images into `docs/screenshots/` with the filenames below (PNG). They'll render here automatically.

| Home | Search | Album |
|------|--------|-------|
| ![Home](docs/screenshots/home.png) | ![Search](docs/screenshots/search.png) | ![Album](docs/screenshots/album.png) |

| Artist | Now Playing | Lyrics |
|--------|-------------|--------|
| ![Artist](docs/screenshots/artist.png) | ![Now Playing](docs/screenshots/now_playing.png) | ![Lyrics](docs/screenshots/lyrics.png) |

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.3.20 (JVM 17) |
| UI | Jetpack Compose + Material 3, MVVM |
| Build | Gradle 9.5, AGP 9.3.2 (built‑in Kotlin), KSP 2.3.11 |
| DI | Hilt 2.60.1 |
| Playback | AndroidX Media3 1.11.0 (ExoPlayer + MediaSessionService) |
| Data | NewPipeExtractor v0.26.5 (YouTube) |
| Storage | Room 2.8.4 |
| Images | Coil 3 |
| Glass blur | Haze |

## Architecture

Single `:app` module, layered so nothing above the data layer knows the source is YouTube.

```
ui/            Compose screens + ViewModels (home, search, library, album, artist, player)
navigation/    Routes, root scaffold, NavHost, bottom bar
domain/        Pure Kotlin models + repository/source interfaces
data/          Impls — only layer importing NewPipe / Room
playback/      Media3 bridge (service, controller manager, stream resolver)
di/            Hilt modules
```

- `MusicSource` (interface) abstracts the catalog + audio; `NewPipeMusicSource` implements it against YouTube.
- `MusicRepository` moves work to IO, caches resolved streams briefly, and builds recommendations.
- `MediaControllerManager` exposes player state to Compose as a `StateFlow`.
- Stream URLs expire (~6 h, IP‑bound), so they are resolved per track at load time by `StreamUrlResolver`.

See [`.codemap.md`](.codemap.md) for a fuller map.

## Building

Requirements:

- Android Studio (uses its bundled JetBrains Runtime for `JAVA_HOME`)
- Android SDK with `compileSdk` / `targetSdk` 37, `minSdk` 24

```bash
# JAVA_HOME must point at the Android Studio JBR
./gradlew :app:assembleDebug
```

Notes:
- `android.disallowKotlinSourceSets=false` is required (AGP 9 built‑in Kotlin + KSP).
- NewPipeExtractor uses `java.nio.file`, so core‑library desugaring is enabled for `minSdk 24`.

## License / legal

Streams via GPLv3 `NewPipeExtractor` and accesses YouTube contrary to its Terms of Service. For personal and educational use only — do not distribute or publish.
