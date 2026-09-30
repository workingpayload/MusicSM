# Release notes

## v3.0.0 — 2026-09-30

A big playback and reach release: DJ-style **Mix** transitions, a controllable song cache with an offline backup page, word-by-word lyrics far more often, playlist import from Apple Music and YouTube, a much richer Wear OS app, and a landscape layout.

### New
- **Mix** — DJ-style transitions between songs. The next song's opening is blended into the one that's ending, and beats are lined up when both songs already share a tempo, finished with a bass swap. Quiet endings and intros are skipped, and the next song carries on exactly where the blend stopped, so nothing repeats or gets cut. Turn it on in **Settings → Playback → Mix**.
- **Smoother crossfade** — crossfade now blends inside the main player, exact to the sample, instead of lining up a second player (which drifted on some phones). With crossfade at 0 and Mix off, playback stays gapless.
- **Song cache controls** — in **Settings → Downloads**:
  - **Cache played songs** — turn automatic caching on or off (on by default). Turning it off keeps songs already cached.
  - **Cache size limit** — a slider from 256 MB to 16 GB (default 512 MB). Least recently played songs are removed first; making the limit smaller frees space straight away.
  - **Clear cached songs** — delete all cached audio in one tap. Downloads, your library and playlists are kept.
- **Offline backup** — songs that are fully cached now have their own page in the Library and a shelf on Home. They play without internet; remove any of them from the cache.
- **Word-by-word lyrics more often** — four more lyrics sources (BiniLyrics, LyricsPlus, SimpMusic, Unison). The exact recording is identified first, so lyrics match the version playing. A new **Prefer word-by-word lyrics** setting (on by default) waits a moment for a word-timed source instead of settling for line-by-line.
- **Share lyrics as a card** — select lines in the lyrics view and share them as an image with the song's cover, title and artist.
- **Lyrics sync themselves to music videos** — lyrics are timed to the album version of a song, so on a music video with an intro (often 5–25 s, sometimes a minute) every line came in early. When you open lyrics that may be off, MusicSM now finds the song's album version, listens to a stretch of both, measures the gap and shifts the lyrics to match. It runs once per song and stays saved. **Sync now** in the lyrics sync sheet runs it again anytime. It only applies a result when two separate stretches of the song agree, so a remix or live take is left alone.
- **Screen stays on for lyrics** — while the lyrics are open and a song is playing, the screen doesn't turn off. Pause, close the lyrics or leave the player, and it sleeps as usual.
- **Import playlists from Apple Music and YouTube** — the importer now takes Apple Music, YouTube and YouTube Music playlist links as well as Spotify. YouTube playlists are copied song for song, the whole playlist (up to 5,000 songs, not just the first 100). Apple Music playlists come in up to 300 songs, Apple's limit for a shared playlist. Matching also checks each song's length, so live and extended versions are passed over. An import keeps going if you leave the screen, and a YouTube Mix (radio) link brings in its first 50 songs.
- **Videos in Search** — unreleased songs, leaks, covers and live recordings that are on YouTube but not released as songs now show up under **Videos**. Search shows the top songs and videos together; tap **Songs** or **Videos** (or **Show all**) for the full list. Videos play as audio like any song. Turn it on in **Settings → Search → Videos in search** (off by default).
- **Artwork styles** — pick how animated cover videos show in **Settings**: **Card** (the video plays inside the album card while the colours behind it drift slowly), **Top** (Apple Music style: the video spans the top of the player and fades into the controls) or **Full screen**.
- **System volume slider** — the Now Playing volume slider is now your phone's media volume. It moves with the volume buttons, and dragging it changes the system volume instead of a separate in-app level.
- **Cleaner Now Playing** — the top bar is just close and ⋮. Lyrics and Queue sit on either side of the output picker at the bottom, and Share, Sleep timer, About this track and Ambient mode are in the ⋮ menu.
- **Landscape layout** — on wide screens (tablets, foldables, phones turned sideways) the bottom bar becomes a glass side dock with Search and every tab, and the mini player floats at the bottom.
- **Bouncy scrolling** — every list, grid and shelf now scrolls like iOS. Pull past the top or bottom and the content stretches with a rubber-band feel, then springs back. A fast fling that hits the end bounces a little past it and settles. Home keeps Android's usual stretch and pull-to-refresh.
- **Wear OS app** — the watch app is no longer just play/pause/skip:
  - **Search by voice** from the watch and tap a result to play it on the phone.
  - **Lyrics** on the watch, highlighted and scrolling in time.
  - **Always-on screen** that keeps showing the song (and the current lyric line) without burning in.
  - Album artwork, and **Open on phone**.

### Fixes
- **Galaxy Watch and Wear OS media controls work.** Play, pause and skip from the watch's own media controller now reach MusicSM. Before, the watch showed the song but its buttons did nothing.
- **Artist pages no longer list the same song several times.** Music videos and live versions from the artist page were being mixed into the song list. It now shows the artist's songs (up to 50, most popular first), each once.
- **Age-restricted songs play.** YouTube won't play an age-restricted music video without signing in, so those used to be skipped. MusicSM now plays the song's official audio version from YouTube Music instead (same song, just without the video). If there isn't one, the song is still skipped.

