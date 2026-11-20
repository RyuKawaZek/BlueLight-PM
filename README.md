# BlueLight

**BlueLight** is a PocketMine-MP / BlueLight server manager for Android written in Kotlin and Jetpack Compose, targeting Android 15 (`targetSdk 36`) with a contrast-locked ANSI console, direct shared-storage file management, and a floating overlay console bubble.

## Summary & Core Features

- **Background Server Hosting (`ServerService`):** Start, gracefully stop (`stop`), or force-kill local PocketMine-MP / BlueLight server processes.
- **ARM64 PHP Runtime (`libphp.so`):** Bundled static ARM64 PHP CLI configured with unbuffered real-time output (`output_buffering=0`, `implicit_flush=1`) plus deduplicated `server.log` tailing.
- **Direct Shared Storage & File Manager (`/storage/emulated/0/PocketMine`):** Browse directories with breadcrumb pills, create folders/files, import/export files, extract `.zip` archives, rename/delete entries, and edit config files in a monospace text editor.
- **Contrast-Protected ANSI Console (`AnsiParser`):** Full ANSI SGR & 256-color rendering with **Terminal Dark**, **Midnight OLED**, and **High-Contrast Light** themes isolated from Android 15 system colors, command history, quick `/command` chips, auto-scroll, and clipboard copy.
- **Floating Console Bubble (`FloatingConsoleService`):** Draggable `Display over other apps` circle bubble that expands into a mini console window while playing Minecraft or using other apps.
- **Metrolist-Inspired Material 3 UI:** Segmented grouped cards, expressive switches, Light/Dark/System themes, Pure Black AMOLED dark mode, Android 12+ Material You dynamic color, accent color presets, and a toggleable floating bottom bar with a persistent Mini Server Bar.

## Recent Updates & Bug Fixes

- **UI Overhaul:** Adopted a Metrolist-inspired Material 3 Expressive layout with floating top/bottom pill bars, Mini Server Bar, segmented grouped lists, Pure Black AMOLED mode, and accent seeds.
- **Console Output Fix:** Enabled unbuffered PHP CLI output flags, character-stream reading, and `server.log` tailing so commands like `/plugins` and `/say` display reliably in real time.
- **Android 15 Console Contrast Fix:** Decoupled console colors from system theme defaults to prevent light-on-light invisible log text.
- **Shared Storage & Floating Overlay:** Moved server workspace directly to `/storage/emulated/0/PocketMine`, added an in-app Files manager, and added the draggable overlay console bubble.

## Download & Install Debug APK

1. Open **[GitHub Releases](../../releases/latest)** in this repository on your phone.
2. Under **Assets**, download `<repo-name>-debug-build-<run>.apk` (or download the `app-debug-apk` artifact from the latest **Actions** workflow run as a backup).
3. Open the `.apk` file and allow **Install unknown apps** for your browser or file manager when Android prompts.
4. **Updating:** Debug APKs built in CI use a temporary debug keystore regenerated per runner, so Android cannot overwrite an earlier build in place—uninstall the previous debug APK first if Android reports a package conflict.

## Tech Stack & Build Environment

- **Language & UI:** Kotlin, Jetpack Compose, Material 3 (`androidx.compose.material3`)
- **Architecture & Async:** MVVM (`AndroidViewModel`, `StateFlow`), Kotlin Coroutines, OkHttp
- **Testing:** JUnit 4, Kotlinx Coroutines Test, Robolectric (`sdk = 36`)
- **Android Build Environment:**
  - **AGP:** `9.1.1`
  - **Gradle:** `9.3.1`
  - **Java:** Temurin JDK `17`
  - **SDK:** `compileSdk 36`, `targetSdk 36`, `minSdk 24`

### Local Build & Test Commands
```bash
# Build debug APK
gradle :app:assembleDebug --stacktrace --no-daemon

# Run unit & Robolectric tests
gradle :app:testDebugUnitTest
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## License

Licensed under the MIT License. See [`LICENSE`](LICENSE).
