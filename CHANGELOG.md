# Changelog

All notable changes to Murai Gallery are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.0] — Major Release

The complete v2 rewrite. **Every line of code, every screen, every string and
every asset in this release was written from scratch** for the native Kotlin +
Jetpack Compose engine. Nothing carries over from the previous codebase.

### Added

#### Core gallery
- Native Jetpack Compose UI with the original "Ink & Amber" design system
- Timeline with paged background scanning — the grid appears immediately on
  50,000+ item libraries while indexing continues
- Albums, favorites, and a bin with restore
- Search by name, folder, place, date, type and size
- Full-screen viewer: pinch-zoom, double-tap, swipe, transitions
- Video player (Media3/ExoPlayer), GIF and animated WebP support
- Motion photo playback and panorama/360° panning viewer
- Metadata viewer and editor (EXIF date + GPS)
- Photo map on OpenStreetMap tiles
- Library statistics dashboard with charts
- Slideshow with configurable pace and crossfade
- Home-screen "Latest photo" widget

#### Smart tools (16)
- Duplicate finder with exact (SHA-256) and perceptual (dHash) matching
- Image editor: crop, brightness/contrast/saturation, 8 filters, text,
  freehand drawing, emoji stickers, undo
- Video trimmer with MediaMuxer-based export
- Batch compressor/resizer with before/after size comparison
- Offline OCR text extraction (ML Kit, on-device model)
- Collage maker for 2–9 photos with grid templates
- GIF maker powered by an original GIF89a/LZW encoder
- Batch rename with `{date}` `{counter}` `{name}` `{ext}` patterns
- Storage cleaner: folder usage, biggest files, screenshot sweeps
- QR/barcode scanner for photos with HTTPS safety confirmation
- Auto wallpaper rotation via WorkManager (1h–24h intervals)
- Telegram sharing through a private bot with progress and retries
- Advanced sort system: 10 sort types with direction toggles, grouping,
  named presets and per-screen memory
- Vault upgrade: PIN + biometric unlock, AndroidKeyStore AES/GCM encryption,
  hidden-from-recents behavior and a decoy PIN
- Backup & restore of settings, tags, favorites and sort presets as a zip
- Settings upgrade: instant whole-app language switcher (10 languages),
  30-day on-device error log with zip export, light/dark/AMOLED/dynamic
  themes, in-app update check against GitHub Releases with offline messaging

#### Platform
- GitHub Actions release pipeline: signed per-ABI + universal APKs,
  automatic versioning (`versionCode = 200 + run number`), checksums and
  GitHub Release attachment on tags

### Changed
- Application identity stays `com.murai.gallery`; version restarts at 2.0.0
  (versionCode 200) and always updates over any 1.x build
- New brand identity: aperture-ring murai logo, adaptive + monochrome icons,
  new splash animation, notification icon and social images
- All UI copy original; app ships in 10 languages

### Removed
- The entire previous cross-platform source tree, build pipeline, assets,
  translation corpus and legacy dependencies

### Fixed
- "New Tools" row no longer overlaps the status bar or clips icons — the app
  is fully edge-to-edge with correct window insets on every screen, in both
  orientations and with gesture and 3-button navigation
- Loading never stalls: media indexing is paged and reports real progress
- No overlapping or clipped elements; layouts verified across form factors
- Media operations use MediaStore content URIs exclusively, eliminating
  "Invalid URI" errors from the previous generation
- Missing metadata no longer breaks sorting — such items sort to the end

### Breaking changes
- **Signature change:** v2.0.0 is signed with a new release key. Uninstall
  v1.0.6 before installing v2.0.0. Photos are stored in the system gallery
  and are not affected.
- The old preference database is not migrated; the new engine stores its own
  settings. Use Backup & Restore inside v2 to move your tags and favorites
  forward.

## [1.0.6] — previous generation

Maintenance release of the v1.x codebase (no longer shipped).
