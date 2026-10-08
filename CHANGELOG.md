# Changelog

All notable changes to Murai Gallery are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.1] — Stability Update

A crash and major bug-fix release. Same app, same features — far fewer ways
to fall over. Every fix below was found by a 10-round audit (crashes, memory,
Android 8/10/13/14/15 permissions, threading, navigation).

### Fixed

#### File paths were never real file paths
- `LibraryItemEntity.path` stores a MediaStore **relative path**, not a file
  path, but move, copy, rename, legacy delete, the EXIF sheet, the editor,
  the compressor and motion-photo extraction all used it as one. Everything
  now streams through the item's **content URI** (`openInputStream` /
  `openFileDescriptor`):
  - move/rename go through MediaStore updates (`RELATIVE_PATH` on Q+,
    DATA-resolved file renames on 26–28)
  - EXIF editing round-trips through a private temp copy and writes back via
    `openOutputStream`, requesting **`createWriteRequest` consent** when the
    system demands it
  - the editor, compressor and panorama viewer decode from content URIs with
    two-pass sampling
  - motion-photo trailer search streams through a fixed 512 KB window instead
    of loading the whole JPEG

#### Scanner rebuilt for every API level
- Projection is built per API level: `RELATIVE_PATH` only on 29+,
  `IS_FAVORITE` / `IS_TRASHED` only on 30+, folder names derived from
  `DATA` / `BUCKET_DISPLAY_NAME` on 26–28; optional columns are read with
  `getColumnIndex` + `-1` checks instead of `getColumnIndexOrThrow`
- API 30+ queries include **trashed rows** via `QUERY_ARG_MATCH_TRASHED =
  MATCH_INCLUDE`, so the Bin keeps working across rescans; 26–29 keep the
  app-internal bin
- GPS coordinates are backfilled in a bounded background pass via
  `MediaStore.setRequireOriginal(uri)` + `ACCESS_MEDIA_LOCATION`, so the Map,
  location sort/group and GeoLabeler actually have coordinates on 29+
- Scanning is **single-flight**: ScanWorker, pull-to-refresh and the
  permission callback share one coordinated pass instead of interleaving
- `deleteStale` runs only after a fully completed, permitted pass — never
  after a partial, failed or permission-less one; a revoked or partial grant
  (Android 14 selected photos) can no longer wipe the library cache

#### Startup crashes
- `ErrorLogger` is installed **before** anything else can fail (it used to be
  registered after the DI container was built)
- The persisted app language is applied with `AppCompatDelegate
  .setApplicationLocales` on the **main thread** before UI, not from a
  background dispatcher
- Notification channels and WorkManager scheduling run in guarded blocks and
  log failures instead of crashing the launch

#### Video player
- Removed the `runBlocking` that froze the main thread: sources resolve off
  the main thread with a loading state, the player is created in a
  `DisposableEffect` keyed on the resolved source (released on change and on
  dispose), and decode failures show a friendly error with retry

#### Memory (OOM)
- Wallpaper application decodes with `inSampleSize` to screen size and
  recycles the bitmap — a 50 MP wallpaper source no longer kills the process
- Vault encryption/decryption **streams in 64 KB GCM chunks** (new container
  format with magic header); old whole-buffer vault files still decrypt via
  legacy-format detection, and nothing is ever read into a byte array whole
- The viewer builds its stream from **id-only queries** with a per-scope
  cache and a sliding entity window, instead of loading every full row

#### Permissions (Android 8 → 15)
- Android 14's `READ_MEDIA_VISUAL_USER_SELECTED` is treated as **partial
  access**: the library shows, with a small *limited access* banner and a
  *Select more* action
- The rationale dialog no longer loops after "Not now"; it shows once per
  entry while nothing is granted, and requests only follow the rationale

#### External intents
- `ACTION_VIEW` opens the **exact incoming URI** in a dedicated `uri:` scope
  (widening to its folder when the item resolves to a library row) — never a
  fallback to the first gallery item; works with zero storage permission via
  URI grants
- `ACTION_SEND`, `ACTION_SEND_MULTIPLE` and `ACTION_SET_WALLPAPER` are all
  handled, plus `onNewIntent` while the app is already running

#### Navigation
- Every dynamic route argument (bucket ids, viewer payloads, video/editor/
  trimmer URIs) is **encoded** and decoded through one helper with a safe
  format replacing `|` / `:` separators; hostile folder names (`A/B`,
  `a?b`, `{x}`, unicode, spaces) round-trip safely
- Every destination guards missing/invalid arguments and pops back instead
  of crashing

#### Consent bus
- Replaced the last-write-wins `StateFlow` with a **one-shot queue**: multiple
  requests line up instead of overwriting, each `IntentSender` launches at
  most once, and cancel/failure/activity recreation always completes the
  callback

#### Database
- **`fallbackToDestructiveMigration` removed**: upgrades use real migrations
  and `exportSchema = true` with a committed schema directory — tags,
  favorites, sort presets and the vault index can no longer be wiped by an
  upgrade
- Unrecoverable database corruption is handled: the broken file is backed up
  next to itself, a fresh database is created, and the event is logged

#### Crash hardening
- Every tool screen (Duplicates, Editor, Compressor, Collage, GIF Maker,
  Trimmer, OCR, QR, Rename, Cleaner, Telegram, Wallpaper, Backup, Vault, Bin)
  runs its work through a guarded launcher: failures land in the error log
  and logcat instead of killing the process
- New **global recovery screen**: after an uncaught crash, the next launch
  shows the saved stack trace with *Copy log* and *Export log zip* actions

### Added
- Unit test suite: per-API projection building, relative-path derivation,
  hostile-name route encoding/decoding, single-flight scan + deleteStale
  guard, consent-bus queue semantics, and streaming vault round-trips
  (including legacy-format files)

### Changed
- Version 2.0.1 (versionCode 201+); still `com.murai.gallery`, same signature,
  updates cleanly over 2.0.0

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
