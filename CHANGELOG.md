# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project tracks regular feature updates, bug fixes, and automated debug APK builds.

## [Unreleased]

### Added
- **Floating Bubble Upgrades (`FloatingConsoleService`):** Saved bubble & panel coordinates across restarts (clamped to screen bounds), added bubble size (`Small`/`Medium`/`Large`) and opacity (`40%–100%`) controls in both Settings and an in-overlay quick-tune drawer, and added customizable 1-tap command macros (`/list`, `/save-all`, `/time set day`, `/gamemode c`, etc.).
- **Console Filter, Search & Autocomplete (`ConsoleScreen`, `ConsoleTools`):** Added log-level filter chips (`All`, `Info`, `Warn`, `Error`, `Chat`), keyword search bar with filtered line count, and `/` command autocomplete suggestions with syntax hints and recent history.
- **Multi-Server Profiles (`ServerRepository`, `ProfileManagerDialog`):** Added support for creating, renaming, deleting, and switching between isolated server workspaces under `/storage/emulated/0/PocketMine-Profiles/<slug>` alongside the default `/storage/emulated/0/PocketMine` workspace.

## [2.0.0] - 2026-11-20

### Added
- **Metrolist-Inspired Material 3 UI System:** Added Pure Black AMOLED mode, Material You dynamic color toggle (Android 12+), selectable accent color presets (`Sapphire`, `Cyan`, `Emerald`, `Violet`, `Amber`), segmented grouped settings/file rows, expressive switches, floating top/bottom pill bars, and a persistent Mini Server Bar (`MetroComponents.kt`, `Shapes.kt`, `Theme.kt`).
- **Floating Console Bubble (`FloatingConsoleService`):** Added `Display over other apps` draggable circle icon and expandable mini console window for running commands over Minecraft and other apps.
- **Direct Shared Storage & In-App File Manager (`FilesScreen.kt`):** Added direct workspace management at `/storage/emulated/0/PocketMine` with breadcrumb navigation, folder/file creation, `.zip` extraction, SAF import/export, rename/delete, and a monospace text editor.
- **Automated Debug APK CI/CD (`.github/workflows/build-apk.yml`):** Configured GitHub Actions with Temurin Java 17, Gradle 9.3.1, AGP 9.1.1, temporary debug keystore generation, `app-debug-apk` artifact upload, and automatic GitHub Release publishing.

### Changed
- **Re-Sorted App Navigation & Screens:** Reorganized Dashboard, Console, Files, and Settings into grouped Material 3 sections with horizontal quick-action chip shelves and adaptive `NavigationRail` support on wide screens.
- **Documentation Maintenance:** Updated `README.md` and `CHANGELOG.md` to reflect current build requirements, bug fixes, and debug APK download steps.

### Fixed
- **Missing Command Output in Console:** Added `-d output_buffering=0 -d implicit_flush=1` to PHP CLI invocations, switched stdout capture to a `CharArray` stream reader, and added a deduplicated `server.log` tailer so `/plugins`, `/say`, and live server logs appear immediately (`ServerRepository.kt`).
- **Invisible Console Text on Android 15:** Replaced unreadable light-theme console text with an explicit ANSI SGR/256-color parser (`AnsiParser.kt`) and isolated high-contrast console color tokens (`Terminal Dark`, `Midnight OLED`, `High-Contrast Light`).

### Known Issues
- Debug APKs published by GitHub Actions are signed with a temporary per-run debug keystore, so Android requires uninstalling an older CI debug build before installing a newer one.

## [Initial Setup] - Earlier Iteration

- Initial signed release pipeline, documentation scaffolding, and Kotlin/Jetpack Compose migration from `BlueLightAPP`.
