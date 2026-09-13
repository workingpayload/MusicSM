---
name: Liquid Lumina
colors:
  surface: '#121318'
  surface-dim: '#121318'
  surface-bright: '#38393f'
  surface-container-lowest: '#0d0e13'
  surface-container-low: '#1a1b21'
  surface-container: '#1e1f25'
  surface-container-high: '#292a2f'
  surface-container-highest: '#34343a'
  on-surface: '#e3e1e9'
  on-surface-variant: '#e6bdbc'
  inverse-surface: '#e3e1e9'
  inverse-on-surface: '#2f3036'
  outline: '#ad8887'
  outline-variant: '#5d3f3f'
  surface-tint: '#ffb3b2'
  primary: '#ffb3b2'
  on-primary: '#680013'
  primary-container: '#ff525e'
  on-primary-container: '#5b0010'
  inverse-primary: '#bf002c'
  secondary: '#ddb7ff'
  on-secondary: '#490080'
  secondary-container: '#6f00be'
  on-secondary-container: '#d6a9ff'
  tertiary: '#00dfc1'
  on-tertiary: '#00382f'
  tertiary-container: '#00a38d'
  on-tertiary-container: '#003028'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdad9'
  primary-fixed-dim: '#ffb3b2'
  on-primary-fixed: '#410008'
  on-primary-fixed-variant: '#92001f'
  secondary-fixed: '#f0dbff'
  secondary-fixed-dim: '#ddb7ff'
  on-secondary-fixed: '#2c0051'
  on-secondary-fixed-variant: '#6900b3'
  tertiary-fixed: '#26fedc'
  tertiary-fixed-dim: '#00dfc1'
  on-tertiary-fixed: '#00201a'
  on-tertiary-fixed-variant: '#005144'
  background: '#121318'
  on-background: '#e3e1e9'
  surface-variant: '#34343a'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 44px
    fontWeight: '800'
    lineHeight: 52px
  display-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 34px
    fontWeight: '800'
    lineHeight: 40px
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 38px
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 12px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.5rem
  margin-mobile: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.25rem
---

## Brand & Style
This design system defines an immersive, tactile, and cinematic audio experience built upon advanced glassmorphic layering and deep-field atmospheric color bleeding. Rooted in an obsessive dedication to optical clarity and acoustic intimacy, the visual identity prioritizes content-first minimalism while enveloping the user in luminous, real-time reactive canvas styling.

