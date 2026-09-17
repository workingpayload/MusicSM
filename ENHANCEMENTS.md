# MusicSM — Enhancements Roadmap

A catalogue of ideas to improve the user experience, derived from a full read of the
current codebase (v1.5.0). Each item notes **current state**, the **proposed
enhancement**, and a rough **impact / effort** rating.

Legend — Impact: 🔥 high · ✨ medium · 💤 low  |  Effort: S (hours) · M (a day or two) · L (multi‑day)

---

## 0. Quick wins (highest value per hour)

| # | Enhancement | Why | Effort |
|---|---|---|---|
| 1 | **Sleep timer** | The single most requested feature in any music app; nothing exists today. | S |
| 2 | **Long‑press / "…" context menu on every song row** (`ui/components/SongRow.kt`) with *Play next, Add to queue, Add to playlist, Go to album, Go to artist, Download, Share* | Backend commands already exist (`MediaControllerManager.playNext/addToQueue`) but are unreachable from most screens. | S |
| 3 | **"Go to album / artist" from Now Playing** | Metadata is already in `PlayerState`; today the player is a dead end. | S |
| 4 | **Share song / playlist** via `ACTION_SEND` | No share path exists anywhere in the app. | S |
| 5 | **Drag‑to‑reorder the queue** | `MediaControllerManager.moveItem()` is implemented but no UI calls it (`ui/player/QueueScreen.kt` only supports remove/tap). | S |
| 6 | **Search history + suggestion chips** | Every search starts from an empty box; no recents, no "clear". | S |
| 7 | **Buffering indicator** | `PlayerState.isBuffering` is tracked but never rendered — taps feel unresponsive on slow networks. | S |
| 8 | ~~**Fix download progress math**~~ — *verified false positive:* `DownloadRepository.progress` is `Map<String, Float>`, so `map.values.sum() / map.size` is float division and the percentage is correct. Replaced with **tappable download notification** (`setContentIntent` → `MainActivity`) + correct "Starting download…" title on service start. | The notification previously did nothing when tapped. | S |
| 9 | **Retry buttons on every error state** | Only Home has one (`ui/home/HomeScreen.kt`); Search / Album / Artist / Playlist / Downloads show bare text. | S |
| 10 | **Global offline banner** | Offline awareness only exists on Home, yet the whole catalog needs network. | S |

---

## 1. Playback experience

### 1.1 Missing playback controls
- **Sleep timer** — end of track / 15‑30‑45‑60 min / custom, with optional fade‑out. Persist across process death.
- **Playback speed & pitch** — `player.setPlaybackParameters()`; useful for podcasts/live sets. Add a 0.5×–2.0× control in the player sheet.
- **Crossfade between tracks** (0–12 s) and **gapless** verification for albums.
- **Skip silence** — `ExoPlayer.setSkipSilenceEnabled(true)` behind a setting.
- **Volume/loudness normalisation** — ReplayGain‑style levelling so imported playlists don't jump in volume.
- **Equalizer** — expose the ExoPlayer audio session id and bind `android.media.audiofx.Equalizer` + bass boost + virtualizer, or offer an "open system equalizer" intent as a cheap first step.
- **Seek gestures** — double‑tap left/right to skip ±10 s; long‑press next/prev to fast‑forward.
- **A‑B repeat / loop section** for practice use.

### 1.2 Reliability & resilience
- **Stream‑resolution retry with backoff.** `StreamUrlResolver` and `PlayerViewModel` wrap everything in `runCatching` and silently swallow failures — a failed track just goes quiet. Add: retry → skip to next → surface a snackbar "Couldn't play *Track*".
- **Player error surface.** Add `error: PlaybackError?` to `PlayerState` and render an inline retry.
- **MediaController reconnection.** `MediaControllerManager.initialize()` builds one future and never retries; if the service dies the UI silently stops responding. Add reconnect + a command queue so taps issued before connection aren't dropped.
- **Cheaper state ticker.** `pushState()` rebuilds the whole queue list every 500 ms; only rebuild on `onTimelineChanged`.
- **Auto‑fallback to a downloaded copy** when a stream resolve fails (the local‑file path already exists in `StreamUrlResolver`).

### 1.3 Session persistence
- **Restore the queue, current track and position on app launch.** Today the queue only lives inside the controller and is lost on process death — a major daily annoyance.
- **Playback resumption** — implement `MediaSession.Callback.onPlaybackResumption()` so the system media notification can restart the last queue after reboot.
- **"Continue listening" shelf** on Home, seeded from `play_history`.

