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
- **Collaborative‑feel playlist sharing** via exported links/QR (local‑only, no backend needed). — ✅ **done**, see §16.
- **Chromecast‑style "now playing" screensaver / ambient mode** for a docked phone. — ✅ **done**, see §16.
- **Audio visualiser** in the player (fits the glassmorphic aesthetic) — *built in Phase 5 and then removed; see the Phase 5 notes for why.*
- **Widget‑style always‑on lyrics** on the lockscreen.
- **The import mini‑game** (`ui/importer/DinoGame.kt`) is a lovely touch — surface it during long downloads too.

---

## Suggested sequencing

| Phase | Theme | Items | Status |
|---|---|---|---|
| **1 — Polish** | Make what exists feel finished | §0 quick wins, error/retry states, buffering UX, download notification fix | ✅ shipped |
| **2 — Daily‑driver** | Things users hit every day | Queue persistence, sleep timer, settings screen, sort/filter, search history, download resume | ✅ shipped |
| **3 — Reach** | Meet users where they are | Android Auto (`MediaLibraryService`), widget, shortcuts, deep links, share | ✅ shipped |
| **4 — Scale** | Robustness & breadth | Localisation extraction, tablet/landscape, offline caching, tests + migrations, R8 | ✅ shipped |
| **5 — Delight** | Differentiate | Stats, equalizer, crossfade, themes/Material You | ✅ shipped |

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

### Phase 4 — what landed

| Item | Where |
|---|---|
| ~200 hardcoded strings + 6 plurals extracted from Compose/services into resources | `res/values/strings.xml`; call sites use `stringResource` / `pluralStringResource` |
| Domain layer stays Android‑free | `SongSort` lost its `label: String`; the `@StringRes SongSort.labelRes` mapping lives in `ui/components/SortMenu.kt`. `TopLevelDestination` carries `labelRes` directly since it is already a UI type |
| Browse‑tree and deep‑link messages localised | `MusicLibraryCallback` takes a `Context` and maps categories to string ids; `AppIntent.Unsupported` now carries a `@StringRes messageRes` |
| Per‑app language picker (API 33+) | `res/xml/locales_config.xml` + `android:localeConfig`, with a Settings row launching `ACTION_APP_LOCALE_SETTINGS` |
| Stable lazy‑list keys on 17 call sites | Home, Search, Library, Downloads, Album/Artist/Playlist detail, `AddToPlaylistSheet`. **`QueueScreen` is deliberately excluded** — a queue may legally hold the same song twice and a duplicate key crashes Compose |
| One shared, cached `OkHttpClient` (was 4 instances) | `di/NetworkModule.kt` provides it with a 20 MB disk cache; `di/AppEntryPoint.kt` is the escape hatch for the non‑Hilt widget |
| Explicit Coil image cache | `MusicSmApp` implements `SingletonImageLoader.Factory` — 20 % memory + 128 MB disk |
| Large downloads no longer evict metadata | `DownloadRepositoryImpl` fetches tracks with `CacheControl.noStore()` |
| R8 enabled for release — **29.1 MB → 5.6 MB** | `optimization { enable = true }` in `app/build.gradle.kts` + a full `proguard-rules.pro` keeping NewPipeExtractor, Rhino, jsoup, nanojson, Room entities, `WearContract` and manifest‑declared services |
| Room foreign keys + `ON DELETE CASCADE` | `playlist_songs` (both columns), `liked_songs`, `play_history`, `downloads` in `data/local/entity/Entities.kt` |
| DB v4 → v5 migration, schema export on | `MIGRATION_4_5` in `di/DatabaseModule.kt` rebuilds each table, drops orphans, and uses `PRAGMA defer_foreign_keys`; schemas land in `app/schemas/` |
| Migration proven against real SQLite | `tools/migration_check.sql` replays v4 → v5 and asserts data preservation, orphan removal, schema shape, cascade behaviour and constraint enforcement |
| 33 unit tests, all green | LRC parsing, track‑metadata cleaning, stream‑cache TTL, Spotify playlist‑id extraction and `SongSort` ordering, under `app/src/test/` |

**Still open in §4:** tablet/landscape `WindowSizeClass` layouts, a baseline profile, ktlint/detekt, and renaming the `com.example.*` application id (which must change in `:app` and `:wear` together). The R8 release build compiles but wants a device smoke‑test, since NewPipe and Rhino are reflection‑heavy.

