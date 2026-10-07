# Changelog

All notable changes to Murai Gallery are documented here.
Murai Gallery is based on [Aves](https://github.com/deckerst/aves) (BSD-3-Clause).

## 1.0.0

Initial public release of Murai Gallery — Aves, rebranded and extended.

### Rebranding

- New identity: application id `com.murai.gallery`, new logo and launcher icons
  (adaptive, monochrome/themed, splash, notification), new app name everywhere
- Firebase/Crashlytics removed; crash reports go to the console
- BSD-3-Clause license kept; Aves credited in the About screen

### New features

1. **Duplicate Finder** — exact (MD5) and similar (perceptual hash) detection, side-by-side preview, bulk delete
2. **Full Image Editor** — crop, rotate/flip, brightness/contrast/saturation, filters, text, drawing, stickers
3. **Video Trimmer** — frame-strip timeline, lossless MP4 trimming
4. **Compressor & Resizer** — batch quality/resolution reduction with before/after sizes
5. **OCR Text Extractor** — offline on-device text recognition (ML Kit)
6. **Collage Maker** — 2–9 photos, grid templates, borders, background color
7. **GIF Maker** — from video clips or image sequences
8. **Batch Rename** — `{date}` `{time}` `{counter}` `{name}` `{ext}` patterns with live preview
9. **Storage Cleaner** — usage by folder/type, large files, screenshots, one-tap cleanup
10. **QR/Barcode Scanner** — offline decoding from photos, safe link opening
11. **Auto Wallpaper Changer** — album-based rotation (hourly/daily/weekly) via WorkManager
12. **Share to Telegram** — bot API uploads with progress and retries, secrets stored encrypted
13. **Advanced Sort** — 10 sort types, ascending/descending, live preview, group-by combination, per-screen memory, named presets, "set as default"
14. **Private Vault Upgrade** — decoy PIN, recents screenshot hiding, biometric unlock
15. **Backup & Restore** — zip export/import of settings, favourites and presets
16. **Settings Upgrades** — in-app update checks (GitHub Releases), error library (30-day retention, export), AMOLED/dynamic themes, language switcher

### Engineering

- Signed release APKs (split per ABI + universal) on every push to `main`
- Auto-versioning: `1.0.<run number>`, tags `v1.0.<run number>`
- Quality workflow: `flutter analyze` (clean) + unit tests on every push