### 1.4 System integration
- **Android Auto / Wear** — upgrade `PlaybackService` from `MediaSessionService` to `MediaLibraryService` and expose a browse tree (Liked / Playlists / Downloads / Recent). Big win, medium effort.
- **Home‑screen widget** (1×1 play/pause up to 4×2 with artwork) and a **Quick Settings tile**.
- **App shortcuts** (`shortcuts.xml`): *Liked songs*, *Resume*, *Search*.
- **Google Cast** support for speakers/TVs.
- **Assistant / voice** — `MEDIA_PLAY_FROM_SEARCH` intent handling ("play X on MusicSM").
- **Bluetooth / car metadata** — verify `MediaMetadata` includes artwork, album, track number, and `EXTRA_*` for car head units.
- **Output switcher** — Media3 `setDeviceVolumeControl` + system output picker in the player.

---

## 2. Library, playlists & organisation

- **Sort & filter everywhere** — Liked songs, playlists and downloads have no sort (A‑Z, recently added, artist, duration, most played).
- **Multi‑select batch mode** — long‑press to select, then *Add to playlist / Download / Remove / Queue*.
- **Swipe actions** on song rows — swipe right to queue, left to remove.
- **Drag‑to‑reorder tracks inside a playlist** — `PlaylistSongCrossRef.position` exists in the schema but there's no reorder UI.
- **Playlist descriptions & rename** — only a cover image is supported today.
- **Folders / pinning / sections** for a large library.
- **Smart playlists** — *Most played*, *Recently added*, *Rediscover* (liked but not played in 60 days), *On repeat*. `play_history` already exists but is unused in the UI.
- **Play counts & listening stats** — a "Your year in music"‑style screen from `play_history` (top artists, top songs, total hours).
- **Liked artists surface** — `liked_artists` is persisted but has almost no UI affordance; add a *Following* tab with new‑release checks.
- **Duplicate detection** when adding to a playlist.
- **Local library export / import** (JSON) so users can back up and move between devices; also useful as a safety net given no cloud account exists.
- **Recently deleted / undo** — deleting a playlist is permanent behind a dialog; a snackbar undo is friendlier.

---

## 3. Downloads & offline

- **Pause / resume / cancel** individual downloads, plus resume after app restart (currently every failure restarts from zero — no HTTP range requests).
- **Retry with backoff** and a visible failed state; `DownloadRepositoryImpl` currently logs and drops errors silently.
- **Download quality setting** (e.g. Low 64k / Normal 128k / High best‑available) and a **Wi‑Fi‑only** toggle.
- **Download whole playlists / albums / liked songs** in one tap, with a queue and overall progress.
- **Auto‑download liked songs** option.
- **Storage management screen** — space used, per‑playlist breakdown, "remove all downloads", move to SD/external storage.
- **Proper audio files** — downloads are stored as `<songId>.audio` in internal storage with no tags or artwork. Consider writing real `.m4a`/`.opus` with embedded metadata so files are usable, and/or a MediaStore export option.
- **Offline mode toggle** — force the whole app to only show downloaded content.
- **Verify integrity** — store expected content length / hash and re‑download corrupt files.

---

## 4. Discovery & content

- **Better recommendations** — `MusicRepositoryImpl.recommendations()` randomises `relatedTo()` results and swallows failures. Blend in play history, liked artists and recency; cache the feed so Home isn't empty offline.
- **Cache the Home feed and search results in Room** with a TTL so the app opens instantly and degrades gracefully offline.
- **Search improvements** — filter tabs (Songs/Albums/Artists/Playlists), search‑as‑you‑type suggestions, recent searches, voice search, and searching *your own library* (currently search is remote‑only).
- **YouTube playlist import** (NewPipe already supports playlist extraction — low effort, high value) plus **Apple Music / CSV / JSON** import.
- **Better Spotify matching** — `SpotifyImportRepositoryImpl` takes the *first* YouTube result. Score candidates on duration delta, title/artist similarity and channel type ("Topic" channels), and show a manual "fix match" UI for failures.
- **Import progress detail & partial‑failure report** — list the tracks that couldn't be matched, and let the user retry or search manually.
- **Artist pages** — add discography tabs, "similar artists", bio, follow button.
- **Album pages** — release year, label, "more by this artist".
- **Radio / autoplay endless queue** — `playWithRadio` exists; add an "Autoplay similar songs when the queue ends" setting.
- **New‑releases feed** from followed artists.

---

## 5. Lyrics