### Phase 5 — what landed

| Item | Where |
|---|---|
| **Listening stats & year‑in‑review** | New append‑only `play_events` table (the old `play_history` is keyed by `songId` and trimmed to 20, so it can never hold play *counts*). `data/local/dao/StatsDao.kt` aggregates it; `ui/stats/StatsScreen.kt` renders range chips, metric cards, a daily activity chart, top artists, a 24‑hour listening clock and a ranked song list. Reached from the Library header |
| Day/hour bucketing that respects the user's clock | `strftime('%Y-%m-%d', playedAt/1000, 'unixepoch', 'localtime')` — an 11 pm play lands on the right local day |
| DB v5 → v6 migration, verified against real SQLite | `MIGRATION_5_6` in `di/DatabaseModule.kt` creates `play_events` and back‑fills it from `play_history`; `tools/migration_check_5_6.sql` replays it with 14 assertions |
| **Equalizer + audio effects** | `playback/AudioEffectsManager.kt` owns `Equalizer`, `BassBoost`, `Virtualizer` and `LoudnessEnhancer` on the player's session. Every effect is constructed inside `runCatching` because OEM ROMs throw from these constructors; unsupported controls are hidden rather than shown broken. `ui/settings/EqualizerScreen.kt` exposes device presets, per‑band sliders (mB → dB), bass/virtualizer/loudness |
| Third‑party EQ apps still work | `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` broadcasts, plus the session id is generated up front with `Util.generateAudioSessionIdV21` so the EQ exists before the first track loads |
| **Crossfade + playback speed** | `playback/TrackFadeController.kt`. ExoPlayer has one output, so this is an honest fade‑out/fade‑in, not an overlap — documented in the class KDoc and in the setting's own subtitle. `fadeMs = 0` leaves the player untouched so native gapless still works |
| Three writers on `player.volume` reconciled | The in‑app volume slider, `SleepTimerManager`'s fade and the track fader all move volume. The fader ignores the echo of its own write and treats anything else as a new *base*, applying its envelope multiplicatively — so a sleep‑timer fade and a track fade compose instead of fighting |
| **Light theme + system following, AMOLED, Material You, accent picker** | `ui/theme/Palette.kt` defines `MusicSmPalette` (dark / AMOLED / light) published via `LocalMusicSmPalette`; `ui/theme/Color.kt` turns every existing token (`Coral`, `SurfaceLow`, `OnDark`, …) into a `@Composable @ReadOnlyComposable` accessor, so ~35 screens follow the theme without being rewritten. `MusicSMTheme` finally honours its parameters |
| Default look is unchanged | `DarkPalette` holds the original Stitch values byte‑for‑byte, and `ThemeSettings` defaults to dark / no accent — a user who touches nothing sees exactly the old app. Asserted by `PaletteTest` |
| Overlay tints that survive a light background | ~120 literal `Color.White` uses were classified rather than blind‑replaced: translucent fills became `OverlayTint` (white on dark, near‑black on light), content on coral became `OnAccent`, and content over *artwork* deliberately stayed literal white because a scrim always darkens it |
| Accent sources are mutually exclusive | Material You, album‑art colouring and the manual picker all drive the accent, so `SettingsViewModel` turns the others off when one is chosen and the picker greys out when it isn't in charge |
| Artwork theming without a recomposition storm | `ui/theme/AppThemeViewModel.kt` extracts the palette off‑composition and emits once per track, instead of animating a colour through the root composable |
| **Themed (monochrome) app icon** | `drawable/ic_launcher_monochrome.xml` + `<monochrome>` in both `mipmap-anydpi-v26` icons. Every path is fully opaque — the system tints the layer, so a translucent path would read as a lighter patch, not a highlight |
| 49 unit tests, all green | +9 `PaletteTest`, +6 `ListeningStatsTest` on top of the Phase 4 suite |