### Aesthetic Principles
- **Atmospheric Depth:** True pitch-black (#050507) acts as the silent void, pierced exclusively by dynamic, multi-stop aurora radial mesh gradients sampled from the currently playing album artwork.
- **Precision Glassmorphism:** Translucent glass sheets feature high-refraction specular edges, dual-layer backdrop blurs, and variable optical transmission depending on hierarchical elevation.
- **Fluid Micro-physics:** Transitions embody real-world inertia, utilizing spring-based dynamics for album art expansion, haptic transport controls, and elastic scrubber interactions.
- **Audio-Centric Ergonomics:** Core actions reside entirely within comfortable thumb reach zones, pairing ultra-crisp typography with high-contrast glowing micro-indicators.

## Colors
The palette is engineered specifically for OLED displays and deep dynamic range rendering. The foundation avoids flat dark grays in favor of an ultra-deep obsidian canvas tinted with 1% midnight blue.

### Strategic Color Roles
- **Primary (`#FA2D48` - Lumina Crimson):** The signature vibrant streaming accent used for high-urgency interactive states, active play heads, audio fidelity tags (Lossless, Spatial Audio), and favorite states.
- **Secondary (`#A855F7` - Neon Amethyst):** A harmonic atmospheric color paired with primary gradients for ambient artwork backlight simulations and secondary media selections.
- **Tertiary (`#00F5D4` - Aurora Cyan):** A high-frequency contrast punch reserved for live lyric sync bars, volume ceiling highlights, and active routing icons (AirPlay, Bluetooth).
- **Neutral Canvas (`#090A0F` - Obsidian Core):** Serves as the zero-luminance bedrock, allowing ambient blur overlays to create perceived depth without washing out contrast.

### Translucent Glass & Overlay Tokens
- **Glass Base (Standard Container):** `rgba(255, 255, 255, 0.06)` with backdrop-filter `blur(24px) saturate(180%)`.
- **Glass Elevated (Floating Sheets/Modals):** `rgba(255, 255, 255, 0.12)` with backdrop-filter `blur(40px) saturate(200%)`.
- **Specular Rim Highlights:** Linear gradient top-to-bottom: `rgba(255, 255, 255, 0.20)` blending to `rgba(255, 255, 255, 0.02)` at the bottom edge.
- **Backdrop Canvas Glow:** 3 dynamic ambient light orbs at 25% opacity, scaled at `blur(120px)`.

## Typography
Typographic hierarchy is calibrated to mimic the balance of human-centric mobile OS design while injecting subtle geometric modernity. `Plus Jakarta Sans` delivers clean grotesque terminals, tight tracking at display scales, and wide open apertures for high legibility under varying frosted blur backgrounds.

### Typographic Hierarchy & Behavior
- **Displays & Primary Titles:** Tight letter-spacing (-0.02em) with heavy weights creates visual mass that anchors against full-bleed album artwork.
- **Track & Artist Meta:** Track titles prioritize bold weights (`headline-md`) with pure white `#FFFFFF` foreground, whereas contributing artists drop to `body-md` rendered in `rgba(255, 255, 255, 0.64)`.
- **Live Lyrics Mode:** Inactive lyric lines use `headline-lg` styled with `rgba(255, 255, 255, 0.25)` and 4px Gaussian blur; the active line transitions instantaneously to 100% white, zero blur, and a subtle scaling boost of 1.05x.
- **Audio Badges:** Badges (e.g., "LOSSLESS", "DOLBY ATMOS") use `label-sm` with +0.08em tracking in uppercase for razor-sharp micro-rendering.

## Layout & Spacing
The layout employs an adaptive fluid column framework anchored to hardware safe areas (dynamic islands, navigation bars, and thumb sweep horizons). 

### Layout Model
- **Mobile First Canvas:** Fluid container constrained by 1.25rem (`margin-mobile`) horizontal edge insets. On compact screens, the layout organizes into a vertical stack: Dynamic Ambient Canvas > Large Format Cover Art (1:1 ratio) > Track Info Header > Floating Progress Bar > Hero Transport Cluster > Secondary Utilitarian Dock.
- **Reflow & Tablet/Desktop Presentation:** At viewports ≥ 768px, layout transitions from single-column vertical stack into an asymmetric dual-pane layout: sticky Left Pane hosting the 3D-projected album glass display, and Right Pane hosting dynamic scrollable track queues, lyrics, and metadata details.
- **Thumb Zone Ergonomics:** Interactive targets (play/pause, skip, scrubbing) are centralized strictly between 25% and 75% from the screen bottom.

## Elevation & Depth
Elevation is expressed through refractive glass optical density, luminous light diffusion, and progressive backdrop filtering rather than opaque material drop shadows.

### Surface Elevation Tiers
1. **Tier 0 (Bedrock Canvas):** Deep pitch obsidian `#090A0F` containing fluctuating dynamic radial mesh glows (extracted from cover art color poles, Gaussian blurred to 120px radius).
2. **Tier 1 (Surface Glass):** Used for standard cards, secondary trays, and lyric sheet scrollers. Filled with `rgba(255, 255, 255, 0.05)`, edged with a 1px border of `rgba(255, 255, 255, 0.10)`, and filtered with `backdrop-filter: blur(24px)`.
3. **Tier 2 (Floating Controls & Navigation):** Docked bottom bars and mini-players. Filled with `rgba(255, 255, 255, 0.10)`, top-specular edge `rgba(255, 255, 255, 0.25)`, ambient perimeter drop shadow `0 20px 40px rgba(0, 0, 0, 0.45)`, and `backdrop-filter: blur(36px) saturate(190%)`.
4. **Tier 3 (Modals & Sheet Overlays):** Full-bleed queue drawers and contextual menus. Dynamic `rgba(18, 20, 29, 0.72)` tint with specular perimeter stroke and deep ambient shadow `0 32px 64px rgba(0, 0, 0, 0.65)`.
5. **Interactive Glow Elevation:** Active transport elements (e.g., active play button) emit a directional ambient halo matching the primary hue (`0 0 24px rgba(250, 45, 72, 0.45)`).

## Shapes
Geometry is smooth and continuous, following super-elliptical curvature (squircle) to blend seamlessly with premium mobile hardware corners.

### Shape Geometry Rules
- **Structural Framing:** Base interactive modules (cards, sheet modals) implement standard `rounded-lg` (1rem / 16px) or `rounded-xl` (1.5rem / 24px) squircle curves.
- **Album Artwork:** Scaled with an exact 0.75rem (12px) to 1.125rem (18px) curvature, preserving edge clarity without harsh angular intersections.
- **Full Pills (`rounded-full`):** Transport controls, tag badges, scrub handles, and floating filter chips maintain complete geometric pill status (9999px border radius) to maximize touch affordance.

## Components

### Hero Transport Controls
- **Play/Pause Toggle:** Floating 68px glass orb filled with high-refraction glass `rgba(255, 255, 255, 0.15)` or solid Primary Accent (`#FA2D48`), inset highlight on upper rim, containing sharp monochrome SVG icons. On press, scales to `0.92` with spring dampening.
- **Skip/Previous Track:** 48px translucent circular hit-targets. Fill: `rgba(255, 255, 255, 0.04)` resting, switching to `rgba(255, 255, 255, 0.12)` on active touch.

### Fluid Scrubber & Volume Slider
- **Progress Track:** 4px tall (expands to 8px during user drag interaction). Inactive background: `rgba(255, 255, 255, 0.16)`. Buffered progress: `rgba(255, 255, 255, 0.32)`. Active played track: `#FFFFFF` or multi-stop gradient fading from `#FA2D48` to `#A855F7`.
- **Thumb Indicator:** 14px circular glass pill featuring an inner glow and ambient shadow `0 2px 8px rgba(0,0,0,0.5)`. Scales to 20px upon touch input.

### Glassmorphic Cards & Track Rows
- **Track Row Item:** Compact 60px height container. Surface: transparent default; `rgba(255, 255, 255, 0.08)` when active/pressed. Includes high-resolution 44px album thumbnail, bold track name, secondary artist string, and an animated 3-bar audio equalizer icon when currently playing.
- **Now Playing Mini-Dock:** Floating dock anchored 12px above safe area margins. Elevated Tier 2 glass construction, surrounded by a 1px continuous specular stroke, displaying track title, thumbnail, play/pause, and fast-forward actions.

### Interactive Chips & Format Badges
- **Pill Badges:** Lossless, Spatial Audio, Master quality tags. Rendered with `label-sm`, uppercase, enclosed inside a 20px tall capsule with `rgba(255, 255, 255, 0.08)` fill and `rgba(255, 255, 255, 0.18)` outline.
- **Segmented Glass Tabs:** Pill-shaped grouped selector for "Up Next", "Lyrics", and "Related". Active tab features high-luminance glass backing with subtle specular reflection; inactive tabs display flat 50% opacity text.

### Inputs & Search Bars
- **Glass Search Field:** Fully pill-shaped search bar with `rgba(255, 255, 255, 0.08)` fill, dynamic backdrop-blur (16px), integrated magnifying icon with `rgba(255, 255, 255, 0.40)` fill, and clear action button. Focused state intensifies border stroke to `rgba(250, 45, 72, 0.60)` accompanied by a subtle 8px neon halo.