- **Cache lyrics in Room** — every open re‑hits LRCLIB over the network.
- **Manual sync offset** (± ms) per track, persisted.
- **Second provider fallback** (e.g. NetEase / Musixmatch‑style) and a manual search/paste option when matching fails.
- **Full‑screen lyrics mode** with larger type, share‑a‑lyric card, and copy.
- **Translation / romanisation** for non‑Latin lyrics.
- **Distinguish loading vs. no‑lyrics‑found** — `LyricsPanel` shows a spinner for both states today.
- **Word‑by‑word (enhanced LRC) highlighting** for a premium feel.

---

## 6. UI, theming & personalisation

- **Settings screen** — none exists. Needs: theme, audio quality, download settings, playback behaviour, cache clearing, lyrics provider, about/licences.
- **Light theme + system theme following.** `MusicSMTheme(darkTheme, dynamicColor)` accepts both parameters and **ignores them** (`ui/theme/Theme.kt`) — surprising API and no light mode at all.
- **Material You / dynamic colour** from the wallpaper, plus optional "colour from album art" theming (a `DynamicColor` palette helper already exists).
- **Accent colour picker** and an **AMOLED pure‑black** variant.
- **Themed (monochrome) app icon** — `ic_launcher.xml` has no `<monochrome>` layer, so the icon won't theme on Android 13+.
- **Player layout options** — compact vs. full art, blur intensity slider, hide/show lyrics or queue peek.
- **Reduced‑motion support** — shimmer, hue cycling, wave bars and dominant‑colour transitions all run infinite animations with no accessibility gate.
- **Haptics** on transport controls, like, and seek release.
- **Better empty states** — friendly illustrations + a CTA instead of bare text (Search, Downloads, Library, Playlist detail).
- **Onboarding** — a 3‑screen intro covering notification permission, import, and offline downloads.

---

## 7. Accessibility

- **TalkBack pass** — add `contentDescription`/`semantics` to all icon‑only controls and merge song rows into single, meaningfully‑labelled nodes ("Track, Artist, liked, playing").
- **48 dp minimum touch targets** — audit the 44 dp circles and dense rows.
- **Font‑scale resilience** — many fixed `56.dp` / `64.dp` / `160.dp` heights will clip at large text sizes; switch to intrinsic/min heights.
- **Contrast check** on the glassmorphic surfaces; secondary text over blurred artwork can fall below 4.5:1.
- **Live region announcements** for track changes.
- **Keyboard / D‑pad focus order** for TV, Chromebook and external keyboards.

---

## 8. Localisation

- **`strings.xml` contains exactly one entry (`app_name`) and the codebase uses `stringResource` zero times.** All ~100+ user‑visible strings are hardcoded in Kotlin. Extract them all — this is a prerequisite for any translation, and also makes copy review possible.
- Add **plurals** (`%d songs`), **RTL verification** (`supportsRtl` is already true), locale‑aware **duration/number formatting**, and a **per‑app language** picker (`locales_config.xml`).

---

## 9. Responsiveness & form factors

- **Tablet / landscape layouts** — there is no `WindowSizeClass` usage anywhere; every screen is a phone‑centric single column. Add a two‑pane layout (list + detail) and a side‑rail navigation for expanded widths.
- **Foldable support** — hinge‑aware player and table‑top mode.
- **Predictive back** — wire `BackHandler` usages into predictive back APIs so the shared player sheet animates correctly on Android 14+.
- **Landscape Now Playing** — art left, controls right.
- **Chromebook / desktop mode** — mouse hover states and keyboard shortcuts (space = play/pause).

---

## 10. Navigation & entry points

- **Deep links / App Links** — the nav graph has no `navDeepLink` and the manifest has no `VIEW` intent filter. Support `musicsm://song/{id}`, album, artist and playlist links so shared links open in‑app.
- **Handle incoming YouTube / Spotify share links** — "Share to MusicSM" from another app should offer *Play* or *Import*.
- **Queue and Lyrics as real destinations** (currently overlays inside the expanded player) so back behaviour is predictable.
- **Consistent top bars** — screens mix custom back buttons and `BackHandler`; unify into one shared component.

---

## 11. Performance