### Notes
- Update the watch app too: the new Wear features need both the phone and watch on v3.0.0.
- No database change (still schema v6), so your library carries over untouched. New settings are included in backups.
- Still not Play-shippable (GPLv3 NewPipeExtractor + YouTube ToS); for personal / educational use.
## v2.3.0 — 2026-09-24

A design and polish release: a Liquid Glass bottom bar, animated artwork, finer lyrics and output control, plus a batch of navigation fixes.

### New
- **Scrub the tab bar** — press and slide a finger across the bottom bar; a clear glass lens follows it, lighting each tab it passes (with a haptic tick), and letting go opens the tab underneath.
- **Minimising bottom bar** — scroll down and the tab bar shrinks to a single icon while the Now Playing pill tucks in between it and Search, giving the content more room. Scroll up (or tap the icon) to bring it back. Can be turned off in **Settings → Minimise bar on scroll**; off by default on low-RAM devices.
- **Animated artwork** — releases with a looping cover video play it in Now Playing, optionally full screen. Settings let you turn it off, limit it to Wi‑Fi, or pick the artwork source.
- **Lyrics sync** — nudge synced lyrics earlier/later (100 ms / 1 s steps) or tap to sync the highlighted line to now; offsets are remembered per song. Lyrics matched from a different version of a song are flagged as possibly out of sync.
- **Audio output picker** — see where audio is playing and switch between speaker, Bluetooth, wired, USB, HDMI and cast outputs from Now Playing.
- **About this track** — title, artist, album, duration and source (streamed, downloaded or local file).
- **More Home shelves** — *Daily discover*, *Forgotten favourites*, *Your artists*, *Albums you might have missed* and *More from …*.
- **Settings tab** in the bottom bar.

### Fixes
- **Back from Now Playing returns to the album** (or artist / playlist) you played from, instead of skipping back to Search.
- **The first Back press on Now Playing no longer gets swallowed** (most noticeable while a song was loading).
- **The keyboard no longer pops up when you turn the screen back on** after playing something from Search.

### Notes
- New permission: **Nearby devices** (`BLUETOOTH_CONNECT`, Android 12+) — optional, only used to show Bluetooth output names.
- No database change (still schema v6) — your library carries over untouched.
- Still not Play-shippable — GPLv3 NewPipeExtractor + YouTube ToS; personal / educational use.

## v2.0.2 — 2026-09-20

A small follow-up to v2.0.1.

### New
- **In-app updates** — MusicSM now checks GitHub for a newer release on launch and shows an "Update available" popup with the release notes; you can also **Check for updates** any time from Settings. Updating downloads the new APK and opens the installer, so you no longer have to visit the releases page by hand.

### Improvements
- The **Buy me a coffee** popup now has explicit **Copy** (UPI ID) and **Close** buttons.

### Notes
- In-app updating only applies from this version onward (the installed build has to contain the updater), and each new version must be published as a GitHub release with the APK attached and a higher version tag.
- No database change. Still not Play-shippable — GPLv3 NewPipeExtractor + YouTube ToS; personal / educational use.

## v2.0.1 — 2026-09-20

A stability, performance, and reach patch on top of v2.0.0.

### Fixes
- **Fixed the crash when updating from an older build.** v2.0.0 shipped with R8 full mode stripping reflection-driven code (NewPipeExtractor / Rhino / ML Kit); this build ships with the correct configuration, so upgrading no longer crashes on launch.
- **Fixed a crash while searching.** Duplicate results from YouTube (e.g. same-name official / VEVO / "Topic" artist channels) are de-duplicated, so the results list can no longer crash on a repeated key.
- **Fixed downloads silently failing in the background** — the download service no longer stops itself before a download has registered.
- **Songs that used to "load and stop" now recover** — an expired or rejected stream URL is re-resolved on the fly, and the track is skipped after a couple of retries instead of leaving playback stuck.
- **Tapping an artist opens instantly** instead of landing on a blank screen; the page now shows the artist's name while it loads.

### Performance & battery
- Removed two recomposition storms in the player — playback position is now its own stream — so scrolling and the Now Playing screen stay smooth while music plays.
- The queue is no longer rebuilt and re-serialized on the main thread twice a second.
- Crossfade no longer polls in the background while paused, and the position tick was halved (500ms → 1s); both cut idle battery drain.

### New
- **Local files** — play music stored on your device (scanned from the system media library); like it and add it to playlists like any other track. New entry in the Library.
- **Backup & restore** — export your whole library and settings to a single file and merge it back on a new install (there is no account login). Playlists merge by name and nothing existing is overwritten.
- **Buy me a coffee** — a support option at the top of Settings: pay over **UPI** (Google Pay / PhonePe / Paytm / any UPI app) or copy the UPI ID.

### Notes
- **No database change** (still schema v6) — your library carries over untouched.
- Backups include liked songs, playlists, play history / stats and settings. Downloaded audio files aren't bundled (they re-download on demand), and device-local file references may not resolve on a different phone.
- Still not Play-shippable — GPLv3 NewPipeExtractor + YouTube ToS; personal / educational use.

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