**Not done in §5:** the **audio visualiser was built and then removed** — Android gates `android.media.audiofx.Visualizer` behind `RECORD_AUDIO` even for an app reading its own output, and enough devices hand back an all‑zero FFT (offloaded output, several Bluetooth routes, some OEM ROMs) that it wasn't worth a microphone permission prompt for a feature that silently degrades to a fake animation. The "colour from album art" option re‑tints the *accent* only — surfaces stay Stitch‑grey by design, so the app remains recognisable. A true overlapping crossfade would need a second `ExoPlayer` instance. None of this has been smoke‑tested on a physical device; verification so far is compile + unit tests + a SQLite migration replay.

---

## 16. Ambient mode & QR playlist sharing

The last two §14 "delight" items, shipped together.

### Ambient mode — `ui/player/AmbientScreen.kt`

A Chromecast-style screensaver for a docked or idle phone. Reached from the Now Playing overflow menu (the top bar was already full at six icons, and `SongOptionsSheet` already had an `extraAction` slot).

It is a **root-level overlay, not a nav destination** — the expanded player is itself drawn above the nav host, so a route would have rendered *behind* it.

- `FLAG_KEEP_SCREEN_ON`, immersive system bars and `screenBrightness = 0.35f` are applied in one `DisposableEffect` and fully restored in `onDispose`, so none of it can leak into the rest of the app.
- **Burn-in protection:** one 120-second phase drives three independent Lissajous orbits (backdrop, content, clock). The multipliers are whole numbers so the paths stay continuous where the animation restarts at 2π.
- The clock re-syncs on the minute boundary rather than ticking per second, and honours the system 12/24-hour setting and locale.
- Controls auto-hide after 5 s, return on tap; double-tap or back exits.

### Playlist sharing — `domain/share/PlaylistShare.kt`, `ui/share/`

**No backend, no account, no camera permission.** The entire track list is packed into the link itself.

- **Payload `MSM1`:** name + one `id␟title␟artist␟duration` line per track → Deflate → Base64url. Chosen over JSON because a QR code tops out at ~2,950 bytes; 60 tracks encode to under 2,000 characters.
- **Artwork URLs are omitted deliberately** — long, poorly compressible and fully reconstructible, since `Song.id` *is* the YouTube video id.
- Sharing produces `musicsm://shared/playlist?d=…`, rendered as a QR plus "Send link" / "Copy link". Past QR capacity the code is dropped and only the link is offered — that is a normal outcome, not an error.
- The app only ever *shows* codes. Scanning is the system camera's job, which opens the existing `musicsm` scheme filter — hence no `CAMERA` permission.
- **Untrusted input is treated as such:** 1,000-track cap, 1 MiB inflate ceiling (zip-bomb guard), separator characters stripped on encode, and `decode()` returns null rather than throwing. The import screen previews the playlist and **writes nothing until the user taps "Add to library"**.
- Covered by 8 unit tests in `PlaylistShareCodecTest`.

Verification: `assembleDebug`, `assembleRelease` (R8 clean with zxing), 57 unit tests passing, lint unchanged at its 9 pre-existing errors. Still no physical-device smoke test.

---

## 17. Artist pages that actually show the artist

**The bug:** an artist page showed karaoke covers, "best of" compilations and random tracks with the artist's name in the *title*, while missing most of the artist's real catalogue. The Albums section was permanently empty.

**The cause:** `NewPipeMusicSource.artist(id)` was literally `searchSongs(id, limit = 20)` — a plain keyword search. YouTube matches a query against the **title** just as readily as the uploader, one page deep, and nothing ever populated `Artist.albums`.

**The fix** — `data/source/youtube/ArtistMatching.kt`, a new pure-Kotlin matcher:

| Decision | Why |
|---|---|
| Match on `uploaderName`, **never** the title | This is the whole bug. A channel name is a claim of authorship; a title is not |
| Normalise before comparing | Strips channel noise (`- Topic`, `VEVO`, `- Official…`), folds accents via NFD + `\p{Mn}` removal, drops non-alphanumerics, lowercases. "Beyoncé - Topic" and "BeyonceVEVO" both reduce to `beyonce` |
| Test the **whole** normalised name before splitting on separators | Keeps "Simon & Garfunkel" and "Earth, Wind & Fire" intact instead of shattering them into two artists on the `&` |
| Split on `, ; · & / feat. ft. featuring with vs.` — but **never a bare `x`** | `x` is a collaboration separator *and* a letter inside names like "Lil Nas X" |
| Whole-name equality, never substring | Otherwise "Drake" matches "Drake Bell" |
| **Partition, don't drop** (`credited.ifEmpty { rest }`) | Some artists only ever appear under a label channel (HYBE for BTS). An empty page is worse than a slightly noisy one |
| Dedupe on id *and* on normalised title | The same track is routinely uploaded as both "Artist" and "Artist - Topic" |