- **Add stable `key = {}` to every `LazyColumn`/`LazyRow`** — Home, Search, Library, Playlist, Album, Artist and Queue lists mostly lack keys, causing avoidable recomposition and scroll‑position churn.
- **Move Palette/dominant‑colour extraction off the composition** and cache results per artwork URL; it currently decodes bitmaps in several screens.
- **Reduce blurred layers** in Now Playing — multiple stacked blurred `AsyncImage`s are expensive on low‑end devices; downsample the source aggressively.
- **Configure a Coil disk cache** and an **OkHttp cache** — `NetworkModule` builds a plain client with no cache, and lyrics/Spotify clients create their own uncached clients.
- **Baseline profile generation** — a `baselineProfiles/` output exists but there's no `androidx.baselineprofile` module; add one for faster cold start.
- **Enable R8/minify for release** — `buildTypes.release { optimization { enable = false } }` ships an unshrunk, unobfuscated APK. Turn it on with keep rules and measure size/startup.
- **Compose compiler metrics / strong skipping** to find unstable parameters.

---

## 12. Data layer & correctness

- **Foreign keys + `ON DELETE CASCADE`** — no entity declares `foreignKeys`, so orphaned `playlist_songs` rows depend entirely on manual cleanup.
- **`exportSchema = true`** and check schemas into version control for safe, reviewable migrations.
- **Persist settings** (DataStore) — there is no preferences store at all today; every new setting above needs one.
- **Persist search history, queue state and play counts.**
- **Centralise the OkHttp client** — three separate clients with hardcoded user agents (`NewPipeDownloaderImpl`, `LyricsRepositoryImpl`, `SpotifyPublicClient`). One shared, cached, interceptor‑equipped client is easier to tune and debug.
- **Move hardcoded constants into config** — the ~5 h stream TTL in `NewPipeMusicSource`, user‑agent strings, download directory.
- **Rate limiting / backoff for imports** — per‑track YouTube searches currently fire as fast as possible.
- **`allowBackup="true"`** currently backs up the Room DB; either make that deliberate (nice: library survives a device switch) or exclude it explicitly.

---

## 13. Quality, testing & release

