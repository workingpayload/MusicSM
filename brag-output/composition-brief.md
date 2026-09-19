# Hyperframes Composition Brief: MusicSM

## Objective
A 20s app-store-style launch brag for MusicSM, a dark glassmorphic Android music app.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 20s

## Source Material
- Project root: C:/Users/rs919/AndroidStudioProjects/MusicSM
- Product name: MusicSM
- Tagline / strongest claim: "Your music, done right." / "Built from scratch."
- Key UI to recreate: Now Playing (album art + flowing coral→lavender→teal seek bar + frosted round play button), Home shelves, wavy download bar, crossfade.
- Copy verbatim: MusicSM · Your music, done right. · Now Playing · Recommended for you · Trending now · From artists you follow · Downloaded · True crossfade — no gaps. · Built from scratch.

## Creative Direction
- Tone preset: app-store
- Creative direction: sleek premium music-app launch film
- Angle: show the real coral-on-black aurora-glass UI; the flex is the density of polish shipped solo.
- Hook: coral MusicSM wordmark over aurora bloom + "Your music, done right."
- Outro: feature chips collapse into MusicSM + "Built from scratch."
- Avoid: generic SaaS language, abstract filler, redesigns.

## Visual Identity
- Background: #121318
- Accent: #FF525E (coral); secondary #DDB7FF (lavender), #00DFC1 (teal)
- Text: #E3E1E9
- Fonts: Plus Jakarta Sans (fallback system sans)
- References: aurora coral/lavender radial blooms; capsule seek bar; frosted round play button; rounded album cards.

## Storyboard
See brag-plan.md. Scenes: 1 Hook 0-3 · 2 Now Playing 3-8 · 3 Home shelves 8-12 · 4 Offline+Crossfade 12-16 · 5 Chips→Outro 16-20.

## Audio
- Role: warm modern music bed (music leads — it's a music app).
- Music: assets/music/music.mp3 (happy-beats-business-moves vol 1, 120 BPM). Volume ~0.85, gentle fade in the last ~0.6s.
- Cue guidance: strong cue at 16.02s → land the outro wordmark there (beat-locked). Shelf reveals on the 0.5s beat grid (8.5/9.5/10.5), held to the read floor.
- Audio-reactive: subtle — seek bar / aurora may breathe; no waveform bars.
- SFX: sparse — tap.ogg on the play button, tick.ogg on shelf + chip arrivals.
- Restraint: no loud stingers; premium and clean.

## Notes
- Real album artwork can't be fetched offline; album art is a coral→lavender gradient placeholder. The recreated real UI elements are the seek bar, glass play button, shelves, download wave, and aurora palette.
- All motion authored on the single GSAP root timeline (seek-safe); deterministic only.
