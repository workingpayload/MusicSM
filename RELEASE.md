# Release notes

## v1.0.0 — 2026-09-13

First release of MusicSM: a dark, glassmorphic YouTube‑backed music player.

### Highlights

- **Streaming playback** of YouTube audio through Media3/ExoPlayer, with a background foreground service, media notification, and lockscreen controls that reopen the app.
- **Personalized home** — *Recommended for you* and *Because you liked …* shelves seeded from your liked songs, real **Trending now** from YouTube's `trending_music` kiosk, and curated genre/mood shelves.
- **Browsing** — Search across songs, albums, and artists; dedicated **Album** and **Artist** detail pages (open a page instead of playing immediately).
- **Library** — like songs and build local playlists (Room‑backed).
- **Now Playing** — blurred artwork backdrop, live synced lyrics, queue, and volume.

### UI & polish

- Hue‑cycling frosted‑glass play/pause button (samples the artwork backdrop) with press and icon‑swap animations.
- Animated multi‑hue seek bar that flows while playing.
- Frame‑interpolated playback position for a smooth seek bar and accurate lyric highlighting between state ticks.
- Pull‑to‑refresh on Home; shimmer skeleton cards while the feed loads.
- Horizontal slide transitions between screens.
- High‑resolution artwork on the Now Playing screen (original‑size decode) and upscaled thumbnails throughout.

### Album page actions

- **Play / Shuffle** the album.
- **Favorite** likes every track; **Add** saves the album as a local playlist. Both reflect real, reactive state from the library.

### Known limitations

- Recommendation shelves need liked songs; with an empty library, Home shows Trending + genres only.
- No account / sign‑in — no YouTube Music listening‑history personalization.
- Not shippable: GPLv3 `NewPipeExtractor` + YouTube ToS. Personal/educational use only.

### Tech

Kotlin 2.3.20 · Compose + Material 3 · Media3 1.11.0 · Hilt · Room 2.8.4 · Coil 3 · NewPipeExtractor v0.26.5 · minSdk 24 / targetSdk 37.