Paging was added to `searchItems(…, pages)` — up to 4 pages / 80 results scanned, 50 songs kept — so the catalogue is deep enough to be useful. A failed continuation `break`s and keeps what it already had, rather than discarding the page. Albums now come from a real playlist search, and subscriber counts are formatted (`1.2M`).

Covered by 14 unit tests in `ArtistMatchingTest`.

---

## 18. Jam — listen together on one Wi-Fi

A Spotify-Jam-style shared session, built to the same constraints as everything else here: **no backend, no account, no login, no camera permission.**

### Shape of it

**Host-authoritative, host-only audio.** The host's Media3 timeline *is* the shared queue; guests are rich remote controls. One player means there is no clock to synchronise and no drift to correct — the hard half of "listen together" is designed out rather than solved badly.

- **Transport:** a plain `ServerSocket(0)` on the host's Wi-Fi address, one line-delimited text message per exchange. The OS picks the port, so the port travels in the invite.
- **Joining:** the host shows a QR encoding `musicsm://jam?…`. Guests scan it with the **system camera**, which opens the existing scheme filter — the same trick §16 uses, and the reason Jam needs no `CAMERA` permission. Share/copy-link fallbacks exist for cameras that won't offer a custom scheme.
- **No new manifest permission at all.** `INTERNET` already covers sockets, and the host's address comes from enumerating `NetworkInterface`, which avoids the Wi-Fi APIs (and their location-permission strings) entirely.

### Decisions worth keeping

| Decision | Why |
|---|---|
| `positionMs` + `hostClockMs` are in `JamSnapshot` **from day one**, unread | Synced multi-device playback is the obvious sequel. Adding those fields later would be a breaking protocol change; carrying them now costs 20 bytes |
| Snapshots are **absolute, never deltas** | A guest that misses a message is still correct after the next one. No replay, no sequence numbers, no resync path |
| Per-guest `Channel(32, onBufferOverflow = DROP_OLDEST)` + `trySend` | Follows from the above: dropping a stale snapshot is *correct*, so one guest on a bad connection can never stall the host's UI thread |
| Structural changes broadcast immediately; position rides a 3 s heartbeat | Naively mirroring player state would push ~20 messages/second to every guest |
| Three nested separators (`\u001F` / `\u001E` / `\u001D`), all stripped on encode | One message per line means `readLine()` frames it for free, and a song titled `a\u001Fb` can't forge a field boundary |
| `decode()` returns null instead of throwing; 512 KiB line cap, 1,000-track queue cap | Anything arriving over a socket is untrusted input |
| 10-char token from an unambiguous alphabet (no `0/O/1/l`), via `SecureRandom` | Not real security — it stops a co-located user port-scanning in, and invalidates stale links once the host restarts |
| Guest actions are re-routed in `PlayerViewModel`, not blocked in the UI | A guest tapping "add to queue", "play" or the transport controls anywhere in the app forwards to the host instead of starting a second, silent player. "Play next" degrades to a plain enqueue, and picking a track from an album contributes **only that track** — pushing 50 songs into someone else's Jam is rude |

A loopback integration test drives a real `JamServer` and `JamClient` over actual sockets, and earned its place immediately: it caught `JamClient` reporting `connect_failed` for a refused join because `onDisconnected` fired twice, burying the real `bad_token` reason. Every exit path now flows through a single `finally`.

Covered by 28 unit tests (`JamProtocolTest`, `JamInviteTest`, `JamLoopbackTest`).

**Known limitation:** many public and corporate Wi-Fi networks enable **AP isolation**, which blocks client-to-client traffic and will stop a Jam from connecting with no way for the app to tell that apart from a wrong network. Phone hotspots and home Wi-Fi are fine.

Verification: `assembleDebug`, `assembleRelease`, 99 unit tests passing, lint unchanged at its 9 pre-existing errors. Jam has been validated **in-process over loopback only** — it has never run on two physical devices.

---

*Generated from a full audit of the v1.5.0 source tree. File references point at the code that would need to change.*