- **Tests** — only the generated `ExampleUnitTest` / `ExampleInstrumentedTest` exist. Add unit tests for `LyricsRepositoryImpl` LRC parsing, Spotify URL parsing, stream‑cache TTL, and Room migrations (`MigrationTestHelper`); add Compose UI tests for the player and library flows.
- **Crash / error reporting** — no diagnostics at all; even a local "recent errors" log in Settings would help debugging a personal build.
- **Signing config + versioned CI builds** — `app/release/app-release.apk` is committed to the repo; move to CI artefacts/releases instead.
- **Lint/format gates** — add ktlint or detekt and treat Android Lint warnings as errors in CI.
- **In‑app update check** against GitHub Releases (the app can't ship on Play, so self‑updating is genuinely useful).
- **`applicationId`** is still `com.example.musicsm` — worth renaming before any wider sharing.

---

## 14. Delight / differentiators

- **Listening stats & year‑in‑review** from `play_history`.
- **Song "radio" from any row** — instant endless mix.
- **Collaborative‑feel playlist sharing** via exported links/QR (local‑only, no backend needed).
- **Chromecast‑style "now playing" screensaver / ambient mode** for a docked phone.
- **Audio visualiser** in the player (fits the glassmorphic aesthetic).
- **Widget‑style always‑on lyrics** on the lockscreen.
- **The import mini‑game** (`ui/importer/DinoGame.kt`) is a lovely touch — surface it during long downloads too.

---

## Suggested sequencing

| Phase | Theme | Items | Status |
|---|---|---|---|
| **1 — Polish** | Make what exists feel finished | §0 quick wins, error/retry states, buffering UX, download notification fix | ✅ shipped |
| **2 — Daily‑driver** | Things users hit every day | Queue persistence, sleep timer, settings screen, sort/filter, search history, download resume | ✅ shipped |
| **3 — Reach** | Meet users where they are | Android Auto (`MediaLibraryService`), widget, shortcuts, deep links, share | ✅ shipped |
| **4 — Scale** | Robustness & breadth | Localisation extraction, tablet/landscape, offline caching, tests + migrations, R8 | |
| **5 — Delight** | Differentiate | Stats, equalizer, crossfade, themes/Material You, visualiser | |

### Phase 2 — what landed

| Item | Where |
|---|---|
| Settings screen (playback, downloads, privacy, about) | `ui/settings/SettingsScreen.kt` + `SettingsViewModel`, reachable from the Library header |
| Persisted settings store | `data/prefs/AppPreferences.kt` (SharedPreferences + `Flow` per key) |
| Queue persistence & resume | `MediaControllerManager` saves queue/index/position (throttled) and restores it paused on launch; opt-out via *Resume where you left off* |
| Sleep timer (5–90 min, end‑of‑track, fade‑out, survives process death) | `playback/SleepTimerManager.kt`, `ui/player/SleepTimerSheet.kt`, entry point in the Now Playing top bar |
| Sort & filter | `domain/model/SongSort.kt` + `ui/components/SortMenu.kt`, wired into playlists/liked songs and downloads, persisted per list |
| Search history | Recent-search chips with per-chip and bulk clear; prefix‑deduped as you type |
| Download resume | HTTP `Range` requests into a `.part` file, 416 fallback, cancel, failed state + retry, Wi‑Fi‑only toggle, storage usage + "remove all" |
| Bonus: autoplay similar songs when the queue ends, skip‑silence toggle | `PlayerViewModel`, `PlaybackService` |

### Phase 3 — what landed

| Item | Where |
|---|---|
| Launcher shortcuts (*Resume*, *Liked*, *Search*) | `res/xml/shortcuts.xml` + `ic_shortcut_*` icons, routed as `musicsm://` links |
| Deep links (`musicsm://song/artist/album/playlist/liked/downloads/search/resume`) | `navigation/AppIntents.kt` parses them; `MusicSmRoot` acts on them — no nav‑graph rewiring, so ids that are URLs stay intact |
| Open YouTube / YouTube Music links in MusicSM | `VIEW` filters for `youtu.be`, `*.youtube.com/watch|playlist|shorts`; a track plays with radio, a playlist opens as an album |
| "Share to MusicSM" (`ACTION_SEND`) | Any shared text is scanned for a link; plain text falls back to a search |
| Voice / Assistant "play X on MusicSM" | `MEDIA_PLAY_FROM_SEARCH` → `PlayerViewModel.searchAndPlay` |
| Home‑screen widget (artwork, title, prev/play/next) | `widget/NowPlayingWidget.kt` + `NowPlayingWidgetProvider`, layout `res/layout/widget_now_playing.xml` |
| Quick Settings tile (play/pause + current track) | `tile/PlaybackTileService.kt` |
| Out‑of‑process state for both surfaces | `AppPreferences.saveNowPlaying/nowPlaying` + `playback/NowPlayingPublisher.kt`, pushed by a `Player.Listener` in `PlaybackService`; falls back to the saved queue after process death |
| Transport from widget/tile without a `MediaController` | `playback/MediaButtons.kt` (`ACTION_MEDIA_BUTTON` → `PlaybackService`) |
| Android Auto / Wear browse tree (Liked / Downloads / Recently played / Playlists) + voice search | `playback/MusicLibraryCallback.kt`; `PlaybackService` is now a `MediaLibraryService`, declared with `automotive_app_desc.xml` |
| Playback resumption after reboot | `MusicLibraryCallback.onPlaybackResumption` replays the persisted queue |
| Track lookup by id | `MusicSource.song()` / `MusicRepository.song()` (NewPipe `StreamInfo`), needed by links and shares |

**Still open in §1.4:** Google Cast, the system output switcher, and verifying car head‑unit metadata on real hardware.

### Wear OS companion — what landed

A new `:wear` Gradle module ships a watch app that remote‑controls the phone over the Wearable Data Layer. It shares the phone's `applicationId` (`com.example.musicsm`) — required for pairing — with its own namespace `com.example.musicsm.wear`, and is declared non‑standalone.

| Item | Where |
|---|---|
| Protocol (state path, command path, keys) | `wear/WearContract.kt`, duplicated verbatim in `app/…/wear/WearContract.kt` |
| Phone → watch state push | `NowPlayingPublisher.publishToWear()` writes an urgent `PutDataMapRequest` on `/musicsm/now_playing` with a timestamp, so repeats still fire `DATA_CHANGED` |
| Watch → phone transport | `WearPlayerViewModel.sendCommand` → `MessageClient` on `/musicsm/command`; `app/…/wear/WearBridgeService.kt` (`WearableListenerService`) maps it to a `MediaButtons` key event |
| Watch UI | `WearPlayerScreen.kt` — Wear Compose `Scaffold`/`TimeText`, title + artist, prev/play/next, coral theme matching the phone |
| "Open on phone" | `RemoteActivityHelper` launches `musicsm://resume`, which the existing `AppIntents` parser already handles |
| Optimistic play/pause | The watch flips its own icon immediately, then reconciles with the next data item |

**Not done:** a Wear Tile / ongoing activity, standalone (LTE) playback, offline sync to the watch, and browsing the library from the wrist — the watch only controls what the phone is already playing.

---

*Generated from a full audit of the v1.5.0 source tree. File references point at the code that would need to change.*
