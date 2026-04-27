# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Install

```bash
# Build debug APK locally
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI (GitHub Actions) builds automatically on every push to `main` and uploads the APK as the artifact `AutoYouTube-debug-apk`. To trigger manually: **Actions → Build APK → Run workflow**.

## Architecture

The app has two Android Auto entry points that serve different purposes:

### 1. `AutoYouTubeService` (Car App Library — primary UI)
Category: `POI`. Entry point: `AutoYouTubeSession` → `SearchScreen`.

- **`SearchScreen`** — renders a `SearchTemplate` (search box + results list). Calls `YouTubeHelper.search()` on IO dispatcher, then calls `invalidate()` to re-render.
- **`VideoScreen`** — renders a `MapTemplate` with a `SurfaceCallback` so ExoPlayer can paint frames onto the car's hardware surface. Play/Pause/Back are in an `ActionStrip`.

### 2. `YouTubeMediaService` (MediaBrowserServiceCompat — media protocol)
Handles Android Auto's media browsing protocol (voice search via `onPlayFromSearch`, media controls). Declared via `automotive_app_desc.xml` (`<uses name="media"/>`), which is why the app appears in the Android Auto media section.

### Data / Playback layer

- **`YouTubeHelper`** — singleton wrapping NewPipe Extractor. `init()` must be called before any other method (done in both service `onCreate` calls). `search()` and `getStreamResult()` are blocking and must run on IO dispatcher.
- **`DownloaderImpl`** — singleton OkHttp-based `Downloader` passed to `NewPipe.init()`. Bridges NewPipe's HTTP abstraction to OkHttp.
- ExoPlayer supports DASH (preferred), HLS (fallback), and Progressive streams. The stream type is determined by `YouTubeHelper.getStreamResult()` which inspects `StreamInfo` from NewPipe.

## Key constraints

- **NewPipe version**: `v0.24.2` — NewPipe's extractor API changes significantly between versions. Check release notes before upgrading.
- **Car API level**: `minCarApiLevel=2` in the manifest meta-data. `SearchTemplate` requires API 2+; `MapTemplate` (used for video) requires API 2+.
- **Threading**: All NewPipe calls block the thread. Always use `withContext(Dispatchers.IO)`. Never call `YouTubeHelper` on the main thread.
- **Surface lifecycle**: ExoPlayer's video surface must be set/cleared in `SurfaceCallback.onSurfaceAvailable` / `onSurfaceDestroyed`. The surface can arrive before or after `ExoPlayer` is built.
