# Release notes

## v2.0.0 — 2026-09-20

A major release: offline, sharing, room-filling playback, and reach beyond the phone.

### Playlists & sharing
- **Import from a Spotify link** — paste a public playlist URL; every track is matched on YouTube and saved as a local playlist (progress + "matched N/N").
- **Share playlists** — share a playlist as a link / QR code, and **scan a code** to open a shared playlist.
- Custom playlist covers, delete playlists.

### Offline
- **Spotify-style caching** — tracks you play are cached to disk (LRU, capped) and replay offline automatically, no action needed.
- **Downloads** — explicit offline downloads with a **background download service** + progress notification; a Downloads page with a live wavy progress bar; download whole albums/playlists.
- Offline-aware launch: no internet → opens to your library; Home falls back to downloads/recent/liked.

### Playback
- **True overlapping crossfade** — two tracks blend via a second player (not just a fade); tunable duration; gapless when off.
- **Equalizer & audio effects** — multi-band EQ, bass boost, virtualizer, loudness, skip-silence, playback speed.
- **Sleep timer**, high-bitrate audio, and a look-ahead buffer for seamless transitions.

### Discovery
- Taste-based **recommendation shelves** built from history, likes, and followed artists; real trending.
- **Follow artists**, play history (recently played), and a **Listening Stats** screen.

### Beyond the phone
- **Android Auto** (browse + play), **Wear OS** transport, a **home-screen widget**, and a **Quick Settings tile**.
- Deep links & "Open with / Share to MusicSM" for YouTube links; voice "play … on MusicSM".

### UI
- Animated Now Playing: breathing album art, flowing multi-hue seek bar, frosted hue play/pause button, glassy album-tinted volume, smooth live-lyrics transitions.
- Genre tiles with representative art; shimmer skeletons; slide navigation.

### Notes
- Database migrated through v4 (playlists cover, liked artists, play history, downloads); existing library preserved.
- Still not Play-shippable — GPLv3 NewPipeExtractor + YouTube ToS; personal/educational use.

## v1.5.0 — 2026-09-15

### New

- **Import playlists from a Spotify link** — paste a public playlist URL; each track is matched on YouTube and saved as a local playlist, with the Spotify cover art pulled in automatically. A mini "tap the notes" game and an animated wave play while it imports.
- **Custom playlist covers** — set or change a playlist's cover from your photos (persists); falls back to the imported/first-track art.
- **Delete playlists** — from the playlist screen, with a confirm dialog.
- **Album & Artist pages** — open a detail page (art, tracklist, Play/Shuffle) instead of playing immediately.

### Improvements

- **Lyrics** — added a track-only fallback that prefers synced results, so more songs highlight in time.
- **Now Playing** — hue-cycling frosted-glass play/pause button, animated multi-hue seek bar, frame-smooth progress + lyric sync, high-resolution artwork.
- **Home** — personalized recommendation shelves, real trending, pull-to-refresh, shimmer skeleton loading.
- **Modern progress bar** for imports (gradient fill + percentage).
- Media notification / lockscreen tap now reopens the app.
- Horizontal slide transitions between screens.

### Notes

- Spotify import requires a **public** playlist link (the Web API path is blocked for Development-Mode apps).
- Database migrated to v2 (adds playlist cover); existing library is preserved.

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
